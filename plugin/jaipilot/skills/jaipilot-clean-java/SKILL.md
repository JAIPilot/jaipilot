---
name: jaipilot-clean-java
description: Clean, modernize, and validate Java code through JAIPilot's OpenRewrite-first isolated MCP workflow. Use when a user asks to clean, refactor, simplify, optimize, review, or improve Java production code safely.
---

# Clean Java with JAIPilot

Combine pinned, exact-scoped OpenRewrite recipes with the connected agent's contextual review. JAIPilot owns isolation, scope enforcement, clean builds, drift protection, and transactional apply.

## Workflow

1. Call `jaipilot_inspect_project` with the project root.
2. Choose one target mode from the user's intent:
   - `classes`: pass class names, fully qualified names, or source paths.
   - `changed`: default for a general cleanup request.
   - `all`: use only when the user explicitly requests repository-wide production cleanup.
3. Call `jaipilot_prepare_cleanup`. This performs a clean baseline build, copies an isolated workspace, and runs pinned `CodeCleanup` plus `CommonStaticAnalysis` recipes only on selected production files.
4. Read `openRewriteChanges`, `agentInstructions`, `targets`, and `workspaceRoot`.
5. Work only inside `workspaceRoot`. Use the host's file tools when that path is accessible; otherwise use `jaipilot_read_run_file` and `jaipilot_write_run_file`. Review OpenRewrite's diff rather than blindly preserving every mechanical change.
6. Improve the selected production files and only directly related Java tests. Do not edit build files, configuration, documentation, generated artifacts, or unselected production files.
7. Call `jaipilot_validate_run`. Fix the isolated workspace and revalidate until `readyToApply` is true or a concrete blocker remains.
8. Apply with `jaipilot_apply_run` only after successful validation and user authorization. Otherwise call `jaipilot_discard_run`.

## Cleanup Standard

- Preserve public behavior unless a proven defect and regression test justify a change.
- Prioritize correctness, resource safety, concurrency safety, clear ownership, small methods, useful names, simpler control flow, null and error handling, and deletion of dead or redundant code.
- Optimize performance only when the affected journey and measurement boundary are explicit. Do not replace clear code with speculative micro-optimizations.
- Keep Java 17 compatibility and the project's existing conventions and dependency set.
- Avoid broad abstractions, churn-only formatting, suppressed warnings, weakened tests, hidden failures, or timeout inflation.
- Prefer a smaller validated diff over a larger cosmetic rewrite.

## SonarQube Comparison Discipline

Aim to deliver workflow value beyond static analysis: deterministic recipes, agent reasoning, isolated candidate edits, executable regression tests, clean-build proof, fresh coverage feedback, drift detection, and transactional merge. Never claim JAIPilot is universally or "supremely" better than SonarQube—or any tool—without a reproducible, comparable benchmark. State the actual scope and evidence.

## Safety and Handoff

- Never edit the live project while a run is open or copy sandbox files manually.
- Do not bypass scope, build, validation, or drift failures. Correct the cause.
- Report selected classes, OpenRewrite changes, final changed files, clean verification, warnings, performance evidence when relevant, and apply status.
