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
   and elements, encounter order, locale or normalization, mutable inputs, and missing values. When
   a candidate replaces a public accessor or derived view with its backing collection, include a
   baseline case that makes the skipped copy, sort, validation, and exception timing observable.
   Put a malformed later element after an earlier match to detect unsafe short-circuiting.
5. Inspect JaCoCo or PIT declarations, executions, profiles, and lifecycle bindings. A report goal
   bound to `package` or `verify` is configured even when `test` emits no report. Only after this
   inspection may configured evidence be called unavailable; never use stale reports.
6. Run the existing focused tests before editing tests. Record existing failures without weakening
   tests, build gates, dependencies, exclusions, suppressions, or timeouts.

## Parallelize the complete scoped class queue

Apply this plan to every request that contains multiple test classes, not only an explicit coverage
campaign.

1. Enumerate every production class and corresponding test class in the user's exact scope. Do not
   stop after convenient classes or silently widen a bounded request to the whole repository. Every
   scoped class must receive useful tests or a reported reason why no test was added.
2. Treat test classes as independent only after checking shared fixtures, mutable static or
   singleton state, system properties, ports, files, databases, external services, framework
   contexts, and ordered-test constraints. Queue every safely independent class for parallel work;
   serialize an unsafe group and report the shared constraint instead of assuming independence.
3. Choose a bounded worker count from available agent slots, CPU, memory, and repository services.
   Assign one production class and normally one corresponding test class per creation worker. Keep
   overlapping fixture or production edits with one owner.
4. Give concurrent editing or build workers isolated worktrees and output directories. If dirty work
   cannot be reproduced safely, parallelize read-only analysis, then serialize edits and execution.
   Never run concurrent Maven or Gradle processes against one checkout and output tree.
5. After integrating the patches, execute every safely independent changed test class in parallel.
   Prefer repository-configured JUnit, TestNG, Surefire, Failsafe, Gradle, or module-level native
   parallelism. Otherwise use isolated checkouts and outputs for targeted class commands when the
   setup cost is justified. Do not silently change build configuration, inject unsupported forks,
   or weaken test semantics merely to claim parallel execution.
6. If a test fails only in parallel, rerun that exact class or unsafe group serially once. Preserve
   a failure that also occurs serially; if serial passes, keep that group serialized and report the
   shared-state or ordering risk. If workers or a safe parallel mechanism are unavailable, execute
   sequentially and state that limitation explicitly.

## Add tests

1. Follow the repository's framework, assertion, fixture, mocking, and naming conventions.
2. Prefer public behavior, real values, and stable collaboration boundaries over private-method
   assertions, implementation mirroring, reflection, or excessive mocking.
3. Avoid sleeps, real network calls, order dependence, shared mutable state, unfixed randomness,
   and assertions that pass without reaching the intended branch.
4. Keep setup concise. Reuse a small fixture helper or parameterized cases when that removes
   repetition without hiding the contract. Do not add redundant arrange/act/assert comments.
5. Use the parallel class plan above with the narrowest repository-native commands that execute all
   changed test classes. Confirm from runner output or fresh reports that every intended test method
   executed; task submission alone is not execution evidence.
6. Use configured PIT when practical. Strengthen meaningful survivors; do not assert incidental
   implementation details merely to kill mutations.
7. Change production code only with user approval for a necessary testability change or defect fix.
8. Use configured ArchUnit when the requested proof is a stable architecture invariant. If adding
   ArchUnit would provide durable value, follow the `jaipilot-clean-java` tool procedure and obtain
   approval before changing build dependencies or configuration.

## Run a coverage campaign when requested

1. Use the explicit scope; otherwise prefer changed classes and then their module.
2. Run fresh configured JaCoCo evidence. For every eligible class, calculate covered lines divided
   by covered plus missed lines. Honor only existing generated-code or coverage exclusions and list
   zero-executable-line classes separately.
3. Treat 80% fresh line coverage per eligible class as a default objective, not a guarantee or
   permission to add hollow tests. When JaCoCo is absent, continue with useful tests but ask before
   adding tooling and do not claim a percentage.
4. Queue every scoped class by missed lines, risk, and class name, then process the complete queue
   with the parallel class plan above.
5. Each worker returns its test patch, exact command, executed tests, fresh class coverage, mutation
   evidence, and blockers. Do not commit or modify files outside the assignment unless asked.

## Integrate and verify

1. Integrate isolated patches one at a time. Resolve overlapping fixtures centrally and remove
   duplicated setup, contradictory tests, debug output, and unrelated edits.
2. Run every safely independent changed test class through the parallel plan, then refresh one
   aggregate configured report. Schedule another targeted parallel wave only where useful cases
   remain uncovered.
3. For each class below the target, report the measured value and reason rather than labeling best
   effort as passing.
4. Run related tests and the normal final clean verification. Re-read the complete diff and confirm
   intended tests executed.
5. Use `jaipilot-fast-execution` for substantial command work whenever safe batching or bounded
   native parallelism can reduce wall time without changing the required proof.
6. Default focused and aggregate test execution, coverage, mutation analysis, and final clean
   verification to the `jaipilot-remote-java` skill whenever the laptop provides no concrete
   advantage under that skill's routing rules. Remote proof covers only the uploaded tracked and
   unignored working tree; upload the latest state again after changing local tests or production
   files.

## Report

Announce a completed result only as
`**JAIPilot · Test generation** — <outcome>; <proof>.` in progress or as the final outcome lead. Then
render this exact flat section; do not nest bullets:
**JAIPilot impact**
- **Test generation:** <outcome>
- **Evidence:** <strongest proof>
Apply [impact-reporting.md](references/impact-reporting.md), then report scope, measures, commands/results, test/coverage/mutation evidence, workers, nesting, limitations, and exclusions.
