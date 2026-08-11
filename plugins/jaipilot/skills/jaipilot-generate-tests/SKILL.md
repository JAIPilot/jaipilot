---
name: jaipilot-generate-tests
description: Generate, repair, and verify meaningful Java tests using the repository's existing framework and build. Use for JUnit or TestNG tests, regression tests, coverage gaps, surviving mutations, boundary cases, or requests to improve Java test quality.
---

# Generate meaningful Java tests

Use tests to describe observable behavior, not to reward a coverage number.

## Understand the target

1. Confirm that the selected root contains a Java Maven or Gradle project. Otherwise report that
   this skill is not applicable and stop.
2. Read repository instructions, build files, existing tests, fixtures, and the production contract.
3. Record the current Git status and preserve unrelated work.
4. Identify the exact class, behavior, defect, or changed lines the user wants protected.
5. Use the repository's existing test framework, style, assertion library, mocking approach, and
   naming conventions. Do not add dependencies or reconfigure the build without approval.

## Design the tests

1. Cover observable normal, boundary, invalid-input, state-transition, and failure behavior that is
   relevant to the request.
2. Add a regression test that fails for the demonstrated defect before changing established
   production behavior.
3. Prefer public contracts and stable collaboration boundaries over private-method or
   implementation-detail assertions.
4. Avoid sleeps, real network calls, order dependence, shared mutable state, random outcomes without
   fixed seeds, and assertions that merely repeat the implementation.
5. Keep fixtures small. Reuse existing builders and helpers when they remain clear.
6. Change production code only when the user permits a necessary testability or defect fix. Keep
   that change minimal and behavior-preserving unless the regression test proves otherwise.

## Execute and strengthen

1. Run the narrowest repository-native command that executes the new tests. Confirm from test output
   or reports that the intended class and methods ran.
2. Run related tests after the focused test passes.
3. Use the repository's configured JaCoCo report to inspect relevant line and branch coverage.
   Treat missing or stale reports as unavailable.
4. Use configured PIT mutation testing when it is practical for the selected class. Strengthen tests
   against meaningful survivors; do not assert implementation trivia merely to kill a mutation.
5. Run the repository's normal final verification command.
6. Re-read the final diff and remove hollow tests, duplication, unused fixtures, debug output, and
   unrelated formatting.

## Report

Return:

- target behavior and tests added or changed;
- production changes, if any, and their justification;
- exact test and build commands with pass, fail, skipped, or unavailable status;
- executed test classes and methods when available;
- coverage and mutation evidence only when freshly measured;
- important cases intentionally not covered and why; and
- remaining limitations.

Never describe unconfigured coverage or mutation tooling as a pass.
