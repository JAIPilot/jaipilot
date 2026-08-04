# Spring Framework Petclinic evaluation

This evaluation exercises JAIPilot's quality analysis, test generation, mutation testing, cleanup,
validation, and transactional apply against the canonical Spring Framework Petclinic repository.
The results apply to the exact revision and environment below.

## Boundaries

| Item | Value |
| --- | --- |
| Date | 2026-08-05 |
| JAIPilot | v3.1.1 candidate, bundled macOS x64 runner |
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
| Analyzer only | 0.261, 0.215, 0.212, 0.215, 0.216 | 0.215 s | 0.261 s |
| JVM startup, discovery, analysis, JSON | 0.77, 0.70, 0.70, 0.70, 0.70 | 0.70 s | 0.77 s |

The report contained no critical/high findings or bug risks: six code smells, one modernization
opportunity, 0.6% duplication, 66 minutes of estimated debt, maximum cyclomatic complexity 14, and
maximum cognitive complexity 21.

## Unit-test and mutation journey

Target: `org.springframework.samples.petclinic.model.Owner`, with 80% line-coverage and 70% mutation
gates.

Five identical preparations took 17.84, 16.23, 15.73, 15.72, and 15.85 seconds wall time (15.85
seconds median, 17.84 seconds p95). Their clean baselines were 16.86, 15.18, 14.70, 14.70, and
14.83 seconds internally (14.83 seconds median, 16.86 seconds p95). JAIPilot found 55% line and 30%
branch coverage, created an isolated candidate, and reported a 100.0 source-quality score for the
selected class on every run.

Five focused tests were added to the existing `OwnerTests` for contact properties, bidirectional pet
association, case-insensitive lookup, persisted-versus-new filtering, missing lookup, and diagnostic
state. Validation produced:

- 95% line and 100% branch coverage;
- 14 PIT mutations, all killed, with 100% mutation score and 100% test strength;
- a 98.8 `EXCELLENT` test-quality score with 100% evidence completeness;
- proof that the one changed test file executed;
- no warnings, failures, source-quality regressions, or missing reports.

The five validations took 28.47, 27.64, 27.44, 27.03, and 27.06 seconds wall time (27.44 seconds
median, 28.47 seconds p95). Internal validation was 26.77 seconds median and 27.77 seconds p95;
targeted PIT was 10.89 seconds median and 11.40 seconds p95. Each candidate was discarded after its
measurement. A final identical candidate was then validated and transactionally applied: exactly
`OwnerTests.java` changed, and the isolated workspace and active run state were removed.

## Cleanup journey

Target: `org.springframework.samples.petclinic.util.CallMonitoringAspect` in a second clean checkout.

Five identical preparations took 25.16, 28.90, 25.31, 25.66, and 25.37 seconds wall time (25.37
seconds median, 28.90 seconds p95). The pinned, exact-scoped OpenRewrite pass took 8.88, 11.69, 8.89,
9.10, and 8.95 seconds (8.95 seconds median, 11.69 seconds p95). It changed only the target class by
removing redundant primitive initializers and adding braces. All five unchanged OpenRewrite
candidates validated with a stable 99.8 quality score and zero introduced findings. A final
candidate received the contextual redundant-`else` cleanup before apply.

The five OpenRewrite-only validations took 16.13, 16.25, 16.82, 16.51, and 16.33 seconds wall time
(16.33 seconds median, 16.82 seconds p95). The final refined candidate reran the real build and
reported:

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
