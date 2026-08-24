# JAIPilot

JAIPilot bundles four Java engineering skills with an optional remote-execution MCP server:

- jaipilot-review-diff
- jaipilot-generate-tests
- jaipilot-clean-java
- jaipilot-remote-java

The host agent owns reasoning, edits, shell commands, retries, cancellation, Git, and user
interaction. The first three skills use the repository's existing Git, Maven or Gradle wrapper,
tests, and configured analysis tools. The remote skill lets the host agent run long builds, tests,
analyzers, profilers, or benchmarks on a disposable Java workspace at one exact committed GitHub
SHA.

Remote execution never includes staged, unstaged, or untracked local files. It does not synchronize
remote edits back to the local checkout and receives no GitHub write credential. It is currently an
operator preview requiring Deno 2.x, the JAIPilot GitHub App, and separately provisioned
authentication. The shared workspace has public outbound access but no customer VPN/VPC, private
artifact repository, internal database, or enterprise service connectivity; those checks remain
local.

JAIPilot does not install hooks, watch repositories, run automatically, lower quality gates, or
claim unavailable evidence passed.

Learn more at <https://github.com/JAIPilot/jaipilot>.
