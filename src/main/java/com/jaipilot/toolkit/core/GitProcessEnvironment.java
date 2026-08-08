package com.jaipilot.toolkit.core;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Creates a prompt-free Git environment without inherited repository routing or config injection. */
public final class GitProcessEnvironment {

    private GitProcessEnvironment() {
    }

    static Map<String, String> sanitizedCopy() {
        return sanitizedCopy(System.getenv());
    }

    static Map<String, String> sanitizedCopy(Map<String, String> inherited) {
        Map<String, String> environment = new LinkedHashMap<>(inherited);
        environment.keySet().removeIf(GitProcessEnvironment::unsafeVariable);
        environment.put("GIT_CONFIG_COUNT", "0");
        environment.put("GIT_CONFIG_NOSYSTEM", "1");
        environment.put("GIT_CONFIG_GLOBAL", nullDevice());
        environment.put("GIT_NO_REPLACE_OBJECTS", "1");
        environment.put("GIT_OPTIONAL_LOCKS", "0");
        environment.put("GIT_TERMINAL_PROMPT", "0");
        environment.put("GCM_INTERACTIVE", "Never");
        environment.put("GIT_PAGER", "cat");
        environment.put("LC_ALL", "C");
        return environment;
    }

    public static boolean unsafeVariable(String name) {
        String normalized = name.toUpperCase(Locale.ROOT);
        return normalized.startsWith("GIT_") || "GCM_INTERACTIVE".equals(normalized);
    }

    private static String nullDevice() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")
                ? "NUL"
                : "/dev/null";
    }
}
