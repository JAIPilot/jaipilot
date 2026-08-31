# Optimize measured Java performance

Preserve functionality first. Optimize a measured workload, not code that merely looks slow.

## Use remote builds for reproducible measurements

For substantial profiling or benchmarks, use `jaipilot-remote-java` with the `large` build profile
when 4 CPU/8 GiB materially helps. The coding agent remains the only planner and editor; remote
hardware runs one exact committed revision and command, then terminates.

1. Before editing, define one representative workload, its correctness oracle, focused check, final
   clean build, primary metric, and minimum worthwhile improvement. Do not invent a toy workload and
   present it as production evidence.
2. With repository-specific consent, upload an archive from the exact baseline commit and run its
   focused check. Stop if it already fails.
3. Profile with repository-configured JMH, JFR, load tests, Micrometer, SQL counters, allocation
   evidence, or another justified tool. Prefer deterministic query, allocation, parse, or operation
   counts over shared-machine wall time. The current remote service returns bounded text logs, not
   raw profiler artifacts; keep raw JFR, heap-dump, or flamegraph workflows local.
4. Make one small candidate locally. Review and commit it only through the task's authorized Git
   workflow. Never upload staged, unstaged, or untracked candidate files or create a hidden commit
   merely to use remote hardware.
5. Upload the exact candidate commit and run the identical focused check and workload with the same
   CodeBuild image, profile, JDK, input, workers, warmup, and measurement settings. Baseline and
   candidate are separate fresh builds, so the benchmark command must establish its own comparable
   cache and warmup boundary rather than relying on a persistent workspace.

For repository-native JMH, add or strengthen the behavior test and benchmark before the baseline,
then keep those proof sources byte-identical for the candidate. Change only production code between
measurements. Read `primaryMetric.rawData`, not only JMH's aggregate score; require matching
benchmark names, parameters, mode, threads, forks, warmup and measurement settings, JDK, JVM
arguments, and inputs. Flatten per-fork observations, sort them, use the middle value for the median,
and compute p95 at `(n - 1) * 0.95` with linear interpolation. Compare relevant secondary metrics
such as `gc.alloc.rate.norm`, but do not turn near-zero profiler overhead into an allocation claim.
Gradle or Maven task time is setup when the measured path is JMH; do not mix it into the
operation-level result.

For a stable shell workload, make the command perform its own warmup and at least seven observations
within one build and print bounded raw values, median, and p95. Do not use whole-build wall time when
dependency download, compilation, fixture setup, or teardown dominates the path. Never put source,
signed upload URLs, credentials, or binary profiler data in command output.

## Establish a reproducible baseline

1. Define the real workload, data distribution, concurrency, warmup, cache state, correctness
   oracle, and resource limits. Separate cold start, steady state, and tail latency when relevant.
2. Use repository-configured JMH, load tests, profilers, JFR, Micrometer, or production-like fixtures.
   Ask before adding benchmark tooling or large fixtures.
3. Measure at least seven comparable observations with the same JDK, build, inputs, and boundaries.
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

1. Apply one hypothesis at a time and rerun the identical benchmark for at least seven observations.
2. Reject improvements within noise, regressions in p95 or resources, throughput gains that increase
   errors, and changes that move cost outside the measured boundary.
3. Run clean functional verification, configured stress or race tests, architecture and analysis,
   and inspect the final diff with the `jaipilot-review-diff` skill.

Accept a remote wall-time claim only when the baseline and candidate build boundaries match, median
improves by at least 10%, p95 does not materially regress, and raw observations show stable
separation. A smaller change may still be valuable when a deterministic count or asymptotic bound
improves, but describe that evidence instead of inventing a latency saving.

Report the workload, exact baseline and candidate commits, environment, raw observations, median and
p95, profile evidence, correctness results, resource tradeoffs, accepted and rejected hypotheses,
and remaining unmeasured production differences. Never describe an unbenchmarked refactor as faster.
