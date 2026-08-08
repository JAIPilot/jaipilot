package com.jaipilot.toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DashboardServerTest {

    @TempDir
    Path tempDir;

    @Test
    void servesLeanCurrentStateDashboardAndCanonicalLogoOnLoopback() throws Exception {
        ObjectMapper mapper = JaiPilotToolkit.mapper();
        Path stateRoot = tempDir.resolve("state");
        Path project = TestProject.maven(tempDir, "project");
        RepositorySnapshotStore snapshots = new RepositorySnapshotStore(mapper, stateRoot);
        RepositorySnapshotStore.RepositoryState registered = snapshots.register(project);

        try (DashboardServer.RunningDashboard dashboard = DashboardServer.start(mapper, stateRoot, 0)) {
            assertTrue(dashboard.status().url().startsWith("http://127.0.0.1:"));
            Response page = get(dashboard.status().url());
            assertEquals(200, page.status());
            assertTrue(page.body().contains("JAIPilot Local Quality"));
            assertTrue(page.body().contains("Current findings"));
            assertTrue(page.body().contains("Observed impact"));
            assertTrue(page.body().contains("repository-count"));
            assertFalse(page.body().contains("Local usage"));
            assertFalse(page.body().contains("Recent analysis"));
            assertTrue(page.contentSecurityPolicy().contains("default-src 'self'"));

            Response script = get(dashboard.status().url() + "assets/dashboard.js");
            assertTrue(script.body().contains("proof.fingerprint === fingerprint"));
            assertTrue(script.body().contains("severityOrder"));
            assertFalse(script.body().contains("supportAlert"));

            Response styles = get(dashboard.status().url() + "assets/dashboard.css");
            assertFalse(styles.body().contains("linear-gradient"));
            assertFalse(styles.body().contains("@keyframes"));

            Response logo = get(dashboard.status().url() + "logo.svg");
            assertEquals(200, logo.status());
            assertEquals(Files.readString(Path.of("plugins/jaipilot/assets/jaipilot-logo-dark.svg")).replaceAll("\\s+", ""),
                    logo.body().replaceAll("\\s+", ""));
            assertTrue(logo.body().contains("#F0F6FC"));

            JsonNode metrics = mapper.readTree(get(dashboard.status().url() + "api/metrics").body());
            assertEquals(1, metrics.path("repositories").size());
            assertEquals(registered.id(), metrics.path("selectedRepository").path("id").asText());
            assertEquals("initializing", metrics.path("selectedRepository").path("analysisStatus").asText());
            assertEquals("no-store", get(dashboard.status().url() + "api/metrics").cacheControl());
        }
        assertFalse(DashboardServer.currentStatus(mapper, stateRoot).running());
    }

    @Test
    void selectsRepositoriesExplicitlyAndRejectsUnknownIds() throws Exception {
        ObjectMapper mapper = JaiPilotToolkit.mapper();
        Path stateRoot = tempDir.resolve("selection-state");
        RepositorySnapshotStore snapshots = new RepositorySnapshotStore(mapper, stateRoot);
        RepositorySnapshotStore.RepositoryState first = snapshots.register(TestProject.maven(tempDir, "first"));
        RepositorySnapshotStore.RepositoryState second = snapshots.register(TestProject.maven(tempDir, "second"));

        try (DashboardServer.RunningDashboard dashboard = DashboardServer.start(mapper, stateRoot, 0)) {
            JsonNode selected = mapper.readTree(get(dashboard.status().url()
                    + "api/metrics?repository=" + first.id()).body());
            assertEquals(first.id(), selected.path("selectedRepository").path("id").asText());
            assertNotEquals(second.id(), selected.path("selectedRepository").path("id").asText());
            assertEquals(400, get(dashboard.status().url()
                    + "api/metrics?repository=" + "0".repeat(64)).status());
        }
    }

    @Test
    void usesFallbackPortAndRejectsMutationOrUnknownRoutes() throws Exception {
        ObjectMapper mapper = JaiPilotToolkit.mapper();
        InetAddress loopback = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        try (ServerSocket occupied = new ServerSocket(0, 1, loopback);
             DashboardServer.RunningDashboard dashboard = DashboardServer.start(
                     mapper, tempDir.resolve("fallback"), occupied.getLocalPort())) {
            assertTrue(dashboard.status().fallbackPort());
            assertNotEquals(occupied.getLocalPort(), dashboard.status().port());
            assertEquals(405, request(dashboard.status().url() + "api/metrics", "POST").status());
            assertEquals(404, get(dashboard.status().url() + "missing").status());
            assertEquals(200, get(dashboard.status().url() + "api/health").status());
        }
    }

    private Response get(String url) throws Exception {
        return request(url, "GET");
    }

    private Response request(String url, String method) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(1_000);
        connection.setReadTimeout(1_000);
        connection.setRequestMethod(method);
        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        byte[] body = stream == null ? new byte[0] : stream.readAllBytes();
        if (stream != null) stream.close();
        Response response = new Response(
                status,
                new String(body, StandardCharsets.UTF_8),
                connection.getHeaderField("Content-Security-Policy"),
                connection.getHeaderField("Cache-Control")
        );
        connection.disconnect();
        return response;
    }

    private record Response(int status, String body, String contentSecurityPolicy, String cacheControl) {
    }
}
