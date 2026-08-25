---
name: jaipilot-review-diff
description: Review a complete Java Git change for behavioral regressions, unnecessary code, compatibility risk, and missing proof using repository-native checks. Use for pull-request or pre-commit review, changed-code risk, architecture drift, regression analysis, or a request to verify a Java change.
---

# Review a Java diff

Review the complete requested change, not only the most obvious file. Use repository-native evidence
and keep the host agent in control.

## Establish the boundary

1. Confirm that the selected root contains a Java Maven or Gradle project. If it does not, report
   that this skill is not applicable and stop.
2. Read the repository's AGENTS.md, contribution guide, build files, and relevant module
   instructions.
3. Record git status --short, the current revision, and the comparison base. Never fetch or change
   branches unless the user asks.
4. Include staged, unstaged, and untracked Java, test, build, wrapper, and configuration files.
   In a multi-module repository, include every affected module.
5. Preserve all unrelated work. Never run reset, checkout, clean, stash, or broad formatting to
   manufacture a clean diff.

## Review

1. Read every changed production file, relevant tests, and directly affected contracts.
2. Look for incorrect behavior, missing edge cases, compatibility breaks, unsafe resource or
   concurrency behavior, architecture drift, dead code, duplication, needless abstractions, and
   unrelated edits. For normalization, lookup, sorting, collection, or caching changes, explicitly
   compare nulls, empty values, duplicates and multiplicity, locale or Unicode, ordering,
   exceptions, identity, mutability, and missing-value behavior.
3. Prefer deletion and reuse. Keep only lines required by the request or its proof.
4. Treat repository-configured compiler checks, Checkstyle, PMD, SpotBugs, Error Prone, ArchUnit,
   SonarQube reports, and similar tools as evidence. Do not invent equivalent findings when a tool
   is absent.
5. Make corrections only when the user asked for implementation. Otherwise report findings with
   file, location, impact, and the smallest reasonable fix.
6. For JDK, wrapper, plugin, framework, BOM, or dependency edits, verify authoritative stable
   release selection, migration requirements, resolved graph changes, runtime compatibility, and
   the repository's downstream-consumer baseline. Reject unrelated version churn.

## Verify

1. Use the repository wrapper and documented commands. Run focused tests while iterating.
2. Run the normal module or repository verification command after the diff stabilizes.
3. Run configured JaCoCo, PIT, ArchUnit, OpenRewrite, or static-analysis tasks when they apply.
   Do not add plugins, dependencies, exclusions, suppressions, or weaker thresholds merely to pass.
   Inspect declarations, executions, profiles, and lifecycle bindings before calling configured
   evidence unavailable. If implementation was requested and a new durable ArchUnit rule or bounded
   OpenRewrite migration would materially help, follow the `jaipilot-clean-java` tool procedure and
   obtain approval before adding tooling.
4. Confirm that changed tests actually executed. For behavior-sensitive production edits, require
   focused coverage of the affected contract and edge cases. Review tests for observable assertions.
   Do not infer test quality from a green build or line coverage alone.
5. Re-read the final diff after verification and check that generated output did not enter it.
6. If a command cannot run, report the exact failure and leave that property unavailable.
7. Use the `jaipilot-remote-java` skill only when an expensive check materially benefits from remote
   hardware and the exact candidate is already a GitHub-available commit. Remote `HEAD` results do
   not verify staged, unstaged, or untracked files.

## Report

Return:

- revision, comparison base, modules, and files reviewed;
- findings ordered by severity, followed by extra or unnecessary code;
- edits made and why each was necessary;
- exact commands and whether each passed, failed, skipped, or was unavailable;
- test execution, coverage, mutation, architecture, and analyzer evidence when measured; and
- residual risks and unverified boundaries.

Do not claim that the change is correct solely because the build passed.
