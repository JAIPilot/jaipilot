package com.jaipilot.toolkit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RepositoryMetadataServiceTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @MethodSource("githubRemotes")
    void normalizesOnlyApprovedGitHubOrigins(String remote, String expected) {
        assertEquals(expected, RepositoryMetadataService.normalizeGithubUrl(remote));
    }

    static Stream<Arguments> githubRemotes() {
        return Stream.of(
                Arguments.of("https://github.com/JAIPilot/jaipilot.git", "https://github.com/JAIPilot/jaipilot"),
                Arguments.of("ssh://git@github.com/JAIPilot/jaipilot.git", "https://github.com/JAIPilot/jaipilot"),
                Arguments.of("ssh://git@ssh.github.com:443/JAIPilot/jaipilot.git", "https://github.com/JAIPilot/jaipilot"),
                Arguments.of("git@github.com:JAIPilot/jaipilot.git", "https://github.com/JAIPilot/jaipilot"),
                Arguments.of("https://user:secret@github.com/JAIPilot/jaipilot", null),
                Arguments.of("https://github.example/JAIPilot/jaipilot", null),
                Arguments.of("https://github.com/JAIPilot/jaipilot/issues", null),
                Arguments.of("file:///tmp/repository", null),
                Arguments.of("", null),
                Arguments.of(null, null)
        );
    }

    @Test
    void resolvesCanonicalGitRootAndLocalOriginWithoutWriting() throws Exception {
        Path project = Files.createDirectories(tempDir.resolve("project"));
        git(project, "init", "-q");
        git(project, "config", "user.email", "test@example.com");
        git(project, "config", "user.name", "Test");
        git(project, "remote", "add", "origin", "git@github.com:acme/widgets.git");
        Path nested = Files.createDirectories(project.resolve("module/src/main"));

        RepositoryMetadataService.RepositoryMetadata metadata = new RepositoryMetadataService().resolve(nested);

        assertEquals(project.toRealPath(), metadata.projectRoot());
        assertEquals("acme/widgets", metadata.displayName());
        assertEquals("https://github.com/acme/widgets", metadata.githubUrl());
        assertFalse(Files.exists(project.resolve(".jaipilot")));
    }

    @Test
    void usesDirectoryNameForNonGitAndRejectsMissingPaths() throws Exception {
        Path directory = Files.createDirectory(tempDir.resolve("plain-project"));
        RepositoryMetadataService.RepositoryMetadata metadata = new RepositoryMetadataService().resolve(directory);

        assertEquals(directory.toRealPath(), metadata.projectRoot());
        assertEquals("plain-project", metadata.displayName());
        assertNull(metadata.githubUrl());
        assertThrows(IllegalArgumentException.class,
                () -> new RepositoryMetadataService().resolve(tempDir.resolve("missing")));
    }

    private static void git(Path directory, String... arguments) throws Exception {
        String[] command = new String[arguments.length + 1];
        command[0] = "git";
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        Process process = new ProcessBuilder(command).directory(directory.toFile()).start();
        int status = process.waitFor();
        String error = new String(process.getErrorStream().readAllBytes());
        assertEquals(0, status, error);
    }
}
