# Cross-repository dependency-upgrade knowledge

This file is a public, human-readable system of record for version-upgrade work only. It catalogs
every public pull request authored by `skrcode` during the 2026-08-27 dependency-bot repair
campaign: 30 companion attempts covering 26 source upgrades (25 Dependabot and one Renovate).
Performance, cleanup, refactoring, and unrelated maintainer lessons do not belong here.

This is optional input, not product state. A maintainer-intent run reads it only when the user
explicitly supplies or names it for a dependency or version upgrade. Each record is a search lead,
not a patch recipe. Revalidate both pull requests, their exact heads, the dependency graph, the
repository's current support floor, and all maintainer feedback before using a record.

## Evidence grades

- **Accepted**: the companion merged and the source upgrade subsequently merged. This is the
  strongest transferable evidence in the campaign.
- **Maintainer-directed**: a maintainer explicitly accepted, rejected, redirected, superseded, or
  deferred the approach. Transfer the decision constraint, not necessarily the code.
- **Candidate**: the companion remains open without substantive maintainer review. Its diagnosis
  and proof can guide a search, but its solution is not established precedent.
- **Unadopted**: the companion closed without merge and without positive maintainer validation.
  Preserve the attempt and reason when public, but never present it as a successful pattern.

Automated checks establish build evidence, not maintainer preference. Self-closure establishes no
technical verdict. A merged source bot pull request does not retroactively validate a rejected
companion implementation.

## Transfer rules supported by the campaign

1. Start from the immutable failing bot head and reproduce the same focused command on the
   candidate. Recheck the bot head immediately before publication because bot branches move.
2. Prefer the incumbent dependency-update experience. Twenty-seven of the 30 companions targeted
   the bot branch, but follow explicit repository direction when a source-only fix belongs on the
   default branch or a maintainer requests a different upgrade shape.
3. Diagnose the failure class before editing: removed or relocated API, split artifact, incompatible
   runtime floor, misaligned BOM or lock, generated dependency metadata, analyzer-exposed defect,
   intentional upstream behavior change, or a real behavioral regression.
4. Preserve the repository's declared Java, Gradle, Maven, Android SDK, OSGi, serialization, and
   runtime contracts. “Newest available” is not automatically compatible.
5. Update generated locks, dependency baselines, license references, feature manifests, or runtime
   bundles only through the repository's native generator and prove a deterministic second run.
6. Search for an existing maintainer fix, planned removal, patch release, issue key, or contribution
   gate before opening code. Join, wait, or stop when higher-ranked evidence already owns the
   outcome.
7. A failing assertion after an upgrade may expose either a legitimate new dependency behavior or a
   latent product defect. Trace the producer and upstream release before changing expectations.

## Campaign index

| ID | Companion | Source upgrade | Evidence | Verified state on 2026-08-28 |
| --- | --- | --- | --- | --- |
| U-001 | [Kestra #73](https://github.com/kestra-io/plugin-email/pull/73) | [#60](https://github.com/kestra-io/plugin-email/pull/60) | Accepted | Merged; source merged |
| U-002 | [Species Lists #814](https://github.com/AtlasOfLivingAustralia/species-lists/pull/814) | [#761](https://github.com/AtlasOfLivingAustralia/species-lists/pull/761) | Candidate | Open |
| U-003 | [PIT #1497](https://github.com/hcoles/pitest/pull/1497) | [#1460](https://github.com/hcoles/pitest/pull/1460) | Accepted | Merged |
| U-004 | [Donkey Kong #51](https://github.com/mircoterenzi/donkey-kong/pull/51) | [#41](https://github.com/mircoterenzi/donkey-kong/pull/41) | Unadopted | Closed by repository actor |
| U-005 | [Donkey Kong #50](https://github.com/mircoterenzi/donkey-kong/pull/50) | [#45](https://github.com/mircoterenzi/donkey-kong/pull/45) | Unadopted | Closed by repository actor |
| U-006 | [Kestra #74](https://github.com/kestra-io/plugin-email/pull/74) | [#60](https://github.com/kestra-io/plugin-email/pull/60) | Unadopted | Self-closed; U-001 remains open |
| U-007 | [MariaDB4j #1376](https://github.com/MariaDB4j/MariaDB4j/pull/1376) | [#1370](https://github.com/MariaDB4j/MariaDB4j/pull/1370) | Unadopted | Self-closed; U-017 remains open |
| U-008 | [MeDev #43](https://github.com/MrSgemaSeny/MeDev/pull/43) | [#34](https://github.com/MrSgemaSeny/MeDev/pull/34) | Unadopted | Self-closed |
| U-009 | [Camunda #61272](https://github.com/camunda/camunda/pull/61272) | [#60956](https://github.com/camunda/camunda/pull/60956) | Maintainer-directed | Open against `main` |
| U-010 | [Maven Indexer #764](https://github.com/apache/maven-indexer/pull/764) | [#762](https://github.com/apache/maven-indexer/pull/762) | Maintainer-directed | Open against `master` |
| U-011 | [Camunda #61284](https://github.com/camunda/camunda/pull/61284) | [#60956](https://github.com/camunda/camunda/pull/60956) | Unadopted | Self-closed duplicate of U-009 |
| U-012 | [Spring Security #19605](https://github.com/spring-projects/spring-security/pull/19605) | [#19551](https://github.com/spring-projects/spring-security/pull/19551) | Candidate | Open |
| U-013 | [Commons Numbers #218](https://github.com/apache/commons-numbers/pull/218) | [#217](https://github.com/apache/commons-numbers/pull/217) | Maintainer-directed | Rejected and closed; source merged |
| U-014 | [DWH Migration Tools #1168](https://github.com/google/dwh-migration-tools/pull/1168) | [#1148](https://github.com/google/dwh-migration-tools/pull/1148) | Candidate | Open |
| U-015 | [Jenkins Allure #474](https://github.com/jenkinsci/allure-plugin/pull/474) | [#459](https://github.com/jenkinsci/allure-plugin/pull/459) | Candidate | Open |
| U-016 | [Micrometer Tracing #1534](https://github.com/micrometer-metrics/tracing/pull/1534) | [#1533](https://github.com/micrometer-metrics/tracing/pull/1533) | Maintainer-directed | Superseded and closed |
| U-017 | [MariaDB4j #1377](https://github.com/MariaDB4j/MariaDB4j/pull/1377) | [#1370](https://github.com/MariaDB4j/MariaDB4j/pull/1370) | Candidate | Open |
| U-018 | [openHAB #5806](https://github.com/openhab/openhab-core/pull/5806) | [#5803](https://github.com/openhab/openhab-core/pull/5803) | Candidate | Open |
| U-019 | [Iceberg #17850](https://github.com/apache/iceberg/pull/17850) | [#17777](https://github.com/apache/iceberg/pull/17777) | Maintainer-directed | Deferred and self-closed |
| U-020 | [Google Cloud Java #14200](https://github.com/googleapis/google-cloud-java/pull/14200) | [#14070](https://github.com/googleapis/google-cloud-java/pull/14070) | Unadopted | Self-closed |
| U-021 | [Signal Server #182](https://github.com/signalapp/Signal-Server/pull/182) | [#181](https://github.com/signalapp/Signal-Server/pull/181) | Unadopted | Closed when bot source closed |
| U-022 | [WildFly #20362](https://github.com/wildfly/wildfly/pull/20362) | [#20352](https://github.com/wildfly/wildfly/pull/20352) | Maintainer-directed | Failed contribution gate; self-closed |
| U-023 | [WildFly #20363](https://github.com/wildfly/wildfly/pull/20363) | [#20352](https://github.com/wildfly/wildfly/pull/20352) | Candidate | Corrected and open |
| U-024 | [Amplify Android #3401](https://github.com/aws-amplify/amplify-android/pull/3401) | [#3398](https://github.com/aws-amplify/amplify-android/pull/3398) | Candidate | Open |
| U-025 | [Mockito #3858](https://github.com/mockito/mockito/pull/3858) | [#3742](https://github.com/mockito/mockito/pull/3742) | Candidate | Open |
| U-026 | [Dubbo #16442](https://github.com/apache/dubbo/pull/16442) | [#16342](https://github.com/apache/dubbo/pull/16342) | Candidate | Open |
| U-027 | [Flink JDBC #222](https://github.com/apache/flink-connector-jdbc/pull/222) | [#204](https://github.com/apache/flink-connector-jdbc/pull/204) | Candidate | Open |
| U-028 | [Qpid Broker-J #433](https://github.com/apache/qpid-broker-j/pull/433) | [#426](https://github.com/apache/qpid-broker-j/pull/426) | Candidate | Open |
| U-029 | [Workflow CPS #1827](https://github.com/jenkinsci/workflow-cps-plugin/pull/1827) | [#1824](https://github.com/jenkinsci/workflow-cps-plugin/pull/1824) | Accepted | Merged and approved |
| U-030 | [Application Insights #4842](https://github.com/microsoft/ApplicationInsights-Java/pull/4842) | [#4821](https://github.com/microsoft/ApplicationInsights-Java/pull/4821) | Unadopted | Self-closed |

## U-001 — Simple Java Mail 9 removed recipient shortcuts

**Source upgrade:** Kestra [#60](https://github.com/kestra-io/plugin-email/pull/60), Simple
Java Mail 8.12.6 to 9.1.0. Its final observed head was
`251fef8b52404c9e6c21987eb389d2c3f535c21d`, merged as
`ffbb90673ce69f9b70f0074db4d88466bdcdfdd4`.

**Companion:** [#73](https://github.com/kestra-io/plugin-email/pull/73), final head
`d188c32486007699e163a519fa66bc40a49505cd`, merged as
`243d914b6d2e9b1cc3720693f49dd0b96b21b10d` into the bot branch.

**Failure and candidate:** Removed `to`, `cc`, and `bcc` builders required migration to
`withRecipients`; MIME assertions also had to follow the library's equivalent filename
serialization while retaining `Content-Disposition` proof.

**Outcome:** Accepted. A Kestra maintainer verified the removed 9.1 API directly, approved the
`withRecipients` migration, confirmed recipient and MIME behavior, found no blocking guideline,
security, or performance issue, and added the final named-error commit for missing `to`/`from`
values. The companion merged at 2026-08-28 09:07 UTC, the source upgrade merged two minutes later,
and post-merge CI passed, including all 13 reported Java tests.

**Transfer:** For removed fluent APIs, preserve recipient type, parsing, and wire-level MIME
semantics instead of mechanically renaming methods. Named validation errors may be part of the
maintainer-accepted migration even when the original failure is compilation.

## U-002 — Spring Boot 4 removed transitive and package compatibility

**Source upgrade:** Species Lists [#761](https://github.com/AtlasOfLivingAustralia/species-lists/pull/761),
Spring Boot 3.5.12 to 4.1.0, observed bot head `dc47748b44c4a93aad6e5f7d14394edf40f21d52`.

**Companion:** [#814](https://github.com/AtlasOfLivingAustralia/species-lists/pull/814), observed
head `852cc7c92ecba3b3d3b1edce53bede39b9cf81ef`, targeting the bot branch.

**Failure and candidate:** Restored an explicitly used HttpCore 4 artifact and migrated the moved
Tomcat factory and Elasticsearch script-builder APIs.

**Outcome:** Candidate; open without substantive maintainer review.

**Transfer:** A framework major can combine lost transitives and multiple unrelated API moves.
Inventory compile failures by owning dependency rather than adding a broad compatibility bundle.

## U-003 — Plexus Utils 4 split XML classes into another artifact

**Source upgrade:** PIT [#1460](https://github.com/hcoles/pitest/pull/1460), Plexus Utils 3.3.1 to
4.0.3. The source ultimately merged at `8f6aefc1df2093b2a44b02db01970046d2b3096f`.

**Companion:** [#1497](https://github.com/hcoles/pitest/pull/1497), merged head
`fa23e7f43d8aa07d8d9c6f350e324798e97676a3`, targeting the bot branch.

**Failure and candidate:** `Xpp3Dom` and related XML classes moved to
`org.codehaus.plexus:plexus-xml`; the fix added the Maven-3-compatible 3.0.2 artifact directly to
the consuming module.

**Outcome:** Accepted. The maintainer merged the companion, thanked the contributor, then merged
the source upgrade.

**Transfer:** When a major release modularizes classes, use the vendor's migration artifact and
place it only in the real consumer module. This is accepted evidence, not merely a green build.

## U-004 — Vert.x transitively moved the JUnit stack without a launcher

**Source upgrade:** Donkey Kong [#41](https://github.com/mircoterenzi/donkey-kong/pull/41), Vert.x
5.0.11 to 5.1.6, observed bot head `90d9b9fd668433c484c7fcb24d499b1a931d9b12`.

**Companion:** [#51](https://github.com/mircoterenzi/donkey-kong/pull/51), head
`0f46141015e12d149de4e246eff24330317e86a5`, targeting the bot branch.

**Failure and candidate:** Test discovery reported `OutputDirectoryCreator not available`; the
candidate declared the versionless JUnit Platform launcher so the Vert.x BOM supplied the aligned
version.

**Outcome:** Unadopted. A repository actor closed it without explanatory feedback; the source
upgrade remains open.

**Transfer:** An explicit launcher can align a BOM-managed test stack, but this attempt supplies no
maintainer precedent.

## U-005 — Jupiter changed while Gradle retained an older launcher

**Source upgrade:** Donkey Kong [#45](https://github.com/mircoterenzi/donkey-kong/pull/45), JUnit
Jupiter 5.9.1 to 5.14.4, observed bot head `1b0b26d79cf6c6efb0fbafe6518ebbac1f454b1c`.

**Companion:** [#50](https://github.com/mircoterenzi/donkey-kong/pull/50), head
`4935b5a8c936a23ac8bcf4627c6ecff9731dddb4`, targeting the bot branch.

**Failure and candidate:** The same discovery error was addressed with a matching JUnit BOM and an
explicit runtime launcher.

**Outcome:** Unadopted. A repository actor closed it without explanatory feedback; the source
upgrade remains open.

**Transfer:** Compare this with U-004 and U-017 when diagnosing JUnit discovery, but do not treat
the repeated candidate shape as acceptance.

## U-006 — Duplicate Simple Java Mail delivery

**Source upgrade:** The same Kestra source [#60](https://github.com/kestra-io/plugin-email/pull/60)
and bot head `02f6b7d94b1d4395f7f5942d98676aa33f3050d0` as U-001.

**Companion:** [#74](https://github.com/kestra-io/plugin-email/pull/74), head
`e2daac343b57f95620e3034590dee0d4f26785c3`, also targeting the bot branch.

**Failure and candidate:** It performed the same recipient migration with a narrower MIME assertion
description.

**Outcome:** Unadopted and self-closed while U-001 remained open.

**Transfer:** Search the contributor's own open work before publishing. Two technically similar
companions against one bot head fragment review and create no additional evidence.

## U-007 — First MariaDB4j JUnit-alignment attempt

**Source upgrade:** MariaDB4j [#1370](https://github.com/MariaDB4j/MariaDB4j/pull/1370), JUnit
Jupiter Engine 5.12.2 to 6.1.1, observed bot head `7c94fc27d14c54aa5a977ebbd6c8898743f76875`.

**Companion:** [#1376](https://github.com/MariaDB4j/MariaDB4j/pull/1376), head
`ef9c354ce08c7793ae7749bcda6bdd16b5afe3ea`, targeting the bot branch.

**Failure and candidate:** Imported the JUnit 6.1.1 BOM ahead of Spring Boot's older managed stack
and added the Platform launcher.

**Outcome:** Unadopted and self-closed; the independently rebuilt U-017 remains open.

**Transfer:** Preserve the duplicate history, but use only U-017 as the current candidate identity.

## U-008 — Spring Boot 4.1 required a broad application migration

**Source upgrade:** MeDev [#34](https://github.com/MrSgemaSeny/MeDev/pull/34), Spring Boot 3.3.0 to
4.1.1, observed bot head `9f3dd29e82fb1db422885e66b770ad18b0ac039c`.

**Companion:** [#43](https://github.com/MrSgemaSeny/MeDev/pull/43), head
`dd960d4d34eb587768875e4bb4eb3f87fa11118a`, targeting the bot branch.

**Failure and candidate:** Updated Gradle, moved framework APIs and test annotations, added split
Boot test/runtime modules, excluded an obsolete Spring Cloud Function transitive, and corrected a
Redis matcher.

**Outcome:** Unadopted and self-closed without human review.

**Transfer:** A large framework migration spanning toolchain, production APIs, tests, and dependency
exclusions is not a bounded precedent merely because one build passed.

## U-009 — NullAway exposed an independent source contract defect

**Source upgrade:** Camunda [#60956](https://github.com/camunda/camunda/pull/60956), Renovate update
of NullAway to 0.14.0, current observed source head `1d037d39ce818a9741f897f94e6956b3156858ea`.

**Companion:** [#61272](https://github.com/camunda/camunda/pull/61272), observed head
`6d21f5045a31cf1dbac2cc1dc1cc59e03ca9920a`, now targeting `main`.

**Failure and candidate:** Aligned nullable generic upper bounds between `CompletableActorFuture`
and `ActorFuture` without changing erased signatures.

**Outcome:** Maintainer-directed. A maintainer said the independent source fix should land on
`main`, or be cherry-picked into the Renovate branch. The PR was retargeted accordingly and remains
open.

**Transfer:** An analyzer upgrade can reveal a real latent contract mismatch whose correct delivery
is the default branch, not the bot branch. Ask when branch mechanics would create a merge commit.

## U-010 — A Jetty upgrade became a maintainer-directed dependency reduction

**Source upgrade:** Maven Indexer [#762](https://github.com/apache/maven-indexer/pull/762), Jetty
WebApp 10.0.24 to 11.0.25, observed source head `883bc0efb8e7294e5a8d52d96347ac00535d67f4`.

**Companion:** [#764](https://github.com/apache/maven-indexer/pull/764), observed head
`00c0a07d5dd3e86f9e467a8b64609a31a2b5c3ff`, targeting `master`.

**Failure and candidate:** The final candidate replaces Servlet/WebApp fixtures with Jetty 12 Core
handlers while preserving fresh reads, slow responses, and redirects.

**Outcome:** Maintainer-directed and open. The maintainer requested Jetty Core and explicitly
rejected the first interpretation that replaced Jetty with the JDK HTTP server.

**Transfer:** “Jetty Core” means Jetty's server handler API, not a JDK substitute or an artifact
literally named `jetty-core`. Follow the named architecture and sibling-repository precedent.

## U-011 — Duplicate Camunda default-branch delivery

**Source upgrade:** The same Camunda Renovate source [#60956](https://github.com/camunda/camunda/pull/60956)
as U-009, current observed head `1d037d39ce818a9741f897f94e6956b3156858ea`.

**Companion:** [#61284](https://github.com/camunda/camunda/pull/61284), head
`e97ecbbc67da8774b44004de3e614df83428f896`, targeting `main`.

**Failure and candidate:** Repeated the nullable-bound source patch on a fresh default-branch base.

**Outcome:** Unadopted and self-closed after U-009 itself was corrected to target `main`.

**Transfer:** Retarget or rebase the existing PR when GitHub permits it; do not create a second
default-branch PR for the same bytes and outcome.

## U-012 — HtmlUnit 5 relocated the cookie type

**Source upgrade:** Spring Security [#19551](https://github.com/spring-projects/spring-security/pull/19551),
Selenium 4.43.0 to 4.47.0, bot head `8cab91b485ecefa1b37dbe83a4b4839f94977db8`.

**Companion:** [#19605](https://github.com/spring-projects/spring-security/pull/19605), head
`9ed2f0c36a75c2aee5303aac527c29f6e3156798`, targeting the bot branch.

**Failure and candidate:** Selenium resolved HtmlUnit 5.4, which moved `Cookie` from
`org.htmlunit.util` to `org.htmlunit.http`; the candidate changes only the test-fixture import.

**Outcome:** Candidate; open without substantive maintainer review.

**Transfer:** For a pure package relocation, prove the returned type and invoked methods are
otherwise identical and keep the patch to the affected fixture.

## U-013 — A changed RNG exposed a deeper selection-bound bug

**Source upgrade:** Commons Numbers [#217](https://github.com/apache/commons-numbers/pull/217), RNG
1.6 to 1.7. The source later merged at `1efb412e51206af9ff7c05c6845f6aed52e93d8e`.

**Companion:** [#218](https://github.com/apache/commons-numbers/pull/218), rejected head
`9b3baa9412a5cfbd31baf9a9ad3f7fbc44388ec5`, targeting the bot branch.

**Failure and candidate:** New deterministic samples revealed incorrect selection and destructive
replacement. The candidate discarded returned bounds and recomputed a contiguous equal run.

**Outcome:** Maintainer-directed rejection. The maintainer traced the defect deeper into
`partitionKBM` and closed the companion; the source upgrade later merged without this patch.

**Transfer:** Follow invalid bounds to their producer. Do not mask a faulty selection contract by
recomputing at a caller, even when focused and full tests pass.

## U-014 — A version catalog changed while strict Gradle locks did not

**Source upgrade:** DWH Migration Tools [#1148](https://github.com/google/dwh-migration-tools/pull/1148),
gRPC 1.76.1 to 1.82.0, bot head `70a38464f95579a7e2905bd11545a4144e7d2a33`.

**Companion:** [#1168](https://github.com/google/dwh-migration-tools/pull/1168), head
`713139746721cffdabb1c5d6d33f28dca25f4659`, targeting the bot branch.

**Failure and candidate:** Two modules remained strictly locked to 1.76.1. The candidate used
Gradle's selective `io.grpc:*` lock update and verified byte-identical regeneration.

**Outcome:** Candidate; open without substantive maintainer review.

**Transfer:** Locate every consuming lockfile, use the repository's selective lock command, and
require a no-diff second generation. Do not hand-edit lock entries.

## U-015 — Jenkins plugin-parent latest major violated the Java floor

**Source upgrade:** Allure Plugin [#459](https://github.com/jenkinsci/allure-plugin/pull/459), plugin
parent 4.74 to 6.2153, bot head `cdc6978ad927c91e28db0d3d6f8c1f46fac5e2d4`.

**Companion:** [#474](https://github.com/jenkinsci/allure-plugin/pull/474), head
`f224a65fc05e53d6ba06a87472665c7c49ed6af4`, targeting the bot branch.

**Failure and candidate:** Parent 6.x requires Java 17 and Maven 3.9.6 while the plugin supports
Java 11 and uses Maven 3.9.1. The candidate selects parent 4.88, the latest compatible 4.x line.

**Outcome:** Candidate; open without substantive maintainer review.

**Transfer:** Preserve the repository's runtime floor and wrapper contract. A lower compatible
upgrade may be correct, but this candidate is not yet accepted precedent.

## U-016 — A disappeared exporter was already owned by a maintainer plan

**Source upgrade:** Micrometer Tracing [#1533](https://github.com/micrometer-metrics/tracing/pull/1533),
OpenTelemetry instrumentation BOM 2.30.0 to 2.31.1, bot head
`aed8d3d4a89039ddc8516f8185dc93a13ae2c8b9`.

**Companion:** [#1534](https://github.com/micrometer-metrics/tracing/pull/1534), head
`3e44c8f3592d22a0d5d815195f9bd2b11bb1ee23`, targeting the bot branch.

**Failure and candidate:** OpenTelemetry stopped publishing its Zipkin exporter at 1.65.0; the
candidate pinned that one module to its final 1.64.0 release.

**Outcome:** Maintainer-directed and closed. The maintainer planned deprecation/removal and
superseded the patch with [#1537](https://github.com/micrometer-metrics/tracing/pull/1537).

**Transfer:** Search roadmap and active maintainer work before pinning a discontinued artifact.
When a maintainer owns a broader removal, join or stop rather than preserve the old integration.

## U-017 — Current MariaDB4j JUnit-alignment candidate

**Source upgrade:** The same MariaDB4j source [#1370](https://github.com/MariaDB4j/MariaDB4j/pull/1370)
and bot head `7c94fc27d14c54aa5a977ebbd6c8898743f76875` as U-007.

**Companion:** [#1377](https://github.com/MariaDB4j/MariaDB4j/pull/1377), head
`50f38503383f9bf8dd50e18480e03eae01188381`, targeting the bot branch.

**Failure and candidate:** Surefire rejected Jupiter 6.1.1 mixed with Spring Boot-managed Jupiter
5.12.2 and Platform 1.12.2. The candidate aligns the full test stack with the JUnit BOM and launcher.

**Outcome:** Candidate; open without substantive maintainer review.

**Transfer:** Resolve and report every Jupiter, engine, launcher, and Platform component. Mixed test
stacks often fail before executing a test, but U-017 remains a hypothesis until reviewed.

## U-018 — Xtext raised OSGi transitive floors and feature ranges

**Source upgrade:** openHAB [#5803](https://github.com/openhab/openhab-core/pull/5803), Xtext 2.43.0
to 2.44.0, bot head `c27ec1ac64671505b42c65d6d5e861da6ccc3c17`.

**Companion:** [#5806](https://github.com/openhab/openhab-core/pull/5806), head
`ca0dfe60c4e2edfafea3d27797bc98a243966618`, targeting the bot branch.

**Failure and candidate:** The update raised ClassGraph and ASM runtime floors, invalidated feature
capability ranges, required explicit ASM bundles, and changed bnd-resolved integration bundles.

**Outcome:** Candidate; open without substantive maintainer review.

**Transfer:** OSGi upgrades require capability ranges, target-platform features, compile/runtime
BOMs, and generated run bundles to agree. Regenerate rather than guess bundle lists.

## U-019 — Parquet behavior changed, but maintainers chose to wait

**Source upgrade:** Iceberg [#17777](https://github.com/apache/iceberg/pull/17777), Parquet 1.17.1
to 1.18.0, bot head `f1ea2155263b4c045ccb3697b788885fe8c81230`.

**Companion:** [#17850](https://github.com/apache/iceberg/pull/17850), head
`2e007de0bb1074c896cd963a54711fd6c3cd1b8a`, targeting the bot branch.

**Failure and candidate:** Parquet's new IEEE-754 total-order statistics changed valid row-group
pruning expectations; the candidate updated two assertions and regenerated seven runtime baselines.

**Outcome:** Maintainer-directed deferral. A maintainer pointed to source-review direction to wait
for Parquet 1.18.1, and the companion was self-closed.

**Transfer:** Even a well-explained behavior update should stop when maintainers choose the next
patch release. Record `WAIT`, not `PROCEED`.

## U-020 — HttpClient and HttpCore were managed at incompatible versions

**Source upgrade:** Google Cloud Java [#14070](https://github.com/googleapis/google-cloud-java/pull/14070),
HttpClient 5.3.1 to 5.6.3, bot head `7479b4ecfc536a5478a9058d458bc031591cf077`.

**Companion:** [#14200](https://github.com/googleapis/google-cloud-java/pull/14200), head
`017073a51b5467e459b6093bdf15bfe4cfeb82f8`, targeting the bot branch.

**Failure and candidate:** The shared BOM still forced HttpCore 5.2.5 although HttpClient 5.6.3
requires 5.4.3, causing dependency convergence and `NoSuchMethodError` failures. The candidate
aligned the property with an independently proposed version.

**Outcome:** Unadopted and self-closed without human validation.

**Transfer:** A `NoSuchMethodError` after a dependency bump is a strong signal to inspect the
resolved companion modules, but search existing version proposals before publishing.

## U-021 — One incompatible member poisoned a grouped update

**Source upgrade:** Signal Server [#181](https://github.com/signalapp/Signal-Server/pull/181), a
ten-dependency group including Lettuce 7.6.0 to 7.7.0, bot head
`721fdd821e909ff4e2d1bb6e6e111419333187dc`.

**Companion:** [#182](https://github.com/signalapp/Signal-Server/pull/182), head
`1292a5a49014dbf69a8d7309691bc69551164c28`, targeting the bot branch.

**Failure and candidate:** Lettuce 7.7 changed disconnected-cluster failures from timeout/failover
behavior to immediate `RedisException`; the candidate retained the other nine upgrades and held
Lettuce at 7.6.

**Outcome:** Unadopted. Dependabot closed the companion when the source bot PR closed; there was no
human validation.

**Transfer:** Bisect grouped upgrades and preserve the compatible members, but bot closure and
supersession require a fresh source identity before any reuse.

## U-022 — WildFly contribution mechanics invalidated the first delivery

**Source upgrade:** WildFly [#20352](https://github.com/wildfly/wildfly/pull/20352), IronJacamar
3.0.22.Final to 3.0.23.Final, bot head `a6248e48e039f696e6613b340788f5278cb86a75`.

**Companion:** [#20362](https://github.com/wildfly/wildfly/pull/20362), head
`e5db1296c98ed2235a8625aaee48898f9ec85115`, targeting the bot branch.

**Failure and candidate:** A constructor gained `validationTimeoutSeconds`; the candidate passed
`null` in both source paths to preserve the absence of a WildFly setting.

**Outcome:** Maintainer-directed by an automated repository gate. The title, commit, and description
lacked a required `WFLY-####` issue key and JIRA link; the PR was self-closed.

**Transfer:** Contribution metadata is part of a valid dependency fix. Discover issue-key, title,
link, and sign-off requirements before publication, not after the build passes.

## U-023 — Corrected WildFly delivery with the required issue identity

**Source upgrade:** The same WildFly source [#20352](https://github.com/wildfly/wildfly/pull/20352)
and bot head `a6248e48e039f696e6613b340788f5278cb86a75` as U-022.

**Companion:** [#20363](https://github.com/wildfly/wildfly/pull/20363), head
`6fe6c1ada191e5d0ca01efece832a891609a5d3d`, targeting the bot branch.

**Failure and candidate:** Carries the same constructor migration under the required WFLY-22185
identity, JIRA link, title format, and DCO sign-off.

**Outcome:** Candidate; open without substantive maintainer review.

**Transfer:** U-023 proves the mechanical gate can be satisfied, not that the `null` behavior is
accepted. Keep process compliance and technical acceptance separate.

## U-024 — AndroidX Core exceeded the repository's Android toolchain

**Source upgrade:** Amplify Android [#3398](https://github.com/aws-amplify/amplify-android/pull/3398),
AndroidX Core 1.5.0 to 1.19.0, bot head `e74847b789c234adf0c9443059109648e524accf`.

**Companion:** [#3401](https://github.com/aws-amplify/amplify-android/pull/3401), head
`d186cf5f05b159a7dd82c6c8a89aa95028575929`, targeting the bot branch.

**Failure and candidate:** Core 1.19 requires compile SDK 37 and AGP 9.1 while the repository uses
SDK 36 and AGP 8.11. The candidate selects compatible Core 1.18 and fixes notification-permission
lint and the revocation race exposed on that line.

**Outcome:** Candidate; open without substantive maintainer review.

**Transfer:** Check AAR metadata before changing Android toolchains. The highest compatible stable
dependency may be smaller than Dependabot's proposal, but added behavioral fixes need their own
focused proof and maintainer review.

## U-025 — JUnit 6 exceeded Mockito's Java 11 contract

**Source upgrade:** Mockito [#3742](https://github.com/mockito/mockito/pull/3742), JUnit Platform
Launcher 1.13.4 to 6.0.0, bot head `eb9de45902a2102ef1f77cea2cf39b9de77f4462`.

**Companion:** [#3858](https://github.com/mockito/mockito/pull/3858), head
`9c6ac48c4633c5b2068a13e63d5053e8e30f2078`, targeting the bot branch.

**Failure and candidate:** JUnit 6 requires Java 17 while Mockito still supports Java 11. The
candidate restores Platform 1.13.4 and prevents repeat major-version proposals for that coordinate.

**Outcome:** Candidate; open without substantive maintainer review.

**Transfer:** Evaluate the library's consumer/runtime floor, not only the CI launcher JDK. Ignore
rules are policy changes and need explicit maintainer acceptance.

## U-026 — MCP SDK 0.18 replaced JSON and schema APIs

**Source upgrade:** Dubbo [#16342](https://github.com/apache/dubbo/pull/16342), MCP SDK 0.11.2 to
0.18.3, bot head `09795dcd58e0137c63ea5307b025893cd1aa0c48`.

**Companion:** [#16442](https://github.com/apache/dubbo/pull/16442), observed head
`5e0424d90c57ec2fc153b7a0a8adff8b2c612f2c`, targeting the bot branch.

**Failure and candidate:** Migrates Jackson-specific SDK calls to `McpJsonMapper`, `TypeRef`, and
the mapper-aware tool builder; also carries the already merged test-isolation fix required by the
older bot branch.

**Outcome:** Candidate; open without substantive human review. Automated coverage reported missing
changed-line coverage, which is proof debt rather than a maintainer decision.

**Transfer:** Use the new dependency's adapters before writing custom serialization. When an old bot
head predates an accepted repository fix, explicitly carry or rebase that fix and separate it in
the evidence.

## U-027 — Derby's newest release exceeded Java 17 and split XA classes

**Source upgrade:** Flink JDBC [#204](https://github.com/apache/flink-connector-jdbc/pull/204), Derby
10.14.2.0 to 10.17.1.0, observed bot head `0a233f6d8c5a026eaf5dc5115e351183ae9ec58f`.

**Companion:** [#222](https://github.com/apache/flink-connector-jdbc/pull/222), observed head
`020c545010cc1a1ef8f0c5dc8a730c7819a7a696`, targeting the bot branch.

**Failure and candidate:** Derby 10.17 bytecode requires Java 19, while Flink CI uses Java 17;
`EmbeddedXADataSource` also moved to `derbytools`. The candidate selects Java-17-compatible 10.16.1.1
and adds matching test-scoped `derbytools`.

**Outcome:** Candidate; open without substantive maintainer review.

**Transfer:** Verify class-file versions directly and inspect artifact splits. Retain the newest
release compatible with the repository's JDK only after confirming it remains a desired upgrade.

## U-028 — Qpid's generated license reference lagged the dependency

**Source upgrade:** Qpid Broker-J [#426](https://github.com/apache/qpid-broker-j/pull/426),
HttpClient 5.6.2 to 5.6.3, bot head `65df8b52871cb9bcb55ee0167f1ac03849aa4c2d`.

**Companion:** [#433](https://github.com/apache/qpid-broker-j/pull/433), head
`1c92cd40c06137d604b651784008f57a3e18dfb5`, targeting the bot branch.

**Failure and candidate:** Compilation and tests passed, but the release/license gate rejected the
checked-in assembly reference still naming 5.6.2. The candidate updates the one generated version
and documentation URL.

**Outcome:** Candidate; open without substantive maintainer review.

**Transfer:** A dependency upgrade is incomplete when checked-in release metadata remains stale.
Prove the generated/reference diff and whether LICENSE or NOTICE actually changes.

## U-029 — A parent upgrade enabled SpotBugs and exposed a real null path

**Source upgrade:** Workflow CPS [#1824](https://github.com/jenkinsci/workflow-cps-plugin/pull/1824),
Jenkins plugin parent 6.2211 to 6.2221. The source ultimately merged at
`cc2c12357949aaa15a092fe51f2a3833cb42d020`.

**Companion:** [#1827](https://github.com/jenkinsci/workflow-cps-plugin/pull/1827), approved and
merged head `597badf9d2700815ac2f0f5ed603623c83a06348`, targeting the bot branch.

**Failure and candidate:** The newer parent enabled SpotBugs 4.10.3, which found a nullable
`FlowExecutionOwner` lifecycle path. The accepted fix followed same-class precedent and used the
declared `IOException` path rather than suppressing the analyzer or allowing an accidental NPE.

**Outcome:** Accepted. A maintainer approved and merged the companion, then the source upgrade
merged.

**Transfer:** A tool upgrade can reveal a genuine behavior defect. Follow local null-handling
precedent, preserve declared failure behavior, test lifecycle/restart paths, and never suppress a
correct analyzer finding merely to make the bot PR green.

## U-030 — Partial instrumentation rollback preserved one smoke path

**Source upgrade:** Application Insights [#4821](https://github.com/microsoft/ApplicationInsights-Java/pull/4821),
an 11-dependency OpenTelemetry group update whose bot head was
`1871dc7ebbe15dca7c42e587870b8b0153cb7e7e`.

**Companion:** [#4842](https://github.com/microsoft/ApplicationInsights-Java/pull/4842), head
`a730bcd2391bea727f1989cf60f52c967fb08a34`, targeting the bot branch.

**Failure and candidate:** OpenTelemetry 2.30 Azure Core slices expected API classes absent from the
smoke application's classloader, so instrumentation was rejected and telemetry disappeared. The
candidate kept the 2.30 agent but strictly replaced two Azure slices with 2.28.1 artifacts.

**Outcome:** Unadopted and self-closed without substantive maintainer review.

**Transfer:** Selective component rollback inside a newer instrumentation distribution carries
high compatibility and maintenance risk. Treat this only as a diagnostic experiment unless the
vendor or repository validates mixed-version packaging.

## Maintaining this record

Add a stable `U-###` record for every new `skrcode` dependency-bot companion PR, including
duplicates and failed attempts. Record the source bot PR, immutable source and companion heads,
upgrade coordinates, failure signature, candidate shape, target branch, closure actor or maintainer
direction, and current evidence grade. Update state and heads when revalidating, but keep dated
rejections and supersession history. Never add private repositories, customer source, credentials,
remote logs, or unrelated Java work.
