package com.jaipilot.toolkit.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CoverageRefreshService {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(30);
    private static final String MAVEN_OPTS = "MAVEN_OPTS";
    private static final String MAVEN_TRACKING_PROPERTY = "aether.enhancedLocalRepository.trackingFilename";
    private static final String MAVEN_TRACKING_OPTION = "-D" + MAVEN_TRACKING_PROPERTY + "=ignore";
    private static final String GRADLE_USER_HOME = "GRADLE_USER_HOME";
    private static final String XDG_CACHE_HOME = "XDG_CACHE_HOME";
    private static final String TARGET_COVERAGE_TASK = "jaipilotTargetCoverage";

    private final JavaProjectService projectService;
    private final CoverageReportService coverageReportService;
    private final ProcessExecutor processExecutor;
    private final Duration timeout;
    private final Map<String, String> currentEnvironment;

    public CoverageRefreshService(
            JavaProjectService projectService,
            CoverageReportService coverageReportService
    ) {
        this(projectService, coverageReportService, new ProcessExecutor(), DEFAULT_TIMEOUT, System.getenv());
    }

    CoverageRefreshService(
            JavaProjectService projectService,
            CoverageReportService coverageReportService,
            ProcessExecutor processExecutor,
            Duration timeout
    ) {
        this(projectService, coverageReportService, processExecutor, timeout, System.getenv());
    }

    CoverageRefreshService(
            JavaProjectService projectService,
            CoverageReportService coverageReportService,
            ProcessExecutor processExecutor,
            Duration timeout,
            Map<String, String> currentEnvironment
    ) {
        this.projectService = Objects.requireNonNull(projectService, "projectService");
        this.coverageReportService = Objects.requireNonNull(coverageReportService, "coverageReportService");
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.currentEnvironment = Map.copyOf(Objects.requireNonNull(currentEnvironment, "currentEnvironment"));
    }

    public CoverageReportService.CoverageSnapshot refresh(Path projectRoot) {
        return refresh(projectRoot, List.of());
    }

    public CoverageReportService.CoverageSnapshot refresh(
            Path projectRoot,
            List<JavaProjectService.JavaClassDescriptor> targets
    ) {
        Objects.requireNonNull(projectRoot, "projectRoot");

        try (ProjectRefreshLock ignored = acquireProjectLock(projectRoot)) {
            return refreshWithProjectLock(projectRoot, normalizeTargets(projectRoot, targets));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to manage the project coverage-refresh lock.", exception);
        }
    }

    public Optional<CoverageReportService.CoverageSnapshot> readCachedSnapshot(Path projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        try (ProjectRefreshLock ignored = acquireProjectLock(projectRoot)) {
            return coverageReportService.readProjectSnapshot(projectRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to manage the project coverage-refresh lock.", exception);
        }
    }

    CoverageReportService.CoverageSnapshot refreshWithProjectLock(Path projectRoot) {
        return refreshWithProjectLock(projectRoot, List.of());
    }

    CoverageReportService.CoverageSnapshot refreshWithProjectLock(
            Path projectRoot,
            List<CoverageTarget> targets
    ) {
        try {
            invalidateCoverageReports(projectRoot);
            return refreshCoverage(projectRoot, targets);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            discardFailedRefresh(projectRoot, exception);
            throw new IllegalStateException("Clean full-suite coverage refresh was interrupted.", exception);
        } catch (IOException | RuntimeException exception) {
            discardFailedRefresh(projectRoot, exception);
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to run the clean full-suite coverage refresh.", exception);
        }
    }

    private CoverageReportService.CoverageSnapshot refreshCoverage(
            Path projectRoot,
            List<CoverageTarget> targets
    ) throws IOException, InterruptedException {
        JavaProjectService.BuildTool buildTool = projectService.detectBuildTool(projectRoot);
        String executable = projectService.resolveBuildExecutable(projectRoot);
        if (buildTool == JavaProjectService.BuildTool.GRADLE
                && !targets.isEmpty()
                && usesModuleCoverageConvention(projectRoot)) {
            return refreshGradleModules(projectRoot, executable, targets);
        }
        ProcessExecutor.ExecutionResult result = executeCoverage(
                projectRoot,
                coverageCommand(projectRoot, buildTool, executable)
        );
        requireSuccessfulCoverage(result);
        return coverageReportService.readProjectSnapshot(projectRoot)
                .orElseThrow(() -> new IllegalStateException(
                        "The clean full suite passed but did not generate a readable JaCoCo XML report."
                ));
    }

    private List<String> coverageCommand(
            Path projectRoot,
            JavaProjectService.BuildTool buildTool,
            String executable
    ) {
        return switch (buildTool) {
            case MAVEN -> List.of(executable, "-B", "-Dmaven.build.cache.enabled=false", "clean", "verify");
            case GRADLE -> projectService.usesGradleCoverageAggregation(projectRoot)
                    ? List.of(executable, "--no-daemon", "--no-build-cache", "--rerun-tasks",
                            "clean", "build", "testCodeCoverageReport")
                    : List.of(executable, "--no-daemon", "--no-build-cache", "--rerun-tasks",
                            "clean", "build", "jacocoTestReport");
        };
    }

    private ProcessExecutor.ExecutionResult executeCoverage(Path projectRoot, List<String> command)
            throws IOException, InterruptedException {
        return processExecutor.execute(
                command,
                projectRoot,
                timeout,
                false,
                new PrintWriter(System.err, true),
                null,
                ProcessExecutor.ProgressListener.noOp(),
                ProcessExecutor.OutputListener.noOp(),
                buildToolCacheEnvironment(projectRoot, currentEnvironment)
        );
    }

    private void requireSuccessfulCoverage(ProcessExecutor.ExecutionResult result) {
        if (result.timedOut()) {
            throw new IllegalStateException("Clean full-suite coverage refresh timed out after "
                    + timeout.toMinutes() + " minutes.");
        }
        if (result.exitCode() != 0) {
            throw new IllegalStateException("Clean full-suite coverage refresh failed:"
                    + System.lineSeparator() + tailBuildOutput(result.output()));
        }
    }

    ProjectRefreshLock acquireProjectLock(Path projectRoot) throws IOException {
        Path canonicalRoot = projectRoot.toRealPath();
        String lockName = UUID.nameUUIDFromBytes(
                canonicalRoot.toString().getBytes(StandardCharsets.UTF_8)
        ) + ".lock";
        Path lockDirectory = Path.of(System.getProperty("java.io.tmpdir"), "jaipilot", "coverage-locks");
        Files.createDirectories(lockDirectory);
        FileChannel channel = FileChannel.open(
                lockDirectory.resolve(lockName),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
        );
        try {
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException exception) {
                lock = null;
            }
            if (lock == null) {
                throw new RefreshInProgressException(
                        "Another JAIPilot coverage refresh is already running for this project. Wait for it to finish and retry."
                );
            }
            return new ProjectRefreshLock(channel, lock);
        } catch (IOException | RuntimeException exception) {
            try {
                channel.close();
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    void invalidateCoverageReports(Path projectRoot) {
        coverageReportService.findCoverageReports(projectRoot).forEach(reportPath -> {
            try {
                Files.deleteIfExists(reportPath);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to remove stale JaCoCo report " + reportPath, exception);
            }
        });
    }

    static Map<String, String> buildToolCacheEnvironment(
            Path workingDirectory,
            Map<String, String> currentEnvironment
    ) {
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(currentEnvironment, "currentEnvironment");
        Map<String, String> environment = new LinkedHashMap<>();
        String mavenOpts = currentEnvironment.getOrDefault(MAVEN_OPTS, "");
        if (!containsMavenTrackingOverride(mavenOpts)) {
            environment.put(MAVEN_OPTS, appendJvmOption(mavenOpts, MAVEN_TRACKING_OPTION));
        }
        if (!currentEnvironment.containsKey(GRADLE_USER_HOME)) {
            environment.put(GRADLE_USER_HOME, gradleCacheHome(currentEnvironment).toString());
        }
        return environment;
    }

    private static Path gradleCacheHome(Map<String, String> environment) {
        String configured = environment.get(XDG_CACHE_HOME);
        Path cacheRoot;
        if (configured != null && !configured.isBlank() && Path.of(configured).isAbsolute()) {
            cacheRoot = Path.of(configured);
        } else {
            String ownerHome = System.getProperty("user.home", "");
            cacheRoot = ownerHome.isBlank()
                    ? Path.of(System.getProperty("java.io.tmpdir"), "jaipilot-cache")
                    : Path.of(ownerHome, ".cache");
        }
        return cacheRoot.toAbsolutePath().normalize().resolve("jaipilot/gradle");
    }

    private List<CoverageTarget> normalizeTargets(
            Path projectRoot,
            List<JavaProjectService.JavaClassDescriptor> descriptors
    ) {
        Path root = projectRoot.toAbsolutePath().normalize();
        return descriptors.stream().map(descriptor -> {
            Path module = descriptor.moduleRoot().toAbsolutePath().normalize();
            if (!module.startsWith(root) || !Files.isDirectory(module) || Files.isSymbolicLink(module)) {
                throw new IllegalArgumentException("Coverage target module is outside the project: " + module);
            }
            return new CoverageTarget(module, descriptor.fullyQualifiedName());
        }).distinct().sorted(Comparator.comparing(CoverageTarget::moduleRoot)
                .thenComparing(CoverageTarget::className)).toList();
    }

    private boolean usesModuleCoverageConvention(Path projectRoot) {
        for (String buildName : List.of("build.gradle", "build.gradle.kts")) {
            Path build = projectRoot.resolve(buildName);
            if (!Files.isRegularFile(build)) {
                continue;
            }
            try {
                String contents = Files.readString(build).toLowerCase(Locale.ROOT);
                if (contents.contains("reportcoverage") && contents.contains("enabletestcoverage")) {
                    return true;
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to inspect Gradle coverage configuration " + build, exception);
            }
        }
        return false;
    }

    private CoverageReportService.CoverageSnapshot refreshGradleModules(
            Path projectRoot,
            String executable,
            List<CoverageTarget> targets
    ) throws IOException, InterruptedException {
        Path initScript = Files.createTempFile("jaipilot-coverage-", ".gradle");
        try {
            Files.writeString(initScript, moduleCoverageInitScript(targets), StandardCharsets.UTF_8);
            List<String> command = new ArrayList<>(List.of(
                    executable,
                    "--no-daemon",
                    "--no-build-cache",
                    "--rerun-tasks",
                    "--init-script",
                    initScript.toAbsolutePath().normalize().toString(),
                    "-PenableTestCoverage=true",
                    "-Dorg.gradle.parallel=false",
                    "clean",
                    "build",
                    TARGET_COVERAGE_TASK
            ));
            ProcessExecutor.ExecutionResult result = processExecutor.execute(
                    command, projectRoot, timeout, false, new PrintWriter(System.err, true), null,
                    ProcessExecutor.ProgressListener.noOp(), ProcessExecutor.OutputListener.noOp(),
                    buildToolCacheEnvironment(projectRoot, currentEnvironment)
            );
            requireSuccessfulBuild(result);
            return coverageReportService.readTargetSnapshot(projectRoot, targetsByModule(targets));
        } finally {
            Files.deleteIfExists(initScript);
        }
    }

    String moduleCoverageInitScript(List<CoverageTarget> targets) {
        String directories = targets.stream().map(CoverageTarget::moduleRoot).distinct().sorted()
                .map(path -> "new File(" + groovyString(path.toString()) + ").canonicalFile")
                .collect(java.util.stream.Collectors.joining(", "));
        return """
                def jaipilotDirectories = [%s] as Set
                def jaipilotMatched = [] as Set
                def jaipilotTask = null
                gradle.projectsLoaded {
                    jaipilotTask = gradle.rootProject.tasks.register('%s')
                }
                gradle.afterProject { candidate, state ->
                    def directory = candidate.projectDir.canonicalFile
                    if (jaipilotDirectories.contains(directory)) {
                        def report = candidate.tasks.findByName('reportCoverage')
                        if (state.failure != null || report == null) {
                            throw new GradleException('Selected project has no usable reportCoverage task: ' + candidate.path)
                        }
                        jaipilotMatched.add(directory)
                        jaipilotTask.configure { dependsOn(report) }
                    }
                }
                gradle.projectsEvaluated {
                    def missing = jaipilotDirectories - jaipilotMatched
                    if (!missing.isEmpty()) throw new GradleException('No Gradle project owns: ' + missing)
                }
                """.formatted(directories, TARGET_COVERAGE_TASK);
    }

    private String groovyString(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private Map<Path, List<String>> targetsByModule(List<CoverageTarget> targets) {
        Map<Path, LinkedHashSet<String>> grouped = new LinkedHashMap<>();
        for (CoverageTarget target : targets) {
            grouped.computeIfAbsent(target.moduleRoot(), ignored -> new LinkedHashSet<>()).add(target.className());
        }
        Map<Path, List<String>> result = new LinkedHashMap<>();
        grouped.forEach((module, classes) -> result.put(module, List.copyOf(classes)));
        return Map.copyOf(result);
    }

    private void requireSuccessfulBuild(ProcessExecutor.ExecutionResult result) {
        if (result.timedOut()) {
            throw new IllegalStateException("Clean full-suite coverage refresh timed out after "
                    + timeout.toMinutes() + " minutes.");
        }
        if (result.exitCode() != 0) {
            throw new IllegalStateException("Clean full-suite coverage refresh failed:"
                    + System.lineSeparator() + tailBuildOutput(result.output()));
        }
    }

    private void discardFailedRefresh(Path projectRoot, Throwable failure) {
        try {
            invalidateCoverageReports(projectRoot);
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private String tailBuildOutput(String output) {
        List<String> lines = output == null ? List.of() : output.lines()
                .map(String::stripTrailing)
                .filter(line -> !line.isBlank())
                .toList();
        LinkedHashSet<String> summary = lines.stream()
                .filter(this::isFailureSignal)
                .limit(20)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        int start = Math.max(0, lines.size() - 40);
        summary.addAll(lines.subList(start, lines.size()));
        return String.join(System.lineSeparator(), summary);
    }

    private boolean isFailureSignal(String line) {
        String value = line.strip();
        return value.endsWith(" FAILED")
                || value.startsWith("FAILURE:")
                || value.startsWith("Execution failed for task")
                || value.matches("[0-9]+ tests completed,.*");
    }

    private static boolean containsMavenTrackingOverride(String value) {
        return value != null && value.contains(MAVEN_TRACKING_PROPERTY);
    }

    private static String appendJvmOption(String existingValue, String option) {
        if (existingValue == null || existingValue.isBlank()) {
            return option;
        }
        return existingValue.stripTrailing() + " " + option;
    }

    static final class ProjectRefreshLock implements AutoCloseable {

        private final FileChannel channel;
        private final FileLock lock;

        private ProjectRefreshLock(FileChannel channel, FileLock lock) {
            this.channel = channel;
            this.lock = lock;
        }

        @Override
        public void close() throws IOException {
            try {
                lock.release();
            } finally {
                channel.close();
            }
        }
    }

    public static final class RefreshInProgressException extends IllegalStateException {

        RefreshInProgressException(String message) {
            super(message);
        }
    }

    record CoverageTarget(Path moduleRoot, String className) {
    }
}
