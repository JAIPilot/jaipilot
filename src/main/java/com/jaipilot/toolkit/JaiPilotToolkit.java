package com.jaipilot.toolkit;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.jaipilot.toolkit.core.WorkflowRunService;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Agent-facing command entry point for the JAIPilot Java Enterprise Toolkit Harness. */
public final class JaiPilotToolkit {

    private static final String USAGE = """
            JAIPilot Java Enterprise Toolkit Harness

            Usage:
              jaipilot inspect [--project <path>]
              jaipilot quality [selection]
              jaipilot prepare-tests [selection] [quality gates]
              jaipilot prepare-cleanup [selection]
              jaipilot status --run <uuid>
              jaipilot validate --run <uuid>
              jaipilot apply --run <uuid> --confirm
              jaipilot discard --run <uuid>
              jaipilot version

            Selection:
              --project <path>                 Project directory; defaults to the current directory
              --mode <classes|changed|all>     Cleanup or test target mode
              --mode coverage                  Test classes below fresh JaCoCo line coverage
              --class <selector>               Repeat for classes mode
              --coverage-threshold <0-100>     Coverage-selection threshold; defaults to 80

            Test quality gates:
              --minimum-line-coverage <0-100>  Required fresh line coverage; defaults to 80
              --minimum-mutation-score <0-100> Required targeted PIT score; defaults to 70

            Commands emit structured JSON. JAIPilot never invokes a model or uploads source.
            """;

    private JaiPilotToolkit() {
    }

    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(String[] args, PrintStream stdout, PrintStream stderr) {
        ObjectMapper mapper = mapper();
        try {
            Object result = execute(args, mapper);
            if (result instanceof Usage usage) {
                stdout.print(usage.text());
            } else {
                mapper.writeValue(stdout, new Success(result));
                stdout.println();
            }
            return 0;
        } catch (IllegalArgumentException exception) {
            writeError(mapper, stdout, "invalid_request", exception.getMessage());
            stderr.println("jaipilot: " + exception.getMessage());
            return 2;
        } catch (RuntimeException exception) {
            writeError(mapper, stdout, "workflow_failed", rootMessage(exception));
            stderr.println("jaipilot: " + rootMessage(exception));
            return 1;
        } catch (IOException exception) {
            stderr.println("jaipilot: failed to write command output: " + exception.getMessage());
            return 1;
        }
    }

    private static Object execute(String[] args, ObjectMapper mapper) {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0]) || "help".equals(args[0])) {
            return new Usage(USAGE);
        }
        ParsedArguments parsed = ParsedArguments.parse(args);
        if ("version".equals(parsed.command())) {
            parsed.rejectOptions();
            return Map.of("version", implementationVersion());
        }

        ToolkitRunStore store = new ToolkitRunStore(mapper, ToolkitRunStore.defaultRoot());
        return switch (parsed.command()) {
            case "inspect" -> {
                parsed.allow("project");
                yield store.inspect(parsed.project());
            }
            case "quality" -> {
                parsed.allow("project", "mode", "class", "coverage-threshold");
                yield store.quality(parsed.project(), parsed.selection(false));
            }
            case "prepare-tests" -> {
                parsed.allow(
                        "project",
                        "mode",
                        "class",
                        "coverage-threshold",
                        "minimum-line-coverage",
                        "minimum-mutation-score"
                );
                yield store.prepareTests(
                        parsed.project(),
                        parsed.selection(true),
                        parsed.percentage("minimum-line-coverage", 80.0d),
                        parsed.percentage("minimum-mutation-score", 70.0d)
                );
            }
            case "prepare-cleanup" -> {
                parsed.allow("project", "mode", "class", "coverage-threshold");
                yield store.prepareCleanup(parsed.project(), parsed.selection(false));
            }
            case "status" -> {
                parsed.allow("run");
                yield store.status(parsed.requiredSingle("run"));
            }
            case "validate" -> {
                parsed.allow("run");
                yield store.validate(parsed.requiredSingle("run"));
            }
            case "apply" -> {
                parsed.allow("run", "confirm");
                if (!parsed.flag("confirm")) {
                    throw new IllegalArgumentException("apply requires --confirm after reviewing the validated candidate.");
                }
                yield store.apply(parsed.requiredSingle("run"));
            }
            case "discard" -> {
                parsed.allow("run");
                String runId = parsed.requiredSingle("run");
                store.discard(runId);
                yield Map.of("runId", runId, "discarded", true);
            }
            default -> throw new IllegalArgumentException("Unknown command: " + parsed.command());
        };
    }

    private static ObjectMapper mapper() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Path.class, stringSerializer(Path::toString));
        module.addSerializer(Duration.class, stringSerializer(Duration::toString));
        module.addSerializer(Instant.class, stringSerializer(Instant::toString));
        return new ObjectMapper()
                .registerModule(module)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    private static <T> JsonSerializer<T> stringSerializer(java.util.function.Function<T, String> formatter) {
        return new JsonSerializer<>() {
            @Override
            public void serialize(T value, JsonGenerator generator, SerializerProvider serializers)
                    throws IOException {
                generator.writeString(formatter.apply(value));
            }
        };
    }

    private static void writeError(ObjectMapper mapper, PrintStream stdout, String code, String message) {
        try {
            mapper.writeValue(stdout, new Failure(new ErrorDetail(code, message)));
            stdout.println();
        } catch (IOException ignored) {
            // stderr receives the same concise failure immediately afterward.
        }
    }

    private static String implementationVersion() {
        String version = JaiPilotToolkit.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "development" : version;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private record Success(boolean ok, Object result) {
        private Success(Object result) {
            this(true, result);
        }
    }

    private record Failure(boolean ok, ErrorDetail error) {
        private Failure(ErrorDetail error) {
            this(false, error);
        }
    }

    private record ErrorDetail(String code, String message) {
    }

    private record Usage(String text) {
    }

    private static final class ParsedArguments {

        private static final SetLike FLAGS = new SetLike(List.of("confirm"));

        private final String command;
        private final Map<String, List<String>> options;

        private ParsedArguments(String command, Map<String, List<String>> options) {
            this.command = command;
            this.options = options;
        }

        static ParsedArguments parse(String[] args) {
            String command = args[0];
            Map<String, List<String>> options = new LinkedHashMap<>();
            for (int index = 1; index < args.length; index++) {
                String token = args[index];
                if (!token.startsWith("--") || token.length() == 2) {
                    throw new IllegalArgumentException("Expected an option, got: " + token);
                }
                String name = token.substring(2);
                if (FLAGS.contains(name)) {
                    options.computeIfAbsent(name, ignored -> new ArrayList<>()).add("true");
                    continue;
                }
                if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
                    throw new IllegalArgumentException("Missing value for --" + name + ".");
                }
                options.computeIfAbsent(name, ignored -> new ArrayList<>()).add(args[++index]);
            }
            return new ParsedArguments(command, options);
        }

        String command() {
            return command;
        }

        void allow(String... names) {
            SetLike allowed = new SetLike(List.of(names));
            options.keySet().stream()
                    .filter(name -> !allowed.contains(name))
                    .findFirst()
                    .ifPresent(name -> {
                        throw new IllegalArgumentException("Unsupported option for " + command + ": --" + name);
                    });
        }

        void rejectOptions() {
            if (!options.isEmpty()) {
                throw new IllegalArgumentException(command + " does not accept options.");
            }
        }

        Path project() {
            return Path.of(single("project", "."));
        }

        WorkflowRunService.TargetSelection selection(boolean coverageAllowed) {
            String mode = single("mode", options.containsKey("class") ? "classes" : "changed")
                    .toUpperCase(Locale.ROOT);
            WorkflowRunService.TargetMode targetMode;
            try {
                targetMode = WorkflowRunService.TargetMode.valueOf(mode);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unsupported target mode: " + mode.toLowerCase(Locale.ROOT));
            }
            if (!coverageAllowed && targetMode == WorkflowRunService.TargetMode.COVERAGE) {
                throw new IllegalArgumentException("Coverage target mode is only available for test generation.");
            }
            List<String> classes = List.copyOf(options.getOrDefault("class", List.of()));
            return new WorkflowRunService.TargetSelection(
                    targetMode,
                    classes,
                    percentage("coverage-threshold", 80.0d)
            );
        }

        boolean flag(String name) {
            return options.containsKey(name);
        }

        String requiredSingle(String name) {
            String value = single(name, null);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("--" + name + " is required.");
            }
            return value;
        }

        String single(String name, String defaultValue) {
            List<String> values = options.get(name);
            if (values == null) {
                return defaultValue;
            }
            if (values.size() != 1) {
                throw new IllegalArgumentException("--" + name + " may be supplied only once.");
            }
            return values.get(0);
        }

        double percentage(String name, double defaultValue) {
            String raw = single(name, Double.toString(defaultValue));
            try {
                double value = Double.parseDouble(raw);
                if (!Double.isFinite(value) || value < 0.0d || value > 100.0d) {
                    throw new NumberFormatException();
                }
                return value;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("--" + name + " must be between 0 and 100.");
            }
        }
    }

    private record SetLike(List<String> values) {
        private boolean contains(String value) {
            return values.contains(value);
        }
    }
}
