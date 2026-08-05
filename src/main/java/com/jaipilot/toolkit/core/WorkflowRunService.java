package com.jaipilot.toolkit.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

    public static final int MAX_ACTIVE_RUNS = 4;
    public static final Duration RUN_TTL = Duration.ofHours(2);

    private final ProjectFileService fileService;
    private final JavaProjectService projectService;
    private final CoverageReportService coverageReportService;
    private final TestExecutionReportService testReportService;
    private final BuildGate buildGate;
    private final CoverageGate coverageGate;
    private final RewriteGate rewriteGate;
    private final QualityGate qualityGate;
    private final MutationGate mutationGate;
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
        this(
                fileService,
                projectService,
                coverageReportService,
                buildGate,
                coverageGate,
                rewriteGate,
                null,
                (root, targets, tests, minimum) -> skippedMutationReport(minimum)
        );
    }

    WorkflowRunService(
            ProjectFileService fileService,
            JavaProjectService projectService,
            CoverageReportService coverageReportService,
            BuildGate buildGate,
            CoverageGate coverageGate,
            RewriteGate rewriteGate,
            QualityGate qualityGate,
            MutationGate mutationGate
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
        this.qualityGate = qualityGate != null
                ? qualityGate
                : new JavaQualityService()::analyze;
        this.mutationGate = mutationGate != null
                ? mutationGate
                : new MutationTestingService(projectService, fileService)::run;
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

    public QualityInspection inspectQuality(Path requestedRoot, TargetSelection selection) {
        Path root = resolveRoot(requestedRoot);
        TargetSelection normalized = Objects.requireNonNull(selection, "selection")
                .normalizedFor(WorkflowKind.CLEAN_JAVA);
        List<JavaProjectService.JavaClassDescriptor> targets = selectTargets(root, normalized, null);
        if (targets.isEmpty()) {
            throw new IllegalStateException("No Java production classes matched the requested quality scope.");
        }
        JavaQualityService.QualityReport quality = qualityGate.analyze(
                root,
                targets.stream().map(JavaProjectService.JavaClassDescriptor::cutPath).toList()
        );
        return new QualityInspection(
                root,
                targets.stream().map(JavaProjectService.JavaClassDescriptor::fullyQualifiedName).toList(),
                quality
        );
    }

    public PreparedRun prepareTestGeneration(
            Path requestedRoot,
            TargetSelection selection,
            double minimumLineCoverage
    ) {
        return prepareTestGeneration(requestedRoot, selection, minimumLineCoverage, 70.0d);
    }

    public PreparedRun prepareTestGeneration(
            Path requestedRoot,
            TargetSelection selection,
            double minimumLineCoverage,
            double minimumMutationScore
    ) {
        validateCoveragePercentage(minimumLineCoverage, "minimumLineCoverage");
        validateCoveragePercentage(minimumMutationScore, "minimumMutationScore");
        return prepare(
                requestedRoot,
                WorkflowKind.GENERATE_TESTS,
                selection,
                minimumLineCoverage,
                minimumMutationScore
        );
    }

    public PreparedRun prepareCodeCleanup(Path requestedRoot, TargetSelection selection) {
        return prepare(requestedRoot, WorkflowKind.CLEAN_JAVA, selection, 0.0d, 70.0d);
    }

    private PreparedRun prepare(
            Path requestedRoot,
            WorkflowKind kind,
            TargetSelection selection,
            double minimumLineCoverage,
            double minimumMutationScore
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

            JavaQualityService.QualityReport qualityBefore = qualityGate.analyze(
                    root,
                    targets.stream().map(JavaProjectService.JavaClassDescriptor::cutPath).toList()
            );

            Map<Path, ProjectFileService.FileFingerprint> projectBaseline = fileService.snapshotWorkspaceFiles(root);
            sandbox = Files.createTempDirectory("jaipilot-" + kind.slug() + "-");
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
            JavaQualityService.QualityReport qualityAfterRewrite = kind == WorkflowKind.CLEAN_JAVA
                    ? qualityGate.analyze(sandbox, rebaseTargets(root, sandbox, targets))
                    : qualityBefore;

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
                    minimumMutationScore,
                    qualityBefore,
                    Instant.now()
            );
            PreparedRun result = preparedView(
                    run,
                    baselineElapsed,
                    rewriteElapsed,
                    rewriteChanges,
                    qualityAfterRewrite
            );
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

    /**
     * Exports an active workflow without exposing mutable in-memory state.
     *
     * <p>The skills-first toolkit harness uses this representation to continue a run across short-lived
     * agent command invocations.</p>
     */
    public StoredRunState exportRun(String runId) {
        ActiveRun run = requireRun(runId);
        run.lock.lock();
        try {
            return new StoredRunState(
                    3,
                    run.id,
                    run.kind.name(),
                    run.status.name(),
                    run.projectRoot.toString(),
                    run.sandboxRoot.toString(),
                    run.targets.stream().map(target -> new StoredTarget(
                            portablePath(run.projectRoot.relativize(target.moduleRoot())),
                            portablePath(run.projectRoot.relativize(target.cutPath())),
                            target.packageName(),
                            target.className(),
                            target.fullyQualifiedName()
                    )).toList(),
                    storeSnapshot(run.projectRoot, run.projectBaseline),
                    storeSnapshot(run.sandboxRoot, run.sandboxBaseline),
                    storeCoverage(run.projectRoot, run.beforeCoverage),
                    run.minimumLineCoverage,
                    run.minimumMutationScore,
                    run.qualityBefore,
                    run.createdAt.toString(),
                    storeValidation(run.lastValidation),
                    run.validatedSnapshot == null
                            ? null
                            : storeSnapshot(run.sandboxRoot, run.validatedSnapshot)
            );
        } finally {
            run.lock.unlock();
        }
    }

    /** Restores one trusted, locally persisted toolkit-harness run into this service instance. */
    public void restoreRun(StoredRunState state) {
        Objects.requireNonNull(state, "state");
        if (state.schemaVersion() < 1 || state.schemaVersion() > 3) {
            throw new IllegalArgumentException("Unsupported JAIPilot run-state schema: " + state.schemaVersion());
        }
        validateStoredRunId(state.runId());
        Instant createdAt = Instant.parse(state.createdAt());
        if (createdAt.isBefore(Instant.now().minus(RUN_TTL))) {
            throw new IllegalStateException("Stored JAIPilot run has expired: " + state.runId());
        }
        WorkflowKind kind = WorkflowKind.valueOf(state.kind());
        RunStatus status = RunStatus.valueOf(state.status());
        if (status != RunStatus.PREPARED && status != RunStatus.VALIDATED) {
            throw new IllegalStateException("Stored JAIPilot run is not open: " + status);
        }
        validateCoveragePercentage(state.minimumLineCoverage(), "stored minimumLineCoverage");
        if (state.schemaVersion() >= 2) {
            validateCoveragePercentage(state.minimumMutationScore(), "stored minimumMutationScore");
        }
        Path projectRoot = realDirectory(state.projectRoot(), "stored projectRoot");
        Path sandboxRoot = realDirectory(state.workspaceRoot(), "stored workspaceRoot");
        if (state.targets() == null) {
            throw new IllegalArgumentException("Stored JAIPilot targets are required.");
        }
        List<JavaProjectService.JavaClassDescriptor> targets = state.targets().stream()
                .map(target -> new JavaProjectService.JavaClassDescriptor(
                        projectRoot,
                        restoreRelativePath(projectRoot, target.moduleRelativePath()),
                        restoreRelativePath(projectRoot, target.sourceRelativePath()),
                        target.packageName(),
                        target.className(),
                        target.fullyQualifiedName()
                ))
                .toList();
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("Stored JAIPilot run has no targets.");
        }
        JavaQualityService.QualityReport qualityBefore = state.qualityBefore() != null
                ? state.qualityBefore()
                : qualityGate.analyze(
                        projectRoot,
                        targets.stream().map(JavaProjectService.JavaClassDescriptor::cutPath).toList()
                );

        reserve(projectRoot, state.runId());
        try {
            ActiveRun run = new ActiveRun(
                    state.runId(),
                    kind,
                    projectRoot,
                    sandboxRoot,
                    targets,
                    restoreSnapshot(projectRoot, state.projectBaseline()),
                    restoreSnapshot(sandboxRoot, state.workspaceBaseline()),
                    restoreCoverage(projectRoot, state.beforeCoverage()),
                    state.minimumLineCoverage(),
                    state.schemaVersion() >= 2 ? state.minimumMutationScore() : 70.0d,
                    qualityBefore,
                    createdAt
            );
            run.status = status;
            run.lastValidation = restoreValidation(state.lastValidation());
            if (run.lastValidation != null && !run.id.equals(run.lastValidation.runId())) {
                throw new IllegalStateException("Stored validation proof belongs to another run.");
            }
            run.validatedSnapshot = state.validatedSnapshot() == null
                    ? null
                    : restoreSnapshot(sandboxRoot, state.validatedSnapshot());
            if (status == RunStatus.VALIDATED
                    && (run.lastValidation == null
                    || !run.lastValidation.readyToApply()
                    || run.validatedSnapshot == null)) {
                throw new IllegalStateException("Stored validated run is missing its validation proof.");
            }
            runs.put(run.id, run);
        } catch (RuntimeException exception) {
            if (activeProjectRuns.remove(projectRoot, state.runId())) {
                activeRunSlots.release();
            }
            throw exception;
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
                        true,
                        run.qualityBefore,
                        qualityDelta(run.qualityBefore, run.qualityBefore),
                        null,
                        null,
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
            List<String> missingTestReports = List.of();
            List<Path> changedTests = changes.stream()
                    .filter(WorkflowRunService::isTestJava)
                    .map(run.sandboxRoot::resolve)
                    .toList();
            if (run.kind == WorkflowKind.GENERATE_TESTS) {
                missingTestReports = testReportService.findMissingReports(run.sandboxRoot, changedTests);
            }
            Map<String, CoverageChange> coverage = coverageChanges(run, afterCoverage);
            List<String> warnings = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            if (run.kind == WorkflowKind.GENERATE_TESTS && afterCoverage == null) {
                warnings.add("JaCoCo coverage is unavailable; build and test-execution gates still passed.");
            }
            if (!missingTestReports.isEmpty()) {
                failures.add("The clean build did not execute these changed tests: " + missingTestReports);
            }
            boolean qualityGoalMet = coverage.isEmpty() || coverage.values().stream()
                    .allMatch(value -> value.afterLineCoverage() >= run.minimumLineCoverage);
            if (!qualityGoalMet) {
                warnings.add("One or more targets remain below the requested line-coverage goal of "
                        + formatPercentage(run.minimumLineCoverage) + ".");
            }

            JavaQualityService.QualityReport quality = qualityGate.analyze(
                    run.sandboxRoot,
                    rebaseTargets(run.projectRoot, run.sandboxRoot, run.targets)
            );
            QualityDelta qualityChange = qualityDelta(run.qualityBefore, quality);
            boolean sourceQualityGoalMet = quality.parseFailures().isEmpty()
                    && qualityChange.newCriticalOrHighFindings() == 0
                    && qualityChange.qualityScoreAfter() + 0.1d >= qualityChange.qualityScoreBefore();
            if (!quality.parseFailures().isEmpty()) {
                failures.add("JAIPilot could not parse selected Java sources for quality analysis: "
                        + quality.parseFailures());
            }
            if (qualityChange.newCriticalOrHighFindings() > 0) {
                failures.add("The candidate introduced " + qualityChange.newCriticalOrHighFindings()
                        + " new critical/high quality findings.");
            }
            if (qualityChange.qualityScoreAfter() + 0.1d < qualityChange.qualityScoreBefore()) {
                failures.add("The candidate regressed the JAIPilot quality score from "
                        + qualityChange.qualityScoreBefore() + " to " + qualityChange.qualityScoreAfter() + ".");
            }

            MutationTestingService.MutationReport mutation = null;
            boolean mutationGoalMet = true;
            boolean shouldRunMutation = run.kind == WorkflowKind.GENERATE_TESTS || !changedTests.isEmpty();
            if (shouldRunMutation) {
                try {
                    mutation = mutationGate.run(
                            run.sandboxRoot,
                            mutationTargets(run),
                            changedTests,
                            run.minimumMutationScore
                    );
                    mutationGoalMet = mutation.goalMet();
                    if (!mutationGoalMet) {
                        failures.add(mutationFailure(run, mutation));
                    } else if (mutation.survived() > 0 || mutation.noCoverage() > 0) {
                        warnings.add("PIT found " + mutation.survived() + " surviving and "
                                + mutation.noCoverage() + " uncovered mutations; review the mutation evidence.");
                    }
                    if (mutation.incompleteReason() != null) {
                        warnings.add(mutation.incompleteReason());
                    }
                } catch (RuntimeException exception) {
                    mutationGoalMet = false;
                    mutation = failedMutationReport(run.minimumMutationScore, rootMessage(exception));
                    failures.add("PIT mutation testing did not complete: " + rootMessage(exception));
                }
            }

            TestQualityScore testQuality = run.kind == WorkflowKind.GENERATE_TESTS || !changedTests.isEmpty()
                    ? testQualityScore(coverage, changedTests.size(), missingTestReports, mutation)
                    : null;

            Map<Path, ProjectFileService.FileFingerprint> verified = fileService.snapshotWorkspaceFiles(run.sandboxRoot);
            List<Path> buildDrift = changedRelativePaths(run.sandboxRoot, candidate, verified);
            if (!buildDrift.isEmpty()) {
                throw new IllegalStateException(
                        "The validation tools changed files outside excluded build output: " + buildDrift
                );
            }
            Duration verificationElapsed = Duration.ofNanos(System.nanoTime() - started);
            boolean valid = failures.isEmpty();
            boolean readyToApply = valid && qualityGoalMet && sourceQualityGoalMet && mutationGoalMet;
            ValidationResult result = new ValidationResult(
                    run.id,
                    valid,
                    readyToApply,
                    changes,
                    List.copyOf(warnings),
                    List.copyOf(failures),
                    missingTestReports,
                    qualityGoalMet,
                    coverage,
                    sourceQualityGoalMet,
                    quality,
                    qualityChange,
                    mutation,
                    testQuality,
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
            List<Path> rewriteChanges,
            JavaQualityService.QualityReport qualityAfterRewrite
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
                run.qualityBefore,
                qualityAfterRewrite,
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

    private List<MutationTestingService.MutationTarget> mutationTargets(ActiveRun run) {
        List<MutationTestingService.MutationTarget> targets = new ArrayList<>();
        for (JavaProjectService.JavaClassDescriptor target : run.targets) {
            Path sandboxModule = run.sandboxRoot.resolve(
                    run.projectRoot.relativize(target.moduleRoot())
            ).normalize();
            Path sandboxSource = run.sandboxRoot.resolve(
                    run.projectRoot.relativize(target.cutPath())
            ).normalize();
            JavaProjectService.JavaClassDescriptor sandboxTarget = new JavaProjectService.JavaClassDescriptor(
                    run.sandboxRoot,
                    sandboxModule,
                    sandboxSource,
                    target.packageName(),
                    target.className(),
                    target.fullyQualifiedName()
            );
            List<String> likelyTests = projectService.findLikelyTests(sandboxTarget).stream()
                    .map(JavaProjectService.JavaTestDescriptor::fullyQualifiedName)
                    .toList();
            targets.add(new MutationTestingService.MutationTarget(
                    sandboxModule,
                    target.fullyQualifiedName(),
                    likelyTests
            ));
        }
        return List.copyOf(targets);
    }

    private String mutationFailure(
            ActiveRun run,
            MutationTestingService.MutationReport mutation
    ) {
        if (mutation.errors() > 0) {
            return "PIT returned " + mutation.errors()
                    + " error or unfinished statuses; mutation evidence is incomplete.";
        }
        if (mutation.mutationScore() == null) {
            return "PIT produced no scorable mutations; the required mutation score is "
                    + formatPercentage(run.minimumMutationScore) + ".";
        }
        return "PIT mutation score " + mutation.mutationScore() + "% is below the required "
                + formatPercentage(run.minimumMutationScore) + ".";
    }

    private QualityDelta qualityDelta(
            JavaQualityService.QualityReport before,
            JavaQualityService.QualityReport after
    ) {
        Map<String, Long> beforeCounts = qualityFindingCounts(before.findings(), false);
        Map<String, Long> afterCounts = qualityFindingCounts(after.findings(), false);
        Map<String, Long> beforeSevereCounts = qualityFindingCounts(before.findings(), true);
        Map<String, Long> afterSevereCounts = qualityFindingCounts(after.findings(), true);
        long resolved = countExcess(beforeCounts, afterCounts);
        long introduced = countExcess(afterCounts, beforeCounts);
        long newSevere = countExcess(afterSevereCounts, beforeSevereCounts);
        return new QualityDelta(
                before.metrics().qualityScore(),
                after.metrics().qualityScore(),
                before.metrics().reliabilityScore(),
                after.metrics().reliabilityScore(),
                before.metrics().maintainabilityScore(),
                after.metrics().maintainabilityScore(),
                before.metrics().findingCount(),
                after.metrics().findingCount(),
                before.metrics().bugRiskCount(),
                after.metrics().bugRiskCount(),
                before.metrics().codeSmellCount(),
                after.metrics().codeSmellCount(),
                before.metrics().remediationDebtMinutes(),
                after.metrics().remediationDebtMinutes(),
                before.metrics().duplicationPercent(),
                after.metrics().duplicationPercent(),
                before.metrics().maximumCyclomaticComplexity(),
                after.metrics().maximumCyclomaticComplexity(),
                safeInt(resolved),
                safeInt(introduced),
                safeInt(newSevere)
        );
    }

    private Map<String, Long> qualityFindingCounts(
            List<JavaQualityService.Finding> findings,
            boolean severeOnly
    ) {
        Map<String, Long> counts = new LinkedHashMap<>();
        findings.stream()
                .filter(finding -> !severeOnly
                        || finding.severity() == JavaQualityService.Severity.CRITICAL
                        || finding.severity() == JavaQualityService.Severity.HIGH)
                .map(this::qualityFindingKey)
                .forEach(key -> counts.merge(key, 1L, Long::sum));
        return counts;
    }

    private long countExcess(Map<String, Long> minuend, Map<String, Long> subtrahend) {
        return minuend.entrySet().stream()
                .mapToLong(entry -> Math.max(0L, entry.getValue() - subtrahend.getOrDefault(entry.getKey(), 0L)))
                .sum();
    }

    private String qualityFindingKey(JavaQualityService.Finding finding) {
        return finding.id() + "|" + finding.relativePath() + "|" + finding.symbol();
    }

    private TestQualityScore testQualityScore(
            Map<String, CoverageChange> coverage,
            int changedTestFileCount,
            List<String> missingTestReports,
            MutationTestingService.MutationReport mutation
    ) {
        Double lineCoverage = coverage.isEmpty() ? null : round(coverage.values().stream()
                .mapToDouble(CoverageChange::afterLineCoverage).average().orElse(0.0d));
        Double branchCoverage = coverage.isEmpty() ? null : round(coverage.values().stream()
                .mapToDouble(CoverageChange::afterBranchCoverage).average().orElse(0.0d));
        Double mutationScore = mutation == null ? null : mutation.mutationScore();
        Double testStrength = mutation == null ? null : mutation.testStrength();
        double executionScore = missingTestReports.isEmpty() ? 100.0d : 0.0d;
        double score = value(lineCoverage) * 0.25d
                + value(branchCoverage) * 0.20d
                + value(mutationScore) * 0.35d
                + value(testStrength) * 0.15d
                + executionScore * 0.05d;
        double completeness = 5.0d
                + (lineCoverage == null ? 0.0d : 25.0d)
                + (branchCoverage == null ? 0.0d : 20.0d)
                + (mutationScore == null ? 0.0d : 35.0d)
                + (testStrength == null ? 0.0d : 15.0d);
        double roundedScore = round(score);
        return new TestQualityScore(
                roundedScore,
                grade(roundedScore),
                round(completeness),
                lineCoverage,
                branchCoverage,
                mutationScore,
                testStrength,
                executionScore,
                missingTestReports.isEmpty(),
                changedTestFileCount,
                Math.max(0, changedTestFileCount - missingTestReports.size())
        );
    }

    private String grade(double score) {
        if (score >= 90.0d) {
            return "EXCELLENT";
        }
        if (score >= 80.0d) {
            return "STRONG";
        }
        if (score >= 70.0d) {
            return "GOOD";
        }
        if (score >= 50.0d) {
            return "NEEDS_WORK";
        }
        return "WEAK";
    }

    private double value(Double value) {
        return value == null ? 0.0d : value;
    }

    private double round(double value) {
        return Math.round(value * 10.0d) / 10.0d;
    }

    private int safeInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private static MutationTestingService.MutationReport skippedMutationReport(double minimum) {
        return new MutationTestingService.MutationReport(
                MutationTestingService.PITEST_VERSION,
                false,
                minimum,
                null,
                null,
                true,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                "Mutation execution was replaced by the test harness.",
                0L
        );
    }

    private static MutationTestingService.MutationReport failedMutationReport(double minimum, String reason) {
        return new MutationTestingService.MutationReport(
                MutationTestingService.PITEST_VERSION,
                false,
                minimum,
                null,
                null,
                false,
                0,
                0,
                0,
                0,
                0,
                1,
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                reason,
                0L
        );
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

    private void validateStoredRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("Stored runId is required.");
        }
        try {
            if (!UUID.fromString(runId).toString().equalsIgnoreCase(runId)) {
                throw new IllegalArgumentException("Stored runId must be a UUID.");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Stored runId must be a UUID.", exception);
        }
    }

    private String formatPercentage(double value) {
        return "%.1f%%".formatted(value);
    }

    private List<StoredFingerprint> storeSnapshot(
            Path root,
            Map<Path, ProjectFileService.FileFingerprint> snapshot
    ) {
        return snapshot.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new StoredFingerprint(
                        portablePath(root.relativize(entry.getKey())),
                        entry.getValue().size(),
                        entry.getValue().sha256(),
                        entry.getValue().symbolicLink()
                ))
                .toList();
    }

    private Map<Path, ProjectFileService.FileFingerprint> restoreSnapshot(
            Path root,
            List<StoredFingerprint> stored
    ) {
        if (stored == null) {
            throw new IllegalArgumentException("Stored file snapshot is required.");
        }
        Map<Path, ProjectFileService.FileFingerprint> restored = new LinkedHashMap<>();
        for (StoredFingerprint fingerprint : stored) {
            Path path = restoreRelativePath(root, fingerprint.relativePath());
            ProjectFileService.FileFingerprint previous = restored.put(
                    path,
                    new ProjectFileService.FileFingerprint(
                            fingerprint.size(),
                            fingerprint.sha256(),
                            fingerprint.symbolicLink()
                    )
            );
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate stored file path: " + fingerprint.relativePath());
            }
        }
        return Map.copyOf(restored);
    }

    private StoredCoverage storeCoverage(
            Path projectRoot,
            CoverageReportService.CoverageSnapshot coverage
    ) {
        if (coverage == null) {
            return null;
        }
        return new StoredCoverage(
                portablePath(projectRoot.relativize(coverage.reportPath())),
                coverage.totalLineCoverage(),
                coverage.totalBranchCoverage(),
                coverage.classCoverageByName()
        );
    }

    private CoverageReportService.CoverageSnapshot restoreCoverage(
            Path projectRoot,
            StoredCoverage coverage
    ) {
        if (coverage == null) {
            return null;
        }
        return new CoverageReportService.CoverageSnapshot(
                restoreRelativePath(projectRoot, coverage.reportRelativePath()),
                coverage.totalLineCoverage(),
                coverage.totalBranchCoverage(),
                Map.copyOf(coverage.classCoverageByName())
        );
    }

    private StoredValidation storeValidation(ValidationResult validation) {
        if (validation == null) {
            return null;
        }
        return new StoredValidation(
                validation.runId(),
                validation.valid(),
                validation.readyToApply(),
                validation.changedRelativePaths().stream().map(this::portablePath).toList(),
                validation.warnings(),
                validation.failures(),
                validation.missingTestReports(),
                validation.coverageGoalMet(),
                validation.coverage(),
                validation.qualityGoalMet(),
                validation.quality(),
                validation.qualityDelta(),
                validation.mutation(),
                validation.testQuality(),
                validation.verificationElapsed().toMillis()
        );
    }

    private ValidationResult restoreValidation(StoredValidation validation) {
        if (validation == null) {
            return null;
        }
        return new ValidationResult(
                validation.runId(),
                validation.valid(),
                validation.readyToApply(),
                validation.changedRelativePaths().stream().map(this::parseStoredRelativePath).toList(),
                List.copyOf(validation.warnings()),
                List.copyOf(validation.failures()),
                List.copyOf(validation.missingTestReports()),
                validation.coverageGoalMet(),
                Map.copyOf(validation.coverage()),
                validation.quality() == null || validation.qualityGoalMet(),
                validation.quality(),
                validation.qualityDelta(),
                validation.mutation(),
                validation.testQuality(),
                Duration.ofMillis(validation.verificationElapsedMillis())
        );
    }

    private Path realDirectory(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
        Path path = Path.of(value).toAbsolutePath().normalize();
        try {
            Path real = path.toRealPath();
            if (!Files.isDirectory(real) || Files.isSymbolicLink(path)) {
                throw new IllegalArgumentException(name + " is missing, non-directory, or symbolic: " + path);
            }
            return real;
        } catch (IOException exception) {
            throw new IllegalArgumentException(name + " cannot be resolved: " + path, exception);
        }
    }

    private Path restoreRelativePath(Path root, String value) {
        Path relative = parseStoredRelativePath(value);
        Path restored = root.resolve(relative).normalize();
        if (!restored.startsWith(root)) {
            throw new IllegalArgumentException("Stored path escapes its workspace: " + value);
        }
        return restored;
    }

    private Path parseStoredRelativePath(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Stored relative path is required.");
        }
        Path relative = Path.of(value).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IllegalArgumentException("Stored path escapes its workspace: " + value);
        }
        return relative;
    }

    private String portablePath(Path path) {
        String value = path.normalize().toString().replace('\\', '/');
        return value.isBlank() ? "." : value;
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

    @FunctionalInterface
    interface QualityGate {
        JavaQualityService.QualityReport analyze(Path projectRoot, List<Path> targets);
    }

    @FunctionalInterface
    interface MutationGate {
        MutationTestingService.MutationReport run(
                Path projectRoot,
                List<MutationTestingService.MutationTarget> targets,
                List<Path> changedTests,
                double minimumMutationScore
        );
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

    public record QualityInspection(
            Path projectRoot,
            List<String> targets,
            JavaQualityService.QualityReport quality
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
            JavaQualityService.QualityReport qualityBefore,
            JavaQualityService.QualityReport qualityAfterOpenRewrite,
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

    public record QualityDelta(
            double qualityScoreBefore,
            double qualityScoreAfter,
            double reliabilityScoreBefore,
            double reliabilityScoreAfter,
            double maintainabilityScoreBefore,
            double maintainabilityScoreAfter,
            int findingsBefore,
            int findingsAfter,
            int bugRisksBefore,
            int bugRisksAfter,
            int codeSmellsBefore,
            int codeSmellsAfter,
            int remediationDebtMinutesBefore,
            int remediationDebtMinutesAfter,
            double duplicationPercentBefore,
            double duplicationPercentAfter,
            int maximumCyclomaticComplexityBefore,
            int maximumCyclomaticComplexityAfter,
            int resolvedFindings,
            int introducedFindings,
            int newCriticalOrHighFindings
    ) {
    }

    public record TestQualityScore(
            double score,
            String grade,
            double evidenceCompletenessPercent,
            Double lineCoverage,
            Double branchCoverage,
            Double mutationScore,
            Double testStrength,
            double changedTestExecutionScore,
            boolean everyChangedTestExecuted,
            int changedTestFileCount,
            int executedChangedTestFileCount
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
            boolean qualityGoalMet,
            JavaQualityService.QualityReport quality,
            QualityDelta qualityDelta,
            MutationTestingService.MutationReport mutation,
            TestQualityScore testQuality,
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StoredRunState(
            int schemaVersion,
            String runId,
            String kind,
            String status,
            String projectRoot,
            String workspaceRoot,
            List<StoredTarget> targets,
            List<StoredFingerprint> projectBaseline,
            List<StoredFingerprint> workspaceBaseline,
            StoredCoverage beforeCoverage,
            double minimumLineCoverage,
            double minimumMutationScore,
            JavaQualityService.QualityReport qualityBefore,
            String createdAt,
            StoredValidation lastValidation,
            List<StoredFingerprint> validatedSnapshot
    ) {
    }

    public record StoredTarget(
            String moduleRelativePath,
            String sourceRelativePath,
            String packageName,
            String className,
            String fullyQualifiedName
    ) {
    }

    public record StoredFingerprint(
            String relativePath,
            long size,
            String sha256,
            boolean symbolicLink
    ) {
    }

    public record StoredCoverage(
            String reportRelativePath,
            double totalLineCoverage,
            double totalBranchCoverage,
            Map<String, CoverageReportService.ClassCoverage> classCoverageByName
    ) {
    }

    public record StoredValidation(
            String runId,
            boolean valid,
            boolean readyToApply,
            List<String> changedRelativePaths,
            List<String> warnings,
            List<String> failures,
            List<String> missingTestReports,
            Boolean coverageGoalMet,
            Map<String, CoverageChange> coverage,
            boolean qualityGoalMet,
            JavaQualityService.QualityReport quality,
            QualityDelta qualityDelta,
            MutationTestingService.MutationReport mutation,
            TestQualityScore testQuality,
            long verificationElapsedMillis
    ) {
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
        private final double minimumMutationScore;
        private final JavaQualityService.QualityReport qualityBefore;
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
                double minimumMutationScore,
                JavaQualityService.QualityReport qualityBefore,
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
            this.minimumMutationScore = minimumMutationScore;
            this.qualityBefore = Objects.requireNonNull(qualityBefore, "qualityBefore");
            this.createdAt = createdAt;
        }
    }
}
