---
name: jaipilot-generate-tests
description: Generate, repair, and prove high-quality Java unit tests with JAIPilot's local coverage and PIT evidence. Use for JUnit tests, changed-class tests, coverage gaps, regression tests, surviving mutations, or test improvement in Maven and Gradle repositories.
---

# Generate Java Tests with JAIPilot

The host agent reasons about behavior and owns all edits. JAIPilot supplies deterministic targets,
fresh build evidence, JaCoCo coverage, targeted PIT mutation results, quality, and ArchUnit proof.

## Workflow

1. Run `jaipilot_inspect` for the repository. Do not silently change build configuration when Maven,
   Gradle, JaCoCo, or PIT prerequisites are missing.
2. Select named classes, changed production classes, or classes below a fresh coverage threshold.
   Use whole-project scope only when explicitly requested.
3. Inspect production contracts and existing tests. In the agent-controlled branch or worktree, add
   the smallest tests for observable normal, boundary, invalid-input, failure, and state behavior.
4. Reuse the repository's framework and dependencies. Avoid sleeps, real network calls, order
   dependence, shared mutable state, implementation-detail assertions, and hollow coverage tests.
5. Run focused repository tests while iterating. Run `jaipilot_quality` on touched production code
   when testability requires a production refactor.
6. When the diff is stable, run `jaipilot_prove_diff`. Use changed-line coverage and surviving or
   uncovered PIT mutations to strengthen assertions. Require the clean build and all applicable
   gates to pass; never infer mutation strength from coverage alone.
7. Confirm `jaipilot_diff_gate` reports `passed` for the exact fingerprint before handing off.

Report targets, tests changed and executed, clean-build evidence, changed line/branch coverage,
mutation counts and survivors, quality/architecture evidence, warnings, and concrete unscorable
boundaries. JAIPilot does not create or apply a separate candidate workspace.
