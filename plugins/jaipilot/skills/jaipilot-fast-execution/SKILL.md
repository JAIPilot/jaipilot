---
name: jaipilot-fast-execution
description: Reduce wall time for substantial Java repository builds, tests, analyzers, and benchmarks through resource-aware batching and safe native parallelism. Use for slow Maven or Gradle work, CI-parity runs, large test suites, or requests to execute Java verification faster without weakening it.
---

# Run substantial Java commands efficiently

Minimize end-to-end wall time, not merely one command's displayed duration. Preserve the controlling
task's exact behavior, coverage, measurement, and final-verification requirements. Never skip work,
weaken a gate, raise a timeout, change test semantics, or edit repository configuration just to
make a run appear faster.

## Size work to the real machine

1. Read repository instructions, wrappers, build configuration, modules, CI commands, required
   services, configured test forks, and effective Java version.
2. Determine usable CPU and memory from the execution boundary. On Linux, inspect
   `getconf _NPROCESSORS_ONLN` and cgroup v2 `cpu.max` and `memory.max` when present; a container's
   quota matters more than host totals.
3. Choose one bounded worker count `N`: at least one, no greater than usable CPUs, and lower when
   build JVMs, test forks, services, or memory make saturation unsafe. Record `N` and non-secret
   resource evidence.
4. Classify substantial work as CPU-, memory-, I/O-, network-, or shared-state-bound. More workers
   are not faster when they create contention, throttling, or nondeterminism.

## Batch without corrupting evidence

1. Prefer one targeted `rg` traversal, one Git query with all pathspecs, batched reads, and one
   script invocation over repeated per-file subprocesses.
2. Run independent read-only discovery or isolated scripts concurrently up to `N`. Keep output
   attributable and propagate every exit status.
3. Prefer native parallelism in the compiler, analyzer, test runner, or build graph over multiple
   top-level builds.
4. Give concurrent application or tool runs separate temporary directories, ports, databases, and
   outputs. Do not invent concurrency around global state, ordered tests, rate-limited services, or
   a shared database.
5. Never run concurrent Git writers, dependency resolvers, or Maven/Gradle processes against the
   same checkout and output tree. Never overlap performance measurements; contention invalidates
   comparison.

## Spend full builds where they prove something

1. Identify the normal clean verification and the smallest focused command. Run the required clean
   baseline once, retain safe dependency and wrapper caches, and iterate with the affected module,
   class, or task.
2. Keep comparable performance commands, profiles, environment, worker flags, and workload
   selection identical.
3. Run the final repository-native clean verification once after the diff stabilizes. Confirm the
   intended tests and analyzers executed.
4. Time material commands and report wall time, concurrency, and outcome. Separate dependency
   resolution, compilation, tests, analysis, and workload measurement when possible.

## Maven and Gradle

- Prefer `./mvnw`. Use reactor `-T N` only for thread-safe lifecycle work; a non-thread-safe-plugin
  warning or concurrency-only failure requires the serial command for proof. During iteration, use
  `-pl <module> -am` and focused Surefire/Failsafe selection when repository conventions allow it.
- Prefer `./gradlew`. Use `--parallel --max-workers=N` only when the project graph and repository
  guidance permit it. Iterate with affected tasks such as `:module:test --tests <class>`.
- Reuse populated caches. Do not purge dependencies, force snapshot updates, inject test forks,
  enable configuration or remote caches, or add build properties without repository evidence and
  user approval.

If acceleration plausibly causes a failure, run the repository-native serial equivalent once. If
serial passes, stop using the unsafe option and report the fallback; if serial also fails, preserve
the real failure. Never retry blindly or kill unrelated processes.

Use the `jaipilot-remote-java` skill when an exact committed GitHub revision materially benefits from remote
hardware. Keep local execution when the requested proof includes dirty files, private network or
artifact access, unavailable secrets, or machine-specific services.

## Report

Return the execution boundary, resource evidence, `N`, batching and native-parallelism choices,
exact commands, material durations, outcomes, serial fallbacks, cache assumptions, and any proof
that remained unavailable. Treat workflow acceleration and product performance as separate claims.
