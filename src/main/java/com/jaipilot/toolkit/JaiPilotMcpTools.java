package com.jaipilot.toolkit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** Small schema-validated MCP adapter over the private structured runner. */
final class JaiPilotMcpTools {

    private static final String SCHEMA = "https://json-schema.org/draft/2020-12/schema";
    private static final int MAX_RESULT_BYTES = 2 * 1024 * 1024;
    private static final TypeReference<LinkedHashMap<String, Object>> OBJECT = new TypeReference<>() {
    };

    private final ObjectMapper mapper;
    private final Path stateRoot;
    private final PrintStream diagnostics;

    JaiPilotMcpTools() {
        this(JaiPilotToolkit.mapper(), ToolkitRunStore.defaultRoot(), System.err);
    }

    JaiPilotMcpTools(ObjectMapper mapper, Path stateRoot) {
        this(mapper, stateRoot, System.err);
    }

    JaiPilotMcpTools(ObjectMapper mapper, Path stateRoot, PrintStream diagnostics) {
        this.mapper = mapper;
        this.stateRoot = stateRoot.toAbsolutePath().normalize();
        this.diagnostics = diagnostics;
    }

    List<SyncToolSpecification> specifications() {
        return List.of(
                tool("jaipilot_inspect", "Discover a local Java repository and deterministic engines.",
                        projectSchema(), hints(true, false), request -> projectCommand("inspect", request)),
                tool("jaipilot_snapshot", "Refresh current whole-repository quality and dashboard evidence.",
                        projectSchema(), hints(true, false), request -> projectCommand("snapshot", request)),
                tool("jaipilot_quality", "Return deterministic quality metrics and actionable findings.",
                        selectionSchema(), hints(true, false), request -> selectionCommand("quality", request)),
                tool("jaipilot_rewrite", "Run pinned, exactly scoped OpenRewrite recipes in the agent-managed worktree.",
                        selectionSchema(), hints(false, true), request -> selectionCommand("rewrite", request)),
                tool("jaipilot_diff_gate", "Check whether the exact Java/build diff has a valid proof receipt.",
                        proofSchema(), hints(true, false), request -> proofCommand("diff-gate", request)),
                tool("jaipilot_prove_diff", "Run clean build, coverage, PIT, quality, and ArchUnit proof for the exact diff.",
                        proofSchema(), hints(false, false), request -> proofCommand("prove-diff", request))
        );
    }

    private SyncToolSpecification tool(
            String name,
            String description,
            Map<String, Object> inputSchema,
            ToolAnnotations annotations,
            Function<CallToolRequest, List<String>> command
    ) {
        Tool definition = Tool.builder(name, inputSchema)
                .description(description)
                .outputSchema(outputSchema())
                .annotations(annotations)
                .build();
        return SyncToolSpecification.builder()
                .tool(definition)
                .callHandler((exchange, request) -> dispatch(request, command))
                .build();
    }

    private CallToolResult dispatch(
            CallToolRequest request,
            Function<CallToolRequest, List<String>> command
    ) {
        try {
            return invoke(command.apply(request));
        } catch (IllegalArgumentException exception) {
            return error("invalid_request", safeMessage(exception));
        }
    }

    private CallToolResult invoke(List<String> arguments) {
        try {
            BoundedOutputStream bytes = new BoundedOutputStream(MAX_RESULT_BYTES);
            int status;
            try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
                status = JaiPilotToolkit.run(
                        arguments.toArray(String[]::new),
                        output,
                        System.err,
                        stateRoot,
                        mapper
                );
            }
            Map<String, Object> payload = mapper.readValue(bytes.value(), OBJECT);
            return CallToolResult.builder()
                    .addTextContent(json(payload))
                    .structuredContent(payload)
                    .isError(status != 0)
                    .build();
        } catch (IllegalArgumentException exception) {
            return error("invalid_request", safeMessage(exception));
        } catch (IOException | RuntimeException exception) {
            diagnostics.println("jaipilot-mcp: deterministic tool failed ["
                    + exception.getClass().getSimpleName() + "]");
            return error("deterministic_check_failed", safeMessage(exception));
        }
    }

    private CallToolResult error(String code, String message) {
        Map<String, Object> payload = map(
                "ok", false,
                "error", map("code", code, "message", message)
        );
        return CallToolResult.builder()
                .addTextContent(json(payload))
                .structuredContent(payload)
                .isError(true)
                .build();
    }

    private List<String> projectCommand(String command, CallToolRequest request) {
        List<String> result = new ArrayList<>();
        result.add(command);
        add(result, "--project", text(request, "projectRoot", true));
        return List.copyOf(result);
    }

    private List<String> selectionCommand(String command, CallToolRequest request) {
        List<String> result = new ArrayList<>(projectCommand(command, request));
        String mode = text(request, "mode", false);
        add(result, "--mode", mode == null ? null : mode.toLowerCase(Locale.ROOT));
        for (String value : strings(request, "classes")) {
            add(result, "--class", value);
        }
        addNumber(result, "--coverage-threshold", request, "coverageThreshold");
        return List.copyOf(result);
    }

    private List<String> proofCommand(String command, CallToolRequest request) {
        List<String> result = new ArrayList<>(projectCommand(command, request));
        addNumber(result, "--minimum-line-coverage", request, "minimumLineCoverage");
        addNumber(result, "--minimum-branch-coverage", request, "minimumBranchCoverage");
        addNumber(result, "--minimum-mutation-score", request, "minimumMutationScore");
        addNumber(result, "--minimum-quality-score", request, "minimumQualityScore");
        return List.copyOf(result);
    }

    private Map<String, Object> projectSchema() {
        return objectSchema(map(
                "projectRoot", map(
                        "type", "string",
                        "minLength", 1,
                        "description", "Existing local Java project path detected by the host coding agent."
                )
        ), List.of("projectRoot"));
    }

    private Map<String, Object> selectionSchema() {
        Map<String, Object> properties = new LinkedHashMap<>(properties(projectSchema()));
        properties.put("mode", map(
                "type", "string",
                "enum", List.of("classes", "changed", "all", "coverage")
        ));
        properties.put("classes", map(
                "type", "array",
                "items", map("type", "string", "minLength", 1),
                "uniqueItems", true
        ));
        properties.put("coverageThreshold", percentage());
        return objectSchema(properties, List.of("projectRoot"));
    }

    private Map<String, Object> proofSchema() {
        Map<String, Object> properties = new LinkedHashMap<>(properties(projectSchema()));
        properties.put("minimumLineCoverage", percentage());
        properties.put("minimumBranchCoverage", percentage());
        properties.put("minimumMutationScore", percentage());
        properties.put("minimumQualityScore", percentage());
        return objectSchema(properties, List.of("projectRoot"));
    }

    private Map<String, Object> outputSchema() {
        return map(
                "$schema", SCHEMA,
                "type", "object",
                "properties", map("ok", map("type", "boolean"), "result", Map.of(), "error", Map.of()),
                "required", List.of("ok"),
                "additionalProperties", false
        );
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return map(
                "$schema", SCHEMA,
                "type", "object",
                "properties", properties,
                "required", required,
                "additionalProperties", false
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> properties(Map<String, Object> schema) {
        return (Map<String, Object>) schema.get("properties");
    }

    private Map<String, Object> percentage() {
        return map("type", "number", "minimum", 0, "maximum", 100);
    }

    private ToolAnnotations hints(boolean readOnly, boolean destructive) {
        return ToolAnnotations.builder()
                .readOnlyHint(readOnly)
                .destructiveHint(destructive)
                .idempotentHint(readOnly)
                .openWorldHint(false)
                .build();
    }

    private Map<String, Object> arguments(CallToolRequest request) {
        return request.arguments() == null ? Map.of() : request.arguments();
    }

    private String text(CallToolRequest request, String name, boolean required) {
        Object value = arguments(request).get(name);
        if (value == null && !required) {
            return null;
        }
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(name + " must be a non-empty string.");
        }
        return text.trim();
    }

    private List<String> strings(CallToolRequest request, String name) {
        Object value = arguments(request).get(name);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> values)) {
            throw new IllegalArgumentException(name + " must be an array of strings.");
        }
        List<String> result = new ArrayList<>(values.size());
        for (Object item : values) {
            if (!(item instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException(name + " must contain non-empty strings.");
            }
            result.add(text.trim());
        }
        return List.copyOf(result);
    }

    private void addNumber(List<String> result, String option, CallToolRequest request, String property) {
        Object value = arguments(request).get(property);
        if (value == null) {
            return;
        }
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() < 0 || number.doubleValue() > 100) {
            throw new IllegalArgumentException(property + " must be a number from 0 through 100.");
        }
        add(result, option, Double.toString(number.doubleValue()));
    }

    private void add(List<String> result, String option, String value) {
        if (value != null) {
            result.add(option);
            result.add(value);
        }
    }

    private String json(Map<String, Object> value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize MCP output.", exception);
        }
    }

    private String safeMessage(Throwable failure) {
        String value = failure.getMessage();
        return value == null || value.isBlank() ? failure.getClass().getSimpleName() : value;
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> map(Object... entries) {
        Map<K, V> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((K) entries[index], (V) entries[index + 1]);
        }
        return result;
    }

    private static final class BoundedOutputStream extends OutputStream {

        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        private final int limit;

        private BoundedOutputStream(int limit) {
            this.limit = limit;
        }

        @Override
        public void write(int value) throws IOException {
            require(1);
            delegate.write(value);
        }

        @Override
        public void write(byte[] values, int offset, int length) throws IOException {
            require(length);
            delegate.write(values, offset, length);
        }

        private void require(int length) throws IOException {
            if (delegate.size() + length > limit) {
                throw new IOException("Toolkit output exceeded the 2 MiB MCP limit.");
            }
        }

        private byte[] value() {
            return delegate.toByteArray();
        }
    }
}
