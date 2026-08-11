---
name: jaipilot-clean-java
description: Simplify, refactor, and verify Java while preserving behavior and scope. Use for code smells, dead code, duplication, excessive complexity, resource safety, maintainability, modernization, or requests to clean changed Java with configured OpenRewrite and analysis tools.
---

# Clean Java safely

Make the code smaller and clearer without turning cleanup into an unrelated rewrite.

## Establish scope

1. Confirm that the selected root contains a Java Maven or Gradle project. Otherwise report that
   this skill is not applicable and stop.
2. Read repository instructions, build files, relevant production code, and tests.
3. Record Git status and the exact requested files or behavior. Preserve unrelated work.
4. Establish a passing focused baseline when practical. If the baseline already fails, separate that
   failure from the cleanup.
5. Identify concrete debt: dead code, duplication, needless abstraction, tangled control flow,
   misleading names, unsafe resources, broad exceptions, avoidable allocation, or outdated patterns.

## Clean

1. Prefer deletion, direct code, existing abstractions, and repository conventions.
2. Keep behavior stable. Add or strengthen a regression test before intentionally changing behavior.
3. If the repository configures OpenRewrite, inspect the pinned version, active recipes, scope, and
   dry-run diff before applying a relevant recipe. Review every resulting edit.
4. If OpenRewrite is absent, state that it is unavailable. Do not inject a plugin, recipe, dependency,
   suppression, or exclusion without user approval.
5. Use configured formatter, compiler checks, Checkstyle, PMD, SpotBugs, Error Prone, ArchUnit,
   SonarQube reports, or similar analyzers when applicable.
6. Refine mechanical output into the smallest readable change. Remove speculative helpers,
   comments that restate code, compatibility paths without a requirement, and unrelated formatting.

## Verify

1. Run focused tests after each coherent behavior-sensitive change.
2. Run the normal module or repository verification command after the diff stabilizes.
3. Run relevant configured architecture and static-analysis tasks.
4. Use configured coverage or mutation testing when production behavior or tests changed.
5. Re-read the complete final diff. Confirm that every changed line serves the requested cleanup or
   its proof and that generated output is absent.
6. Report a failed or unavailable check honestly instead of weakening the cleanup criteria.

## Report

Return:

- files and debt selected;
- code deleted, simplified, or rewritten and why;
- OpenRewrite recipes and mechanical edits accepted or rejected, when applicable;
- exact test, build, architecture, and analyzer commands with results;
- behavior evidence and any measured coverage or mutation result; and
- remaining debt deliberately left outside scope.

Do not claim behavior preservation without relevant execution evidence.
