<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/jaipilot-logo-dark.svg">
    <source media="(prefers-color-scheme: light)" srcset="assets/jaipilot-logo.svg">
    <img src="assets/jaipilot-logo.svg" alt="JAIPilot" width="96" height="96">
  </picture>
</p>

<h1 align="center">JAIPilot — workflow-based Java testing in your IDE</h1>

<p align="center"><strong>Generate tests · Improve coverage · Fix failing tests<br>
Stabilize flaky tests · Test current changes · Lock existing behavior</strong></p>

JAIPilot's ACP adapter brings six testing outcomes to IntelliJ AI Assistant and other ACP clients
with terminal support. Choose an outcome, supply an exact Java scope, and review the commands, test
results, coverage evidence, and complete diff in your IDE. Use your existing JAIPilot account,
subscription, and credits.

## Get started in IntelliJ

The ACP Registry submission requires upstream review before JAIPilot appears in the public
**Settings → Tools → AI Assistant → Agents** catalog. You can use it now as a custom agent:

1. Download your platform's archive and checksum from the
   [JAIPilot ACP release](https://github.com/JAIPilot/jaipilot/releases/latest), verify the hash, and extract it.
2. Add the extracted executable as **JAIPilot** using AI Chat → **Add Custom Agent**.
3. Disable **Pass custom MCP servers** and **Pass IntelliJ MCP server** for this bounded adapter.
4. Select JAIPilot in AI Chat and sign in when prompted.
5. Choose an outcome or enter a slash command with scope:

```text
/generate_tests --class com.acme.OrderService
/improve_coverage --selection src/main/java/com/acme/OrderService.java:42-88
/fix_tests --all
```

[Complete IntelliJ setup, all outcomes, and limitations](docs/intellij-acp.md).

Requirements: Git, your project's supported JDK and build tools, AI Assistant with ACP terminal
support, and a JAIPilot account. The release executable includes its runtime; JAIPilot does not
install Java or build tools. No npm or npx distribution is published.

## Exact scope and evidence

Use exactly one scope type: the entire Git repository (`--all`), repeated paths (`--path`), Java
classes (`--class`), or selected line ranges (`--selection`). Attached Java file links can supply
scope when no explicit flags are present. Relative paths use the IDE session's working directory.
Save editor buffers before running, since commands use the files on disk.

The workflows instruct the service to preserve production source and unrelated work, follow the
repository's existing test conventions, run focused verification and the final test suite, and
measure coverage only through configured tools. Instructions alone do not enforce correctness.
Review the full diff and rerun your required checks. Missing coverage, failed checks, and blockers
remain explicit; no coverage target or speedup is guaranteed.

## Permissions and privacy

Commands run in local IDE terminals with your user permissions. JAIPilot asks for service consent
and approval for each command, displays execution evidence, and supports cancellation and timeouts.
Rejecting a command stops the workflow. This adapter uses the local-tools service protocol and does
not start remote workspaces or bulk source-upload workers.

Selected paths, project metadata, your text request, and approved command output are sent to
JAIPilot's managed model service and its provider. Commands can read source into their output;
inspect them before approving them. Keep credentials and private logs out of public issues.

This release supports six stable outcomes and ephemeral sessions. Custom MCP servers, image/audio
prompts, session restoration, and the CLI's experimental 100% coverage outcome are unavailable.

## Earlier CLI demonstrations

These recordings show earlier CLI runs, not this ACP adapter. The experimental `coverage_100`
outcome is available in CLI 1.0.2 and is not part of the ACP release.

### Watch it run on Apache Kafka

<p align="center">
  <img src="assets/kafka-cli-demo.gif" alt="Actual macOS Terminal recording of JAIPilot improving Apache Kafka's Deadline and LockUtils tests, from 34.3% to 97.1% selected line coverage" width="900">
</p>

**One command. Two Kafka classes. 34.3% → 97.1% line coverage in 1 minute 53 seconds.** The run added 13 tests covering behavior, equality, lock handling, and exception paths. All **949 tests in the `server-common` module** passed; production code was unchanged.

Recorded directly from macOS Terminal with JAIPilot CLI. The GIF shortens idle waits.

<details>
<summary>Run the same workflow</summary>

After [installing the earlier CLI](https://github.com/JAIPilot/jaipilot/blob/v1.0.2/README.md#install) and signing in, run inside an Apache Kafka checkout:

```bash
jaipilot run improve_coverage \
  --class org.apache.kafka.server.util.Deadline \
  --class org.apache.kafka.server.util.LockUtils
```

This recording used a development build, [Kafka commit `adf8c4c`](https://github.com/apache/kafka/commit/adf8c4cebdab7e31267be7ce5430d7c6b13701cf), JDK 25, and a warm Gradle dependency cache. Coverage measures the two selected classes (12/35 → 34/35 lines). A fresh module test run, JaCoCo report, Checkstyle, and Spotless independently confirmed the result. Runtime and coverage vary by project.

</details>

### Watch 100% coverage on Spring Petclinic (Experimental)

<p align="center">
  <img src="assets/petclinic-coverage-100-demo.gif" alt="Actual macOS Terminal recording of JAIPilot's experimental coverage_100 workflow on the entire official Spring Framework Petclinic project, from 85.7% to 100% line coverage in 3 minutes 21 seconds" width="900">
</p>

**One command. The entire Petclinic project. 85.7% → 100% line coverage in 3 minutes 21 seconds.** The experimental `coverage_100` outcome targets 100% line coverage to make code agent ready. This run added **36 tests**, and all **111 tests** passed. Only test files changed.

Recorded directly from macOS Terminal with the released JAIPilot CLI and production backend. The GIF shortens idle waits; the reported runtime is the full job duration.

<details>
<summary>Run the same workflow</summary>

After [installing the earlier CLI](https://github.com/JAIPilot/jaipilot/blob/v1.0.2/README.md#install) and signing in, use the official Spring Framework Petclinic repository:

```bash
git clone https://github.com/spring-petclinic/spring-framework-petclinic.git
cd spring-framework-petclinic
jaipilot run coverage_100 --all
```

This recording used CLI **1.0.2**, official [Petclinic commit `2d75ede`](https://github.com/spring-petclinic/spring-framework-petclinic/commit/2d75ede6e3a01c4b227e3e05001e6c53a48990bb), JDK 17, and a warm Maven dependency cache. Coverage measures all production code in the project's existing JaCoCo report: **449/524 → 524/524 lines** across 36 executable classes. Branch coverage reached **97.4% (111/114)**. A separate `./mvnw clean verify` build independently confirmed the coverage, all 111 passing tests, and successful WAR packaging. Production code, existing tests, and build configuration were preserved.

`coverage_100` is experimental and prompt based. Runtime and achievable coverage vary by project; 100% line coverage does not guarantee correctness.

</details>

## Development

```sh
deno task check
deno task compile:acp
deno run -A scripts/check-acp.ts dist/jaipilot-acp
```

CI verifies source and the packaged ACP handshake on Linux, macOS, and Windows. The release workflow
publishes only ACP archives and their SHA-256 checksums for macOS Intel/Apple Silicon, Linux
x86-64/ARM64, and Windows x86-64. ACP stdio contains only protocol messages; diagnostics use stderr.

The earlier CLI remains available at [v1.0.2](https://github.com/JAIPilot/jaipilot/releases/tag/v1.0.2)
with its [versioned documentation](https://github.com/JAIPilot/jaipilot/blob/v1.0.2/README.md).

[JAIPilot.com](https://www.jaipilot.com/) ·
[Report an issue](https://github.com/JAIPilot/jaipilot/issues) · [MIT License](LICENSE)
