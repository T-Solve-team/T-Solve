---
module: chatbot
feature: citations
task: 2
attempt: 2
date: 2026-10-23
---

# Test Report: chatbot — Task 2: citations (attempt 1)

**Date:** 2026-10-23
**Code:** `default: 48cb785..b64f804` (branch `feature/KAN-73-chatbot-citations`): `c0d46fa` (citations) and
`b64f804` (BUG-007). I ran the checks at HEAD `92d4949`, which adds only harness files on top of `b64f804`. The
working tree was clean.
**Commands (run by the reviewer in `frontend/`, one after another, as the dispatcher asked):**
`npm run lint` · `npm run typecheck` · `npx vitest run src/features/chatbot --reporter=verbose` · from the repo
root `bun harnessclaude/hooks/sdd.ts trace chatbot STORY-001 STORY-004`, `… trace chatbot STORY-002` (for AC-002.9
and AC-002.10), `bun harnessclaude/hooks/sdd.ts diff chatbot`.
The dispatcher said not to run the full suite, `build`, `format:check` or `generate:api` (the machine is short on
memory), so I did not run them. The session log reports format:check clean, 435/435 tests, the build passing and
`generate:api` with no diff. I did not check those results myself.

## Results
- Feature tests: 2 files, 36 tests. Passed: 36 · Failed: 0 · Skipped: 0 · Duration: 39.5 s (`vitest run`, exit 0)
  - `mocks/handlers.test.ts`: 8 tests (AC-004.1 ×3, AC-004.2, AC-004.3 ×2, AC-004.4, AC-004.5)
  - `pages/KnowledgeChatbotPage.test.tsx`: 28 tests. New in this task: AC-001.4 (BUG-007), AC-001.7 ×2 (text
    typed while waiting is kept; citations in number order), AC-001.8 (HTML in a citation), AC-001.9, AC-001.10,
    AC-001.11 ×2, AC-002.9, AC-002.10. The Task 1 tests (AC-001.4 and AC-001.5 included) still pass.
  - jsdom printed one notice, "Not implemented: Window's getComputedStyle() method: with pseudo-elements". It is
    not a failure.
- Lint: pass (`eslint .`, no output, exit 0). This includes the import-boundary rules and the banned raw antd
  components.
- Typecheck: pass (`tsc -b --noEmit`, exit 0)
- Coverage: `package.json` has no coverage script, so none was measured.

## Traceability
- `sdd.ts trace chatbot STORY-001 STORY-004`: 19/19, exit 0.
- `sdd.ts trace chatbot STORY-002`: AC-002.9 and AC-002.10 have a test. The other 9 are Task 3's (KAN-72).
- `sdd.ts diff chatbot`: the spec is unchanged since it was approved (2026-10-23T14:05:13Z).

## Failures
None. One test passes without proving its criterion. `AC-002.10 keeps the Wiki links …` makes its assertions
before the failed availability response has arrived. See the review, Issue 1.

---

# Test Report: chatbot — Task 2: citations (attempt 2)

**Date:** 2026-10-23
**Code:** `default: b64f804..a5fe25b`: `4c9f297` (hook + tests) and `a5fe25b` (guideline 09). The checks were run at
HEAD `77e259b`, which adds only harness files. The working tree was clean.
**Commands (run by the reviewer, one after another):** in `frontend/`: `npm run lint` · `npm run typecheck` ·
`npx vitest run src/features/chatbot`. From the repo root: `bun harnessclaude/hooks/sdd.ts trace chatbot STORY-001
STORY-004`, `… trace chatbot STORY-002`, `bun harnessclaude/hooks/sdd.ts diff chatbot`. The full suite, `build`,
`format:check` and `generate:api` were not run (dispatcher). The session log reports 437/437, build OK and no
`generate:api` diff, and I did not check them.

## Results
- Feature tests: 2 files, 38 tests. Passed: 38 · Failed: 0 · Skipped: 0 · Duration: 52.8 s (exit 0).
  The two new tests are AC-001.7 "out of order" and AC-002.9 "every turn / keeps MSG110 while it checks again".
  AC-002.9 and AC-002.10 were changed. jsdom printed its usual getComputedStyle pseudo-element notice, which is
  not a failure.
- Lint: pass (`eslint .`, exit 0).
- Typecheck: pass (`tsc -b --noEmit`, exit 0).
- Coverage: there is no coverage script.

## Mutation runs (a scratch copy of `frontend/`, not the repo)
Each mutant was run against the tests that should catch it (`vitest run … -t`).
- M1, no `keepPreviousData`: the AC-002.9 re-check test failed (caught).
- M2, MSG110 for every document on error: the AC-002.10 test failed (caught).
- M3, an MSG06 toast on error: the AC-002.10 test **passed (not caught)**. A probe confirmed that the toast does
  show later. See review A1.
- M4, no sort: the AC-001.7 out-of-order test failed (caught).
- M5, only the last turn ids: the AC-002.9 every-turn test failed (caught).

## Traceability
- `trace chatbot STORY-001 STORY-004`: 19/19, exit 0.
- `trace chatbot STORY-002`: AC-002.9 and AC-002.10 have a test (2/11; the rest belong to Task 3).
- `diff chatbot`: the spec is unchanged since it was approved (2026-10-23T14:05:13Z).

## Failures
None.
