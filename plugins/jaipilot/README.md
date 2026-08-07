# JAIPilot

**Java Enterprise Toolkit Harness for Codex and Claude Code.**

JAIPilot provides high-quality Java unit testing and code cleanup. Its Agent Skills guide focused
test generation and OpenRewrite-first remediation while the toolkit harness proves changes with the real
build, JaCoCo coverage, PIT mutation testing, enterprise quality scorecards, drift detection, and
ArchUnit architecture analysis before safe apply.

Its automatic Git diff guard reviews newly committed and working-tree Java production changes before
the agent finishes. Proof is cached only for the exact diff fingerprint and requires a clean build,
90% changed-line coverage, 85% changed-branch coverage, 80% changed-line PIT, a 90 new-code quality
score, and no new critical/high findings by default.
Changed production classes must also have complete ArchUnit evidence and zero package-cycle
violations.

The first toolkit invocation also starts a private live impact dashboard on
`http://127.0.0.1:7433/`. JAIPilot selects a free loopback port when 7433 is occupied; run
`jaipilot dashboard` to retrieve the active URL. The dashboard shows local usage, verified proof,
and applied improvement metrics without uploading telemetry, source, or paths.

The plugin includes `jaipilot-review-diff`, `jaipilot-generate-tests`, and `jaipilot-clean-java`.

Learn more at <https://github.com/JAIPilot/jaipilot>.
