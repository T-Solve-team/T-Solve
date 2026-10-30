# Task 2: Search Published tickets

**Module:** search
**Assignee:** Anh Tran Hai
**Status:** passing
**Iteration:** 1

---

## Session 2026-10-30 00:46

### Working on: ticket-search (6.1 Search, KAN-145)
- Branch `feature/KAN-145-search-page-v2` from the tip of `feature/KAN-227-search-ticket-list` (1ad08be), stacked:
  Task 2 reuses Task 1's `TicketProject` and `TicketDepartment` schemas and #87 is not merged yet. The planned name
  `feature/KAN-145-search-page` is taken by an old local branch that holds only KAN-147 commits (left as it is).
- Code taken from commit `85db36a` (branch `feature/KAN-145-search-published-tickets`, built on the closed KAN-147-v2
  base, never reviewed): cherry-picked onto the new branch. `tsolve-api.yaml` merged with no conflict (additive: tag
  `search`, `GET /api/v1/search/tickets`, `TicketSearchResult`, `TicketSearchResultPage`); the conflict in
  `src/shared/api/generated` was solved by `npm run generate:api`, not by hand.
- Contract, mocks (`search-data.ts`: 14 Published results, UUIDs of `src/mocks/session.ts`, two `helpfulRate: null`,
  one `0`), `useTicketSearch`, `SearchFilters`, `SearchResultList`, `SearchResultCard`, `SearchPage` as planned.
- Gaps filled test-first: edge case #7 (a link with filters but no query) and #9 (only the first Resolved day).
- Review fixes (below) in the tests, `SearchFilters.tsx` and `useTicketSearch.ts`; BUG-059 logged.
- Commit: default@b3f0d6d (author Anh Tran Hai).

### Review (attempt 1)
- harnessclaude-reviewer: verdict pass, no Critical finding
  (`artifacts/06-evaluation/search/task-2-ticket-search-review.md`, test report next to it).
- code-reviewer (quality), fixed before the commit:
  - Two tests ended once their request was sent, before the answer (the BUG-057 shape): AC-002.6 and AC-002.9, and
    four more that ended right after a URL change (AC-002.5 ×3, AC-002.10 history) now wait for the rendered result.
  - Six fixed `delay(300)` waits (guideline 10 T5) replaced by a later request whose order proves that nothing was
    sent before it.
  - BUG-059: the Search box lost the focus after Enter (`key={q}` remount). It is now controlled and reset during
    render when the applied query changes. The Ticket List (KAN-227) has the same pattern: still open in the row.
  - A hand-edited link: the query is capped at 200 characters, an empty `departmentId` and a reversed Resolved range
    are ignored (each would make every search a 400 on the real backend).
  - `act` around `router.navigate(-1)`; tests for the clear icon and for Try again on page 2 (edge case #11).
- Left as they are (no criterion broken): the magnifier also searches; a page past the last shows MSG04 with no
  pagination (same as the Ticket List, edge case #2); the Resolved date end input has no own label; tests select
  antd parts by class, like Task 1.
- Open questions for Anh Tran Hai (not coded): the time zone of the Resolved days in the contract (the cards show
  Vietnam time; the mock keeps every `resolvedAt` between 00:00 and 16:59 UTC so both agree); MSG39 and future days
  on the Resolved date filter (guideline 09 and decision FE-36 name it, AC-002.5 does not); a visible label or
  placeholder for the Search box (Figma). `design.md` "Data Model" still gives the old numeric mock ids.

### Verification (after the review fixes)
- `npx vitest run src/features/search`: 2 files, 32/32 pass (the BUG-059 test failed before the fix).
- `npm run test` (full): 46 files, 874/874 pass. A first full run during the two reviews (each running Vitest too)
  had 46 failures in 15 files this branch does not touch, mostly `Hook timed out in 10000ms` in `src/test/setup.ts`
  (machine load, as BUG-002–004 and BUG-057); no search test failed.
- `npm run format:check`, `lint`, `typecheck`, `build`: pass. `npm run generate:api`: no diff.
- `bun harnessclaude/hooks/sdd.ts trace search STORY-002`: 14/14. With STORY-004: 17/18, the ✗ is AC-004.4 (Task 3).
- Status: evaluating → passing (verdict pass, attempt 1; the fixes above came after it and only harden tests and
  input from links).

### Deviations from the plan
- Branch name `-v2` and the stacked base (above); a PR for it targets `feature/KAN-227-search-ticket-list`.

### Blockers
none

### Next up
A pull request when Anh Tran Hai asks (base `feature/KAN-227-search-ticket-list`); then Task 3 (8.1 Give Feedback, KAN-148) after #87 and this branch.
