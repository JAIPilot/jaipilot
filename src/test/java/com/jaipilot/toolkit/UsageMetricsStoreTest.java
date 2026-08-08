package com.jaipilot.toolkit;

import static com.jaipilot.toolkit.DashboardProofFixture.architecture;
import static com.jaipilot.toolkit.DashboardProofFixture.proof;
import static com.jaipilot.toolkit.DashboardProofFixture.quality;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UsageMetricsStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void creditsImprovementOnlyAfterTheValidatedRunIsApplied() throws Exception {
        UsageMetricsStore store = new UsageMetricsStore(new ObjectMapper(), tempDir.resolve("state"));
        String runId = "00000000-0000-0000-0000-000000000001";
        Path privateProject = tempDir.resolve("customers/secret-orders");

        store.record("prepare-tests", 0, Map.of(
                "runId", runId,
                "kind", "GENERATE_TESTS",
                "projectRoot", privateProject.toString(),
                "targets", List.of(Map.of("fullyQualifiedName", "com.example.OrderService"))
        ), Duration.ofMillis(120));
        store.record("validate", 0, validation(runId, 45.0d, 82.5d, 76.0d, 91.5d), Duration.ofSeconds(2));

        UsageMetricsStore.DashboardMetrics beforeApply = store.snapshot();
        assertEquals(0, beforeApply.impact().targetsImproved());
        assertEquals(0.0d, beforeApply.impact().coveragePointsChanged());
        assertEquals(1, beforeApply.impact().validationsReadyToApply());
        assertEquals(91.5d, beforeApply.latestEvidence().testQualityScore());
        assertEquals("Test validation", beforeApply.latestEvidence().findings().source());
        assertEquals(0, beforeApply.latestEvidence().findings().total());
        assertEquals(Boolean.TRUE, beforeApply.latestEvidence().architecture().goalMet());
        assertEquals(Boolean.TRUE, beforeApply.latestEvidence().gates().passed());

        store.record("apply", 0, Map.of(
                "runId", runId,
                "changedRelativePaths", List.of("src/test/java/com/example/OrderServiceTest.java")
        ), Duration.ofMillis(80));
        store.record("invalid-command", 2, null, Duration.ofMillis(5));

        UsageMetricsStore.DashboardMetrics metrics = store.snapshot();
        assertEquals(4, metrics.usage().totalCommands());
        assertEquals(3, metrics.usage().successfulCommands());
        assertEquals(1, metrics.usage().failedCommands());
        assertEquals(1, metrics.usage().projectsSeen());
        assertEquals(1, metrics.impact().testRunsApplied());
        assertEquals(1, metrics.impact().targetsImproved());
        assertEquals(37.5d, metrics.impact().coveragePointsChanged());
        assertEquals(15.5d, metrics.impact().qualityPointsChanged());
        assertEquals(3, metrics.impact().findingsResolved());
        assertEquals(18, metrics.impact().debtMinutesRemoved());
        assertEquals(12, metrics.impact().mutationsKilled());
        assertEquals(2, metrics.impact().changedTestsExecuted());
        assertEquals(1, metrics.impact().filesChanged());

        String persisted = Files.readString(tempDir.resolve("state/metrics/summary.json"));
        assertFalse(persisted.contains(privateProject.toString()));
        assertFalse(persisted.contains("OrderService"));
    }

    @Test
    void laterValidationReplacesPendingEvidenceInsteadOfDoubleCountingIt() {
        UsageMetricsStore store = new UsageMetricsStore(new ObjectMapper(), tempDir.resolve("state"));
        String runId = "00000000-0000-0000-0000-000000000002";
        store.record("prepare-cleanup", 0, Map.of(
                "runId", runId,
                "targets", List.of(Map.of(), Map.of())
        ), Duration.ZERO);
        store.record("validate", 0, validation(runId, 50.0d, 60.0d, 70.0d, 74.0d), Duration.ZERO);
        store.record("validate", 0, validation(runId, 50.0d, 72.0d, 70.0d, 79.0d), Duration.ZERO);
        store.record(
                "apply",
                0,
                Map.of("runId", runId, "changedRelativePaths", List.of("Example.java")),
                Duration.ZERO
        );

        UsageMetricsStore.Impact impact = store.snapshot().impact();
        assertEquals(1, impact.cleanupRunsApplied());
        assertEquals(2, impact.targetsImproved());
        assertEquals(22.0d, impact.coveragePointsChanged());
        assertEquals(9.0d, impact.qualityPointsChanged());
    }

    @Test
    void failedDiffProofStillAppearsAsHonestEvidence() {
        UsageMetricsStore store = new UsageMetricsStore(new ObjectMapper(), tempDir.resolve("state"));
        store.record("prove-diff", 1, proof(false), Duration.ofSeconds(4));
        UsageMetricsStore.DashboardMetrics metrics = store.snapshot();

        assertEquals(1, metrics.impact().diffProofsRun());
        assertEquals(0, metrics.impact().diffProofsPassed());
        assertEquals(Boolean.FALSE, metrics.latestEvidence().lastProofPassed());
        assertEquals(88.0d, metrics.latestEvidence().qualityScore());
        assertEquals(1, metrics.latestEvidence().findings().total());
        assertEquals(1, metrics.latestEvidence().findings().high());
        assertEquals("JAI-QUAL-001", metrics.latestEvidence().findings().items().get(0).id());
        assertEquals(Boolean.TRUE, metrics.latestEvidence().architecture().complete());
        assertEquals(Boolean.FALSE, metrics.latestEvidence().architecture().goalMet());
        assertEquals(1, metrics.latestEvidence().architecture().violationCount());
        assertEquals("ArchUnit", metrics.latestEvidence().architecture().engine());
        assertEquals("JAI-ARCH-001", metrics.latestEvidence().architecture().violations().get(0).id());
        assertEquals(Boolean.FALSE, metrics.latestEvidence().gates().passed());
        assertEquals(1, metrics.latestEvidence().gates().failures().size());
        assertEquals(1, metrics.latestEvidence().gates().warnings().size());
        assertTrue(metrics.recentActivity().get(0).summary().contains("gaps"));
    }

    @Test
    void laterCleanProofReplacesStaleFindingsAndArchitectureGaps() {
        UsageMetricsStore store = new UsageMetricsStore(new ObjectMapper(), tempDir.resolve("live-state"));
        store.record("prove-diff", 1, proof(false), Duration.ZERO);
        store.record("prove-diff", 0, proof(true), Duration.ZERO);

        UsageMetricsStore.LatestEvidence evidence = store.snapshot().latestEvidence();
        assertEquals(Boolean.TRUE, evidence.lastProofPassed());
        assertEquals(0, evidence.findings().total());
        assertTrue(evidence.findings().items().isEmpty());
        assertEquals(Boolean.TRUE, evidence.architecture().goalMet());
        assertEquals(0, evidence.architecture().violationCount());
        assertTrue(evidence.architecture().violations().isEmpty());
        assertEquals(Boolean.TRUE, evidence.gates().passed());
        assertTrue(evidence.gates().failures().isEmpty());
        assertTrue(evidence.gates().warnings().isEmpty());
    }

    @Test
    void automaticPostCommitAnalysisPersistsCompleteCurrentProjectQuality() {
        UsageMetricsStore store = new UsageMetricsStore(new ObjectMapper(), tempDir.resolve("commit-state"));
        store.record("hook-post-commit", 0, Map.of(
                "analysisStatus", "analyzed",
                "revision", "0123456789abcdef0123456789abcdef01234567",
                "fingerprint", "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                "currentQuality", Map.of(
                        "projectRoot", tempDir.resolve("project").toString(),
                        "targets", List.of(
                                "com.example.PaymentService",
                                "com.example.InvoiceService"
                        ),
                        "quality", quality(false)
                )
        ), Duration.ofMillis(310));

        UsageMetricsStore.DashboardMetrics metrics = store.snapshot();
        UsageMetricsStore.CurrentQualityEvidence current = metrics.latestEvidence().currentQuality();
        assertEquals(3, metrics.schemaVersion());
        assertEquals("Automatic post-commit analysis", current.source());
        assertEquals("whole_project", current.scope());
        assertEquals("analyzed", current.analysisStatus());
        assertEquals("0123456789abcdef0123456789abcdef01234567", current.revision());
        assertEquals(
                "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                current.fingerprint()
        );
        assertEquals(2, current.targetCount());
        assertEquals(4, current.fileCount());
        assertEquals(240, current.linesOfCode());
        assertEquals(8_192L, current.sourceBytes());
        assertEquals(18, current.methodCount());
        assertEquals(1, current.findingCount());
        assertEquals(1, current.bugRiskCount());
        assertEquals(1, current.codeSmellCount());
        assertEquals(2, current.modernizationOpportunityCount());
        assertEquals(14, current.maximumCyclomaticComplexity());
        assertEquals(4.6d, current.averageCyclomaticComplexity());
        assertEquals(19, current.maximumCognitiveComplexity());
        assertEquals(24, current.duplicatedLineCount());
        assertEquals(10.0d, current.duplicationPercent());
        assertEquals(55, current.remediationDebtMinutes());
        assertEquals(3.8d, current.remediationDebtRatioPercent());
        assertEquals(75.0d, current.reliabilityScore());
        assertEquals(84.0d, current.maintainabilityScore());
        assertEquals(70.0d, current.complexityScore());
        assertEquals(80.0d, current.duplicationScore());
        assertEquals(88.0d, current.qualityScore());
        assertEquals(125_000_000L, current.analysisElapsedNanos());
        assertEquals(0, current.parseFailures());
        assertEquals(1, current.findings().total());
        assertNull(metrics.latestEvidence().findings().total());
        assertTrue(metrics.recentActivity().get(0).summary().contains("Git commit"));
    }

    @Test
    void migratesVersionOneMetricsWithoutInventingCurrentEvidence() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path stateRoot = tempDir.resolve("legacy-state");
        Path summary = stateRoot.resolve("metrics/summary.json");
        Files.createDirectories(summary.getParent());
        Files.writeString(summary, """
                {
                  "schemaVersion": 1,
                  "firstUsedAt": "2026-01-01T00:00:00Z",
                  "lastUsedAt": "2026-01-01T00:00:01Z",
                  "totalCommands": 1,
                  "successfulCommands": 1,
                  "failedCommands": 0,
                  "totalCommandDurationMillis": 10,
                  "commands": {"inspect": 1},
                  "projectIds": [],
                  "impact": {},
                  "latestEvidence": {"qualityScore": 77.0, "verifiedTargetCount": 1},
                  "pendingRuns": {},
                  "recentActivity": []
                }
                """);

        UsageMetricsStore store = new UsageMetricsStore(mapper, stateRoot);
        UsageMetricsStore.DashboardMetrics migrated = store.snapshot();
        assertEquals(3, migrated.schemaVersion());
        assertEquals(77.0d, migrated.latestEvidence().qualityScore());
        assertNull(migrated.latestEvidence().currentQuality().qualityScore());
        assertNull(migrated.latestEvidence().findings().total());
        assertNull(migrated.latestEvidence().architecture().complete());
        assertNull(migrated.latestEvidence().gates().passed());

        store.record("inspect", 0, Map.of(), Duration.ZERO);
        assertEquals(3, mapper.readTree(Files.readString(summary)).path("schemaVersion").asInt());
    }

    @Test
    void preservesAndRecoversFromCorruptMetricsOnTheNextWrite() throws Exception {
        Path stateRoot = tempDir.resolve("recovery-state");
        UsageMetricsStore store = new UsageMetricsStore(new ObjectMapper(), stateRoot);
        Path summary = stateRoot.resolve("metrics/summary.json");
        Files.writeString(summary, "not-json");

        store.record("inspect", 0, Map.of("projectRoot", tempDir.toString()), Duration.ofMillis(10));

        assertEquals(1, store.snapshot().usage().totalCommands());
        try (var files = Files.list(summary.getParent())) {
            assertEquals(1, files.filter(path -> path.getFileName().toString().startsWith("summary.corrupt-")).count());
        }
        assertTrue(Files.readString(summary).contains("\"totalCommands\" : 1"));
    }

    @Test
    void dashboardHelpAndVersionChecksDoNotInflateProductUsage() {
        UsageMetricsStore store = new UsageMetricsStore(new ObjectMapper(), tempDir.resolve("admin-state"));

        store.record("version", 0, Map.of("version", "3.1.2"), Duration.ofMillis(5));
        store.record("help", 0, null, Duration.ofMillis(5));
        store.record("dashboard", 0, Map.of("running", true), Duration.ofMillis(5));

        assertEquals(0, store.snapshot().usage().totalCommands());
    }

    private Map<String, Object> validation(
            String runId,
            double coverageBefore,
            double coverageAfter,
            double qualityBefore,
            double qualityAfter
    ) {
        return Map.of(
                "runId", runId,
                "readyToApply", true,
                "coverage", Map.of("com.example.OrderService", Map.of(
                        "beforeLineCoverage", coverageBefore,
                        "afterLineCoverage", coverageAfter
                )),
                "qualityDelta", Map.of(
                        "qualityScoreBefore", qualityBefore,
                        "qualityScoreAfter", qualityAfter,
                        "resolvedFindings", 3,
                        "remediationDebtMinutesBefore", 25,
                        "remediationDebtMinutesAfter", 7
                ),
                "quality", quality(true),
                "architecture", architecture(true),
                "failures", List.of(),
                "warnings", List.of(),
                "mutation", Map.of("mutationScore", 85.0d, "killed", 12),
                "testQuality", Map.of(
                        "score", 91.5d,
                        "lineCoverage", coverageAfter,
                        "branchCoverage", 77.0d,
                        "executedChangedTestFileCount", 2
                )
        );
    }

}
