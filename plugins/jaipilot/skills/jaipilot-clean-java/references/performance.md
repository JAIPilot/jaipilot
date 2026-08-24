# Optimize measured Java performance

Preserve functionality first. Optimize a measured workload, not code that merely looks slow.

## Establish a reproducible baseline

1. Define the real workload, data distribution, concurrency, warmup, cache state, correctness
   oracle, and resource limits. Separate cold start, steady state, and tail latency when relevant.
2. Use repository-configured JMH, load tests, profilers, JFR, Micrometer, or production-like fixtures.
   Ask before adding benchmark tooling or large fixtures.
3. Measure at least five comparable runs on the same machine, JDK, build, inputs, and boundaries.
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

1. Add or strengthen regression tests with the `jaipilot-generate-tests` skill before changing observable
   ordering, timeouts, concurrency, resource ownership, or failure behavior.
2. Apply one hypothesis at a time and rerun the identical benchmark for at least five observations.
3. Reject improvements within noise, regressions in p95 or resources, throughput gains that increase
   errors, and changes that move cost outside the measured boundary.
4. Run clean functional verification, configured stress or race tests, architecture and analysis,
   and inspect the final diff with the `jaipilot-review-diff` skill.

Report the workload, environment, raw baseline and candidate runs, median and p95, profile evidence,
correctness results, resource tradeoffs, accepted and rejected hypotheses, and remaining unmeasured
production differences. Never describe an unbenchmarked refactor as faster.
