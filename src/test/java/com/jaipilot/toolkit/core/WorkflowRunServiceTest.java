package com.jaipilot.toolkit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowRunServiceTest {

    @TempDir
    Path tempDir;

    private ProjectFileService fileService;
    private CoverageReportService coverageService;
    private JavaProjectService projectService;
    private AtomicInteger builds;
    private WorkflowRunService service;

    @BeforeEach
    void setUp() throws Exception {
        fileService = new ProjectFileService();
        coverageService = new CoverageReportService();
        projectService = new JavaProjectService(fileService, coverageService);
        builds = new AtomicInteger();
        createProject(false);
        service = createService((root, targets) -> successfulRewrite());
    }

    @AfterEach
    void tearDown() {
        service.close();
    }

    @Test
    void generatesValidatesAndAppliesTestsTransactionally() throws Exception {
        WorkflowRunService.PreparedRun run = service.prepareTestGeneration(
                tempDir,
                WorkflowRunService.TargetSelection.classes(List.of("OrderService")),
                80.0d
        );
        Path test = writeTest(run.workspaceRoot(), "OrderServiceTest", "class OrderServiceTest {}\n");
        writeExecutedReport(run.workspaceRoot(), "com.example.OrderServiceTest");

        WorkflowRunService.ValidationResult validation = service.validate(run.runId());

        assertTrue(validation.valid());
        assertTrue(validation.readyToApply());
        assertEquals(List.of(Path.of("src/test/java/com/example/OrderServiceTest.java")),
                validation.changedRelativePaths());
        assertTrue(validation.missingTestReports().isEmpty());
        String candidateContents = Files.readString(test);
        WorkflowRunService.AppliedRun applied = service.apply(run.runId());
        assertEquals(validation.changedRelativePaths(), applied.changedRelativePaths());
        assertEquals(candidateContents, Files.readString(tempDir.resolve(validation.changedRelativePaths().get(0))));
        assertFalse(Files.exists(run.workspaceRoot()));
    }

    @Test
    void exportedRunContinuesAcrossAgentCommandProcesses() throws Exception {
        WorkflowRunService original = service;
        WorkflowRunService.PreparedRun run = prepareTests();
        writeTest(run.workspaceRoot(), "OrderServiceTest", "class OrderServiceTest {}\n");
        writeExecutedReport(run.workspaceRoot(), "com.example.OrderServiceTest");
        WorkflowRunService.StoredRunState stored = original.exportRun(run.runId());
        ObjectMapper mapper = new ObjectMapper();
        stored = mapper.readValue(
                mapper.writeValueAsBytes(stored),
                WorkflowRunService.StoredRunState.class
        );

        WorkflowRunService restored = createService((root, targets) -> successfulRewrite());
        restored.restoreRun(stored);
        service = restored;

        WorkflowRunService.ValidationResult validation = restored.validate(run.runId());
        assertTrue(validation.readyToApply());
        WorkflowRunService.StoredRunState validated = restored.exportRun(run.runId());
        validated = mapper.readValue(
                mapper.writeValueAsBytes(validated),
                WorkflowRunService.StoredRunState.class
        );
        assertEquals(WorkflowRunService.RunStatus.VALIDATED.name(), validated.status());
        assertNotNull(validated.validatedSnapshot());

        WorkflowRunService finalProcess = createService((root, targets) -> successfulRewrite());
        finalProcess.restoreRun(validated);
        finalProcess.apply(run.runId());
        assertTrue(Files.isRegularFile(tempDir.resolve("src/test/java/com/example/OrderServiceTest.java")));
        assertFalse(Files.exists(run.workspaceRoot()));

        original.close();
        restored.close();
        service = finalProcess;
    }

    @Test
    void testGenerationRejectsProductionEdits() throws Exception {
        WorkflowRunService.PreparedRun run = prepareTests();
        Files.writeString(
                run.workspaceRoot().resolve("src/main/java/com/example/OrderService.java"),
                "package com.example; class OrderService { int changed; }\n"
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.validate(run.runId())
        );

        assertTrue(failure.getMessage().contains("outside the workflow allowlist"));
        assertFalse(Files.readString(tempDir.resolve("src/main/java/com/example/OrderService.java")).contains("changed"));
    }

    @Test
    void testGenerationRejectsDeletedTests() throws Exception {
        Path existing = writeTest(tempDir, "OrderServiceTest", "class OrderServiceTest {}\n");
        WorkflowRunService.PreparedRun run = prepareTests();
        Files.delete(run.workspaceRoot().resolve(tempDir.relativize(existing)));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.validate(run.runId())
        );

        assertTrue(failure.getMessage().contains("deletions are not allowed"));
    }

    @Test
    void missingExecutionReportKeepsCandidateUnappliable() throws Exception {
        WorkflowRunService.PreparedRun run = prepareTests();
        writeTest(run.workspaceRoot(), "OrderServiceTest", "class OrderServiceTest {}\n");

        WorkflowRunService.ValidationResult validation = service.validate(run.runId());

        assertFalse(validation.valid());
        assertFalse(validation.readyToApply());
        assertEquals(List.of("com.example.OrderServiceTest"), validation.missingTestReports());
        assertThrows(IllegalStateException.class, () -> service.apply(run.runId()));
    }

    @Test
    void noChangesCanNeverBeApplied() {
        WorkflowRunService.PreparedRun run = prepareTests();

        WorkflowRunService.ValidationResult validation = service.validate(run.runId());

        assertTrue(validation.valid());
        assertFalse(validation.readyToApply());
        assertTrue(validation.warnings().get(0).contains("no source changes"));
    }

    @Test
    void candidateMustBeRevalidatedAfterAnyEdit() throws Exception {
        WorkflowRunService.PreparedRun run = prepareTests();
        Path test = writeTest(run.workspaceRoot(), "OrderServiceTest", "class OrderServiceTest {}\n");
        writeExecutedReport(run.workspaceRoot(), "com.example.OrderServiceTest");
        assertTrue(service.validate(run.runId()).readyToApply());
        Files.writeString(test, "class OrderServiceTest { int changed; }\n");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.apply(run.runId())
        );

        assertTrue(failure.getMessage().contains("changed after validation"));
        assertEquals(WorkflowRunService.RunStatus.PREPARED, service.getRun(run.runId()).status());
    }

    @Test
    void liveProjectDriftPreventsApply() throws Exception {
        WorkflowRunService.PreparedRun run = prepareTests();
        writeTest(run.workspaceRoot(), "OrderServiceTest", "class OrderServiceTest {}\n");
        writeExecutedReport(run.workspaceRoot(), "com.example.OrderServiceTest");
        assertTrue(service.validate(run.runId()).readyToApply());
        Files.writeString(tempDir.resolve("README.md"), "concurrent change\n");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.apply(run.runId())
        );

        assertTrue(failure.getMessage().contains("live project changed"));
    }

    @Test
    void cleanupStartsWithOpenRewriteAndAllowsSelectedProductionAndTests() throws Exception {
        service.close();
        service = createService((root, targets) -> {
            fileService.writeFile(targets.get(0), "package com.example; class OrderService { int cleaned; }\n");
            return successfulRewrite();
        });

        WorkflowRunService.PreparedRun run = service.prepareCodeCleanup(
                tempDir,
                WorkflowRunService.TargetSelection.classes(List.of("OrderService"))
        );

        assertEquals(List.of(Path.of("src/main/java/com/example/OrderService.java")), run.openRewriteChanges());
        assertTrue(run.agentInstructions().contains("OpenRewrite changed"));
        WorkflowRunService.ValidationResult validation = service.validate(run.runId());
        assertTrue(validation.readyToApply());
        service.apply(run.runId());
        assertTrue(Files.readString(tempDir.resolve("src/main/java/com/example/OrderService.java"))
                .contains("cleaned"));
    }

    @Test
    void cleanupRejectsUnselectedProductionFile() throws Exception {
        createProductionClass("OtherService", "class OtherService {}\n");
        WorkflowRunService.PreparedRun run = service.prepareCodeCleanup(
                tempDir,
                WorkflowRunService.TargetSelection.classes(List.of("OrderService"))
        );
        Files.writeString(
                run.workspaceRoot().resolve("src/main/java/com/example/OtherService.java"),
                "package com.example; class OtherService { int changed; }\n"
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.validate(run.runId())
        );

        assertTrue(failure.getMessage().contains("outside the workflow allowlist"));
    }

    @Test
    void oneActiveRunPerProjectPreventsOverlappingMerges() {
        WorkflowRunService.PreparedRun run = prepareTests();

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> prepareTests()
        );

        assertTrue(failure.getMessage().contains(run.runId()));
    }

    @Test
    void activeRunLimitIsGlobalAndReleasedAfterDiscard() throws Exception {
        List<Path> projects = new java.util.ArrayList<>();
        for (int index = 0; index <= WorkflowRunService.MAX_ACTIVE_RUNS; index++) {
            Path project = tempDir.resolve("project-" + index);
            createProject(project, false);
            projects.add(project);
        }
        List<WorkflowRunService.PreparedRun> active = projects.stream()
                .limit(WorkflowRunService.MAX_ACTIVE_RUNS)
                .map(project -> service.prepareTestGeneration(
                        project,
                        WorkflowRunService.TargetSelection.classes(List.of("OrderService")),
                        80.0d
                ))
                .toList();

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.prepareTestGeneration(
                        projects.get(WorkflowRunService.MAX_ACTIVE_RUNS),
                        WorkflowRunService.TargetSelection.classes(List.of("OrderService")),
                        80.0d
                )
        );
        assertTrue(failure.getMessage().contains("4 active runs"));

        service.discard(active.get(0).runId());
        WorkflowRunService.PreparedRun replacement = service.prepareTestGeneration(
                projects.get(WorkflowRunService.MAX_ACTIVE_RUNS),
                WorkflowRunService.TargetSelection.classes(List.of("OrderService")),
                80.0d
        );
        assertNotNull(replacement);
    }

    @Test
    void discardDeletesWorkspaceAndReleasesProject() {
        WorkflowRunService.PreparedRun run = prepareTests();

        service.discard(run.runId());

        assertFalse(Files.exists(run.workspaceRoot()));
        assertThrows(IllegalStateException.class, () -> service.getRun(run.runId()));
        assertNotNull(prepareTests());
    }

    @Test
    void coverageGoalMustPassBeforeApply() throws Exception {
        service.close();
        createProject(true);
        WorkflowRunService.CoverageGate lowCoverage = root -> {
            writeExecutedReport(root, "com.example.OrderServiceTest");
            return coverage(root, 60.0d);
        };
        service = new WorkflowRunService(
                fileService,
                projectService,
                coverageService,
                this::successfulBuild,
                lowCoverage,
                (root, targets) -> successfulRewrite()
        );
        WorkflowRunService.PreparedRun run = service.prepareTestGeneration(
                tempDir,
                WorkflowRunService.TargetSelection.classes(List.of("OrderService")),
                80.0d
        );
        writeTest(run.workspaceRoot(), "OrderServiceTest", "class OrderServiceTest {}\n");

        WorkflowRunService.ValidationResult validation = service.validate(run.runId());

        assertTrue(validation.valid());
        assertFalse(validation.readyToApply());
        assertEquals(false, validation.coverageGoalMet());
        assertEquals(60.0d, validation.coverage().get("com.example.OrderService").afterLineCoverage());
    }

    @Test
    void mutationGoalBlocksApplyAndFeedsTheTransparentTestScore() throws Exception {
        service.close();
        createProject(true);
        service = new WorkflowRunService(
                fileService,
                projectService,
                coverageService,
                this::successfulBuild,
                root -> coverage(root, 100.0d),
                (root, targets) -> successfulRewrite(),
                null,
                (root, targets, tests, minimum) -> mutationReport(minimum, 50.0d, 60.0d, false)
        );
        WorkflowRunService.PreparedRun run = service.prepareTestGeneration(
                tempDir,
                WorkflowRunService.TargetSelection.classes(List.of("OrderService")),
                80.0d,
                70.0d,
                true
        );
        writeTest(run.workspaceRoot(), "OrderServiceTest", "class OrderServiceTest {}\n");
        writeExecutedReport(run.workspaceRoot(), "com.example.OrderServiceTest");

        WorkflowRunService.ValidationResult validation = service.validate(run.runId());

        assertFalse(validation.valid());
        assertFalse(validation.readyToApply());
        assertTrue(validation.failures().get(0).contains("below the required 70"));
        assertEquals(76.5d, validation.testQuality().score());
        assertEquals("GOOD", validation.testQuality().grade());
        assertEquals(100.0d, validation.testQuality().evidenceCompletenessPercent());
        assertEquals(1, validation.testQuality().changedTestFileCount());
        assertEquals(1, validation.testQuality().executedChangedTestFileCount());
    }

    @Test
    void newSevereQualityFindingBlocksCleanupApply() throws Exception {
        WorkflowRunService.PreparedRun run = service.prepareCodeCleanup(
                tempDir,
                WorkflowRunService.TargetSelection.classes(List.of("OrderService"))
        );
        Files.writeString(
                run.workspaceRoot().resolve("src/main/java/com/example/OrderService.java"),
                "package com.example; class OrderService { public static Object shared; }\n"
        );

        WorkflowRunService.ValidationResult validation = service.validate(run.runId());

        assertFalse(validation.valid());
        assertFalse(validation.readyToApply());
        assertFalse(validation.qualityGoalMet());
        assertEquals(1, validation.qualityDelta().newCriticalOrHighFindings());
        assertTrue(validation.failures().stream().anyMatch(message -> message.contains("critical/high")));
    }

    @Test
    void invalidSelectionsFailBeforeCreatingWorkspace() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.prepareTestGeneration(
                        tempDir,
                        WorkflowRunService.TargetSelection.classes(List.of()),
                        80.0d
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.prepareCodeCleanup(
                        tempDir,
                        WorkflowRunService.TargetSelection.coverageBelow(80.0d)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.prepareTestGeneration(
                        tempDir,
                        WorkflowRunService.TargetSelection.changed(),
                        101.0d
                )
        );
        assertFalse(service.inspect(tempDir).activeRunId() != null);
    }

    private WorkflowRunService createService(WorkflowRunService.RewriteGate rewriteGate) {
        return new WorkflowRunService(
                fileService,
                projectService,
                coverageService,
                this::successfulBuild,
                root -> coverage(root, 100.0d),
                rewriteGate
        );
    }

    private JavaBuildVerificationService.VerificationResult successfulBuild(Path root) {
        builds.incrementAndGet();
        return new JavaBuildVerificationService.VerificationResult(List.of("fixture-build"), Duration.ofMillis(1));
    }

    private OpenRewriteCleanupService.RewriteResult successfulRewrite() {
        return new OpenRewriteCleanupService.RewriteResult(List.of("fixture-rewrite"), Duration.ofMillis(1));
    }

    private MutationTestingService.MutationReport mutationReport(
            double minimum,
            double mutationScore,
            double testStrength,
            boolean goalMet
    ) {
        return new MutationTestingService.MutationReport(
                MutationTestingService.PITEST_VERSION,
                true,
                minimum,
                mutationScore,
                testStrength,
                goalMet,
                10,
                5,
                3,
                2,
                0,
                0,
                0,
                List.of(),
                List.of("target/jaipilot-pit/mutations.xml"),
                List.of(List.of("pitest")),
                null,
                Duration.ofMillis(10).toNanos()
        );
    }

    private WorkflowRunService.PreparedRun prepareTests() {
        return service.prepareTestGeneration(
                tempDir,
                WorkflowRunService.TargetSelection.classes(List.of("OrderService")),
                80.0d
        );
    }

    private void createProject(boolean jacoco) throws IOException {
        createProject(tempDir, jacoco);
    }

    private void createProject(Path root, boolean jacoco) throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve("pom.xml"), jacoco ? "<project>jacoco</project>\n" : "<project/>\n");
        Files.writeString(root.resolve("README.md"), "fixture\n");
        Path source = root.resolve("src/main/java/com/example/OrderService.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package com.example; class OrderService { int value() { return 1; } }\n");
    }

    private Path createProductionClass(String name, String body) throws IOException {
        Path source = tempDir.resolve("src/main/java/com/example/" + name + ".java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package com.example; " + body);
        return source;
    }

    private Path writeTest(Path root, String className, String body) throws IOException {
        Path test = root.resolve("src/test/java/com/example/" + className + ".java");
        Files.createDirectories(test.getParent());
        Files.writeString(test, "package com.example; " + body);
        return test;
    }

    private void writeExecutedReport(Path root, String fullyQualifiedName) {
        try {
            Path report = root.resolve("target/surefire-reports/TEST-" + fullyQualifiedName + ".xml");
            Files.createDirectories(report.getParent());
            Files.writeString(report, "<testsuite tests=\"1\" failures=\"0\"/>\n");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private CoverageReportService.CoverageSnapshot coverage(Path root, double lineCoverage) {
        CoverageReportService.ClassCoverage classCoverage = new CoverageReportService.ClassCoverage(
                "com.example.OrderService",
                lineCoverage,
                lineCoverage
        );
        return new CoverageReportService.CoverageSnapshot(
                root.resolve("target/site/jacoco/jacoco.xml"),
                lineCoverage,
                lineCoverage,
                Map.of(classCoverage.fullyQualifiedName(), classCoverage)
        );
    }
}
