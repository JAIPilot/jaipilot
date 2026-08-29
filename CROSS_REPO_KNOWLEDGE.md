# Cross-repository dependency-upgrade knowledge

This file is a public, human-readable system of record for version-upgrade work only. It catalogs
every public pull request authored by `skrcode` during the dependency-bot repair campaigns that
began on 2026-08-27: 55 companion attempts covering 51 source upgrades (46 Dependabot and five
Renovate). Performance, cleanup, refactoring, and unrelated maintainer lessons do not belong here.

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
2. Prefer the incumbent dependency-update experience. Fifty-two of the 55 companions targeted
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

| ID | Companion | Source upgrade | Evidence | Verified state on 2026-08-29 |
| --- | --- | --- | --- | --- |
| U-001 | [Kestra #73](https://github.com/kestra-io/plugin-email/pull/73) | [#60](https://github.com/kestra-io/plugin-email/pull/60) | Accepted | Merged; source merged |
| U-002 | [Species Lists #814](https://github.com/AtlasOfLivingAustralia/species-lists/pull/814) | [#761](https://github.com/AtlasOfLivingAustralia/species-lists/pull/761) | Candidate | Open |
| U-003 | [PIT #1497](https://github.com/hcoles/pitest/pull/1497) | [#1460](https://github.com/hcoles/pitest/pull/1460) | Accepted | Merged |
| U-004 | [Donkey Kong #51](https://github.com/mircoterenzi/donkey-kong/pull/51) | [#41](https://github.com/mircoterenzi/donkey-kong/pull/41) | Unadopted | Closed by repository actor |
| U-005 | [Donkey Kong #50](https://github.com/mircoterenzi/donkey-kong/pull/50) | [#45](https://github.com/mircoterenzi/donkey-kong/pull/45) | Unadopted | Closed by repository actor |
| U-006 | [Kestra #74](https://github.com/kestra-io/plugin-email/pull/74) | [#60](https://github.com/kestra-io/plugin-email/pull/60) | Unadopted | Self-closed duplicate; U-001 later merged |
| U-007 | [MariaDB4j #1376](https://github.com/MariaDB4j/MariaDB4j/pull/1376) | [#1370](https://github.com/MariaDB4j/MariaDB4j/pull/1370) | Unadopted | Self-closed; U-017 remains open |
| U-008 | [MeDev #43](https://github.com/MrSgemaSeny/MeDev/pull/43) | [#34](https://github.com/MrSgemaSeny/MeDev/pull/34) | Unadopted | Self-closed |
| U-009 | [Camunda #61272](https://github.com/camunda/camunda/pull/61272) | [#60956](https://github.com/camunda/camunda/pull/60956) | Accepted | Merged into `main`; source merged |
| U-010 | [Maven Indexer #764](https://github.com/apache/maven-indexer/pull/764) | [#762](https://github.com/apache/maven-indexer/pull/762) | Maintainer-directed | Open against `master` |
| U-011 | [Camunda #61284](https://github.com/camunda/camunda/pull/61284) | [#60956](https://github.com/camunda/camunda/pull/60956) | Unadopted | Self-closed duplicate of U-009 |
| U-012 | [Spring Security #19605](https://github.com/spring-projects/spring-security/pull/19605) | [#19551](https://github.com/spring-projects/spring-security/pull/19551) | Candidate | Open |
| U-013 | [Commons Numbers #218](https://github.com/apache/commons-numbers/pull/218) | [#217](https://github.com/apache/commons-numbers/pull/217) | Maintainer-directed | Rejected and closed; source merged |
| U-014 | [DWH Migration Tools #1168](https://github.com/google/dwh-migration-tools/pull/1168) | [#1148](https://github.com/google/dwh-migration-tools/pull/1148) | Candidate | Open; CLA and OSV checks block adoption |
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
| U-031 | [CXF #3410](https://github.com/apache/cxf/pull/3410) | [#3409](https://github.com/apache/cxf/pull/3409) | Maintainer-directed | Open; maintainer integrated compatibility fix and requested the test |
| U-032 | [Karaf Decanter #731](https://github.com/apache/karaf-decanter/pull/731) | [#729](https://github.com/apache/karaf-decanter/pull/729) | Candidate | Open |
| U-033 | [C3R #1403](https://github.com/aws/c3r/pull/1403) | [#1398](https://github.com/aws/c3r/pull/1398) | Candidate | Open |
| U-034 | [OFBiz #1806](https://github.com/apache/ofbiz-framework/pull/1806) | [#1803](https://github.com/apache/ofbiz-framework/pull/1803) | Candidate | Open |
| U-035 | [Artifact Manager S3 #806](https://github.com/jenkinsci/artifact-manager-s3-plugin/pull/806) | [#805](https://github.com/jenkinsci/artifact-manager-s3-plugin/pull/805) | Maintainer-directed | Companion merged; source open with changes requested |
| U-036 | [Auditor #368](https://github.com/GrapheneOS/Auditor/pull/368) | [#360](https://github.com/GrapheneOS/Auditor/pull/360) | Maintainer-directed | Closed; maintainer will regenerate in a release batch |
| U-037 | [Legend Engine #5139](https://github.com/finos/legend-engine/pull/5139) | [#5079](https://github.com/finos/legend-engine/pull/5079) | Candidate | Open; CLA blocks adoption |
| U-038 | [Testcontainers #11999](https://github.com/testcontainers/testcontainers-java/pull/11999) | [#11879](https://github.com/testcontainers/testcontainers-java/pull/11879) | Candidate | Open; post-fix runtime proof unavailable |
| U-039 | [BookKeeper #4871](https://github.com/apache/bookkeeper/pull/4871) | [#4849](https://github.com/apache/bookkeeper/pull/4849) | Candidate | Open |
| U-040 | [MCP Server #240](https://github.com/jenkinsci/mcp-server-plugin/pull/240) | [#236](https://github.com/jenkinsci/mcp-server-plugin/pull/236) | Candidate | Open; all reported checks green |
| U-041 | [Cucumber Reports #560](https://github.com/jenkinsci/cucumber-reports-plugin/pull/560) | [#559](https://github.com/jenkinsci/cucumber-reports-plugin/pull/559) | Candidate | Open |
| U-042 | [Parquet Format #612](https://github.com/apache/parquet-format/pull/612) | [#597](https://github.com/apache/parquet-format/pull/597) | Candidate | Open |
| U-043 | [Dependency-Track #459](https://github.com/jenkinsci/dependency-track-plugin/pull/459) | [#455](https://github.com/jenkinsci/dependency-track-plugin/pull/455) | Candidate | Open; all reported checks green |
| U-044 | [Groovy Events Listener #242](https://github.com/jenkinsci/groovy-events-listener-plugin/pull/242) | [#238](https://github.com/jenkinsci/groovy-events-listener-plugin/pull/238) | Candidate | Open; all reported checks green |
| U-045 | [Stapler #797](https://github.com/jenkinsci/stapler/pull/797) | [#552](https://github.com/jenkinsci/stapler/pull/552) | Candidate | Open; all reported checks green |
| U-046 | [Display URL API #306](https://github.com/jenkinsci/display-url-api-plugin/pull/306) | [#305](https://github.com/jenkinsci/display-url-api-plugin/pull/305) | Candidate | Open; all reported checks green |
| U-047 | [Job Config History #618](https://github.com/jenkinsci/job-config-history-plugin/pull/618) | [#611](https://github.com/jenkinsci/job-config-history-plugin/pull/611) | Accepted | Approved and merged; source merged |
| U-048 | [Publish Over #93](https://github.com/jenkinsci/publish-over-plugin/pull/93) | [#87](https://github.com/jenkinsci/publish-over-plugin/pull/87) | Candidate | Open; all reported checks green |
| U-049 | [EC2 #2032](https://github.com/jenkinsci/ec2-plugin/pull/2032) | [#2029](https://github.com/jenkinsci/ec2-plugin/pull/2029) | Candidate | Open; all reported checks green |
| U-050 | [Workflow Support #430](https://github.com/jenkinsci/workflow-support-plugin/pull/430) | [#429](https://github.com/jenkinsci/workflow-support-plugin/pull/429) | Candidate | Open; all reported checks green |
| U-051 | [URLTrigger #195](https://github.com/jenkinsci/urltrigger-plugin/pull/195) | [#188](https://github.com/jenkinsci/urltrigger-plugin/pull/188) | Candidate | Open; all reported checks green |
| U-052 | [OIC Auth #791](https://github.com/jenkinsci/oic-auth-plugin/pull/791) | [#786](https://github.com/jenkinsci/oic-auth-plugin/pull/786) | Candidate | Open; all reported checks green |
| U-053 | [Pipeline Maven #1513](https://github.com/jenkinsci/pipeline-maven-plugin/pull/1513) | [#1489](https://github.com/jenkinsci/pipeline-maven-plugin/pull/1489) | Candidate | Approved; Jenkins green; one UI lane hit Maven Central 502 |
| U-054 | [LangChain4j #6218](https://github.com/langchain4j/langchain4j/pull/6218) | [#6048](https://github.com/langchain4j/langchain4j/pull/6048) | Candidate | Open |
| U-055 | [Configuration as Code #2896](https://github.com/jenkinsci/configuration-as-code-plugin/pull/2896) | [#2891](https://github.com/jenkinsci/configuration-as-code-plugin/pull/2891) | Maintainer-directed | Open; implements explicit Spotless request |

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

**Outcome:** Unadopted and self-closed before U-001 was later accepted and merged.

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
of NullAway to 0.14.0, final source head `2e4349f75d898cfbdd5af7a2d16f22a5e3fb5875`,
merged as `06181a1fae6abe38643de7874cd9c546708c1c96`.

**Companion:** [#61272](https://github.com/camunda/camunda/pull/61272), final head
`d71bebee336701f613ab9cdbd029c9f5a398cb80`, merged as
`340793fd0928d8b8ffedae6dfe91f18d76f397a0` into `main`.

**Failure and candidate:** Aligned nullable generic upper bounds between `CompletableActorFuture`
and `ActorFuture` without changing erased signatures.

**Outcome:** Accepted. A maintainer said the independent source fix should land on `main`, or be
cherry-picked into the Renovate branch. After the PR was retargeted, the maintainer requested
removal of a merge commit. The contributor force-pushed the clean one-commit head recorded above;
its status checks passed, the maintainer approved it, and it merged at 2026-08-28 14:00 UTC. The
maintainer then rebased the Renovate source after the companion and merged it at 14:50 UTC.

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
as U-009, current observed head `2e4349f75d898cfbdd5af7a2d16f22a5e3fb5875`.

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

The 2026-08-29 reconciliation also found an unsigned Google CLA gate and a failing OSV scan on the
companion. Neither was bypassed or represented as passing, so adoption remains blocked even though
the dependency-lock diagnosis is retained.

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

## U-031 — Tika 4 required shared detection and parsing context

**Source upgrade:** CXF [#3409](https://github.com/apache/cxf/pull/3409), Tika 3.3.2 to 4.0.0,
current observed source head `6b997de126665b767572409e0ccad83fe7345b6f`.

**Companion:** [#3410](https://github.com/apache/cxf/pull/3410), observed head
`bc24185b4c5725ecb1350a3a3203c6decadd217e`, targeting the bot branch.

**Failure and candidate:** Tika 4 added `ParseContext` to detector calls. The candidate initialized
or reused one extraction context and passed it through detection, parser selection, and parsing,
with a focused detector-context regression test.

**Outcome:** Maintainer-directed and open. A maintainer explained that CXF minor releases must
support both old and new dependency lines, integrated the compatibility implementation into the
source upgrade, and requested that the useful regression test be rebased or merged onto that head.

**Transfer:** A major dependency bump on a maintenance branch may require dual-version source
compatibility. Keep a validated test when a maintainer supplies the production shape, but do not
overwrite or duplicate the maintainer's implementation.

## U-032 — OSHI 7 renamed resident memory and changed JNA variants

**Source upgrade:** Karaf Decanter [#729](https://github.com/apache/karaf-decanter/pull/729), OSHI
6.12.0 to 7.6.0, observed source head `8662cb69c6b48c1c647137bc5bd7e958520d730c`.

**Companion:** [#731](https://github.com/apache/karaf-decanter/pull/731), observed head
`8aaa919d9d91b4c9160fe13d003ccbae90d5f2d9`, targeting the bot branch.

**Failure and candidate:** Replaced removed `getResidentSetSize` with `getResidentMemory` while
preserving Decanter's event key, and aligned direct JNA dependencies with OSHI's JPMS artifacts to
avoid duplicate bundle variants.

**Outcome:** Candidate; open without substantive maintainer review. Focused reactor verification,
RAT, bundle generation, tests, and the resolved JNA dependency tree passed locally and remotely.

**Transfer:** Preserve the product's external metric name when only an upstream accessor changes.
For OSGi/JPMS upgrades, verify artifact variants in the resolved graph as well as compilation.

## U-033 — Gradle 9 removed APIs used by the incumbent Shadow plugin

**Source upgrade:** C3R [#1398](https://github.com/aws/c3r/pull/1398), Gradle wrapper 8.0.1 to 9.7.1,
observed source head `b1e7b02cb27728873a71664882bb056328061d31`.

**Companion:** [#1403](https://github.com/aws/c3r/pull/1403), observed head
`8dbcfbe58e943e628992a6acb7157763fa80a6bc`, targeting the bot branch.

**Failure and candidate:** Moved the application modules to the GradleUp Shadow 8.3 compatibility
line and pinned the previous Checkstyle engine so a wrapper upgrade did not become a broad style
rewrite.

**Outcome:** Candidate; open without substantive maintainer review. The full JDK 17 Gradle build,
tests, JaCoCo verification, Checkstyle, SpotBugs, packaging, and shadow distributions passed.

**Transfer:** A build-tool major may require plugin replacement while preserving the repository's
existing analyzer semantics. Do not silently rewrite production style to satisfy a new default.

## U-034 — Mustang 2.26 crossed Tika, PDFBox, and JAXB major boundaries

**Source upgrade:** OFBiz [#1803](https://github.com/apache/ofbiz-framework/pull/1803), Mustang
2.8.0 to 2.26.0, observed source head `68af858c5ae05e0470f328eeb4826a59933a0c83`.

**Companion:** [#1806](https://github.com/apache/ofbiz-framework/pull/1806), observed head
`ae8aaa888068448b2c51a04b15d1e2772b1490db`, targeting the bot branch.

**Failure and candidate:** Aligned Tika and PDFBox, added `pdfbox-io`, migrated loading to the
PDFBox 3 `Loader` API, and used Jakarta JAXB expected by Mustang 2.26, following a related accepted
main-line migration.

**Outcome:** Candidate; open without substantive maintainer review. Compilation, Checkstyle, and
CodeNarc passed, but a complete repository verification was not claimed.

**Transfer:** A single library bump can hide several ecosystem majors. Use repository precedent for
the migration shape and keep incomplete verification explicit.

## U-035 — Tika 4 added bundled CommonMark artifacts

**Source upgrade:** Artifact Manager S3 [#805](https://github.com/jenkinsci/artifact-manager-s3-plugin/pull/805),
Tika Core 3.3.2 to 4.0.0, current source head `21b2517d23178c4ca52d6eec0892490b4ac6deb9`.

**Companion:** [#806](https://github.com/jenkinsci/artifact-manager-s3-plugin/pull/806), head
`ee864fcb37312f71dfbb73e5c389d084b19c0fcb`, merged as
`21b2517d23178c4ca52d6eec0892490b4ac6deb9` into the bot branch.

**Failure and candidate:** Strict HPI bundled-artifact validation required explicit declarations
for the three CommonMark artifacts introduced by Tika 4.

**Outcome:** Maintainer-directed. The companion merged after a clean JDK 21 verification with 40
tests, HPI validation, SpotBugs, and Enforcer. The source upgrade remains open with an empty
changes-requested review, so the campaign is not accepted end to end.

**Transfer:** When a plugin packages new transitives, update the exact bundled-artifact allowlist
only after inspecting the resolved HPI. A merged companion does not become accepted evidence until
the source upgrade also lands.

## U-036 — GrapheneOS reserves verification metadata for release batches

**Source upgrade:** Auditor [#360](https://github.com/GrapheneOS/Auditor/pull/360), KSP 2.3.6 to
2.3.11, observed source head `12712ce7814151a1b44234d9cf0b29842e441734`.

**Companion:** [#368](https://github.com/GrapheneOS/Auditor/pull/368), head
`955be4bd3e1e80b505d1703544f33702225bbba5`, targeting the bot branch.

**Failure and candidate:** Added SHA-512 verification entries for the six newly resolved KSP JAR
and module artifacts without removing or weakening existing trust.

**Outcome:** Maintainer-directed and closed. The maintainer said verification metadata is generated
through the project's standard process and most dependency updates are intentionally batched before
releases rather than handled continuously.

**Transfer:** Generated trust metadata is repository policy, not merely bytes. Stop when maintainers
reserve regeneration and dependency cadence for their release process.

## U-037 — HttpClient 5.6.3 conflicted with ClickHouse's HttpCore line

**Source upgrade:** Legend Engine [#5079](https://github.com/finos/legend-engine/pull/5079),
HttpClient 5.4.4 to 5.6.3, observed source head `814ecd7d68e41ffdc36a4a77e501bf61828f59ee`.

**Companion:** [#5139](https://github.com/finos/legend-engine/pull/5139), observed head
`abca47d71ba6ff17241bdee7676e8dd302d8ee22`, targeting the bot branch.

**Failure and candidate:** Managed `httpcore5` and `httpcore5-h2` at 5.4.3 so HttpClient and every
ClickHouse 5.3.4 request converge on the newer compatible core.

**Outcome:** Candidate; open without substantive maintainer review. Focused effective-POM and
dependency-tree evidence passed, but internal pre-reactor snapshots prevented a full build and an
unsigned CLA blocks adoption.

**Transfer:** Prove every convergence path, but do not mistake a resolved graph for a full reactor
pass. A legal contribution gate remains unavailable evidence and must never be accepted silently.

## U-038 — pgJDBC capped CockroachDB's SCRAM iteration count

**Source upgrade:** Testcontainers [#11879](https://github.com/testcontainers/testcontainers-java/pull/11879),
pgJDBC 42.7.10 to 42.7.12, observed source head `cc8bd7365b1c54a0f1e8e18085abcba57ebfecdf`.

**Companion:** [#11999](https://github.com/testcontainers/testcontainers-java/pull/11999), observed
head `cba0498e5e9421a5d605dcd46de88fe737c44b63`, targeting the bot branch.

**Failure and candidate:** CockroachDB requested 119,680 SCRAM iterations above pgJDBC's new 100,000
default cap. The candidate set that exact bounded value in both container APIs and asserted the
generated URL while preserving caller overrides.

**Outcome:** Candidate; open without substantive maintainer review. Compilation and Checkstyle
passed, but Docker was unavailable, so the required post-fix CockroachDB runtime proof is missing.

**Transfer:** Raise a new security bound only to the observed server requirement and preserve user
override. Compilation cannot substitute for the unavailable integration behavior.

## U-039 — Jetty patch versions changed BookKeeper's license manifests

**Source upgrade:** BookKeeper [#4849](https://github.com/apache/bookkeeper/pull/4849), Jetty Server
12.1.7 to 12.1.10, observed source head `8e580c3b3aac480c94d19102a0f80eed3de9d426`.

**Companion:** [#4871](https://github.com/apache/bookkeeper/pull/4871), observed head
`7ebb4e505a333cc10879d93a6e6c9f27db12e192`, targeting the bot branch.

**Failure and candidate:** Updated nine Jetty JAR entries and their source link across the four
checked-in binary LICENSE/NOTICE manifests.

**Outcome:** Candidate; open without substantive maintainer review. All 90 reactor modules,
distribution assembly, and the repository license checker passed on the documented pure-Rust
native profile.

**Transfer:** Patch upgrades can invalidate checked-in binary manifests without changing legal
text. Verify every produced distribution with the repository's own license checker.

## U-040 — JUnit 1424 exceeded the compatibility BOM's Script Security

**Source upgrade:** MCP Server [#236](https://github.com/jenkinsci/mcp-server-plugin/pull/236),
Renovate update of Jenkins JUnit to 1424, observed source head
`0e447f67322072aa08b3a0e7b66ae02541e910ee`.

**Companion:** [#240](https://github.com/jenkinsci/mcp-server-plugin/pull/240), observed head
`8f658770aea26bb3d2f383761c92269ed5a2f3a3`, targeting the Renovate branch.

**Failure and candidate:** Added Script Security 1412 only to test scope because the intentionally
old compatibility BOM supplied 1402 and prevented JUnit from loading in the test harness.

**Outcome:** Candidate; open without substantive maintainer review and all reported checks green,
including Jenkins Linux and Windows lanes. Focused and full JDK 17 verification passed 204 tests
with SpotBugs, Spotless, HPI, and license generation, and confirmed the dependency did not enter the
production HPI manifest.

**Transfer:** Preserve an intentional compatibility BOM by scoping a harness-only floor to tests
and proving it is absent from the packaged plugin.

## U-041 — SpotBugs exposed checksum corruption on partial reads

**Source upgrade:** Cucumber Reports [#559](https://github.com/jenkinsci/cucumber-reports-plugin/pull/559),
Jenkins plugin parent 6.2189 to 6.2221, observed source head
`f1dc486be856492ef99d4aad2b2e5e9279ef62a7`.

**Companion:** [#560](https://github.com/jenkinsci/cucumber-reports-plugin/pull/560), observed head
`d59d3e0fac1cfbde72e61ff09601997968ae5d30`, targeting the bot branch.

**Failure and candidate:** The newer analyzer found that checksum code ignored the byte count from
`read` and hashed stale buffer contents after a partial final read. The candidate hashed only the
returned bytes and added a 1,025-byte regression case.

**Outcome:** Candidate; open without substantive maintainer review. Focused and full JDK 25
verification, 11 tests, HPI packaging, Enforcer, and SpotBugs passed.

**Transfer:** Treat new analyzer findings as possible product defects, not upgrade noise. Exercise a
buffer size that forces a partial final read before accepting the fix.

## U-042 — Thrift compiler installation lagged the Maven dependency

**Source upgrade:** Parquet Format [#597](https://github.com/apache/parquet-format/pull/597),
libthrift 0.23.0 to 0.24.0, observed source head `3a4b3b49c55ac6a90236c6a536d1d3c4358c5407`.

**Companion:** [#612](https://github.com/apache/parquet-format/pull/612), observed head
`667176e64fcc18755302f9b41ccf1c870986677a`, targeting the bot branch.

**Failure and candidate:** Updated the workflow download and source directory from compiler 0.23.0
to 0.24.0 so the installed generator matches Maven's required version.

**Outcome:** Candidate; open without substantive maintainer review. Clean verification and
Javadocs passed across JDK 8, 11, 17, and 21 with RAT and generated-source compilation.

**Transfer:** Generated-source tool versions are part of a dependency upgrade. Search the workflow
and native tool installation paths, not only Maven or Gradle declarations.

## U-043 — A parent upgrade exposed repeated nullable access

**Source upgrade:** Dependency-Track Plugin [#455](https://github.com/jenkinsci/dependency-track-plugin/pull/455),
Jenkins plugin parent 6.2189 to 6.2221, observed source head
`dff2fce0eae84d565ecf9befe0c3208ec3b38ad8`.

**Companion:** [#459](https://github.com/jenkinsci/dependency-track-plugin/pull/459), observed head
`c8c9adbd07f410a37b21e130a1ad384d6bf69752`, targeting the bot branch.

**Failure and candidate:** Addressed the upgraded SpotBugs null-safety findings by snapshotting
nullable record values before check-and-use and rejecting a successful BOM upload without the token
required for a VEX upload, while preserving project-ID and name/version request behavior.

**Outcome:** Candidate; open without substantive maintainer review. JDK 21 and 25 verification each
passed 311 tests, SpotBugs with zero findings, JaCoCo, and all reported companion checks.

**Transfer:** Snapshot nullable values across check-and-use boundaries, and distinguish a transport
success from a response that contains the token required by the next operation.

## U-044 — Groovy 2.4 could not emit Java 21 bytecode

**Source upgrade:** Groovy Events Listener [#238](https://github.com/jenkinsci/groovy-events-listener-plugin/pull/238),
Jenkins plugin parent 6.2138 to 6.2221, observed source head
`db1eebbfbc3d97f40d8a225beaeefa50885fc8bd`.

**Companion:** [#242](https://github.com/jenkinsci/groovy-events-listener-plugin/pull/242), observed
head `cb1cdc328ff8ba8a4ec4178325189956504b7c44`, targeting the bot branch.

**Failure and candidate:** Configured GMavenPlus to emit Java 8 bytecode, the highest level supported
by the runtime Groovy 2.4 compiler, while retaining the parent-selected Java 21 target for Java
sources.

**Outcome:** Candidate; open without substantive maintainer review. JDK 21 verification passed 59
tests, SpotBugs, the CodeQL autobuild-equivalent package, class-file inspection, and all reported
companion checks.

**Transfer:** Mixed Java/Groovy builds can require distinct bytecode targets. Lower only the compiler
that cannot emit the new class version and verify the resulting class files directly.

## U-045 — Elementary 3 wrote generated resources to disk

**Source upgrade:** Stapler [#552](https://github.com/jenkinsci/stapler/pull/552), Elementary 2.0.1
to 3.0.0, observed source head `b84eabc482f7ad68b9f85680f5bc37943c8f4cee`.

**Companion:** [#797](https://github.com/jenkinsci/stapler/pull/797), observed head
`2b33da3af9734fc301d84736ea42a88dfd728823`, targeting the bot branch.

**Failure and candidate:** Configured the affected processor tests with Elementary 3's class-output
directory and read generated resources from disk instead of the removed in-memory results view.

**Outcome:** Candidate; open without substantive maintainer review. All five modules, the 15
affected processor tests, Checkstyle, SpotBugs, Spotless, and all reported companion checks passed on
JDK 25.

**Transfer:** Annotation-processor test libraries may change the storage contract without changing
generation itself. Adapt the harness output boundary and preserve processor and diagnostic assertions.

## U-046 — RestAssured 6 was unnecessary for redirect assertions

**Source upgrade:** Display URL API [#305](https://github.com/jenkinsci/display-url-api-plugin/pull/305),
RestAssured 5.5.5 to 6.0.1, observed source head `b54531edf69bff6fbf00fe56d865c86012627d0d`.

**Companion:** [#306](https://github.com/jenkinsci/display-url-api-plugin/pull/306), observed head
`9cc6ab755f9158df1283728505d7763d365a86b7`, targeting the bot branch.

**Failure and candidate:** RestAssured 6 brought a newer Groovy runtime while Jenkins core supplied
another, causing an `IndyInterface` linkage failure on JDK 25. The redirect tests only required a
status and header, so the candidate used the existing HtmlUnit harness and removed RestAssured.

**Outcome:** Candidate; open without substantive maintainer review. JDK 25 verification passed 25
tests, SpotBugs, Enforcer, and all reported companion checks.

**Transfer:** Before aligning a conflicting test dependency's runtime, ask whether the repository
already has a smaller native fixture that proves the same HTTP contract.

## U-047 — New SpotBugs accessibility checks met public plugin APIs

**Source upgrade:** Job Config History [#611](https://github.com/jenkinsci/job-config-history-plugin/pull/611),
Jenkins plugin parent 5.28 to 6.2221. Its final bot head was
`e32f5282119ac2a464c374d52a87649b668575cf`, merged as
`1f335722db567d7f51964a2e42405fa81b58e405`.

**Companion:** [#618](https://github.com/jenkinsci/job-config-history-plugin/pull/618), head
`b0005998f4a45b0fe6d78d18bfb999de68c9001b`, approved and merged as
`e32f5282119ac2a464c374d52a87649b668575cf` into the bot branch.

**Failure and candidate:** The new `IAOM_DO_NOT_INCREASE_METHOD_ACCESSIBILITY` finding covered seven
intentional public overrides used by Jelly or established Java consumers. The candidate added a
detector-specific suppression with a local justification at each boundary.

**Outcome:** Accepted. A maintainer confirmed the new SpotBugs origin, approved and merged the
companion, then merged the underlying source upgrade. Local proof covered 219 unit tests, 99
integration tests, and zero remaining SpotBugs findings.

**Transfer:** Suppression is legitimate only when a narrower visibility would break a named public
or framework contract. Use the exact detector and justify every intentional boundary separately.

## U-048 — EasyMock 5.0 class proxies failed on JDK 25

**Source upgrade:** Publish Over [#87](https://github.com/jenkinsci/publish-over-plugin/pull/87),
EasyMock 3.2 to 5.0.0, observed source head `a6dd487725a2d8e1b2e8a1e9b17f233a5daf0e8f`.

**Companion:** [#93](https://github.com/jenkinsci/publish-over-plugin/pull/93), observed head
`00ac8cd4178808c539881e51645b056f6c2453dc`, targeting the bot branch.

**Failure and candidate:** Advanced the dependency to EasyMock 5.7.0, whose current proxy
implementation avoids the package/lookup failure in 5.0.0, without changing production code.

**Outcome:** Candidate; open without substantive maintainer review. JDK 25 verification passed 142
tests, SpotBugs, Enforcer, and all reported companion checks.

**Transfer:** Dependabot's requested version may be an obsolete intermediate release. Test the
current compatible patch line before introducing source workarounds for an already-fixed runtime bug.

## U-049 — The upgraded harness submitted configuration before JavaScript settled

**Source upgrade:** EC2 Plugin [#2029](https://github.com/jenkinsci/ec2-plugin/pull/2029), Jenkins
plugin parent 5.24 to 6.2221, observed source head `7335a9ce115bc01ea2eccddc618f04997b559f3f`.

**Companion:** [#2032](https://github.com/jenkinsci/ec2-plugin/pull/2032), observed head
`e8d953e8de40ef47d578bf2fe89535a20c05c5b0`, targeting the bot branch.

**Failure and candidate:** Waited for background JavaScript before submitting the cloud configuration
form so the asynchronous instance-type selector retained the chosen value. The diff also disclosed
the upgraded parent's required formatting changes.

**Outcome:** Candidate; open without substantive maintainer review. JDK 25 verification passed 335
tests with one skip, SpotBugs, Spotless, and all reported companion checks.

**Transfer:** Synchronize with the harness's real asynchronous completion signal rather than adding
a timing sleep, and disclose mechanical formatter churn separately from the behavior fix.

## U-050 — Build completion preceded final CPS persistence

**Source upgrade:** Workflow Support [#429](https://github.com/jenkinsci/workflow-support-plugin/pull/429),
Jenkins plugin parent 6.2211 to 6.2221, observed source head
`d57ea82a75fb0d6f594531f2777c83f7a7965c60`.

**Companion:** [#430](https://github.com/jenkinsci/workflow-support-plugin/pull/430), observed head
`b6b5bcf47eea32c12146119d4d01a7530173915b`, targeting the bot branch.

**Failure and candidate:** Captured the CPS flow execution and waited for its suspension barrier
after an aborted build, preventing the test harness from deleting the build directory while the
final `build.xml` write was still active.

**Outcome:** Candidate; open without substantive maintainer review. The focused hard-kill test
passed three consecutive runs; the full JDK 25 suite, SpotBugs, Linux 21/25, and the automatically
retried Windows 17 CI lane all passed.

**Transfer:** Use a lifecycle quiescence barrier for asynchronous persistence races, not a sleep.
Separate an agent-removal infrastructure retry from a repository failure when the replacement passes.

## U-051 — Commons Net 3.13 raised Commons IO's lower bound

**Source upgrade:** URLTrigger [#188](https://github.com/jenkinsci/urltrigger-plugin/pull/188),
Commons Net 3.11.1 to 3.13.0, observed source head `cc2978652a13e814148e37a758375a4f727ed342`.

**Companion:** [#195](https://github.com/jenkinsci/urltrigger-plugin/pull/195), observed head
`3374518d0a012f0347b2d4af605393c074dd1622`, targeting the bot branch.

**Failure and candidate:** Added a narrow dependency-management override for Commons IO 2.21.0
after the Jenkins BOM, which otherwise forced 2.18.0 and failed Enforcer's upper-bound rule.

**Outcome:** Candidate; open without substantive maintainer review. JDK 25 verification passed 95
tests with six skips, Enforcer, SpotBugs, and all reported companion checks.

**Transfer:** When a BOM violates an upgraded dependency's documented transitive floor, override
only the conflicting artifact and prove both the resolved graph and Enforcer result.

## U-052 — The plugin parent moved HtmlUnit's Cookie class

**Source upgrade:** OIC Auth [#786](https://github.com/jenkinsci/oic-auth-plugin/pull/786), Jenkins
plugin parent 6.2138 to 6.2221, observed source head `acae6f1004cff6538d58a8afa7eb4632f2e65d71`.

**Companion:** [#791](https://github.com/jenkinsci/oic-auth-plugin/pull/791), observed head
`d2db47bdd1db25b496178c62fb78c8a875793b21`, targeting the bot branch.

**Failure and candidate:** Changed the test import from `org.htmlunit.util.Cookie` to
`org.htmlunit.http.Cookie` and applied the one parent-required formatting adjustment.

**Outcome:** Candidate; open without substantive maintainer review. The exact JDK 21 coverage
workflow, full JDK 25 suite with 166 tests and two skips, analyzers, packaging, and all reported
companion checks passed.

**Transfer:** For a pure package relocation, keep the diff to the affected fixture plus required
formatting and verify the same returned type and methods under both repository JDK lanes.

## U-053 — JsonUnit 6 removed the standalone JsonPath module

**Source upgrade:** Pipeline Maven
[#1489](https://github.com/jenkinsci/pipeline-maven-plugin/pull/1489), JsonUnit AssertJ 5.1.2 to
6.2.0, observed source head `c28b606374afb5f09067276005eb2f63ce157aef`.

**Companion:** [#1513](https://github.com/jenkinsci/pipeline-maven-plugin/pull/1513), observed head
`d1a59ed7d9f12b966633747fc219e3e5c4924481`, targeting the Renovate branch.

**Failure and candidate:** JsonUnit 6 moved JsonPath support into `json-unit-core` and stopped
publishing `json-unit-json-path`. The candidate removes that obsolete explicit UI-test dependency;
`json-unit-assertj:6.2.0` supplies the replacement core artifact transitively.

**Outcome:** Candidate; a repository contributor approved the exact companion head, which remains
open. Jenkins passed the Linux 25 and Windows 21 builds, tests, analyzers, packaging, and security
scan. Three of four UI lanes passed; the remaining Firefox Global Snippet Generator lane failed
before test execution because Maven Central returned HTTP 502 for `jackson-core:2.22.2`. JDK 21
verification also passed the five-module install and test compilation, the UI module's test
compilation and Enforcer rules, and resolved
`json-unit-assertj:6.2.0 -> json-unit-core:6.2.0`.

**Transfer:** When a major release folds an optional feature module into core, remove the vanished
artifact only after proving the surviving consumer supplies the replacement transitively and the
affected tests still compile.

## U-054 — HttpClient 5.6 required a matching HttpCore line

**Source upgrade:** LangChain4j
[#6048](https://github.com/langchain4j/langchain4j/pull/6048), HttpClient 5.3 to the security-fixed
5.6.3, observed source head `04ca3d399b69e6020bafa396322644d2dace40cb`.

**Companion:** [#6218](https://github.com/langchain4j/langchain4j/pull/6218), observed head
`4338235839f919a2a8d922073a2e65f7615e80d3`, targeting the Renovate branch.

**Failure and candidate:** OpenSearch Java supplied HttpCore 5.2.4 while HttpClient 5.6.3 required
5.4.3, so Maven dependency convergence failed. The candidate manages only `httpcore5` at 5.4.3
and applies the formatter's one-line whitespace correction in the same POM.

**Outcome:** Candidate; the source upgrade was member-approved at the observed head, while the
companion is open without substantive review. JDK 21 verification passed all six affected reactor
modules, Dependency Convergence, Require Upper Bound Dependencies, the Git-aware Spotless gate, and
the resolved `httpclient5:5.6.3 -> httpcore5:5.4.3` graph.

**Transfer:** When an upgraded client raises its core-library floor against an older transitive
consumer, manage only the conflicting core artifact at the client's required version and prove both
the resolved path and convergence rules.

## U-055 — The WireMock replacement needed native POM sorting

**Source upgrade:** Configuration as Code
[#2891](https://github.com/jenkinsci/configuration-as-code-plugin/pull/2891), replacement of
`com.github.tomakehurst:wiremock-jre8-standalone:2.35.2` with
`org.wiremock:wiremock-standalone:3.0.1`, observed source head
`64f69cba5f4406f0d1979c1dc2a82958d944a498`.

**Companion:** [#2896](https://github.com/jenkinsci/configuration-as-code-plugin/pull/2896),
observed head `762ff4478bf3f16f49c102389c21f5686c087a6c`, targeting the Renovate branch.

**Failure and candidate:** The replacement itself passed tests, but Spotless required the new
WireMock dependency to sort after `jsr305`. The companion contains only the repository formatter's
dependency-block move.

**Outcome:** Maintainer-directed. A contributor with repository history approved the exact source
head and explicitly requested `mvn spotless:apply`; the companion is open pending review. The
plugin's 206 tests and test harness's 194 tests passed with Enforcer, SpotBugs, Checkstyle, and
Spotless, and a final focused Spotless check passed on JDK 21.

**Transfer:** When a dependency-only bot PR passes tests but fails POM formatting, run the
repository's native formatter and preserve its exact mechanical ordering without mixing in
coordinate or source changes.

## Maintaining this record

Add a stable `U-###` record for every new `skrcode` dependency-bot companion PR, including
duplicates and failed attempts. Record the source bot PR, immutable source and companion heads,
upgrade coordinates, failure signature, candidate shape, target branch, closure actor or maintainer
direction, and current evidence grade. Update state and heads when revalidating, but keep dated
rejections and supersession history. Never add private repositories, customer source, credentials,
remote logs, or unrelated Java work.

Before selecting more upgrade work and before every campaign handoff, compare this index with all
public `skrcode` pull requests created since the campaign began. Explicitly exclude unrelated work,
then reconcile every indexed companion and source for head changes, CI, comments, reviews,
maintainer direction, merges, supersession, and closure. Update the record immediately; a green
companion, a merged companion, and an end-to-end accepted source upgrade are distinct states.
