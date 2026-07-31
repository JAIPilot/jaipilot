# Contributing

Thanks for contributing to JAIPilot.

## Development Setup

- Java 17+
- Git
- A POSIX shell environment for the helper scripts
- Node.js 20+ and npm for package verification

Clone the repo and run:

```sh
./mvnw -B verify
npm ci --ignore-scripts
npm run pack:check
```

This runs the unit tests, integration tests, packaging, and the install smoke test used in CI.

## Common Commands

Build and test:

```sh
./mvnw -B verify
```

Smoke-test the install script:

```sh
./scripts/smoke-test-install.sh
./scripts/smoke-test-npm.sh
```

## Pull Request Guidelines

- keep changes focused and scoped to a clear problem
- include tests for behavioral changes when practical
- update documentation when CLI behavior or installation steps change
- prefer small, reviewable pull requests over broad refactors
- do not commit generated `target/` output

Before opening a pull request, run:

```sh
./mvnw -B verify
npm ci --ignore-scripts
./scripts/smoke-test-install.sh
./scripts/smoke-test-npm.sh
```

## Release Publishing

`scripts/release-build.sh` keeps the Maven revision, Java fallback version, `package.json`, and `package-lock.json` aligned. Tag builds publish the bundled runtime archives to GitHub Releases, then publish the dependency-free `jaipilot` launcher to npm.

The npm package should use npm trusted publishing rather than a long-lived token. Before the first automated publication, a package owner must claim `jaipilot` on npm and configure this repository's `release.yml` workflow as its GitHub Actions trusted publisher with `npm publish` permission. The workflow requests `id-token: write`, uses an OIDC-capable npm CLI, and publishes only after all platform release assets exist. See [npm trusted publishing](https://docs.npmjs.com/trusted-publishers/).

## Reporting Bugs

When filing a bug, include:

- JAIPilot version
- Java version
- Maven version
- operating system
- the command you ran
- relevant logs or failing output
- a minimal sample project, if available

## Security

For security issues, follow [SECURITY.md](SECURITY.md) instead of opening a public issue.
