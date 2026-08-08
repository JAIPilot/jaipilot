<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/assets/jaipilot-logo-dark.svg" />
    <img src="docs/assets/jaipilot-logo.svg" alt="JAIPilot logo" width="140" />
  </picture>
  <h1>JAIPilot</h1>
  <p><strong>Java Enterprise Harness for Codex and Claude Code.</strong></p>
  <p>Deterministic guardrails for high-quality agentic Java changes—cutting down agentic drift.</p>
  <p><a href="#install"><strong>Install JAIPilot</strong></a></p>
  <p>
    <a href="https://github.com/JAIPilot/jaipilot/actions/workflows/ci.yml"><img src="https://github.com/JAIPilot/jaipilot/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI" /></a>
    <a href="https://github.com/JAIPilot/jaipilot/releases"><img src="https://img.shields.io/github/v/release/JAIPilot/jaipilot?display_name=tag&sort=semver" alt="Latest release" /></a>
    <a href="LICENSE"><img src="https://img.shields.io/github/license/JAIPilot/jaipilot" alt="MIT license" /></a>
    <img src="https://img.shields.io/badge/Java-17%2B-2457D6" alt="Java 17+" />
  </p>
</div>

JAIPilot is the Java Enterprise Harness that cuts down agentic drift so Codex and Claude Code can
produce and maintain high-quality Java with minimal manual intervention, especially in enterprise
brownfield projects. It gives coding agents deterministic, local guardrails through automatic Git
diff review, high-quality unit testing, and safe code cleanup.

> **Built for enterprise Java teams:** keep source local, constrain agent edits, and require
> real-build evidence before apply. In a reproducible Spring Framework Petclinic evaluation,
> JAIPilot raised line coverage **55% → 95%**, branch coverage **30% → 100%**, killed **14/14 PIT
> mutations**, and reduced cleanup debt **5 → 0 minutes**. [See the full
> evaluation](docs/evaluations/spring-framework-petclinic.md).

## Install

### Codex

```bash
codex plugin marketplace add JAIPilot/jaipilot
codex plugin add jaipilot@jaipilot
```

### Claude Code

Run these commands inside Claude Code:

```text
/plugin marketplace add JAIPilot/jaipilot
/plugin install jaipilot@jaipilot
```

The plugin downloads its private Java runtime on first use. It does not require npm or a globally
installed JDK. Codex and Claude Code show the bundled Stop hook for trust review; approve it to
enable automatic changed-code proof.

## Built to reduce agentic drift

JAIPilot keeps agentic Java work inside a continuous quality ratchet: make the smallest worthwhile
change, remove avoidable complexity, and prove the result before accepting it. The connected coding
agent owns reasoning and edits; JAIPilot supplies deterministic scope, isolation, cleanup, execution,
quality, drift, and apply checks that remain stable across long-running coding sessions.

- **Leanest:** constrain the change to its intended scope, reuse existing code, and reject unrelated
  files, unsafe paths, deletion, generated-source drift, and unnecessary production changes.
- **Cleanest:** run pinned, exactly scoped OpenRewrite cleanup first, surface actionable quality debt,
  and prevent severe findings or overall quality regressions.
- **Meanest:** require the real clean build, changed-test execution, fresh coverage, targeted mutation
  strength, ArchUnit architecture proof, exact-diff proof, and drift-safe transactional apply.

The objective is straightforward: coding agents should be able to work autonomously for longer while
continuously producing and maintaining focused, clean, well-tested Java instead of accumulating
silent complexity and regressions for a human reviewer to unwind later.

## Core tools

### Automatic changed-code proof

- Detect committed branch work from the local default-branch merge base, plus staged, unstaged, and
  untracked Java changes.
- Check at every agent Stop, block completion for an unproved production diff, and continue the
  connected agent into focused remediation and proof.
- Fail closed when an in-scope Git inspection cannot be completed; non-Git workspaces remain quiet.
- Run expensive proof once per exact diff fingerprint, then reuse the local receipt until relevant
  Java or build files change.
- Require a clean full build, fresh changed-line coverage, changed-line PIT, new-code quality,
  zero new or severity-escalated critical/high findings, and zero ArchUnit package-cycle violations
  involving changed classes.
- Default to 90% changed-line coverage, 85% changed-branch coverage, 80% changed-line mutation
  score, and a 90 new-code quality score.

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
- Analyze freshly compiled production bytecode with pinned ArchUnit and return actionable package-cycle
  findings with classes, source path, line, cycle, and remediation.
- Reject new severe findings or quality regressions, then verify behavior with the real build.
- Reject candidate drift, live-worktree drift, deletion, and unsafe paths.

Every score includes its underlying counts and timings. See [quality metrics](docs/quality-metrics.md)
for the formulas, gates, and interpretation boundaries.

## Local impact dashboard

JAIPilot starts a private impact dashboard automatically on the first toolkit invocation and keeps
it available at `http://127.0.0.1:7433/`. If that port is already occupied, JAIPilot selects a free
loopback port automatically. Run `jaipilot dashboard` to retrieve the active URL as structured JSON.

The dashboard refreshes live and shows:

- local workflow invocation counts, success rate, projects seen, command mix, and recent activity;
- applied coverage and quality-score change, resolved findings, removed remediation debt, killed
  mutations, executed changed tests, and transactionally applied files;
- the latest changed-code quality, test-quality, coverage, mutation, and proof evidence;
- current findings by severity with actionable file-and-line details, the latest ArchUnit ruleset
  status and violations, and the actual blocking failures or warnings from the latest proof gate;
- prepared, validated, applied, and safely discarded workflow counts.

Improvement totals are credited only after a validated candidate is applied; attempted or discarded
work never inflates them. Current status is replaced by each new quality analysis, validation, or
changed-code proof, with its source and capture time shown so stale evidence is not presented as
live. Metrics and one-way project identifiers stay in JAIPilot's owner-private local state. The
dashboard binds only to IPv4 loopback, exposes read-only endpoints, and sends no telemetry, source,
paths, or usage data anywhere.

See the [local dashboard startup evaluation](docs/evaluations/local-dashboard-startup.md) for raw
cold-start, steady-state, and real `inspect` timings, and the [current-status dashboard
evaluation](docs/evaluations/dashboard-current-status.md) for API and real-browser live-refresh
evidence.

## Agent Skills

- `jaipilot-generate-tests`
- `jaipilot-clean-java`
- `jaipilot-review-diff`

## Example requests

```text
Generate high-quality unit tests for OrderService and reach 90% line coverage.
Strengthen PaymentServiceTest until it reaches an 80% mutation score.
Generate tests for the Java classes changed on this branch.
Find and fix bug risks, complexity, duplication, and code smells in the changed Java classes.
Review PaymentService for code cleanup, but show me the candidate before apply.
Review and prove every Java production change on this branch.
```

## Workflow

```text
diff-gate → quality and isolated remediation → prove-diff → fingerprinted local proof
```

Each remediation transaction remains `prepare-tests` or `prepare-cleanup → edit isolated candidate
→ validate → apply or discard`. Apply requires an immediately validated candidate and explicit
confirmation. Final diff proof runs in another isolated copy and never edits live source.

Read [how JAIPilot works](docs/how-it-works.md) for the complete scope, evidence, concurrency, and
transaction model.

## Verified on Spring Framework Petclinic

The v3.1.2 workflow was tested against the canonical Spring Framework Petclinic repository:

| Check | Result |
| --- | --- |
| Baseline | 75 tests passed; zero failures, errors, or skips |
| Quality analysis | 43 files in 0.217 s median analyzer time; deterministic 97.9 score |
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
