package com.jaipilot.toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaipilot.toolkit.core.DiffVerificationService;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolkitRunStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void inspectAndQualityExposeOnlyCurrentRepositoryEvidence() throws Exception {
        Path project = TestProject.maven(tempDir, "inspect");
        ToolkitRunStore store = store("state");

        ToolkitRunStore.Inspection inspection = store.inspect(project);
        ToolkitRunStore.QualityInspection quality = store.quality(project, ToolkitRunStore.TargetSelection.all());

        assertEquals(project.toRealPath(), inspection.projectRoot());
        assertEquals("maven", inspection.buildTool());
        assertEquals(1, inspection.productionClassCount());
        assertEquals(1, quality.targets().size());
        assertEquals(1, quality.quality().metrics().fileCount());
        assertTrue(Files.isDirectory(tempDir.resolve("state/proofs")));
        assertFalse(Files.exists(tempDir.resolve("state/runs")));
        assertFalse(Files.exists(tempDir.resolve("state/workspaces")));
    }

    @Test
    void nestedModuleInvocationUsesTheCanonicalGitWorktreeBoundary() throws Exception {
        Path project = tempDir.resolve("multi-module");
        Files.createDirectories(project);
        Files.writeString(project.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId><artifactId>root</artifactId><version>1</version>
                  <packaging>pom</packaging><modules><module>one</module><module>two</module></modules>
                </project>
                """);
        createModule(project.resolve("one"), "OneService");
        createModule(project.resolve("two"), "TwoService");
        TestProject.git(project, "init", "-q", "-b", "main");
        TestProject.git(project, "config", "user.name", "JAIPilot Test");
        TestProject.git(project, "config", "user.email", "test@jaipilot.local");
        TestProject.commit(project, "baseline");

        ToolkitRunStore.Inspection inspection = store("nested-state").inspect(
                project.resolve("one/src/main/java")
        );

        assertEquals(project.toRealPath(), inspection.projectRoot());
        assertEquals(2, inspection.productionClassCount());
    }

    @Test
    void targetSelectionRejectsAmbiguousOrMissingScope() throws Exception {
        Path project = TestProject.maven(tempDir, "selection");
        ToolkitRunStore store = store("selection-state");

        assertThrows(IllegalArgumentException.class, () -> new ToolkitRunStore.TargetSelection(
                ToolkitRunStore.TargetMode.CLASSES, List.of(), 80
        ));
        assertThrows(IllegalStateException.class, () -> store.quality(project,
                new ToolkitRunStore.TargetSelection(
                        ToolkitRunStore.TargetMode.CLASSES, List.of("com.example.Missing"), 80
                )));
    }

    @Test
    void unchangedRepositoryIsNotApplicableAndChangedSourceRequiresProof() throws Exception {
        Path project = TestProject.gitMaven(tempDir, "gate");
        ToolkitRunStore store = store("gate-state");

        assertEquals("not_applicable", gate(store, project).status());
        Path source = project.resolve("src/main/java/com/example/OrderService.java");
        Files.writeString(source, "package com.example; public class OrderService { int total() { return 2; } }\n");

        ToolkitRunStore.DiffGateStatus gate = gate(store, project);
        assertEquals("review_required", gate.status());
        assertEquals(1, gate.changedProductionPaths().size());
        assertTrue(gate.fingerprint().matches("[0-9a-f]{64}"));
    }

    @Test
    void buildOnlyProofRunsCleanBuildAndCreatesExactReceipt() throws Exception {
        Path project = TestProject.gitMaven(tempDir, "build-only");
        TestProject.successfulWrapper(project);
        TestProject.commit(project, "add wrapper");
        Files.writeString(project.resolve("pom.xml"), TestProject.pom().replace("1.0.0", "1.0.1"));
        ToolkitRunStore store = store("proof-state");

        assertEquals("review_required", gate(store, project).status());
        assertTrue(store.proveDiff(project, DiffVerificationService.DEFAULT_THRESHOLDS).passed());
        assertEquals("passed", gate(store, project).status());

        Files.writeString(project.resolve("pom.xml"), TestProject.pom().replace("1.0.0", "1.0.2"));
        assertEquals("review_required", gate(store, project).status());
    }

    @Test
    void stricterThresholdsInvalidateOtherwiseCurrentReceipt() throws Exception {
        Path project = TestProject.gitMaven(tempDir, "threshold");
        TestProject.successfulWrapper(project);
        TestProject.commit(project, "add wrapper");
        Files.writeString(project.resolve("pom.xml"), TestProject.pom().replace("1.0.0", "1.0.1"));
        ToolkitRunStore store = store("threshold-state");
        store.proveDiff(project, DiffVerificationService.DEFAULT_THRESHOLDS);

        assertEquals("review_required", store.diffGate(project,
                new DiffVerificationService.VerificationThresholds(91, 85, 80, 90)).status());
        assertEquals("passed", store.diffGate(project,
                new DiffVerificationService.VerificationThresholds(89, 84, 79, 89)).status());
    }

    @Test
    void sameRepositoryProofLockFailsFast() throws Exception {
        Path project = TestProject.gitMaven(tempDir, "locked");
        Files.writeString(project.resolve("pom.xml"), TestProject.pom().replace("1.0.0", "1.0.1"));
        Path state = tempDir.resolve("locked-state");
        ToolkitRunStore store = new ToolkitRunStore(new ObjectMapper(), state);
        Path lock = state.resolve("locks/proof-" + ToolkitRunStore.repositoryId(project.toRealPath()) + ".lock");

        try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> store.proveDiff(project, DiffVerificationService.DEFAULT_THRESHOLDS));
            assertTrue(failure.getMessage().contains("already running"));
        }
    }

    private ToolkitRunStore store(String name) {
        return new ToolkitRunStore(new ObjectMapper(), tempDir.resolve(name));
    }

    private ToolkitRunStore.DiffGateStatus gate(ToolkitRunStore store, Path project) {
        return store.diffGate(project, DiffVerificationService.DEFAULT_THRESHOLDS);
    }

    private void createModule(Path module, String className) throws Exception {
        Files.createDirectories(module.resolve("src/main/java/com/example"));
        Files.writeString(module.resolve("pom.xml"), TestProject.pom().replace("fixture", className.toLowerCase()));
        Files.writeString(module.resolve("src/main/java/com/example/" + className + ".java"),
                "package com.example; public class " + className + " {}\n");
    }
}
