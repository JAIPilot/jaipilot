<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/assets/jaipilot-logo-dark.svg" />
    <img src="docs/assets/jaipilot-logo.svg" alt="JAIPilot logo" width="140" />
  </picture>
  <h1>JAIPilot</h1>
  <p><strong>Deterministic guardrails that cut down agentic drift for high-quality agentic Java changes.</strong></p>
  <p>
    <a href="https://github.com/JAIPilot/jaipilot/actions/workflows/ci.yml"><img src="https://github.com/JAIPilot/jaipilot/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI" /></a>
    <a href="https://github.com/JAIPilot/jaipilot/releases"><img src="https://img.shields.io/github/v/release/JAIPilot/jaipilot?display_name=tag&sort=semver" alt="Latest release" /></a>
    <a href="LICENSE"><img src="https://img.shields.io/github/license/JAIPilot/jaipilot" alt="MIT license" /></a>
    <img src="https://img.shields.io/badge/Java-17%2B-2457D6" alt="Java 17+" />
  </p>
</div>

JAIPilot is a local, backend-free Java Enterprise Harness for Codex, Claude Code, and any coding
tool that supports its stdio MCP server. It helps coding agents produce and maintain high-quality
code with minimal manual intervention, especially in enterprise brownfield projects.

JAIPilot is deliberately not another coding agent. The host agent reasons, edits, retries, manages
Git, and talks to the user. JAIPilot supplies the small deterministic evidence kernel that keeps a
long coding session anchored to the real repository.

## The boundary

| Host coding agent owns | JAIPilot owns |
| --- | --- |
| Planning and architectural judgment | Repository and changed-scope discovery |
| Source and test edits | Deterministic quality findings and scores |
| Branches, commits, rebases, and PRs | Pinned, explicitly invoked OpenRewrite cleanup |
| Focused iteration and retry strategy | Clean-build, test-execution, coverage, PIT, and ArchUnit proof |
| Cancellation and process supervision | Exact diff fingerprint and local proof receipt |
| Asking before opening a support issue | Latest per-repository snapshot and local dashboard |

This division keeps JAIPilot lean. It does not maintain a second workflow engine, candidate
workspace, apply transaction, command history, usage analytics, or agent scheduler.

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

The plugin uses Java 17+ from `JAVA_HOME` or `PATH` and downloads one checksum-verified portable
JAIPilot JAR on first use. It does not download a second JRE or separate operating-system payload.
The stdio MCP server and Agent Skills work together. Hooks initialize and refresh only Maven or
Gradle directories that contain Java source; other directories exit silently without starting Java,
creating state, downloading JAIPilot, or starting the dashboard.

## Six deterministic MCP tools

| Tool | Purpose |
| --- | --- |
| `jaipilot_inspect` | Discover the Java build, production classes, and available evidence engines. |
| `jaipilot_snapshot` | Refresh whole-repository quality and the dashboard's current state. |
| `jaipilot_quality` | Return deterministic findings, debt, complexity, duplication, and scorecards. |
| `jaipilot_rewrite` | Run pinned OpenRewrite recipes for an exact agent-selected scope. |
| `jaipilot_diff_gate` | Check whether the current Java/build fingerprint has a valid proof receipt. |
| `jaipilot_prove_diff` | Run the clean build and every applicable coverage, PIT, quality, and ArchUnit gate. |

All tools are synchronous and return structured evidence. Long builds remain visible to the host,
which can cancel or retry them using its normal process controls. JAIPilot never commits, pushes,
opens a PR, or applies a hidden candidate.

## How drift is reduced

At session start JAIPilot first checks for a Maven or Gradle build and Java source. For an applicable
repository it records the canonical path and local GitHub origin, downloads the small portable
payload when needed, refreshes current quality in a detached process, and starts the loopback
dashboard. This initialization does not edit the repository or create files inside it. Non-Java
directories do not invoke Java or download anything. Stop never performs a synchronous download: if
background bootstrap is incomplete or the network is unavailable, it exits silently and the MCP
tool reports the actionable setup error when explicitly invoked.

The direct `git commit` post-tool hook queues the same detached repository snapshot. The Stop hook checks
the current Java/build diff and returns actionable proof requirements to the host agent. These are
coding-tool hooks, not operating-system-wide Git hooks; commits made in unrelated terminals or hidden
inside arbitrary wrapper programs are outside this automatic boundary. The diff gate still catches
applicable working-tree changes at Stop or whenever the agent invokes it.

The normal loop is intentionally short:

```text
inspect → edit with the host agent → quality → prove-diff → diff-gate
```

Proof is cached only for the exact relevant fingerprint. A Java file, test, build descriptor, wrapper,
symlink, or executable-mode change invalidates the receipt. Defaults are:

- 90% changed-line coverage;
- 85% changed-branch coverage;
- 80% changed-line mutation score;
- 90 changed-code quality score;
- zero introduced or severity-escalated critical/high findings; and
- complete ArchUnit evidence with zero package-cycle violations involving changed classes.

Build/test-only and deletion-only diffs still require the clean build. Gates that are genuinely not
applicable remain explicit rather than being represented as a zero score or a pass.

## Unit tests and cleanup

The `jaipilot-generate-tests` skill asks the host agent to write focused tests, then uses fresh
JaCoCo, test-execution XML, and targeted PIT evidence to show whether those tests are meaningful.

The `jaipilot-clean-java` skill runs pinned, exactly scoped OpenRewrite recipes first when cleanup is
useful. The host reviews the resulting Git diff, keeps only worthwhile behavior-preserving changes,
and adds a regression test before changing established behavior.

`jaipilot-review-diff` ties both paths to the exact Git fingerprint. No skill lowers a gate, changes
build configuration merely to pass, or treats coverage as a substitute for mutation strength.

## Local current-evidence dashboard

JAIPilot starts an owner-private dashboard on `http://127.0.0.1:7433/`. If that port is occupied, it
uses another loopback port. This is one machine-wide dashboard, not one server per repository. Its
selector includes every Java repository retained in the common local store and shows its canonical
path and GitHub link when available. For the selected repository it shows:

- current whole-project quality and findings;
- current proof status and exact-fingerprint freshness;
- applicable ArchUnit, coverage, mutation, and gate evidence; and
- observed quality/finding deltas between snapshots.

The dashboard is a view over bounded per-repository snapshots, not a project-management database.
It has no telemetry, hosted backend, command history, usage analytics, portfolios, or compliance
workflow. State is stored outside repositories under `JAIPILOT_STATE_HOME`, then
`$XDG_STATE_HOME/jaipilot`, or `~/.local/state/jaipilot`, with owner-only permissions.

## Privacy and support

Repository inspection and Git-origin lookup are local; JAIPilot never fetches. Source code, prompts,
findings, paths, and metrics are not uploaded. The dashboard binds only to IPv4 loopback and exposes
read-only endpoints.

If a structured JAIPilot failure looks like a product defect, the host agent may offer to open a
[GitHub issue](https://github.com/JAIPilot/jaipilot/issues/new/choose). It must ask first and sanitize
paths, source, prompts, credentials, environment values, and repository-private details. JAIPilot
never files or uploads an issue automatically.

## Honest boundaries

JAIPilot complements rather than replaces centralized analysis. SonarQube and similar platforms
remain stronger for formal security/data-flow analysis, large rule catalogs, centralized governance,
portfolios, compliance, and long-term organization-wide history.

JAIPilot requires a local Maven or Gradle repository and uses local Git refs without fetching. Fresh
coverage and mutation evidence depend on the repository's JaCoCo/PIT compatibility. A clean existing
root commit is treated as repository history; automatic direct-commit observation begins after the
plugin is active.

See [how JAIPilot works](docs/how-it-works.md), [quality metrics](docs/quality-metrics.md), and the
[static-analysis boundary](docs/static-analysis-boundary.md). The
[v4.0 lean-kernel evidence](docs/evaluations/lean-kernel-4.0.0.md) records raw performance and Kafka
acceptance results, including the failed Kafka test-suite boundary. The
[v4.0.2 plugin-bootstrap evidence](docs/evaluations/plugin-bootstrap-4.0.2.md) records the payload-size
reduction, protocol timings, retry behavior, and silent Stop failure-path acceptance.

## Project links

- [Releases](https://github.com/JAIPilot/jaipilot/releases)
- [Contributing](CONTRIBUTING.md)
- [Support](SUPPORT.md)
- [Security](SECURITY.md)
- [Changelog](CHANGELOG.md)

## License

[MIT](LICENSE)
