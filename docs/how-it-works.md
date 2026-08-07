# How JAIPilot works

JAIPilot helps a coding agent change Java code safely. The agent decides what to change and edits the
code. JAIPilot finds the right files, keeps work isolated, runs the checks, and applies an approved
result.

Everything runs on your machine. JAIPilot does not upload your source code.

## The basic workflow

```text
inspect or quality → prepare tests or cleanup → edit → validate → apply or discard
```

### 1. Check the project

`inspect` finds the project root, build tool, production classes, changed files, coverage setup, and
active JAIPilot runs.

`quality` checks one class, the changed classes, or the whole project when requested. It reports
issues, complex methods, duplication, cleanup effort, and quality scores.

### 2. Create a safe workspace

JAIPilot runs a clean build, copies the source into an isolated workspace, and records which files
the agent may change. The live project stays untouched.

For test generation, you can target:

- named classes or source files;
- classes changed in the repository;
- classes below a coverage target, using a fresh JaCoCo report; or
- the whole project, when explicitly requested.

For cleanup, you can target named classes, changed classes, or the whole project. JAIPilot first runs
a fixed, versioned set of OpenRewrite fixes on only those files. Any temporary build setup stays in
the isolated workspace.

### 3. Let the agent edit

The connected Codex or Claude Code agent edits only the isolated workspace.

- Test runs may change Java files only under `src/test/java`.
- Cleanup runs may change the selected production files and their related Java tests.
- Build files, documentation, generated output, unrelated production files, deletions, and symbolic
  links are not allowed.

### 4. Validate the result

JAIPilot checks that the agent changed only allowed files, then runs a clean build. It checks the files
again after the build so generated source changes cannot slip through.

Validation also proves that:

- changed tests really ran, using fresh Maven or Gradle test reports;
- line and branch coverage meet any requested target when JaCoCo is available;
- focused PIT mutation testing reaches the default 70% target for test generation, and for cleanup
  that changes related tests; and
- the change adds no critical or high-severity issue and does not lower the overall quality score.

If PIT finds no mutations to score, a positive mutation target does not pass. The report shows the
coverage, test execution, mutation results, complexity, duplication, and cleanup effort. See
[quality metrics](quality-metrics.md) for the exact formulas and scope rules.

### 5. Apply or discard

JAIPilot applies a result only after you confirm it. The result must still match the version that
passed validation, and the live source must not have changed since the run began. JAIPilot writes
only approved files and rolls everything back if a write fails.

Discarding removes the isolated workspace and leaves the live project unchanged.

## Automatic review of Java changes

At the end of each agent turn, `diff-gate` checks for new Java changes. On a feature branch, it checks
everything since the branch split from the local default branch. On the default branch, it checks the
previous commit. Staged, unstaged, and untracked files are included.

This check is quick and does not run a build. If a Java or build-file change has no matching proof
receipt, the plugin asks the agent to review it. Non-Git projects are ignored. If Git inspection fails,
the plugin stops and shows a recovery command instead of skipping the check.

After the fixes are complete, `prove-diff` copies the current project into a fresh workspace and
checks the exact diff. By default, it requires:

- a clean, full test build;
- 90% coverage of changed executable lines;
- 85% coverage of changed branches;
- an 80% PIT score for mutations on changed lines;
- a new-code quality score of 90; and
- no new or newly escalated critical or high-severity issues.

Whole-class results are still shown, but old code outside the patch does not make a small change fail.
When the diff passes, JAIPilot saves a local receipt for that exact Java and build-file fingerprint.
Any relevant change makes the receipt stale.

## Private local state

JAIPilot stores run state in `JAIPILOT_STATE_HOME`, `XDG_STATE_HOME/jaipilot`, or your platform's local
state directory. Proof receipts contain fingerprints and scores, not source code. Writes are atomic,
private to the owner where POSIX permissions are available, and protected by per-run locks.

Only one run may be active for a project, with at most four active runs across all projects. Runs
expire after two hours. JAIPilot removes an expired workspace only when its path matches the expected
temporary-workspace pattern.

## Local impact dashboard

Every normal toolkit invocation idempotently ensures that one dashboard process is running. It
prefers `127.0.0.1:7433`; when that bind reports a port conflict, it binds an operating-system-selected
free loopback port and atomically records the chosen URL. A cross-process lock prevents concurrent
agents from starting duplicate dashboards. `jaipilot dashboard` returns the current URL, selected
port, process ID, start time, and whether fallback selection was needed. Set
`JAIPILOT_DASHBOARD_PORT` to choose a different preferred port.

The HTTP server uses the bundled Java runtime, has no web framework or external assets, binds only
to IPv4 loopback, accepts only read requests, and applies restrictive browser security headers. A
dashboard failure is reported on stderr without corrupting command JSON on stdout or preventing the
agent from completing a proof workflow. `JAIPILOT_DASHBOARD_DISABLED=1` is an operational escape
hatch for environments that prohibit local listeners.

Usage and outcome summaries live beneath the same private state root in `metrics/summary.json`.
Atomic writes plus a file lock make concurrent runner and dashboard access deterministic. The store
keeps bounded recent activity, aggregate command counts, one-way hashes for distinct-project counts,
and pending validation evidence. Administrative help, version, and dashboard-status checks are not
counted as product usage. The store never records project paths, class names, source, agent prompts,
or failure messages. Pending coverage, quality, finding, debt, mutation, and test-execution gains
become cumulative impact only after transactional apply; discard removes the pending evidence. If
the metrics summary is corrupt, the next write preserves it with a `summary.corrupt-*` name and
starts a fresh summary instead of blocking the Java workflow or silently deleting evidence.

JAIPilot has no hosted backend and does not call Codex, Claude, or another model. Your coding tool
provides the reasoning; JAIPilot provides local, repeatable checks and safe application.
