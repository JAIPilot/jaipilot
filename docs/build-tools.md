# Maven coverage workflows

JAIPilot 1.2.1 adds optional Maven goals that read the evaluated JaCoCo configuration,
run fresh tests, and compare the XML counters with every supported coverage minimum.
`check` performs local verification without a JAIPilot account or model call. `run`
uses your signed-in account to generate tests when coverage is below the requirements,
then repeats local verification. Review the generated test diff.

Existing builds and CLI workflows remain unchanged until you select these commands.
JAIPilot does not install JaCoCo, edit production code, or lower existing coverage gates.
Targets are percentages: `80` means 80%, while a JaCoCo ratio of `0.80` is read as 80%.
An explicit target adds a requirement; it cannot override a stricter configured minimum.
Line and branch requirements are evaluated separately, without rounding for pass/fail.

## Installation status

The Maven adapter, shared core, and parent POM 1.2.1 are published on Maven Central
under `com.jaipilot`. The Maven commands below resolve directly from Central.
The Maven adapter uses native JAIPilot 1.2.1 from PATH or downloads that exact release with
checksum verification when an adapter goal runs. No Node.js or Deno is required.
Use `jaipilot.executable` to select an already installed 1.2.1 executable.

## Maven

Your project must already configure active JaCoCo `prepare-agent` instrumentation.
Use its configured `check` minimum, or supply a target when no minimum exists:

```sh
./mvnw com.jaipilot:jaipilot-maven-plugin:1.2.1:check -Djaipilot.coverage.line=80
jaipilot auth login
./mvnw com.jaipilot:jaipilot-maven-plugin:1.2.1:run -Djaipilot.coverage.line=80
```

On Windows, use `mvnw.cmd` in PowerShell. For selected classes or a separate branch target:

```sh
./mvnw com.jaipilot:jaipilot-maven-plugin:1.2.1:run \
  -Djaipilot.coverage.classes=com.acme.OrderService \
  -Djaipilot.coverage.line=80 -Djaipilot.coverage.branch=70
```

Omit explicit percentages to use existing JaCoCo `check` rules. The aggregator reads
the selected reactor modules and active profiles; `pom` modules are skipped. JaCoCo
report execution IDs must match across selected modules. The agent and report must
use the same execution data file. A fresh `clean test` prevents deleted tests or
old execution data from influencing the result. Existing `verify` gates run after
the measured targets pass. Existing lifecycle bindings are not modified.

## Native CLI

For single-module Maven projects, a percentage opts into fresh verification:

```sh
jaipilot run improve_coverage --all --coverage-target 80
jaipilot run improve_coverage --class com.acme.OrderService --coverage-target 80
```

Without the new options, existing `run improve_coverage` behavior is preserved.
The Maven adapter exports a policy to `target/jaipilot/coverage-policy.json`.
You can reuse it directly:

```sh
jaipilot coverage check --policy target/jaipilot/coverage-policy.json
jaipilot coverage run --policy target/jaipilot/coverage-policy.json --json
```

The policy includes repository-relative report and execution-data paths, production
scope, test roots, test/report commands, optional native verification command, and
versioned JaCoCo targets. Commands are executable build instructions; review a policy
before running one supplied by someone else. Tracked files and symlinks cannot be
removed as coverage evidence. Generation requires a clean Git working tree before
the first model call and rejects changes outside the declared test roots.

Unsupported rule values, missing JaCoCo, missing targets, empty scopes, zero measurable
counters, missing fresh XML, failing tests, and unmet requirements are blockers.
Maximum iterations default to 3 (range 1–10); the total timeout defaults to 1,200 seconds
(range 1–7,200). Configure `jaipilot.maxIterations` / `jaipilot.timeoutSeconds` in Maven.
Exit 0 means fresh verification passed; exit 2 means a valid run was blocked;
exit 1 means an invalid request or error.
