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

- **Mutation score** = killed mutations ÷ actionable mutations. Actionable mutations exclude
  PIT's `EQUIVALENT`, `NON_VIABLE`, and error/unfinished statuses; timed-out mutations remain in the
  denominator so timeouts cannot inflate the score.
- **Test strength** = killed mutations ÷ covered actionable mutations
  (`killed + survived + timed out`).
- **Mutation evidence** includes total, killed, survived, no-coverage, timed-out, error, and
  equivalent and non-viable counts plus class, method, line, mutator, description, reports, commands,
  and elapsed time.

Test generation always runs PIT and defaults to a 70% mutation target. Cleanup uses the same gate
whenever a directly related test changes. A run with no scorable mutations cannot satisfy a positive
target, and an error or unfinished PIT status makes the proof incomplete. JAIPilot does not modify
the project's committed Maven or Gradle configuration.

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

The automatic diff proof uses the same evidence weights, with the final 5% representing a fresh
clean full-suite build rather than changed-test-file execution. It scores only executable lines,
branches, and PIT mutations located on added or modified lines; whole-class coverage remains in the
report as context. Its defaults are 90% changed-line coverage, 85% changed-branch coverage, and 80%
changed-line mutation score. A component with no executable changed element is reported as not
applicable instead of being assigned a fabricated percentage.

New-code quality compares stable rule, path, symbol, and severity identity with the Git baseline.
Existing findings remain visible in the whole-file report but do not become new debt. New or
severity-escalated critical/high findings subtract 20 points each, medium findings subtract 3, and
low findings subtract 1 from a 100-point new-code score. The default requires 90 and zero new or
escalated critical/high findings. Deletion-only diffs require the clean build but report coverage,
mutation, and new-code findings as not applicable.

## Interpretation boundary

The source analyzer is fast, local, syntactic, and remediation-oriented. It does not claim formal
interprocedural taint analysis, vulnerability certification, centralized policy governance,
portfolios, compliance reporting, or centralized historical dashboards. Compare revisions only with
identical scope and build conditions, and keep the raw evidence alongside every score.

## Dashboard aggregation

The local dashboard does not invent an aggregate “better code” score. It presents raw cumulative
outcomes from applied JAIPilot transactions and the latest independently scored evidence:

- coverage change is the sum of per-target after-minus-before line-coverage percentage points;
- quality change is the sum of selected-source after-minus-before quality-score points;
- resolved findings and removed remediation minutes come directly from validation deltas;
- killed mutations and execution-proven changed tests come from the applied candidate's final
  mutation and test-execution evidence;
- changed-code proof counts distinguish attempted proofs from passed proofs.

Validation can run repeatedly while an agent improves a candidate. Only the latest apply-ready
validation for that run is credited, and only once, when apply succeeds. Failed commands, failed
proofs, and discarded candidates remain visible in usage/activity statistics but add no improvement.
