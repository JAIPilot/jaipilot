---
name: jaipilot-generate-tests
description: Add or strengthen meaningful Java tests and raise fresh per-class coverage through bounded parallel work. Use for JUnit or TestNG gaps, regression tests, boundary cases, surviving mutations, missing assertions, or an explicit JaCoCo coverage campaign.
---

# Add meaningful Java tests

Tests should exercise useful contracts, not inflate a score. Use the user's requested class,
package, module, behavior, or coverage scope; do not silently expand a bounded request into a
repository-wide campaign.

## Establish the scope

1. Confirm the selected root is a Java Maven or Gradle repository. Otherwise report that this skill
   is not applicable and stop.
2. Read repository instructions, build files, existing tests, fixtures, production contracts,
   source sets, profiles, and test conventions. Record Git status and preserve unrelated work.
3. Identify the production classes and useful cases in scope: normal inputs, nulls, boundaries,
   errors, ordering, identity, state transitions, transactions, serialization, security,
   concurrency, and framework lifecycle where relevant.
4. For collection transformations, assess empty input, duplicates and multiplicity, null containers
   and elements, encounter order, locale or normalization, mutable inputs, and missing values.
5. Inspect JaCoCo or PIT declarations, executions, profiles, and lifecycle bindings. A report goal
   bound to `package` or `verify` is configured even when `test` emits no report. Only after this
   inspection may configured evidence be called unavailable; never use stale reports.
6. Run the existing focused tests before editing tests. Record existing failures without weakening
   tests, build gates, dependencies, exclusions, suppressions, or timeouts.

## Add tests

1. Follow the repository's framework, assertion, fixture, mocking, and naming conventions.
2. Prefer public behavior, real values, and stable collaboration boundaries over private-method
   assertions, implementation mirroring, reflection, or excessive mocking.
3. Avoid sleeps, real network calls, order dependence, shared mutable state, unfixed randomness,
   and assertions that pass without reaching the intended branch.
4. Keep setup concise. Reuse a small fixture helper or parameterized cases when that removes
   repetition without hiding the contract. Do not add redundant arrange/act/assert comments.
5. Run the narrowest repository-native command that executes each changed test class. Confirm from
   runner output or fresh reports that every intended test method executed.
6. Use configured PIT when practical. Strengthen meaningful survivors; do not assert incidental
   implementation details merely to kill mutations.
7. Change production code only with user approval for a necessary testability change or defect fix.

## Run a coverage campaign when requested

1. Use the explicit scope; otherwise prefer changed classes and then their module.
2. Run fresh configured JaCoCo evidence. For every eligible class, calculate covered lines divided
   by covered plus missed lines. Honor only existing generated-code or coverage exclusions and list
   zero-executable-line classes separately.
3. Treat 80% fresh line coverage per eligible class as a default objective, not a guarantee or
   permission to add hollow tests. When JaCoCo is absent, continue with useful tests but ask before
   adding tooling and do not claim a percentage.
4. Queue classes by missed lines, risk, and class name. Assign one production class per worker and
   normally one corresponding test class.
5. When the host supports workers, size a bounded batch for available agent slots, CPU, memory, and
   repository services. Give editing/build workers isolated worktrees or output directories. Never
   run concurrent Maven or Gradle builds against one checkout and output tree.
6. If dirty work cannot be reproduced safely, parallelize read-only analysis and serialize edits
   and builds. If workers are unavailable, process the same queue sequentially and report it.
7. Each worker returns its test patch, exact command, executed tests, fresh class coverage, mutation
   evidence, and blockers. Do not commit or modify files outside the assignment unless asked.

## Integrate and verify

1. Integrate isolated patches one at a time. Resolve overlapping fixtures centrally and remove
   duplicated setup, contradictory tests, debug output, and unrelated edits.
2. Run focused tests together, refresh one aggregate configured report, and schedule another
   targeted wave only where useful cases remain uncovered.
3. For each class below the target, report the measured value and reason rather than labeling best
   effort as passing.
4. Run related tests and the normal final clean verification. Re-read the complete diff and confirm
   intended tests executed.
5. Use the `jaipilot-remote-java` skill only for a long aggregate command whose exact candidate is
   already a GitHub-available commit; remote `HEAD` does not prove dirty local test files.

## Report

Return scope, initial test result, tests added, exact focused and final commands, executed tests,
fresh coverage or mutation evidence, worker count and isolation, per-class results when applicable,
exclusions, unavailable evidence, and remaining limitations. Never report unconfigured, stale, or
missing evidence as a pass.
