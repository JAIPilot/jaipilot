---
name: jaipilot-review-diff
description: Review, improve, and prove Java changes from Git commits or the working tree. Use for branch review, pre-commit quality, changed-code coverage, mutation strength, architecture checks, or Java pull-request diff review when the host agent decides deterministic proof is useful.
---

# Review a Java Diff with JAIPilot

Treat the returned Git fingerprint as the proof boundary. The host agent owns analysis, editing,
retries, cancellation, commits, and review; JAIPilot owns deterministic local evidence.

## Workflow

1. Run `jaipilot_diff_gate`. Feature branches use the local default-branch merge base; the default
   branch uses `HEAD^` plus staged, unstaged, and untracked work. Set `JAIPILOT_DIFF_BASE` only for an
   explicit local comparison ref. JAIPilot never fetches.
2. Stop when status is `not_applicable` or `passed`. For `review_required`, retain the returned
   production, test, and build paths as the boundary.
3. Run `jaipilot_quality --mode changed`. Fix critical/high findings and parse failures first. Run
   `jaipilot_rewrite --mode changed` when production cleanup is useful, then review every recipe edit.
4. Inspect behavior and tests. Make the smallest coherent correction in the agent-controlled Git
   worktree. Preserve behavior unless a regression test proves a defect.
5. Use focused builds while editing. Avoid repeatedly running the full proof against a moving diff.
6. Run `jaipilot_prove_diff` after the diff stabilizes. By default it requires a clean full build,
   90% changed-line coverage, 85% changed-branch coverage, 80% changed-line PIT score, 90 changed-code
   quality, zero new critical/high findings, and complete zero-violation ArchUnit evidence.
7. Resolve raw failures and rerun. Finish only when `jaipilot_diff_gate` confirms the same fingerprint.

Do not lower gates, add exclusions, or change build configuration merely to pass. Build/test-only and
deletion-only diffs still require the clean build; non-applicable coverage, mutation, or architecture
must remain explicit. Report the baseline, fingerprint, targets, edits, coverage, mutations, quality,
ArchUnit results, elapsed time, warnings, and final status.
