# JAIPilot and static analysis

JAIPilot supplies deterministic local evidence to a coding agent. Static-analysis platforms solve a
different and complementary governance problem.

| Capability | JAIPilot | SonarQube | OpenRewrite alone | Host coding agent |
| --- | --- | --- | --- | --- |
| Primary job | Exact-scope local Java proof | Analysis, security, and governance | Deterministic transforms | Reasoning and edits |
| Test creation | Evidence for agent-written tests | Not an end-to-end authoring flow | No | Yes |
| Cleanup | Pinned recipes plus proof | Rule-dependent fixes | Core strength | Contextual judgment |
| Real clean build | Required for proof | External CI/build concern | User-integrated | Host-dependent |
| Changed-test execution | Fresh XML required | Imports measures | No | Host-dependent |
| Changed-line coverage/PIT | Fresh, local, exact-diff gates | Coverage import; mutation external | No | Must be assembled |
| Architecture | Pinned ArchUnit changed-cycle gate | Separate rule/integration choices | Recipe-dependent | Host-dependent |
| Fingerprinted proof receipt | Built in | Not a local edit boundary | No | Host-dependent |
| Formal taint/hotspot analysis | Not a substitute | Stronger | Recipe-dependent | Not formal analysis |
| Governance/portfolio/compliance/history | Not a substitute | Stronger | No | No |
| Planning, edits, Git, cancellation | Delegated to host | Not its role | Transform only | Core strength |
| Local/private operation | Local; no JAIPilot backend | Self-hosted or cloud | Local | Provider-dependent |

Use SonarQube or equivalent alongside JAIPilot when formal security/data-flow analysis, central policy,
compliance, portfolios, or organization-wide history matter.

Comparisons should use identical revisions and boundaries and measure accepted verified fixes, false
positives, escaped defects, regressions, generated-test quality, coverage gain, elapsed time, reviewer
actions, cancellation, and failure recovery. JAIPilot does not claim universal superiority without
reproducible evidence.
