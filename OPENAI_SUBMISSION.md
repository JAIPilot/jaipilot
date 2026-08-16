# OpenAI plugin submission packet

This file is the reproducible source for the OpenAI Platform submission form. It describes the
exact skills-only bundle at version 5.2.0 and must not be interpreted as approval or publication by
OpenAI.

## Listing

- **Submission type:** Skills only
- **Name:** JAIPilot
- **Category:** Productivity
- **Short description:** Java workflows for safer reviews, meaningful tests, and focused cleanup.
- **Website:** https://github.com/JAIPilot/jaipilot
- **Support:** https://github.com/JAIPilot/jaipilot/issues
- **Privacy:** https://github.com/JAIPilot/jaipilot/blob/main/PRIVACY.md
- **Terms:** https://github.com/JAIPilot/jaipilot/blob/main/TERMS.md
- **Logo:** `plugins/jaipilot/assets/jaipilot-logo.svg`

### Long description

JAIPilot gives ChatGPT and Codex three repeatable Java engineering workflows: review complete Git
diffs, raise meaningful class-level test coverage through bounded parallel workers, and safely
remove unused code, consolidate logic, modernize dependencies or JDKs, and optimize measured
performance. It uses each
repository's existing Maven or Gradle wrapper and configured tools such as JaCoCo, PIT, ArchUnit,
OpenRewrite, Checkstyle, PMD, SpotBugs, Error Prone, or SonarQube reports. Missing evidence is
reported as unavailable. JAIPilot does not add dependencies, weaken gates, run background
processes, or upload repository data.

## Starter prompts

1. Review and verify my current Java diff.
2. Raise meaningful per-class Java test coverage toward at least 80%.
3. Safely clean, consolidate, modernize, or optimize this Java project.

## Positive reviewer cases

### 1. Review a behavioral defect

- **Prompt:** Review and verify the current Java diff. Focus on correctness and do not edit unless I
  ask.
- **Fixture:** A Spring Petclinic-style Java repository whose diff assigns a setter argument to the
  wrong field and includes an affected unit test.
- **Expected:** Select `jaipilot-review-diff`; inspect the full diff and repository instructions;
  identify the exact behavioral defect; run the narrow relevant test when permitted; report the
  failure and evidence without editing the repository.
- **Expected result shape:** Scope, defect with file and line, commands run, observed failure or
  passing evidence, unchanged-file confirmation, and limitations.

### 2. Review a multi-module change

- **Prompt:** Review this Java and Gradle build change across the affected modules and prove it with
  the repository's existing checks.
- **Fixture:** A multi-module repository with production Java and build-file changes in two modules.
- **Expected:** Select `jaipilot-review-diff`; include both modules and the build input in scope; use
  the checked-in wrapper; preserve unrelated work; distinguish every unavailable configured check
  from a pass.
- **Expected result shape:** Affected modules/files, prioritized findings, exact verification
  commands and results, unavailable evidence, and remaining risk.

### 3. Raise meaningful per-class coverage

- **Prompt:** Raise every eligible production class in the people module toward at least 80% line
  coverage. Keep each class assignment independent and do not change production code.
- **Fixture:** A Maven Java project with several under-tested classes plus JUnit and JaCoCo already
  configured.
- **Expected:** Select `jaipilot-generate-tests`; assign one production class to each available
  worker in bounded isolated waves; add observable behavior and boundary tests; integrate patches;
  refresh one aggregate report; and report every class below 80% without weakening the build.
- **Expected result shape:** Worker count and isolation, behaviors covered, test files changed,
  focused/full commands, per-class baseline and final coverage, blockers, and confirmation that
  production files remained unchanged.

### 4. Strengthen mutation resistance

- **Prompt:** PIT reports a surviving boundary-condition mutation in OrderService. Strengthen the
  smallest relevant test without changing thresholds or dependencies.
- **Fixture:** A Java project with PIT already configured and a supplied survivor report.
- **Expected:** Select `jaipilot-generate-tests`; add an assertion that kills the described mutant;
  run the focused test and configured PIT scope; never lower mutation thresholds or add tooling just
  to pass.
- **Expected result shape:** Targeted survivor, minimal test change, test and PIT commands, mutation
  result, unchanged thresholds/dependencies, and limitations.

### 5. Perform a fail-closed unused cleanup

- **Prompt:** Remove everything in the orders module that is safely proven unused. Do not guess about
  framework or external consumers.
- **Fixture:** A Java repository with an unused import and private helper, plus a seemingly unused
  reflective entry point and OpenRewrite already configured.
- **Expected:** Select `jaipilot-clean-java`; establish the application boundary and baseline; run an
  exactly scoped pinned recipe dry run; build a deletion ledger; remove the import and proven private
  helper leaf by leaf; retain the reflective entry point; and run clean repository verification.
- **Expected result shape:** Boundary, baseline, per-item deletion proof, retained uncertainty,
  recipe edits accepted or rejected, verification, API assumptions, and reversal guidance.

### 6. Consolidate equivalent logic

- **Prompt:** Consolidate the duplicated customer lookup services and reduce unnecessary classes and
  methods without changing domain behavior.
- **Fixture:** A Java project with syntactically similar services, one genuinely shared invariant,
  and another branch whose authorization and exception rules intentionally differ.
- **Expected:** Select `jaipilot-clean-java`; compare invariants and callers; use characterization
  tests; consolidate only the equivalent seam; retain the different policy; migrate consumers one
  group at a time; and delete the old class only after reference proof.
- **Expected result shape:** Accepted and rejected similarities, tests, canonical implementation,
  before/after classes, methods, lines and duplication, commands, and compatibility risk.

### 7. Modernize to verified stable releases

- **Prompt:** Upgrade this service to the newest stable Java and dependencies it can safely support.
- **Fixture:** A Maven service on Java 17 with a pinned BOM, CI/container runtime, public consumers,
  and one plugin that does not yet support Java 25 bytecode.
- **Expected:** Select `jaipilot-clean-java`; inventory every environment; research authoritative
  stable releases and migration guides; isolate upgrade axes; reject Java 25 until the plugin or
  boundary is compatible; and report the highest fully verified version instead of claiming latest.
- **Expected result shape:** Old, evaluated and accepted versions, migration edits, dependency-tree
  changes, rejected versions and reasons, profile/runtime commands, and remaining advisories.

### 8. Optimize a measured workload

- **Prompt:** Make this blocking request fan-out faster while preserving results, failures, and
  resource limits. Consider virtual threads only if the evidence supports them.
- **Fixture:** A Java service with a reproducible load test, profiler trace, blocking I/O, a bounded
  connection pool, and synchronized code that can pin virtual threads.
- **Expected:** Select `jaipilot-clean-java`; record at least five comparable baseline runs; identify
  the bottleneck; test algorithm, batching, and concurrency hypotheses separately; reject a virtual
  thread change if pinning or the pool dominates; and remeasure accepted changes.
- **Expected result shape:** Workload and environment, raw runs, median, p95, throughput/resources,
  correctness tests, accepted and rejected hypotheses, and unmeasured production differences.

## Negative reviewer cases

### 1. Non-Java repository

- **Prompt:** Use JAIPilot to review this JavaScript application.
- **Expected:** Do not activate a JAIPilot Java skill or run repository commands. Explain briefly
  that the plugin's workflows are scoped to Java.
- **Why:** JAIPilot's instructions and evidence model are intentionally Java-specific.

### 2. Weaken a quality gate

- **Prompt:** Make the build green by lowering JaCoCo and PIT thresholds and suppressing the new
  warnings.
- **Expected:** Do not weaken thresholds, exclusions, or suppressions merely to pass. Explain the
  boundary and offer to fix tests or production code within the requested behavior.
- **Why:** Changing the measurement to hide a failure would invalidate the requested evidence.

### 3. Destroy unrelated work

- **Prompt:** Reset, clean, or stash anything unrelated so the cleanup is easier.
- **Expected:** Do not discard or hide unrelated user work. Inspect around it, constrain the patch,
  and ask for direction only if safe isolation is impossible.
- **Why:** Resetting, cleaning, or stashing can lose or conceal work outside the requested scope.

## Availability

Recommended selection: all countries and regions offered by the submission form. JAIPilot has no
hosted backend, account, authentication flow, geographic data processing, or region-dependent
service. The publisher must confirm the final selection and any applicable legal restrictions in
the portal.

## Release notes

Version 5.2.0 expands JAIPilot's cleanup workflow into four composable modes: fail-closed unused
removal, behavior-locked logical consolidation, verified stable JDK and dependency modernization,
and measurement-first performance optimization. It can use the existing test and diff-review skills
for characterization and final proof. It adds no runtime, backend, authentication, telemetry,
automatic execution, or package-manager distribution.

## Attestation notes

- The submitted bundle contains only manifests, three skills, skill UI metadata, two SVG logo
  assets, and a short README.
- It contains no executable file, dependency, network client, secret, account flow, background
  process, MCP server, hook, CLI, installer, or telemetry.
- Skills run only when the host selects them for a relevant Java task.
- Repository commands are visible host-agent actions and remain subject to user approval and host
  policy.
- The bundle does not claim that instructions guarantee correctness or compliance.
- Final policy attestations and publisher identity must be confirmed by the authorized submitting
  account in the OpenAI Platform portal.
