# JAIPilot 4.0 lean-kernel evidence

Measured on 2026-08-08 on macOS 13.1 x86_64 with Temurin 25.0.3. Java compilation still
targets Java 17. Commands used the same final JAIPilot source tree, warm dependencies, and an
owner-private temporary state directory. p95 is the nearest-rank value for five runs.

## Size and self-quality

The release diff removes 7,898 lines and adds 4,717, for a net deletion of 3,181 lines. The final
changed-production analysis covers 19 files and 6,856 lines. It reports quality 96.5, zero
critical/high findings, 14 medium findings, one low finding, and 0.1% duplication.

## Stable local paths

| Path | Raw wall seconds | Median | p95 | Result |
| --- | --- | --- | --- | --- |
| v3.4 changed-quality runner on the final source | 1.32, 1.45, 1.36, 1.39, 1.35 | 1.36 | 1.45 | Baseline |
| v4.0 changed-quality runner on the final source | 1.26, 1.35, 1.44, 1.37, 1.38 | 1.37 | 1.44 | No material wall-time change |
| Packaged MCP initialize plus tools/list | 1.41, 1.12, 1.09, 1.10, 1.10 | 1.10 | 1.41 | Exactly six tools; protocol-only stdout |
| Live dashboard `/api/metrics` | 0.014179, 0.010895, 0.017362, 0.013525, 0.009874 | 0.013525 | 0.017362 | Same current snapshot on every request |

The packaged dashboard was also rendered in Chrome at 1440×900 and 390×844. Both views showed the
same repository, current score, severity totals, findings, and exact proof state.

## Kafka brownfield acceptance

The source repository was Apache Kafka at `33b93fc411441c95a89f40e17006f2b1ae2c77da`.
JAIPilot used a disposable shared clone; the source checkout remained clean.

- Snapshot: 3,658 production files, 406,024 lines, 6,451 findings in 12.90 seconds.
- Diff: one Javadoc-only change in `clients`, with baseline `HEAD` and exactly one production target.
- Proof run 1: failed closed after 7m21s of internal verification because 1 of 13,315
  `:clients:test` tests failed; 29 were skipped.
- Proof run 2: failed closed after 9m00s because 2 of 13,315 tests failed; 29 were skipped.
- Both runs used one `clean build jaipilotTargetCoverage` Gradle lifecycle with cache/up-to-date
  bypass, coverage enabled, parallelism disabled, and the machine dependency cache. Neither run
  issued a proof receipt or claimed coverage, mutation, or architecture success.

This is a verified upstream test-suite blocker on this machine, not a passing Kafka proof. JAIPilot
did not retry a third time or weaken the clean-build gate.
