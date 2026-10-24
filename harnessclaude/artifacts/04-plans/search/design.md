---
module: search
date: 2026-10-24
owner: Dev
---

# Design: search

Spec: `artifacts/03-modules/search/spec.md`

## Architecture Overview

Frontend only for now (STORY-001, STORY-002), in the feature `tickets` (guideline 01: 6.2 lives there). The screen
follows the reference feature `review` (7.1 Review Queue): a generated orval hook wrapped in `hooks/`, filters in a
toolbar component, the table in a component on the kit `DataTable`, page/sort/filters in the URL through
`useListSearchParams`. MSW serves the contract until the backend `search` package exists.

```
TicketListPage ── useListSearchParams (URL: keyword, departmentId, source, status, page, sort)
   ├─ TicketListFilters  (Input.Search · Select ×3)        ── session.departments
   └─ TicketListTable    (DataTable, 8 columns)            ── useTicketList → useGetTickets → GET /api/v1/tickets
                                                                              └─ mocks/handlers.ts (MSW)
```

## Data Model

Contract types are generated from `frontend/openapi/tsolve-api.yaml` (spec Interface Contracts): `TicketStatus`
(new enum), `TicketSummary`, `TicketSummaryPage`, `GetTicketsParams`; `ProjectRef`, `DepartmentRef`, `TicketSource`
and `PageMetadata` are reused.

Mock data (`features/tickets/mocks/ticket-data.ts`): 26 tickets in two Departments (IT, HR) and three Projects
(ITSUP, ITNET in IT; HRHELP in HR — the ids of the mock session users), every status and source, each with a Solution
text used only by the keyword filter (it is not part of `TicketSummary`). The first eight are the rows of Figma 6.2
(NETOPS / TCK keys renamed to the mock Projects).

## Key Decisions

| Decision | Chosen | Alternatives | Rationale |
|----------|--------|--------------|-----------|
| Keyword trigger | antd `Input.Search`, search on Enter / the search icon | debounce while typing | User, 24/10 (AC-001.4); no request per keystroke |
| Keyword draft | local `useState` initialised from the URL; the input gets `key={keyword}` so back/forward resets it | controlled by the URL on every keystroke | Typing must not reload (AC-001.4); the URL stays the single source of the applied filter |
| Default sort | `updatedAt,desc`; sortable Ticket ID, Title, Project, Review due date, Updated | no column sort | User, 24/10 (AC-001.7); same pattern as Review Queue |
| Filter values in the URL | `departmentId`, `source`, `status`, `keyword`; unknown values are ignored (treated as "All") | trust the URL | A hand-edited link must not send an invalid enum to the API |
| Labels | `TICKET_SOURCE_LABELS` (shared/lib) and `STATUS_TAGS` (shared/ui) | new label maps | Reuse; glossary words exactly (guideline 03) |
| Polling | none | refetch interval | Only the Review Queue has the 5 s NFR |
| Page size | `PAGE_SIZE` (20) from shared/lib | 8 as in Figma | SRS default 20; Figma shows a sample |

## File Structure

```
frontend/openapi/tsolve-api.yaml                       # tag tickets, GET /api/v1/tickets, TicketStatus, TicketSummary(Page)
frontend/src/shared/api/generated/**                   # npm run generate:api
frontend/src/features/tickets/
  hooks/useTicketList.ts                               # useGetTickets + keepPreviousData
  components/TicketListFilters.tsx (+ .module.css)     # Keyword, Department, Source, Status
  components/TicketListTable.tsx                       # DataTable, 8 columns
  pages/TicketListPage.tsx (+ .module.css)             # replaces the placeholder
  pages/TicketListPage.test.tsx                        # AC-001.x
  mocks/ticket-data.ts                                 # 26 tickets
  mocks/handlers.ts                                    # GET /api/v1/tickets with filters, sort, paging
  mocks/handlers.test.ts                               # AC-002.x
```

## External Dependencies

None new (spec NFR 3).

## Edge Cases & Risks

| # | Case | Severity | Handling | Test |
|---|------|----------|----------|------|
| 1 | URL has `status=FOO` or `departmentId=abc` | Medium | Ignored, control shows "All"; no invalid param sent | AC-001.9 |
| 2 | Back/forward changes `keyword` in the URL | Medium | `key={keyword}` resets the draft | AC-001.9 |
| 3 | Keyword of only spaces | Medium | Removes the keyword | AC-001.4 |
| 4 | `reviewDueDate` null | High | "—" | AC-001.3 |
| 5 | Filter change while a page is shown | Low | `keepPreviousData`, back to page 1 | AC-001.5, AC-001.6 |
| 6 | Title with HTML | High | rendered as text by React | AC-001.13 |
| 7 | 401 | Medium | global handler (MSG07); the table shows no error | covered by fe-foundation |
| 8 | Page past the last page (old link) | Low | API returns empty content → MSG04 | AC-001.11 |
| 9 | Long titles | Low | DataTable ellipsis, as Figma | visual |
