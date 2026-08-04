package com.jaipilot.toolkit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OpenRewriteCleanupServiceTest {

    @TempDir
    Path root;

    @Test
    void mavenUsesPinnedRecipesAndExactEscapedTargets() throws Exception {
        Path first = createMavenTarget("OrderService.java");
        Path quoted = createMavenTarget("Customer's.java");
        AtomicReference<String> yaml = new AtomicReference<>();
        AtomicReference<Path> configPath = new AtomicReference<>();
        OpenRewriteCleanupService service = service((command, workingDirectory, timeout, logs, writer, progress, env) -> {
            Path config = optionPath(command, "-Drewrite.configLocation=");
            configPath.set(config);
            yaml.set(Files.readString(config));
            assertEquals(Duration.ofMinutes(3), timeout);
            assertFalse(logs);
            return success(command);
        });

        OpenRewriteCleanupService.RewriteResult result = service.clean(root, List.of(first, first, quoted));

        assertTrue(result.command().contains("org.openrewrite.maven:rewrite-maven-plugin:"
                + OpenRewriteCleanupService.MAVEN_PLUGIN_VERSION + ":runNoFork"));
        assertTrue(result.command().contains("-Drewrite.recipeArtifactCoordinates="
                + "org.openrewrite.recipe:rewrite-static-analysis:"
                + OpenRewriteCleanupService.STATIC_ANALYSIS_VERSION));
        assertTrue(yaml.get().contains("org.openrewrite.staticanalysis.CodeCleanup"));
        assertTrue(yaml.get().contains("org.openrewrite.staticanalysis.CommonStaticAnalysis"));
        assertTrue(yaml.get().contains("OrderService.java;src/main/java/com/example/Customer''s.java"));
        assertFalse(Files.exists(configPath.get()));
    }

    @Test
    void gradleUsesPinnedInitScriptAndNoDaemon() throws Exception {
        Path target = createGradleTarget();
        AtomicReference<String> init = new AtomicReference<>();
        AtomicReference<Path> initPath = new AtomicReference<>();
        OpenRewriteCleanupService service = service((command, workingDirectory, timeout, logs, writer, progress, env) -> {
            Path path = Path.of(command.get(command.indexOf("--init-script") + 1));
            initPath.set(path);
            init.set(Files.readString(path));
            assertEquals(Map.of(), env);
            return success(command);
        });

        OpenRewriteCleanupService.RewriteResult result = service.clean(root, List.of(target));

        assertEquals(List.of("gradle", "--no-daemon", "--init-script"), result.command().subList(0, 3));
        assertTrue(init.get().contains("org.openrewrite:plugin:"
                + OpenRewriteCleanupService.GRADLE_PLUGIN_VERSION));
        assertTrue(init.get().contains("activeRecipe('" + OpenRewriteCleanupService.TARGETED_RECIPE + "')"));
        assertFalse(Files.exists(initPath.get()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bad;Name.java", "Bad*Name.java", "Bad?Name.java", "Bad[Name].java", "Bad{One}.java"})
    void rejectsAmbiguousRecipePatternsBeforeExecution(String name) throws Exception {
        Path target = createMavenTarget(name);
        List<List<String>> commands = new ArrayList<>();
        OpenRewriteCleanupService service = service((command, root, timeout, logs, writer, progress, env) -> {
            commands.add(command);
            return success(command);
        });

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.clean(root, List.of(target))
        );

        assertTrue(failure.getMessage().contains("unsupported character"));
        assertTrue(commands.isEmpty());
    }

    @Test
    void rejectsOutsideTestMissingAndSymbolicTargets() throws Exception {
        Path valid = createMavenTarget("OrderService.java");
        Path test = write(root.resolve("src/test/java/com/example/OrderServiceTest.java"), "class Test {}\n");
        Path outside = write(root.getParent().resolve("outside/src/main/java/Outside.java"), "class Outside {}\n");
        Path symbolic = root.resolve("src/main/java/com/example/Link.java");
        Files.createSymbolicLink(symbolic, valid);
        OpenRewriteCleanupService service = service((command, cwd, timeout, logs, writer, progress, env) ->
                success(command));

        for (Path invalid : List.of(test, outside, symbolic, root.resolve("missing.java"))) {
            assertThrows(IllegalArgumentException.class, () -> service.clean(root, List.of(invalid)));
        }
        assertThrows(IllegalArgumentException.class, () -> service.clean(root, List.of()));
        assertThrows(NullPointerException.class, () -> service.clean(root, null));
    }

    @Test
    void reportsBoundedUsefulFailureTail() throws Exception {
        Path target = createMavenTarget("OrderService.java");
        String output = java.util.stream.IntStream.rangeClosed(1, 45)
                .mapToObj(index -> "line-%02d".formatted(index))
                .collect(java.util.stream.Collectors.joining("\n"));
        OpenRewriteCleanupService service = service((command, cwd, timeout, logs, writer, progress, env) ->
                new ProcessExecutor.ExecutionResult(command, 7, false, output, Duration.ofMillis(5)));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.clean(root, List.of(target))
        );

        assertTrue(failure.getMessage().contains("exit code 7"));
        assertFalse(failure.getMessage().contains("line-05" + System.lineSeparator()));
        assertTrue(failure.getMessage().contains("line-06"));
        assertTrue(failure.getMessage().endsWith("line-45"));
    }

    @Test
    void timeoutAndInterruptionDiscardTemporaryRecipe() throws Exception {
        Path target = createMavenTarget("OrderService.java");
        OpenRewriteCleanupService timeout = service((command, cwd, duration, logs, writer, progress, env) ->
                new ProcessExecutor.ExecutionResult(command, -1, true, "", duration));
        assertTrue(assertThrows(IllegalStateException.class, () -> timeout.clean(root, List.of(target)))
                .getMessage().contains("timed out after 3 minutes"));

        AtomicReference<Path> recipe = new AtomicReference<>();
        InterruptedException interruption = new InterruptedException("stop");
        OpenRewriteCleanupService interrupted = service((command, cwd, duration, logs, writer, progress, env) -> {
            recipe.set(optionPath(command, "-Drewrite.configLocation="));
            throw interruption;
        });
        Thread.interrupted();
        try {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> interrupted.clean(root, List.of(target))
            );
            assertSame(interruption, failure.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
            assertFalse(Files.exists(recipe.get()));
        } finally {
            Thread.interrupted();
        }
    }

    private OpenRewriteCleanupService service(OpenRewriteCleanupService.RewriteExecutor executor) {
        ProjectFileService files = new ProjectFileService();
        JavaProjectService projects = new JavaProjectService(files, new CoverageReportService());
        return new OpenRewriteCleanupService(projects, executor, Duration.ofMinutes(3));
    }

    private Path createMavenTarget(String name) throws IOException {
        Files.writeString(root.resolve("pom.xml"), "<project/>\n");
        return write(root.resolve("src/main/java/com/example/" + name), "package com.example; class Fixture {}\n");
    }

    private Path createGradleTarget() throws IOException {
        Files.writeString(root.resolve("build.gradle"), "plugins { id 'java' }\n");
        return write(root.resolve("src/main/java/com/example/OrderService.java"), "class OrderService {}\n");
    }

    private Path write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path;
    }

    private Path optionPath(List<String> command, String prefix) {
        return Path.of(command.stream().filter(value -> value.startsWith(prefix)).findFirst().orElseThrow()
                .substring(prefix.length()));
    }

    private ProcessExecutor.ExecutionResult success(List<String> command) {
        return new ProcessExecutor.ExecutionResult(command, 0, false, "", Duration.ofMillis(5));
    }
}
