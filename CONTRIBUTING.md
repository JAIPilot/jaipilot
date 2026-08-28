# Contributing

JAIPilot is a small Java skills and remote-execution plugin. Contributions should make one of its
eight workflows or the eight-tool hosted MCP boundary clearer, safer, or more useful.

## Setup

You need Git, Python 3, and Deno 2.x. Codex and Claude Code are required for distribution changes.

```bash
deno task check
```

## Rules

- Keep plugins/jaipilot limited to manifests, eight skills, UI metadata, SVG assets, one hosted MCP
  URL, and its short README.
- Keep remote execution to explicit bounded source uploads, eight lifecycle tools, and
  agent-selected commands. Do not add another reasoning agent, hooks, a daemon, dashboard,
  installer, package-manager dependency, or automatic repository work.
- Never commit or print credentials, customer source, remote logs, or generated artifacts.
- Keep skill instructions concise and imperative.
- Prefer repository wrappers and configured tools.
- Never tell an agent to discard unrelated work, weaken a gate, or add tooling without approval.
- Report unavailable evidence honestly.
- Forward-test workflow changes on disposable Java repositories.

Before submitting a change:

```bash
git diff --check
deno task check
python3 /path/to/skill-creator/scripts/quick_validate.py plugins/jaipilot/skills/<skill>
python3 /path/to/plugin-creator/scripts/validate_plugin.py plugins/jaipilot
claude plugin validate plugins/jaipilot
```

Include the affected skill or tool, test prompt, repository shape, host version, commands, cleanup
outcome, and observed result. Do not include proprietary source, credentials, or private paths.

## Releases

VERSION, all plugin manifests, and the Claude marketplace entry must match. A v* tag is validated
before GitHub publishes the release. The official OpenAI plugin directory, Codex plugin marketplace,
and Claude Code plugin marketplace are the only distribution channels.

For vulnerabilities, follow [SECURITY.md](SECURITY.md).
