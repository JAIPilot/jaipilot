# JAIPilot

JAIPilot provides three Java engineering skills:

- jaipilot-review-diff
- jaipilot-generate-tests
- jaipilot-clean-java

The host agent uses the target repository's existing Git, Maven or Gradle wrapper, tests, and
configured analysis tools. JAIPilot adds no executable, MCP server, hook, installer, background
process, dashboard, account, or telemetry.

Ask the agent to review a Java diff, add meaningful Java tests, or simplify Java without changing
behavior. The selected skill establishes scope, preserves unrelated work, runs applicable
repository-native checks, and reports both evidence and limitations.

JAIPilot is guidance, not an enforcement engine. It does not silently add build plugins, lower
quality gates, or claim that unavailable evidence passed.

Learn more at <https://github.com/JAIPilot/jaipilot>.
