# JAIPilot

**Ship better Java with your coding agent.**

JAIPilot helps your coding agent:

- keep changes lean and reduce agent drift;
- add meaningful tests and verify behavior;
- remove proven waste and simplify complexity;
- review risky Java changes;
- modernize compatible dependencies and JDKs;
- optimize measured bottlenecks; and
- run applicable Maven and Gradle work on ready remote hardware by default; and
- profile and compare a bounded optimization on one large remote workspace.

Your coding agent remains the only planner and editor. JAIPilot supplies focused Java workflows,
compute, and repository-native evidence.

On the same Petclinic change, the result without JAIPilot had **75 tests**, **0%** coverage of the
new method, and an unused helper. With JAIPilot: **85 tests**, **100%** line and branch coverage of
the method, **7 net production lines removed**, and class complexity reduced from **25 to 24**. Both
states passed clean Maven verification.

In a separate measured Petclinic run, JAIPilot removed a JDBC N+1 path: listing six vets fell from
**8 SQL statements (`2 + N`) to exactly 2**. The added behavior and query-count tests passed 15/15,
the clean build passed 79/79, and matching local/remote diff digests proved the tested code was the
candidate kept locally. This is deterministic query-count evidence, not an invented latency claim.

On Apache Calcite's existing graph-removal JMH benchmark, JAIPilot found repeated full-edge-set
scans and produced a two-file +28/-5 candidate. Median times improved by **92.4%, 87.8%, and 87.5%**
for the 10%, 50%, and 90% removal workloads on the same large remote workspace. Matching behavior
tests passed before and after the production edit, and a fresh clean check completed **16,644 tests
with zero failures**. See the repository README for the complete median, p95, and methodology.

On OpenTelemetry Java, JAIPilot replaced large immutable-attribute linear scans with a bounded
linear-plus-binary lookup. Across five 128-attribute workloads, medians improved by **12.2–80.3%**
and p95 by **13.8–79.8%** over 21 observations per side. On Micrometer, single-value replacement
merges improved by **61.3–62.5%** at median and allocated **23.5% less**. Both candidates passed
same-command behavior checks, clean repository-native verification, and local/remote diff matching.

```text
Make my current Java changes production-ready without changing their behavior. Add missing tests,
remove unnecessary code, simplify anything overcomplicated, improve only measurable performance,
and run the project's full verification before you finish. Use JAIPilot Remote for Java execution
unless the work genuinely requires resources available only on my laptop.
```

The skills work locally without an account. Sign in and approve the repository upload when your
agent first uses remote hardware. Remote work is disposable and never commits, pushes, or publishes
code. Private networks, local services, secrets, hardware, or state remain valid reasons to run on
the laptop.

Learn more at <https://github.com/JAIPilot/jaipilot>.
