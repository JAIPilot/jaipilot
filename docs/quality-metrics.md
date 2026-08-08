# Quality metrics

JAIPilot returns raw evidence with every 0–100 score. Scores prioritize work; they do not replace
review or behavioral proof.

## Source quality

Each finding has a stable rule ID, severity, file, line, symbol, remediation guidance, estimated
effort, and deterministic-fix availability.

- **Reliability** subtracts weighted bug-risk density. Critical/high bug risks weigh 20 and
  medium/low risks weigh 5, normalized by at least one thousand lines.
- **Maintainability** is `100 - 2 × debt ratio`, where debt is estimated remediation minutes against
  30 minutes per source line.
- **Complexity** starts at 100 and penalizes maximum cyclomatic complexity above 10 and average
  cyclomatic complexity above 5. Cognitive complexity and nesting are also returned directly.
- **Duplication** is `100 - 2 × duplicated-line percentage`.
- **Overall quality** is 40% maintainability, 30% reliability, 15% complexity, and 15% duplication.

Scores are clamped to 0–100 and rounded to one decimal. The result includes source files/bytes/lines,
method count, severity counts, cyclomatic and cognitive complexity, duplicated lines, debt minutes,
parse failures, and analyzer time.

## Changed-code quality

New-code quality compares stable rule, path, symbol, and severity identities with the Git baseline.
Existing brownfield findings remain visible but do not become new debt. A new or escalated
critical/high finding subtracts 20 points, medium 3, and low 1 from 100. Proof defaults require a 90
score, zero new/escalated critical/high findings, and complete parsing.

## Coverage and test execution

Coverage is read only from fresh JaCoCo XML produced by the isolated clean build. Module and fully
qualified class identity must match; missing or ambiguous target evidence fails closed. Proof gates
only executable added/modified lines and branches, while whole-class coverage remains context.

Changed sources that map deterministically to an executable test class require matching fresh Maven
Surefire/Failsafe or Gradle test XML evidence. Plain helpers are reported explicitly. This is
class-level discovery evidence, not a claim that every changed method ran. Malformed, contradictory,
skipped-only, stale, or structurally invalid reports do not prove execution.

## Mutation testing

Targeted PIT reports:

- **Mutation score** = killed ÷ actionable mutations;
- **Test strength** = killed ÷ covered actionable mutations; and
- totals for killed, survived, no-coverage, timed-out, error, equivalent, and non-viable mutations.

Equivalent/non-viable mutations are excluded from the actionable denominator; timeouts remain.
Unfinished/error PIT evidence is incomplete. No scorable mutation cannot satisfy a positive target.
The changed-diff default is 80%.

## Architecture

Pinned ArchUnit analyzes freshly compiled Maven `target/classes` and Gradle
`build/classes/*/main`. Project-authored architecture tests remain part of the real build. JAIPilot
adds `JAI-ARCH-001` for direct package dependency cycles involving changed production classes.

Each violation contains origin/target classes, source path/line, the package cycle, affected target,
and remediation. Missing target bytecode, ambiguous module identity, or truncated search fails closed.

## Applicability

Build/test-only and deletion-only diffs still require a clean build. A gate without an executable or
production target is `not_applicable`; it is not scored as zero and is not called passed. The proof
receipt records the applicability decision so the dashboard can distinguish `PASSED`, `FAILED`,
`REQUIRED`, `STALE`, and `NOT APPLICABLE`.

## Dashboard interpretation

The dashboard's default is the latest whole-project snapshot: component scores, counts, complexity,
duplication, debt, parse status, and bounded actionable findings. A matching proof fingerprint reveals
the proof gates. A mismatch hides old facts.

Observed impact is only the delta between consecutive repository snapshots. It is not an attribution
claim, an applied-transaction counter, a usage metric, or an organization-wide history.

## Boundary

The analyzer is local, syntactic, and remediation-oriented. It does not claim formal interprocedural
taint analysis, vulnerability certification, centralized policy governance, portfolios, compliance,
or long-term centralized history. Compare revisions only with identical scope, build conditions, and
raw evidence.
