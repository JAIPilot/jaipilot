# Security policy

## Supported version

Security fixes target the latest release and main. The retired runtime line at v4.0.8 is preserved
for reference and is not actively supported.

## Private reporting

Use [GitHub private vulnerability reporting](https://github.com/JAIPilot/jaipilot/security/advisories/new).
Include the affected version, impact, preconditions, minimal reproduction, and suggested mitigation.
Remove secrets, proprietary source, and private repository details.

## Boundary

JAIPilot contains instructions and SVG assets. It has no executable runtime, server, hook,
installer, or telemetry.

The skills instruct the host agent to run commands from the target Java repository. Maven and Gradle
wrappers, build scripts, plugins, annotation processors, tests, and dependencies are executable
code. Review untrusted repositories and use operating-system isolation where appropriate. A skill
is not a sandbox and cannot guarantee that an agent follows every instruction.
