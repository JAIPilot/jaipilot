# JAIPilot and static analysis

JAIPilot is designed for the complete local remediation transaction: select a useful scope, create
a candidate, prove it with the real build and execution evidence, and apply it without losing
concurrent work. Static-analysis platforms solve a different and complementary problem.

| Capability | JAIPilot | SonarQube | OpenRewrite alone | Direct coding agent |
| --- | --- | --- | --- | --- |
| Primary job | Agent-driven Java tests and verified remediation | Static/security analysis and governance | Deterministic transformations | General reasoning and edits |
| Project-specific test creation | Through Codex or Claude Code | Not an end-to-end workflow | No | Yes |
| Deterministic cleanup | OpenRewrite first, then contextual review | Rule-dependent fixes | Core strength | Model-dependent |
| Clean behavioral proof before apply | Required | Does not own the local source transaction | Build integration is user-defined | Host-dependent |
| Changed-test execution proof | Required | Imports test and coverage measures | Not built in | Host-dependent |
| Fresh coverage-driven targeting | Yes | Central coverage gates and dashboards | No | Must be assembled |
| Isolated candidate and strict write scope | Built in | Does not own local edits | Recipe scope | Host-dependent |
| Drift-safe transactional apply | Built in | Not its role | Not its role | Host-dependent |
| Formal taint analysis and hotspots | Not a substitute | Stronger | Recipe-dependent | Not formal analysis |
| Governance, portfolios, compliance, history | Not a substitute | Stronger | No | No |
| Local/private operation | Local; no JAIPilot backend | Self-hosted or cloud | Local | Depends on host/provider |

Use SonarQube or equivalent analysis alongside JAIPilot when formal security/data-flow analysis,
central policy, compliance reporting, portfolios, or historical dashboards matter.

Any comparative claim should use identical revisions and boundaries and measure accepted verified
fixes, false positives, escaped defects, regressions, generated-test quality, coverage gain,
elapsed time, reviewer actions, cancellation, and failure recovery. JAIPilot does not claim
universal superiority without that reproducible evidence.
