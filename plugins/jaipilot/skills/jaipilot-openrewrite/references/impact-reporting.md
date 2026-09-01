# Show migration impact

Make the retained migration and rejected recipe noise visible without treating generation as value.

## Milestone update

After a migration stage reaches a terminal milestone and the host supports progress commentary,
emit at most one line in exactly this shape:

`**JAIPilot · Migration** — <completed outcome>; <strongest fresh proof>.`

This is the only format for announcing a completed milestone. Replace unbranded completion prose
such as `Migration complete` or `Build succeeded` with this line. Keep the bold label, middle dot,
em dash, and evidence order unchanged. Use completed evidence, not intent or routing. When
completion coincides with the final response, use this line as its outcome lead immediately before
the impact card. If this skill is nested, pass its route, outcome, comparable counts, proof, and
limitations to the coordinating JAIPilot skill instead of rendering another card.

## Final impact card

Immediately after the final response's one-sentence outcome lead, render this standalone card or
contribute its row to the coordinator's consolidated card:

The card is not a closing appendix. Put no scope, table, or supporting detail between the lead and
card, and preserve the exact flat heading and bullet structure below rather than restyling it. Do not
nest bullets.

**JAIPilot impact**
- **Migration:** migrated scope, rejected noise, and `<preview> → <retained> (<delta>)`
- **Evidence:** repeat dry run and fresh compatibility verification

Use exactly these two flat rows. Prefer migrated files and occurrences, previewed edits rejected as
noise, manual compatibility edits, repeat-dry-run remainder, and tests or compatibility gates passed.
All generated lines are not benefit; distinguish requested transformations from formatting churn and
manual repair.

Do not imply recipe execution proves correctness or claim time saved without a matched run. For
`MANUAL` or `NO_ACTION`, identify the unsafe or unnecessary rewrite avoided and use `numeric delta
not applicable` when appropriate. Do not upload or persist impact telemetry.
