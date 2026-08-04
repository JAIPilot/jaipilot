# JAIPilot Project Memory

This is the durable implementation memory for the JAIPilot enterprise Java toolkit. Keep evolving
facts here, stable repository rules in `AGENTS.md`, and reusable agent procedures in
`plugins/jaipilot/skills/`.

## Current Direction

- Version 3 is skills-first: Codex and Claude Code install one shared plugin containing Agent Skills,
  a pinned local bootstrap, and deterministic toolkit code.
- `jaipilot` is a short-lived, structured-JSON internal agent runner. It persists locked workflow metadata
  locally so prepare, edit, validate, and apply can span separate host command invocations.
- There is no extra transport layer, user command shell, custom backend, hosted generation API,
  embedded model, or source upload.
- Codex or Claude owns reasoning and source edits. JAIPilot owns deterministic discovery, target
  selection, isolation, OpenRewrite, validation evidence, drift protection, and transactional apply.

## Primary Toolkit Surface

- `jaipilot inspect`: read build, project, coverage, and active-run metadata.
- `jaipilot prepare-tests`: clean-baseline and isolate named, changed, all, or fresh-coverage targets.
- `jaipilot prepare-cleanup`: clean-baseline, isolate, and run exact-scoped OpenRewrite first.
- `jaipilot status`: inspect persisted run state.
- `jaipilot validate`: enforce scope, build, execution, coverage, and drift gates.
- `jaipilot apply --confirm`: apply only an immediately validated unchanged candidate transactionally.
- `jaipilot discard`: remove an abandoned candidate.

## Workflow Invariants

- Default test coverage target is 80%.
- Maven and Gradle wrappers are preferred only when both launcher and wrapper metadata are valid.
- Fresh coverage invalidates recognized XML first, runs the clean suite, and never falls back to
  stale or partial reports after failure.
- Test generation may change only `src/test/java/**/*.java`; changed tests require newly generated
  non-zero Surefire, Failsafe, or Gradle XML execution evidence.
- Cleanup runs pinned OpenRewrite `CodeCleanup` and `CommonStaticAnalysis` recipes in one exactly
  scoped sandbox pass, then allows contextual review of selected production Java and related tests.
- Validation rejects deletion, symbolic paths, invalid scope, build-time source drift, and missing
  execution evidence. Apply rejects post-validation candidate drift and live-worktree drift.
- Direct runs persist beneath `JAIPILOT_STATE_HOME`, `XDG_STATE_HOME/jaipilot`, or the user's local
  state directory. Metadata writes are atomic, directories are owner-only where POSIX permissions
  exist, run operations are file-locked, and creation uses a brief global registry lock.
- Active work is capped at four runs globally and one per real project. Runs expire after two hours.

## Clean Code and SonarQube Boundary

- JAIPilot includes the full local clean-code journey: scope, deterministic OpenRewrite fixes,
  contextual review, relevant tests, clean behavioral proof, evidence, and safe apply.
- JAIPilot does not install, configure, or invoke a Sonar scanner; the clean-code implementation is
  native JAIPilot orchestration plus pinned OpenRewrite recipes and host-agent review.
- It is not a SonarQube clone. Do not claim formal security/data-flow analysis, centralized quality
  gates, dashboards, portfolios, compliance, or historical governance.
- Compare identical revisions using accepted verified fixes, false positives, escaped defects,
  regressions, generated-test quality, coverage, elapsed time, reviewer actions, cancellation, and recovery.

## Packaging and Delivery

- Maven artifact: `com.jaipilot:jaipilot-toolkit`.
- Published archives contain a jlink runtime, shaded toolkit, runner, installer, and
  `plugins/jaipilot`.
- The plugin-local bootstrap requires no package manager. First launch checksum-verifies and caches
  the matching pinned GitHub archive.
- The installer writes only to its private app directory and never creates a PATH or global launcher.
- Codex marketplace: `.agents/plugins/marketplace.json`.
- Claude marketplace: `.claude-plugin/marketplace.json`.
- Shared plugin: `plugins/jaipilot`, containing `jaipilot-generate-tests` and `jaipilot-clean-java`.

## Performance Baseline

On 2026-08-04, after one warmup, five complete bundled `jaipilot inspect --project .` runs on the
same macOS x64 machine and clean v3.0.0 build were 0.557, 0.542, 0.540, 0.551, and 0.546 seconds:
median 0.546 seconds and nearest-rank p95 0.557 seconds. These measurements include process startup and
project discovery, but exclude builds and host-agent work.

## Keep Fresh

Update this file when runner operations, persistence rules, workflow gates, recipes, run
limits, packaging, skills, or verified performance evidence change.
