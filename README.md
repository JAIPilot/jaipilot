<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/jaipilot-logo-dark.svg">
    <source media="(prefers-color-scheme: light)" srcset="assets/jaipilot-logo.svg">
    <img src="assets/jaipilot-logo.svg" alt="JAIPilot" width="96" height="96">
  </picture>
</p>

<h1 align="center">JAIPilot — workflow-based Java testing in your IDE</h1>

<p align="center"><strong>Generate tests · Improve coverage · Fix failing tests<br>
Stabilize flaky tests · Test current changes · Lock existing behavior</strong></p>

JAIPilot's ACP adapter brings six testing outcomes to IntelliJ AI Assistant and other ACP clients
with terminal support. Choose an outcome, supply an exact Java scope, and review the commands, test
results, coverage evidence, and complete diff in your IDE. Use your existing JAIPilot account,
subscription, and credits.

## Get started in IntelliJ

The ACP Registry submission requires upstream review before JAIPilot appears in the public
**Settings → Tools → AI Assistant → Agents** catalog. You can use it now as a custom agent:

1. Download your platform's archive and checksum from the
   [JAIPilot ACP release](https://github.com/JAIPilot/jaipilot/releases/latest), verify the hash, and extract it.
2. Add the extracted executable as **JAIPilot** using AI Chat → **Add Custom Agent**.
3. Disable **Pass custom MCP servers** and **Pass IntelliJ MCP server** for this bounded adapter.
4. Select JAIPilot in AI Chat and sign in when prompted.
5. Choose an outcome or enter a slash command with scope:

```text
/generate_tests --class com.acme.OrderService
/improve_coverage --selection src/main/java/com/acme/OrderService.java:42-88
/fix_tests --all
```

[Complete IntelliJ setup, all outcomes, and limitations](docs/intellij-acp.md).

Requirements: Git, your project's supported JDK and build tools, AI Assistant with ACP terminal
support, and a JAIPilot account. The release executable includes its runtime; JAIPilot does not
install Java or build tools. No npm or npx distribution is published.

## Exact scope and evidence

Use exactly one scope type: the entire Git repository (`--all`), repeated paths (`--path`), Java
classes (`--class`), or selected line ranges (`--selection`). Attached Java file links can supply
scope when no explicit flags are present. Relative paths use the IDE session's working directory.
Save editor buffers before running, since commands use the files on disk.

The workflows instruct the service to preserve production source and unrelated work, follow the
repository's existing test conventions, run focused verification and the final test suite, and
measure coverage only through configured tools. Instructions alone do not enforce correctness.
Review the full diff and rerun your required checks. Missing coverage, failed checks, and blockers
remain explicit; no coverage target or speedup is guaranteed.

## Permissions and privacy

Commands run in local IDE terminals with your user permissions. JAIPilot asks for service consent
and approval for each command, displays execution evidence, and supports cancellation and timeouts.
Rejecting a command stops the workflow. This adapter uses the local-tools service protocol and does
not start remote workspaces or bulk source-upload workers.

Selected paths, project metadata, your text request, and approved command output are sent to
JAIPilot's managed model service and its provider. Commands can read source into their output;
inspect them before approving them. Keep credentials and private logs out of public issues.

This release supports six stable outcomes and ephemeral sessions. Custom MCP servers, image/audio
prompts, session restoration, and the CLI's experimental 100% coverage outcome are unavailable.

## Development

```sh
deno task check
deno task compile:acp
deno run -A scripts/check-acp.ts dist/jaipilot-acp
```

CI verifies source and the packaged ACP handshake on Linux, macOS, and Windows. The release workflow
publishes only ACP archives and their SHA-256 checksums for macOS Intel/Apple Silicon, Linux
x86-64/ARM64, and Windows x86-64. ACP stdio contains only protocol messages; diagnostics use stderr.

The earlier CLI remains available at [v1.0.2](https://github.com/JAIPilot/jaipilot/releases/tag/v1.0.2)
with its [versioned documentation](https://github.com/JAIPilot/jaipilot/blob/v1.0.2/README.md).

[JAIPilot.com](https://www.jaipilot.com/) ·
[Report an issue](https://github.com/JAIPilot/jaipilot/issues) · [MIT License](LICENSE)
