---
name: jaipilot-maintainer-intent
description: Research repository history and maintainer intent before implementing or publishing a Java maintenance change. Use for dependency-bot failures, unfamiliar upstream contributions, competing fixes, or deciding whether to proceed, join existing work, comment, wait, or take no action.
---

# Establish maintainer intent before changing code

Treat repository history as part of the task specification. A technically correct patch is not a
useful contribution when it duplicates active work, conflicts with an explicit roadmap, targets the
wrong branch, or ignores the repository's established solution shape.

This is a read-only preflight. It does not authorize a comment, issue, branch, commit, push, or pull
request. Preserve the worktree and unrelated user work: never switch branches, reset, clean, stash,
or overwrite files while researching.

## Establish the exact source

1. Read repository instructions, contribution guidance, security policy, and any AI-assistance or
   disclosure policy before acting.
2. Record the repository, immutable revision, source issue or pull request, author, current state,
   target branch, head branch, and the user's intended delivery target. Distinguish a dependency
   bot branch from the default branch and from a human feature branch.
3. For a pull request, read the complete conversation: title, body, commits, force-pushes, issue
   comments, reviews, inline review comments, check failures, linked issues, and linked pull
   requests. Do not infer the current decision from the title or final diff alone.
4. Use only read access already available to the host. Do not expose credentials. Do not fetch or
   mutate refs merely to obtain history; ask before a read operation that would change local Git
   state. If relevant history is inaccessible, record that limitation and do not guess.

## Search for the repository's answer

1. Search open and closed issues and pull requests in the same repository for the dependency
   coordinate, old and new versions, failing symbol or error, affected module, and proposed
   solution. Check for the user's own open work as well as other contributors' work.
2. Read the nearest accepted, rejected, reverted, and superseded examples. Prefer examples for the
   same dependency or subsystem; otherwise use the closest contribution type. Read their review
   discussion, not only the merged bytes.
3. Inspect local `git log`, `git blame`, and the introducing commit for the failing contract. Follow
   the producer of an invalid value before adding a caller workaround. Preserve the incumbent
   library or architecture unless repository evidence supports replacing it.
4. Identify contribution mechanics: direct default-branch pull request versus a patch onto an
   existing bot branch, required issue or Jira key, title format, generated files, sign-off, DCO,
   CLA, test and coverage expectations, and disclosure requirements. Never claim or accept a legal
   agreement for the user.
5. Detect branch churn. Repeated bot supersession or force-pushes lower the value of speculative
   work and require rechecking the exact head immediately before any authorized publication.

## Separate evidence from inference

Rank evidence in this order:

1. explicit maintainer direction in the current source conversation;
2. an active or merged same-problem change and its review;
3. repeated accepted or rejected repository precedent;
4. contribution documentation and repository configuration;
5. code-history inference; and
6. general ecosystem convention.

Label inference as inference. Never convert silence, a stale bot pull request, or a passing build
into a claim about maintainer preference.

Stop when decisive higher-ranked evidence determines a terminal decision. After an explicit
current-thread maintainer direction, perform only the exact duplicate check and contribution-channel
check needed to report the next action; do not enumerate unrelated repository history. When no such
direction exists, inspect only the nearest relevant examples needed to establish a consistent
pattern, and state residual uncertainty instead of continuing an open-ended search.

## Choose exactly one decision

- `PROCEED`: repository evidence supports the change, no exact or near duplicate owns the outcome,
  no explicit wait or roadmap conflict exists, the delivery target is known, and required proof is
  feasible.
- `JOIN_EXISTING`: an active change already owns the same outcome. Contribute there only when the
  user authorizes that interaction; do not open a competing pull request.
- `COMMENT`: one maintainer decision is required before code would be useful. Draft the smallest
  precise question, but do not post it without user authorization.
- `WAIT`: a maintainer or source conversation explicitly depends on an upstream fix, coordinated
  release, branch movement, or other known event. State the event that would make the task ready.
- `NO_ACTION`: the work is obsolete, superseded, rejected by current policy, already solved, a
  duplicate, or not a real repository problem.

Any unresolved blocker prevents `PROCEED`. Do not write code first and use this decision afterward
to justify it.

## Hand off only a supported change

For `PROCEED`, name the repository-established delivery target before editing: the current change,
an existing bot branch, or a direct pull request to the repository's normal base. Describe the
smallest accepted solution shape, proof expected by prior reviews, and contribution mechanics.
Then use the applicable JAIPilot implementation and review skills.

For substantial Java compilation, tests, analysis, profiling, or benchmarks, default to
`jaipilot-remote-java` when the laptop provides no concrete advantage and follow its explicit upload
consent boundary.

## Report

Return the exact source identity; evidence links or commit IDs; relevant prior decisions; conflicts
and duplicates; contribution and disclosure rules; decision; confidence; delivery target; allowed
next action; and unresolved limitations. For `JOIN_EXISTING`, `COMMENT`, `WAIT`, or `NO_ACTION`,
report that no implementation or publication was performed.
