# Test Report: search — Task 1: Ticket List (ticket-list, KAN-147)

**Date:** 2026-10-26
**Commit under test:** `default@3cb60af` (range `5d962cd..3cb60af`), branch `feature/KAN-147-search-ticket-list`
**Commands (from `frontend/`):** `npm run format:check` · `npm run lint` · `npm run typecheck` · `npm run build` ·
`npm run generate:api` + `git diff --exit-code -- src/shared/api/generated` · `npx vitest run src/features/tickets` ·
`npm run test` · re-runs of the full-suite failures, one file at a time (`npx vitest run <file>`).
Traceability: `bun harnessclaude/hooks/sdd.ts trace search STORY-001 STORY-004` (project root).

## Results

| Check | Result | Evidence |
|-------|--------|----------|
| Format | pass | `All matched files use Prettier code style!`, exit 0 |
| Lint | pass | `eslint .`, no output, exit 0 |
| Typecheck | pass | `tsc -b --noEmit`, exit 0 |
| Build | pass | `✓ built in 1m 1s`, exit 0 |
| `generate:api` drift | pass | orval 8.41.0 regenerated; `git diff --exit-code -- src/shared/api/generated` exit 0, `git status` clean |
| Feature tests (`src/features/tickets`) | pass | `Test Files 2 passed (2)` · `Tests 28 passed (28)` · 65.9 s, exit 0 |
| Full suite (`npm run test`) | **5 failed / 503 passed** (508) | `Test Files 3 failed | 26 passed (29)` · 426.8 s, exit 1 |
| Failing files re-run alone | pass | TicketListPage 22/22 (45.6 s) · ReviewQueuePage 9/9 (49.8 s) · mock-mode 2/2 (8.9 s) |

- Total (full suite): 508 · Passed: 503 · Failed: 5 · Skipped: 0 · Duration: 426.8 s
- Nothing else ran on the machine from this review during the full suite (the static checks had finished).

## Coverage

| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch coverage | not measured — `package.json` has no coverage script | none; the spec's NFR 1 asks for one test per AC, not a percentage |
| Acceptance criteria of this task with a named test | 16/16 (AC-001.1–001.14, AC-004.1, AC-004.2) | all (NFR 1) |

## Acceptance Criteria → Tests

```
  ✓ AC-001.1  frontend/src/features/tickets/pages/TicketListPage.test.tsx
  ✓ AC-001.2  frontend/src/features/tickets/pages/TicketListPage.test.tsx
  ✓ AC-001.3  frontend/src/features/tickets/pages/TicketListPage.test.tsx
  ✓ AC-001.4  frontend/src/features/tickets/pages/TicketListPage.test.tsx
  ✓ AC-001.5  frontend/src/features/tickets/pages/TicketListPage.test.tsx
  ✓ AC-001.6  frontend/src/features/tickets/pages/TicketListPage.test.tsx
  ✓ AC-001.7  frontend/src/features/tickets/pages/TicketListPage.test.tsx
  ✓ AC-001.8  frontend/src/features/tickets/pages/TicketListPage.test.tsx
  ✓ AC-001.9  frontend/src/features/tickets/pages/TicketListPage.test.tsx
  ✓ AC-001.10  frontend/src/features/tickets/pages/TicketListPage.test.tsx
  ✓ AC-001.11  frontend/src/features/tickets/pages/TicketListPage.test.tsx
  ✓ AC-001.12  frontend/src/features/tickets/pages/TicketListPage.test.tsx
  ✓ AC-001.13  frontend/src/features/tickets/pages/TicketListPage.test.tsx
  ✓ AC-001.14  frontend/src/features/tickets/pages/TicketListPage.test.tsx
  ✓ AC-004.1  frontend/src/features/tickets/mocks/handlers.test.ts
  ✓ AC-004.2  frontend/src/features/tickets/mocks/handlers.test.ts
  ✗ AC-004.3  no test under this module's paths (...)
  ✗ AC-004.4  no test under this module's paths (...)
trace search (STORY-001, STORY-004): 16/18 acceptance criteria have a test
```

AC-004.3 (search mock) and AC-004.4 (feedback mock) belong to Task 2 and Task 3 (`iteration-1/tasks.md`) and are
outside this feature; their ✗ is expected now and is not a finding against Task 1. The trace exits 1 for that
reason only.

## Failures (full suite only; all pass when their file runs alone)

### `src/features/tickets/pages/TicketListPage.test.tsx` — AC-001.1 shows the filters and the eight columns to a Project Manager
- Error: `Unable to find an element with the text: /^Total \d+ items$/` at line 18 (`openList`, first `findBy`,
  `{ timeout: 5000 }`), test took 8770 ms.
- Cause: the first test of the file renders the app cold (session, layout, antd table) and the first `findBy`
  ran past 5 s while 29 files competed for CPU. The same file passes 22/22 alone (45.6 s) and 28/28 with the
  mock tests. Same pattern as BUG-002, BUG-004 and BUG-010; the code under test is not wrong.
- Suggested fix: none in this feature (guideline 10 §6 forbids raising timeouts); BUG-010's owner decides the
  test-setup fix (fe-foundation).

### `src/features/review/pages/ReviewQueuePage.test.tsx` — 3 tests
- `lists the pending tickets … (BUG-004)`: `Unable to find role="link" and name "ITSUP-1031"` (11.8 s) — the same
  first-test failure recorded as BUG-004 on `main`.
- `approves one ticket at once (no confirm) and shows MSG14` (16.6 s) and `requests changes for every selected
  ticket with one comment (MSG17)` (15.1 s): `Test timed out in 15000ms`.
- Cause: load-induced timeouts in a file this commit does not touch; 9/9 pass alone.

### `src/app/dev/mock-mode.test.tsx` — AC-009.6 starts no mock server … when mock mode is off
- Error: `Test timed out in 15000ms` (15.5 s). Not touched by this commit; 2/2 pass alone (8.9 s).

## Notes
- Flaky under load: the failing set differs from BUG-010's run on the same day (11 failures there: review 5,
  chatbot 4, tickets 2; 5 here: review 3, tickets 1, mock-mode 1). Four of the five failures are in files
  outside this commit. Recorded as a judgment call in the review.
- The new page file adds 22 jsdom page tests to the suite, which lengthens the window in which the machine is
  saturated; it does not introduce the timeout pattern, which predates it (BUG-002…BUG-004).
- No skipped tests. Slow tests (>5 s each) are the page tests of review, chatbot and tickets, as documented in
  guideline 10 §6 (2–5 s per antd table test, 15 s timeout).
- jsdom prints `Not implemented: Window's getComputedStyle() method: with pseudo-elements` many times; harmless
  (antd), present in every run.
