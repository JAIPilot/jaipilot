package com.jaipilot.toolkit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.LoggingLevel;
import io.modelcontextprotocol.spec.McpSchema.LoggingMessageNotification;
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
                tool("jaipilot_inspect", "Inspect Java repository",
                        "Discover a local Java repository and deterministic engines.",
                        projectSchema(), hints(true, false), request -> projectCommand("inspect", request)),
                tool("jaipilot_snapshot", "Refresh current evidence",
                        "Refresh current whole-repository quality and dashboard evidence.",
                        projectSchema(), hints(true, false), request -> projectCommand("snapshot", request)),
                tool("jaipilot_quality", "Analyze Java quality",
                        "Return deterministic quality metrics and actionable findings.",
                        selectionSchema(), hints(true, false), request -> selectionCommand("quality", request)),
                tool("jaipilot_rewrite", "Run scoped OpenRewrite cleanup",
                        "Run pinned, exactly scoped OpenRewrite recipes in the agent-managed worktree.",
                        selectionSchema(), hints(false, true), request -> selectionCommand("rewrite", request)),
                tool("jaipilot_diff_gate", "Check exact-diff proof",
                        "Check whether the exact Java/build diff has a valid proof receipt.",
                        proofSchema(), hints(true, false), request -> proofCommand("diff-gate", request)),
                tool("jaipilot_prove_diff", "Prove exact Java diff",
                        "Run clean build, coverage, PIT, quality, and ArchUnit proof for the exact diff.",
                        proofSchema(), hints(false, false), request -> proofCommand("prove-diff", request))
        );
    }

    private SyncToolSpecification tool(
            String name,
            String title,
            String description,
            Map<String, Object> inputSchema,
            ToolAnnotations annotations,
            Function<CallToolRequest, List<String>> command
    ) {
        Tool definition = Tool.builder(name, inputSchema)
                .title("JAIPilot: " + title)
                .description(description + " Before calling, send this exact user-facing line: \"JAIPilot is running: "
                        + title + ".\" After completion, copy the returned JAIPilot finished, Why this mattered, "
                        + "and Evidence lines to the user; do not replace them with a generic summary.")
                .outputSchema(outputSchema())
                .annotations(annotations)
                .build();
        return SyncToolSpecification.builder()
                .tool(definition)
                .callHandler((exchange, request) -> {
                    announce(exchange, title);
                    return dispatch(request, name, title, command);
                })
                .build();
    }

    private void announce(McpSyncServerExchange exchange, String title) {
        if (exchange != null) {
            exchange.loggingNotification(new LoggingMessageNotification(
                    LoggingLevel.NOTICE,
                    "jaipilot",
                    "JAIPilot is running: " + title + "."
            ));
        }
    }

    private CallToolResult dispatch(
            CallToolRequest request,
            String name,
            String title,
            Function<CallToolRequest, List<String>> command
    ) {
        try {
            return invoke(name, title, command.apply(request));
        } catch (IllegalArgumentException exception) {
            return error(name, title, "invalid_request", safeMessage(exception));
        }
    }

    private CallToolResult invoke(String name, String title, List<String> arguments) {
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
            return result(name, title, payload, status != 0);
        } catch (IllegalArgumentException exception) {
            return error(name, title, "invalid_request", safeMessage(exception));
        } catch (IOException | RuntimeException exception) {
            diagnostics.println("jaipilot-mcp: deterministic tool failed ["
                    + exception.getClass().getSimpleName() + "]");
            return error(name, title, "deterministic_check_failed", safeMessage(exception));
        }
    }

    private CallToolResult result(String name, String title, Map<String, Object> payload, boolean failed) {
        RunSummary summary = summarize(name, title, payload, failed);
        return CallToolResult.builder()
                .addTextContent(json(payload))
                .addTextContent(summary.text())
                .structuredContent(payload)
                .meta(map("jaipilot", summary.metadata()))
                .isError(failed)
                .build();
    }

    private CallToolResult error(String name, String title, String code, String message) {
        Map<String, Object> payload = map(
                "ok", false,
                "error", map("code", code, "message", message)
        );
        return result(name, title, payload, true);
    }

    private RunSummary summarize(String name, String title, Map<String, Object> payload, boolean failed) {
        Map<String, Object> outcome = object(payload.get("result"));
        if (outcome.isEmpty()) {
            Map<String, Object> error = object(payload.get("error"));
            return new RunSummary(
                    title,
                    "failed",
                    "JAIPilot failed closed, so incomplete deterministic evidence was not presented as a pass.",
                    value(error.get("code"), "deterministic_check_failed") + ": "
                            + value(error.get("message"), "No additional detail was returned.")
            );
        }
        String status = summaryStatus(name, outcome, failed);
        return new RunSummary(title, status, benefit(name, status), evidence(name, outcome));
    }

    private String summaryStatus(String name, Map<String, Object> outcome, boolean failed) {
        return switch (name) {
            case "jaipilot_snapshot" -> Boolean.FALSE.equals(outcome.get("applicable"))
                    ? "not applicable" : "completed";
            case "jaipilot_diff_gate" -> value(outcome.get("status"), "unknown").replace('_', ' ');
            case "jaipilot_prove_diff" -> Boolean.TRUE.equals(outcome.get("passed")) && !failed
                    ? "passed" : "action required";
            default -> "completed";
        };
    }

    private String benefit(String name, String status) {
        return switch (name) {
            case "jaipilot_inspect" -> "JAIPilot established the repository and build boundary before deeper checks, reducing wrong-scope analysis.";
            case "jaipilot_snapshot" -> "not applicable".equals(status)
                    ? "JAIPilot confirmed that no Java baseline applied and made no quality claim."
                    : "JAIPilot replaced unknown or stale project state with a timestamped whole-repository baseline.";
            case "jaipilot_quality" -> "JAIPilot measured the selected scope before code was declared clean; zero targets remain explicit.";
            case "jaipilot_rewrite" -> "JAIPilot kept pinned OpenRewrite cleanup inside the selected scope; the host still reviews the diff.";
            case "jaipilot_diff_gate" -> "JAIPilot checked proof freshness against the exact current fingerprint instead of trusting stale evidence.";
            case "jaipilot_prove_diff" -> "JAIPilot ran the clean build and applicable gates instead of relying on agent confidence; failures remain explicit.";
            default -> throw new IllegalArgumentException("Unknown JAIPilot MCP tool: " + name);
        };
    }

    private String evidence(String name, Map<String, Object> outcome) {
        return switch (name) {
            case "jaipilot_inspect" -> "build=" + value(outcome.get("buildTool"), "unknown")
                    + "; production classes=" + value(outcome.get("productionClassCount"), "unknown")
                    + "; changed production classes=" + size(outcome.get("changedProductionClasses"))
                    + "; JaCoCo=" + configured(outcome.get("jacocoConfigured"));
            case "jaipilot_snapshot" -> snapshotEvidence(outcome);
            case "jaipilot_quality" -> qualityEvidence(outcome);
            case "jaipilot_rewrite" -> "recipe command completed; elapsed="
                    + value(outcome.get("elapsed"), "unavailable");
            case "jaipilot_diff_gate" -> "proof-relevant paths=" + size(outcome.get("proofRelevantPaths"))
                    + "; fingerprint=" + abbreviated(outcome.get("fingerprint"))
                    + "; verified=" + (outcome.get("verifiedAt") == null ? "no" : "yes");
            case "jaipilot_prove_diff" -> "targets=" + size(outcome.get("targets"))
                    + "; failures=" + size(outcome.get("failures"))
                    + "; warnings=" + size(outcome.get("warnings"))
                    + "; elapsed=" + value(outcome.get("verificationElapsed"), "unavailable");
            default -> throw new IllegalArgumentException("Unknown JAIPilot MCP tool: " + name);
        };
    }

    private String snapshotEvidence(Map<String, Object> outcome) {
        if (Boolean.FALSE.equals(outcome.get("applicable"))) {
            return "reason=" + value(outcome.get("reason"), "not_java_project");
        }
        Map<String, Object> quality = object(outcome.get("quality"));
        Map<String, Object> metrics = object(quality.get("metrics"));
        return "analysis=" + value(outcome.get("analysisStatus"), "unknown")
                + "; quality score=" + value(metrics.get("qualityScore"), "unavailable")
                + "; findings=" + value(quality.get("totalFindings"), "unavailable")
                + "; diff gate=" + value(quality.get("gateStatus"), "unavailable");
    }

    private String qualityEvidence(Map<String, Object> outcome) {
        Map<String, Object> quality = object(outcome.get("quality"));
        Map<String, Object> metrics = object(quality.get("metrics"));
        return "targets=" + size(outcome.get("targets"))
                + "; quality score=" + value(metrics.get("qualityScore"), "unavailable")
                + "; findings=" + value(metrics.get("findingCount"), "unavailable")
                + "; critical/high=" + criticalHigh(metrics)
                + "; parse failures=" + size(quality.get("parseFailures"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private int size(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
    }

    private String value(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private String configured(Object value) {
        return Boolean.TRUE.equals(value) ? "configured" : "not configured";
    }

    private String criticalHigh(Map<String, Object> metrics) {
        Map<String, Object> severities = object(metrics.get("findingsBySeverity"));
        return value(severities.get("CRITICAL"), "0") + "/" + value(severities.get("HIGH"), "0");
    }

    private String abbreviated(Object value) {
        String fingerprint = value(value, "unavailable");
        return fingerprint.length() <= 12 ? fingerprint : fingerprint.substring(0, 12);
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

    private record RunSummary(String operation, String status, String benefit, String evidence) {

        String text() {
            return "JAIPilot finished: " + operation + " (" + status + ")\n"
                    + "Why this mattered: " + benefit + "\n"
                    + "Evidence: " + evidence;
        }

        Map<String, Object> metadata() {
            return map(
                    "operation", operation,
                    "status", status,
                    "benefit", benefit,
                    "evidence", evidence
            );
        }
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
