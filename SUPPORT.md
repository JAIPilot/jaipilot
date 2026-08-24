# Support

- Ask usage questions in [GitHub Discussions](https://github.com/JAIPilot/jaipilot/discussions).
- Report reproducible defects with the
  [bug template](https://github.com/JAIPilot/jaipilot/issues/new?template=bug_report.yml).
- Propose workflow improvements with the
  [feature template](https://github.com/JAIPilot/jaipilot/issues/new?template=feature_request.yml).
- Report vulnerabilities privately through [SECURITY.md](SECURITY.md).

Include the JAIPilot version, Codex or Claude Code version, skill name, exact request, Java project
shape, build tool, commands the agent ran, sanitized output, and expected behavior.

For remote-execution problems, also include whether OAuth completed, the source archive size (not
its contents), workspace ID, process exit code, whether logs were truncated, and whether workspace
destruction succeeded. Never include access tokens, signed upload URLs, private repository contents,
or unsanitized logs.

JAIPilot can improve its skills, hosted MCP, and metadata. It does not own the host agent, project
build, third-party analyzers, repository configuration, or external provider availability. A minimal
public reproduction helps identify which boundary failed.
