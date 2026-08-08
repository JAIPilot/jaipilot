package com.jaipilot.toolkit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaipilot.toolkit.core.ArchitectureService;
import com.jaipilot.toolkit.core.DiffVerificationService;
import com.jaipilot.toolkit.core.JavaQualityService;
import com.jaipilot.toolkit.core.OwnerPermissions;
import com.jaipilot.toolkit.core.RepositoryMetadataService;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Owner-private, bounded latest-state storage for the local machine dashboard. */
final class RepositorySnapshotStore {

    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_REPOSITORIES = 64;
    private static final int MAX_FINDINGS = 100;
    private static final int MAX_MESSAGES = 20;
    private static final int MAX_TEXT = 500;
    private static final Set<PosixFilePermission> OWNER_DIRECTORY = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
    );
    private static final Set<PosixFilePermission> OWNER_FILE = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
    );

    private final ObjectMapper mapper;
    private final Path repositories;
    private final Path lockPath;
    private final RepositoryMetadataService metadataService;

    RepositorySnapshotStore(ObjectMapper mapper, Path stateRoot) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        Path root = stateRoot.toAbsolutePath().normalize();
        this.repositories = root.resolve("repositories");
        this.lockPath = root.resolve("repositories.lock");
        this.metadataService = new RepositoryMetadataService();
        createPrivateDirectory(root);
        createPrivateDirectory(repositories);
    }

    RepositoryState register(Path requestedRoot) {
        RepositoryMetadataService.RepositoryMetadata metadata = metadataService.resolve(requestedRoot);
        return withLock(() -> {
            RepositoryState current = readById(ToolkitRunStore.repositoryId(metadata.projectRoot()));
            RepositoryState registered = current == null
                    ? initialState(metadata)
                    : current.withMetadata(metadata, Instant.now().toString());
            if (!registered.equals(current)) {
                write(registered);
                prune();
            }
            return registered;
        });
    }

    RepositoryState recordAnalysis(
            Path requestedRoot,
            ToolkitRunStore.QualityInspection inspection,
            ToolkitRunStore.DiffGateStatus gate
    ) {
        RepositoryMetadataService.RepositoryMetadata metadata = metadataService.resolve(requestedRoot);
        return withLock(() -> {
            RepositoryState previous = readById(ToolkitRunStore.repositoryId(metadata.projectRoot()));
            String capturedAt = Instant.now().toString();
            QualityView quality = qualityView(inspection.quality(), capturedAt, gate);
            ImpactView impact = impact(previous == null ? null : previous.quality(), quality);
            RepositoryState updated = new RepositoryState(
                    SCHEMA_VERSION,
                    ToolkitRunStore.repositoryId(metadata.projectRoot()),
                    metadata.projectRoot().toString(),
                    metadata.displayName(),
                    metadata.githubUrl(),
                    "ready",
                    capturedAt,
                    quality,
                    previous == null ? null : previous.proof(),
                    impact,
                    null
            );
            write(updated);
            prune();
            return updated;
        });
    }

    RepositoryState recordProof(
            Path requestedRoot,
            DiffVerificationService.DiffVerification verification,
            ToolkitRunStore.DiffGateStatus gate
    ) {
        RepositoryMetadataService.RepositoryMetadata metadata = metadataService.resolve(requestedRoot);
        return withLock(() -> {
            RepositoryState previous = readById(ToolkitRunStore.repositoryId(metadata.projectRoot()));
            RepositoryState base = previous == null ? initialState(metadata) : previous;
            RepositoryState updated = base.withProof(proofView(verification, gate), Instant.now().toString());
            write(updated);
            prune();
            return updated;
        });
    }

    RepositoryState recordFailure(Path requestedRoot, String message) {
        RepositoryMetadataService.RepositoryMetadata metadata = metadataService.resolve(requestedRoot);
        return withLock(() -> {
            RepositoryState previous = readById(ToolkitRunStore.repositoryId(metadata.projectRoot()));
            RepositoryState base = previous == null ? initialState(metadata) : previous;
            RepositoryState failed = base.withFailure(text(message), Instant.now().toString());
            write(failed);
            prune();
            return failed;
        });
    }

    DashboardView view(String requestedRepositoryId) {
        List<RepositoryState> states = readAll();
        RepositoryState selected;
        if (requestedRepositoryId == null || requestedRepositoryId.isBlank()) {
            selected = states.isEmpty() ? null : states.get(0);
        } else {
            selected = states.stream()
                    .filter(state -> state.id().equals(requestedRepositoryId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown repository id."));
        }
        List<RepositorySummary> summaries = states.stream().map(RepositorySummary::from).toList();
        return new DashboardView(implementationVersion(), summaries, selected);
    }

    private RepositoryState initialState(RepositoryMetadataService.RepositoryMetadata metadata) {
        return new RepositoryState(
                SCHEMA_VERSION,
                ToolkitRunStore.repositoryId(metadata.projectRoot()),
                metadata.projectRoot().toString(),
                metadata.displayName(),
                metadata.githubUrl(),
                "initializing",
                Instant.now().toString(),
                null,
                null,
                ImpactView.empty(),
                null
        );
    }

    private QualityView qualityView(
            JavaQualityService.QualityReport report,
            String capturedAt,
            ToolkitRunStore.DiffGateStatus gate
    ) {
        List<FindingView> findings = report.findings().stream()
                .sorted(Comparator
                        .comparingInt((JavaQualityService.Finding finding) -> severityRank(finding.severity()))
                        .thenComparing(JavaQualityService.Finding::relativePath)
                        .thenComparingInt(JavaQualityService.Finding::line)
                        .thenComparing(JavaQualityService.Finding::id))
                .limit(MAX_FINDINGS)
                .map(FindingView::from)
                .toList();
        return new QualityView(
                capturedAt,
                gate == null ? null : gate.headCommit(),
                gate == null ? null : gate.fingerprint(),
                gate == null ? "not_applicable" : gate.status(),
                report.metrics(),
                findings,
                report.findings().size(),
                report.parseFailures().size()
        );
    }

    private ProofView proofView(
            DiffVerificationService.DiffVerification verification,
            ToolkitRunStore.DiffGateStatus gate
    ) {
        ArchitectureService.ArchitectureReport architecture = verification.architecture();
        return new ProofView(
                verification.passed(),
                Instant.now().toString(),
                gate.headCommit(),
                verification.diff().fingerprint(),
                verification.targets().size(),
                averageCoverage(verification, true),
                averageCoverage(verification, false),
                verification.mutation() == null ? null : verification.mutation().mutationScore(),
                verification.testQuality() == null ? null : verification.testQuality().score(),
                architecture == null ? null : architecture.complete(),
                architecture == null ? null : architecture.violations().size(),
                architecture == null ? null : architecture.rulesetVersion(),
                architecture == null ? List.of() : architecture.violations().stream()
                        .limit(MAX_MESSAGES)
                        .map(violation -> text(violation.message()))
                        .toList(),
                messages(verification.failures()),
                messages(verification.warnings())
        );
    }

    private Double averageCoverage(DiffVerificationService.DiffVerification verification, boolean line) {
        if (verification.changedCoverage().isEmpty()) {
            return null;
        }
        return verification.changedCoverage().values().stream()
                .mapToDouble(value -> coverageValue(value, line))
                .average()
                .orElse(0.0d);
    }

    private double coverageValue(DiffVerificationService.ChangedCodeCoverage coverage, boolean line) {
        Double value = line ? coverage.lineCoverage() : coverage.branchCoverage();
        return value == null ? 0.0d : value;
    }

    private ImpactView impact(QualityView before, QualityView after) {
        if (before == null || before.metrics() == null || after.metrics() == null) {
            return ImpactView.empty();
        }
        return new ImpactView(
                after.metrics().qualityScore() - before.metrics().qualityScore(),
                before.totalFindings() - after.totalFindings(),
                before.metrics().remediationDebtMinutes() - after.metrics().remediationDebtMinutes()
        );
    }

    private List<String> messages(List<String> values) {
        return values.stream().limit(MAX_MESSAGES).map(RepositorySnapshotStore::text).toList();
    }

    private int severityRank(JavaQualityService.Severity severity) {
        return switch (severity) {
            case CRITICAL -> 0;
            case HIGH -> 1;
            case MEDIUM -> 2;
            case LOW -> 3;
        };
    }

    private List<RepositoryState> readAll() {
        try (var paths = Files.list(repositories)) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::read)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(RepositoryState::updatedAt).reversed())
                    .limit(MAX_REPOSITORIES)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read local repository snapshots.", exception);
        }
    }

    private RepositoryState readById(String id) {
        return read(repositories.resolve(id + ".json"));
    }

    private RepositoryState read(Path path) {
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
            return null;
        }
        try {
            RepositoryState state = mapper.readValue(path.toFile(), RepositoryState.class);
            return valid(state, path) ? state : null;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private boolean valid(RepositoryState state, Path path) {
        return state.schemaVersion() == SCHEMA_VERSION
                && state.id() != null
                && state.id().matches("[0-9a-f]{64}")
                && path.getFileName().toString().equals(state.id() + ".json")
                && state.projectRoot() != null
                && state.displayName() != null
                && state.updatedAt() != null;
    }

    private void write(RepositoryState state) throws IOException {
        Path destination = repositories.resolve(state.id() + ".json");
        Path temporary = Files.createTempFile(repositories, ".snapshot-", ".tmp");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), state);
            OwnerPermissions.set(temporary, OWNER_FILE);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void prune() throws IOException {
        List<Path> snapshots;
        try (var paths = Files.list(repositories)) {
            snapshots = paths
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(this::lastModified).reversed())
                    .toList();
        }
        for (int index = MAX_REPOSITORIES; index < snapshots.size(); index++) {
            Files.deleteIfExists(snapshots.get(index));
        }
    }

    private Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException exception) {
            return Instant.EPOCH;
        }
    }

    private <T> T withLock(IoOperation<T> operation) {
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            OwnerPermissions.set(lockPath, OWNER_FILE);
            return operation.run();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to update local repository snapshots.", exception);
        }
    }

    private void createPrivateDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            OwnerPermissions.set(directory, OWNER_DIRECTORY);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create local state directory " + directory, exception);
        }
    }

    private static String text(String value) {
        if (value == null) {
            return "";
        }
        String clean = value.replaceAll("[\\r\\n\\t]+", " ").strip();
        return clean.length() <= MAX_TEXT ? clean : clean.substring(0, MAX_TEXT);
    }

    private String implementationVersion() {
        String version = JaiPilotToolkit.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "development" : version;
    }

    @FunctionalInterface
    private interface IoOperation<T> {
        T run() throws IOException;
    }

    record DashboardView(String version, List<RepositorySummary> repositories, RepositoryState selectedRepository) {
    }

    record RepositorySummary(
            String id,
            String displayName,
            String githubUrl,
            String analysisStatus,
            String updatedAt,
            Double qualityScore,
            int findings,
            String gateStatus
    ) {
        static RepositorySummary from(RepositoryState state) {
            return new RepositorySummary(
                    state.id(),
                    state.displayName(),
                    state.githubUrl(),
                    state.analysisStatus(),
                    state.updatedAt(),
                    state.quality() == null || state.quality().metrics() == null
                            ? null : state.quality().metrics().qualityScore(),
                    state.quality() == null ? 0 : state.quality().totalFindings(),
                    state.quality() == null ? "not_run" : state.quality().gateStatus()
            );
        }
    }

    record RepositoryState(
            int schemaVersion,
            String id,
            String projectRoot,
            String displayName,
            String githubUrl,
            String analysisStatus,
            String updatedAt,
            QualityView quality,
            ProofView proof,
            ImpactView impact,
            String error
    ) {
        RepositoryState withMetadata(RepositoryMetadataService.RepositoryMetadata metadata, String at) {
            if (projectRoot.equals(metadata.projectRoot().toString())
                    && displayName.equals(metadata.displayName())
                    && Objects.equals(githubUrl, metadata.githubUrl())) {
                return this;
            }
            return new RepositoryState(schemaVersion, id, metadata.projectRoot().toString(), metadata.displayName(),
                    metadata.githubUrl(), analysisStatus, at, quality, proof, impact, error);
        }

        RepositoryState withProof(ProofView value, String at) {
            return new RepositoryState(schemaVersion, id, projectRoot, displayName, githubUrl,
                    analysisStatus, at, quality, value, impact, error);
        }

        RepositoryState withFailure(String message, String at) {
            return new RepositoryState(schemaVersion, id, projectRoot, displayName, githubUrl,
                    "failed", at, quality, proof, impact, message);
        }
    }

    record QualityView(
            String capturedAt,
            String revision,
            String fingerprint,
            String gateStatus,
            JavaQualityService.QualityMetrics metrics,
            List<FindingView> findings,
            int totalFindings,
            int parseFailures
    ) {
    }

    record FindingView(
            String id,
            String category,
            String severity,
            String relativePath,
            int line,
            String symbol,
            String message,
            String remediation
    ) {
        static FindingView from(JavaQualityService.Finding finding) {
            return new FindingView(
                    text(finding.id()),
                    finding.category().name(),
                    finding.severity().name(),
                    text(finding.relativePath()),
                    finding.line(),
                    text(finding.symbol()),
                    text(finding.message()),
                    text(finding.remediation())
            );
        }
    }

    record ProofView(
            boolean passed,
            String verifiedAt,
            String revision,
            String fingerprint,
            int targetCount,
            Double lineCoverage,
            Double branchCoverage,
            Double mutationScore,
            Double testQualityScore,
            Boolean architectureComplete,
            Integer architectureViolations,
            Integer architectureRulesetVersion,
            List<String> architectureMessages,
            List<String> failures,
            List<String> warnings
    ) {
    }

    record ImpactView(double qualityScoreChange, int findingsResolved, int debtMinutesRemoved) {
        static ImpactView empty() {
            return new ImpactView(0.0d, 0, 0);
        }
    }
}
