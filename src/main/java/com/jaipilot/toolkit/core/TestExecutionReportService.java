package com.jaipilot.toolkit.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Proves that generated tests were discovered and executed by the clean build. */
public final class TestExecutionReportService {

    private static final Pattern TEST_COUNT_PATTERN = Pattern.compile("<testsuite\\b[^>]*\\btests=\"(\\d+)\"");
    static final int MAX_REPORT_BYTES = 1_048_576;

    private final JavaProjectService projectService;

    public TestExecutionReportService(JavaProjectService projectService) {
        this.projectService = projectService;
    }

    public List<String> findMissingReports(Path projectRoot, List<Path> expectedTestPaths) {
        Map<Path, List<String>> reportNamesByModule = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (Path testPath : expectedTestPaths.stream().distinct().sorted().toList()) {
            if (!Files.isRegularFile(testPath)) {
                missing.add(testPath.toString());
                continue;
            }
            JavaProjectService.JavaTestDescriptor descriptor = projectService.describeTestClass(testPath, projectRoot);
            String expectedReport = "TEST-" + descriptor.fullyQualifiedName() + ".xml";
            List<String> moduleReports = reportNamesByModule.computeIfAbsent(
                    descriptor.moduleRoot(),
                    this::findExecutedReportNames
            );
            if (!moduleReports.contains(expectedReport)) {
                missing.add(descriptor.fullyQualifiedName());
            }
        }
        return List.copyOf(missing);
    }

    private List<String> findExecutedReportNames(Path moduleRoot) {
        List<Path> reportRoots = List.of(
                moduleRoot.resolve("target/surefire-reports"),
                moduleRoot.resolve("target/failsafe-reports"),
                moduleRoot.resolve("build/test-results")
        );
        List<String> reportNames = new ArrayList<>();
        for (Path reportRoot : reportRoots) {
            if (!Files.isDirectory(reportRoot)) {
                continue;
            }
            try (var paths = Files.walk(reportRoot)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.getFileName().toString();
                            return name.startsWith("TEST-") && name.endsWith(".xml");
                        })
                        .filter(this::containsExecutedTests)
                        .map(path -> path.getFileName().toString())
                        .forEach(reportNames::add);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to inspect test results under " + reportRoot, exception);
            }
        }
        return reportNames.stream().distinct().toList();
    }

    private boolean containsExecutedTests(Path reportPath) {
        try {
            if (Files.size(reportPath) > MAX_REPORT_BYTES) {
                throw new IllegalStateException("Test execution report exceeds " + MAX_REPORT_BYTES
                        + " bytes: " + reportPath);
            }
            Matcher matcher = TEST_COUNT_PATTERN.matcher(Files.readString(reportPath));
            return matcher.find() && Long.parseLong(matcher.group(1)) > 0L;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to inspect test count in " + reportPath, exception);
        }
    }
}
