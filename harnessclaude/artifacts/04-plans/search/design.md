---
module: search
date: 2026-10-25
---

# Design: search

Spec: `artifacts/03-modules/search/spec.md` · Module: `artifacts/03-modules/search/module.md` ·
Rules: `frontend/AGENTS.md` and `frontend/guidelines/` (they win over anything here that contradicts them).

## Architecture Overview

Web app only for now: the backend is served by MSW from the contract added to `frontend/openapi/tsolve-api.yaml`.
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
          │ useGiveFeedback (generated mutation, handlesOwnErrors)
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

The Department and Project ids of the mocks are the fixture users' ids as strings (`'1'` IT, `'2'` HR; `'101'`,
`'102'`, `'201'`), declared once per mock file with a comment pointing to `src/mocks/session.ts`, because feature
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
        ├── hooks/useGiveFeedback.ts
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
