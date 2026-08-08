package com.jaipilot.toolkit.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/** Applies owner-only permissions when the filesystem supports POSIX attributes. */
public final class OwnerPermissions {

    private OwnerPermissions() {
    }

    public static void set(Path path, Set<PosixFilePermission> permissions) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, permissions);
        }
    }
}
