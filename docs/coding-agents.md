# Use JAIPilot inside your coding agent

JAIPilot's local stdio MCP server lets a coding agent prepare Java characterization tests before
editing existing production code, then verify the preserved behavior after its edits. It uses the
same workflow runner, account and credits as the CLI. Builds and tests run in your checkout.
The MCP server executes the local commands itself. Your coding agent invokes the tools and receives
job IDs, progress, and command evidence; it makes the planned production edits once the baseline is
ready.

## Connect Codex

Install a JAIPilot version with the `mcp` command and sign in:

```sh
jaipilot auth login
codex mcp add jaipilot -- jaipilot mcp --repo /absolute/path/to/java-repository
codex mcp list
```

Use an absolute executable path if `jaipilot` is not on Codex's `PATH`. The server is bound to the
repository passed at startup; configure a different server entry for another checkout. Start a new
Codex session after configuring it. This works with Codex CLI and the IDE extension. Other MCP hosts
can launch the same command using their stdio server configuration.

JAIPilot's preparation and verification tools write files or run local commands, so Codex may ask
for approval. For unattended use after authorizing this server, add this to its existing configuration:

```toml
[mcp_servers.jaipilot.tools.lock_behavior]
approval_mode = "approve"

[mcp_servers.jaipilot.tools.verify_behavior]
approval_mode = "approve"

[mcp_servers.jaipilot.tools.cancel_job]
approval_mode = "approve"
```

Keep the normal approval setting if you prefer reviewing each invocation. Setting Codex's overall
approval policy to `never` alone does not authorize a write-capable MCP tool. See the
[official Codex MCP configuration](https://learn.chatgpt.com/docs/extend/mcp?surface=cli).

For a source checkout, without installing or replacing your released CLI:

```sh
codex mcp add jaipilot -- deno run -A \
  /absolute/path/to/jaipilot-cli/cli/main.ts mcp --repo /absolute/path/to/java-repository
```

## Make preparation part of the edit workflow

The server supplies tool descriptions and workflow instructions. Add the following to the Java
repository's existing `AGENTS.md` (or your coding agent's equivalent project instructions):

```text
Before editing existing Java production code:
1. Identify the affected behavior and scope, the actual test directories, and a test command that
   runs the tests and exits nonzero on failures. Include relevant dependencies and callers in scope.
2. Call JAIPilot's lock_behavior before production edits. Supply the scope, explicit test_paths,
   test_command and planned intent. Build output directories must be gitignored.
3. Poll get_job_status with wait_seconds=30. Proceed only when ready_to_edit=true. If blocked,
   failed or cancelled, investigate the evidence and report the blocker before proceeding.
4. Keep result.baseline_id and preserve the baseline tests unchanged. Make the planned source edits.
5. Call verify_behavior with that baseline_id and poll until verified=true. Investigate failures;
   do not regenerate the baseline to bless a regression.

Do not edit the checkout while a JAIPilot job is running. If the planned scope expands, prepare the
additional scope against its original code before editing it. An intentional behavior change must
be identified explicitly and its old expectations reviewed with the user.
```

MCP makes the tools available; project instructions guide when the coding agent calls them. The
server does not intercept the host's file writes or guarantee that the host follows those instructions.

## Tool contract

All paths are relative to the server's repository root. Choose exactly one scope type: `all: true`,
`paths`, `classes`, or `selections`. Selection strings use `path/File.java:start-end`.

Example `lock_behavior` arguments:

```json
{
  "scope": { "classes": ["com.acme.OrderService"] },
  "test_paths": ["src/test/java"],
  "test_command": "./mvnw -q clean verify",
  "timeout_seconds": 3600,
  "intent": "Extract order validation while preserving observable behavior"
}
```

`test_paths` explicitly declares the files or directories where JAIPilot may create or edit tests.
Every tracked and nonignored file outside those paths is protected, including pre-existing dirty
files and build configuration. The chosen command must actually execute the baseline tests;
JAIPilot cannot infer that an arbitrary successful shell command is a valid test suite. For projects
without coverage instrumentation, configure it before preparation if you need measured coverage.

| Tool | Input | Result |
| --- | --- | --- |
| `lock_behavior` | Scope, test paths, test command, optional intent and timeout | Starts preparation; returns `job_id`. |
| `get_job_status` | `job_id`, optional `wait_seconds` (0–30, default 10) | Progress, state, readiness and final evidence. |
| `verify_behavior` | `baseline_id` | Starts a local rerun of the original command; returns `job_id`. |
| `cancel_job` | `job_id` | Requests cancellation; poll until `cancelled`. |

Preparation uses the existing managed `lock_behavior` workflow. It then independently runs the
supplied verification command. Only a completed workflow, unchanged protected files, nonempty test
files and a passing verification command produce `ready_to_edit: true` and `result.baseline_id`.
The command must leave tracked and nonignored source files unchanged during verification.

Verification does not call the LLM. It refuses added, removed or changed files in the baseline test
paths, reruns the exact original command and reports real exit status and bounded output. Check
`verified: true`; failed tests return `blocked` with command evidence. Passing tests demonstrate the
behavior covered by those tests; they do not prove all possible behavior or complete coverage.

## Jobs, storage and cancellation

- Tool calls return promptly while a job runs. Status calls can wait up to 30 seconds; the underlying
  service retains the CLI's existing transient-error retries.
- Background jobs have no overall elapsed-time cutoff. `timeout_seconds` defaults to 3600 seconds
  (1 hour), with a maximum of 7200 (2 hours). It is the minimum allowance for each local command
  during preparation and the limit for the independent verification command. Longer valid command
  limits requested by JAIPilot are honored. The original verification limit is stored in the baseline
  and reused after edits. A status poll returning while the job is running does not cancel the job.
- One MCP job runs at a time per checkout, including across MCP server processes. Different Git
  worktrees have separate locks and baselines. Other CLI/ACP processes and host edits are not locked.
- Baseline manifests store test and original file SHA-256 hashes, scope, command and verification
  evidence under the checkout's Git metadata (`git rev-parse --git-path jaipilot-mcp`). They survive
  server restarts and are not added to your source tree.
- Jobs and their recent progress are in memory (up to 100 retained jobs). Job IDs do not survive a
  restart; baseline IDs do. Closing the MCP connection or stopping the server cancels active work.
- Cancellation stops the local command process tree. It leaves partial edits for review and does
  not reset your working tree. Review before retrying preparation.
- Explicitly selected test paths must contain the actual baseline tests and must not contain
  production code. Symbolic links in test paths are rejected. Gitignored build artifacts are not
  hashed or protected.
- Long-lived MCP servers do not update themselves during a session. Run `jaipilot update` and
  restart the coding agent to use a newer installed version.

The existing [local execution and privacy policy](../README.md#local-execution-and-privacy) applies.

## Verify a development build

```sh
deno task check
deno task compile
deno run -A --frozen scripts/check-mcp.ts dist/jaipilot
```

For an opt-in test through a separate Codex session, sign in to both products and provide Maven and
a compatible JDK (17 recommended), then run:

```sh
deno run -A --frozen scripts/check-codex-mcp.ts dist/jaipilot
```

This spends JAIPilot and coding-agent credits. It creates a disposable Java checkout under `dist`,
asks Codex to refactor through the real MCP integration, checks the original baseline was prepared
before edits, and independently injects a regression, verifies failure, restores the refactor and
verifies success. The checkout, Codex event log and summary remain under the printed artifact path.
