---
name: jaipilot-remote-java
description: Run long Java builds, tests, analyzers, profilers, or benchmarks on disposable JAIPilot remote hardware at an exact committed GitHub SHA. Use only when remote execution materially helps and the intended state is committed and available through GitHub; do not use for ordinary quick checks or uncommitted changes.
---

# Run committed Java work remotely

Use JAIPilot Remote as execution hardware while the host agent keeps ownership of reasoning, edits,
Git, and user interaction. Never treat the remote service as another coding agent.

## Establish an exact boundary

1. Confirm that the selected root is a GitHub-hosted Java Maven or Gradle repository and read its
   instructions, build files, wrapper, modules, and required services.
2. Record `git status --short`, the lowercase 40-character `HEAD`, and the `origin` repository.
   Never fetch, commit, push, reset, clean, stash, or change branches merely to enable this skill.
3. Use remote execution only when the exact state to test is already committed and available from
   GitHub. The workspace contains only that commit; staged, unstaged, and untracked files are absent.
4. If local changes affect the requested proof, run locally or wait for the user-authorized Git
   workflow. Never describe a remote result for `HEAD` as proof of dirty local files.
5. Confirm that the JAIPilot GitHub App is installed and that sending the committed repository to
   the configured remote sandbox is acceptable. Treat repository builds as executable code with
   outbound network access.
6. Confirm that the command is self-contained or needs only public network dependencies. If it
   requires a corporate VPN/VPC, private artifact repository, internal database, licensed service,
   or unavailable secret, keep the check local and report why; do not repeatedly retry the cloud.

## Execute deliberately

1. Prefer the repository wrapper and documented commands. Use `medium` for normal builds, `small`
   for light checks, and `large` only when CPU or memory evidence justifies it. Set the shortest
   practical workspace lifetime and command timeout.
2. Call `workspace_create` once for the exact repository and SHA. Reuse that workspace for related
   commands so Maven, Gradle, and wrapper caches remain useful.
3. Start one bounded command with `process_start`. Poll `process_status`, inspect bounded
   `process_logs`, and use the final exit code as the authority. A truncated log is not complete
   evidence.
4. Keep focused iteration separate from the final repository-native clean verification. Confirm
   that intended tests and analyzers actually ran.
5. For performance claims, use the same workspace, JDK, command, inputs, warmup, and resource
   boundary for comparable variants with at least five observations. Do not call noise a win.
6. Cancel abandoned commands. Always call `workspace_destroy` after success, failure, cancellation,
   or an interrupted plan; the TTL is only a recovery backstop.

## Boundaries

- JDK 17, 21, and 25, Maven 3.9.16, and Gradle 9.7.0 are preinstalled. Repository wrappers remain
  preferred, and repository metadata selects the default JDK.
- Remote edits remain disposable and are not synchronized to the local checkout or pushed to
  GitHub. Make product edits locally through the host agent.
- Missing authentication, App access, private services, artifacts, or network dependencies are
  unavailable evidence, not repository failures.
- The shared sandbox does not provide corporate VPN/VPC connectivity or customer-internal service
  access. Do not claim general enterprise-network compatibility.
- The current remote bundle is an operator preview that requires a separately provisioned internal
  credential. Do not ask users to paste credentials into prompts or commit them.

## Report

Return the repository and exact SHA, hardware profile, commands, process exit codes, test/analyzer
evidence, log truncation, timings when measured, unavailable dependencies, confirmation that local
dirty state was excluded, and workspace-destruction outcome.
