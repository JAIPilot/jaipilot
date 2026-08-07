package com.jaipilot.toolkit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiffVerificationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void provesChangedClassesWithFreshCoverageQualityAndMutationEvidence() throws Exception {
        Path root = changedProject("passing");
        ProjectFileService files = new ProjectFileService();
        CoverageReportService reports = new CoverageReportService();
        JavaProjectService projects = new JavaProjectService(files, reports);
        AtomicInteger coverageRuns = new AtomicInteger();
        AtomicInteger mutationRuns = new AtomicInteger();
        DiffVerificationService service = new DiffVerificationService(
                files,
                projects,
                reports,
                new GitChangeService(),
                new DiffVerificationService.VerificationGates(
                        project -> {
                            throw new AssertionError("Coverage proof should own the clean build for existing targets.");
                        },
                        project -> {
                            coverageRuns.incrementAndGet();
                            return coverage(project, 95.0d, 90.0d);
                        },
                        new JavaQualityService()::analyze,
                        (project, targets, tests, minimum, lines) -> {
                            mutationRuns.incrementAndGet();
                            assertEquals(80.0d, minimum);
                            assertTrue(lines.get("com.example.OrderService").contains(1));
                            return mutation(minimum, 90.0d, 95.0d, true);
                        }
                )
        );

        DiffVerificationService.DiffVerification proof = service.verify(
                root,
                DiffVerificationService.DEFAULT_THRESHOLDS
        );

        assertTrue(proof.passed());
        assertEquals(List.of("com.example.OrderService"), proof.targets());
        assertEquals(100.0d, proof.changedCoverage().get("com.example.OrderService").lineCoverage());
        assertEquals(90.0d, proof.mutation().mutationScore());
        assertEquals("EXCELLENT", proof.testQuality().grade());
        assertEquals(1, coverageRuns.get());
        assertEquals(1, mutationRuns.get());
    }

    @Test
    void reportsEveryFailedChangedCodeGateWithoutWritingToLiveSource() throws Exception {
        Path root = changedProject("failing");
        Path liveSource = root.resolve("src/main/java/com/example/OrderService.java");
        String before = Files.readString(liveSource);
        ProjectFileService files = new ProjectFileService();
        CoverageReportService reports = new CoverageReportService();
        JavaProjectService projects = new JavaProjectService(files, reports);
        DiffVerificationService service = new DiffVerificationService(
                files,
                projects,
                reports,
                new GitChangeService(),
                new DiffVerificationService.VerificationGates(
                        project -> null,
                        project -> coverage(project, 89.0d, 70.0d),
                        new JavaQualityService()::analyze,
                        (project, targets, tests, minimum, lines) -> mutation(minimum, 60.0d, 75.0d, false)
                )
        );

        DiffVerificationService.DiffVerification proof = service.verify(
                root,
                DiffVerificationService.DEFAULT_THRESHOLDS
        );

        assertFalse(proof.passed());
        assertTrue(proof.failures().stream().anyMatch(value -> value.contains("changed-line coverage")));
        assertTrue(proof.failures().stream().anyMatch(value -> value.contains("changed-branch coverage")));
        assertTrue(proof.failures().stream().anyMatch(value -> value.contains("PIT mutation score")));
        assertEquals("WEAK", proof.testQuality().grade());
        assertEquals(before, Files.readString(liveSource));
    }

    @Test
    void preExistingSevereFindingIsReportedButDoesNotBecomeNewCodeDebt() throws Exception {
        Path root = changedProject(
                "baseline-debt",
                "package com.example; class OrderService { public static Object shared; int value() { return 1; } }\n",
                "package com.example; class OrderService { public static Object shared; int value() { return 2; } }\n"
        );
        DiffVerificationService service = passingService();

        DiffVerificationService.DiffVerification proof = service.verify(
                root,
                DiffVerificationService.DEFAULT_THRESHOLDS
        );

        assertTrue(proof.quality().findings().stream()
                .anyMatch(finding -> finding.severity() == JavaQualityService.Severity.HIGH));
        assertEquals(0, proof.changedQuality().criticalOrHighFindings());
        assertTrue(proof.passed());
    }

    @Test
    void newlyIntroducedSevereFindingBlocksChangedCodeProof() throws Exception {
        Path root = changedProject(
                "new-debt",
                "package com.example; class OrderService { int value() { return 1; } }\n",
                "package com.example; class OrderService { public static Object shared; int value() { return 2; } }\n"
        );
        DiffVerificationService service = passingService();

        DiffVerificationService.DiffVerification proof = service.verify(
                root,
                DiffVerificationService.DEFAULT_THRESHOLDS
        );

        assertFalse(proof.passed());
        assertEquals(1, proof.changedQuality().criticalOrHighFindings());
        assertTrue(proof.failures().stream().anyMatch(value -> value.contains("critical/high")));
    }

    @Test
    void unchangedRepositoryNeedsNoExpensiveEvidence() throws Exception {
        Path root = changedProject("unchanged");
        git(root, "reset", "--hard", "HEAD^");

        DiffVerificationService.DiffVerification proof = passingService().verify(
                root,
                DiffVerificationService.DEFAULT_THRESHOLDS
        );

        assertTrue(proof.passed());
        assertTrue(proof.targets().isEmpty());
        assertNull(proof.testQuality());
        assertTrue(proof.warnings().get(0).contains("No changed Java production files"));
    }

    @Test
    void deletionOnlyDiffRunsTheBuildWithoutInventingCoverage() throws Exception {
        Path root = changedProject("deletion");
        Files.delete(root.resolve("src/main/java/com/example/OrderService.java"));
        commit(root, "delete production class");
        AtomicInteger builds = new AtomicInteger();
        DiffVerificationService service = service(new DiffVerificationService.VerificationGates(
                project -> {
                    builds.incrementAndGet();
                    return null;
                },
                project -> {
                    throw new AssertionError("Coverage is not applicable to a deletion-only diff.");
                },
                new JavaQualityService()::analyze,
                (project, targets, tests, minimum, lines) -> {
                    throw new AssertionError("PIT is not applicable to a deletion-only diff.");
                }
        ));

        DiffVerificationService.DiffVerification proof = service.verify(
                root,
                DiffVerificationService.DEFAULT_THRESHOLDS
        );

        assertTrue(proof.passed());
        assertEquals(1, builds.get());
        assertTrue(proof.warnings().get(0).contains("only deletes production code"));
    }

    @Test
    void missingJacocoFailsAfterTheCleanBuild() throws Exception {
        Path root = changedProject("without-jacoco");
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        AtomicInteger builds = new AtomicInteger();
        DiffVerificationService service = service(new DiffVerificationService.VerificationGates(
                project -> {
                    builds.incrementAndGet();
                    return null;
                },
                project -> {
                    throw new AssertionError("Coverage refresh must not run without JaCoCo configuration.");
                },
                new JavaQualityService()::analyze,
                (project, targets, tests, minimum, lines) -> {
                    throw new AssertionError("PIT must not run without fresh coverage.");
                }
        ));

        DiffVerificationService.DiffVerification proof = service.verify(
                root,
                DiffVerificationService.DEFAULT_THRESHOLDS
        );

        assertFalse(proof.passed());
        assertEquals(1, builds.get());
        assertTrue(proof.failures().stream().anyMatch(value -> value.contains("JaCoCo XML coverage")));
    }

    @Test
    void pitExecutionFailureIsAnExplicitProofFailure() throws Exception {
        Path root = changedProject("pit-failure");
        DiffVerificationService service = service(new DiffVerificationService.VerificationGates(
                project -> null,
                project -> coverage(project, 100.0d, 100.0d),
                new JavaQualityService()::analyze,
                (project, targets, tests, minimum, lines) -> {
                    throw new IllegalStateException("wrapper", new java.io.IOException("fixture PIT failure"));
                }
        ));

        DiffVerificationService.DiffVerification proof = service.verify(
                root,
                DiffVerificationService.DEFAULT_THRESHOLDS
        );

        assertFalse(proof.passed());
        assertTrue(proof.failures().stream().anyMatch(value -> value.contains("fixture PIT failure")));
    }

    @Test
    void liveDiffDriftDuringPitInvalidatesOtherwisePassingEvidence() throws Exception {
        Path root = changedProject("drift");
        Path source = root.resolve("src/main/java/com/example/OrderService.java");
        DiffVerificationService service = service(
                new DiffVerificationService.VerificationGates(
                        project -> null,
                        project -> coverage(project, 100.0d, 100.0d),
                        new JavaQualityService()::analyze,
                        (project, targets, tests, minimum, lines) -> mutation(
                                minimum,
                                100.0d,
                                100.0d,
                                true
                        )
                ),
                message -> {
                    if (message.contains("targeted PIT")) {
                        try {
                            Files.writeString(source, Files.readString(source) + "// concurrent change\n");
                        } catch (java.io.IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    }
                }
        );

        DiffVerificationService.DiffVerification proof = service.verify(
                root,
                DiffVerificationService.DEFAULT_THRESHOLDS
        );

        assertFalse(proof.passed());
        assertTrue(proof.failures().stream().anyMatch(value -> value.contains("changed during verification")));
    }

    @Test
    void newlyAddedClassUsesAnEmptyBaselineWithoutInventingLegacyDebt() throws Exception {
        Path root = tempDir.resolve("new-class");
        Files.createDirectories(root);
        Files.writeString(root.resolve("pom.xml"), "<project><!-- jacoco --></project>\n");
        git(root, "init", "-q", "-b", "main");
        git(root, "config", "user.name", "JAIPilot Test");
        git(root, "config", "user.email", "test@jaipilot.local");
        commit(root, "baseline");
        Path source = root.resolve("src/main/java/com/example/OrderService.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package com.example; class OrderService { int value() { return 1; } }\n");

        DiffVerificationService.DiffVerification proof = passingService().verify(
                root,
                DiffVerificationService.DEFAULT_THRESHOLDS
        );

        assertTrue(proof.passed());
        assertEquals(100.0d, proof.changedQuality().wholeFileQualityScoreBefore());
    }

    @Test
    void modeOnlyJavaChangeDoesNotRunPitWithoutChangedSourceLines() throws Exception {
        Path root = changedProject("mode-only");
        Path source = root.resolve("src/main/java/com/example/OrderService.java");
        assertTrue(source.toFile().setExecutable(true, false));
        commit(root, "change source mode only");
        DiffVerificationService service = service(new DiffVerificationService.VerificationGates(
                project -> null,
                project -> coverage(project, 100.0d, 100.0d),
                new JavaQualityService()::analyze,
                (project, targets, tests, minimum, lines) -> {
                    throw new AssertionError("PIT must not run without changed source lines.");
                }
        ));

        DiffVerificationService.DiffVerification proof = service.verify(
                root,
                DiffVerificationService.DEFAULT_THRESHOLDS
        );

        assertTrue(proof.passed());
        assertNull(proof.mutation());
        assertTrue(proof.warnings().stream().anyMatch(value -> value.contains("mutation-scored")));
    }

    @Test
    void nonExecutableChangedLinesAreReportedAsNotApplicable() throws Exception {
        Path root = changedProject("declaration-only");
        DiffVerificationService service = service(new DiffVerificationService.VerificationGates(
                project -> null,
                project -> coverage(project, 100.0d, 100.0d, 2),
                new JavaQualityService()::analyze,
                (project, targets, tests, minimum, lines) -> mutation(minimum, 100.0d, 100.0d, true)
        ));

        DiffVerificationService.DiffVerification proof = service.verify(
                root,
                DiffVerificationService.DEFAULT_THRESHOLDS
        );

        assertTrue(proof.passed());
        assertNull(proof.changedCoverage().get("com.example.OrderService").lineCoverage());
        assertTrue(proof.warnings().stream().anyMatch(value -> value.contains("no executable changed lines")));
        assertTrue(proof.warnings().stream().anyMatch(value -> value.contains("no changed branches")));
    }

    @Test
    void malformedChangedJavaFailsTheQualityGate() throws Exception {
        Path root = changedProject(
                "malformed-java",
                "package com.example; class OrderService { int value() { return 1; } }\n",
                "package com.example; class OrderService { int value( }\n"
        );

        DiffVerificationService.DiffVerification proof = passingService().verify(
                root,
                DiffVerificationService.DEFAULT_THRESHOLDS
        );

        assertFalse(proof.passed());
        assertTrue(proof.failures().stream().anyMatch(value -> value.contains("could not be parsed")));
    }

    @Test
    void cleanBuildFailureIsPreservedAsTheProofCause() throws Exception {
        Path root = changedProject("build-failure");
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        DiffVerificationService service = service(new DiffVerificationService.VerificationGates(
                project -> {
                    throw new IllegalStateException("fixture clean-build failure");
                },
                project -> {
                    throw new AssertionError("Coverage must not run after a failed build.");
                },
                new JavaQualityService()::analyze,
                (project, targets, tests, minimum, lines) -> {
                    throw new AssertionError("PIT must not run after a failed build.");
                }
        ));

        DiffVerificationService.DiffVerification proof = service.verify(
                root,
                DiffVerificationService.DEFAULT_THRESHOLDS
        );

        assertFalse(proof.passed());
        assertNull(proof.testQuality());
        assertTrue(proof.failures().stream().anyMatch(value -> value.contains("fixture clean-build failure")));
    }

    @Test
    void progressReportsEveryExpensivePhaseWithoutTouchingStdout() throws Exception {
        Path root = changedProject("progress");
        List<String> progress = new ArrayList<>();
        DiffVerificationService service = service(
                new DiffVerificationService.VerificationGates(
                        project -> null,
                        project -> coverage(project, 100.0d, 100.0d),
                        new JavaQualityService()::analyze,
                        (project, targets, tests, minimum, lines) -> mutation(
                                minimum,
                                100.0d,
                                100.0d,
                                true
                        )
                ),
                progress::add
        );

        DiffVerificationService.DiffVerification proof = service.verify(
                root,
                DiffVerificationService.DEFAULT_THRESHOLDS
        );

        assertTrue(proof.passed());
        assertEquals(5, progress.size());
        assertTrue(progress.get(0).startsWith("Reviewing 1 changed Java production file"));
        assertTrue(progress.get(1).contains("clean full-suite build"));
        assertTrue(progress.get(2).contains("ArchUnit architecture analysis"));
        assertTrue(progress.get(3).contains("targeted PIT"));
        assertEquals("Changed-code proof passed.", progress.get(4));
    }

    @Test
    void architectureViolationsBlockDiffProofAndReturnActionableEvidence() throws Exception {
        Path root = changedProject("architecture-failure");
        AtomicInteger mutations = new AtomicInteger();
        ArchitectureService.ArchitectureViolation violation = new ArchitectureService.ArchitectureViolation(
                ArchitectureService.PACKAGE_CYCLE_RULE,
                "HIGH",
                "com.example.OrderService",
                "com.example.inventory.Inventory",
                "src/main/java/com/example/OrderService.java",
                1,
                List.of("com.example", "com.example.inventory", "com.example"),
                List.of("com.example.OrderService"),
                "Package cycle com.example -> com.example.inventory -> com.example.",
                "Invert the dependency."
        );
        DiffVerificationService service = service(new DiffVerificationService.VerificationGates(
                project -> null,
                project -> coverage(project, 100.0d, 100.0d),
                new JavaQualityService()::analyze,
                (project, targets) -> architectureReport(targets, List.of(violation)),
                (project, targets, tests, minimum, lines) -> {
                    mutations.incrementAndGet();
                    return mutation(minimum, 100.0d, 100.0d, true);
                }
        ));

        DiffVerificationService.DiffVerification proof = service.verify(
                root,
                DiffVerificationService.DEFAULT_THRESHOLDS
        );

        assertFalse(proof.passed());
        assertEquals(0, mutations.get());
        assertEquals(List.of(violation), proof.architecture().violations());
        assertTrue(proof.failures().stream().anyMatch(message -> message.contains("architecture violation")));
    }

    @Test
    void compositeScoreGradeBoundariesRemainTransparent() throws Exception {
        Path root = changedProject("fair-score");
        DiffVerificationService service = service(new DiffVerificationService.VerificationGates(
                project -> null,
                project -> coverage(project, 100.0d, 100.0d),
                new JavaQualityService()::analyze,
                (project, targets, tests, minimum, lines) -> mutation(minimum, 57.2d, 0.0d, true)
        ));

        DiffVerificationService.DiffVerification proof = service.verify(
                root,
                new DiffVerificationService.VerificationThresholds(0, 0, 0, 0)
        );

        assertTrue(proof.passed());
        assertEquals(70.0d, proof.testQuality().score());
        assertEquals("FAIR", proof.testQuality().grade());
        assertEquals(100.0d, proof.testQuality().evidenceCompletenessPercent());

        Path goodRoot = changedProject("good-score");
        DiffVerificationService goodService = service(new DiffVerificationService.VerificationGates(
                project -> null,
                project -> coverage(project, 100.0d, 100.0d),
                new JavaQualityService()::analyze,
                (project, targets, tests, minimum, lines) -> mutation(minimum, 60.0d, 75.0d, true)
        ));
        DiffVerificationService.DiffVerification good = goodService.verify(
                goodRoot,
                new DiffVerificationService.VerificationThresholds(0, 0, 0, 0)
        );
        assertEquals(82.3d, good.testQuality().score());
        assertEquals("GOOD", good.testQuality().grade());
    }

    @Test
    void qualityFindingIsAffectedByADirectLineOrAnOverlappingChangedMethod() {
        DiffVerificationService service = passingService();
        JavaQualityService.Finding finding = new JavaQualityService.Finding(
                "JAI-TEST",
                JavaQualityService.Category.CODE_SMELL,
                JavaQualityService.Severity.MEDIUM,
                "src/main/java/com/example/OrderService.java",
                2,
                "calculate()",
                "fixture",
                "fixture",
                false,
                5
        );
        JavaQualityService.MethodMetric method = new JavaQualityService.MethodMetric(
                finding.relativePath(),
                5,
                finding.symbol(),
                10,
                5,
                2,
                2,
                1
        );

        assertTrue(service.affectedFinding(
                finding,
                Map.of(finding.relativePath(), List.of(new GitChangeService.LineRange(2, 2))),
                List.of()
        ));
        assertTrue(service.affectedFinding(
                finding,
                Map.of(finding.relativePath(), List.of(new GitChangeService.LineRange(10, 10))),
                List.of(method)
        ));
        assertFalse(service.affectedFinding(
                finding,
                Map.of(finding.relativePath(), List.of(new GitChangeService.LineRange(20, 20))),
                List.of(method)
        ));
        assertTrue(service.overlapsMethod(new GitChangeService.LineRange(14, 14), method));
        assertFalse(service.overlapsMethod(new GitChangeService.LineRange(15, 15), method));
    }

    @Test
    void everyChangedCodeThresholdRejectsInvalidPercentages() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiffVerificationService.VerificationThresholds(-1, 85, 80, 90));
        assertThrows(IllegalArgumentException.class,
                () -> new DiffVerificationService.VerificationThresholds(90, 101, 80, 90));
        assertThrows(IllegalArgumentException.class,
                () -> new DiffVerificationService.VerificationThresholds(90, 85, Double.NaN, 90));
        assertThrows(IllegalArgumentException.class,
                () -> new DiffVerificationService.VerificationThresholds(90, 85, 80, Double.POSITIVE_INFINITY));
    }

    @Test
    void unscorableMutationEvidenceFailsWithAnExplicitReason() throws Exception {
        Path root = changedProject("unscorable-mutation");
        DiffVerificationService service = service(new DiffVerificationService.VerificationGates(
                project -> null,
                project -> coverage(project, 100.0d, 100.0d),
                new JavaQualityService()::analyze,
                (project, targets, tests, minimum, lines) -> new MutationTestingService.MutationReport(
                        MutationTestingService.PITEST_VERSION,
                        true,
                        minimum,
                        null,
                        null,
                        false,
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
                        "No scorable mutations were generated.",
                        1L
                )
        ));

        DiffVerificationService.DiffVerification proof = service.verify(
                root,
                DiffVerificationService.DEFAULT_THRESHOLDS
        );

        assertFalse(proof.passed());
        assertTrue(proof.failures().stream().anyMatch(value -> value.contains("unscorable")));
        assertTrue(proof.warnings().contains("No scorable mutations were generated."));
        assertEquals(50.0d, proof.testQuality().evidenceCompletenessPercent());
    }

    private DiffVerificationService passingService() {
        return service(new DiffVerificationService.VerificationGates(
                project -> null,
                project -> coverage(project, 100.0d, 100.0d),
                new JavaQualityService()::analyze,
                (project, targets, tests, minimum, lines) -> mutation(minimum, 100.0d, 100.0d, true)
        ));
    }

    private DiffVerificationService service(DiffVerificationService.VerificationGates gates) {
        return service(gates, ignored -> { });
    }

    private DiffVerificationService service(
            DiffVerificationService.VerificationGates gates,
            java.util.function.Consumer<String> progress
    ) {
        ProjectFileService files = new ProjectFileService();
        CoverageReportService reports = new CoverageReportService();
        JavaProjectService projects = new JavaProjectService(files, reports);
        return new DiffVerificationService(
                files,
                projects,
                reports,
                new GitChangeService(),
                gates,
                progress
        );
    }

    private Path changedProject(String name) throws Exception {
        return changedProject(
                name,
                "package com.example; class OrderService { int value() { return 1; } }\n",
                "package com.example; class OrderService { int value() { return 2; } }\n"
        );
    }

    private Path changedProject(String name, String baseline, String changed) throws Exception {
        Path root = tempDir.resolve(name);
        Files.createDirectories(root);
        Files.writeString(root.resolve("pom.xml"), "<project><!-- jacoco --></project>\n");
        Path source = root.resolve("src/main/java/com/example/OrderService.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, baseline);
        git(root, "init", "-q", "-b", "main");
        git(root, "config", "user.name", "JAIPilot Test");
        git(root, "config", "user.email", "test@jaipilot.local");
        commit(root, "baseline");
        Files.writeString(source, changed);
        commit(root, "changed behavior");
        return root;
    }

    private CoverageReportService.CoverageSnapshot coverage(Path root, double line, double branch) {
        return coverage(root, line, branch, 1);
    }

    private ArchitectureService.ArchitectureReport architectureReport(
            List<String> targets,
            List<ArchitectureService.ArchitectureViolation> violations
    ) {
        return new ArchitectureService.ArchitectureReport(
                "ArchUnit",
                ArchitectureService.ARCHUNIT_VERSION,
                ArchitectureService.RULESET_VERSION,
                List.of(ArchitectureService.PACKAGE_CYCLE_RULE),
                true,
                2,
                List.of("target/classes"),
                targets,
                List.of(),
                violations,
                null,
                1L
        );
    }

    private CoverageReportService.CoverageSnapshot coverage(
            Path root,
            double line,
            double branch,
            int sourceLine
    ) {
        String name = "com.example.OrderService";
        Path report = root.resolve("target/site/jacoco/jacoco.xml");
        try {
            Files.createDirectories(report.getParent());
            Files.writeString(report, """
                    <report name="fixture">
                      <package name="com/example">
                        <class name="com/example/OrderService">
                          <counter type="LINE" missed="1" covered="9"/>
                          <counter type="BRANCH" missed="1" covered="9"/>
                        </class>
                        <sourcefile name="OrderService.java">
                          <line nr="%d" mi="%d" ci="%d" mb="%d" cb="%d"/>
                        </sourcefile>
                      </package>
                    </report>
                    """.formatted(
                    sourceLine,
                    line >= 90.0d ? 0 : 1,
                    line >= 90.0d ? 1 : 0,
                    branch >= 85.0d ? 0 : 1,
                    branch >= 85.0d ? 1 : 0
            ));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
        return new CoverageReportService.CoverageSnapshot(
                report,
                line,
                branch,
                Map.of(name, new CoverageReportService.ClassCoverage(name, line, branch))
        );
    }

    private MutationTestingService.MutationReport mutation(
            double minimum,
            double score,
            double strength,
            boolean passed
    ) {
        return new MutationTestingService.MutationReport(
                MutationTestingService.PITEST_VERSION,
                true,
                minimum,
                score,
                strength,
                passed,
                10,
                passed ? 10 : 4,
                passed ? 0 : 4,
                passed ? 0 : 2,
                0,
                0,
                0,
                0,
                List.of(),
                List.of("target/jaipilot-pit/mutations.xml"),
                List.of(List.of("pitest")),
                passed ? null : "Fixture mutation evidence is incomplete.",
                1_000_000L
        );
    }

    private void commit(Path root, String message) throws Exception {
        git(root, "add", ".");
        git(root, "commit", "-qm", message);
    }

    private void git(Path root, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(root.toFile()).start();
        int status = process.waitFor();
        String errors = new String(process.getErrorStream().readAllBytes());
        assertEquals(0, status, errors);
    }
}
