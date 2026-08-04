# JAIPilot

JAIPilot is the local-first enterprise Java toolkit for Codex and Claude Code. Its Agent Skills
coordinate high-coverage unit-test generation and OpenRewrite-first clean-code refactoring while
the bundled deterministic runner owns project discovery, isolation, real-build verification,
execution evidence, coverage, drift checks, and transactional apply.

The plugin invokes `bin/jaipilot`. On first use, that pinned launcher uses the plugin-local installer
to retrieve the matching GitHub release, verify its published checksum, and cache the bundled Java
runtime. Node.js and npm are not required. Set `JAIPILOT_TOOLKIT_EXECUTABLE` to an existing runner
for offline or managed setups.

JAIPilot does not upload source, invoke a nested model, or provide a hosted backend. Codex or Claude
Code performs contextual reasoning and edits inside the isolated workspace.
