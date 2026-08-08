package com.jaipilot.toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JaiPilotMcpServerTest {

    @TempDir
    Path tempDir;

    @Test
    void serverAdvertisesOnlyTools() {
        StdioServerTransportProvider transport = new StdioServerTransportProvider(
                McpJsonDefaults.getMapper(), new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        McpSyncServer server = JaiPilotMcpServer.buildServer(
                transport, new JaiPilotMcpTools(JaiPilotToolkit.mapper(), tempDir.resolve("state")));
        try {
            assertEquals(6, server.listTools().size());
            assertNotNull(server.getServerCapabilities().tools());
            assertNull(server.getServerCapabilities().prompts());
            assertNull(server.getServerCapabilities().resources());
            assertEquals("jaipilot", server.getServerInfo().name());
        } finally {
            server.closeGracefully();
        }
    }

    @Test
    void initializeAndToolsListUseProtocolOnlyStdout() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PipedInputStream input = new PipedInputStream();
        try (PipedOutputStream client = new PipedOutputStream(input)) {
            StdioServerTransportProvider transport = new StdioServerTransportProvider(
                    McpJsonDefaults.getMapper(), input, output);
            McpSyncServer server = JaiPilotMcpServer.buildServer(
                    transport, new JaiPilotMcpTools(JaiPilotToolkit.mapper(), tempDir.resolve("protocol")));
            try {
                client.write(("""
                        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}
                        """).getBytes(StandardCharsets.UTF_8));
                client.flush();
                waitFor(() -> output.toString(StandardCharsets.UTF_8).contains("\"id\":1"));
                client.write(("""
                        {"jsonrpc":"2.0","method":"notifications/initialized"}
                        {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                        """).getBytes(StandardCharsets.UTF_8));
                client.flush();
                waitFor(() -> output.toString(StandardCharsets.UTF_8).contains("\"id\":2"));

                String messages = output.toString(StandardCharsets.UTF_8);
                assertTrue(messages.lines().filter(line -> !line.isBlank()).allMatch(this::jsonLine));
                assertTrue(messages.contains("jaipilot_prove_diff"));
                assertTrue(messages.contains("jaipilot_rewrite"));
                assertTrue(!messages.contains("jaipilot_operation_status"));
            } finally {
                server.closeGracefully();
            }
        }
    }

    private boolean jsonLine(String line) {
        try {
            JaiPilotToolkit.mapper().readTree(line);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private void waitFor(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(condition.getAsBoolean(), "Timed out waiting for MCP response");
    }
}
