---
module: search
date: 2026-10-25
owner: BA
---

# Spec: search

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-10-25 | STORY-001..STORY-004 | Initial spec of the web app part: 6.2 Ticket List (UC-27), 6.1 Search (UC-26), 8.1 Give Feedback (UC-28) on MSW mocks; ids are UUID strings | Plan the search module's screens (KAN-147, KAN-145, KAN-148) from the 25/10 baseline (UC list, SRS_Delta §6, Report3 §III 6.1, 6.2, 8.1) and Figma; Hải Anh 25/10: the popup is a component that 5.5 mounts (Q1), UUID ids now without waiting for fe-foundation Task 10 (Q2), no search before a query (Q3), no helpful rate without Feedback (Q4); 24/10: Keyword on Enter, Ticket List sorted by Updated newest first |
| 2026-10-25 | STORY-003, STORY-004 | AC-003.5, AC-003.8, AC-003.11, AC-004.4 and the contract: the Feedback field `helpful` is named `isHelpful` and the popup prop `open` is named `isOpen`; no behaviour changes | Builder check of the plan: guideline 03 (booleans start with `is`; glossary row "Feedback: `isHelpful`"), backend `AGENTS.md` (boolean `is` prefix) and `FormModal`'s `isOpen` |

## Requirements

### Functional
1. A Ticket Resolver or Project Manager browses the tickets they may see on 6.2 Ticket List (UC-27), filtered and
   sorted, and opens a ticket's 5.5 Ticket Detail from its Ticket ID.
2. A Ticket Resolver or Project Manager searches the Published tickets of their own Departments on 6.1 Search
   (UC-26) with a query, filters the results, and opens a result's 5.5 Ticket Detail.
3. A Ticket Resolver or Project Manager marks a Published ticket Helpful or Not helpful, with an optional comment,
   in the 8.1 Give Feedback popup (UC-28); a new Feedback replaces the user's previous one on that ticket.
4. Which tickets a user may see and search (UC-27: Published tickets of own Departments plus the tickets in other
   statuses the user wrote or that belong to the user's Projects; UC-26: Published tickets of own Departments,
   GBR-02, D25, D26), the ranking, the Search Log and the stored Feedback are decided by the backend; the web app
   shows what the API returns and never sends a Workspace (GBR-37).

### Non-Functional
1. Every acceptance criterion has a test whose `it` name starts with its id; `sdd.ts trace search` passes.
2. The PR passes the frontend `verify`: `format:check`, `lint`, `typecheck`, `test`, `build`, and `generate:api`
   leaves no diff.
3. No new npm dependency.
4. The screens depend only on the REST contract below: replacing the MSW mocks with the real backend needs no
   change outside the features' `mocks/` folders.
5. Every id in the contract below is a UUID string (`type: string`, `format: uuid`), never a number.
6. Changes to `app/`, `shared/`, `mocks/` or `test/` (fe-foundation) are not part of this module; if one is
   needed, it is a quick change or a change request decided by the FE Lead.

## User Stories

### STORY-001: Browse the Ticket List
- As a Ticket Resolver or Project Manager, I want to find tickets by Ticket ID, title, Solution, Department,
  source and status, so that I can follow the knowledge of my Projects and open a ticket.
- Acceptance:
  - AC-001.1: WHEN a Ticket Resolver or Project Manager opens `/tickets`, THE web app SHALL show 6.2 Ticket List
    with the filters Keyword, Department, Source and Status, and a table with the columns Ticket ID, Title,
    Project, Department, Source, Status, Review due date and Updated, in that order.
  - AC-001.2: WHEN the list is opened with no filter, page or sort in the URL, THE web app SHALL call
    `GET /api/v1/tickets` with page 0, size 20 and sort `updatedAt,desc`, and no filter.
  - AC-001.3: THE web app SHALL show each row's Ticket ID (`sourceTicketId`) as a link to 5.5 Ticket Detail of
    that ticket (`ticketId`), the Project name, the Department name, the source label (Solution Form · Jira
    import · CSV · Manual), the status as a status tag (Pending review · Changes requested · Published · Rejected
    · Unpublished · Expired), and Review due date and Updated as date and time; an empty Review due date SHALL
    show "—".
  - AC-001.4: WHEN the user presses Enter in the Keyword box, THE web app SHALL reload the list from page 1 with
    the trimmed text as `keyword`; an empty or white-space text SHALL remove the keyword, and typing without
    Enter SHALL not reload the list. The box's placeholder is "Search by ticket ID, title or solution".
  - AC-001.5: THE Keyword box SHALL accept at most 100 characters.
  - AC-001.6: THE Department filter SHALL offer "All Departments" (the default) and each Department of the
    session's `departments`; WHEN the user picks one, THE web app SHALL reload the list from page 1 with that
    Department's id as a string in `departmentId`, and "All Departments" SHALL remove it.
  - AC-001.7: THE Source filter SHALL offer "All" (the default) and the four source labels; THE Status filter
    SHALL offer "All statuses" (the default) and the six status labels. WHEN the user picks a value, THE web app
    SHALL reload the list from page 1 with `source` or `status`; the default SHALL remove it.
  - AC-001.8: THE columns Ticket ID, Title, Project, Review due date and Updated SHALL be sortable; WHEN the user
    clicks a sortable header, THE web app SHALL reload the list with that sort.
  - AC-001.9: THE table SHALL show "Total N items" and pagination of 20 rows per page; WHEN the user picks a page,
    THE web app SHALL load that page with the same filters and sort.
  - AC-001.10: THE keyword, Department, Source, Status, page and sort SHALL be kept in the URL, so that a reload
    or a shared link shows the same list, with the filter controls showing those values.
  - AC-001.11: WHILE the list is loading, THE web app SHALL show the loading state of the table (Figma 6.2 (c)).
  - AC-001.12: WHEN the API returns no ticket, THE web app SHALL show MSG04 ("No results found.") in the table
    (Figma 6.2 (b)).
  - AC-001.13: IF the request fails with an error other than 401, THEN THE web app SHALL show the error state of
    the table with Try again, and Try again SHALL load the list again.
  - AC-001.14: THE web app SHALL show the title and every text field as plain text, never as HTML.

### STORY-002: Search Published tickets
- As a Ticket Resolver or Project Manager, I want to search the Published tickets of my Departments by meaning
  and keywords, so that I find an existing Solution quickly.
- Acceptance:
  - AC-002.1: WHEN a Ticket Resolver or Project Manager opens `/search`, THE web app SHALL show 6.1 Search with
    the Search box and the filters Department, Source and Resolved date, above the results area.
  - AC-002.2: WHILE the URL has no query, or only white space, THE web app SHALL not call the search API and
    SHALL show the results area empty, with no message (decision Q3).
  - AC-002.3: WHEN the user presses Enter in the Search box with a non-blank text, THE web app SHALL call
    `GET /api/v1/search/tickets` with the trimmed text as `q`, the current filters, page 0 and size 20;
    pressing Enter with an empty or white-space text SHALL do nothing, and typing without Enter SHALL not
    search.
  - AC-002.4: THE Search box SHALL accept at most 200 characters.
  - AC-002.5: THE Department filter SHALL offer "All Departments" (the default) and each Department of the
    session's `departments`, sent as that Department's id as a string in `departmentId`; THE Source filter SHALL
    offer "All" (the default) and the four source labels, sent as `source`; THE Resolved date filter SHALL be an
    optional date range, sent as `resolvedFrom` and `resolvedTo` (`YYYY-MM-DD`, both days included). The default
    or a cleared value SHALL remove the parameter.
  - AC-002.6: WHEN the user changes a filter while the URL has a query, THE web app SHALL search again from page
    1 with the new filters; WHILE the URL has no query, a filter change SHALL only be kept in the URL.
  - AC-002.7: THE web app SHALL show each result, in the order the API returns them, as a card with the title as
    a link to 5.5 Ticket Detail of that ticket, a line "Project · Department · Resolved <date and time>", the
    helpful rate as "<N>% helpful" with a thumbs-up icon, and the excerpt (Figma 6.1).
  - AC-002.8: IF a result's `helpfulRate` is null (no Feedback yet), THEN THE web app SHALL show no helpful rate
    on that card (decision Q4).
  - AC-002.9: THE results SHALL show "Total N items" and pagination of 20 results per page; WHEN the user picks a
    page, THE web app SHALL load that page with the same query and filters.
  - AC-002.10: THE query, Department, Source, Resolved date range and page SHALL be kept in the URL, so that a
    reload or a shared link runs the same search, with the Search box and the filters showing those values.
  - AC-002.11: WHILE a search is loading, THE web app SHALL show the loading state in the results area (Figma
    6.1 (c)).
  - AC-002.12: WHEN the API returns no result, THE web app SHALL show MSG04 ("No results found.") in the results
    area (Figma 6.1 (b)).
  - AC-002.13: IF the search fails with an error other than 401, THEN THE web app SHALL show the error state in
    the results area with Try again, and Try again SHALL run the same search again.
  - AC-002.14: THE web app SHALL show the title, the excerpt and every text field as plain text, never as HTML.

### STORY-003: Give Feedback popup
- As a Ticket Resolver or Project Manager, I want to say whether a Published ticket helped me, so that good
  Solutions are recognised and weak ones are improved.
- Acceptance:
  - AC-003.1: WHEN the popup is opened for a ticket, THE web app SHALL show 8.1 Give Feedback with the required
    field "Helpful?" as the two buttons Helpful and Not helpful, none selected, the optional multi-line field
    Comment, empty, and the buttons Cancel and Send (Figma 8.1).
  - AC-003.2: WHEN the user clicks Helpful or Not helpful, THE web app SHALL show that button as selected and the
    other as not selected.
  - AC-003.3: IF the user clicks Send with neither button selected, THEN THE web app SHALL show MSG01 ("The
    Helpful? field is required.") under the field and SHALL send nothing (Figma 8.1 (b)).
  - AC-003.4: IF the user clicks Send with a Comment longer than 500 characters, THEN THE web app SHALL show
    MSG02 ("Comment must not exceed 500 characters.") under the field and SHALL send nothing.
  - AC-003.5: WHEN the user clicks Send with a valid form, THE web app SHALL call
    `PUT /api/v1/tickets/{ticketId}/feedback` with `isHelpful` (true for Helpful) and the trimmed Comment as
    `comment`, or null when it is empty or white space.
  - AC-003.6: WHEN the request succeeds, THE web app SHALL close the popup and show MSG26 ("Thank you for your
    feedback.") as a success toast (Figma 8.1 (c)).
  - AC-003.7: WHILE the request is running, THE web app SHALL show Send as loading and SHALL not send the form a
    second time.
  - AC-003.8: IF the API answers with a field error on `isHelpful` or `comment`, THEN THE web app SHALL show that
    error's message (by its `code`) under the field and SHALL keep the popup open with what the user entered.
  - AC-003.9: IF the request fails with another error than 401 or a field error, THEN THE web app SHALL keep
    the popup open with what the user entered and show the message of the error's `code`, or MSG06 when it
    has none.
  - AC-003.10: WHEN the user clicks Cancel or the close icon, THE web app SHALL close the popup and send
    nothing; the next opening SHALL start empty (AC-003.1).
  - AC-003.11: THE popup SHALL be the component `GiveFeedbackModal` exported by `features/feedback/index.ts`,
    with the props `ticketId` (UUID string), `isOpen` and `onClose`, so that 5.5 Ticket Detail can mount it behind
    its "Give feedback" button (decision Q1).

### STORY-004: Mocks behind the contract
- As a developer, I want realistic mocks behind the contract, so that the three screens can be built and shown
  before the backend exists.
- Acceptance:
  - AC-004.1: WHILE mock mode is on, THE MSW handler of `GET /api/v1/tickets` SHALL return at least 25 tickets
    that cover every status and every source, spread over the Departments of the fixture users with their ids as
    strings, with UUID `ticketId`s, typed with the generated types.
  - AC-004.2: THE Ticket List mock SHALL apply `keyword` (case-insensitive, on Ticket ID, title and Solution
    text), `departmentId`, `source`, `status`, the sort of AC-001.8 and the paging, and SHALL return
    `reviewDueDate` only for Pending review and Changes requested tickets.
  - AC-004.3: THE MSW handler of `GET /api/v1/search/tickets` SHALL return only the Published mock tickets whose
    title or Solution text contains `q` (case-insensitive), apply `departmentId`, `source`, `resolvedFrom`,
    `resolvedTo` and the paging, and SHALL return `helpfulRate` null for at least one ticket.
  - AC-004.4: THE MSW handler of `PUT /api/v1/tickets/{ticketId}/feedback` SHALL answer 204 for a valid body,
    400 with field error MSG01 on `isHelpful` when it is missing, 400 with field error MSG02 on `comment` when it is
    longer than 500 characters, and 404 for an unknown or not Published ticket.

## Out of Scope
- The backend (`search` package): `GET /api/v1/tickets`, `GET /api/v1/search/tickets`,
  `PUT /api/v1/tickets/{ticketId}/feedback`, the visibility rules (UC-27, GBR-02, D25, D26, D32), semantic
  ranking with embeddings, the Search Log and searches with no results (UC-26), storing Feedback — added later by
  a change to this spec. Until then MSW serves the contract.
- The "Give feedback" button on 5.5 Ticket Detail and that screen (`ticket-repository`); it mounts
  `GiveFeedbackModal` (AC-003.11).
- Showing the user's previous Feedback in the popup; a new Feedback replaces it on the backend (UC-28).
- The Issue Panel search and suggestions in Jira (UC-26, KAN-146: `jira-app` and the backend).
- A search before a query or a "latest tickets" list on 6.1 (decision Q3).
- Row actions, row selection, export and polling of the Ticket List; a Project filter (Report3 §III 6.2).
- Changing the ids of `DepartmentRef`, `ProjectRef` or the session (`fe-foundation` Task 10, `ticket-review`).

## Interface Contracts

### Exposed by this module
REST, in `frontend/openapi/tsolve-api.yaml` (served by the backend later). Every id is a UUID string.

```yaml
GET /api/v1/tickets?keyword=&departmentId=&source=&status=&page=&size=&sort=   # tag tickets, operationId getTickets
  keyword:      string, optional, maxLength 100   # Ticket ID, title or Solution text
  departmentId: string (uuid), optional           # omitted = every Department the user may see
  source:       TicketSource, optional
  status:       TicketStatus, optional
  page, size, sort: the shared Page, Size, Sort parameters; default sort updatedAt,desc
  200:      TicketSummaryPage
  401, 403: ProblemDetail
  # sort keys: sourceTicketId, title, project, reviewDueDate, updatedAt

GET /api/v1/search/tickets?q=&departmentId=&source=&resolvedFrom=&resolvedTo=&page=&size=   # tag search, operationId searchTickets
  q:            string, required, minLength 1, maxLength 200
  departmentId: string (uuid), optional
  source:       TicketSource, optional
  resolvedFrom: string (date), optional          # day included
  resolvedTo:   string (date), optional          # day included
  page, size:   the shared Page and Size parameters (ranked by relevance, no sort)
  200:      TicketSearchResultPage
  400, 401, 403: ProblemDetail

PUT /api/v1/tickets/{ticketId}/feedback          # tag feedback, operationId giveFeedback
  ticketId: string (uuid)
  body:     TicketFeedbackRequest
  204:      no body                              # the user's Feedback on this ticket, replacing any previous one
  400:      ProblemDetail (field errors: MSG01 on isHelpful, MSG02 on comment)
  401, 403, 404: ProblemDetail

TicketStatus: PENDING_REVIEW | CHANGES_REQUESTED | PUBLISHED | REJECTED | UNPUBLISHED | EXPIRED
TicketProject:    { required: [projectId, name],    projectId: uuid,    name: string }
TicketDepartment: { required: [departmentId, name], departmentId: uuid, name: string }
TicketSummary:
  required: [ticketId, sourceTicketId, title, project, department, source, status, reviewDueDate, updatedAt]
  ticketId:       uuid
  sourceTicketId: string                 # the Ticket ID shown, e.g. ITSUP-1031
  title:          string
  project:        TicketProject
  department:     TicketDepartment
  source:         TicketSource
  status:         TicketStatus
  reviewDueDate:  date-time | null       # only while Pending review or Changes requested
  updatedAt:      date-time
TicketSummaryPage:      { required: [content, page], content: TicketSummary[],      page: PageMetadata }
TicketSearchResult:
  required: [ticketId, title, project, department, excerpt, helpfulRate, resolvedAt]
  ticketId:    uuid
  title:       string
  project:     TicketProject
  department:  TicketDepartment
  excerpt:     string                    # short excerpt of the Symptoms and Resolution steps, plain text
  helpfulRate: integer 0–100 | null      # rounded percent of Helpful Feedback; null = no Feedback yet
  resolvedAt:  date-time
TicketSearchResultPage: { required: [content, page], content: TicketSearchResult[], page: PageMetadata }
TicketFeedbackRequest:
  required: [isHelpful]
  isHelpful: boolean
  comment: string | null, maxLength 500
```

Web app component, exported by `frontend/src/features/feedback/index.ts`:

```ts
GiveFeedbackModal(props: { ticketId: string; isOpen: boolean; onClose: () => void })
```

### Required from other modules
- `fe-foundation` (done): routes `/search` and `/tickets` for Ticket Resolver and Project Manager,
  `paths.ticketDetail`, the session `departments`, `DataTable` (loading, error with Try again, empty MSG04,
  pagination, sort), `EmptyState`, `ErrorState`, `LoadingState`, `FormModal`, `StatusTag`, `TICKET_SOURCE_LABELS`,
  `useListSearchParams`, `PAGE_SIZE`, the MSG catalog (MSG01, MSG02, MSG04, MSG06, MSG26), the fixture users.
- `ticket-repository` (later): the tickets and Solutions that are listed and searched; 5.5 Ticket Detail mounts
  `GiveFeedbackModal`.

## Definition of Done
- [ ] Every acceptance criterion of STORY-001 to STORY-004 has a test whose name starts with its AC id;
      `sdd.ts trace search` passes
- [ ] The frontend `verify` passes on each feature's PR
- [ ] Each PR is reviewed by the FE Lead
- [ ] Minh (`ticket-repository`) is told how to mount `GiveFeedbackModal` on 5.5

<!-- ACCEPTANCE CRITERIA: one testable statement per item, id AC-<story>.<n> (never reuse a
     removed id), in EARS form: THE <system> SHALL <response>, optionally led by
     WHEN <trigger>, WHILE <state>, IF <unwanted condition> THEN, or WHERE <feature is included>.
     A story with a WHEN criterion also gets an IF … THEN criterion for its failure path.
     OUT OF SCOPE: name what is excluded, so it is not built by accident; adding it later is a
     spec change (Changelog row, re-approval).
     OPEN QUESTIONS: where the answer is missing, write [NEEDS CLARIFICATION: <question>] and ask
     the user — never fill the gap with a guess. `bun harnessclaude/hooks/sdd.ts check <module>`
     fails while any remain.
     SPEC EDIT RULE: Any edit to this file that changes business logic, requirements, stories, or
     acceptance criteria MUST add a new row to the Changelog table above (Date, Story if
     applicable, Change summary, Reason). Once the user has approved the spec, any edit locks the
     module's code until they approve the new version (`sdd.ts approve <module>`). -->
