---
name: jaipilot-remote-java
description: Run a committed Java revision as a bounded disposable remote build for compilation, tests, analyzers, profiling, benchmarks, or toolchain work when the laptop has no concrete advantage. Requires repository-specific source-upload consent and never uploads dirty files.
---

# Run an exact committed Java revision remotely

Use JAIPilot as execution hardware while the host agent retains reasoning, local edits, Git,
command selection, retries, cancellation, and user interaction. Each attempt is a normal AWS
CodeBuild build that terminates when its command exits or reaches the hard timeout. There is no
persistent remote workspace and no remote coding agent.

## Decide whether remote execution fits

1. Read repository instructions, build files, wrappers, modules, toolchain declarations, required
   services, and the intended verification command.
2. Prefer remote execution for substantial compilation, tests, analyzers, profilers, benchmarks,
   and toolchain work when public outbound network access is sufficient and the laptop has no
   concrete advantage.
3. Keep execution local when it needs the laptop's VPN, private artifact repository, internal
   service or database, local secret, licensed dependency, machine-specific hardware or state, raw
   profiler artifacts, or when setup would dominate a tiny command. The current service is not
   attached to the user's VPC or home network.
4. Tell the user which repository and exact commit would be uploaded and why offloading helps.
   Remote hardware changes the execution boundary, not the strength of the evidence.

## Prepare only an immutable commit

1. Confirm the root is a Java Maven or Gradle Git repository. Record `git status --short` and the
   full `git rev-parse HEAD`. Preserve unrelated work.
2. Never upload staged, unstaged, untracked, ignored, or `.git` content. `git archive` must read one
   exact commit object. A dirty checkout may archive its unchanged `HEAD`, but that build is not
   evidence for the dirty files. If the intended change is not committed, keep the build local or
   wait for an authorized commit; do not manufacture a hidden commit to bypass this boundary.
3. Before the first upload for each repository in the current conversation, show the repository
   root and exact SHA, explain that the committed archive will run in a private disposable build
   with public outbound network access, and require affirmative repository-specific confirmation.
   An explicit request to run this repository through JAIPilot Remote counts; an ordinary coding
   request, installation, or consent for another repository does not.
4. Outside the repository, use `mktemp -d` and create a ZIP with `git archive --format=zip` for the
   exact SHA. Compute its lowercase SHA-256 and exact byte count. Do not use the working-tree file
   list, add a manifest, or include files outside that commit.
5. Call `build_prepare` with `source_name`, `source_sha`, `archive_sha256`, and `archive_bytes`.
   Upload the raw ZIP with HTTP `PUT`, supplying every returned `upload_headers` entry exactly.
   Treat any HTTP 2xx response as success. Never echo, persist, or paste the signed URL.
6. Delete the exact temporary ZIP and then its empty temporary directory immediately after
   `build_start` succeeds or fails. Prefer exact-file `unlink` and `rmdir`; never target a broad
   directory or unresolved variable.

## Run and recover one bounded attempt

1. Call `build_start` with the upload ID, one deliberate shell command, a normalized repository
   relative `cwd`, JDK 17, 21, or 25, the shortest practical 60-7200 second timeout, and `medium`
   unless 4 CPU/8 GiB materially helps. `medium` is 2 CPU/4 GiB; `large` is 4 CPU/8 GiB.
   Use `jaipilot-fast-execution` when safe batching or bounded native parallelism can reduce wall
   time without changing the required proof, and select repository-supported worker bounds for
   substantial Maven, Gradle, analyzer, test, or benchmark commands.
2. Prefer repository wrappers. Wrapper downloads and ordinary package-manager or tool setup may run
   inside the command. Multiple ordered setup/build steps may be one fail-fast script, but never
   hide an intermediate failure or weaken the repository's real command.
3. Poll `build_status`, then read `build_logs`. The terminal provider status and final
   `JAIPILOT_BUILD_V1` exit code are authoritative. A missing completion record or truncated
   200 KiB log is incomplete evidence.
4. Each attempt starts from a fresh container with no workspace cache. If the agent edits code,
   retry only from the new authorized commit: create a new archive, reservation, and build. For the
   same committed source, retry an infrastructure or transient network failure at most once when
   evidence supports that classification; do not loop on a deterministic build failure.
5. Use `build_cancel` for abandoned attempts. Successful, failed, timed-out, and cancelled builds
   stop automatically; terminal status or log reads also trigger immediate private-source deletion,
   with the S3 lifecycle as a one-day cleanup backstop.

## Profile and benchmark honestly

Use `large` when CPU or memory headroom is material. JFR, JMH, async-profiler, repository profilers,
and custom tools may run as normal commands if they work without privileged host access. Print a
bounded textual summary needed for the decision; the current service returns logs, not binary JFR,
heap-dump, or flamegraph artifacts. Keep raw-artifact workflows local.

For a performance claim, make the benchmark command perform its own warmup and at least seven
observations inside one build. Compare baseline and candidate commits with the same CodeBuild
profile, JDK, image, command, input, worker settings, and network assumptions. Report raw values,
median, p95, and noise. Do not translate infrastructure variance or an unprofiled refactor into a
speedup.

## Boundaries and report

- A signed-in user may have one active build and five included compute hours per month.
- Source is private, short-lived, limited to 1 GiB, checksum-verified by S3, and bound to the
  declared commit metadata. The build receives no GitHub or AWS control-plane credential.
- Builds have public outbound internet access. Private networks, services, and artifacts are
  unavailable unless the service deployment is deliberately attached to an appropriate VPC.
- Remote edits, files, and caches do not return to the checkout. Apply product edits locally.

Announce a completed result only as
`**JAIPilot · Remote verification** — <outcome>; <proof>.` in progress or as the final outcome lead.
Then render this exact flat section; do not nest bullets:
**JAIPilot impact**
- **Remote verification:** <outcome>
- **Evidence:** <strongest proof>
Apply [impact-reporting.md](references/impact-reporting.md) for measures, nesting, and limitations,
then provide supporting detail.

Report the exact source SHA and archive digest, command, cwd, JDK, profile, provider status, exit
code, test/analyzer/profile outcome, material timing, log truncation, retry or cancellation, source
cleanup result, and any unavailable dependency. Describe only the concrete benefit observed.
