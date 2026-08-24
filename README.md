<p align="center">
  <img src="plugins/jaipilot/assets/jaipilot-logo.svg" alt="JAIPilot" width="112">
</p>

# JAIPilot

**Behavior-locked Java engineering for Codex and Claude Code.**

JAIPilot helps your coding agent turn a Java change into its smallest verified form: lock behavior,
add meaningful tests, remove proven waste, reduce real complexity, measure performance, evaluate
compatible upgrades, and run the repository's own proof.

The customer's agent remains the only planner and editor. JAIPilot is six focused skills plus six
optional remote-execution tools—not another agent, code generator, daemon, or background scanner.

~~~text
Your request → Codex or Claude Code → JAIPilot skills → repository-native evidence
                                           └──────→ remote hardware, only when useful
~~~

## Start with one request

~~~text
Use JAIPilot to optimize my current Java change without changing its behavior.
~~~

The agent will:

1. bound the exact diff, module, or project area and preserve unrelated work;
2. run the repository's baseline and lock observable behavior with focused tests;
3. remove only proved-unused code and consolidate only genuinely equivalent logic;
4. optimize only a measured workload or deterministic operation count;
5. modernize only stable versions proved compatible with the declared boundary; and
6. review the complete result and run the final clean repository build.

An applicable pass may end with no change. Missing proof is reported as unavailable or rejected,
never converted into a synthetic win.

## Skills

| Skill | What it does |
| --- | --- |
| `jaipilot-optimize-java` | Orchestrates one bounded, behavior-locked Java improvement across tests, cleanup, performance, and justified modernization. |
| `jaipilot-generate-tests` | Adds characterization or regression tests, or runs a bounded per-class coverage campaign using fresh configured evidence. |
| `jaipilot-clean-java` | Removes proven waste, consolidates equivalent logic, reduces complexity, modernizes compatible versions, and optimizes measured workloads. |
| `jaipilot-review-diff` | Reviews the complete Java change for regressions, unnecessary code, architecture drift, compatibility risk, and missing proof. |
| `jaipilot-fast-execution` | Speeds substantial Maven/Gradle builds, tests, and analyzers through safe resource sizing, batching, and native parallelism. |
| `jaipilot-remote-java` | Runs long commands on disposable hardware for one exact committed GitHub revision. |

The skills use JaCoCo, PIT, ArchUnit, OpenRewrite, Checkstyle, PMD, SpotBugs, Error Prone,
SonarQube reports, JMH, JFR, or similar tools only when the repository already configures them or
the user approves adding them. They do not silently weaken thresholds, add suppressions, or install
quality machinery to manufacture a pass.

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

The six skills have no JAIPilot runtime dependency. Deno 2.x is required only for the bundled
remote MCP adapter.

## Optional remote execution

The host agent may offload an expensive committed build, test suite, analyzer, profiler, or
benchmark. It still chooses every command and owns the interpretation.

| Tool | Purpose |
| --- | --- |
| `workspace_create` | Check out an exact GitHub commit in a private TTL-bounded workspace. |
| `process_start` | Start one asynchronous repository command. |
| `process_status` | Read durable running state and the final exit code. |
| `process_logs` | Read a bounded final 200 KiB log tail. |
| `process_cancel` | Stop and remove a process session. |
| `workspace_destroy` | Delete the workspace, processes, files, and same-workspace caches. |

The Java images include Temurin JDK 17, 21, and 25, Maven 3.9.16, and Gradle 9.7.0. Repository
wrappers remain preferred.

Remote execution has a strict boundary:

- only one lowercase 40-character commit already available through GitHub is mounted;
- staged, unstaged, and untracked local files are never uploaded;
- the sandbox receives a short-lived repository-scoped read token and no GitHub write credential;
- remote edits remain disposable and are neither synchronized nor pushed;
- every process is bounded and every workspace must be destroyed; and
- repository build code runs with outbound network access.

The shared workspace has no customer VPN/VPC, private artifact repository, internal database,
licensed service, or arbitrary enterprise secret. Those checks remain on the customer's existing
execution boundary. Remote `HEAD` evidence never proves dirty local changes.

Remote execution is currently a limited operator preview. It requires the JAIPilot GitHub App and
separately provisioned authentication; public customer authentication is not shipped in this
release. Never paste that credential into a prompt or repository.

## Evidence, not confidence

Every workflow returns a concise engineering record:

~~~text
Scope: revision, comparison base, modules, and files
Changes: accepted and rejected hypotheses with reasons
Commands: exact local or remote commands and outcomes
Evidence: behavior, tests, coverage, architecture, analysis, or measurements
Limitations: unavailable services, profiles, consumers, or production conditions
Cleanup: remote cancellation and workspace destruction, when applicable
~~~

JAIPilot skills are procedural guidance, not an enforcement engine. A green build is evidence for
the tested boundary, not proof of universal correctness or business intent.

## Repository

The distributable plugin lives in `plugins/jaipilot`. Root-level Deno and Python files exist only to
format, type-check, protocol-test, structurally validate, and release that bundle; they are not
installed as a separate CLI or runtime. The repository intentionally contains no npm package,
hooks, watcher, dashboard, telemetry SDK, generated binary, or downloaded runtime.

See [Contributing](CONTRIBUTING.md), [Support](SUPPORT.md), [Security](SECURITY.md),
[Privacy](PRIVACY.md), [Terms](TERMS.md), and the [Changelog](CHANGELOG.md).

Licensed under the [MIT License](LICENSE).
