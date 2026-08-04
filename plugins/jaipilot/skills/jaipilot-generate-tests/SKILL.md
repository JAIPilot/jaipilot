---
name: jaipilot-generate-tests
description: Generate, repair, and validate efficient high-coverage Java unit tests with JAIPilot's local isolated toolkit. Use for JUnit tests, changed-class tests, coverage-gap tests, regression tests, or safe test improvement in Maven and Gradle projects.
---

# Generate Java Tests with JAIPilot

Let the host agent reason about behavior and write tests. Use JAIPilot for deterministic target
selection, isolation, real-build verification, execution evidence, fresh JaCoCo feedback, drift
protection, and transactional apply.

## Workflow

1. Resolve the plugin root as two directories above this `SKILL.md`, then use
   `<plugin-root>/bin/jaipilot` for every command.
2. Run `jaipilot inspect --project <root>`. Report missing Maven, Gradle, or JaCoCo prerequisites;
   do not silently alter build configuration.
3. Choose a target mode from the request:
   - `classes`: repeat `--class <selector>` for named classes or source paths.
   - `changed`: target changed production classes.
   - `coverage`: select classes below fresh `--coverage-threshold <percent>`.
   - `all`: use only for an explicit whole-project request.
4. Run `jaipilot prepare-tests --project <root> --mode <mode> ...`. Pass the requested
   `--minimum-line-coverage`, or 80 by default.
5. Read the JSON result. Work only inside `result.workspaceRoot`; never edit the live project while
   the run is open.
6. Inspect the targets and existing tests. Add the smallest coherent tests under `src/test/java`.
7. Run `jaipilot validate --run <runId>`. Fix the isolated candidate and repeat until
   `readyToApply` is true or a concrete limitation is established.
8. After reviewing the candidate and confirming the user's requested change, run
   `jaipilot apply --run <runId> --confirm`. Otherwise run `jaipilot discard --run <runId>`.

## Test quality

- Assert observable contracts, not private implementation details.
- Cover normal behavior, boundaries, invalid inputs, failures, state transitions, and meaningful branches.
- Reuse the project's test framework and dependency set.
- Keep tests deterministic, independent, fast, and readable; avoid sleeps, real network calls,
  wall-clock assumptions, order dependence, and shared mutable state.
- Require non-zero execution evidence for every changed test. Coverage alone never justifies hollow assertions.

Report targets, changed tests, clean-build evidence, executed tests, coverage changes, warnings, and
whether the candidate was applied.
