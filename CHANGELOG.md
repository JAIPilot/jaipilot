# Changelog

## [7.2.0] - 2026-09-01

### Added

- Added visible impact reporting to all eight JAIPilot skills: concise evidence-backed stage
  completion updates and one prominent consolidated `JAIPilot impact` card in the final agent
  response.
- Added skill-specific impact measures for test generation, Java cleanup, maintainer intent,
  OpenRewrite migrations, diff review, optimized execution, and exact-SHA remote verification.

### Changed

- Required same-scope before/after measurements, explicit `not measured` or `not applicable`
  boundaries, nested-skill aggregation, and zero impact telemetry instead of synthetic quality,
  speed, risk, or “without JAIPilot” claims.

## [7.0.1] - 2026-08-31

### Changed

- Documented direct JAIPilot setup for any remote HTTP MCP client with OAuth, including the raw
  endpoint, the common `mcpServers` configuration shape, and the read-only `skill_get` fallback for
  clients without MCP Skills support.

## [7.0.0] - 2026-08-31

### Added

- Added bounded normal AWS CodeBuild execution for exact committed Maven and Gradle sources, with
  private checksum-verified S3 upload, durable status, bounded CloudWatch logs, cancellation, hard
  timeouts, Corretto 17/21/25, and 2 CPU/4 GiB or 4 CPU/8 GiB profiles.
- Added explicit guidance for fresh-build retries, wrapper and tool downloads, textual JFR/JMH or
  profiler evidence, public-network boundaries, variable cold-start latency, and exact source
  cleanup.

### Changed

- Replaced the eight-tool workspace/process lifecycle with six tools: `skill_get`, `build_prepare`,
  `build_start`, `build_status`, `build_logs`, and `build_cancel`.
- Restricted remote proof to `git archive` output for an explicitly approved exact commit. Dirty,
  staged, untracked, ignored, credential, and `.git` content is never uploaded.
- Made each attempt an independent disposable build with no persistent remote workspace or cache;
  the host agent remains responsible for local edits, commits, command choice, retries, and Git.

### Removed

- Removed the retired Daytona runtime, snapshot images, process wrapper, local operator adapter,
  and any promise that uncommitted working-tree files or remote edits synchronize with the host.

### Security

- Builds receive neither GitHub write credentials nor AWS control-plane credentials. Private source
  is deleted on terminal reads or cancellation, with a one-day S3 lifecycle backstop.

## [6.9.6] - 2026-08-29

### Changed

- Updated the Pipeline Maven JsonUnit 6 record with its exact-head approval, green Jenkins Linux
  and Windows build, three passing UI lanes, and one Maven Central HTTP 502 infrastructure failure.

## [6.9.5] - 2026-08-29

### Fixed

- Corrected the Jenkins MCP Server JUnit 1424 record to include its green Linux and Windows Jenkins
  lanes, keeping automated build evidence distinct from still-pending maintainer review.

## [6.9.4] - 2026-08-29

### Changed

- Extended the public dependency-upgrade record with three new Renovate companions for Pipeline
  Maven, LangChain4j, and Jenkins Configuration as Code.
- Recorded each immutable source and companion head, failure diagnosis, smallest repair, validation
  evidence, current review state, and reusable upgrade lesson.

## [6.9.3] - 2026-08-29

### Changed

- Required `jaipilot-maintainer-intent` to locate and completely read applicable contribution
  guidance, pull-request templates, repository instructions, and AI-assisted contribution or
  disclosure policies before recommending or publishing Java maintenance work.
- Required absent or inaccessible repository guidance to be reported explicitly instead of
  inferring rules that the project has not documented.

## [6.9.2] - 2026-08-29

### Changed

- Reconciled the public dependency-upgrade record with all 52 companion attempts across 48 source
  upgrades, including exact observed heads, current CI or review outcomes, and maintainer direction.
- Recorded 22 previously missing attempts, promoted the Camunda and Job Config History upgrades to
  end-to-end accepted evidence, and captured the current CLA, security-scan, and supersession states.
- Required a full public `skrcode` PR-to-index reconciliation before new upgrade selection and every
  campaign handoff, while keeping green, merged-companion, and accepted-source states distinct.

## [6.9.1] - 2026-08-28

### Changed

- Routed substantial command work from every JAIPilot workflow through
  `jaipilot-fast-execution` whenever safe batching or bounded native parallelism can reduce wall
  time without changing the required evidence.
- Kept concurrency resource-aware and isolated, with serial fallback instead of assuming that
  builds, tests, repositories, or shared services are independent.

## [6.9.0] - 2026-08-28

### Changed

- Made `jaipilot-generate-tests` process the complete requested multi-class scope through a bounded
  parallel queue, with one production/test-class assignment per creation worker.
- Made safely independent changed test classes execute in parallel through repository-configured
  native support or isolated checkouts and outputs, with execution evidence, serial diagnosis for
  concurrency-only failures, and explicit reporting when safe parallelism is unavailable.

## [6.8.1] - 2026-08-28

### Changed

- Promoted “Ship better Java with your coding agent.” into the main README heading as JAIPilot's
  emphasized tagline.
- Expanded the post-install README examples across JAIPilot's Java workflows, including maintainer
  research, meaningful test generation, cleanup, diff review, measured optimization, and efficient
  remote verification.
- Added copy-paste prompts for verified dependency, build-tool, framework, wrapper, and JDK upgrades
  plus Dependabot and Renovate repair work.

## [6.8.0] - 2026-08-28

### Added

- Added the public, zero-dependency JAIPilot Streamable HTTP MCP implementation to this repository.
- Added the standard `io.modelcontextprotocol/skills` capability with paginated `skills/list`, exact
  `skills/get`, and digest-verified `resources/read` for all eight Java skills and their referenced
  files.
- Added standard paginated `mcp/skill` resources plus a read-only `skill_get` compatibility tool
  backed by the same generated sources for Codex builds that do not yet promote server skills.
- Added a deterministic generated skill bundle and drift gate so the MCP always serves the reviewed
  repository sources with exact `sha256:` resource digests.

### Changed

- Made one direct `codex mcp add` command the primary Codex installation path; Codex no longer needs
  the JAIPilot plugin or a copied local skill directory.
- Pointed the MCP Registry and compatibility manifests at the combined public endpoint. Static skill
  reads stay local to that endpoint, while only remote `tools/list` and `tools/call` requests are
  forwarded to the existing bounded OAuth service.
- Removed the Codex plugin manifest and marketplace; Codex now installs only the hosted MCP URL.
  Preserved the Claude Code plugin for Claude's current distribution path.

### Security

- The public MCP forwards an OAuth bearer only for remote tool methods and never forwards cookies or
  browser origins. Skill discovery and resource reads create no upload, workspace, or compute.
- The combined endpoint publishes its own protected-resource metadata and rewrites upstream OAuth
  challenges to that identity so Codex can authenticate without receiving a mismatched resource.

## [6.7.0] - 2026-08-28

### Added

- Added `jaipilot-openrewrite`, a dedicated provider-neutral workflow that chooses whether a clean
  Java migration should use an existing recipe, approved temporary tooling, manual edits, or no
  action.
- Added pinned Maven and Gradle recipe execution guidance, mandatory dry-run and complete-diff
  review, custom-recipe tests, repeat dry-run idempotence evidence, and repository-native proof.

### Changed

- Routed cleanup, modernization, optimization, and review workflows to the dedicated migration
  skill instead of duplicating a smaller OpenRewrite checklist inside general cleanup.

### Fixed

- Corrected Kestra duplicate U-006 to reflect U-001's later accepted merge, and updated Camunda
  U-009 with its final clean head, maintainer approval, merge commit, and still-open source upgrade.

## [6.6.2] - 2026-08-28

### Changed

- Promoted dependency-upgrade record U-001 to accepted after a Kestra maintainer reviewed and
  merged the companion, added the final named-validation-error commit, merged the underlying
  Dependabot upgrade, and completed successful post-merge CI.

## [6.6.1] - 2026-08-28

### Fixed

- Scoped the optional cross-repository record exclusively to dependency and version upgrades.
- Replaced the generic examples with all 30 public `skrcode` companion PRs from the
  dependency-bot repair campaign, including exact source identities, failure classes, delivery
  channels, evidence grades, maintainer direction, duplicates, superseded work, and unadopted
  attempts.
- Prevented open or self-closed candidates from being treated as accepted cross-repository
  precedent when maintainer intent makes the final decision.

## [6.6.0] - 2026-08-28

### Added

- Added a transparent public cross-repository maintenance record with immutable source identities,
  transfer constraints, rejected approaches, and dated outcomes.
- Let `jaipilot-maintainer-intent` use an explicitly supplied knowledge file as optional search
  input while requiring every relevant source and outcome to be revalidated before its final
  decision. The skill never discovers or loads the file automatically.

## [6.5.2] - 2026-08-28

### Changed

- Require behavior baselines for public-accessor or derived-view bypasses to exercise skipped copy,
  sort, validation, and exception timing, including a malformed later collection element after an
  earlier match. This closes a short-circuit regression found by the live maintainer-intent Cloud
  acceptance.

## [6.5.1] - 2026-08-28

### Changed

- Bounded maintainer-history research after decisive current-thread evidence so the skill checks
  only duplicates and contribution mechanics instead of continuing an open-ended repository scan.

## [6.5.0] - 2026-08-28

### Added

- Added `jaipilot-maintainer-intent`, a read-only repository-history preflight that chooses one of
  `PROCEED`, `JOIN_EXISTING`, `COMMENT`, `WAIT`, or `NO_ACTION` before upstream Java maintenance.

### Changed

- Made the controlling optimization workflow require a `PROCEED` decision before editing an
  unfamiliar upstream repository or failed dependency-bot change.

## [6.4.2] - 2026-08-26

### Changed

- Described the `large` API profile by its exact 4 CPU/8 GiB capacity instead of implying an
  unspecified larger machine.
- Removed the undocumented compute-hours allowance from agent-facing remote-execution guidance.
- Prepared the hosted skill catalog for the same immutable public-plugin release used by Claude.

## [6.4.1] - 2026-08-26

### Changed

- Moved installation and a copy-paste first-use prompt ahead of the detailed acceptance evidence.
- Replaced the `large` hardware label with the exact 4 CPU/8 GiB workspace size.
- Reduced remote source upload to a user-level approval and sign-in flow, with explicit fail-closed
  behavior when packaging or integrity verification fails.
- Removed the compute-hours statement from the public README.

## [6.4.0] - 2026-08-26

### Added

- Added a deterministic native-JMH comparison contract: hold proof sources constant, compare raw
  fork observations, verify experiment identity, compute median and p95 reproducibly, and separate
  measured operations from build setup.

### Changed

- Published controlled OpenTelemetry Java results: large attribute lookups improved by **12.2% to
  80.3%** at median and **13.8% to 79.8%** at p95 across five workloads.
- Published controlled Micrometer results: single-tag replacement merges improved by **61.3% to
  62.5%** at median while allocation fell by **23.5%**.
- Kept the evidence honest: Micrometer insertion latency below the 10% threshold is not presented
  as a speed claim, and OpenTelemetry `Value` allocation is reported as unchanged.

## [6.3.2] - 2026-08-25

### Changed

- Published the controlled Apache Calcite performance result: existing graph-removal JMH workload
  medians improved by 87.5–92.4% across three removal sizes on one large remote workspace.
- Recorded the paired benchmark method, p95 results, matching 15-test behavior proof, two-file
  candidate scope, and fresh 16,644-test clean verification.

## [6.3.1] - 2026-08-25

### Changed

- Added the verified Petclinic JDBC result: six-vet listing queries fell from 8 (`2 + N`) to a
  constant 2 while 15 focused and 79 clean-build tests passed.
- Made remote source packaging robust in zsh by reserving its special `path` variable, accepting
  every successful HTTP 2xx upload response, and documenting exact-file cleanup for restrictive
  host policies.
- Live-tested seven-sample measurement output, durable cancellation, idempotent repeated
  cancellation, and confirmed workspace deletion on the public remote service.

## [6.3.0] - 2026-08-25

### Added

- Added a large-hardware performance workflow for one representative Java workload, an exact
  digest-matched candidate, and same-workspace baseline/candidate evidence.

### Changed

- Raised performance evidence to seven raw observations with median and p95 reporting, a 10%
  shared-hardware wall-time threshold, and deterministic count evidence for smaller wins.
- Kept the coding agent as the only planner and local editor; remote candidate changes remain
  disposable and are never synchronized or published.

## [6.2.0] - 2026-08-25

### Changed

- Made JAIPilot Remote the default execution boundary for applicable Java compilation, builds,
  tests, analysis, profiling, benchmarks, and toolchain work.
- Kept commands local only when the laptop provides a concrete advantage such as private network
  access, local services or secrets, machine-specific state, lower latency for a tiny command, or
  remote unavailability.
- Required remote evidence to match the exact uploaded working tree and a fresh upload after
  relevant local edits.

## [6.1.5] - 2026-08-25

### Changed

- Removed an underpowered local-skills comparison from public product positioning because it did
  not exercise JAIPilot Remote, the product's primary execution benefit.

## [6.1.4] - 2026-08-25

### Added

- Published a matched JAIPilot-versus-control evaluation across Apache Commons Lang, Gson, and
  Apache Calcite, using exact pre-fix commits and accepted upstream tests held out until both agents
  finished.

### Changed

- Reframed the public product claim around the result the evaluation supports: repeatable Java
  proof and scoped verification, not an unsupported increase in model intelligence.

## [6.1.3] - 2026-08-25

### Changed

- Standardized the product tagline across the README, plugin metadata, and MCP Registry listing:
  "Ship better Java with your coding agent."

## [6.1.2] - 2026-08-25

### Added

- Added an official MCP Registry listing for the hosted JAIPilot Remote server.
- Added release-time registry publication through GitHub OIDC with a digest-pinned publisher.

## [6.1.1] - 2026-08-25

### Changed

- Replaced the raw Supabase MCP hostname with the stable `api.jaipilot.com` public API domain.

## [6.1.0] - 2026-08-25

### Added

- Added bounded ArchUnit guidance for durable repository architecture invariants.
- Added pinned, dry-run-first OpenRewrite guidance for reviewed cleanup and migration recipes.

### Changed

- Routed optimization, test generation, and diff review through the shared tool procedure when
  either tool would materially improve proof.
- Kept adding dependencies, plugins, recipes, or lasting build configuration behind explicit user
  approval.

## [6.0.4] - 2026-08-25

### Changed

- Replaced the README's product-language example with a natural behavior-preserving Java hardening
  request.

## [6.0.3] - 2026-08-25

### Changed

- Required repository-specific affirmative consent before the first remote source upload in a
  conversation.

## [6.0.2] - 2026-08-25

### Changed

- Replaced standalone JAIPilot metrics with a controlled before/after comparison at one exact
  Petclinic pull-request head.
- Independently reproduced test counts, changed-method and class coverage, production-line
  reduction, method count, complexity, and clean-build outcomes for both states.

## [6.0.1] - 2026-08-25

### Changed

- Rewrote the public and bundled READMEs around JAIPilot's user outcome: less agent drift, better
  Java changes, repository-native proof, and remote execution that frees the developer laptop.
- Replaced authentication and infrastructure detail with a single sign-in instruction.
- Added transparent Petclinic acceptance data for code reduction, tests, coverage, clean-build time,
  toolchain readiness, and included remote compute.

## [6.0.0] - 2026-08-25

### Added

- Added standard OAuth sign-in through jaipilot.com for remote tools in Codex and Claude Code.
- Added explicit, digest-verified upload of the current tracked and unignored Git working tree.
- Added `workspace_prepare` and public-beta ownership, concurrency, source-size, lifetime, and
  included-compute bounds.
- Added a benefit-and-proof section with the measured Spring Framework Petclinic acceptance run.

### Changed

- Replaced the local Deno adapter and internal shared bearer with one hosted HTTP MCP resource.
- Made remote Java execution independent of a user-installed GitHub App and able to verify staged,
  unstaged, and untracked repository files.
- Required agents to identify the concrete benefit of every remote run without inventing speedup.

### Removed

- Removed the bundled MCP runtime, cloud trigger secret configuration, provider-specific setup, and
  exact-committed-SHA-only limitation from the public plugin.

## [5.4.1] - 2026-08-24

### Removed

- Removed the behavior-lock mode, pre-change characterization stage, and baseline/candidate
  verification contract from every JAIPilot CLI skill.

### Changed

- Made test generation a direct testing and coverage workflow, while optimization now relies on
  focused repository tests, configured checks, and final clean verification.

## [5.4.0] - 2026-08-24

### Added

- Added `jaipilot-optimize-java`, a controlling workflow that turns a bounded Java change into its
  smallest repository-verified form.
- Added `jaipilot-fast-execution`, a resource-aware workflow for reducing Maven/Gradle build, test,
  analysis, and benchmark wall time without weakening proof.

### Changed

- Strengthened test generation with direct test-generation and coverage-campaign workflows,
  lifecycle-aware JaCoCo/PIT discovery, and semantic collection edge cases.
- Strengthened cleanup and review with concrete AI-clutter criteria, honest complexity reduction,
  stable-upgrade scope, semantic replacement checks, and compatibility evidence.
- Rewrote plugin, marketplace, skill, and repository descriptions around one clear outcome: verified
  Java engineering performed by the customer's own coding agent.
- Simplified the public repository by removing internal OpenAI submission paperwork and the
  historical Cloud campaign inventory; retained only source, distribution, verification, security,
  community, and release material.

## [5.3.0] - 2026-08-24

### Added

- Bundled the six-tool JAIPilot Remote MCP server with the existing Java skills for Codex and Claude
  Code.
- Added `jaipilot-remote-java` to run long builds, tests, analyzers, profilers, and benchmarks on
  disposable Java hardware at one exact committed GitHub SHA.
- Added pinned remote Java environments with JDK 17, 21, and 25, Maven 3.9.16, and Gradle 9.7.0.
- Added Deno protocol tests and structural validation for the MCP manifest, permissions, adapter,
  and secret-free distribution boundary.
- Added separate Codex and Claude Code launch bindings for one shared adapter, including the Codex
  plugin-relative working-directory path required by current hosts.

### Changed

- Kept the host agent responsible for reasoning, edits, command selection, Git, and user
  interaction; the MCP supplies execution hardware only.
- Made remote execution opt-in and fail closed for staged, unstaged, or untracked local changes.
- Updated privacy, security, terms, contribution, and submission documentation for the hosted
  execution boundary.
- Explicitly kept builds requiring corporate VPN/VPC access, private artifacts, internal services,
  or unavailable secrets on the developer's existing execution boundary.

## [5.2.0] - 2026-08-16

### Changed

- Expanded `jaipilot-clean-java` into a fail-closed unused-code cleanup with a per-candidate
  deletion ledger, leaf-first application, and reversible verification.
- Added evidence-backed logical consolidation that reduces classes, methods, lines, and duplication
  only when invariants and downstream contracts genuinely match.
- Added stable JDK, build, framework, plugin, and dependency modernization in isolated compatibility
  batches, including full-environment checks before adopting Java 25.
- Added measurement-first algorithm, allocation, I/O, caching, and concurrency optimization with
  reproducible raw runs, median, p95, and virtual-thread suitability checks.
- Required repository-wide symbol and configuration checks plus explicit reflection, framework,
  side-effect, profile, public-API, resource, dependency, packaging, and runtime boundaries.
- Made uncertainty a retained-and-reported result instead of an unsafe deletion or universal safety
  claim, while keeping configured, pinned, exactly scoped OpenRewrite dry runs first and composing
  the existing test-generation and diff-review skills for regression coverage and final proof.

## [5.1.0] - 2026-08-16

### Changed

- Expanded `jaipilot-generate-tests` into a per-class coverage campaign that targets at least 80%
  fresh JaCoCo line coverage for every eligible production class without rewarding hollow tests.
- Added bounded one-class-per-worker parallel waves with isolated worktrees or build outputs,
  deterministic prioritization, aggregate remeasurement, and honest per-class blockers.
- Kept test execution repository-native and prevented concurrent Maven or Gradle processes from
  sharing one checkout or output tree.

## [5.0.1] - 2026-08-11

### Added

- Added public privacy and terms documents for the OpenAI plugin review.
- Added a reproducible submission packet with listing copy, starter prompts, reviewer test cases,
  availability guidance, attestations, and release notes.
- Declared the public privacy and terms URLs in the Codex plugin interface metadata.

### Changed

- Prepared the same three skills-only workflows for the official OpenAI plugin directory without
  adding a runtime, backend, authentication, telemetry, or a package-manager distribution.

## [5.0.0] - 2026-08-11

### Changed

- Rebuilt JAIPilot as three self-contained Agent Skills for Codex and Claude Code.
- Delegated reasoning, edits, commands, retries, cancellation, Git, and reporting to the host agent.
- Made repository-native Maven, Gradle, test, coverage, mutation, architecture, refactoring, and
  analysis configuration the source of truth.

### Removed

- Removed the Java runtime and its 13,781 lines of production and test code.
- Removed MCP, launchers, first-use downloads, installers, background work, hooks, persistent state,
  exact receipts, metrics, and the dashboard.
- Removed runtime release payloads, Maven project files, legacy evaluations, and obsolete docs.

The runtime line remains available at tag
[v4.0.8](https://github.com/JAIPilot/jaipilot/releases/tag/v4.0.8).
