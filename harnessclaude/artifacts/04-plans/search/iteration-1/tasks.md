---
module: search
date: 2026-10-24
total_tasks: 1
owner: Dev
---

# Tasks: search — iteration 1

Spec: `artifacts/03-modules/search/spec.md` · Design: `artifacts/04-plans/search/design.md`

One task = one Jira issue = one branch = one PR. Every test's `it` name starts with the AC id it proves (guideline 10
T6). The task ends with the frontend `verify` (root `CLAUDE.md`).

## Task 1: Ticket List with filters, on mocks

**Story:** STORY-001, STORY-002
**Feature:** ticket-list
**Jira:** KAN-77 · branch `feature/KAN-77-search-ticket-list`
**Description:** One vertical slice (guideline 06: an endpoint and its first screen in one PR).
- Contract: tag `tickets`, `GET /api/v1/tickets` (operationId `getTickets`) with `keyword`, `departmentId`, `source`,
  `status` and the shared paging parameters; schemas `TicketStatus`, `TicketSummary`, `TicketSummaryPage` (spec
  Interface Contracts); `npm run generate:api`.
- Mocks: `mocks/ticket-data.ts` (26 tickets, design Data Model) and the handler in `mocks/handlers.ts` applying
  keyword, filters, sort and paging; `resetMockData()` stays a no-op (the data never changes).
- `hooks/useTicketList.ts`: `useGetTickets` with `keepPreviousData`.
- `components/TicketListFilters.tsx`: `Input.Search` (Enter / icon, trimmed; draft keyed by the applied keyword),
  Department / Source / Status `Select`s with the "All…" option; labels from `TICKET_SOURCE_LABELS` and `STATUS_TAGS`.
- `components/TicketListTable.tsx`: `DataTable` with the 8 columns of Figma 6.2, sortable Ticket ID, Title, Project,
  Review due date, Updated; Status as `StatusTag`; empty MSG04.
- `pages/TicketListPage.tsx`: `useListSearchParams({ field: 'updatedAt', order: 'desc' })`, URL values validated,
  replaces the placeholder (keeps `export default`).
- Tests: `pages/TicketListPage.test.tsx` (AC-001.1–AC-001.13), `mocks/handlers.test.ts` (AC-002.1, AC-002.2).
**Files:** `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**`,
`frontend/src/features/tickets/{hooks,components,pages,mocks}/**`
**Depends on:** —
