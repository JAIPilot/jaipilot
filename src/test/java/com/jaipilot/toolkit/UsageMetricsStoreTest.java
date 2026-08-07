package com.jaipilot.toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        Map<String, Object> proof = Map.of(
                "ok", false,
                "result", Map.of(
                        "passed", false,
                        "targets", List.of("com.example.PaymentService"),
                        "changedQuality", Map.of("score", 88.0d),
                        "testQuality", Map.of("score", 72.0d, "lineCoverage", 80.0d, "branchCoverage", 60.0d),
                        "mutation", Map.of("mutationScore", 65.0d)
                )
        );

        store.record("prove-diff", 1, proof, Duration.ofSeconds(4));
        UsageMetricsStore.DashboardMetrics metrics = store.snapshot();

        assertEquals(1, metrics.impact().diffProofsRun());
        assertEquals(0, metrics.impact().diffProofsPassed());
        assertEquals(Boolean.FALSE, metrics.latestEvidence().lastProofPassed());
        assertEquals(88.0d, metrics.latestEvidence().qualityScore());
        assertTrue(metrics.recentActivity().get(0).summary().contains("gaps"));
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
