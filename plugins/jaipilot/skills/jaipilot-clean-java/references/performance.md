# Optimize measured Java performance

Preserve functionality first. Optimize a measured workload, not code that merely looks slow.

## Use the remote performance lab

For substantial profiling or benchmarks, use `jaipilot-remote-java` and request the `large`
workspace profile. Keep the coding agent as the only planner and editor; remote hardware only runs
the commands and disposable source state that the agent selects.

1. Before editing, define one representative workload, its correctness oracle, the focused check,
   the final clean build, the primary metric, and the minimum worthwhile improvement. Do not invent
   a toy workload and present it as production evidence.
2. With repository-specific upload consent, upload the exact starting working tree and create one
   large workspace. Its initial Git commit is the immutable experiment baseline. Run the focused
   check before measuring and stop if it already fails.
3. Profile the workload there with repository-configured JMH, JFR, load tests, Micrometer, SQL
   counters, or allocation evidence. Prefer deterministic query, allocation, parse, or operation
   counts over shared-machine wall time.
4. Make one small candidate locally. Produce an exact binary patch relative to the uploaded state
   without staging, committing, resetting, cleaning, or disturbing unrelated work. Include new
   files explicitly. Record its local SHA-256.
5. For a bounded patch that safely fits the remote command limit, write it to a temporary remote
   file without echoing its contents, verify its SHA-256, run `git apply --check`, and apply it to
   the disposable checkout. Require the remote Git delta digest to match the local candidate
   digest before measuring. Reject this fast path when the starting snapshot is ambiguous, the
   patch is over 20 KiB, or exact transport cannot be proved.
6. Run the identical focused check and workload in the same workspace, JDK, resource profile, data,
   warmup, and cache boundary. Destroy the workspace after the experiment; remote edits never
   replace or synchronize the local patch.

When the workload is one stable shell command, call `process_start` with `warmup_runs: 2` and
`measurement_runs: 7`. Use the final `JAIPILOT_MEASUREMENTS_V1` record for bounded raw nanoseconds,
median, and p95. Do not use this wrapper for a command whose build, dependency download, fixture
setup, or teardown dominates the code path; use repository-native JMH or a workload-specific
counter instead.

Never put patch contents, source, signed upload URLs, or credentials in command output. If a
candidate cannot be compared in one workspace, report the limitation and do not make a precise
speed claim from separate machines.

## Establish a reproducible baseline

1. Define the real workload, data distribution, concurrency, warmup, cache state, correctness
   oracle, and resource limits. Separate cold start, steady state, and tail latency when relevant.
2. Use repository-configured JMH, load tests, profilers, JFR, Micrometer, or production-like fixtures.
   Ask before adding benchmark tooling or large fixtures.
3. Measure at least seven comparable runs on the same machine, JDK, build, inputs, and boundaries.
   Report raw values, median, p95, throughput, CPU, allocation, GC, memory, I/O, and error rate where
   applicable. Separate build and dependency-resolution time from the measured path.
4. Profile to identify the dominant hot path. Reject a hypothesis that is not visible in the
   workload or profile.

## Optimize in this order

1. Improve algorithmic complexity and eliminate repeated work or needless data movement.
2. Reduce expensive I/O round trips through safe batching, streaming, or query changes while
   preserving transaction and backpressure semantics.
3. Reduce proven allocation, copying, boxing, parsing, contention, or lock scope.
4. Add caching only with explicit key identity, invalidation, size, lifetime, consistency, and
   memory bounds.
5. Change concurrency only after the sequential contract is tested and the bottleneck is understood.

Use virtual threads only for high-concurrency blocking I/O on a supported JDK when measurements show
thread scarcity. Check synchronized or native pinning, thread-local assumptions, connection-pool and
remote-service limits, cancellation, deadlines, observability, backpressure, and shutdown. Virtual
threads do not make CPU-bound algorithms faster.

## Prove each hypothesis

1. Apply one hypothesis at a time and rerun the identical benchmark for at least seven
   observations.
2. Reject improvements within noise, regressions in p95 or resources, throughput gains that increase
   errors, and changes that move cost outside the measured boundary.
3. Run clean functional verification, configured stress or race tests, architecture and analysis,
   and inspect the final diff with the `jaipilot-review-diff` skill.

For a shared large workspace, accept a wall-time claim only when the median improves by at least
10%, p95 does not materially regress, and raw observations show a stable separation. A smaller
change may still be valuable when a deterministic count or asymptotic bound improves, but describe
that exact evidence instead of translating it into an invented latency saving.

Report the workload, environment, raw baseline and candidate runs, median and p95, profile evidence,
correctness results, resource tradeoffs, accepted and rejected hypotheses, and remaining unmeasured
production differences. Never describe an unbenchmarked refactor as faster.
