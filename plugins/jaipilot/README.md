# JAIPilot

JAIPilot provides three Java engineering skills:

- jaipilot-review-diff
- jaipilot-generate-tests
- jaipilot-clean-java

The host agent uses the target repository's existing Git, Maven or Gradle wrapper, tests, and
configured analysis tools. JAIPilot adds no executable, MCP server, hook, installer, background
process, dashboard, account, or telemetry.

Ask the agent to review a Java diff, raise meaningful per-class test coverage, or safely reduce,
consolidate, modernize, and optimize Java. The test skill can assign one production class to each
available worker in bounded, isolated waves. The clean skill composes fail-closed unused removal,
behavior-locked consolidation, verified stable upgrades, and measured performance work; it can use
the other two skills for tests and final review. Every selected skill establishes scope, preserves
unrelated work, runs applicable repository-native checks, and reports evidence and limitations.

JAIPilot is guidance, not an enforcement engine. It does not silently add build plugins, lower
quality gates, or claim that unavailable evidence passed.

Learn more at <https://github.com/JAIPilot/jaipilot>.
