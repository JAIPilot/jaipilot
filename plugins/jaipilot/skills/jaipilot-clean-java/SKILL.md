---
name: jaipilot-clean-java
description: Analyze, clean, refactor, modernize, and prove Java with JAIPilot's local OpenRewrite-first evidence kernel. Use for code smells, bug risks, complexity, duplication, maintainability debt, resource safety, performance cleanup, or IDE-style Java inspections in Maven and Gradle repositories.
---

# Clean Java with JAIPilot

Let the host agent own the branch or worktree, edits, retries, cancellation, Git, and user decisions.
Use JAIPilot only for deterministic scope, pinned OpenRewrite, quality evidence, and final proof.

## Workflow

1. Inspect the repository with `jaipilot_inspect` or the private runner:
   `<plugin-root>/bin/jaipilot inspect --project <root>`.
2. Choose `changed`, `classes`, or `all`. Use `all` only for an explicit whole-project request.
3. Run `jaipilot_quality`. Prioritize critical/high bug risks, then complexity, duplication, debt,
   performance, and modernization. Treat parse failures as incomplete evidence.
4. Ensure the agent controls a clean, recoverable Git branch or worktree. Run `jaipilot_rewrite` for
   the exact scope so pinned OpenRewrite recipes execute first. Review the resulting Git diff; keep
   only useful, behavior-preserving edits.
5. Refine the smallest coherent change. Add a regression test before changing established behavior.
   Do not lower gates, suppress findings, or broaden scope merely to obtain a pass.
6. Run the repository's normal focused tests while iterating. When the diff is stable, run
   `jaipilot_prove_diff` once. Resolve every build, coverage, PIT, quality, and ArchUnit failure.
7. Recheck `jaipilot_diff_gate`. Continue only when the exact current fingerprint is `passed` or
   no Java/build input is applicable.

Report the selected scope, OpenRewrite edits accepted or reverted, findings and debt changed,
tests added, proof evidence, warnings, and remaining limitations. JAIPilot never commits or applies
the patch; the agent follows the user's normal Git and review workflow.
