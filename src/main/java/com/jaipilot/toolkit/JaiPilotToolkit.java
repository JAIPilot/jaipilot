package com.jaipilot.toolkit;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.jaipilot.toolkit.core.DiffVerificationService;
import com.jaipilot.toolkit.core.GitChangeService;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Private structured runner used by the JAIPilot plugin, Agent Skills, and MCP adapter. */
public final class JaiPilotToolkit {

    private static final String USAGE = """
            JAIPilot deterministic Java evidence runner

            Commands:
              inspect       Discover the repository and available deterministic engines
              quality       Analyze classes, changed production Java, or all production Java
              snapshot      Refresh the latest whole-repository dashboard state
              rewrite       Run pinned OpenRewrite recipes in the agent-managed worktree
              diff-gate     Check whether the exact current diff has a valid proof receipt
              prove-diff    Run the clean build, coverage, PIT, quality, and ArchUnit proof
              dashboard     Return the private local dashboard URL
              version       Return the installed JAIPilot version

            Scope:
              --project <path>                  Defaults to the current directory
              --mode <classes|changed|all|coverage>
              --class <selector>                Repeat for classes mode
              --coverage-threshold <0-100>      Defaults to 80

            Proof gates:
              --minimum-line-coverage <0-100>   Defaults to 90
              --minimum-branch-coverage <0-100> Defaults to 85
              --minimum-mutation-score <0-100>  Defaults to 80
              --minimum-quality-score <0-100>   Defaults to 90

            This runner is non-interactive. It emits JSON and never invokes a model or uploads source.
            """;

    private JaiPilotToolkit() {
    }

    public static void main(String[] arguments) {
        if (arguments.length > 0 && "mcp".equals(arguments[0])) {
            JaiPilotMcpServer.main(java.util.Arrays.copyOfRange(arguments, 1, arguments.length));
            return;
        }
        ObjectMapper mapper = mapper();
        Path stateRoot = ToolkitRunStore.defaultRoot();
        if (arguments.length > 0 && "dashboard-serve".equals(arguments[0])) {
            System.exit(DashboardServer.serve(mapper, stateRoot, System.err));
        }
        int status = run(arguments, System.in, System.out, System.err, stateRoot, mapper);
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(String[] arguments, PrintStream stdout, PrintStream stderr) {
        return run(arguments, InputStream.nullInputStream(), stdout, stderr);
    }

    static int run(String[] arguments, InputStream stdin, PrintStream stdout, PrintStream stderr) {
        return run(arguments, stdin, stdout, stderr, ToolkitRunStore.defaultRoot());
    }

    static int run(
            String[] arguments,
            InputStream stdin,
            PrintStream stdout,
            PrintStream stderr,
            Path stateRoot
    ) {
        return run(arguments, stdin, stdout, stderr, stateRoot, mapper());
    }

    static int run(
            String[] arguments,
            InputStream stdin,
            PrintStream stdout,
            PrintStream stderr,
            Path stateRoot,
            ObjectMapper mapper
    ) {
        try {
            Object result = execute(arguments, stdin, stderr, stateRoot, mapper);
            if (result == NoOutput.INSTANCE) {
                return 0;
            }
            if (result instanceof Usage usage) {
                stdout.print(usage.text());
                return 0;
            }
            if (result instanceof FailedProof failed) {
                writeJson(mapper, stdout, failed);
                return 1;
            }
            if (result instanceof HookResponse response) {
                writeJson(mapper, stdout, response);
                return 0;
            }
            writeJson(mapper, stdout, new Success(true, result));
            return 0;
        } catch (IllegalArgumentException exception) {
            writeFailure(mapper, stdout, "invalid_request", exception.getMessage());
            stderr.println("jaipilot: " + exception.getMessage());
            return 2;
        } catch (RuntimeException exception) {
            String message = rootMessage(exception);
            writeFailure(mapper, stdout, "deterministic_check_failed", message);
            stderr.println("jaipilot: " + message);
            return 1;
        } catch (IOException exception) {
            stderr.println("jaipilot: failed to write JSON output: " + exception.getMessage());
            return 1;
        }
    }

    private static Object execute(
            String[] arguments,
            InputStream stdin,
            PrintStream stderr,
            Path stateRoot,
            ObjectMapper mapper
    ) {
        if (arguments.length == 0 || "help".equals(arguments[0])
                || "--help".equals(arguments[0]) || "-h".equals(arguments[0])) {
            return new Usage(USAGE);
        }
        ParsedArguments parsed = ParsedArguments.parse(arguments);
        if ("version".equals(parsed.command())) {
            parsed.allow();
            return Map.of("version", implementationVersion());
        }
        if ("dashboard".equals(parsed.command())) {
            parsed.allow();
            DashboardServer.ensureRunning(mapper, stateRoot, stderr, true);
            return DashboardServer.currentStatus(mapper, stateRoot);
        }
        ToolkitRunStore store = new ToolkitRunStore(mapper, stateRoot, message -> stderr.println("jaipilot: " + message));
        RepositorySnapshotStore snapshots = new RepositorySnapshotStore(mapper, stateRoot);
        if ("hook-stop".equals(parsed.command())) {
            parsed.allow("project");
            return stopHook(mapper, stdin, parsed.project(), store);
        }
        return executeEvidenceCommand(parsed, store, snapshots);
    }

    private static Object executeEvidenceCommand(
            ParsedArguments parsed,
            ToolkitRunStore store,
            RepositorySnapshotStore snapshots
    ) {
        return switch (parsed.command()) {
            case "inspect" -> {
                parsed.allow("project");
                snapshots.register(parsed.project());
                yield store.inspect(parsed.project());
            }
            case "snapshot" -> {
                parsed.allow("project");
                yield refreshSnapshot(parsed.project(), store, snapshots);
            }
            case "quality" -> {
                parsed.allow("project", "mode", "class", "coverage-threshold");
                snapshots.register(parsed.project());
                ToolkitRunStore.QualityInspection quality = store.quality(parsed.project(), parsed.selection());
                if (parsed.selection().mode() == ToolkitRunStore.TargetMode.ALL) {
                    snapshots.recordAnalysis(
                            parsed.project(),
                            quality,
                            safeGate(parsed.project(), store)
                    );
                }
                yield quality;
            }
            case "rewrite" -> {
                parsed.allow("project", "mode", "class", "coverage-threshold");
                snapshots.register(parsed.project());
                yield store.rewrite(parsed.project(), parsed.selection());
            }
            case "diff-gate" -> {
                parsed.allowProofOptions();
                snapshots.register(parsed.project());
                yield store.diffGate(parsed.project(), parsed.thresholds());
            }
            case "prove-diff" -> {
                parsed.allowProofOptions();
                snapshots.register(parsed.project());
                DiffVerificationService.DiffVerification proof = store.proveDiff(
                        parsed.project(),
                        parsed.thresholds()
                );
                snapshots.recordProof(
                        parsed.project(),
                        proof,
                        store.diffGate(parsed.project(), parsed.thresholds())
                );
                yield proof.passed() ? proof : new FailedProof(false, proof);
            }
            default -> throw new IllegalArgumentException("Unknown command: " + parsed.command());
        };
    }

    private static Object refreshSnapshot(
            Path project,
            ToolkitRunStore store,
            RepositorySnapshotStore snapshots
    ) {
        if (!store.hasProductionJava(project)) {
            return new SnapshotSkipped(false, "not_java_project");
        }
        snapshots.register(project);
        try {
            ToolkitRunStore.QualityInspection quality = store.currentQuality(project).orElseThrow();
            ToolkitRunStore.DiffGateStatus gate = safeGate(project, store);
            return snapshots.recordAnalysis(project, quality, gate);
        } catch (RuntimeException exception) {
            snapshots.recordFailure(project, rootMessage(exception));
            throw exception;
        }
    }

    private static ToolkitRunStore.DiffGateStatus safeGate(Path project, ToolkitRunStore store) {
        try {
            return store.diffGate(project, DiffVerificationService.DEFAULT_THRESHOLDS);
        } catch (GitChangeService.NotGitWorktreeException exception) {
            return null;
        }
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
            return "review_required".equals(gate.status())
                    ? new HookResponse("block", hookReason(gate))
                    : NoOutput.INSTANCE;
        } catch (GitChangeService.NotGitWorktreeException exception) {
            return NoOutput.INSTANCE;
        } catch (RuntimeException exception) {
            return new HookResponse(
                    "block",
                    "JAIPilot could not inspect the current Java diff: " + rootMessage(exception)
                            + ". Resolve this deterministic check before finishing."
            );
        }
    }

    private static boolean stopHookActive(ObjectMapper mapper, InputStream stdin) {
        try {
            JsonNode input = mapper.readTree(stdin);
            return input != null && input.path("stop_hook_active").asBoolean(false);
        } catch (IOException exception) {
            return false;
        }
    }

    private static String hookReason(ToolkitRunStore.DiffGateStatus gate) {
        return "JAIPilot found " + gate.proofRelevantPaths().size()
                + " changed Java/build input(s) without proof for fingerprint "
                + gate.fingerprint().substring(0, 12)
                + ". Use the jaipilot-review-diff skill, run the private `prove-diff` command, "
                + "and continue only after the clean build, coverage, PIT, quality, and ArchUnit gates pass.";
    }

    static ObjectMapper mapper() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Path.class, stringSerializer(Path::toString));
        module.addSerializer(Duration.class, stringSerializer(Duration::toString));
        module.addSerializer(Instant.class, stringSerializer(Instant::toString));
        return new ObjectMapper().registerModule(module).enable(SerializationFeature.INDENT_OUTPUT);
    }

    private static <T> JsonSerializer<T> stringSerializer(Function<T, String> formatter) {
        return new JsonSerializer<>() {
            @Override
            public void serialize(T value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
                generator.writeString(formatter.apply(value));
            }
        };
    }

    private static void writeJson(ObjectMapper mapper, PrintStream stdout, Object value) throws IOException {
        mapper.writeValue(stdout, value);
        stdout.println();
    }

    private static void writeFailure(ObjectMapper mapper, PrintStream stdout, String code, String message) {
        try {
            writeJson(mapper, stdout, new Failure(false, new ErrorDetail(code, message)));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize structured error output.", exception);
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
    }

    private record Failure(boolean ok, ErrorDetail error) {
    }

    private record ErrorDetail(String code, String message) {
    }

    private record HookResponse(String decision, String reason) {
    }

    private record SnapshotSkipped(boolean applicable, String reason) {
    }

    private record FailedProof(boolean ok, DiffVerificationService.DiffVerification result) {
    }

    private record Usage(String text) {
    }

    private enum NoOutput {
        INSTANCE
    }

    private static final class ParsedArguments {

        private final String command;
        private final Map<String, List<String>> options;
        private final Set<String> flags;

        private ParsedArguments(String command, Map<String, List<String>> options, Set<String> flags) {
            this.command = command;
            this.options = options;
            this.flags = flags;
        }

        static ParsedArguments parse(String[] arguments) {
            String command = arguments[0];
            Map<String, List<String>> options = new HashMap<>();
            Set<String> flags = new HashSet<>();
            for (int index = 1; index < arguments.length; index++) {
                String token = arguments[index];
                if (!token.startsWith("--") || token.length() == 2) {
                    throw new IllegalArgumentException("Unexpected argument: " + token);
                }
                String name = token.substring(2);
                if ("confirm".equals(name)) {
                    flags.add(name);
                    continue;
                }
                if (++index >= arguments.length) {
                    throw new IllegalArgumentException("Missing value for --" + name + ".");
                }
                options.computeIfAbsent(name, ignored -> new ArrayList<>()).add(arguments[index]);
            }
            return new ParsedArguments(command, options, flags);
        }

        String command() {
            return command;
        }

        Path project() {
            return Path.of(single("project", "."));
        }

        ToolkitRunStore.TargetSelection selection() {
            String modeName = single("mode", "changed").toUpperCase(Locale.ROOT);
            ToolkitRunStore.TargetMode mode;
            try {
                mode = ToolkitRunStore.TargetMode.valueOf(modeName);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Mode must be classes, changed, all, or coverage.");
            }
            return new ToolkitRunStore.TargetSelection(
                    mode,
                    options.getOrDefault("class", List.of()),
                    percentage("coverage-threshold", 80.0d)
            );
        }

        DiffVerificationService.VerificationThresholds thresholds() {
            return new DiffVerificationService.VerificationThresholds(
                    percentage("minimum-line-coverage", 90.0d),
                    percentage("minimum-branch-coverage", 85.0d),
                    percentage("minimum-mutation-score", 80.0d),
                    percentage("minimum-quality-score", 90.0d)
            );
        }

        void allowProofOptions() {
            allow(
                    "project",
                    "minimum-line-coverage",
                    "minimum-branch-coverage",
                    "minimum-mutation-score",
                    "minimum-quality-score"
            );
        }

        void allow(String... names) {
            Set<String> allowed = Set.of(names);
            for (String option : options.keySet()) {
                if (!allowed.contains(option)) {
                    throw new IllegalArgumentException("--" + option + " is not valid for " + command + ".");
                }
            }
            if (!flags.isEmpty()) {
                throw new IllegalArgumentException("Unexpected flag for " + command + ": --" + flags.iterator().next());
            }
        }

        private String single(String name, String defaultValue) {
            List<String> values = options.get(name);
            if (values == null) {
                return defaultValue;
            }
            if (values.size() != 1) {
                throw new IllegalArgumentException("--" + name + " may be specified once.");
            }
            return values.get(0);
        }

        private double percentage(String name, double defaultValue) {
            String value = single(name, Double.toString(defaultValue));
            try {
                double parsed = Double.parseDouble(value);
                if (!Double.isFinite(parsed) || parsed < 0.0d || parsed > 100.0d) {
                    throw new IllegalArgumentException();
                }
                return parsed;
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("--" + name + " must be between 0 and 100.");
            }
        }
    }
}
