---
name: jaipilot-generate-tests
description: Add or strengthen meaningful Java tests to lock behavior before a risky change or raise fresh per-class coverage through bounded parallel work. Use for characterization or regression tests, JUnit or TestNG gaps, boundary cases, surviving mutations, behavior-preserving refactors, or an explicit JaCoCo coverage campaign.
---

# Prove Java behavior with meaningful tests

Tests constrain observable behavior; they do not exist to inflate a score. Choose behavior-lock mode
for a refactor, cleanup, optimization, or upgrade. Choose coverage-campaign mode only when the user
asks to improve a bounded class, package, module, or repository metric.

## Establish the boundary

1. Confirm the selected root is a Java Maven or Gradle repository. Otherwise report that this skill
   is not applicable and stop.
2. Read repository instructions, build files, existing tests, fixtures, production contracts,
   source sets, profiles, and test conventions. Record Git status and preserve unrelated work.
3. Define the affected production classes and observable behavior: normal cases, nulls, boundaries,
   errors, ordering, identity, state transitions, transactions, serialization, security,
   concurrency, and framework lifecycle where relevant.
4. For collection transformations, explicitly assess empty input, duplicates and multiplicity,
   null containers and elements, encounter order, locale or normalization, mutable inputs, and
   missing values. Test every material contract instead of assuming two library operations match.
5. Inspect JaCoCo or PIT declarations, executions, profiles, and lifecycle bindings. A report goal
   bound to `package` or `verify` is configured even when `test` emits no report. Only after this
   inspection may configured evidence be called unavailable; never use stale reports.

## Behavior-lock mode

1. Before production edits, choose and run one focused repository-native command against the saved
   original implementation. Record its exact command and executed tests.
2. If material behavior is uncovered, add focused characterization tests while production remains
   unchanged. Prove those tests pass against the original production state in an isolated worktree
   or copy when safe. Never reset, stash, clean, or omit user work to construct the baseline.
3. Keep functional assertions identical across baseline and candidate. A performance invariant may
   distinguish operation counts, but this workflow must not silently redefine behavior or fix an
   unrelated defect.
4. Prefer public behavior, real values, and stable collaboration boundaries over private-method
   assertions, implementation mirroring, reflection, or excessive mocking.
5. Avoid sleeps, real network calls, order dependence, shared mutable state, unfixed randomness,
   and assertions that pass without reaching the intended branch.
6. After the production change, run the exact same focused command and confirm every added test
   method executed. Record the two outcomes as behavior baseline and candidate.

## Coverage-campaign mode

1. Use the user's explicit scope; otherwise prefer changed classes and then their module. Do not
   silently expand a bounded request into a repository-wide campaign.
2. Run baseline tests and fresh configured JaCoCo evidence. For every eligible class, calculate
   covered lines divided by covered plus missed lines. Honor only existing generated-code or
   coverage exclusions and list zero-executable-line classes separately.
3. Treat 80% fresh line coverage per eligible class as a default objective, not a guarantee or
   permission to add hollow tests. When JaCoCo is absent, continue with useful behavior tests but
   ask before adding tooling and do not claim a percentage.
4. Queue classes by missed lines, behavioral risk, and class name. Assign one production class per
   worker and normally one corresponding test class.
5. When the host supports workers, size a bounded batch for available agent slots, CPU, memory, and
   repository services. Give editing/build workers isolated worktrees or output directories. Never
   run concurrent Maven or Gradle builds against one checkout and output tree.
6. If dirty work cannot be reproduced safely, parallelize read-only analysis and serialize edits
   and builds. If workers are unavailable, process the same queue sequentially and report it.
7. Each worker must read the class contract and callers, add observable normal, boundary, invalid,
   state, and failure tests, follow repository conventions, run the narrowest applicable test
   command, confirm execution, refresh class coverage, and return its patch and exact evidence.
8. Use configured PIT when practical. Strengthen meaningful survivors; do not assert incidental
   implementation detail merely to kill mutations.
9. Change production code only with user approval for a necessary testability or defect fix. Never
   add dependencies, plugins, exclusions, suppressions, or weaker gates merely to reach a target.

## Integrate and prove

1. Integrate isolated patches one at a time. Resolve overlapping fixtures centrally and remove
   duplicated setup, redundant comments, contradictory tests, debug output, and unrelated edits.
2. Run focused tests together, refresh one aggregate configured report, and schedule another
   targeted wave only where meaningful behavior remains uncovered.
3. For each class below the target, report the measured value and reason rather than labeling best
   effort as passing.
4. Run related tests and the normal final clean verification. Re-read the complete diff and confirm
   intended tests executed.
5. Use the `jaipilot-remote-java` skill only for a long aggregate command whose exact candidate is already a
   GitHub-available commit; remote `HEAD` does not prove dirty local test files.

## Report

Return mode, scope, original state, baseline command, tests added, exact focused and final commands,
executed tests, fresh coverage or mutation evidence, worker count and isolation, per-class results
when applicable, exclusions, unavailable evidence, and remaining behavioral risk. Never report
unconfigured, stale, or missing evidence as a pass.
