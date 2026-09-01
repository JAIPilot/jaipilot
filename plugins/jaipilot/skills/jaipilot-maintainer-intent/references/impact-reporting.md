# Show maintainer-intent impact

Make a valuable decision visible even when the correct result is no code or pull request.

## During the run

When the preflight reaches its decision and the host supports progress commentary, give at most one
sentence naming the decision and decisive repository evidence. If this skill is nested, pass the
decision, avoided mistake or justified path, proof, and limitations to the coordinating JAIPilot
skill instead of rendering another card.

## Final impact card

Immediately after the final response's one-sentence outcome lead, render this standalone card or
contribute its row to the coordinator's consolidated card:

> **JAIPilot impact**
> - **Maintainer intent:** decision and concrete contribution mistake avoided or path justified
> - **Proof:** decisive maintainer, duplicate, policy, or history evidence

Use at most three evidence rows. Prefer the exact decision, duplicates or competing changes found,
required delivery target, and maintainer constraint uncovered. A supported `NO_ACTION`,
`JOIN_EXISTING`, or `WAIT` should identify the duplicate work, wrong branch, premature implementation,
or policy conflict the evidence prevented. `PROCEED` should identify the uncertainty resolved and
the proof and delivery path now justified.

Do not assign invented hours, lines, acceptance probability, or risk reduction. Use `numeric delta
not applicable` when the benefit is decisional rather than quantitative. Do not upload or persist
impact telemetry.
