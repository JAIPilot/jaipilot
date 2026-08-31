---
name: jaipilot-clean-java
description: Safely remove unused Java, consolidate equivalent logic, reduce real complexity, enforce stable architecture rules, modernize compatible dependencies or JDKs, and optimize measured workloads. Use for dead code, AI-generated clutter, duplication, excessive classes or methods, ArchUnit rules, stable upgrades, slow algorithms, allocation, I/O, contention, or virtual-thread evaluation.
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
  concurrency, including virtual threads when the workload proves they fit;
- [architecture-and-rewrites.md](references/architecture-and-rewrites.md): use ArchUnit for a stable
  architectural invariant and route justified type-aware migrations to `jaipilot-openrewrite`.

Use only the modes requested by the user or controlling workflow. For a comprehensive request, use
this order unless repository constraints justify another: remove proven unused leaves, consolidate
equivalent behavior, optimize measured workloads, then modernize justified build or runtime paths
in isolated batches. Evaluating a mode does not require editing in it.

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
5. Identify the repository's focused checks and normal verification command. Record known existing
   failures and never make a failing check look green by weakening measurement.

## Work in reversible evidence-backed batches

1. When an architecture rule would materially improve proof, follow
   [architecture-and-rewrites.md](references/architecture-and-rewrites.md). When a repeated
   type-aware migration would be safer than manual edits, invoke `jaipilot-openrewrite`. Prefer
   configured tools; never add ArchUnit, OpenRewrite, recipes, plugins, or build configuration
   without approval.
2. Use an isolated worktree or equivalent reversible copy when relevant state can be reproduced
   without hiding dirty work. Never reset, clean, or stash unrelated work.
3. Apply one coherent leaf, consolidation seam, performance hypothesis, or upgrade axis at a time.
   Compile and run the narrow relevant tests after every material batch.
4. Reverse only a failed batch. Recompute references, compatibility, and measurements before the
   next batch because one accepted change can alter later evidence.
5. Use the `jaipilot-generate-tests` skill when the request includes tests or coverage, or when a
   retained change exposes a specific regression gap. Do not add hollow tests merely to permit a rewrite.
6. After integration, use the `jaipilot-review-diff` skill to inspect the complete Java and build
   diff, then run the repository's applicable final clean proof.
7. Use `jaipilot-fast-execution` for substantial command work whenever safe batching or bounded
   native parallelism can reduce wall time without changing the required proof.
8. Default compilation, tests, analyzers, profilers, benchmarks, and final clean proof to the
   `jaipilot-remote-java` skill whenever the laptop provides no concrete advantage under that
   skill's routing rules. Remote proof covers only the uploaded exact committed revision. After
   relevant local edits, keep verification local until an authorized commit exists, then upload
   that new commit before claiming remote proof.

## Shared acceptance rules

Accept a change only when:

- the observable contract, errors, ordering, transactions, security, resource ownership, and
  framework lifecycle remain intentional;
- public API and downstream compatibility match the declared boundary;
- configured tests, architecture checks, static analysis, packaging, and relevant profiles pass;
- no dependency, exclusion, suppression, warning, timeout, threshold, or test was weakened to
  pass; and
- the final benefit is measured in the mode's terms and outweighs added indirection or risk.

Prefer zero net change when proof is incomplete. A passing suite is evidence, not a universal
guarantee.

## Report

Return:

- modes, boundary, initial worktree state, baseline, and unavailable evidence;
- candidate or hypothesis ledger with accepted, rejected, and retained items;
- ArchUnit rules and violations, plus OpenRewrite route and recipe evidence when invoked;
- exact commands and pass, fail, skipped, or unavailable results;
- before/after code shape, resolved versions, or performance measurements as applicable;
- the final diff relative to the saved starting state, not only relative to `HEAD`;
- external-consumer, runtime, workload, and profile assumptions; and
- remaining risk plus how to reverse the uncommitted patch.
