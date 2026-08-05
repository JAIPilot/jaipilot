# Contributing

Thanks for contributing to the JAIPilot Java Enterprise Toolkit Harness.

Before participating, read the [community code of conduct](CODE_OF_CONDUCT.md). For usage questions
and early ideas, start with [GitHub Discussions](https://github.com/JAIPilot/jaipilot/discussions).
Use issues for reproducible defects and proposals with concrete acceptance evidence.

## Development setup

You need Java 17+, Git, Python 3, and a POSIX shell.

```bash
./mvnw -B verify
python3 ./scripts/validate-plugin.py
```

`verify` runs the Java suite, creates JaCoCo XML, builds the shaded plugin runner, and produces the
current-platform bundled distribution.

## Common checks

```bash
./mvnw -B test
java -jar target/jaipilot-toolkit-3.1.2-all.jar inspect --project .
./scripts/smoke-test-install.sh
```

The direct smoke checks structured toolkit-harness discovery. The installer smoke exercises checksum
verification, the bundled runtime, first plugin launch, and cached launch.

## Pull requests

- Keep changes focused and add deterministic tests for behavior, safety gates, races, timeouts, and failure modes.
- Preserve structured JSON on runner stdout.
- Keep model reasoning in the connected coding agent; do not add a provider-specific subprocess.
- Update README, skills, manifests, and project memory when their contract changes.
- Include repeatable before/after evidence for performance-sensitive changes.
- Do not commit `target/`, temporary workspaces, or local configuration.
- Explain the user-visible outcome, verification evidence, safety impact, and explicit boundaries.

Before opening a pull request:

```bash
git diff --check
./mvnw -B verify
python3 ./scripts/validate-plugin.py
./scripts/smoke-test-install.sh
```

## Release publishing

`scripts/release-build.sh` aligns the Maven revision, plugin bootstrap, marketplaces, and all plugin
manifests. A `v*` tag builds checksum-protected macOS/Linux x64/arm64 archives and publishes a
GitHub Release with the plugin-local installer.

## Reporting bugs and security issues

For a bug, include the JAIPilot version, Codex or Claude Code version, Java version, build
tool/version, operating system, command or tool arguments, stderr diagnostics, and a minimal project
when possible.

Follow [SECURITY.md](SECURITY.md) for vulnerabilities; do not disclose them in a public issue.

For support channel guidance, see [SUPPORT.md](SUPPORT.md).
