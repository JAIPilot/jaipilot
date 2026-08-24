<p align="center">
  <img src="plugins/jaipilot/assets/jaipilot-logo.svg" alt="JAIPilot" width="112">
</p>

# JAIPilot

**Java engineering skills with opt-in remote build and test execution.**

JAIPilot is one plugin for ChatGPT, Codex, and Claude Code. It gives the customer’s coding agent
four focused Java workflows and six remote-execution tools. The customer agent still reasons,
edits, chooses commands, owns Git, and talks to the user. JAIPilot Remote supplies disposable Java
hardware only when the agent decides it materially helps.

Nothing runs on repository open, file change, commit, or agent shutdown. There are no hooks,
watchers, dashboards, background scans, or automatic code changes.

## Why

Large Java repositories contain behavior, integrations, exceptions, and architectural constraints
that no agent can hold perfectly in context. Agents drift sooner when code has hidden coupling,
duplicated logic, weak tests, and unclear boundaries. Long builds, profilers, and benchmark runs can
also make the developer laptop the bottleneck.

JAIPilot gives the host agent a repeatable engineering loop:

- establish the exact requested scope and preserve unrelated work;
- inspect the complete Java and build diff;
- lock observable behavior with meaningful tests;
- remove only proved-unused or genuinely redundant code;
- modernize only to releases the repository can prove compatible;
- optimize only measured workloads;
- use configured JaCoCo, PIT, ArchUnit, OpenRewrite, and analyzers when applicable; and
- optionally run expensive commands on a disposable exact-SHA Java workspace.

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

The skills need no JAIPilot runtime. The optional MCP server uses Deno 2.x and starts with the
plugin. Its tools call JAIPilot Cloud only when selected. Remote execution is currently an operator
preview: it requires the JAIPilot GitHub App on the repository and a separately provisioned
internal credential in the host environment. Do not distribute or paste that credential into
prompts. Public customer authentication is not shipped in this release. Codex and Claude Code use
small host-specific bindings to launch the same reviewed adapter from the installed plugin.

## Four skills

| Skill | Use it for |
| --- | --- |
| jaipilot-review-diff | Review a Java Git diff, find risk or unnecessary code, and run applicable repository checks. |
| jaipilot-generate-tests | Raise meaningful per-class Java coverage with bounded workers and fresh configured JaCoCo or PIT evidence. |
| jaipilot-clean-java | Safely remove unused code, consolidate logic, modernize Java/dependencies, and optimize measured performance. |
| jaipilot-remote-java | Run long builds, tests, analyzers, profilers, or benchmarks remotely for an exact committed GitHub SHA. |

Example requests:

~~~text
Use JAIPilot to review and verify my current Java diff.
Use JAIPilot to raise every eligible class in the orders module toward at least 80% line coverage.
Use JAIPilot to remove everything the repository can prove unused, retaining anything uncertain.
Use JAIPilot to consolidate equivalent services and reduce classes without changing behavior.
Use JAIPilot to upgrade this project to the newest stable JDK and dependencies it can prove compatible.
Use JAIPilot to optimize this workload from profiler and benchmark evidence.
Use JAIPilot Remote to run this committed Java build on disposable remote hardware.
~~~

The skills can trigger naturally when a request clearly matches their descriptions. Remote hardware
is never mandatory for the other three skills.

## Remote execution

The bundled MCP exposes a deliberately small contract:

| Tool | Purpose |
| --- | --- |
| workspace_create | Check out an exact GitHub commit in a private TTL-bounded Java workspace. |
| process_start | Start one asynchronous repository command. |
| process_status | Read durable running state and the final exit code. |
| process_logs | Read a bounded final 200 KiB log tail. |
| process_cancel | Stop and remove a process session. |
| workspace_destroy | Delete the workspace, processes, files, and same-workspace caches. |

The immutable Java images include Temurin JDK 17, 21, and 25, Maven 3.9.16, and Gradle 9.7.0;
repository wrappers remain preferred. Maven and Gradle caches persist only for the life of one
workspace.

The boundary is strict:

- only a lowercase 40-character commit available through GitHub is mounted;
- staged, unstaged, and untracked local files are not uploaded;
- the sandbox receives a short-lived repository-scoped read token for checkout and no GitHub write
  credential;
- remote edits stay disposable and are not synchronized locally or pushed;
- commands have outbound network access and execute repository build code; and
- the agent must cancel abandoned processes and destroy every workspace when finished.

The current shared workspace is suitable for public or self-contained Java builds. It does not
have a customer's corporate VPN/VPC, private artifact repositories, internal databases, licensed
services, or arbitrary enterprise secrets. When one of those is required, the skill keeps the
check local and reports remote evidence as unavailable instead of classifying the repository as
broken or retrying the cloud.

This means a remote run of committed `HEAD` is not proof of dirty local changes. The skill reports
that limitation instead of manufacturing a green result.

## What the agent should return

Every workflow ends with a concise record:

~~~text
Scope: revision, module, and files reviewed
Changes: what changed and why
Commands: exact local or remote commands
Evidence: passed, failed, skipped, or unavailable results
Limitations: unmeasured or excluded boundaries
Cleanup: remote process and workspace destruction outcome, when used
~~~

Missing JaCoCo, PIT, ArchUnit, OpenRewrite, authentication, private services, or another dependency
is reported as unavailable. JAIPilot does not silently add tooling, lower thresholds, or describe
an unmeasured property as passing.

## Deliberate boundary

JAIPilot’s skills are procedural guidance, not an enforcement engine. The MCP is execution
infrastructure, not another AI agent. Neither guarantees correctness, business intent, coverage,
architecture, performance, or host-agent compliance.

Local workflows stay on the developer machine apart from normal repository tool behavior. When the
remote skill is explicitly used, the selected committed repository is processed by GitHub,
Supabase, and Daytona under their respective service terms and data boundaries. See
[Privacy](PRIVACY.md), [Security](SECURITY.md), and [Terms](TERMS.md).

The previous all-local runtime, metrics, and dashboard implementation is preserved at
[v4.0.8](https://github.com/JAIPilot/jaipilot/releases/tag/v4.0.8).

## Project

- [JAIPilot Cloud campaign results](CLOUD_RESULTS.md)
- [Contributing](CONTRIBUTING.md)
- [Support](SUPPORT.md)
- [Security](SECURITY.md)
- [Privacy](PRIVACY.md)
- [Terms](TERMS.md)
- [Changelog](CHANGELOG.md)

Licensed under the [MIT License](LICENSE).
