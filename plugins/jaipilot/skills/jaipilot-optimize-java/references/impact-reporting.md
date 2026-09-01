# Consolidate JAIPilot impact

Answer the user's final question: what outcome did JAIPilot materially help achieve in this run?

## During the run

After a component skill reaches a meaningful milestone, give at most one concise progress sentence
when the host supports commentary and the result adds new evidence. As coordinator, retain each
component's outcome, comparable baseline and final values, proof, and unmeasured boundary. Do not
print a branded banner after every nested skill.

## Final impact card

Immediately after the final response's one-sentence outcome lead, render one consolidated card:

> **JAIPilot impact**
> - **Java cleanup:** strongest concrete outcome and measured delta
> - **Test generation:** strongest concrete outcome and measured delta
> - **Diff review:** finding resolved or assurance boundary established
> - **Verification:** fresh configured checks and result

Include one concise row for each skill that materially shaped the result, plus one final proof row;
merely loading a skill does not earn attribution. Use friendly labels rather than internal routing
detail. Lead each row with the user-visible outcome, then a comparable `baseline → final (delta)`.
Use percentage points for coverage or rates.

Include rejected or already-satisfied work only when it prevented concrete churn or risk. Never
claim `versus no JAIPilot`, time saved, quality gained, or risk removed without a matched control on
the same revisions, prompt, model, tools, budget, and acceptance tests. For decisional value, use
`numeric delta not applicable`; for unavailable comparison, use `not measured`. Do not add tooling
for the card. Do not upload or persist impact telemetry.
