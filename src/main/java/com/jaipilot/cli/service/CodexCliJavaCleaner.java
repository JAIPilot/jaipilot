package com.jaipilot.cli.service;

import com.jaipilot.cli.ui.TerminalUi;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class CodexCliJavaCleaner {

    private static final Duration CODEX_TIMEOUT = Duration.ofMinutes(45);

    private final ProjectFileService fileService;
    private final PromptTemplateService promptTemplateService;
    private final DeterministicCleaner deterministicCleaner;
    private final CleanupAgent cleanupAgent;
    private final BuildVerifier buildVerifier;

    public CodexCliJavaCleaner(ProjectFileService fileService, JavaProjectService projectService) {
        this(
                fileService,
                new PromptTemplateService(fileService),
                new OpenRewriteCleanupService(projectService)::clean,
                defaultAgent(new CodexCliRunner()),
                new JavaBuildVerificationService(projectService)::verify
        );
    }

    CodexCliJavaCleaner(
            ProjectFileService fileService,
            PromptTemplateService promptTemplateService,
            CleanupAgent cleanupAgent,
            BuildVerifier buildVerifier
    ) {
        this(
                fileService,
                promptTemplateService,
                (root, targets, ui, showLogs, logWriter) ->
                        new OpenRewriteCleanupService.RewriteResult(List.of(), Duration.ZERO),
                cleanupAgent,
                buildVerifier
        );
    }

    CodexCliJavaCleaner(
            ProjectFileService fileService,
            PromptTemplateService promptTemplateService,
            DeterministicCleaner deterministicCleaner,
            CleanupAgent cleanupAgent,
            BuildVerifier buildVerifier
    ) {
        this.fileService = Objects.requireNonNull(fileService, "fileService");
        this.promptTemplateService = Objects.requireNonNull(promptTemplateService, "promptTemplateService");
        this.deterministicCleaner = Objects.requireNonNull(deterministicCleaner, "deterministicCleaner");
        this.cleanupAgent = Objects.requireNonNull(cleanupAgent, "cleanupAgent");
        this.buildVerifier = Objects.requireNonNull(buildVerifier, "buildVerifier");
    }

    public CleanupResult clean(
            Path projectRoot,
            List<JavaProjectService.JavaClassDescriptor> targets,
            String model,
            boolean checkOnly,
            TerminalUi ui,
            boolean showLogs,
            PrintWriter logWriter
    ) throws Exception {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        List<Path> targetPaths = normalizeTargets(normalizedRoot, targets);
        if (targetPaths.isEmpty()) {
            throw new IllegalArgumentException("At least one Java production class is required.");
        }

        JavaBuildVerificationService.VerificationResult baseline = buildVerifier.verify(
                normalizedRoot,
                ui,
                showLogs,
                logWriter
        );
        Map<Path, ProjectFileService.FileFingerprint> projectBaseline = fileService.snapshotWorkspaceFiles(normalizedRoot);

        Path sandboxRoot = Files.createTempDirectory("jaipilot-clean-");
        try {
            fileService.copyProjectWorkspace(normalizedRoot, sandboxRoot);
            List<Path> sandboxTargets = targetPaths.stream()
                    .map(path -> sandboxRoot.resolve(normalizedRoot.relativize(path)).normalize())
                    .toList();
            Map<Path, ProjectFileService.FileFingerprint> sandboxBefore = fileService.snapshotWorkspaceFiles(sandboxRoot);
            OpenRewriteCleanupService.RewriteResult rewriteResult = deterministicCleaner.improve(
                    sandboxRoot,
                    sandboxTargets,
                    ui,
                    showLogs,
                    logWriter
            );
            Map<Path, ProjectFileService.FileFingerprint> sandboxAfterRewrite =
                    fileService.snapshotWorkspaceFiles(sandboxRoot);
            List<Path> rewriteChangedRelativePaths = changedRelativePaths(
                    sandboxRoot,
                    sandboxBefore,
                    sandboxAfterRewrite
            );
            validateScope(
                    sandboxRoot,
                    sandboxTargets,
                    sandboxBefore,
                    sandboxAfterRewrite,
                    rewriteChangedRelativePaths,
                    false,
                    "OpenRewrite"
            );
            CodexCliRunner.RunResult agentResult = cleanupAgent.improve(
                    sandboxRoot,
                    model,
                    promptTemplateService.buildCleanupPrompt(
                            sandboxRoot,
                            sandboxTargets,
                            rewriteChangedRelativePaths
                    ),
                    ui,
                    showLogs,
                    logWriter
            );
            Map<Path, ProjectFileService.FileFingerprint> sandboxAfter = fileService.snapshotWorkspaceFiles(sandboxRoot);
            List<Path> changedRelativePaths = changedRelativePaths(sandboxRoot, sandboxBefore, sandboxAfter);
            validateScope(
                    sandboxRoot,
                    sandboxTargets,
                    sandboxBefore,
                    sandboxAfter,
                    changedRelativePaths,
                    true,
                    "Cleanup"
            );

            if (changedRelativePaths.isEmpty()) {
                ensureProjectUnchanged(normalizedRoot, projectBaseline);
                return new CleanupResult(
                        List.of(),
                        false,
                        checkOnly,
                        agentResult.usage(),
                        baseline.elapsed(),
                        rewriteResult.elapsed(),
                        rewriteChangedRelativePaths,
                        null,
                        agentResult.elapsed()
                );
            }

            JavaBuildVerificationService.VerificationResult candidate = buildVerifier.verify(
                    sandboxRoot,
                    ui,
                    showLogs,
                    logWriter
            );
            Map<Path, ProjectFileService.FileFingerprint> verifiedSandbox =
                    fileService.snapshotWorkspaceFiles(sandboxRoot);
            List<Path> verificationDrift = changedRelativePaths(sandboxRoot, sandboxAfter, verifiedSandbox);
            if (!verificationDrift.isEmpty()) {
                throw new IllegalStateException(
                        "The candidate build changed files outside its build output; no files were merged: "
                                + verificationDrift
                );
            }
            ensureProjectUnchanged(normalizedRoot, projectBaseline);
            if (!checkOnly) {
                mergeCandidate(
                        normalizedRoot,
                        sandboxRoot,
                        projectBaseline,
                        verifiedSandbox,
                        changedRelativePaths
                );
            }
            return new CleanupResult(
                    changedRelativePaths,
                    !checkOnly,
                    checkOnly,
                    agentResult.usage(),
                    baseline.elapsed(),
                    rewriteResult.elapsed(),
                    rewriteChangedRelativePaths,
                    candidate.elapsed(),
                    agentResult.elapsed()
            );
        } finally {
            fileService.deleteRecursively(sandboxRoot);
        }
    }

    private static CleanupAgent defaultAgent(CodexCliRunner runner) {
        return (sandboxRoot, model, prompt, ui, showLogs, logWriter) -> {
            runner.ensureAvailable(sandboxRoot);
            return runner.run(
                    sandboxRoot,
                    model,
                    prompt,
                    ui,
                    showLogs,
                    true,
                    logWriter,
                    CODEX_TIMEOUT,
                    "codex improving Java code",
                    "Codex failed while improving Java code:"
            );
        };
    }

    private List<Path> normalizeTargets(
            Path projectRoot,
            List<JavaProjectService.JavaClassDescriptor> targets
    ) {
        LinkedHashSet<Path> normalized = new LinkedHashSet<>();
        for (JavaProjectService.JavaClassDescriptor target : targets) {
            Path path = target.cutPath().toAbsolutePath().normalize();
            if (!path.startsWith(projectRoot) || !isProductionJava(path) || Files.isSymbolicLink(path)) {
                throw new IllegalArgumentException("Cleanup target is not a Java production file under the project: " + path);
            }
            normalized.add(path);
        }
        return List.copyOf(normalized);
    }

    private void validateScope(
            Path sandboxRoot,
            List<Path> sandboxTargets,
            Map<Path, ProjectFileService.FileFingerprint> before,
            Map<Path, ProjectFileService.FileFingerprint> after,
            List<Path> changedRelativePaths,
            boolean allowTests,
            String phase
    ) {
        Set<Path> allowedProduction = sandboxTargets.stream()
                .map(path -> sandboxRoot.relativize(path).normalize())
                .collect(java.util.stream.Collectors.toSet());
        List<Path> invalid = new ArrayList<>();
        List<Path> deleted = new ArrayList<>();
        for (Path relative : changedRelativePaths) {
            Path sandboxPath = sandboxRoot.resolve(relative).normalize();
            if (!after.containsKey(sandboxPath)) {
                deleted.add(relative);
                continue;
            }
            if (Files.isSymbolicLink(sandboxPath)
                    || (!allowedProduction.contains(relative) && !(allowTests && isTestJava(sandboxPath)))) {
                invalid.add(relative);
            }
        }
        if (!deleted.isEmpty()) {
            throw new IllegalStateException(phase + " cleanup deleted files; deletions are not allowed: " + deleted);
        }
        if (!invalid.isEmpty()) {
            String scope = allowTests ? "production/test allowlist" : "selected production-file allowlist";
            throw new IllegalStateException(phase + " cleanup edited files outside the " + scope + ": " + invalid);
        }
        for (Path target : sandboxTargets) {
            if (!before.containsKey(target) || !after.containsKey(target) || Files.isSymbolicLink(target)) {
                throw new IllegalStateException("Cleanup target is missing or symbolic in the isolated workspace: " + target);
            }
        }
    }

    private void mergeCandidate(
            Path projectRoot,
            Path sandboxRoot,
            Map<Path, ProjectFileService.FileFingerprint> projectBaseline,
            Map<Path, ProjectFileService.FileFingerprint> sandboxAfter,
            List<Path> changedRelativePaths
    ) {
        Map<Path, String> contents = new LinkedHashMap<>();
        Map<Path, ProjectFileService.FileFingerprint> expected = new LinkedHashMap<>();
        for (Path relative : changedRelativePaths) {
            Path sandboxPath = sandboxRoot.resolve(relative).normalize();
            if (!sandboxAfter.containsKey(sandboxPath)) {
                throw new IllegalStateException("Verified candidate file is missing: " + relative);
            }
            Path projectPath = projectRoot.resolve(relative).normalize();
            contents.put(projectPath, fileService.readFile(sandboxPath));
            expected.put(projectPath, projectBaseline.get(projectPath));
        }
        fileService.writeFilesTransactionally(contents, expected);
    }

    private void ensureProjectUnchanged(
            Path projectRoot,
            Map<Path, ProjectFileService.FileFingerprint> projectBaseline
    ) {
        Map<Path, ProjectFileService.FileFingerprint> current = fileService.snapshotWorkspaceFiles(projectRoot);
        List<Path> drift = changedRelativePaths(projectRoot, projectBaseline, current);
        if (!drift.isEmpty()) {
            throw new IllegalStateException(
                    "Project files changed while cleanup was running; no candidate files were merged: " + drift
            );
        }
    }

    static List<Path> changedRelativePaths(
            Path root,
            Map<Path, ProjectFileService.FileFingerprint> before,
            Map<Path, ProjectFileService.FileFingerprint> after
    ) {
        TreeSet<Path> paths = new TreeSet<>();
        paths.addAll(before.keySet());
        paths.addAll(after.keySet());
        return paths.stream()
                .filter(path -> !Objects.equals(before.get(path), after.get(path)))
                .map(root::relativize)
                .map(Path::normalize)
                .toList();
    }

    private static boolean isProductionJava(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return normalized.endsWith(".java") && normalized.contains("/src/main/java/");
    }

    private static boolean isTestJava(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return normalized.endsWith(".java") && normalized.contains("/src/test/java/");
    }

    @FunctionalInterface
    interface DeterministicCleaner {
        OpenRewriteCleanupService.RewriteResult improve(
                Path sandboxRoot,
                List<Path> targetFiles,
                TerminalUi ui,
                boolean showLogs,
                PrintWriter logWriter
        );
    }

    @FunctionalInterface
    interface CleanupAgent {
        CodexCliRunner.RunResult improve(
                Path sandboxRoot,
                String model,
                String prompt,
                TerminalUi ui,
                boolean showLogs,
                PrintWriter logWriter
        ) throws Exception;
    }

    @FunctionalInterface
    interface BuildVerifier {
        JavaBuildVerificationService.VerificationResult verify(
                Path projectRoot,
                TerminalUi ui,
                boolean showLogs,
                PrintWriter logWriter
        );
    }

    public record CleanupResult(
            List<Path> changedRelativePaths,
            boolean applied,
            boolean checkOnly,
            CodexCliRunner.Usage usage,
            Duration baselineVerificationElapsed,
            Duration openRewriteElapsed,
            List<Path> openRewriteChangedRelativePaths,
            Duration candidateVerificationElapsed,
            Duration agentElapsed
    ) {
    }
}
