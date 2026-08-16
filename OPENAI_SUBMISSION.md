# OpenAI plugin submission packet

This file is the reproducible source for the OpenAI Platform submission form. It describes the
exact skills-only bundle at version 5.1.0 and must not be interpreted as approval or publication by
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
diffs, raise meaningful class-level test coverage through bounded parallel workers, and simplify
code without widening scope. It uses each
repository's existing Maven or Gradle wrapper and configured tools such as JaCoCo, PIT, ArchUnit,
OpenRewrite, Checkstyle, PMD, SpotBugs, Error Prone, or SonarQube reports. Missing evidence is
reported as unavailable. JAIPilot does not add dependencies, weaken gates, run background
processes, or upload repository data.

## Starter prompts

1. Review and verify my current Java diff.
2. Raise meaningful per-class Java test coverage toward at least 80%.
3. Simplify the changed Java code without changing behavior.

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

### 5. Perform a minimal cleanup

- **Prompt:** Simplify the changed EntityUtils code without changing behavior. Prefer deletion over
  abstraction.
- **Fixture:** A Java repository with redundant branches and OpenRewrite already configured.
- **Expected:** Select `jaipilot-clean-java`; establish a baseline; use only an applicable pinned
  recipe if it reduces the requested scope; review its patch; keep the smallest coherent cleanup;
  run focused and repository verification; report zero net change if no safe improvement exists.
- **Expected result shape:** Baseline, recipe or manual simplification used, final minimal diff,
  verification results, behavior-preservation evidence, and any declined cleanup.

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

Version 5.1.0 expands JAIPilot's test workflow into bounded one-class-per-worker coverage campaigns.
It targets at least 80% fresh line coverage for every eligible production class, integrates worker
patches centrally, and reports blockers honestly. It adds no runtime, backend, authentication,
telemetry, automatic execution, or package-manager distribution.

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
