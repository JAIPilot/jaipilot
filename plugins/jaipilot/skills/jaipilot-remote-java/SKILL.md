---
name: jaipilot-remote-java
description: Run Java builds, tests, analyzers, profilers, benchmarks, and toolchain work on authenticated disposable JAIPilot hardware by default whenever the laptop provides no concrete advantage. Before the first upload for each repository, require affirmative source-upload confirmation; an ordinary task request does not count. Keep work local only for laptop-bound resources, lower latency on tiny commands, or remote unavailability.
---

# Run substantial Java work remotely

Use JAIPilot as execution hardware while the host agent retains reasoning, edits, Git, command
selection, and user interaction. JAIPilot Remote is not another coding agent.

## Default to remote execution

1. Read repository instructions, build files, wrappers, modules, toolchain declarations, and
   required services before selecting a command.
2. Prefer JAIPilot Remote for any command that compiles, packages, tests, analyzes, profiles,
   benchmarks, or provisions a Java toolchain. The laptop being capable of running the command is
   not by itself a reason to keep it local.
3. Keep execution local only when the laptop provides a concrete advantage: required corporate
   VPN/VPC access, a private artifact repository, internal service or database, local secret,
   licensed dependency, machine-specific hardware or state, a tiny inspection command whose
   workspace setup would dominate it, or an unavailable remote service or allowance.
4. For related commands against one uploaded state, create one workspace and reuse it. Do not
   alternate between local and remote full builds merely to duplicate evidence.
5. Tell the user when JAIPilot starts, what state is uploaded, and why offloading is useful. Never
   claim that remote hardware itself makes a command semantically stronger.

## Prepare the exact working tree

1. Confirm this is a Java Maven or Gradle Git repository. Record `git status --short` and preserve
   every unrelated change. Never fetch, commit, push, reset, clean, stash, or switch branches merely
   to use remote hardware.
2. Before the first upload for this repository in the current conversation, show the repository
   root and explain that tracked plus unignored staged, unstaged, and untracked files will run in a
   managed workspace with outbound network access; `.git`, ignored build output, and files outside
   the repository are excluded. Require affirmative user confirmation immediately before
   `workspace_prepare`. An explicit request to use JAIPilot Remote for this repository counts as
   confirmation; installation, consent for another repository, or a general request to optimize
   code does not. Ask again if the repository or upload scope changes.
3. Call `workspace_prepare` with the repository directory name. If the host requests browser
   authentication, let the user sign in at JAIPilot; never request, copy, or store an access token.
4. Outside the repository, create one temporary gzip tar archive from
   `git ls-files -co --exclude-standard -z`. This deliberately includes tracked files plus unignored
   staged, unstaged, and untracked files, and excludes `.git` and ignored build output. Include only
   listed paths that still exist so staged or unstaged deletions remain absent. Reject filenames
   containing newlines and links the service cannot safely extract. Inspect the list for credentials
   or inappropriate data; never add files outside the repository. In zsh, do not name a loop or
   scalar variable `path`: zsh treats `path` as the array backing `PATH`, so assigning a filename to
   it can make later commands disappear. Use a task-specific name such as `source_file`.
5. Compute the archive's lowercase SHA-256. Upload its raw bytes with HTTP `PUT` to the short-lived
   `upload_url`, `content-type: application/gzip`, and `x-upsert: false`. Do not echo or retain the
   signed URL. Treat any HTTP 2xx response as success; Supabase signed uploads normally return 200,
   not 201. Delete the exact local temporary archive and file list immediately after
   `workspace_create` succeeds or fails. Prefer exact-file `unlink` plus removal of the now-empty
   temporary directory when the host blocks broad `rm` commands.
6. Call `workspace_create` with `upload_id`, the exact archive SHA-256, and the shortest practical
   15-120 minute lifetime. Use the API profile `large` (4 CPU, 8 GiB) only for substantial
   profiling, benchmarks, or builds that benefit from those resources; otherwise use `medium`.
   The service verifies ownership, byte size, and digest before extraction.
7. Treat the upload as an immutable evidence boundary. If relevant local files change afterward,
   destroy the stale workspace and upload the latest state before claiming focused or final proof.

## Execute deliberately

1. Prefer repository wrappers and documented commands. JDK 17, 21, and 25, Maven 3.9.16, and Gradle
   9.7.0 are ready; repository metadata chooses the default JDK.
2. Reuse one workspace for related commands against the exact uploaded state so dependency and
   wrapper caches remain useful. Use `jaipilot-fast-execution` for substantial command work whenever
   safe batching or bounded native parallelism can reduce wall time without changing the required
   proof. Start each bounded command with `process_start`, poll `process_status`, and inspect
   `process_logs`. Durable IDs allow recovery after a host disconnect. The final exit code is
   authoritative; a truncated log is incomplete evidence.
3. Keep focused iteration separate from final repository-native clean verification. Confirm that
   intended tests, analyzers, and modules actually ran.
4. For performance claims, upload the exact starting state before editing, profile it on the 4 CPU,
   8 GiB `large` API profile, and compare only a bounded local candidate whose patch and remote Git
   delta digests match. Use the same workspace, JDK, command, input, warmup, and resource boundary
   for both states, with at least seven observations. For one stable shell workload, set
   `warmup_runs` and
   `measurement_runs` on `process_start` and use its final `JAIPILOT_MEASUREMENTS_V1` record. Keep
   source and patch contents out of command output. Report raw values, median, p95, and noise; do
   not call noise a win.
5. Cancel abandoned commands. Always call `workspace_destroy` after success, failure, cancellation,
   or an interrupted plan. The hard lifetime is only a recovery backstop.

## Boundaries

- The signed-in user may have one active workspace.
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
