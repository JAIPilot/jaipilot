# Changelog

## [5.3.0] - 2026-08-24

### Added

- Bundled the six-tool JAIPilot Remote MCP server with the existing Java skills for Codex and
  Claude Code.
- Added `jaipilot-remote-java` to run long builds, tests, analyzers, profilers, and benchmarks on
  disposable Java hardware at one exact committed GitHub SHA.
- Added pinned remote Java environments with JDK 17, 21, and 25, Maven 3.9.16, and Gradle 9.7.0.
- Added Deno protocol tests and structural validation for the MCP manifest, permissions, adapter,
  and secret-free distribution boundary.
- Added separate Codex and Claude Code launch bindings for one shared adapter, including the Codex
  plugin-relative working-directory path required by current hosts.

### Changed

- Kept the host agent responsible for reasoning, edits, command selection, Git, and user
  interaction; the MCP supplies execution hardware only.
- Made remote execution opt-in and fail closed for staged, unstaged, or untracked local changes.
- Updated privacy, security, terms, contribution, and submission documentation for the hosted
  execution boundary.
- Explicitly kept builds requiring corporate VPN/VPC access, private artifacts, internal services,
  or unavailable secrets on the developer's existing execution boundary.

## [5.2.0] - 2026-08-16

### Changed

- Expanded `jaipilot-clean-java` into a fail-closed unused-code cleanup with a per-candidate
  deletion ledger, leaf-first application, and reversible verification.
- Added behavior-locked logical consolidation that reduces classes, methods, lines, and duplication
  only when invariants and downstream contracts genuinely match.
- Added stable JDK, build, framework, plugin, and dependency modernization in isolated compatibility
  batches, including full-environment checks before adopting Java 25.
- Added measurement-first algorithm, allocation, I/O, caching, and concurrency optimization with
  reproducible raw runs, median, p95, and virtual-thread suitability checks.
- Required repository-wide symbol and configuration checks plus explicit reflection, framework,
  side-effect, profile, public-API, resource, dependency, packaging, and runtime boundaries.
- Made uncertainty a retained-and-reported result instead of an unsafe deletion or universal safety
  claim, while keeping configured, pinned, exactly scoped OpenRewrite dry runs first and composing
  the existing test-generation and diff-review skills for behavior locks and final proof.

## [5.1.0] - 2026-08-16

### Changed

- Expanded `jaipilot-generate-tests` into a per-class coverage campaign that targets at least 80%
  fresh JaCoCo line coverage for every eligible production class without rewarding hollow tests.
- Added bounded one-class-per-worker parallel waves with isolated worktrees or build outputs,
  deterministic prioritization, aggregate remeasurement, and honest per-class blockers.
- Kept test execution repository-native and prevented concurrent Maven or Gradle processes from
  sharing one checkout or output tree.

## [5.0.1] - 2026-08-11

### Added

- Added public privacy and terms documents for the OpenAI plugin review.
- Added a reproducible submission packet with listing copy, starter prompts, reviewer test cases,
  availability guidance, attestations, and release notes.
- Declared the public privacy and terms URLs in the Codex plugin interface metadata.

### Changed

- Prepared the same three skills-only workflows for the official OpenAI plugin directory without
  adding a runtime, backend, authentication, telemetry, or a package-manager distribution.

## [5.0.0] - 2026-08-11

### Changed

- Rebuilt JAIPilot as three self-contained Agent Skills for Codex and Claude Code.
- Delegated reasoning, edits, commands, retries, cancellation, Git, and reporting to the host agent.
- Made repository-native Maven, Gradle, test, coverage, mutation, architecture, refactoring, and
  analysis configuration the source of truth.

### Removed

- Removed the Java runtime and its 13,781 lines of production and test code.
- Removed MCP, launchers, first-use downloads, installers, background work, hooks, persistent state,
  exact receipts, metrics, and the dashboard.
- Removed runtime release payloads, Maven project files, legacy evaluations, and obsolete docs.

The runtime line remains available at tag
[v4.0.8](https://github.com/JAIPilot/jaipilot/releases/tag/v4.0.8).
