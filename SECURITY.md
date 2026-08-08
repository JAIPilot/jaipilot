# Security policy

## Supported versions

Security fixes are expected to land in the latest released version and the `main` branch. Older
releases may not receive fixes.

## Report a vulnerability privately

Do not open a public issue for an undisclosed vulnerability.

Use [GitHub private vulnerability reporting](https://github.com/JAIPilot/jaipilot/security/advisories/new)
to contact the maintainers. Include:

- a clear description of the issue and affected JAIPilot versions;
- impact and realistic attack preconditions;
- minimal reproduction steps or a proof of concept;
- relevant operating system, filesystem, build-tool, and repository conditions;
- a suggested mitigation, if known.

Remove third-party secrets and proprietary source from the report. The maintainers will acknowledge
the report after triage and coordinate disclosure and remediation when the issue is confirmed.

## Security boundaries

JAIPilot is local and backend-free, but it intentionally executes the target project's Maven or
Gradle build and pinned OpenRewrite tooling. Treat repository build scripts, wrappers, plugins, and
dependencies as executable code. Review untrusted projects before running them.

JAIPilot's scope, symlink, drift, isolated-proof, and exact-receipt controls protect its evidence
boundary. OpenRewrite intentionally edits the user-selected live worktree, which must remain under the
host agent's Git/recovery control. These controls are not a sandbox for malicious build scripts and do
not replace operating-system isolation for untrusted repositories.
