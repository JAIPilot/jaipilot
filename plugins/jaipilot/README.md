# JAIPilot

JAIPilot gives Codex and Claude Code six focused Java engineering workflows:

- `jaipilot-optimize-java`: turn a bounded Java change into its smallest verified form;
- `jaipilot-generate-tests`: lock behavior or run a meaningful coverage campaign;
- `jaipilot-clean-java`: remove proven waste, simplify, modernize, or optimize safely;
- `jaipilot-review-diff`: find behavioral risk, unnecessary code, and missing proof;
- `jaipilot-fast-execution`: reduce Java verification time with safe resource-aware execution; and
- `jaipilot-remote-java`: offload long commands for an exact committed GitHub revision.

The customer's coding agent remains the only planner and editor. JAIPilot contributes engineering
workflows and, when explicitly selected, disposable execution hardware. It does not install hooks,
watch repositories, run background scans, commit, push, or merge.

Local skills use the repository's existing Git, Maven or Gradle wrapper, tests, and configured
analysis tools. Remote execution excludes staged, unstaged, and untracked files; returns no edits;
and receives no GitHub write credential. The current remote service is a limited operator preview
requiring Deno 2.x, the JAIPilot GitHub App, and separately provisioned authentication. It has no
customer VPN/VPC, private artifact repository, internal database, or enterprise-service access.

Learn more at <https://github.com/JAIPilot/jaipilot>.
