# Security policy

## Supported version

Security fixes target the latest release and main. The retired runtime line at v4.0.8 is preserved
for reference and is not actively supported.

## Private reporting

Use [GitHub private vulnerability reporting](https://github.com/JAIPilot/jaipilot/security/advisories/new).
Include the affected version, impact, preconditions, minimal reproduction, and suggested mitigation.
Remove secrets, proprietary source, and private repository details.

## Boundary

JAIPilot contains Java workflow instructions, SVG assets, and a small Deno MCP client. It installs
no hook, watcher, daemon, dashboard, package-manager dependency, or automatic repository task. The
MCP server starts with an enabled plugin but performs no network operation until one of its tools is
called.

The skills instruct the host agent to run commands from the target Java repository. Maven and Gradle
wrappers, build scripts, plugins, annotation processors, tests, and dependencies are executable
code. Review untrusted repositories and use operating-system isolation where appropriate. A skill
is not a sandbox and cannot guarantee that an agent follows every instruction.

JAIPilot Remote checks out only an exact committed GitHub SHA into a TTL-bounded Daytona workspace.
The checkout receives a short-lived repository-scoped read token and no GitHub write credential.
The sandbox has outbound network access and executes arbitrary repository commands selected by the
host agent. Remote edits are disposable and are not synchronized locally or pushed.

The distributed adapter contains the non-secret production API URL but no bearer, GitHub token,
private key, Daytona key, or customer source. Never put `JAIPILOT_CLOUD_TRIGGER_SECRET` in a
prompt, repository, command argument, issue, or log. Public customer authentication is not shipped;
the MCP remains a limited operator preview.
