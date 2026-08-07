package com.jaipilot.toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaipilot.toolkit.core.ArchitectureService;
import com.jaipilot.toolkit.core.DiffVerificationService;
import com.jaipilot.toolkit.core.WorkflowRunService;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
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

    @Test
    void passingProofReceiptIsExactAndRelevantChangesInvalidateIt() throws Exception {
        Path project = tempDir.resolve("proof-project");
        Path source = project.resolve("src/main/java/com/example/DeletedService.java");
        Files.createDirectories(source.getParent());
        Files.writeString(project.resolve("pom.xml"), minimalPom());
        Files.writeString(source, "package com.example; class DeletedService {}\n");
        Path wrapper = project.resolve("mvnw");
        Files.writeString(wrapper, "#!/bin/sh\nexit 0\n");
        assertTrue(wrapper.toFile().setExecutable(true, false));
        Path wrapperMetadata = project.resolve(".mvn/wrapper/maven-wrapper.properties");
        Files.createDirectories(wrapperMetadata.getParent());
        Files.writeString(wrapperMetadata, "distributionUrl=https://repo.maven.apache.org/maven2\n");
        initializeGit(project);
        commit(project, "baseline");
        Files.delete(source);
        commit(project, "delete obsolete production class");

        List<String> progress = new ArrayList<>();
        ToolkitRunStore store = new ToolkitRunStore(
                new ObjectMapper(),
                tempDir.resolve("proof-state"),
                progress::add
        );
        DiffVerificationService.VerificationThresholds thresholds = DiffVerificationService.DEFAULT_THRESHOLDS;

        assertEquals("review_required", store.diffGate(project, thresholds).status());
        assertTrue(store.proveDiff(project, thresholds).passed());
        assertTrue(progress.stream().anyMatch(value -> value.contains("deletion-only diff")));
        assertEquals("Changed-code proof passed.", progress.get(progress.size() - 1));
        assertEquals("passed", store.diffGate(project, thresholds).status());
        assertEquals(
                "review_required",
                store.diffGate(project, new DiffVerificationService.VerificationThresholds(91, 85, 80, 90)).status()
        );
        assertEquals(
                "review_required",
                store.diffGate(project, new DiffVerificationService.VerificationThresholds(90, 86, 80, 90)).status()
        );
        assertEquals(
                "review_required",
                store.diffGate(project, new DiffVerificationService.VerificationThresholds(90, 85, 81, 90)).status()
        );
        assertEquals(
                "review_required",
                store.diffGate(project, new DiffVerificationService.VerificationThresholds(90, 85, 80, 91)).status()
        );
        assertEquals(
                "passed",
                store.diffGate(project, new DiffVerificationService.VerificationThresholds(89, 84, 79, 89)).status()
        );
        String projectHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(project.toRealPath().toString().getBytes(StandardCharsets.UTF_8)));
        Path receipt = tempDir.resolve("proof-state/proofs/" + projectHash + ".json");
        String validReceipt = Files.readString(receipt);
        ObjectMapper mapper = new ObjectMapper();
        var validNode = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(validReceipt);
        assertEquals(2, validNode.path("schemaVersion").asInt());
        assertEquals(0, validNode.path("architectureViolationCount").asInt());
        assertEquals(
                ArchitectureService.RULESET_VERSION,
                validNode.path("architectureRulesetVersion").asInt()
        );
        Files.writeString(receipt, "{not-json");
        assertEquals("review_required", store.diffGate(project, thresholds).status());
        var invalidSchema = validNode.deepCopy();
        invalidSchema.put("schemaVersion", 3);
        Files.writeString(receipt, mapper.writeValueAsString(invalidSchema));
        assertEquals("review_required", store.diffGate(project, thresholds).status());
        invalidSchema.put("schemaVersion", 1);
        invalidSchema.putNull("thresholds");
        Files.writeString(receipt, mapper.writeValueAsString(invalidSchema));
        assertEquals("review_required", store.diffGate(project, thresholds).status());
        var staleArchitecture = validNode.deepCopy();
        staleArchitecture.put("architectureRulesetVersion", ArchitectureService.RULESET_VERSION + 1);
        Files.writeString(receipt, mapper.writeValueAsString(staleArchitecture));
        assertEquals("review_required", store.diffGate(project, thresholds).status());
        staleArchitecture = validNode.deepCopy();
        staleArchitecture.put("architectureViolationCount", 1);
        Files.writeString(receipt, mapper.writeValueAsString(staleArchitecture));
        assertEquals("review_required", store.diffGate(project, thresholds).status());
        Files.writeString(receipt, validReceipt);

        Files.writeString(project.resolve("pom.xml"), minimalPom().replace("1.0.0", "1.0.1"));

        assertEquals("review_required", store.diffGate(project, thresholds).status());
    }

    @Test
    void unchangedRepositoryMakesTheDiffGateNotApplicable() throws Exception {
        Path project = tempDir.resolve("unchanged-project");
        Path source = project.resolve("src/main/java/com/example/OrderService.java");
        Files.createDirectories(source.getParent());
        Files.writeString(project.resolve("pom.xml"), minimalPom());
        Files.writeString(source, "package com.example; class OrderService {}\n");
        initializeGit(project);
        commit(project, "baseline");
        ToolkitRunStore store = new ToolkitRunStore(new ObjectMapper(), tempDir.resolve("unchanged-state"));

        ToolkitRunStore.DiffGateStatus gate = store.diffGate(
                project,
                DiffVerificationService.DEFAULT_THRESHOLDS
        );

        assertEquals("not_applicable", gate.status());
        assertTrue(gate.changedProductionPaths().isEmpty());
        assertTrue(store.proveDiff(project, DiffVerificationService.DEFAULT_THRESHOLDS).passed());
    }

    @Test
    void concurrentProofForTheSameProjectFailsFast() throws Exception {
        Path project = tempDir.resolve("locked-project");
        Path source = project.resolve("src/main/java/com/example/OrderService.java");
        Files.createDirectories(source.getParent());
        Files.writeString(project.resolve("pom.xml"), minimalPom());
        Files.writeString(source, "package com.example; class OrderService {}\n");
        initializeGit(project);
        commit(project, "baseline");
        Path state = tempDir.resolve("locked-state");
        ToolkitRunStore store = new ToolkitRunStore(new ObjectMapper(), state);
        String projectHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(project.toRealPath().toString().getBytes(StandardCharsets.UTF_8)));
        Path lockPath = state.resolve("locks/proof-" + projectHash + ".lock");

        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> store.proveDiff(project, DiffVerificationService.DEFAULT_THRESHOLDS)
            );
            assertTrue(failure.getMessage().contains("already running"));
        }
    }

    @Test
    void proofProgressReporterIsRequired() {
        assertThrows(
                NullPointerException.class,
                () -> new ToolkitRunStore(new ObjectMapper(), tempDir.resolve("null-progress"), null)
        );
    }

    private void initializeGit(Path project) throws Exception {
        git(project, "init", "-q", "-b", "main");
        git(project, "config", "user.name", "JAIPilot Test");
        git(project, "config", "user.email", "test@jaipilot.local");
    }

    private void commit(Path project, String message) throws Exception {
        git(project, "add", "-A");
        git(project, "commit", "-qm", message);
    }

    private void git(Path project, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(project.toFile()).start();
        int status = process.waitFor();
        String errors = new String(process.getErrorStream().readAllBytes());
        assertEquals(0, status, errors);
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
