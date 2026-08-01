package com.jaipilot.mcp.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Applies pinned OpenRewrite cleanup recipes without modifying the project's build files. */
public final class OpenRewriteCleanupService {

    static final String MAVEN_PLUGIN_VERSION = "6.44.0";
    static final String GRADLE_PLUGIN_VERSION = "7.37.0";
    static final String STATIC_ANALYSIS_VERSION = "2.39.0";
    static final String TARGETED_RECIPE = "com.jaipilot.TargetedCodeCleanup";

    private static final String STATIC_ANALYSIS_ARTIFACT =
            "org.openrewrite.recipe:rewrite-static-analysis:" + STATIC_ANALYSIS_VERSION;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(20);
    private static final int MAX_CAPTURED_OUTPUT_CHARACTERS = 1_048_576;
    private static final String UNSUPPORTED_FILE_PATTERN_CHARACTERS = ";\n\r*?[]{}\\";

    private final JavaProjectService projectService;
    private final RewriteExecutor rewriteExecutor;
    private final Duration timeout;

    public OpenRewriteCleanupService(JavaProjectService projectService) {
        this(projectService, defaultExecutor(new ProcessExecutor()), DEFAULT_TIMEOUT);
    }

    OpenRewriteCleanupService(
            JavaProjectService projectService,
            RewriteExecutor rewriteExecutor,
            Duration timeout
    ) {
        this.projectService = Objects.requireNonNull(projectService, "projectService");
        this.rewriteExecutor = Objects.requireNonNull(rewriteExecutor, "rewriteExecutor");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    public RewriteResult clean(Path projectRoot, List<Path> targetFiles) {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        List<Path> relativeTargets = normalizeTargets(normalizedRoot, targetFiles);
        Path recipeConfig = null;
        Path gradleInitScript = null;
        try {
            recipeConfig = Files.createTempFile("jaipilot-openrewrite-", ".yml");
            Files.writeString(recipeConfig, buildRecipeYaml(relativeTargets), StandardCharsets.UTF_8);

            JavaProjectService.BuildTool buildTool = projectService.detectBuildTool(normalizedRoot);
            if (buildTool == JavaProjectService.BuildTool.GRADLE) {
                gradleInitScript = Files.createTempFile("jaipilot-openrewrite-", ".gradle");
                Files.writeString(
                        gradleInitScript,
                        buildGradleInitScript(recipeConfig),
                        StandardCharsets.UTF_8
                );
            }

            List<String> command = buildCommand(normalizedRoot, buildTool, recipeConfig, gradleInitScript);
            ProcessExecutor.ExecutionResult result = rewriteExecutor.execute(
                    command,
                    normalizedRoot,
                    timeout,
                    false,
                    new PrintWriter(System.err, true),
                    ProcessExecutor.ProgressListener.noOp(),
                    cacheEnvironment(normalizedRoot, buildTool)
            );
            validateResult(result);
            return new RewriteResult(List.copyOf(command), result.elapsed());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenRewrite cleanup was interrupted; the isolated candidate was discarded.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to run OpenRewrite cleanup in the isolated workspace.", exception);
        } finally {
            deleteTemporaryFile(gradleInitScript);
            deleteTemporaryFile(recipeConfig);
        }
    }

    List<String> buildCommand(
            Path projectRoot,
            JavaProjectService.BuildTool buildTool,
            Path recipeConfig,
            Path gradleInitScript
    ) {
        String executable = projectService.resolveBuildExecutable(projectRoot);
        return switch (buildTool) {
            case MAVEN -> List.of(
                    executable,
                    "-B",
                    "-ntp",
                    "test-compile",
                    "org.openrewrite.maven:rewrite-maven-plugin:" + MAVEN_PLUGIN_VERSION + ":runNoFork",
                    "-Drewrite.configLocation=" + recipeConfig.toAbsolutePath().normalize(),
                    "-Drewrite.recipeArtifactCoordinates=" + STATIC_ANALYSIS_ARTIFACT,
                    "-Drewrite.activeRecipes=" + TARGETED_RECIPE,
                    "-Drewrite.exportDatatables=false",
                    "-DskipMavenParsing=true"
            );
            case GRADLE -> List.of(
                    executable,
                    "--no-daemon",
                    "--init-script",
                    Objects.requireNonNull(gradleInitScript, "gradleInitScript").toAbsolutePath().normalize().toString(),
                    "rewriteRun"
            );
        };
    }

    String buildRecipeYaml(List<Path> relativeTargets) {
        String filePattern = relativeTargets.stream()
                .map(this::validateFilePattern)
                .reduce((left, right) -> left + ";" + right)
                .orElseThrow(() -> new IllegalArgumentException("At least one OpenRewrite target is required."));
        return """
                ---
                type: specs.openrewrite.org/v1beta/recipe
                name: %s
                displayName: JAIPilot targeted Java cleanup
                description: Applies OpenRewrite's deterministic cleanup only to JAIPilot's selected production files.
                preconditions:
                  - org.openrewrite.FindSourceFiles:
                      filePattern: '%s'
                recipeList:
                  - org.openrewrite.staticanalysis.CodeCleanup
                  - org.openrewrite.staticanalysis.CommonStaticAnalysis
                """.formatted(TARGETED_RECIPE, yamlSingleQuoted(filePattern));
    }

    String buildGradleInitScript(Path recipeConfig) {
        return """
                initscript {
                    repositories {
                        maven { url = uri('https://plugins.gradle.org/m2') }
                    }
                    dependencies {
                        classpath('org.openrewrite:plugin:%s')
                    }
                }

                rootProject {
                    plugins.apply(org.openrewrite.gradle.RewritePlugin)
                    dependencies {
                        rewrite('%s')
                    }
                    rewrite {
                        configFile = file('%s')
                        activeRecipe('%s')
                        setExportDatatables(false)
                    }
                    afterEvaluate {
                        if (repositories.isEmpty()) {
                            repositories {
                                mavenCentral()
                            }
                        }
                    }
                }
                """.formatted(
                GRADLE_PLUGIN_VERSION,
                STATIC_ANALYSIS_ARTIFACT,
                groovySingleQuoted(recipeConfig.toAbsolutePath().normalize().toString()),
                TARGETED_RECIPE
        );
    }

    private List<Path> normalizeTargets(Path projectRoot, List<Path> targetFiles) {
        Objects.requireNonNull(targetFiles, "targetFiles");
        LinkedHashSet<Path> relativeTargets = new LinkedHashSet<>();
        for (Path targetFile : targetFiles) {
            Path normalizedTarget = Objects.requireNonNull(targetFile, "targetFile").toAbsolutePath().normalize();
            if (!normalizedTarget.startsWith(projectRoot)
                    || !Files.isRegularFile(normalizedTarget)
                    || Files.isSymbolicLink(normalizedTarget)) {
                throw new IllegalArgumentException(
                        "OpenRewrite target is not a Java production file under the project: " + normalizedTarget
                );
            }
            Path relativeTarget = projectRoot.relativize(normalizedTarget).normalize();
            String normalizedRelativeTarget = "/" + normalize(relativeTarget);
            if (!normalizedRelativeTarget.contains("/src/main/java/")
                    || !normalizedRelativeTarget.endsWith(".java")) {
                throw new IllegalArgumentException(
                        "OpenRewrite target is not a Java production file under the project: " + normalizedTarget
                );
            }
            relativeTargets.add(relativeTarget);
        }
        if (relativeTargets.isEmpty()) {
            throw new IllegalArgumentException("At least one OpenRewrite target is required.");
        }
        return List.copyOf(relativeTargets);
    }

    private String validateFilePattern(Path path) {
        for (Path segment : path) {
            String value = segment.toString();
            if (value.chars()
                    .anyMatch(character -> UNSUPPORTED_FILE_PATTERN_CHARACTERS.indexOf(character) >= 0)) {
                throw new IllegalArgumentException("OpenRewrite target path contains an unsupported character: " + path);
            }
        }
        return normalize(path);
    }

    private void validateResult(ProcessExecutor.ExecutionResult result) {
        if (result.timedOut()) {
            throw new IllegalStateException(
                    "OpenRewrite cleanup timed out after " + timeout.toMinutes()
                            + " minutes; the isolated candidate was discarded."
            );
        }
        if (result.exitCode() != 0) {
            String details = System.lineSeparator() + tail(result.output());
            throw new IllegalStateException(
                    "OpenRewrite cleanup failed with exit code " + result.exitCode()
                            + "; the isolated candidate was discarded." + details
            );
        }
    }

    private Map<String, String> cacheEnvironment(
            Path projectRoot,
            JavaProjectService.BuildTool buildTool
    ) {
        if (buildTool == JavaProjectService.BuildTool.GRADLE) {
            return Map.of();
        }
        Map<String, String> buildEnvironment = CoverageRefreshService.buildToolCacheEnvironment(
                projectRoot,
                System.getenv()
        );
        String mavenOpts = buildEnvironment.get("MAVEN_OPTS");
        return mavenOpts == null ? Map.of() : Map.of("MAVEN_OPTS", mavenOpts);
    }

    private String yamlSingleQuoted(String value) {
        return value.replace("'", "''");
    }

    private String groovySingleQuoted(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    private String tail(String output) {
        List<String> lines = output == null ? List.of() : output.lines()
                .map(String::stripTrailing)
                .filter(line -> !line.isBlank())
                .toList();
        int start = Math.max(0, lines.size() - 40);
        return String.join(System.lineSeparator(), lines.subList(start, lines.size()));
    }

    private void deleteTemporaryFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The recipe files contain no credentials or project data and live in the OS temp directory.
        }
    }

    private static RewriteExecutor defaultExecutor(ProcessExecutor processExecutor) {
        return (command, workingDirectory, timeout, showLogs, logWriter, progressListener, environment) ->
                processExecutor.execute(
                        command,
                        workingDirectory,
                        timeout,
                        showLogs,
                        logWriter,
                        null,
                        progressListener,
                        ProcessExecutor.OutputListener.noOp(),
                        environment,
                        MAX_CAPTURED_OUTPUT_CHARACTERS
                );
    }

    @FunctionalInterface
    interface RewriteExecutor {
        ProcessExecutor.ExecutionResult execute(
                List<String> command,
                Path workingDirectory,
                Duration timeout,
                boolean showLogs,
                PrintWriter logWriter,
                ProcessExecutor.ProgressListener progressListener,
                Map<String, String> environment
        ) throws IOException, InterruptedException;
    }

    public record RewriteResult(
            List<String> command,
            Duration elapsed
    ) {
    }
}
