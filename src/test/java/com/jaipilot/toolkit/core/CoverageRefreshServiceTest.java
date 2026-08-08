package com.jaipilot.toolkit.core;

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
                test "$2" = "-Dmaven.build.cache.enabled=false"
                test "$3" = "clean"
                test "$4" = "verify"
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
                echo 'Gradle Test Run :clients:test > FlakyTest > failsReliably() FAILED'
                i=0
                while [ "$i" -lt 50 ]; do echo "passing output $i"; i=$((i + 1)); done
                echo 'fixture failure'
                exit 9
                """);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> service.refresh(root));

        assertTrue(failure.getMessage().contains("FlakyTest > failsReliably() FAILED"));
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
        Map<String, String> defaults = CoverageRefreshService.buildToolCacheEnvironment(
                root, Map.of("XDG_CACHE_HOME", root.resolve("cache").toString())
        );
        assertTrue(defaults.get("MAVEN_OPTS").contains("trackingFilename=ignore"));
        assertEquals(root.resolve("cache/jaipilot/gradle").toAbsolutePath().normalize().toString(),
                defaults.get("GRADLE_USER_HOME"));
        assertFalse(defaults.get("GRADLE_USER_HOME").startsWith(root.resolve(".gradle").toString()));

        Map<String, String> preserved = CoverageRefreshService.buildToolCacheEnvironment(root, Map.of(
                "MAVEN_OPTS", "-Xmx1g -Daether.enhancedLocalRepository.trackingFilename=custom",
                "GRADLE_USER_HOME", "/custom/gradle"
        ));
        assertTrue(preserved.isEmpty());
    }

    @Test
    void kafkaStyleGradleTargetsRunFullBuildAndReadOnlyTheirModuleReport() throws Exception {
        Files.delete(root.resolve("pom.xml"));
        Files.writeString(root.resolve("build.gradle"), """
                if (project.hasProperty('enableTestCoverage')) { tasks.register('reportCoverage') }
                """);
        Path module = root.resolve("clients");
        Path source = module.resolve("src/main/java/com/example/Client.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package com.example; class Client {}\n");
        Files.writeString(module.resolve("build.gradle"), "plugins { id 'java' }\n");
        Path wrapperProperties = root.resolve("gradle/wrapper/gradle-wrapper.properties");
        Files.createDirectories(wrapperProperties.getParent());
        Files.writeString(wrapperProperties, "distributionUrl=fixture\n");
        Path wrapper = root.resolve("gradlew");
        Files.writeString(wrapper, """
                #!/bin/sh
                set -eu
                for required in --no-build-cache --rerun-tasks clean build jaipilotTargetCoverage; do
                  case " $* " in *" $required "*) ;; *) echo "missing $required: $*"; exit 8 ;; esac
                done
                case " $* " in *" -PenableTestCoverage=true "*) ;; *) exit 9 ;; esac
                mkdir -p clients/build/reports/jacoco/test
                cat > clients/build/reports/jacoco/test/jacocoTestReport.xml <<'XML'
                <report><package name="com/example"><class name="com/example/Client"><counter type="LINE" missed="0" covered="1"/></class><sourcefile name="Client.java"><line nr="1" mi="0" ci="1" mb="0" cb="0"/></sourcefile></package></report>
                XML
                """);
        assertTrue(wrapper.toFile().setExecutable(true, false));
        ProjectFileService files = new ProjectFileService();
        CoverageReportService reports = new CoverageReportService();
        JavaProjectService projects = new JavaProjectService(files, reports);
        CoverageRefreshService gradle = new CoverageRefreshService(
                projects, reports, new ProcessExecutor(), Duration.ofSeconds(10),
                Map.of("XDG_CACHE_HOME", root.resolve("cache").toString())
        );
        JavaProjectService.JavaClassDescriptor target = new JavaProjectService.JavaClassDescriptor(
                root, module, source, "com.example", "Client", "com.example.Client"
        );

        CoverageReportService.CoverageSnapshot snapshot = gradle.refresh(root, java.util.List.of(target));

        assertEquals(100.0d, snapshot.classCoverage("com.example.Client").orElseThrow().lineCoverage());
        assertTrue(snapshot.reportPathForClass("com.example.Client").startsWith(module));
        assertFalse(Files.exists(root.resolve(".gradle/jaipilot")));
    }

    private void writeWrapper(String script) throws Exception {
        Path wrapper = root.resolve("mvnw");
        Files.writeString(wrapper, script);
        assertTrue(wrapper.toFile().setExecutable(true, false));
    }
}
