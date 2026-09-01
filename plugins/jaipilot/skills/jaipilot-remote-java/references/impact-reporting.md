# Show remote-verification impact

Make the offloaded proof visible without turning remote duration into an invented speedup.

## Milestone update

After a remote attempt reaches a terminal state and the host supports progress commentary, emit at
most one line in exactly this shape:

`**JAIPilot · Remote verification** — <completed outcome>; <strongest fresh proof>.`

This is the only format for announcing a completed milestone. Replace unbranded completion prose
such as `Build succeeded` or `Remote run complete` with this line. Keep the bold label, middle dot,
em dash, and evidence order unchanged. Use completed evidence, not intent or routing. When
completion coincides with the final response, use this line as its outcome lead immediately before
the impact card. If this skill is nested, pass its outcome, duration, source boundary, cleanup
result, and limitations to the coordinating JAIPilot skill instead of rendering another card.

## Final impact card

Immediately after the final response's one-sentence outcome lead, render this standalone card or
contribute its row to the coordinator's consolidated card:

The card is not a closing appendix. Put no scope, table, or supporting detail between the lead and
card, and preserve the exact flat heading and bullet structure below rather than restyling it. Do not
nest bullets.

**JAIPilot impact**
- **Remote verification:** exact revision, substantial command, outcome, and duration
- **Evidence:** source digest and cleanup result; speedup measured or unmeasured

Use exactly these two flat rows. Prefer the substantial command offloaded, tests or analyzers
executed, remote wall time, exact-SHA boundary, and verified source cleanup. A speedup requires a
matched baseline with the same revision, JDK, command, workload, caches, and acceptance checks.

Otherwise say `speedup not measured`; never convert remote duration into local time saved or imply
that hardware made the evidence stronger. A deterministic build failure can be a useful diagnosis,
but infrastructure failure is not product impact. Do not upload or persist impact telemetry.
