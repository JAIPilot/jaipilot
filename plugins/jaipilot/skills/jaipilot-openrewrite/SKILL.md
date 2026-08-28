---
name: jaipilot-openrewrite
description: Apply clean, bounded Java migrations with pinned OpenRewrite recipes and complete diff review. Use for repeated type-aware JDK, framework, dependency, build, package, or API transformations in Maven or Gradle; do not use for a small manual refactor or behavior redesign.
---

# Run clean Java migrations with OpenRewrite

OpenRewrite generates a candidate; it does not establish compatibility or authorize a broad
rewrite. Use it only when a repeatable type-aware transformation is safer and more reviewable than
manual edits. Keep behavior changes and design decisions in the host agent.

## Choose the migration route

Record one route before editing:

- `CONFIGURED_RECIPE`: the repository already pins applicable OpenRewrite tooling and a matching
  recipe;
- `TEMPORARY_RECIPE`: a reviewed recipe fits a sufficiently large mechanical migration, and the
  user approves temporary pinned tooling;
- `MANUAL`: the change is small, behavior-heavy, unsupported by a trustworthy recipe, or easier to
  review directly; or
- `NO_ACTION`: the target, compatibility boundary, maintainer direction, or required proof is
  unresolved.

Use `jaipilot-clean-java` for ordinary cleanup and modernization that does not justify a recipe.
OpenRewrite being available is not by itself a reason to select it.

## Establish the migration contract

1. Confirm the root is a Java Maven or Gradle repository. Read repository instructions, build
   files, modules, source sets, generated-code rules, supported JDKs and runtimes, public API
   policy, and normal verification.
2. State the exact migration axis, old and target states, affected modules, excluded generated or
   vendored paths, behavior to preserve, and downstream-consumer boundary. Do not combine an
   unrelated dependency, framework, JDK, formatting, or cleanup axis.
3. Record the revision, `git status --short`, tracked diff, and relevant untracked files. Preserve
   unrelated work; never reset, clean, stash, or overwrite it.
4. For an unfamiliar upstream repository, dependency-bot failure, or competing migration, invoke
   `jaipilot-maintainer-intent` first and continue only on `PROCEED`.
5. Run the smallest meaningful baseline compile and tests plus any configured API, architecture,
   serialization, or runtime check that defines acceptance. Keep pre-existing failures visible.

## Select and contain the recipe

1. Prefer repository-configured tooling. Verify the active recipe name, artifact, version,
   options, plugin/core compatibility, required JDK, supported source versions, license, and
   documented preconditions against current official or repository-approved sources.
2. Pin exact plugin and recipe artifact versions. Never use `latest`, `latest.release`, snapshots,
   dynamic ranges, or an unreviewed artifact repository. Recipe artifacts execute build-time code;
   treat their provenance like a build plugin.
3. If tooling is absent, explain why the migration justifies OpenRewrite and obtain approval before
   adding or running a plugin, recipe dependency, init script, or configuration. Prefer temporary
   configuration outside the repository; keep persistent setup only when the user explicitly wants
   repository-owned repeatability.
4. Author a custom recipe only when no maintained recipe fits and the repeated transformation
   justifies its own implementation and tests. Do not create a recipe to automate one or two clear
   edits.

Read [running-recipes.md](references/running-recipes.md) for the Maven, Gradle, and custom-recipe
execution details relevant to the selected route.

## Preview before writing

1. Use recipe discovery when identity or classpath availability is uncertain. Activate one
   migration axis explicitly and run the repository-native dry run before any source-writing run.
2. Inspect every proposed file and hunk. Reject generated output, vendored code, unrelated
   formatting, speculative modernization, broad dependency churn, suppressions, and edits outside
   the contract. If the patch is too large to review completely, narrow the recipe, modules,
   options, or target and preview again.
3. Save the recipe identity, options, dry-run command, proposed file inventory, and patch digest.
   A successful dry run proves only that the recipe executed.

## Apply one coherent candidate

1. Run the exact reviewed recipe and options in an isolated local worktree or reversible copy when
   possible. Recipe writes must remain inspectable local candidate edits; never treat unreturned
   remote workspace changes as the patch.
2. Compare the actual file inventory and diff with the preview. Investigate every difference and
   separate the smallest necessary manual compatibility fixes from generated edits.
3. Run focused compilation and tests after each coherent batch. Do not weaken tests, warnings,
   analyzers, dependency constraints, exclusions, timeouts, or compatibility checks.
4. Re-run the same dry run after the candidate stabilizes. Accept remaining output only when it is
   explicitly understood and justified; otherwise the migration is incomplete or non-idempotent.
5. Remove temporary configuration and generated reports before final review unless they are an
   explicitly requested, useful repository artifact.

## Verify and report

Invoke `jaipilot-review-diff` on the complete Java and build diff. Run affected tests, resolved
dependency comparison, configured API compatibility and analyzers, relevant runtime or packaging
smoke tests, and the normal clean verification. Default substantial compilation, tests, analysis,
and final proof to `jaipilot-remote-java` whenever the laptop provides no concrete advantage;
upload the latest local candidate after recipe and manual edits.

Report the route, migration contract, recipe and pinned coordinates, approval and configuration
boundary, baseline, preview and applied inventories, rejected output, manual follow-ups, repeat
dry-run result, exact verification commands and outcomes, final diff, unavailable evidence,
remaining compatibility risk, and reversal path. Never call a generated or green patch safe by
default.
