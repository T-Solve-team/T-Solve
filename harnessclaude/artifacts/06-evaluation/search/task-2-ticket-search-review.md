---
module: search
feature: ticket-search
task: 2
verdict: pass
attempt: 1
date: 2026-10-30
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: search — Task 2: Search Published tickets (KAN-145, STORY-002, AC-004.3)

## Summary
I reviewed the staged, uncommitted change `git diff --cached 1ad08be` on `feature/KAN-145-search-page-v2` (frontend
only, 20 files) against the spec. All 14 criteria of STORY-002 and AC-004.3 are met in the code. Each one has a
test that names it and checks it. My own runs: the feature tests pass 29/29, and format, lint, typecheck and build
pass. `generate:api` leaves no diff. The trace shows 17/18; the one ✗ is AC-004.4, which belongs to Task 3. There are
no CRITICAL findings. Verdict: PASS. I did not run the full suite (the dispatcher runs it elsewhere), so I record
that as an open judgment call (see Assumptions made).

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Stage 1: 15/15 PASS (table below). |
| Completeness | pass | No MISSING. EXTRA: the magnifier button also searches, and the clear icon removes the query (Informational 1). |
| Correctness | pass | Feature 29/29. Static checks pass, and `generate:api` leaves no diff. No CRITICAL findings. Full suite not run by the reviewer (Assumption 1). |
| Integration | pass | `GET /api/v1/search/tickets` in `openapi/tsolve-api.yaml` matches the spec contract field for field: `q` required with minLength 1 and maxLength 200, `departmentId` uuid, `source`, `resolvedFrom`/`resolvedTo` as `date`, Page and Size with no Sort, 200/400/401/403, `TicketSearchResult` with the 7 required fields, `helpfulRate` integer 0–100 or null, and `TicketSearchResultPage`. It reuses Task 1's `TicketProject`/`TicketDepartment`. The page calls only the generated `useSearchTickets` through `@/shared/api`. The mock is in `features/search/mocks/` and is typed by orval's `getSearchTicketsMockHandler` and `TicketSearchResult` (NFR 4, edge 16). The search handlers come before the generated faker handlers (`src/mocks/handlers.ts`). No file of `app/`, hand-edited `shared/`, `src/mocks`, `src/test` or `package.json` changes (NFR 3, NFR 6). The ids are UUID strings, the same as the fixture session (NFR 5). |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-002.1 Search box, Department, Source, Resolved date above the results area | PASS | `SearchPage.tsx:16-41` puts the filters before `<section aria-label="Results">`. The route title gives the "Search" heading. Tests cover both roles. |
| 2 | AC-002.2 no query or blank: no call, empty area, no message | PASS | `useTicketSearch.ts:22,30` trims `q` and sets `enabled: q !== ''`, and `SearchPage.tsx:28` renders nothing without `q`. Two tests (`?q=%20%20`, and filters without `q`) check that no request is made and the area is an empty DOM element. |
| 3 | AC-002.3 Enter with trimmed text, current filters, page 0, size 20; blank Enter does nothing; typing does not search | PASS | `SearchFilters.tsx:60-63` and `setFilter`, which drops `page`. Two tests check the first request's `q`, `page` and `size` and that no sort or filter is sent, that typing alone makes no request, and that blank Enter leaves the URL and makes no new request. |
| 4 | AC-002.4 at most 200 characters | PASS | `maxLength={QUERY_MAX}` (`SearchFilters.tsx:57`). The test pastes 220 characters. |
| 5 | AC-002.5 Department (session, id as string), Source (All + 4), Resolved range `YYYY-MM-DD`, both or none; default or cleared removes | PASS | `SearchFilters.tsx:68-102` and `useTicketSearch.ts:45-58`, which sets both days in one URL update. Four tests: Department with `tr-two-departments`, the four source labels, a range then clear, and only the first day picked. |
| 6 | AC-002.6 a filter change with a query searches from page 1; without a query it only changes the URL | PASS | `setFilter` and `setResolved` drop `page`. Two tests: `page=2` then a filter gives a request with `page=0`, and with no query a filter makes no request. |
| 7 | AC-002.7 cards in API order: title link to 5.5, "Project · Department · Resolved <date time>", "N% helpful" with thumbs-up, excerpt | PASS | `SearchResultCard.tsx:16-29` (`paths.ticketDetail`, `formatDateTime`, `LikeOutlined`). Tests check the order, the href, the full meta line in Vietnam time, the icon, the excerpt, and "0% helpful" for 0 (edge 8). |
| 8 | AC-002.8 null `helpfulRate` shows no rate | PASS | `SearchResultCard.tsx:23`. The test checks that the card has no "helpful" text and no like icon. |
| 9 | AC-002.9 "Total N items", 20 per page, a page keeps the query and filters | PASS | `SearchResultList.tsx:35-43` (`PAGE_SIZE`, no size changer). The test checks that page 2 sends `page=1`, `q`, `departmentId` and `size=20`. |
| 10 | AC-002.10 query, filters, range and page in the URL, and the controls show them | PASS | `useTicketSearch.ts:21-27`, controlled Selects and RangePicker, and `key={q}` on the Search box. Three tests: a shared link with every value, invalid `source` or dates ignored, and back in history resetting the box. |
| 11 | AC-002.11 loading state in the results area | PASS | `isLoading={results.isPending}` with no `placeholderData`. The test uses an endless delay and finds "Loading ...". Try again after a failed first load also shows it, because TanStack Query 5.104 `fetchState` resets a query with no data to `pending`. |
| 12 | AC-002.12 MSG04 in the results area | PASS | `EmptyState` (default MSG04). The test searches "zzz". |
| 13 | AC-002.13 error (not 401) with Try again that runs the same search | PASS | `SearchResultList.tsx:28` and `SearchPage.tsx:33-34`. 401 is handled globally (`global-errors.ts` `onUnauthorized`). The test gets a 500, sees MSG06, clicks Try again and gets the same query string twice (edge 11). |
| 14 | AC-002.14 title, excerpt and text as plain text | PASS | Only React text children, no `dangerouslySetInnerHTML`; the excerpt is clamped by CSS. The test uses `<img onerror>`, `<b>`, `<script>` and `<i>` (edge 10). |
| 15 | AC-004.3 mock: Published only, `q` on title or Solution text in any case, Department, source, both days included, paging, a null `helpfulRate` | PASS | `mocks/handlers.ts:8-26,41-58` and `search-data.ts` (14 Published rows, two with null, one with 0, UUID `ticketId`s). Five mock tests, including a match found only in the Solution text, the range edges (`2026-09-15` included at both ends) and page 2 of 10. |

### Stage 2 — Quality

CRITICAL (blocks pass): none.

Informational (does not block):
1. [`frontend/src/features/search/components/SearchFilters.tsx:60-63`] `Input.Search` also calls `onSearch` from the
   magnifier button, where a non-blank text searches, and from the clear icon, which removes `q` and empties the
   results area. AC-002.3 names only Enter. Nothing breaks: blank Enter still does nothing, and with no query the
   area is empty, as AC-002.2 says. But the clear-icon path is extra behaviour and has no test; Task 1 has one for
   its own clear icon.
   Fix: add an `AC-002.3 … clear icon` test, or note in the design that the clear icon is accepted.
2. [`frontend/src/features/search/hooks/useTicketSearch.ts:22-23`] A query from the URL is trimmed but not capped,
   and a `departmentId` from the URL is checked neither as a uuid nor against the session. A hand-edited link with
   more than 200 characters breaks the contract's `maxLength: 200` (a real backend answers 400, so the page shows
   the error state). An unknown Department id shows the raw UUID in the Select. This is the same gap as Task 1's
   Informational 2 and 3, which are still open in `TicketListPage.tsx:19`.
   Fix: `list.getFilter('q')?.trim().slice(0, 200) ?? ''`.
3. [`frontend/src/features/search/hooks/useTicketSearch.ts:28-31`] Pressing Enter again with the query already in
   the URL changes nothing, so no request is sent. The same happens when a query is searched again within the
   30 s `staleTime`: the results come from the cache and no loading state shows. After an error, Enter with the
   same text does not retry; only Try again does. AC-002.3 says Enter "SHALL call" the API. I read this as "search
   with this text" and accepted the app-wide cache defaults (guideline 06 §4). See Assumption 3.
   Fix, if the FE Lead wants every Enter to reach the API: call `results.refetch()` when the submitted text equals
   the current `q`.
4. [`frontend/src/features/search/components/SearchFilters.tsx:52`] `key={q}` remounts the Search box whenever the
   applied query changes, so after Enter the keyboard focus leaves the box (it goes to `body`). The same pattern
   is in Task 1's Keyword box. This affects accessibility and UX only.
   Fix: a controlled value that is reset from the URL, or `autoFocus` on remount when the change came from Enter.
5. [`frontend/src/features/search/pages/SearchPage.test.tsx:127,135,147,169,246,265`] Six tests wait a fixed
   `delay(300)` to show that nothing happened. Guideline 10 T5 says "never `setTimeout` or arbitrary sleeps". Task 1
   checked the same "typing does not reload" rule without a sleep. The helpers also use `.ant-select-item-option`,
   `.ant-picker`, `.ant-picker-clear` and `.anticon-like` (T1 prefers roles; the icon is `aria-hidden`, so the class
   is its only handle). This costs about 1.8 s and is cosmetic.
   Fix: assert the URL and request count right after the action, as Task 1 does, or keep the sleeps and name
   the exception in the test.
6. [`SearchFilters.tsx:24,59`, `SearchResultCard.tsx:10,23`, `SearchResults.module.css:31`,
   `useTicketSearch.ts:16-17`, `SearchPage.tsx:26`, `mocks/handlers.ts:7`, `mocks/search-data.ts:5,11,41`] Code
   comments cite spec, decision and design ids ("spec AC-002.4", "decision Q4, AC-002.8", "design edge case #8").
   The project rule "Stable Code Artifacts" asks for the invariant instead. This is cosmetic and the same as Task 1's
   Informational 5.
7. Process: the branch is stacked on Task 1's tip `1ad08be` (KAN-227, not on `main` yet) instead of an up-to-date
   `main`, and its name ends in `-v2` (tasks.md says `feature/KAN-145-search-page`). The PR will carry Task 1's
   commits until KAN-227 merges. Task 2 depends on Task 1's schemas, so stacking works, but the merge order must
   be Task 1 first.

Edge cases in the design (rows 6–11 for STORY-002, row 16): all have tests.
- Row 6: blank Enter and 200+ characters are covered by AC-002.3 and AC-002.4.
- Row 7: filters without `q` are covered by AC-002.2 and AC-002.6.
- Row 8: a rate of 0 and a null rate are covered by AC-002.7 and AC-002.8.
- Row 9: a range with only a start day, and clearing it, are covered by AC-002.5.
- Row 10: HTML in the title or excerpt is covered by AC-002.14.
- Row 11: 500 then Try again with the same query string is covered by AC-002.13.
- Row 16: the mock is typed by `getSearchTicketsMockHandler` and `TicketSearchResult`, so typecheck fails if it
  drifts from the contract.

Security (threat model): the screen only reads and stores nothing. The title link is built by `paths.ticketDetail`
from the API's `ticketId` and rendered by React Router's `Link`. Every text is rendered as React children, so it is
escaped. The URL values reach the API only as query parameters, encoded by the generated client, and the backend
decides visibility (spec requirement 4). A security review is not needed: there is no auth logic, upload,
user-supplied URL or HTML.

## Assumptions made
1. **Full suite not run by the reviewer (open judgment call).** The dispatcher asked me not to run `npm run test`
   because it is already running elsewhere on a slow machine. My evidence is the feature run (29/29) plus the
   static checks. The evaluate skill says "run all tests" and "any failing test → fail". The pass therefore holds
   only if that full-suite run is green, or fails only with the known load timeouts of the BUG-002–004 and BUG-057
   family in files this change does not touch (as accepted for Task 1). The user should confirm this before the
   commit.
2. **"Only the Published tickets" in AC-004.3 holds by construction.** The search mock data contains only Published
   tickets, and `TicketSearchResult` has no status field, so no test can show a non-Published ticket being left
   out. I accept this as meeting the criterion; the design chose the data set this way ("14 Published records").
3. **Enter with the query already applied may be served from the cache** (Informational 3). I read AC-002.3's
   "SHALL call" as "search with this text", not as "always send a new request". The user or FE Lead may decide
   that every Enter must reach the API.
4. **The excerpt counts as part of the Solution text** for the mock's `q` match (`mocks/handlers.ts:18`). The
   contract calls the excerpt "the Symptoms and Resolution steps", which are parts of the Solution.
5. **The magnifier and clear-icon triggers are acceptable** (Informational 1). The spec names Enter and says
   nothing about the clear icon.
6. **No coverage threshold:** the spec sets none (NFR 1 is one test per criterion), and `frontend/` has no
   coverage script.

## Traceability
```
  ✓ AC-002.1  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.2  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.3  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.4  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.5  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.6  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.7  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.8  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.9  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.10  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.11  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.12  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.13  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.14  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-004.1  frontend/src/features/tickets/mocks/handlers.test.ts      ← Task 1
  ✓ AC-004.2  frontend/src/features/tickets/mocks/handlers.test.ts      ← Task 1
  ✓ AC-004.3  frontend/src/features/search/mocks/handlers.test.ts
  ✗ AC-004.4  no test under this module's paths (...)   ← Task 3, out of this feature
trace search (STORY-002, STORY-004): 17/18 acceptance criteria have a test
```
All 15 criteria of this task have a named test. I read each test, and each one checks its criterion; none only
mentions the id.

## Issues
None blocking. Informational items 1–7 are listed under Stage 2 above. Items 1 (a test for the clear icon) and 2
(cap the query from the URL) are worth a small follow-up. Item 3 is a judgment call for the FE Lead.

## Test Report
Full report: `harnessclaude/artifacts/06-evaluation/search/task-2-ticket-search-test-report.md`.
- `npx vitest run src/features/search`: `Tests 29 passed (29)`, 50.2 s
- `npm run format:check`: pass · `npm run lint`: pass · `npm run typecheck`: pass · `npm run build`: pass
- `npm run generate:api` then `git diff --exit-code -- src/shared/api/generated`: exit 0, no diff
- `npm run test`: not run by the reviewer (Assumption 1)
- `sdd.ts trace search STORY-002 STORY-004`: 17/18. The one ✗ (AC-004.4) belongs to Task 3.
- Coverage: no coverage script; the spec sets no threshold.
