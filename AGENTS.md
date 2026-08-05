# AGENTS.md

## Project Overview

- JAIPilot is a Java 17+ Java Enterprise Toolkit Harness delivered as portable Agent Skills plus a deterministic internal agent runner.
- It provides isolated high-coverage Java unit-test generation and OpenRewrite-first clean-code refactoring for Codex, Claude Code, and provider-neutral coding agents.
- Do not add another transport layer, a user command shell, a custom backend, a hosted generation service, or nested model invocation.
- Treat `AGENTS.md`, `.jaipilot/project-memory.md`, and `plugins/jaipilot/skills/` as durable context.

## Setup Commands

- Full build/test/package: `./mvnw -B verify`
- Unit tests: `./mvnw -B test`
- One test class: `./mvnw -Dtest=ClassName test`
- Agent-runner smoke: `java -jar target/jaipilot-toolkit-3.1.2-all.jar inspect --project .`
- Installer smoke: `./scripts/smoke-test-install.sh`
- Plugin validation: `python3 ./scripts/validate-plugin.py`

## Repository Map

- `src/main/java/com/jaipilot/toolkit` internal agent runner and persisted run coordination.
- `src/main/java/com/jaipilot/toolkit/core` project discovery, OpenRewrite, build, coverage, run, and transaction services.
- `src/test/java/com/jaipilot/toolkit` JUnit runner and workflow tests.
- `plugins/jaipilot` shared Codex, Claude, and generic plugin manifests, Agent Skills, assets, and pinned bootstrap.
- `.agents/plugins/marketplace.json` and `.claude-plugin/marketplace.json` publish the shared plugin.
- `src/main/dist/bin/jaipilot` bundled internal agent-runner launcher.
- `scripts/build-bundled-dist.sh` builds a platform release with the toolkit harness, runtime, installer, and plugin.
- `plugins/jaipilot/libexec/install.sh` performs checksum-verified, versioned, atomic installation.
- `.jaipilot/project-memory.md` evolving implementation decisions and known facts.

## Protocol And Architecture Invariants

- Emit structured JSON from the internal `jaipilot` runner; send diagnostics and installer receipts to stderr.
- Keep the toolkit harness provider-neutral. The connected coding agent owns reasoning and edits; JAIPilot must not shell out to Codex, Claude, or another model tool.
- Keep prepare, edit, and validate isolated from live source. The internal runner's confirmed apply operation is the only live merge path.
- Require a clean live baseline before creating a candidate.
- Test generation may edit only Java under `src/test/java`.
- Cleanup runs pinned, exactly scoped OpenRewrite recipes first; the agent may edit only selected production Java and related Java tests.
- Reject deletion, path traversal, symbolic paths, out-of-scope changes, build-generated source drift, candidate drift, and live-worktree drift.
- Require changed-test execution evidence. Use fresh JaCoCo as the coverage source of truth where configured and targeted PIT for mutation evidence.
- Reject new critical/high source findings and overall quality-score regressions. Keep score formulas and raw evidence public and versioned.
- Apply only an immediately validated snapshot and write it transactionally with rollback.
- Keep active work bounded to four runs globally, one per project, with two-hour expiry and per-run serialization.
- Persist direct-command run state locally with owner-only permissions, atomic metadata writes, per-run file locks, and a brief registry lock for reservations.
- Preserve Java 17 compatibility and Maven/Gradle wrapper preference.

## Product Quality Mandate

- Aim to deliver a materially better Java remediation journey than static-analysis-only workflows: target accurately, create a useful candidate, prove behavior, surface evidence, and apply safely.
- Do not claim JAIPilot is universally or “supremely” better than SonarQube or any other tool without a reproducible, comparable benchmark. Keep SonarQube's stronger security-analysis and governance use cases explicit.
- Earn superiority claims on accepted verified changes, false positives, escaped defects, regressions, generated-test quality, coverage improvement, elapsed time, reviewer actions, cancellation, and failure recovery.
- Optimize the complete local journey: runner startup, discovery, clean baseline, coverage refresh, target selection, sandbox creation, OpenRewrite, agent handoff, validation, and apply.
- Prefer deletion, reuse, single-pass work, bounded streaming, deterministic ordering, and explicit concurrency limits over configuration and abstraction.
- Keep safe defaults automatic and quiet. Do not add prompts, flags, remote requests, dependencies, or output the server can safely avoid.
- Never trade correctness, fresh coverage, execution evidence, scope safety, determinism, privacy, maintainability, or rollback for speed.
- Never hide a timing defect by increasing a timeout. Reproduce and fix the underlying behavior, then add a deterministic regression test when practical.

## Performance Evidence Loop

For a performance-sensitive change:

1. Define the affected user journey and success criterion.
2. Establish a representative baseline before implementation.
3. Compare the same machine, JDK, build tool, fixture, cache state, command, and measurement boundaries.
4. Use at least five runs for stable deterministic paths; report median and p95 where tail latency matters.
5. Measure the complete journey and suspected hot path. Include elapsed time, first useful response, process/build count, memory or copied bytes where practical, terminal clarity, cancellation, and recovery.
6. Add focused coverage for changed behavior, races, timeouts, scopes, merge rules, and failure modes.
7. Record the environment, commands, raw observations, conclusion, and any external agent/build time separately.

## Documentation And Distribution

- Update `README.md` for skills, install, safety, performance, comparison, or release changes.
- Keep the three plugin manifests, two marketplace catalogs, and pinned plugin bootstrap aligned with Maven `revision`.
- Keep first-use bootstrap self-contained in the plugin and checksum-verified; do not require Node.js, npm, or another package manager.
- Keep release installation private to the selected app directory; do not create PATH or global launchers.
- Do not commit `target/`, `.classpath.txt`, temporary run workspaces, or local agent configuration.

## Completion And Delivery Gates

Before handing off a behavior change:

1. Review and simplify the diff; remove dead code, stale tests, unused dependencies, and obsolete documentation.
2. Run focused tests while iterating, then `git diff --check` and `./mvnw -B verify`.
3. Run plugin/skill validation when manifests or skills change.
4. Run the installer smoke test when packaging, launchers, runtime bundles, or distribution change.
5. Run and report repeatable performance evidence for affected hot paths.
6. Verify structured agent-runner inspect output and that diagnostics do not corrupt stdout.
7. Preserve unrelated worktree changes.

For implementation tasks where the user requests end-to-end delivery, do not stop at a pull request: create a focused branch, test, commit, push, open a ready PR, merge it to `main`, update local `main`, and tag/publish/deploy the release when requested. Never report a commit, merge, GitHub release, or deployment until the remote operation is verified. If credentials, protection rules, or an external review block a step, report the exact remaining blocker.
