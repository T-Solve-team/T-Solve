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
| 2026-11-05 | STORY-005..STORY-009 (new) | **Backend change.** New STORY-005 embedding pipeline (KAN-141), STORY-006 Ticket List API (KAN-147), STORY-007 search API with Ollama fallback and Search Log (KAN-144), STORY-008 Feedback API (KAN-148), STORY-009 similar-ticket suggestions (KAN-146). The backend items leave Out of Scope; Requirements 5–7, NFR 1 (Java test names), NFR 4 (contract drift found by the backend may change the feature's hooks and components), NFR 7–10, Interface Contracts (new `POST /api/v1/search/suggestions`, the search tables, the `VisibleScope` port) and Definition of Done updated. STORY-001..STORY-004 criteria unchanged; AC-001.3 and AC-002.7 may be reworded once the open questions on `project.name`, `reviewDueDate` and `resolvedAt` are answered | A task is done only with real backend and frontend (user, 05/11). User decisions of 05/11: bge-m3 query embedding ranked by cosine distance on pgvector, keyword fallback when Ollama fails or times out, a `search_log` row per search; one Feedback per user per ticket, a new one replaces the old; suggestions reuse the search ranking on the web session (Forge token out of scope); search adds its own changesets only (`ticket_embedding`, `search_log`, `ticket_feedback`, `public_id` UUID on `ticket`, `project`, `department`, `updated_at` on `ticket`); visibility through a fail-closed port until identity and project-integration provide membership. No table holds a Project name, a review due date or a resolved time, so those are open questions |
| 2026-11-05 | STORY-002, STORY-006, STORY-007 | Open questions answered: AC-006.6 `project.name` is the Project's `jira_project_key` (a stand-in until project-integration adds a name column; list, search and suggestions); AC-006.7 `reviewDueDate` is always null and its sort key falls back to the tie-breaker; AC-007.5 `resolvedAt` is the start of `resolved_date` in Asia/Ho_Chi_Minh, null without one, and becomes nullable in the contract (`TicketSearchResult`). New AC-002.15 (the card shows "Resolved —" for a null `resolvedAt`). AC-006.2 reworded and new AC-006.11, AC-006.12: a dev-only stand-in `DevAllVisibleScope`, used only when `SEARCH_DEV_SCOPE=all`; otherwise the fail-closed `NoVisibleScope`. Interface Contracts, Out of Scope and Required from other modules follow | User answers of 05/11 (Q1 A, Q2 A, Q3 A) and the user's request for a local demo path like ticket's `StubProjectAccess` |
| 2026-11-06 | STORY-005, STORY-006, STORY-007 | Builder check (CAUTION) fixes: AC-005.3 reworded (rollback by the count of search changesets; the `vector` extension stays; rollbacks that drop data are for development databases only); AC-005.7 reworded (batch `SEARCH_EMBED_BATCH`, default 4, one batch at a time, so embedding never holds more than one Ollama slot); new AC-005.9 (a ticket id that fails 3 times in a row is skipped until the backend restarts); AC-007.7 reworded (dates only as `yyyy-MM-dd`, BUG-106); `GET /api/v1/tickets` declares `400` in the contract (AC-006.9). Out of Scope: retention and access of `search_log`. No other criterion changes | Fresh builder check of 06/11; user decisions of 06/11: `SEARCH_MIN_SIMILARITY` 0.5 accepted as a tunable default; `search_log` privacy deferred to the dashboard (KAN-150) |
| 2026-11-06 | STORY-010 (new) | New STORY-010: the in-process `SolutionRetrieval` service the `chatbot` module calls for the Solutions nearest to a query vector, over every Published ticket of the Workspace with an ACTIVE Department (no Department scope, chatbot Requirement 2). Terms gain **Workspace Solution**; Interface Contracts gain the service; Definition of Done covers STORY-010. No other criterion changes | Plan of the chatbot backend (KAN-179): `backend/AGENTS.md` lets `chatbot` use search only through a `service/` interface, never its tables |
| 2026-11-07 | STORY-005 | New AC-005.10: `project.project_id` becomes a UUID primary key holding the former `public_id`, which is dropped; every `project_id` column and `department.default_project_id` hold that UUID (hex-form values from changeset 010 rewritten); AC-005.1 and Interface Contracts follow. The Department key stays BIGINT | KAN-291 (#147): one Project id type instead of BIGINT keys, `public_id` and hex UUIDs mixed, which needed a two-form join in `SearchTicket` and caused the 32 IT errors of #140 |

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
5. The backend (`vn.tsolve.search`) embeds every Published ticket with bge-m3 and keeps the vector in pgvector
   (KAN-141), and serves the Ticket List (KAN-147), the search with its Search Log (KAN-144) and the stored Feedback
   (KAN-148) behind the contract below.
6. The backend serves the five most similar Published tickets of the user's Departments for a Jira issue's summary
   and description (KAN-146, GBR-02), with the same ranking as the search.
7. AI is optional (GBR-12): when Ollama fails or times out, the search and the suggestions still answer from a
   keyword match, and a ticket left without a vector is embedded later.

### Non-Functional
1. Every acceptance criterion has a test that names its id: a frontend `it` name starts with it
   (`it('AC-001.1 …')`), a backend test method ends with it (`returnsOnlyVisibleTickets_AC_006_1`);
   `sdd.ts trace search` passes.
2. The PR passes the frontend `verify`: `format:check`, `lint`, `typecheck`, `test`, `build`, and `generate:api`
   leaves no diff.
3. No new npm dependency.
4. The screens depend only on the REST contract below: replacing the MSW mocks with the real backend needs no
   change outside the features' `mocks/` folders, except where the backend changes a contract field (recorded in
   the Changelog); then the feature's hooks and components follow the regenerated types.
5. Every id in the contract below is a UUID string (`type: string`, `format: uuid`), never a number.
6. Changes to `app/`, `shared/`, `mocks/` or `test/` (fe-foundation) are not part of this module; if one is
   needed, it is a quick change or a change request decided by the FE Lead.
7. Each backend PR passes `mvn verify` (unit tests, the Testcontainers `*IT` tests on `pgvector/pgvector:pg16`, and
   `mvn spotless:check`).
8. No new Maven dependency: vectors are written and compared with native SQL on pgvector.
9. Every schema change is a search-owned Liquibase changeset, one per commit, with a `--rollback`; no applied
   changeset is edited; no other module's Java file is changed.
10. No call to Ollama runs inside a database transaction, and no query text or ticket text is written to the
    application log (backend `AGENTS.md` §4, §9).

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
  - AC-002.15: IF a result's `resolvedAt` is null (the ticket has no resolved date), THEN THE web app SHALL show
    "Resolved —" in that card's line instead of a date and time.

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

### Terms of the backend stories

Used by STORY-005 to STORY-009:
- **Visible scope:** what the search-owned port `VisibleScope` answers for the signed-in user: the user's
  Departments, the Projects the user manages, and the `ticket.writer` value of the user's own tickets (Interface
  Contracts). Request filters only narrow it.
- **Searchable ticket:** a ticket whose status is `PUBLISHED`, whose Project's Department has status `ACTIVE`, and
  whose Department is one of the user's Departments (UC-26, GBR-02, D25, D26). An Unpublished ticket or a ticket of a
  deleted Department is never searchable.
- **Ranking:** STORY-007's way of ordering searchable tickets for a text (AC-007.1, AC-007.2), reused by STORY-009.
- **Workspace Solution:** the Solution of a ticket whose status is `PUBLISHED` and whose Project's Department has
  status `ACTIVE`, whatever Department the asking user belongs to (chatbot Requirement 2, GBR-36). Used by STORY-010.

### STORY-005: Embed Published tickets
- As a Ticket Resolver or Project Manager, I want every Published ticket to carry a meaning vector, so that search
  and suggestions find it by meaning (KAN-141).
- Acceptance:
  - AC-005.1: THE search changesets SHALL add to `ticket`, `project` and `department` a column `public_id UUID NOT
    NULL` with `DEFAULT gen_random_uuid()` and a unique constraint, giving every existing row its own value (the
    Project's column becomes its key under AC-005.10), and SHALL
    add to `ticket` a column `updated_at TIMESTAMPTZ NOT NULL` set to `created_at` for existing rows, to the insert
    time for new rows, and to the time of the change on every later `UPDATE` of the row (a database trigger).
  - AC-005.2: THE search changesets SHALL create the tables `ticket_embedding`, `search_log` and `ticket_feedback`
    with the columns and constraints of Interface Contracts, each following the tenant-table convention (singular
    name, `<table>_id UUID PRIMARY KEY DEFAULT gen_random_uuid()`, no `workspace_id`, `TIMESTAMPTZ NOT NULL`
    timestamps), and SHALL enable the `vector` extension when it is missing.
  - AC-005.3: THE search changesets SHALL each hold one logical change and a `--rollback` that undoes it, so that
    rolling back all search changesets (by their count) leaves the schema as it was before them, except the `vector`
    extension, which stays; the rollbacks of the changesets that drop data (`public_id`, `search_log`,
    `ticket_feedback`) are for development databases only.
  - AC-005.4: WHEN a `TicketsPublishedEvent` is committed, THE backend SHALL embed, after the commit and on a
    background thread, each listed ticket that is still `PUBLISHED`, from the text "title, Symptoms, Root cause,
    Resolution steps, Workaround" (the sections present, one per line), with the `ai` module's `EmbeddingClient`
    (bge-m3, 1024 dimensions), and SHALL store one `ticket_embedding` row per ticket, replacing an older row of
    that ticket.
  - AC-005.5: THE embedding SHALL never run inside a database transaction and SHALL never change the result or the
    response of the import that published the tickets.
  - AC-005.6: IF the `EmbeddingClient` fails or times out (`AiUnavailableException`), THEN THE backend SHALL write
    no `ticket_embedding` row for that batch, log the failure with the internal ticket ids only (no ticket text), and
    leave those tickets to the catch-up job (AC-005.7).
  - AC-005.7: WHILE a `PUBLISHED` ticket has no `ticket_embedding` row, THE catch-up job SHALL embed it, at most
    `SEARCH_EMBED_BATCH` tickets (default 4) per run, lowest internal id first, one run every
    `SEARCH_EMBED_CATCH_UP_DELAY` (default 60 s) after the previous run ends, so that tickets published before this
    change or while Ollama was down get a vector; THE listener and the job SHALL send at most one batch to Ollama at a
    time, in batches of at most `SEARCH_EMBED_BATCH` tickets.
  - AC-005.8: IF an id of the event names no ticket, or a ticket that is no longer `PUBLISHED`, THEN THE backend SHALL
    skip that id and still embed the others.
  - AC-005.9: IF the embedding of a ticket id fails 3 times in a row, THEN THE catch-up job SHALL skip that id until the
    backend restarts and SHALL log the skip with the id only; a success SHALL reset the count, and a later
    `TicketsPublishedEvent` for that id SHALL still embed it.
  - AC-005.10: THE search changesets SHALL make `project.project_id` a `UUID` primary key holding each Project's
    former `public_id` and drop `project.public_id`; every `project_id` column (`ticket`, `import_batch`,
    `import_preview`, `suggestion_result`) and `department.default_project_id` SHALL hold that UUID, with a value
    written as the hex form of the old BIGINT key (changeset `010-ticket-import-project-uuid`) rewritten to the
    Project's UUID, and the foreign keys to `project` kept; its rollback restores BIGINT keys and `public_id` and is
    for development databases only (AC-005.3). The Department key stays BIGINT.

### STORY-006: Serve the Ticket List from the backend
- As a Ticket Resolver or Project Manager, I want 6.2 Ticket List to show the real tickets I may see, so that I
  follow the knowledge of my Departments and Projects (KAN-147, UC-27).
- Acceptance:
  - AC-006.1: WHEN a user holding TICKET_RESOLVER or PROJECT_MANAGER calls `GET /api/v1/tickets`, THE backend SHALL
    return, as `TicketSummaryPage`, only the tickets whose Project's Department has status `ACTIVE` and that are
    either `PUBLISHED` in one of the user's Departments, or in another status and written by the user
    (`ticket.writer` equals the visible scope's writer) or in a Project the user manages (UC-27).
  - AC-006.2: THE backend SHALL take the user's Departments, managed Projects and writer only from the port
    `VisibleScope`; WHILE no module provides it and `SEARCH_DEV_SCOPE` is not `all`, THE stand-in `NoVisibleScope`
    SHALL answer no Department, no Project and no writer, so that every list, search and suggestion of STORY-006 to
    STORY-009 is empty and every Feedback answers 404.
  - AC-006.3: WHEN no page, size or sort is given, THE backend SHALL return page 0 of 20 rows sorted by `updatedAt`
    descending; THE sort keys `sourceTicketId`, `title`, `project`, `reviewDueDate` and `updatedAt` SHALL sort in the
    asked direction, with the internal ticket id descending as the last tie-breaker.
  - AC-006.4: WHEN `keyword`, `departmentId`, `source` or `status` is given, THE backend SHALL combine them with AND
    inside the AC-006.1 scope: the trimmed keyword SHALL match, case-insensitively and with `%` and `_` taken
    literally, a part of the Ticket ID, the title, or a Solution section (Symptoms, Root cause, Resolution steps,
    Workaround, Scope); a blank keyword SHALL be ignored; a `departmentId` that is not one of the user's Departments
    SHALL give an empty page.
  - AC-006.5: THE backend SHALL return each row's `ticketId`, `project.projectId` and `department.departmentId` as
    the `public_id` of the ticket, Project and Department; `sourceTicketId` as `ticket.ticket_id`; the Department's
    `name`; `source` and `status` as stored; and `updatedAt` as `ticket.updated_at`.
  - AC-006.6: THE backend SHALL return `project.name` as the Project's `jira_project_key` (e.g. ITSUP) in every
    `TicketProject` it answers (Ticket List, search, suggestions), and the `project` sort key SHALL sort by it; this
    is a stand-in until project-integration adds a Project name column.
  - AC-006.7: THE backend SHALL return `reviewDueDate` as null for every ticket until ticket-review stores a review
    due date; THE sort key `reviewDueDate` SHALL be accepted and order the rows by the tie-breaker of AC-006.3 alone.
  - AC-006.8: IF the caller has no session, THEN THE backend SHALL answer 401 with MSG07; IF the caller holds neither
    TICKET_RESOLVER nor PROJECT_MANAGER, THEN THE backend SHALL answer 403 with MSG08 and return no ticket.
  - AC-006.9: IF `keyword` is longer than 100 characters, THEN THE backend SHALL answer 400 with field error MSG02 on
    `keyword`; IF `departmentId` is not a UUID, `source` or `status` is not a contract value, `page` is negative,
    `size` is outside 1..100, or the sort key is not one of AC-006.3, THEN THE backend SHALL answer 400 with MSG06.
  - AC-006.10: THE endpoints of STORY-006 to STORY-009 SHALL accept no Workspace parameter, header or body field and
    SHALL answer for the Workspace of the request's host only (GBR-37); an unknown query parameter such as
    `workspaceId` SHALL change nothing in the answer.

  - AC-006.11: WHERE `SEARCH_DEV_SCOPE` is exactly `all` (local demo only), THE backend SHALL use the dev stand-in
    `DevAllVisibleScope` instead of `NoVisibleScope`: every user's Departments SHALL be every Department with status
    `ACTIVE`, the managed Projects every Project, and the writer none; THE backend SHALL log one warning at startup
    that the dev scope is on.
  - AC-006.12: IF `SEARCH_DEV_SCOPE` is unset, empty or any other value than `all`, THEN THE backend SHALL use
    `NoVisibleScope` (AC-006.2) and SHALL not create `DevAllVisibleScope`, so that the default fails closed.

### STORY-007: Search Published tickets on the backend
- As a Ticket Resolver or Project Manager, I want my query ranked by meaning over the Published tickets of my
  Departments, and still answered when the AI is down, so that I find an existing Solution (KAN-144, UC-26).
- Acceptance:
  - AC-007.1: WHEN a TICKET_RESOLVER or PROJECT_MANAGER sends `q`, THE backend SHALL embed the trimmed `q`
    (`GET /api/v1/search/tickets`) with the `EmbeddingClient` and return, as `TicketSearchResultPage`, only
    searchable tickets with a `ticket_embedding` row whose cosine similarity to the query (1 − cosine distance) is at
    least `SEARCH_MIN_SIMILARITY` (default 0.5), most similar first, the internal ticket id as the tie-breaker; page 0
    of 20 results when no page or size is given.
  - AC-007.2: IF the query embedding fails or times out (`AiUnavailableException`), THEN THE backend SHALL answer
    200 from a keyword match instead: searchable tickets whose title, Symptoms or Resolution steps contain the trimmed
    `q` (case-insensitive, `%` and `_` taken literally), newest `resolved_date` first with tickets without one last,
    the internal ticket id descending as the tie-breaker, with the same filters and paging.
  - AC-007.3: WHEN `departmentId`, `source`, `resolvedFrom` or `resolvedTo` is given, THE backend SHALL combine them
    with AND inside the searchable tickets; `resolvedFrom` and `resolvedTo` SHALL include both days and SHALL not
    match a ticket without a `resolved_date`; a `departmentId` that is not one of the user's Departments SHALL give an
    empty page.
  - AC-007.4: THE backend SHALL return each result's `ticketId`, `project` and `department` as in AC-006.5 and
    AC-006.6, the `title`, an `excerpt` of the Symptoms followed by the Resolution steps with white space collapsed to
    single spaces and cut to at most 200 characters, and `helpfulRate` as the rounded percent (half up) of the
    ticket's `ticket_feedback` rows rated `HELPFUL`, or null when the ticket has none.
  - AC-007.5: THE backend SHALL return `resolvedAt` as the start (00:00) of the ticket's `resolved_date` in
    Asia/Ho_Chi_Minh, as a date-time with its offset (e.g. `2026-10-01T00:00:00+07:00`), and as null when the ticket
    has no `resolved_date`.
  - AC-007.6: WHEN a search of page 0 is answered, THE backend SHALL write one `search_log` row with the user's id,
    the Department of `departmentId` (null when none was given), the trimmed `q`, channel `WEB`, `result_count` equal
    to the total number of matching results, `is_fallback` true when AC-007.2 answered, and the time; a request for
    another page SHALL write no row.
  - AC-007.7: IF `q` is missing or blank, THEN THE backend SHALL answer 400 with field error MSG01 on `q`; IF `q` is
    longer than 200 characters, THEN 400 with field error MSG02 on `q`; IF `resolvedFrom` is after `resolvedTo`, THEN
    400 with MSG39; IF `departmentId`, `source`, `page` or `size` is not valid (AC-006.9 rules), or a date is not
    `yyyy-MM-dd` with a four-digit year (BUG-106), THEN 400 with MSG06; in each case THE backend SHALL call no
    embedding and write no `search_log` row.
  - AC-007.8: IF the caller has no session, THEN THE backend SHALL answer 401 with MSG07; IF the caller holds neither
    TICKET_RESOLVER nor PROJECT_MANAGER, THEN THE backend SHALL answer 403 with MSG08, call no embedding and write no
    `search_log` row.
  - AC-007.9: THE backend SHALL send only the query text to Ollama for a search and SHALL write neither the query
    nor any ticket text to the application log.

### STORY-008: Store Feedback on the backend
- As a Ticket Resolver or Project Manager, I want my Helpful or Not helpful answer kept, so that the helpful rate of
  a Solution reflects what users found (KAN-148, UC-28).
- Acceptance:
  - AC-008.1: WHEN a TICKET_RESOLVER or PROJECT_MANAGER sends Feedback, THE backend SHALL store it: on
    `PUT /api/v1/tickets/{ticketId}/feedback` with a valid body for a searchable ticket (by its `public_id`), the
    user's Feedback with rating `HELPFUL` when `isHelpful` is true and `NOT_HELPFUL` when it is false, the trimmed
    `comment` (null when it is null, empty or white space) and channel `WEB`, and SHALL answer 204 with no body.
  - AC-008.2: WHEN the user already has Feedback on that ticket, THE backend SHALL replace its rating, comment,
    channel and `updated_at`, so that a user has at most one `ticket_feedback` row per ticket, also when two requests
    of the same user arrive at the same time.
  - AC-008.3: IF `ticketId` is not a searchable ticket's `public_id`, THEN THE backend SHALL answer 404 with MSG04
    and store nothing (unknown id, not `PUBLISHED`, a Department that is not `ACTIVE`, or not one of the user's
    Departments).
  - AC-008.4: IF `isHelpful` is missing or null, THEN THE backend SHALL answer 400 with field error MSG01 on
    `isHelpful`; IF `comment` is longer than 500 characters, THEN 400 with field error MSG02 on `comment`; IF
    `ticketId` is not a UUID, THEN 400 with MSG06; in each case nothing is stored.
  - AC-008.5: IF the caller has no session, THEN THE backend SHALL answer 401 with MSG07; IF the caller holds neither
    TICKET_RESOLVER nor PROJECT_MANAGER, THEN THE backend SHALL answer 403 with MSG08 and store nothing.

### STORY-009: Suggest similar tickets for a Jira issue
- As a Ticket Resolver, I want the five Published tickets most like an issue's summary and description, so that the
  Issue Panel can show me a known Solution (KAN-146, UC-26, GBR-02).
- Acceptance:
  - AC-009.1: WHEN a TICKET_RESOLVER or PROJECT_MANAGER asks for suggestions, THE backend SHALL rank
    (`POST /api/v1/search/suggestions`, valid body) the searchable tickets with AC-007.1 for the text "trimmed
    summary, a blank line, trimmed description" (the summary alone when the description is null or blank) and return
    at most 5 `TicketSearchResult` items with the fields of AC-007.4 and AC-007.5, most similar first.
  - AC-009.2: IF the embedding fails or times out, THEN THE backend SHALL answer 200 from the keyword match of
    AC-007.2 on the trimmed summary, at most 5 items.
  - AC-009.3: WHEN suggestions are answered, THE backend SHALL write one `search_log` row with the user's id, no
    Department, the trimmed summary as the query, channel `ISSUE_PANEL`, `result_count` equal to the number of items
    returned, and `is_fallback` true when AC-009.2 answered.
  - AC-009.4: IF `summary` is missing or blank, THEN THE backend SHALL answer 400 with field error MSG01 on
    `summary`; IF `summary` is longer than 255 characters or `description` longer than 32767 characters (the Jira
    field limits), THEN 400 with field error MSG02 on that field; in each case THE backend SHALL call no embedding and
    write no `search_log` row.
  - AC-009.5: IF the request has no web session, THEN THE backend SHALL answer 401 with MSG07 (a Forge Invocation
    Token alone does not count); IF the caller holds neither TICKET_RESOLVER nor PROJECT_MANAGER, THEN 403 with MSG08.

### STORY-010: Retrieve Solutions for the Chatbot
- As the `chatbot` module, I want the Workspace Solutions nearest in meaning to a question, so that the Knowledge
  Chatbot answers only from Published knowledge (KAN-179, chatbot STORY-005).
- Acceptance:
  - AC-010.1: WHEN `SolutionRetrieval.nearest(queryVector, minSimilarity, limit)` is called, THE backend SHALL return
    at most `limit` Workspace Solutions that have a `ticket_embedding` row whose cosine similarity to `queryVector`
    is at least `minSimilarity`, most similar first, the internal ticket id as the tie-breaker.
  - AC-010.2: THE backend SHALL return each hit with the ticket's Symptoms, Root cause, Resolution steps, Workaround
    (null when blank) and Scope of applicability, and its similarity; it SHALL return no ticket id, title, Project,
    Department, writer, snapshot or Evidence (GBR-36).
  - AC-010.3: IF a ticket is unpublished, inactive or too far, THEN THE backend SHALL leave it out (not
    `PUBLISHED`, its Department not `ACTIVE`, or its similarity below `minSimilarity`) and SHALL return an empty
    list when none is left.
  - AC-010.5: IF an argument is out of range, THEN THE service SHALL throw `IllegalArgumentException` and SHALL run
    no query (`queryVector` without 1024 dimensions, `minSimilarity` outside 0 to 1, or `limit` below 1).
  - AC-010.4: THE service SHALL take no user, Workspace or Department argument and SHALL call no `EmbeddingClient`
    (the caller embeds the question), write no `search_log` row and log no Solution text.

## Out of Scope
- The membership behind `VisibleScope` (which Departments a user belongs to, which Projects they manage, which
  `ticket.writer` value is theirs): `identity` and `project-integration` implement the port; until then the
  fail-closed stand-in answers nothing (AC-006.2); the dev stand-in of AC-006.11 is for a local demo only and is never
  set in a deployed environment.
- Privacy of `search_log` (user, 06/11): no retention period yet; it is decided with the dashboard (KAN-150) by
  Hải Anh with the BA. Until then only Admin may read the table (no API of this module returns it), and the
  application log never holds the query (AC-007.9).
- A dev-only signed-in user for the local demo: no such path exists in the backend today; the web session principal
  `TSolveUser` comes with `identity` (sign-in on Keycloak).
- The Forge Invocation Token for `POST /api/v1/search/suggestions`, the Issue Panel UI and its call from Jira
  (`jira-app`, `platform`); the endpoint uses the web session for now (AC-009.5).
- Removing a ticket's vector when it is Unpublished or its Department is deleted: no such event exists today; the
  queries filter on `PUBLISHED` and an `ACTIVE` Department instead (searchable ticket). A later event may delete it.
- An approximate-nearest-neighbour index (HNSW, IVFFlat) on `ticket_embedding`; the search compares every candidate
  vector exactly until the volume needs one.
- Binding the background embedding to a Workspace (`TenantContext`, JobRunr jobs of `worker`): today the backend
  has one tenant database; the binding comes with `platform` STORY-002 and `worker`.
- The row policy of the tenant row guard (platform AC-001.29) on the three search tables, unless the guard is on
  `main` when the changesets are written; then they follow it.
- Retrieval of Wiki documents and the shared retrieval interface for the Chatbot and triage (KAN-38).
- Converting or dropping the legacy table `reuse_record` (platform AC-001.21).
- The "Give feedback" button on 5.5 Ticket Detail and that screen (`ticket-repository`); it mounts
  `GiveFeedbackModal` (AC-003.11).
- Showing the user's previous Feedback in the popup; a new Feedback replaces it on the backend (UC-28).
- A search before a query or a "latest tickets" list on 6.1 (decision Q3).
- Row actions, row selection, export and polling of the Ticket List; a Project filter (Report3 §III 6.2).
- Changing the ids of `DepartmentRef`, `ProjectRef` or the session (`fe-foundation` Task 10, `ticket-review`).

## Interface Contracts

### Exposed by this module
REST, in `frontend/openapi/tsolve-api.yaml`, served by the backend `vn.tsolve.search` (STORY-006 to STORY-009).
Every id is a UUID string: the `public_id` of the ticket or Department and the `project_id` of the Project (AC-005.10),
never an internal BIGINT key.
401 answers MSG07, 403 MSG08, 404 MSG04; a field error carries its MSG code (MSG01, MSG02) on the field.

```yaml
GET /api/v1/tickets?keyword=&departmentId=&source=&status=&page=&size=&sort=   # tag tickets, operationId getTickets
  keyword:      string, optional, maxLength 100   # Ticket ID, title or Solution text
  departmentId: string (uuid), optional           # omitted = every Department the user may see
  source:       TicketSource, optional
  status:       TicketStatus, optional
  page, size, sort: the shared Page, Size, Sort parameters; default sort updatedAt,desc
  200:      TicketSummaryPage
  400:      ProblemDetail (field error MSG02 on keyword; MSG06 for a bad value, paging or sort)   # AC-006.9
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

POST /api/v1/search/suggestions                  # tag search, operationId suggestTickets (KAN-146)
  body:     TicketSuggestionRequest
  200:      TicketSearchResult[]                 # at most 5, most similar first; empty array when none
  400:      ProblemDetail (field errors: MSG01 on summary, MSG02 on summary or description)
  401, 403: ProblemDetail
TicketSuggestionRequest:
  required: [summary]
  summary:     string, minLength 1, maxLength 255     # the Jira issue summary
  description: string | null, maxLength 32767        # the Jira issue description as plain text

TicketStatus: PENDING_REVIEW | CHANGES_REQUESTED | PUBLISHED | REJECTED | UNPUBLISHED | EXPIRED
TicketProject:    { required: [projectId, name],    projectId: uuid,    name: string }   # name = jira_project_key for now (AC-006.6)
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
  resolvedAt:  date-time | null          # start of resolved_date in Asia/Ho_Chi_Minh; null = no resolved date
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

Search tables (tenant changelog, search-owned changesets; AC-005.1, AC-005.2):

```sql
ticket      + public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE, + updated_at TIMESTAMPTZ NOT NULL (trigger)
project     + public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE
            then (AC-005.10) project_id UUID PRIMARY KEY = former public_id; public_id dropped;
            project_id UUID in ticket, import_batch, import_preview, suggestion_result; department.default_project_id UUID
department  + public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE

ticket_embedding
  ticket_embedding_id UUID PK DEFAULT gen_random_uuid()
  ticket_id           BIGINT NOT NULL UNIQUE  FK ticket(id) ON DELETE CASCADE
  embedding           vector(1024) NOT NULL
  model               VARCHAR(50) NOT NULL    -- e.g. bge-m3
  created_at, updated_at TIMESTAMPTZ NOT NULL DEFAULT now()

search_log                                     -- append-only (baseline T-Solve_Database_Tables §search_log)
  search_log_id  UUID PK DEFAULT gen_random_uuid()
  user_id        UUID NOT NULL                 FK app_user(id)
  department_id  BIGINT NULL                   FK department(department_id)   -- the departmentId filter
  query          TEXT NOT NULL
  channel        VARCHAR(20) NOT NULL CHECK IN ('WEB', 'ISSUE_PANEL', 'CHATBOT')
  result_count   INTEGER NOT NULL CHECK >= 0
  is_fallback    BOOLEAN NOT NULL              -- the keyword fallback answered (AC-007.2)
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()

ticket_feedback                                -- one row per (ticket, user); a new Feedback replaces it
  ticket_feedback_id UUID PK DEFAULT gen_random_uuid()
  ticket_id      BIGINT NOT NULL               FK ticket(id)
  user_id        UUID NOT NULL                 FK app_user(id)
  rating         VARCHAR(20) NOT NULL CHECK IN ('HELPFUL', 'NOT_HELPFUL')
  comment        TEXT NULL CHECK char_length <= 500
  channel        VARCHAR(20) NOT NULL CHECK IN ('WEB', 'ISSUE_PANEL')
  created_at, updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
  UNIQUE (ticket_id, user_id)
```

Search-owned port, implemented later by `identity` / `project-integration` (AC-006.2):

```java
package vn.tsolve.search.scope.service;
public interface VisibleScope {
    Scope of(TSolveUser user);
    /** Internal ids; writer is the ticket.writer value of the user's own tickets, empty when unknown. */
    record Scope(Set<Long> departmentIds, Set<Long> managedProjectIds, Optional<String> writer) {}
}
// vn.tsolve.search.scope.service.impl.NoVisibleScope: answers empty sets and no writer (fail closed; the default)
// vn.tsolve.search.scope.service.impl.DevAllVisibleScope: only when SEARCH_DEV_SCOPE=all (local demo; AC-006.11)
```

In-process service for the `chatbot` module (STORY-010):

```java
package vn.tsolve.search.retrieval.service;
public interface SolutionRetrieval {
    /** Workspace Solutions nearest to queryVector (bge-m3, 1024 dims), most similar first; AC-010.1 to AC-010.5. */
    List<RetrievedSolution> nearest(float[] queryVector, double minSimilarity, int limit);
}
// vn.tsolve.search.retrieval.dto
public record RetrievedSolution(
        String symptoms, String rootCause, String resolutionSteps, String workaround, String scopeOfApplicability,
        double similarity) {} // workaround null when blank
```

### Required from other modules
- `fe-foundation` (done): routes `/search` and `/tickets` for Ticket Resolver and Project Manager,
  `paths.ticketDetail`, the session `departments`, `DataTable` (loading, error with Try again, empty MSG04,
  pagination, sort), `EmptyState`, `ErrorState`, `LoadingState`, `FormModal`, `StatusTag`, `TICKET_SOURCE_LABELS`,
  `useListSearchParams`, `PAGE_SIZE`, the MSG catalog (MSG01, MSG02, MSG04, MSG06, MSG26), the fixture users.
- `ticket-repository` (later): the tickets and Solutions that are listed and searched; 5.5 Ticket Detail mounts
  `GiveFeedbackModal`; its `GET /api/v1/tickets/{ticketId}` takes the same `ticket.public_id` as these APIs.
- `ticket` (done): the `ticket` table and `vn.tsolve.ticket.event.TicketsPublishedEvent` (internal ticket ids),
  published inside the import transaction.
- `ai` (done): `vn.tsolve.ai.client.EmbeddingClient` (bge-m3, 1024 dimensions) and `AiUnavailableException` on a
  failure or timeout (`AI_EMBED_TIMEOUT`).
- `identity` / `project-integration` (later): an implementation of `VisibleScope`; the session principal
  `TSolveUser` (its `id` is `app_user.id`).
- `project-integration` / `workspace`: the `project` and `department` tables of changeset `002` (search adds only
  `public_id`); the session `departments` of the web app carry `department.public_id` as `departmentId`.
- `ticket-review` (later): the review due date; until then `reviewDueDate` is null (AC-006.7).
- `project-integration` (later): a Project name column; until then `project.name` is `jira_project_key` (AC-006.6),
  changed in one mapping line (the `project_name` expression).
- `platform`: `TenantContext` and the tenant row guard; the search tables follow them when they reach `main`.

## Definition of Done
- [ ] Every acceptance criterion of STORY-001 to STORY-010 has a test that names its AC id;
      `sdd.ts trace search` passes
- [ ] The frontend `verify` passes on each frontend PR; `mvn verify` passes on each backend PR
- [ ] Each screen runs against the real backend (mock mode off) with the generated client pulled from it
- [ ] The search changesets apply on an empty database and on a copy of `main`'s schema, and roll back
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
