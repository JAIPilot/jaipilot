# Show execution impact

Make JAIPilot's execution contribution visible without manufacturing a speed claim.

## During the run

When substantial execution completes and the host supports progress commentary, give at most one
sentence with elapsed time, worker count, and proof outcome. Skip it when it adds no user-relevant
evidence. If this skill is nested, pass its outcome, timing, command scope, worker count, proof, and
measurement boundary to the coordinating JAIPilot skill instead of rendering another card.

## Final impact card

Immediately after the final response's one-sentence outcome lead, render this standalone card or
contribute its row to the coordinator's consolidated card:

> **JAIPilot impact**
> - **Execution:** `<baseline> → <final> (<delta>)`, or observed duration with speedup unmeasured
> - **Proof:** command scope, worker count, and result

Use at most three evidence rows. Prefer total wall time, matched baseline-to-final reduction,
commands or test classes safely batched, and serial fallback. A speedup is valid only when both runs
use the same revision, caches, JDK, command, workload, services, and acceptance checks.

Do not run an unnecessary slow baseline just to create a metric. Without a matched baseline, report
the observed duration and concurrency as delivery evidence and say `speedup not measured`. Never
infer time saved from worker count, remote hardware, or CI from another revision. Do not upload or
persist impact telemetry.
