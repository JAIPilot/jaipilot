---
name: jaipilot-remote-java
description: Offload substantial Java builds, tests, analyzers, profilers, or benchmarks to authenticated disposable JAIPilot hardware, including the current tracked and unignored working tree. Use when remote execution materially reduces laptop setup, resource pressure, or interruption; do not use for ordinary quick checks or workloads that require unavailable private services.
---

# Run substantial Java work remotely

Use JAIPilot as execution hardware while the host agent retains reasoning, edits, Git, command
selection, and user interaction. JAIPilot Remote is not another coding agent.

## Decide whether remote execution helps

1. Read repository instructions, build files, wrappers, modules, toolchain declarations, and
   required services before selecting a command.
2. Prefer local execution for quick checks. Use remote hardware for long clean builds, broad test
   suites, mutation testing, static analysis, profiling, benchmarks, or missing local JDK capacity.
3. Keep work local when it needs a corporate VPN/VPC, private artifact repository, internal
   database, licensed service, hardware peripheral, or secret that is not explicitly available.
4. Tell the user when JAIPilot starts, what state is uploaded, and why offloading is useful. Never
   claim that remote hardware itself makes a command semantically stronger.

## Prepare the exact working tree

1. Confirm this is a Java Maven or Gradle Git repository. Record `git status --short` and preserve
   every unrelated change. Never fetch, commit, push, reset, clean, stash, or switch branches merely
   to use remote hardware.
2. Call `workspace_prepare` once with the repository directory name. If the host requests browser
   authentication, let the user sign in at JAIPilot; never request, copy, or store an access token.
3. Outside the repository, create one temporary gzip tar archive from
   `git ls-files -co --exclude-standard -z`. This deliberately includes tracked files plus unignored
   staged, unstaged, and untracked files, and excludes `.git` and ignored build output. Include only
   listed paths that still exist so staged or unstaged deletions remain absent. Reject filenames
   containing newlines and links the service cannot safely extract. Inspect the list for credentials
   or inappropriate data; never add files outside the repository.
4. Compute the archive's lowercase SHA-256. Upload its raw bytes with HTTP `PUT` to the short-lived
   `upload_url`, `content-type: application/gzip`, and `x-upsert: false`. Do not echo or retain the
   signed URL. Delete the local temporary archive immediately after `workspace_create` succeeds or
   fails.
5. Call `workspace_create` with `upload_id`, the exact archive SHA-256, and the shortest practical
   15-120 minute lifetime. The service verifies ownership, byte size, and digest before extraction.

## Execute deliberately

1. Prefer repository wrappers and documented commands. JDK 17, 21, and 25, Maven 3.9.16, and Gradle
   9.7.0 are ready; repository metadata chooses the default JDK.
2. Reuse one workspace for related commands so dependency and wrapper caches remain useful. Start
   each bounded command with `process_start`, poll `process_status`, and inspect `process_logs`.
   Durable IDs allow recovery after a host disconnect. The final exit code is authoritative; a
   truncated log is incomplete evidence.
3. Keep focused iteration separate from final repository-native clean verification. Confirm that
   intended tests, analyzers, and modules actually ran.
4. For performance claims, use the same workspace, JDK, command, input, warmup, and resource
   boundary for baseline and candidate, with at least five observations. Report raw values, median,
   p95, and noise; do not call noise a win.
5. Cancel abandoned commands. Always call `workspace_destroy` after success, failure, cancellation,
   or an interrupted plan. The hard lifetime is only a recovery backstop.

## Boundaries

- The signed-in user may have one active workspace and five included compute hours per month.
- Source upload is explicit, private, short-lived, limited to 100 MiB, and deleted after workspace
  preparation. Repository build code still executes with outbound network access.
- Remote edits are disposable. They are not synchronized to the local checkout, committed, pushed,
  or published. Apply product edits locally through the host agent.
- Missing authentication, private services, artifacts, or network dependencies are unavailable
  evidence, not repository failures. Do not loop on environmental errors.

## Report the benefit

Report the uploaded Git state, included dirty files at a summary level, commands, JDK, exit codes,
tests or analyzer outcomes, timings, log truncation, unavailable dependencies, cancellation, and
confirmed workspace destruction. Explain the concrete benefit: local setup avoided, laptop time or
resources freed, durable recovery used, or reproducible measurement obtained. Do not invent a
speedup without a comparable local baseline.
