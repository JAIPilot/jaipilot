# Dashboard current-status evaluation

## Automatic post-commit candidate

On 2026-08-08, the automatic post-commit candidate was measured against its `da595e6` parent on
the same macOS 13.1 x64 machine with Temurin 25.0.3. Both runs used the same repository, Maven
cache, working tree, warm dependency state, and v3.3.2 shaded runner. Timings below measure
wall-clock process time. The candidate measurements used the final packaged jar.

The acceptance criteria were:

- an observed agent `git commit` refreshes whole-project metrics and returns actionable continuation
  feedback without running a build;
- ordinary shell commands leave the Java runtime stopped;
- warmed `/api/metrics` median and p95 remain below 15 ms across five requests;
- the rendered score follows the persisted API within one normal three-second polling interval;
- desktop and 390-pixel mobile views have no browser errors or horizontal overflow; and
- real ArchUnit evidence appears with its engine, ruleset, class count, violation count, and honest
  proof status.

The parent had no post-commit refresh. Its closest manual sequence was a whole-project `quality`
process followed by the existing clean Stop-hook process:

| Journey | Raw observations (seconds) | Median | p95 |
| --- | --- | --- | --- |
| Parent `quality --mode all` | 1.47, 1.46, 1.53, 1.48, 1.49 | 1.48 | 1.53 |
| Parent clean `hook-stop` | 0.69, 0.66, 0.68, 0.66, 0.67 | 0.67 | 0.69 |
| Candidate automatic post-commit refresh and diff gate | 1.710, 1.688, 1.757, 1.703, 1.725 | 1.710 | 1.757 |

The two parent medians sum to 2.15 seconds, but they were measured as separate processes and are not
a paired end-to-end observation. The candidate adds about 1.71 seconds to each observed commit where
the parent did no automatic work. It replaces the two manual Java starts with one automatic process.
No build, test, coverage, mutation, or ArchUnit process runs in this quick hook; those remain part of
the proof loop that the hook tells the agent to complete.

The shell filter was also measured across ten non-commit tool completions. Raw observations were
12.47, 10.12, 10.26, 9.67, 9.11, 8.93, 9.12, 8.68, 8.47, and 8.29 milliseconds. The median was
9.12 ms and nearest-rank p95 was 12.47 ms. These paths did not start the Java runtime.

After an unmeasured warmup, the final metrics API returned a 6,173-byte response in 0.007269,
0.007228, 0.007448, 0.007183, and 0.007287 seconds. Median was 0.007269 seconds and nearest-rank
p95 was 0.007448 seconds.

A Playwright Chromium run loaded the packaged dashboard at 1440 by 900 pixels and at 390 by 844
pixels. The initial rendered 78.9 score exactly matched the live API. While the page remained open,
an automatic hook replaced the snapshot with a clean 100 score and zero findings; the browser showed
the new values after 1.261 seconds. A 65-finding snapshot rendered 12 bounded rows with the full
severity totals. Both viewports had no horizontal overflow, background image, console error, or page
error.

The same browser then read proof produced by ArchUnit 1.4.2 after a real clean Maven build. It showed
`CLEAN`, ruleset v1, one compiled changed class, and zero violations. The adjacent proof remained
`FAILED` because that fixture had no fresh JaCoCo report. This confirms that clean architecture
evidence does not conceal a different failed gate.

Agent reasoning time, builds, tests, dependency resolution, and proof time are excluded from the
post-commit and API timings. The ArchUnit browser check includes those systems only as correctness
evidence, not as hook latency.

## Earlier v3.3.0 current-status evaluation

On 2026-08-08, the current-status dashboard candidate was compared with its `c6b4054` v3.3.0
parent on the same macOS 13.1 x64 machine with Temurin 25.0.3. Both shaded jars were built from
their respective worktrees with the same Maven repository and then served on separate loopback
ports with isolated empty state directories.

The acceptance criteria were:

- warmed `/api/metrics` median and p95 remain below 15 ms across five alternating requests;
- a running browser replaces failed proof evidence within one normal three-second polling interval;
- desktop and 390-pixel mobile layouts render the current findings and architecture state without
  browser errors or horizontal overflow;
- a newer clean proof removes the prior findings, architecture violations, and blocking messages.

After one unmeasured warmup request to each server, `curl` measured the complete loopback request and
response time:

| Revision | Raw observations (seconds) | Median | p95 | Response bytes |
| --- | --- | --- | --- | --- |
| v3.3.0 (`c6b4054`) | 0.006963, 0.006673, 0.006537, 0.006466, 0.006594 | 0.006594 | 0.006963 | 1,155 |
| Current-status candidate | 0.008633, 0.006848, 0.007039, 0.006858, 0.006525 | 0.006858 | 0.008633 | 1,911 |

The larger candidate response is the expected bounded schema payload for findings, ArchUnit, and
gate status. Both the median and nearest-rank p95 remain below the acceptance threshold.

A Playwright Chromium check then loaded the packaged candidate from its real loopback server. The
initial proof contained one high finding, one `JAI-ARCH-001` violation, one failure, and one warning.
The browser rendered each value and detail. While the page remained open, a second metrics writer
persisted a clean proof. The normal dashboard poll changed findings and architecture to `CLEAN`, both
counts to zero, and the gate text to passed in 2.416 seconds. Desktop and 390-by-844 mobile checks
completed with zero console or page errors and no horizontal overflow.

This evaluation separates dashboard/API time from Java build, agent, dependency-resolution, and
proof time. It demonstrates presentation freshness; the underlying analysis correctness remains
covered by the quality, validation, and changed-code proof test suites.

A post-review browser check seeded seven failures and two warnings to exercise bounded gate
feedback. Chromium rendered exactly six rows plus `Showing 6 of 9 gate messages`, exposed the
severity counters as an accessible group, retained 10-pixel action context, and had zero console
errors or mobile horizontal overflow.
