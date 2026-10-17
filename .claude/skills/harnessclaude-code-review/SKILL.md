---
name: harnessclaude-code-review
description: Two-stage review of one feature — spec compliance first, then a critical/informational quality pass
type: on-demand
phases: [evaluate]
agent: reviewer
delegates:
  - scout
  - verify
---

# Code Review

Production-readiness review of one feature against its spec. Used by the evaluate skill; the result feeds the Criteria table of `task-{N}-{slug}-review.md`.

## Posture

- Assume the code may be AI-written. Polished structure, confident comments, and passing happy-path tests are **not** evidence of correctness. Verify claims against the diff, the spec, and commands you run.
- No rubber stamps. You are not here to preserve momentum; you block defects, regressions, scope drift, and slop.
- Be concise and specific: `file:line`, problem, fix. No praise, no preamble.

## Inputs

1. The diff for the feature in each repo it changed — from the commits in the session log (`git -C <repo> show <sha>`), or `git -C <repo> diff <base>..<head>` when the feature spans several commits. In the monorepo layout, leave the harness folder out of the diff (`-- . ':(exclude)harnessclaude'`).
2. Spec: `artifacts/03-modules/{module}/spec.md` (the STORY-IDs this task references).
3. Edge cases and risks from the active plan path's `design.md`, if present.

Read the **full** diff before flagging anything.

## Stage 1 — Spec Compliance (must pass before Stage 2)

List every acceptance criterion (AC id) of the task's STORY-IDs, then check each against the code. `sdd.ts trace` tells you which tests name each criterion — read those tests too; a test that names an AC but does not check it is MISSING.

| # | Criterion (AC id) | Status | Evidence |
|---|-------------------|--------|----------|
| 1 | AC-001.1 {from spec} | PASS / MISSING / EXTRA | {file:line or test name} |

- **MISSING** → verdict fail; stop here, no quality review yet.
- **EXTRA** (behavior not in the spec) → flag; acceptable only if the session log justifies it and the user agrees.

Well-written code that does not match the spec is still wrong.

## Stage 2 — Quality

1. **Impact scout** — for multi-file changes or shared utilities, run the `scout` skill in impact mode: who else calls the changed code, which error paths and boundaries are untested.
2. **Checklist** — load `references/checklist.md`. Always apply the base pass; add the API or web overlay when the code is an API or a web UI.
3. Report in two groups:

```
CRITICAL (blocks pass):
- [file:line] problem
  Fix: suggested fix

Informational (does not block):
- [file:line] problem
  Fix: suggested fix
```

If nothing is found: `No issues found.` — do not invent findings to look thorough.

## Final Gate

Run the tests and checks yourself (`run-tests` skill), then apply `verify`. A CRITICAL finding or a failing test means verdict **fail**.

## Mapping to the Review Template

| Template criterion | Comes from |
|--------------------|-----------|
| Functionality | Stage 1 rows |
| Completeness | Stage 1 MISSING / EXTRA |
| Correctness | Test run + CRITICAL findings |
| Integration | Interface contracts in the spec + scout impact findings |

## Receiving This Review (for the developer fixing a failed feature)

1. Read the whole review first; if any item is unclear, ask about all unclear items before changing anything.
2. Check each finding against the code. If one is wrong for this codebase, say why with evidence — do not implement it to be agreeable.
3. Fix in order: CRITICAL → simple → complex, re-running tests after each fix.
4. Reply with what changed (`Fixed: [file:line] …`), not with thanks or agreement phrases.
