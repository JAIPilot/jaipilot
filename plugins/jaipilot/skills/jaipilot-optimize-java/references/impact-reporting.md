# Consolidate JAIPilot impact

Answer the user's final question: what outcome did JAIPilot materially help achieve in this run?

## Milestone update

After the overall workflow reaches a meaningful terminal milestone and the host supports progress
commentary, emit at most one line in exactly this shape:

`**JAIPilot · Optimization** — <completed outcome>; <strongest fresh proof>.`

This is the only format for announcing a completed milestone. Replace unbranded completion prose
such as `Optimization complete` or `Tests passed` with this line. Keep the bold label, middle dot,
em dash, and evidence order unchanged. Use completed evidence, not intent or routing. When
completion coincides with the final response, use this line as its outcome lead immediately before
the impact card.
Component skills use the same grammar with their fixed capability label; do not restyle or duplicate
their updates. As coordinator, retain each component's outcome, comparable baseline and final
values, proof, and unmeasured boundary.

## Final impact card

Immediately after the final response's one-sentence outcome lead, render one consolidated card:

The card is not a closing appendix. Put no scope, table, or supporting detail between the lead and
card, and preserve the exact flat heading and bullet structure below rather than restyling it. Do not
nest bullets.

**JAIPilot impact**
- **Optimization:** strongest component outcomes and measured deltas, naming each material skill
- **Evidence:** fresh configured checks and result

Use exactly these two flat rows. Include one concise phrase for each skill that materially shaped
the result; merely loading a skill does not earn attribution. Use friendly labels rather than
internal routing detail. Lead with the user-visible outcomes, then comparable
`baseline → final (delta)` evidence. Use percentage points for coverage or rates.

Include rejected or already-satisfied work only when it prevented concrete churn or risk. Never
claim `versus no JAIPilot`, time saved, quality gained, or risk removed without a matched control on
the same revisions, prompt, model, tools, budget, and acceptance tests. For decisional value, use
`numeric delta not applicable`; for unavailable comparison, use `not measured`. Do not add tooling
for the card. Do not upload or persist impact telemetry.
