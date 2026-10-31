---
module: search
date: 2026-10-25
total_tasks: 3
owner: Dev
---

# Tasks: search — iteration 1

Spec: `artifacts/03-modules/search/spec.md` · Design: `artifacts/04-plans/search/design.md`

One task = one Jira Story = one branch from an up-to-date `main` = one PR, merged in this order. Each task is one
vertical slice a user can try: it carries every layer it needs (contract, mocks, hooks, components, tests).
Frontend only; the backend is not in this iteration. Every test's `it` name starts with the AC id it proves
(guideline 10 T6). Each task ends with the frontend `verify` (root `CLAUDE.md`): `format:check`, `lint`,
`typecheck`, `test`, `build`, and `generate:api` with no diff.

## Task 1: Ticket List

**Story:** STORY-001, STORY-004
**Feature:** ticket-list
**Jira:** KAN-227 · branch `feature/KAN-227-search-ticket-list` (also carries the plan commit); the backend API of UC-27 is KAN-147
**Description:**
- Contract: tag `tickets`, `GET /api/v1/tickets` (`getTickets`) with `TicketStatus`, `TicketProject`,
  `TicketDepartment`, `TicketSummary`, `TicketSummaryPage` (spec Interface Contracts); regenerate the client.
- Mocks: `ticket-data.ts` (28 tickets, every status and source, Departments `'1'` IT and `'2'` HR with Projects
  `'101'` ITSUP, `'102'` ITNET, `'201'` HRHELP as in `src/mocks/session.ts`, UUID `ticketId`s, Solution text);
  the handler applies `keyword`, `departmentId`, `source`, `status`, sort and paging; `reviewDueDate` only for
  Pending review and Changes requested; `resetMockData()` stays empty (the data never changes).
- Screen: `useTicketList` (URL state, default sort `updatedAt,desc`, generated `useGetTickets`),
  `TicketListFilters` (Keyword on Enter, `maxLength` 100, Department from the session as `String(id)`, Source,
  Status), `TicketListTable` (`DataTable`, Ticket ID link to `paths.ticketDetail(ticketId)`, `StatusTag`,
  `TICKET_SOURCE_LABELS`, `formatDateTime`, "—"), `TicketListPage` replacing the placeholder.
- Tests: the page test (`renderApp('/tickets', { role })`, MSW overrides for loading, empty, 500 then Try again,
  HTML in a title, a user with no Department) and the mock handler test.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (regenerated)
- Modify: `frontend/src/features/tickets/pages/TicketListPage.tsx`, `frontend/src/features/tickets/mocks/handlers.ts`
- Create: `frontend/src/features/tickets/pages/TicketListPage.module.css`,
  `frontend/src/features/tickets/components/TicketListFilters.tsx`,
  `frontend/src/features/tickets/components/TicketListFilters.module.css`,
  `frontend/src/features/tickets/components/TicketListTable.tsx`, `frontend/src/features/tickets/hooks/useTicketList.ts`,
  `frontend/src/features/tickets/mocks/ticket-data.ts`
- Test: `frontend/src/features/tickets/pages/TicketListPage.test.tsx`,
  `frontend/src/features/tickets/mocks/handlers.test.ts`
**Covers:** AC-001.1 – AC-001.14, AC-004.1, AC-004.2 (edge cases #1–#5, #16)
**Dependencies:** none
**Estimated size:** medium

---

## Task 2: Search Published tickets

**Story:** STORY-002, STORY-004
**Feature:** ticket-search
**Jira:** KAN-145 · branch `feature/KAN-145-search-page`
**Description:**
- Contract: tag `search`, `GET /api/v1/search/tickets` (`searchTickets`) with `TicketSearchResult` and
  `TicketSearchResultPage`, reusing Task 1's `TicketProject` and `TicketDepartment`; regenerate the client.
- Mocks: `search-data.ts` (14 Published results with Solution text, same Department and Project constants as
  Task 1, two with `helpfulRate: null`, one with `0`); the handler matches `q` on title and Solution text
  (case-insensitive) and applies `departmentId`, `source`, `resolvedFrom`, `resolvedTo` (days included) and paging.
- Screen: `useTicketSearch` (URL state for `q`, filters and page; `enabled` only with a non-blank `q`),
  `SearchFilters` (Search box on Enter, `maxLength` 200; Department; Source; `DatePicker.RangePicker` as
  `YYYY-MM-DD`), `SearchResultList` (`LoadingState`, `ErrorState` with Try again, `EmptyState` MSG04, antd
  `Pagination` with "Total N items"), `SearchResultCard` (title link, "Project · Department · Resolved …",
  "N% helpful" with a thumbs-up icon unless null, clamped excerpt), `SearchPage` replacing the placeholder.
- Tests: the page test (no call without `q`, Enter with blank text, filters with and without `q`, URL restore,
  `helpfulRate` 0 and null, loading, empty, 500 then Try again, HTML in title and excerpt) and the mock handler test.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (regenerated)
- Modify: `frontend/src/features/search/pages/SearchPage.tsx`, `frontend/src/features/search/mocks/handlers.ts`
- Create: `frontend/src/features/search/pages/SearchPage.module.css`,
  `frontend/src/features/search/components/SearchFilters.tsx`,
  `frontend/src/features/search/components/SearchResultList.tsx`,
  `frontend/src/features/search/components/SearchResultCard.tsx`,
  `frontend/src/features/search/components/SearchResults.module.css`,
  `frontend/src/features/search/hooks/useTicketSearch.ts`, `frontend/src/features/search/mocks/search-data.ts`
- Test: `frontend/src/features/search/pages/SearchPage.test.tsx`, `frontend/src/features/search/mocks/handlers.test.ts`
**Covers:** AC-002.1 – AC-002.14, AC-004.3 (edge cases #6–#11, #16)
**Dependencies:** Task 1 (its `TicketProject` and `TicketDepartment` schemas)
**Estimated size:** medium

---

## Task 3: Give Feedback popup

**Story:** STORY-003, STORY-004
**Feature:** give-feedback
**Jira:** KAN-148 · branch `feature/KAN-148-search-give-feedback`
**Description:**
- Contract: tag `feedback`, `PUT /api/v1/tickets/{ticketId}/feedback` (`giveFeedback`, `204`) with
  `TicketFeedbackRequest` (`isHelpful`, `comment`); regenerate the client.
- Mocks: the handler accepts the Published ticket ids of Task 2's `search-data.ts` (copied as constants), answers
  204 and stores the Feedback per ticket (a new one replaces the old one), 400 MSG01 on `isHelpful` when missing,
  400 MSG02 on `comment` over 500 characters, 404 for any other id; `resetMockData()` clears the stored Feedback.
- Popup: `useGiveFeedback` (generated mutation, `meta: { handlesOwnErrors: true }`), `GiveFeedbackModal`
  (`FormModal` "Give Feedback", Send; Helpful? as two `aria-pressed` buttons bound to `isHelpful` with
  `rules.required('Helpful?')`; Comment `TextArea` with `rules.maxLength('Comment', 500)` and a counter; trimmed
  comment, blank → null; field errors through `applyApiErrors`, others `showMessage(code ?? 'MSG06')`; success →
  `onClose()` and MSG26); export it from `features/feedback/index.ts`.
- Tests: the popup test (`renderWithProviders`, each AC, double click on Send, 404, reopen after Cancel) and the
  mock handler test.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (regenerated)
- Modify: `frontend/src/features/feedback/index.ts`, `frontend/src/features/feedback/mocks/handlers.ts`
- Create: `frontend/src/features/feedback/components/GiveFeedbackModal.tsx`,
  `frontend/src/features/feedback/components/GiveFeedbackModal.module.css`,
  `frontend/src/features/feedback/hooks/useGiveFeedback.ts`
- Test: `frontend/src/features/feedback/components/GiveFeedbackModal.test.tsx`,
  `frontend/src/features/feedback/mocks/handlers.test.ts`
**Covers:** AC-003.1 – AC-003.11, AC-004.4 (edge cases #12–#16)
**Dependencies:** Task 2 (the Published ids of its mock data)
**Estimated size:** small
