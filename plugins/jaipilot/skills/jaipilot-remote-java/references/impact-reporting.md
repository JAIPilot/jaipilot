# Show remote-verification impact

Make the offloaded proof visible without turning remote duration into an invented speedup.

## During the run

When a remote attempt reaches a terminal state and the host supports progress commentary, give at
most one sentence with exact revision, command outcome, and elapsed time. If this skill is nested,
pass its outcome, duration, source boundary, cleanup result, and limitations to the coordinating
JAIPilot skill instead of rendering another card.

## Final impact card

Immediately after the final response's one-sentence outcome lead, render this standalone card or
contribute its row to the coordinator's consolidated card:

> **JAIPilot impact**
> - **Remote verification:** exact revision, substantial command, outcome, and duration
> - **Proof boundary:** source digest and cleanup result; speedup measured or unmeasured

Use at most three evidence rows. Prefer the substantial command offloaded, tests or analyzers
executed, remote wall time, exact-SHA boundary, and verified source cleanup. A speedup requires a
matched baseline with the same revision, JDK, command, workload, caches, and acceptance checks.

Otherwise say `speedup not measured`; never convert remote duration into local time saved or imply
that hardware made the evidence stronger. A deterministic build failure can be a useful diagnosis,
but infrastructure failure is not product impact. Do not upload or persist impact telemetry.
