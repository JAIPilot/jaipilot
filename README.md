<div align="center">
  <img src="docs/assets/jaipilot-logo.svg" alt="JAIPilot logo" width="140" />
  <h1>JAIPilot</h1>
  <p><strong>Java Enterprise Toolkit for Codex and Claude Code.</strong></p>
  <p>High-quality Java unit testing and code cleanup.</p>
  <p>
    <a href="https://github.com/JAIPilot/jaipilot/actions/workflows/ci.yml"><img src="https://github.com/JAIPilot/jaipilot/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI" /></a>
    <a href="https://github.com/JAIPilot/jaipilot/releases"><img src="https://img.shields.io/github/v/release/JAIPilot/jaipilot?display_name=tag&sort=semver" alt="Latest release" /></a>
    <a href="LICENSE"><img src="https://img.shields.io/github/license/JAIPilot/jaipilot" alt="MIT license" /></a>
    <img src="https://img.shields.io/badge/Java-17%2B-2457D6" alt="Java 17+" />
  </p>
</div>

JAIPilot is the Java Enterprise Toolkit for Codex and Claude Code. It gives coding agents two
focused capabilities: high-quality Java unit testing and safe code cleanup.

## Core tools

### High-quality unit testing

- Generate focused JUnit tests for selected, changed, under-covered, or all production classes.
- Use the project's real Maven or Gradle build.
- Prove that every changed test executed.
- Measure fresh JaCoCo line and branch coverage plus targeted PIT mutation strength.
- Block apply when requested coverage or mutation targets are not met.
- Report a transparent test-quality score with evidence completeness and raw inputs.

### Java code cleanup

- Detect bug risks, code smells, modernization opportunities, complexity, duplication, and
  performance hazards with file-and-line remediation guidance.
- Run pinned, exactly scoped OpenRewrite cleanup first.
- Let the coding agent review and refine the candidate.
- Keep production edits limited to selected Java files and related tests.
- Track reliability, maintainability, complexity, duplication, debt, and overall quality scores.
- Reject new severe findings or quality regressions, then verify behavior with the real build.
- Reject candidate drift, live-worktree drift, deletion, and unsafe paths.

Every score includes its underlying counts and timings. See [quality metrics](docs/quality-metrics.md)
for the formulas, gates, and interpretation boundaries.

## Agent Skills

- `jaipilot-generate-tests`
- `jaipilot-clean-java`

## Example requests

```text
Generate high-quality unit tests for OrderService and reach 90% line coverage.
Strengthen PaymentServiceTest until it reaches an 80% mutation score.
Generate tests for the Java classes changed on this branch.
Find and fix bug risks, complexity, duplication, and code smells in the changed Java classes.
Review PaymentService for code cleanup, but show me the candidate before apply.
```

## Workflow

```text
inspect quality → prepare tests or cleanup → edit isolated candidate → validate scorecards → apply or discard
```

Apply requires an immediately validated candidate and explicit confirmation. Preparing, editing,
validating, and discarding never change the live source tree.

Read [how JAIPilot works](docs/how-it-works.md) for the complete scope, evidence, concurrency, and
transaction model.

## Verified on Spring Framework Petclinic

The v3.1 workflow was tested against the canonical Spring Framework Petclinic repository:

| Check | Result |
| --- | --- |
| Baseline | 75 tests passed; zero failures, errors, or skips |
| Quality analysis | 43 files in 0.259 s median analyzer time; deterministic 97.9 score |
| `Owner` test generation | 55% → 95% line; 30% → 100% branch coverage |
| Mutation proof | 14/14 mutations killed; 100% mutation score and test strength |
| Test score | 98.8 `EXCELLENT`; 100% evidence completeness |
| Code cleanup | Quality 99.8 → 100.0; debt 5 → 0; exactly one selected file |

See the [full reproducible evaluation](docs/evaluations/spring-framework-petclinic.md).

## Project links

- [Releases](https://github.com/JAIPilot/jaipilot/releases)
- [Contributing](CONTRIBUTING.md)
- [Support](SUPPORT.md)
- [Security](SECURITY.md)
- [Changelog](CHANGELOG.md)

## License

[MIT](LICENSE)
