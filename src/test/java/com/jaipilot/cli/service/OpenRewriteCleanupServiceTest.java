package com.jaipilot.cli.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jaipilot.cli.ui.TerminalUi;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OpenRewriteCleanupServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void mavenCleanupUsesPinnedRecipeAndExactTargetPreconditionWithoutEditingBuild() throws Exception {
        Path target = createProductionFile("pom.xml", "<project/>\n");
        Path quotedTarget = createProductionFile("pom.xml", "<project/>\n", "Customer's.java");
        AtomicReference<String> recipeYaml = new AtomicReference<>();
        AtomicReference<Path> recipePath = new AtomicReference<>();
        AtomicReference<ProcessExecutor.ProgressListener> progressListener = new AtomicReference<>();
        AtomicReference<Map<String, String>> environmentRef = new AtomicReference<>();
        OpenRewriteCleanupService service = service((command, root, timeout, logs, writer, progress, environment) -> {
            Path config = pathFromOption(command, "-Drewrite.configLocation=");
            recipePath.set(config);
            recipeYaml.set(Files.readString(config));
            progressListener.set(progress);
            environmentRef.set(environment);
            assertEquals(tempDir.toAbsolutePath().normalize(), root);
            assertEquals(Duration.ofMinutes(3), timeout);
            return successful(command);
        });

        OpenRewriteCleanupService.RewriteResult result = service.clean(
                tempDir,
                List.of(target, target, quotedTarget),
                ui(),
                false,
                writer()
        );

        assertEquals("mvn", result.command().get(0));
        assertTrue(result.command().contains("-ntp"));
        assertTrue(result.command().contains("test-compile"));
        assertTrue(result.command().contains("-DskipMavenParsing=true"));
        assertTrue(result.command().contains(
                "org.openrewrite.maven:rewrite-maven-plugin:"
                        + OpenRewriteCleanupService.MAVEN_PLUGIN_VERSION + ":runNoFork"
        ));
        assertTrue(result.command().contains(
                "-Drewrite.recipeArtifactCoordinates=org.openrewrite.recipe:rewrite-static-analysis:"
                        + OpenRewriteCleanupService.STATIC_ANALYSIS_VERSION
        ));
        assertTrue(recipeYaml.get().contains("name: " + OpenRewriteCleanupService.TARGETED_RECIPE));
        assertTrue(recipeYaml.get().contains(
                "filePattern: 'src/main/java/com/example/OrderService.java;"
                        + "src/main/java/com/example/Customer''s.java'"
        ));
        assertTrue(recipeYaml.get().contains("org.openrewrite.staticanalysis.CodeCleanup"));
        assertTrue(recipeYaml.get().contains("org.openrewrite.staticanalysis.CommonStaticAnalysis"));
        assertTrue(progressListener.get() instanceof TerminalUi.Spinner);
        Map<String, String> buildEnvironment = CoverageRefreshService.buildToolCacheEnvironment(
                tempDir,
                System.getenv()
        );
        String mavenOpts = buildEnvironment.get("MAVEN_OPTS");
        assertEquals(mavenOpts == null ? Map.of() : Map.of("MAVEN_OPTS", mavenOpts), environmentRef.get());
        assertEquals(Duration.ofMillis(20), result.elapsed());
        assertEquals("<project/>\n", Files.readString(tempDir.resolve("pom.xml")));
        assertFalse(Files.exists(recipePath.get()));
    }

    @Test
    void gradleCleanupUsesPinnedInitScriptAndSharedGradleCache() throws Exception {
        Path target = createProductionFile("build.gradle", "plugins { id 'java' }\n");
        AtomicReference<String> initScript = new AtomicReference<>();
        AtomicReference<Path> initPath = new AtomicReference<>();
        AtomicReference<Map<String, String>> environment = new AtomicReference<>();
        OpenRewriteCleanupService service = service((command, root, timeout, logs, writer, progress, env) -> {
            int initIndex = command.indexOf("--init-script") + 1;
            Path path = Path.of(command.get(initIndex));
            initPath.set(path);
            initScript.set(Files.readString(path));
            environment.set(env);
            return successful(command);
        });

        OpenRewriteCleanupService.RewriteResult result = service.clean(
                tempDir,
                List.of(target),
                ui(),
                false,
                writer()
        );

        assertEquals(List.of("gradle", "--no-daemon", "--init-script"), result.command().subList(0, 3));
        assertEquals("rewriteRun", result.command().get(result.command().size() - 1));
        assertTrue(initScript.get().contains(
                "org.openrewrite:plugin:" + OpenRewriteCleanupService.GRADLE_PLUGIN_VERSION
        ));
        assertTrue(initScript.get().contains(
                "org.openrewrite.recipe:rewrite-static-analysis:" + OpenRewriteCleanupService.STATIC_ANALYSIS_VERSION
        ));
        assertTrue(initScript.get().contains("activeRecipe('" + OpenRewriteCleanupService.TARGETED_RECIPE + "')"));
        assertEquals(Map.of(), environment.get());
        assertFalse(Files.exists(initPath.get()));
    }

    @Test
    void failingRewriteReportsTheUsefulOutputTail() throws Exception {
        Path target = createProductionFile("pom.xml", "<project/>\n");
        String output = IntStream.rangeClosed(1, 42)
                .mapToObj(index -> "entry-%02d  ".formatted(index))
                .collect(Collectors.joining("\n", "", "\n\n"));
        OpenRewriteCleanupService service = service((command, root, timeout, logs, writer, progress, environment) ->
                new ProcessExecutor.ExecutionResult(
                        command,
                        7,
                        false,
                        output,
                        Duration.ofMillis(20)
                ));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.clean(tempDir, List.of(target), ui(), false, writer())
        );

        assertTrue(failure.getMessage().contains("exit code 7"));
        assertFalse(failure.getMessage().contains("entry-02" + System.lineSeparator()));
        assertTrue(failure.getMessage().contains("entry-03" + System.lineSeparator()));
        assertTrue(failure.getMessage().endsWith("entry-42"));
        assertTrue(failure.getMessage().contains("isolated candidate was discarded"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Order;Service.java",
            "Order\nService.java",
            "Order\rService.java",
            "Order*Service.java",
            "Order?Service.java",
            "Order[Service].java",
            "Order{One,Two}Service.java"
    })
    void rejectsAmbiguousTargetPatternBeforeStartingRewrite(String sourceName) throws Exception {
        Path target = createProductionFile("pom.xml", "<project/>\n", sourceName);
        List<List<String>> commands = new ArrayList<>();
        OpenRewriteCleanupService service = service((command, root, timeout, logs, writer, progress, environment) -> {
            commands.add(command);
            return successful(command);
        });

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.clean(tempDir, List.of(target), ui(), false, writer())
        );

        assertTrue(failure.getMessage().contains("unsupported character"));
        assertTrue(commands.isEmpty());
    }

    @Test
    void rejectsTargetWhenOnlyProjectRootContainsProductionSourcePath() throws Exception {
        Path projectRoot = tempDir.resolve("src/main/java/project");
        writeFile(projectRoot.resolve("pom.xml"), "<project/>\n");
        Path target = writeFile(
                projectRoot.resolve("com/example/OrderService.java"),
                "package com.example; class OrderService {}\n"
        );
        AtomicInteger executions = new AtomicInteger();
        OpenRewriteCleanupService service = service((command, root, timeout, logs, writer, progress, environment) -> {
            executions.incrementAndGet();
            return successful(command);
        });

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.clean(projectRoot, List.of(target), ui(), false, writer())
        );

        assertTrue(failure.getMessage().contains("not a Java production file"));
        assertEquals(0, executions.get());
    }

    @Test
    void rejectsInvalidTargetsBeforeStartingRewrite() throws Exception {
        Path projectRoot = tempDir.resolve("project");
        Files.createDirectories(projectRoot);
        Path validTarget = writeFile(
                projectRoot.resolve("src/main/java/com/example/OrderService.java"),
                "package com.example; class OrderService {}\n"
        );
        Path outsideTarget = writeFile(
                tempDir.resolve("outside/src/main/java/com/example/Outside.java"),
                "package com.example; class Outside {}\n"
        );
        Path testTarget = writeFile(
                projectRoot.resolve("src/test/java/com/example/OrderServiceTest.java"),
                "package com.example; class OrderServiceTest {}\n"
        );
        Path nonJavaTarget = writeFile(
                projectRoot.resolve("src/main/java/com/example/OrderService.txt"),
                "not Java\n"
        );
        Path symbolicTarget = projectRoot.resolve("src/main/java/com/example/OrderServiceLink.java");
        Files.createSymbolicLink(symbolicTarget, validTarget);
        Path missingTarget = projectRoot.resolve("src/main/java/com/example/Missing.java");
        AtomicInteger executions = new AtomicInteger();
        OpenRewriteCleanupService service = service((command, root, timeout, logs, writer, progress, environment) -> {
            executions.incrementAndGet();
            return successful(command);
        });

        for (Path invalidTarget : List.of(
                outsideTarget,
                missingTarget,
                symbolicTarget,
                testTarget,
                nonJavaTarget
        )) {
            IllegalArgumentException failure = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.clean(projectRoot, List.of(invalidTarget), ui(), false, writer())
            );
            assertTrue(failure.getMessage().contains("not a Java production file"));
        }
        assertThrows(NullPointerException.class, () -> service.clean(projectRoot, null, ui(), false, writer()));
        assertThrows(
                NullPointerException.class,
                () -> service.clean(projectRoot, Collections.singletonList(null), ui(), false, writer())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.clean(projectRoot, List.of(), ui(), false, writer())
        );
        assertEquals(0, executions.get());
    }

    @Test
    void verboseFailureLogsTheCommandAndPointsToStreamedOutput() throws Exception {
        Path target = createProductionFile("pom.xml", "<project/>\n");
        StringWriter output = new StringWriter();
        PrintWriter logWriter = new PrintWriter(output, true);
        TerminalUi ui = new TerminalUi(logWriter);
        AtomicReference<ProcessExecutor.ProgressListener> progressListener = new AtomicReference<>();
        OpenRewriteCleanupService service = service((command, root, timeout, logs, writer, progress, environment) -> {
            assertTrue(logs);
            assertSame(logWriter, writer);
            progressListener.set(progress);
            return new ProcessExecutor.ExecutionResult(
                    command,
                    9,
                    false,
                    "already streamed detail",
                    Duration.ofMillis(20)
            );
        });

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.clean(tempDir, List.of(target), ui, true, logWriter)
        );

        assertTrue(output.toString().contains("[openrewrite] mvn -B -ntp test-compile"));
        assertSame(ProcessExecutor.ProgressListener.noOp(), progressListener.get());
        assertTrue(failure.getMessage().contains("Build output was streamed above"));
        assertFalse(failure.getMessage().contains("already streamed detail"));
    }

    @Test
    void timedOutRewriteReportsConfiguredTimeout() throws Exception {
        Path target = createProductionFile("pom.xml", "<project/>\n");
        OpenRewriteCleanupService service = service((command, root, timeout, logs, writer, progress, environment) ->
                new ProcessExecutor.ExecutionResult(command, 0, true, "", Duration.ofMinutes(3)));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.clean(tempDir, List.of(target), ui(), false, writer())
        );

        assertTrue(failure.getMessage().contains("timed out after 3 minutes"));
        assertTrue(failure.getMessage().contains("isolated candidate was discarded"));
    }

    @Test
    void failureWithoutCapturedOutputDoesNotAppendLiteralNull() throws Exception {
        Path target = createProductionFile("pom.xml", "<project/>\n");
        OpenRewriteCleanupService service = service((command, root, timeout, logs, writer, progress, environment) ->
                new ProcessExecutor.ExecutionResult(command, 5, false, null, Duration.ZERO));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.clean(tempDir, List.of(target), ui(), false, writer())
        );

        assertTrue(failure.getMessage().contains("exit code 5"));
        assertFalse(failure.getMessage().contains("null"));
    }

    @Test
    void interruptedRewriteRestoresInterruptAndDeletesRecipe() throws Exception {
        Path target = createProductionFile("pom.xml", "<project/>\n");
        AtomicReference<Path> recipePath = new AtomicReference<>();
        InterruptedException interruption = new InterruptedException("stop");
        OpenRewriteCleanupService service = service((command, root, timeout, logs, writer, progress, environment) -> {
            recipePath.set(pathFromOption(command, "-Drewrite.configLocation="));
            throw interruption;
        });
        Thread.interrupted();

        try {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> service.clean(tempDir, List.of(target), ui(), false, writer())
            );

            assertSame(interruption, failure.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
            assertFalse(Files.exists(recipePath.get()));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void executorIoFailureIsWrappedAndDeletesRecipe() throws Exception {
        Path target = createProductionFile("pom.xml", "<project/>\n");
        AtomicReference<Path> recipePath = new AtomicReference<>();
        IOException ioFailure = new IOException("fixture failure");
        OpenRewriteCleanupService service = service((command, root, timeout, logs, writer, progress, environment) -> {
            recipePath.set(pathFromOption(command, "-Drewrite.configLocation="));
            throw ioFailure;
        });

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.clean(tempDir, List.of(target), ui(), false, writer())
        );

        assertTrue(failure.getMessage().contains("isolated workspace"));
        assertSame(ioFailure, failure.getCause());
        assertFalse(Files.exists(recipePath.get()));
    }

    @Test
    void publicConstructorExecutesThroughLocalMavenWrapper() throws Exception {
        Path target = createProductionFile("pom.xml", "<project/>\n");
        Path wrapperProperties = tempDir.resolve(".mvn/wrapper/maven-wrapper.properties");
        writeFile(wrapperProperties, "distributionUrl=https://repo.maven.apache.org/maven2\n");
        Path wrapper = writeFile(tempDir.resolve("mvnw"), """
                #!/bin/sh
                set -eu
                printf '%s\n' "$@" > wrapper-args.txt
                printf 'local rewrite fixture completed\n'
                """);
        assertTrue(wrapper.toFile().setExecutable(true, false));
        OpenRewriteCleanupService service = new OpenRewriteCleanupService(projectService());

        OpenRewriteCleanupService.RewriteResult result = service.clean(
                tempDir,
                List.of(target),
                ui(),
                false,
                writer()
        );

        assertEquals("./mvnw", result.command().get(0));
        assertEquals(
                result.command().subList(1, result.command().size()),
                Files.readAllLines(tempDir.resolve("wrapper-args.txt"))
        );
        assertFalse(result.elapsed().isNegative());
    }

    @Test
    void recipeBuilderRejectsEmptyTargets() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service(this::successful).buildRecipeYaml(List.of())
        );

        assertTrue(failure.getMessage().contains("At least one OpenRewrite target"));
    }

    @Test
    void gradleScriptEscapesSingleQuotesAndBackslashes() {
        String script = service(this::successful).buildGradleInitScript(
                Path.of("/tmp/rewrite\\candidate's config.yml")
        );

        assertTrue(script.contains("rewrite\\\\candidate\\'s config.yml"));
    }

    @Test
    void gradleCommandRequiresAnInitScript() throws Exception {
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }\n");
        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> service(this::successful).buildCommand(
                        tempDir,
                        JavaProjectService.BuildTool.GRADLE,
                        tempDir.resolve("recipe.yml"),
                        null
                )
        );

        assertEquals("gradleInitScript", failure.getMessage());
    }

    @Test
    void constructorRejectsNullDependencies() {
        OpenRewriteCleanupService.RewriteExecutor executor = this::successful;

        assertEquals(
                "projectService",
                assertThrows(
                        NullPointerException.class,
                        () -> new OpenRewriteCleanupService(null, executor, Duration.ofSeconds(1))
                ).getMessage()
        );
        assertEquals(
                "rewriteExecutor",
                assertThrows(
                        NullPointerException.class,
                        () -> new OpenRewriteCleanupService(projectService(), null, Duration.ofSeconds(1))
                ).getMessage()
        );
        assertEquals(
                "timeout",
                assertThrows(
                        NullPointerException.class,
                        () -> new OpenRewriteCleanupService(projectService(), executor, null)
                ).getMessage()
        );
    }

    @Test
    void temporaryCleanupFailureDoesNotMaskSuccessfulRewrite() throws Exception {
        Path target = createProductionFile("pom.xml", "<project/>\n");
        AtomicReference<Path> recipePath = new AtomicReference<>();
        OpenRewriteCleanupService service = service((command, root, timeout, logs, writer, progress, environment) -> {
            Path path = pathFromOption(command, "-Drewrite.configLocation=");
            recipePath.set(path);
            Files.delete(path);
            Files.createDirectory(path);
            Files.writeString(path.resolve("retained-fixture"), "fixture\n");
            return successful(command);
        });

        try {
            OpenRewriteCleanupService.RewriteResult result = service.clean(
                    tempDir,
                    List.of(target),
                    ui(),
                    false,
                    writer()
            );

            assertEquals(Duration.ofMillis(20), result.elapsed());
            assertTrue(Files.isDirectory(recipePath.get()));
        } finally {
            if (recipePath.get() != null) {
                Files.deleteIfExists(recipePath.get().resolve("retained-fixture"));
                Files.deleteIfExists(recipePath.get());
            }
        }
    }

    private OpenRewriteCleanupService service(OpenRewriteCleanupService.RewriteExecutor executor) {
        return new OpenRewriteCleanupService(projectService(), executor, Duration.ofMinutes(3));
    }

    private JavaProjectService projectService() {
        return new JavaProjectService(
                new ProjectFileService(),
                new CoverageReportService()
        );
    }

    private Path createProductionFile(String buildFile, String buildContents) throws Exception {
        return createProductionFile(buildFile, buildContents, "OrderService.java");
    }

    private Path createProductionFile(String buildFile, String buildContents, String sourceName) throws Exception {
        Files.writeString(tempDir.resolve(buildFile), buildContents);
        Path target = tempDir.resolve("src/main/java/com/example").resolve(sourceName);
        Files.createDirectories(target.getParent());
        Files.writeString(target, "package com.example; class OrderService {}\n");
        return target;
    }

    private Path writeFile(Path path, String contents) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
        return path;
    }

    private Path pathFromOption(List<String> command, String prefix) {
        return Path.of(command.stream()
                .filter(value -> value.startsWith(prefix))
                .findFirst()
                .orElseThrow()
                .substring(prefix.length()));
    }

    private ProcessExecutor.ExecutionResult successful(List<String> command) {
        return new ProcessExecutor.ExecutionResult(
                command,
                0,
                false,
                "",
                Duration.ofMillis(20)
        );
    }

    private ProcessExecutor.ExecutionResult successful(
            List<String> command,
            Path root,
            Duration timeout,
            boolean logs,
            PrintWriter writer,
            ProcessExecutor.ProgressListener progress,
            Map<String, String> environment
    ) {
        return successful(command);
    }

    private TerminalUi ui() {
        return new TerminalUi(writer());
    }

    private PrintWriter writer() {
        return new PrintWriter(new StringWriter(), true);
    }
}
