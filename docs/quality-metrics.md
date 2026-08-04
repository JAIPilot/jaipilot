# Quality metrics

JAIPilot reports scores on a 0–100 scale together with raw findings and evidence. Scores prioritize
work and make changes comparable; they never replace review or behavioral proof.

## Source quality

The analyzer reports bug risks, code smells, modernization opportunities, complexity, duplication,
and performance findings. Each finding includes a stable rule ID, severity, file, line, symbol,
remediation guidance, estimated effort, and whether a deterministic quick fix is available.

For the selected production files:

- **Reliability** = `100 - weighted bug-risk density`. Critical/high bug risks weigh 20 points and
  medium/low risks weigh 5, normalized by at least one thousand lines of code.
- **Maintainability** = `100 - 2 × debt ratio`, where debt ratio is estimated remediation minutes as
  a percentage of 30 minutes per source line.
- **Complexity** starts at 100 and subtracts 3 points for each maximum cyclomatic point above 10 and
  2 points for each average cyclomatic point above 5. Cognitive complexity and nesting are also
  returned per method and as findings.
- **Duplication** = `100 - 2 × duplicated-line percentage`.
- **Overall quality** = 40% maintainability + 30% reliability + 15% complexity + 15% duplication.

All scores are clamped to 0–100 and rounded to one decimal. Results also include source files and
bytes analyzed, lines of code, method count, severity counts, maximum and average cyclomatic
complexity, maximum cognitive complexity, duplicated lines, debt minutes, and analyzer time.

Cleanup validation blocks apply when selected code has a parse failure, introduces a new critical or
high finding, or reduces the overall quality score by more than rounding tolerance.

## Mutation testing

JAIPilot uses pinned PIT versions and scopes work to the selected production classes and likely or
changed tests. It reports every mutation status and up to 100 actionable survivors.

- **Mutation score** = killed mutations ÷ all non-equivalent mutations.
- **Test strength** = killed mutations ÷ covered mutations (`killed + survived`).
- **Mutation evidence** includes total, killed, survived, no-coverage, timed-out, error, and
  equivalent counts plus class, method, line, mutator, description, reports, commands, and elapsed time.

Test generation defaults to a 70% mutation target. A run with no scorable mutations cannot satisfy a
positive target. JAIPilot does not modify the project's committed Maven or Gradle configuration.

## Test quality

The composite test-quality score is:

- 25% average selected-target line coverage;
- 20% average selected-target branch coverage;
- 35% targeted mutation score;
- 15% test strength;
- 5% proof that every changed test file executed in the clean build.

Missing components contribute zero. `evidenceCompletenessPercent` reports which weighted components
were actually available, so consumers can distinguish a low score from incomplete evidence. Results
also include changed and executed test-file counts. Grades are `EXCELLENT` (90+), `STRONG` (80+),
`GOOD` (70+), `NEEDS_WORK` (50+), and `WEAK` (below 50).

## Interpretation boundary

The source analyzer is fast, local, syntactic, and remediation-oriented. It does not claim formal
interprocedural taint analysis, vulnerability certification, centralized policy governance,
portfolios, compliance reporting, or historical dashboards. Compare revisions only with identical
scope and build conditions, and keep the raw evidence alongside every score.
