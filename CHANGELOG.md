# Changelog

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
