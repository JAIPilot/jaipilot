package com.jaipilot.toolkit.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Proves the current Git Java diff in an isolated copy of the real project. */
public final class DiffVerificationService {

    public static final VerificationThresholds DEFAULT_THRESHOLDS = new VerificationThresholds(
            90.0d,
            85.0d,
            80.0d,
            90.0d
    );

    private final ProjectFileService fileService;
    private final JavaProjectService projectService;
    private final CoverageReportService coverageReportService;
    private final GitChangeService gitChangeService;
    private final TestExecutionReportService testReports;
    private final BuildGate buildGate;
    private final CoverageGate coverageGate;
    private final QualityGate qualityGate;
    private final ArchitectureGate architectureGate;
    private final DiffMutationGate mutationGate;
    private final Consumer<String> progress;

    public DiffVerificationService() {
        this(ignored -> { });
    }

    public DiffVerificationService(Consumer<String> progress) {
        this(new ProjectFileService(), new CoverageReportService(), new GitChangeService(), progress);
    }

    private DiffVerificationService(
            ProjectFileService fileService,
            CoverageReportService coverageReportService,
            GitChangeService gitChangeService,
            Consumer<String> progress
    ) {
        this(
                fileService,
                new JavaProjectService(fileService, coverageReportService),
                coverageReportService,
                gitChangeService,
                defaultGates(fileService, new JavaProjectService(fileService, coverageReportService), coverageReportService),
                progress
        );
    }

    DiffVerificationService(
            ProjectFileService fileService,
            JavaProjectService projectService,
            CoverageReportService coverageReportService,
            GitChangeService gitChangeService,
            VerificationGates gates
    ) {
        this(
                fileService,
                projectService,
                coverageReportService,
                gitChangeService,
                gates,
                ignored -> { }
        );
    }

    DiffVerificationService(
            ProjectFileService fileService,
            JavaProjectService projectService,
            CoverageReportService coverageReportService,
            GitChangeService gitChangeService,
            VerificationGates gates,
            Consumer<String> progress
    ) {
        this.fileService = Objects.requireNonNull(fileService, "fileService");
        this.projectService = Objects.requireNonNull(projectService, "projectService");
        this.coverageReportService = Objects.requireNonNull(coverageReportService, "coverageReportService");
        this.gitChangeService = Objects.requireNonNull(gitChangeService, "gitChangeService");
        this.testReports = new TestExecutionReportService(projectService);
        VerificationGates configured = Objects.requireNonNull(gates, "gates");
        this.buildGate = Objects.requireNonNull(configured.build(), "build gate");
        this.coverageGate = Objects.requireNonNull(configured.coverage(), "coverage gate");
        this.qualityGate = Objects.requireNonNull(configured.quality(), "quality gate");
        this.architectureGate = Objects.requireNonNull(configured.architecture(), "architecture gate");
        this.mutationGate = Objects.requireNonNull(configured.mutation(), "mutation gate");
        this.progress = Objects.requireNonNull(progress, "progress");
    }

    private static VerificationGates defaultGates(
            ProjectFileService fileService,
            JavaProjectService projectService,
            CoverageReportService coverageReportService
    ) {
        CoverageRefreshService coverage = new CoverageRefreshService(projectService, coverageReportService);
        return new VerificationGates(
                new JavaBuildVerificationService(projectService)::verify,
                coverage::refresh,
                new JavaQualityService()::analyze,
                new ArchitectureService(projectService)::analyze,
                new MutationTestingService(projectService, fileService)::run
        );
    }

    public DiffVerification verify(Path requestedRoot, VerificationThresholds thresholds) {
        Objects.requireNonNull(thresholds, "thresholds");
        Path root = realProjectRoot(requestedRoot);
        GitChangeService.DiffSnapshot before = gitChangeService.snapshot(root);
        if (before.proofRelevantPaths().isEmpty()) {
            return emptyVerification(before, thresholds);
        }
        long started = System.nanoTime();
        ProofScope scope = proofScope(root, before);
        reportScope(before);
        VerificationEvidence evidence = runIsolatedProof(scope, thresholds);
        rejectDrift(root, before, evidence.failures);
        DiffVerification result = result(scope, thresholds, evidence, started);
        progress.accept(result.passed() ? "Changed-code proof passed." : "Changed-code proof found actionable gaps.");
        return result;
    }

    private DiffVerification emptyVerification(
            GitChangeService.DiffSnapshot diff,
            VerificationThresholds thresholds
    ) {
        return new DiffVerification(
                diff,
                thresholds,
                true,
                List.of(),
                null,
                null,
                Map.of(),
                Map.of(),
                null,
                null,
                null,
                List.of(),
                List.of("No changed Java production files or build inputs require proof."),
                Duration.ZERO
        );
    }

    private ProofScope proofScope(Path root, GitChangeService.DiffSnapshot diff) {
        List<JavaProjectService.JavaClassDescriptor> liveTargets = diff.existingProductionPaths().stream()
                .map(path -> projectService.resolveClass(root, path.toString()))
                .toList();
        rejectDuplicateClassNames(liveTargets);
        Map<Path, List<GitChangeService.LineRange>> changedLineRanges = gitChangeService.changedLineRanges(diff);
        Map<String, List<GitChangeService.LineRange>> rangesByClass = rangesByClass(root, liveTargets, changedLineRanges);
        List<String> targetNames = liveTargets.stream()
                .map(JavaProjectService.JavaClassDescriptor::fullyQualifiedName)
                .toList();
        List<Path> changedTests = diff.changedJavaPaths().stream()
                .filter(this::isTestPath)
                .filter(path -> Files.isRegularFile(root.resolve(path)))
                .filter(path -> !Files.isSymbolicLink(root.resolve(path)))
                .map(root::resolve)
                .toList();
        long deletedTests = diff.changedJavaPaths().stream()
                .filter(this::isTestPath)
                .filter(path -> Files.notExists(root.resolve(path)))
                .count();
        return new ProofScope(root, diff, liveTargets, changedTests, deletedTests,
                changedLineRanges, rangesByClass, targetNames);
    }

    private void reportScope(GitChangeService.DiffSnapshot diff) {
        progress.accept("Reviewing " + diff.existingProductionPaths().size()
                + " changed Java production file(s) for fingerprint " + diff.fingerprint().substring(0, 12) + ".");
    }

    private VerificationEvidence runIsolatedProof(ProofScope scope, VerificationThresholds thresholds) {
        VerificationEvidence evidence = new VerificationEvidence();
        Path sandbox = null;
        try {
            sandbox = Files.createTempDirectory("jaipilot-diff-proof-");
            fileService.copyProjectWorkspace(scope.root, sandbox);
            List<JavaProjectService.JavaClassDescriptor> sandboxTargets = rebaseTargets(
                    scope.root,
                    sandbox,
                    scope.liveTargets
            );
            analyzeQuality(scope, sandbox, sandboxTargets, thresholds, evidence);
            runBuildEvidence(scope, sandbox, sandboxTargets, scope.rangesByClass, thresholds, evidence);
        } catch (RuntimeException | IOException exception) {
            evidence.failures.add("Isolated diff verification failed: " + rootMessage(exception));
        } finally {
            if (sandbox != null) {
                fileService.deleteRecursively(sandbox);
            }
        }
        return evidence;
    }

    private void analyzeQuality(
            ProofScope scope,
            Path sandbox,
            List<JavaProjectService.JavaClassDescriptor> sandboxTargets,
            VerificationThresholds thresholds,
            VerificationEvidence evidence
    ) throws IOException {
        if (sandboxTargets.isEmpty()) {
            return;
        }
        JavaQualityService.QualityReport baseline = baselineQuality(scope.diff, scope.liveTargets);
        evidence.quality = qualityGate.analyze(
                sandbox,
                sandboxTargets.stream().map(JavaProjectService.JavaClassDescriptor::cutPath).toList()
        );
        evidence.changedQuality = changedQuality(
                scope.root,
                scope.liveTargets,
                scope.changedLineRanges,
                baseline,
                evidence.quality
        );
        evaluateQuality(evidence.changedQuality, evidence.quality, thresholds, evidence.failures);
    }

    private void runBuildEvidence(
            ProofScope scope,
            Path sandbox,
            List<JavaProjectService.JavaClassDescriptor> sandboxTargets,
            Map<String, List<GitChangeService.LineRange>> rangesByClass,
            VerificationThresholds thresholds,
            VerificationEvidence evidence
    ) {
        if (sandboxTargets.isEmpty()) {
            runNoProductionBuild(scope, sandbox, evidence);
            return;
        }
        if (!projectService.supportsCoverage(sandbox)) {
            runBuildWithoutCoverage(scope, sandbox, sandboxTargets, evidence);
            return;
        }
        progress.accept("Running the clean full-suite build and fresh JaCoCo coverage.");
        evidence.coverage = coverageGate.refresh(sandbox, sandboxTargets);
        evidence.buildPassed = true;
        verifyChangedTests(scope, sandbox, evidence);
        evidence.changedCoverage = changedCoverage(evidence.coverage, sandboxTargets, rangesByClass);
        evaluateCoverage(evidence.changedCoverage, thresholds, evidence.failures, evidence.warnings);
        runArchitectureEvidence(sandbox, sandboxTargets, evidence);
        if (evidence.architecture != null && !evidence.architecture.goalMet()) {
            return;
        }
        runMutationEvidence(sandbox, sandboxTargets, rangesByClass, thresholds, evidence);
    }

    private void runNoProductionBuild(ProofScope scope, Path sandbox, VerificationEvidence evidence) {
        runCleanBuild(
                scope,
                sandbox,
                evidence,
                "Running the clean full-suite build for changed test, build, or deleted production inputs."
        );
        evidence.warnings.add(
                "The diff only deletes production code or changes tests/build inputs; "
                        + "coverage, PIT, and ArchUnit are not applicable."
        );
    }

    private void runBuildWithoutCoverage(
            ProofScope scope,
            Path sandbox,
            List<JavaProjectService.JavaClassDescriptor> targets,
            VerificationEvidence evidence
    ) {
        runCleanBuild(scope, sandbox, evidence, "Running the clean full-suite build; JaCoCo is unavailable.");
        runArchitectureEvidence(sandbox, targets, evidence);
        evidence.failures.add(
                "The clean build passed, but fresh JaCoCo XML coverage is unavailable for the changed classes."
        );
    }

    private void runCleanBuild(
            ProofScope scope,
            Path sandbox,
            VerificationEvidence evidence,
            String progressMessage
    ) {
        progress.accept(progressMessage);
        buildGate.verify(sandbox);
        evidence.buildPassed = true;
        verifyChangedTests(scope, sandbox, evidence);
    }

    private void verifyChangedTests(ProofScope scope, Path sandbox, VerificationEvidence evidence) {
        List<Path> sandboxTests = scope.changedTests.stream()
                .map(path -> sandbox.resolve(scope.root.relativize(path)))
                .toList();
        TestExecutionReportService.ExecutionEvidence execution = testReports.inspect(sandbox, sandboxTests);
        if (!execution.missingClasses().isEmpty()) {
            evidence.failures.add("Changed executable test classes have no fresh execution report: "
                    + execution.missingClasses() + ".");
        } else if (!execution.expectedClasses().isEmpty()) {
            evidence.warnings.add("Fresh test execution proved for "
                    + execution.expectedClasses().size() + " changed test class(es).");
        }
        if (!execution.helperSources().isEmpty()) {
            evidence.warnings.add("Changed non-executable test helper sources were clean-build verified: "
                    + execution.helperSources() + ".");
        }
        if (scope.deletedTests > 0) {
            evidence.warnings.add(scope.deletedTests
                    + " deleted test source(s) were verified by the clean build.");
        }
    }

    private boolean isTestPath(Path path) {
        String portable = "/" + path.normalize().toString().replace('\\', '/');
        return portable.contains("/src/test/java/") && portable.endsWith(".java");
    }

    private void runArchitectureEvidence(
            Path sandbox,
            List<JavaProjectService.JavaClassDescriptor> targets,
            VerificationEvidence evidence
    ) {
        progress.accept("Running ArchUnit architecture analysis on freshly compiled changed classes.");
        evidence.architecture = architectureGate.analyze(
                sandbox,
                targets.stream().map(JavaProjectService.JavaClassDescriptor::fullyQualifiedName).toList()
        );
        if (!evidence.architecture.complete()) {
            evidence.failures.add("ArchUnit architecture evidence is incomplete: "
                    + evidence.architecture.incompleteReason());
        } else if (!evidence.architecture.violations().isEmpty()) {
            evidence.failures.add("ArchUnit found " + evidence.architecture.violations().size()
                    + " architecture violation(s) involving changed classes; "
                    + "resolve every item in architecture.violations and rerun proof.");
        }
    }

    private void runMutationEvidence(
            Path sandbox,
            List<JavaProjectService.JavaClassDescriptor> targets,
            Map<String, List<GitChangeService.LineRange>> rangesByClass,
            VerificationThresholds thresholds,
            VerificationEvidence evidence
    ) {
        Map<String, Set<Integer>> lines = mutationLines(rangesByClass);
        if (lines.values().stream().allMatch(Set::isEmpty)) {
            evidence.warnings.add("No remaining changed production lines can be mutation-scored; targeted PIT is not applicable.");
            return;
        }
        try {
            progress.accept("Running targeted PIT and scoring only changed-line mutations.");
            evidence.mutation = mutationGate.run(
                    sandbox,
                    mutationTargets(sandbox, targets),
                    List.of(),
                    thresholds.minimumMutationScore(),
                    lines
            );
            evaluateMutation(evidence.mutation, thresholds, evidence.failures, evidence.warnings);
        } catch (RuntimeException exception) {
            evidence.failures.add("Targeted PIT mutation testing did not complete: " + rootMessage(exception));
        }
    }

    private void rejectDrift(
            Path root,
            GitChangeService.DiffSnapshot before,
            List<String> failures
    ) {
        GitChangeService.DiffSnapshot after = gitChangeService.snapshot(root);
        if (!before.fingerprint().equals(after.fingerprint())) {
            failures.add("The Git Java diff changed during verification; rerun proof against the new fingerprint.");
        }
    }

    private DiffVerification result(
            ProofScope scope,
            VerificationThresholds thresholds,
            VerificationEvidence evidence,
            long started
    ) {
        return new DiffVerification(
                scope.diff,
                thresholds,
                evidence.failures.isEmpty(),
                scope.targetNames,
                evidence.quality,
                evidence.changedQuality,
                targetCoverage(evidence.coverage, scope.targetNames),
                evidence.changedCoverage,
                evidence.architecture,
                evidence.mutation,
                score(evidence.changedCoverage, evidence.mutation, evidence.buildPassed),
                List.copyOf(evidence.failures),
                List.copyOf(evidence.warnings),
                Duration.ofNanos(System.nanoTime() - started)
        );
    }

    private Path realProjectRoot(Path requestedRoot) {
        Path root = projectService.resolveProjectRoot(requestedRoot.toAbsolutePath().normalize());
        try {
            return root.toRealPath();
        } catch (IOException exception) {
            throw new IllegalStateException("Project directory is unavailable: " + root, exception);
        }
    }

    private List<JavaProjectService.JavaClassDescriptor> rebaseTargets(
            Path liveRoot,
            Path sandbox,
            List<JavaProjectService.JavaClassDescriptor> targets
    ) {
        return targets.stream().map(target -> new JavaProjectService.JavaClassDescriptor(
                sandbox,
                sandbox.resolve(liveRoot.relativize(target.moduleRoot())).normalize(),
                sandbox.resolve(liveRoot.relativize(target.cutPath())).normalize(),
                target.packageName(),
                target.className(),
                target.fullyQualifiedName()
        )).toList();
    }

    private JavaQualityService.QualityReport baselineQuality(
            GitChangeService.DiffSnapshot diff,
            List<JavaProjectService.JavaClassDescriptor> targets
    ) throws IOException {
        Path baselineRoot = Files.createTempDirectory("jaipilot-diff-baseline-");
        try {
            List<Path> baselineFiles = new ArrayList<>();
            for (JavaProjectService.JavaClassDescriptor target : targets) {
                Path relative = diff.projectRoot().relativize(target.cutPath());
                gitChangeService.baselineFile(diff, relative).ifPresent(contents -> {
                    Path baselineFile = baselineRoot.resolve(relative).normalize();
                    fileService.writeFile(baselineFile, contents);
                    baselineFiles.add(baselineFile);
                });
            }
            return baselineFiles.isEmpty() ? null : qualityGate.analyze(baselineRoot, baselineFiles);
        } finally {
            fileService.deleteRecursively(baselineRoot);
        }
    }

    private void evaluateQuality(
            ChangedCodeQuality changedQuality,
            JavaQualityService.QualityReport wholeFileQuality,
            VerificationThresholds thresholds,
            List<String> failures
    ) {
        if (!wholeFileQuality.parseFailures().isEmpty()) {
            failures.add("Changed production code could not be parsed for quality analysis: "
                    + wholeFileQuality.parseFailures());
        }
        if (changedQuality.criticalOrHighFindings() > 0) {
            failures.add("New or modified production code has " + changedQuality.criticalOrHighFindings()
                    + " critical/high quality findings.");
        }
        if (changedQuality.score() + 0.000_001d < thresholds.minimumQualityScore()) {
            failures.add("Changed-code quality score " + changedQuality.score()
                    + " is below the required " + thresholds.minimumQualityScore() + ".");
        }
    }

    private ChangedCodeQuality changedQuality(
            Path root,
            List<JavaProjectService.JavaClassDescriptor> targets,
            Map<Path, List<GitChangeService.LineRange>> changedLines,
            JavaQualityService.QualityReport baseline,
            JavaQualityService.QualityReport quality
    ) {
        Map<String, List<GitChangeService.LineRange>> byPath = new LinkedHashMap<>();
        for (JavaProjectService.JavaClassDescriptor target : targets) {
            Path relative = root.relativize(target.cutPath());
            byPath.put(portable(relative), changedLines.getOrDefault(relative, List.of()));
        }
        List<JavaQualityService.Finding> affected = quality.findings().stream()
                .filter(finding -> affectedFinding(finding, byPath, quality.methods()))
                .toList();
        List<JavaQualityService.Finding> baselineFindings = baseline == null ? List.of() : baseline.findings();
        List<JavaQualityService.Finding> findings = affected.stream()
                .filter(finding -> isNewOrMoreSevere(finding, baselineFindings))
                .toList();
        long severe = findings.stream().filter(finding -> finding.severity() == JavaQualityService.Severity.CRITICAL
                || finding.severity() == JavaQualityService.Severity.HIGH).count();
        double penalty = findings.stream().mapToDouble(finding -> switch (finding.severity()) {
            case CRITICAL, HIGH -> 20.0d;
            case MEDIUM -> 3.0d;
            case LOW -> 1.0d;
        }).sum();
        return new ChangedCodeQuality(
                round(Math.max(0.0d, 100.0d - penalty)),
                findings.size(),
                (int) severe,
                baseline == null ? 100.0d : baseline.metrics().qualityScore(),
                quality.metrics().qualityScore(),
                findings
        );
    }

    private boolean isNewOrMoreSevere(
            JavaQualityService.Finding current,
            List<JavaQualityService.Finding> baseline
    ) {
        return baseline.stream().noneMatch(previous -> previous.id().equals(current.id())
                && previous.relativePath().equals(current.relativePath())
                && previous.symbol().equals(current.symbol())
                && previous.severity().ordinal() <= current.severity().ordinal());
    }

    boolean affectedFinding(
            JavaQualityService.Finding finding,
            Map<String, List<GitChangeService.LineRange>> changedLines,
            List<JavaQualityService.MethodMetric> methods
    ) {
        List<GitChangeService.LineRange> ranges = changedLines.getOrDefault(finding.relativePath(), List.of());
        if (contains(ranges, finding.line())) {
            return true;
        }
        return methods.stream()
                .filter(method -> method.relativePath().equals(finding.relativePath()))
                .filter(method -> method.symbol().equals(finding.symbol()))
                .anyMatch(method -> ranges.stream().anyMatch(range -> overlapsMethod(range, method)));
    }

    boolean overlapsMethod(GitChangeService.LineRange range, JavaQualityService.MethodMetric method) {
        int methodEnd = method.line() + Math.max(0, method.lines() - 1);
        return range.start() <= methodEnd && range.end() >= method.line();
    }

    private boolean contains(List<GitChangeService.LineRange> ranges, int line) {
        return ranges.stream().anyMatch(range -> range.contains(line));
    }

    private Map<String, ChangedCodeCoverage> changedCoverage(
            CoverageReportService.CoverageSnapshot coverage,
            List<JavaProjectService.JavaClassDescriptor> targets,
            Map<String, List<GitChangeService.LineRange>> rangesByClass
    ) {
        Map<Path, Map<String, Map<Integer, CoverageReportService.LineCoverage>>> sourceLinesByReport =
                new LinkedHashMap<>();
        Map<String, ChangedCodeCoverage> values = new LinkedHashMap<>();
        for (JavaProjectService.JavaClassDescriptor target : targets) {
            Path report = coverage.reportPathForClass(target.fullyQualifiedName());
            Map<String, Map<Integer, CoverageReportService.LineCoverage>> sourceLines =
                    sourceLinesByReport.computeIfAbsent(report, coverageReportService::readSourceLineCoverage);
            CoverageReportService.ClassCoverage wholeClass = coverage.classCoverageByName()
                    .get(target.fullyQualifiedName());
            if (wholeClass == null) {
                throw new IllegalStateException("Fresh JaCoCo XML omitted changed class "
                        + target.fullyQualifiedName() + ".");
            }
            String sourceKey = target.packageName().isBlank()
                    ? target.cutPath().getFileName().toString()
                    : target.packageName().replace('.', '/') + "/" + target.cutPath().getFileName();
            Map<Integer, CoverageReportService.LineCoverage> lines = sourceLines.get(sourceKey);
            if (lines == null) {
                throw new IllegalStateException("Fresh JaCoCo XML omitted changed source " + sourceKey + ".");
            }
            List<GitChangeService.LineRange> ranges = rangesByClass.getOrDefault(
                    target.fullyQualifiedName(),
                    List.of()
            );
            List<CoverageReportService.LineCoverage> changedExecutable = lines.values().stream()
                    .filter(CoverageReportService.LineCoverage::executable)
                    .filter(line -> contains(ranges, line.line()))
                    .toList();
            int coveredLines = (int) changedExecutable.stream()
                    .filter(CoverageReportService.LineCoverage::covered)
                    .count();
            int branches = changedExecutable.stream().mapToInt(CoverageReportService.LineCoverage::branches).sum();
            int coveredBranches = changedExecutable.stream()
                    .mapToInt(CoverageReportService.LineCoverage::coveredBranches)
                    .sum();
            values.put(target.fullyQualifiedName(), new ChangedCodeCoverage(
                    ranges.stream().mapToInt(GitChangeService.LineRange::count).sum(),
                    changedExecutable.size(),
                    coveredLines,
                    percentage(coveredLines, changedExecutable.size()),
                    branches,
                    coveredBranches,
                    percentage(coveredBranches, branches),
                    wholeClass.lineCoverage(),
                    wholeClass.branchCoverage()
            ));
        }
        return Map.copyOf(values);
    }

    private void rejectDuplicateClassNames(List<JavaProjectService.JavaClassDescriptor> targets) {
        Map<String, Long> counts = targets.stream().collect(Collectors.groupingBy(
                JavaProjectService.JavaClassDescriptor::fullyQualifiedName,
                LinkedHashMap::new,
                Collectors.counting()
        ));
        List<String> duplicates = counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException("Changed classes have duplicate fully qualified names across modules: "
                    + duplicates + ". JAIPilot refuses ambiguous coverage and mutation attribution.");
        }
    }

    private void evaluateCoverage(
            Map<String, ChangedCodeCoverage> coverage,
            VerificationThresholds thresholds,
            List<String> failures,
            List<String> warnings
    ) {
        coverage.forEach((className, value) -> {
            if (value.lineCoverage() == null) {
                warnings.add(className + " has no executable changed lines; line coverage is not applicable.");
            } else if (value.lineCoverage() + 0.000_001d < thresholds.minimumLineCoverage()) {
                failures.add(className + " changed-line coverage " + value.lineCoverage()
                        + "% is below the required " + thresholds.minimumLineCoverage() + "%." );
            }
            if (value.branchCoverage() == null) {
                warnings.add(className + " has no changed branches; branch coverage is not applicable.");
            } else if (value.branchCoverage() + 0.000_001d < thresholds.minimumBranchCoverage()) {
                failures.add(className + " changed-branch coverage " + value.branchCoverage()
                        + "% is below the required " + thresholds.minimumBranchCoverage() + "%." );
            }
        });
    }

    private void evaluateMutation(
            MutationTestingService.MutationReport mutation,
            VerificationThresholds thresholds,
            List<String> failures,
            List<String> warnings
    ) {
        if (!mutation.goalMet()) {
            String score = mutation.mutationScore() == null ? "unscorable" : mutation.mutationScore() + "%";
            failures.add("Targeted PIT mutation score " + score + " did not meet the required "
                    + thresholds.minimumMutationScore() + "% with complete evidence.");
        }
        if (mutation.incompleteReason() != null) {
            warnings.add(mutation.incompleteReason());
        }
        if (mutation.survived() > 0 || mutation.noCoverage() > 0) {
            warnings.add("PIT found " + mutation.survived() + " surviving and " + mutation.noCoverage()
                    + " uncovered mutations in changed production classes.");
        }
    }

    private List<MutationTestingService.MutationTarget> mutationTargets(
            Path sandbox,
            List<JavaProjectService.JavaClassDescriptor> targets
    ) {
        return targets.stream().map(target -> new MutationTestingService.MutationTarget(
                target.moduleRoot(),
                target.fullyQualifiedName(),
                projectService.findLikelyTests(target).stream()
                        .map(JavaProjectService.JavaTestDescriptor::fullyQualifiedName)
                        .toList()
        )).toList();
    }

    private Map<String, List<GitChangeService.LineRange>> rangesByClass(
            Path root,
            List<JavaProjectService.JavaClassDescriptor> targets,
            Map<Path, List<GitChangeService.LineRange>> changedLines
    ) {
        Map<String, List<GitChangeService.LineRange>> values = new LinkedHashMap<>();
        for (JavaProjectService.JavaClassDescriptor target : targets) {
            values.put(
                    target.fullyQualifiedName(),
                    changedLines.getOrDefault(root.relativize(target.cutPath()), List.of())
            );
        }
        return Map.copyOf(values);
    }

    private Map<String, Set<Integer>> mutationLines(
            Map<String, List<GitChangeService.LineRange>> rangesByClass
    ) {
        Map<String, Set<Integer>> values = new LinkedHashMap<>();
        rangesByClass.forEach((className, ranges) -> values.put(
                className,
                ranges.stream()
                        .flatMapToInt(range -> java.util.stream.IntStream.rangeClosed(range.start(), range.end()))
                        .boxed()
                        .collect(Collectors.toUnmodifiableSet())
        ));
        return Map.copyOf(values);
    }

    private Map<String, CoverageReportService.ClassCoverage> targetCoverage(
            CoverageReportService.CoverageSnapshot coverage,
            List<String> targets
    ) {
        if (coverage == null) {
            return Map.of();
        }
        Map<String, CoverageReportService.ClassCoverage> values = new LinkedHashMap<>();
        for (String target : targets) {
            values.put(target, coverage.classCoverageByName().getOrDefault(
                    target,
                    new CoverageReportService.ClassCoverage(target, 0.0d, 0.0d)
            ));
        }
        return Map.copyOf(values);
    }

    private DiffTestScore score(
            Map<String, ChangedCodeCoverage> coverage,
            MutationTestingService.MutationReport mutation,
            boolean buildPassed
    ) {
        if (coverage.isEmpty() && mutation == null) {
            return null;
        }
        Double line = average(coverage.values().stream().map(ChangedCodeCoverage::lineCoverage)
                .filter(Objects::nonNull).toList());
        Double branch = average(coverage.values().stream().map(ChangedCodeCoverage::branchCoverage)
                .filter(Objects::nonNull).toList());
        Double mutationScore = mutation == null ? null : mutation.mutationScore();
        Double strength = mutation == null ? null : mutation.testStrength();
        List<ScoreComponent> components = List.of(
                scoreComponent(line, 25.0d),
                scoreComponent(branch, 20.0d),
                scoreComponent(mutationScore, 35.0d),
                scoreComponent(strength, 15.0d),
                scoreComponent(buildPassed ? 100.0d : null, 5.0d)
        );
        double rounded = round(components.stream().mapToDouble(ScoreComponent::points).sum());
        double completeness = components.stream().mapToDouble(ScoreComponent::completeness).sum();
        return new DiffTestScore(
                rounded,
                grade(rounded),
                round(completeness),
                line,
                branch,
                mutationScore,
                strength,
                buildPassed
        );
    }

    private ScoreComponent scoreComponent(Double value, double weight) {
        if (value == null) {
            return new ScoreComponent(0.0d, 0.0d);
        }
        return new ScoreComponent(value * weight / 100.0d, weight);
    }

    private String grade(double score) {
        if (score >= 90.0d) {
            return "EXCELLENT";
        }
        if (score >= 80.0d) {
            return "GOOD";
        }
        if (score >= 70.0d) {
            return "FAIR";
        }
        return "WEAK";
    }

    private Double average(List<Double> values) {
        return values.isEmpty() ? null : round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d));
    }

    private double round(double value) {
        return Math.round(value * 10.0d) / 10.0d;
    }

    private Double percentage(int covered, int total) {
        return total == 0 ? null : round(100.0d * covered / total);
    }

    private String portable(Path path) {
        return path.normalize().toString().replace('\\', '/');
    }

    private String rootMessage(Throwable throwable) {
        if (throwable.getCause() != null) {
            return rootMessage(throwable.getCause());
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    public record VerificationThresholds(
            double minimumLineCoverage,
            double minimumBranchCoverage,
            double minimumMutationScore,
            double minimumQualityScore
    ) {
        public VerificationThresholds {
            validate(minimumLineCoverage, "minimumLineCoverage");
            validate(minimumBranchCoverage, "minimumBranchCoverage");
            validate(minimumMutationScore, "minimumMutationScore");
            validate(minimumQualityScore, "minimumQualityScore");
        }

        private static void validate(double value, String name) {
            if (!Double.isFinite(value) || value < 0.0d || value > 100.0d) {
                throw new IllegalArgumentException(name + " must be between 0 and 100.");
            }
        }
    }

    public record DiffTestScore(
            double score,
            String grade,
            double evidenceCompletenessPercent,
            Double lineCoverage,
            Double branchCoverage,
            Double mutationScore,
            Double testStrength,
            boolean cleanBuildPassed
    ) {
    }

    public record ChangedCodeCoverage(
            int changedLineCount,
            int executableChangedLineCount,
            int coveredChangedLineCount,
            Double lineCoverage,
            int changedBranchCount,
            int coveredChangedBranchCount,
            Double branchCoverage,
            double wholeClassLineCoverage,
            double wholeClassBranchCoverage
    ) {
    }

    public record ChangedCodeQuality(
            double score,
            int findingCount,
            int criticalOrHighFindings,
            double wholeFileQualityScoreBefore,
            double wholeFileQualityScoreAfter,
            List<JavaQualityService.Finding> findings
    ) {
    }

    public record DiffVerification(
            GitChangeService.DiffSnapshot diff,
            VerificationThresholds thresholds,
            boolean passed,
            List<String> targets,
            JavaQualityService.QualityReport quality,
            ChangedCodeQuality changedQuality,
            Map<String, CoverageReportService.ClassCoverage> coverage,
            Map<String, ChangedCodeCoverage> changedCoverage,
            ArchitectureService.ArchitectureReport architecture,
            MutationTestingService.MutationReport mutation,
            DiffTestScore testQuality,
            List<String> failures,
            List<String> warnings,
            Duration verificationElapsed
    ) {
    }

    private record ProofScope(
            Path root,
            GitChangeService.DiffSnapshot diff,
            List<JavaProjectService.JavaClassDescriptor> liveTargets,
            List<Path> changedTests,
            long deletedTests,
            Map<Path, List<GitChangeService.LineRange>> changedLineRanges,
            Map<String, List<GitChangeService.LineRange>> rangesByClass,
            List<String> targetNames
    ) {
    }

    private record ScoreComponent(double points, double completeness) {
    }

    private static final class VerificationEvidence {
        private final List<String> failures = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private JavaQualityService.QualityReport quality;
        private ChangedCodeQuality changedQuality;
        private CoverageReportService.CoverageSnapshot coverage;
        private Map<String, ChangedCodeCoverage> changedCoverage = Map.of();
        private ArchitectureService.ArchitectureReport architecture;
        private MutationTestingService.MutationReport mutation;
        private boolean buildPassed;
    }

    record VerificationGates(
            BuildGate build,
            CoverageGate coverage,
            QualityGate quality,
            ArchitectureGate architecture,
            DiffMutationGate mutation
    ) {
        VerificationGates(
                BuildGate build,
                CoverageGate coverage,
                QualityGate quality,
                DiffMutationGate mutation
        ) {
            this(build, coverage, quality, (projectRoot, targets) -> new ArchitectureService.ArchitectureReport(
                    "ArchUnit",
                    ArchitectureService.ARCHUNIT_VERSION,
                    ArchitectureService.RULESET_VERSION,
                    List.of(ArchitectureService.PACKAGE_CYCLE_RULE),
                    true,
                    targets.size(),
                    List.of(),
                    List.copyOf(targets),
                    List.of(),
                    List.of(),
                    null,
                    0L
            ), mutation);
        }
    }

    @FunctionalInterface
    interface BuildGate {
        JavaBuildVerificationService.VerificationResult verify(Path projectRoot);
    }

    @FunctionalInterface
    interface CoverageGate {
        CoverageReportService.CoverageSnapshot refresh(
                Path projectRoot,
                List<JavaProjectService.JavaClassDescriptor> targets
        );
    }

    @FunctionalInterface
    interface QualityGate {
        JavaQualityService.QualityReport analyze(Path projectRoot, List<Path> targets);
    }

    @FunctionalInterface
    interface ArchitectureGate {
        ArchitectureService.ArchitectureReport analyze(Path projectRoot, List<String> targetClasses);
    }

    @FunctionalInterface
    interface DiffMutationGate {
        MutationTestingService.MutationReport run(
                Path projectRoot,
                List<MutationTestingService.MutationTarget> targets,
                List<Path> changedTests,
                double minimumMutationScore,
                Map<String, Set<Integer>> includedLinesByClass
        );
    }
}
