# Show test-generation impact

Make the protected behavior and fresh test evidence visible, not just the size of the test diff.

## During the run

When a meaningful test wave completes and the host supports progress commentary, give at most one
sentence with useful tests added and the strongest fresh coverage or mutation delta. Skip it when it
would repeat an earlier result. If this skill is nested, pass its outcome, comparable measurements,
proof, and unmeasured boundary to the coordinating JAIPilot skill instead of rendering another card.

## Final impact card

Immediately after the final response's one-sentence outcome lead, render this standalone card or
contribute its row to the coordinator's consolidated card:

> **JAIPilot impact**
> - **Test generation:** useful tests and `<baseline> → <final> (<delta>)`
> - **Proof:** fresh coverage, mutation, or executed-test result

Use at most three evidence rows. Prefer meaningful cases or test methods added, executed tests
`baseline → final`, per-class or changed-scope line and branch coverage, mutation score, and killed
survivors. Express coverage and mutation deltas in percentage points, not percent, unless a relative
percentage is explicitly calculated and labeled.

Compare only the same classes, tool, exclusions, profile, and JDK. Never claim repository-wide
coverage from a narrower report or `versus no JAIPilot` without a matched control. If fresh
comparable coverage is unavailable, headline the behavior protected and tests executed, then say
`coverage delta not measured`. Do not add tooling solely for this card. Do not upload or persist
impact telemetry.
