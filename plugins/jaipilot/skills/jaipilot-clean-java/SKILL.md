---
name: jaipilot-clean-java
description: Safely reduce, consolidate, modernize, and optimize Java with repository evidence. Use for unused code, dead classes, duplication, too many classes or methods, logical consolidation, dependency or JDK upgrades, OpenRewrite migrations, slow algorithms, allocation or concurrency problems, virtual threads, code smells, complexity, or performance cleanup.
---

# Make Java smaller, newer, and faster without guessing

Optimize for verified value, not fewer lines, newer version numbers, or fashionable constructs.
Never claim universal safety: Java reflection, frameworks, configuration, external consumers, and
unmeasured workloads make that impossible. Fail closed when required evidence is unavailable.

## Select the requested modes

Read only the references needed for the request:

- [unused.md](references/unused.md): remove repository-proven unused code, dependencies, or
  resources;
- [consolidate.md](references/consolidate.md): merge genuinely equivalent logic and reduce classes,
  methods, and lines without creating a generic abstraction;
- [modernize.md](references/modernize.md): upgrade the JDK, build, framework, plugins, and public
  dependencies to verified compatible stable releases; and
- [performance.md](references/performance.md): improve measured algorithms, allocation, I/O, and
  concurrency, including virtual threads when the workload proves they fit.

For a combined request, use this order unless repository constraints justify another: remove proven
unused leaves, consolidate equivalent behavior, modernize in isolated batches, then optimize the
result against a fresh benchmark.

## Establish the shared boundary

1. Confirm that the selected root contains a Java Maven or Gradle project. Otherwise report that
   this skill is not applicable and stop.
2. Read repository instructions, build files, modules, source sets, generated-source rules,
   profiles, tests, resources, packaging, supported runtimes, and public API policy.
3. Record Git status and save the tracked diff plus an inventory and content snapshot of relevant
   untracked files for later comparison. Preserve edits outside the explicit scope. Do not assume
   every dirty line is unrelated: an in-scope candidate belongs to the requested cleanup, while
   unclear ownership stays untouched and is reported.
4. Define whether the boundary is a closed application or a published library, plugin, SDK,
   framework extension, or service with downstream consumers.
5. Run the repository's focused and normal verification baseline. Record existing failures before
   edits and never make a failing baseline look green by weakening measurement.

## Work in reversible evidence-backed batches

1. When OpenRewrite is configured, inspect its pinned version and recipes. Run the smallest
   applicable dry run before manual cleanup or migration and review every proposed edit. Never add
   tooling without approval.
2. Use an isolated worktree or equivalent reversible copy when relevant state can be reproduced
   without hiding dirty work. Never reset, clean, or stash unrelated work.
3. Apply one coherent leaf, consolidation seam, upgrade axis, or performance hypothesis at a time.
   Compile and run the narrowest relevant tests after every behavior-sensitive batch.
4. Reverse only a failed batch. Recompute references, compatibility, and measurements before the
   next batch because one accepted change can alter later evidence.
5. Use `$jaipilot-generate-tests` to add characterization or regression tests when behavior is not
   adequately locked before a sensitive change. Do not add hollow tests merely to permit a rewrite.
6. After integration, use `$jaipilot-review-diff` to inspect the complete Java and build diff and run
   the repository's applicable final proof.
7. Use `$jaipilot-remote-java` when a build, analyzer, profiler, or benchmark materially benefits
   from disposable remote hardware and the exact state is already a GitHub-available commit. Do not
   treat committed remote evidence as proof of dirty local edits.

## Shared acceptance rules

Accept a change only when:

- the observable contract, errors, ordering, transactions, security, resource ownership, and
  framework lifecycle remain intentional;
- public API and downstream compatibility match the declared boundary;
- configured tests, architecture checks, static analysis, packaging, and relevant profiles pass;
- no dependency, exclusion, suppression, timeout, threshold, or test was weakened to pass; and
- the final benefit is measured in the mode's terms and outweighs added indirection or risk.

Prefer zero net change when proof is incomplete. A passing suite is evidence, not a universal
guarantee.

## Report

Return:

- modes, boundary, initial worktree state, baseline, and unavailable evidence;
- candidate or hypothesis ledger with accepted, rejected, and retained items;
- OpenRewrite recipes and proposed edits accepted or rejected;
- exact commands and pass, fail, skipped, or unavailable results;
- before/after code shape, resolved versions, or performance measurements as applicable;
- the final diff relative to the saved starting state, not only relative to `HEAD`;
- external-consumer, runtime, workload, and profile assumptions; and
- remaining risk plus how to reverse the uncommitted patch.
