package com.jaipilot.toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JaiPilotMcpToolsTest {

    @TempDir
    Path tempDir;

    @Test
    void exposesOnlySixClosedDeterministicTools() {
        List<SyncToolSpecification> tools = adapter().specifications();
        Set<String> names = tools.stream().map(tool -> tool.tool().name()).collect(Collectors.toSet());

        assertEquals(Set.of(
                "jaipilot_inspect", "jaipilot_snapshot", "jaipilot_quality",
                "jaipilot_rewrite", "jaipilot_diff_gate", "jaipilot_prove_diff"
        ), names);
        for (SyncToolSpecification tool : tools) {
            assertTrue(tool.tool().title().startsWith("JAIPilot: "));
            assertTrue(tool.tool().description().contains("exact user-facing line"));
            assertTrue(tool.tool().description().contains("Why this mattered"));
            assertTrue(tool.tool().description().contains("do not replace them with a generic summary"));
            assertNotNull(tool.tool().inputSchema().get("$schema"));
            assertEquals(false, tool.tool().inputSchema().get("additionalProperties"));
            assertEquals(List.of("projectRoot"), tool.tool().inputSchema().get("required"));
            assertEquals(false, tool.tool().outputSchema().get("additionalProperties"));
            assertEquals(false, tool.tool().annotations().openWorldHint());
        }
        assertEquals(true, find(tools, "jaipilot_inspect").tool().annotations().readOnlyHint());
        assertEquals(true, find(tools, "jaipilot_rewrite").tool().annotations().destructiveHint());
        assertEquals(false, find(tools, "jaipilot_prove_diff").tool().annotations().readOnlyHint());
    }

    @Test
    void invokesStructuredRunnerSynchronouslyAndRegistersRepository() throws Exception {
        Path project = TestProject.maven(tempDir, "inspect");
        JaiPilotMcpTools adapter = adapter();
        CallToolResult result = call(find(adapter.specifications(), "jaipilot_inspect"), Map.of(
                "projectRoot", project.toString()
        ));

        assertFalse(result.isError());
        assertEquals(true, ((Map<?, ?>) result.structuredContent()).get("ok"));
        assertEquals(2, result.content().size());
        TextContent summary = (TextContent) result.content().get(1);
        assertTrue(summary.text().contains("JAIPilot finished: Inspect Java repository (completed)"));
        assertTrue(summary.text().contains("Why this mattered:"));
        assertTrue(summary.text().contains("build=maven"));
        Map<?, ?> metadata = (Map<?, ?>) result.meta().get("jaipilot");
        assertEquals("Inspect Java repository", metadata.get("operation"));
        assertEquals("completed", metadata.get("status"));
        RepositorySnapshotStore.DashboardView dashboard = new RepositorySnapshotStore(
                JaiPilotToolkit.mapper(), tempDir.resolve("state")
        ).view(null);
        assertEquals(project.toRealPath().toString(), dashboard.selectedRepository().projectRoot());
        assertEquals("initializing", dashboard.selectedRepository().analysisStatus());
    }

    @Test
    void rejectsUnknownPropertiesAndInvalidPercentages() {
        List<SyncToolSpecification> tools = adapter().specifications();
        CallToolResult missingProject = call(find(tools, "jaipilot_inspect"), Map.of());
        CallToolResult invalid = call(find(tools, "jaipilot_diff_gate"), Map.of(
                "projectRoot", tempDir.toString(), "minimumLineCoverage", 101
        ));
        assertTrue(missingProject.isError());
        assertTrue(missingProject.structuredContent().toString().contains("projectRoot"));
        TextContent summary = (TextContent) missingProject.content().get(1);
        assertTrue(summary.text().contains("failed closed"));
        assertTrue(invalid.isError());
        assertTrue(invalid.structuredContent().toString().contains("minimumLineCoverage"));
    }

    private JaiPilotMcpTools adapter() {
        return new JaiPilotMcpTools(JaiPilotToolkit.mapper(), tempDir.resolve("state"));
    }

    private SyncToolSpecification find(List<SyncToolSpecification> tools, String name) {
        return tools.stream().filter(tool -> name.equals(tool.tool().name())).findFirst().orElseThrow();
    }

    private CallToolResult call(SyncToolSpecification tool, Map<String, Object> arguments) {
        return tool.callHandler().apply(
                null,
                CallToolRequest.builder(tool.tool().name()).arguments(arguments).build()
        );
    }
}
