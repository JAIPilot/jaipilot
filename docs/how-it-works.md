# How JAIPilot works

JAIPilot is a local control plane for Java changes proposed by a connected coding agent. The agent
owns contextual reasoning and edits. JAIPilot owns deterministic project discovery, target
selection, isolation, verification evidence, drift detection, and apply.

## The transaction

```text
inspect → prepare-tests | prepare-cleanup → validate → apply | discard
```

### 1. Inspect

JAIPilot resolves the real project root, Maven or Gradle build, valid wrapper, production classes,
changed production files, JaCoCo configuration, cached coverage metadata, and active-run state.

### 2. Prepare

Every run begins with a clean build of the live project. JAIPilot snapshots the live source,
creates an isolated workspace, records exact targets, and returns that workspace to the agent.

Test generation supports four deterministic target modes:

- `classes` for explicit classes or production source paths;
- `changed` for production classes changed in the live repository;
- `coverage` for classes below a threshold from a newly generated JaCoCo report;
- `all` only when a whole-project operation is explicitly requested.

Cleanup supports `classes`, `changed`, and explicit `all`. It first runs pinned OpenRewrite
`CodeCleanup` and `CommonStaticAnalysis` recipes with an exact-source precondition. Temporary build
configuration exists only in the workspace and is removed before the agent receives it.

### 3. Improve

The connected Codex or Claude Code agent works only inside the returned workspace.

- Test runs may change Java files only beneath `src/test/java`.
- Cleanup runs may change selected production Java plus directly related Java tests.
- Build files, documentation, generated output, unrelated production source, deletions, and
  symbolic paths are outside the write scope.

### 4. Prove

Validation snapshots the candidate, enforces scope, runs a clean build, and takes a second snapshot
to reject source written by build steps. Changed tests must appear with non-zero execution counts
in newly generated Surefire, Failsafe, or Gradle XML reports.

When JaCoCo is configured, test generation reports before/after line and branch coverage for every
target. A requested minimum line-coverage goal blocks apply until every target meets it.

### 5. Apply or discard

Apply requires explicit confirmation and a candidate identical to the immediately validated
snapshot. JAIPilot also verifies that the live source still matches its original snapshot. It then
writes the allowlisted files transactionally and rolls back if a write fails. Discard removes the
workspace without changing live source.

## Persistence and concurrency

Short-lived runner invocations share local workflow state beneath `JAIPILOT_STATE_HOME`,
`XDG_STATE_HOME/jaipilot`, or the platform-local state directory. Metadata writes are atomic;
directories are owner-only where POSIX permissions are available; each run is file-locked.

JAIPilot permits at most one active run for a real project and four active runs globally. Runs
expire after two hours. Expired workspaces are removed only when their paths match the expected
JAIPilot temporary-workspace pattern.

## Local and provider-neutral

JAIPilot has no hosted backend and never uploads project source. It does not invoke Codex, Claude,
or another model itself. The host supplies the reasoning environment; the toolkit supplies local,
deterministic orchestration and proof.
