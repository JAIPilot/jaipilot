# Contributing

Thanks for contributing to JAIPilot MCP.

## Development setup

You need Java 17+, Git, a POSIX shell, and Node.js 20+ with npm.

```bash
./mvnw -B verify
npm ci --ignore-scripts
npm test
```

`verify` runs the Java suite, creates JaCoCo XML, builds the shaded stdio server, and produces the current-platform bundled distribution.

## Common checks

```bash
./mvnw -B test
node scripts/smoke-test-mcp.mjs java -jar target/jaipilot-mcp-2.0.0-all.jar
./scripts/smoke-test-install.sh
./scripts/smoke-test-npm.sh
npm run pack:check
```

The MCP smoke test performs a real initialize and tools/list exchange and rejects non-JSON stdout. Installer and npm smokes exercise checksum verification, the bundled runtime, first launch, and cached launch.

## Pull requests

- Keep changes focused and add deterministic tests for behavior, safety gates, races, timeouts, and failure modes.
- Preserve stdout exclusively for JSON-RPC.
- Keep model reasoning in the connected coding agent; do not add a provider-specific subprocess to the server.
- Update README, skills, manifests, and project memory when their contract changes.
- Include repeatable before/after evidence for performance-sensitive changes.
- Do not commit `target/`, npm packs, temporary workspaces, or local configuration.

Before opening a pull request:

```bash
git diff --check
./mvnw -B verify
npm ci --ignore-scripts
npm test
./scripts/smoke-test-install.sh
./scripts/smoke-test-npm.sh
```

## Release publishing

`scripts/release-build.sh` aligns the Maven revision, `package.json`, `package-lock.json`, and all plugin manifests. A `v*` tag builds checksum-protected macOS/Linux x64/arm64 archives, publishes a GitHub Release, and then publishes the dependency-free `jaipilot` npm package.

npm publishing uses trusted publishing rather than a long-lived token. A package owner must claim `jaipilot` and configure `.github/workflows/release.yml` as the package's GitHub Actions trusted publisher. The workflow requests `id-token: write` and publishes npm only after all GitHub release assets exist. See [npm trusted publishing](https://docs.npmjs.com/trusted-publishers/).

## Reporting bugs and security issues

For a bug, include the JAIPilot version, MCP host, Java version, build tool/version, operating system, tool name and arguments, stderr diagnostics, and a minimal project when possible.

Follow [SECURITY.md](SECURITY.md) for vulnerabilities; do not disclose them in a public issue.
