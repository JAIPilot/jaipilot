# Codex with JAIPilot: a measured example

On October 5, 2026, two fresh Codex sessions received the same ordinary Java refactor request.
Adding JAIPilot through MCP produced **13 additional reusable tests** and a saved regression suite
that detected **four additional injected faults**.

| Retained regression suite | Codex alone | Codex + JAIPilot |
| --- | --- | --- |
| Passing JUnit tests | 3 | **16** |
| Line coverage across the fixture | 40% (14/35) | **65.7% (23/35)** |
| Branch coverage across the fixture | 30% (3/10) | **80% (8/10)** |
| Line coverage of the refactored `Deadline` class | 47.1% (8/17) | **100% (17/17)** |
| Injected faults caught by the saved JUnit suite | 3/10 | **7/10** |
| Agent session wall time | 3m 24s | **3m 18s** |

The MCP run called `lock_behavior` before production edits, preserved its generated tests,
then called `verify_behavior` successfully after the refactor. No repository instruction file
or explicit request to use JAIPilot was provided. The four additional detected faults were:
accepting a negative delay, ignoring time units, subtracting the delay, and clamping overflow to zero.

## Setup

- Same model and settings: Codex CLI 0.160.0, `gpt-6.1-sol`, `xhigh` reasoning.
- Same source: Kafka's `Deadline` and `LockUtils` at
  [commit `99b9407`](https://github.com/apache/kafka/commit/99b940733a9f6bc409457dba7108f08421d81e42),
  copied into a small Maven fixture with three initial smoke tests.
- Same task: extract the BigInteger calculation and saturation from `Deadline.fromDelay` into a
  private static helper, preserving behavior and existing tests.
- Same environment: macOS, Corretto 17.0.13, JUnit 5.11.4, JaCoCo 0.8.12, warm Maven caches.
- Separate fresh checkouts and sessions, with user configuration ignored. The second session had
  the compiled JAIPilot MCP server connected. Neither checkout contained a repository instruction file.

The refactored class was the selected JAIPilot scope. Fixture totals include the unchanged
`LockUtils`, whose coverage stayed the same in both runs.

## Measurement

These quality metrics describe **saved JUnit regression suites** that remain usable after the task.
Both completed refactors passed an independent `mvn -q clean verify`. The harness then ran each
saved suite against the same original source and ten predefined faults, one at a time. A fault
counted as detected when JUnit assertions failed. Compilation errors and timeouts did not count.
The original implementation and restored refactor passed again.

JaCoCo coverage was measured against the identical original source, avoiding differences in the
refactored code's line count. Runtime includes the entire Codex session and excludes fixture setup
and the independent fault checks. This is one paired example; results vary by task and run.

## Evidence and reproduction

[Measured results and fault definitions](results.json) include exact counters, the original prompt,
test file hashes, and MCP job states. Independent JaCoCo reports:
[Codex alone](codex-alone-coverage.xml), [Codex + JAIPilot](codex-with-jaipilot-coverage.xml).

With a compatible JDK, Maven, and both accounts signed in:

```sh
deno task compile
deno run -A --frozen scripts/compare-codex-mcp.ts dist/jaipilot
```

The [comparison script](../../../scripts/compare-codex-mcp.ts) fixes its inputs and faults before
either session. It spends account credits and retains the full local checkout and logs under `dist`.
