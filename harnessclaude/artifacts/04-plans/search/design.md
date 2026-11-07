---
module: search
date: 2026-10-25
---

# Design: search

Spec: `artifacts/03-modules/search/spec.md` · Module: `artifacts/03-modules/search/module.md` ·
Rules: `frontend/AGENTS.md` and `frontend/guidelines/`, `backend/AGENTS.md` (they win over anything here that
contradicts them).

This file has two parts: the web app (25/10, below) and the **backend** (change of 05/11, section "Backend").

## Architecture Overview

Web app: built first on MSW from the contract added to `frontend/openapi/tsolve-api.yaml`; the backend of section
"Backend" now serves the same contract, and MSW stays for tests and the dev mock mode.
The three screens live in the feature folders the route map already gives them (guideline 01 §5); no file of
`app/`, `shared/`, `mocks/` or `test/` changes.

| Screen | Feature folder | Route (already registered) |
|--------|----------------|----------------------------|
| 6.2 Ticket List | `features/tickets/` (Ticket List files only; 5.5 stays `ticket-repository`'s) | `/tickets` |
| 6.1 Search | `features/search/` | `/search` |
| 8.1 Give Feedback (popup) | `features/feedback/` | none (popup); exported for 5.5 |

```
TicketListPage ── useTicketList ──► useListSearchParams (URL: keyword, departmentId, source, status, page, sort)
   ├ TicketListFilters (Keyword on Enter, Department from session, Source, Status)
   └ TicketListTable (DataTable: loading · error + Try again · MSG04 · sort · 20 per page)
          │ generated useGetTickets ──► GET /api/v1/tickets ── tickets/mocks/handlers.ts

SearchPage ── useTicketSearch ──► useListSearchParams (URL: q, departmentId, source, resolvedFrom, resolvedTo, page)
   ├ SearchFilters (Search box on Enter, Department, Source, Resolved date range)
   └ SearchResultList (LoadingState · ErrorState + Try again · EmptyState MSG04 · cards · Pagination)
          │ generated useSearchTickets, enabled only when q is not blank
          ▼ GET /api/v1/search/tickets ── search/mocks/handlers.ts

GiveFeedbackModal({ ticketId, isOpen, onClose })  ← mounted later by 5.5 Ticket Detail (tickets → feedback, allowed pair)
   FormModal (Helpful? buttons, Comment, Cancel, Send)
          │ useSendFeedback (generated mutation, handlesOwnErrors)
          ▼ PUT /api/v1/tickets/{ticketId}/feedback ── feedback/mocks/handlers.ts
```

- **URL state:** both lists use `useListSearchParams`. The Ticket List's default sort is `updatedAt,desc`. Search
  has no sort: its hook passes a fixed default and sends only `page` and `size` (ranked by relevance). Changing a
  filter goes back to page 1 (the hook already does this).
- **Enter to search:** the Keyword and Search boxes are uncontrolled `Input.Search`-like inputs whose value starts
  from the URL; only Enter writes the trimmed value to the URL (empty → parameter removed). `maxLength` is 100
  (Ticket List) and 200 (Search) on the input itself, so a longer text cannot be typed or pasted.
- **No search before a query (Q3):** `useTicketSearch` passes `query: { enabled: q !== null && q.trim() !== '' }`;
  with no query the page renders the filters and nothing in the results area. Filters still go to the URL.
- **Department filter:** options are the session's `departments`; the value is `String(department.departmentId)`
  (works for today's numeric ids and the UUIDs of fe-foundation Task 10, Q2).
- **Feedback popup:** built on `FormModal` (reopens empty, Send spinner, no double submit). Helpful? is a
  `Form.Item` named `isHelpful` holding `true` / `false` / `undefined`, rendered as two buttons (Helpful with a
  thumbs-up icon, Not helpful with a thumbs-down icon) with `aria-pressed`; `rules.required('Helpful?')` gives
  MSG01. Comment uses `rules.maxLength('Comment', 500)` (MSG02) and `count={{ show: true, max: 500 }}` without a
  hard limit, so MSG02 can show (AC-003.4). On error: `applyApiErrors(form, error)` for field errors, otherwise
  `showMessage(error.code ?? 'MSG06', error.params)`; on success `onClose()` then `showMessage('MSG26')`.
- **Text safety:** every API text is rendered as React text; the excerpt uses CSS line clamping, never
  `dangerouslySetInnerHTML` (AC-001.14, AC-002.14).

## Data Model

No database in this phase. Types come from the generated client (`@/shared/api`), from the contract in the spec:
`TicketSummary`, `TicketSummaryPage`, `TicketSearchResult`, `TicketSearchResultPage`, `TicketProject`,
`TicketDepartment`, `TicketStatus`, `TicketFeedbackRequest`, and the existing `TicketSource`, `PageMetadata`,
`Page`, `Size`, `Sort`. Every id is `string` (`format: uuid`).

Mock data (each feature owns its own; features may not import each other's mocks, guideline 01 §4):

| File | Holds |
|------|-------|
| `tickets/mocks/ticket-data.ts` | 28 `TicketSummary` records plus the Solution text used by the keyword filter; every status and source; Departments IT and HR, Projects ITSUP, ITNET, HRHELP |
| `search/mocks/search-data.ts` | 14 Published `TicketSearchResult` records plus their Solution text; two with `helpfulRate: null` |
| `feedback/mocks/handlers.ts` | the Published ticket ids it accepts (the Published ids of `search-data.ts`, copied as constants) and the stored Feedback per ticket, reset by `resetMockData()` |

The Department and Project ids of the mocks are the fixture users' UUID literals (IT
`00000000-0000-4000-8000-0000000000d1`, HR `…0000000000d2`; Projects `…000000000101`, `…000000000102`,
`…000000000201`; fe-foundation Task 10 is done), declared once per mock file with a comment pointing to `src/mocks/session.ts`, because feature
code may not import `src/mocks` (eslint boundary zone). When fe-foundation Task 10 changes the fixture ids to
UUID literals, these constants are updated to the same literals (a two-line change per mock file).

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Where the popup is opened (Q1) | `GiveFeedbackModal` exported by `features/feedback/index.ts`; 5.5 mounts it | A temporary "Give feedback" button on the 5.5 placeholder | No edit in `ticket-repository`'s files; `tickets → feedback` is an allowed import pair (guideline 01 §5) |
| Ids (Q2) | New schemas with UUID string ids; Department filter sends `String(id)` from the session; no wait for fe-foundation Task 10 | Reuse `DepartmentRef` / `ProjectRef` (still `int64`, owned by fe-foundation / ticket-review); wait for Task 10 | Builds now; `String(uuid)` is the uuid, so the screens need no change when Task 10 lands |
| Search before a query (Q3) | No API call, empty results area, filters kept in the URL | Show the latest Published tickets | Matches UC-26 (rank a query), Figma has no such state, no empty searches in the Search Log |
| No Feedback yet (Q4) | `helpfulRate: null` hides the helpful rate | "No feedback yet"; "0% helpful" | No copy outside Figma and the MSG catalog; 0% would mislead |
| Ticket List location | `features/tickets/` (route `/tickets` is there); the Ticket List files are listed in `module.md` `paths` | Move 6.2 to `features/search/` | The route map and guideline 01 §5 put 6.2 in `tickets`; moving it would change fe-foundation files |
| Boolean names | `isHelpful` in the contract and the form, `isOpen` on the popup | `helpful`, `open` | Guideline 03 (booleans start with `is`; glossary row "Feedback: `isHelpful`") and backend `AGENTS.md` (boolean `is` prefix); `FormModal` already uses `isOpen` (builder check) |
| Search result layout | Cards (`SectionCard`-like `div`s in the feature) with an antd `Pagination` | `DataTable` | Figma 6.1 shows cards, not a table; antd `Pagination` is not a banned raw import |
| Resolved date filter | antd `DatePicker.RangePicker`, values sent as `YYYY-MM-DD` with `formatDate`-style helpers in the feature | A custom pair of date inputs | antd is the kit's base; no new dependency |
| Feedback response | `204` with no body; the popup does not refetch anything | Return the stored Feedback | Nothing on these screens shows a user's Feedback; the dashboard reads it later |
| Error toast in the popup | `meta: { handlesOwnErrors: true }`, field errors via `applyApiErrors`, others `showMessage(code)` | Global mutation error handler | Same pattern as `features/review` (RequestChangesModal); keeps the popup open with the input (AC-003.8, AC-003.9) |

Decided in planning (builder check):
- Search result cards show "Resolved" with `formatDateTime` (the app time zone) like every other date-time.
- Neither list keeps the previous page's data while a new query, filter, sort or page loads (no
  `placeholderData`): the loading state of AC-001.11 / AC-002.11 shows for every new request.
- The Helpful? rule must accept `false` (Not helpful) as a value: a test proves that Not helpful passes and only
  "nothing chosen" gives MSG01.

## File Structure

```
frontend/
├── openapi/tsolve-api.yaml                         # + the three endpoints and their schemas (spec contract)
├── src/shared/api/generated/**                     # regenerated (npm run generate:api), never hand-edited
└── src/features/
    ├── tickets/
    │   ├── pages/TicketListPage.tsx                # replaces the placeholder (6.2)
    │   ├── pages/TicketListPage.module.css
    │   ├── pages/TicketListPage.test.tsx           # AC-001.*
    │   ├── components/TicketListFilters.tsx
    │   ├── components/TicketListFilters.module.css
    │   ├── components/TicketListTable.tsx
    │   ├── hooks/useTicketList.ts
    │   └── mocks/{handlers.ts, ticket-data.ts, handlers.test.ts}   # AC-004.1, AC-004.2
    ├── search/
    │   ├── pages/SearchPage.tsx                    # replaces the placeholder (6.1)
    │   ├── pages/SearchPage.module.css
    │   ├── pages/SearchPage.test.tsx               # AC-002.*
    │   ├── components/SearchFilters.tsx
    │   ├── components/SearchResultList.tsx
    │   ├── components/SearchResultCard.tsx
    │   ├── components/SearchResults.module.css
    │   ├── hooks/useTicketSearch.ts
    │   └── mocks/{handlers.ts, search-data.ts, handlers.test.ts}   # AC-004.3
    └── feedback/
        ├── index.ts                                # + export { GiveFeedbackModal }
        ├── components/GiveFeedbackModal.tsx        # 8.1
        ├── components/GiveFeedbackModal.module.css
        ├── components/GiveFeedbackModal.test.tsx   # AC-003.*
        ├── hooks/useSendFeedback.ts
        └── mocks/{handlers.ts, handlers.test.ts}   # AC-004.4
```

`src/features/search/**` and `src/features/feedback/**` are already in `module.md` `paths`; the Ticket List
files of `features/tickets/` were added to it on 25/10. `features/*/routes.ts` do not change (routes and roles are
already right). `features/feedback/index.ts` gains one export.

## External Dependencies
- None new: React, antd, TanStack Query, orval, MSW, Vitest and React Testing Library are already installed.

## Edge Cases & Risks

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-001 | Input | Keyword of only spaces, or longer than 100 characters pasted | Medium | Spaces remove the keyword; the input stops at 100 characters | Task 1 (AC-001.4, AC-001.5) |
| 2 | STORY-001 | State | Shared link with `page=99` beyond the last page | Low | The API answers an empty page → MSG04 with the filters still shown | Task 1 (AC-001.10, AC-001.12) |
| 3 | STORY-001 | Data | A ticket's title contains `<script>` or HTML | High | Shown as literal text | Task 1 (AC-001.14) |
| 4 | STORY-001 | Permissions | A user with no Department (Ticket Resolver of no Project) | Medium | Department filter shows only "All Departments"; the list is whatever the API returns | Task 1 (AC-001.6) |
| 5 | STORY-001 | Errors | 500 on the first load, then Try again succeeds | High | Error state, then the table | Task 1 (AC-001.13) |
| 6 | STORY-002 | Input | Enter on an empty box; query of 200+ characters pasted | Medium | Nothing happens; the input stops at 200 characters | Task 2 (AC-002.3, AC-002.4) |
| 7 | STORY-002 | State | Opening `/search` with filters but no `q` in the URL | Medium | No API call, filters show their values | Task 2 (AC-002.2, AC-002.6) |
| 8 | STORY-002 | Data | `helpfulRate` 0 vs `null` | High | 0 shows "0% helpful"; null shows nothing | Task 2 (AC-002.7, AC-002.8) |
| 9 | STORY-002 | Input | Resolved date range with only a start or only an end | Medium | RangePicker sets both or none; clearing removes both parameters | Task 2 (AC-002.5) |
| 10 | STORY-002 | Data | Excerpt or title with HTML | High | Literal text, clamped | Task 2 (AC-002.14) |
| 11 | STORY-002 | Errors | Search fails, Try again re-runs the same query | High | Same `q`, filters and page | Task 2 (AC-002.13) |
| 12 | STORY-003 | Input | Comment of only spaces | Medium | Sent as `comment: null` | Task 3 (AC-003.5) |
| 13 | STORY-003 | Concurrency | Double click on Send | High | One request (FormModal `isSubmitting`) | Task 3 (AC-003.7) |
| 14 | STORY-003 | Errors | 404 (ticket Unpublished meanwhile) or 403 | Medium | Popup stays open with the input; message of the error code, else MSG06 | Task 3 (AC-003.9) |
| 15 | STORY-003 | State | Cancel after choosing Helpful, then reopen | Medium | Reopens empty | Task 3 (AC-003.10) |
| 16 | STORY-004 | Contract | Mock data drifts from the generated types | Medium | Mock data is typed with the generated types; `typecheck` fails on drift | Tasks 1–3 |
| 17 | — | Risk | fe-foundation Task 10 changes the fixture Department ids | Medium | Update the Department and Project constants of the three mock files to the new literals in the same PR as Task 10, or right after it | — (coordination with Hiếu) |
| 18 | — | Risk | `openapi/tsolve-api.yaml` changed by another PR at the same time (review, chatbot, fe-foundation Task 10) | Medium | Resolve conflicts in the YAML only, then `npm run generate:api` (guideline 02 §4) | — |

---

## Backend (change of 2026-11-05)

Spec: STORY-005 to STORY-009 (KAN-141, KAN-147, KAN-144, KAN-148, KAN-146). Rules: `backend/AGENTS.md`;
system decisions: `artifacts/03-modules/architecture.md`. Precedent for ports with fail-closed stand-ins:
`vn.tsolve.triage` (`ManagedProjects` / `NoManagedProjects`) and `vn.tsolve.ticket` (`ProjectAccess`).

### Architecture

```
ticket module ── ImportConfirmServiceImpl ── publishEvent(TicketsPublishedEvent(ids)) inside the import tx
                                                 │ AFTER_COMMIT
search.embedding ── TicketsPublishedListener ── applicationTaskExecutor ──► TicketEmbeddingService.embed(ids)
                    EmbeddingCatchUpJob (@Scheduled) ────────────────────►   1 read texts (read-only tx)
                                                                             2 EmbeddingClient.embed (no tx)
                                                                             3 upsert ticket_embedding (tx)
GET  /api/v1/tickets               search.list       ── VisibleScope ── TicketListRepository (Specification on SearchTicket)
GET  /api/v1/search/tickets        search.query      ── VisibleScope ── TicketRankingService ── EmbeddingClient
                                                                           ├ semantic: native pgvector SQL
                                                                           └ fallback: native keyword SQL
                                                     ── SearchLogRepository (one row per page-0 search)
PUT  /api/v1/tickets/{id}/feedback search.feedback   ── VisibleScope ── SearchTicket lookup ── native upsert
POST /api/v1/search/suggestions    search.suggestion ── TicketRankingService (top 5) ── SearchLogRepository
```

The Workspace is never read from the request (GBR-37, AC-006.10): every query runs on the database the request is
routed to; today there is one tenant database, `TenantContext` comes with `platform` STORY-002. The scope (which
Departments, Projects, writer) comes only from `VisibleScope` for the signed-in `TSolveUser`; filters narrow it.

### Packages and classes

One subpackage per feature so the parallel branches touch disjoint files (user, 05/11); inside each, the layers of
`backend/AGENTS.md` §1 (precedent: `identity.signin`, `identity.account`). Only the layers a feature needs exist.

```
backend/src/main/java/vn/tsolve/search/
├── scope/                                   branch 1 (shared)
│   ├── service/VisibleScope.java            port: Scope of(TSolveUser) — departmentIds, managedProjectIds, writer
│   ├── service/impl/NoVisibleScope.java     @Component stand-in: empty sets, no writer (fail closed)
│   ├── entity/SearchTicket.java             @Immutable @Subselect read model: ticket ⋈ project ⋈ department
│   └── dto/TicketProjectRef.java, dto/TicketDepartmentRef.java   (projectId/departmentId UUID, name)
├── config/SearchSchedulingConfig.java     branch 1: its own @EnableScheduling for the catch-up job
├── embedding/                               branch 1
│   ├── entity/TicketEmbedding.java          id, ticketId, model, createdAt, updatedAt (vector not mapped)
│   ├── repository/TicketEmbeddingRepository.java   native: upsert, findPublishedWithoutEmbedding(limit, skipped)
│   ├── repository/EmbeddingTicketRepository.java   JpaRepository<SearchTicket, Long>: findByIdInAndStatus (texts)
│   ├── repository/PgVector.java             float[] → '[0.1,…]' literal for CAST(:v AS vector)
│   ├── service/TicketEmbeddingService.java  embed(List<Long> ticketIds)
│   └── service/impl/{TicketEmbeddingServiceImpl, EmbeddingText, TicketsPublishedListener, EmbeddingCatchUpJob}.java
├── list/                                    branch 2 (KAN-147)
│   ├── controller/TicketListController.java GET /api/v1/tickets (getTickets)
│   ├── dto/{TicketListQuery, TicketSummaryResponse}.java
│   ├── exception/InvalidListQueryException.java   400 MSG06
│   ├── repository/TicketListRepository.java JpaRepository<SearchTicket, Long> + JpaSpecificationExecutor
│   ├── mapper/TicketSummaryMapper.java
│   └── service/TicketListService.java, service/impl/{TicketListServiceImpl, TicketListSpecs}.java
├── query/                                   branch 3 (KAN-144)
│   ├── controller/TicketSearchController.java GET /api/v1/search/tickets (searchTickets)
│   ├── dto/{TicketSearchQuery, TicketSearchResultResponse, RankFilter, RankedPage}.java
│   ├── exception/InvalidSearchQueryException.java   400 MSG06 (bad filter, date or paging)
│   ├── entity/SearchLog.java, repository/SearchLogRepository.java   (native insert, Department by public_id)
│   ├── repository/TicketRankingRepository.java   native semantic + keyword queries, interface projection RankedRow
│   ├── mapper/TicketSearchResultMapper.java (excerpt via Excerpts)
│   └── service/{TicketRankingService, TicketSearchService}.java, service/impl/{…Impl, Excerpts}.java
├── feedback/                                branch 4 (KAN-148)
│   ├── controller/TicketFeedbackController.java PUT /api/v1/tickets/{ticketId}/feedback (giveFeedback)
│   ├── dto/TicketFeedbackRequest.java
│   ├── entity/TicketFeedback.java, repository/TicketFeedbackRepository.java (native upsert)
│   ├── repository/FeedbackTicketRepository.java  JpaRepository<SearchTicket, Long>: findByPublicId
│   └── service/TicketFeedbackService.java, service/impl/TicketFeedbackServiceImpl.java
└── suggestion/                              branch 5 (KAN-146)
    ├── controller/TicketSuggestionController.java POST /api/v1/search/suggestions (suggestTickets)
    ├── dto/TicketSuggestionRequest.java
    └── service/TicketSuggestionService.java, service/impl/TicketSuggestionServiceImpl.java
backend/src/test/java/vn/tsolve/search/…    mirrors main; *IT classes on Testcontainers pgvector/pgvector:pg16
```

Branches 2 (`list`), 3 (`query`) and 4 (`feedback`) depend only on `scope` and the changesets of branch 1, and their
Java files are disjoint; `suggestion` (branch 5) calls the `query` service interface, so it starts after branch 3.
The contract files are not disjoint: `frontend/openapi/tsolve-api.yaml` and `frontend/src/shared/api/generated/**`
are touched by Tasks 11, 13, 18 and the three connect tasks, so the branches that touch them merge one at a time;
each rebases on the latest `main`, re-runs `npm run generate:api` and checks it leaves no diff before its PR.

### Tables and changesets

All in `backend/src/main/resources/db/changelog/changes/`, author `search`, one per commit, each with `--rollback`,
added to `db.changelog-master.xml` in order (spec Interface Contracts has the columns). Numbers are the next free
ones on `main` when the branch is cut; today 008–012.

| # | File | Change | Rollback |
|---|------|--------|----------|
| 008 | `008-search-public-ids.sql` | `public_id UUID NOT NULL DEFAULT gen_random_uuid()` + `uq_<table>_public_id` on `ticket`, `project`, `department` (a volatile default gives each existing row its own value) | drop the three columns |
| 009 | `009-search-ticket-updated-at.sql` (`splitStatements:false`: a function body) | `ticket.updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `UPDATE ticket SET updated_at = created_at`, function `search_touch_updated_at()` + trigger `trg_ticket_updated_at BEFORE UPDATE ON ticket` | drop trigger, function, column |
| 010 | `010-search-ticket-embedding.sql` | `CREATE EXTENSION IF NOT EXISTS vector`; `ticket_embedding` (`ticket_id` unique, FK `ticket(id) ON DELETE CASCADE`, `vector(1024)`) | drop the table (the extension stays: dropping it could break a later module) |
| 011 | `011-search-log.sql` | `search_log` + `idx_search_log_created_at (created_at)`, CHECKs on `channel`, `result_count` | drop the table |
| 012 | `012-search-ticket-feedback.sql` | `ticket_feedback` + `uq_ticket_feedback_ticket_user (ticket_id, user_id)`, CHECKs on `rating`, `channel`, `comment` length | drop the table |

The rollbacks of 008, 011 and 012 drop data (the public ids, the Search Log, the Feedback): they are for development
databases only and are never run on staging or production; a mistake there is fixed forward (AGENTS §7).
`SearchChangesetRollbackIT` runs in its own container and rolls back by the count of changesets of author `search`
(not up to a fixed number), then checks that no search column, table, function or trigger is left (AC-005.3).

The `department` table keeps its BIGINT key (other modules map it), and `ticket` has a UUID key since the
ticket-review changeset `008-review-ticket-storage.sql`; `public_id` is the id the API shows for both. The Project's key is a UUID since `017-search-project-uuid-key.sql` (AC-005.10, KAN-291): `project.project_id`
holds the former `public_id`, which is dropped, and every `project_id` column holds that UUID. The three new tables follow the tenant-table convention (platform AC-001.20, `backend/AGENTS.md`
§7). If the platform's row-policy guard (AC-001.29, AC-001.31) is on `main` when a changeset is written, that
changeset also enables row-level security and adds the platform's policy to its table; otherwise the platform's
changeset covers it.

### Ports and stand-ins

| Port (search-owned) | Stand-in | Until | Effect |
|---------------------|----------|-------|--------|
| `scope.service.VisibleScope.of(TSolveUser)` → `Scope(departmentIds, managedProjectIds, writer)` | `NoVisibleScope` (default) | `identity` / `project-integration` provide membership | every list, search and suggestion empty; every Feedback 404 (AC-006.2, AC-006.12) |
| same port | `DevAllVisibleScope`, only when `SEARCH_DEV_SCOPE=all` | local demo only | every `ACTIVE` Department, every Project, no writer; one startup warning (AC-006.11) |

Exactly one of the two beans exists: `DevAllVisibleScope` carries
`@ConditionalOnExpression("'${SEARCH_DEV_SCOPE:}' == 'all'")` and `NoVisibleScope` carries the exact mirror
`@ConditionalOnExpression("'${SEARCH_DEV_SCOPE:}' != 'all'")`. Both compare case-sensitively; `@ConditionalOnProperty`
is not used because its `havingValue` ignores case (`ALL` would turn the dev scope on). Unset, empty or any other value
gives the fail-closed stand-in (AC-006.12). The startup warning of AC-006.11 is logged from the bean's constructor. `DevAllVisibleScope` reads the ids through `scope/repository/ScopeTicketRepository`
(`JpaRepository<SearchTicket, Long>`: `select distinct departmentId … where departmentStatus = 'ACTIVE'`,
`select distinct projectId …`), so a Department or Project with no ticket is left out, which changes no answer.
It is the counterpart of ticket's `StubProjectAccess` and is deleted with `NoVisibleScope` when the real bean lands.
It gives data, not a signed-in user: the backend has no dev principal today, so a local demo still needs the
`identity` sign-in (edge case #44).

The caller is `@AuthenticationPrincipal TSolveUser` (its `id` is `app_user.id`, written to `search_log.user_id`
and `ticket_feedback.user_id`). The stand-in is deleted when the real bean lands (same as `NoManagedProjects`).
The services short-circuit on an empty scope: no SQL with an empty `IN ()` list, no embedding call.

### Read model

`SearchTicket` is an `@Immutable` entity on `@Subselect("select t.id, t.public_id, t.ticket_id, t.title, t.source,
t.status, t.writer, t.resolved_date, t.updated_at, t.symptoms, t.root_cause, t.resolution_steps, t.workaround,
t.scope, p.project_id, p.public_id as project_public_id, p.jira_project_key as project_name, d.department_id,
d.public_id as department_public_id, d.name as department_name, d.status as department_status from ticket t join
project p on p.project_id = t.project_id join department d on d.department_id = p.department_id")` with
`@Synchronize({"ticket", "project", "department"})`. It is never written, and Hibernate's schema validation skips
it. `p.jira_project_key as project_name` is the stand-in Project name (AC-006.6, user 05/11) until project-integration
adds a name column; it is the one mapping line to change then, together with the same expression in the two ranking
queries (`TicketRankingRepository`). A ticket whose `project_id`
matches no Project is dropped by the join (it has no Department, so nobody may see it).

**Exception to `backend/AGENTS.md` rule 2 (accepted by the user, 06/11):** search reads the `ticket`, `project` and
`department` tables of other modules directly, read-only, instead of through their service interfaces. The exception
is contained in `SearchTicket` (the `@Subselect`) and in the native ranking SQL of `TicketRankingRepository` and
`TicketEmbeddingRepository`; the reason is the pgvector join (ranking, filters, paging and the helpful rate in one
query), which no service interface can give. Search never writes those tables (only its own columns, by changeset).
The BE lead confirms the exception at PR review.

### Ticket List (STORY-006)

- `TicketListQuery` parses the raw request parameters like triage's `HistoryQuery`: `page ≥ 0`, `size` 1..100,
  `sort` `<key>,<asc|desc>` with the keys of AC-006.3, else `InvalidListQueryException` → 400 MSG06; `keyword`
  `@Size(max = 100)` → MSG02 on `keyword` (AC-006.9).
- `TicketListSpecs.visible(scope)`: `departmentStatus = 'ACTIVE' AND ((status = 'PUBLISHED' AND departmentId IN D) OR
  (status <> 'PUBLISHED' AND (writer = w OR projectId IN P)))`, leaving out an empty part; filters AND-ed.
- `departmentId`: `TicketListServiceImpl` first resolves the public id to the internal Department id (through
  `TicketListRepository.findDepartmentId(publicId)`, JPQL `select distinct t.departmentId from SearchTicket t where
  t.departmentPublicId = :publicId`; a Department with no ticket has nothing to list anyway) and checks it against
  `scope.departmentIds`;
  unknown or outside the scope → an empty page at once, so the user's own tickets and the tickets of a managed Project
  in another Department are not returned either (AC-006.4); inside the scope it becomes `departmentId = :id`.
- Keyword:
  `lower(x) like lower(:k) escape '\'` on `sourceTicketId`, `title` and the five Solution sections with
  `%`, `_` and `\` escaped (AC-006.4).
- Sort keys map to attributes: `sourceTicketId`, `title`, `project` → `projectName` (the `jira_project_key`),
  `updatedAt`; `reviewDueDate` is accepted and adds no order, so `id desc` alone decides (AC-006.7); `id desc` is
  always appended (AC-006.3).
- `TicketSummaryMapper` sets `reviewDueDate` to null (`@Mapping(target = "reviewDueDate", expression = "java(null)")`)
  until ticket-review stores a due date (AC-006.7).
- `@Transactional(readOnly = true)`; returns `Page<TicketSummaryResponse>`; the controller wraps it in `PagedModel`.

### Ranking and fallback (STORY-007, STORY-009)

`TicketRankingService.rank(String text, String keywordText, RankFilter filter, Scope scope, Pageable page)` →
`RankedPage(Page<TicketSearchResultResponse> page, boolean isFallback)`:

1. Empty scope → empty page, no embedding call.
2. `EmbeddingClient.embed(List.of(text))` outside any transaction (AGENTS §4). `AiUnavailableException` → step 4.
3. Semantic (native SQL, `TicketRankingRepository.semantic`): searchable tickets joined with `ticket_embedding`,
   `1 - (e.embedding <=> CAST(:query AS vector)) >= :minSimilarity`, filters, `ORDER BY e.embedding <=>
   CAST(:query AS vector), t.id`, `LIMIT/OFFSET` and a count query. `minSimilarity` = `${SEARCH_MIN_SIMILARITY:0.5}`.
4. Keyword fallback (`TicketRankingRepository.keyword`): searchable tickets whose title, Symptoms or Resolution
   steps `ILIKE '%' || :k || '%' ESCAPE '\'` (escaped), `ORDER BY t.resolved_date DESC NULLS LAST, t.id DESC`;
   `isFallback = true`.

- Both queries: `t.status = 'PUBLISHED' AND d.status = 'ACTIVE' AND p.department_id IN (:departmentIds)`; every
  optional filter is bound as text (`String`, null when absent) and cast in SQL, e.g. `(CAST(:departmentId AS text) IS
  NULL OR d.public_id = CAST(:departmentId AS uuid))`, `(CAST(:resolvedFrom AS text) IS NULL OR t.resolved_date >=
  CAST(:resolvedFrom AS date))`: a null `UUID` or `LocalDate` parameter is sent by Hibernate 6 as `bytea` and fails
  in PostgreSQL; an IT call with no filter at all proves it; `departmentId` compared with `d.public_id` (an id outside the
  scope matches nothing, AC-007.3); `helpfulRate` from a `LEFT JOIN` on `(select ticket_id, count(*) total,
  count(*) filter (where rating = 'HELPFUL') helpful from ticket_feedback group by ticket_id)` as
  `round(100.0 * helpful / total)` (PostgreSQL rounds a numeric half away from zero, which is half up here), null
  without rows.
- `Excerpts.of(symptoms, resolutionSteps)`: joined with a space, `\s+` → one space, trimmed, first 200 characters.
- `resolvedAt`: `resolved_date.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime()`, null when the date is
  null (AC-007.5); the contract field becomes `date-time | null`, changed in the YAML by Task 13.
- Search: `TicketSearchQuery` takes the raw parameters as strings, like triage's `HistoryQuery` and
  `TicketListQuery`: `departmentId` a UUID, `source` a contract value, `resolvedFrom`/`resolvedTo` only `yyyy-MM-dd`
  with a four-digit year (`DateTimeFormatter.ofPattern("uuuu-MM-dd")` with `ResolverStyle.STRICT`, BUG-106), `page`,
  `size` as in AC-006.9, else `InvalidSearchQueryException` → 400 MSG06; `q` `@NotBlank @Size(max = 200)` → MSG01 /
  MSG02 on `q`; `resolvedFrom > resolvedTo` → 400 MSG39 (AC-007.7). `TicketSearchServiceImpl` then validates nothing
  more, rank with `text = keywordText = q.trim()`, then on page 0 insert the `search_log` row
  (`SearchLogRepository.log(userId, departmentPublicId, query, 'WEB', total, isFallback)`, a native
  `INSERT … VALUES (…, (SELECT department_id FROM department WHERE public_id = :departmentPublicId), …)`).
- Suggestions (`TicketSuggestionServiceImpl`): `text = summary + "\n\n" + description` (summary alone when blank),
  `keywordText = summary`, page 0 of size 5, returns the content list; logs channel `ISSUE_PANEL`, no Department,
  `result_count` = items returned.
- Transactions: the ranking read and the log insert each run in their own short repository transaction; nothing holds
  a transaction across the Ollama call. A failed log insert answers 500 (the row is part of the contract).

### Feedback (STORY-008)

`TicketFeedbackServiceImpl.give(TSolveUser, UUID ticketId, TicketFeedbackRequest)`, `@Transactional`:
`FeedbackTicketRepository.findByPublicId` → present, `PUBLISHED`, `departmentStatus = 'ACTIVE'` and `departmentId ∈
scope.departmentIds`, else `NotFoundException` (404 MSG04, AC-008.3); then the native upsert `INSERT … ON CONFLICT
(ticket_id, user_id) DO UPDATE SET rating, comment, channel, updated_at = now()` — the unique key makes two
concurrent PUTs of one user end in one row (AC-008.2). `isHelpful` is `@NotNull Boolean` (MSG01), `comment`
`@Size(max = 500)` (MSG02), trimmed, blank → null.

### Embedding pipeline (STORY-005)

- `TicketsPublishedListener`: `@TransactionalEventListener(phase = AFTER_COMMIT)`; hands the ids to Spring Boot's
  `applicationTaskExecutor` (`@Qualifier`, because `@EnableScheduling` also registers a `TaskScheduler`) inside a
  `try/catch` that logs a rejection, so nothing is thrown back into the import's commit (AC-005.5).
- `TicketEmbeddingServiceImpl.embed(ids)`, in batches of `${SEARCH_EMBED_BATCH:4}`, **one batch at a time** for the
  listener and the job together (the batch method is `synchronized`), so embedding never holds more than one of the
  `AI_MAX_CONCURRENT` (2) Ollama slots and a search query can still get the other. `ponytail:` comment: the lock is per
  process; with several API replicas each runs its own batch, fine at today's one replica. For each batch: read the
  texts of the ids still `PUBLISHED` through `EmbeddingTicketRepository.findByIdInAndStatus` on `SearchTicket` (JPA,
  read-only; no native SQL), build
  `EmbeddingText`, call `EmbeddingClient.embed` (no transaction), upsert all rows of the batch in one transaction
  (`INSERT … ON CONFLICT (ticket_id) DO UPDATE SET embedding, model, updated_at`). `AiUnavailableException` or any
  runtime failure of a batch: log `embedding failed for tickets {ids}` (ids only, AC-005.6) and go on.
- `EmbeddingCatchUpJob`: `@Scheduled(fixedDelayString = "${SEARCH_EMBED_CATCH_UP_DELAY:60s}")`, one batch of the
  lowest ids `PUBLISHED` without a row and not skipped (AC-005.7). `@Scheduled` runs only with `@EnableScheduling`;
  search has its own `config/SearchSchedulingConfig` so it does not depend on the ticket module's
  `TicketSchedulingConfig`. The listener and the job may embed the same ticket one after the other; the upsert makes
  that harmless.
- **Skip rule (AC-005.9):** the service keeps an in-memory `Map<Long, Integer>` of consecutive failures per ticket id;
  every failed batch adds 1 to each of its ids, a success removes them. An id that reaches 3 is skipped: the job
  passes the skipped ids to `findPublishedWithoutEmbedding(limit, skipped)` (`AND t.id <> ALL(:skipped)`) and logs the
  skip once with the id only. The map is lost on restart, which retries them. A `TicketsPublishedEvent` for a skipped
  id still embeds it (the listener ignores the map), so a re-published ticket is never stuck. During an Ollama outage
  longer than 3 job runs, the first ids reached are skipped too; a restart (or their next publish) brings them back.
- `model` is written from `${OLLAMA_EMBED_MODEL:bge-m3}` (the same value the `ai` client uses).
- Vectors are passed as text literals (`PgVector.literal`) and cast in SQL: no new Maven dependency (spec NFR 8).

### Key Decisions (backend)

| Decision | Chosen | Alternatives | Rationale |
|----------|--------|--------------|-----------|
| Ranking (user, 05/11) | bge-m3 query embedding, cosine distance on pgvector; keyword fallback on Ollama failure or timeout | Hybrid score (vector + full-text); full-text only | User decision; GBR-12 (AI optional) |
| Relevance cut-off (accepted by the user, 06/11) | `SEARCH_MIN_SIMILARITY`, default 0.5, configurable, tuned on real data | No cut-off (every ticket is a result); top-k only | Without a cut-off no search ever has 0 results, so the Search Log can never show a knowledge gap (UC-26) |
| Data (user, 05/11) | Search-owned changesets only; `public_id` UUID on `ticket`, `project`, `department`; `updated_at` on `ticket` | Change the keys to UUID; wait for other modules' columns | User decision; other modules' entities and BIGINT keys stay untouched |
| Project key (user, 07/11, KAN-291) | `project.project_id` becomes the UUID key holding the former `public_id`, which is dropped; every `project_id` column holds it; Department stays BIGINT | Keep both columns; change the Department too | One Project id type ends the two-form join and the hex ids (#140, #147); supersedes the 05/11 row for the Project only |
| `updated_at` maintenance | Database trigger `BEFORE UPDATE` | Each writer module sets it in its entity | No other module's Java file changes (NFR 9); a future writer cannot forget it |
| Visibility (user, 05/11) | Search-owned port `VisibleScope` + fail-closed `NoVisibleScope` | Read `project_membership` directly; wait for identity | Same pattern as triage; no membership table exists |
| Local demo scope (user, 05/11) | `DevAllVisibleScope` only when `SEARCH_DEV_SCOPE=all`; otherwise `NoVisibleScope` | A per-user list like `TSOLVE_STUB_PROJECTS`; no dev scope | A demo needs data while membership is missing; one exact value keeps the default fail-closed, and the bean does not exist without it |
| Project name (user, 05/11) | `project.jira_project_key` as `TicketProject.name` (stand-in) | Wait for a name column | No name column exists; triage shows the key too; one mapping line changes later |
| Review due date (user, 05/11) | Always null; sort key kept | Drop it from the contract | No column; no frontend change |
| Resolved time (user, 05/11) | Start of `resolved_date` in Asia/Ho_Chi_Minh; `resolvedAt` nullable | `resolvedDate` as a date | Keeps the date-time field the card formats; the resolved date is optional in the import |
| Reading other modules' tables (AGENTS rule 2 exception, accepted by the user, 06/11) | `@Immutable @Subselect` entity `SearchTicket` (list, feedback, embedding texts) + native SQL (ranking) | A database view (one more changeset, needs `security_invoker` for the row guard); a `ticket` query service | JPA Specification and `Pageable` sorting for the list with no new table or view; the ticket module has no query service and is not ours to change; pgvector needs native SQL anyway (AGENTS §3) |
| Embedding load on Ollama | One batch at a time, `SEARCH_EMBED_BATCH` default 4; skip an id after 3 failures in a row until restart | Batches of 16 in parallel; retry forever | The embedding never takes both Ollama slots from search; the catch-up job never spins on the same ids |
| Embedding trigger | AFTER_COMMIT listener → `applicationTaskExecutor`, plus a `@Scheduled` catch-up job | Synchronous listener (the import response waits for Ollama); JobRunr job (`worker` not built, no dependency); catch-up job only | The import never waits for or fails on Ollama (AC-005.5); the job covers Ollama downtime and tickets published before this change |
| Vector index | None (exact distance on every candidate) | HNSW / IVFFlat | Exact and correct with the Department filter and paging; HNSW with a filter can return fewer rows than asked; the volume is thousands of tickets. A `ponytail:` comment on the semantic query: add HNSW past ~50 000 vectors |
| Vector binding | Text literal + `CAST(:v AS vector)` | `com.pgvector:pgvector` / `hibernate-vector` | No new Maven dependency (NFR 8) |
| Feedback storage (user, 05/11) | One row per (ticket, user), native upsert; `rating` and `channel` as in the baseline `feedback` table | Append-only rows, latest wins | User decision (a new Feedback replaces the old, UC-28); the unique key handles concurrent PUTs. The baseline calls Feedback append-only; the user decision of 05/11 wins |
| Search Log rows | One per page-0 search and per suggestion request; `is_fallback` added to the baseline columns | A row per request (pages double-count) | Paging is not a new search; the dashboard counts knowledge gaps per search |
| Package layout (user, 05/11) | Feature subpackages `scope`, `embedding`, `list`, `query`, `feedback`, `suggestion`, each with the AGENTS layers | One `controller/`, `service/`… for the whole module | Parallel branches on disjoint files; precedent `identity.signin` |
| Department filter | Compared with `department.public_id` inside the scope; outside the scope → empty page | 403 MSG08 (triage) | Nothing leaks either way; the screens show MSG04 instead of an error state |
| Paging validation | Parsed by the feature (`TicketListQuery`, `TicketSearchQuery`), 400 MSG06 | Spring's `Pageable` resolver | The resolver clamps `size` silently; AC-006.9 needs 400 (triage precedent) |

Decided in planning (builder check):
- The size limits of `POST /api/v1/search/suggestions` are Jira's own field limits (summary 255, description 32767).
- `excerpt` is computed in Java (`Excerpts`), so it is unit-tested without a database.
- The changeset numbers are re-checked against `main` at implementation; a clash with a teammate's new changeset is
  solved by taking the next free number, never by editing an applied one.
- `SEARCH_MIN_SIMILARITY`, `SEARCH_EMBED_BATCH`, `SEARCH_EMBED_CATCH_UP_DELAY` and `SEARCH_DEV_SCOPE` (empty in
  `.env.example`, with a comment: `all` only for a local demo) are not secret and carry
  defaults (`@Value("${…:default}")`), so `application.yml` does not change; their names go into `.env.example` with
  the first task that reads them (that file is outside `module.md` `paths`: a quick change if the gate blocks it).
- The embedding text is the sections present, one per line: title, Symptoms, Root cause, Resolution steps,
  Workaround (Scope is left out: it says where a Solution applies, not what it solves).

### Contract and frontend connection

- Each backend branch keeps `frontend/openapi/tsolve-api.yaml` the source of truth (AGENTS rule 8): if the backend
  must change a field (AC-007.5 makes `TicketSearchResult.resolvedAt` nullable, in Task 13), the YAML changes first in that branch and
  `npm run generate:api` runs in the same commit. Task 11 adds `400: ProblemDetail` to `getTickets` (AC-006.9);
  KAN-146 adds `suggestTickets` and `TicketSuggestionRequest`. A backend task that changes the YAML also runs the
  frontend `npm run typecheck` and checks that `npm run generate:api` leaves no diff.
- Each frontend branch: backend running locally, `npm run api:pull`. `api:pull` rewrites the whole file from
  springdoc, so only the differences of this feature's operations and schemas are kept (the file is restored with
  `git restore` and those differences applied by hand, so the mock-only endpoints of other modules stay); then
  `npm run generate:api`; the feature's hooks, components and MSW mocks follow the regenerated types; then the screen
  is run with `VITE_API_MOCKING=false` against the backend.

### Edge Cases & Risks (backend)

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 19 | STORY-005 | Errors | Ollama down during an import | High | Import answers as usual; no rows; the catch-up job embeds later | Task 10 (AC-005.5, AC-005.6, AC-005.7) |
| 20 | STORY-005 | Timing | Listener and catch-up job embed the same ticket at once | Medium | One row (upsert) | Task 10 (AC-005.4) |
| 21 | STORY-005 | Data | Event lists an id that is not Published or missing | Medium | Skipped, the others embedded | Task 10 (AC-005.8) |
| 22 | STORY-005 | Errors | One batch keeps failing (a text Ollama rejects) | Medium | After 3 failures in a row its ids are skipped until restart (AC-005.9); the job moves on | Task 10 |
| 48 | STORY-006 | Authorization | `departmentId` of another Department while the user manages a Project there or wrote a ticket there | High | Empty page (AC-006.4) | Task 11 |
| 49 | STORY-006 | Security | `SEARCH_DEV_SCOPE=ALL` or `true` | High | Fail closed: `NoVisibleScope` (case-sensitive expression) | Task 9 (AC-006.12) |
| 50 | STORY-007 | Input | `resolvedFrom=+999999999-12-31` or `2026-1-5` | Medium | 400 MSG06 (BUG-106) | Task 14 (AC-007.7) |
| 51 | STORY-007 | Integration | Search with no filter at all (null parameters) | High | 200; text-bound parameters avoid the `bytea` error | Task 13 |
| 23 | STORY-005 | Data | Existing rows when 008 and 009 run | High | A distinct `public_id` per row; `updated_at = created_at` | Tasks 4, 5 (AC-005.1) |
| 24 | STORY-006 | Authorization | A Resolver passes another Department's `departmentId` | High | Empty page, nothing leaks | Task 11 (AC-006.4) |
| 25 | STORY-006 | Authorization | Pending ticket of another writer in a non-managed Project | High | Not listed | Task 11 (AC-006.1) |
| 26 | STORY-006 | Data | Department deleted (status not `ACTIVE`) | High | Its tickets in no list, search, suggestion or Feedback | Tasks 11, 13, 16 |
| 27 | STORY-006 | Input | Keyword `50%_off\` | Medium | Matched literally | Task 11 (AC-006.4) |
| 28 | STORY-006 | Input | `size=1000`, `sort=foo,desc`, `status=DRAFT` | Medium | 400 MSG06 | Task 11 (AC-006.9) |
| 29 | STORY-006 | Tenancy | A `workspaceId` query parameter | High | Ignored; same answer | Tasks 11, 14, 16, 18 (AC-006.10) |
| 30 | STORY-007 | Errors | Embedding timeout (`AI_EMBED_TIMEOUT`) or AI permit wait exceeded | High | 200 from the keyword match, `is_fallback` true | Tasks 13, 14 (AC-007.2, AC-007.6) |
| 31 | STORY-007 | Data | Published ticket without a vector yet | Medium | Missing from semantic results until the job runs; found by the fallback | Task 13 |
| 32 | STORY-007 | Data | An Unpublished ticket still has a vector | High | Never returned (`status` filter) | Task 13 (AC-007.1) |
| 33 | STORY-007 | Scale | Query below the cut-off for every ticket | Medium | Empty page, `result_count` 0 (knowledge gap) | Tasks 13, 14 |
| 34 | STORY-007 | Data | `helpfulRate` with 2 of 3 Helpful; 1 of 2 | Medium | 67; 50 | Task 13 (AC-007.4) |
| 35 | STORY-007 | Input | `resolvedFrom` after `resolvedTo`; a ticket with no `resolved_date` and a date filter | Medium | 400 MSG39; not matched | Tasks 13, 14 (AC-007.3, AC-007.7) |
| 36 | STORY-007 | Privacy | Query text in the application log | High | Never logged; stored only in `search_log` | Task 14 (AC-007.9) |
| 37 | STORY-008 | Concurrency | Double PUT of one user | High | One row | Task 16 (AC-008.2) |
| 38 | STORY-008 | Authorization | Feedback on another Department's or an Unpublished ticket | High | 404 MSG04 | Task 16 (AC-008.3) |
| 39 | STORY-009 | Input | Description null, blank or 32 768 characters | Medium | Summary alone; 400 MSG02 on `description` | Task 18 (AC-009.1, AC-009.4) |
| 40 | STORY-009 | Auth | A call with only a Forge Invocation Token | High | 401 MSG07 | Task 18 (AC-009.5) |
| 41 | — | Risk | `CREATE EXTENSION vector` needs a privilege the migration user lacks on a server | High | Provisioning (`platform`) creates the extension as the server owner; the changeset's `IF NOT EXISTS` then passes | — (coordination with Hiếu) |
| 42 | — | Risk | `NoVisibleScope` makes every real-backend screen empty until membership lands | High | `SEARCH_DEV_SCOPE=all` shows data locally once a user can sign in; the connect tasks also check the empty state, the errors and the contract; the data paths are proven by the `*IT` tests | Tasks 12, 15, 17 |
| 43 | — | Risk | The background embedding has no Workspace binding once `TenantContext` lands | High | Listener and job bind the Workspace (the event carries it, or one run per Workspace) before multi-tenant routing ships | — (platform STORY-002, worker) |
| 44 | — | Risk | No web sign-in yet sets `TSolveUser` as the principal (identity on Keycloak, KAN-275), and the backend has no dev principal | High | Controller tests use `@WithTSolveUser`; the browser check with data waits for identity's sign-in; no dev principal is added by search | Tasks 12, 15, 17 |
| 45 | STORY-006 | Security | `SEARCH_DEV_SCOPE` left as `all` on a deployed server | High | Every user sees every Active Department; the startup warning names it; deployment configs never set it; any other value fails closed | Task 9 (AC-006.11, AC-006.12) |
| 46 | STORY-007 | Data | Ticket with no `resolved_date`; a date at the Vietnam day boundary | Medium | `resolvedAt` null; `2026-10-01T00:00:00+07:00` | Task 13 (AC-007.5) |
| 47 | STORY-002 | Data | Result with `resolvedAt` null on the card | Medium | "Resolved —" | Task 15 (AC-002.15) |

### Solution retrieval for the Chatbot (STORY-010, change of 2026-11-06)

`vn.tsolve.search.retrieval` exposes `SolutionRetrieval.nearest(queryVector, minSimilarity, limit)` to the `chatbot`
module (search Task 19). It ranks `ticket_embedding` by cosine distance like STORY-007 but over every Workspace
Solution (`PUBLISHED`, Department `ACTIVE`), with no `VisibleScope`, no embedding call, no `search_log` row and no
keyword fallback: the chatbot embeds the question once and shares the vector with its Wiki source, and answers
`NO_SOURCE` or 503 itself. The query and the edge cases are in the chatbot design ("Backend (change of 2026-11-06)").

| Decision | Chosen | Alternatives | Rationale |
|----------|--------|--------------|-----------|
| Chatbot access to Solutions | A search `service/` interface taking a vector | Chatbot reads `ticket_embedding`; reuse `TicketRankingService` | `backend/AGENTS.md` module rule; the chatbot scope is the Workspace, not the user's Departments |
