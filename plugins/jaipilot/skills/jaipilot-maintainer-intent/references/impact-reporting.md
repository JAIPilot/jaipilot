# Show maintainer-intent impact

Make a valuable decision visible even when the correct result is no code or pull request.

## Milestone update

After the preflight reaches its final decision and the host supports progress commentary, emit at
most one line in exactly this shape:

`**JAIPilot · Maintainer intent** — <completed decision>; <strongest fresh proof>.`

This is the only format for announcing a completed milestone. Replace unbranded completion prose
such as `Preflight complete` or `Decision reached` with this line. Keep the bold label, middle dot,
em dash, and evidence order unchanged. Use completed evidence, not intent or routing. When
completion coincides with the final response, use this line as its outcome lead immediately before
the impact card. If this skill is nested, pass the decision, avoided mistake or justified path,
proof, and limitations to the coordinating JAIPilot skill instead of rendering another card.

## Final impact card

Immediately after the final response's one-sentence outcome lead, render this standalone card or
contribute its row to the coordinator's consolidated card:

The card is not a closing appendix. Put no scope, table, or supporting detail between the lead and
card, and preserve the exact flat heading and bullet structure below rather than restyling it. Do not
nest bullets.

**JAIPilot impact**
- **Maintainer intent:** decision and concrete contribution mistake avoided or path justified
- **Evidence:** decisive maintainer, duplicate, policy, or history evidence

Use exactly these two flat rows. Prefer the exact decision, duplicates or competing changes found,
required delivery target, and maintainer constraint uncovered. A supported `NO_ACTION`,
`JOIN_EXISTING`, or `WAIT` should identify the duplicate work, wrong branch, premature implementation,
or policy conflict the evidence prevented. `PROCEED` should identify the uncertainty resolved and
the proof and delivery path now justified.

Do not assign invented hours, lines, acceptance probability, or risk reduction. Use `numeric delta
not applicable` when the benefit is decisional rather than quantitative. Do not upload or persist
impact telemetry.
