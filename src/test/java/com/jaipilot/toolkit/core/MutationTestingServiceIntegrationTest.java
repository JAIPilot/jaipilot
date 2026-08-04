package com.jaipilot.toolkit.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

class MutationTestingServiceIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    @EnabledIfSystemProperty(named = "jaipilot.pit.integration", matches = "true")
    void runsPinnedPitAgainstARealJUnitFiveMavenProject() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/example/Calculator.java");
        Path test = tempDir.resolve("src/test/java/com/example/CalculatorTest.java");
        Files.createDirectories(source.getParent());
        Files.createDirectories(test.getParent());
        Path repository = Path.of("").toAbsolutePath().normalize();
        Path wrapperProperties = tempDir.resolve(".mvn/wrapper/maven-wrapper.properties");
        Files.createDirectories(wrapperProperties.getParent());
        Files.copy(repository.resolve("mvnw"), tempDir.resolve("mvnw"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(
                repository.resolve(".mvn/wrapper/maven-wrapper.properties"),
                wrapperProperties,
                StandardCopyOption.REPLACE_EXISTING
        );
        tempDir.resolve("mvnw").toFile().setExecutable(true, false);
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>pit-fixture</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <maven.compiler.release>17</maven.compiler.release>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.junit.jupiter</groupId>
                      <artifactId>junit-jupiter</artifactId>
                      <version>5.11.4</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                  <build>
                    <plugins>
                      <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-surefire-plugin</artifactId>
                        <version>3.5.2</version>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """);
        Files.writeString(source, """
                package com.example;
                public final class Calculator {
                    public int add(int left, int right) { return left + right; }
                }
                """);
        Files.writeString(test, """
                package com.example;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                import org.junit.jupiter.api.Test;
                class CalculatorTest {
                    @Test void adds() { assertEquals(7, new Calculator().add(3, 4)); }
                }
                """);

        ProjectFileService files = new ProjectFileService();
        JavaProjectService projects = new JavaProjectService(files, new CoverageReportService());
        MutationTestingService.MutationReport report = new MutationTestingService(projects, files).run(
                tempDir,
                List.of(new MutationTestingService.MutationTarget(
                        tempDir,
                        "com.example.Calculator",
                        List.of("com.example.CalculatorTest")
                )),
                List.of(test),
                0.0d
        );

        assertTrue(report.executed());
        assertTrue(report.totalMutations() > 0);
        assertTrue(report.killed() > 0);
        assertNotNull(report.mutationScore());
        assertNotNull(report.testStrength());
        assertTrue(report.goalMet());
        assertTrue(report.reportPaths().stream().allMatch(path -> path.endsWith("mutations.xml")));
    }
}
