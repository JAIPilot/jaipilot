# Dashboard current-status evaluation

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
