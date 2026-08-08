package com.jaipilot.toolkit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaipilot.toolkit.core.ArchitectureService;
import com.jaipilot.toolkit.core.CoverageRefreshService;
import com.jaipilot.toolkit.core.CoverageReportService;
import com.jaipilot.toolkit.core.DiffVerificationService;
import com.jaipilot.toolkit.core.GitChangeService;
import com.jaipilot.toolkit.core.JavaProjectService;
import com.jaipilot.toolkit.core.JavaQualityService;
import com.jaipilot.toolkit.core.OpenRewriteCleanupService;
import com.jaipilot.toolkit.core.OwnerPermissions;
import com.jaipilot.toolkit.core.ProjectFileService;
import com.jaipilot.toolkit.core.RepositoryMetadataService;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Small deterministic facade over repository inspection, quality, rewrite, and proof.
 *
 * <p>The coding agent owns editing, retries, cancellation, and Git workflow. JAIPilot stores only
 * a fingerprint-bound proof receipt; it does not persist an orchestration state machine.</p>
 */
final class ToolkitRunStore {

    private static final int RECEIPT_SCHEMA = 1;
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
    private final Path root;
    private final Path locks;
    private final Path proofs;
    private final ProjectFileService files;
    private final CoverageReportService coverageReports;
    private final JavaProjectService projects;
    private final Consumer<String> proofProgress;

    ToolkitRunStore(ObjectMapper mapper, Path root) {
        this(mapper, root, ignored -> { });
    }

    ToolkitRunStore(ObjectMapper mapper, Path root, Consumer<String> proofProgress) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.root = root.toAbsolutePath().normalize();
        this.locks = this.root.resolve("locks");
        this.proofs = this.root.resolve("proofs");
        this.files = new ProjectFileService();
        this.coverageReports = new CoverageReportService();
        this.projects = new JavaProjectService(files, coverageReports);
        this.proofProgress = Objects.requireNonNull(proofProgress, "proofProgress");
        createPrivateDirectory(this.root);
        createPrivateDirectory(locks);
        createPrivateDirectory(proofs);
    }

    static Path defaultRoot() {
        String explicit = System.getenv("JAIPILOT_STATE_HOME");
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit);
        }
        String xdg = System.getenv("XDG_STATE_HOME");
        if (xdg != null && !xdg.isBlank()) {
            return Path.of(xdg).resolve("jaipilot");
        }
        return Path.of(System.getProperty("user.home"), ".local", "state", "jaipilot");
    }

    Inspection inspect(Path requestedRoot) {
        Path projectRoot = resolveProjectRoot(requestedRoot);
        List<JavaProjectService.JavaClassDescriptor> production = projects.findProductionClasses(projectRoot);
        List<String> changed = changedTargets(projectRoot);
        Optional<CoverageReportService.CoverageSnapshot> coverage = coverageReports.readProjectSnapshot(projectRoot);
        return new Inspection(
                projectRoot,
                projects.detectBuildTool(projectRoot).name().toLowerCase(Locale.ROOT),
                projects.resolveBuildWrapper(projectRoot).isPresent(),
                projects.supportsCoverage(projectRoot),
                production.size(),
                changed,
                coverage.map(CoverageReportService.CoverageSnapshot::totalLineCoverage).orElse(null),
                coverage.map(snapshot -> snapshot.reportPath().toString()).orElse(null)
        );
    }

    QualityInspection quality(Path requestedRoot, TargetSelection selection) {
        Path projectRoot = resolveProjectRoot(requestedRoot);
        List<JavaProjectService.JavaClassDescriptor> targets = selectTargets(projectRoot, selection);
        JavaQualityService.QualityReport quality = new JavaQualityService().analyze(
                projectRoot,
                targets.stream().map(JavaProjectService.JavaClassDescriptor::cutPath).toList()
        );
        return new QualityInspection(
                projectRoot,
                targets.stream().map(TargetInfo::from).toList(),
                quality
        );
    }

    Optional<QualityInspection> currentQuality(Path requestedRoot) {
        Path projectRoot = resolveProjectRoot(requestedRoot);
        if (projects.findProductionClasses(projectRoot).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(quality(projectRoot, TargetSelection.all()));
    }

    OpenRewriteCleanupService.RewriteResult rewrite(Path requestedRoot, TargetSelection selection) {
        Path projectRoot = resolveProjectRoot(requestedRoot);
        List<Path> targets = selectTargets(projectRoot, selection).stream()
                .map(JavaProjectService.JavaClassDescriptor::cutPath)
                .toList();
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("No production Java files match the requested cleanup scope.");
        }
        return new OpenRewriteCleanupService(projects).clean(projectRoot, targets);
    }

    DiffGateStatus diffGate(
            Path requestedRoot,
            DiffVerificationService.VerificationThresholds requiredThresholds
    ) {
        Path projectRoot = resolveProjectRoot(requestedRoot);
        GitChangeService.DiffSnapshot diff = new GitChangeService().snapshot(projectRoot);
        if (diff.proofRelevantPaths().isEmpty()) {
            return gateStatus("not_applicable", diff, requiredThresholds, null,
                    "No changed Java or build inputs require proof.");
        }
        ProofReceipt receipt = readProof(projectRoot);
        boolean current = receipt != null
                && Objects.equals(receipt.fingerprint(), diff.fingerprint())
                && meets(receipt.thresholds(), requiredThresholds);
        return gateStatus(
                current ? "passed" : "review_required",
                diff,
                requiredThresholds,
                current ? receipt.verifiedAt() : null,
                current
                        ? "The exact current Java/build diff has fresh JAIPilot proof."
                        : "Run the deterministic proof command for the current fingerprint."
        );
    }

    DiffVerificationService.DiffVerification proveDiff(
            Path requestedRoot,
            DiffVerificationService.VerificationThresholds thresholds
    ) {
        Path projectRoot = resolveProjectRoot(requestedRoot);
        Path lockPath = locks.resolve("proof-" + repositoryId(projectRoot) + ".lock");
        try (FileChannel channel = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
        ); FileLock ignored = acquireProofLock(channel, projectRoot)) {
            DiffVerificationService.DiffVerification result = new DiffVerificationService(proofProgress)
                    .verify(projectRoot, thresholds);
            updateProofReceipt(projectRoot, thresholds, result);
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to lock changed-code proof for " + projectRoot + ".", exception);
        }
    }

    private FileLock acquireProofLock(FileChannel channel, Path projectRoot) throws IOException {
        try {
            FileLock lock = channel.tryLock();
            if (lock != null) {
                return lock;
            }
        } catch (OverlappingFileLockException exception) {
            throw proofAlreadyRunning(projectRoot, exception);
        }
        throw new IllegalStateException("Another JAIPilot proof is already running for " + projectRoot + ".");
    }

    private IllegalStateException proofAlreadyRunning(Path projectRoot, RuntimeException cause) {
        return new IllegalStateException("Another JAIPilot proof is already running for " + projectRoot + ".", cause);
    }

    private void updateProofReceipt(
            Path projectRoot,
            DiffVerificationService.VerificationThresholds thresholds,
            DiffVerificationService.DiffVerification verification
    ) {
        if (!verification.passed() || verification.diff().proofRelevantPaths().isEmpty()) {
            deleteProofIfCurrent(projectRoot, verification.diff().fingerprint());
            return;
        }
        writeAtomically(proofPath(projectRoot), new ProofReceipt(
                RECEIPT_SCHEMA,
                projectRoot.toString(),
                verification.diff().fingerprint(),
                Instant.now().toString(),
                thresholds,
                verification.targets().size(),
                verification.architecture() == null ? null : verification.architecture().violations().size(),
                verification.architecture() == null ? null : ArchitectureService.RULESET_VERSION
        ));
    }

    private DiffGateStatus gateStatus(
            String status,
            GitChangeService.DiffSnapshot diff,
            DiffVerificationService.VerificationThresholds thresholds,
            String verifiedAt,
            String message
    ) {
        return new DiffGateStatus(
                status,
                diff.projectRoot(),
                diff.baselineCommit(),
                diff.baselineDescription(),
                diff.headCommit(),
                diff.fingerprint(),
                diff.changedJavaPaths(),
                diff.changedProductionPaths(),
                diff.proofRelevantPaths(),
                thresholds,
                verifiedAt,
                message
        );
    }

    private List<JavaProjectService.JavaClassDescriptor> selectTargets(
            Path projectRoot,
            TargetSelection selection
    ) {
        Objects.requireNonNull(selection, "selection");
        return switch (selection.mode()) {
            case ALL -> projects.findProductionClasses(projectRoot);
            case CHANGED -> projects.findChangedProductionClasses(projectRoot);
            case CLASSES -> distinctTargets(selection.classes().stream()
                    .map(selector -> projects.resolveClass(projectRoot, selector))
                    .toList());
            case COVERAGE -> {
                CoverageReportService.CoverageSnapshot snapshot = new CoverageRefreshService(projects, coverageReports)
                        .refresh(projectRoot);
                yield projects.findClassesBelowCoverage(projectRoot, selection.coverageThreshold(), snapshot);
            }
        };
    }

    private List<JavaProjectService.JavaClassDescriptor> distinctTargets(
            List<JavaProjectService.JavaClassDescriptor> targets
    ) {
        Map<Path, JavaProjectService.JavaClassDescriptor> unique = new LinkedHashMap<>();
        for (JavaProjectService.JavaClassDescriptor target : targets) {
            unique.putIfAbsent(target.cutPath().toAbsolutePath().normalize(), target);
        }
        return List.copyOf(unique.values());
    }

    private List<String> changedTargets(Path projectRoot) {
        try {
            return projects.findChangedProductionClasses(projectRoot).stream()
                    .map(JavaProjectService.JavaClassDescriptor::fullyQualifiedName)
                    .toList();
        } catch (GitChangeService.NotGitWorktreeException exception) {
            return List.of();
        }
    }

    private Path resolveProjectRoot(Path requestedRoot) {
        Path detectedRoot = new RepositoryMetadataService().resolve(requestedRoot).projectRoot();
        Path projectRoot = projects.resolveProjectRoot(detectedRoot);
        try {
            return projectRoot.toRealPath();
        } catch (IOException exception) {
            throw new IllegalStateException("Project directory is unavailable: " + projectRoot, exception);
        }
    }

    private boolean meets(
            DiffVerificationService.VerificationThresholds actual,
            DiffVerificationService.VerificationThresholds required
    ) {
        return actual.minimumLineCoverage() >= required.minimumLineCoverage()
                && actual.minimumBranchCoverage() >= required.minimumBranchCoverage()
                && actual.minimumMutationScore() >= required.minimumMutationScore()
                && actual.minimumQualityScore() >= required.minimumQualityScore();
    }

    private ProofReceipt readProof(Path projectRoot) {
        Path path = proofPath(projectRoot);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            ProofReceipt receipt = mapper.readValue(path.toFile(), ProofReceipt.class);
            return validReceipt(projectRoot, receipt) ? receipt : null;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private boolean validReceipt(Path projectRoot, ProofReceipt receipt) {
        if (receipt.schemaVersion() != RECEIPT_SCHEMA
                || !projectRoot.toString().equals(receipt.projectRoot())
                || receipt.fingerprint() == null
                || !receipt.fingerprint().matches("[0-9a-f]{64}")
                || receipt.thresholds() == null
                || !validArchitectureReceipt(receipt)) {
            return false;
        }
        try {
            Instant.parse(receipt.verifiedAt());
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean validArchitectureReceipt(ProofReceipt receipt) {
        if (receipt.targetCount() == 0) {
            return receipt.architectureViolationCount() == null
                    && receipt.architectureRulesetVersion() == null;
        }
        return receipt.architectureViolationCount() != null
                && receipt.architectureViolationCount() == 0
                && receipt.architectureRulesetVersion() != null
                && receipt.architectureRulesetVersion() == ArchitectureService.RULESET_VERSION;
    }

    private void deleteProofIfCurrent(Path projectRoot, String fingerprint) {
        ProofReceipt receipt = readProof(projectRoot);
        if (receipt == null || !Objects.equals(receipt.fingerprint(), fingerprint)) {
            return;
        }
        try {
            Files.deleteIfExists(proofPath(projectRoot));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to invalidate changed-code proof.", exception);
        }
    }

    private void writeAtomically(Path destination, Object value) {
        try {
            Path temporary = Files.createTempFile(destination.getParent(), ".jaipilot-", ".tmp");
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
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
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist JAIPilot state at " + destination, exception);
        }
    }

    private void createPrivateDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            OwnerPermissions.set(directory, OWNER_DIRECTORY);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create JAIPilot state directory " + directory, exception);
        }
    }

    private Path proofPath(Path projectRoot) {
        return proofs.resolve(repositoryId(projectRoot) + ".json");
    }

    static String repositoryId(Path projectRoot) {
        return sha256(projectRoot.toAbsolutePath().normalize().toString());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            ));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    enum TargetMode {
        CLASSES,
        CHANGED,
        ALL,
        COVERAGE
    }

    record TargetSelection(TargetMode mode, List<String> classes, double coverageThreshold) {

        TargetSelection {
            Objects.requireNonNull(mode, "mode");
            classes = classes == null ? List.of() : classes.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .toList();
            if (mode == TargetMode.CLASSES && classes.isEmpty()) {
                throw new IllegalArgumentException("Classes mode requires at least one --class selector.");
            }
            if (!Double.isFinite(coverageThreshold) || coverageThreshold < 0.0d || coverageThreshold > 100.0d) {
                throw new IllegalArgumentException("Coverage threshold must be between 0 and 100.");
            }
        }

        static TargetSelection all() {
            return new TargetSelection(TargetMode.ALL, List.of(), 80.0d);
        }
    }

    record Inspection(
            Path projectRoot,
            String buildTool,
            boolean wrapperAvailable,
            boolean jacocoConfigured,
            int productionClassCount,
            List<String> changedProductionClasses,
            Double cachedLineCoverage,
            String cachedCoverageReport
    ) {
    }

    record QualityInspection(
            Path projectRoot,
            List<TargetInfo> targets,
            JavaQualityService.QualityReport quality
    ) {
    }

    record TargetInfo(String className, String fullyQualifiedName, Path sourcePath, Path moduleRoot) {
        static TargetInfo from(JavaProjectService.JavaClassDescriptor descriptor) {
            return new TargetInfo(
                    descriptor.className(),
                    descriptor.fullyQualifiedName(),
                    descriptor.cutPath(),
                    descriptor.moduleRoot()
            );
        }
    }

    record DiffGateStatus(
            String status,
            Path projectRoot,
            String baselineCommit,
            String baselineDescription,
            String headCommit,
            String fingerprint,
            List<Path> changedJavaPaths,
            List<Path> changedProductionPaths,
            List<Path> proofRelevantPaths,
            DiffVerificationService.VerificationThresholds requiredThresholds,
            String verifiedAt,
            String message
    ) {
    }

    private record ProofReceipt(
            int schemaVersion,
            String projectRoot,
            String fingerprint,
            String verifiedAt,
            DiffVerificationService.VerificationThresholds thresholds,
            int targetCount,
            Integer architectureViolationCount,
            Integer architectureRulesetVersion
    ) {
    }
}
