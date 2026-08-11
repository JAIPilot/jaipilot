<p align="center">
  <img src="plugins/jaipilot/assets/jaipilot-logo.svg" alt="JAIPilot" width="112">
</p>

# JAIPilot

**Java engineering skills that help coding agents make smaller, safer, better-proven changes.**

JAIPilot is a skills-only plugin for Codex and Claude Code. It gives the coding agent three focused
workflows for reviewing Java diffs, generating tests, and cleaning Java code.

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
| jaipilot-generate-tests | Add behavior-focused JUnit tests and use configured coverage or mutation testing when available. |
| jaipilot-clean-java | Simplify Java safely and use configured OpenRewrite or analyzers without broadening scope. |

Example requests:

~~~text
Use JAIPilot to review and verify my current Java diff.
Use JAIPilot to add meaningful tests for OrderService.
Use JAIPilot to simplify the changed Java code without changing behavior.
~~~

The skills can also trigger naturally when the request clearly matches their descriptions.

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

- [Contributing](CONTRIBUTING.md)
- [Support](SUPPORT.md)
- [Security](SECURITY.md)
- [Changelog](CHANGELOG.md)

Licensed under the [MIT License](LICENSE).
