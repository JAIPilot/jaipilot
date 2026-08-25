<p align="center">
  <img src="plugins/jaipilot/assets/jaipilot-logo.svg" alt="JAIPilot" width="112">
</p>

# JAIPilot

**Ship better Java with your coding agent.**

JAIPilot helps Codex and Claude Code clean, test, review, modernize, and optimize real Java
repositories without drifting into unnecessary code or unproved changes.

By default, your agent runs Java builds, tests, analysis, profiling, and benchmarks on a ready
remote Java machine whenever the task does not require laptop-only access or state.

JAIPilot does not replace your coding agent or add another AI. It gives your agent focused Java
workflows, remote compute, and one rule: **show evidence, not confidence.**

| **12.2–80.3% faster** | **61.3–62.5% faster** | **87.5–92.4% faster** | **8 → 2** SQL statements |
| --------------------- | --------------------- | --------------------- | ------------------------ |
| OTel lookup medians   | Micrometer merges     | Calcite JMH medians   | N+1 removed              |

## JAIPilot vs no JAIPilot

The original [Petclinic PR](https://github.com/skrcode/spring-framework-petclinic/pull/21) already
had a green build. JAIPilot reviewed that exact head and produced this
[companion change](https://github.com/skrcode/spring-framework-petclinic/pull/25):

| Metric                         |       Without JAIPilot | With JAIPilot | Outcome                         |
| ------------------------------ | ---------------------: | ------------: | ------------------------------- |
| Tests                          |                     75 |            85 | **+10 tests (+13.3%)**          |
| Changed-method line coverage   |              0/12 (0%) |  11/11 (100%) | **+100 percentage points**      |
| Changed-method branch coverage |               0/8 (0%) |    8/8 (100%) | **+100 percentage points**      |
| `Owner` class line coverage    |          22/53 (41.5%) | 33/51 (64.7%) | **+23.2 points with less code** |
| Production change              | Unused helper remained |   +2/-9 lines | **7 net lines removed**         |
| `Owner` methods                |                     16 |            15 | **1 unused method removed**     |
| `Owner` complexity             |                     25 |            24 | **4% lower**                    |
| Clean Maven verification       |           75/75 passed |  85/85 passed | **Both stayed green**           |

The important result is not simply “more tests.” Without JAIPilot, the build passed while the new
behavior had zero coverage and unused code remained. With JAIPilot, the same behavior stayed green,
the edge cases became executable tests, and production code became smaller.

The comparison uses the original PR head and JAIPilot's direct child commit, clean worktrees, the
same `./mvnw -q clean verify` command, and fresh JaCoCo 0.8.14 reports.

## Measured performance: Apache Calcite

On `skrcode/calcite` at exact commit
[`d3a5d8d`](https://github.com/skrcode/calcite/tree/d3a5d8d9e6713c5fd483810e1aa1f38652d2dd8d),
JAIPilot profiled Calcite's existing
`DefaultDirectedGraphBenchmark.removeAllVertices{10,50,90}Benchmark`. The 50% workload attributed
31.1% of runnable samples to `Collection.removeIf`: the implementation scanned the complete global
edge set once for every removed vertex.

The candidate changed two files (+28/-5), removed the repeated scans, and added behavior tests for
the majority-removal and self-loop paths. Lower JMH scores are better:

| Removed vertices | Baseline median (µs/op) | JAIPilot median (µs/op) | Improvement | Baseline p95 (µs/op) | JAIPilot p95 (µs/op) | Improvement |
| ---------------: | ----------------------: | ----------------------: | ----------: | -------------------: | -------------------: | ----------: |
|              10% |                  26.710 |                   2.029 |   **92.4%** |               27.142 |                2.439 |   **91.0%** |
|              50% |                  74.619 |                   9.140 |   **87.8%** |               87.245 |               14.993 |   **82.8%** |
|              90% |                  77.423 |                   9.677 |   **87.5%** |               89.514 |               10.052 |   **88.8%** |

Baseline and candidate ran on the same large remote workspace with the same Temurin JDK 17, built
JMH jar, command, and workload. Each row contains 21 measured observations: seven forks with three
measured iterations per fork after warm-up. The identical focused command passed 15/15 tests before
and after the production edit. A fresh exact-SHA `:core:clean :core:check` then completed 16,644
tests with 0 failures and 155 skips, and the tested remote diff matched the local candidate digest.

This is a controlled result for Calcite's existing graph-removal workloads, not a claim that every
Java workload becomes faster.

## Measured performance: OpenTelemetry Java

On `skrcode/opentelemetry-java` at exact commit
[`35636ae`](https://github.com/skrcode/opentelemetry-java/tree/35636aec8d3dc6706bb483bad92383fdd6012af0),
JAIPilot found that immutable attribute sets were sorted by key name during construction but still
used a full linear scan for every lookup. This matters at the default span limit of 128 attributes.

The [three-file draft change](https://github.com/skrcode/opentelemetry-java/pull/3) preserves the
small-set and first-four-entry fast path, then uses binary search for the rest. It also adds a
large-set behavior test and a repository-native JMH benchmark. Lower values are better:

| Lookup          | Baseline median (ns/op) | JAIPilot median (ns/op) | Improvement | Baseline p95 | JAIPilot p95 | Improvement |
| --------------- | ----------------------: | ----------------------: | ----------: | -----------: | ------------: | ----------: |
| First           |                   2.483 |                   2.179 |   **12.2%** |        2.637 |         2.272 |   **13.8%** |
| Middle          |                 169.124 |                  85.905 |   **49.2%** |      178.250 |        88.537 |   **50.3%** |
| Last            |                 346.195 |                  87.323 |   **74.8%** |      358.321 |        90.084 |   **74.9%** |
| Missing         |                 141.560 |                  69.552 |   **50.9%** |      150.321 |        74.261 |   **50.6%** |
| Last as `Value` |                 368.629 |                  72.684 |   **80.3%** |      387.467 |        78.087 |   **79.8%** |

Baseline and candidate ran in the same large remote workspace with Temurin JDK 21, the same JMH
jar, command, warm-up, and workload. Each row has 21 observations. The new focused behavior test
passed before and after the production edit; a clean `:api:all:check` passed all 147 tasks including
Animal Sniffer, Checkstyle, Spotless, tests, and japicmp. The tested remote Git delta matched the
local candidate digest. The `Value` workload still allocates about 16 B/op; JAIPilot reports the
lookup-time win without claiming that allocation disappeared.

## Measured performance: Micrometer

On `skrcode/micrometer` at exact commit
[`22207bf`](https://github.com/skrcode/micrometer/tree/22207bf9ccc973a1b1bc3890b33645e53fb2e475),
JAIPilot found that adding or replacing one `Tag` or `KeyValue` went through temporary varargs and
iterable merge machinery even though the backing arrays were already sorted.

The [six-file draft change](https://github.com/skrcode/micrometer/pull/3) adds a bounded binary-search
merge for the single-value overloads, behavior tests, and four workloads in Micrometer's existing
JMH module:

| Replacement workload | Baseline median (ns/op) | JAIPilot median (ns/op) | Improvement | Baseline p95 | JAIPilot p95 | Improvement | Allocation |
| -------------------- | ----------------------: | ----------------------: | ----------: | -----------: | ------------: | ----------: | ---------: |
| `KeyValues.and`      |                  57.941 |                  22.449 |   **61.3%** |       63.554 |        23.487 |   **63.0%** | **136 → 104 B/op** |
| `Tags.and`           |                  58.968 |                  22.101 |   **62.5%** |       63.388 |        24.425 |   **61.5%** | **136 → 104 B/op** |

Single-value insertion reduced median allocation by 17.6% and p95 allocation by 46.2%. Its median
latency improved by only 6.7–8.9%, below JAIPilot's 10% shared-hardware threshold, so it is not
presented as a speed win. The same-workspace experiment used 21 observations per workload; 92
focused tests and the final clean scoped build passed, with 1,132 tests, zero failures, and the
remote production diff matching the local digest.

## Measured database work: Petclinic

On the current Petclinic JDBC vet listing, JAIPilot found a real N+1 query path: six vets required
eight SQL statements (`2 + N`). It kept the ordered vet query and replaced the per-vet specialty
lookups with one joined association query.

| Evidence                  | Baseline | JAIPilot candidate |
| ------------------------- | -------: | ------------------: |
| SQL statements, six vets |        8 |               **2** |
| Growth with vet count     |  `2 + N` |       **constant 2** |
| Focused JDBC tests        |    11/11 |           **15/15** |
| Clean repository build    |    75/75 |           **79/79** |

The new tests lock vet ordering, specialty ordering, vets without specialties, duplicate links,
shared specialty identity, empty data, and the exact two-statement ceiling. The final clean Maven
build and JaCoCo report passed on a large remote workspace, and the local and remote binary diff
digests matched. This is deterministic query-count evidence; JAIPilot does not turn it into an
invented latency claim.

## Why teams use JAIPilot

- **Less agent drift** — changes stay bounded, lean, and aligned with the repository.
- **Better Java code** — remove proven waste, reduce complexity, improve tests, review risky diffs,
  modernize safely, and optimize measured bottlenecks.
- **Real verification** — use the repository's Maven or Gradle build, tests, coverage, architecture
  rules, and performance measurements.
- **Remote-first execution** — use disposable hardware with JDK 17, 21, and 25, Maven, and Gradle
  ready unless private networks, local services, secrets, hardware, or state require the laptop.
- **Remote performance lab** — profile and compare a bounded optimization on one large workspace,
  with matching patch identity, raw observations, median, p95, and correctness evidence.
- **Works on your current change** — staged, unstaged, and untracked files can be tested without
  committing or pushing first.
- **Your agent stays in control** — Codex or Claude Code chooses every edit and command and reports
  exactly what JAIPilot achieved.

## Start with one prompt

```text
Make my current Java changes production-ready without changing their behavior. Add any missing
tests, remove unnecessary code, simplify the implementation, improve performance where you can
measure a real benefit, and run the project's full verification before you finish. Use JAIPilot
Remote for Java execution unless the work genuinely requires resources available only on my laptop.
```

JAIPilot may add meaningful tests, remove unused code, simplify equivalent logic, improve measured
performance, evaluate compatible upgrades, review the complete diff, and run a final clean build. If
no safe improvement can be proved, it should make no change.

For a performance-focused run:

```text
Use JAIPilot to profile this Java workload on large remote hardware, make one small local
optimization, and keep it only if the same-workspace measurements and full build prove a real win.
```

## More proven results

Additional acceptance runs used repository-native verification:

| Use case                                                                                            | Result                                                                                                                                                                                           |
| --------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Optimize Apache Calcite graph removal                                                               | Existing JMH workload medians improved by **87.5–92.4%** across 10%, 50%, and 90% removal cases; matching behavior tests and a **16,644-test** clean check passed.                               |
| Optimize OpenTelemetry attribute lookup                                                             | Five 128-attribute lookup workloads improved by **12.2–80.3%** at median and **13.8–79.8%** at p95; clean API compatibility and verification passed.                                           |
| Optimize Micrometer single-value merges                                                             | Replacement merges improved by **61.3–62.5%** at median and allocated **23.5% less**; sub-threshold insertion latency was not marketed as a speed win.                                        |
| [Cover previously untested behavior](https://github.com/skrcode/spring-framework-petclinic/pull/34) | **7** focused tests added with **no production or dependency change**; target coverage moved from **0% to 100%** for lines and branches; **82/82** tests passed independently on Java 17 and 21. |
| Run the current change remotely                                                                     | An uncommitted file reached the workspace; `./mvnw clean test` passed **75/75** tests in **44.6 seconds**; job recovery, cancellation, and workspace deletion were verified.                     |
| Remove a JDBC N+1 query                                                                              | Vet listing SQL statements fell from **8 to 2** on the six-vet fixture; **15/15** focused and **79/79** clean-build tests passed, with identical local/remote diff digests.                 |

These are reproducible acceptance results, not claims that every repository will see the same
coverage, code reduction, or speed.

## Install

### Codex

```bash
codex plugin marketplace add JAIPilot/jaipilot
codex plugin add jaipilot@jaipilot
```

### Claude Code

```text
/plugin marketplace add JAIPilot/jaipilot
/plugin install jaipilot@jaipilot
```

### MCP Registry

Clients that consume the official MCP Registry can discover the hosted remote tools as
`io.github.JAIPilot/jaipilot`. This installs remote execution only; install the Codex or Claude Code
plugin above for the six Java engineering skills as well.

The Java skills work locally immediately. When your agent first uses JAIPilot remote hardware, sign
in and approve the repository upload when prompted. No VM account, API key, SSH configuration,
GitHub App, or local JAIPilot runtime is required.

## Included skills

| Skill                     | Outcome                                                 |
| ------------------------- | ------------------------------------------------------- |
| `jaipilot-optimize-java`  | Make one bounded Java change leaner, safer, and faster. |
| `jaipilot-generate-tests` | Add meaningful tests and fresh coverage evidence.       |
| `jaipilot-clean-java`     | Remove waste, enforce architecture, and simplify code.   |
| `jaipilot-review-diff`    | Find regressions, unnecessary code, and missing proof.  |
| `jaipilot-fast-execution` | Run substantial Java verification efficiently.          |
| `jaipilot-remote-java`    | Default applicable Java execution to remote hardware.   |

JAIPilot can work with repository-configured tools such as JaCoCo, PIT, ArchUnit, OpenRewrite,
Checkstyle, PMD, SpotBugs, SonarQube, JMH, and JFR. It never weakens a quality gate merely to get a
green result.

## Remote build beta

The beta includes one active remote workspace and five compute hours per user each month. Remote
work is disposable and never commits, pushes, or publishes code. JAIPilot defaults applicable Java
execution to remote hardware; it stays local when a corporate VPN, private artifact service,
internal database, unavailable secret, machine-specific state, or another laptop-only resource is
required.

See [Security](SECURITY.md), [Privacy](PRIVACY.md), [Support](SUPPORT.md), [Terms](TERMS.md), and
the [Changelog](CHANGELOG.md).

Licensed under the [MIT License](LICENSE).
