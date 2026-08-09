<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/assets/jaipilot-logo-dark.svg" />
    <img src="docs/assets/jaipilot-logo.svg" alt="JAIPilot logo" width="96" />
  </picture>
  <h1>JAIPilot</h1>
  <p><strong>Deterministic proof for agent-written Java changes.</strong></p>
  <p>
    <a href="https://github.com/JAIPilot/jaipilot/actions/workflows/ci.yml"><img src="https://github.com/JAIPilot/jaipilot/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI" /></a>
    <a href="https://github.com/JAIPilot/jaipilot/releases"><img src="https://img.shields.io/github/v/release/JAIPilot/jaipilot?display_name=tag&sort=semver" alt="Latest release" /></a>
    <a href="LICENSE"><img src="https://img.shields.io/github/license/JAIPilot/jaipilot" alt="MIT license" /></a>
    <img src="https://img.shields.io/badge/Java-17%2B-2457D6" alt="Java 17+" />
  </p>
</div>

JAIPilot is a local, backend-free Java harness for Codex and Claude Code. The coding agent plans and
edits. JAIPilot checks the real repository, measures the selected scope, and proves the exact diff
with the build, executed tests, coverage, mutation testing, code quality, and architecture rules.

It is deliberately not another coding agent. It is the small deterministic evidence layer that
keeps a long Java coding session anchored to what actually ran.

## Why enterprise vibe coding drifts

Professional vibe coding—developing software without reading every line yourself—is much harder in
enterprise repositories. Legacy systems are often decades-old monoliths with behavior spread across
code paths, databases, dependent APIs, message brokers, frameworks, and configuration. Adding a
feature correctly can require the context and judgment of an experienced staff engineer.

In practice, the ability to vibe code for longer depends on how many moving parts the agent must keep
coordinated. Fewer moving parts, clearer boundaries, and simpler invariants give the agent a smaller,
more reliable model of the system. Dead code, excess functionality, duplicated logic, hidden
coupling, and unnecessary abstractions make that model decay sooner. Deleting, trimming, and
simplifying are therefore part of making an enterprise repository agent-ready.

High test coverage matters for the same reason. In a legacy repository, a nearly complete test suite
does more than catch known bugs: it locks in existing behavior as an executable specification. Every
test run tells the agent which assumptions still hold. Maintaining that coverage for new work keeps
defining expected behavior, while mutation testing checks whether the tests detect meaningful code
changes instead of merely executing lines.

Deterministic systems provide the other half of the feedback loop. SonarQube-style analysis exposes
quality and reliability problems, ArchUnit enforces architectural boundaries, and OpenRewrite makes
repeatable refactoring possible. When those signals are connected directly to the coding agent, it
can correct its work using evidence from the real repository instead of relying only on context and
confidence.

JAIPilot packages that loop for Java. The agent still owns requirements, reasoning, and edits;
JAIPilot returns deterministic build, test, coverage, mutation, quality, refactoring, and architecture
evidence that helps the agent stay aligned for longer. This cannot guarantee that a business
requirement is right or that code is universally correct, but it makes drift and missing evidence
visible before confidence is mistaken for proof.

## A real Spring Petclinic change

<p align="center">
  <img src="docs/assets/petclinic-proof-demo.gif" alt="Illustrated JAIPilot Spring Petclinic visit-scheduling walkthrough" width="900" />
</p>

*Illustrated walkthrough composed from the recorded run data—not a screen capture or AI-generated
image.*

Codex added a medium-complexity visit-scheduling use case to Spring Framework Petclinic. JAIPilot
found that the working implementation had crossed its complexity limits. The agent extracted three
cohesive helpers, retained 13 passing focused tests, and reran JAIPilot. Exact-diff proof then
reported 100% executable changed-line coverage, 95.5% changed-branch coverage, 95% mutation score,
zero quality findings, and zero architecture violations. Five hidden contract tests and all 89
tests in an independent clean build passed afterward.

[Read the recorded run, exact patch, measurements, prompt, and hidden-test boundary.](evaluations/petclinic-demo/4.0.5/README.md)

## What JAIPilot adds

| An agent can say | JAIPilot can establish |
| --- | --- |
| “I changed the intended files.” | The exact Git baseline, changed Java/build paths, and fingerprint. |
| “The tests pass.” | A clean isolated build and fresh execution of the changed test classes. |
| “Coverage looks good.” | Changed executable-line and branch coverage from fresh JaCoCo evidence. |
| “The tests are meaningful.” | Targeted PIT mutation strength, including every surviving mutation. |
| “The code is clean.” | Deterministic findings, complexity, duplication, parse failures, and score components. |
| “The architecture is intact.” | Complete ArchUnit evidence for the changed production classes. |

JAIPilot does not replace requirement judgment. Strong tests can still encode the wrong business
behavior. Proof says exactly what the executed tests and deterministic gates established—not that an
unmeasured requirement must be correct.

## Evidence before marketing

The public evidence is mixed, and JAIPilot reports it that way.

| Study | Result | Product decision |
| --- | --- | --- |
| [Randomized Petclinic A/B pilot](evaluations/petclinic-pilot/RESULTS.md) | JAIPilot 4.0.3 and baseline Codex each accepted 9/12 trials. Treatment added 58.6% median agent time and 100.9% median input tokens. | Remove automatic hooks and routine proof. Keep JAIPilot lean and agent-invoked. |
| [Current 4.0.5 demonstration](evaluations/petclinic-demo/4.0.5/README.md) | One visit-scheduling run passed hidden tests, clean verification, scope checks, and exact-diff proof. | Demonstrates the current workflow; does not establish A/B improvement. |

The 24-trial pilot uses frozen prompts, hidden tests, randomized ordering, identical Codex settings,
and an independent acceptance oracle. Its committed record contains the runner, measurements, and
fixtures, but not the original 24 raw transcripts and patches. The single current demo publishes its
exact patch and structured measurements. Neither result supports a universal superiority claim.

## Install

JAIPilot is distributed only as Codex and Claude Code plugins. It requires Java 17+ from `JAVA_HOME`
or `PATH` and downloads one checksum-verified portable JAR on first use.

### Codex

```bash
codex plugin marketplace add JAIPilot/jaipilot
codex plugin add jaipilot@jaipilot
```

### Claude Code

Run inside Claude Code:

```text
/plugin marketplace add JAIPilot/jaipilot
/plugin install jaipilot@jaipilot
```

JAIPilot installs no SessionStart, shell, Git, commit, or Stop hooks. Opening a repository does not
analyze it, run its build, create repository state, or start the dashboard. The host agent explicitly
selects a JAIPilot tool when deterministic feedback is useful.

## Three jobs, six tools

| Journey | MCP tools | Outcome |
| --- | --- | --- |
| Understand | `jaipilot_inspect`, `jaipilot_snapshot` | Discover the Java boundary and refresh current whole-project evidence. |
| Improve | `jaipilot_quality`, `jaipilot_rewrite` | Measure an exact scope and optionally run pinned OpenRewrite cleanup. |
| Prove | `jaipilot_prove_diff`, `jaipilot_diff_gate` | Prove the current Java/build fingerprint and reject stale receipts. |

All tools are synchronous and return structured evidence. Long builds stay visible to the host, so
its normal process controls can cancel or retry them. JAIPilot never commits, pushes, opens a PR, or
applies a hidden candidate.

Every tool has a visible `JAIPilot:` title and emits an MCP start notice. Every result ends with the
operation status, a `Why this mattered` explanation, and measurements from that run:

```text
JAIPilot is running: Prove exact Java diff.
JAIPilot finished: Prove exact Java diff (passed)
Why this mattered: JAIPilot ran the clean build and applicable gates instead of relying on agent confidence.
Evidence: targets=3; failures=0; warnings=5; elapsed=PT41.259321665S
```

Failed, skipped, stale, and non-applicable work remains explicit. JAIPilot does not convert missing
evidence into a zero score or a pass.

## The normal loop

```text
inspect → agent edits → quality → prove-diff → diff-gate
```

The agent decides when each step is worthwhile. Unit-test work uses fresh JaCoCo, changed-test XML,
and targeted PIT evidence. Cleanup uses pinned, exactly scoped OpenRewrite recipes first, but only
when the agent selects cleanup; the agent reviews and refines any resulting diff.

Proof is cached only for the exact relevant fingerprint. A Java file, test, build descriptor,
wrapper, symlink, or executable-mode change invalidates the receipt. Default gates are:

- 90% changed executable-line coverage;
- 85% changed-branch coverage;
- 80% changed-line mutation score;
- 90 changed-code quality score;
- zero introduced or severity-escalated critical/high findings; and
- complete ArchUnit evidence with zero package-cycle violations involving changed classes.

Build/test-only and deletion-only diffs still require the clean build. Genuinely non-applicable gates
remain labeled non-applicable.

## A deliberately small boundary

| Host coding agent owns | JAIPilot owns |
| --- | --- |
| Requirements, planning, and architectural judgment | Repository and changed-scope discovery |
| Source and test edits | Deterministic quality findings and scores |
| Branches, commits, rebases, and PRs | Pinned, explicitly invoked OpenRewrite cleanup |
| Iteration and retry strategy | Build, execution, coverage, PIT, and ArchUnit proof |
| Cancellation and process supervision | Exact fingerprint and local proof receipt |
| Deciding whether to report a defect | Current per-repository snapshot and dashboard |

This division keeps JAIPilot lean. It has no second agent loop, workflow engine, candidate workspace,
command history, usage analytics, hosted backend, or scheduler.

## Local evidence dashboard

`jaipilot_snapshot` starts an owner-private dashboard on `http://127.0.0.1:7433/`, or another
loopback port if needed. One machine-wide selector includes every retained Java repository and shows
its canonical path and GitHub link when available. The selected repository shows current quality and
findings, exact-proof freshness, applicable coverage/mutation/architecture evidence, and observed
snapshot deltas.

State is bounded and stored outside repositories under `JAIPILOT_STATE_HOME`, then
`$XDG_STATE_HOME/jaipilot`, or `~/.local/state/jaipilot`, with owner-only permissions.

## Privacy, support, and boundaries

Repository inspection and Git-origin lookup are local; JAIPilot never fetches. Source, prompts,
findings, paths, and metrics are not uploaded. The dashboard binds only to IPv4 loopback and exposes
read-only endpoints.

If a structured failure appears to be a JAIPilot defect, the host may offer to open a
[GitHub issue](https://github.com/JAIPilot/jaipilot/issues/new/choose). It must ask first and sanitize
paths, source, prompts, credentials, environment values, and private repository details.

JAIPilot complements rather than replaces centralized analysis. SonarQube and similar platforms are
stronger for formal security/data-flow analysis, large rule catalogs, centralized governance,
portfolios, compliance, and long-term organization history. JAIPilot requires a local Maven or
Gradle repository; fresh coverage and mutation evidence depend on that repository's JaCoCo and PIT
compatibility.

## Documentation

- [How JAIPilot works](docs/how-it-works.md)
- [Quality metrics and formulas](docs/quality-metrics.md)
- [Static-analysis boundary](docs/static-analysis-boundary.md)
- [Petclinic controlled pilot](evaluations/petclinic-pilot/RESULTS.md)
- [Spring Petclinic 4.0.5 demonstration](evaluations/petclinic-demo/4.0.5/README.md)
- [Lean-kernel and Kafka evidence](docs/evaluations/lean-kernel-4.0.0.md)
- [Plugin bootstrap evidence](docs/evaluations/plugin-bootstrap-4.0.2.md)
- [Contributing](CONTRIBUTING.md) · [Support](SUPPORT.md) · [Security](SECURITY.md) · [Changelog](CHANGELOG.md)

## License

[MIT](LICENSE)
