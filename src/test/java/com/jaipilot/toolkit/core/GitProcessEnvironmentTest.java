package com.jaipilot.toolkit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GitProcessEnvironmentTest {

    @Test
    void removesRepositoryRoutingAndConfigWhilePreservingOrdinaryEnvironment() {
        Map<String, String> sanitized = GitProcessEnvironment.sanitizedCopy(Map.of(
                "PATH", "/bin",
                "HOME", "/owner",
                "GIT_DIR", "/hostile",
                "git_index_file", "/hostile/index",
                "GIT_CONFIG_KEY_0", "core.fsmonitor",
                "GCM_INTERACTIVE", "Always"
        ));

        assertEquals("/bin", sanitized.get("PATH"));
        assertEquals("/owner", sanitized.get("HOME"));
        assertFalse(sanitized.containsKey("GIT_DIR"));
        assertFalse(sanitized.containsKey("git_index_file"));
        assertFalse(sanitized.containsKey("GIT_CONFIG_KEY_0"));
        assertEquals("0", sanitized.get("GIT_CONFIG_COUNT"));
        assertEquals("0", sanitized.get("GIT_OPTIONAL_LOCKS"));
        assertEquals("0", sanitized.get("GIT_TERMINAL_PROMPT"));
    }

    @Test
    void unsafeNamesAreCaseInsensitiveAndNarrow() {
        assertTrue(GitProcessEnvironment.unsafeVariable("git_work_tree"));
        assertTrue(GitProcessEnvironment.unsafeVariable("GIT_ALTERNATE_OBJECT_DIRECTORIES"));
        assertTrue(GitProcessEnvironment.unsafeVariable("gcm_interactive"));
        assertFalse(GitProcessEnvironment.unsafeVariable("GITHUB_TOKEN"));
        assertFalse(GitProcessEnvironment.unsafeVariable("PATH"));
    }
}
