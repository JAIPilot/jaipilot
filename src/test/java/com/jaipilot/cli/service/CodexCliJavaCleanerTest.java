package com.jaipilot.cli.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jaipilot.cli.ui.TerminalUi;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodexCliJavaCleanerTest {

    @TempDir
    Path tempDir;

    private final ProjectFileService fileService = new ProjectFileService();

    @Test
    void verifiedCandidateMergesOnlyTargetAndRelevantTests() throws Exception {
        SampleProject sample = createSampleProject();
        List<Path> verifiedRoots = new ArrayList<>();
        CodexCliJavaCleaner cleaner = cleaner(
                (sandbox, model, prompt, ui, logs, writer) -> {
                    Files.writeString(sandbox.resolve(sample.targetRelative()), improvedSource());
                    Path test = sandbox.resolve("src/test/java/com/example/OrderServiceTest.java");
                    Files.createDirectories(test.getParent());
                    Files.writeString(test, "package com.example; class OrderServiceTest {}\n");
                    assertTrue(prompt.contains(sandbox.resolve(sample.targetRelative()).toString()));
                    return runResult();
                },
                (root, ui, logs, writer) -> {
                    verifiedRoots.add(root);
                    return verificationResult();
                }
        );

        CodexCliJavaCleaner.CleanupResult result = cleaner.clean(
                sample.root(),
                List.of(sample.descriptor()),
                null,
                false,
                ui(),
                false,
                new PrintWriter(new StringWriter(), true)
        );

        assertEquals(improvedSource(), Files.readString(sample.target()));
        assertTrue(Files.isRegularFile(sample.root().resolve("src/test/java/com/example/OrderServiceTest.java")));
        assertEquals(sample.originalUnrelated(), Files.readString(sample.unrelated()));
        assertEquals(2, verifiedRoots.size());
        assertEquals(sample.root(), verifiedRoots.get(0));
        assertTrue(verifiedRoots.get(1).getFileName().toString().startsWith("jaipilot-clean-"));
        assertTrue(result.applied());
        assertEquals(
                List.of(
                        Path.of("src/main/java/com/example/OrderService.java"),
                        Path.of("src/test/java/com/example/OrderServiceTest.java")
                ),
                result.changedRelativePaths()
        );
    }

    @Test
    void checkModeVerifiesButDoesNotMergeCandidate() throws Exception {
        SampleProject sample = createSampleProject();
        AtomicInteger verifications = new AtomicInteger();
        CodexCliJavaCleaner cleaner = cleaner(
                (sandbox, model, prompt, ui, logs, writer) -> {
                    Files.writeString(sandbox.resolve(sample.targetRelative()), improvedSource());
                    return runResult();
                },
                (root, ui, logs, writer) -> {
                    verifications.incrementAndGet();
                    return verificationResult();
                }
        );

        CodexCliJavaCleaner.CleanupResult result = cleaner.clean(
                sample.root(),
                List.of(sample.descriptor()),
                null,
                true,
                ui(),
                false,
                new PrintWriter(new StringWriter(), true)
        );

        assertEquals(sample.originalTarget(), Files.readString(sample.target()));
        assertFalse(result.applied());
        assertTrue(result.checkOnly());
        assertEquals(2, verifications.get());
        assertEquals(List.of(sample.targetRelative()), result.changedRelativePaths());
    }

    @Test
    void outOfScopeProductionEditIsRejectedWithoutTouchingProject() throws Exception {
        SampleProject sample = createSampleProject();
        AtomicInteger verifications = new AtomicInteger();
        CodexCliJavaCleaner cleaner = cleaner(
                (sandbox, model, prompt, ui, logs, writer) -> {
                    Files.writeString(sandbox.resolve(sample.targetRelative()), improvedSource());
                    Files.writeString(sandbox.resolve(sample.unrelatedRelative()), "package com.example; class Other { int changed; }\n");
                    return runResult();
                },
                (root, ui, logs, writer) -> {
                    verifications.incrementAndGet();
                    return verificationResult();
                }
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> cleaner.clean(
                        sample.root(),
                        List.of(sample.descriptor()),
                        null,
                        false,
                        ui(),
                        false,
                        new PrintWriter(new StringWriter(), true)
                )
        );

        assertTrue(failure.getMessage().contains("outside the production/test allowlist"));
        assertEquals(sample.originalTarget(), Files.readString(sample.target()));
        assertEquals(sample.originalUnrelated(), Files.readString(sample.unrelated()));
        assertEquals(1, verifications.get());
    }

    @Test
    void deletionIsRejectedWithoutTouchingProject() throws Exception {
        SampleProject sample = createSampleProject();
        CodexCliJavaCleaner cleaner = cleaner(
                (sandbox, model, prompt, ui, logs, writer) -> {
                    Files.delete(sandbox.resolve(sample.targetRelative()));
                    return runResult();
                },
                (root, ui, logs, writer) -> verificationResult()
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> cleaner.clean(
                        sample.root(),
                        List.of(sample.descriptor()),
                        null,
                        false,
                        ui(),
                        false,
                        new PrintWriter(new StringWriter(), true)
                )
        );

        assertTrue(failure.getMessage().contains("deletions are not allowed"));
        assertEquals(sample.originalTarget(), Files.readString(sample.target()));
    }

    @Test
    void concurrentProjectDriftAbortsTheTransactionalMerge() throws Exception {
        SampleProject sample = createSampleProject();
        CodexCliJavaCleaner cleaner = cleaner(
                (sandbox, model, prompt, ui, logs, writer) -> {
                    Files.writeString(sandbox.resolve(sample.targetRelative()), improvedSource());
                    Files.writeString(sample.unrelated(), "user edit\n");
                    return runResult();
                },
                (root, ui, logs, writer) -> verificationResult()
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> cleaner.clean(
                        sample.root(),
                        List.of(sample.descriptor()),
                        null,
                        false,
                        ui(),
                        false,
                        new PrintWriter(new StringWriter(), true)
                )
        );

        assertTrue(failure.getMessage().contains("Project files changed while cleanup was running"));
        assertEquals(sample.originalTarget(), Files.readString(sample.target()));
        assertEquals("user edit\n", Files.readString(sample.unrelated()));
    }

    @Test
    void concurrentProjectDriftAlsoInvalidatesCheckMode() throws Exception {
        SampleProject sample = createSampleProject();
        CodexCliJavaCleaner cleaner = cleaner(
                (sandbox, model, prompt, ui, logs, writer) -> {
                    Files.writeString(sandbox.resolve(sample.targetRelative()), improvedSource());
                    Files.writeString(sample.unrelated(), "user edit\n");
                    return runResult();
                },
                (root, ui, logs, writer) -> verificationResult()
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> cleaner.clean(
                        sample.root(),
                        List.of(sample.descriptor()),
                        null,
                        true,
                        ui(),
                        false,
                        new PrintWriter(new StringWriter(), true)
                )
        );

        assertTrue(failure.getMessage().contains("Project files changed while cleanup was running"));
        assertEquals(sample.originalTarget(), Files.readString(sample.target()));
        assertEquals("user edit\n", Files.readString(sample.unrelated()));
    }

    @Test
    void candidateBuildCannotRewriteFilesAfterTheAllowlistCheck() throws Exception {
        SampleProject sample = createSampleProject();
        AtomicInteger verifications = new AtomicInteger();
        CodexCliJavaCleaner cleaner = cleaner(
                (sandbox, model, prompt, ui, logs, writer) -> {
                    Files.writeString(sandbox.resolve(sample.targetRelative()), improvedSource());
                    return runResult();
                },
                (root, ui, logs, writer) -> {
                    if (verifications.incrementAndGet() == 2) {
                        fileService.writeFile(root.resolve(sample.unrelatedRelative()), "build rewrite\n");
                    }
                    return verificationResult();
                }
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> cleaner.clean(
                        sample.root(),
                        List.of(sample.descriptor()),
                        null,
                        false,
                        ui(),
                        false,
                        new PrintWriter(new StringWriter(), true)
                )
        );

        assertTrue(failure.getMessage().contains("candidate build changed files"));
        assertEquals(sample.originalTarget(), Files.readString(sample.target()));
        assertEquals(sample.originalUnrelated(), Files.readString(sample.unrelated()));
    }

    @Test
    void failedCandidateVerificationLeavesTheProjectUntouched() throws Exception {
        SampleProject sample = createSampleProject();
        AtomicInteger verifications = new AtomicInteger();
        CodexCliJavaCleaner cleaner = cleaner(
                (sandbox, model, prompt, ui, logs, writer) -> {
                    Files.writeString(sandbox.resolve(sample.targetRelative()), improvedSource());
                    return runResult();
                },
                (root, ui, logs, writer) -> {
                    if (verifications.incrementAndGet() == 2) {
                        throw new IllegalStateException("candidate tests failed");
                    }
                    return verificationResult();
                }
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> cleaner.clean(
                        sample.root(),
                        List.of(sample.descriptor()),
                        null,
                        false,
                        ui(),
                        false,
                        new PrintWriter(new StringWriter(), true)
                )
        );

        assertEquals("candidate tests failed", failure.getMessage());
        assertEquals(sample.originalTarget(), Files.readString(sample.target()));
        assertEquals(2, verifications.get());
    }

    private CodexCliJavaCleaner cleaner(
            CodexCliJavaCleaner.CleanupAgent agent,
            CodexCliJavaCleaner.BuildVerifier verifier
    ) {
        return new CodexCliJavaCleaner(
                fileService,
                new PromptTemplateService(fileService),
                agent,
                verifier
        );
    }

    private SampleProject createSampleProject() throws Exception {
        Path root = tempDir.resolve("sample");
        Files.createDirectories(root);
        Files.writeString(root.resolve("pom.xml"), "<project/>\n");
        Path targetRelative = Path.of("src/main/java/com/example/OrderService.java");
        Path unrelatedRelative = Path.of("src/main/java/com/example/Other.java");
        Path target = root.resolve(targetRelative);
        Path unrelated = root.resolve(unrelatedRelative);
        Files.createDirectories(target.getParent());
        String originalTarget = "package com.example; class OrderService { int value() { return 1; } }\n";
        String originalUnrelated = "package com.example; class Other {}\n";
        Files.writeString(target, originalTarget);
        Files.writeString(unrelated, originalUnrelated);
        JavaProjectService.JavaClassDescriptor descriptor = new JavaProjectService.JavaClassDescriptor(
                root,
                root,
                target,
                "com.example",
                "OrderService",
                "com.example.OrderService"
        );
        return new SampleProject(
                root,
                target,
                unrelated,
                targetRelative,
                unrelatedRelative,
                originalTarget,
                originalUnrelated,
                descriptor
        );
    }

    private CodexCliRunner.RunResult runResult() {
        return new CodexCliRunner.RunResult(
                new CodexCliRunner.Usage(100L, 50L, 20L, 10L),
                Duration.ofMillis(25)
        );
    }

    private JavaBuildVerificationService.VerificationResult verificationResult() {
        return new JavaBuildVerificationService.VerificationResult(
                List.of("verify"),
                Duration.ofMillis(10)
        );
    }

    private TerminalUi ui() {
        return new TerminalUi(new PrintWriter(new StringWriter(), true));
    }

    private String improvedSource() {
        return "package com.example; final class OrderService { int value() { return 1; } }\n";
    }

    private record SampleProject(
            Path root,
            Path target,
            Path unrelated,
            Path targetRelative,
            Path unrelatedRelative,
            String originalTarget,
            String originalUnrelated,
            JavaProjectService.JavaClassDescriptor descriptor
    ) {
    }
}
