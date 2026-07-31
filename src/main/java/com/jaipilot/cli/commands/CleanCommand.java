package com.jaipilot.cli.commands;

import com.jaipilot.cli.service.CodexCliJavaCleaner;
import com.jaipilot.cli.service.CoverageReportService;
import com.jaipilot.cli.service.JavaProjectService;
import com.jaipilot.cli.service.ProjectFileService;
import com.jaipilot.cli.ui.TerminalUi;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "clean",
        mixinStandardHelpOptions = true,
        description = "Improves Java code with Codex in an isolated workspace, verifies it, then merges safe changes."
)
public final class CleanCommand implements Callable<Integer> {

    static final int CHANGES_REQUIRED_EXIT_CODE = 1;

    @Parameters(
            index = "0",
            arity = "0..1",
            paramLabel = "<class>",
            description = "Class path, fully qualified class name, or unique Java class name. Defaults to changed classes."
    )
    private String selector;

    @Option(
            names = "--changed",
            description = "Improve uncommitted Java production classes. This is the default when no target is supplied."
    )
    private boolean changed;

    @Option(
            names = "--all",
            description = "Improve every Java production class in the project."
    )
    private boolean all;

    @Option(
            names = {"--check", "--dry-run"},
            description = "Verify a cleanup candidate without changing the project; exit 1 when improvements are available."
    )
    private boolean checkOnly;

    @Option(
            names = "--model",
            paramLabel = "<model>",
            description = "Optional Codex model override."
    )
    private String model;

    @Option(
            names = "--show-logs",
            description = "Stream live build and Codex logs."
    )
    private boolean showLogs;

    @Spec
    private CommandSpec spec;

    private final JavaProjectService projectService;
    private final CodexCliJavaCleaner cleaner;

    public CleanCommand() {
        this(new ProjectFileService(), new CoverageReportService());
    }

    CleanCommand(ProjectFileService fileService, CoverageReportService coverageReportService) {
        this.projectService = new JavaProjectService(fileService, coverageReportService);
        this.cleaner = new CodexCliJavaCleaner(fileService, projectService);
    }

    @Override
    public Integer call() throws Exception {
        validateTargetMode();
        PrintWriter out = spec.commandLine().getOut();
        TerminalUi ui = new TerminalUi(out);
        Path projectRoot = projectService.resolveProjectRoot(Path.of("").toAbsolutePath().normalize());
        List<JavaProjectService.JavaClassDescriptor> targets = resolveTargets(projectRoot);

        ui.printBanner("Verified Java cleanup with Codex");
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        metadata.put("project", projectRoot.toString());
        metadata.put("target mode", targetMode());
        metadata.put("classes", String.valueOf(targets.size()));
        metadata.put("mode", checkOnly ? "check only" : "apply verified changes");
        metadata.put("logs", showLogs ? "live" : "summary");
        ui.printKeyValues(metadata);

        if (targets.isEmpty()) {
            ui.success(all
                    ? "No Java production classes were found."
                    : "No changed Java production classes were found.");
            return CommandLine.ExitCode.OK;
        }

        ui.section("Targets");
        ui.printTable(
                List.of("Class", "Source"),
                targets.stream()
                        .map(target -> List.of(
                                target.fullyQualifiedName(),
                                normalize(projectRoot.relativize(target.cutPath()))
                        ))
                        .toList()
        );

        ui.section("Isolated Cleanup");
        CodexCliJavaCleaner.CleanupResult result = cleaner.clean(
                projectRoot,
                targets,
                model,
                checkOnly,
                ui,
                showLogs,
                out
        );

        ui.section("Result");
        if (result.changedRelativePaths().isEmpty()) {
            ui.success("Codex found no worthwhile safe cleanup for the selected classes.");
        } else {
            ui.printTable(
                    List.of("Verified file", "Kind"),
                    result.changedRelativePaths().stream()
                            .map(path -> List.of(normalize(path), fileKind(path)))
                            .toList()
            );
            if (result.checkOnly()) {
                ui.warn("Verified cleanup improvements are available; the project was not changed.");
            } else {
                ui.success("Merged the verified cleanup transactionally.");
            }
        }

        LinkedHashMap<String, String> summary = new LinkedHashMap<>();
        summary.put("baseline verify", formatDuration(result.baselineVerificationElapsed()));
        summary.put("codex", formatDuration(result.agentElapsed()));
        summary.put("candidate verify", formatDuration(result.candidateVerificationElapsed()));
        summary.put("changed files", String.valueOf(result.changedRelativePaths().size()));
        summary.put("agent tokens", String.valueOf(result.usage().totalTokens()));
        ui.printKeyValues(summary);

        return result.checkOnly() && !result.changedRelativePaths().isEmpty()
                ? CHANGES_REQUIRED_EXIT_CODE
                : CommandLine.ExitCode.OK;
    }

    private void validateTargetMode() {
        int modes = (selector == null || selector.isBlank() ? 0 : 1) + (changed ? 1 : 0) + (all ? 1 : 0);
        if (modes > 1) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    "Choose only one cleanup target: `<class>`, `--changed`, or `--all`."
            );
        }
    }

    private List<JavaProjectService.JavaClassDescriptor> resolveTargets(Path projectRoot) {
        if (selector != null && !selector.isBlank()) {
            return List.of(projectService.resolveClass(projectRoot, selector));
        }
        if (all) {
            return projectService.findProductionClasses(projectRoot);
        }
        return projectService.findChangedProductionClasses(projectRoot);
    }

    private String targetMode() {
        if (selector != null && !selector.isBlank()) {
            return "class " + selector;
        }
        return all ? "all production classes" : "changed production classes";
    }

    private String fileKind(Path path) {
        String normalized = normalize(path);
        return normalized.startsWith("src/test/java/") || normalized.contains("/src/test/java/")
                ? "regression test"
                : "production";
    }

    private String formatDuration(Duration duration) {
        if (duration == null) {
            return "not needed";
        }
        long millis = duration.toMillis();
        if (millis < 1_000L) {
            return millis + " ms";
        }
        return "%.2f s".formatted(millis / 1_000.0d);
    }

    private String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }
}
