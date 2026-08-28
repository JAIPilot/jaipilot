# Use ArchUnit for durable boundaries and route rewrites deliberately

Use ArchUnit to preserve a stable architectural boundary in executable tests. For a bounded,
repeatable source or build migration, invoke `jaipilot-openrewrite` and let that skill decide whether
a configured recipe, approved temporary recipe, manual edit, or no action is appropriate. Neither
tool is a default requirement for ordinary cleanup, and neither proves behavior by itself.

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
