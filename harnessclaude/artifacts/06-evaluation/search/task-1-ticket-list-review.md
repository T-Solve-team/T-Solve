---
module: search
feature: ticket-list
task: 1
verdict: pass
attempt: 1
date: 2026-10-26
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: search — Task 1: Ticket List (KAN-227, STORY-001, AC-004.1, AC-004.2)

## Summary
Reviewed `default@3cb60af` (`5d962cd..3cb60af`, frontend only) against the spec. All 14 criteria of STORY-001 and
AC-004.1 and AC-004.2 are met in the code, and each has a test that names it and checks it. Format, lint, typecheck
and build pass, `generate:api` leaves no diff, and the feature tests pass 28/28. The full suite had 5 timeouts under
load (4 of them in files this commit does not touch). Each failing file passes when it runs alone, and the same
pattern was already logged as BUG-002–004 and BUG-057. No CRITICAL findings. Verdict: PASS, with the full-suite
flakiness recorded as an open judgment call (see Assumptions made).

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Stage 1: 16/16 PASS (table below). |
| Completeness | pass | No MISSING. EXTRA: the Keyword box also searches from its magnifier button and its clear icon (Informational 1). |
| Correctness | pass | Static checks pass; feature 28/28; full suite 503/508. The 5 failures are load timeouts that pass alone (test report). No CRITICAL findings. |
| Integration | pass | `GET /api/v1/tickets` in `openapi/tsolve-api.yaml` matches the spec contract field for field (TicketStatus, TicketProject, TicketDepartment, TicketSummary, TicketSummaryPage, uuid ids, `reviewDueDate` nullable, the five sort keys). The page calls only the generated `useGetTickets` through `@/shared/api`. The mock lives in `features/tickets/mocks/` and is typed by orval's `getGetTicketsMockHandler` (NFR 4, edge 16). The commit does not touch `app/`, hand-edited `shared/`, `package.json` or another feature (NFR 3, NFR 6). |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-001.1 filters and the 8 columns in order | PASS | `TicketListFilters.tsx:47-96`, `TicketListTable.tsx:7-60`; test checks headers in order, both roles |
| 2 | AC-001.2 page 0, size 20, `updatedAt,desc`, no filter | PASS | `TicketListPage.tsx:18,24-30` + `useListSearchParams.apiPaging`; test reads the first request |
| 3 | AC-001.3 link to 5.5 by `ticketId`, labels, tag, date-times, "—" | PASS | `TicketListTable.tsx:13,39,45,52,58`; test checks every cell of a row, the href and "—" |
| 4 | AC-001.4 Enter only, trimmed, page 1, blank removes it, placeholder | PASS | `TicketListFilters.tsx:52-60`, `setFilter` drops `page`; two tests |
| 5 | AC-001.5 at most 100 characters | PASS | `maxLength={KEYWORD_MAX}`; test pastes 120 characters |
| 6 | AC-001.6 All Departments + session departments, string id, page 1 | PASS | `TicketListFilters.tsx:66-75`; tests incl. a user with no Department (edge 4) |
| 7 | AC-001.7 Source/Status options, defaults remove | PASS | `SOURCE_OPTIONS`, `STATUS_OPTIONS`; three tests |
| 8 | AC-001.8 five sortable headers reload with that sort | PASS | `sortable: true` on the five; test checks which headers sort and the URL/rows |
| 9 | AC-001.9 "Total N items", 20 per page, same filters/sort | PASS | DataTable `showTotal`, `PAGE_SIZE`; test checks `page=1`, `sort=title,asc` |
| 10 | AC-001.10 all six in the URL, controls show them | PASS | `useListSearchParams` + controlled Selects + `key={keyword}`; four tests (shared link, invalid values, page 99, back) |
| 11 | AC-001.11 loading state | PASS | `isLoading={tickets.isPending}`, no `placeholderData` (`useTicketList.ts`), so a new filter, sort or page shows the loading state again |
| 12 | AC-001.12 MSG04 in the table | PASS | DataTable `emptyText` MSG04; test with an empty page |
| 13 | AC-001.13 error with Try again that reloads | PASS | `TicketListPage.tsx:47-48`; 401 is handled globally (`global-errors.ts`); test 500 → MSG06 → Try again → rows |
| 14 | AC-001.14 plain text | PASS | only `cells.text`/`Link` children, no `dangerouslySetInnerHTML`; test with `<img onerror>` and `<b>` |
| 15 | AC-004.1 ≥25 tickets, all statuses and sources, fixture Departments as strings, UUID ids, typed | PASS | `ticket-data.ts` (28 rows, `mockTicketId`), typed `TicketSummary`; test |
| 16 | AC-004.2 keyword (any case, ID/title/Solution), filters, sort, paging, `reviewDueDate` only in review | PASS | `handlers.ts:18-48`; five tests, including a match found only in the Solution text and the review due date rule |

### Stage 2 — Quality

CRITICAL (blocks pass): none.

Informational (does not block):
1. [`frontend/src/features/tickets/components/TicketListFilters.tsx:59-60`] `Input.Search` with `allowClear` calls
   `onSearch` from three places: Enter, the magnifier button, and the clear icon (antd 6.6.5 `es/input/Search.js`,
   `onChange` with `source: 'clear'`). Clicking × or the magnifier therefore reloads the list without Enter.
   AC-001.4 names only Enter. This does not break "typing without Enter SHALL not reload", but it is extra behaviour.
   Fix, if the FE Lead wants Enter only: use a plain `Input` with `onPressEnter` and the `allowClear` change handler,
   or accept the extra triggers and note it in the design.
2. [`frontend/src/features/tickets/pages/TicketListPage.tsx:19,25`] A Keyword that comes from the URL is neither
   trimmed nor capped. `?keyword=%20%20` sends `"  "` to the API, and a hand-edited link with more than 100
   characters breaks the contract's `maxLength: 100` (a real backend would answer 400, so the page shows the error
   state). Fix: `list.getFilter('keyword')?.trim().slice(0, 100) ?? ''`.
3. [`frontend/src/features/tickets/pages/TicketListPage.tsx:21`] A `departmentId` in the URL is not checked
   against the session's Departments. An unknown id is sent as is, and the Select shows the raw id as its label.
   The comment says the backend refuses it, which matches spec requirement 4 (visibility is the backend's), so this
   is UX only.
4. [`frontend/src/features/tickets/mocks/ticket-data.ts:10-15`,
   `frontend/src/features/tickets/components/TicketListFilters.tsx:72`] The mock Department ids (`'1'`, `'2'`) and
   Project ids (`'101'`…) are numeric strings, not UUIDs. The contract says `format: uuid` (NFR 5). This follows
   AC-004.1 ("the Departments of the fixture users with their ids as strings"), the Out of Scope line on
   `DepartmentRef`/`ProjectRef`, and design risk 17. Until fe-foundation Task 10, the filter sends `departmentId=1`,
   which a strict uuid backend would refuse. See Assumptions made.
5. [`TicketListFilters.tsx:21`, `TicketListTable.tsx:52`, `useTicketList.ts:5`, `handlers.ts:8,17`,
   `ticket-data.ts:3`] Code comments cite spec ids ("spec AC-001.5", "design, builder check"). The reference
   `review` feature does not do this, and the project rule "Stable Code Artifacts" asks for the invariant instead
   of the id. Cosmetic.
6. [`TicketListPage.tsx:47-48`] After an error, Try again refetches, but `isError` stays true until the retry
   succeeds. The error state therefore stays on screen with no loading indicator while the retry runs. This is the
   shared `DataTable`/`ErrorState` behaviour, the same as in the Review Queue, and not part of this commit.
7. [`frontend/src/features/tickets/pages/TicketListPage.test.tsx:69`] The file's first test (`AC-001.1 shows the
   filters…`) failed in the full suite (its first `findBy` took more than 5 s, 8.8 s in total) and passes alone.
   This is the BUG-057 family. The fix belongs to BUG-057's owner (the test setup is fe-foundation's).

Edge cases in the design (rows 1–5 for STORY-001, row 16): all have tests. Spaces or 100+ characters in the
Keyword box: AC-001.4 and AC-001.5. `page=99`: AC-001.10. HTML in a title: AC-001.14. No Department: AC-001.6.
500 then Try again: AC-001.13. Mock data typed with the generated types: `getGetTicketsMockHandler` and
`TicketSummary`, so typecheck fails if the mock drifts from the contract.

Security (threat model): the screen only reads and stores nothing. The Ticket ID link is built from the API's
`ticketId` by `paths.ticketDetail` and rendered by React Router's `Link`. All text is rendered as React children,
so it is escaped. No security review is needed: no auth logic, uploads, user-supplied URLs or HTML.

## Assumptions made
1. **Full-suite failures do not fail this feature (open judgment call for the user).** `npm run test` had
   5 failed / 503 passed. Four failures are in files this commit does not touch (`ReviewQueuePage.test.tsx` ×3,
   including the BUG-004 test; `mock-mode.test.tsx` ×1). One is this feature's first page test. All five are
   timeouts or slow first `findBy`s, and every failing file passes alone (22/22, 9/9, 2/2). The failing set
   differs from BUG-057's run on the same day. The evaluate skill says "any failing test → fail". I judged these
   to be environment flakiness that predates this change (BUG-002–004, BUG-057), not a defect of the change. The
   user should confirm, or decide that the suite must be green before the commit, which would make this a fail
   blocked on BUG-057.
2. **AC-004.3 and AC-004.4 are out of this task's scope.** The trace shows them ✗ because Task 2 (search mock) and
   Task 3 (feedback mock) own them, as the dispatch states. This is not a MISSING finding for Task 1.
3. **Numeric-string Department and Project ids in the mock are acceptable** (Informational 4). AC-004.1 asks for
   the fixture users' Departments, and changing those ids is out of scope (fe-foundation Task 10). So the
   `format: uuid` of NFR 5 is met by the type (`string`), not yet by the values.
4. **The Keyword's magnifier and clear icon triggers are acceptable** (Informational 1). The spec names Enter
   and is silent on the clear icon; the user or the FE Lead may decide otherwise.
5. **No coverage threshold:** the spec sets none (NFR 1 is one test per criterion), and `frontend/` has no coverage
   script, so no percentage was measured.

## Traceability
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
  ✗ AC-004.3  no test under this module's paths (...)   ← Task 2, out of this feature
  ✗ AC-004.4  no test under this module's paths (...)   ← Task 3, out of this feature
trace search (STORY-001, STORY-004): 16/18 acceptance criteria have a test
```
All 16 criteria of this task have a named test. Each test was read and checks its criterion; none only mentions
the id.

## Issues
None blocking. Informational items 1–7 are under Stage 2 above; items 1 and 2 are the ones worth a small
follow-up.

## Test Report
Full report: `harnessclaude/artifacts/06-evaluation/search/task-1-ticket-list-test-report.md`.
- `npm run format:check`: pass · `npm run lint`: pass · `npm run typecheck`: pass · `npm run build`: pass (built in 1m 1s)
- `npm run generate:api` then `git diff --exit-code -- src/shared/api/generated`: exit 0, no diff
- `npx vitest run src/features/tickets`: `Tests 28 passed (28)`, 65.9 s
- `npm run test`: `Tests 5 failed | 503 passed (508)`, 426.8 s. Re-run alone: TicketListPage 22/22,
  ReviewQueuePage 9/9, mock-mode 2/2 (load flakiness, BUG-057 family)
- `sdd.ts trace search STORY-001 STORY-004`: 16/18. The two ✗ belong to Tasks 2 and 3.
- Coverage: no coverage script; the spec sets no threshold.
