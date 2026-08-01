# JAIPilot Project Memory

This is the durable implementation memory for JAIPilot MCP. Keep evolving facts here; keep stable repository rules in `AGENTS.md` and reusable agent procedures in `plugin/jaipilot/skills/`.

## Current Direction

- `main` is MCP- and Agent-Skills-native from 2.0.0 onward. The prior CLI is archived at `archive/cli-v1.0.16` and tag `v1.0.16`.
- The product is a local stdio MCP server. It has no interactive shell, custom backend, hosted generation API, or embedded model provider.
- The connected coding agent owns reasoning and source edits. JAIPilot owns deterministic project discovery, target selection, isolation, OpenRewrite, validation evidence, drift protection, and transactional apply.
- stdout is JSON-RPC only. Diagnostics and installation receipts use stderr.

## MCP Surface

- `jaipilot_inspect_project`: read build/project/coverage/run metadata.
- `jaipilot_prepare_tests`: clean-baseline and isolate named, changed, all, or fresh-coverage-selected production classes.
- `jaipilot_prepare_cleanup`: clean-baseline, isolate, and run exact-scoped OpenRewrite first.
- `jaipilot_get_run`: inspect active run state.
- `jaipilot_read_run_file` / `jaipilot_write_run_file`: bounded fallback for hosts without direct sandbox filesystem access.
- `jaipilot_validate_run`: enforce scope, build, test execution, coverage, and build-drift gates.
- `jaipilot_apply_run`: apply only an immediately validated and unchanged candidate transactionally.
- `jaipilot_discard_run`: delete the isolated candidate.

## Workflow Invariants

- Default test coverage target is 80%.
- Maven wrapper is preferred only when executable `mvnw` and `.mvn/wrapper/maven-wrapper.properties` both exist.
- Gradle wrapper is preferred only when executable `gradlew` and `gradle/wrapper/gradle-wrapper.properties` both exist.
- Fresh Maven coverage uses `<wrapper-or-mvn> -B clean verify`.
- Fresh Gradle coverage uses `<wrapper-or-gradle> --no-daemon clean test jacocoTestReport`, or `clean testCodeCoverageReport` for coverage aggregation.
- Coverage refresh invalidates recognized XML first, is protected by a project-scoped lock, and never falls back to stale or partial reports after failure.
- Coverage selection uses the immutable snapshot returned by the same clean refresh.
- Coverage discovery accepts reports under `target/site/jacoco*/`, `target/coverage-reports/**/`, and `build/reports/jacoco/**/`. Ambiguous multi-module reports fail unless one aggregate report is identifiable.
- Test generation can change only `src/test/java/**/*.java`. Production files and build configuration are immutable in that workflow.
- Changed tests must have a newly generated Surefire, Failsafe, or Gradle XML report with non-zero execution.
- Cleanup runs pinned OpenRewrite `CodeCleanup` and `CommonStaticAnalysis` recipes in one scoped sandbox pass without persisting build configuration. The agent reviews the result and may change only selected production Java plus directly relevant Java tests.
- Preparing captures complete non-excluded live and sandbox snapshots. Validation rejects deletion, symlinks, invalid scope, build-time source drift, and missing test evidence. Apply rejects post-validation candidate drift and any live-worktree drift.
- Active work is capped at four runs globally and one per real project. Runs expire after two hours and operations on one run are serialized.
- MCP file reads/writes are UTF-8, traversal- and symlink-safe, allowlisted, and capped at 262,144 characters.

## Quality Position

- JAIPilot should beat static-finding-only workflows at useful verified local remediation and unit-test creation.
- “Better than SonarQube” is defensible only for a scoped, measured workflow where JAIPilot produces more accepted, behaviorally verified remediations without more regressions or false positives.
- Do not make universal or “supreme” claims. SonarQube remains stronger for broad deterministic security analysis, quality governance, portfolios, and historical dashboards; OpenRewrite remains the deterministic transformation engine JAIPilot leverages.
- Compare accepted fixes, false positives, escaped defects, generated-test quality, build/test outcomes, coverage, elapsed time, reviewer actions, cancellation, and recovery using identical revisions and boundaries.

## Packaging And Delivery

- Maven artifact: `com.jaipilot:jaipilot-mcp`.
- npm package: `jaipilot`; executable: `jaipilot-mcp`.
- Published archives include a jlink runtime, shaded server, launcher, installer, and `plugin/jaipilot`.
- npm remains dependency-free and has no install lifecycle script. First launch checksum-verifies and caches the matching GitHub archive.
- Plugin manifests exist for Codex, Claude, and generic/Copilot plugin discovery; the portable skills are `jaipilot-generate-tests` and `jaipilot-clean-java`.
- For requested end-to-end changes, delivery includes tests, commit, push, ready PR, merge to `main`, and release/publish verification. Do not stop at PR or claim an external step that did not succeed.

## Keep Fresh

Update this file when MCP tools, protocol invariants, build/coverage commands, recipe versions, run limits, packaging layout, agent skill behavior, or verified performance findings change.
