# JAIPilot Cloud public campaign results

> This page documents the JAIPilot GitHub App cloud evaluation campaign. The skills-only JAIPilot
> plugin in this repository remains local procedural guidance with no service, telemetry, or
> background runtime. The campaign is a separate, bounded GitHub App experiment.

Last live-link verification: **August 23, 2026**.

## Audited outcome

The fixed campaign window was **2026-08-22 03:58:41 UTC through 2026-08-23 03:58:41 UTC**.

| Outcome | Count |
| --- | ---: |
| Exact pull-request heads evaluated | 95 |
| Live validated companion drafts | 60 |
| Safe no-change outcomes | 33 |
| Rejected result | 1 |
| Result withdrawn after independent audit | 1 |
| Auto-merges | 0 |
| Managed sessions, including five duplicate/experimental sessions | 100 |
| Original Anthropic list cost | $156.91 |
| Recovery-audit list cost | $5.39 |
| Total Anthropic list cost | $162.30 |

The 60 links below were rechecked as open, draft, and authored by the JAIPilot GitHub App. A
companion draft is not an upstream acceptance, merge, production benchmark, or proof of universal
correctness. Most evaluation forks currently expose no independent GitHub Check Run; the exact
commands and repository-native evidence are recorded in each draft body.

## What qualified

A result was accepted only when it:

- started from an immutable repository identity and exact pull-request head SHA;
- stayed inside changed production paths plus directly relevant tests;
- retained one small coherent improvement;
- recorded matching behavior baseline and candidate commands;
- included a passing final repository-native build and no failed verification entry;
- passed deterministic result validation outside the reasoning agent; and
- published as an idempotent draft without auto-merge.

No-change is a valid outcome. Unsafe, noisy, out-of-scope, stale, malformed, or insufficiently
proved work was rejected rather than converted into a PR.

## Representative results

### OpenTelemetry: adopted upstream

Removed redundant timeout scheduling for synchronous metric export: one schedule/cancel pair became
zero. The original contributor [adopted the change into upstream PR #8684](https://github.com/open-telemetry/opentelemetry-java/pull/8684/commits/acca5b7d5553b59a228e3322183e131cae4f53af);
32 focused tests and 161 build tasks passed. [Companion draft](https://github.com/skrcode/opentelemetry-java/pull/2).

### Micrometer: measured allocation reduction

Removed per-call HashSet reconciliation from MultiGauge.register: 14.904 to 8.482 microseconds per
operation and 18,616 to 8,000 bytes per operation in the same-host JMH workload; 75 focused tests
and both affected module builds passed. [Companion draft](https://github.com/skrcode/micrometer/pull/2).

### Maven Deploy: deterministic concurrency proof

Made the first cache initialization atomic. A 16-thread regression reproduced duplicate full-reactor
scans in the baseline 5/5 times; the candidate produced exactly one scan in 15/15 observations.
Twenty-nine tests, Checkstyle, RAT, and the clean offline build passed.
[Companion draft](https://github.com/skrcode/maven-deploy-plugin/pull/2).

### JavaParser: terminate repeated and cyclic traversal

Moved the seen check ahead of queue insertion. Shared-node expansions fell from two to one; a cyclic
fixture changed from timeout to about 20 milliseconds. The full reactor passed 2,094 tests.
[Companion draft](https://github.com/skrcode/javaparser/pull/3).

### Honest withdrawal

[Hudi draft #3](https://github.com/skrcode/hudi/pull/3) was closed after independent review found
that its claimed full build was not actually repository-native. The Trino recovery was cancelled and
published no accepted result. Neither is counted among the 60 live drafts.

## Complete live draft inventory

Every entry links to the reviewable diff, commands, evidence, and limitations. The input column is
the exact same-repository evaluation PR used by the cloud path.

| # | Repository | Validated companion draft | Evaluation input |
| ---: | --- | --- | --- |
| 1 | activemq | [Add characterization tests for ActiveMQObjectMessage.isBodyAssignableTo() deserialization-failure contract](https://github.com/skrcode/activemq/pull/2) | [#1](https://github.com/skrcode/activemq/pull/1) |
| 2 | agentscope-java | [perf(core): cache generated JSON schema per Class/Type to cut JsonSchemaUtils lock contention](https://github.com/skrcode/agentscope-java/pull/2) | [#1](https://github.com/skrcode/agentscope-java/pull/1) |
| 3 | agentscope-java | [Replace O(n) deque scans with O(log n) ordering in ReActAgent's bounded slot cache](https://github.com/skrcode/agentscope-java/pull/4) | [#3](https://github.com/skrcode/agentscope-java/pull/3) |
| 4 | agrona | [Add missing trailing newlines to the new OneToOneConcurrentArrayQueue benchmark and jcstress test files](https://github.com/skrcode/agrona/pull/2) | [#1](https://github.com/skrcode/agrona/pull/1) |
| 5 | calcite | [Add characterization tests for the CALCITE-7731 DECIMAL plain-notation bound guards](https://github.com/skrcode/calcite/pull/3) | [#1](https://github.com/skrcode/calcite/pull/1) |
| 6 | calcite | [Add tests for DECIMAL plain-notation bound guards](https://github.com/skrcode/calcite/pull/4) | [#2](https://github.com/skrcode/calcite/pull/2) |
| 7 | commons-lang | [Remove duplicated surrogate-pair start adjustment in StringUtils.mid()](https://github.com/skrcode/commons-lang/pull/2) | [#1](https://github.com/skrcode/commons-lang/pull/1) |
| 8 | dd-trace-java | [Extract duplicated snapshot-cap literal in ExceptionProbeManager](https://github.com/skrcode/dd-trace-java/pull/2) | [#1](https://github.com/skrcode/dd-trace-java/pull/1) |
| 9 | dex2jar | [Lock and optimize the super-constructor bridging feature added for skip-level invoke-special calls](https://github.com/skrcode/dex2jar/pull/2) | [#1](https://github.com/skrcode/dex2jar/pull/1) |
| 10 | dropwizard | [cleanup: remove redundant isEmpty() branch in UUIDParamConverter](https://github.com/skrcode/dropwizard/pull/2) | [#1](https://github.com/skrcode/dropwizard/pull/1) |
| 11 | DSpace | [Simplify nested bean-count branch in DSpaceServiceManager.getServiceByName](https://github.com/skrcode/DSpace/pull/2) | [#1](https://github.com/skrcode/DSpace/pull/1) |
| 12 | eclipse.jdt.core | [Skip lazy source/compliance level lookups for default-package classpath hits in determineIfOnClasspath](https://github.com/skrcode/eclipse.jdt.core/pull/2) | [#1](https://github.com/skrcode/eclipse.jdt.core/pull/1) |
| 13 | flyway | [Clean up leftover formatting artifacts from the S3-checksum-metadata change](https://github.com/skrcode/flyway/pull/2) | [#1](https://github.com/skrcode/flyway/pull/1) |
| 14 | gctoolkit | [Avoid ArrayList<Integer> boxing when collecting ParNew promotion-failure sizes](https://github.com/skrcode/gctoolkit/pull/2) | [#1](https://github.com/skrcode/gctoolkit/pull/1) |
| 15 | groovy | [Remove unused ClassHelper imports from new STC JMH benchmarks](https://github.com/skrcode/groovy/pull/3) | [#2](https://github.com/skrcode/groovy/pull/2) |
| 16 | grpc-java | [okhttp: avoid unnecessary Header allocation when indexing already-lowercase names in Hpack.Writer](https://github.com/skrcode/grpc-java/pull/2) | [#1](https://github.com/skrcode/grpc-java/pull/1) |
| 17 | gson | [Add focused tests locking the accessibility-check caching contract in ReflectiveTypeAdapterFactory](https://github.com/skrcode/gson/pull/2) | [#1](https://github.com/skrcode/gson/pull/1) |
| 18 | h2database | [Add characterization test for PageReference contract (test-only, no production change)](https://github.com/skrcode/h2database/pull/2) | [#1](https://github.com/skrcode/h2database/pull/1) |
| 19 | IPED | [Lock and clean up the new iOS Telegram key-range query added by IPED#2955](https://github.com/skrcode/IPED/pull/2) | [#1](https://github.com/skrcode/IPED/pull/1) |
| 20 | jabref | [Add characterization tests for jump-to-field and field-focus scroll behavior](https://github.com/skrcode/jabref/pull/2) | [#1](https://github.com/skrcode/jabref/pull/1) |
| 21 | jackson-databind | [Consolidate null-provider bookkeeping and drop redundant deserialize() overrides in SettableAnyProperty](https://github.com/skrcode/jackson-databind/pull/2) | [#1](https://github.com/skrcode/jackson-databind/pull/1) |
| 22 | jackson-dataformats-binary | [Lock and characterize Avro VarHandle float/double decoding behavior](https://github.com/skrcode/jackson-dataformats-binary/pull/2) | [#1](https://github.com/skrcode/jackson-dataformats-binary/pull/1) |
| 23 | jackson-dataformats-text | [toml: avoid second char[] allocation when stripping underscores from integer literals](https://github.com/skrcode/jackson-dataformats-text/pull/2) | [#1](https://github.com/skrcode/jackson-dataformats-text/pull/1) |
| 24 | jackson-datatype-hibernate | [Fix Hibernate7ProxySerializer dynamic-serializer cache corrupting @JsonUnwrapped proxies after first hit](https://github.com/skrcode/jackson-datatype-hibernate/pull/2) | [#1](https://github.com/skrcode/jackson-datatype-hibernate/pull/1) |
| 25 | jasperreports | [Add focused characterization tests for the Swing EDT markup-processing fix](https://github.com/skrcode/jasperreports/pull/2) | [#1](https://github.com/skrcode/jasperreports/pull/1) |
| 26 | javaparser | [Fix redundant re-queueing / cyclic-graph hang in breadth-first ancestor traversal](https://github.com/skrcode/javaparser/pull/3) | [#1](https://github.com/skrcode/javaparser/pull/1) |
| 27 | JCTools | [fix: restore Allman brace style in BaseMpscLinkedArrayQueue offer() (spotless)](https://github.com/skrcode/JCTools/pull/2) | [#1](https://github.com/skrcode/JCTools/pull/1) |
| 28 | jenkins | [Fix indentation and restore diamond operator in RunWithSCM.getCulprits()](https://github.com/skrcode/jenkins/pull/4) | [#2](https://github.com/skrcode/jenkins/pull/2) |
| 29 | jenkins | [Simplify CLIAction's write-once locale cache to an unmodifiable HashMap](https://github.com/skrcode/jenkins/pull/5) | [#3](https://github.com/skrcode/jenkins/pull/3) |
| 30 | jvector | [Consolidate NodeRecordTask and test ordinal holes](https://github.com/skrcode/jvector/pull/2) | [#1](https://github.com/skrcode/jvector/pull/1) |
| 31 | kafka | [MINOR: Bound the unbounded static KafkaPrincipal cache in StandardAcl](https://github.com/skrcode/kafka/pull/3) | [#2](https://github.com/skrcode/kafka/pull/2) |
| 32 | kora | [Consolidate duplicate CircularDependencyException construction in GraphBuilder.checkCycle](https://github.com/skrcode/kora/pull/2) | [#1](https://github.com/skrcode/kora/pull/1) |
| 33 | lucene | [Add characterization tests for NeighborArray#addAndEnsureDiversity bulk-score diversity check](https://github.com/skrcode/lucene/pull/2) | [#1](https://github.com/skrcode/lucene/pull/1) |
| 34 | maven-deploy-plugin | [Make projectsWithDeployExecution cache initialization atomic under concurrent first access](https://github.com/skrcode/maven-deploy-plugin/pull/2) | [#1](https://github.com/skrcode/maven-deploy-plugin/pull/1) |
| 35 | maven-install-plugin | [Fix concurrent double-scan race in InstallMojo's projectsUsingPlugin cache](https://github.com/skrcode/maven-install-plugin/pull/2) | [#1](https://github.com/skrcode/maven-install-plugin/pull/1) |
| 36 | micrometer | [Avoid per-call HashSet allocation in MultiGauge.register()](https://github.com/skrcode/micrometer/pull/2) | [#1](https://github.com/skrcode/micrometer/pull/1) |
| 37 | occurrent | [Consolidate duplicated resolveHandlerTarget rationale comment in SubscriptionAnnotationRegistrar](https://github.com/skrcode/occurrent/pull/2) | [#1](https://github.com/skrcode/occurrent/pull/1) |
| 38 | opentelemetry-java | [Skip scheduling a redundant timeout task for synchronously-completed metric exports](https://github.com/skrcode/opentelemetry-java/pull/2) | [#1](https://github.com/skrcode/opentelemetry-java/pull/1) |
| 39 | pinot | [Remove redundant @SuppressWarnings and lock behavior of the new single-thread combine fast path](https://github.com/skrcode/pinot/pull/2) | [#1](https://github.com/skrcode/pinot/pull/1) |
| 40 | portfolio | [Add regression coverage for the AMFI India 8-field NAV parser](https://github.com/skrcode/portfolio/pull/2) | [#1](https://github.com/skrcode/portfolio/pull/1) |
| 41 | powsybl-core | [Remove unneeded read-side buffering in NetworkSerDe.copy](https://github.com/skrcode/powsybl-core/pull/2) | [#1](https://github.com/skrcode/powsybl-core/pull/1) |
| 42 | querydsl | [Fix zero-hash memoization gap in ExpressionBase.hashCode()](https://github.com/skrcode/querydsl/pull/2) | [#1](https://github.com/skrcode/querydsl/pull/1) |
| 43 | rainbowgum | [Simplify FileChannelOutput's closed-after-close guard to reuse FileChannel.isOpen()](https://github.com/skrcode/rainbowgum/pull/2) | [#1](https://github.com/skrcode/rainbowgum/pull/1) |
| 44 | reactor-core | [Remove dead Queue parameter left by groupBy cancellation-signal-loss fix](https://github.com/skrcode/reactor-core/pull/2) | [#1](https://github.com/skrcode/reactor-core/pull/1) |
| 45 | rocketmq | [Fix missing FAQUrl import and duplicate-registration regressions from registerConsumer's new throw contract](https://github.com/skrcode/rocketmq/pull/2) | [#1](https://github.com/skrcode/rocketmq/pull/1) |
| 46 | shardingsphere | [Gate range-operator BINARY check behind the equality branch in ConditionValueCompareOperatorGenerator](https://github.com/skrcode/shardingsphere/pull/2) | [#1](https://github.com/skrcode/shardingsphere/pull/1) |
| 47 | spotbugs | [Remove dead pc+5 enumSwitch tracking superseded by markCurrentSwitchAsDynamic in SwitchHandler](https://github.com/skrcode/spotbugs/pull/2) | [#1](https://github.com/skrcode/spotbugs/pull/1) |
| 48 | spring-framework | [Add spring-core regression test for gh-37159 SubscriberInputStream interrupt fix](https://github.com/skrcode/spring-framework/pull/2) | [#1](https://github.com/skrcode/spring-framework/pull/1) |
| 49 | spring-framework | [Consolidate duplicated read() error handling and add spring-core interrupt test for SubscriberInputStream](https://github.com/skrcode/spring-framework/pull/4) | [#3](https://github.com/skrcode/spring-framework/pull/3) |
| 50 | spring-framework-petclinic | [Simplify Owner.hasPetsWithIds to a single-pass Set lookup](https://github.com/skrcode/spring-framework-petclinic/pull/20) | [#19](https://github.com/skrcode/spring-framework-petclinic/pull/19) |
| 51 | spring-framework-petclinic | [Clean up Owner.containsEveryPetName: remove dead helper and redundant comment, add characterization tests](https://github.com/skrcode/spring-framework-petclinic/pull/25) | [#21](https://github.com/skrcode/spring-framework-petclinic/pull/21) |
| 52 | springdoc-openapi | [Fix mixed tab/space indentation introduced in PR #1's container-validation-annotation-leak fix](https://github.com/skrcode/springdoc-openapi/pull/2) | [#1](https://github.com/skrcode/springdoc-openapi/pull/1) |
| 53 | storm | [Modernize scheduler defaults and lock rejection behavior](https://github.com/skrcode/storm/pull/2) | [#1](https://github.com/skrcode/storm/pull/1) |
| 54 | swagger-core | [Lock OAS 3.1 type deserializer behavior and drop a dead null check](https://github.com/skrcode/swagger-core/pull/2) | [#1](https://github.com/skrcode/swagger-core/pull/1) |
| 55 | Terasology | [perf(rendering): reuse a scratch buffer for ChunkMeshWorker.update()'s per-frame dirty-chunk list](https://github.com/skrcode/Terasology/pull/2) | [#1](https://github.com/skrcode/Terasology/pull/1) |
| 56 | testcontainers-java | [Add characterization tests for Selenium address fail-fast behavior](https://github.com/skrcode/testcontainers-java/pull/2) | [#1](https://github.com/skrcode/testcontainers-java/pull/1) |
| 57 | Velocity | [Add characterization tests for LegacyPingDecoder, HandshakePacket, and ClientPlaySessionHandler plugin-message routing](https://github.com/skrcode/Velocity/pull/2) | [#1](https://github.com/skrcode/Velocity/pull/1) |
| 58 | vert.x | [fix: avoid NPE resetting metrics for a not-yet-begun pipelined HTTP/1 request on connection close](https://github.com/skrcode/vert.x/pull/2) | [#1](https://github.com/skrcode/vert.x/pull/1) |
| 59 | wildfly | [Add characterization tests for ContextNames optional-injection lookup-failure handling](https://github.com/skrcode/wildfly/pull/2) | [#1](https://github.com/skrcode/wildfly/pull/1) |
| 60 | wiremock | [Remove dead multipart-related branch left duplicate by null-part-name fix](https://github.com/skrcode/wiremock/pull/2) | [#1](https://github.com/skrcode/wiremock/pull/1) |

## Interpretation and limitations

- Timed results are sandbox or test-fixture results unless the draft explicitly reproduces the
  production workload.
- Deterministic operation, allocation, query, or concurrency counts are preferred over noisy wall
  time when available.
- A passing repository build is evidence for the tested boundary, not a correctness guarantee.
- Drafts remain in the skrcode forks until upstream authors adopt, cherry-pick, or request a
  follow-up.
- Public repositories and unrestricted sandbox egress were campaign constraints; do not infer
  private-repository, regulated-data, or Zero Data Retention support.
- Money amounts above are Anthropic list-cost minor units converted to USD; estimated sandbox
  runtime cost is separate and was not charged to evaluated repositories.
