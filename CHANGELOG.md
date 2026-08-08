# Changelog

Notable user-facing changes to JAIPilot are recorded here. Releases follow semantic versioning.

## Unreleased

## [3.4.0] - 2026-08-08

### Added

- Added a portable coding-agent `PostToolUse` hook that automatically refreshes whole-project Java
  quality after observed `git commit` commands and feeds unproved diffs back into the agent's review
  workflow without a user prompt.

### Changed

- Rebuilt the local dashboard as a restrained, current-quality-first project view with source
  scores, active findings, ArchUnit/proof freshness, verified impact, and activity in that order.
- Persisted complete current source metrics after each observed agent commit and prevented later
  selected-scope analysis from replacing that whole-project snapshot.

## [3.3.2] - 2026-08-08

### Fixed

- Made truncated gate feedback explicit and improved current-status accessibility, readability, and
  documentation clarity after the v3.3.1 dashboard review.

## [3.3.1] - 2026-08-08

### Added

- Added current findings by severity, actionable finding details, ArchUnit ruleset and violation
  evidence, and latest gate failures and warnings to the local impact/proof dashboard.

### Changed

- Made dashboard current-proof status replace stale evidence after every new analysis, validation,
  or proof, and disabled stale asset caching so the live UI matches the persisted metrics schema.

## [3.3.0] - 2026-08-07

### Added

- Added pinned ArchUnit 1.4.2 analysis of freshly compiled production bytecode, with structured
  package-cycle feedback and zero-violation gates for cleanup and changed-code proof.

### Changed

- Invalidated legacy validation and diff-proof receipts that do not contain architecture evidence.

## [3.2.0] - 2026-08-07

### Added

- Added a portable Codex and Claude Code Stop hook that detects each new committed or working-tree
  Java production diff and continues the agent into an evidence-backed review.
- Added merge-base-aware changed-code discovery, exact diff fingerprints, isolated final proof, and
  local proof receipts invalidated by relevant Java or build changes.
- Added fail-closed Git inspection errors, type-change/symlink detection, and protocol-only hook
  responses with phase diagnostics isolated on stderr.
- Added the `jaipilot-review-diff` Agent Skill and default gates for clean build, per-class line and
  branch coverage, targeted PIT mutation strength, source quality, and severe findings.
- Added an automatically started, loopback-only impact dashboard with conflict-safe port selection,
  live local usage statistics, latest proof evidence, and applied improvement metrics.
- Added owner-private, atomic metrics persistence that hashes project identities and credits
  coverage, quality, findings, debt, mutation, and test-execution gains only after verified apply.

### Changed

- Refined the product identity to **JAIPilot Java Enterprise Toolkit Harness** across plugin,
  documentation, runtime, and distribution metadata.
- Added prominent Codex and Claude Code installation instructions to the README.
- Added an exact dark-mode variant of the established JAIPilot logo for GitHub and Codex.
- Added a concise, evidence-backed enterprise value summary to the README.

## [3.1.2] - 2026-08-05

### Security

- Updated Jackson Databind from 2.18.3 to 2.18.9, the patched line for all seven open GitHub
  dependency alerts on v3.1.1, including two high-severity polymorphic-deserialization bypasses.

## [3.1.1] - 2026-08-05

### Changed

- Made targeted PIT proof mandatory for test generation and for cleanup candidates that change
  related tests; removed the mutation bypass.
- Separated non-viable PIT mutants from execution errors and excluded non-actionable statuses from
  transparent score denominators.
- Stabilized quality finding identity so an improved complexity measurement is not misclassified as
  a newly introduced severe finding.

### Fixed

- Preserved the original build or PIT failure when temporary mutation configuration cleanup also
  fails.
- Removed obsolete mutation-toggle state while retaining compatibility with v1 and v2 run metadata.

## [3.1.0] - 2026-08-05

### Added

- Deterministic Java findings for bug risks, code smells, modernization, complexity, duplication,
  and performance hazards, with file, line, remediation, effort, and quick-fix metadata.
- Reliability, maintainability, complexity, duplication, overall quality, remediation-debt, and
  evidence-completeness scorecards with documented formulas.
- Pinned, target-scoped PIT mutation testing for Maven and Gradle, including survivor evidence,
  mutation score, test strength, and a default 70% test-generation gate.
- Composite test-quality scoring from line coverage, branch coverage, mutation score, test strength,
  and changed-test execution.

### Changed

- Expanded exact-scoped OpenRewrite cleanup with API, resource-safety, interruption, redundant
  branch, condition, and modernization recipes.
- Validation now rejects new critical/high findings and overall source-quality regressions.
- Quality analysis uses deterministic ordering and at most four parallel workers; PIT is bounded to
  selected classes/tests and at most four workers.

## [3.0.2] - 2026-08-04

### Changed

- Restored the established JAIPilot logo across the repository and plugin.
- Simplified the product identity; that branding was later refined to
  **JAIPilot Java Enterprise Toolkit Harness**.
- Focused public copy on high-quality Java unit testing and code cleanup.
- Removed the experimental hero, social-preview artwork, brand guide, and setup-oriented README
  sections.

## [3.0.1] - 2026-08-04

### Changed

- Repositioned JAIPilot around proof-driven Java engineering: **Generate tests. Clean Java. Prove
  every change.**
- Rebuilt the public README, plugin presentation, visual identity, and GitHub social artwork.
- Renamed the canonical GitHub repository from `JAIPilot/jaipilot-cli` to `JAIPilot/jaipilot` to
  match the skills-first product; GitHub redirects preserve existing repository and release URLs.
- Added architecture, static-analysis boundary, and reproducible Spring Framework Petclinic
  evaluation documentation.
- Refreshed Codex and Claude Code plugin descriptions and prompts.

### Distribution

- Updated the pinned plugin installer and published metadata to use the canonical repository URL.
- Kept the npm-free, checksum-verified, private-runtime bootstrap unchanged.

## [3.0.0] - 2026-08-04

### Added

- Shared Codex and Claude Code plugin with `jaipilot-generate-tests` and `jaipilot-clean-java`
  Agent Skills.
- Persistent non-interactive JSON runner for inspect, prepare, validate, apply, and discard.
- Pinned, exactly scoped OpenRewrite cleanup before agent review.
- Clean-build, changed-test execution, JaCoCo coverage, scope, drift, and transactional-apply gates.
- Checksum-protected macOS and Linux runtime bundles for x64 and arm64/aarch64.

### Removed

- Interactive CLI distribution, npm packaging, MCP transport, hosted dependencies, and Sonar
  scanner integration.

[3.4.0]: https://github.com/JAIPilot/jaipilot/releases/tag/v3.4.0
[3.3.2]: https://github.com/JAIPilot/jaipilot/releases/tag/v3.3.2
[3.3.1]: https://github.com/JAIPilot/jaipilot/releases/tag/v3.3.1
[3.3.0]: https://github.com/JAIPilot/jaipilot/releases/tag/v3.3.0
[3.2.0]: https://github.com/JAIPilot/jaipilot/releases/tag/v3.2.0
[3.1.2]: https://github.com/JAIPilot/jaipilot/releases/tag/v3.1.2
[3.1.1]: https://github.com/JAIPilot/jaipilot/releases/tag/v3.1.1
[3.1.0]: https://github.com/JAIPilot/jaipilot/releases/tag/v3.1.0
[3.0.2]: https://github.com/JAIPilot/jaipilot/releases/tag/v3.0.2
[3.0.1]: https://github.com/JAIPilot/jaipilot/releases/tag/v3.0.1
[3.0.0]: https://github.com/JAIPilot/jaipilot/releases/tag/v3.0.0
