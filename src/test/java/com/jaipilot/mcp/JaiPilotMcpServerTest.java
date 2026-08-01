package com.jaipilot.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jaipilot.mcp.core.WorkflowRunService;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JaiPilotMcpServerTest {

    @Test
    void buildsWithValidatedSchemasAndOnlyToolCapability() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StdioServerTransportProvider transport = new StdioServerTransportProvider(
                McpJsonDefaults.getMapper(),
                new ByteArrayInputStream(new byte[0]),
                output
        );
        try (WorkflowRunService workflows = new WorkflowRunService()) {
            McpSyncServer server = JaiPilotMcpServer.buildServer(transport, workflows);
            try {
                assertEquals(9, server.listTools().size());
                assertNotNullCapabilities(server);
                assertEquals(JaiPilotMcpServer.SERVER_NAME, server.getServerInfo().name());
            } finally {
                server.closeGracefully();
            }
        }
    }

    @Test
    void handlesInitializeAndToolsListOverJsonRpcStdio() throws Exception {
        String initialize = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"fixture","version":"1"}}}
                """;
        String listTools = """
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                """;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PipedInputStream input = new PipedInputStream();
        PipedOutputStream client = new PipedOutputStream(input);
        StdioServerTransportProvider transport = new StdioServerTransportProvider(
                McpJsonDefaults.getMapper(),
                input,
                output
        );
        try (WorkflowRunService workflows = new WorkflowRunService()) {
            McpSyncServer server = JaiPilotMcpServer.buildServer(transport, workflows);
            try {
                client.write(initialize.getBytes(StandardCharsets.UTF_8));
                client.flush();
                waitFor(() -> output.toString(StandardCharsets.UTF_8).contains("\"id\":1"));
                client.write(listTools.getBytes(StandardCharsets.UTF_8));
                client.flush();
                waitFor(() -> output.toString(StandardCharsets.UTF_8).contains("\"id\":2"));
                String messages = output.toString(StandardCharsets.UTF_8);
                assertTrue(messages.contains("\"name\":\"jaipilot-mcp\""));
                assertTrue(messages.contains("jaipilot_prepare_tests"));
                assertTrue(messages.contains("jaipilot_prepare_cleanup"));
            } finally {
                client.close();
                server.closeGracefully();
            }
        }
    }

    @Test
    void exposesDevelopmentVersionDuringUnpackagedTests() {
        assertEquals("development", JaiPilotMcpServer.VERSION);
    }

    private void assertNotNullCapabilities(McpSyncServer server) {
        assertTrue(server.getServerCapabilities().tools() != null);
        assertTrue(server.getServerCapabilities().resources() == null);
        assertTrue(server.getServerCapabilities().prompts() == null);
    }

    private void waitFor(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for MCP response");
    }
}