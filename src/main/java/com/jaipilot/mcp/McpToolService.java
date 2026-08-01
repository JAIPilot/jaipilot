package com.jaipilot.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaipilot.mcp.core.WorkflowRunService;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Maps the workflow core onto compact, validated MCP tools. */
public final class McpToolService {

    private static final String JSON_SCHEMA_DIALECT = "https://json-schema.org/draft/2020-12/schema";

    private final WorkflowRunService workflows;
    private final ObjectMapper objectMapper;

    public McpToolService(WorkflowRunService workflows) {
        this(workflows, new ObjectMapper());
    }

    McpToolService(WorkflowRunService workflows, ObjectMapper objectMapper) {
        this.workflows = Objects.requireNonNull(workflows, "workflows");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public List<SyncToolSpecification> specifications() {
        return List.of(
                tool(
                        "jaipilot_inspect_project",
                        "Inspect a local Maven or Gradle Java project without changing source files.",
                        projectRootSchema(),
                        annotations(true, false, true),
                        request -> inspection(workflows.inspect(projectRoot(request)))
                ),
                tool(
                        "jaipilot_prepare_tests",
                        "Create an isolated workspace for agent-written Java unit tests after a clean baseline build.",
                        prepareTestsSchema(),
                        annotations(false, false, false),
                        request -> prepared(workflows.prepareTestGeneration(
                                projectRoot(request),
                                selection(request, true),
                                number(request, "minimumLineCoverage", 80.0d)
                        ))
                ),
                tool(
                        "jaipilot_prepare_cleanup",
                        "Create an isolated Java cleanup workspace and apply exact-scoped OpenRewrite recipes first.",
                        prepareCleanupSchema(),
                        annotations(false, false, false),
                        request -> prepared(workflows.prepareCodeCleanup(
                                projectRoot(request),
                                selection(request, false)
                        ))
                ),
                tool(
                        "jaipilot_get_run",
                        "Read the status and last validation result of an active JAIPilot run.",
                        runIdSchema(),
                        annotations(true, false, true),
                        request -> runStatus(workflows.getRun(string(request, "runId", true)))
                ),
                tool(
                        "jaipilot_read_run_file",
                        "Read one bounded UTF-8 text file from an active isolated workspace.",
                        runFileSchema(false),
                        annotations(true, false, true),
                        request -> runFile(workflows.readRunFile(
                                string(request, "runId", true),
                                string(request, "relativePath", true)
                        ))
                ),
                tool(
                        "jaipilot_write_run_file",
                        "Write one bounded UTF-8 Java file inside an active run's strict workflow allowlist.",
                        runFileSchema(true),
                        annotations(false, false, false),
                        request -> writtenRunFile(workflows.writeRunFile(
                                string(request, "runId", true),
                                string(request, "relativePath", true),
                                string(request, "content", true)
                        ))
                ),
                tool(
                        "jaipilot_validate_run",
                        "Validate candidate scope, clean build, test execution, and JaCoCo coverage without touching live source.",
                        runIdSchema(),
                        annotations(false, false, true),
                        request -> validation(workflows.validate(string(request, "runId", true)))
                ),
                tool(
                        "jaipilot_apply_run",
                        "Transactionally apply an immediately validated candidate if neither candidate nor live project drifted.",
                        runIdSchema(),
                        annotations(false, true, false),
                        request -> applied(workflows.apply(string(request, "runId", true)))
                ),
                tool(
                        "jaipilot_discard_run",
                        "Discard an isolated JAIPilot workspace without changing live source files.",
                        runIdSchema(),
                        annotations(false, false, true),
                        request -> {
                            String runId = string(request, "runId", true);
                            workflows.discard(runId);
                            return Map.of("runId", runId, "discarded", true);
                        }
                )
        );
    }

    private SyncToolSpecification tool(
            String name,
            String description,
            Map<String, Object> inputSchema,
            ToolAnnotations annotations,
            Function<CallToolRequest, Map<String, Object>> handler
    ) {
        Tool tool = Tool.builder(name, inputSchema)
                .description(description)
                .outputSchema(genericOutputSchema())
                .annotations(annotations)
                .build();
        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> handle(handler, request))
                .build();
    }

    private CallToolResult handle(
            Function<CallToolRequest, Map<String, Object>> handler,
            CallToolRequest request
    ) {
        try {
            Map<String, Object> result = handler.apply(request);
            return CallToolResult.builder()
                    .addTextContent(json(result))
                    .structuredContent(result)
                    .isError(false)
                    .build();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", exception.getClass().getSimpleName());
            error.put("message", safeMessage(exception));
            return CallToolResult.builder()
                    .addTextContent(json(error))
                    .structuredContent(error)
                    .isError(true)
                    .build();
        } catch (RuntimeException exception) {
            System.err.println("JAIPilot internal tool failure: " + safeMessage(exception));
            Map<String, Object> error = Map.of(
                    "error", "InternalError",
                    "message", "JAIPilot could not complete the operation. See server stderr for diagnostics."
            );
            return CallToolResult.builder()
                    .addTextContent(json(error))
                    .structuredContent(error)
                    .isError(true)
                    .build();
        }
    }

    private Path projectRoot(CallToolRequest request) {
        String configured = string(request, "projectRoot", false);
        return configured == null
                ? Path.of(System.getProperty("user.dir"))
                : Path.of(configured);
    }

    private WorkflowRunService.TargetSelection selection(CallToolRequest request, boolean allowCoverage) {
        List<String> classes = stringList(request, "classes");
        String defaultMode = classes.isEmpty() ? "changed" : "classes";
        String mode = string(request, "mode", false);
        mode = mode == null ? defaultMode : mode.toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "classes" -> WorkflowRunService.TargetSelection.classes(classes);
            case "changed" -> WorkflowRunService.TargetSelection.changed();
            case "all" -> WorkflowRunService.TargetSelection.all();
            case "coverage" -> {
                if (!allowCoverage) {
                    throw new IllegalArgumentException("coverage mode is only available for test generation.");
                }
                yield WorkflowRunService.TargetSelection.coverageBelow(
                        number(request, "coverageThreshold", 80.0d)
                );
            }
            default -> throw new IllegalArgumentException("Unsupported target mode: " + mode);
        };
    }

    private String string(CallToolRequest request, String name, boolean required) {
        Object value = request.arguments().get(name);
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException(name + " is required.");
            }
            return null;
        }
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(name + " must be a non-empty string.");
        }
        return text.trim();
    }

    private List<String> stringList(CallToolRequest request, String name) {
        Object value = request.arguments().get(name);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof String))) {
            throw new IllegalArgumentException(name + " must be an array of strings.");
        }
        return list.stream().map(String.class::cast).toList();
    }

    private double number(CallToolRequest request, String name, double defaultValue) {
        Object value = request.arguments().get(name);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(name + " must be a number.");
        }
        return number.doubleValue();
    }

    private Map<String, Object> inspection(WorkflowRunService.ProjectInspection value) {
        return map(
                "projectRoot", value.projectRoot().toString(),
                "buildTool", value.buildTool(),
                "wrapperAvailable", value.wrapperAvailable(),
                "jacocoConfigured", value.jacocoConfigured(),
                "productionClassCount", value.productionClassCount(),
                "changedProductionClasses", value.changedProductionClasses(),
                "cachedLineCoverage", value.cachedLineCoverage(),
                "cachedCoverageReport", value.cachedCoverageReport(),
                "activeRunId", value.activeRunId()
        );
    }

    private Map<String, Object> prepared(WorkflowRunService.PreparedRun value) {
        List<Map<String, Object>> targets = value.targets().stream().map(target -> map(
                "fullyQualifiedName", target.fullyQualifiedName(),
                "relativePath", target.relativePath().toString(),
                "lineCoverageBefore", target.lineCoverageBefore(),
                "likelyTests", target.likelyTests()
        )).toList();
        return map(
                "runId", value.runId(),
                "kind", value.kind().name(),
                "projectRoot", value.projectRoot().toString(),
                "workspaceRoot", value.workspaceRoot().toString(),
                "targets", targets,
                "openRewriteChanges", paths(value.openRewriteChanges()),
                "agentInstructions", value.agentInstructions(),
                "baselineElapsedMs", millis(value.baselineElapsed()),
                "openRewriteElapsedMs", millis(value.openRewriteElapsed()),
                "expiresAt", value.expiresAt().toString()
        );
    }

    private Map<String, Object> validation(WorkflowRunService.ValidationResult value) {
        Map<String, Object> coverage = new LinkedHashMap<>();
        value.coverage().forEach((name, change) -> coverage.put(name, map(
                "beforeLineCoverage", change.beforeLineCoverage(),
                "afterLineCoverage", change.afterLineCoverage(),
                "beforeBranchCoverage", change.beforeBranchCoverage(),
                "afterBranchCoverage", change.afterBranchCoverage()
        )));
        return map(
                "runId", value.runId(),
                "valid", value.valid(),
                "readyToApply", value.readyToApply(),
                "changedRelativePaths", paths(value.changedRelativePaths()),
                "warnings", value.warnings(),
                "failures", value.failures(),
                "missingTestReports", value.missingTestReports(),
                "coverageGoalMet", value.coverageGoalMet(),
                "coverage", coverage,
                "verificationElapsedMs", millis(value.verificationElapsed())
        );
    }

    private Map<String, Object> runStatus(WorkflowRunService.RunStatusView value) {
        return map(
                "runId", value.runId(),
                "kind", value.kind().name(),
                "status", value.status().name(),
                "projectRoot", value.projectRoot().toString(),
                "workspaceRoot", value.workspaceRoot().toString(),
                "createdAt", value.createdAt().toString(),
                "lastValidation", value.lastValidation() == null ? null : validation(value.lastValidation())
        );
    }

    private Map<String, Object> applied(WorkflowRunService.AppliedRun value) {
        return map(
                "runId", value.runId(),
                "applied", true,
                "changedRelativePaths", paths(value.changedRelativePaths())
        );
    }

    private Map<String, Object> runFile(WorkflowRunService.RunFile value) {
        return map(
                "runId", value.runId(),
                "relativePath", value.relativePath().toString(),
                "content", value.content()
        );
    }

    private Map<String, Object> writtenRunFile(WorkflowRunService.WrittenRunFile value) {
        return map(
                "runId", value.runId(),
                "relativePath", value.relativePath().toString(),
                "charactersWritten", value.charactersWritten()
        );
    }

    private List<String> paths(List<Path> paths) {
        return paths.stream().map(Path::toString).toList();
    }

    private long millis(Duration duration) {
        return duration == null ? 0L : duration.toMillis();
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize an MCP tool result.", exception);
        }
    }

    private String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private ToolAnnotations annotations(boolean readOnly, boolean destructive, boolean idempotent) {
        return ToolAnnotations.builder()
                .readOnlyHint(readOnly)
                .destructiveHint(destructive)
                .idempotentHint(idempotent)
                .openWorldHint(false)
                .build();
    }

    private Map<String, Object> projectRootSchema() {
        return objectSchema(map(
                "projectRoot", map(
                        "type", "string",
                        "description", "Absolute path inside the local Java project. Defaults to the server working directory."
                )
        ), List.of());
    }

    private Map<String, Object> prepareTestsSchema() {
        return targetSchema(true, map(
                "minimumLineCoverage", percentage("Requested per-target line-coverage goal; defaults to 80.")
        ));
    }

    private Map<String, Object> prepareCleanupSchema() {
        return targetSchema(false, Map.of());
    }

    private Map<String, Object> targetSchema(boolean coverageMode, Map<String, Object> extraProperties) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("projectRoot", map(
                "type", "string",
                "description", "Absolute path inside the local Java project. Defaults to the server working directory."
        ));
        properties.put("mode", map(
                "type", "string",
                "enum", coverageMode ? List.of("classes", "changed", "all", "coverage")
                        : List.of("classes", "changed", "all"),
                "description", "Target selection. Defaults to classes when classes is non-empty, otherwise changed."
        ));
        properties.put("classes", map(
                "type", "array",
                "items", Map.of("type", "string"),
                "uniqueItems", true,
                "description", "Class names, fully qualified names, or Java source paths for classes mode."
        ));
        if (coverageMode) {
            properties.put("coverageThreshold", percentage("Select classes below this fresh line coverage in coverage mode."));
        }
        properties.putAll(extraProperties);
        return objectSchema(properties, List.of());
    }

    private Map<String, Object> runIdSchema() {
        return objectSchema(map(
                "runId", map("type", "string", "minLength", 1, "description", "Run identifier returned by a prepare tool.")
        ), List.of("runId"));
    }

    private Map<String, Object> runFileSchema(boolean write) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("runId", map(
                "type", "string",
                "minLength", 1,
                "description", "Run identifier returned by a prepare tool."
        ));
        properties.put("relativePath", map(
                "type", "string",
                "minLength", 1,
                "description", "Workspace-relative file path without traversal."
        ));
        List<String> required = new java.util.ArrayList<>(List.of("runId", "relativePath"));
        if (write) {
            properties.put("content", map(
                    "type", "string",
                    "maxLength", WorkflowRunService.MAX_MCP_FILE_CHARACTERS,
                    "description", "Complete UTF-8 Java file content."
            ));
            required.add("content");
        }
        return objectSchema(properties, required);
    }

    private Map<String, Object> percentage(String description) {
        return map("type", "number", "minimum", 0, "maximum", 100, "description", description);
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return map(
                "$schema", JSON_SCHEMA_DIALECT,
                "type", "object",
                "properties", properties,
                "required", required,
                "additionalProperties", false
        );
    }

    private Map<String, Object> genericOutputSchema() {
        return map(
                "$schema", JSON_SCHEMA_DIALECT,
                "type", "object",
                "additionalProperties", true
        );
    }

    private Map<String, Object> map(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("Map entries must be key/value pairs.");
        }
        Map<String, Object> value = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            value.put((String) entries[index], entries[index + 1]);
        }
        return value;
    }
}
