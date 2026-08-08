# JAIPilot

**Deterministic guardrails that cut down agentic drift for high-quality agentic Java changes.**

JAIPilot is a local, backend-free Java Enterprise Harness for Codex, Claude Code, and any stdio MCP
host. The coding agent owns reasoning, edits, retries, cancellation, Git, and user interaction.
JAIPilot owns deterministic repository scope, quality evidence, OpenRewrite cleanup, clean-build
proof, exact fingerprints, and the current-evidence dashboard.

The plugin exposes six synchronous MCP tools:

- `jaipilot_inspect`
- `jaipilot_snapshot`
- `jaipilot_quality`
- `jaipilot_rewrite`
- `jaipilot_diff_gate`
- `jaipilot_prove_diff`

Its Agent Skills are `jaipilot-review-diff`, `jaipilot-generate-tests`, and
`jaipilot-clean-java`.

At SessionStart, a lightweight shell check first requires a Maven or Gradle build plus Java source.
Only then does a detached read-only snapshot register the repository, record its local GitHub origin,
initialize current metrics, and start the loopback dashboard. The direct `git commit` post-tool hook
queues the same Java-only refresh. The Stop hook checks the current Java/build diff only in applicable
projects. Other directories exit silently without starting Java or creating JAIPilot state. These are
coding-tool hooks, not a global Git hook.

The private machine-wide dashboard is normally available at <http://127.0.0.1:7433/>. Its repository
selector reads the common owner-private state store and shows every retained Java repository, its
canonical local path, GitHub link when present, current quality and findings, exact-fingerprint proof
freshness, applicable ArchUnit/coverage/PIT gates, and observed snapshot deltas. It has no telemetry,
backend, command history, or usage analytics.

JAIPilot never commits, pushes, opens a PR, applies a hidden workspace, or raises a GitHub issue.
When a structured failure appears to be a product defect, the host agent may ask the user before
opening a sanitized issue at <https://github.com/JAIPilot/jaipilot/issues/new/choose>.

Learn more at <https://github.com/JAIPilot/jaipilot>.
