# Spring Framework Petclinic evaluation

This evaluation exercises JAIPilot's complete test-generation and Java-cleanup journeys against a
real Java repository. It is evidence for these exact revision and environment boundaries, not a
claim about every project or machine.

## Boundaries

| Item | Value |
| --- | --- |
| Date | 2026-08-04 |
| JAIPilot | v3.0.0 bundled macOS x64 runner |
| Project | `skrcode/spring-framework-petclinic` |
| Project revision | `ef714bf289f5ee323487f34f15b153e7da5f980a` |
| Host | macOS x64 |
| Java | Temurin 25.0.3 |
| Build | Project Maven wrapper; warm dependency cache |
| Coverage | Project-configured JaCoCo 0.8.14 |
| Isolation | Clean detached Git worktrees; existing user checkout untouched |

## Baseline

```bash
./mvnw -B clean verify
```

- 75 tests, zero failures, zero errors, zero skipped.
- 85.69% aggregate line coverage.
- 20.28 seconds wall time.
- 43 production classes discovered by JAIPilot.

## Unit-test generation

Target: `org.springframework.samples.petclinic.model.Owner` with an 80% minimum line-coverage gate.

```bash
jaipilot prepare-tests \
  --project <clean-worktree> \
  --mode classes \
  --class org.springframework.samples.petclinic.model.Owner \
  --minimum-line-coverage 80
```

Prepare completed in 18.02 seconds, including a 17.21-second clean baseline. JAIPilot reported
`Owner` at 55% line and 30% branch coverage and returned one isolated target plus its likely tests.

Five focused tests were added to the existing `OwnerTests` for contact properties, bidirectional
pet association, case-insensitive lookup, persisted-versus-new filtering, missing lookup, and the
diagnostic string representation.

The first full validation reached 72.5% line and 100% branch coverage. JAIPilot passed the build
and scope checks but correctly returned `readyToApply: false` because the 80% target was unmet.
After the final useful assertion, validation reported:

- 95% target line coverage;
- 100% target branch coverage;
- no missing execution reports;
- one changed test file;
- `readyToApply: true`.

Transactional apply changed exactly `OwnerTests.java` in the disposable worktree.

## Java cleanup

Target: `org.springframework.samples.petclinic.util.CallMonitoringAspect`, initially at 0% line and
branch coverage.

```bash
jaipilot prepare-cleanup \
  --project <second-clean-worktree> \
  --mode classes \
  --class org.springframework.samples.petclinic.util.CallMonitoringAspect
```

Prepare completed in 28.90 seconds: 15.76 seconds for the clean baseline and 12.43 seconds for
OpenRewrite. The exact-scoped recipe pass changed only the selected class by removing redundant
primitive zero initializers and adding braces to its `if/else`. Agent review retained those fixes,
removed an unnecessary `else` after `return`, and added four deterministic tests covering enabled,
disabled, exceptional, and reset behavior.

Validation reported `readyToApply: true`, no warnings, and exactly two changed paths: the selected
production class and its related test. The fresh reports contained:

- 79 suite tests, zero failures or errors;
- 100% line coverage for `CallMonitoringAspect` (22/22 lines);
- 100% branch coverage for `CallMonitoringAspect` (4/4 branches).

Transactional apply changed exactly those two paths in the disposable worktree.

## Safety and recovery

After successful cleanup validation, a one-line test-file edit intentionally changed the candidate.
Apply was rejected in 0.48 seconds with the exact drifted path. Removing the probe and revalidating
restored `readyToApply: true`; apply then succeeded.

Both disposable worktrees and their local run state were removed after the audit. The original
Petclinic checkout and JAIPilot repository were unchanged.

## Product gaps observed

The core workflows, coverage gate, build verification, scope controls, drift rejection, recovery,
and transactional apply behaved correctly. Two reporting gaps remain:

1. Cleanup validation returned an empty JSON `coverage` object even though its fresh JaCoCo report
   showed 100% target line and branch coverage.
2. Validation proves changed-test execution from XML reports but does not expose the executed-test
   counts in its JSON result; the counts above were read from the generated reports.

These are evidence-presentation gaps, not silent validation bypasses. They should be addressed
before using cleanup coverage or test counts as first-class automation outputs.
