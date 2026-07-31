package com.jaipilot.cli.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaipilot.cli.ui.TerminalUi;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CodexCliRunner {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ProcessExecutor processExecutor;

    public CodexCliRunner() {
        this(new ProcessExecutor());
    }

    CodexCliRunner(ProcessExecutor processExecutor) {
        this.processExecutor = processExecutor;
    }

    public Optional<String> version(Path workingDirectory) {
        try {
            ProcessExecutor.ExecutionResult result = processExecutor.execute(
                    List.of("codex", "--version"),
                    workingDirectory,
                    Duration.ofSeconds(15),
                    false,
                    new PrintWriter(System.err, true)
            );
            if (result.exitCode() == 0) {
                return Optional.of(result.output().trim());
            }
        } catch (Exception ignored) {
            // Absence is reported by the caller.
        }
        return Optional.empty();
    }

    public void ensureAvailable(Path workingDirectory) {
        if (version(workingDirectory).isEmpty()) {
            throw new IllegalStateException("Codex CLI is not installed or not available on PATH.");
        }
    }

    public RunResult run(
            Path workingDirectory,
            String model,
            String prompt,
            TerminalUi ui,
            boolean showLogs,
            boolean showProgress,
            PrintWriter logWriter,
            Duration timeout,
            String progressLabel,
            String failurePrefix
    ) throws Exception {
        List<String> command = buildCommand(model);
        command.add("exec");
        command.add("--json");
        command.add("--skip-git-repo-check");
        command.add("--ephemeral");
        command.add("-");

        printLiveLogHeader(ui, logWriter, showLogs, command);
        CodexJsonLogRenderer logRenderer = showLogs ? new CodexJsonLogRenderer(ui, logWriter) : null;
        ProcessExecutor.ExecutionResult result = processExecutor.execute(
                command,
                workingDirectory,
                timeout,
                false,
                logWriter,
                prompt,
                progressListener(ui, progressLabel, showLogs, showProgress),
                logRenderer,
                CoverageRefreshService.buildToolCacheEnvironment(workingDirectory, System.getenv())
        );
        if (result.timedOut()) {
            throw new IllegalStateException(failurePrefix + System.lineSeparator() + "Timed out.");
        }
        if (result.exitCode() != 0) {
            throw new IllegalStateException(
                    failurePrefix + System.lineSeparator() + summarizeFailure(result.output(), ui)
            );
        }
        return new RunResult(parseUsage(result.output()), result.elapsed());
    }

    List<String> buildCommand(String model) {
        List<String> command = new ArrayList<>();
        command.add("codex");
        command.add("-a");
        command.add("never");
        command.add("-c");
        command.add("hide_agent_reasoning=true");
        command.add("-c");
        command.add("features.multi_agent=false");
        command.add("-s");
        command.add("workspace-write");
        if (model != null && !model.isBlank()) {
            command.add("-m");
            command.add(model.trim());
        }
        return command;
    }

    String summarizeFailure(String output, TerminalUi ui) {
        CodexJsonLogRenderer renderer = new CodexJsonLogRenderer(
                ui,
                new PrintWriter(Writer.nullWriter())
        );
        List<String> rendered = (output == null ? List.<String>of() : output.lines().toList()).stream()
                .map(renderer::render)
                .filter(value -> value != null && !value.isBlank())
                .toList();
        List<String> important = rendered.stream()
                .filter(value -> {
                    String lowerCase = value.toLowerCase(java.util.Locale.ROOT);
                    return lowerCase.contains("error")
                            || lowerCase.contains("failed")
                            || lowerCase.contains("warning");
                })
                .toList();
        List<String> selected = important.isEmpty() ? rendered : important;
        if (selected.isEmpty()) {
            return "Codex exited without a readable error. Re-run with --show-logs for details.";
        }
        int start = Math.max(0, selected.size() - 8);
        return String.join(System.lineSeparator(), selected.subList(start, selected.size()));
    }

    Usage parseUsage(String output) {
        Usage usage = Usage.zero();
        for (String line : output.lines().toList()) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("{")) {
                continue;
            }
            try {
                JsonNode node = OBJECT_MAPPER.readTree(trimmed);
                if (!"turn.completed".equals(node.path("type").asText())) {
                    continue;
                }
                JsonNode usageNode = node.path("usage");
                usage = new Usage(
                        usageNode.path("input_tokens").asLong(0L),
                        usageNode.path("cached_input_tokens").asLong(0L),
                        usageNode.path("output_tokens").asLong(0L),
                        usageNode.path("reasoning_output_tokens").asLong(0L)
                );
            } catch (Exception ignored) {
                // Mixed Codex output can contain non-JSON diagnostics.
            }
        }
        return usage;
    }

    private ProcessExecutor.ProgressListener progressListener(
            TerminalUi ui,
            String label,
            boolean showLogs,
            boolean showProgress
    ) {
        if (showLogs || !showProgress) {
            return ProcessExecutor.ProgressListener.noOp();
        }
        return ui.spinner(label);
    }

    private void printLiveLogHeader(
            TerminalUi ui,
            PrintWriter logWriter,
            boolean showLogs,
            List<String> command
    ) {
        if (!showLogs) {
            return;
        }
        logWriter.printf("%s %s%n", ui.badge(TerminalUi.Tone.PRIMARY, "agent"), formatCommand(command));
        logWriter.flush();
    }

    private String formatCommand(List<String> command) {
        return command.stream()
                .map(this::quoteIfNeeded)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private String quoteIfNeeded(String value) {
        if (value.indexOf(' ') < 0 && value.indexOf('\t') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    public record RunResult(
            Usage usage,
            Duration elapsed
    ) {
    }

    public record Usage(
            long inputTokens,
            long cachedInputTokens,
            long outputTokens,
            long reasoningOutputTokens
    ) {
        public static Usage zero() {
            return new Usage(0L, 0L, 0L, 0L);
        }

        public long totalTokens() {
            return inputTokens + outputTokens;
        }
    }
}
