# Show Java cleanup impact

Make JAIPilot's contribution visible without turning the handoff into an advertisement.

## During the run

When a meaningful cleanup stage completes and the host supports progress commentary, give at most
one sentence with the strongest new evidence, for example: `JAIPilot cleanup complete — one unused
method and seven net production lines removed; focused tests remain green.` Skip the update when it
would repeat an earlier result.

If this skill is nested, return its outcome, comparable baseline and final values, proof, and
unmeasured boundary to the coordinating JAIPilot skill. Do not render a separate impact card for
every nested skill.

## Final impact card

Immediately after the final response's one-sentence outcome lead, render this standalone card or
contribute its row to the coordinator's consolidated card:

> **JAIPilot impact**
> - **Java cleanup:** `<baseline> → <final> (<delta>)` and the concrete result
> - **Proof:** fresh configured verification and outcome

Use at most three evidence rows. Prefer scoped production lines, classes, methods, dependencies,
configured complexity, latency or allocation, query or operation counts, and accepted version
changes. Lines changed are not automatically value; distinguish removed proven waste from added
tests or compatibility code.

Compare only the same scope, command, configuration, JDK, and workload. Use percentage points for
coverage or rates. Never claim `versus no JAIPilot`, time saved, risk removed, or percentage gain
without a matched control. When no safe edit is retained, state the concrete contract or behavior
protected and say `numeric delta not measured`. Do not upload or persist impact telemetry.
