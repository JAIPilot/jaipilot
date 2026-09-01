# Show diff-review impact

Make corrections and assurance boundaries visible without inventing defects prevented.

## During the run

When review reaches a meaningful conclusion and the host supports progress commentary, give at most
one sentence with the highest-severity outcome and proof. If this skill is nested, pass its findings,
corrections, reviewed scope, proof, and limitations to the coordinating JAIPilot skill instead of
rendering another card.

## Final impact card

Immediately after the final response's one-sentence outcome lead, render this standalone card or
contribute its row to the coordinator's consolidated card:

> **JAIPilot impact**
> - **Diff review:** actionable findings and corrections, or the assurance boundary established
> - **Proof:** reviewed scope and fresh configured checks

Use at most three evidence rows. Prefer actionable findings by severity, regressions corrected,
unnecessary production lines or dependencies removed, missing tests added, and checks actually
executed. A clean review may headline the complete scope reviewed and verification passed, but must
not call the diff universally correct.

Do not invent defects prevented, acceptance probability, risk percentage, or time saved. When the
review is read-only or no finding is actionable, state the concrete assurance boundary and use
`numeric delta not applicable`. Do not upload or persist impact telemetry.
