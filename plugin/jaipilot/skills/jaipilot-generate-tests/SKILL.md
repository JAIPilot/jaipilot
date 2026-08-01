---
name: jaipilot-generate-tests
description: Generate, repair, and validate Java unit tests through JAIPilot's isolated MCP workflow. Use when a user asks for tests, JUnit coverage, changed-class tests, coverage-gap tests, or safe test improvement in a Maven or Gradle Java project.
---

# Generate Java Tests with JAIPilot

Use the connected agent for reasoning and edits. Use JAIPilot for deterministic target selection, workspace isolation, clean verification, execution evidence, fresh JaCoCo feedback, drift protection, and transactional apply.

## Workflow

1. Call `jaipilot_inspect_project` with the project root. Report missing Maven, Gradle, or JaCoCo prerequisites clearly; do not silently modify build configuration.
2. Choose one target mode from the user's intent:
   - `classes`: pass class names, fully qualified names, or source paths.
   - `changed`: use changed production classes.
   - `coverage`: set `coverageThreshold` to select classes below fresh line coverage.
   - `all`: use only when the user explicitly wants the entire production source set.
3. Call `jaipilot_prepare_tests`. Use `minimumLineCoverage` from the user's request, or 80 by default.
4. Read `agentInstructions`, `targets`, `likelyTests`, and `workspaceRoot` from the result.
5. Work only inside `workspaceRoot`. Use the host's file tools when that path is accessible; otherwise use `jaipilot_read_run_file` and `jaipilot_write_run_file`. Never edit the live project while the run is open.
6. Inspect the target production code and existing tests. Generate or improve the smallest coherent test set under `src/test/java`.
7. Call `jaipilot_validate_run` with the returned `runId`.
8. If validation reports a scope, compilation, test, execution-report, or coverage failure, fix the isolated workspace and validate again. Do not weaken assertions or disable tests merely to pass.
9. Apply with `jaipilot_apply_run` only when `readyToApply` is true and the user authorized the requested code change. Otherwise call `jaipilot_discard_run` before stopping.

## Test Quality

- Assert observable behavior and contracts, not private implementation details.
- Cover normal behavior, boundaries, invalid inputs, failures, state transitions, and meaningful branches.
- Reuse the project's test framework and dependencies. Do not add mocking or assertion libraries without an explicit need.
- Keep tests deterministic, independent, fast, and readable. Avoid sleeps, real network calls, wall-clock assumptions, order dependence, and shared mutable state.
- Prefer focused fixtures and parameterized tests when they reduce repetition without obscuring intent.
- A passing build is insufficient by itself: changed test classes must have non-zero execution evidence.
- Treat fresh JaCoCo data as feedback, not permission to write hollow assertions. If the requested threshold is infeasible, explain the concrete uncovered behavior rather than claiming success.

## Safety and Handoff

- Do not copy sandbox changes manually into the live project. `jaipilot_apply_run` is the only merge path.
- If the live project or candidate drifts, preserve both and re-prepare or revalidate as instructed by the tool error.
- Report target classes, changed tests, clean verification, execution proof, before/after coverage when available, warnings, and whether apply completed.
