# Show migration impact

Make the retained migration and rejected recipe noise visible without treating generation as value.

## During the run

When a migration stage completes and the host supports progress commentary, give at most one
sentence with the migrated scope and verification outcome. Skip it when it repeats earlier evidence.
If this skill is nested, pass its route, outcome, comparable counts, proof, and limitations to the
coordinating JAIPilot skill instead of rendering another card.

## Final impact card

Immediately after the final response's one-sentence outcome lead, render this standalone card or
contribute its row to the coordinator's consolidated card:

> **JAIPilot impact**
> - **Migration:** migrated scope, rejected noise, and `<preview> → <retained> (<delta>)`
> - **Proof:** repeat dry run and fresh compatibility verification

Use at most three evidence rows. Prefer migrated files and occurrences, previewed edits rejected as
noise, manual compatibility edits, repeat-dry-run remainder, and tests or compatibility gates passed.
All generated lines are not benefit; distinguish requested transformations from formatting churn and
manual repair.

Do not imply recipe execution proves correctness or claim time saved without a matched run. For
`MANUAL` or `NO_ACTION`, identify the unsafe or unnecessary rewrite avoided and use `numeric delta
not applicable` when appropriate. Do not upload or persist impact telemetry.
