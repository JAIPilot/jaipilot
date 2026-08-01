package com.jaipilot.mcp.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class ProjectFileService {

    private static final List<String> GRADLE_BUILD_FILES = List.of(
            "settings.gradle",
            "settings.gradle.kts",
            "build.gradle",
            "build.gradle.kts"
    );
    private static final Set<String> COPY_EXCLUDED_DIRECTORY_NAMES = Set.of(
            ".git",
            "target",
            "build",
            ".gradle",
            ".idea",
            ".vscode",
            ".scannerwork",
            "node_modules",
            "out"
    );
    private static final Set<String> COPY_EXCLUDED_FILE_NAMES = Set.of(
            ".DS_Store"
    );

    public ProjectFileService() {
    }

    public Path resolvePath(Path projectRoot, Path path) {
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return projectRoot.resolve(path).normalize();
    }

    public String readFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read file " + path, exception);
        }
    }

    public void writeFile(Path path, String content) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write file " + path, exception);
        }
    }

    public void writeFilesTransactionally(
            Map<Path, String> contentsByPath,
            Map<Path, FileFingerprint> expectedFingerprints
    ) {
        List<Path> paths = contentsByPath.keySet().stream().sorted().toList();
        Map<Path, Path> stagedFiles = new LinkedHashMap<>();
        Map<Path, byte[]> originalContents = new LinkedHashMap<>();
        List<Path> movedPaths = new ArrayList<>();
        try {
            verifyFingerprints(paths, expectedFingerprints);
            for (Path path : paths) {
                Path parent = path.getParent();
                if (parent == null) {
                    throw new IOException("Cannot stage a file without a parent directory: " + path);
                }
                Files.createDirectories(parent);
                if (Files.isRegularFile(path)) {
                    originalContents.put(path, Files.readAllBytes(path));
                }
                Path staged = Files.createTempFile(parent, ".jaipilot-", ".tmp");
                Files.writeString(staged, contentsByPath.get(path), StandardCharsets.UTF_8);
                stagedFiles.put(path, staged);
            }
            verifyFingerprints(paths, expectedFingerprints);
            for (Path path : paths) {
                moveReplacing(stagedFiles.get(path), path);
                movedPaths.add(path);
            }
        } catch (Exception exception) {
            rollbackFiles(movedPaths, originalContents);
            throw new IllegalStateException("Failed to merge files transactionally.", exception);
        } finally {
            stagedFiles.values().forEach(staged -> {
                try {
                    Files.deleteIfExists(staged);
                } catch (IOException ignored) {
                    // Best-effort cleanup for a failed transaction.
                }
            });
        }
    }

    private void verifyFingerprints(List<Path> paths, Map<Path, FileFingerprint> expectedFingerprints) {
        List<Path> drifted = paths.stream().filter(path -> {
            FileFingerprint current = Files.isRegularFile(path) ? fingerprint(path) : null;
            return !Objects.equals(expectedFingerprints.get(path), current);
        }).toList();
        if (!drifted.isEmpty()) {
            throw new IllegalStateException("Files changed while the isolated workflow was running: " + drifted);
        }
    }

    private void rollbackFiles(List<Path> movedPaths, Map<Path, byte[]> originalContents) {
        List<Path> reverseOrder = new ArrayList<>(movedPaths);
        reverseOrder.sort(Comparator.reverseOrder());
        for (Path path : reverseOrder) {
            try {
                byte[] original = originalContents.get(path);
                if (original == null) {
                    Files.deleteIfExists(path);
                    continue;
                }
                Path staged = Files.createTempFile(path.getParent(), ".jaipilot-rollback-", ".tmp");
                Files.write(staged, original);
                moveReplacing(staged, path);
            } catch (Exception ignored) {
                // Preserve the original merge exception; rollback is best effort.
            }
        }
    }

    private void moveReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(
                    source,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Map<Path, FileFingerprint> snapshotJavaTestFiles(Path root) {
        return snapshotFiles(root, this::isJavaTestPath);
    }

    public Map<Path, FileFingerprint> snapshotJavaSourceFiles(Path root) {
        return snapshotFiles(root, this::isJavaSourcePath);
    }

    public Map<Path, FileFingerprint> snapshotWorkspaceFiles(Path root) {
        Map<Path, FileFingerprint> snapshot = new LinkedHashMap<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    String name = directory.getFileName() == null ? "" : directory.getFileName().toString();
                    if (!directory.equals(root) && COPY_EXCLUDED_DIRECTORY_NAMES.contains(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    if (!COPY_EXCLUDED_FILE_NAMES.contains(file.getFileName().toString())) {
                        if (!attributes.isRegularFile() && !attributes.isSymbolicLink()) {
                            throw new IOException("Project contains an unsupported special file: " + file);
                        }
                        snapshot.put(
                                file.normalize(),
                                attributes.isSymbolicLink() ? fingerprintSymbolicLink(file) : fingerprint(file)
                        );
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to snapshot workspace files under " + root, exception);
        }
        return Map.copyOf(snapshot);
    }

    private Map<Path, FileFingerprint> snapshotFiles(Path root, Predicate<Path> pathFilter) {
        Map<Path, FileFingerprint> snapshot = new LinkedHashMap<>();
        findProjectFiles(root, pathFilter).forEach(path -> snapshot.put(path, fingerprint(path)));
        return snapshot;
    }

    List<Path> findProjectFiles(Path root, Predicate<Path> pathFilter) {
        List<Path> matches = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (isExcludedDirectory(root, directory)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile()
                            && !COPY_EXCLUDED_FILE_NAMES.contains(file.getFileName().toString())
                            && pathFilter.test(file)) {
                        matches.add(file.normalize());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan project files under " + root, exception);
        }
        return matches.stream().sorted().toList();
    }

    private boolean isExcludedDirectory(Path root, Path directory) {
        String name = directory.getFileName() == null ? "" : directory.getFileName().toString();
        return !directory.equals(root) && COPY_EXCLUDED_DIRECTORY_NAMES.contains(name);
    }

    public void copyProjectWorkspace(Path sourceRoot, Path destinationRoot) {
        try {
            Files.createDirectories(destinationRoot);
            Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                    if (isExcludedDirectory(sourceRoot, directory)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    Files.createDirectories(destinationRoot.resolve(sourceRoot.relativize(directory)));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    if (COPY_EXCLUDED_FILE_NAMES.contains(file.getFileName().toString())) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path destination = destinationRoot.resolve(sourceRoot.relativize(file));
                    Path parent = destination.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    if (attributes.isSymbolicLink()) {
                        copyInternalSymbolicLink(sourceRoot, destinationRoot, file, destination);
                        return FileVisitResult.CONTINUE;
                    }
                    if (!attributes.isRegularFile()) {
                        throw new IOException("Project contains an unsupported special file: " + file);
                    }
                    Files.copy(file, destination, StandardCopyOption.COPY_ATTRIBUTES);
                    if (Files.isExecutable(file)) {
                        destination.toFile().setExecutable(true, false);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to create isolated workspace from " + sourceRoot + " to " + destinationRoot,
                    exception
            );
        }
    }

    public void deleteRecursively(Path root) {
        if (root == null || Files.notExists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                    if (exception != null) {
                        throw exception;
                    }
                    Files.deleteIfExists(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete directory " + root, exception);
        }
    }

    public Path findNearestBuildProjectRoot(Path path) {
        Path current = path.normalize();
        if (Files.isRegularFile(current)) {
            current = current.getParent();
        }
        while (current != null) {
            if (containsBuildFile(current)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    public String stripJavaExtension(String fileName) {
        if (fileName.endsWith(".java")) {
            return fileName.substring(0, fileName.length() - ".java".length());
        }
        return fileName;
    }

    private boolean containsBuildFile(Path directory) {
        return Files.isRegularFile(directory.resolve("pom.xml")) || containsGradleBuildFile(directory);
    }

    private boolean containsGradleBuildFile(Path directory) {
        return GRADLE_BUILD_FILES.stream()
                .map(directory::resolve)
                .anyMatch(Files::isRegularFile);
    }

    private boolean isJavaTestPath(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return normalized.endsWith(".java") && normalized.contains("/src/test/java/");
    }

    private boolean isJavaSourcePath(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return normalized.endsWith(".java")
                && (normalized.contains("/src/main/java/") || normalized.contains("/src/test/java/"));
    }

    private FileFingerprint fingerprint(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[16_384];
            long size = 0L;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
                size += read;
            }
            return new FileFingerprint(size, HexFormat.of().formatHex(digest.digest()), false);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to fingerprint file " + path, exception);
        }
    }

    private FileFingerprint fingerprintSymbolicLink(Path path) {
        try {
            byte[] target = Files.readSymbolicLink(path).toString().getBytes(StandardCharsets.UTF_8);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new FileFingerprint(target.length, HexFormat.of().formatHex(digest.digest(target)), true);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to fingerprint symbolic link " + path, exception);
        }
    }

    private void copyInternalSymbolicLink(
            Path sourceRoot,
            Path destinationRoot,
            Path source,
            Path destination
    ) throws IOException {
        Path linkTarget = Files.readSymbolicLink(source);
        Path resolvedTarget = linkTarget.isAbsolute()
                ? linkTarget
                : source.getParent().resolve(linkTarget).normalize();
        Path realRoot = sourceRoot.toRealPath();
        Path realTarget;
        try {
            realTarget = resolvedTarget.toRealPath();
        } catch (IOException exception) {
            throw new IOException("Project contains a dangling symbolic link: " + source, exception);
        }
        if (!realTarget.startsWith(realRoot)) {
            throw new IOException("Project symbolic link resolves outside the project: " + source);
        }
        Path copiedTarget = destinationRoot.resolve(realRoot.relativize(realTarget)).normalize();
        Path safeRelativeTarget = destination.getParent().relativize(copiedTarget);
        Files.createSymbolicLink(destination, safeRelativeTarget);
    }

    public record FileFingerprint(
            long size,
            String sha256,
            boolean symbolicLink
    ) {
    }
}
