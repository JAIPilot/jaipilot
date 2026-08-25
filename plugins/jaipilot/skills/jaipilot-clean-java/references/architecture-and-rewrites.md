# Use ArchUnit and OpenRewrite when they add durable proof

These tools solve different problems. Use ArchUnit to preserve a stable architectural boundary in
executable tests. Use OpenRewrite to apply a bounded, repeatable source or build migration. Neither
is a default requirement for ordinary cleanup, and neither proves behavior by itself.

Prefer repository-configured tooling. When a tool is absent, explain the concrete benefit and get
user approval before adding a dependency, build plugin, recipe library, or lasting configuration.
Resolve current stable coordinates and compatibility from official documentation; pin exact
versions and never use dynamic version ranges.

## Add or run an ArchUnit rule

1. Derive the rule from repository instructions, an existing package/module boundary, or an
   explicitly agreed invariant—not a personal architecture preference.
2. Put the smallest readable rule in the affected module's normal test source set and follow its
   test framework conventions. Scope imported production classes precisely; exclude generated,
   fixture, or test packages only when the repository boundary requires it.
3. Prefer rules that state one durable constraint, such as dependency direction, layer access,
   package isolation, naming tied to framework discovery, or forbidden cycles. Do not encode a
   transient implementation shape merely to freeze the current patch.
4. Run the focused architecture test against the relevant baseline before relying on it. Do not
   hide existing violations with broad ignores, blanket exclusions, or a frozen baseline unless
   the user explicitly accepts that debt boundary.
5. Run the focused test after the edit and include it in the repository's normal verification.
   Report the exact invariant, imported scope, pre-existing violations, command, and outcome.

## Apply an OpenRewrite recipe

1. Prefer an existing pinned recipe and repository-native task. Otherwise select an official or
   reviewed recipe whose documented preconditions match the repository's JDK, build tool,
   framework, source level, and requested scope.
2. Record the starting Git state, recipe coordinate, version, options, and affected modules. Run
   recipe discovery or the smallest available dry run first; never start with a repository-wide
   write when a narrower selection exists.
3. Inspect every proposed file and reject unrelated formatting, generated output, speculative
   modernization, behavior changes, and edits outside the agreed boundary. A recipe is a candidate
   generator, not proof that its output is correct.
4. Apply one coherent recipe or migration axis at a time. Preserve unrelated dirty work and do not
   reset, clean, stash, or overwrite it. Remove temporary configuration after use unless the user
   wants a repeatable repository-owned migration setup.
5. Run focused compilation and behavior tests, then the normal clean verification and applicable
   compatibility checks. Report accepted and rejected recipe edits, exact commands, and any
   evidence that remained unavailable.
