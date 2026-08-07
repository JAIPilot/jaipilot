# JAIPilot

**Java Enterprise Toolkit Harness for Codex and Claude Code.**

JAIPilot provides high-quality Java unit testing and code cleanup. Its Agent Skills guide focused
test generation and OpenRewrite-first remediation while the toolkit harness proves changes with the real
build, JaCoCo coverage, PIT mutation testing, enterprise quality scorecards, drift detection, and
safe apply.

Its automatic Git diff guard reviews newly committed and working-tree Java production changes before
the agent finishes. Proof is cached only for the exact diff fingerprint and requires a clean build,
90% changed-line coverage, 85% changed-branch coverage, 80% changed-line PIT, a 90 new-code quality
score, and no new critical/high findings by default.

The plugin includes `jaipilot-review-diff`, `jaipilot-generate-tests`, and `jaipilot-clean-java`.

Learn more at <https://github.com/JAIPilot/jaipilot>.
