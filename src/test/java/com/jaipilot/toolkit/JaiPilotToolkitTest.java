package com.jaipilot.toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JaiPilotToolkitTest {

    @TempDir
    Path tempDir;

    @Test
    void helpDescribesTheLeanEvidenceKernel() {
        Captured result = run("--help");

        assertEquals(0, result.status());
        assertTrue(result.stdout().contains("inspect"));
        assertTrue(result.stdout().contains("snapshot"));
        assertTrue(result.stdout().contains("quality"));
        assertTrue(result.stdout().contains("rewrite"));
        assertTrue(result.stdout().contains("diff-gate"));
        assertTrue(result.stdout().contains("prove-diff"));
        assertFalse(result.stdout().contains("prepare-tests"));
        assertFalse(result.stdout().contains("apply --run"));
        assertFalse(result.stdout().contains("operation"));
    }

    @Test
    void versionAndInspectionAreStructuredJson() throws Exception {
        Path project = TestProject.maven(tempDir, "inspect");
        JsonNode version = json(run("version"));
        JsonNode inspection = json(run("inspect", "--project", project.toString()));

        assertTrue(version.path("ok").asBoolean());
        assertTrue(version.path("result").path("version").isTextual());
        assertEquals("maven", inspection.path("result").path("buildTool").asText());
        assertEquals(1, inspection.path("result").path("productionClassCount").asInt());
    }

    @Test
    void snapshotInitializesCurrentDashboardStateWithoutTouchingProject() throws Exception {
        Path project = TestProject.gitMaven(tempDir, "snapshot");
        try {
            Captured result = run("snapshot", "--project", project.toString());
            RepositorySnapshotStore.DashboardView dashboard = new RepositorySnapshotStore(
                    JaiPilotToolkit.mapper(), stateRoot()
            ).view(null);

            assertEquals(0, result.status(), result.stderr());
            assertEquals("ready", dashboard.selectedRepository().analysisStatus());
            assertEquals(1, dashboard.selectedRepository().quality().metrics().fileCount());
            assertEquals("", gitStatus(project));
            assertFalse(Files.exists(project.resolve(".jaipilot")));
            assertFalse(Files.exists(project.resolve("target")));
        } finally {
            stopDashboard();
        }
    }

    @Test
    void snapshotSkipsNonJavaDirectoriesWithoutRegisteringThem() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("non-java"));
        Files.writeString(project.resolve("package.json"), "{}\n");

        JsonNode result = json(run("snapshot", "--project", project.toString()));
        RepositorySnapshotStore.DashboardView dashboard = new RepositorySnapshotStore(
                JaiPilotToolkit.mapper(), stateRoot()
        ).view(null);

        assertFalse(result.path("result").path("applicable").asBoolean());
        assertEquals("not_java_project", result.path("result").path("reason").asText());
        assertTrue(dashboard.repositories().isEmpty());
    }

    @Test
    void dashboardCommandStartsTheRequestedPrivateStateRoot() throws Exception {
        JsonNode result = json(run("dashboard"));
        long pid = result.path("result").path("pid").asLong();
        try {
            assertTrue(result.path("result").path("running").asBoolean());
            assertTrue(result.path("result").path("url").asText().startsWith("http://127.0.0.1:"));
            assertTrue(pid > 0);
        } finally {
            stopProcess(pid);
        }
    }

    @Test
    void removedOrInvalidOrchestrationCommandsFailClearly() throws Exception {
        Captured removed = run("prepare-tests", "--project", tempDir.toString());
        Captured invalid = run("quality", "--unknown", "value");

        assertEquals(2, removed.status());
        assertEquals("invalid_request", json(removed).path("error").path("code").asText());
        assertEquals(2, invalid.status());
        assertTrue(invalid.stderr().contains("--unknown"));
    }

    private Captured run(String... arguments) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int status = JaiPilotToolkit.run(
                arguments,
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8),
                stateRoot()
        );
        return new Captured(status, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    }

    private JsonNode json(Captured captured) throws Exception {
        return new ObjectMapper().readTree(captured.stdout());
    }

    private String gitStatus(Path project) throws Exception {
        Process process = new ProcessBuilder("git", "status", "--porcelain")
                .directory(project.toFile()).start();
        assertEquals(0, process.waitFor());
        return new String(process.getInputStream().readAllBytes()).trim();
    }

    private Path stateRoot() {
        return tempDir.resolve("state");
    }

    private void stopDashboard() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        DashboardServer.DashboardStatus status;
        do {
            status = DashboardServer.currentStatus(JaiPilotToolkit.mapper(), stateRoot());
            if (status.pid() > 0) {
                stopProcess(status.pid());
                return;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
    }

    private void stopProcess(long pid) throws Exception {
        ProcessHandle process = pid > 0 ? ProcessHandle.of(pid).orElse(null) : null;
        if (process != null) {
            process.destroy();
            process.onExit().get(5, TimeUnit.SECONDS);
        }
    }

    private record Captured(int status, String stdout, String stderr) {
    }
}
