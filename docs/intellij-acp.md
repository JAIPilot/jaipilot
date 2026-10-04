# JAIPilot in IntelliJ AI Assistant

JAIPilot is an ACP agent with six Java testing outcomes. It uses your existing JAIPilot account,
subscription, and credits. The managed service reasons about the task; commands, edits, builds, tests,
and coverage reporting run in your local Git repository through IntelliJ terminals.

## Install from the ACP Registry

Once the registry submission is accepted, open **Settings → Tools → AI Assistant → Agents**, search
for **JAIPilot**, and install it. Before acceptance, use the custom-agent setup below. A GitHub release
or an open registry pull request does not mean the public catalog listing is live.

Requirements: an ACP-capable JetBrains IDE with AI Assistant, Git, your project's supported JDK and
build tools, and a JAIPilot account. JAIPilot does not install Java or build tools. See
[JetBrains' ACP documentation](https://www.jetbrains.com/help/ai-assistant/acp.html) for current IDE
support and organization policies.

## Set up a custom agent now

1. Download the matching archive from the [JAIPilot ACP release](https://github.com/JAIPilot/jaipilot/releases/latest).
   Compare its SHA-256 hash with the supplied `.sha256` file, extract it, and place `jaipilot-acp`
   (`jaipilot-acp.exe` on Windows) in a stable directory. The executable includes its runtime; no npm,
   npx, Python, or separate Deno installation is required.
2. In AI Chat, choose **Add Custom Agent**. IntelliJ opens `~/.jetbrains/acp.json`.
3. Add a `JAIPilot` entry to the existing `agent_servers` object, preserving other entries:

   ```json
   {
     "agent_servers": {
       "JAIPilot": {
         "command": "/absolute/path/to/jaipilot-acp",
         "args": ["acp"],
         "use_custom_mcp": false,
         "use_idea_mcp": false
       }
     }
   }
   ```

4. Disable **Pass custom MCP servers** and **Pass IntelliJ MCP server** for JAIPilot. This bounded
   adapter uses approved local terminals, and rejects supplied MCP servers with an explicit message.
5. Select **JAIPilot** in AI Chat. Choose **Sign in to JAIPilot** when prompted and complete the
   browser sign-in. You can also run `jaipilot-acp --login` in a terminal. Existing CLI sign-in is
   reused; credentials are never placed in `acp.json`.

## Choose an outcome and exact scope

Choose the outcome from the mode selector, or invoke its slash command:

| Outcome | Command |
| --- | --- |
| Generate tests | `/generate_tests --class com.acme.OrderService` |
| Improve coverage | `/improve_coverage --path src/main/java/com/acme` |
| Fix failing tests | `/fix_tests --all` |
| Stabilize flaky tests | `/stabilize_flaky_tests --path src/test/java/com/acme/OrderTest.java` |
| Test current changes | `/test_current_changes --all` |
| Lock existing behavior | `/lock_behavior --selection src/main/java/com/acme/OrderService.java:42-88` |

Use exactly one scope type: `--all`, repeated `--class`, repeated `--path`, or repeated `--selection`.
Quote paths containing spaces. Relative paths resolve from the IDE session's working directory;
`--all` selects its entire Git repository. Java file links attached by the IDE can supply the scope
when no explicit scope is present. The adapter never silently chooses the whole repository. Use
`/help` to see the choices. Add further constraints after the scope flags, for example:

```text
/generate_tests --class com.acme.OrderService Preserve existing fixtures and cover rejected orders.
```

Save editor buffers first. Commands read files on disk. Review both the original local changes and
the final diff; workflow instructions alone do not enforce correctness or preservation.

## Verification, permissions, and cancellation

The adapter asks permission before using the managed service and before each shell command. The IDE
shows the exact command, repository directory, timeout, live terminal output, and exit status. A
rejection stops the workflow. Cancel interrupts service requests, kills the active terminal command,
and releases the terminal. Timeouts also kill and release terminals. Two sessions in one adapter
process cannot mutate the same repository concurrently; separate agent processes remain the user's
responsibility.

The final message includes the service's evidence summary, build/test/coverage fields, elapsed time,
and Git status. Coverage comes from repository-configured tooling. Missing tools, failed checks, and
unmeasured coverage must remain explicit; no coverage or performance result is guaranteed. Review
the complete diff and independently rerun your repository's required checks before accepting it.

## Privacy and current limitations

Selected paths, project metadata, your text request, and approved command output are sent to JAIPilot
and its model provider. A command can include source in its output; inspect commands before approving
them. This adapter deliberately uses the local-tools service protocol: it does not start remote
workspaces, parallel source-upload workers, or bulk source archives. The service uses your existing
subscription and credits.

Commands run with your local user permissions; the ACP adapter is not an operating-system sandbox.
The user remains responsible for command approval, secrets in command output, and diff review. Do
not put credentials in chat, public issues, or agent configuration.

This release supports text, disk-backed Java file context, six stable outcomes, and ephemeral
sessions. It does not support custom MCP servers, images, audio, session restoration, or the CLI's
experimental 100% coverage outcome. It does not alter IntelliJ's bundled Claude Agent or Codex.

## Develop and verify

```sh
deno task check
deno task compile:acp
deno run -A scripts/check-acp.ts dist/jaipilot-acp
```

The tracked CI runs these checks on Linux, macOS, and Windows. Release artifacts are versioned binary
archives for macOS Intel/Apple Silicon, Linux x86-64/ARM64, and Windows x86-64, with SHA-256 checksums.
The registry entry pins the exact release archives and their hashes.
