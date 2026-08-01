package com.jaipilot.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jaipilot.mcp.core.WorkflowRunService;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpToolServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void exposesCompactUniqueToolSetWithSafetyAnnotations() {
        try (WorkflowRunService workflows = new WorkflowRunService()) {
            List<SyncToolSpecification> tools = new McpToolService(workflows).specifications();
            Set<String> names = tools.stream().map(tool -> tool.tool().name()).collect(Collectors.toSet());

            assertEquals(9, tools.size());
            assertEquals(9, names.size());
            assertTrue(names.containsAll(List.of(
                    "jaipilot_inspect_project",
                    "jaipilot_prepare_tests",
                    "jaipilot_prepare_cleanup",
                    "jaipilot_get_run",
                    "jaipilot_read_run_file",
                    "jaipilot_write_run_file",
                    "jaipilot_validate_run",
                    "jaipilot_apply_run",
                    "jaipilot_discard_run"
            )));
            SyncToolSpecification apply = find(tools, "jaipilot_apply_run");
            assertEquals(true, apply.tool().annotations().destructiveHint());
            assertEquals(false, apply.tool().annotations().readOnlyHint());
            assertNotNull(apply.tool().inputSchema().get("$schema"));
            assertEquals(false, apply.tool().inputSchema().get("additionalProperties"));
        }
    }

    @Test
    void inspectReturnsStructuredContentAndJsonText() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>\n");
        Path source = tempDir.resolve("src/main/java/com/example/OrderService.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package com.example; class OrderService {}\n");
        try (WorkflowRunService workflows = new WorkflowRunService()) {
            SyncToolSpecification inspect = find(new McpToolService(workflows).specifications(),
                    "jaipilot_inspect_project");

            CallToolResult result = inspect.callHandler().apply(
                    null,
                    CallToolRequest.builder("jaipilot_inspect_project")
                            .arguments(Map.of("projectRoot", tempDir.toString()))
                            .build()
            );

            assertEquals(false, result.isError());
            assertTrue(result.structuredContent() instanceof Map);
            assertTrue(result.content().get(0).toString().contains("projectRoot"));
        }
    }

    @Test
    void domainErrorsBecomeToolErrorsWithoutProtocolFailure() {
        try (WorkflowRunService workflows = new WorkflowRunService()) {
            SyncToolSpecification getRun = find(new McpToolService(workflows).specifications(),
                    "jaipilot_get_run");

            CallToolResult result = getRun.callHandler().apply(
                    null,
                    CallToolRequest.builder("jaipilot_get_run")
                            .arguments(Map.of("runId", "missing"))
                            .build()
            );

            assertEquals(true, result.isError());
            assertFalse(result.content().isEmpty());
            assertTrue(result.content().get(0).toString().contains("Unknown or completed"));
        }
    }

    private SyncToolSpecification find(List<SyncToolSpecification> tools, String name) {
        return tools.stream().filter(tool -> name.equals(tool.tool().name())).findFirst().orElseThrow();
    }
}
