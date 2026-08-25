<p align="center">
  <img src="plugins/jaipilot/assets/jaipilot-logo.svg" alt="JAIPilot" width="112">
</p>

# JAIPilot

**Ship better Java with the coding agent you already use.**

JAIPilot helps Codex and Claude Code clean, test, review, modernize, and optimize real Java
repositories without drifting into unnecessary code or unproved changes.

For heavy builds, tests, analysis, and benchmarks, your agent can use a ready remote Java machine
instead of tying up your laptop.

JAIPilot does not replace your coding agent or add another AI. It gives your agent focused Java
workflows, remote compute, and one rule: **show evidence, not confidence.**

| **75 → 85** tests     | **0% → 100%** target coverage | **7** net production lines removed | **25 → 24** class complexity |
| --------------------- | ----------------------------- | ---------------------------------- | ---------------------------- |
| +13.3%, zero failures | Lines and branches            | Same behavior                      | One unused method removed    |

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

## Why teams use JAIPilot

- **Less agent drift** — changes stay bounded, lean, and aligned with the repository.
- **Better Java code** — remove proven waste, reduce complexity, improve tests, review risky diffs,
  modernize safely, and optimize measured bottlenecks.
- **Real verification** — use the repository's Maven or Gradle build, tests, coverage, architecture
  rules, and performance measurements.
- **Free up your laptop** — move long Java work to disposable remote hardware with JDK 17, 21, and
  25, Maven, and Gradle ready.
- **Works on your current change** — staged, unstaged, and untracked files can be tested without
  committing or pushing first.
- **Your agent stays in control** — Codex or Claude Code chooses every edit and command and reports
  exactly what JAIPilot achieved.

## Start with one prompt

```text
Make my current Java changes production-ready without changing their behavior. Add any missing
tests, remove unnecessary code, simplify the implementation, improve performance where you can
measure a real benefit, and run the project's full verification before you finish. Use JAIPilot's
remote environment if the heavier checks would be better run away from my laptop.
```

JAIPilot may add meaningful tests, remove unused code, simplify equivalent logic, improve measured
performance, evaluate compatible upgrades, review the complete diff, and run a final clean build. If
no safe improvement can be proved, it should make no change.

## More proven results

Additional Petclinic acceptance runs used repository-native verification:

| Use case                                                                                            | Result                                                                                                                                                                                           |
| --------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| [Cover previously untested behavior](https://github.com/skrcode/spring-framework-petclinic/pull/34) | **7** focused tests added with **no production or dependency change**; target coverage moved from **0% to 100%** for lines and branches; **82/82** tests passed independently on Java 17 and 21. |
| Run the current change remotely                                                                     | An uncommitted file reached the workspace; `./mvnw clean test` passed **75/75** tests in **44.6 seconds**; job recovery, cancellation, and workspace deletion were verified.                     |

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

The Java skills work locally immediately. When your agent first needs JAIPilot remote hardware, sign
in to JAIPilot when prompted. No VM account, API key, SSH configuration, GitHub App, or local
JAIPilot runtime is required.

## Included skills

| Skill                     | Outcome                                                 |
| ------------------------- | ------------------------------------------------------- |
| `jaipilot-optimize-java`  | Make one bounded Java change leaner, safer, and faster. |
| `jaipilot-generate-tests` | Add meaningful tests and fresh coverage evidence.       |
| `jaipilot-clean-java`     | Remove waste, enforce architecture, and simplify code.   |
| `jaipilot-review-diff`    | Find regressions, unnecessary code, and missing proof.  |
| `jaipilot-fast-execution` | Run substantial Java verification efficiently.          |
| `jaipilot-remote-java`    | Run long Java commands on disposable remote hardware.   |

JAIPilot can work with repository-configured tools such as JaCoCo, PIT, ArchUnit, OpenRewrite,
Checkstyle, PMD, SpotBugs, SonarQube, JMH, and JFR. It never weakens a quality gate merely to get a
green result.

## Remote build beta

The beta includes one active remote workspace and five compute hours per user each month. Remote
work is disposable and never commits, pushes, or publishes code. Use it for repositories that build
without a corporate VPN, private artifact service, internal database, or unavailable secret.

See [Security](SECURITY.md), [Privacy](PRIVACY.md), [Support](SUPPORT.md), [Terms](TERMS.md), and
the [Changelog](CHANGELOG.md).

Licensed under the [MIT License](LICENSE).
