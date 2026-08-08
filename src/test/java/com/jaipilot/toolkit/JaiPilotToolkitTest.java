package com.jaipilot.toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JaiPilotToolkitTest {

    @TempDir
    Path tempDir;

    @Test
    void helpDescribesAgentFacingTransactions() {
        Captured result = run("--help");

        assertEquals(0, result.status());
        assertTrue(result.stdout().contains("prepare-tests"));
        assertTrue(result.stdout().contains("prepare-cleanup"));
        assertTrue(result.stdout().contains("jaipilot quality"));
        assertTrue(result.stdout().contains("jaipilot diff-gate"));
        assertTrue(result.stdout().contains("jaipilot prove-diff"));
        assertTrue(result.stdout().contains("jaipilot dashboard"));
        assertTrue(result.stdout().contains("--minimum-mutation-score"));
        assertFalse(result.stdout().contains("skip-mutation"));
        assertTrue(result.stdout().contains("apply --run <uuid> --confirm"));
        assertEquals("", result.stderr());
    }

    @Test
    void versionIsStructuredJson() throws Exception {
        Captured result = run("version");
        JsonNode json = new ObjectMapper().readTree(result.stdout());

        assertEquals(0, result.status());
        assertTrue(json.path("ok").asBoolean());
        assertTrue(json.path("result").path("version").isTextual());
        assertEquals("", result.stderr());
    }

    @Test
    void dashboardStatusIsStructuredWithoutStartingASecondServerInProcess() throws Exception {
        Captured result = run("dashboard");
        JsonNode json = new ObjectMapper().readTree(result.stdout()).path("result");

        assertEquals(0, result.status());
        assertFalse(json.path("running").asBoolean());
        assertEquals(DashboardServer.DEFAULT_PORT, json.path("preferredPort").asInt());
    }

    @Test
    void stopHookEmitsOnlyPortableContinuationJsonForAnUnprovedJavaDiff() throws Exception {
        Path project = changedProject();
        Captured result = runWithInput(
                "{\"stop_hook_active\":false}",
                "hook-stop",
                "--project",
                project.toString()
        );
        JsonNode json = new ObjectMapper().readTree(result.stdout());

        assertEquals(0, result.status());
        assertEquals("block", json.path("decision").asText());
        assertTrue(json.path("reason").asText().contains("$jaipilot-review-diff"));
        assertFalse(json.has("ok"));
        assertEquals("", result.stderr());
    }

    @Test
    void postCommitHookRefreshesWholeProjectQualityAndContinuesUnprovedJavaWork() throws Exception {
        Path project = changedProject();
        Captured result = runWithInput(
                postToolUse("git add . && git commit -m \"change production code\""),
                "hook-post-commit",
                "--project",
                project.toString()
        );
        JsonNode response = new ObjectMapper().readTree(result.stdout());
        UsageMetricsStore.LatestEvidence evidence = new UsageMetricsStore(
                new ObjectMapper(),
                tempDir.resolve("cli-state")
        ).snapshot().latestEvidence();

        assertEquals(0, result.status());
        assertEquals("block", response.path("decision").asText());
        assertTrue(response.path("reason").asText().contains("automatically analyzed this Git commit"));
        assertTrue(response.path("reason").asText().contains("$jaipilot-review-diff"));
        assertEquals("Automatic post-commit analysis", evidence.currentQuality().source());
        assertEquals("whole_project", evidence.currentQuality().scope());
        assertFalse(evidence.currentQuality().revision().isBlank());
        assertFalse(evidence.currentQuality().fingerprint().isBlank());
        assertEquals(1, evidence.currentQuality().fileCount());
        assertEquals(1, evidence.currentQuality().targetCount());
        assertEquals(evidence.currentQuality().qualityScore(), evidence.qualityScore());
        assertEquals("", result.stderr());
    }

    @Test
    void postCommitHookRecognizesPortableGitInvocationsButRejectsTextualFalsePositives() throws Exception {
        Path project = changedProject();
        Captured globalOptions = runWithInput(
                postToolUse("env LANG=C git -C . -c user.name=Agent commit --amend --no-edit"),
                "hook-post-commit",
                "--project",
                project.toString()
        );
        Captured echo = runWithInput(
                postToolUse("echo 'run git commit when ready'"),
                "hook-post-commit",
                "--project",
                project.toString()
        );
        Captured status = runWithInput(
                postToolUse("git status --short"),
                "hook-post-commit",
                "--project",
                project.toString()
        );
        Captured wrongEvent = runWithInput(
                "{\"hook_event_name\":\"PreToolUse\",\"tool_name\":\"Bash\","
                        + "\"tool_input\":{\"command\":\"git commit -m test\"}}",
                "hook-post-commit",
                "--project",
                project.toString()
        );

        assertEquals("block", new ObjectMapper().readTree(globalOptions.stdout()).path("decision").asText());
        assertEquals("", echo.stdout());
        assertEquals("", status.stdout());
        assertEquals("", wrongEvent.stdout());
    }

    @Test
    void postCommitHookKeepsCurrentMetricsFreshWhenNoProductionDiffRemains() throws Exception {
        Path project = changedProject();
        git(project, "reset", "--hard", "HEAD^");

        Captured result = runWithInput(
                postToolUse("git commit --allow-empty -m metrics"),
                "hook-post-commit",
                "--project",
                project.toString()
        );
        UsageMetricsStore.CurrentQualityEvidence quality = new UsageMetricsStore(
                new ObjectMapper(),
                tempDir.resolve("cli-state")
        ).snapshot().latestEvidence().currentQuality();

        assertEquals(0, result.status());
        assertEquals("", result.stdout());
        assertEquals(1, quality.fileCount());
        assertTrue(quality.qualityScore() > 0.0d);
        assertEquals("whole_project", quality.scope());
    }

    @Test
    void postCommitHookReplacesStaleScoresWhenNoProductionSourcesRemain() throws Exception {
        Path project = changedProject();
        runWithInput(postToolUse("git commit -m analyzed"), "hook-post-commit", "--project", project.toString());
        Files.delete(project.resolve("src/main/java/com/example/OrderService.java"));
        git(project, "add", "-A");
        git(project, "commit", "-qm", "remove production sources");

        Captured result = runWithInput(
                postToolUse("git commit -m remove"),
                "hook-post-commit",
                "--project",
                project.toString()
        );
        UsageMetricsStore.CurrentQualityEvidence quality = new UsageMetricsStore(
                new ObjectMapper(),
                tempDir.resolve("cli-state")
        ).snapshot().latestEvidence().currentQuality();

        assertEquals(0, result.status());
        assertEquals("block", new ObjectMapper().readTree(result.stdout()).path("decision").asText());
        assertEquals("no_java_sources", quality.analysisStatus());
        assertEquals("whole_project", quality.scope());
        assertFalse(quality.revision().isBlank());
        assertNull(quality.qualityScore());
        assertNull(quality.findings().total());
    }

    @Test
    void repeatedStopHookNeverCreatesAnInfiniteContinuationLoop() throws Exception {
        Path project = changedProject();
        Captured result = runWithInput(
                "{\"stop_hook_active\":true}",
                "hook-stop",
                "--project",
                project.toString()
        );

        assertEquals(0, result.status());
        assertEquals("", result.stdout());
        assertEquals("", result.stderr());
    }

    @Test
    void diffGateReportsCommittedChangesAndExplicitThresholds() throws Exception {
        Path project = changedProject();

        Captured result = run(
                "diff-gate",
                "--project",
                project.toString(),
                "--minimum-line-coverage",
                "92",
                "--minimum-branch-coverage",
                "87",
                "--minimum-mutation-score",
                "82",
                "--minimum-quality-score",
                "95"
        );
        JsonNode json = new ObjectMapper().readTree(result.stdout()).path("result");

        assertEquals(0, result.status());
        assertEquals("review_required", json.path("status").asText());
        assertEquals(92.0d, json.path("requiredThresholds").path("minimumLineCoverage").asDouble());
        assertEquals("HEAD^", json.path("baselineDescription").asText());
        assertEquals(1, json.path("changedProductionPaths").size());
    }

    @Test
    void invalidDiffThresholdReturnsStructuredError() throws Exception {
        Path project = changedProject();

        Captured result = run(
                "diff-gate",
                "--project",
                project.toString(),
                "--minimum-line-coverage",
                "101"
        );
        JsonNode json = new ObjectMapper().readTree(result.stdout());

        assertEquals(2, result.status());
        assertEquals("invalid_request", json.path("error").path("code").asText());
        assertTrue(result.stderr().contains("between 0 and 100"));
    }

    @Test
    void malformedHookInputStillPerformsTheSafeDiffCheck() throws Exception {
        Path project = changedProject();

        Captured result = runWithInput("not-json", "hook-stop", "--project", project.toString());

        assertEquals(0, result.status());
        assertEquals("block", new ObjectMapper().readTree(result.stdout()).path("decision").asText());
    }

    @Test
    void hookIsSilentWhenTheRepositoryHasNoProductionDiff() throws Exception {
        Path project = changedProject();
        git(project, "reset", "--hard", "HEAD^");

        Captured result = runWithInput(
                "{\"stop_hook_active\":false}",
                "hook-stop",
                "--project",
                project.toString()
        );

        assertEquals(0, result.status());
        assertEquals("", result.stdout());
        assertEquals("", result.stderr());
    }

    @Test
    void hookIsSilentOutsideGitButFailsClosedOnInspectionErrors() throws Exception {
        Path notGit = tempDir.resolve("not-git");
        Files.createDirectories(notGit);
        Captured outsideGit = runWithInput(
                "{\"stop_hook_active\":false}",
                "hook-stop",
                "--project",
                notGit.toString()
        );
        Captured unavailable = runWithInput(
                "{\"stop_hook_active\":false}",
                "hook-stop",
                "--project",
                tempDir.resolve("missing-project").toString()
        );

        assertEquals("", outsideGit.stdout());
        assertEquals("block", new ObjectMapper().readTree(unavailable.stdout()).path("decision").asText());
        assertTrue(unavailable.stdout().contains("could not safely inspect"));
    }

    @Test
    void qualityCommandUsesThePrepareCommandRoute() throws Exception {
        Path project = changedProject();

        Captured result = run("quality", "--project", project.toString(), "--mode", "changed");
        JsonNode json = new ObjectMapper().readTree(result.stdout());

        assertEquals(0, result.status());
        assertTrue(json.path("result").path("quality").path("metrics").path("qualityScore").isNumber());
    }

    @Test
    void runCommandAndUnknownCommandErrorsStayStructured() throws Exception {
        Captured invalidRun = run("status", "--run", "not-a-uuid");
        Captured unknown = run("definitely-not-a-command");

        assertEquals(2, invalidRun.status());
        assertEquals(2, unknown.status());
        assertEquals(
                "invalid_request",
                new ObjectMapper().readTree(invalidRun.stdout()).path("error").path("code").asText()
        );
        assertTrue(unknown.stderr().contains("Unknown command"));
    }

    @Test
    void proveDiffCommandReportsProgressAndStructuredSuccess() throws Exception {
        Path project = deletionProject();

        Captured result = run("prove-diff", "--project", project.toString());
        JsonNode json = new ObjectMapper().readTree(result.stdout());

        assertEquals(0, result.status());
        assertTrue(json.path("ok").asBoolean());
        assertTrue(json.path("result").path("passed").asBoolean());
        assertTrue(result.stderr().contains("deletion-only diff"));
        assertTrue(result.stderr().contains("proof passed"));
    }

    @Test
    void proveDiffCommandReturnsFailedProofWithoutCorruptingJson() throws Exception {
        Path project = changedProject();
        addSuccessfulMavenWrapper(project);

        Captured result = run("prove-diff", "--project", project.toString());
        JsonNode json = new ObjectMapper().readTree(result.stdout());

        assertEquals(1, result.status());
        assertFalse(json.path("ok").asBoolean());
        assertFalse(json.path("result").path("passed").asBoolean());
        assertTrue(json.path("result").path("failures").toString().contains("JaCoCo"));
        assertFalse(json.path("result").path("architecture").path("complete").asBoolean());
        assertTrue(result.stderr().contains("actionable gaps"));
    }

    @Test
    void inspectAndTestPreparationLifecycleRemainAgentFacingJson() throws Exception {
        Path project = changedProject();
        addSuccessfulMavenWrapper(project);

        Captured inspect = run("inspect", "--project", project.toString());
        Captured prepare = run(
                "prepare-tests",
                "--project",
                project.toString(),
                "--mode",
                "changed",
                "--minimum-line-coverage",
                "90",
                "--minimum-mutation-score",
                "80"
        );

        assertEquals(0, inspect.status());
        assertEquals(1, new ObjectMapper().readTree(inspect.stdout()).path("result")
                .path("changedProductionClasses").size());
        assertEquals(0, prepare.status(), prepare.stderr());
        String runId = new ObjectMapper().readTree(prepare.stdout()).path("result").path("runId").asText();
        assertFalse(runId.isBlank());

        Captured status = run("status", "--run", runId);
        Captured discard = run("discard", "--run", runId);
        assertEquals(0, status.status());
        assertEquals(0, discard.status());
        assertTrue(new ObjectMapper().readTree(discard.stdout()).path("result").path("discarded").asBoolean());
    }

    @Test
    void applyRequiresExplicitConfirmationBeforeReadingRunState() {
        Captured result = run("apply", "--run", "00000000-0000-0000-0000-000000000000");

        assertEquals(2, result.status());
        assertTrue(result.stderr().contains("requires --confirm"));
    }

    @Test
    void remainingCommandRoutesFailAsStructuredJsonWhenStateOrProjectIsMissing() throws Exception {
        String missingRun = "00000000-0000-0000-0000-000000000000";
        Captured validate = run("validate", "--run", missingRun);
        Captured confirmedApply = run("apply", "--run", missingRun, "--confirm");
        Captured cleanup = run(
                "prepare-cleanup",
                "--project",
                tempDir.resolve("missing-cleanup-project").toString(),
                "--mode",
                "changed"
        );

        assertEquals(1, validate.status());
        assertEquals(1, confirmedApply.status());
        assertEquals(2, cleanup.status());
        assertEquals("workflow_failed", new ObjectMapper().readTree(validate.stdout())
                .path("error").path("code").asText());
    }

    private Captured run(String... arguments) {
        return runWithInput("", arguments);
    }

    private Captured runWithInput(String input, String... arguments) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int status = JaiPilotToolkit.run(
                arguments,
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8),
                tempDir.resolve("cli-state")
        );
        return new Captured(
                status,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8)
        );
    }

    private String postToolUse(String command) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of(
                "hook_event_name", "PostToolUse",
                "tool_name", "Bash",
                "tool_input", Map.of("command", command)
        ));
    }

    private Path changedProject() throws Exception {
        Path project = Files.createTempDirectory(tempDir, "hook-project-");
        Path source = project.resolve("src/main/java/com/example/OrderService.java");
        Files.createDirectories(source.getParent());
        Files.writeString(project.resolve("pom.xml"), "<project/>\n");
        Files.writeString(source, "package com.example; class OrderService {}\n");
        git(project, "init", "-q", "-b", "main");
        git(project, "config", "user.name", "JAIPilot Test");
        git(project, "config", "user.email", "test@jaipilot.local");
        commit(project, "baseline");
        Files.writeString(source, "package com.example; class OrderService { int changed; }\n");
        commit(project, "change production code");
        return project;
    }

    private Path deletionProject() throws Exception {
        Path project = changedProject();
        addSuccessfulMavenWrapper(project);
        Files.delete(project.resolve("src/main/java/com/example/OrderService.java"));
        git(project, "add", "-A");
        git(project, "commit", "-qm", "delete obsolete production class");
        return project;
    }

    private void addSuccessfulMavenWrapper(Path project) throws Exception {
        Path wrapper = project.resolve("mvnw");
        Files.writeString(wrapper, "#!/bin/sh\nexit 0\n");
        assertTrue(wrapper.toFile().setExecutable(true, false));
        Path metadata = project.resolve(".mvn/wrapper/maven-wrapper.properties");
        Files.createDirectories(metadata.getParent());
        Files.writeString(metadata, "distributionUrl=https://repo.maven.apache.org/maven2\n");
    }

    private void commit(Path project, String message) throws Exception {
        git(project, "add", ".");
        git(project, "commit", "-qm", message);
    }

    private void git(Path project, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(project.toFile()).start();
        int status = process.waitFor();
        String errors = new String(process.getErrorStream().readAllBytes());
        assertEquals(0, status, errors);
    }

    private record Captured(int status, String stdout, String stderr) {
    }
}
