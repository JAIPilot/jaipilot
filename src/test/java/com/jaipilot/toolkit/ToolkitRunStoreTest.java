package com.jaipilot.toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaipilot.toolkit.core.WorkflowRunService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolkitRunStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void inspectsProjectWithoutCreatingAnActiveRun() throws Exception {
        Path project = tempDir.resolve("project");
        Path source = project.resolve("src/main/java/com/example/OrderService.java");
        Files.createDirectories(source.getParent());
        Files.writeString(project.resolve("pom.xml"), minimalPom());
        Files.writeString(source, "package com.example; class OrderService {}\n");

        ToolkitRunStore store = new ToolkitRunStore(new ObjectMapper(), tempDir.resolve("state"));
        WorkflowRunService.ProjectInspection inspection = store.inspect(project);

        assertEquals(project.toRealPath(), inspection.projectRoot());
        assertEquals("maven", inspection.buildTool());
        assertEquals(1, inspection.productionClassCount());
        assertNull(inspection.activeRunId());
        assertFalse(Files.exists(tempDir.resolve("state/runs/unknown.json")));
    }

    @Test
    void rejectsNonUuidRunIdentifiersBeforeResolvingStateFiles() {
        ToolkitRunStore store = new ToolkitRunStore(new ObjectMapper(), tempDir.resolve("state"));

        assertThrows(IllegalArgumentException.class, () -> store.status("../outside"));
    }

    @Test
    void rejectsStateWhoseEmbeddedRunIdDoesNotMatchItsSafeFilename() throws Exception {
        Path project = tempDir.resolve("project");
        Path source = project.resolve("src/main/java/com/example/OrderService.java");
        Files.createDirectories(source.getParent());
        Files.writeString(project.resolve("pom.xml"), minimalPom());
        Files.writeString(source, "package com.example; class OrderService {}\n");
        Path stateRoot = tempDir.resolve("state");
        ToolkitRunStore store = new ToolkitRunStore(new ObjectMapper(), stateRoot);
        String safeFilename = UUID.randomUUID().toString();
        Files.writeString(
                stateRoot.resolve("runs").resolve(safeFilename + ".json"),
                "{\"schemaVersion\":1,\"runId\":\"../../escape\"}"
        );

        assertThrows(IllegalStateException.class, () -> store.inspect(project));
        assertFalse(Files.exists(tempDir.resolve("escape.lock")));
    }

    private String minimalPom() {
        return """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>fixture</artifactId>
                  <version>1.0.0</version>
                </project>
                """;
    }
}
