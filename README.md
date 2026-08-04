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
- Measure fresh JaCoCo line and branch coverage.
- Block apply when a requested coverage target is not met.

### Java code cleanup

- Run pinned, exactly scoped OpenRewrite cleanup first.
- Let the coding agent review and refine the candidate.
- Keep production edits limited to selected Java files and related tests.
- Verify behavior with a clean build and test execution evidence.
- Reject candidate drift, live-worktree drift, deletion, and unsafe paths.

## Agent Skills

- `jaipilot-generate-tests`
- `jaipilot-clean-java`

## Example requests

```text
Generate high-quality unit tests for OrderService and reach 90% line coverage.
Generate tests for the Java classes changed on this branch.
Clean the changed Java classes and apply only the verified result.
Review PaymentService for code cleanup, but show me the candidate before apply.
```

## Workflow

```text
inspect → prepare tests or cleanup → edit isolated candidate → validate → apply or discard
```

Apply requires an immediately validated candidate and explicit confirmation. Preparing, editing,
validating, and discarding never change the live source tree.

Read [how JAIPilot works](docs/how-it-works.md) for the complete scope, evidence, concurrency, and
transaction model.

## Verified on Spring Framework Petclinic

The released v3 workflow was tested against a clean Spring Framework Petclinic revision:

| Check | Result |
| --- | --- |
| Baseline | 75 tests passed; 85.69% aggregate line coverage |
| `Owner` test generation | 55% → 95% line coverage; 30% → 100% branch coverage |
| Coverage gate | Correctly blocked apply at 72.5% against an 80% requirement |
| Code cleanup | Exactly one selected class plus four related tests; 79 tests passed |
| Drift safety | Rejected a post-validation edit and identified the exact file |

See the [full reproducible evaluation](docs/evaluations/spring-framework-petclinic.md).

## Project links

- [Releases](https://github.com/JAIPilot/jaipilot/releases)
- [Contributing](CONTRIBUTING.md)
- [Support](SUPPORT.md)
- [Security](SECURITY.md)
- [Changelog](CHANGELOG.md)

## License

[MIT](LICENSE)
