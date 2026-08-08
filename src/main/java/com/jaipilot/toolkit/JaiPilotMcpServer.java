package com.jaipilot.toolkit;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;

/** Provider-neutral stdio entry point for JAIPilot's deterministic evidence tools. */
public final class JaiPilotMcpServer {

    private JaiPilotMcpServer() {
    }

    public static void main(String[] arguments) {
        StdioServerTransportProvider transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper());
        JaiPilotMcpTools tools = new JaiPilotMcpTools();
        McpSyncServer server = buildServer(transport, tools);
        Runtime.getRuntime().addShutdownHook(new Thread(server::closeGracefully, "jaipilot-mcp-shutdown"));
    }

    static McpSyncServer buildServer(StdioServerTransportProvider transport, JaiPilotMcpTools tools) {
        return McpServer.sync(transport)
                .serverInfo("jaipilot", version())
                .instructions("""
                        JAIPilot is a local deterministic evidence kernel for Java coding agents.
                        The host agent owns planning, edits, retries, Git, and user interaction.
                        Use JAIPilot to inspect, measure, rewrite with pinned recipes, and prove exact diffs.
                        """)
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(tools.specifications())
                .strictToolNameValidation(true)
                .validateToolInputs(true)
                .build();
    }

    private static String version() {
        String value = JaiPilotMcpServer.class.getPackage().getImplementationVersion();
        return value == null || value.isBlank() ? "development" : value;
    }
}
