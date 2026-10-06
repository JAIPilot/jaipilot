# JAIPilot CLI guide

[Quick start](../README.md#install) · [Coding agent integration](coding-agents.md) · [Recorded runs](examples.md)

JAIPilot is a fast, high coverage testing agent for Java, **optimized for the testing outcome you choose**. **Choose an outcome. Select your code. Get verified tests.** Each outcome guides how JAIPilot plans, writes, repairs, and verifies your tests using your project's existing build and test framework.

## Spend less time asking for tests

Long waits. Repeated prompts. More test code to maintain, while important scenarios still slip through. If that is your experience asking a general coding agent to write tests, JAIPilot gives Java testing a clear finish line.

- **Focus on the scenarios that matter.** Generate tests for behavior, boundaries, and failure paths with meaningful assertions. Aim for focused, maintainable tests that protect your code.
- **Let the agent carry the task through.** JAIPilot plans, writes, repairs, and verifies the tests. Focused checks and a final regression suite provide evidence for the outcome.
- **Generate and verify in one workflow.** Run one command to generate tests, verify them locally, and measure coverage.
- **See the coverage you gained.** JAIPilot targets **at least 90% line coverage in your selected production scope** when coverage tooling is configured. It reports fresh measurements and concrete blockers.

Use the whole repository, a list of classes, paths, or selected lines. Builds, tests, and coverage reporting run on your computer. Sign in with your existing JAIPilot account and use the same subscription and credits.

## Install

Requirements: Git, a JDK, and your project's Maven, Gradle, or other Java build tools.

On **macOS or Linux**, install with one command:

```bash
curl -fsSL https://raw.githubusercontent.com/JAIPilot/jaipilot/main/install.sh | sh
```

The installer detects your OS and CPU, verifies the release's SHA-256 checksum, and installs
`jaipilot` into `~/.local/bin`. If that directory is not on your `PATH`, add this to your shell profile:

```bash
export PATH="$HOME/.local/bin:$PATH"
```

On **Windows**, download the [Windows executable](https://github.com/JAIPilot/jaipilot/releases/latest/download/jaipilot-x86_64-pc-windows-msvc.exe),
rename it to `jaipilot.exe`, and put it in a directory on your `PATH`.

Sign in and run inside your Java repository:

```bash
jaipilot auth login
jaipilot run improve_coverage --class com.acme.OrderService
```

Sign-in opens [jaipilot.com](https://www.jaipilot.com/) and uses the same JAIPilot account, subscription, and credits as the IntelliJ distribution.

## Updates

Before `run` and `workflows`, the CLI checks for a newer stable GitHub release. When one is available,
it downloads the matching binary, verifies its checksum and version, installs it, and restarts with
your original arguments. Failed checks or downloads leave the current installation usable and the
requested command continues. Help, version, and account commands do not check for updates.

```bash
jaipilot update          # Install the latest stable release now
jaipilot update --check  # Check without installing
JAIPILOT_NO_UPDATE=1 jaipilot run improve_coverage --class com.acme.OrderService
```

The installer can also upgrade or repair an existing installation. To choose a directory or pin a release:

```bash
curl -fsSL https://raw.githubusercontent.com/JAIPilot/jaipilot/main/install.sh | \
  JAIPILOT_INSTALL_DIR="$HOME/bin" JAIPILOT_VERSION=v1.0.0 sh
```

Updates leave your Java repositories untouched. Downloads require internet access and write
access to the CLI's installation directory.

## Verify a coverage target

For single-module Maven projects with active JaCoCo configuration:

```sh
jaipilot run improve_coverage --all --coverage-target 80
jaipilot run improve_coverage --class com.acme.OrderService --coverage-target 80 --branch-coverage-target 70
```

These optional flags run fresh tests and verify local JaCoCo XML counters. Existing
configured minima are retained. Without these flags, existing workflows keep their
current behavior. For Maven/Gradle build tasks and exported coverage policies, see
[the build tools guide](build-tools.md).

## Run an outcome

```bash
jaipilot workflows
jaipilot run improve_coverage --all
jaipilot run generate_tests --class com.acme.OrderService --class com.acme.InvoiceService
jaipilot run stabilize_flaky_tests --path src/test/java/com/acme/OrderServiceTest.java
jaipilot run improve_coverage --selection src/main/java/com/acme/OrderService.java:42-88
```

Run these commands inside a Git repository, or add `--repo /path/to/repository`. You can repeat `--path`, `--class`, or `--selection` to supply a list. Use exactly one scope type per run:

| Scope | Example | Meaning |
| --- | --- | --- |
| Entire repository | `--all` | Discover the Java source and tests across the repository. |
| Paths | `--path module/src/main/java` | One or more files or directories. |
| Classes | `--class com.acme.OrderService` | One or more Java classes; simple names work when unambiguous. |
| Selections | `--selection path/File.java:42-88` | One or more line ranges, verified in their containing classes. |

| Outcome | Workflow |
| --- | --- |
| Create focused tests and verify them | `generate_tests` |
| Measure coverage and close meaningful gaps | `improve_coverage` |
| **100% coverage (Experimental): make code agent ready** | `coverage_100` |
| Repair failing tests and rerun the suite | `fix_tests` |
| Reproduce and stabilize flaky tests | `stabilize_flaky_tests` |
| Test behavior changed in your current Git diff | `test_current_changes` |
| Capture existing behavior before refactoring | `lock_behavior` |

The agent uses your existing build and test framework. It is instructed to preserve production code and unrelated edits, runs focused tests and the final suite, and reports fresh coverage when configured. If coverage tooling is absent, it reports that plainly.

**100% Coverage (Experimental)** is a CLI-only outcome that targets 100% line coverage
for your selected production code to make it agent ready. It uses the same workflow as
`improve_coverage`, with a prompt requesting 100% instead of the usual 90% target.
The agent reports measured coverage and concrete blockers when the target cannot be reached.

```bash
jaipilot run coverage_100 --all
jaipilot run coverage_100 --class com.acme.OrderService
```

The final job output shows the total time taken, including agent requests and local
commands, for example `Time taken: 1m 53s`.

Use `--json` for structured results in scripts, including elapsed time in `durationMs`:

```bash
jaipilot run improve_coverage --class com.acme.OrderService --json
```

Exit codes are `0` for a completed workflow, `2` for a concrete blocker, and `1` for a CLI or service error. Review the result and `git diff` before accepting changes. Repository-wide work may consume more credits than a focused class run.

## Local execution and privacy

Your refreshable sign-in is stored locally with user-only file permissions. Workflow requests send selected paths, project metadata, and local command output to JAIPilot's managed service and model provider. Parallel class work can also upload selected source and related test/build context to a private job tied to your account.

Commands, builds, and tests run locally with your user permissions. Review the result and `git diff` before accepting changes. Keep credentials, private source, and unredacted logs out of public issues.
