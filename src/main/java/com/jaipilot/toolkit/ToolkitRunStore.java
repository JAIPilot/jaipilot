package com.jaipilot.toolkit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaipilot.toolkit.core.ArchitectureService;
import com.jaipilot.toolkit.core.CoverageReportService;
import com.jaipilot.toolkit.core.DiffVerificationService;
import com.jaipilot.toolkit.core.GitChangeService;
import com.jaipilot.toolkit.core.JavaProjectService;
import com.jaipilot.toolkit.core.ProjectFileService;
import com.jaipilot.toolkit.core.WorkflowRunService;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Persists short-lived JAIPilot workflow state between agent command invocations.
 *
 * <p>Run operations use per-run file locks. Creation uses a brief registry lock so separate agents
 * cannot exceed the global limit or prepare overlapping runs for one project.</p>
 */
final class ToolkitRunStore {

    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
    );

    private final ObjectMapper mapper;
    private final Path root;
    private final Path runs;
    private final Path reservations;
    private final Path locks;
    private final Path proofs;
    private final Path registryLock;
    private final Consumer<String> proofProgress;

    ToolkitRunStore(ObjectMapper mapper, Path root) {
        this(mapper, root, ignored -> { });
    }

    ToolkitRunStore(ObjectMapper mapper, Path root, Consumer<String> proofProgress) {
        this.mapper = mapper;
        this.root = root.toAbsolutePath().normalize();
        this.runs = this.root.resolve("runs");
        this.reservations = this.root.resolve("reservations");
        this.locks = this.root.resolve("locks");
        this.proofs = this.root.resolve("proofs");
        this.registryLock = this.root.resolve("registry.lock");
        this.proofProgress = Objects.requireNonNull(proofProgress, "proofProgress");
        createPrivateDirectories();
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

    WorkflowRunService.ProjectInspection inspect(Path requestedRoot) {
        pruneExpired();
        WorkflowRunService service = new WorkflowRunService();
        WorkflowRunService.ProjectInspection inspection = service.inspect(requestedRoot);
        String activeRun = findActiveRun(inspection.projectRoot());
        return new WorkflowRunService.ProjectInspection(
                inspection.projectRoot(),
                inspection.buildTool(),
                inspection.wrapperAvailable(),
                inspection.jacocoConfigured(),
                inspection.productionClassCount(),
                inspection.changedProductionClasses(),
                inspection.cachedLineCoverage(),
                inspection.cachedCoverageReport(),
                activeRun
        );
    }

    WorkflowRunService.QualityInspection quality(
            Path requestedRoot,
            WorkflowRunService.TargetSelection selection
    ) {
        pruneExpired();
        return new WorkflowRunService().inspectQuality(requestedRoot, selection);
    }

    Optional<WorkflowRunService.QualityInspection> currentQuality(Path requestedRoot) {
        pruneExpired();
        return new WorkflowRunService().inspectQualityIfPresent(
                requestedRoot,
                WorkflowRunService.TargetSelection.all()
        );
    }

    DiffGateStatus diffGate(
            Path requestedRoot,
            DiffVerificationService.VerificationThresholds requiredThresholds
    ) {
        Path projectRoot = resolveProjectRoot(requestedRoot);
        GitChangeService.DiffSnapshot diff = new GitChangeService().snapshot(projectRoot);
        if (!diff.hasProductionChanges()) {
            return new DiffGateStatus(
                    "not_applicable",
                    projectRoot,
                    diff.baselineCommit(),
                    diff.baselineDescription(),
                    diff.headCommit(),
                    diff.fingerprint(),
                    diff.changedJavaPaths(),
                    diff.changedProductionPaths(),
                    requiredThresholds,
                    null,
                    "No changed Java production files require review."
            );
        }
        ProofReceipt receipt = readProof(projectRoot);
        boolean current = receipt != null
                && receipt.fingerprint().equals(diff.fingerprint())
                && meets(receipt.thresholds(), requiredThresholds);
        return new DiffGateStatus(
                current ? "passed" : "review_required",
                projectRoot,
                diff.baselineCommit(),
                diff.baselineDescription(),
                diff.headCommit(),
                diff.fingerprint(),
                diff.changedJavaPaths(),
                diff.changedProductionPaths(),
                requiredThresholds,
                current ? receipt.verifiedAt() : null,
                current
                        ? "The exact current Java diff has fresh JAIPilot proof."
                        : "The current Java diff has not passed the required quality, coverage, and mutation gates."
        );
    }

    DiffVerificationService.DiffVerification proveDiff(
            Path requestedRoot,
            DiffVerificationService.VerificationThresholds thresholds
    ) {
        Path projectRoot = resolveProjectRoot(requestedRoot);
        Path lockPath = locks.resolve("proof-" + sha256(projectRoot.toString()) + ".lock");
        try (FileChannel channel = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
        )) {
            try (FileLock lock = acquireProofLock(channel, projectRoot)) {
                DiffVerificationService.DiffVerification verification = new DiffVerificationService(proofProgress)
                        .verify(projectRoot, thresholds);
                updateProofReceipt(projectRoot, thresholds, verification);
                return verification;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to lock changed-code proof for " + projectRoot + ".", exception);
        }
    }

    private FileLock acquireProofLock(FileChannel channel, Path projectRoot) throws IOException {
        FileLock lock = tryProofLock(channel);
        if (lock != null) {
            return lock;
        }
        throw new IllegalStateException(
                "Another JAIPilot changed-code proof is already running for " + projectRoot + "."
        );
    }

    private FileLock tryProofLock(FileChannel channel) throws IOException {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException exception) {
            return null;
        }
    }

    private void updateProofReceipt(
            Path projectRoot,
            DiffVerificationService.VerificationThresholds thresholds,
            DiffVerificationService.DiffVerification verification
    ) {
        if (!verification.passed() || !verification.diff().hasProductionChanges()) {
            deleteProofIfCurrent(projectRoot, verification.diff().fingerprint());
            return;
        }
        writeAtomically(proofPath(projectRoot), new ProofReceipt(
                2,
                projectRoot.toString(),
                verification.diff().fingerprint(),
                Instant.now().toString(),
                thresholds,
                verification.targets().size(),
                verification.changedQuality() == null ? null : verification.changedQuality().score(),
                verification.testQuality() == null ? null : verification.testQuality().score(),
                verification.architecture() == null ? 0 : verification.architecture().violations().size(),
                ArchitectureService.RULESET_VERSION
        ));
    }

    WorkflowRunService.PreparedRun prepareTests(
            Path projectRoot,
            WorkflowRunService.TargetSelection selection,
            double minimumLineCoverage,
            double minimumMutationScore
    ) {
        return prepare(projectRoot, service -> service.prepareTestGeneration(
                projectRoot,
                selection,
                minimumLineCoverage,
                minimumMutationScore
        ));
    }

    WorkflowRunService.PreparedRun prepareCleanup(
            Path projectRoot,
            WorkflowRunService.TargetSelection selection
    ) {
        return prepare(projectRoot, service -> service.prepareCodeCleanup(projectRoot, selection));
    }

    WorkflowRunService.RunStatusView status(String runId) {
        return withRun(runId, service -> service.getRun(runId), true);
    }

    WorkflowRunService.ValidationResult validate(String runId) {
        return withRun(runId, service -> service.validate(runId), true);
    }

    WorkflowRunService.AppliedRun apply(String runId) {
        return withRun(runId, service -> service.apply(runId), false);
    }

    void discard(String runId) {
        withRun(runId, service -> {
            service.discard(runId);
            return null;
        }, false);
    }

    private WorkflowRunService.PreparedRun prepare(Path requestedRoot, PrepareOperation operation) {
        WorkflowRunService inspectionService = new WorkflowRunService();
        Path projectRoot = inspectionService.inspect(requestedRoot).projectRoot();
        String reservationId = reserve(projectRoot);
        WorkflowRunService service = new WorkflowRunService();
        try {
            WorkflowRunService.PreparedRun prepared = operation.prepare(service);
            WorkflowRunService.StoredRunState state = service.exportRun(prepared.runId());
            withRegistry(() -> {
                requireReservation(projectRoot, reservationId);
                boolean projectBusy = readStates().stream().anyMatch(existing -> sameProject(existing, projectRoot));
                if (projectBusy) {
                    throw new IllegalStateException("Project acquired another active JAIPilot run while preparing.");
                }
                writeState(state);
                Files.deleteIfExists(reservationPath(projectRoot));
                return null;
            });
            return prepared;
        } catch (RuntimeException exception) {
            service.close();
            clearReservation(projectRoot, reservationId);
            throw exception;
        }
    }

    private String reserve(Path projectRoot) {
        return withRegistry(() -> {
            pruneExpiredLocked();
            List<WorkflowRunService.StoredRunState> active = readStates();
            boolean projectBusy = active.stream().anyMatch(state -> sameProject(state, projectRoot));
            Path reservation = reservationPath(projectRoot);
            if (projectBusy || Files.exists(reservation)) {
                String runId = active.stream()
                        .filter(state -> sameProject(state, projectRoot))
                        .map(WorkflowRunService.StoredRunState::runId)
                        .findFirst()
                        .orElse("preparing");
                throw new IllegalStateException("Project already has active JAIPilot run " + runId + ".");
            }
            long activeReservations;
            try (var stream = Files.list(reservations)) {
                activeReservations = stream.filter(Files::isRegularFile).count();
            }
            if (active.size() + activeReservations >= WorkflowRunService.MAX_ACTIVE_RUNS) {
                throw new IllegalStateException("JAIPilot already has " + WorkflowRunService.MAX_ACTIVE_RUNS
                        + " active runs. Apply or discard one before preparing another.");
            }
            String reservationId = UUID.randomUUID().toString();
            Reservation value = new Reservation(reservationId, projectRoot.toString(), Instant.now().toString());
            writeAtomically(reservation, value);
            return reservationId;
        });
    }

    private void clearReservation(Path projectRoot, String expectedId) {
        withRegistry(() -> {
            Path reservation = reservationPath(projectRoot);
            if (Files.isRegularFile(reservation)) {
                Reservation current = mapper.readValue(reservation.toFile(), Reservation.class);
                if (expectedId.equals(current.id())) {
                    Files.deleteIfExists(reservation);
                }
            }
            return null;
        });
    }

    private void requireReservation(Path projectRoot, String expectedId) throws IOException {
        Path reservation = reservationPath(projectRoot);
        if (!Files.isRegularFile(reservation)) {
            throw new IllegalStateException("JAIPilot preparation reservation expired before the run was ready.");
        }
        Reservation current = mapper.readValue(reservation.toFile(), Reservation.class);
        if (!expectedId.equals(current.id())) {
            throw new IllegalStateException("JAIPilot preparation reservation is owned by another process.");
        }
    }

    private <T> T withRun(String runId, RunOperation<T> operation, boolean retain) {
        validateRunId(runId);
        Path lockPath = locks.resolve(runId + ".lock");
        try (FileChannel channel = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
        ); FileLock ignored = channel.lock()) {
            WorkflowRunService.StoredRunState state = readState(runId);
            WorkflowRunService service = new WorkflowRunService();
            service.restoreRun(state);
            try {
                T result = operation.run(service);
                if (retain) {
                    writeState(service.exportRun(runId));
                } else {
                    Files.deleteIfExists(statePath(runId));
                }
                return result;
            } catch (RuntimeException exception) {
                try {
                    writeState(service.exportRun(runId));
                } catch (RuntimeException ignoredExportFailure) {
                    // Preserve the operation failure; a completed apply/discard has no state to export.
                }
                throw exception;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to operate on JAIPilot run " + runId + ".", exception);
        }
    }

    private String findActiveRun(Path projectRoot) {
        return withRegistry(() -> readStates().stream()
                .filter(state -> sameProject(state, projectRoot))
                .map(WorkflowRunService.StoredRunState::runId)
                .findFirst()
                .orElse(null));
    }

    private boolean sameProject(WorkflowRunService.StoredRunState state, Path projectRoot) {
        return Path.of(state.projectRoot()).toAbsolutePath().normalize().equals(projectRoot);
    }

    private Path resolveProjectRoot(Path requestedRoot) {
        ProjectFileService fileService = new ProjectFileService();
        JavaProjectService projectService = new JavaProjectService(fileService, new CoverageReportService());
        Path root = projectService.resolveProjectRoot(requestedRoot.toAbsolutePath().normalize());
        try {
            return root.toRealPath();
        } catch (IOException exception) {
            throw new IllegalStateException("Project directory is unavailable: " + root, exception);
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
            return receipt.schemaVersion() == 2
                    && receipt.thresholds() != null
                    && receipt.architectureViolationCount() != null
                    && receipt.architectureViolationCount() == 0
                    && receipt.architectureRulesetVersion() != null
                    && receipt.architectureRulesetVersion() == ArchitectureService.RULESET_VERSION
                    ? receipt
                    : null;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private void deleteProofIfCurrent(Path projectRoot, String fingerprint) {
        ProofReceipt current = readProof(projectRoot);
        if (current == null || !current.fingerprint().equals(fingerprint)) {
            return;
        }
        try {
            Files.deleteIfExists(proofPath(projectRoot));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to invalidate stale changed-code proof.", exception);
        }
    }

    private void pruneExpired() {
        withRegistry(() -> {
            pruneExpiredLocked();
            return null;
        });
    }

    private void pruneExpiredLocked() throws IOException {
        Instant cutoff = Instant.now().minus(WorkflowRunService.RUN_TTL);
        for (WorkflowRunService.StoredRunState state : readStates()) {
            if (!Instant.parse(state.createdAt()).isBefore(cutoff)) {
                continue;
            }
            Path runLockPath = locks.resolve(state.runId() + ".lock");
            try (FileChannel channel = FileChannel.open(
                    runLockPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE
            )) {
                FileLock runLock;
                try {
                    runLock = channel.tryLock();
                } catch (OverlappingFileLockException exception) {
                    runLock = null;
                }
                if (runLock == null) {
                    continue;
                }
                try {
                    deleteExpiredWorkspace(state);
                    Files.deleteIfExists(statePath(state.runId()));
                } finally {
                    runLock.close();
                }
            }
        }

        try (var stream = Files.list(reservations)) {
            for (Path reservation : stream.filter(Files::isRegularFile).toList()) {
                Reservation value = mapper.readValue(reservation.toFile(), Reservation.class);
                if (Instant.parse(value.createdAt()).isBefore(cutoff)) {
                    Files.deleteIfExists(reservation);
                }
            }
        }
    }

    private void deleteExpiredWorkspace(WorkflowRunService.StoredRunState state) {
        Path workspace = Path.of(state.workspaceRoot()).toAbsolutePath().normalize();
        Path temporaryRoot = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        String name = workspace.getFileName() == null ? "" : workspace.getFileName().toString();
        if (!workspace.startsWith(temporaryRoot)
                || !name.startsWith("jaipilot-")
                || Files.isSymbolicLink(workspace)) {
            throw new IllegalStateException("Refusing to remove an unexpected expired workspace: " + workspace);
        }
        new ProjectFileService().deleteRecursively(workspace);
    }

    private List<WorkflowRunService.StoredRunState> readStates() throws IOException {
        try (var stream = Files.list(runs)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .map(path -> {
                        try {
                            return readStateFile(path);
                        } catch (IOException exception) {
                            throw new StateReadException(exception);
                        }
                    })
                    .toList();
        } catch (StateReadException exception) {
            throw exception.ioException();
        }
    }

    private WorkflowRunService.StoredRunState readState(String runId) {
        Path path = statePath(runId);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Unknown or completed JAIPilot run: " + runId);
        }
        try {
            return readStateFile(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read JAIPilot run state: " + runId, exception);
        }
    }

    private WorkflowRunService.StoredRunState readStateFile(Path path) throws IOException {
        WorkflowRunService.StoredRunState state = mapper.readValue(
                path.toFile(),
                WorkflowRunService.StoredRunState.class
        );
        String filename = path.getFileName().toString();
        String expectedRunId = filename.substring(0, filename.length() - ".json".length());
        try {
            validateRunId(state.runId());
        } catch (IllegalArgumentException exception) {
            throw new IOException("JAIPilot state contains an invalid runId: " + path, exception);
        }
        if (!expectedRunId.equalsIgnoreCase(state.runId())) {
            throw new IOException("JAIPilot state filename does not match its runId: " + path);
        }
        return state;
    }

    private void writeState(WorkflowRunService.StoredRunState state) {
        writeAtomically(statePath(state.runId()), state);
    }

    private void writeAtomically(Path destination, Object value) {
        try {
            Path temporary = Files.createTempFile(destination.getParent(), ".jaipilot-state-", ".tmp");
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
                try {
                    Files.move(
                            temporary,
                            destination,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING
                    );
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

    private <T> T withRegistry(IoOperation<T> operation) {
        try (FileChannel channel = FileChannel.open(
                registryLock,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
        ); FileLock ignored = channel.lock()) {
            return operation.run();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to update the JAIPilot run registry.", exception);
        }
    }

    private Path reservationPath(Path projectRoot) {
        return reservations.resolve(sha256(projectRoot.toString()) + ".json");
    }

    private Path statePath(String runId) {
        return runs.resolve(runId + ".json");
    }

    private Path proofPath(Path projectRoot) {
        return proofs.resolve(sha256(projectRoot.toString()) + ".json");
    }

    private void validateRunId(String runId) {
        if (runId == null) {
            throw new IllegalArgumentException("runId must be a UUID.");
        }
        try {
            if (!UUID.fromString(runId).toString().equalsIgnoreCase(runId)) {
                throw new IllegalArgumentException("runId must be a UUID.");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("runId must be a UUID.", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(
                    java.nio.charset.StandardCharsets.UTF_8
            )));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private void createPrivateDirectories() {
        try {
            for (Path directory : List.of(root, runs, reservations, locks, proofs)) {
                Files.createDirectories(directory);
                try {
                    Files.setPosixFilePermissions(directory, OWNER_ONLY);
                } catch (UnsupportedOperationException ignored) {
                    // Windows is not currently a release platform; retain portable filesystem behavior.
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create JAIPilot state directories under " + root, exception);
        }
    }

    @FunctionalInterface
    private interface PrepareOperation {
        WorkflowRunService.PreparedRun prepare(WorkflowRunService service);
    }

    @FunctionalInterface
    private interface RunOperation<T> {
        T run(WorkflowRunService service);
    }

    @FunctionalInterface
    private interface IoOperation<T> {
        T run() throws IOException;
    }

    private record Reservation(String id, String projectRoot, String createdAt) {
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
            Double qualityScore,
            Double testQualityScore,
            Integer architectureViolationCount,
            Integer architectureRulesetVersion
    ) {
    }

    private static final class StateReadException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final IOException ioException;

        private StateReadException(IOException ioException) {
            super(ioException);
            this.ioException = ioException;
        }

        private IOException ioException() {
            return ioException;
        }
    }
}
