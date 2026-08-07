package com.jaipilot.toolkit;

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
            assertTrue(page.contentSecurityPolicy().contains("default-src 'self'"));

            Response metrics = get(dashboard.status().url() + "api/metrics");
            JsonNode json = mapper.readTree(metrics.body());
            assertEquals(200, metrics.status());
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
                connection.getHeaderField("Content-Security-Policy")
        );
        connection.disconnect();
        return response;
    }

    private record Response(int status, String body, String contentSecurityPolicy) {
    }
}
