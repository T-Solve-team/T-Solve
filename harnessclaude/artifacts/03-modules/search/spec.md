---
module: search
date: 2026-10-24
owner: BA
---

# Spec: search

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-10-24 | STORY-001, STORY-002 | Initial spec: 6.2 Ticket List (UC-27) from the guidelines (03 §4, 08, 12 §1) and Figma 6.2 (a)–(c); the backend is mocked for now | Plan the Ticket List (KAN-77); Hải Anh 24/10: Keyword searches on Enter, default sort Updated newest first with sortable columns |

## Requirements

### Functional
1. A Ticket Resolver or Project Manager browses the tickets they may see on screen 6.2 Ticket List (UC-27), one page
   at a time, and opens a ticket's 5.5 Ticket Detail from its Ticket ID.
2. The list is filtered by Keyword (ticket ID, title or Solution), Department, Source and Status, and sorted by
   column; filters, page and sort are kept in the URL.
3. Which tickets a user may see (Permission Matrix note ⁵) is decided by the backend; the web app shows what the API
   returns.
4. Semantic search (6.1, UC-26) and feedback (8.1, UC-28) are later stories of this module.

### Non-Functional
1. Every acceptance criterion has a test whose `it` name starts with its id; `sdd.ts trace search STORY-001
   STORY-002` passes.
2. The PR passes the frontend `verify`: `format:check`, `lint`, `typecheck`, `test`, `build`, `generate:api` with no
   diff.
3. No new npm dependency.
4. The screen depends only on the REST contract below: replacing the MSW mocks with the real backend needs no change
   in `frontend/src/features/tickets/` other than its `mocks/` folder.
5. Changes to `app/`, `shared/`, `mocks/` or `test/` (fe-foundation) are not part of this module; if one is needed,
   it is a quick change or a change request decided by the FE Lead.

## User Stories

### STORY-001: Browse the Ticket List
- As a Ticket Resolver or Project Manager, I want to find tickets by ID, title, Solution, Department, source and
  status, so that I can follow the knowledge of my Projects and open a ticket.
- Acceptance:
  - AC-001.1: WHEN a Ticket Resolver or Project Manager opens `/tickets`, THE web app SHALL show 6.2 Ticket List with
    the filters Keyword, Department, Source and Status, and a table with the columns Ticket ID, Title, Project,
    Department, Source, Status, Review due date and Updated, in that order.
  - AC-001.2: WHEN the list is opened, THE web app SHALL call `GET /api/v1/tickets` with page 0, size 20 and sort
    `updatedAt,desc`, with no filter.
  - AC-001.3: THE web app SHALL show each row's Ticket ID as a link to 5.5 Ticket Detail of that ticket, the Project
    name, the Department name, the source label (Solution Form · Jira import · CSV · Manual), the status as a status
    tag (Pending review · Changes requested · Published · Rejected · Unpublished · Expired), and Review due date and
    Updated as date and time; an empty Review due date SHALL show "—".
  - AC-001.4: WHEN the user presses Enter in the Keyword box, THE web app SHALL reload the list from page 1 with the
    trimmed text as `keyword`; an empty or white-space text SHALL remove the keyword, and typing without Enter SHALL
    not reload the list. The box's placeholder is "Search by ticket ID, title or solution".
  - AC-001.5: THE Department filter SHALL offer "All Departments" (the default) and the user's own Departments;
    WHEN the user picks one, THE web app SHALL reload the list from page 1 with its `departmentId`, and "All
    Departments" SHALL remove it.
  - AC-001.6: THE Source filter SHALL offer "All" (the default) and the four source labels; THE Status filter SHALL
    offer "All statuses" (the default) and the six status labels. WHEN the user picks a value, THE web app SHALL
    reload the list from page 1 with `source` or `status`; the default SHALL remove it.
  - AC-001.7: THE columns Ticket ID, Title, Project, Review due date and Updated SHALL be sortable; WHEN the user
    clicks a sortable header, THE web app SHALL reload the list with that sort.
  - AC-001.8: THE table SHALL show "Total N items" and pagination of 20 rows per page; WHEN the user picks a page,
    THE web app SHALL load that page with the same filters and sort.
  - AC-001.9: THE keyword, Department, Source, Status, page and sort SHALL be kept in the URL, so that a reload or a
    shared link shows the same list, with the filter controls showing those values.
  - AC-001.10: WHILE the first page is loading, THE web app SHALL show the loading state of the table (Figma 6.2 (c)).
  - AC-001.11: WHEN the API returns no ticket, THE web app SHALL show MSG04 ("No results found.") in the table
    (Figma 6.2 (b)).
  - AC-001.12: IF the request fails with an error other than 401, THEN THE web app SHALL show the error state of the
    table with Try again, and Try again SHALL load the list again.
  - AC-001.13: THE web app SHALL show the title and every text field as plain text, never as HTML.

### STORY-002: Mock tickets
- As a developer, I want realistic mock tickets behind the contract, so that the Ticket List can be built and
  demonstrated before the backend exists.
- Acceptance:
  - AC-002.1: WHILE mock mode is on, THE MSW handler of `GET /api/v1/tickets` SHALL return at least 25 tickets that
    cover every status and every source and at least two Departments, typed with the generated types.
  - AC-002.2: THE mock SHALL apply `keyword` (case-insensitive, on ticket ID, title and Solution text),
    `departmentId`, `source`, `status`, the sort of AC-001.7 and the paging, and SHALL return `reviewDueDate` only for
    Pending review and Changes requested tickets.

## Out of Scope
- The backend `search` package (`GET /api/v1/tickets`, the visibility rule of Permission Matrix note ⁵) — added later
  by a change to this spec. Until then MSW serves the contract.
- 6.1 Search (UC-26) and 8.1 Give Feedback (UC-28) — later stories of this module.
- 5.5 Ticket Detail (`ticket-repository`); it stays a placeholder.
- Row actions, row selection, export, and polling of the list.
- A Project filter (glossary 03 §5: not used).

## Interface Contracts

### Exposed by this module
REST, in `frontend/openapi/tsolve-api.yaml`, tag `tickets` (served by the backend later):

```yaml
GET /api/v1/tickets?keyword=&departmentId=&source=&status=&page=&size=&sort=   # operationId getTickets
  keyword:      string, optional        # ticket ID, title or Solution text
  departmentId: int64, optional         # omitted = every Department the user may see
  source:       TicketSource, optional
  status:       TicketStatus, optional
  page, size, sort: the shared Page, Size, Sort parameters; default sort updatedAt,desc
  200:      TicketSummaryPage
  401, 403: ProblemDetail

TicketStatus: PENDING_REVIEW | CHANGES_REQUESTED | PUBLISHED | REJECTED | UNPUBLISHED | EXPIRED
TicketSummary:
  required: [ticketId, sourceTicketId, title, project, department, source, status, reviewDueDate, updatedAt]
  ticketId:       int64
  sourceTicketId: string                 # the Jira issue key, e.g. ITSUP-1031
  title:          string
  project:        ProjectRef
  department:     DepartmentRef
  source:         TicketSource
  status:         TicketStatus
  reviewDueDate:  date-time | null       # only while Pending review or Changes requested
  updatedAt:      date-time
TicketSummaryPage:
  required: [content, page]
  content: TicketSummary[]
  page:    PageMetadata
```

Sort keys: `sourceTicketId`, `title`, `project`, `reviewDueDate`, `updatedAt`.

### Required from other modules
- `fe-foundation` (done): route `/tickets` for Ticket Resolver and Project Manager, `paths.ticketDetail`, the session
  Departments, `DataTable` (loading, error with Try again, empty MSG04, pagination, sort), `StatusTag`,
  `TICKET_SOURCE_LABELS`, `useListSearchParams`, MSG04.
- `ticket-repository` (later, backend): the tickets and their Solutions that are listed.

## Definition of Done
- [ ] Every acceptance criterion of STORY-001 and STORY-002 has a test whose name starts with its AC id;
      `sdd.ts trace search STORY-001 STORY-002` passes
- [ ] The frontend `verify` passes on the KAN-77 PR
- [ ] The PR is reviewed by the FE Lead
