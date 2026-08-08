package com.jaipilot.toolkit.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Discovers the exact local Java diff without fetching or changing the repository. */
public final class GitChangeService {

    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_GIT_OUTPUT_BYTES = 16 * 1024 * 1024;
    private static final Pattern HUNK_HEADER = Pattern.compile(
            "^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,(\\d+))? @@.*$"
    );
    private static final List<String> DEFAULT_BRANCH_REFS = List.of(
            "refs/remotes/origin/main",
            "refs/remotes/origin/master",
            "refs/heads/main",
            "refs/heads/master"
    );
    private static final Set<String> BUILD_FILES = Set.of(
            "pom.xml",
            "build.gradle",
            "build.gradle.kts",
            "settings.gradle",
            "settings.gradle.kts"
    );
    private static final Set<String> BUILD_WRAPPERS = Set.of("mvnw", "gradlew");

    private final String explicitBase;

    public GitChangeService() {
        this(System.getenv("JAIPILOT_DIFF_BASE"));
    }

    GitChangeService(String explicitBase) {
        this.explicitBase = explicitBase == null || explicitBase.isBlank() ? null : explicitBase.trim();
    }

    public DiffSnapshot snapshot(Path requestedRoot) {
        Path projectRoot = requestedRoot.toAbsolutePath().normalize();
        requireGitRepository(projectRoot);
        String headCommit = gitOptional(projectRoot, "rev-parse", "--verify", "HEAD^{commit}")
                .map(String::trim)
                .orElse(null);
        BaseRevision base = selectBase(projectRoot, headCommit);

        LinkedHashSet<String> changed = new LinkedHashSet<>();
        if (base.commit() != null) {
            changed.addAll(nulSeparated(gitRequired(
                    projectRoot,
                    "diff",
                    "--name-only",
                    "-z",
                    "--diff-filter=ACMRDT",
                    "--relative",
                    base.commit(),
                    "--"
            )));
        } else {
            changed.addAll(nulSeparated(gitRequired(
                    projectRoot,
                    "ls-files",
                    "-z",
                    "--cached"
            )));
        }
        changed.addAll(nulSeparated(gitRequired(
                projectRoot,
                "ls-files",
                "-z",
                "--others",
                "--exclude-standard"
        )));

        List<Path> changedPaths = changed.stream()
                .map(Path::of)
                .map(Path::normalize)
                .filter(path -> !path.isAbsolute() && !path.startsWith(".."))
                .distinct()
                .sorted()
                .toList();
        List<Path> changedJava = changedPaths.stream().filter(GitChangeService::isJavaPath).toList();
        List<Path> changedProduction = changedJava.stream()
                .filter(GitChangeService::isProductionJavaPath)
                .toList();
        List<Path> existingProduction = changedProduction.stream()
                .filter(path -> !"package-info.java".equals(path.getFileName().toString()))
                .filter(path -> Files.isRegularFile(projectRoot.resolve(path))
                        && !Files.isSymbolicLink(projectRoot.resolve(path)))
                .toList();
        List<Path> proofPaths = changedPaths.stream().filter(GitChangeService::affectsJavaProof).toList();
        String fingerprint = fingerprint(projectRoot, base.commit(), proofPaths);
        return new DiffSnapshot(
                projectRoot,
                base.commit(),
                base.description(),
                headCommit,
                fingerprint,
                changedPaths,
                changedJava,
                changedProduction,
                existingProduction,
                proofPaths
        );
    }

    /** Returns new-file line ranges for the existing production files in a previously captured diff. */
    public Map<Path, List<LineRange>> changedLineRanges(DiffSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Map<Path, List<LineRange>> ranges = new java.util.LinkedHashMap<>();
        for (Path relative : snapshot.existingProductionPaths()) {
            List<LineRange> changed = diffLineRanges(snapshot, relative);
            if (changed.isEmpty() && !isTracked(snapshot.projectRoot(), relative)) {
                int lineCount;
                try {
                    lineCount = Files.readAllLines(snapshot.projectRoot().resolve(relative), StandardCharsets.UTF_8).size();
                } catch (IOException exception) {
                    throw new IllegalStateException("Failed to read untracked Java source " + relative, exception);
                }
                changed = lineCount == 0 ? List.of() : List.of(new LineRange(1, lineCount));
            }
            ranges.put(relative, changed);
        }
        return Map.copyOf(ranges);
    }

    /** Reads a file exactly as it existed at the selected comparison baseline. */
    public Optional<String> baselineFile(DiffSnapshot snapshot, Path relative) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.baselineCommit() == null) {
            return Optional.empty();
        }
        String prefix = gitOptional(snapshot.projectRoot(), "rev-parse", "--show-prefix")
                .map(String::trim)
                .orElse("");
        String repositoryPath = prefix + portable(relative);
        return gitOptional(
                snapshot.projectRoot(),
                "show",
                snapshot.baselineCommit() + ":" + repositoryPath
        );
    }

    private List<LineRange> diffLineRanges(DiffSnapshot snapshot, Path relative) {
        String base = snapshot.baselineCommit() == null ? "HEAD" : snapshot.baselineCommit();
        Optional<String> patch = gitOptional(
                snapshot.projectRoot(),
                "diff",
                "--unified=0",
                "--no-color",
                "--relative",
                base,
                "--",
                relative.toString()
        );
        if (patch.isEmpty()) {
            throw new IllegalStateException("Git failed to calculate changed lines for " + relative + ".");
        }
        List<LineRange> ranges = new ArrayList<>();
        for (String line : patch.get().lines().toList()) {
            Matcher matcher = HUNK_HEADER.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            int start = Integer.parseInt(matcher.group(1));
            int count = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
            if (count > 0) {
                ranges.add(new LineRange(start, start + count - 1));
            }
        }
        return mergeRanges(ranges);
    }

    private boolean isTracked(Path root, Path relative) {
        return gitOptional(root, "ls-files", "--error-unmatch", "--", relative.toString()).isPresent();
    }

    List<LineRange> mergeRanges(List<LineRange> ranges) {
        if (ranges.size() < 2) {
            return List.copyOf(ranges);
        }
        List<LineRange> sorted = ranges.stream().sorted(java.util.Comparator.comparingInt(LineRange::start)).toList();
        List<LineRange> merged = new ArrayList<>();
        LineRange current = sorted.get(0);
        for (int index = 1; index < sorted.size(); index++) {
            LineRange next = sorted.get(index);
            if (next.start() <= current.end() + 1) {
                current = new LineRange(current.start(), Math.max(current.end(), next.end()));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return List.copyOf(merged);
    }

    private void requireGitRepository(Path root) {
        String inside = gitOptional(root, "rev-parse", "--is-inside-work-tree").orElse("");
        if (!"true".equals(inside.trim())) {
            throw new NotGitWorktreeException("JAIPilot changed-code review requires a Git worktree: " + root);
        }
    }

    private BaseRevision selectBase(Path root, String headCommit) {
        if (headCommit == null) {
            return new BaseRevision(null, "unborn HEAD");
        }
        if (explicitBase != null) {
            String base = mergeBase(root, explicitBase, headCommit)
                    .orElseThrow(() -> new IllegalStateException(
                            "JAIPILOT_DIFF_BASE does not resolve to a commit related to HEAD: " + explicitBase
                    ));
            return new BaseRevision(base, "merge-base(" + explicitBase + ",HEAD)");
        }

        Optional<String> defaultRef = defaultBranchRef(root);
        String currentBranch = gitOptional(root, "branch", "--show-current").orElse("").trim();
        if (defaultRef.isPresent()) {
            if (isDefaultBranch(currentBranch, defaultRef.get())) {
                return new BaseRevision(headCommit, "HEAD");
            }
            Optional<String> mergeBase = mergeBase(root, defaultRef.get(), headCommit);
            if (mergeBase.isPresent()) {
                return new BaseRevision(mergeBase.get(), "merge-base(" + shortRef(defaultRef.get()) + ",HEAD)");
            }
        }

        Optional<String> parent = gitOptional(root, "rev-parse", "--verify", "HEAD^");
        return parent.map(value -> new BaseRevision(value.trim(), "HEAD^"))
                .orElseGet(() -> new BaseRevision(headCommit, "HEAD"));
    }

    private Optional<String> defaultBranchRef(Path root) {
        Optional<String> symbolic = gitOptional(
                root,
                "symbolic-ref",
                "--quiet",
                "refs/remotes/origin/HEAD"
        );
        if (symbolic.isPresent() && refExists(root, symbolic.get().trim())) {
            return Optional.of(symbolic.get().trim());
        }
        return DEFAULT_BRANCH_REFS.stream().filter(ref -> refExists(root, ref)).findFirst();
    }

    private boolean refExists(Path root, String ref) {
        return gitOptional(root, "rev-parse", "--verify", ref + "^{commit}").isPresent();
    }

    private Optional<String> mergeBase(Path root, String ref, String headCommit) {
        return gitOptional(root, "merge-base", ref, headCommit).map(String::trim).filter(value -> !value.isBlank());
    }

    boolean isDefaultBranch(String branch, String defaultRef) {
        if (branch.isBlank()) {
            return false;
        }
        String normalized = shortRef(defaultRef);
        int separator = normalized.lastIndexOf('/');
        String defaultBranch = separator < 0 ? normalized : normalized.substring(separator + 1);
        return branch.equals(defaultBranch);
    }

    String shortRef(String ref) {
        return ref.replaceFirst("^refs/remotes/", "").replaceFirst("^refs/heads/", "");
    }

    private String fingerprint(Path root, String base, List<Path> paths) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, "schema=3\n");
            for (Path relative : paths) {
                fingerprintBaseline(root, base, relative, digest);
                fingerprintPath(root, relative, digest);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to fingerprint the changed Java scope under " + root, exception);
        }
    }

    private void fingerprintBaseline(Path root, String base, Path relative, MessageDigest digest) {
        if (base == null) {
            update(digest, "baseline=missing\n");
            return;
        }
        String entry = gitOptional(root, "ls-tree", base, "--", portable(relative)).orElse("").strip();
        update(digest, entry.isEmpty() ? "baseline=missing\n" : "baseline=" + entry + "\n");
    }

    private void fingerprintPath(Path root, Path relative, MessageDigest digest) throws IOException {
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalStateException("Git returned a changed path outside the project: " + relative);
        }
        update(digest, "path=" + portable(relative) + "\n");
        if (Files.isSymbolicLink(resolved)) {
            update(digest, "symlink=" + Files.readSymbolicLink(resolved) + "\n");
            return;
        }
        if (Files.isRegularFile(resolved)) {
            update(digest, "file\nexecutable=" + Files.isExecutable(resolved) + "\n");
            digest.update(Files.readAllBytes(resolved));
            update(digest, "\n");
            return;
        }
        update(digest, "deleted\n");
    }

    private void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }

    private String gitRequired(Path root, String... arguments) {
        return git(root, arguments).orElseThrow(() -> new IllegalStateException(
                "Git command failed while discovering changed Java under " + root + ": git "
                        + String.join(" ", arguments)
        ));
    }

    private Optional<String> gitOptional(Path root, String... arguments) {
        return git(root, arguments);
    }

    private Optional<String> git(Path root, String... arguments) {
        Process process = null;
        try {
            process = startGit(root, arguments);
            return captureGit(process);
        } catch (InterruptedException exception) {
            cancel(process);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Git change discovery was interrupted.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start Git for changed-code discovery.", exception);
        }
    }

    private Process startGit(Path root, String[] arguments) throws IOException {
        List<String> command = new ArrayList<>(List.of("git", "-c", "core.quotepath=false"));
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(root.toFile())
                .redirectErrorStream(true);
        builder.environment().clear();
        builder.environment().putAll(GitProcessEnvironment.sanitizedCopy());
        return builder.start();
    }

    private Optional<String> captureGit(Process process) throws InterruptedException {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        AtomicBoolean truncated = new AtomicBoolean();
        AtomicReference<IOException> readFailure = new AtomicReference<>();
        Thread reader = gitReader(process, captured, truncated, readFailure);
        reader.start();
        boolean finished = awaitGit(process);
        reader.join(TimeUnit.SECONDS.toMillis(2));
        if (!validGitResult(process, reader, finished, truncated, readFailure)) {
            return Optional.empty();
        }
        return Optional.of(captured.toString(StandardCharsets.UTF_8));
    }

    private Thread gitReader(
            Process process,
            ByteArrayOutputStream captured,
            AtomicBoolean truncated,
            AtomicReference<IOException> readFailure
    ) {
        Thread reader = new Thread(
                () -> readGitOutput(process, captured, truncated, readFailure),
                "jaipilot-git-reader"
        );
        reader.setDaemon(true);
        return reader;
    }

    private boolean awaitGit(Process process) throws InterruptedException {
        if (process.waitFor(GIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            return true;
        }
        process.destroy();
        if (!process.waitFor(500L, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            process.waitFor();
        }
        return false;
    }

    private boolean validGitResult(
            Process process,
            Thread reader,
            boolean finished,
            AtomicBoolean truncated,
            AtomicReference<IOException> readFailure
    ) {
        return finished
                && !reader.isAlive()
                && !truncated.get()
                && readFailure.get() == null
                && process.exitValue() == 0;
    }

    private void cancel(Process process) {
        if (process != null) {
            process.destroyForcibly();
        }
    }

    private void readGitOutput(
            Process process,
            ByteArrayOutputStream captured,
            AtomicBoolean truncated,
            AtomicReference<IOException> failure
    ) {
        byte[] buffer = new byte[8192];
        try (var input = process.getInputStream()) {
            int read;
            int total = 0;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                if (total + read <= MAX_GIT_OUTPUT_BYTES) {
                    captured.write(buffer, 0, read);
                } else {
                    truncated.set(true);
                }
                total += read;
            }
        } catch (IOException exception) {
            failure.compareAndSet(null, exception);
        }
    }

    List<String> nulSeparated(String output) {
        if (output == null || output.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < output.length(); index++) {
            if (output.charAt(index) != '\0') {
                continue;
            }
            if (index > start) {
                values.add(output.substring(start, index));
            }
            start = index + 1;
        }
        if (start < output.length()) {
            values.add(output.substring(start));
        }
        return List.copyOf(values);
    }

    private static boolean isJavaPath(Path path) {
        return portable(path).toLowerCase(Locale.ROOT).endsWith(".java");
    }

    static boolean isProductionJavaPath(Path path) {
        String normalized = "/" + portable(path);
        return normalized.contains("/src/main/java/") && !normalized.contains("/src/test/");
    }

    static boolean affectsJavaProof(Path path) {
        String normalized = portable(path);
        String filename = path.getFileName().toString();
        return isJavaPath(path)
                || BUILD_FILES.contains(filename)
                || BUILD_WRAPPERS.contains(normalized)
                || normalized.startsWith(".mvn/")
                || normalized.startsWith("gradle/");
    }

    private static String portable(Path path) {
        return path.normalize().toString().replace('\\', '/');
    }

    private record BaseRevision(String commit, String description) {
    }

    public record DiffSnapshot(
            Path projectRoot,
            String baselineCommit,
            String baselineDescription,
            String headCommit,
            String fingerprint,
            List<Path> changedPaths,
            List<Path> changedJavaPaths,
            List<Path> changedProductionPaths,
            List<Path> existingProductionPaths,
            List<Path> proofRelevantPaths
    ) {
        public DiffSnapshot {
            changedPaths = List.copyOf(changedPaths);
            changedJavaPaths = List.copyOf(changedJavaPaths);
            changedProductionPaths = List.copyOf(changedProductionPaths);
            existingProductionPaths = List.copyOf(existingProductionPaths);
            proofRelevantPaths = List.copyOf(proofRelevantPaths);
        }

        public boolean hasProductionChanges() {
            return !changedProductionPaths.isEmpty();
        }
    }

    public record LineRange(int start, int end) {
        public LineRange {
            if (start < 1 || end < start) {
                throw new IllegalArgumentException("Changed line range must be positive and ordered.");
            }
        }

        public boolean contains(int line) {
            return line >= start && line <= end;
        }

        public int count() {
            return end - start + 1;
        }
    }

    public static final class NotGitWorktreeException extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        private NotGitWorktreeException(String message) {
            super(message);
        }
    }
}
