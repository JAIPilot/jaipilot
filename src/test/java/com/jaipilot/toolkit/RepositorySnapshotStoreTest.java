package com.jaipilot.toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaipilot.toolkit.core.DiffVerificationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositorySnapshotStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void registrationPublishesInitializingStateAndNormalizedGitHubIdentity() throws Exception {
        Path project = TestProject.gitMaven(tempDir, "registered");
        TestProject.git(project, "remote", "add", "origin", "git@github.com:JAIPilot/jaipilot.git");
        Path stateRoot = tempDir.resolve("state");
        RepositorySnapshotStore store = new RepositorySnapshotStore(new ObjectMapper(), stateRoot);

        RepositorySnapshotStore.RepositoryState state = store.register(project.resolve("src/main"));

        assertEquals(project.toRealPath().toString(), state.projectRoot());
        assertEquals("JAIPilot/jaipilot", state.displayName());
        assertEquals("https://github.com/JAIPilot/jaipilot", state.githubUrl());
        assertEquals("initializing", state.analysisStatus());
        assertNull(state.quality());
        if (Files.getFileStore(stateRoot).supportsFileAttributeView("posix")) {
            assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(stateRoot)));
        }

        Path snapshot;
        try (var paths = Files.list(stateRoot.resolve("repositories"))) {
            snapshot = paths.findFirst().orElseThrow();
        }
        long unchanged = Files.getLastModifiedTime(snapshot).toMillis();
        Thread.sleep(20);
        store.register(project);
        assertEquals(unchanged, Files.getLastModifiedTime(snapshot).toMillis());
    }

    @Test
    void analysisStoresCurrentMetricsAndObservedImpactOnly() throws Exception {
        Path project = TestProject.gitMaven(tempDir, "analysis");
        Path stateRoot = tempDir.resolve("analysis-state");
        ObjectMapper mapper = new ObjectMapper();
        ToolkitRunStore engine = new ToolkitRunStore(mapper, stateRoot);
        RepositorySnapshotStore snapshots = new RepositorySnapshotStore(mapper, stateRoot);
        snapshots.register(project);
        ToolkitRunStore.QualityInspection first = engine.quality(project, ToolkitRunStore.TargetSelection.all());
        snapshots.recordAnalysis(project, first, engine.diffGate(project, DiffVerificationService.DEFAULT_THRESHOLDS));

        RepositorySnapshotStore.RepositoryState state = snapshots.view(null).selectedRepository();
        assertEquals("ready", state.analysisStatus());
        assertEquals(1, state.quality().metrics().fileCount());
        assertEquals(0.0d, state.impact().qualityScoreChange());
        assertTrue(Files.list(stateRoot.resolve("repositories")).count() == 1);
        assertTrue(Files.notExists(stateRoot.resolve("usage.json")));
        assertTrue(Files.notExists(stateRoot.resolve("runs")));
    }

    @Test
    void selectionIsExplicitAndFailuresPreserveLastGoodEvidence() throws Exception {
        Path first = TestProject.maven(tempDir, "first");
        Path second = TestProject.maven(tempDir, "second");
        ObjectMapper mapper = new ObjectMapper();
        Path stateRoot = tempDir.resolve("selection-state");
        RepositorySnapshotStore snapshots = new RepositorySnapshotStore(mapper, stateRoot);
        ToolkitRunStore engine = new ToolkitRunStore(mapper, stateRoot);
        RepositorySnapshotStore.RepositoryState firstState = snapshots.register(first);
        snapshots.register(second);
        snapshots.recordAnalysis(first, engine.quality(first, ToolkitRunStore.TargetSelection.all()), null);
        snapshots.recordFailure(first, "synthetic failure");

        RepositorySnapshotStore.RepositoryState failed = snapshots.view(firstState.id()).selectedRepository();
        assertEquals("failed", failed.analysisStatus());
        assertEquals("synthetic failure", failed.error());
        assertEquals(1, failed.quality().metrics().fileCount());
        assertThrows(IllegalArgumentException.class, () -> snapshots.view("0".repeat(64)));
    }
}
