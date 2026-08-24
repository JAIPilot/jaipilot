# Consolidate genuinely equivalent logic

Reduce code only when fewer concepts remain. Similar syntax is not proof of the same invariant.

## Find candidates

1. Use configured duplication reports and structural search, then read every candidate's callers,
   tests, data ownership, transactions, authorization, exceptions, ordering, lifecycle, and change
   history available locally.
2. Write the shared invariant in one sentence. Reject consolidation when the candidates merely look
   alike but encode different policies, domain language, rates of change, or failure semantics.
3. Record before counts for production classes, methods, executable lines, branches, dependencies,
   and duplicated blocks. Counts describe shape; they do not justify a change alone.
4. Prefer deleting a redundant path or reusing an existing domain abstraction over introducing a
   new utility, base class, strategy hierarchy, boolean mode, generic framework, or god object.

For complexity cleanup, prefer fewer real decisions, earlier exits, smaller pure operations, and
one authoritative invariant. Do not game cyclomatic or cognitive-complexity metrics by moving
branches into helpers, streams, annotations, reflection, configuration, or polymorphism. Use the
repository's configured analyzer when available; otherwise report structural decision counts
without inventing a tool score.

## Confirm equivalence before merging

1. Map each candidate's observable inputs, outputs, state transitions, errors, events, persistence,
   and concurrency semantics.
2. Compare callers, existing tests, configuration, and runtime contracts. Exercise both
   implementations with the same case matrix where their behavior is meant to match, and explicit
   separate cases where it is not. Reject the consolidation when equivalence is uncertain.
3. Retain public adapters or deprecation paths when downstream consumers are outside the boundary.
   Do not reduce class count by silently breaking API or serialization identity.

## Consolidate one seam

1. Choose one canonical implementation with the clearer domain ownership.
2. Migrate one consumer group at a time and run focused tests after each move.
3. Delete the old method or class only after all Java, configuration, reflection, framework, test,
   and generated references are gone under the unused-code proof.
4. Stop if the result adds indirection, parameters, branching, coupling, or a less precise name.

## Accept and report

Run clean repository verification and configured architecture, duplication, coverage, and mutation
checks. Accept only if existing verification passes and total concepts, duplication, or cognitive load fall
without worse coupling. Report before/after classes, methods, production lines, duplication,
branches, dependencies, adapters retained, rejected similarities, and any external-consumer risk.
