package com.jaipilot.toolkit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitChangeServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void featureBranchUsesDefaultBranchMergeBaseAndIncludesCommittedWork() throws Exception {
        Path root = project("feature");
        Path source = source(root);
        commit(root, "baseline");
        git(root, "switch", "-qc", "feature/proof");
        Files.writeString(source, "package com.example; class OrderService { int changed; }\n");
        commit(root, "feature change");

        GitChangeService service = new GitChangeService();
        GitChangeService.DiffSnapshot snapshot = service.snapshot(root);

        assertEquals("merge-base(main,HEAD)", snapshot.baselineDescription());
        assertEquals(List.of(Path.of("src/main/java/com/example/OrderService.java")),
                snapshot.existingProductionPaths());
        assertEquals(
                List.of(new GitChangeService.LineRange(1, 1)),
                service.changedLineRanges(snapshot).get(Path.of("src/main/java/com/example/OrderService.java"))
        );
        assertTrue(snapshot.hasProductionChanges());
    }

    @Test
    void defaultBranchUsesHeadAndIncludesOnlyWorkingAndUntrackedJava() throws Exception {
        Path root = project("main");
        Path source = source(root);
        commit(root, "baseline");
        Files.writeString(source, "package com.example; class OrderService { int committed; }\n");
        commit(root, "latest change");
        Files.writeString(source, "package com.example; class OrderService { int working; }\n");
        Path test = root.resolve("src/test/java/com/example/OrderServiceTest.java");
        Files.createDirectories(test.getParent());
        Files.writeString(test, "package com.example; class OrderServiceTest {}\n");

        GitChangeService.DiffSnapshot snapshot = new GitChangeService().snapshot(root);

        assertEquals("HEAD", snapshot.baselineDescription());
        assertTrue(snapshot.changedJavaPaths().contains(root.relativize(source)));
        assertTrue(snapshot.changedJavaPaths().contains(root.relativize(test)));
    }

    @Test
    void untrackedProductionFileTreatsEveryNewLineAsChanged() throws Exception {
        Path root = project("untracked-lines");
        commit(root, "baseline");
        Path added = root.resolve("src/main/java/com/example/NewService.java");
        Files.writeString(added, "package com.example;\nclass NewService {\n  int value() { return 1; }\n}\n");
        GitChangeService service = new GitChangeService();
        GitChangeService.DiffSnapshot snapshot = service.snapshot(root);

        assertEquals(
                List.of(new GitChangeService.LineRange(1, 4)),
                service.changedLineRanges(snapshot).get(root.relativize(added))
        );
    }

    @Test
    void fingerprintChangesWithRelevantContentButNotDocumentationOnlyContent() throws Exception {
        Path root = project("fingerprint");
        Path source = source(root);
        commit(root, "baseline");
        Files.writeString(source, "package com.example; class OrderService { int one; }\n");
        GitChangeService service = new GitChangeService();
        String first = service.snapshot(root).fingerprint();

        Path readme = root.resolve("README.md");
        Files.writeString(readme, "documentation one\n");
        String documentationOnly = service.snapshot(root).fingerprint();
        Files.writeString(readme, "documentation two\n");
        String documentationChanged = service.snapshot(root).fingerprint();
        git(root, "add", "README.md");
        git(root, "commit", "-qm", "documentation only");
        String documentationCommitted = service.snapshot(root).fingerprint();
        Files.writeString(source, "package com.example; class OrderService { int two; }\n");
        String sourceChanged = service.snapshot(root).fingerprint();

        assertEquals(first, documentationOnly);
        assertEquals(documentationOnly, documentationChanged);
        assertEquals(documentationChanged, documentationCommitted);
        assertNotEquals(documentationCommitted, sourceChanged);
    }

    @Test
    void explicitBaseAndBaselineFileUseTheMergeBaseWithoutFetching() throws Exception {
        Path root = project("explicit-base");
        Path source = source(root);
        String baseline = Files.readString(source);
        commit(root, "baseline");
        git(root, "switch", "-qc", "feature/explicit");
        Files.writeString(source, "package com.example; class OrderService { int explicit; }\n");
        commit(root, "feature change");
        GitChangeService service = new GitChangeService("main");

        GitChangeService.DiffSnapshot snapshot = service.snapshot(root);

        assertEquals("merge-base(main,HEAD)", snapshot.baselineDescription());
        assertEquals(baseline, service.baselineFile(snapshot, root.relativize(source)).orElseThrow());
    }

    @Test
    void deletedProductionFileRemainsInTheProofFingerprintButNotLiveTargets() throws Exception {
        Path root = project("deleted");
        Path source = source(root);
        commit(root, "baseline");
        git(root, "switch", "-qc", "feature/delete");
        Files.delete(source);
        commit(root, "delete source");

        GitChangeService.DiffSnapshot snapshot = new GitChangeService().snapshot(root);

        assertEquals(List.of(root.relativize(source)), snapshot.changedProductionPaths());
        assertTrue(snapshot.existingProductionPaths().isEmpty());
        assertTrue(snapshot.proofRelevantPaths().contains(root.relativize(source)));
    }

    @Test
    void nonGitDirectoryFailsWithAUsefulTypedError() throws Exception {
        Path root = tempDir.resolve("not-git");
        Files.createDirectories(root);

        GitChangeService.NotGitWorktreeException failure = assertThrows(
                GitChangeService.NotGitWorktreeException.class,
                () -> new GitChangeService().snapshot(root)
        );

        assertTrue(failure.getMessage().contains("requires a Git worktree"));
    }

    @Test
    void unbornRepositoryUsesTheIndexAsItsDeterministicScope() throws Exception {
        Path root = project("unborn");
        git(root, "add", "pom.xml", "src/main/java/com/example/OrderService.java");

        GitChangeService.DiffSnapshot snapshot = new GitChangeService().snapshot(root);

        assertEquals("unborn HEAD", snapshot.baselineDescription());
        assertEquals(List.of(Path.of("src/main/java/com/example/OrderService.java")),
                snapshot.existingProductionPaths());
        assertTrue(new GitChangeService().baselineFile(
                snapshot,
                Path.of("src/main/java/com/example/OrderService.java")
        ).isEmpty());
    }

    @Test
    void invalidExplicitBaseFailsInsteadOfGuessingAComparison() throws Exception {
        Path root = project("bad-base");
        commit(root, "baseline");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new GitChangeService("missing-local-ref").snapshot(root)
        );

        assertTrue(failure.getMessage().contains("JAIPILOT_DIFF_BASE"));
    }

    @Test
    void changedProductionSymlinkIsFingerprintedButNeverTreatedAsALiveTarget() throws Exception {
        Path root = project("symlink");
        Path source = source(root);
        commit(root, "baseline");
        git(root, "switch", "-qc", "feature/symlink");
        Files.delete(source);
        Files.createSymbolicLink(source, Path.of("../../../../../pom.xml"));
        commit(root, "replace source with symlink");

        GitChangeService.DiffSnapshot snapshot = new GitChangeService().snapshot(root);

        assertEquals(List.of(root.relativize(source)), snapshot.changedProductionPaths());
        assertTrue(snapshot.existingProductionPaths().isEmpty());
    }

    @Test
    void proofScopeIncludesTestsAndBuildInputsButExcludesDocumentation() throws Exception {
        Path root = project("scope-classification");
        commit(root, "baseline");
        Path test = root.resolve("src/test/java/com/example/OrderServiceTest.java");
        Files.createDirectories(test.getParent());
        Files.writeString(test, "package com.example; class OrderServiceTest {}\n");
        Files.writeString(root.resolve("pom.xml"), "<project><version>2</version></project>\n");
        Files.writeString(root.resolve("README.md"), "docs only\n");

        GitChangeService.DiffSnapshot snapshot = new GitChangeService().snapshot(root);

        assertTrue(snapshot.changedProductionPaths().isEmpty());
        assertEquals(
                List.of(Path.of("pom.xml"), Path.of("src/test/java/com/example/OrderServiceTest.java")),
                snapshot.proofRelevantPaths()
        );
        assertTrue(snapshot.changedPaths().contains(Path.of("README.md")));
    }

    @Test
    void packageInfoInvalidatesProofButIsNotAMutationTarget() throws Exception {
        Path root = project("package-info");
        commit(root, "baseline");
        Path packageInfo = root.resolve("src/main/java/com/example/package-info.java");
        Files.writeString(packageInfo, "package com.example;\n");

        GitChangeService.DiffSnapshot snapshot = new GitChangeService().snapshot(root);

        assertEquals(List.of(root.relativize(packageInfo)), snapshot.changedProductionPaths());
        assertTrue(snapshot.existingProductionPaths().isEmpty());
        assertTrue(snapshot.proofRelevantPaths().contains(root.relativize(packageInfo)));
    }

    @Test
    void changedLineRangesPreserveSeparateHunksAndMergeAdjacentLines() throws Exception {
        Path root = project("line-ranges");
        Path source = source(root);
        Files.writeString(source, numberedSource("one", "two", "three", "four", "five", "six", "seven"));
        commit(root, "baseline");
        git(root, "switch", "-qc", "feature/line-ranges");
        Files.writeString(source, numberedSource("one", "TWO", "THREE", "four", "five", "SIX", "seven"));
        commit(root, "separate hunks");
        GitChangeService service = new GitChangeService();
        GitChangeService.DiffSnapshot snapshot = service.snapshot(root);

        assertEquals(
                List.of(new GitChangeService.LineRange(4, 5), new GitChangeService.LineRange(8, 8)),
                service.changedLineRanges(snapshot).get(root.relativize(source))
        );
    }

    @Test
    void symbolicOriginHeadSelectsTheConfiguredLocalDefaultBranch() throws Exception {
        Path root = project("symbolic-default");
        Path source = source(root);
        commit(root, "baseline");
        git(root, "update-ref", "refs/remotes/origin/trunk", "HEAD");
        git(root, "symbolic-ref", "refs/remotes/origin/HEAD", "refs/remotes/origin/trunk");
        git(root, "switch", "-qc", "feature/symbolic");
        Files.writeString(source, "package com.example; class OrderService { int symbolic; }\n");
        commit(root, "feature change");

        GitChangeService.DiffSnapshot snapshot = new GitChangeService().snapshot(root);

        assertEquals("merge-base(origin/trunk,HEAD)", snapshot.baselineDescription());
        assertTrue(snapshot.hasProductionChanges());
    }

    @Test
    void emptyUntrackedJavaFileHasNoInventedChangedLineRange() throws Exception {
        Path root = project("empty-untracked");
        commit(root, "baseline");
        Path empty = root.resolve("src/main/java/com/example/EmptyService.java");
        Files.writeString(empty, "");
        GitChangeService service = new GitChangeService();
        GitChangeService.DiffSnapshot snapshot = service.snapshot(root);

        assertTrue(service.changedLineRanges(snapshot).get(root.relativize(empty)).isEmpty());
    }

    @Test
    void lineRangeRejectsInvalidBoundsAndReportsExactMembership() {
        GitChangeService.LineRange range = new GitChangeService.LineRange(3, 5);

        assertTrue(range.contains(3));
        assertTrue(range.contains(5));
        assertEquals(3, range.count());
        assertThrows(IllegalArgumentException.class, () -> new GitChangeService.LineRange(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new GitChangeService.LineRange(5, 4));
    }

    @Test
    void nulParserAndRangeMergerHandleBoundaryInputsDeterministically() {
        GitChangeService service = new GitChangeService();

        assertTrue(service.nulSeparated(null).isEmpty());
        assertTrue(service.nulSeparated("").isEmpty());
        assertEquals(List.of("one"), service.nulSeparated("one"));
        assertEquals(List.of("one", "two"), service.nulSeparated("\0one\0\0two\0"));
        assertEquals(
                List.of(new GitChangeService.LineRange(2, 8), new GitChangeService.LineRange(10, 11)),
                service.mergeRanges(List.of(
                        new GitChangeService.LineRange(10, 11),
                        new GitChangeService.LineRange(2, 8),
                        new GitChangeService.LineRange(3, 4)
                ))
        );
    }

    @Test
    void proofPathClassifiersCoverJavaBuildWrapperAndIrrelevantPaths() {
        assertTrue(GitChangeService.isProductionJavaPath(Path.of("src/main/java/com/example/App.java")));
        assertTrue(!GitChangeService.isProductionJavaPath(Path.of("src/test/java/com/example/AppTest.java")));
        assertTrue(!GitChangeService.isProductionJavaPath(Path.of("tools/App.java")));
        assertTrue(!GitChangeService.isProductionJavaPath(Path.of("src/test/src/main/java/App.java")));
        assertTrue(GitChangeService.affectsJavaProof(Path.of("src/test/java/com/example/AppTest.java")));
        assertTrue(GitChangeService.affectsJavaProof(Path.of("pom.xml")));
        assertTrue(GitChangeService.affectsJavaProof(Path.of("mvnw")));
        assertTrue(GitChangeService.affectsJavaProof(Path.of(".mvn/wrapper/maven-wrapper.properties")));
        assertTrue(GitChangeService.affectsJavaProof(Path.of("gradle/wrapper/gradle-wrapper.properties")));
        assertTrue(!GitChangeService.affectsJavaProof(Path.of("README.md")));
    }

    @Test
    void missingUntrackedFileAndUnavailableGitMetadataFailWithExactContext() throws Exception {
        Path root = project("vanished-untracked");
        commit(root, "baseline");
        Path added = root.resolve("src/main/java/com/example/VanishedService.java");
        Files.writeString(added, "package com.example; class VanishedService {}\n");
        GitChangeService service = new GitChangeService();
        GitChangeService.DiffSnapshot snapshot = service.snapshot(root);
        Files.delete(added);

        IllegalStateException readFailure = assertThrows(
                IllegalStateException.class,
                () -> service.changedLineRanges(snapshot)
        );
        assertTrue(readFailure.getMessage().contains("untracked Java source"));

        Path metadata = root.resolve(".git");
        Path movedMetadata = root.resolveSibling(root.getFileName() + "-git-metadata");
        Files.move(metadata, movedMetadata);
        IllegalStateException lineFailure = assertThrows(
                IllegalStateException.class,
                () -> service.changedLineRanges(snapshot)
        );
        assertTrue(lineFailure.getMessage().contains("calculate changed lines"));
        IllegalStateException gitFailure = assertThrows(
                IllegalStateException.class,
                () -> service.snapshot(root)
        );
        assertTrue(gitFailure.getMessage().contains("requires a Git worktree"));
    }

    @Test
    void defaultBranchHelpersHandleBlankShortAndFullyQualifiedRefs() throws Exception {
        GitChangeService service = new GitChangeService(" ");

        assertTrue(!service.isDefaultBranch("", "main"));
        assertTrue(service.isDefaultBranch("main", "main"));
        assertTrue(service.isDefaultBranch("main", "refs/heads/main"));
        assertTrue(service.isDefaultBranch("trunk", "refs/remotes/origin/trunk"));
        assertEquals("origin/main", service.shortRef("refs/remotes/origin/main"));

        Path root = project("blank-explicit-base");
        commit(root, "baseline");
        assertEquals("HEAD", service.snapshot(root).baselineDescription());
    }

    private String numberedSource(String... values) {
        StringBuilder source = new StringBuilder("package com.example;\nclass OrderService {\n");
        for (String value : values) {
            source.append("  // ").append(value).append('\n');
        }
        return source.append("}\n").toString();
    }

    private Path project(String name) throws Exception {
        Path root = tempDir.resolve(name);
        Files.createDirectories(root);
        Files.writeString(root.resolve("pom.xml"), "<project/>\n");
        Path source = source(root);
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package com.example; class OrderService {}\n");
        git(root, "init", "-q", "-b", "main");
        git(root, "config", "user.name", "JAIPilot Test");
        git(root, "config", "user.email", "test@jaipilot.local");
        return root;
    }

    private Path source(Path root) {
        return root.resolve("src/main/java/com/example/OrderService.java");
    }

    private void commit(Path root, String message) throws Exception {
        git(root, "add", ".");
        git(root, "commit", "-qm", message);
    }

    private void git(Path root, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(root.toFile()).start();
        int status = process.waitFor();
        String errors = new String(process.getErrorStream().readAllBytes());
        assertEquals(0, status, errors);
    }
}
