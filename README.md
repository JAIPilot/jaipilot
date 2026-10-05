<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/JAIPilot/jaipilot/main/assets/jaipilot-logo-dark.svg">
    <source media="(prefers-color-scheme: light)" srcset="https://raw.githubusercontent.com/JAIPilot/jaipilot/main/assets/jaipilot-logo.svg">
    <img src="https://raw.githubusercontent.com/JAIPilot/jaipilot/main/assets/jaipilot-logo.svg" alt="JAIPilot" width="96" height="96">
  </picture>
</p>

<h1 align="center">JAIPilot — faster Java testing, stronger coverage, fewer regressions</h1>

<p align="center">
  <strong>Make your tests bulletproof.<br>
  Enterprise grade quality unit tests. A 90% line coverage target.<br>
  Make your coding agent better at Java testing.</strong>
</p>

**Give Codex, Claude Code, or your existing coding agent a Java testing specialist.** Testing with a general coding agent can mean long waits, repeated prompts, and missed behavior or edge cases. JAIPilot helps your agent **lock existing behavior before edits and catch regressions afterward**.

**Built for faster testing, 90%+ coverage, and stronger regression protection.** JAIPilot writes, repairs, and verifies tests in one focused workflow, targeting **at least 90% line coverage in your selected code** when coverage tooling is configured. It focuses on meaningful assertions, boundary cases, and failure paths, giving your coding agent real test results to guide its fixes.

**Optimized for outcomes. Choose an outcome. Select your code. Get verified tests.** Use JAIPilot from the terminal or connect it to your coding agent through MCP. It works with your project's existing build and test framework.

<p align="center">
  <a href="https://github.com/JAIPilot/jaipilot/releases/latest">Download CLI</a> ·
  <a href="#install">Get started</a> ·
  <a href="#mcp-for-coding-agents">MCP setup</a> ·
  <a href="https://www.jaipilot.com/">JAIPilot.com</a>
</p>

## How it works

- **Spend less time asking for tests.** One workflow carries the task through inspection, test generation, repairs, and local verification.
- **Cover the scenarios that matter.** Meaningful assertions protect existing behavior, boundaries, and failure paths.
- **Change code with more confidence.** Lock behavior before your coding agent edits, then verify the preserved tests afterward.

`generate_tests`, `improve_coverage`, and `lock_behavior` target **at least 90% line coverage in the selected production code** when coverage tooling is configured, and report measured gaps or blockers. Locking behavior also requires assertions for what the code does and passing tests before edits.

| You want to… | Run this workflow |
| --- | --- |
| Create unit tests | `generate_tests` |
| Improve coverage | `improve_coverage` |
| Fix failing tests | `fix_tests` |
| Stabilize flaky tests | `stabilize_flaky_tests` |
| Test your current changes | `test_current_changes` |
| Capture behavior before a refactor | `lock_behavior` |
| Target 100% line coverage (experimental) | `coverage_100` |

## Install

You need Git, a JDK, and your project's build tools. On macOS or Linux:

```bash
curl -fsSL https://raw.githubusercontent.com/JAIPilot/jaipilot/main/install.sh | sh
```

On Windows, [download the executable](https://github.com/JAIPilot/jaipilot/releases/latest/download/jaipilot-x86_64-pc-windows-msvc.exe) and put it on your `PATH`.

Sign in, then run inside your Java repository:

```bash
jaipilot auth login
jaipilot run improve_coverage --class com.acme.OrderService
```

Use your existing JAIPilot account, subscription, and credits. The CLI automatically checks for updates before workflows. See the [CLI guide](docs/cli-guide.md) for installation paths, manual updates, and troubleshooting.

## Use the CLI

```bash
jaipilot run generate_tests --class com.acme.OrderService
jaipilot run test_current_changes --all
jaipilot run lock_behavior --path src/main/java/com/acme
```

Choose the whole repository, classes, paths, or selected lines. JAIPilot runs focused checks and a final regression suite. Review the result and `git diff` before accepting changes.

[All commands, scopes, JSON output, and exit codes →](docs/cli-guide.md#run-an-outcome)

## MCP for coding agents

**Make Codex, Claude Code, or another MCP coding agent better at changing Java code.** JAIPilot adds stronger test coverage of existing behavior and a verification step before the agent finishes.

1. **Lock behavior before editing.** JAIPilot creates and runs characterization tests for current behavior, boundaries, and failure paths, targeting at least 90% line coverage in the selected code when coverage tooling is configured.
2. **Make the change.** Your coding agent edits the production code while preserving those tests.
3. **Verify the original baseline.** JAIPilot reruns the same tests. Unintended changes to covered behavior become test failures the agent can investigate and repair.

Capturing expectations before the edit reduces regression risk and gives the agent concrete feedback about what broke. Passing tests protect the behavior they exercise; intentional behavior changes need an explicit review of the old expectations.

Connect the agent you use:

**Codex**

```bash
codex mcp add jaipilot -- jaipilot mcp --repo /absolute/path/to/java-repository
```

**Claude Code**

```bash
claude mcp add --transport stdio jaipilot -- jaipilot mcp --repo /absolute/path/to/java-repository
```

Start a new agent session. **The workflow is built into the MCP instructions and tool descriptions; no repository instruction file is required.** The MCP server writes tests and runs commands locally. Jobs run in the background, with a default local command timeout of 1 hour.

MCP requires a build with the `mcp` command. [Setup from source, tool contracts, and timeout options →](docs/coding-agents.md)

### Measured example: Codex with JAIPilot

Two fresh Codex sessions received the same ordinary refactor request in a small fixture containing two Kafka utilities. The MCP run used the testing workflow automatically, with no repository instruction file or extra testing prompt.

| Saved regression suite | Codex alone | Codex + JAIPilot |
| --- | --- | --- |
| Passing JUnit tests | 3 | **16** |
| Line coverage of the selected `Deadline` class | 47.1% | **100%** |
| Line coverage across both fixture classes | 40% | **65.7%** |
| Branch coverage across both fixture classes | 30% | **80%** |
| Injected faults caught by the saved tests | 3/10 | **7/10** |

**The 90% target applies to the selected code:** `Deadline` reached **100% line coverage (17/17 lines)**. The combined **65.7% (23/35 lines)** also includes `LockUtils`, which was outside the selected scope and remained at 6/18 covered lines.

**13 additional reusable tests. Four additional faults caught.** Both runs used the same model, settings, initial tests, and warm build caches. This is one measured example; results vary.

[Method, evidence, and reproduction →](docs/benchmarks/codex-mcp-2026-10-05/README.md)

## See it run

- **Apache Kafka:** 34.3% → 97.1% line coverage for two classes in **1m 53s**; all **949 module tests passed**.
- **Spring Petclinic:** 85.7% → 100% line coverage in **3m 21s** with the experimental `coverage_100` workflow; all **111 tests passed**.

Both runs preserved production code. These are individual runs with warm build caches; results vary by project. [Watch the recordings and see the full measurements →](docs/examples.md)

## Local execution and privacy

Builds and tests run on your computer. Selected source, project context, and command output are sent to JAIPilot's managed service and model provider. Sign-in credentials are stored locally with user-only permissions.

[Privacy details](docs/cli-guide.md#local-execution-and-privacy) · [Ask a question](https://github.com/JAIPilot/jaipilot/discussions) · [Report an issue](https://github.com/JAIPilot/jaipilot/issues) · [Report a vulnerability privately](https://github.com/JAIPilot/jaipilot/security/advisories/new)

[MIT License](LICENSE) · [JAIPilot.com](https://www.jaipilot.com/)
