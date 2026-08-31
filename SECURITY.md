# Security policy

## Supported version

Security fixes target the latest release and main. The retired runtime line at v4.0.8 is preserved
for reference and is not actively supported.

## Private reporting

Use
[GitHub private vulnerability reporting](https://github.com/JAIPilot/jaipilot/security/advisories/new).
Include the affected version, impact, preconditions, minimal reproduction, and suggested mitigation.
Remove secrets, proprietary source, and private repository details.

## Boundary

JAIPilot contains Java workflow instructions, SVG assets, and one hosted MCP URL. It installs no
hook, watcher, daemon, dashboard, package-manager dependency, or automatic repository task. The host
contacts the MCP service only when it connects or invokes a remote tool.

The skills instruct the host agent to run commands from the target Java repository. Maven and Gradle
wrappers, build scripts, plugins, annotation processors, tests, and dependencies are executable
code. Review untrusted repositories and use operating-system isolation where appropriate. A skill is
not a sandbox and cannot guarantee that an agent follows every instruction.

JAIPilot Remote accepts one explicit, short-lived ZIP produced by `git archive` from a declared
exact commit. Staged, unstaged, untracked, ignored, and `.git` content is excluded. The
service binds uploads and builds to the signed-in user; enforces byte, digest, commit-identity,
concurrency, quota, and timeout bounds; and runs each attempt in a fresh AWS CodeBuild container
with no persistent workspace or cache. The container has outbound network access and executes the
repository command selected by the host agent. It receives no GitHub write or AWS control-plane
credential. Private source is deleted on terminal reads or cancellation, with a one-day S3
lifecycle backstop. Remote edits are disposable and are not synchronized locally or pushed.

`git archive` does not detect a secret already committed to the repository. Never upload a commit
containing credentials, personal data, or source that is unsuitable for this public beta.

The plugin contains only the production MCP URL—no bearer, cloud credential, GitHub token, private
key, runtime, or customer source. OAuth tokens are issued and stored through the host's standard MCP
authentication flow. Never copy a token into a prompt, repository, command argument, issue, or log.
