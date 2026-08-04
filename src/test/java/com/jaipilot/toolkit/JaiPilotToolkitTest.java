package com.jaipilot.toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JaiPilotToolkitTest {

    @Test
    void helpDescribesAgentFacingTransactions() {
        Captured result = run("--help");

        assertEquals(0, result.status());
        assertTrue(result.stdout().contains("prepare-tests"));
        assertTrue(result.stdout().contains("prepare-cleanup"));
        assertTrue(result.stdout().contains("jaipilot quality"));
        assertTrue(result.stdout().contains("--minimum-mutation-score"));
        assertFalse(result.stdout().contains("skip-mutation"));
        assertTrue(result.stdout().contains("apply --run <uuid> --confirm"));
        assertEquals("", result.stderr());
    }

    @Test
    void versionIsStructuredJson() throws Exception {
        Captured result = run("version");
        JsonNode json = new ObjectMapper().readTree(result.stdout());

        assertEquals(0, result.status());
        assertTrue(json.path("ok").asBoolean());
        assertTrue(json.path("result").path("version").isTextual());
        assertEquals("", result.stderr());
    }

    private Captured run(String... arguments) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int status = JaiPilotToolkit.run(
                arguments,
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8)
        );
        return new Captured(
                status,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8)
        );
    }

    private record Captured(int status, String stdout, String stderr) {
    }
}
