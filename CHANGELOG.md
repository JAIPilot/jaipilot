# Changelog

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
