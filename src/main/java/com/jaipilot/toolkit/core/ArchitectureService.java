package com.jaipilot.toolkit.core;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.library.cycle_detection.Cycle;
import com.tngtech.archunit.library.cycle_detection.CycleDetector;
import com.tngtech.archunit.library.cycle_detection.Cycles;
import com.tngtech.archunit.library.cycle_detection.Edge;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Deterministic ArchUnit-backed architecture analysis over freshly compiled production bytecode. */
public final class ArchitectureService {

    public static final String ARCHUNIT_VERSION = "1.4.2";
    public static final int RULESET_VERSION = 1;
    public static final String PACKAGE_CYCLE_RULE = "JAI-ARCH-001";

    private static final String MAX_CYCLES_PROPERTY = "cycles.maxNumberToDetect";
    private static final String MAX_CYCLES = "100";
    private static final Set<String> EXCLUDED_DIRECTORY_NAMES = Set.of(
            ".git",
            ".gradle",
            ".idea",
            ".vscode",
            "node_modules"
    );

    private final JavaProjectService projectService;

    public ArchitectureService(JavaProjectService projectService) {
        this.projectService = Objects.requireNonNull(projectService, "projectService");
    }

    public ArchitectureReport analyze(Path requestedRoot, List<String> requestedTargets) {
        Path root = Objects.requireNonNull(requestedRoot, "projectRoot").toAbsolutePath().normalize();
        List<String> targets = normalizeTargets(requestedTargets);
        long started = System.nanoTime();
        List<Path> classRoots;
        try {
            classRoots = findProductionClassRoots(root);
        } catch (RuntimeException exception) {
            return incomplete(root, targets, List.of(), List.of(), failureMessage(exception), started);
        }
        if (classRoots.isEmpty()) {
            return incomplete(
                    root,
                    targets,
                    List.of(),
                    targets,
                    "No compiled production class directories were found after the clean build.",
                    started
            );
        }

        try {
            Map<String, Path> sourceByClass = sourcePaths(root);
            JavaClasses imported = ArchConfiguration.withThreadLocalScope(configuration -> {
                configuration.setResolveMissingDependenciesFromClassPath(false);
                configuration.setMd5InClassSourcesEnabled(false);
                return new ClassFileImporter().importPaths(classRoots);
            });
            Map<String, JavaClass> projectClasses = projectClasses(imported, sourceByClass.keySet());
            List<String> missingTargets = targets.stream()
                    .filter(target -> !projectClasses.containsKey(target))
                    .toList();
            List<DependencyEdge> dependencies = dependencies(projectClasses, sourceByClass, root);
            ArchitectureAnalysis analysis = ArchConfiguration.withThreadLocalScope(configuration -> {
                configuration.setProperty(MAX_CYCLES_PROPERTY, MAX_CYCLES);
                return analyzePackageCycles(dependencies, Set.copyOf(targets));
            });
            boolean complete = missingTargets.isEmpty() && !analysis.maximumCycleCountReached();
            String incompleteReason = incompleteReason(missingTargets, analysis.maximumCycleCountReached());
            return new ArchitectureReport(
                    "ArchUnit",
                    ARCHUNIT_VERSION,
                    RULESET_VERSION,
                    List.of(PACKAGE_CYCLE_RULE),
                    complete,
                    projectClasses.size(),
                    relativeRoots(root, classRoots),
                    targets,
                    missingTargets,
                    analysis.violations(),
                    incompleteReason,
                    System.nanoTime() - started
            );
        } catch (RuntimeException exception) {
            return incomplete(
                    root,
                    targets,
                    classRoots,
                    targets,
                    "ArchUnit analysis failed: " + failureMessage(exception),
                    started
            );
        }
    }

    private ArchitectureAnalysis analyzePackageCycles(
            List<DependencyEdge> dependencies,
            Set<String> targetClasses
    ) {
        Map<PackagePair, List<DependencyEdge>> dependenciesByPackage = new TreeMap<>();
        for (DependencyEdge dependency : dependencies) {
            dependenciesByPackage.computeIfAbsent(
                    new PackagePair(dependency.originPackage(), dependency.targetPackage()),
                    ignored -> new ArrayList<>()
            ).add(dependency);
        }
        dependenciesByPackage.values().forEach(values -> values.sort(DependencyEdge.ORDER));
        List<PackageEdge> packageEdges = dependenciesByPackage.keySet().stream()
                .map(pair -> new PackageEdge(pair.origin(), pair.target()))
                .toList();
        Set<String> packages = new TreeSet<>();
        packageEdges.forEach(edge -> {
            packages.add(edge.origin());
            packages.add(edge.target());
        });
        Cycles<PackageEdge> cycles = CycleDetector.detectCycles(packages, packageEdges);
        List<ArchitectureViolation> violations = cycles.stream()
                .map(cycle -> violation(cycle, dependenciesByPackage, targetClasses))
                .filter(Objects::nonNull)
                .sorted(ArchitectureViolation.ORDER)
                .toList();
        return new ArchitectureAnalysis(violations, cycles.maxNumberOfCyclesReached());
    }

    private ArchitectureViolation violation(
            Cycle<PackageEdge> cycle,
            Map<PackagePair, List<DependencyEdge>> dependenciesByPackage,
            Set<String> targetClasses
    ) {
        List<DependencyEdge> affected = cycle.getEdges().stream()
                .flatMap(edge -> dependenciesByPackage.getOrDefault(
                        new PackagePair(edge.origin(), edge.target()),
                        List.of()
                ).stream())
                .filter(dependency -> targetClasses.contains(dependency.originClass())
                        || targetClasses.contains(dependency.targetClass()))
                .sorted(DependencyEdge.ORDER)
                .toList();
        if (affected.isEmpty()) {
            return null;
        }
        DependencyEdge primary = affected.get(0);
        List<String> cyclePackages = canonicalCycle(cycle.getEdges());
        List<String> affectedTargets = affected.stream()
                .flatMap(dependency -> List.of(dependency.originClass(), dependency.targetClass()).stream())
                .filter(targetClasses::contains)
                .distinct()
                .sorted()
                .toList();
        return new ArchitectureViolation(
                PACKAGE_CYCLE_RULE,
                "HIGH",
                primary.originClass(),
                primary.targetClass(),
                primary.relativePath(),
                primary.line(),
                cyclePackages,
                affectedTargets,
                "Package cycle " + String.join(" -> ", cyclePackages) + ". " + primary.description(),
                "Break the dependency cycle by moving the shared contract inward, inverting the dependency, "
                        + "or removing the unnecessary cross-package reference."
        );
    }

    private List<DependencyEdge> dependencies(
            Map<String, JavaClass> projectClasses,
            Map<String, Path> sourceByClass,
            Path root
    ) {
        List<DependencyEdge> dependencies = new ArrayList<>();
        for (JavaClass origin : projectClasses.values()) {
            for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                String targetClassName = dependency.getTargetClass().getName();
                if (!projectClasses.containsKey(targetClassName)) {
                    continue;
                }
                String originName = topLevelClassName(origin.getName());
                String targetName = topLevelClassName(targetClassName);
                String originPackage = origin.getPackageName();
                String targetPackage = dependency.getTargetClass().getPackageName();
                if (originPackage.equals(targetPackage)) {
                    continue;
                }
                Path source = sourceByClass.get(originName);
                dependencies.add(new DependencyEdge(
                        originName,
                        targetName,
                        originPackage,
                        targetPackage,
                        source == null ? "" : portable(root.relativize(source)),
                        Math.max(1, dependency.getSourceCodeLocation().getLineNumber()),
                        dependency.getDescription()
                ));
            }
        }
        return dependencies.stream().distinct().sorted(DependencyEdge.ORDER).toList();
    }

    private Map<String, JavaClass> projectClasses(JavaClasses imported, Set<String> sourceClasses) {
        Map<String, JavaClass> classes = new TreeMap<>();
        for (JavaClass javaClass : imported) {
            String topLevel = topLevelClassName(javaClass.getName());
            if (sourceClasses.contains(topLevel)) {
                classes.putIfAbsent(javaClass.getName(), javaClass);
            }
        }
        return Map.copyOf(classes);
    }

    private Map<String, Path> sourcePaths(Path root) {
        Map<String, Path> paths = new TreeMap<>();
        projectService.findProductionClasses(root).forEach(descriptor -> paths.merge(
                descriptor.fullyQualifiedName(),
                descriptor.cutPath(),
                (left, right) -> left.compareTo(right) <= 0 ? left : right
        ));
        return Map.copyOf(paths);
    }

    private List<Path> findProductionClassRoots(Path root) {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (!directory.equals(root)
                            && (Files.isSymbolicLink(directory)
                            || EXCLUDED_DIRECTORY_NAMES.contains(directory.getFileName().toString()))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (isProductionClassRoot(root, directory)) {
                        roots.add(directory.toAbsolutePath().normalize());
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to discover compiled production classes under " + root, exception);
        }
        return roots.stream().sorted().toList();
    }

    private boolean isProductionClassRoot(Path root, Path directory) {
        if (directory.equals(root)) {
            return false;
        }
        Path relative = root.relativize(directory).normalize();
        int count = relative.getNameCount();
        if (count >= 2
                && "target".equals(relative.getName(count - 2).toString())
                && "classes".equals(relative.getName(count - 1).toString())) {
            return true;
        }
        return count >= 4
                && "build".equals(relative.getName(count - 4).toString())
                && "classes".equals(relative.getName(count - 3).toString())
                && "main".equals(relative.getName(count - 1).toString());
    }

    private ArchitectureReport incomplete(
            Path root,
            List<String> targets,
            List<Path> classRoots,
            List<String> missingTargets,
            String reason,
            long started
    ) {
        return new ArchitectureReport(
                "ArchUnit",
                ARCHUNIT_VERSION,
                RULESET_VERSION,
                List.of(PACKAGE_CYCLE_RULE),
                false,
                0,
                relativeRoots(root, classRoots),
                targets,
                List.copyOf(missingTargets),
                List.of(),
                reason,
                System.nanoTime() - started
        );
    }

    private String incompleteReason(List<String> missingTargets, boolean maximumCycleCountReached) {
        List<String> reasons = new ArrayList<>();
        if (!missingTargets.isEmpty()) {
            reasons.add("Compiled bytecode is missing for target classes " + missingTargets + ".");
        }
        if (maximumCycleCountReached) {
            reasons.add("ArchUnit reached its maximum number of package cycles; resolve the reported cycles and rerun.");
        }
        return reasons.isEmpty() ? null : String.join(" ", reasons);
    }

    private List<String> relativeRoots(Path root, Collection<Path> classRoots) {
        return classRoots.stream()
                .map(path -> portable(root.relativize(path)))
                .sorted()
                .toList();
    }

    private List<String> normalizeTargets(List<String> requestedTargets) {
        Objects.requireNonNull(requestedTargets, "targetClasses");
        return requestedTargets.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> canonicalCycle(List<PackageEdge> edges) {
        if (edges.isEmpty()) {
            return List.of();
        }
        List<String> packages = edges.stream().map(PackageEdge::origin).toList();
        List<String> best = null;
        for (int offset = 0; offset < packages.size(); offset++) {
            List<String> candidate = new ArrayList<>(packages.size());
            for (int index = 0; index < packages.size(); index++) {
                candidate.add(packages.get((offset + index) % packages.size()));
            }
            if (best == null || compare(candidate, best) < 0) {
                best = candidate;
            }
        }
        List<String> closed = new ArrayList<>(Objects.requireNonNull(best));
        closed.add(best.get(0));
        return List.copyOf(closed);
    }

    private int compare(List<String> left, List<String> right) {
        for (int index = 0; index < Math.min(left.size(), right.size()); index++) {
            int comparison = left.get(index).compareTo(right.get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private String topLevelClassName(String name) {
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
    }

    private String portable(Path path) {
        return path.normalize().toString().replace('\\', '/');
    }

    private String failureMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    public record ArchitectureReport(
            String engine,
            String engineVersion,
            int rulesetVersion,
            List<String> rules,
            boolean complete,
            int compiledClassCount,
            List<String> classOutputRoots,
            List<String> targetClasses,
            List<String> missingTargetClasses,
            List<ArchitectureViolation> violations,
            String incompleteReason,
            long elapsedNanos
    ) {
        public ArchitectureReport {
            rules = List.copyOf(rules);
            classOutputRoots = List.copyOf(classOutputRoots);
            targetClasses = List.copyOf(targetClasses);
            missingTargetClasses = List.copyOf(missingTargetClasses);
            violations = List.copyOf(violations);
        }

        public boolean goalMet() {
            return complete && violations.isEmpty();
        }
    }

    public record ArchitectureViolation(
            String id,
            String severity,
            String originClass,
            String targetClass,
            String relativePath,
            int line,
            List<String> cyclePackages,
            List<String> affectedTargets,
            String message,
            String remediation
    ) {
        private static final Comparator<ArchitectureViolation> ORDER = Comparator
                .comparing(ArchitectureViolation::id)
                .thenComparing(ArchitectureViolation::relativePath)
                .thenComparingInt(ArchitectureViolation::line)
                .thenComparing(ArchitectureViolation::originClass)
                .thenComparing(ArchitectureViolation::targetClass)
                .thenComparing(violation -> String.join("|", violation.cyclePackages()));

        public ArchitectureViolation {
            cyclePackages = List.copyOf(cyclePackages);
            affectedTargets = List.copyOf(affectedTargets);
        }
    }

    private record ArchitectureAnalysis(
            List<ArchitectureViolation> violations,
            boolean maximumCycleCountReached
    ) {
    }

    private record PackagePair(String origin, String target) implements Comparable<PackagePair> {
        @Override
        public int compareTo(PackagePair other) {
            int originComparison = origin.compareTo(other.origin);
            return originComparison != 0 ? originComparison : target.compareTo(other.target);
        }
    }

    private record PackageEdge(String origin, String target) implements Edge<String> {
        @Override
        public String getOrigin() {
            return origin;
        }

        @Override
        public String getTarget() {
            return target;
        }
    }

    private record DependencyEdge(
            String originClass,
            String targetClass,
            String originPackage,
            String targetPackage,
            String relativePath,
            int line,
            String description
    ) {
        private static final Comparator<DependencyEdge> ORDER = Comparator
                .comparing(DependencyEdge::originClass)
                .thenComparing(DependencyEdge::targetClass)
                .thenComparing(DependencyEdge::relativePath)
                .thenComparingInt(DependencyEdge::line)
                .thenComparing(DependencyEdge::description);
    }
}
