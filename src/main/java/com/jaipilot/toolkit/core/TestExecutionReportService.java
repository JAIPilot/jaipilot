package com.jaipilot.toolkit.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** Proves class-level execution for changed test sources that map deterministically to test classes. */
public final class TestExecutionReportService {

    private static final Pattern TEST_CLASS = Pattern.compile(
            "(?:Test.*|.*Test|.*Tests|.*TestCase|IT.*|.*IT|.*ITCase)"
    );
    private static final Pattern TEST_SEMANTICS = Pattern.compile(
            "@(?:[A-Za-z_$][A-Za-z0-9_$.]*\\.)?(?!(?:Before|After)Test\\b)"
                    + "(?:Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate|[A-Za-z0-9_$]*Test)\\b"
                    + "|\\bextends\\s+(?:[A-Za-z_$][A-Za-z0-9_$.]*\\.)?TestCase\\b"
    );
    static final int MAX_REPORT_BYTES = 1_048_576;
    static final int MAX_REPORT_FILES = 20_000;

    private final JavaProjectService projectService;

    public TestExecutionReportService(JavaProjectService projectService) {
        this.projectService = Objects.requireNonNull(projectService, "projectService");
    }

    public ExecutionEvidence inspect(Path projectRoot, List<Path> changedTestPaths) {
        Path root = projectRoot.toAbsolutePath().normalize();
        Map<Path, List<String>> reportsByModule = new LinkedHashMap<>();
        List<String> expected = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> helpers = new ArrayList<>();
        for (Path path : changedTestPaths.stream().distinct().sorted().toList()) {
            Path testPath = path.isAbsolute() ? path.normalize() : root.resolve(path).normalize();
            if (!Files.isRegularFile(testPath) || Files.isSymbolicLink(testPath)) {
                throw new IllegalStateException("Changed test source is unavailable or unsafe: " + path);
            }
            if (!likelyExecutableTest(testPath)) {
                helpers.add(portable(root.relativize(testPath)));
                continue;
            }
            JavaProjectService.JavaTestDescriptor descriptor = projectService.describeTestClass(testPath, root);
            String className = descriptor.fullyQualifiedName();
            expected.add(className);
            List<String> moduleReports = reportsByModule.computeIfAbsent(
                    descriptor.moduleRoot(), this::findExecutedReportClasses
            );
            if (moduleReports.stream().noneMatch(report -> ownsReport(className, report))) {
                missing.add(className);
            }
        }
        return new ExecutionEvidence(List.copyOf(expected), List.copyOf(missing), List.copyOf(helpers));
    }

    /** Compatibility entry point used by focused tests and older callers. */
    public List<String> findMissingReports(Path projectRoot, List<Path> expectedTestPaths) {
        List<Path> existing = expectedTestPaths.stream().filter(Files::isRegularFile).toList();
        List<String> missing = new ArrayList<>();
        expectedTestPaths.stream().filter(path -> !Files.isRegularFile(path))
                .map(Path::toString).forEach(missing::add);
        missing.addAll(inspect(projectRoot, existing).missingClasses());
        return List.copyOf(missing);
    }

    private boolean likelyExecutableTest(Path testPath) {
        String name = testPath.getFileName().toString();
        String simpleName = name.endsWith(".java") ? name.substring(0, name.length() - 5) : name;
        if (TEST_CLASS.matcher(simpleName).matches()) {
            return true;
        }
        try {
            if (Files.size(testPath) > MAX_REPORT_BYTES) {
                throw new IllegalStateException("Changed test source is too large to classify: " + testPath);
            }
            return TEST_SEMANTICS.matcher(Files.readString(testPath)).find();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to classify changed test source " + testPath, exception);
        }
    }

    private List<String> findExecutedReportClasses(Path moduleRoot) {
        List<String> reportClasses = new ArrayList<>();
        int[] inspected = {0};
        for (Path reportRoot : List.of(
                moduleRoot.resolve("target/surefire-reports"),
                moduleRoot.resolve("target/failsafe-reports"),
                moduleRoot.resolve("build/test-results")
        )) {
            if (!Files.isDirectory(reportRoot)) {
                continue;
            }
            try (var paths = Files.walk(reportRoot)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().startsWith("TEST-"))
                        .filter(path -> path.getFileName().toString().endsWith(".xml"))
                        .sorted()
                        .forEach(path -> addExecutedReport(reportClasses, inspected, path));
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to inspect test results under " + reportRoot, exception);
            }
        }
        return reportClasses.stream().distinct().toList();
    }

    private void addExecutedReport(List<String> reportClasses, int[] inspected, Path report) {
        if (++inspected[0] > MAX_REPORT_FILES) {
            throw new IllegalStateException("Test execution report count exceeds " + MAX_REPORT_FILES + ".");
        }
        if (containsExecutedTests(report)) {
            String name = report.getFileName().toString();
            reportClasses.add(name.substring("TEST-".length(), name.length() - ".xml".length()));
        }
    }

    private boolean containsExecutedTests(Path reportPath) {
        try {
            if (Files.size(reportPath) > MAX_REPORT_BYTES) {
                throw new IllegalStateException("Test execution report exceeds " + MAX_REPORT_BYTES
                        + " bytes: " + reportPath);
            }
            DocumentBuilderFactory factory = secureXmlFactory();
            try (InputStream input = Files.newInputStream(reportPath)) {
                var builder = factory.newDocumentBuilder();
                builder.setErrorHandler(new DefaultHandler());
                return documentShowsExecution(builder.parse(input).getDocumentElement());
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (IOException | ParserConfigurationException | SAXException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to inspect test execution report " + reportPath, exception);
        }
    }

    private boolean documentShowsExecution(Element root) {
        if (root == null || !("testsuite".equals(root.getTagName()) || "testsuites".equals(root.getTagName()))) {
            return false;
        }
        if ("testsuite".equals(root.getTagName())) {
            return suiteExecuted(root);
        }
        var suites = root.getElementsByTagName("testsuite");
        for (int index = 0; index < suites.getLength(); index++) {
            if (suites.item(index) instanceof Element suite && suiteExecuted(suite)) {
                return true;
            }
        }
        return false;
    }

    private boolean suiteExecuted(Element suite) {
        int tests = count(suite, "tests");
        int skipped = count(suite, "skipped") + count(suite, "disabled");
        if (tests < 0 || skipped < 0 || skipped > tests) {
            throw new IllegalStateException("Test execution report contains contradictory suite counts.");
        }
        var cases = suite.getElementsByTagName("testcase");
        if (cases.getLength() == 0) {
            return tests > skipped;
        }
        for (int index = 0; index < cases.getLength(); index++) {
            if (cases.item(index) instanceof Element testCase && !skipped(testCase)) {
                return true;
            }
        }
        return false;
    }

    private int count(Element suite, String name) {
        String value = suite.getAttribute(name);
        if (value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Test execution report contains an invalid " + name + " count.", exception);
        }
    }

    private boolean skipped(Element testCase) {
        String status = testCase.getAttribute("status");
        if ("skipped".equalsIgnoreCase(status) || "disabled".equalsIgnoreCase(status)) {
            return true;
        }
        for (Node child = testCase.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element
                    && ("skipped".equals(element.getTagName()) || "disabled".equals(element.getTagName()))) {
                return true;
            }
        }
        return false;
    }

    private DocumentBuilderFactory secureXmlFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private boolean ownsReport(String expectedClass, String reportClass) {
        return reportClass.equals(expectedClass) || reportClass.startsWith(expectedClass + "$");
    }

    private String portable(Path path) {
        return path.toString().replace('\\', '/');
    }

    public record ExecutionEvidence(
            List<String> expectedClasses,
            List<String> missingClasses,
            List<String> helperSources
    ) {
        public boolean complete() {
            return missingClasses.isEmpty();
        }
    }
}
