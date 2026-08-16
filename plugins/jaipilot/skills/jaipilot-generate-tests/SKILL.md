---
name: jaipilot-generate-tests
description: Generate, repair, and verify meaningful Java unit tests, including parallel per-class coverage campaigns. Use for JUnit or TestNG tests, regression tests, coverage gaps, surviving mutations, boundary cases, or requests to maximize fresh JaCoCo line coverage toward at least 80% for each eligible production class.
---

# Raise meaningful Java coverage per class

Target at least 80% fresh JaCoCo line coverage for every eligible in-scope production class. Treat
80% as an objective, not permission to add hollow tests or claim unmeasured coverage.

## Establish the campaign

1. Confirm that the selected root contains a Java Maven or Gradle project. Otherwise report that
   this skill is not applicable and stop.
2. Read repository instructions, build files, existing tests, fixtures, and the production
   contracts. Record Git status and preserve unrelated work.
3. Define the production classes in scope. Use the user's explicit scope; otherwise prefer changed
   classes, then their module. Do not silently turn a bounded request into a repository-wide job.
4. Run the repository's existing baseline tests and configured JaCoCo report. Calculate each
   eligible class's line coverage as covered lines divided by covered plus missed lines.
5. Include classes with executable production lines. Honor only existing generated-code or coverage
   exclusions, and list excluded or zero-executable-line classes separately.
6. If fresh class-level JaCoCo data is unavailable, ask before adding or changing tooling. Continue
   with behavior-focused tests when useful, but do not claim an 80% result.

## Coordinate one class per worker

1. Build a deterministic queue ordered by missed lines, risk, and class name. Assign exactly one
   production class to each worker and normally one corresponding test class.
2. When the host supports subagents, run a bounded parallel batch sized for available agent slots,
   CPU, memory, and repository services. Keep the remaining classes queued for later waves.
3. Give editing or build workers separate temporary Git worktrees or equivalent isolated build
   outputs. Never run concurrent Maven or Gradle processes against the same checkout or output tree.
4. When relevant uncommitted work cannot be reproduced safely, do not stash, reset, or omit it.
   Parallelize read-only analysis and serialize edits/builds, or obtain approval for isolated copies.
5. Do not assume tests are runtime-independent merely because authoring is split by class. Do not
   enable parallel test execution unless the repository already supports it safely.
6. If the host cannot run subagents, process the same per-class queue sequentially and report that
   parallel authoring was unavailable.

## Worker contract

For the assigned production class:

1. Read its public contract, collaborators, existing tests, and relevant call sites.
2. Cover observable normal, boundary, invalid-input, state-transition, and failure behavior. Add a
   regression test that fails before any permitted defect fix.
3. Follow the repository's existing framework, assertion, mocking, fixture, and naming conventions.
   Prefer real values and stable collaboration boundaries over private-method assertions.
4. Avoid sleeps, real network calls, order dependence, shared mutable state, unfixed randomness,
   excessive mocking, reflection into internals, and assertions that repeat the implementation.
5. Run the narrowest repository-native command that executes the assigned test class. Confirm from
   output or reports that its intended methods ran.
6. Refresh the configured JaCoCo evidence in the isolated checkout and iterate while useful
   behavior remains uncovered. Aim for at least 80% line coverage for the assigned class.
7. Use configured PIT for the assigned class when practical. Strengthen meaningful survivors; do
   not assert implementation trivia merely to kill mutations.
8. Change production code only with user approval for a necessary testability or defect fix. Never
   add dependencies, plugins, exclusions, suppressions, or weaker gates merely to reach the target.
9. Return the test patch, exact commands, executed tests, fresh class coverage, mutation evidence,
   and any blocker. Do not commit or modify files outside the assignment unless asked.

## Integrate and prove

1. Review and integrate worker patches one at a time. Resolve overlapping fixtures centrally and
   remove duplicated or contradictory tests.
2. Run the focused test classes together using the repository's normal runner. Then refresh one
   aggregate JaCoCo report from the integrated tree.
3. Compare every eligible class with the 80% target. Schedule another targeted wave for classes
   below it when meaningful uncovered behavior remains.
4. For a class that remains below 80%, record the exact measured value and reason: unreachable or
   generated paths, environment dependency, unsafe behavior, missing tooling, or diminishing-value
   implementation detail. Never label a best effort as passing.
5. Run related tests and the repository's normal final verification command. Re-read the complete
   diff and remove hollow tests, duplication, unused fixtures, debug output, and unrelated changes.

## Report

Return:

- scope, baseline command, worker count, isolation method, and parallel waves;
- tests and any approved production changes;
- exact focused, aggregate, coverage, mutation, and final commands with outcomes;
- a table of every eligible class with baseline coverage, final coverage, target status, and blocker;
- excluded classes and the repository rule that excluded them;
- important behavior intentionally not covered and why; and
- unavailable evidence and remaining limitations.

Never describe unconfigured, stale, or missing coverage or mutation evidence as a pass.
