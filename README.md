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

| **75/75** tests passed      | **44.6s** clean Maven build | **3 JDKs** ready    | **5 hours** remote compute included monthly |
| --------------------------- | --------------------------- | ------------------- | ------------------------------------------- |
| Petclinic remote acceptance | Fresh disposable workspace  | Java 17, 21, and 25 | Per signed-in beta user                     |

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
Use JAIPilot to make my current Java change as lean, safe, and fast as possible. Preserve behavior,
run the repository's real checks, and use remote hardware for substantial work when useful.
```

JAIPilot may add meaningful tests, remove unused code, simplify equivalent logic, improve measured
performance, evaluate compatible upgrades, review the complete diff, and run a final clean build. If
no safe improvement can be proved, it should make no change.

## Real JAIPilot results

All three acceptance runs used Spring Framework Petclinic and repository-native verification:

| Use case                                                                                            | Result                                                                                                                                                                                             |
| --------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [Lean and optimize a change](https://github.com/skrcode/spring-framework-petclinic/pull/25)         | **10** behavior tests added; **7 net production lines removed** (+2/-9); changed method reached **100%** instruction, line, and branch coverage; **85/85** tests passed; Java 17 and 21 CI passed. |
| [Cover previously untested behavior](https://github.com/skrcode/spring-framework-petclinic/pull/34) | **7** focused tests added with **no production or dependency change**; target coverage moved from **0% to 100%** for lines and branches; **82/82** tests passed independently on Java 17 and 21.   |
| Run the current change remotely                                                                     | An uncommitted file reached the workspace; `./mvnw clean test` passed **75/75** tests in **44.6 seconds**; job recovery, cancellation, and workspace deletion were verified.                       |

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

The Java skills work locally immediately. When your agent first needs JAIPilot remote hardware, sign
in to JAIPilot when prompted. No VM account, API key, SSH configuration, GitHub App, or local
JAIPilot runtime is required.

## Included skills

| Skill                     | Outcome                                                 |
| ------------------------- | ------------------------------------------------------- |
| `jaipilot-optimize-java`  | Make one bounded Java change leaner, safer, and faster. |
| `jaipilot-generate-tests` | Add meaningful tests and fresh coverage evidence.       |
| `jaipilot-clean-java`     | Remove proven waste and simplify real complexity.       |
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
