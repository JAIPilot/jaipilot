# How JAIPilot works

JAIPilot is a deterministic evidence kernel for a host coding agent. It does not plan changes or run
a second agent workflow. Everything runs locally and source code is never uploaded.

## One loop

```text
inspect → host-agent edit → quality → prove-diff → diff-gate
```

1. `inspect` resolves the canonical Git/build root, build tool, production classes, and available
   coverage, mutation, cleanup, and architecture engines.
2. The host agent plans and edits in the user's normal branch or worktree.
3. `quality` returns bounded deterministic findings and source scorecards. When useful, the agent may
   explicitly invoke `rewrite` for a named, changed, or whole-project production scope, then review
   every OpenRewrite edit in Git.
4. `prove-diff` copies the current repository to a temporary proof workspace, runs the real clean
   build, collects fresh applicable evidence, checks drift, and writes a local receipt.
5. `diff-gate` accepts only a receipt for the exact current relevant fingerprint.

JAIPilot does not prepare a candidate, track an editable run, validate an agent-owned workspace, or
apply/discard files. The host agent already has branch, diff, process, cancellation, and recovery
controls, so duplicating them adds drift rather than reducing it.

## Repository and diff scope

Repository resolution uses only the local filesystem and local Git configuration. It canonicalizes
the Git worktree root and normalizes a GitHub `origin` for display when present; it never fetches.

On a feature branch, the diff boundary is the merge base with a locally discoverable default branch.
On the default branch, it is normally `HEAD^`. Staged, unstaged, and untracked Java/build inputs are
included. `JAIPILOT_DIFF_BASE` provides an explicit local comparison ref when the normal boundary is
not suitable.

The fingerprint includes relevant paths, file type, executable mode, symlink target, and bytes. Any
production Java, test Java, Maven/Gradle descriptor, settings file, version catalog, or build-wrapper
change invalidates the receipt.

## Proof

Proof runs in a fresh copy that excludes build output and JAIPilot state. The live repository is
checked before and after proof; drift fails the run. The proof never writes source back.

For production changes, the default gates are:

- clean Maven `verify` or Gradle `build`, with build-cache reuse disabled;
- fresh class-level execution evidence for changed test sources that map deterministically to an
  executable test class;
- 90% changed-line and 85% changed-branch JaCoCo coverage;
- 80% changed-line PIT mutation score;
- 90 changed-code quality, with no new/escalated critical or high finding; and
- complete pinned ArchUnit evidence with no package cycle involving a changed class.

Build/test-only and deletion-only changes require the clean build. Coverage, mutation, quality, or
architecture that has no meaningful target is returned as `not_applicable`, never fabricated as a
zero or pass. Missing, malformed, ambiguous, stale, or cross-module evidence fails closed.

The receipt contains the canonical project identity, fingerprint, thresholds, proof timestamp, and
applicable gate results. It contains no source. A receipt is reusable only for the same schema and
exact fingerprint.

## MCP and Agent Skills

The stdio server exposes six synchronous tools: inspect, snapshot, quality, rewrite, diff gate, and
diff proof. Synchronous tools keep lifecycle ownership with the host: progress is visible, standard
host cancellation works, and there is no detached-operation database to reconcile after a crash.

The three skills teach the host agent how to use those primitives for test generation, cleanup, and
diff review. The skills own no hidden state.

## Agent-selected execution

JAIPilot installs no SessionStart, PostToolUse, Stop, shell, or repository Git hooks. MCP startup
publishes the six tools but does not inspect or register the current directory. The host agent decides
when the task benefits from deterministic evidence and invokes only the required tool.

An explicit `snapshot` registers the repository, records its local GitHub link, computes current
whole-project quality, and starts the dashboard. `quality`, `rewrite`, `diff-gate`, and `prove-diff`
run only when selected by the agent. No file watcher or background scheduler reruns them after an edit
or commit.

## Bounded private state

State lives under `JAIPILOT_STATE_HOME`, `$XDG_STATE_HOME/jaipilot`, or
`~/.local/state/jaipilot`. Directories and files are owner-only where supported. Writes are locked,
atomic, bounded, and symlink-safe.

The machine-wide snapshot store keeps at most 64 repositories, 100 current findings per repository, and bounded
proof messages. It stores the canonical local path because the machine-wide selector must reopen the
repository; nothing is uploaded. It does not store command history, usage analytics, prompts, source,
or an editable workflow.

## Dashboard

One process binds to IPv4 loopback, preferring port 7433. It serves bundled assets and read-only
health/metrics APIs with restrictive browser headers. Each request returns the last atomic snapshot;
repository analysis never blocks the HTTP request thread.

The selected repository shows current quality/findings, proof freshness, applicable gate evidence,
and observed snapshot deltas. Proof facts remain hidden when their fingerprint does not match current
state.
