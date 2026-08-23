<p align="center">
  <img src="plugins/jaipilot/assets/jaipilot-logo.svg" alt="JAIPilot" width="112">
</p>

# JAIPilot

**Java engineering skills that help coding agents make smaller, safer, better-proven changes.**

JAIPilot is a skills-only plugin for ChatGPT, Codex, and Claude Code. It gives the coding agent three
focused workflows for reviewing Java diffs, generating tests, and cleaning Java code.

There is no JAIPilot runtime, MCP server, dashboard, background process, installer, or automatic
hook. Installing the plugin adds Markdown instructions and brand assets—nothing starts, downloads,
or scans a repository.

## Why

Large Java repositories contain behavior, integrations, exceptions, and architectural constraints
that no agent can hold perfectly in context. Agents drift sooner when code has hidden coupling,
duplicated logic, weak tests, and unclear boundaries.

JAIPilot does not try to replace the agent or build another analysis platform. It gives the agent a
repeatable engineering checklist:

- establish the exact requested scope;
- preserve unrelated work;
- inspect the complete Java and build diff;
- use the repository's existing Maven or Gradle wrapper;
- use configured tests, JaCoCo, PIT, ArchUnit, OpenRewrite, and static analyzers when applicable;
- keep the smallest coherent change; and
- report what was actually run, what passed, and what remains unknown.

## Install

### Codex

~~~bash
codex plugin marketplace add JAIPilot/jaipilot
codex plugin add jaipilot@jaipilot
~~~

### Claude Code

Run inside Claude Code:

~~~text
/plugin marketplace add JAIPilot/jaipilot
/plugin install jaipilot@jaipilot
~~~

No Java version is required to install JAIPilot itself. The Java repository still needs its normal
JDK, build wrapper, dependencies, and configured engineering tools.

## Three skills

| Skill | Use it for |
| --- | --- |
| jaipilot-review-diff | Review a Java Git diff, find risk or unnecessary code, and run the repository's applicable verification. |
| jaipilot-generate-tests | Raise meaningful per-class Java coverage with bounded parallel workers and fresh configured JaCoCo or PIT evidence. |
| jaipilot-clean-java | Safely remove unused code, consolidate logic, modernize Java/dependencies, and optimize measured performance. |

Example requests:

~~~text
Use JAIPilot to review and verify my current Java diff.
Use JAIPilot to raise every eligible class in the orders module toward at least 80% line coverage.
Use JAIPilot to remove everything the repository can prove unused, retaining anything uncertain.
Use JAIPilot to consolidate equivalent services and reduce classes without changing behavior.
Use JAIPilot to upgrade this project to the newest stable JDK and dependencies it can prove compatible.
Use JAIPilot to optimize this workload from profiler and benchmark evidence.
~~~

The skills can also trigger naturally when the request clearly matches their descriptions.

For a coverage campaign, the test skill assigns one production class to each available worker and
runs bounded waves in isolated worktrees or build outputs. It does not run concurrent Maven or
Gradle processes in one checkout, assume shared tests are runtime-independent, or turn missing
JaCoCo data into a pass. After integration, the agent refreshes one aggregate report and lists the
baseline and final coverage of every eligible class, including honest blockers below 80%.

The clean skill has four composable modes: fail-closed unused removal, behavior-locked
consolidation, verified stable modernization, and measured performance optimization. It can invoke
the test skill for characterization and regression coverage and the review skill for final proof.
It does not treat fewer lines, larger version numbers, virtual threads, or one passing test suite as
success by themselves. Anything uncertain stays in place and is reported.

## What the agent should return

Every workflow ends with a concise record:

~~~text
Scope: module and files reviewed
Changes: what the agent changed and why
Commands: exact builds, tests, and analyzers executed
Evidence: passed, failed, skipped, or unavailable results
Limitations: anything that was not measured
~~~

Missing JaCoCo, PIT, ArchUnit, OpenRewrite, or another analyzer is reported as unavailable. JAIPilot
does not silently add tooling, lower thresholds, or describe an unmeasured property as passing.

## Deliberate boundary

JAIPilot is procedural guidance, not an enforcement engine. It cannot guarantee correctness,
business intent, coverage, architecture, or agent compliance. Its value is helping the agent use the
real repository toolchain consistently and explain the resulting evidence.

The plugin has no telemetry and uploads nothing. Repository commands may still execute project build
scripts and resolve dependencies according to that project's configuration. Treat untrusted builds
as executable code.

The previous runtime-based implementation—including MCP, metrics, and the local dashboard—is
preserved at [v4.0.8](https://github.com/JAIPilot/jaipilot/releases/tag/v4.0.8).

## Project

- [JAIPilot Cloud campaign results](CLOUD_RESULTS.md) — a separate bounded GitHub App experiment
- [Contributing](CONTRIBUTING.md)
- [Support](SUPPORT.md)
- [Security](SECURITY.md)
- [Privacy](PRIVACY.md)
- [Terms](TERMS.md)
- [Changelog](CHANGELOG.md)

Licensed under the [MIT License](LICENSE).
