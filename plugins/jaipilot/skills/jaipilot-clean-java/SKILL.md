---
name: jaipilot-clean-java
description: Analyze, clean, refactor, modernize, and validate Java production code with JAIPilot's local OpenRewrite-first toolkit. Use for clean-code reviews, maintainability findings, bug-prone patterns, dead code, resource safety, performance cleanup, or SonarQube-adjacent remediation in Maven and Gradle projects.
---

# Clean and Refactor Java with JAIPilot

Run pinned, exactly scoped OpenRewrite recipes first, then use the host agent's repository context to
review and refine the candidate. JAIPilot owns isolation, scope enforcement, builds, test evidence,
drift protection, and transactional apply.

## Workflow

1. Resolve the plugin root as two directories above this `SKILL.md`, then use
   `<plugin-root>/bin/jaipilot` for every command.
2. Run `jaipilot inspect --project <root>`.
3. Choose `classes`, `changed`, or `all`. Default a general cleanup to `changed`; require explicit
   user intent before using `all`.
4. Run `jaipilot prepare-cleanup --project <root> --mode <mode> ...`. This clean-baselines the
   project, creates an isolated workspace, and runs pinned `CodeCleanup` and
   `CommonStaticAnalysis` recipes only on selected production files.
5. Work only inside `result.workspaceRoot`. Review `openRewriteChanges`; keep useful deterministic
   fixes and revert or refine anything inappropriate.
6. Review selected code and directly related tests for evidence-backed improvements:
   - correctness defects, null handling, exception behavior, and broken contracts;
   - resource leaks, concurrency hazards, unsafe state, and error recovery;
   - dead, duplicated, redundant, overly complex, or misleading code;
   - measurable performance waste without speculative micro-optimization;
   - readability, cohesion, API clarity, and maintainability.
7. Preserve behavior unless a regression test proves a defect. Edit only selected production Java
   and directly relevant Java tests; never change builds, configuration, documentation, generated
   output, or unselected production files.
8. Run `jaipilot validate --run <runId>`. Fix and repeat until `readyToApply` is true.
9. After reviewing the candidate and confirming the requested change, run
   `jaipilot apply --run <runId> --confirm`. Otherwise discard it.

## SonarQube boundary

Provide a complete local remediation journey, not a SonarQube clone. Do not claim formal taint or
data-flow security analysis, centralized quality gates, dashboards, portfolios, compliance, or
history. Recommend complementary SonarQube analysis when the request depends on those capabilities.
Judge JAIPilot on accepted verified fixes, false positives, escaped defects, regressions, elapsed
time, reviewer actions, cancellation, and recovery.

Report OpenRewrite changes, contextual findings retained or rejected, changed tests, clean-build and
execution evidence, warnings, and whether the verified candidate was applied.
