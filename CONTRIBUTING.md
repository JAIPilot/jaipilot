# Contributing

JAIPilot is a small, skills-only plugin. Contributions should make one of its three Java workflows
clearer, safer, or more useful without adding runtime machinery.

## Setup

You need Git and Python 3. Codex and Claude Code are useful for host-level validation.

~~~bash
python3 scripts/validate-plugin.py
~~~

## Rules

- Keep plugins/jaipilot limited to manifests, skills, UI metadata, SVG assets, and its short README.
- Do not add MCP, a runtime, CLI, hook, daemon, installer, backend, package-manager dependency, or
  automatic repository work.
- Keep skill instructions concise and imperative.
- Prefer repository wrappers and configured tools.
- Never tell an agent to discard unrelated work, weaken a gate, or add tooling without approval.
- Report unavailable evidence honestly.
- Forward-test workflow changes on disposable Java repositories.

Before submitting a change:

~~~bash
git diff --check
python3 scripts/validate-plugin.py
python3 /path/to/skill-creator/scripts/quick_validate.py plugins/jaipilot/skills/<skill>
python3 /path/to/plugin-creator/scripts/validate_plugin.py plugins/jaipilot
claude plugin validate plugins/jaipilot
~~~

Include the affected skill, the test prompt, repository shape, host version, commands, and observed
result. Do not include proprietary source, credentials, or private paths.

## Releases

VERSION, all plugin manifests, and the Claude marketplace entry must match. A v* tag is validated
before GitHub publishes the release. The official OpenAI plugin directory, Codex plugin
marketplace, and Claude Code plugin marketplace are the only distribution channels.

For vulnerabilities, follow [SECURITY.md](SECURITY.md).
