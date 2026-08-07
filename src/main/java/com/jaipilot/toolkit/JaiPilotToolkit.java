package com.jaipilot.toolkit;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.jaipilot.toolkit.core.DiffVerificationService;
import com.jaipilot.toolkit.core.GitChangeService;
import com.jaipilot.toolkit.core.WorkflowRunService;
import java.io.IOException;
import java.io.InputStream;
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
              jaipilot diff-gate [diff quality gates]
              jaipilot prove-diff [diff quality gates]
              jaipilot quality [selection]
              jaipilot prepare-tests [selection] [quality gates]
              jaipilot prepare-cleanup [selection]
              jaipilot status --run <uuid>
              jaipilot validate --run <uuid>
              jaipilot apply --run <uuid> --confirm
              jaipilot discard --run <uuid>
              jaipilot dashboard
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

            Automatic diff quality gates:
              --minimum-line-coverage <0-100>   Changed-code line coverage; defaults to 90
              --minimum-branch-coverage <0-100> Changed-code branch coverage; defaults to 85
              --minimum-mutation-score <0-100>  Targeted PIT mutation score; defaults to 80
              --minimum-quality-score <0-100>   Changed-code quality score; defaults to 90

            Commands emit structured JSON. JAIPilot never invokes a model or uploads source.
            """;

    private JaiPilotToolkit() {
    }

    public static void main(String[] args) {
        ObjectMapper mapper = mapper();
        Path stateRoot = ToolkitRunStore.defaultRoot();
        if (args.length > 0 && "dashboard-serve".equals(args[0])) {
            int dashboardStatus = DashboardServer.serve(mapper, stateRoot, System.err);
            if (dashboardStatus != 0) {
                System.exit(dashboardStatus);
            }
            return;
        }
        boolean dashboardRequest = args.length > 0 && "dashboard".equals(args[0]);
        DashboardServer.ensureRunning(mapper, stateRoot, System.err, dashboardRequest);
        int status = run(args, System.in, System.out, System.err, stateRoot, mapper);
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(String[] args, PrintStream stdout, PrintStream stderr) {
        return run(args, InputStream.nullInputStream(), stdout, stderr);
    }

    static int run(String[] args, InputStream stdin, PrintStream stdout, PrintStream stderr) {
        return run(args, stdin, stdout, stderr, ToolkitRunStore.defaultRoot());
    }

    static int run(
            String[] args,
            InputStream stdin,
            PrintStream stdout,
            PrintStream stderr,
            Path stateRoot
    ) {
        return run(args, stdin, stdout, stderr, stateRoot, mapper());
    }

    private static int run(
            String[] args,
            InputStream stdin,
            PrintStream stdout,
            PrintStream stderr,
            Path stateRoot,
            ObjectMapper mapper
    ) {
        Instant startedAt = Instant.now();
        String command = metricCommand(args);
        Object result = null;
        int status = 1;
        try {
            result = execute(args, mapper, stdin, stderr, stateRoot);
            status = writeResult(mapper, stdout, result);
            return status;
        } catch (IllegalArgumentException exception) {
            writeError(mapper, stdout, "invalid_request", exception.getMessage());
            stderr.println("jaipilot: " + exception.getMessage());
            status = 2;
            return status;
        } catch (RuntimeException exception) {
            writeError(mapper, stdout, "workflow_failed", rootMessage(exception));
            stderr.println("jaipilot: " + rootMessage(exception));
            status = 1;
            return status;
        } catch (IOException exception) {
            stderr.println("jaipilot: failed to write command output: " + exception.getMessage());
            status = 1;
            return status;
        } finally {
            recordMetrics(
                    mapper,
                    stateRoot,
                    stderr,
                    command,
                    status,
                    result,
                    Duration.between(startedAt, Instant.now())
            );
        }
    }

    private static void recordMetrics(
            ObjectMapper mapper,
            Path stateRoot,
            PrintStream stderr,
            String command,
            int status,
            Object result,
            Duration elapsed
    ) {
        try {
            new UsageMetricsStore(mapper, stateRoot).record(command, status, result, elapsed);
        } catch (RuntimeException exception) {
            stderr.println("jaipilot: usage metrics could not be recorded: " + rootMessage(exception));
        }
    }

    private static String metricCommand(String[] args) {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            return "help";
        }
        return switch (args[0]) {
            case "help", "version", "dashboard", "inspect", "diff-gate", "prove-diff", "hook-stop",
                    "quality", "prepare-tests", "prepare-cleanup", "status", "validate", "apply", "discard" ->
                    args[0];
            default -> "unknown";
        };
    }

    private static int writeResult(ObjectMapper mapper, PrintStream stdout, Object result) throws IOException {
        if (result instanceof Usage usage) {
            stdout.print(usage.text());
            return 0;
        }
        if (result == NoOutput.INSTANCE) {
            return 0;
        }
        if (result instanceof HookResponse response) {
            writeJsonLine(mapper, stdout, response);
            return 0;
        }
        if (result instanceof FailedProof failed) {
            writeJsonLine(mapper, stdout, failed);
            return 1;
        }
        writeJsonLine(mapper, stdout, new Success(result));
        return 0;
    }

    private static void writeJsonLine(ObjectMapper mapper, PrintStream stdout, Object result) throws IOException {
        mapper.writeValue(stdout, result);
        stdout.println();
    }

    private static Object execute(
            String[] args,
            ObjectMapper mapper,
            InputStream stdin,
            PrintStream stderr,
            Path stateRoot
    ) {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0]) || "help".equals(args[0])) {
            return new Usage(USAGE);
        }
        ParsedArguments parsed = ParsedArguments.parse(args);
        if ("version".equals(parsed.command())) {
            parsed.rejectOptions();
            return Map.of("version", implementationVersion());
        }
        if ("dashboard".equals(parsed.command())) {
            parsed.rejectOptions();
            return DashboardServer.currentStatus(mapper, stateRoot);
        }

        ToolkitRunStore store = new ToolkitRunStore(
                mapper,
                stateRoot,
                message -> stderr.println("jaipilot: " + message)
        );
        return switch (parsed.command()) {
            case "inspect", "diff-gate", "prove-diff", "hook-stop" -> executeReviewCommand(
                    parsed,
                    mapper,
                    stdin,
                    store
            );
            case "quality", "prepare-tests", "prepare-cleanup" -> executePrepareCommand(parsed, store);
            case "status", "validate", "apply", "discard" -> executeRunCommand(parsed, store);
            default -> throw new IllegalArgumentException("Unknown command: " + parsed.command());
        };
    }

    private static Object executeReviewCommand(
            ParsedArguments parsed,
            ObjectMapper mapper,
            InputStream stdin,
            ToolkitRunStore store
    ) {
        return switch (parsed.command()) {
            case "inspect" -> {
                parsed.allow("project");
                yield store.inspect(parsed.project());
            }
            case "diff-gate" -> {
                parsed.allow(
                        "project",
                        "minimum-line-coverage",
                        "minimum-branch-coverage",
                        "minimum-mutation-score",
                        "minimum-quality-score"
                );
                yield store.diffGate(parsed.project(), parsed.diffThresholds());
            }
            case "prove-diff" -> {
                parsed.allow(
                        "project",
                        "minimum-line-coverage",
                        "minimum-branch-coverage",
                        "minimum-mutation-score",
                        "minimum-quality-score"
                );
                DiffVerificationService.DiffVerification verification = store.proveDiff(
                        parsed.project(),
                        parsed.diffThresholds()
                );
                if (!verification.passed()) {
                    yield new FailedProof(false, verification);
                }
                yield verification;
            }
            case "hook-stop" -> {
                parsed.allow("project");
                yield stopHook(mapper, stdin, parsed.project(), store);
            }
            default -> throw new IllegalArgumentException("Unknown review command: " + parsed.command());
        };
    }

    private static Object executePrepareCommand(ParsedArguments parsed, ToolkitRunStore store) {
        return switch (parsed.command()) {
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
            default -> throw new IllegalArgumentException("Unknown prepare command: " + parsed.command());
        };
    }

    private static Object executeRunCommand(ParsedArguments parsed, ToolkitRunStore store) {
        return switch (parsed.command()) {
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
            default -> throw new IllegalArgumentException("Unknown run command: " + parsed.command());
        };
    }

    private static Object stopHook(
            ObjectMapper mapper,
            InputStream stdin,
            Path project,
            ToolkitRunStore store
    ) {
        if (stopHookActive(mapper, stdin)) {
            return NoOutput.INSTANCE;
        }
        try {
            ToolkitRunStore.DiffGateStatus gate = store.diffGate(
                    project,
                    DiffVerificationService.DEFAULT_THRESHOLDS
            );
            if (!"review_required".equals(gate.status())) {
                return NoOutput.INSTANCE;
            }
            return new HookResponse("block", hookReason(gate));
        } catch (GitChangeService.NotGitWorktreeException exception) {
            return NoOutput.INSTANCE;
        } catch (RuntimeException exception) {
            return new HookResponse(
                    "block",
                    "JAIPilot could not safely inspect the current Java Git diff: " + rootMessage(exception)
                            + ". Run `jaipilot diff-gate --project " + shellSafeDisplay(project)
                            + "` and resolve the failure before finishing."
            );
        }
    }

    private static boolean stopHookActive(ObjectMapper mapper, InputStream stdin) {
        try {
            return mapper.readTree(stdin).path("stop_hook_active").asBoolean(false);
        } catch (IOException exception) {
            return false;
        }
    }

    private static String hookReason(ToolkitRunStore.DiffGateStatus gate) {
        return "JAIPilot detected " + gate.changedProductionPaths().size()
                + " changed Java production file(s) since " + gate.baselineDescription()
                + " without proof for this exact diff. Use the $jaipilot-review-diff skill now. "
                + "Improve only the changed scope, then run `jaipilot prove-diff --project "
                + shellSafeDisplay(gate.projectRoot())
                + "` and do not finish until its fresh build, >=90% changed-line coverage, >=85% "
                + "changed-branch coverage, >=80% changed-line PIT mutation score, >=90 new-code "
                + "quality score, and zero new critical/high findings pass. Report any genuinely "
                + "unscorable target explicitly; never invent evidence.";
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

    private record HookResponse(String decision, String reason) {
    }

    private record FailedProof(boolean ok, DiffVerificationService.DiffVerification result) {
    }

    private enum NoOutput {
        INSTANCE
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

        DiffVerificationService.VerificationThresholds diffThresholds() {
            DiffVerificationService.VerificationThresholds defaults = DiffVerificationService.DEFAULT_THRESHOLDS;
            return new DiffVerificationService.VerificationThresholds(
                    percentage("minimum-line-coverage", defaults.minimumLineCoverage()),
                    percentage("minimum-branch-coverage", defaults.minimumBranchCoverage()),
                    percentage("minimum-mutation-score", defaults.minimumMutationScore()),
                    percentage("minimum-quality-score", defaults.minimumQualityScore())
            );
        }
    }

    private static String shellSafeDisplay(Path path) {
        String value = path.toString();
        return value.indexOf(' ') >= 0 ? "\"" + value.replace("\"", "\\\"") + "\"" : value;
    }

    private record SetLike(List<String> values) {
        private boolean contains(String value) {
            return values.contains(value);
        }
    }
}
