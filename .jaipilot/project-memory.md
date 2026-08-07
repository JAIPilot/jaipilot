# JAIPilot Project Memory

This is the durable implementation memory for the JAIPilot Java Enterprise Toolkit Harness. Keep evolving
facts here, stable repository rules in `AGENTS.md`, and reusable agent procedures in
`plugins/jaipilot/skills/`.

## Current Direction

- Version 3 is skills-first: Codex and Claude Code install one shared plugin containing Agent Skills,
  a pinned local bootstrap, and deterministic toolkit-harness code.
- `jaipilot` is a short-lived, structured-JSON internal agent runner. It persists locked workflow metadata
  locally so prepare, edit, validate, and apply can span separate host command invocations.
- There is no extra transport layer, user command shell, custom backend, hosted generation API,
  embedded model, or source upload.
- Codex or Claude owns reasoning and source edits. JAIPilot owns deterministic discovery, target
  selection, isolation, OpenRewrite, validation evidence, drift protection, and transactional apply.

## Primary Toolkit Harness Surface

- `jaipilot inspect`: read build, project, coverage, and active-run metadata.
- `jaipilot quality`: report deterministic findings, method complexity, duplication, debt, and scores.
- `jaipilot diff-gate`: discover committed and working-tree Java changes and check the exact local
  proof receipt without running a build.
- `jaipilot prove-diff`: prove the exact Git fingerprint in a fresh isolated workspace with a clean
  build, changed-line JaCoCo, changed-line PIT, and new-code quality evidence.
- `jaipilot prepare-tests`: clean-baseline and isolate named, changed, all, or fresh-coverage targets.
- `jaipilot prepare-cleanup`: clean-baseline, isolate, and run exact-scoped OpenRewrite first.
- `jaipilot status`: inspect persisted run state.
- `jaipilot validate`: enforce scope, build, execution, coverage, mutation, quality, and drift gates.
- `jaipilot apply --confirm`: apply only an immediately validated unchanged candidate transactionally.
- `jaipilot discard`: remove an abandoned candidate.

## Workflow Invariants

- Default test line-coverage target is 80%; default targeted PIT mutation target is 70%.
- Maven and Gradle wrappers are preferred only when both launcher and wrapper metadata are valid.
- Fresh coverage invalidates recognized XML first, runs the clean suite, and never falls back to
  stale or partial reports after failure.
- Test generation may change only `src/test/java/**/*.java`; changed tests require newly generated
  non-zero Surefire, Failsafe, or Gradle XML execution evidence.
- Cleanup runs a pinned OpenRewrite bundle in one exactly scoped sandbox pass, then allows contextual
  review of selected production Java and related tests.
- Source quality reports bug risks, code smells, modernization, complexity, duplication, performance,
  remediation debt, and 0–100 component/overall scores. Validation blocks new severe findings and
  overall score regressions.
- Test validation runs class-and-test-scoped PIT with bounded parallelism and reports mutation score,
  test strength, survivors, evidence completeness, and a composite test-quality score.
- Validation rejects deletion, symbolic paths, invalid scope, build-time source drift, and missing
  execution evidence. Apply rejects post-validation candidate drift and live-worktree drift.
- The shared plugin Stop hook runs the cheap diff gate at the end of every agent turn. It ignores
  non-Git workspaces, fails closed on unexpected inspection errors, and continues an agent when a
  changed Java production fingerprint lacks a sufficiently strict receipt.
- Changed-code proof defaults are 90% executable-line coverage, 85% changed-branch coverage, 80%
  changed-line PIT score, 90 new-code quality, and zero new or severity-escalated critical/high
  findings. Deletion-only diffs still require a clean full build.
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
- Published archives contain a jlink runtime, shaded toolkit harness, runner, installer, and
  `plugins/jaipilot`.
- The plugin-local bootstrap requires no package manager. First launch checksum-verifies and caches
  the matching pinned GitHub archive.
- The installer writes only to its private app directory and never creates a PATH or global launcher.
- Codex marketplace: `.agents/plugins/marketplace.json`.
- Claude marketplace: `.claude-plugin/marketplace.json`.
- Shared plugin: `plugins/jaipilot`, containing `jaipilot-generate-tests`, `jaipilot-clean-java`, and
  `jaipilot-review-diff`.

## Performance Baseline

On 2026-08-04, after one warmup, five complete bundled `jaipilot inspect --project .` runs on the
same macOS x64 machine and clean v3.0.0 build were 0.557, 0.542, 0.540, 0.551, and 0.546 seconds:
median 0.546 seconds and nearest-rank p95 0.557 seconds. These measurements include process startup and
project discovery, but exclude builds and host-agent work.

After removing npm on the same machine and v3.0.0 build, five fresh plugin bootstraps from the same
local checksum-verified archive were 2.646, 2.734, 2.671, 2.659, and 2.652 seconds: median 2.659
seconds and nearest-rank p95 2.734 seconds. This isolates install/extract/start time from network
download time. Five cached `version` launches were 0.243, 0.239, 0.247, 0.243, and 0.265 seconds:
median 0.243 seconds and nearest-rank p95 0.265 seconds.

On 2026-08-05, five v3.1.0 full-source quality runs over the canonical Spring Framework Petclinic
revision `f2b1c6d` analyzed the same 43 files, 1,452 LOC, 172 methods, and seven findings every time.
Analyzer times were 0.253, 0.243, 0.264, 0.285, and 0.259 seconds: median 0.259 seconds and p95
0.285 seconds. Complete runner times were 0.96, 0.82, 0.85, 0.87, and 0.84 seconds: median 0.85
seconds and p95 0.96 seconds.

The v3.1.0 Petclinic `Owner` journey prepared in 19.90 seconds and validated in 32.69 seconds wall
time. Targeted PIT used 12.74 seconds, killed 14/14 mutations, and produced a 98.8 test-quality score
with complete evidence. The `CallMonitoringAspect` cleanup prepared in 30.08 seconds and validated
in 18.95 seconds wall time, resolving one smell and moving quality 99.8 → 100.0.

On 2026-08-05, v3.1.1 removed the mutation bypass, made related-test cleanup candidates use the
default 70% PIT gate, separated non-viable PIT mutants from errors, and stabilized finding identity
across measurement-only message changes. Run-state schema 3 no longer exposes a mutation toggle but
continues to read v1/v2 metadata.

Five repeated Petclinic `Owner` journeys at revision
`f2b1c6df3a89f4d0294c7402b48ea351af2c92ca` prepared in 15.85 seconds median / 17.84 seconds p95 and
validated in 27.44 seconds median / 28.47 seconds p95. All five produced 95% line, 100% branch,
14/14 killed mutations, and a 98.8 `EXCELLENT` score with complete evidence. PIT time was 10.89
seconds median / 11.40 seconds p95. Five cleanup journeys prepared in 25.37 seconds median / 28.90
seconds p95 and validated in 16.33 seconds median / 16.82 seconds p95. Final test and cleanup
candidates were transactionally applied in disposable clones with exactly one allowlisted file each.

GitHub dependency scanning then identified seven Jackson Databind 2.18.3 advisories, including two
high-severity alerts. v3.1.2 moves the pinned dependency to the patched 2.18.9 line; do not publish
another release while known high-severity dependency alerts have an available compatible fix.
Five v3.1.2 Petclinic full-source quality runs retained the same scope, findings, and 97.9 score;
analyzer time was 0.217 seconds median / 0.221 seconds p95 and complete runner time was 0.71 seconds
median / 0.73 seconds p95.

## Keep Fresh

Update this file when runner operations, persistence rules, workflow gates, recipes, run
limits, packaging, skills, or verified performance evidence change.
