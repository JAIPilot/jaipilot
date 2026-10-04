<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/JAIPilot/jaipilot/main/assets/jaipilot-logo-dark.svg">
    <source media="(prefers-color-scheme: light)" srcset="https://raw.githubusercontent.com/JAIPilot/jaipilot/main/assets/jaipilot-logo.svg">
    <img src="https://raw.githubusercontent.com/JAIPilot/jaipilot/main/assets/jaipilot-logo.svg" alt="JAIPilot" width="96" height="96">
  </picture>
</p>

<h1 align="center">JAIPilot — faster, more accurate Java testing with better coverage than your coding agent</h1>

<p align="center">
  <strong>5× faster than testing with a regular coding agent.<br>
  Enterprise grade quality unit tests. A 90% line coverage target.</strong>
</p>

<h2 align="center">Optimized for outcomes</h2>

<p align="center">
  <strong>Generate tests · Improve coverage · Fix failing tests<br>
  Stabilize flaky tests · Test current changes · Lock existing behavior</strong>
</p>

<p align="center">
  <a href="https://github.com/JAIPilot/jaipilot/releases/latest">Download CLI</a> ·
  <a href="#install">Get started</a> ·
  <a href="https://www.jaipilot.com/">JAIPilot.com</a>
</p>

## Watch 100% coverage on Spring Petclinic (Experimental)

<p align="center">
  <img src="assets/petclinic-coverage-100-demo.gif" alt="Actual macOS Terminal recording of JAIPilot's experimental coverage_100 workflow on the entire official Spring Framework Petclinic project, from 85.7% to 100% line coverage in 3 minutes 21 seconds" width="900">
</p>

**One command. The entire Petclinic project. 85.7% → 100% line coverage in 3 minutes 21 seconds.** The experimental `coverage_100` outcome targets 100% line coverage to make code agent ready. This run added **36 tests**, and all **111 tests** passed. Only test files changed.

Recorded directly from macOS Terminal with the released JAIPilot CLI and production backend. The GIF shortens idle waits; the reported runtime is the full job duration.

<details>
<summary>Run the same workflow</summary>

After [installing](#install) and signing in, use the official Spring Framework Petclinic repository:

```bash
git clone https://github.com/spring-petclinic/spring-framework-petclinic.git
cd spring-framework-petclinic
jaipilot run coverage_100 --all
```

This recording used CLI **1.0.2**, official [Petclinic commit `2d75ede`](https://github.com/spring-petclinic/spring-framework-petclinic/commit/2d75ede6e3a01c4b227e3e05001e6c53a48990bb), JDK 17, and a warm Maven dependency cache. Coverage measures all production code in the project's existing JaCoCo report: **449/524 → 524/524 lines** across 36 executable classes. Branch coverage reached **97.4% (111/114)**. A separate `./mvnw clean verify` build independently confirmed the coverage, all 111 passing tests, and successful WAR packaging. Production code, existing tests, and build configuration were preserved.

`coverage_100` is experimental and prompt based. Runtime and achievable coverage vary by project; 100% line coverage does not guarantee correctness.

</details>

## Watch it run on Apache Kafka

<p align="center">
  <img src="assets/kafka-cli-demo.gif" alt="Actual macOS Terminal recording of JAIPilot improving Apache Kafka's Deadline and LockUtils tests, from 34.3% to 97.1% selected line coverage" width="900">
</p>

**One command. Two Kafka classes. 34.3% → 97.1% line coverage in 1 minute 53 seconds.** The run added 13 tests covering behavior, equality, lock handling, and exception paths. All **949 tests in the `server-common` module** passed; production code was unchanged.

Recorded directly from macOS Terminal with JAIPilot CLI. The GIF shortens idle waits.

<details>
<summary>Run the same workflow</summary>

After [installing](#install) and signing in, run inside an Apache Kafka checkout:

```bash
jaipilot run improve_coverage \
  --class org.apache.kafka.server.util.Deadline \
  --class org.apache.kafka.server.util.LockUtils
```

This recording used a development build, [Kafka commit `adf8c4c`](https://github.com/apache/kafka/commit/adf8c4cebdab7e31267be7ce5430d7c6b13701cf), JDK 25, and a warm Gradle dependency cache. Coverage measures the two selected classes (12/35 → 34/35 lines). A fresh module test run, JaCoCo report, Checkstyle, and Spotless independently confirmed the result. Runtime and coverage vary by project.

</details>

JAIPilot is a fast, high coverage testing agent for Java, **optimized for the testing outcome you choose**. **Choose an outcome. Select your code. Get verified tests.** Each outcome guides how JAIPilot plans, writes, repairs, and verifies your tests using your project's existing build and test framework.

## Spend less time asking for tests

Long waits. Repeated prompts. More test code to maintain, while important scenarios still slip through. If that is your experience asking a general coding agent to write tests, JAIPilot gives Java testing a clear finish line.

- **Focus on the scenarios that matter.** Generate tests for behavior, boundaries, and failure paths with meaningful assertions. Aim for focused, maintainable tests that protect your code.
- **Let the agent carry the task through.** JAIPilot plans, writes, repairs, and verifies the tests. Focused checks and a final regression suite provide evidence for the outcome.
- **5× faster than testing with a regular coding agent.** Run one command to generate tests, verify them locally, and measure coverage.
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

## Verified on Petclinic

| Selected classes | Time | Line / branch coverage | Full suite |
| --- | --- | --- | --- |
| 1 | 62 seconds | 100% / 100% | 83 passing tests |
| 2 | 67 seconds | 100% / 100% | 85 passing tests |
| 5 | 126 seconds | 100% / 100% | 95 passing tests |

Each run began from the same clean Petclinic commit using Java 17 and a warm Maven dependency cache. Fresh clean builds independently confirmed the results. Only test files changed. Runtime and coverage vary by project; quantifying a 5× speedup requires a controlled sequential comparison.

## Local execution and privacy

Your refreshable sign-in is stored locally with user-only file permissions. Workflow requests send selected paths, project metadata, and local command output to JAIPilot's managed service and model provider. Parallel class work can also upload selected source and related test/build context to a private job tied to your account.

Commands, builds, and tests run locally with your user permissions. Review the result and `git diff` before accepting changes. Keep credentials, private source, and unredacted logs out of public issues.

## Get help

[Ask a question](https://github.com/JAIPilot/jaipilot/discussions) · [Report an issue](https://github.com/JAIPilot/jaipilot/issues) · [Report a vulnerability privately](https://github.com/JAIPilot/jaipilot/security/advisories/new)

For issues, include your CLI version, OS, JDK, build tool, workflow, and sanitized output.

---

[JAIPilot.com](https://www.jaipilot.com/) · [Download](https://github.com/JAIPilot/jaipilot/releases/latest) · [MIT License](LICENSE)
