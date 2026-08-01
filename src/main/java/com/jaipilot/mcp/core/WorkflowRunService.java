package com.jaipilot.mcp.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Owns JAIPilot's isolated, two-phase agent workflows.
 *
 * <p>The connected coding agent edits only the returned sandbox. JAIPilot then proves scope,
 * build correctness, test execution, coverage, candidate immutability, and live-worktree stability
 * before merging files transactionally.</p>
 */
public final class WorkflowRunService implements AutoCloseable {

    static final int MAX_ACTIVE_RUNS = 4;
    static final Duration RUN_TTL = Duration.ofHours(2);
    public static final int MAX_MCP_FILE_CHARACTERS = 262_144;

    private final ProjectFileService fileService;
    private final JavaProjectService projectService;
    private final CoverageReportService coverageReportService;
    private final TestExecutionReportService testReportService;
    private final BuildGate buildGate;
    private final CoverageGate coverageGate;
    private final RewriteGate rewriteGate;
    private final Map<String, ActiveRun> runs = new ConcurrentHashMap<>();
    private final Map<Path, String> activeProjectRuns = new ConcurrentHashMap<>();
    private final Semaphore activeRunSlots = new Semaphore(MAX_ACTIVE_RUNS);

    public WorkflowRunService() {
        this(new ProjectFileService(), new CoverageReportService());
    }

    private WorkflowRunService(ProjectFileService fileService, CoverageReportService coverageReportService) {
        this(
                fileService,
                new JavaProjectService(fileService, coverageReportService),
                coverageReportService,
                null,
                null,
                null
        );
    }

    WorkflowRunService(
            ProjectFileService fileService,
            JavaProjectService projectService,
            CoverageReportService coverageReportService,
            BuildGate buildGate,
            CoverageGate coverageGate,
            RewriteGate rewriteGate
    ) {
        this.fileService = Objects.requireNonNull(fileService, "fileService");
        this.projectService = Objects.requireNonNull(projectService, "projectService");
        this.coverageReportService = Objects.requireNonNull(coverageReportService, "coverageReportService");
        this.testReportService = new TestExecutionReportService(projectService);
        this.buildGate = buildGate != null
                ? buildGate
                : new JavaBuildVerificationService(projectService)::verify;
        this.coverageGate = coverageGate != null
                ? coverageGate
                : new CoverageRefreshService(projectService, coverageReportService)::refresh;
        this.rewriteGate = rewriteGate != null
                ? rewriteGate
                : new OpenRewriteCleanupService(projectService)::clean;
    }

    public ProjectInspection inspect(Path requestedRoot) {
        Path root = resolveRoot(requestedRoot);
        JavaProjectService.BuildTool buildTool = projectService.detectBuildTool(root);
        List<JavaProjectService.JavaClassDescriptor> classes = projectService.findProductionClasses(root);
        List<JavaProjectService.JavaClassDescriptor> changed = projectService.findChangedProductionClasses(root);
        Optional<CoverageReportService.CoverageSnapshot> coverage;
        try {
            coverage = coverageReportService.readProjectSnapshot(root);
        } catch (RuntimeException exception) {
            coverage = Optional.empty();
        }
        return new ProjectInspection(
                root,
                buildTool.displayName(),
                projectService.resolveBuildWrapper(root).isPresent(),
                projectService.supportsCoverage(root),
                classes.size(),
                changed.stream().map(JavaProjectService.JavaClassDescriptor::fullyQualifiedName).toList(),
                coverage.map(CoverageReportService.CoverageSnapshot::totalLineCoverage).orElse(null),
                coverage.map(snapshot -> snapshot.reportPath().toString()).orElse(null),
                activeProjectRuns.get(root)
        );
    }

    public PreparedRun prepareTestGeneration(
            Path requestedRoot,
            TargetSelection selection,
            double minimumLineCoverage
    ) {
        validateCoveragePercentage(minimumLineCoverage, "minimumLineCoverage");
        return prepare(requestedRoot, WorkflowKind.GENERATE_TESTS, selection, minimumLineCoverage);
    }

    public PreparedRun prepareCodeCleanup(Path requestedRoot, TargetSelection selection) {
        return prepare(requestedRoot, WorkflowKind.CLEAN_JAVA, selection, 0.0d);
    }

    private PreparedRun prepare(
            Path requestedRoot,
            WorkflowKind kind,
            TargetSelection selection,
            double minimumLineCoverage
    ) {
        pruneExpiredRuns();
        Path root = resolveRoot(requestedRoot);
        TargetSelection normalizedSelection = Objects.requireNonNull(selection, "selection").normalizedFor(kind);
        String runId = UUID.randomUUID().toString();
        reserve(root, runId);
        Path sandbox = null;
        try {
            long baselineStarted = System.nanoTime();
            CoverageReportService.CoverageSnapshot beforeCoverage = baseline(root, normalizedSelection);
            Duration baselineElapsed = Duration.ofNanos(System.nanoTime() - baselineStarted);
            List<JavaProjectService.JavaClassDescriptor> targets = selectTargets(
                    root,
                    normalizedSelection,
                    beforeCoverage
            );
            if (targets.isEmpty()) {
                throw new IllegalStateException("No Java production classes matched the requested target selection.");
            }

            Map<Path, ProjectFileService.FileFingerprint> projectBaseline = fileService.snapshotWorkspaceFiles(root);
            sandbox = Files.createTempDirectory("jaipilot-mcp-" + kind.slug() + "-");
            fileService.copyProjectWorkspace(root, sandbox);
            Map<Path, ProjectFileService.FileFingerprint> sandboxBaseline = fileService.snapshotWorkspaceFiles(sandbox);

            Duration rewriteElapsed = Duration.ZERO;
            List<Path> rewriteChanges = List.of();
            if (kind == WorkflowKind.CLEAN_JAVA) {
                List<Path> sandboxTargets = rebaseTargets(root, sandbox, targets);
                OpenRewriteCleanupService.RewriteResult rewrite = rewriteGate.clean(sandbox, sandboxTargets);
                rewriteElapsed = rewrite.elapsed();
                Map<Path, ProjectFileService.FileFingerprint> afterRewrite = fileService.snapshotWorkspaceFiles(sandbox);
                rewriteChanges = changedRelativePaths(sandbox, sandboxBaseline, afterRewrite);
                validateScope(kind, sandbox, relativeTargetPaths(root, targets), sandboxBaseline, afterRewrite, rewriteChanges);
            }

            ActiveRun run = new ActiveRun(
                    runId,
                    kind,
                    root,
                    sandbox,
                    targets,
                    projectBaseline,
                    sandboxBaseline,
                    beforeCoverage,
                    minimumLineCoverage,
                    Instant.now()
            );
            PreparedRun result = preparedView(run, baselineElapsed, rewriteElapsed, rewriteChanges);
            runs.put(runId, run);
            return result;
        } catch (IOException exception) {
            releaseFailed(root, runId, sandbox);
            throw new IllegalStateException("Failed to create the isolated JAIPilot workspace.", exception);
        } catch (RuntimeException exception) {
            releaseFailed(root, runId, sandbox);
            throw exception;
        }
    }

    public RunStatusView getRun(String runId) {
        ActiveRun run = requireRun(runId);
        run.lock.lock();
        try {
            return new RunStatusView(
                    run.id,
                    run.kind,
                    run.status,
                    run.projectRoot,
                    run.sandboxRoot,
                    run.createdAt,
                    run.lastValidation
            );
        } finally {
            run.lock.unlock();
        }
    }

    public RunFile readRunFile(String runId, String relativePath) {
        ActiveRun run = requireRun(runId);
        run.lock.lock();
        try {
            requireOpen(run);
            Path file = resolveRunFile(run, relativePath);
            rejectSymbolicPath(run.sandboxRoot, file);
            if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
                throw new IllegalArgumentException("Run file is missing, non-regular, or symbolic: " + relativePath);
            }
            String content = fileService.readFile(file);
            if (content.length() > MAX_MCP_FILE_CHARACTERS) {
                throw new IllegalStateException("Run file exceeds the MCP read limit of "
                        + MAX_MCP_FILE_CHARACTERS + " characters: " + relativePath);
            }
            return new RunFile(run.id, run.sandboxRoot.relativize(file), content);
        } finally {
            run.lock.unlock();
        }
    }

    public WrittenRunFile writeRunFile(String runId, String relativePath, String content) {
        ActiveRun run = requireRun(runId);
        run.lock.lock();
        try {
            requireOpen(run);
            if (content == null) {
                throw new IllegalArgumentException("content is required.");
            }
            if (content.length() > MAX_MCP_FILE_CHARACTERS) {
                throw new IllegalArgumentException("content exceeds the MCP write limit of "
                        + MAX_MCP_FILE_CHARACTERS + " characters.");
            }
            Path file = resolveRunFile(run, relativePath);
            Path relative = run.sandboxRoot.relativize(file);
            Set<Path> selectedProduction = relativeTargetPaths(run.projectRoot, run.targets);
            boolean allowed = run.kind == WorkflowKind.GENERATE_TESTS
                    ? isTestJava(relative)
                    : selectedProduction.contains(relative) || isTestJava(relative);
            if (!allowed) {
                throw new IllegalArgumentException("Run file is outside the workflow write allowlist: " + relative);
            }
            rejectSymbolicPath(run.sandboxRoot, file);
            fileService.writeFile(file, content);
            run.status = RunStatus.PREPARED;
            run.validatedSnapshot = null;
            run.lastValidation = null;
            return new WrittenRunFile(run.id, relative, content.length());
        } finally {
            run.lock.unlock();
        }
    }

    public ValidationResult validate(String runId) {
        ActiveRun run = requireRun(runId);
        run.lock.lock();
        try {
            requireOpen(run);
            run.status = RunStatus.VALIDATING;
            Map<Path, ProjectFileService.FileFingerprint> candidate = fileService.snapshotWorkspaceFiles(run.sandboxRoot);
            List<Path> changes = changedRelativePaths(run.sandboxRoot, run.sandboxBaseline, candidate);
            validateScope(
                    run.kind,
                    run.sandboxRoot,
                    relativeTargetPaths(run.projectRoot, run.targets),
                    run.sandboxBaseline,
                    candidate,
                    changes
            );
            if (changes.isEmpty()) {
                ValidationResult result = new ValidationResult(
                        run.id,
                        true,
                        false,
                        List.of(),
                        List.of("The isolated workspace contains no source changes."),
                        List.of(),
                        List.of(),
                        null,
                        Map.of(),
                        Duration.ZERO
                );
                run.lastValidation = result;
                run.validatedSnapshot = candidate;
                run.status = RunStatus.VALIDATED;
                return result;
            }

            long started = System.nanoTime();
            CoverageReportService.CoverageSnapshot afterCoverage = null;
            if (run.kind == WorkflowKind.GENERATE_TESTS && projectService.supportsCoverage(run.sandboxRoot)) {
                afterCoverage = coverageGate.refresh(run.sandboxRoot);
            } else {
                buildGate.verify(run.sandboxRoot);
                if (run.kind == WorkflowKind.GENERATE_TESTS) {
                    afterCoverage = coverageReportService.readProjectSnapshot(run.sandboxRoot).orElse(null);
                }
            }
            Duration verificationElapsed = Duration.ofNanos(System.nanoTime() - started);

            Map<Path, ProjectFileService.FileFingerprint> verified = fileService.snapshotWorkspaceFiles(run.sandboxRoot);
            List<Path> buildDrift = changedRelativePaths(run.sandboxRoot, candidate, verified);
            if (!buildDrift.isEmpty()) {
                throw new IllegalStateException(
                        "The clean build changed files outside excluded build output: " + buildDrift
                );
            }

            List<String> missingTestReports = List.of();
            if (run.kind == WorkflowKind.GENERATE_TESTS) {
                List<Path> changedTests = changes.stream()
                        .filter(WorkflowRunService::isTestJava)
                        .map(run.sandboxRoot::resolve)
                        .toList();
                missingTestReports = testReportService.findMissingReports(run.sandboxRoot, changedTests);
            }
            Map<String, CoverageChange> coverage = coverageChanges(run, afterCoverage);
            List<String> warnings = new ArrayList<>();
            if (run.kind == WorkflowKind.GENERATE_TESTS && afterCoverage == null) {
                warnings.add("JaCoCo coverage is unavailable; build and test-execution gates still passed.");
            }
            List<String> failures = missingTestReports.isEmpty()
                    ? List.of()
                    : List.of("The clean build did not execute these changed tests: " + missingTestReports);
            boolean qualityGoalMet = coverage.isEmpty() || coverage.values().stream()
                    .allMatch(value -> value.afterLineCoverage() >= run.minimumLineCoverage);
            if (!qualityGoalMet) {
                warnings.add("One or more targets remain below the requested line-coverage goal of "
                        + formatPercentage(run.minimumLineCoverage) + ".");
            }
            boolean valid = failures.isEmpty();
            boolean readyToApply = valid && qualityGoalMet;
            ValidationResult result = new ValidationResult(
                    run.id,
                    valid,
                    readyToApply,
                    changes,
                    List.copyOf(warnings),
                    failures,
                    missingTestReports,
                    qualityGoalMet,
                    coverage,
                    verificationElapsed
            );
            run.lastValidation = result;
            run.validatedSnapshot = verified;
            run.status = readyToApply ? RunStatus.VALIDATED : RunStatus.PREPARED;
            return result;
        } catch (RuntimeException exception) {
            run.status = RunStatus.PREPARED;
            throw exception;
        } finally {
            run.lock.unlock();
        }
    }

    public AppliedRun apply(String runId) {
        ActiveRun run = requireRun(runId);
        run.lock.lock();
        try {
            if (run.status != RunStatus.VALIDATED || run.lastValidation == null || !run.lastValidation.readyToApply()) {
                throw new IllegalStateException("Run " + run.id + " must pass validation immediately before apply.");
            }
            Map<Path, ProjectFileService.FileFingerprint> currentCandidate =
                    fileService.snapshotWorkspaceFiles(run.sandboxRoot);
            List<Path> candidateDrift = changedRelativePaths(
                    run.sandboxRoot,
                    run.validatedSnapshot,
                    currentCandidate
            );
            if (!candidateDrift.isEmpty()) {
                run.status = RunStatus.PREPARED;
                throw new IllegalStateException(
                        "The candidate changed after validation; validate again before apply: " + candidateDrift
                );
            }
            Map<Path, ProjectFileService.FileFingerprint> currentProject =
                    fileService.snapshotWorkspaceFiles(run.projectRoot);
            List<Path> projectDrift = changedRelativePaths(run.projectRoot, run.projectBaseline, currentProject);
            if (!projectDrift.isEmpty()) {
                throw new IllegalStateException(
                        "The live project changed while the run was open; no files were applied: " + projectDrift
                );
            }

            Map<Path, String> contents = new LinkedHashMap<>();
            Map<Path, ProjectFileService.FileFingerprint> expected = new LinkedHashMap<>();
            for (Path relative : run.lastValidation.changedRelativePaths()) {
                Path candidatePath = run.sandboxRoot.resolve(relative).normalize();
                Path projectPath = run.projectRoot.resolve(relative).normalize();
                contents.put(projectPath, fileService.readFile(candidatePath));
                expected.put(projectPath, run.projectBaseline.get(projectPath));
            }
            fileService.writeFilesTransactionally(contents, expected);
            run.status = RunStatus.APPLIED;
            AppliedRun result = new AppliedRun(run.id, List.copyOf(run.lastValidation.changedRelativePaths()));
            retire(run);
            return result;
        } finally {
            run.lock.unlock();
        }
    }

    public void discard(String runId) {
        ActiveRun run = requireRun(runId);
        run.lock.lock();
        try {
            run.status = RunStatus.DISCARDED;
            retire(run);
        } finally {
            run.lock.unlock();
        }
    }

    private CoverageReportService.CoverageSnapshot baseline(Path root, TargetSelection selection) {
        if (selection.mode() == TargetMode.COVERAGE) {
            return coverageGate.refresh(root);
        }
        buildGate.verify(root);
        return coverageReportService.readProjectSnapshot(root).orElse(null);
    }

    private List<JavaProjectService.JavaClassDescriptor> selectTargets(
            Path root,
            TargetSelection selection,
            CoverageReportService.CoverageSnapshot coverage
    ) {
        return switch (selection.mode()) {
            case CLASSES -> selection.classes().stream()
                    .map(selector -> projectService.resolveClass(root, selector))
                    .collect(java.util.stream.Collectors.collectingAndThen(
                            java.util.stream.Collectors.toMap(
                                    descriptor -> descriptor.cutPath().normalize(),
                                    descriptor -> descriptor,
                                    (left, right) -> left,
                                    LinkedHashMap::new
                            ),
                            values -> List.copyOf(values.values())
                    ));
            case CHANGED -> projectService.findChangedProductionClasses(root);
            case ALL -> projectService.findProductionClasses(root);
            case COVERAGE -> projectService.findClassesBelowCoverage(root, selection.coverageThreshold(),
                    Objects.requireNonNull(coverage, "coverage"));
        };
    }

    private PreparedRun preparedView(
            ActiveRun run,
            Duration baselineElapsed,
            Duration rewriteElapsed,
            List<Path> rewriteChanges
    ) {
        List<TargetInfo> targetInfo = run.targets.stream().map(target -> {
            CoverageReportService.ClassCoverage coverage = run.beforeCoverage == null
                    ? null
                    : run.beforeCoverage.classCoverageByName().get(target.fullyQualifiedName());
            List<String> likelyTests = projectService.findLikelyTests(target).stream()
                    .map(test -> run.projectRoot.relativize(test.testPath()).toString())
                    .toList();
            return new TargetInfo(
                    target.fullyQualifiedName(),
                    run.projectRoot.relativize(target.cutPath()),
                    coverage == null ? null : coverage.lineCoverage(),
                    likelyTests
            );
        }).toList();
        return new PreparedRun(
                run.id,
                run.kind,
                run.projectRoot,
                run.sandboxRoot,
                targetInfo,
                rewriteChanges,
                prompt(run, targetInfo, rewriteChanges),
                baselineElapsed,
                rewriteElapsed,
                run.createdAt.plus(RUN_TTL)
        );
    }

    private String prompt(ActiveRun run, List<TargetInfo> targets, List<Path> rewriteChanges) {
        String targetList = targets.stream()
                .map(target -> "- " + target.fullyQualifiedName() + " (" + target.relativePath() + ")")
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("- none");
        if (run.kind == WorkflowKind.GENERATE_TESTS) {
            return """
                    Work only inside this isolated workspace: %s

                    Generate or improve focused Java unit tests for:
                    %s

                    Rules:
                    - Edit only Java files under src/test/java; never edit production code or build configuration.
                    - Follow the project's existing test framework, conventions, and dependency set.
                    - Cover observable behavior, boundaries, failures, and meaningful branches; avoid implementation-coupled assertions.
                    - Keep tests deterministic, independent, readable, and fast. Do not use sleeps, network calls, or global-state leaks.
                    - Prefer the smallest relevant test command while iterating, but rely on JAIPilot validation for the clean final gate.
                    - When ready, call jaipilot_validate_run with runId %s. Fix any reported issue in this workspace and revalidate.
                    - Call jaipilot_apply_run only after validation passes and the user wants the verified candidate applied.
                    """.formatted(run.sandboxRoot, targetList, run.id);
        }
        String rewriteSummary = rewriteChanges.isEmpty()
                ? "OpenRewrite made no deterministic changes."
                : "OpenRewrite changed: " + rewriteChanges;
        return """
                Work only inside this isolated workspace: %s

                Review and improve these Java production classes:
                %s

                %s

                Rules:
                - Production edits are restricted to the selected files; related Java tests may also be edited.
                - Preserve public behavior unless a proven defect and its regression test justify a change.
                - Improve correctness, clarity, maintainability, resource safety, and performance where evidence supports it.
                - Remove dead or redundant code; do not add dependencies, build changes, generated artifacts, or speculative abstractions.
                - Inspect OpenRewrite's candidate rather than blindly retaining it. Keep useful deterministic fixes and refine them.
                - Call jaipilot_validate_run with runId %s. Fix any reported issue in this workspace and revalidate.
                - Call jaipilot_apply_run only after validation passes and the user wants the verified candidate applied.
                """.formatted(run.sandboxRoot, targetList, rewriteSummary, run.id);
    }

    private Map<String, CoverageChange> coverageChanges(
            ActiveRun run,
            CoverageReportService.CoverageSnapshot afterCoverage
    ) {
        if (afterCoverage == null) {
            return Map.of();
        }
        Map<String, CoverageChange> changes = new LinkedHashMap<>();
        for (JavaProjectService.JavaClassDescriptor target : run.targets) {
            CoverageReportService.ClassCoverage before = run.beforeCoverage == null
                    ? null
                    : run.beforeCoverage.classCoverageByName().get(target.fullyQualifiedName());
            CoverageReportService.ClassCoverage after = afterCoverage.classCoverageByName().getOrDefault(
                    target.fullyQualifiedName(),
                    new CoverageReportService.ClassCoverage(target.fullyQualifiedName(), 0.0d, 0.0d)
            );
            changes.put(target.fullyQualifiedName(), new CoverageChange(
                    before == null ? null : before.lineCoverage(),
                    after.lineCoverage(),
                    before == null ? null : before.branchCoverage(),
                    after.branchCoverage()
            ));
        }
        return Map.copyOf(changes);
    }

    private void validateScope(
            WorkflowKind kind,
            Path sandboxRoot,
            Set<Path> selectedProduction,
            Map<Path, ProjectFileService.FileFingerprint> before,
            Map<Path, ProjectFileService.FileFingerprint> after,
            List<Path> changes
    ) {
        List<Path> deleted = new ArrayList<>();
        List<Path> invalid = new ArrayList<>();
        for (Path relative : changes) {
            Path candidate = sandboxRoot.resolve(relative).normalize();
            if (!after.containsKey(candidate)) {
                deleted.add(relative);
                continue;
            }
            boolean allowed = kind == WorkflowKind.GENERATE_TESTS
                    ? isTestJava(relative)
                    : selectedProduction.contains(relative) || isTestJava(relative);
            if (!allowed || Files.isSymbolicLink(candidate)) {
                invalid.add(relative);
            }
        }
        if (!deleted.isEmpty()) {
            throw new IllegalStateException("Candidate file deletions are not allowed: " + deleted);
        }
        if (!invalid.isEmpty()) {
            throw new IllegalStateException("Candidate edited files outside the workflow allowlist: " + invalid);
        }
        if (kind == WorkflowKind.CLEAN_JAVA) {
            for (Path relative : selectedProduction) {
                Path candidate = sandboxRoot.resolve(relative).normalize();
                if (!before.containsKey(candidate) || !after.containsKey(candidate) || Files.isSymbolicLink(candidate)) {
                    throw new IllegalStateException("Selected production file is missing or symbolic: " + relative);
                }
            }
        }
    }

    private void reserve(Path root, String runId) {
        if (!activeRunSlots.tryAcquire()) {
            throw new IllegalStateException("JAIPilot already has " + MAX_ACTIVE_RUNS
                    + " active runs. Apply or discard one before preparing another.");
        }
        String existing = activeProjectRuns.putIfAbsent(root, runId);
        if (existing != null) {
            activeRunSlots.release();
            throw new IllegalStateException("Project already has active JAIPilot run " + existing + ".");
        }
    }

    private void releaseFailed(Path root, String runId, Path sandbox) {
        if (activeProjectRuns.remove(root, runId)) {
            activeRunSlots.release();
        }
        if (sandbox != null) {
            fileService.deleteRecursively(sandbox);
        }
    }

    private void retire(ActiveRun run) {
        if (runs.remove(run.id, run)) {
            activeProjectRuns.remove(run.projectRoot, run.id);
            activeRunSlots.release();
            fileService.deleteRecursively(run.sandboxRoot);
        }
    }

    private void pruneExpiredRuns() {
        Instant cutoff = Instant.now().minus(RUN_TTL);
        runs.values().stream()
                .filter(run -> run.createdAt.isBefore(cutoff))
                .forEach(run -> {
                    if (run.lock.tryLock()) {
                        try {
                            run.status = RunStatus.EXPIRED;
                            retire(run);
                        } finally {
                            run.lock.unlock();
                        }
                    }
                });
    }

    private ActiveRun requireRun(String runId) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId is required.");
        }
        ActiveRun run = runs.get(runId);
        if (run == null) {
            throw new IllegalStateException("Unknown or completed JAIPilot run: " + runId);
        }
        return run;
    }

    private void requireOpen(ActiveRun run) {
        if (run.status != RunStatus.PREPARED && run.status != RunStatus.VALIDATED) {
            throw new IllegalStateException("Run " + run.id + " is not open for validation: " + run.status);
        }
    }

    private Path resolveRoot(Path requestedRoot) {
        Path requested = Objects.requireNonNull(requestedRoot, "projectRoot").toAbsolutePath().normalize();
        if (!Files.isDirectory(requested)) {
            throw new IllegalArgumentException("projectRoot is not a directory: " + requested);
        }
        Path root = projectService.resolveProjectRoot(requested).toAbsolutePath().normalize();
        try {
            return root.toRealPath();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to resolve projectRoot: " + root, exception);
        }
    }

    private Path resolveRunFile(ActiveRun run, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath is required.");
        }
        Path relative;
        try {
            relative = Path.of(relativePath).normalize();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("relativePath is invalid: " + relativePath, exception);
        }
        if (relative.isAbsolute() || relative.getNameCount() == 0 || relative.startsWith("..")) {
            throw new IllegalArgumentException("relativePath must stay inside the run workspace: " + relativePath);
        }
        Path resolved = run.sandboxRoot.resolve(relative).normalize();
        if (!resolved.startsWith(run.sandboxRoot)) {
            throw new IllegalArgumentException("relativePath must stay inside the run workspace: " + relativePath);
        }
        return resolved;
    }

    private void rejectSymbolicPath(Path workspaceRoot, Path file) {
        Path current = workspaceRoot;
        for (Path segment : workspaceRoot.relativize(file)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("Symbolic run paths cannot be written: "
                        + workspaceRoot.relativize(file));
            }
        }
    }

    private List<Path> rebaseTargets(
            Path projectRoot,
            Path sandboxRoot,
            List<JavaProjectService.JavaClassDescriptor> targets
    ) {
        return targets.stream()
                .map(target -> sandboxRoot.resolve(projectRoot.relativize(target.cutPath())).normalize())
                .toList();
    }

    private Set<Path> relativeTargetPaths(
            Path projectRoot,
            List<JavaProjectService.JavaClassDescriptor> targets
    ) {
        return targets.stream()
                .map(target -> projectRoot.relativize(target.cutPath()).normalize())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
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

    private static boolean isTestJava(Path path) {
        String normalized = "/" + path.toString().replace('\\', '/');
        return normalized.endsWith(".java") && normalized.contains("/src/test/java/");
    }

    private void validateCoveragePercentage(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0d || value > 100.0d) {
            throw new IllegalArgumentException(name + " must be between 0 and 100.");
        }
    }

    private String formatPercentage(double value) {
        return "%.1f%%".formatted(value);
    }

    @Override
    public void close() {
        List.copyOf(runs.values()).forEach(run -> {
            run.lock.lock();
            try {
                retire(run);
            } finally {
                run.lock.unlock();
            }
        });
    }

    @FunctionalInterface
    interface BuildGate {
        JavaBuildVerificationService.VerificationResult verify(Path projectRoot);
    }

    @FunctionalInterface
    interface CoverageGate {
        CoverageReportService.CoverageSnapshot refresh(Path projectRoot);
    }

    @FunctionalInterface
    interface RewriteGate {
        OpenRewriteCleanupService.RewriteResult clean(Path projectRoot, List<Path> targets);
    }

    public enum WorkflowKind {
        GENERATE_TESTS("tests"),
        CLEAN_JAVA("clean");

        private final String slug;

        WorkflowKind(String slug) {
            this.slug = slug;
        }

        String slug() {
            return slug;
        }
    }

    public enum RunStatus {
        PREPARED,
        VALIDATING,
        VALIDATED,
        APPLIED,
        DISCARDED,
        EXPIRED
    }

    public enum TargetMode {
        CLASSES,
        CHANGED,
        ALL,
        COVERAGE
    }

    public record TargetSelection(TargetMode mode, List<String> classes, double coverageThreshold) {

        public TargetSelection {
            classes = classes == null ? List.of() : classes.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .toList();
        }

        public static TargetSelection classes(List<String> selectors) {
            return new TargetSelection(TargetMode.CLASSES, selectors, 80.0d);
        }

        public static TargetSelection changed() {
            return new TargetSelection(TargetMode.CHANGED, List.of(), 80.0d);
        }

        public static TargetSelection all() {
            return new TargetSelection(TargetMode.ALL, List.of(), 80.0d);
        }

        public static TargetSelection coverageBelow(double threshold) {
            return new TargetSelection(TargetMode.COVERAGE, List.of(), threshold);
        }

        private TargetSelection normalizedFor(WorkflowKind kind) {
            TargetMode selectedMode = Objects.requireNonNull(mode, "target mode");
            if (selectedMode == TargetMode.CLASSES && classes.isEmpty()) {
                throw new IllegalArgumentException("At least one class selector is required for classes mode.");
            }
            if (selectedMode != TargetMode.CLASSES && !classes.isEmpty()) {
                throw new IllegalArgumentException("Class selectors are only valid in classes mode.");
            }
            if (kind == WorkflowKind.CLEAN_JAVA && selectedMode == TargetMode.COVERAGE) {
                throw new IllegalArgumentException("Coverage target selection is only available for test generation.");
            }
            if (selectedMode == TargetMode.COVERAGE
                    && (!Double.isFinite(coverageThreshold) || coverageThreshold < 0.0d || coverageThreshold > 100.0d)) {
                throw new IllegalArgumentException("coverageThreshold must be between 0 and 100.");
            }
            return this;
        }
    }

    public record ProjectInspection(
            Path projectRoot,
            String buildTool,
            boolean wrapperAvailable,
            boolean jacocoConfigured,
            int productionClassCount,
            List<String> changedProductionClasses,
            Double cachedLineCoverage,
            String cachedCoverageReport,
            String activeRunId
    ) {
    }

    public record TargetInfo(
            String fullyQualifiedName,
            Path relativePath,
            Double lineCoverageBefore,
            List<String> likelyTests
    ) {
    }

    public record PreparedRun(
            String runId,
            WorkflowKind kind,
            Path projectRoot,
            Path workspaceRoot,
            List<TargetInfo> targets,
            List<Path> openRewriteChanges,
            String agentInstructions,
            Duration baselineElapsed,
            Duration openRewriteElapsed,
            Instant expiresAt
    ) {
    }

    public record CoverageChange(
            Double beforeLineCoverage,
            double afterLineCoverage,
            Double beforeBranchCoverage,
            double afterBranchCoverage
    ) {
    }

    public record ValidationResult(
            String runId,
            boolean valid,
            boolean readyToApply,
            List<Path> changedRelativePaths,
            List<String> warnings,
            List<String> failures,
            List<String> missingTestReports,
            Boolean coverageGoalMet,
            Map<String, CoverageChange> coverage,
            Duration verificationElapsed
    ) {
    }

    public record RunStatusView(
            String runId,
            WorkflowKind kind,
            RunStatus status,
            Path projectRoot,
            Path workspaceRoot,
            Instant createdAt,
            ValidationResult lastValidation
    ) {
    }

    public record AppliedRun(String runId, List<Path> changedRelativePaths) {
    }

    public record RunFile(String runId, Path relativePath, String content) {
    }

    public record WrittenRunFile(String runId, Path relativePath, int charactersWritten) {
    }

    private static final class ActiveRun {

        private final String id;
        private final WorkflowKind kind;
        private final Path projectRoot;
        private final Path sandboxRoot;
        private final List<JavaProjectService.JavaClassDescriptor> targets;
        private final Map<Path, ProjectFileService.FileFingerprint> projectBaseline;
        private final Map<Path, ProjectFileService.FileFingerprint> sandboxBaseline;
        private final CoverageReportService.CoverageSnapshot beforeCoverage;
        private final double minimumLineCoverage;
        private final Instant createdAt;
        private final ReentrantLock lock = new ReentrantLock();
        private RunStatus status = RunStatus.PREPARED;
        private ValidationResult lastValidation;
        private Map<Path, ProjectFileService.FileFingerprint> validatedSnapshot;

        private ActiveRun(
                String id,
                WorkflowKind kind,
                Path projectRoot,
                Path sandboxRoot,
                List<JavaProjectService.JavaClassDescriptor> targets,
                Map<Path, ProjectFileService.FileFingerprint> projectBaseline,
                Map<Path, ProjectFileService.FileFingerprint> sandboxBaseline,
                CoverageReportService.CoverageSnapshot beforeCoverage,
                double minimumLineCoverage,
                Instant createdAt
        ) {
            this.id = id;
            this.kind = kind;
            this.projectRoot = projectRoot;
            this.sandboxRoot = sandboxRoot;
            this.targets = List.copyOf(targets);
            this.projectBaseline = Map.copyOf(projectBaseline);
            this.sandboxBaseline = Map.copyOf(sandboxBaseline);
            this.beforeCoverage = beforeCoverage;
            this.minimumLineCoverage = minimumLineCoverage;
            this.createdAt = createdAt;
        }
    }
}
