# Changelog

Notable user-facing changes to JAIPilot are recorded here. Releases follow semantic versioning.

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
- Simplified the product identity to **JAIPilot Java Enterprise Toolkit**.
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

[3.1.0]: https://github.com/JAIPilot/jaipilot/releases/tag/v3.1.0
[3.0.2]: https://github.com/JAIPilot/jaipilot/releases/tag/v3.0.2
[3.0.1]: https://github.com/JAIPilot/jaipilot/releases/tag/v3.0.1
[3.0.0]: https://github.com/JAIPilot/jaipilot/releases/tag/v3.0.0
