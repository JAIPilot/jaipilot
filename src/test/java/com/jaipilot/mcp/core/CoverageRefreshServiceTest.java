package com.jaipilot.mcp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoverageRefreshServiceTest {

    @TempDir
    Path root;

    private CoverageRefreshService service;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project>jacoco</project>\n");
        Path wrapperProperties = root.resolve(".mvn/wrapper/maven-wrapper.properties");
        Files.createDirectories(wrapperProperties.getParent());
        Files.writeString(wrapperProperties, "distributionUrl=fixture\n");
        ProjectFileService files = new ProjectFileService();
        CoverageReportService reports = new CoverageReportService();
        JavaProjectService projects = new JavaProjectService(files, reports);
        service = new CoverageRefreshService(projects, reports, new ProcessExecutor(), Duration.ofSeconds(10));
    }

    @Test
    void refreshRunsCleanWrapperBuildAndReadsFreshJacoco() throws Exception {
        writeWrapper("""
                #!/bin/sh
                set -eu
                test "$1" = "-B"
                test "$2" = "clean"
                test "$3" = "verify"
                rm -rf target
                mkdir -p target/site/jacoco
                cat > target/site/jacoco/jacoco.xml <<'XML'
                <report><counter type="LINE" missed="1" covered="3"/><counter type="BRANCH" missed="1" covered="1"/></report>
                XML
                """);

        CoverageReportService.CoverageSnapshot snapshot = service.refresh(root);

        assertEquals(75.0d, snapshot.totalLineCoverage());
        assertEquals(50.0d, snapshot.totalBranchCoverage());
    }

    @Test
    void failedRefreshRemovesStaleAndPartialReports() throws Exception {
        Path stale = root.resolve("target/site/jacoco/jacoco.xml");
        Files.createDirectories(stale.getParent());
        Files.writeString(stale, "<report/>\n");
        writeWrapper("""
                #!/bin/sh
                set -eu
                mkdir -p target/site/jacoco
                printf '<partial' > target/site/jacoco/jacoco.xml
                echo 'fixture failure'
                exit 9
                """);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> service.refresh(root));

        assertTrue(failure.getMessage().contains("fixture failure"));
        assertFalse(Files.exists(stale));
    }

    @Test
    void projectLockRejectsOverlapWithoutWaiting() throws Exception {
        try (CoverageRefreshService.ProjectRefreshLock ignored = service.acquireProjectLock(root)) {
            CoverageRefreshService.RefreshInProgressException failure = assertThrows(
                    CoverageRefreshService.RefreshInProgressException.class,
                    () -> service.acquireProjectLock(root)
            );
            assertTrue(failure.getMessage().contains("already running"));
        }
    }

    @Test
    void cacheEnvironmentPreservesUserValuesAndAddsSafeDefaults() {
        Map<String, String> defaults = CoverageRefreshService.buildToolCacheEnvironment(root, Map.of());
        assertTrue(defaults.get("MAVEN_OPTS").contains("trackingFilename=ignore"));
        assertEquals(root.toAbsolutePath().normalize().resolve(".gradle/jaipilot").toString(),
                defaults.get("GRADLE_USER_HOME"));

        Map<String, String> preserved = CoverageRefreshService.buildToolCacheEnvironment(root, Map.of(
                "MAVEN_OPTS", "-Xmx1g -Daether.enhancedLocalRepository.trackingFilename=custom",
                "GRADLE_USER_HOME", "/custom/gradle"
        ));
        assertTrue(preserved.isEmpty());
    }

    private void writeWrapper(String script) throws Exception {
        Path wrapper = root.resolve("mvnw");
        Files.writeString(wrapper, script);
        assertTrue(wrapper.toFile().setExecutable(true, false));
    }
}
