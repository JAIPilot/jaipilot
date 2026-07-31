Improve the selected Java production code in this isolated workspace.

Project root: `{{PROJECT_ROOT}}`

Production-file allowlist:

{{TARGET_FILES}}

Deterministic cleanup already performed:

{{DETERMINISTIC_CLEANUP}}

Before editing, read these files if they exist:

- AGENTS.override.md
- AGENTS.md
- .jaipilot/project-memory.md

Objectives

- Understand each target in its real call graph, module, build, and test context before changing it.
- Review the OpenRewrite candidate first. Keep, refine, or revert each deterministic edit based on repository context and behavioral value; do not trust or churn it blindly.
- Make behavior-preserving improvements that materially increase clarity, correctness, maintainability, testability, reliability, security, or measured performance.
- Consolidate proven duplication, simplify control flow and state, strengthen resource and error handling, remove genuinely unreachable or obsolete code, and use clear Java 17+ idioms where they improve the code.
- Preserve public APIs, CLI behavior, serialization formats, exception contracts, threading semantics, and observable side effects unless an existing regression test proves they are wrong.
- Add or strengthen focused regression tests when a cleanup changes risky logic or exposes an unrecorded failure mode.
- Run focused tests while iterating. JAIPilot independently runs the repository's clean full test suite after you stop.

Rules

- Edit production Java only in the allowlist above. You may add or update directly relevant Java tests under `src/test/java`.
- Do not edit build files, wrappers, configuration, documentation, generated files, unrelated tests, or production files outside the allowlist.
- Do not add dependencies, suppress warnings, disable checks or tests, loosen assertions, raise timeouts, or reduce coverage to make the build pass.
- Do not perform cosmetic churn, mass reformatting, speculative abstractions, framework migrations, or rewrites without a demonstrated user-visible or maintenance benefit.
- Do not change correct code merely to produce a diff. If no worthwhile safe improvement exists, leave the workspace unchanged.
- Keep filesystem work, collections, subprocesses, concurrency, and memory bounded. Preserve interruption and cancellation behavior.
- For a performance change, compare the affected path before and after with repeatable inputs and retain the change only when the evidence shows a meaningful improvement.
- Leave the candidate compiling, with its focused tests passing and no unfinished TODOs.
