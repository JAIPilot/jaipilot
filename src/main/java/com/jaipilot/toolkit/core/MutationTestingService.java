package com.jaipilot.toolkit.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Runs pinned, target-scoped PIT mutation testing without changing the project's build files. */
public final class MutationTestingService {

    static final String PITEST_VERSION = "1.25.9";
    static final String PITEST_JUNIT5_VERSION = "1.2.3";
    static final String GRADLE_PITEST_PLUGIN_VERSION = "1.19.0";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(20);
    private static final int MAX_THREADS = 4;
    private static final int MAX_SURVIVORS = 100;

    private final JavaProjectService projectService;
    private final ProjectFileService fileService;
    private final ProcessExecutor processExecutor;
    private final Duration timeout;

    public MutationTestingService(JavaProjectService projectService, ProjectFileService fileService) {
        this(projectService, fileService, new ProcessExecutor(), DEFAULT_TIMEOUT);
    }

    MutationTestingService(
            JavaProjectService projectService,
            ProjectFileService fileService,
            ProcessExecutor processExecutor,
            Duration timeout
    ) {
        this.projectService = Objects.requireNonNull(projectService, "projectService");
        this.fileService = Objects.requireNonNull(fileService, "fileService");
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    public MutationReport run(
            Path projectRoot,
            List<MutationTarget> targets,
            List<Path> changedTests,
            double minimumMutationScore
    ) {
        Path root = projectRoot.toAbsolutePath().normalize();
        validatePercentage(minimumMutationScore);
        List<MutationTarget> normalizedTargets = normalizeTargets(root, targets);
        if (normalizedTargets.isEmpty()) {
            throw new IllegalArgumentException("At least one mutation target is required.");
        }
        long started = System.nanoTime();
        List<Execution> executions = switch (projectService.detectBuildTool(root)) {
            case MAVEN -> runMaven(root, normalizedTargets, changedTests);
            case GRADLE -> List.of(runGradle(root, normalizedTargets, changedTests));
        };
        List<Path> reportPaths = executions.stream().flatMap(execution -> execution.reports().stream()).sorted().toList();
        MutationCounts counts = readReports(reportPaths);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        int scorable = counts.scorable();
        Double mutationScore = scorable == 0 ? null : percentage(counts.killed(), scorable);
        int covered = counts.killed() + counts.survived();
        Double testStrength = covered == 0 ? null : percentage(counts.killed(), covered);
        boolean goalMet = mutationScore == null
                ? minimumMutationScore == 0.0d
                : mutationScore + 0.000_001d >= minimumMutationScore;
        String incompleteReason = mutationScore == null
                ? "PIT completed but generated no scorable mutations for the selected classes."
                : null;
        return new MutationReport(
                PITEST_VERSION,
                true,
                minimumMutationScore,
                mutationScore,
                testStrength,
                goalMet,
                counts.total(),
                counts.killed(),
                counts.survived(),
                counts.noCoverage(),
                counts.timedOut(),
                counts.errors(),
                counts.equivalent(),
                List.copyOf(counts.survivors()),
                reportPaths.stream().map(path -> portable(root, path)).toList(),
                executions.stream().map(Execution::command).toList(),
                incompleteReason,
                elapsed.toNanos()
        );
    }

    private List<Execution> runMaven(
            Path root,
            List<MutationTarget> targets,
            List<Path> changedTests
    ) {
        Map<Path, List<MutationTarget>> byModule = new LinkedHashMap<>();
        targets.stream().sorted(MutationTarget.ORDER).forEach(target ->
                byModule.computeIfAbsent(target.moduleRoot(), ignored -> new ArrayList<>()).add(target));
        List<Execution> executions = new ArrayList<>();
        for (Map.Entry<Path, List<MutationTarget>> entry : byModule.entrySet()) {
            Path moduleRoot = entry.getKey();
            Path pom = moduleRoot.resolve("pom.xml");
            if (!Files.isRegularFile(pom)) {
                throw new IllegalStateException("PIT Maven target module has no pom.xml: " + moduleRoot);
            }
            Path reportRoot = moduleRoot.resolve("target/jaipilot-pit").normalize();
            clearReportDirectory(moduleRoot, reportRoot, "target/jaipilot-pit");
            List<String> testGlobs = testGlobs(root, moduleRoot, entry.getValue(), changedTests);
            Path temporaryPom = moduleRoot.resolve(".jaipilot-pitest-" + UUID.randomUUID() + ".xml");
            try {
                writeMavenPom(pom, temporaryPom, entry.getValue(), testGlobs, reportRoot);
                List<String> command = List.of(
                        buildExecutable(root, JavaProjectService.BuildTool.MAVEN),
                        "-B",
                        "-ntp",
                        "-f",
                        temporaryPom.toString(),
                        "test-compile",
                        "org.pitest:pitest-maven:" + PITEST_VERSION + ":mutationCoverage"
                );
                execute(command, moduleRoot);
                executions.add(new Execution(command, findReports(reportRoot)));
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to configure targeted PIT mutation testing for " + moduleRoot, exception);
            } finally {
                try {
                    Files.deleteIfExists(temporaryPom);
                } catch (IOException exception) {
                    throw new IllegalStateException("Failed to remove temporary PIT Maven configuration " + temporaryPom, exception);
                }
            }
        }
        return List.copyOf(executions);
    }

    private Execution runGradle(
            Path root,
            List<MutationTarget> targets,
            List<Path> changedTests
    ) {
        List<Path> reportRoots = gradleReportRoots(root, targets);
        reportRoots.forEach(reportRoot -> clearReportDirectory(root, reportRoot, "build/reports/pitest"));
        List<String> testGlobs = testGlobs(root, null, targets, changedTests);
        Path initScript = null;
        try {
            initScript = Files.createTempFile("jaipilot-pitest-", ".gradle");
            Files.writeString(initScript, gradleInitScript(targets, testGlobs), StandardCharsets.UTF_8);
            List<String> command = List.of(
                    buildExecutable(root, JavaProjectService.BuildTool.GRADLE),
                    "--no-daemon",
                    "--init-script",
                    initScript.toAbsolutePath().normalize().toString(),
                    "pitest"
            );
            execute(command, root);
            List<Path> reports = reportRoots.stream().flatMap(path -> findReports(path).stream()).sorted().toList();
            return new Execution(command, reports);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to configure targeted PIT mutation testing for Gradle.", exception);
        } finally {
            if (initScript != null) {
                try {
                    Files.deleteIfExists(initScript);
                } catch (IOException exception) {
                    throw new IllegalStateException("Failed to remove temporary PIT Gradle configuration.", exception);
                }
            }
        }
    }

    private void execute(List<String> command, Path workingDirectory) {
        try {
            ProcessExecutor.ExecutionResult result = processExecutor.execute(
                    command,
                    workingDirectory,
                    timeout,
                    false,
                    new PrintWriter(System.err, true),
                    null,
                    ProcessExecutor.ProgressListener.noOp(),
                    ProcessExecutor.OutputListener.noOp(),
                    CoverageRefreshService.buildToolCacheEnvironment(workingDirectory, System.getenv())
            );
            if (result.timedOut()) {
                throw new IllegalStateException("Targeted PIT mutation testing timed out after "
                        + timeout.toMinutes() + " minutes.");
            }
            if (result.exitCode() != 0) {
                throw new IllegalStateException("Targeted PIT mutation testing failed with exit code "
                        + result.exitCode() + "." + System.lineSeparator() + tail(result.output()));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Targeted PIT mutation testing was interrupted.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start targeted PIT mutation testing.", exception);
        }
    }

    void writeMavenPom(
            Path sourcePom,
            Path temporaryPom,
            List<MutationTarget> targets,
            List<String> testGlobs,
            Path reportRoot
    ) throws IOException {
        try {
            DocumentBuilderFactory factory = secureDocumentBuilderFactory();
            Document document = factory.newDocumentBuilder().parse(sourcePom.toFile());
            Element project = document.getDocumentElement();
            String namespace = project.getNamespaceURI();
            Element build = child(document, project, namespace, "build");
            Element plugins = child(document, build, namespace, "plugins");
            Element plugin = findPlugin(plugins, "org.pitest", "pitest-maven");
            if (plugin == null) {
                plugin = element(document, namespace, "plugin");
                plugins.appendChild(plugin);
            } else {
                removeChildren(plugin, "configuration");
            }
            setText(document, plugin, namespace, "groupId", "org.pitest");
            setText(document, plugin, namespace, "artifactId", "pitest-maven");
            setText(document, plugin, namespace, "version", PITEST_VERSION);

            Element dependencies = child(document, plugin, namespace, "dependencies");
            removeMatchingDependency(dependencies, "org.pitest", "pitest-junit5-plugin");
            Element dependency = element(document, namespace, "dependency");
            dependencies.appendChild(dependency);
            setText(document, dependency, namespace, "groupId", "org.pitest");
            setText(document, dependency, namespace, "artifactId", "pitest-junit5-plugin");
            setText(document, dependency, namespace, "version", PITEST_JUNIT5_VERSION);

            Element configuration = element(document, namespace, "configuration");
            plugin.appendChild(configuration);
            appendList(document, configuration, namespace, "targetClasses", "param",
                    targets.stream().map(target -> target.className() + "*").distinct().sorted().toList());
            if (!testGlobs.isEmpty()) {
                appendList(document, configuration, namespace, "targetTests", "param", testGlobs);
            }
            appendList(document, configuration, namespace, "outputFormats", "param", List.of("XML"));
            setText(document, configuration, namespace, "timestampedReports", "false");
            setText(document, configuration, namespace, "reportsDirectory", reportRoot.toString());
            setText(document, configuration, namespace, "threads", Integer.toString(threadCount()));
            setText(document, configuration, namespace, "mutationThreshold", "0");
            setText(document, configuration, namespace, "thresholdPrecision", "1");
            setText(document, configuration, namespace, "failWhenNoMutations", "false");
            setText(document, configuration, namespace, "verbose", "false");

            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.transform(new DOMSource(document), new StreamResult(temporaryPom.toFile()));
        } catch (Exception exception) {
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to create temporary PIT Maven model.", exception);
        }
    }

    String gradleInitScript(List<MutationTarget> targets, List<String> testGlobs) {
        String targetList = groovyList(targets.stream().map(target -> target.className() + "*").distinct().sorted().toList());
        String testList = groovyList(testGlobs);
        return """
                initscript {
                    repositories {
                        maven { url = uri('https://plugins.gradle.org/m2') }
                        mavenCentral()
                    }
                    dependencies {
                        classpath('info.solidsoft.gradle.pitest:gradle-pitest-plugin:%s')
                    }
                }

                allprojects {
                    afterEvaluate { candidate ->
                        if (candidate.plugins.hasPlugin('java') || candidate.plugins.hasPlugin('java-library')) {
                            candidate.plugins.apply(info.solidsoft.gradle.pitest.PitestPlugin)
                            candidate.pitest {
                                pitestVersion.set('%s')
                                junit5PluginVersion.set('%s')
                                targetClasses.set(%s as Set)
                                %s
                                threads.set(%d)
                                outputFormats.set(['XML'] as Set)
                                timestampedReports.set(false)
                                mutationThreshold.set(0)
                                thresholdPrecision.set(1)
                                failWhenNoMutations.set(false)
                                verbosity.set('QUIET')
                            }
                        }
                    }
                }
                """.formatted(
                GRADLE_PITEST_PLUGIN_VERSION,
                PITEST_VERSION,
                PITEST_JUNIT5_VERSION,
                targetList,
                testGlobs.isEmpty() ? "" : "targetTests.set(" + testList + " as Set)",
                threadCount()
        );
    }

    private List<String> testGlobs(
            Path root,
            Path selectedModule,
            List<MutationTarget> targets,
            List<Path> changedTests
    ) {
        LinkedHashSet<String> tests = new LinkedHashSet<>();
        targets.stream().flatMap(target -> target.likelyTests().stream()).forEach(test -> tests.add(test + "*"));
        for (Path changedTest : changedTests) {
            Path normalized = changedTest.toAbsolutePath().normalize();
            if (!normalized.startsWith(root) || !Files.isRegularFile(normalized)) {
                continue;
            }
            JavaProjectService.JavaTestDescriptor descriptor = projectService.describeTestClass(normalized, root);
            if (selectedModule == null || descriptor.moduleRoot().toAbsolutePath().normalize().equals(selectedModule)) {
                tests.add(descriptor.fullyQualifiedName() + "*");
            }
        }
        return tests.stream().sorted().toList();
    }

    private List<Path> gradleReportRoots(Path root, List<MutationTarget> targets) {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        roots.add(root.resolve("build/reports/pitest").normalize());
        targets.stream().map(MutationTarget::moduleRoot).distinct()
                .map(module -> module.resolve("build/reports/pitest").normalize()).forEach(roots::add);
        return List.copyOf(roots);
    }

    private void clearReportDirectory(Path boundary, Path reportRoot, String expectedSuffix) {
        Path normalizedBoundary = boundary.toAbsolutePath().normalize();
        Path normalizedReport = reportRoot.toAbsolutePath().normalize();
        String portable = normalizedReport.toString().replace('\\', '/');
        if (!normalizedReport.startsWith(normalizedBoundary)
                || !portable.endsWith(expectedSuffix.replace('\\', '/'))
                || Files.isSymbolicLink(normalizedReport)) {
            throw new IllegalStateException("Refusing to clear unexpected PIT report path: " + normalizedReport);
        }
        fileService.deleteRecursively(normalizedReport);
    }

    private List<Path> findReports(Path reportRoot) {
        if (!Files.isDirectory(reportRoot)) {
            return List.of();
        }
        try (var paths = Files.walk(reportRoot)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("mutations.xml"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to locate PIT XML reports under " + reportRoot, exception);
        }
    }

    MutationCounts readReports(List<Path> reports) {
        MutableMutationCounts counts = new MutableMutationCounts();
        for (Path report : reports) {
            try {
                Document document = secureDocumentBuilderFactory().newDocumentBuilder().parse(report.toFile());
                NodeList mutations = document.getElementsByTagName("mutation");
                for (int index = 0; index < mutations.getLength(); index++) {
                    Element mutation = (Element) mutations.item(index);
                    String status = mutation.getAttribute("status").toUpperCase(Locale.ROOT);
                    if (status.isBlank()) {
                        status = text(mutation, "status").toUpperCase(Locale.ROOT);
                    }
                    counts.add(status, survivor(mutation, status));
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to parse PIT mutation report " + report, exception);
            }
        }
        return counts.freeze();
    }

    private SurvivingMutation survivor(Element mutation, String status) {
        if (!"SURVIVED".equals(status) && !"NO_COVERAGE".equals(status) && !"TIMED_OUT".equals(status)) {
            return null;
        }
        int line = parseInteger(text(mutation, "lineNumber"));
        return new SurvivingMutation(
                text(mutation, "mutatedClass"),
                text(mutation, "mutatedMethod"),
                line,
                text(mutation, "mutator"),
                text(mutation, "description"),
                status
        );
    }

    private DocumentBuilderFactory secureDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private Element child(Document document, Element parent, String namespace, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getLocalName() == null ? element.getNodeName() : element.getLocalName())) {
                return element;
            }
        }
        Element created = element(document, namespace, name);
        parent.appendChild(created);
        return created;
    }

    private Element element(Document document, String namespace, String name) {
        return namespace == null || namespace.isBlank()
                ? document.createElement(name)
                : document.createElementNS(namespace, name);
    }

    private Element findPlugin(Element plugins, String groupId, String artifactId) {
        for (Node node = plugins.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Element plugin) || !"plugin".equals(localName(plugin))) {
                continue;
            }
            if (groupId.equals(directText(plugin, "groupId")) && artifactId.equals(directText(plugin, "artifactId"))) {
                return plugin;
            }
        }
        return null;
    }

    private void removeMatchingDependency(Element dependencies, String groupId, String artifactId) {
        List<Node> remove = new ArrayList<>();
        for (Node node = dependencies.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element dependency
                    && "dependency".equals(localName(dependency))
                    && groupId.equals(directText(dependency, "groupId"))
                    && artifactId.equals(directText(dependency, "artifactId"))) {
                remove.add(node);
            }
        }
        remove.forEach(dependencies::removeChild);
    }

    private void removeChildren(Element parent, String name) {
        List<Node> remove = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(localName(element))) {
                remove.add(node);
            }
        }
        remove.forEach(parent::removeChild);
    }

    private void setText(Document document, Element parent, String namespace, String name, String value) {
        Element element = null;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child && name.equals(localName(child))) {
                element = child;
                break;
            }
        }
        if (element == null) {
            element = element(document, namespace, name);
            parent.appendChild(element);
        }
        element.setTextContent(value);
    }

    private void appendList(
            Document document,
            Element parent,
            String namespace,
            String containerName,
            String itemName,
            List<String> values
    ) {
        Element container = element(document, namespace, containerName);
        parent.appendChild(container);
        for (String value : values) {
            Element item = element(document, namespace, itemName);
            item.setTextContent(value);
            container.appendChild(item);
        }
    }

    private String directText(Element parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(localName(element))) {
                return element.getTextContent().strip();
            }
        }
        return "";
    }

    private String localName(Element element) {
        return element.getLocalName() == null ? element.getNodeName() : element.getLocalName();
    }

    private String text(Element parent, String name) {
        NodeList nodes = parent.getElementsByTagName(name);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().strip();
    }

    private int parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private List<MutationTarget> normalizeTargets(Path root, List<MutationTarget> targets) {
        Objects.requireNonNull(targets, "targets");
        return targets.stream().filter(Objects::nonNull).map(target -> {
            Path module = target.moduleRoot().toAbsolutePath().normalize();
            if (!module.startsWith(root) || !Files.isDirectory(module) || Files.isSymbolicLink(module)) {
                throw new IllegalArgumentException("Mutation target module is outside the project: " + module);
            }
            if (target.className() == null || target.className().isBlank()) {
                throw new IllegalArgumentException("Mutation target class name is required.");
            }
            return new MutationTarget(module, target.className(), target.likelyTests());
        }).distinct().sorted(MutationTarget.ORDER).toList();
    }

    private String buildExecutable(Path root, JavaProjectService.BuildTool tool) {
        return projectService.resolveBuildWrapper(root)
                .map(command -> command.startsWith("./") ? root.resolve(command.substring(2)).toString() : command)
                .orElseGet(() -> tool == JavaProjectService.BuildTool.MAVEN ? "mvn" : "gradle");
    }

    private int threadCount() {
        return Math.max(1, Math.min(MAX_THREADS, Runtime.getRuntime().availableProcessors()));
    }

    private String groovyList(List<String> values) {
        return values.stream().map(value -> "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'")
                .reduce((left, right) -> left + ", " + right).map(value -> "[" + value + "]").orElse("[]");
    }

    private String tail(String output) {
        List<String> lines = output == null ? List.of() : output.lines()
                .map(String::stripTrailing).filter(line -> !line.isBlank()).toList();
        int start = Math.max(0, lines.size() - 50);
        return String.join(System.lineSeparator(), lines.subList(start, lines.size()));
    }

    private void validatePercentage(double value) {
        if (!Double.isFinite(value) || value < 0.0d || value > 100.0d) {
            throw new IllegalArgumentException("minimumMutationScore must be between 0 and 100.");
        }
    }

    private double percentage(int numerator, int denominator) {
        return Math.round((1000.0d * numerator / denominator)) / 10.0d;
    }

    private String portable(Path root, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        Path relative = normalized.startsWith(root) ? root.relativize(normalized) : normalized;
        return relative.toString().replace('\\', '/');
    }

    public record MutationTarget(Path moduleRoot, String className, List<String> likelyTests) {
        private static final Comparator<MutationTarget> ORDER = Comparator
                .comparing(MutationTarget::moduleRoot)
                .thenComparing(MutationTarget::className);

        public MutationTarget {
            likelyTests = likelyTests == null ? List.of() : likelyTests.stream()
                    .filter(Objects::nonNull).map(String::trim).filter(value -> !value.isBlank()).distinct().sorted().toList();
        }
    }

    public record SurvivingMutation(
            String mutatedClass,
            String mutatedMethod,
            int line,
            String mutator,
            String description,
            String status
    ) {
    }

    public record MutationReport(
            String pitestVersion,
            boolean executed,
            double minimumMutationScore,
            Double mutationScore,
            Double testStrength,
            boolean goalMet,
            int totalMutations,
            int killed,
            int survived,
            int noCoverage,
            int timedOut,
            int errors,
            int equivalent,
            List<SurvivingMutation> survivingMutations,
            List<String> reportPaths,
            List<List<String>> commands,
            String incompleteReason,
            long elapsedNanos
    ) {
    }

    private record Execution(List<String> command, List<Path> reports) {
    }

    record MutationCounts(
            int total,
            int killed,
            int survived,
            int noCoverage,
            int timedOut,
            int errors,
            int equivalent,
            List<SurvivingMutation> survivors
    ) {
        int scorable() {
            return Math.max(0, total - equivalent);
        }
    }

    private static final class MutableMutationCounts {
        private int total;
        private int killed;
        private int survived;
        private int noCoverage;
        private int timedOut;
        private int errors;
        private int equivalent;
        private final List<SurvivingMutation> survivors = new ArrayList<>();

        private void add(String status, SurvivingMutation survivor) {
            total++;
            switch (status) {
                case "KILLED" -> killed++;
                case "SURVIVED" -> survived++;
                case "NO_COVERAGE" -> noCoverage++;
                case "TIMED_OUT" -> timedOut++;
                case "EQUIVALENT" -> equivalent++;
                default -> errors++;
            }
            if (survivor != null && survivors.size() < MAX_SURVIVORS) {
                survivors.add(survivor);
            }
        }

        private MutationCounts freeze() {
            return new MutationCounts(
                    total, killed, survived, noCoverage, timedOut, errors, equivalent,
                    survivors.stream().sorted(Comparator
                            .comparing(SurvivingMutation::mutatedClass)
                            .thenComparingInt(SurvivingMutation::line)
                            .thenComparing(SurvivingMutation::mutator)).toList()
            );
        }
    }
}
