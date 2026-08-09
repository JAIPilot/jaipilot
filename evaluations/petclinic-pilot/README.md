# Petclinic controlled pilot

This evaluation tests whether making JAIPilot available helps the same coding agent produce more
acceptable Java changes. It is not a product demo and JAIPilot does not grade itself.

## Frozen design

- Repository: `spring-petclinic/spring-framework-petclinic`
- Revision: `233dfcd06db3fb0505c2accc106f45ef72670990` (`v7.0.3`)
- Runtime: Amazon Corretto 17.0.13
- Agent: Codex CLI 0.147.0, `gpt-5.6-sol`, `xhigh`, fast service tier
- Execution: non-interactive `danger-full-access` for both arms; Mockito instrumentation and
  PIT cannot run inside Codex's macOS `workspace-write` sandbox
- Tasks: four medium-size use cases in `tasks/`
- Repetitions: three per task and arm
- Arms: identical environment with JAIPilot absent or JAIPilot 4.0.3 available
- Order: randomized with seed `20260808`

The prompt never tells the treatment agent to invoke JAIPilot. Natural tool selection is part of the
product being tested. The baseline plugin is physically removed before its run and restored for the
treatment run. Codex CLI 0.147.0 does not merge the plugin's local stdio server into non-interactive
`exec`, so the treatment invocation explicitly registers the plugin's own `jaipilot-mcp` launcher;
the task prompt remains identical. The pinned 4.0.3 runtime is downloaded and checksum-verified before
timing starts. Each trial uses a new clone, private JAIPilot state, an identical warm Maven cache, and
the same time limit.

## Independent acceptance

The runner saves the agent patch before adding the task's hidden tests. A trial is accepted only when:

1. the hidden contract tests pass;
2. `./mvnw -B clean verify` passes on Java 17 with the hidden tests present;
3. `git diff --check` passes for the agent patch; and
4. the agent changed only production/test Java in the task's declared packages.

JAIPilot quality, coverage, mutation, and architecture results are retained as treatment evidence but
are not the acceptance oracle. Every trial retains its transcript, stderr, patch, status, timings,
changed paths, tool-use count, hidden-test output, and full-build output.

## Decision rule

Continue investing when the treatment improves accepted solutions by at least 15 percentage points
or reduces escaped hidden-test defects/reviewer corrections by at least 30%, without increasing
regressions or scope violations. Median end-to-end overhead should remain below 25%. Publish neutral
and negative trials alongside positive ones.

This pilot is intentionally small. A positive result must be repeated with Claude Code before making
a provider-neutral product claim.

The completed Codex results are in [RESULTS.md](RESULTS.md), with per-trial measurements in
[trials.csv](trials.csv). The committed record contains the runner, prompts, hidden tests, aggregate
report, and trial table. It does not contain the original 24 raw transcripts and patches, so the
study is reproducible but the historical runs are not fully re-auditable from this repository alone.

The separately labeled [JAIPilot 4.0.5 visit-scheduling demonstration](../petclinic-demo/4.0.5/README.md)
publishes its exact patch and structured measurements. It is not another comparison arm and does not
change this pilot's decision.
