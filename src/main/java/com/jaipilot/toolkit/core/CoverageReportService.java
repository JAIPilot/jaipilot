package com.jaipilot.toolkit.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.SAXException;

public final class CoverageReportService {

    private static final Set<String> SCAN_EXCLUDED_DIRECTORY_NAMES = Set.of(
            ".git",
            ".gradle",
            ".idea",
            ".vscode",
            ".scannerwork",
            "node_modules",
            "out"
    );

    public Optional<Path> findCoverageReport(Path projectRoot) {
        return findCoverageReports(projectRoot).stream().findFirst();
    }

    public List<Path> findCoverageReports(Path projectRoot) {
        List<Path> matches = new ArrayList<>();
        try {
            Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    String name = directory.getFileName() == null ? "" : directory.getFileName().toString();
                    if (!directory.equals(projectRoot) && SCAN_EXCLUDED_DIRECTORY_NAMES.contains(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile()
                            && isCoverageReportFile(file)
                            && isKnownCoverageReportLocation(file)) {
                        matches.add(file.normalize());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan coverage reports under " + projectRoot, exception);
        }
        return matches.stream()
                .sorted(Comparator.comparingInt(path -> projectRoot.relativize(path).getNameCount()))
                .toList();
    }

    public Optional<CoverageSnapshot> readProjectSnapshot(Path projectRoot) {
        List<Path> reports = findCoverageReports(projectRoot);
        if (reports.isEmpty()) {
            return Optional.empty();
        }
        if (reports.size() == 1) {
            return Optional.of(readReportSnapshot(reports.get(0)));
        }
        List<Path> aggregateReports = reports.stream()
                .filter(this::isAggregateCoverageReport)
                .toList();
        if (aggregateReports.size() == 1) {
            return Optional.of(readReportSnapshot(aggregateReports.get(0)));
        }
        throw new IllegalStateException(
                "Multiple JaCoCo XML reports were found. Configure one aggregate report so coverage is not assigned "
                        + "to the wrong module: " + reports
        );
    }

    public CoverageSnapshot readReportSnapshot(Path reportPath) {
        Document document = parse(reportPath);
        Map<String, ClassCoverage> coverageByClass = new HashMap<>();
        NodeList packageElements = document.getElementsByTagName("package");
        for (int packageIndex = 0; packageIndex < packageElements.getLength(); packageIndex++) {
            Element packageElement = (Element) packageElements.item(packageIndex);
            for (Element classElement : childElements(packageElement, "class")) {
                String fullyQualifiedName = classElement.getAttribute("name").replace('/', '.');
                double lineCoverage = readCoverage(classElement, "LINE", 100.0d);
                double branchCoverage = readCoverage(classElement, "BRANCH");
                coverageByClass.put(fullyQualifiedName, new ClassCoverage(fullyQualifiedName, lineCoverage, branchCoverage));
            }
        }
        return new CoverageSnapshot(
                reportPath,
                readCoverage(document.getDocumentElement(), "LINE"),
                readCoverage(document.getDocumentElement(), "BRANCH"),
                Map.copyOf(coverageByClass),
                coverageByClass.keySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                        className -> className,
                        ignored -> reportPath
                ))
        );
    }

    /** Merges only selected module reports while retaining report provenance for each target class. */
    public CoverageSnapshot readTargetSnapshot(Path projectRoot, Map<Path, List<String>> targetsByModule) {
        if (targetsByModule == null || targetsByModule.isEmpty()) {
            throw new IllegalArgumentException("At least one module-qualified coverage target is required.");
        }
        Path root = projectRoot.toAbsolutePath().normalize();
        Map<String, ClassCoverage> coverage = new LinkedHashMap<>();
        Map<String, Path> provenance = new LinkedHashMap<>();
        LinkedHashSet<Path> reports = new LinkedHashSet<>();
        targetsByModule.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            Path module = entry.getKey().toAbsolutePath().normalize();
            if (!module.startsWith(root)) {
                throw new IllegalArgumentException("Coverage target module is outside the project: " + module);
            }
            Path report = moduleReport(module);
            CoverageSnapshot snapshot = readReportSnapshot(report);
            reports.add(report);
            for (String className : entry.getValue().stream().distinct().sorted().toList()) {
                if (provenance.containsKey(className)) {
                    throw new IllegalStateException("Coverage target class is duplicated across selected modules: "
                            + className);
                }
                ClassCoverage value = snapshot.classCoverage(className).orElseThrow(() -> new IllegalStateException(
                        "JaCoCo report " + root.relativize(report) + " for module " + root.relativize(module)
                                + " does not contain target class " + className + "."
                ));
                coverage.put(className, value);
                provenance.put(className, report);
            }
        });
        if (coverage.isEmpty()) {
            throw new IllegalArgumentException("At least one target class is required.");
        }
        return new CoverageSnapshot(
                reports.iterator().next(),
                average(coverage.values().stream().map(ClassCoverage::lineCoverage).toList()),
                average(coverage.values().stream().map(ClassCoverage::branchCoverage).toList()),
                coverage,
                provenance
        );
    }

    private Path moduleReport(Path module) {
        Path reportRoot = module.resolve("build/reports/jacoco");
        List<Path> reports = Files.isDirectory(reportRoot) ? findCoverageReports(reportRoot) : List.of();
        Path conventional = module.resolve("build/reports/jacoco/test/jacocoTestReport.xml").normalize();
        if (reports.contains(conventional)) {
            return conventional;
        }
        if (reports.size() == 1) {
            return reports.get(0);
        }
        throw new IllegalStateException(reports.isEmpty()
                ? "No JaCoCo XML report was generated for module " + module + "."
                : "Multiple JaCoCo XML reports were generated for module " + module + ": " + reports);
    }

    private double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
    }

    /** Reads executable-line and branch counters keyed by JaCoCo package/source path. */
    public Map<String, Map<Integer, LineCoverage>> readSourceLineCoverage(Path reportPath) {
        Document document = parse(reportPath);
        Map<String, Map<Integer, LineCoverage>> bySource = new HashMap<>();
        NodeList packageElements = document.getElementsByTagName("package");
        for (int packageIndex = 0; packageIndex < packageElements.getLength(); packageIndex++) {
            Element packageElement = (Element) packageElements.item(packageIndex);
            String packageName = packageElement.getAttribute("name");
            for (Element sourceElement : childElements(packageElement, "sourcefile")) {
                String key = packageName.isBlank()
                        ? sourceElement.getAttribute("name")
                        : packageName + "/" + sourceElement.getAttribute("name");
                Map<Integer, LineCoverage> lines = new HashMap<>();
                for (Element lineElement : childElements(sourceElement, "line")) {
                    int line = parseInteger(lineElement.getAttribute("nr"));
                    if (line < 1) {
                        continue;
                    }
                    lines.put(line, new LineCoverage(
                            line,
                            parseInteger(lineElement.getAttribute("mi")),
                            parseInteger(lineElement.getAttribute("ci")),
                            parseInteger(lineElement.getAttribute("mb")),
                            parseInteger(lineElement.getAttribute("cb"))
                    ));
                }
                bySource.put(key, Map.copyOf(lines));
            }
        }
        return bySource.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                Map.Entry::getValue
        ));
    }

    private Document parse(Path reportPath) {
        try (InputStream inputStream = Files.newInputStream(reportPath)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/validation", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            // JaCoCo reports commonly declare report.dtd, but coverage parsing only needs the XML payload.
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) {
                    // Warnings do not make an otherwise readable report unusable.
                }

                @Override
                public void error(SAXParseException exception) throws SAXParseException {
                    throw exception;
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXParseException {
                    throw exception;
                }
            });
            return builder.parse(inputStream);
        } catch (IOException | ParserConfigurationException | SAXException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to parse JaCoCo report " + reportPath, exception);
        }
    }

    private double readCoverage(Element parent, String counterType) {
        return readCoverage(parent, counterType, 0.0d);
    }

    private double readCoverage(Element parent, String counterType, double missingCoverage) {
        for (Element counterElement : childElements(parent, "counter")) {
            if (!counterType.equals(counterElement.getAttribute("type"))) {
                continue;
            }
            double missed = parseDouble(counterElement.getAttribute("missed"));
            double covered = parseDouble(counterElement.getAttribute("covered"));
            double total = missed + covered;
            if (total <= 0) {
                return 100.0d;
            }
            return (covered / total) * 100.0d;
        }
        return missingCoverage;
    }

    private List<Element> childElements(Element parent, String tagName) {
        List<Element> elements = new ArrayList<>();
        NodeList childNodes = parent.getChildNodes();
        for (int index = 0; index < childNodes.getLength(); index++) {
            if (childNodes.item(index) instanceof Element child && tagName.equals(child.getTagName())) {
                elements.add(child);
            }
        }
        return elements;
    }

    private double parseDouble(String value) {
        return value == null || value.isBlank() ? 0.0d : Double.parseDouble(value);
    }

    int parseInteger(String value) {
        return value == null || value.isBlank() ? 0 : Integer.parseInt(value);
    }

    private String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    private boolean isKnownCoverageReportLocation(Path path) {
        String normalized = normalize(path);
        return normalized.contains("/target/site/jacoco/")
                || normalized.contains("/target/site/jacoco-")
                || normalized.contains("/target/coverage-reports/")
                || normalized.contains("/build/reports/jacoco/");
    }

    private boolean isCoverageReportFile(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml");
    }

    private boolean isAggregateCoverageReport(Path path) {
        String normalized = normalize(path).toLowerCase(Locale.ROOT);
        return normalized.contains("/jacoco-aggregate/")
                || normalized.contains("/jacoco/aggregate/")
                || normalized.contains("testcodecoveragereport");
    }

    public record CoverageSnapshot(
            Path reportPath,
            double totalLineCoverage,
            double totalBranchCoverage,
            Map<String, ClassCoverage> classCoverageByName,
            Map<String, Path> reportPathByClass
    ) {
        public CoverageSnapshot(
                Path reportPath,
                double totalLineCoverage,
                double totalBranchCoverage,
                Map<String, ClassCoverage> classCoverageByName
        ) {
            this(reportPath, totalLineCoverage, totalBranchCoverage, classCoverageByName,
                    classCoverageByName.keySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                            className -> className, ignored -> reportPath
                    )));
        }

        public CoverageSnapshot {
            classCoverageByName = Map.copyOf(classCoverageByName);
            reportPathByClass = Map.copyOf(reportPathByClass);
        }

        public Optional<ClassCoverage> classCoverage(String fullyQualifiedName) {
            return Optional.ofNullable(classCoverageByName.get(fullyQualifiedName));
        }

        public Path reportPathForClass(String fullyQualifiedName) {
            return reportPathByClass.getOrDefault(fullyQualifiedName, reportPath);
        }
    }

    public record ClassCoverage(
            String fullyQualifiedName,
            double lineCoverage,
            double branchCoverage
    ) {
    }

    public record LineCoverage(
            int line,
            int missedInstructions,
            int coveredInstructions,
            int missedBranches,
            int coveredBranches
    ) {
        public boolean executable() {
            return missedInstructions + coveredInstructions > 0;
        }

        public boolean covered() {
            return coveredInstructions > 0;
        }

        public int branches() {
            return missedBranches + coveredBranches;
        }
    }
}
