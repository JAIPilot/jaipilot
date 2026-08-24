<p align="center">
  <img src="plugins/jaipilot/assets/jaipilot-logo.svg" alt="JAIPilot" width="112">
</p>

# JAIPilot

**Verified Java engineering and remote build hardware for Codex and Claude Code.**

JAIPilot helps your existing coding agent keep Java changes small, clean, fast, and demonstrably
safe. Six focused skills guide testing, cleanup, review, modernization, and measured optimization.
When laptop setup or runtime becomes the bottleneck, the same agent can send the current repository
working tree to a ready, disposable Java machine and run long Maven or Gradle work there.

JAIPilot is not another AI agent. Codex or Claude Code remains the only planner and editor.

```text
Your request → your Codex or Claude Code → JAIPilot Java skills → repository-native evidence
                                                └──────────────→ remote Java hardware when useful
```

## Why use JAIPilot?

A default coding agent can already edit code and run local commands. JAIPilot adds value where
professional Java work usually becomes slow or unreliable:

- **Less agent drift.** The skills require the agent to bound scope, preserve contracts, use the
  repository's real checks, reject unsupported guesses, and report evidence—not confidence.
- **Java-specific judgment.** Tests, safe deletion, framework/reflection boundaries, compatible
  upgrades, JDK selection, JMH/JFR measurement, ArchUnit, JaCoCo, PIT, and OpenRewrite are handled
  as one coherent workflow instead of a generic “improve this” prompt.
- **No machine setup tax.** Remote workspaces already have JDK 17, 21, and 25, Maven 3.9.16, and
  Gradle 9.7.0. There is no VM account, API key, SSH setup, or local Deno runtime to configure.
- **The current change is testable.** Tracked files plus unignored staged, unstaged, and untracked
  files can be uploaded explicitly. The proof is not limited to an old Git commit.
- **Long work survives disconnects.** Commands are asynchronous and return durable IDs for status,
  bounded logs, cancellation, and cleanup.
- **Your model remains your model.** JAIPilot supplies workflows and compute; it does not add a
  second reasoning loop or charge for another hidden coding agent.

The honest promise is not “remote is always faster.” The benefit is ready Java capacity, fewer local
dependencies, a free laptop, durable execution, and repeatable evidence on one controlled machine. A
speed or performance claim is made only when comparable measurements prove it.

## What has been proved

The version 6.0.0 release acceptance used standard Codex OAuth and a fresh Spring Framework
Petclinic checkout containing an uncommitted marker:

| JAIPilot claim               | Acceptance evidence                                                                              | User benefit                                                               |
| ---------------------------- | ------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------- |
| Normal hosted sign-in        | default OAuth discovery, consent, callback, and token exchange succeeded without a shared secret | no cloud API key, VM account, GitHub App, or provider setup                |
| Current changes run remotely | 141 tracked/unignored files were digest-verified; the uncommitted marker appeared remotely       | test staged, unstaged, and untracked work without committing or pushing it |
| Java is ready                | preinstalled Temurin JDK 17.0.20 ran `./mvnw clean test`                                         | avoid installing the repository toolchain on the laptop                    |
| Real repository proof        | `BUILD SUCCESS`; 75 tests, 0 failures, 0 errors, 0 skipped; Maven time 44.647 seconds            | receive the build's result, not an agent's confidence                      |
| Long work is durable         | status and bounded logs were recovered through returned process IDs                              | reconnect to work instead of restarting it with the chat session           |
| Compute is controllable      | a separate running process was cancelled and the workspace deletion was confirmed                | stop abandoned work and release the machine predictably                    |

This proves authentication, current-working-tree transport, toolchain readiness, a real clean Java
build, durable process recovery, cancellation, and cleanup. It does not claim that the shared
machine is faster than every developer laptop, that a green test suite proves all behavior, or that
the environment represents production.

## Start with one request

```text
Use JAIPilot to optimize my current Java change, verify it with the repository's own checks, and
offload substantial verification to JAIPilot remote hardware when that materially helps.
```

The agent will bound the change, run focused checks, remove only proved waste, simplify equivalent
logic, optimize measured workloads, evaluate compatible upgrades, review the whole diff, and run a
final clean build. An honest pass may end with no code change.

## Skills

| Skill                     | What it does                                                                                                                               |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `jaipilot-optimize-java`  | Orchestrates one bounded Java improvement across tests, cleanup, performance, and justified modernization.                                 |
| `jaipilot-generate-tests` | Adds meaningful tests or runs a bounded per-class coverage campaign using fresh configured evidence.                                       |
| `jaipilot-clean-java`     | Removes proven waste, consolidates equivalent logic, reduces complexity, modernizes compatible versions, and optimizes measured workloads. |
| `jaipilot-review-diff`    | Reviews the complete Java change for regressions, unnecessary code, architecture drift, compatibility risk, and missing proof.             |
| `jaipilot-fast-execution` | Speeds substantial Maven/Gradle verification through safe resource sizing, batching, and native parallelism.                               |
| `jaipilot-remote-java`    | Offloads long Java commands for the explicitly uploaded current working tree.                                                              |

The skills use JaCoCo, PIT, ArchUnit, OpenRewrite, Checkstyle, PMD, SpotBugs, Error Prone, SonarQube
reports, JMH, JFR, or similar tools only when already configured or when the user approves adding
them. They never weaken thresholds, add suppressions, or install machinery to manufacture a pass.

## Install

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

The skills work locally without a JAIPilot account. On the first remote tool call, Codex or Claude
Code opens the standard OAuth flow; sign in at **jaipilot.com** and approve the connection. No
JAIPilot secret, GitHub App, cloud-provider account, or local runtime is required.

## Remote Java execution

| Tool                | Purpose                                                   |
| ------------------- | --------------------------------------------------------- |
| `workspace_prepare` | Create a short-lived, user-owned source upload.           |
| `workspace_create`  | Verify the archive and create one private Java workspace. |
| `process_start`     | Start one asynchronous repository command.                |
| `process_status`    | Read durable running state and the final exit code.       |
| `process_logs`      | Read a bounded final 200 KiB log tail.                    |
| `process_cancel`    | Stop and remove a process session.                        |
| `workspace_destroy` | Delete the workspace, processes, files, and caches.       |

Current public-beta bounds are one active medium workspace, a 15-120 minute hard lifetime, a 100 MiB
source archive, and five included compute hours per signed-in user each month. Source upload is
explicit. The service verifies user ownership, size, and SHA-256, deletes the upload after workspace
preparation, and gives the workspace no GitHub write credential.

Remote edits are disposable and never synchronize, commit, push, open a pull request, or merge.
Build code has outbound network access. Work requiring a corporate VPN/VPC, private artifact
repository, internal database, licensed service, or unavailable secret stays on the customer's own
execution boundary.

## Evidence, not confidence

Every workflow should return:

```text
Scope: Git state, modules, and files
Changes: accepted and rejected hypotheses with reasons
Commands: exact local or remote commands and outcomes
Evidence: tests, coverage, architecture, analysis, compatibility, or measurements
Benefit: setup avoided, resources freed, durable recovery, or measured improvement
Limitations: unavailable services, profiles, consumers, or production conditions
Cleanup: cancellation and confirmed workspace destruction
```

JAIPilot skills are procedural guidance, not an enforcement engine. A green build proves only the
tested boundary, not universal correctness or business intent.

## Repository

The distributable plugin lives in `plugins/jaipilot` and contains only manifests, six skills,
metadata, assets, and the hosted MCP URL. Root-level Deno and Python files only validate and release
the bundle. There is no npm package, hook, watcher, dashboard, telemetry SDK, downloaded runtime, or
provider credential.

See [Contributing](CONTRIBUTING.md), [Support](SUPPORT.md), [Security](SECURITY.md),
[Privacy](PRIVACY.md), [Terms](TERMS.md), and the [Changelog](CHANGELOG.md).

Licensed under the [MIT License](LICENSE).
