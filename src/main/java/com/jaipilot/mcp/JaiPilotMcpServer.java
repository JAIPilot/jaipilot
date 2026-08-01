package com.jaipilot.mcp;

import com.jaipilot.mcp.core.WorkflowRunService;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;

/** Local stdio MCP server. Standard output is reserved exclusively for JSON-RPC. */
public final class JaiPilotMcpServer {

    static final String SERVER_NAME = "jaipilot-mcp";
    static final String VERSION = resolveVersion();

    private JaiPilotMcpServer() {
    }

    public static void main(String[] args) {
        WorkflowRunService workflows = new WorkflowRunService();
        StdioServerTransportProvider transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper());
        McpSyncServer server = buildServer(transport, workflows);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            workflows.close();
            server.closeGracefully();
        }, "jaipilot-mcp-shutdown"));
    }

    static McpSyncServer buildServer(
            StdioServerTransportProvider transport,
            WorkflowRunService workflows
    ) {
        McpToolService tools = new McpToolService(workflows);
        return McpServer.sync(transport)
                .serverInfo(SERVER_NAME, VERSION)
                .instructions("""
                        JAIPilot provides local, isolated Java test-generation and cleanup workflows.
                        Prepare a run, edit only the returned workspace, validate until it passes, then apply explicitly.
                        Never edit the live project while a run is open. Discard abandoned runs.
                        """)
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(tools.specifications())
                .strictToolNameValidation(true)
                .validateToolInputs(true)
                .build();
    }

    private static String resolveVersion() {
        String implementationVersion = JaiPilotMcpServer.class.getPackage().getImplementationVersion();
        return implementationVersion == null || implementationVersion.isBlank()
                ? "development"
                : implementationVersion;
    }
}
