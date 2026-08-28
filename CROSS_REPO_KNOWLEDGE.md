# Cross-repository maintenance knowledge

This is a public, human-readable system of record for Java maintenance knowledge that has crossed
repository or contribution boundaries. It is optional input, not product state. Read it only when a
user explicitly supplies or names it for a maintainer-intent run.

The records are search leads. They do not override the current repository, its instructions, an
explicit maintainer direction, or a newer source state. Revalidate every relevant link, revision,
version, constraint, and outcome before using a record in a decision. Transfer a hypothesis and its
proof requirements, never a patch blindly.

Only public evidence belongs here. Do not add private repository information, customer source,
credentials, remote logs, personal data beyond a public GitHub identity, or conclusions that cannot
be traced to a public source. Separate observed facts from inference and preserve superseded or
rejected approaches because they prevent repeated mistakes.

## Record format

Each record contains:

- **Status**: the current public outcome, with open work described as open.
- **Last verified**: the UTC date on which the linked state was checked.
- **Source identities**: exact repositories, pull requests, and immutable revisions.
- **Fingerprint**: dependency, symbol, failure mode, or contribution situation worth matching.
- **Transferable knowledge**: the bounded hypothesis supported by the sources.
- **Transfer checks**: constraints that must match before the hypothesis is useful.
- **Do not infer**: conclusions the evidence does not support.
- **Outcome history**: public events, including corrections and rejected approaches.

When adding a record, use the next stable `K-###` identifier. Update an existing record when its
public outcome changes; do not create a second record for the same cases. Keep dated outcome events
so the earlier state remains visible.

## K-001 — Parallel first-fill races in Maven plugin-context caches

**Status:** Replicated across two sibling repositories and incorporated into both currently open
pull requests; neither pull request is recorded as merged.

**Last verified:** 2026-08-28 UTC.

**Source identities:**

- Apache Maven Install Plugin [#427](https://github.com/apache/maven-install-plugin/pull/427):
  baseline `05e6221587f26c87282769820e9d9088e2ff1301`; current observed head
  `956abb74405afc653f7fd5e371c632a84dbba9b4`.
- Apache Maven Deploy Plugin [#684](https://github.com/apache/maven-deploy-plugin/pull/684):
  baseline `b6f2f0f3604c97af00b9d539fd647bafd237abbf`; current observed head
  `a7f2319cb57ebde6bde74f7a4a7edd932c8baf32`.

**Fingerprint:** A performance optimization stores an invariant full-reactor scan in a shared
plugin-context map using a compound `get` / null-check / compute / `put`. Maven parallel builds can
let several module mojos observe the empty cache and repeat the expensive scan before publication.

**Transferable knowledge:** When adding a shared lazy cache to per-module Maven plugin execution,
test the first fill under coordinated parallel callers. Preserve filtering, encounter order,
empty-reactor behavior, and reuse. Choose atomic initialization only after establishing the map's
actual API and runtime contract.

**Transfer checks:** Confirm that the computed value is invariant for the session, the context is
shared across module executions, parallel execution is supported, and publication does not recurse
through the same cache. Inspect both the declared `Map` contract and the runtime implementation.

**Do not infer:** Do not cast a generic `Map` to `ConcurrentMap` merely because one implementation is
currently concurrent. Do not claim a particular initialization primitive is repository policy. The
author first used synchronization, then chose `computeIfAbsent` after reasoning about Maven's runtime
`ConcurrentHashMap`; that choice must be revalidated elsewhere.

**Outcome history:**

- 2026-08-23: JAIPilot published an
  [Install Plugin race reproduction](https://github.com/apache/maven-install-plugin/pull/427#issuecomment-5384083509)
  and a separate
  [Deploy Plugin reproduction](https://github.com/apache/maven-deploy-plugin/pull/684#issuecomment-5384279097).
- 2026-08-27: the author explicitly
  [credited the reproduced Install race](https://github.com/apache/maven-install-plugin/pull/427#issuecomment-5445668844)
  and [applied the same fix to Deploy](https://github.com/apache/maven-deploy-plugin/pull/684#issuecomment-5445669138).
- 2026-08-28: both open branches used `computeIfAbsent`; their observed Linux, Windows, and macOS
  verification matrices passed on the recorded heads.

## K-002 — Jetty 12 Core handlers remove the Servlet layer

**Status:** A sibling Apache Maven implementation establishes the API precedent. Maven Indexer
[#764](https://github.com/apache/maven-indexer/pull/764) follows it at current observed head
`00c0a07d5dd3e86f9e467a8b64609a31a2b5c3ff` and is awaiting maintainer review.

**Last verified:** 2026-08-28 UTC.

**Source identities:**

- Apache Maven `master` at `d861f5983bb683dc0779c84519c4b9245bc6fd0b`, including its
  [Jetty Core test server](https://github.com/apache/maven/blob/d861f5983bb683dc0779c84519c4b9245bc6fd0b/its/core-it-suite/src/test/java/org/apache/maven/it/HttpServer.java)
  and [Jetty 12.1.12 dependency](https://github.com/apache/maven/blob/d861f5983bb683dc0779c84519c4b9245bc6fd0b/its/core-it-suite/pom.xml).
- Apache Maven Indexer [#764](https://github.com/apache/maven-indexer/pull/764), based on
  `55c8249b763e4ffbc82a211bbacb381db6a1b86c` at the observed decision point.

**Fingerprint:** A Jetty-backed test fixture depends on `WebAppContext`, Servlet request/response
types, sessions, security, or `jetty-webapp`, while its real needs are bounded HTTP request handling,
static bytes, redirects, or controlled timing.

**Transferable knowledge:** In Jetty 12, Core means the `org.eclipse.jetty.server` handler API:
`Handler.Abstract`, `Request`, `Response`, `Callback`, and `Content`. The artifact is
`org.eclipse.jetty:jetty-server`; there is no required artifact literally named `jetty-core`. A
direct handler can avoid Servlet and web-application dependencies while remaining inside Jetty.

**Transfer checks:** Verify the repository's build JDK, bytecode enforcement, SLF4J compatibility,
exact fixture behavior, and accepted Jetty version. Re-read mutable files for every request when a
test replaces repository content. Preserve redirect status, slow-response behavior, path handling,
and callback completion.

**Do not infer:** “Reduce Servlet dependencies” does not mean “remove Jetty.” A passing replacement
implemented with the JDK HTTP server does not satisfy an explicit request for Jetty Core. The Maven
Indexer candidate is not recorded as accepted or merged until its public state says so.

**Outcome history:**

- 2026-08-27: a Maven Indexer maintainer explicitly
  [requested Jetty Core](https://github.com/apache/maven-indexer/pull/764#issuecomment-5439047001).
- 2026-08-27: the request was incorrectly interpreted as replacing Jetty with the JDK HTTP server;
  the maintainer [rejected that interpretation](https://github.com/apache/maven-indexer/pull/764#issuecomment-5444756958).
- 2026-08-28: the pull request was updated to the servlet-free Jetty 12 handler API already used by
  Apache Maven; maintainer acceptance remains unknown.

## K-003 — Fold a small follow-up into the active source branch

**Status:** The original contributor incorporated the improvement into the active source pull
request. The companion pull request was closed as superseded, not merged independently.

**Last verified:** 2026-08-28 UTC.

**Source identities:**

- MovingBlocks/Terasology source [#5367](https://github.com/MovingBlocks/Terasology/pull/5367),
  current observed head `3786379560e4e814be01e9a3cb9ec9e48be8518b`.
- JAIPilot companion [#5391](https://github.com/MovingBlocks/Terasology/pull/5391), head
  `b1db1ab3ac8b8862d9cc86a374f84dcc8863ebb0`, closed without merge.

**Fingerprint:** A focused optimization improves an active contributor's unmerged performance pull
request, and maintaining a second long-lived companion would split one coherent outcome.

**Transferable knowledge:** When the active contributor owns the larger change, a measured,
independent follow-up may be best delivered onto that source branch and then folded into the
original pull request. Adoption into the original branch is a successful outcome even when the
companion is closed without merge.

**Transfer checks:** Confirm that the contributor controls the source branch, the follow-up is
strictly within its intent, the proof uses the exact source head, and the resulting source commit
contains the same behavior. Close the companion only after verifying incorporation.

**Do not infer:** A closed companion is not a rejection when its content was incorporated elsewhere.
Incorporation does not mean the source pull request itself has been merged.

**Outcome history:**

- 2026-08-23: JAIPilot reported measured per-frame scratch-list reuse and opened #5391 against the
  contributor's branch.
- 2026-08-27: the contributor stated that the
  [scratch-list suggestion was folded into #5367](https://github.com/MovingBlocks/Terasology/pull/5367#issuecomment-5444290686).
- 2026-08-28: #5391 was
  [closed as superseded](https://github.com/MovingBlocks/Terasology/pull/5391#issuecomment-5448982009)
  after the incorporated source head was verified.

## K-004 — A valid optimization can belong in a separate follow-up

**Status:** Maintainer-confirmed planned follow-up; the source and companion pull requests remain
open, and the caching change is not recorded as accepted or merged.

**Last verified:** 2026-08-28 UTC.

**Source identities:**

- AgentScope Java source [#2796](https://github.com/agentscope-ai/agentscope-java/pull/2796),
  current observed head `7cc7317b0730201995c787872489cfb0a54da9ba`.
- Contributor-branch companion [#1](https://github.com/jnduan/agentscope-java/pull/1), current
  observed head `8f9e00c56d9588464dc02b6cfb46b42190761316`.

**Fingerprint:** A correctness pull request introduces synchronization around expensive reflective
generation. Caching repeated results could remove the contention, but it adds lifecycle and mutation
semantics beyond the source pull request's single concern.

**Transferable knowledge:** Technical compatibility does not imply contribution compatibility. Keep
a correctness fix bounded when the author intentionally chose one concern, and record a verified
optimization as a separate follow-up. For schema caches, preserve independent returned-map mutation,
null behavior, and class/type identity, and assess retention under dynamic class loading.

**Transfer checks:** Confirm that generation is deterministic for the cache key, callers cannot
mutate shared cached state, memory retention is acceptable, and the current pull request is not the
maintainer-approved delivery channel.

**Do not infer:** “Planned follow-up” is not approval of a specific patch, benchmark, cache key, or
lifecycle policy. Do not fold the optimization into the source pull request without new direction.

**Outcome history:**

- 2026-08-23: JAIPilot published a measured schema-caching follow-up with explicit lifecycle limits.
- 2026-08-23: the source author confirmed that
  [caching was planned separately](https://github.com/agentscope-ai/agentscope-java/pull/2796#issuecomment-5385325150)
  after the minimal synchronization change lands.
