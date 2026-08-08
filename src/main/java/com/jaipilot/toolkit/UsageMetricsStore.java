package com.jaipilot.toolkit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** Persists privacy-preserving local usage and outcome metrics for the dashboard. */
final class UsageMetricsStore {

    private static final int SCHEMA_VERSION = 3;
    private static final int MAX_RECENT_ACTIVITY = 40;
    private static final int MAX_PROJECT_IDENTITIES = 10_000;
    private static final int MAX_PENDING_RUNS = 1_000;
    private static final int MAX_CURRENT_FINDINGS = 50;
    private static final int MAX_ARCHITECTURE_VIOLATIONS = 20;
    private static final int MAX_GATE_MESSAGES = 20;
    private static final int MAX_LIST_VALUES = 50;
    private static final int MAX_TEXT_LENGTH = 1_000;
    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
    );
    private static final ConcurrentHashMap<Path, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();

    private final ObjectMapper mapper;
    private final Path metricsDirectory;
    private final Path summaryPath;
    private final Path lockPath;

    UsageMetricsStore(ObjectMapper mapper, Path stateRoot) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        Path normalizedRoot = Objects.requireNonNull(stateRoot, "stateRoot").toAbsolutePath().normalize();
        this.metricsDirectory = normalizedRoot.resolve("metrics");
        this.summaryPath = metricsDirectory.resolve("summary.json");
        this.lockPath = metricsDirectory.resolve("metrics.lock");
        createPrivateDirectory();
    }

    void record(String command, int status, Object result, Duration elapsed) {
        if (command == null
                || command.isBlank()
                || "dashboard-serve".equals(command)
                || "dashboard".equals(command)
                || "version".equals(command)
                || "help".equals(command)) {
            return;
        }
        withState(state -> update(state, command, status, result, elapsed), true);
    }

    DashboardMetrics snapshot() {
        return withState(this::view, false);
    }

    private MetricsState update(
            MetricsState stored,
            String command,
            int status,
            Object result,
            Duration elapsed
    ) {
        MetricsState state = normalized(stored);
        boolean successful = status == 0;
        String now = Instant.now().toString();
        Map<String, Long> commands = new TreeMap<>(state.commands());
        commands.merge(command, 1L, Long::sum);
        Set<String> projects = new LinkedHashSet<>(state.projectIds());
        Map<String, RunEvidence> runs = new LinkedHashMap<>(state.pendingRuns());
        JsonNode json = result == null ? null : mapper.valueToTree(result);
        if ("prove-diff".equals(command) && json != null && json.path("result").isObject()) {
            json = json.path("result");
        }
        addProject(projects, json);

        MutableImpact impact = new MutableImpact(state.impact());
        MutableEvidence evidence = new MutableEvidence(state.latestEvidence());
        if (successful) {
            captureSuccessfulResult(command, json, runs, impact, evidence, now);
        } else if ("prove-diff".equals(command) && json != null) {
            captureDiffProof(json, impact, evidence, now);
        }
        boundPendingRuns(runs);

        long elapsedMillis = elapsed == null ? 0L : Math.max(0L, elapsed.toMillis());
        List<Activity> recent = new ArrayList<>();
        recent.add(new Activity(now, command, successful, activitySummary(command, successful, json), elapsedMillis));
        recent.addAll(state.recentActivity());
        if (recent.size() > MAX_RECENT_ACTIVITY) {
            recent = new ArrayList<>(recent.subList(0, MAX_RECENT_ACTIVITY));
        }
        return new MetricsState(
                SCHEMA_VERSION,
                state.firstUsedAt() == null ? now : state.firstUsedAt(),
                now,
                state.totalCommands() + 1,
                state.successfulCommands() + (successful ? 1 : 0),
                state.failedCommands() + (successful ? 0 : 1),
                state.totalCommandDurationMillis() + elapsedMillis,
                Map.copyOf(commands),
                List.copyOf(projects),
                impact.freeze(),
                evidence.freeze(),
                Map.copyOf(runs),
                List.copyOf(recent)
        );
    }

    private void captureSuccessfulResult(
            String command,
            JsonNode json,
            Map<String, RunEvidence> runs,
            MutableImpact impact,
            MutableEvidence evidence,
            String capturedAt
    ) {
        if (json == null) {
            return;
        }
        switch (command) {
            case "prepare-tests", "prepare-cleanup" -> capturePrepared(command, json, runs, impact);
            case "validate" -> captureValidation(json, runs, impact, evidence, capturedAt);
            case "apply" -> captureApply(json, runs, impact);
            case "discard" -> captureDiscard(json, runs, impact);
            case "prove-diff" -> captureDiffProof(json, impact, evidence, capturedAt);
            case "quality" -> captureQuality(
                    json.path("quality"),
                    evidence,
                    new QualitySnapshotContext(
                            size(json.path("targets")),
                            "selected_scope",
                            "Quality analysis",
                            capturedAt,
                            false,
                            null,
                            null
                    )
            );
            case "hook-post-commit" -> capturePostCommitQuality(json, evidence, capturedAt);
            default -> {
                // The remaining commands contribute usage, latency, project, and activity metrics only.
            }
        }
    }

    private void capturePrepared(
            String command,
            JsonNode json,
            Map<String, RunEvidence> runs,
            MutableImpact impact
    ) {
        String runId = text(json, "runId");
        String kind = "prepare-tests".equals(command) ? "GENERATE_TESTS" : "CLEAN_JAVA";
        int targetCount = json.path("targets").isArray() ? json.path("targets").size() : 0;
        if ("GENERATE_TESTS".equals(kind)) {
            impact.testRunsPrepared++;
        } else {
            impact.cleanupRunsPrepared++;
        }
        if (runId != null) {
            runs.put(runId, new RunEvidence(kind, targetCount, false, 0.0d, 0, 0, 0, 0, 0));
        }
    }

    private void captureValidation(
            JsonNode json,
            Map<String, RunEvidence> runs,
            MutableImpact impact,
            MutableEvidence evidence,
            String capturedAt
    ) {
        impact.validations++;
        String runId = text(json, "runId");
        boolean ready = json.path("readyToApply").asBoolean(false);
        if (ready) {
            impact.validationsReadyToApply++;
        }
        RunEvidence previous = runId == null ? null : runs.get(runId);
        double coveragePointChange = coveragePointChange(json.path("coverage"));
        JsonNode delta = json.path("qualityDelta");
        double qualityPointChange = difference(delta, "qualityScoreAfter", "qualityScoreBefore");
        int findingsResolved = integer(delta, "resolvedFindings");
        int debtMinutesRemoved = Math.max(
                0,
                integer(delta, "remediationDebtMinutesBefore") - integer(delta, "remediationDebtMinutesAfter")
        );
        JsonNode mutation = json.path("mutation");
        int mutationsKilled = integer(mutation, "killed");
        int changedTestsExecuted = integer(json.path("testQuality"), "executedChangedTestFileCount");
        evidence.qualityScore = decimal(delta, "qualityScoreAfter");
        evidence.testQualityScore = decimal(json.path("testQuality"), "score");
        evidence.mutationScore = decimal(mutation, "mutationScore");
        evidence.lineCoverage = decimal(json.path("testQuality"), "lineCoverage");
        evidence.branchCoverage = decimal(json.path("testQuality"), "branchCoverage");
        String source = previous != null && "CLEAN_JAVA".equals(previous.kind())
                ? "Cleanup validation"
                : "Test validation";
        evidence.findings = findings(json.path("quality"), source, capturedAt);
        evidence.architecture = architecture(json.path("architecture"), source, capturedAt);
        evidence.gates = gates(json, source, capturedAt, ready);
        if (runId != null) {
            runs.put(runId, new RunEvidence(
                    previous == null ? "UNKNOWN" : previous.kind(),
                    previous == null ? 0 : previous.targetCount(),
                    ready,
                    coveragePointChange,
                    findingsResolved,
                    debtMinutesRemoved,
                    mutationsKilled,
                    changedTestsExecuted,
                    qualityPointChange
            ));
        }
    }

    private void captureApply(JsonNode json, Map<String, RunEvidence> runs, MutableImpact impact) {
        impact.appliedRuns++;
        JsonNode changedPaths = json.path("changedRelativePaths");
        impact.filesChanged += changedPaths.isArray() ? changedPaths.size() : 0;
        String runId = text(json, "runId");
        RunEvidence run = runId == null ? null : runs.remove(runId);
        if (run == null || !run.readyToApply()) {
            return;
        }
        if ("GENERATE_TESTS".equals(run.kind())) {
            impact.testRunsApplied++;
        } else if ("CLEAN_JAVA".equals(run.kind())) {
            impact.cleanupRunsApplied++;
        }
        impact.targetsImproved += run.targetCount();
        impact.coveragePointsChanged += run.coveragePointChange();
        impact.findingsResolved += run.findingsResolved();
        impact.debtMinutesRemoved += run.debtMinutesRemoved();
        impact.mutationsKilled += run.mutationsKilled();
        impact.changedTestsExecuted += run.changedTestsExecuted();
        impact.qualityPointsChanged += run.qualityPointChange();
    }

    private void captureDiscard(JsonNode json, Map<String, RunEvidence> runs, MutableImpact impact) {
        impact.discardedRuns++;
        String runId = text(json, "runId");
        if (runId != null) {
            runs.remove(runId);
        }
    }

    private void captureDiffProof(
            JsonNode json,
            MutableImpact impact,
            MutableEvidence evidence,
            String capturedAt
    ) {
        impact.diffProofsRun++;
        if (json.path("passed").asBoolean(false)) {
            impact.diffProofsPassed++;
        }
        JsonNode targets = json.path("targets");
        evidence.verifiedTargetCount = targets.isArray() ? targets.size() : 0;
        evidence.qualityScore = decimal(json.path("changedQuality"), "score");
        evidence.testQualityScore = decimal(json.path("testQuality"), "score");
        evidence.mutationScore = decimal(json.path("mutation"), "mutationScore");
        evidence.lineCoverage = decimal(json.path("testQuality"), "lineCoverage");
        evidence.branchCoverage = decimal(json.path("testQuality"), "branchCoverage");
        evidence.lastProofPassed = json.path("passed").asBoolean(false);
        evidence.lastProofAt = capturedAt;
        evidence.findings = findings(json.path("quality"), "Changed-code proof", capturedAt);
        evidence.architecture = architecture(json.path("architecture"), "Changed-code proof", capturedAt);
        evidence.gates = gates(json, "Changed-code proof", capturedAt, evidence.lastProofPassed);
    }

    private void captureQuality(
            JsonNode quality,
            MutableEvidence evidence,
            QualitySnapshotContext context
    ) {
        evidence.qualityScore = decimal(quality.path("metrics"), "qualityScore");
        FindingsEvidence analyzedFindings = findings(quality, context.source(), context.capturedAt());
        if (context.currentProject()) {
            evidence.currentQuality = currentQuality(
                    quality,
                    context,
                    analyzedFindings
            );
            evidence.findings = FindingsEvidence.empty();
        } else {
            evidence.findings = analyzedFindings;
        }
    }

    private void capturePostCommitQuality(JsonNode json, MutableEvidence evidence, String capturedAt) {
        JsonNode inspection = json.path("currentQuality");
        if (!inspection.isObject()) {
            String status = text(json, "analysisStatus");
            String source = "failed".equals(status)
                    ? "Automatic post-commit analysis failed"
                    : "Automatic post-commit analysis";
            evidence.qualityScore = null;
            evidence.currentQuality = CurrentQualityEvidence.unavailable(
                    source,
                    capturedAt,
                    "whole_project",
                    status,
                    boundedText(json, "revision"),
                    boundedText(json, "fingerprint")
            );
            return;
        }
        captureQuality(
                inspection.path("quality"),
                evidence,
                new QualitySnapshotContext(
                        size(inspection.path("targets")),
                        "whole_project",
                        "Automatic post-commit analysis",
                        capturedAt,
                        true,
                        boundedText(json, "revision"),
                        boundedText(json, "fingerprint")
                )
        );
    }

    private CurrentQualityEvidence currentQuality(
            JsonNode quality,
            QualitySnapshotContext context,
            FindingsEvidence analyzedFindings
    ) {
        JsonNode metrics = quality.path("metrics");
        if (!metrics.isObject()) {
            return CurrentQualityEvidence.unavailable(
                    context.source(),
                    context.capturedAt(),
                    context.scope(),
                    "failed",
                    context.revision(),
                    context.fingerprint()
            );
        }
        return new CurrentQualityEvidence(
                context.source(),
                context.capturedAt(),
                context.scope(),
                context.revision(),
                context.fingerprint(),
                "analyzed",
                context.targetCount(),
                integer(metrics, "fileCount"),
                integer(metrics, "linesOfCode"),
                longValue(metrics, "sourceBytes"),
                integer(metrics, "methodCount"),
                integer(metrics, "findingCount"),
                integer(metrics, "bugRiskCount"),
                integer(metrics, "codeSmellCount"),
                integer(metrics, "modernizationOpportunityCount"),
                integer(metrics, "maximumCyclomaticComplexity"),
                decimal(metrics, "averageCyclomaticComplexity"),
                integer(metrics, "maximumCognitiveComplexity"),
                integer(metrics, "duplicatedLineCount"),
                decimal(metrics, "duplicationPercent"),
                integer(metrics, "remediationDebtMinutes"),
                decimal(metrics, "remediationDebtRatioPercent"),
                decimal(metrics, "reliabilityScore"),
                decimal(metrics, "maintainabilityScore"),
                decimal(metrics, "complexityScore"),
                decimal(metrics, "duplicationScore"),
                decimal(metrics, "qualityScore"),
                longValue(metrics, "analysisElapsedNanos"),
                size(quality.path("parseFailures")),
                analyzedFindings
        );
    }

    private FindingsEvidence findings(JsonNode quality, String source, String capturedAt) {
        if (quality == null || !quality.isObject()) {
            return FindingsEvidence.unavailable(source, capturedAt);
        }
        JsonNode metrics = quality.path("metrics");
        JsonNode findings = quality.path("findings");
        if (!metrics.isObject() && !findings.isArray()) {
            return FindingsEvidence.unavailable(source, capturedAt);
        }
        List<FindingEvidence> items = new ArrayList<>();
        if (findings.isArray()) {
            for (JsonNode finding : findings) {
                if (items.size() >= MAX_CURRENT_FINDINGS) {
                    break;
                }
                items.add(new FindingEvidence(
                        boundedText(finding, "id"),
                        boundedText(finding, "category"),
                        boundedText(finding, "severity"),
                        boundedText(finding, "relativePath"),
                        Math.max(0, integer(finding, "line")),
                        boundedText(finding, "symbol"),
                        boundedText(finding, "message"),
                        boundedText(finding, "remediation")
                ));
            }
        }
        JsonNode bySeverity = metrics.path("findingsBySeverity");
        return new FindingsEvidence(
                source,
                capturedAt,
                findingCount(metrics, findings),
                severityCount(bySeverity, findings, "CRITICAL"),
                severityCount(bySeverity, findings, "HIGH"),
                severityCount(bySeverity, findings, "MEDIUM"),
                severityCount(bySeverity, findings, "LOW"),
                size(quality.path("parseFailures")),
                List.copyOf(items)
        );
    }

    private int findingCount(JsonNode metrics, JsonNode findings) {
        JsonNode reported = metrics.path("findingCount");
        if (reported.isNumber()) {
            return Math.max(0, reported.asInt());
        }
        return findings.isArray() ? findings.size() : 0;
    }

    private int severityCount(JsonNode bySeverity, JsonNode findings, String severity) {
        JsonNode value = bySeverity.path(severity);
        if (value.isNumber()) {
            return Math.max(0, value.asInt());
        }
        if (!findings.isArray()) {
            return 0;
        }
        int count = 0;
        for (JsonNode finding : findings) {
            if (severity.equals(finding.path("severity").asText())) {
                count++;
            }
        }
        return count;
    }

    private ArchitectureEvidence architecture(JsonNode architecture, String source, String capturedAt) {
        if (architecture == null || !architecture.isObject()) {
            return ArchitectureEvidence.unavailable(source, capturedAt);
        }
        List<ArchitectureViolationEvidence> violations = new ArrayList<>();
        JsonNode violationNodes = architecture.path("violations");
        if (violationNodes.isArray()) {
            for (JsonNode violation : violationNodes) {
                if (violations.size() >= MAX_ARCHITECTURE_VIOLATIONS) {
                    break;
                }
                violations.add(new ArchitectureViolationEvidence(
                        boundedText(violation, "id"),
                        boundedText(violation, "severity"),
                        boundedText(violation, "originClass"),
                        boundedText(violation, "targetClass"),
                        boundedText(violation, "relativePath"),
                        Math.max(0, integer(violation, "line")),
                        textValues(violation.path("cyclePackages"), MAX_LIST_VALUES),
                        boundedText(violation, "message"),
                        boundedText(violation, "remediation")
                ));
            }
        }
        boolean complete = architecture.path("complete").asBoolean(false);
        int violationCount = violationNodes.isArray() ? violationNodes.size() : 0;
        return new ArchitectureEvidence(
                source,
                capturedAt,
                boundedText(architecture, "engine"),
                boundedText(architecture, "engineVersion"),
                architecture.path("rulesetVersion").isNumber()
                        ? Math.max(0, architecture.path("rulesetVersion").asInt())
                        : null,
                textValues(architecture.path("rules"), MAX_LIST_VALUES),
                complete,
                complete && violationCount == 0,
                violationCount,
                Math.max(0, integer(architecture, "compiledClassCount")),
                textValues(architecture.path("missingTargetClasses"), MAX_LIST_VALUES),
                boundedText(architecture, "incompleteReason"),
                List.copyOf(violations)
        );
    }

    private GateEvidence gates(
            JsonNode json,
            String source,
            String capturedAt,
            Boolean passed
    ) {
        return new GateEvidence(
                source,
                capturedAt,
                passed,
                textValues(json.path("failures"), MAX_GATE_MESSAGES),
                textValues(json.path("warnings"), MAX_GATE_MESSAGES)
        );
    }

    private String activitySummary(String command, boolean successful, JsonNode json) {
        if ("prove-diff".equals(command) && json != null && json.has("passed")) {
            return json.path("passed").asBoolean(false)
                    ? "Changed-code proof passed"
                    : "Changed-code proof found actionable gaps";
        }
        if (!successful) {
            return "Command failed without changing project files";
        }
        if (json == null) {
            return "Completed";
        }
        return switch (command) {
            case "prepare-tests" -> "Prepared " + size(json.path("targets")) + " test target(s)";
            case "prepare-cleanup" -> "Prepared " + size(json.path("targets")) + " cleanup target(s)";
            case "validate" -> json.path("readyToApply").asBoolean(false)
                    ? "Candidate passed every apply gate"
                    : "Candidate returned actionable validation gaps";
            case "apply" -> "Applied " + size(json.path("changedRelativePaths")) + " verified file(s)";
            case "discard" -> "Discarded an isolated candidate";
            case "prove-diff" -> json.path("passed").asBoolean(false)
                    ? "Changed-code proof passed"
                    : "Changed-code proof found actionable gaps";
            case "hook-stop" -> "Checked the current Java diff";
            case "hook-post-commit" -> postCommitActivity(json);
            case "quality" -> "Analyzed source quality";
            case "inspect" -> "Inspected a Java project";
            case "dashboard" -> "Opened dashboard status";
            default -> "Completed";
        };
    }

    private String postCommitActivity(JsonNode json) {
        return switch (json.path("analysisStatus").asText("")) {
            case "analyzed" -> "Refreshed current project quality after a Git commit";
            case "no_java_sources" -> "Checked a Git commit with no Java production sources";
            case "failed" -> "Post-commit project quality refresh failed";
            default -> "Checked an agent Git commit";
        };
    }

    private int size(JsonNode node) {
        return node != null && node.isArray() ? node.size() : 0;
    }

    private double coveragePointChange(JsonNode coverage) {
        if (coverage == null || !coverage.isObject()) {
            return 0.0d;
        }
        double change = 0.0d;
        var fields = coverage.fields();
        while (fields.hasNext()) {
            JsonNode value = fields.next().getValue();
            Double before = decimal(value, "beforeLineCoverage");
            Double after = decimal(value, "afterLineCoverage");
            if (before != null && after != null) {
                change += after - before;
            }
        }
        return round(change);
    }

    private double difference(JsonNode node, String afterName, String beforeName) {
        Double before = decimal(node, beforeName);
        Double after = decimal(node, afterName);
        return before == null || after == null ? 0.0d : round(after - before);
    }

    private void addProject(Set<String> projects, JsonNode json) {
        JsonNode project = json == null ? null : json.findValue("projectRoot");
        if (projects.size() < MAX_PROJECT_IDENTITIES
                && project != null
                && project.isTextual()
                && !project.textValue().isBlank()) {
            projects.add(sha256(project.textValue()));
        }
    }

    private void boundPendingRuns(Map<String, RunEvidence> runs) {
        while (runs.size() > MAX_PENDING_RUNS) {
            String remove = runs.keySet().stream().sorted().findFirst().orElse(null);
            if (remove == null) {
                return;
            }
            runs.remove(remove);
        }
    }

    private DashboardMetrics view(MetricsState stored) {
        MetricsState state = normalized(stored);
        double successRate = state.totalCommands() == 0
                ? 0.0d
                : round(100.0d * state.successfulCommands() / state.totalCommands());
        double averageDuration = state.totalCommands() == 0
                ? 0.0d
                : round((double) state.totalCommandDurationMillis() / state.totalCommands());
        List<CommandUsage> commands = state.commands().entrySet().stream()
                .map(entry -> new CommandUsage(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(CommandUsage::count).reversed()
                        .thenComparing(CommandUsage::command))
                .toList();
        Impact impact = state.impact();
        return new DashboardMetrics(
                SCHEMA_VERSION,
                Instant.now().toString(),
                new Usage(
                        state.firstUsedAt(),
                        state.lastUsedAt(),
                        state.totalCommands(),
                        state.successfulCommands(),
                        state.failedCommands(),
                        successRate,
                        averageDuration,
                        state.projectIds().size()
                ),
                new Impact(
                        impact.testRunsPrepared(),
                        impact.cleanupRunsPrepared(),
                        impact.validations(),
                        impact.validationsReadyToApply(),
                        impact.appliedRuns(),
                        impact.testRunsApplied(),
                        impact.cleanupRunsApplied(),
                        impact.discardedRuns(),
                        impact.diffProofsRun(),
                        impact.diffProofsPassed(),
                        impact.targetsImproved(),
                        round(impact.coveragePointsChanged()),
                        round(impact.qualityPointsChanged()),
                        impact.findingsResolved(),
                        impact.debtMinutesRemoved(),
                        impact.mutationsKilled(),
                        impact.changedTestsExecuted(),
                        impact.filesChanged()
                ),
                state.latestEvidence(),
                commands,
                state.recentActivity()
        );
    }

    private MetricsState normalized(MetricsState state) {
        if (state == null) {
            return emptyState();
        }
        if (state.schemaVersion() < 1 || state.schemaVersion() > SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported JAIPilot metrics schema: " + state.schemaVersion());
        }
        return new MetricsState(
                SCHEMA_VERSION,
                state.firstUsedAt(),
                state.lastUsedAt(),
                Math.max(0L, state.totalCommands()),
                Math.max(0L, state.successfulCommands()),
                Math.max(0L, state.failedCommands()),
                Math.max(0L, state.totalCommandDurationMillis()),
                state.commands() == null ? Map.of() : Map.copyOf(state.commands()),
                state.projectIds() == null ? List.of() : List.copyOf(state.projectIds()),
                state.impact() == null ? Impact.empty() : state.impact(),
                state.latestEvidence() == null ? LatestEvidence.empty() : state.latestEvidence(),
                state.pendingRuns() == null ? Map.of() : Map.copyOf(state.pendingRuns()),
                state.recentActivity() == null ? List.of() : List.copyOf(state.recentActivity())
        );
    }

    private MetricsState emptyState() {
        return new MetricsState(
                SCHEMA_VERSION,
                null,
                null,
                0L,
                0L,
                0L,
                0L,
                Map.of(),
                List.of(),
                Impact.empty(),
                LatestEvidence.empty(),
                Map.of(),
                List.of()
        );
    }

    private <T> T withState(StateOperation<T> operation, boolean recoverCorruptSummary) {
        ReentrantLock jvmLock = JVM_LOCKS.computeIfAbsent(lockPath, ignored -> new ReentrantLock());
        jvmLock.lock();
        try (FileChannel channel = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
        ); FileLock ignored = channel.lock()) {
            MetricsState state;
            try {
                state = readState();
            } catch (IOException exception) {
                if (!recoverCorruptSummary) {
                    throw exception;
                }
                preserveCorruptSummary();
                state = emptyState();
            }
            T value = operation.apply(state);
            if (value instanceof MetricsState updated) {
                writeState(updated);
            }
            return value;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to access JAIPilot usage metrics.", exception);
        } finally {
            jvmLock.unlock();
        }
    }

    private MetricsState readState() throws IOException {
        if (!Files.isRegularFile(summaryPath)) {
            return emptyState();
        }
        return mapper.readValue(summaryPath.toFile(), MetricsState.class);
    }

    private void preserveCorruptSummary() throws IOException {
        if (!Files.isRegularFile(summaryPath)) {
            return;
        }
        Path preserved = metricsDirectory.resolve(
                "summary.corrupt-" + Instant.now().toEpochMilli() + "-" + java.util.UUID.randomUUID() + ".json"
        );
        Files.move(summaryPath, preserved);
    }

    private void writeState(MetricsState state) throws IOException {
        Path temporary = Files.createTempFile(metricsDirectory, ".jaipilot-metrics-", ".tmp");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), state);
            try {
                Files.move(
                        temporary,
                        summaryPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, summaryPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void createPrivateDirectory() {
        try {
            Files.createDirectories(metricsDirectory);
            try {
                Files.setPosixFilePermissions(metricsDirectory, OWNER_ONLY);
            } catch (UnsupportedOperationException ignored) {
                // Retain portable filesystem behavior where POSIX permissions are unavailable.
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create JAIPilot metrics directory.", exception);
        }
    }

    private String text(JsonNode node, String name) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(name);
        return value.isTextual() && !value.textValue().isBlank() ? value.textValue() : null;
    }

    private String boundedText(JsonNode node, String name) {
        String value = text(node, name);
        if (value == null || value.length() <= MAX_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_TEXT_LENGTH - 1) + "…";
    }

    private List<String> textValues(JsonNode values, int maximum) {
        if (values == null || !values.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            if (result.size() >= maximum) {
                break;
            }
            if (value.isTextual() && !value.textValue().isBlank()) {
                String text = value.textValue();
                result.add(text.length() <= MAX_TEXT_LENGTH
                        ? text
                        : text.substring(0, MAX_TEXT_LENGTH - 1) + "…");
            }
        }
        return List.copyOf(result);
    }

    private int integer(JsonNode node, String name) {
        if (node == null) {
            return 0;
        }
        JsonNode value = node.path(name);
        return value.isNumber() ? value.asInt() : 0;
    }

    private Long longValue(JsonNode node, String name) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(name);
        return value.isNumber() ? Math.max(0L, value.asLong()) : null;
    }

    private Double decimal(JsonNode node, String name) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(name);
        return value.isNumber() && Double.isFinite(value.asDouble()) ? value.asDouble() : null;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            ));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private double round(double value) {
        return Math.round(value * 10.0d) / 10.0d;
    }

    @FunctionalInterface
    private interface StateOperation<T> {
        T apply(MetricsState state);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MetricsState(
            int schemaVersion,
            String firstUsedAt,
            String lastUsedAt,
            long totalCommands,
            long successfulCommands,
            long failedCommands,
            long totalCommandDurationMillis,
            Map<String, Long> commands,
            List<String> projectIds,
            Impact impact,
            LatestEvidence latestEvidence,
            Map<String, RunEvidence> pendingRuns,
            List<Activity> recentActivity
    ) {
    }

    private record RunEvidence(
            String kind,
            int targetCount,
            boolean readyToApply,
            double coveragePointChange,
            int findingsResolved,
            int debtMinutesRemoved,
            int mutationsKilled,
            int changedTestsExecuted,
            double qualityPointChange
    ) {
    }

    record DashboardMetrics(
            int schemaVersion,
            String generatedAt,
            Usage usage,
            Impact impact,
            LatestEvidence latestEvidence,
            List<CommandUsage> commands,
            List<Activity> recentActivity
    ) {
    }

    record Usage(
            String firstUsedAt,
            String lastUsedAt,
            long totalCommands,
            long successfulCommands,
            long failedCommands,
            double successRatePercent,
            double averageCommandDurationMillis,
            int projectsSeen
    ) {
    }

    record Impact(
            long testRunsPrepared,
            long cleanupRunsPrepared,
            long validations,
            long validationsReadyToApply,
            long appliedRuns,
            long testRunsApplied,
            long cleanupRunsApplied,
            long discardedRuns,
            long diffProofsRun,
            long diffProofsPassed,
            long targetsImproved,
            double coveragePointsChanged,
            double qualityPointsChanged,
            long findingsResolved,
            long debtMinutesRemoved,
            long mutationsKilled,
            long changedTestsExecuted,
            long filesChanged
    ) {
        private static Impact empty() {
            return new Impact(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0d, 0.0d, 0, 0, 0, 0, 0);
        }
    }

    record LatestEvidence(
            Double qualityScore,
            CurrentQualityEvidence currentQuality,
            Double testQualityScore,
            Double mutationScore,
            Double lineCoverage,
            Double branchCoverage,
            int verifiedTargetCount,
            Boolean lastProofPassed,
            String lastProofAt,
            FindingsEvidence findings,
            ArchitectureEvidence architecture,
            GateEvidence gates
    ) {
        LatestEvidence {
            currentQuality = currentQuality == null ? CurrentQualityEvidence.empty() : currentQuality;
            findings = findings == null ? FindingsEvidence.empty() : findings;
            architecture = architecture == null ? ArchitectureEvidence.empty() : architecture;
            gates = gates == null ? GateEvidence.empty() : gates;
        }

        private static LatestEvidence empty() {
            return new LatestEvidence(
                    null,
                    CurrentQualityEvidence.empty(),
                    null,
                    null,
                    null,
                    null,
                    0,
                    null,
                    null,
                    FindingsEvidence.empty(),
                    ArchitectureEvidence.empty(),
                    GateEvidence.empty()
            );
        }
    }

    record CurrentQualityEvidence(
            String source,
            String capturedAt,
            String scope,
            String revision,
            String fingerprint,
            String analysisStatus,
            int targetCount,
            Integer fileCount,
            Integer linesOfCode,
            Long sourceBytes,
            Integer methodCount,
            Integer findingCount,
            Integer bugRiskCount,
            Integer codeSmellCount,
            Integer modernizationOpportunityCount,
            Integer maximumCyclomaticComplexity,
            Double averageCyclomaticComplexity,
            Integer maximumCognitiveComplexity,
            Integer duplicatedLineCount,
            Double duplicationPercent,
            Integer remediationDebtMinutes,
            Double remediationDebtRatioPercent,
            Double reliabilityScore,
            Double maintainabilityScore,
            Double complexityScore,
            Double duplicationScore,
            Double qualityScore,
            Long analysisElapsedNanos,
            int parseFailures,
            FindingsEvidence findings
    ) {
        CurrentQualityEvidence {
            findings = findings == null ? FindingsEvidence.empty() : findings;
        }

        private static CurrentQualityEvidence empty() {
            return unavailable(null, null, null, null, null, null);
        }

        private static CurrentQualityEvidence unavailable(
                String source,
                String capturedAt,
                String scope,
                String analysisStatus,
                String revision,
                String fingerprint
        ) {
            return new CurrentQualityEvidence(
                    source,
                    capturedAt,
                    scope,
                    revision,
                    fingerprint,
                    analysisStatus,
                    0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    FindingsEvidence.unavailable(source, capturedAt)
            );
        }
    }

    private record QualitySnapshotContext(
            int targetCount,
            String scope,
            String source,
            String capturedAt,
            boolean currentProject,
            String revision,
            String fingerprint
    ) {
    }

    record FindingsEvidence(
            String source,
            String capturedAt,
            Integer total,
            int critical,
            int high,
            int medium,
            int low,
            int parseFailures,
            List<FindingEvidence> items
    ) {
        FindingsEvidence {
            items = items == null ? List.of() : List.copyOf(items);
        }

        private static FindingsEvidence empty() {
            return new FindingsEvidence(null, null, null, 0, 0, 0, 0, 0, List.of());
        }

        private static FindingsEvidence unavailable(String source, String capturedAt) {
            return new FindingsEvidence(source, capturedAt, null, 0, 0, 0, 0, 0, List.of());
        }
    }

    record FindingEvidence(
            String id,
            String category,
            String severity,
            String relativePath,
            int line,
            String symbol,
            String message,
            String remediation
    ) {
    }

    record ArchitectureEvidence(
            String source,
            String capturedAt,
            String engine,
            String engineVersion,
            Integer rulesetVersion,
            List<String> rules,
            Boolean complete,
            Boolean goalMet,
            Integer violationCount,
            int compiledClassCount,
            List<String> missingTargetClasses,
            String incompleteReason,
            List<ArchitectureViolationEvidence> violations
    ) {
        ArchitectureEvidence {
            rules = rules == null ? List.of() : List.copyOf(rules);
            missingTargetClasses = missingTargetClasses == null ? List.of() : List.copyOf(missingTargetClasses);
            violations = violations == null ? List.of() : List.copyOf(violations);
        }

        private static ArchitectureEvidence empty() {
            return new ArchitectureEvidence(
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    null,
                    null,
                    null,
                    0,
                    List.of(),
                    null,
                    List.of()
            );
        }

        private static ArchitectureEvidence unavailable(String source, String capturedAt) {
            return new ArchitectureEvidence(
                    source,
                    capturedAt,
                    null,
                    null,
                    null,
                    List.of(),
                    null,
                    null,
                    null,
                    0,
                    List.of(),
                    "Architecture evidence was not produced.",
                    List.of()
            );
        }
    }

    record ArchitectureViolationEvidence(
            String id,
            String severity,
            String originClass,
            String targetClass,
            String relativePath,
            int line,
            List<String> cyclePackages,
            String message,
            String remediation
    ) {
        ArchitectureViolationEvidence {
            cyclePackages = cyclePackages == null ? List.of() : List.copyOf(cyclePackages);
        }
    }

    record GateEvidence(
            String source,
            String capturedAt,
            Boolean passed,
            List<String> failures,
            List<String> warnings
    ) {
        GateEvidence {
            failures = failures == null ? List.of() : List.copyOf(failures);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        private static GateEvidence empty() {
            return new GateEvidence(null, null, null, List.of(), List.of());
        }
    }

    record CommandUsage(String command, long count) {
    }

    record Activity(String at, String command, boolean successful, String summary, long durationMillis) {
    }

    private static final class MutableImpact {
        private long testRunsPrepared;
        private long cleanupRunsPrepared;
        private long validations;
        private long validationsReadyToApply;
        private long appliedRuns;
        private long testRunsApplied;
        private long cleanupRunsApplied;
        private long discardedRuns;
        private long diffProofsRun;
        private long diffProofsPassed;
        private long targetsImproved;
        private double coveragePointsChanged;
        private double qualityPointsChanged;
        private long findingsResolved;
        private long debtMinutesRemoved;
        private long mutationsKilled;
        private long changedTestsExecuted;
        private long filesChanged;

        private MutableImpact(Impact value) {
            this.testRunsPrepared = value.testRunsPrepared();
            this.cleanupRunsPrepared = value.cleanupRunsPrepared();
            this.validations = value.validations();
            this.validationsReadyToApply = value.validationsReadyToApply();
            this.appliedRuns = value.appliedRuns();
            this.testRunsApplied = value.testRunsApplied();
            this.cleanupRunsApplied = value.cleanupRunsApplied();
            this.discardedRuns = value.discardedRuns();
            this.diffProofsRun = value.diffProofsRun();
            this.diffProofsPassed = value.diffProofsPassed();
            this.targetsImproved = value.targetsImproved();
            this.coveragePointsChanged = value.coveragePointsChanged();
            this.qualityPointsChanged = value.qualityPointsChanged();
            this.findingsResolved = value.findingsResolved();
            this.debtMinutesRemoved = value.debtMinutesRemoved();
            this.mutationsKilled = value.mutationsKilled();
            this.changedTestsExecuted = value.changedTestsExecuted();
            this.filesChanged = value.filesChanged();
        }

        private Impact freeze() {
            return new Impact(
                    testRunsPrepared,
                    cleanupRunsPrepared,
                    validations,
                    validationsReadyToApply,
                    appliedRuns,
                    testRunsApplied,
                    cleanupRunsApplied,
                    discardedRuns,
                    diffProofsRun,
                    diffProofsPassed,
                    targetsImproved,
                    coveragePointsChanged,
                    qualityPointsChanged,
                    findingsResolved,
                    debtMinutesRemoved,
                    mutationsKilled,
                    changedTestsExecuted,
                    filesChanged
            );
        }
    }

    private static final class MutableEvidence {
        private Double qualityScore;
        private CurrentQualityEvidence currentQuality;
        private Double testQualityScore;
        private Double mutationScore;
        private Double lineCoverage;
        private Double branchCoverage;
        private int verifiedTargetCount;
        private Boolean lastProofPassed;
        private String lastProofAt;
        private FindingsEvidence findings;
        private ArchitectureEvidence architecture;
        private GateEvidence gates;

        private MutableEvidence(LatestEvidence value) {
            this.qualityScore = value.qualityScore();
            this.currentQuality = value.currentQuality();
            this.testQualityScore = value.testQualityScore();
            this.mutationScore = value.mutationScore();
            this.lineCoverage = value.lineCoverage();
            this.branchCoverage = value.branchCoverage();
            this.verifiedTargetCount = value.verifiedTargetCount();
            this.lastProofPassed = value.lastProofPassed();
            this.lastProofAt = value.lastProofAt();
            this.findings = value.findings();
            this.architecture = value.architecture();
            this.gates = value.gates();
        }

        private LatestEvidence freeze() {
            return new LatestEvidence(
                    qualityScore,
                    currentQuality,
                    testQualityScore,
                    mutationScore,
                    lineCoverage,
                    branchCoverage,
                    verifiedTargetCount,
                    lastProofPassed,
                    lastProofAt,
                    findings,
                    architecture,
                    gates
            );
        }
    }
}
