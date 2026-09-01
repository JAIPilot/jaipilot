# Show test-generation impact

Make the protected behavior and fresh test evidence visible, not just the size of the test diff.

## Milestone update

After a meaningful test wave reaches a terminal milestone and the host supports progress
commentary, emit at most one line in exactly this shape:

`**JAIPilot · Test generation** — <completed outcome>; <strongest fresh proof>.`

This is the only format for announcing a completed milestone. Replace unbranded completion prose
such as `All tests passed` or `Coverage generated` with this line. Keep the bold label, middle dot,
em dash, and evidence order unchanged. Use completed evidence, not intent or routing. When
completion coincides with the final response, use this line as its outcome lead immediately before
the impact card. If this skill is nested, pass its outcome, comparable measurements, proof, and
unmeasured boundary to the coordinating JAIPilot skill instead of rendering another card.

## Final impact card

Immediately after the final response's one-sentence outcome lead, render this standalone card or
contribute its row to the coordinator's consolidated card:

The card is not a closing appendix. Put no scope, table, or supporting detail between the lead and
card, and preserve the exact flat heading and bullet structure below rather than restyling it. Do not
nest bullets.

**JAIPilot impact**
- **Test generation:** useful tests and `<baseline> → <final> (<delta>)`
- **Evidence:** fresh coverage, mutation, or executed-test result

Use exactly these two flat rows. Prefer meaningful cases or test methods added, executed tests
`baseline → final`, per-class or changed-scope line and branch coverage, mutation score, and killed
survivors. Express coverage and mutation deltas in percentage points, not percent, unless a relative
percentage is explicitly calculated and labeled. Spell out percentage points; never abbreviate them
as `pp`.

Compare only the same classes, tool, exclusions, profile, and JDK. Never claim repository-wide
coverage from a narrower report or `versus no JAIPilot` without a matched control. If fresh
comparable coverage is unavailable, headline the behavior protected and tests executed, then say
`coverage delta not measured`. Do not add tooling solely for this card. Do not upload or persist
impact telemetry.
