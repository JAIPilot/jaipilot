# Petclinic controlled pilot results

Run date: 2026-08-09

## Decision

The current JAIPilot 4.0.3 default workflow did **not** meet the frozen investment threshold in this
pilot. Baseline and treatment each produced 9 accepted changes out of 12 trials (75%). Treatment
added 58.6% median agent time and 100.9% median input-token overhead, above the 25% limit.

This result supports a narrower product: let the host agent reason, interpret requirements, choose
tests, and edit. Keep JAIPilot as an explicitly requested deterministic quality/proof layer for risky
or release-bound Java diffs. Do not run cleanup or full proof automatically on routine changes.

## Frozen setup

- Repository: `spring-petclinic/spring-framework-petclinic`
- Revision: `233dfcd06db3fb0505c2accc106f45ef72670990` (`v7.0.3`)
- Java: Amazon Corretto 17.0.13
- Agent: Codex CLI 0.147.0, `gpt-5.6-sol`, `xhigh`, fast service tier
- JAIPilot: 4.0.3, available only in the treatment arm
- Design: four tasks, three repetitions, two arms, randomized with seed `20260808`
- Acceptance: hidden contract tests, Java 17 `./mvnw -B clean verify`, diff check, and scope check

The prompt was identical between arms and never instructed the treatment agent to use JAIPilot.
Treatment registered the plugin's own stdio MCP server because this Codex CLI version did not merge
the installed plugin MCP configuration into non-interactive `codex exec`. Both arms used the same
non-interactive `danger-full-access` sandbox because Mockito self-attachment and PIT could not run in
the macOS `workspace-write` sandbox.

## Aggregate results

| Measure | Baseline | JAIPilot | Difference |
| --- | ---: | ---: | ---: |
| Accepted trials | 9/12 (75%) | 9/12 (75%) | 0 percentage points |
| Median agent time | 230.322 s | 365.224 s | +58.6% |
| p95 agent time (nearest rank) | 396.666 s | 599.079 s | +51.0% |
| Median input tokens | 601,954.5 | 1,209,465 | +100.9% |
| Median output tokens | 11,686.5 | 15,343 | +31.3% |
| Hidden-test passes | 9/12 | 9/12 | 0 |
| Diff/scope passes | 12/12 | 12/12 | 0 |
| JAIPilot references | 0 | 182 | +182 |

## Results by task

| Task | Baseline accepted | JAIPilot accepted | Baseline median | JAIPilot median | Median overhead |
| --- | ---: | ---: | ---: | ---: | ---: |
| Pet transfer | 3/3 | 3/3 | 259.806 s | 517.639 s | +99.2% |
| Upcoming visits | 0/3 | 0/3 | 171.437 s | 234.354 s | +36.7% |
| Vet specialty | 3/3 | 3/3 | 148.456 s | 264.444 s | +78.1% |
| Visit scheduling | 3/3 | 3/3 | 244.859 s | 419.425 s | +71.3% |

Raw agent times, in repetition order:

| Task | Baseline seconds | JAIPilot seconds |
| --- | --- | --- |
| Pet transfer | 259.806, 238.110, 396.666 | 599.079, 517.639, 486.259 |
| Upcoming visits | 160.839, 171.437, 223.184 | 234.354, 230.390, 287.167 |
| Vet specialty | 132.277, 227.538, 148.456 | 329.323, 242.198, 264.444 |
| Visit scheduling | 233.106, 246.515, 244.859 | 433.699, 401.125, 419.425 |

## What JAIPilot demonstrably helped

- In all three visit-scheduling treatment trials, changed-code analysis found the newly written
  service method above the complexity limits (cyclomatic 13 and cognitive 16). The agent extracted
  narrow validation, lookup, and conflict helpers while retaining passing tests.
- One pet-transfer proof failed closed on missing changed-line execution evidence. The agent added
  concrete tests for both repository-not-found conventions and a domain refusal path, and removed an
  abstract test whose execution could not be attributed deterministically.
- Exact-diff receipts, clean builds, changed-line coverage, mutation scores, and architecture checks
  gave useful release evidence for accepted treatment patches.

These are quality and confidence improvements, not acceptance-rate improvements in this sample.

## What did not work

- Every upcoming-visits trial in both arms implemented a plausible repository-backed interpretation.
  The task required aggregation from visits attached to the owner's pets. All six self-authored test
  suites encoded the same wrong assumption, and all six failed the independent contract tests.
- JAIPilot reported successful high-coverage, high-mutation proofs for the three treatment versions
  of that incorrect behavior. It proved the selected tests and diff; it did not prove the business
  requirement. Coverage and mutation strength must not be presented as semantic correctness.
- Treatment skills repeatedly ran OpenRewrite on clean targets before feature work. The recipes
  proposed only unrelated import/annotation formatting (including wildcard imports), which agents
  then reverted. This happened systematically and added work without value.
- Treatment commonly ran full tests plus clean coverage/PIT proof, sometimes more than once. The
  additional build and reasoning work increased both latency and context usage substantially.

## Product action

1. Remove automatic hooks and automatic proof from the default coding path. The agent should opt in
   when a Java diff is risky, release-bound, or explicitly asks for proof.
2. Do not run OpenRewrite when scoped quality has no actionable finding. Keep rewrite as an explicit
   cleanup operation selected by the host agent.
3. Keep a lean MCP surface centered on repository inspection, changed-code quality, exact-diff proof,
   status, and cancellation. Delegate requirement interpretation, test design, and remediation choices
   to the host agent.
4. Label proof honestly: it certifies the exact diff against the executed build/tests and thresholds;
   it does not certify that the tests encode the intended business contract.
5. Re-run this frozen pilot only after the workflow is simplified. A positive Codex result would then
   require independent Claude Code replication before any provider-neutral claim.

No Claude replication was run because the Codex treatment did not pass the predeclared positive-result
threshold.
