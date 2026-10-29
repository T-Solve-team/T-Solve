# Task 1: Ticket List

**Module:** search
**Assignee:** Anh Tran Hai
**Status:** passing
**Iteration:** 1

---

## Session 2026-10-26 14:35

### Working on: ticket-list (6.2 Ticket List, KAN-227)
- Contract: `GET /api/v1/tickets` (`getTickets`, tag `tickets`) with `TicketStatus`, `TicketProject`,
  `TicketDepartment`, `TicketSummary`, `TicketSummaryPage`; every id a UUID string; client regenerated.
- Mocks: `tickets/mocks/ticket-data.ts` — 28 tickets (every status and source), Departments `'1'` IT and `'2'` HR,
  Projects `'101'`, `'102'`, `'201'` copied from `src/mocks/session.ts` (feature code may not import `src/mocks`),
  fixed UUID `ticketId`s; the handler applies keyword, Department, source, status, sort and paging.
- Screen: `TicketListPage` replaces the placeholder; `TicketListFilters` (Keyword on Enter, `maxLength` 100,
  Department as `String(id)` from the session, Source, Status), `TicketListTable` (`DataTable`), `useTicketList`
  (no `keepPreviousData`, design builder check).
- Test-first: the mock test failed 6/6 and the page test 20/21 before the code (AC-001.11 passed early: the lazy
  module load also shows the spinner); then 28/28.
- Reused most of the KAN-77 Ticket List code (commit `bd1f914`, branch not merged), adapted to the new spec: UUID
  ids, Keyword limit, AC numbers.
- The longest page test (AC-001.7) was split in two (guideline 10 §6: split, never raise timeouts).
- Commit: default@3cb60af (author Anh Tran Hai).

### Verification
- `npm run format:check`, `npm run lint`, `npm run typecheck`, `npm run build`: pass.
- `npm run generate:api`: no diff (same hash before and after).
- `npx vitest run src/features/tickets`: 2 files, 28/28 pass.
- `npm run test` (full): 496 pass, 11 fail (5 review, 4 chatbot, 2 tickets), mostly at the 15 s timeout while
  another heavy process ran; the three files alone: 7 of 87 fail, other tests; the tickets folder alone passes.
  Logged as BUG-057 (owner to confirm: fe-foundation test setup).
- `bun harnessclaude/hooks/sdd.ts trace search STORY-001 STORY-004`:
  ```
  ✓ AC-001.1 … AC-001.14  frontend/src/features/tickets/pages/TicketListPage.test.tsx
  ✓ AC-004.1, AC-004.2    frontend/src/features/tickets/mocks/handlers.test.ts
  ✗ AC-004.3  no test (Task 2, search mock)
  ✗ AC-004.4  no test (Task 3, feedback mock)
  trace search (STORY-001, STORY-004): 16/18 acceptance criteria have a test
  ```
  Every criterion of this task has a test; AC-004.3 and AC-004.4 belong to Tasks 2 and 3.

### Deviations from the plan
- Branch `feature/KAN-147-search-ticket-list` (team rule, README "Branching and conventions") instead of the
  harness default `feat/search` that `sdd.ts status … implementing` recorded.
- About 1,580 added lines (mostly the generated client and the mock data), over the README's ~400-line PR hint.

### Blockers
none

### Next up
Evaluation of ticket-list; then Task 2 (6.1 Search, KAN-145) on `feature/KAN-145-search-page` from `main`.

## Session 2026-10-26 14:59

### Working on: ticket-list — evaluation
- Independent review (harnessclaude-reviewer, attempt 1): verdict pass, no Critical finding
  (`artifacts/06-evaluation/search/task-1-ticket-list-review.md`).
- Open judgment call of the review — the full suite had 5 timeouts (4 in files this commit does not touch), each
  failing file passes alone: Anh Tran Hai accepted the pass on 26/10; the flakiness stays tracked as BUG-057.
- Informational findings left as they are (no criterion broken): the Keyword also reloads from the search icon and
  the clear icon; a Keyword from the URL is not trimmed or capped; mock Department and Project ids are numeric
  strings until fe-foundation Task 10.
- Status: passing.

### Next up
Task 2 (6.1 Search, KAN-145) on `feature/KAN-145-search-page` from `main`.

## Session 2026-10-29 20:35

### Working on: ticket-list — review of #80 and a new pull request
- Review of #80: the branch, commits and harness used KAN-147, the backend story of UC-27 (API and swapping the
  mocks for it); the screen on mocks is KAN-227. CI `frontend` failed once (BUG-057, below). The flaky-suite bug
  of 26/10 had been renumbered BUG-022, an id `main` gives to another bug (BUG-023 and up to BUG-056 are taken too, on `main` and open branches), and was missing from `docs/bug-log.md`.
  `lastCommit` pointed to `3cb60af`, a commit of the closed branch.
- Rebuilt on `feature/KAN-227-search-ticket-list` from `main` (19e7c51): the files of #80 as they were, then the
  fixes. The Jira key is KAN-227 in tasks, the iteration design, iteration-1, this log, the review and the test
  report; the bug references are BUG-057.
- BUG-057: `AC-001.10 resets the Keyword box when going back in history` waited for a 20-row first render on a
  busy CI runner (5.0 s, 1 of 818). It now starts on `?keyword=vpn` and checks that Back restores that keyword; no
  timeout was raised (guideline 10 §6).
- Commit: default@2a0ac12 (author Anh Tran Hai).

### Verification
- `npm run format:check`, `lint`, `typecheck`, `build`: pass. `npm run generate:api`: no diff.
- `npx vitest run src/features/tickets`: 2 files, 29/29 pass; the changed test alone passes.
- `npm run test` (full, this laptop): 18 failed | 492 passed (review, chatbot, users, shared — files this branch
  does not touch; all tickets tests passed). Those 13 files with `--no-file-parallelism`: 410/410 pass. Same load
  pattern as BUG-002–004 and BUG-057; CI is the check.
- `bun harnessclaude/hooks/sdd.ts trace search STORY-001 STORY-004`: 16/18, unchanged (AC-004.3 and AC-004.4
  belong to Tasks 2 and 3).

### Next up
CI of the new pull request; then Task 2 (6.1 Search, KAN-145). The backend of UC-27 (KAN-147) needs the search
module's backend stories in the spec first (plan, Change Mode).
