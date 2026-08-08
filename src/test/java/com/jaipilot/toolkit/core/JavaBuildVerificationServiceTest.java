package com.jaipilot.toolkit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaBuildVerificationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void mavenVerificationUsesBatchCleanVerify() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>\n");
        JavaProjectService projectService = new JavaProjectService(
                new ProjectFileService(),
                new CoverageReportService()
        );
        JavaBuildVerificationService service = new JavaBuildVerificationService(projectService);

        assertEquals(List.of("mvn", "-B", "-Dmaven.build.cache.enabled=false", "clean", "verify"),
                service.buildCommand(tempDir));
    }

    @Test
    void gradleVerificationUsesFreshFullBuild() throws Exception {
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }\n");
        JavaProjectService projectService = new JavaProjectService(
                new ProjectFileService(),
                new CoverageReportService()
        );
        JavaBuildVerificationService service = new JavaBuildVerificationService(projectService);

        assertEquals(List.of("gradle", "--no-daemon", "--no-build-cache", "--rerun-tasks", "clean", "build"),
                service.buildCommand(tempDir));
    }
}
