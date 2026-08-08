## Outcome

What user-visible result does this change deliver?

## Why

What problem or product gap does it address? Include the root cause for fixes.

## Evidence

- [ ] Focused tests passed
- [ ] `git diff --check`
- [ ] `./mvnw -B verify`
- [ ] Plugin and skill validators, when those surfaces changed
- [ ] `./scripts/smoke-test-install.sh`, when distribution changed
- [ ] Same-boundary raw timings, median, and p95, when a hot path changed

Summarize relevant test counts, coverage, fixture/revision, or failure-recovery evidence here.

## Safety and scope

Describe effects on clean-build proof, execution evidence, privacy, determinism, cancellation,
recovery, drift detection, and exact receipts. Write “no change” where applicable.

## Reviewer notes

Call out intentional tradeoffs, boundaries, or follow-up work.
