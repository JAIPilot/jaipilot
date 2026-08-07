---
name: jaipilot-clean-java
description: Analyze, clean, refactor, modernize, and validate Java with JAIPilot's local OpenRewrite-first quality engine. Use for code smells, bug-prone patterns, complexity, duplication, maintainability debt, resource safety, performance cleanup, or IDE-style Java inspections in Maven and Gradle projects.
---

# Clean and Refactor Java with JAIPilot

Use JAIPilot's deterministic findings and scores to prioritize work. Run pinned, exactly scoped
OpenRewrite recipes first, then use the host agent's repository context to review and refine the
candidate. JAIPilot owns isolation, scope enforcement, builds, ArchUnit architecture feedback, test
and mutation evidence, quality regression gates, drift protection, and transactional apply.

## Workflow

1. Resolve the plugin root as two directories above this `SKILL.md`, then use
   `<plugin-root>/bin/jaipilot` for every command.
2. Run `jaipilot inspect --project <root>`.
3. Choose `classes`, `changed`, or `all`. Default a general cleanup to `changed`; require explicit
   user intent before using `all`.
4. Run `jaipilot quality --project <root> --mode <mode> ...`. Triage critical/high bug risks first,
   then high-debt complexity and duplication, code smells, performance findings, and modernization.
5. Run `jaipilot prepare-cleanup --project <root> --mode <mode> ...`. This clean-baselines the
   project, creates an isolated workspace, and runs the pinned exact-source OpenRewrite bundle only
   on selected production files.
6. Work only inside `result.workspaceRoot`. Review `openRewriteChanges`, `qualityBefore`,
   `qualityAfterOpenRewrite`, and `architectureBefore`; keep useful deterministic fixes and refine or
   revert inappropriate ones.
7. Review selected code and directly related tests for evidence-backed improvements:
   - correctness defects, null handling, exception behavior, and broken contracts;
   - resource leaks, concurrency hazards, unsafe state, and error recovery;
   - dead, duplicated, redundant, overly complex, or misleading code;
   - measurable performance waste without speculative micro-optimization;
   - readability, cohesion, API clarity, and maintainability.
8. Preserve behavior unless a regression test proves a defect. Edit only selected production Java
   and directly relevant Java tests; never change builds, configuration, documentation, generated
   output, or unselected production files.
9. Run `jaipilot validate --run <runId>`. Resolve every item in `architecture.violations`, any new
   critical/high finding, and any quality-score regression. If tests changed, the required 70% PIT
   gate runs; review survivors and strengthen the tests. Repeat until `readyToApply` is true.
10. After reviewing the candidate and confirming the requested change, run
   `jaipilot apply --run <runId> --confirm`. Otherwise discard it.

## Quality evidence

Use each finding's rule, severity, file, line, symbol, remediation, estimated effort, and quick-fix
indicator. Report reliability, maintainability, complexity, duplication, and overall quality scores
with raw finding counts, code-smell count, modernization opportunities, duplicated lines, remediation
debt, and parse failures. Scores prioritize review; they do not replace contextual judgment.

Report OpenRewrite changes, findings resolved and introduced, before/after score and debt deltas,
ArchUnit completeness and violations, changed tests, build and mutation evidence, elapsed timings,
warnings, and whether the verified candidate was applied.
