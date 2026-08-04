# JAIPilot

**Generate tests. Clean Java. Prove every change.**

JAIPilot is the proof-driven Java engineering plugin for Codex and Claude Code. Its Agent Skills
guide contextual test generation and OpenRewrite-first cleanup while a deterministic local runner
owns project discovery, isolation, real-build verification, execution evidence, fresh coverage,
drift detection, and transactional apply.

The pinned `bin/jaipilot` launcher downloads the matching GitHub release on first use, verifies its
published SHA-256 checksum, and caches a private bundled runtime. Node.js and npm are not required.
Set `JAIPILOT_TOOLKIT_EXECUTABLE` to an approved runner for managed or offline environments.

JAIPilot has no hosted backend, never uploads source, and does not invoke another model. Codex or
Claude Code performs contextual reasoning and edits only inside the isolated workspace.

Learn more at <https://github.com/JAIPilot/jaipilot>.
