package com.jaipilot.toolkit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MutationTestingServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsPinnedScopedMavenConfigurationWithoutEditingTheProjectPom() throws Exception {
        Path pom = tempDir.resolve("pom.xml");
        Path generated = tempDir.resolve("pit.xml");
        String original = """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>fixture</artifactId>
                  <version>1.0.0</version>
                </project>
                """;
        Files.writeString(pom, original);
        MutationTestingService service = service();

        service.writeMavenPom(
                pom,
                generated,
                List.of(new MutationTestingService.MutationTarget(tempDir, "com.example.OrderService", List.of())),
                List.of("com.example.OrderServiceTest*"),
                tempDir.resolve("target/jaipilot-pit")
        );

        String configured = Files.readString(generated);
        assertEquals(original, Files.readString(pom));
        assertTrue(configured.contains("<version>" + MutationTestingService.PITEST_VERSION + "</version>"));
        assertTrue(configured.contains("pitest-junit5-plugin"));
        assertTrue(configured.contains("com.example.OrderService*"));
        assertTrue(configured.contains("com.example.OrderServiceTest*"));
        assertTrue(configured.contains("<outputFormats>"));
        assertFalse(configured.contains("withHistory"));
    }

    @Test
    void parsesMutationStrengthAndSurvivorEvidence() throws Exception {
        Path report = tempDir.resolve("mutations.xml");
        Files.writeString(report, """
                <mutations>
                  <mutation detected="true" status="KILLED">
                    <mutatedClass>com.example.OrderService</mutatedClass>
                    <mutatedMethod>total</mutatedMethod>
                    <lineNumber>12</lineNumber>
                    <mutator>ConditionalsBoundaryMutator</mutator>
                    <description>changed conditional boundary</description>
                  </mutation>
                  <mutation detected="false" status="SURVIVED">
                    <mutatedClass>com.example.OrderService</mutatedClass>
                    <mutatedMethod>total</mutatedMethod>
                    <lineNumber>14</lineNumber>
                    <mutator>MathMutator</mutator>
                    <description>replaced addition with subtraction</description>
                  </mutation>
                  <mutation detected="false" status="NO_COVERAGE">
                    <mutatedClass>com.example.OrderService</mutatedClass>
                    <mutatedMethod>cancel</mutatedMethod>
                    <lineNumber>22</lineNumber>
                    <mutator>VoidMethodCallMutator</mutator>
                    <description>removed call</description>
                  </mutation>
                  <mutation detected="false" status="EQUIVALENT">
                    <mutatedClass>com.example.OrderService</mutatedClass>
                    <mutatedMethod>id</mutatedMethod>
                    <lineNumber>7</lineNumber>
                    <mutator>Equivalent</mutator>
                    <description>equivalent</description>
                  </mutation>
                  <mutation detected="true" status="NON_VIABLE">
                    <mutatedClass>com.example.OrderService</mutatedClass>
                    <mutatedMethod>id</mutatedMethod>
                    <lineNumber>7</lineNumber>
                    <mutator>BrokenMutator</mutator>
                    <description>could not load</description>
                  </mutation>
                </mutations>
                """);

        MutationTestingService.MutationCounts counts = service().readReports(List.of(report));

        assertEquals(5, counts.total());
        assertEquals(1, counts.killed());
        assertEquals(1, counts.survived());
        assertEquals(1, counts.noCoverage());
        assertEquals(1, counts.equivalent());
        assertEquals(1, counts.nonViable());
        assertEquals(3, counts.scorable());
        assertEquals(2, counts.survivors().size());
    }

    @Test
    void gradleConfigurationIsTargetedParallelAndMachineReadable() {
        MutationTestingService service = service();

        String script = service.gradleInitScript(
                List.of(new MutationTestingService.MutationTarget(tempDir, "com.example.OrderService", List.of())),
                List.of("com.example.OrderServiceTest*")
        );

        assertTrue(script.contains("pitestVersion.set('" + MutationTestingService.PITEST_VERSION + "')"));
        assertTrue(script.contains("junit5PluginVersion.set('" + MutationTestingService.PITEST_JUNIT5_VERSION + "')"));
        assertTrue(script.contains("targetClasses.set(['com.example.OrderService*'] as Set)"));
        assertTrue(script.contains("targetTests.set(['com.example.OrderServiceTest*'] as Set)"));
        assertTrue(script.contains("outputFormats.set(['XML'] as Set)"));
        assertFalse(script.contains("timestampedReports.set(true)"));
    }

    private MutationTestingService service() {
        ProjectFileService fileService = new ProjectFileService();
        CoverageReportService coverage = new CoverageReportService();
        return new MutationTestingService(
                new JavaProjectService(fileService, coverage),
                fileService,
                new ProcessExecutor(),
                Duration.ofSeconds(10)
        );
    }
}
