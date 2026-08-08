package com.jaipilot.toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs({OS.LINUX, OS.MAC})
class HookScriptTest {

    @TempDir
    Path tempDir;

    @Test
    void hooksDelegateOnlyInitializationDirectCommitAndStopToThePrivateRunner() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Path log = tempDir.resolve("runner.log");
        Path fakeRunner = tempDir.resolve("fake-runner.sh");
        Files.writeString(fakeRunner, "#!/bin/sh\nprintf '%s|%s\\n' \"$PWD\" \"$*\" >> \"$JAIPILOT_TEST_LOG\"\n");
        assertTrue(fakeRunner.toFile().setExecutable(true, false));
        Path pluginRoot = Path.of("plugins/jaipilot").toAbsolutePath().normalize();
        Map<String, String> environment = Map.of(
                "PLUGIN_ROOT", pluginRoot.toString(),
                "JAIPILOT_TOOLKIT_EXECUTABLE", fakeRunner.toString(),
                "JAIPILOT_STATE_HOME", tempDir.resolve("state").toString(),
                "JAIPILOT_TEST_LOG", log.toString()
        );

        assertEquals("", run(pluginRoot.resolve("hooks/post-tool-use.sh"), project, environment,
                "{\"tool_input\":{\"command\":\"git status --short\"}}"));
        assertFalse(Files.exists(log));

        run(pluginRoot.resolve("hooks/post-tool-use.sh"), project, environment,
                "{\"tool_input\":{\"command\":\"git commit -m test\"}}" );
        String canonicalProject = project.toRealPath().toString();
        waitFor(() -> read(log).contains("snapshot --project " + canonicalProject));

        run(pluginRoot.resolve("hooks/session-start.sh"), project, environment, "");
        waitFor(() -> read(log).contains("snapshot --project " + canonicalProject));

        assertFalse(Files.exists(project.resolve(".jaipilot")));
        assertFalse(Files.exists(project.resolve("target")));
    }

    private String run(Path script, Path directory, Map<String, String> environment, String input) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(script.toString()).directory(directory.toFile());
        builder.environment().putAll(environment);
        Process process = builder.start();
        process.getOutputStream().write(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        process.getOutputStream().close();
        assertEquals(0, process.waitFor(), () -> read(process.getErrorStream()));
        return read(process.getInputStream());
    }

    private String read(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path) : "";
        } catch (Exception exception) {
            return "";
        }
    }

    private String read(java.io.InputStream stream) {
        try {
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return "";
        }
    }

    private void waitFor(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for detached SessionStart snapshot");
    }
}
