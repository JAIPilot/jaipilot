package com.jaipilot.toolkit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestExecutionReportServiceTest {

    @TempDir
    Path root;

    private TestExecutionReportService service;
    private Path testPath;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project/>\n");
        testPath = root.resolve("src/test/java/com/example/OrderServiceTest.java");
        Files.createDirectories(testPath.getParent());
        Files.writeString(testPath, "package com.example; class OrderServiceTest {}\n");
        ProjectFileService files = new ProjectFileService();
        service = new TestExecutionReportService(new JavaProjectService(files, new CoverageReportService()));
    }

    @Test
    void acceptsSurefireReportWithExecutedTests() throws Exception {
        writeReport("target/surefire-reports", "<testsuite tests=\"2\"/>\n");

        assertTrue(service.findMissingReports(root, List.of(testPath)).isEmpty());
    }

    @Test
    void acceptsNestedGradleTestReport() throws Exception {
        writeReport("build/test-results/test", "<testsuite tests=\"1\"/>\n");

        assertTrue(service.findMissingReports(root, List.of(testPath)).isEmpty());
    }

    @Test
    void rejectsZeroTestAndMissingReports() throws Exception {
        writeReport("target/surefire-reports", "<testsuite tests=\"0\"/>\n");

        assertEquals(List.of("com.example.OrderServiceTest"), service.findMissingReports(root, List.of(testPath)));
        assertEquals(List.of(root.resolve("missing.java").toString()),
                service.findMissingReports(root, List.of(root.resolve("missing.java"))));
    }

    @Test
    void rejectsUnreasonablyLargeExecutionReports() throws Exception {
        writeReport(
                "target/surefire-reports",
                "<testsuite tests=\"1\">" + " ".repeat(TestExecutionReportService.MAX_REPORT_BYTES) + "</testsuite>"
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.findMissingReports(root, List.of(testPath))
        );

        assertTrue(failure.getMessage().contains("exceeds"));
    }

    private void writeReport(String directory, String contents) throws Exception {
        Path report = root.resolve(directory).resolve("TEST-com.example.OrderServiceTest.xml");
        Files.createDirectories(report.getParent());
        Files.writeString(report, contents);
    }
}
