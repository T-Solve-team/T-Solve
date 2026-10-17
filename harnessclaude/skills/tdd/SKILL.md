---
name: tdd
description: Test-first loop for one feature — failing test, minimal code, refactor, verify
type: on-demand
phases: [implement]
agent: developer
---

# TDD

Write the test before the code, for every feature in `tasks.md`. Used by the implement skill for each task.

## Iron Law

```
NO PRODUCTION CODE WITHOUT A FAILING TEST FIRST
```

Never ignore, skip, or weaken a failing test to make the suite pass. No mocks, stubs, or hard-coded returns whose only purpose is to turn a test green.

## Pick the Loop

| Situation | Loop |
|-----------|------|
| New behavior | **Red → Green → Refactor** |
| Refactor / changing existing code | **Pin → Change → Verify** — first write tests for the *current* behavior, then change the code, then confirm the pinned tests still pass |
| Bug fix | Write a test that reproduces the bug (must fail), then fix — use the `debug` skill if the root cause is unclear |

## Red → Green → Refactor

1. **Read the acceptance criteria** for the task's STORY-ID in `artifacts/03-modules/{module}/spec.md`. Each criterion (`AC-001.1`, …) becomes at least one test, and the test name **starts with its id** — `it("AC-002.1 applies the percent discount", …)` — so `sdd.ts trace` can map tests back to the spec.
2. **Red** — write one test for one criterion. Run it. It MUST fail, and fail for the expected reason (assertion, not an import or syntax error). A test that passes before the code exists proves nothing.
3. **Green** — write the smallest code that makes it pass. No extra features "while you're here".
4. Run the **whole** test suite for the module, not just the new test.
5. **Refactor** — clean up names and duplication with tests green. Re-run after every refactor.
6. Repeat for the next criterion. Cover the error paths and edge cases listed in `design.md` (from the `edge-cases` skill), not just the happy path.

## Before Handing Off

1. Run typecheck/lint if the project has them, then the full test command.
2. Run `bun harnessclaude/hooks/sdd.ts trace {module} {task's STORY-IDs}` — every criterion must show a test (exit 0).
3. Apply the `verify` skill — record the actual commands and their results in the session log.
4. Only then commit (`commit` skill) and move the feature to `evaluating`.

## Quality Bar

- Tests are deterministic, isolated (no shared state or order dependence), and clean up their data.
- Test names start with the AC id and describe behavior: `AC-003.2 rejects expired token`, not `test2`.
- Assert on outcomes and side effects, not only on status codes or types.
- Critical paths (auth, payments, data mutation) get negative-path tests.

## Red Flags — Stop and Restart the Loop

- "I'll add tests after" / "this is too simple to test"
- Test written after the code and passed on first run
- Commenting out, skipping, or loosening an assertion to get green
- Several behaviors added in one green step
- The spec does not say what should happen — stop: write `[NEEDS CLARIFICATION: <question>]` in spec.md and ask the user. Never encode a guess in a test.
