# Show diff-review impact

Make corrections and assurance boundaries visible without inventing defects prevented.

## Milestone update

After review reaches a meaningful terminal conclusion and the host supports progress commentary,
emit at most one line in exactly this shape:

`**JAIPilot · Diff review** — <completed outcome>; <strongest fresh proof>.`

This is the only format for announcing a completed milestone. Replace unbranded completion prose
such as `Review complete` or `Tests passed` with this line. Keep the bold label, middle dot, em dash,
and evidence order unchanged. Use completed evidence, not intent or routing. When completion
coincides with the final response, use this line as its outcome lead immediately before the impact
card. If this skill is nested, pass its findings, corrections, reviewed scope, proof, and limitations
to the coordinating JAIPilot skill instead of rendering another card.

## Final impact card

Immediately after the final response's one-sentence outcome lead, render this standalone card or
contribute its row to the coordinator's consolidated card:

The card is not a closing appendix. Put no scope, table, or supporting detail between the lead and
card, and preserve the exact flat heading and bullet structure below rather than restyling it. Do not
nest bullets.

**JAIPilot impact**
- **Diff review:** actionable findings and corrections, or the assurance boundary established
- **Evidence:** reviewed scope and fresh configured checks

Use exactly these two flat rows. Prefer actionable findings by severity, regressions corrected,
unnecessary production lines or dependencies removed, missing tests added, and checks actually
executed. A clean review may headline the complete scope reviewed and verification passed, but must
not call the diff universally correct.

Do not invent defects prevented, acceptance probability, risk percentage, or time saved. When the
review is read-only or no finding is actionable, state the concrete assurance boundary and use
`numeric delta not applicable`. Do not upload or persist impact telemetry.
