<div align="center">
  <img src="docs/assets/jaipilot-logo.svg" alt="JAIPilot logo" width="160" />
  <h1>JAIPilot</h1>
  <p><strong>The enterprise Java engineering toolkit for Codex and Claude Code.</strong></p>
  <p>
    <a href="https://github.com/JAIPilot/jaipilot-cli/actions/workflows/ci.yml">
      <img src="https://github.com/JAIPilot/jaipilot-cli/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI">
    </a>
    <a href="https://github.com/JAIPilot/jaipilot-cli/releases">
      <img src="https://img.shields.io/github/v/release/JAIPilot/jaipilot-cli" alt="Release">
    </a>
    <a href="LICENSE">
      <img src="https://img.shields.io/github/license/JAIPilot/jaipilot-cli" alt="License">
    </a>
  </p>
</div>

JAIPilot installs into Codex and Claude Code as a skills-plus-code plugin. It helps coding agents
generate efficient high-coverage Java unit tests and perform OpenRewrite-first clean-code
refactoring, then proves each candidate with the repository's real build, test-execution evidence,
fresh coverage, scope checks, drift detection, and transactional apply.

The host agent performs contextual reasoning and edits. JAIPilot provides deterministic local
orchestration and proof. It has no hosted backend, nested model invocation, provider API key, or
source upload.

## What the toolkit provides

- High-coverage JUnit generation for named, changed, all, or freshly measured under-covered classes.
- Pinned, exactly scoped OpenRewrite `CodeCleanup` and `CommonStaticAnalysis` recipes before agent review.
- Contextual clean-code remediation for correctness, null/error handling, resource safety,
  concurrency, dead code, complexity, performance waste, readability, and maintainability.
- Clean baseline and candidate builds using the project's Maven or Gradle wrapper when valid.
- Non-zero Surefire, Failsafe, or Gradle XML execution evidence for changed tests.
- Fresh JaCoCo coverage feedback and optional per-target line-coverage apply gates.
- Isolated workspaces, strict write allowlists, deletion and symlink rejection, candidate and live
  drift protection, and transactional apply with rollback.
- A bundled, non-interactive JSON runner used internally by the Codex and Claude Code skills.

## Install in Codex

Add the JAIPilot marketplace and install the plugin:

```bash
codex plugin marketplace add JAIPilot/jaipilot-cli --ref main
codex plugin add jaipilot@jaipilot
```

Start a new Codex thread after installation. The plugin contributes
`jaipilot-generate-tests` and `jaipilot-clean-java`.

For local development:

```bash
codex plugin marketplace add .
codex plugin add jaipilot@jaipilot
```

## Install in Claude Code

```bash
claude plugin marketplace add JAIPilot/jaipilot-cli
claude plugin install jaipilot@jaipilot
```

Run `/reload-plugins` after installation. During development, load the plugin directly:

```bash
claude --plugin-dir ./plugins/jaipilot
```

## Plugin runtime

The plugin ships a pinned local installer. On first use it downloads the matching GitHub release,
verifies the published SHA-256 checksum, and caches the bundled Java runtime in the user's private
data directory. Node.js and npm are not required.

Managed or offline environments can point the plugin at an existing executable with
`JAIPILOT_TOOLKIT_EXECUTABLE`. Release platforms are macOS and Linux on x64 and arm64/aarch64.

## Ask naturally

```text
Generate strong unit tests for OrderService and reach at least 90% line coverage.
Generate tests for changed production classes in this project.
Clean and refactor the changed Java classes, then apply only the verified result.
Review all Java production classes for clean-code issues, but show me the candidate before apply.
```

The skills drive the complete inspect → prepare → edit → validate → apply/discard transaction.

## Internal runner contract

The skills invoke a bundled, short-lived internal runner whose structured operations form this
transaction:

`inspect → prepare-tests | prepare-cleanup → validate → apply | discard`

Operations emit structured JSON. Workflow state is stored locally with owner-only permissions so
short-lived agent command invocations can continue the same isolated run. Operations on one run
are file-locked; globally, at most four runs and one run per real project may be active. Runs expire
after two hours.

Apply requires both a fresh successful validation and the explicit `--confirm` flag. Preparing,
editing, validating, and discarding do not change the live source tree.

## Unit-test workflow

Target modes are deterministic:

- `classes`: exact class names, fully qualified names, or source paths.
- `changed`: changed production classes.
- `coverage`: classes below a threshold from a newly generated JaCoCo snapshot.
- `all`: every discovered production class, only when explicitly requested.

The isolated candidate may edit only Java under `src/test/java`. Validation runs a clean build,
proves every changed test executed, reports before/after line and branch coverage when JaCoCo is
configured, and blocks apply when the requested coverage goal is unmet.

## Clean-code and refactoring workflow

Cleanup targets `classes`, `changed`, or explicitly requested `all`. JAIPilot first runs pinned
OpenRewrite recipes in the isolated workspace using a temporary exact-source precondition. It does
not persist a Rewrite plugin or recipe declaration in the target project.

The agent then reviews and refines the deterministic candidate using repository context. Production
edits remain restricted to selected Java files; directly relevant Java tests may also change.
Behavior must be preserved unless a regression test proves a defect. Validation independently
checks scope, clean build behavior, changed-test execution, build drift, candidate drift, and live
worktree drift before transactional apply.

## Is this a SonarQube replacement?

JAIPilot targets a different layer. It aims to outperform finding-only workflows at the complete
local **change → proof → safe apply** journey, not copy SonarQube's centralized analysis platform.

| Capability | JAIPilot | SonarQube | OpenRewrite alone | Direct coding agent |
| --- | --- | --- | --- | --- |
| Primary job | Agent-driven Java tests and verified remediation | Static/security analysis and governance | Deterministic transformations | General reasoning and edits |
| Creates project-specific tests | Yes, through Codex or Claude Code | Not an end-to-end workflow | No | Yes |
| Deterministic cleanup recipes | OpenRewrite first, then contextual review | Rule-dependent fixes | Core strength | Model-dependent |
| Clean behavioral proof before apply | Required | Does not own the local source transaction | Build integration is user-defined | Host-dependent |
| Changed-test execution proof | Required | Imports test and coverage measures | No built-in agent workflow | Host-dependent |
| Fresh coverage-driven targeting | Yes | Central coverage gates and dashboards | No | Must be assembled |
| Isolated candidate and strict scope | Built in | Does not own local edits | Recipe scope | Host-dependent |
| Drift-safe transactional merge | Built in | Not its role | Not its role | Host-dependent |
| Formal taint analysis and hotspots | Not a substitute | Stronger | Recipe-dependent | Not formal analysis |
| Governance, portfolios, compliance, history | Not a substitute | Stronger | No | No |
| Local/private operation | Local; no JAIPilot backend | Self-hosted or cloud | Local | Depends on host/provider |

Use SonarQube alongside JAIPilot when formal security/data-flow analysis or centralized governance
matters. Any superiority claim should compare identical revisions and boundaries using accepted
verified fixes, false positives, escaped defects, regressions, generated-test quality, coverage,
elapsed time, reviewer actions, cancellation, and recovery.

## Development and verification

```bash
./mvnw -B verify
python3 ./scripts/validate-plugin.py
python3 ~/.codex/skills/.system/plugin-creator/scripts/validate_plugin.py plugins/jaipilot
claude plugin validate .
./scripts/smoke-test-install.sh
```

`verify` runs the Java suite, creates JaCoCo XML, builds the shaded internal runner, and creates the
current platform bundle. Distribution smoke tests exercise structured project inspection,
checksum-verified first use, and cached plugin launch.

Release tags publish checksum-protected platform archives and the standalone plugin installer.

## License

[MIT](LICENSE)
