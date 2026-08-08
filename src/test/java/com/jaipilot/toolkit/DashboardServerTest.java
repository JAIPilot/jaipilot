package com.jaipilot.toolkit;

import static com.jaipilot.toolkit.DashboardProofFixture.proof;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DashboardServerTest {

    @TempDir
    Path tempDir;

    @Test
    void servesDashboardAndMetricsOnlyOnLoopback() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path stateRoot = tempDir.resolve("state");
        new UsageMetricsStore(mapper, stateRoot).record(
                "inspect",
                0,
                Map.of("projectRoot", tempDir.resolve("project").toString()),
                Duration.ofMillis(42)
        );

        try (DashboardServer.RunningDashboard dashboard = DashboardServer.start(mapper, stateRoot, 0)) {
            assertTrue(dashboard.status().running());
            assertTrue(dashboard.status().url().startsWith("http://127.0.0.1:"));

            Response page = get(dashboard.status().url());
            assertEquals(200, page.status());
            assertTrue(page.body().contains("JAIPilot Impact Dashboard"));
            assertTrue(page.body().contains("current-status-panel"));
            assertTrue(page.body().contains("gate-message-count"));
            assertTrue(page.contentSecurityPolicy().contains("default-src 'self'"));
            assertEquals("no-cache", page.cacheControl());

            Response script = get(dashboard.status().url() + "assets/dashboard.js");
            assertEquals(200, script.status());
            assertTrue(script.body().contains("renderCurrentStatus"));
            assertTrue(script.body().contains("Showing ${items.length} of ${total} gate messages"));
            assertEquals("no-cache", script.cacheControl());

            Response styles = get(dashboard.status().url() + "assets/dashboard.css");
            assertEquals(200, styles.status());
            assertTrue(styles.body().contains(".current-status-grid"));
            assertEquals("no-cache", styles.cacheControl());

            Response metrics = get(dashboard.status().url() + "api/metrics");
            JsonNode json = mapper.readTree(metrics.body());
            assertEquals(200, metrics.status());
            assertEquals("no-store", metrics.cacheControl());
            assertEquals(1, json.path("usage").path("totalCommands").asInt());
            assertEquals(1, json.path("usage").path("projectsSeen").asInt());

            DashboardServer.DashboardStatus discovered = DashboardServer.currentStatus(mapper, stateRoot);
            assertTrue(discovered.running());
            assertEquals(dashboard.status().url(), discovered.url());
            assertNull(DashboardServer.start(mapper, stateRoot, 0));
        }

        assertFalse(DashboardServer.currentStatus(mapper, stateRoot).running());
        assertFalse(Files.exists(stateRoot.resolve("dashboard/server.json")));
        assertFalse(Files.exists(stateRoot.resolve("dashboard/server.ready")));
    }

    @Test
    void selectsAFreePortWhenThePreferredPortIsOccupied() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InetAddress loopback = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        try (ServerSocket occupied = new ServerSocket(0, 1, loopback);
                DashboardServer.RunningDashboard dashboard = DashboardServer.start(
                        mapper,
                        tempDir.resolve("fallback-state"),
                        occupied.getLocalPort()
                )) {
            assertTrue(dashboard.status().fallbackPort());
            assertEquals(occupied.getLocalPort(), dashboard.status().preferredPort());
            assertNotEquals(occupied.getLocalPort(), dashboard.status().port());
            assertEquals(200, get(dashboard.status().url() + "api/health").status());
        }
    }

    @Test
    void rejectsMutatingHttpMethodsAndUnknownRoutes() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (DashboardServer.RunningDashboard dashboard = DashboardServer.start(
                mapper,
                tempDir.resolve("read-only-state"),
                0
        )) {
            assertEquals(405, request(dashboard.status().url() + "api/metrics", "POST").status());
            assertEquals(404, get(dashboard.status().url() + "does-not-exist").status());
        }
    }

    @Test
    void servesFreshProofStatusWrittenAfterTheDashboardStarts() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path stateRoot = tempDir.resolve("live-state");
        UsageMetricsStore writer = new UsageMetricsStore(mapper, stateRoot);

        try (DashboardServer.RunningDashboard dashboard = DashboardServer.start(mapper, stateRoot, 0)) {
            JsonNode initial = metrics(mapper, dashboard.status().url());
            assertFalse(initial.path("latestEvidence").path("findings").path("total").isNumber());

            writer.record("prove-diff", 1, proof(false), Duration.ZERO);
            JsonNode gaps = metrics(mapper, dashboard.status().url());
            assertEquals(1, gaps.path("latestEvidence").path("findings").path("total").asInt());
            assertEquals(1, gaps.path("latestEvidence").path("architecture").path("violationCount").asInt());
            assertFalse(gaps.path("latestEvidence").path("gates").path("passed").asBoolean());

            writer.record("prove-diff", 0, proof(true), Duration.ZERO);
            JsonNode clean = metrics(mapper, dashboard.status().url());
            assertEquals(0, clean.path("latestEvidence").path("findings").path("total").asInt());
            assertEquals(0, clean.path("latestEvidence").path("architecture").path("violationCount").asInt());
            assertTrue(clean.path("latestEvidence").path("architecture").path("goalMet").asBoolean());
            assertTrue(clean.path("latestEvidence").path("gates").path("passed").asBoolean());
        }
    }

    private JsonNode metrics(ObjectMapper mapper, String dashboardUrl) throws Exception {
        return mapper.readTree(get(dashboardUrl + "api/metrics").body());
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
        InputStream responseStream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        byte[] bytes = new byte[0];
        if (responseStream != null) {
            try (responseStream) {
                bytes = responseStream.readAllBytes();
            }
        }
        Response response = new Response(
                status,
                new String(bytes, StandardCharsets.UTF_8),
                connection.getHeaderField("Content-Security-Policy"),
                connection.getHeaderField("Cache-Control")
        );
        connection.disconnect();
        return response;
    }

    private record Response(int status, String body, String contentSecurityPolicy, String cacheControl) {
    }
}
