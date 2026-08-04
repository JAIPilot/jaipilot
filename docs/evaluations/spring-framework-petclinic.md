# Spring Framework Petclinic evaluation

This evaluation exercises JAIPilot's quality analysis, test generation, mutation testing, cleanup,
validation, and transactional apply against the canonical Spring Framework Petclinic repository.
The results apply to the exact revision and environment below.

## Boundaries

| Item | Value |
| --- | --- |
| Date | 2026-08-05 |
| JAIPilot | v3.1.0 candidate, bundled macOS x64 runner |
| Project | `spring-petclinic/spring-framework-petclinic` |
| Project revision | `f2b1c6df3a89f4d0294c7402b48ea351af2c92ca` |
| Host | macOS x64 |
| Java | Temurin 25.0.3 |
| Build | Project Maven 3.8.4 wrapper |
| Coverage | Project-configured JaCoCo 0.8.15 |
| Isolation | Disposable clones and separate JAIPilot state directories |

The cold `./mvnw -B clean verify` completed in 43.06 seconds including dependency resolution. All 75
tests passed with zero failures, errors, or skips.

## Quality analysis

Five identical full-production-source runs analyzed 43 files, 1,452 code lines, and 172 methods.
Every run returned the same seven findings and 97.9 overall quality score.

| Boundary | Raw seconds | Median | p95 |
| --- | --- | --- | --- |
| Analyzer only | 0.253, 0.243, 0.264, 0.285, 0.259 | 0.259 s | 0.285 s |
| JVM startup, discovery, analysis, JSON | 0.96, 0.82, 0.85, 0.87, 0.84 | 0.85 s | 0.96 s |

The report contained no critical/high findings or bug risks: six code smells, one modernization
opportunity, 0.6% duplication, 66 minutes of estimated debt, maximum cyclomatic complexity 14, and
maximum cognitive complexity 21.

## Unit-test and mutation journey

Target: `org.springframework.samples.petclinic.model.Owner`, with 80% line-coverage and 70% mutation
gates.

Preparation took 19.90 seconds wall time, including an 18.65-second clean baseline. JAIPilot found
55% line and 30% branch coverage, created an isolated candidate, and reported a 100.0 source-quality
score for the selected class.

Five focused tests were added to the existing `OwnerTests` for contact properties, bidirectional pet
association, case-insensitive lookup, persisted-versus-new filtering, missing lookup, and diagnostic
state. Validation produced:

- 95% line and 100% branch coverage;
- 14 PIT mutations, all killed, with 100% mutation score and 100% test strength;
- a 98.8 `EXCELLENT` test-quality score with 100% evidence completeness;
- proof that the one changed test file executed;
- no warnings, failures, source-quality regressions, or missing reports.

Validation took 31.86 seconds internally and 32.69 seconds wall time; targeted PIT accounted for
12.74 seconds. Transactional apply changed exactly `OwnerTests.java` and removed the isolated
workspace and run state.

## Cleanup journey

Target: `org.springframework.samples.petclinic.util.CallMonitoringAspect` in a second clean checkout.

Preparation took 30.08 seconds wall time: 18.21 seconds for the clean baseline and 10.70 seconds for
the pinned, exact-scoped OpenRewrite pass. OpenRewrite changed only the target class by removing
redundant primitive initializers and adding braces. Contextual review then removed the reported
redundant `else` after a terminal branch.

Validation reran the real build and completed in 18.14 seconds internally, reporting:

- overall quality 99.8 → 100.0 and maintainability 99.4 → 100.0;
- code smells 1 → 0 and remediation debt 5 → 0 minutes;
- one resolved finding, zero introduced findings, and zero new critical/high findings;
- no warnings or failures and `readyToApply: true`.

Transactional apply changed exactly `CallMonitoringAspect.java` and removed the isolated workspace.

## Interpretation

This proves the complete local workflow on one real revision; it is not a universal superiority
claim. JAIPilot's analyzer is syntactic and remediation-oriented. It does not replace formal
interprocedural security analysis, centralized governance, compliance controls, portfolios, or
historical dashboards.
