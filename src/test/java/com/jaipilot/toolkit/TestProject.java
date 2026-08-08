package com.jaipilot.toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class TestProject {

    private TestProject() {
    }

    static Path maven(Path parent, String name) throws Exception {
        Path root = parent.resolve(name);
        Path source = root.resolve("src/main/java/com/example/OrderService.java");
        Files.createDirectories(source.getParent());
        Files.writeString(root.resolve("pom.xml"), pom());
        Files.writeString(source, "package com.example; public class OrderService { int total() { return 1; } }\n");
        return root;
    }

    static Path gitMaven(Path parent, String name) throws Exception {
        Path root = maven(parent, name);
        git(root, "init", "-q", "-b", "main");
        git(root, "config", "user.name", "JAIPilot Test");
        git(root, "config", "user.email", "test@jaipilot.local");
        commit(root, "baseline");
        return root;
    }

    static void successfulWrapper(Path root) throws Exception {
        Path wrapper = root.resolve("mvnw");
        Files.writeString(wrapper, "#!/bin/sh\nexit 0\n");
        if (!wrapper.toFile().setExecutable(true, false)) {
            throw new IllegalStateException("Could not make fixture wrapper executable.");
        }
        Path metadata = root.resolve(".mvn/wrapper/maven-wrapper.properties");
        Files.createDirectories(metadata.getParent());
        Files.writeString(metadata, "distributionUrl=https://repo.maven.apache.org/maven2\n");
    }

    static void commit(Path root, String message) throws Exception {
        git(root, "add", "-A");
        git(root, "commit", "-qm", message);
    }

    static void git(Path root, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(root.toFile()).start();
        int status = process.waitFor();
        assertEquals(0, status, new String(process.getErrorStream().readAllBytes()));
    }

    static String pom() {
        return """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId><artifactId>fixture</artifactId><version>1.0.0</version>
                </project>
                """;
    }
}
