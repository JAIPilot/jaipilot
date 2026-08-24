---
name: jaipilot-optimize-java
description: Turn a bounded Java change into its smallest verified form by locking behavior, removing proven waste, simplifying logic, measuring performance, and evaluating compatible upgrades. Use when asked to optimize, perfect, harden, or comprehensively improve a Java diff, pull request, module, or explicitly scoped project area.
---

# Optimize one bounded Java change

Produce the smallest coherent improvement the repository can prove. "Optimize" means evaluating
the applicable passes below; it does not require an edit in every pass or justify unrelated work.
Prefer no production change to a plausible change with incomplete proof.

## Establish the boundary

1. Confirm the selected root is a Java Maven or Gradle repository. Otherwise report that this skill
   is not applicable and stop.
2. Read repository instructions, contribution guidance, build files, supported runtimes, affected
   modules, tests, profiles, generated-source rules, and public API policy.
3. Record the current revision, comparison base, `git status --short`, tracked diff, and relevant
   untracked files. Never fetch, switch branches, reset, clean, stash, or overwrite unrelated work.
4. Use the user's explicit scope. Otherwise use the current Java/build diff when one exists; do not
   silently turn a changed-code request into a repository-wide rewrite.
5. Define the observable behavior, downstream-consumer boundary, and normal clean verification.
   Run a baseline before editing and distinguish pre-existing failures from candidate regressions.

## Evaluate the passes in order

1. **Lock behavior.** Use the `jaipilot-generate-tests` skill in behavior-lock mode. Choose a focused
   repository-native command and run it before production edits. Lock material normal, boundary,
   null, error, ordering, state, transaction, serialization, concurrency, security, and lifecycle
   behavior. When new characterization tests are required, prove they pass against the saved
   original production state in an isolated copy when that can be done without hiding dirty work.
2. **Remove and simplify.** Use the unused and consolidation modes of the `jaipilot-clean-java` skill.
   Remove only repository-proven waste. Reduce real decisions, concepts, duplication, indirection,
   and generated-looking ceremony without moving complexity elsewhere or erasing domain rationale.
3. **Improve measured performance.** Use the performance mode of the `jaipilot-clean-java` skill only for a
   defined workload or deterministic operation count. Prefer algorithmic, I/O, allocation, parsing,
   and contention improvements. Require comparable baseline and candidate evidence; make no speed
   claim from inspection or one noisy timing.
4. **Modernize when justified.** Use the modernization mode of the `jaipilot-clean-java` skill when the user
   requested it, the changed code requires it, or the affected build path is already in scope.
   Evaluate authoritative stable releases and accept only independently reversible upgrades proved
   compatible with the declared JDK, framework, runtime, build, and consumer boundary.
5. **Review and re-prove.** Run the exact focused behavior command against the final candidate,
   invoke the `jaipilot-review-diff` skill, and finish with the repository's normal clean verification.
   Confirm intended tests and configured quality gates actually executed.

Use the `jaipilot-fast-execution` skill for substantial command work when safe batching or bounded native
parallelism materially reduces wall time. Use the `jaipilot-remote-java` skill only when the exact candidate
is already committed and available through GitHub; a remote result for `HEAD` never proves dirty
local files.

## Acceptance

For behavior, cleanup, performance, and modernization, record one of: applied,
already satisfied, not applicable, rejected, or unavailable. Accept the combined patch only when:

- behavior, errors, identity, ordering, transactions, security, persistence, serialization,
  concurrency, resource ownership, and framework lifecycle remain intentional;
- public API and downstream compatibility match the declared boundary;
- focused checks, the final clean build, and applicable configured analysis pass;
- no test, dependency, warning, threshold, timeout, exclusion, or suppression was weakened; and
- each retained edit has a concrete benefit that outweighs its added risk or indirection.

## Report

Return the scope and comparison base, initial worktree state, pass outcomes, edits retained and
rejected, exact commands and outcomes, before/after behavior or measurements, configured quality
evidence, unavailable boundaries, remaining risk, and reversal path. Do not describe a green build
as universal correctness.
