# JAIPilot 6.1.3 matched multi-repository evaluation

This evaluation asks a narrow question: does adding JAIPilot to the same strong coding agent improve
the outcome on realistic Java maintenance work?

The answer from these three trials is mixed. JAIPilot did not improve held-out correctness. It did
improve one task's proof boundary and verification choice. That supports JAIPilot as a Java
engineering workflow and remote-execution product, not as a replacement for model intelligence.

## Protocol

Each task used Codex 0.149.1 with `gpt-5.6-sol`, `xhigh` reasoning effort, the same prompt, and the
exact parent of an accepted upstream fix. The control ran with plugins disabled. The treatment ran
with JAIPilot 6.1.3. Both worked locally in separate checkouts and were prohibited from fetching,
contacting GitHub, or inspecting another checkout.

After both agents stopped, we constructed a new oracle checkout containing:

1. the exact pre-fix source commit;
2. only that agent's production diff; and
3. the accepted upstream regression tests, which the agent had not seen.

We then ran the narrow upstream test command. Self-authored tests and prose were not accepted as the
correctness oracle. No candidate was repaired after seeing the held-out result.

## Task prompts

Each pair received the same repository-specific request, followed by the same constraints to keep
the change small, avoid GitHub and other checkouts, run focused and repository-native verification,
and report commands, outcomes, changed files, and limitations.

Commons Lang:

```text
Fix CharRange.contains(CharRange) for a negated argument that touches Character.MIN_VALUE or
Character.MAX_VALUE. Preserve every negated/non-negated combination and null behavior. Cover the
lower boundary, upper boundary, and the empty negated range with focused regression tests.
```

Gson:

```text
Make duplicate Map keys, AtomicLongArray null elements, invalid Character lengths, and malformed
URL/URI values throw JsonSyntaxException with the current JSON path. Preserve URL/URI parsing causes,
successful behavior, null semantics, and public compatibility. Add non-root regression cases.
```

Calcite:

```text
Fix SqlUtil.lookupSubjectRoutines when SqlKind.getFunctionKind() remaps a kind and multiple
candidates share a name. Reproduce it with a duplicated operator table covering POSITION,
CHAR_LENGTH, and CHARACTER_LENGTH. Preserve overload resolution, syntax filtering, and ordinary
non-remapped behavior.
```

## Results

| Repository and task | Exact source | Without JAIPilot | With JAIPilot | Comparison |
| --- | --- | --- | --- | --- |
| Apache Commons Lang: negated `CharRange` boundaries | [`596269f`](https://github.com/apache/commons-lang/commit/596269f16d33cd4a20223978c245a15c898c5084), before accepted commit [`1d6ef29`](https://github.com/apache/commons-lang/commit/1d6ef29ce004309e50bd91e27f0f5e80e1a57a76) ([PR 1775](https://github.com/apache/commons-lang/pull/1775)) | 19/19 held-out tests passed; full Maven verification passed 89,197 tests. | 19/19 held-out tests passed; repository verification passed 89,199 tests plus configured analyzers. | Hidden-correctness tie. The control used a smaller production expression; JAIPilot used three explicit branches. No JAIPilot uplift. |
| Google Gson: consistent deserialization exceptions, paths, and causes | [`119818b`](https://github.com/google/gson/commit/119818bc666d3b9f897d6c0ca7546ce28e9bbcac), before accepted commit [`dae37cf`](https://github.com/google/gson/commit/dae37cf0fe12235b76fb09f01118a0a8c8823f42) ([PR 3096](https://github.com/google/gson/pull/3096)) | 149/155 held-out tests passed; its own eight-module clean build passed. | 149/155 held-out tests passed; its own eight-module clean build passed. | Tie. Both covered every requested error family but failed the same six accepted assertions for exact duplicate-key wording/location and richer Character wording. |
| Apache Calcite: function-kind remapping with duplicate candidates | [`7925800`](https://github.com/apache/calcite/commit/7925800cb86892e32183959776cf476c4add1244), before accepted commit [`f9b66d4`](https://github.com/apache/calcite/commit/f9b66d49297c525166cac172234cc2f336361f5a) ([PR 5197](https://github.com/apache/calcite/pull/5197)) | The held-out upstream regression passed; `:core:check` passed 16,632 tests. | The held-out upstream regression passed; `:core:check` passed 16,632 tests. | Both production diffs were byte-identical. JAIPilot additionally checked non-remapped `UPPER` behavior and went directly to the scoped core gate. |

Summary: **0 JAIPilot hidden-correctness wins, 0 losses, 3 ties.** The Calcite treatment supplied the
only clear incremental benefit: one relevant control case and a better-scoped verification plan.

## Held-out commands

Commons Lang used the accepted `CharRangeTest` against each candidate production diff:

```bash
mvn -Dtest=CharRangeTest test
```

Gson used all five accepted affected test classes against each candidate production diff:

```bash
mvn -pl gson \
  -Dtest=DefaultTypeAdaptersTest,JavaUtilConcurrentAtomicTest,MapAsArrayTypeAdapterTest,MapTest,PrimitiveCharacterTest \
  test
```

The Calcite production patches had the same SHA-256. One shared oracle run therefore tested the
byte-identical patch with the accepted upstream method:

```bash
./gradlew :core:test \
  --tests org.apache.calcite.test.SqlValidatorTest.testFunctionKindMismatchWithDuplicateOperatorTableEntry
```

## What this supports

- A strong coding model can already solve difficult Java defects. JAIPilot should not claim credit
  merely because it was installed.
- JAIPilot gives the host agent a consistent method for baselines, focused tests, native quality
  gates, bounded scope, and final review.
- That method can improve proof quality and avoid irrelevant verification work, as it did on
  Calcite.
- Remote execution is a separate benefit. None of these treatment runs used it because the edits
  were local and the agents correctly determined that remote execution was not necessary.

## Limitations

- This is one run per condition on three hand-selected maintenance tasks, not a statistically
  powered benchmark.
- Accepted upstream tests are a useful independent oracle, not a complete definition of quality.
  Gson's six misses were diagnostic-contract differences, not failures to throw the requested
  exception types.
- Dependency caches were shared and runs were sequential, so wall-clock times are descriptive and
  are not used to claim a speedup.
- Gson and Calcite used depth-one checkouts containing only the exact parent commit. Commons used
  isolated worktrees from a local mirror where the later object was technically reachable; command
  transcripts showed neither agent inspected it, but that trial was not object-level source-blind.
- The agents knew whether JAIPilot was installed. Only the accepted tests and fixes were held out.
- The Petclinic and remote-execution acceptance results in the README test different product
  properties and are not folded into the three-repository score above.

## Try the claim that matters

Install JAIPilot, choose a real Java diff with a meaningful proof burden, and ask:

```text
Make this Java change production-ready without changing behavior. Establish a focused baseline,
add only meaningful regression tests, keep the implementation small, run the narrow checks while
iterating, then run the repository-native final gate and tell me exactly what remains unverified.
```

Compare the resulting diff, test evidence, and unverified boundaries with your normal agent
workflow. JAIPilot earns its place only when that evidence is better.
