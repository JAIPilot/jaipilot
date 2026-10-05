# Recorded runs and verification results

These are individual measured runs. Runtime and achievable coverage vary by project.

## Watch it run on Apache Kafka

<p align="center">
  <img src="../assets/kafka-cli-demo.gif" alt="Actual macOS Terminal recording of JAIPilot improving Apache Kafka's Deadline and LockUtils tests, from 34.3% to 97.1% selected line coverage" width="900">
</p>

**One command. Two Kafka classes. 34.3% → 97.1% line coverage in 1 minute 53 seconds.** The run added 13 tests covering behavior, equality, lock handling, and exception paths. All **949 tests in the `server-common` module** passed; production code was unchanged.

Recorded directly from macOS Terminal with JAIPilot CLI. The GIF shortens idle waits.

<details>
<summary>Run the same workflow</summary>

After [installing](../README.md#install) and signing in, run inside an Apache Kafka checkout:

```bash
jaipilot run improve_coverage \
  --class org.apache.kafka.server.util.Deadline \
  --class org.apache.kafka.server.util.LockUtils
```

This recording used a development build, [Kafka commit `adf8c4c`](https://github.com/apache/kafka/commit/adf8c4cebdab7e31267be7ce5430d7c6b13701cf), JDK 25, and a warm Gradle dependency cache. Coverage measures the two selected classes (12/35 → 34/35 lines). A fresh module test run, JaCoCo report, Checkstyle, and Spotless independently confirmed the result. Runtime and coverage vary by project.

</details>

## Watch 100% coverage on Spring Petclinic (Experimental)

<p align="center">
  <img src="../assets/petclinic-coverage-100-demo.gif" alt="Actual macOS Terminal recording of JAIPilot's experimental coverage_100 workflow on the entire official Spring Framework Petclinic project, from 85.7% to 100% line coverage in 3 minutes 21 seconds" width="900">
</p>

**One command. The entire Petclinic project. 85.7% → 100% line coverage in 3 minutes 21 seconds.** The experimental `coverage_100` outcome targets 100% line coverage to make code agent ready. This run added **36 tests**, and all **111 tests** passed. Only test files changed.

Recorded directly from macOS Terminal with the released JAIPilot CLI and production backend. The GIF shortens idle waits; the reported runtime is the full job duration.

<details>
<summary>Run the same workflow</summary>

After [installing](../README.md#install) and signing in, use the official Spring Framework Petclinic repository:

```bash
git clone https://github.com/spring-petclinic/spring-framework-petclinic.git
cd spring-framework-petclinic
jaipilot run coverage_100 --all
```

This recording used CLI **1.0.2**, official [Petclinic commit `2d75ede`](https://github.com/spring-petclinic/spring-framework-petclinic/commit/2d75ede6e3a01c4b227e3e05001e6c53a48990bb), JDK 17, and a warm Maven dependency cache. Coverage measures all production code in the project's existing JaCoCo report: **449/524 → 524/524 lines** across 36 executable classes. Branch coverage reached **97.4% (111/114)**. A separate `./mvnw clean verify` build independently confirmed the coverage, all 111 passing tests, and successful WAR packaging. Production code, existing tests, and build configuration were preserved.

`coverage_100` is experimental and prompt based. Runtime and achievable coverage vary by project; 100% line coverage does not guarantee correctness.

</details>

## Verified on Petclinic

| Selected classes | Time | Line / branch coverage | Full suite |
| --- | --- | --- | --- |
| 1 | 62 seconds | 100% / 100% | 83 passing tests |
| 2 | 67 seconds | 100% / 100% | 85 passing tests |
| 5 | 126 seconds | 100% / 100% | 95 passing tests |

Each run began from the same clean Petclinic commit using Java 17 and a warm Maven dependency cache. Fresh clean builds independently confirmed the results. Only test files changed. Runtime and coverage vary by project; quantifying a 5× speedup requires a controlled sequential comparison.
