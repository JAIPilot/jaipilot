package com.jaipilot.toolkit.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class JavaBuildVerificationService {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(30);

    private final JavaProjectService projectService;
    private final ProcessExecutor processExecutor;
    private final Duration timeout;

    public JavaBuildVerificationService(JavaProjectService projectService) {
        this(projectService, new ProcessExecutor(), DEFAULT_TIMEOUT);
    }

    JavaBuildVerificationService(
            JavaProjectService projectService,
            ProcessExecutor processExecutor,
            Duration timeout
    ) {
        this.projectService = Objects.requireNonNull(projectService, "projectService");
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    public VerificationResult verify(Path projectRoot) {
        List<String> command = buildCommand(projectRoot);
        try {
            ProcessExecutor.ExecutionResult result = processExecutor.execute(
                    command,
                    projectRoot,
                    timeout,
                    false,
                    new PrintWriter(System.err, true),
                    null,
                    ProcessExecutor.ProgressListener.noOp(),
                    ProcessExecutor.OutputListener.noOp(),
                    CoverageRefreshService.buildToolCacheEnvironment(projectRoot, System.getenv())
            );
            if (result.timedOut()) {
                throw new IllegalStateException("Clean full-suite verification timed out after "
                        + timeout.toMinutes() + " minutes.");
            }
            if (result.exitCode() != 0) {
                String details = System.lineSeparator() + tail(result.output());
                throw new IllegalStateException("Clean full-suite verification failed with exit code "
                        + result.exitCode() + "." + details);
            }
            return new VerificationResult(List.copyOf(command), result.elapsed());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Clean full-suite verification was interrupted.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to run clean full-suite verification.", exception);
        }
    }

    List<String> buildCommand(Path projectRoot) {
        String executable = projectService.resolveBuildExecutable(projectRoot);
        return switch (projectService.detectBuildTool(projectRoot)) {
            case MAVEN -> List.of(executable, "-B", "-Dmaven.build.cache.enabled=false", "clean", "verify");
            case GRADLE -> List.of(executable, "--no-daemon", "--no-build-cache", "--rerun-tasks", "clean", "build");
        };
    }

    private String tail(String output) {
        List<String> lines = output == null ? List.of() : output.lines()
                .map(String::stripTrailing)
                .filter(line -> !line.isBlank())
                .toList();
        int start = Math.max(0, lines.size() - 40);
        return String.join(System.lineSeparator(), lines.subList(start, lines.size()));
    }

    public record VerificationResult(
            List<String> command,
            Duration elapsed
    ) {
    }
}
