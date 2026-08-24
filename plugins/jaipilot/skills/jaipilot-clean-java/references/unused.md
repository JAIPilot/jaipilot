# Remove repository-proven unused code

Prefer a missed cleanup over an unsafe deletion. Treat "safe" as fail-closed: delete only when every
applicable check succeeds, and retain every uncertain candidate with the reason.

## Build a deletion ledger

1. Use configured OpenRewrite recipes, compiler checks, IDE-style reports, Checkstyle, PMD,
   SpotBugs, Error Prone, SonarQube reports, or dependency analyzers to nominate candidates, not to
   prove deletion alone.
2. Search symbol references and textual or configuration references across every applicable source
   set, test, resource, template, build file, service descriptor, script, SQL file, and profile.
3. Order candidates leaf-first: imports and side-effect-free locals; private members; package types;
   source files; then dependencies or resources. Recompute after each accepted deletion.
4. Do not infer unused code from coverage, one analyzer, an IDE hint, or text search alone. Never
   delete a test merely because it did not execute in one run.
5. Record each candidate's kind, module, definition, searches, analyzer evidence, dynamic-use risks,
   proposed deletion, and required verification.

## Prove each deletion

Require all applicable conditions:

1. A symbol-aware search finds no Java reference outside the definition, and a repository-wide text
   search finds no configured or generated reference.
2. The candidate is not an override, interface implementation, constructor contract, bean property,
   serialization field, annotation processor input, JNI binding, service-provider entry, command,
   endpoint, listener, scheduled job, persistence mapping, injection target, expression-language
   name, reflective target, or framework-discovered type.
3. Removal cannot discard initialization, constructor, assignment, registration, cleanup, or other
   side effects. Treat unknown calls and class initialization as effectful.
4. Applicable profiles, source sets, platforms, flags, and downstream consumers are represented by
   available evidence.
5. Retain public or protected API unless the user supplies a closed consumer boundary and API
   compatibility evidence permits removal. Retain uncertain package API.
6. Retain dependencies, resources, migrations, configuration keys, templates, and service
   descriptors by default. Remove one only when requested, all static and dynamic uses are excluded,
   applicable profiles pass, and packaging or runtime smoke evidence exists.

Treat "AI slop" as observable waste, never an authorship judgment: redundant comments that restate
code, unused imports or locals, unreachable branches, duplicate guards, needless temporary
collections, forwarding methods with no policy, speculative extension points, and abstractions with
one trivial use. Do not delete domain rationale, compatibility adapters, generated sources, or
framework entry points merely because they look verbose.

An item already dirty at the saved starting state is not automatically unrelated. If it is inside
the user's explicit cleanup boundary, evaluate it normally; otherwise preserve it. Track accepted
deletions against the starting snapshot so the final report does not mislabel the agent's own edit
as a pre-existing state.

## Apply and verify

1. Accept only the proven-safe subset of a configured OpenRewrite dry-run, or edit the same scope
   manually when no recipe applies.
2. Delete one candidate or independent leaf batch at a time. Do not combine it with behavior
   changes, renames, modernization, or unrelated formatting.
3. Compile and run narrow affected tests after each sensitive batch, then re-run discovery for
   cascading candidates.
4. Finish with the normal clean build plus configured profiles, architecture, static analysis, API
   compatibility, packaging, integration, and smoke checks applicable to the boundary.
5. Report a ledger of removed items and proof, retained candidates and uncertainty, lines or files
   removed, unavailable evidence, and reversal steps. Describe incomplete boundaries as verified
   local cleanup, never universally safe.
