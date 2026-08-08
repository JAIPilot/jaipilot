package com.jaipilot.toolkit.core;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves canonical local repository identity and an optional normalized GitHub origin, without network access. */
public final class RepositoryMetadataService {

    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(3);
    private static final int MAX_OUTPUT_CHARACTERS = 4_096;
    private static final int MAX_REMOTE_LENGTH = 2_048;
    private static final Pattern SCP_GITHUB_REMOTE = Pattern.compile(
            "\\Agit@github\\.com:([^/]+)/([^/]+)/?\\z",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern GITHUB_SEGMENT = Pattern.compile("[A-Za-z0-9._-]+");

    private final ProcessExecutor processes = new ProcessExecutor();

    public RepositoryMetadata resolve(Path requestedRoot) {
        Path requested = canonicalExistingDirectory(Objects.requireNonNull(requestedRoot, "projectRoot"));
        String discoveredRoot = readGitValue(requested, "rev-parse", "--show-toplevel");
        Path projectRoot = discoveredRoot == null
                ? requested
                : canonicalExistingDirectory(Path.of(discoveredRoot));
        String githubUrl = normalizeGithubUrl(readGitValue(
                projectRoot,
                "config",
                "--local",
                "--get",
                "remote.origin.url"
        ));
        String fallback = projectRoot.getFileName() == null
                ? projectRoot.toString()
                : projectRoot.getFileName().toString();
        String displayName = githubUrl == null
                ? fallback
                : githubUrl.substring("https://github.com/".length());
        return new RepositoryMetadata(projectRoot, displayName, githubUrl);
    }

    private Path canonicalExistingDirectory(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(absolute)) {
            throw new IllegalArgumentException("Repository directory does not exist: " + absolute);
        }
        try {
            return absolute.toRealPath();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Repository directory is unavailable: " + absolute, exception);
        }
    }

    private String readGitValue(Path projectRoot, String... arguments) {
        ArrayList<String> command = new ArrayList<>();
        command.add("git");
        command.add("--no-optional-locks");
        command.addAll(List.of(arguments));
        try {
            ProcessExecutor.ExecutionResult result = processes.execute(
                    command,
                    projectRoot,
                    GIT_TIMEOUT,
                    false,
                    null,
                    null,
                    ProcessExecutor.ProgressListener.noOp(),
                    ProcessExecutor.OutputListener.noOp(),
                    ProcessExecutor.isolatedEnvironment(GitProcessEnvironment.sanitizedCopy()),
                    MAX_OUTPUT_CHARACTERS
            );
            if (result.timedOut() || result.exitCode() != 0) {
                return null;
            }
            String output = result.output().strip();
            return output.isEmpty() ? null : output;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException exception) {
            return null;
        }
    }

    static String normalizeGithubUrl(String rawRemote) {
        String remote = cleanRemote(rawRemote);
        if (remote == null) {
            return null;
        }
        Matcher scp = SCP_GITHUB_REMOTE.matcher(remote);
        if (scp.matches()) {
            return githubRepository(scp.group(1), scp.group(2));
        }
        URI uri = parseUri(remote);
        return validGithubUri(uri) ? repositoryFromPath(uri.getRawPath()) : null;
    }

    private static String cleanRemote(String rawRemote) {
        if (rawRemote == null) {
            return null;
        }
        String remote = rawRemote.strip();
        return remote.isEmpty() || remote.length() > MAX_REMOTE_LENGTH
                || remote.chars().anyMatch(Character::isWhitespace) ? null : remote;
    }

    private static boolean validGithubUri(URI uri) {
        if (uri == null || uri.isOpaque() || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            return false;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        return switch (scheme) {
            case "https" -> validGithubHttps(uri);
            case "ssh" -> validGithubSsh(uri);
            default -> false;
        };
    }

    private static boolean validGithubHttps(URI uri) {
        return "github.com".equals(host(uri))
                && uri.getRawUserInfo() == null
                && standardPort(uri, 443);
    }

    private static boolean validGithubSsh(URI uri) {
        if (!"git".equals(uri.getRawUserInfo())) {
            return false;
        }
        return "github.com".equals(host(uri)) && standardPort(uri, 22)
                || "ssh.github.com".equals(host(uri)) && uri.getPort() == 443;
    }

    private static URI parseUri(String remote) {
        try {
            return new URI(remote);
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private static String host(URI uri) {
        return uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
    }

    private static boolean standardPort(URI uri, int expected) {
        return uri.getPort() == -1 || uri.getPort() == expected;
    }

    private static String repositoryFromPath(String rawPath) {
        if (rawPath == null || !rawPath.startsWith("/")) {
            return null;
        }
        String path = rawPath.substring(1);
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String[] segments = path.split("/", -1);
        return segments.length == 2 ? githubRepository(segments[0], segments[1]) : null;
    }

    private static String githubRepository(String owner, String rawRepository) {
        String repository = rawRepository.toLowerCase(Locale.ROOT).endsWith(".git")
                ? rawRepository.substring(0, rawRepository.length() - 4)
                : rawRepository;
        if (!validSegment(owner) || !validSegment(repository)) {
            return null;
        }
        return "https://github.com/" + owner + "/" + repository;
    }

    private static boolean validSegment(String value) {
        return value.length() <= 255
                && !".".equals(value)
                && !"..".equals(value)
                && GITHUB_SEGMENT.matcher(value).matches();
    }

    public record RepositoryMetadata(Path projectRoot, String displayName, String githubUrl) {
    }
}
