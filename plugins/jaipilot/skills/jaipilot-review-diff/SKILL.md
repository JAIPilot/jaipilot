---
name: jaipilot-review-diff
description: Review, improve, and prove Java production changes discovered from Git commits and the working tree. Use automatically when JAIPilot's Stop hook detects an unproved Java diff, or when asked to review branch changes, pre-commit quality, changed-code coverage, mutation strength, or a Java pull-request diff.
---

# Review a Java Git Diff with JAIPilot

Treat the current Git fingerprint as the boundary. The host agent reasons about behavior and edits
isolated candidates; JAIPilot discovers the scope and provides deterministic quality, clean-build,
JaCoCo, PIT, drift, and apply evidence.

## Workflow

1. Resolve the plugin root as two directories above this `SKILL.md`, then use
   `<plugin-root>/bin/jaipilot` for every command.
2. Run `jaipilot diff-gate --project <root>`. JAIPilot compares feature branches with the merge base
   of the local default branch; on the default branch it reviews `HEAD^` plus staged, unstaged, and
   untracked work. `JAIPILOT_DIFF_BASE` may define an explicit local comparison ref.
3. If `status` is `not_applicable` or `passed`, report that result and stop. For
   `review_required`, keep the returned changed production paths as the exact production scope.
4. Run `jaipilot quality --project <root> --mode changed`. Review critical/high bug risks first,
   followed by complexity, duplication, code smells, modernization, performance, and debt.
5. Improve production code through the OpenRewrite-first transaction:
   - Run `jaipilot prepare-cleanup --project <root> --mode changed`.
   - Work only in `result.workspaceRoot`. Review every OpenRewrite edit and refine only
     behavior-preserving, scope-appropriate changes.
   - Run `jaipilot validate --run <runId>` until `readyToApply` is true, then apply with
     `jaipilot apply --run <runId> --confirm`. If no worthwhile change exists, discard the run.
6. Strengthen tests through a separate isolated transaction:
   - Run `jaipilot prepare-tests --project <root> --mode changed --minimum-line-coverage 90
     --minimum-mutation-score 80`.
   - Add or improve the smallest focused tests for observable normal, boundary, failure, and state
     behavior. Use surviving PIT mutations to sharpen assertions; never add hollow coverage tests.
   - Validate until ready, apply the reviewed candidate, or discard it if existing tests already
     provide the required behavior evidence.
7. After the live diff is stable, run `jaipilot prove-diff --project <root>`. This copies the exact
   project into a fresh isolated workspace and requires, by default:
   - clean full-suite build and test execution;
   - at least 90% coverage on changed executable lines and 85% on changed branches;
   - at least 80% PIT mutation score for mutations located on changed lines, with complete evidence;
   - at least 90 new-code quality score and zero new or severity-escalated critical/high findings.
8. If proof fails, use its raw coverage, quality findings, mutation survivors, and warnings to make
   the smallest relevant correction, then rerun proof. Do not repeatedly run full proof while the
   diff is still changing.

## Boundaries

- Do not expand to all production code unless the user explicitly requests whole-project work.
- Do not lower a gate, add exclusions, suppress findings, or change build configuration merely to
  make the score pass.
- Preserve behavior unless a regression test proves a defect. Keep tests deterministic, isolated,
  fast, readable, and free of real network or sleep dependencies.
- A deletion-only diff still requires a clean full build; coverage and mutation are reported as not
  applicable when there is no remaining production target.
- If a target is genuinely unscorable or the project cannot emit JaCoCo/PIT evidence, report the
  exact limitation and evidence. Never claim or infer a perfect score.

Report the baseline description and fingerprint, changed targets, applied cleanup and tests, fresh
changed and whole-class line/branch coverage, changed-line PIT counts and survivors, new and whole-file quality metrics, composite
test score, elapsed verification time, warnings, and final `passed` status.
