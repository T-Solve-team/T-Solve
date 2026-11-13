---
module: ticket-repository
date: 2026-11-07
total_tasks: 13
owner: Dev
---

# Tasks: ticket-repository — Iteration 1 (KAN-45 Jira import)

Spec: `artifacts/03-modules/ticket-repository/spec.md`
Design: `artifacts/04-plans/ticket-repository/iteration-1/design.md` (Master: `artifacts/04-plans/ticket-repository/design.md`)

Paths are relative to the repo root. `T` = `backend/src/main/java/vn/tsolve/ticket`, `TT` = `backend/src/test/java/vn/tsolve/ticket`,
`F` = `frontend/src/features/ticket-import`. Every test method name cites its AC id (`…_AC_001_2`). Test files of this
module are named `Jira*`, `*Draft*` or live under `jira/` (module.md `paths`), so trace can tell them from the CSV
tests of `import-ticket-list`, which use the same AC ids.

Order: Tasks 1–4 and 7 need nothing else. Tasks 5, 6, 8 and 9 start after the `import-ticket-list` features
`import-type-upload`, `preview-fill-solution`, `import-type-confirm`, `row-solution-popup` and
`fill-solution-screen` of PR #110 are done.

## Task 1: OpenAPI contract for Jira import and Drafts

**Story:** STORY-001, STORY-002, STORY-003, STORY-004
**Feature:** jira-import-contract
**Description:** Replace the separate Jira preview endpoints added first (issues, issue, solutions, remove-issues, finish and their schemas) by the shape of the spec's Interface Contracts: `POST /imports/jira` (`projectId` UUID) returning `ImportPreviewResponse`; `POST …/draft`, `GET /imports/drafts`, `DELETE /imports/drafts/{previewId}`; `ImportPreviewResponse` + `source`, `resolvedFrom`, `resolvedTo`, `draftSavedAt`, nullable `fileName`; `ImportPreviewRowResponse` + `sourceUrl`, `comments`; `ImportResultResponse` + `notice`. Regenerate the client.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`
- Modify: `frontend/src/shared/api/generated/**` (generated only)
**Dependencies:** none
**Estimated size:** small

---

## Task 2: Schema, entities, Draft-aware repositories, Project Jira key

**Story:** STORY-001, STORY-004
**Feature:** jira-import-schema
**Description:** Liquibase `009-ticket-jira-import.sql` as the design's Data Model. `ImportPreview`: `resolvedFrom`, `resolvedTo`, `moreAvailable`, `draftSavedAt`, a Jira factory, `saveAsDraft(Instant)`; `ImportPreviewRow`: `sourceUrl`, `comments` (JSONB list of `IssueComment`) and a Jira constructor. `ImportPreviewRepository`: `deleteByOwner` and `findIdsCreatedBefore` skip Drafts, `findDraftsByOwner` newest first. `ProjectService.findJiraProjectKey(UUID)`. Testcontainers IT for the partial unique index, the Draft-aware queries and the JSONB round trip.
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/009-ticket-jira-import.sql`, `T/dto/IssueComment.java`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`, `T/entity/ImportPreview.java`, `T/entity/ImportPreviewRow.java`, `T/repository/ImportPreviewRepository.java`, `backend/src/main/java/vn/tsolve/project/service/ProjectService.java`, `backend/src/main/java/vn/tsolve/project/service/impl/ProjectServiceImpl.java`
- Test: `TT/repository/ImportDraftRepositoryIT.java` (AC-004.6)
**Dependencies:** none
**Estimated size:** medium

---

## Task 3: Jira client — `JiraIssueSource` and `JiraRestIssueSource`

**Story:** STORY-001
**Feature:** jira-issue-source
**Description:** Port `JiraIssueSource.resolvedIssues(projectKey, from, to)` returning issues oldest first page by page (`JiraIssue`: key, summary, description text, assignee, resolvedAt, url, comments). `JiraRestIssueSource` with `RestClient` (10 s / 30 s, basic auth), `search/jql` + `nextPageToken`, extra comment pages, Vietnam-time re-check, `AdfText`. `JiraProperties` (no defaults, trailing slash trimmed, non-https rejected). Any 4xx/5xx, timeout or missing config → `JiraUnavailableException` whose message holds only status / error type. Keys in `application.yml` and `.env.example`. Tests with `MockRestServiceServer` (Edge 1, 2, 4, 5, 16).
**Files:**
- Create: `T/service/JiraIssueSource.java`, `T/service/JiraIssue.java`, `T/jira/JiraRestIssueSource.java`, `T/jira/JiraProperties.java`, `T/jira/AdfText.java`, `T/exception/JiraUnavailableException.java`
- Modify: `backend/src/main/resources/application.yml`, `.env.example`
- Test: `TT/jira/JiraRestIssueSourceTest.java`, `TT/jira/AdfTextTest.java`
**Dependencies:** none
**Estimated size:** medium

---

## Task 4: Pull from Jira — service and endpoint

**Story:** STORY-001
**Feature:** jira-pull
**Description:** `JiraImportService.pull(user, projectId, from, to)` per the design's Pull flow (MSG01 / MSG39 first, 403, Jira key or MSG50, duplicate filter per page, 100 + MSG35, scan cap, MSG04, replace the non-Draft preview, preview + Needs Solution rows in one transaction after the Jira calls; Failed batch + MSG50 on `JiraUnavailableException`). `JiraImportController` `POST /api/v1/imports/jira`, PM role.
**Files:**
- Create: `T/service/JiraImportService.java`, `T/service/impl/JiraImportServiceImpl.java`, `T/controller/JiraImportController.java`, `T/dto/JiraPullRequest.java`
- Modify: `T/service/impl/ImportBatchRecorder.java`, `T/dto/ImportPreviewResponse.java` and its mapper
- Test: `TT/service/impl/JiraImportServiceImplTest.java` (AC-001.1…AC-001.10), `TT/controller/JiraImportControllerTest.java`
**Dependencies:** Task 2, Task 3
**Estimated size:** medium

---

## Task 5: Jira preview through the CSV preview endpoints

**Story:** STORY-002
**Feature:** jira-preview
**Description:** Make the rows list, row detail, fill-solution and remove-rows of `import-ticket-list` serve a `JIRA_IMPORT` preview like a `WITHOUT_SOLUTION` one; row detail returns `sourceUrl` and `comments`; the preview response carries source, dates and MSG35. Tests on the Jira paths only (the CSV tests stay as they are).
**Files:**
- Modify: `T/service/impl/ImportPreviewServiceImpl.java`, `T/dto/ImportPreviewRowResponse.java`, `T/mapper/ImportPreviewMapper.java`
- Test: `TT/service/impl/JiraPreviewServiceTest.java` (AC-002.1…AC-002.5, AC-002.7…AC-002.9)
**Dependencies:** Task 4, `import-ticket-list` `preview-fill-solution`
**Estimated size:** small

---

## Task 6: Finish — Confirm of a Jira preview

**Story:** STORY-003
**Feature:** jira-finish
**Description:** In `ImportConfirmServiceImpl`, for `JIRA_IMPORT`: skip Valid rows whose key is now a Ticket (`notice` MSG38 with count and keys; all skipped → Completed batch with 0), retry once after a unique violation (Edge 6), Ticket source `JIRA_IMPORT`, title cut to 200 (Edge 13), Draft deleted with the preview. The Needs Solution check (`MSG_NEW_4`), MSG40 / 403 order, the lock, the Failed batch and the event stay as `import-ticket-list` has them. IT for the concurrency cases (Edge 6, 7) and a Draft of an inactive Project (Edge 11).
**Files:**
- Modify: `T/service/impl/ImportConfirmServiceImpl.java`, `T/dto/ImportResultResponse.java`
- Test: `TT/service/impl/JiraFinishTest.java` (AC-003.1…AC-003.10), `TT/service/impl/JiraFinishIT.java`
**Dependencies:** Task 4, `import-ticket-list` `import-type-confirm`
**Estimated size:** medium

---

## Task 7: Drafts — save, list, reopen, delete

**Story:** STORY-004
**Feature:** jira-drafts
**Description:** `ImportDraftService`: save (`saveAsDraft`, no validation, MSG41, same Draft on save again), list the caller's Drafts newest first, delete (404 for another user's or a CSV preview). Reopen is `GET /imports/previews/{previewId}`. `ImportDraftController`.
**Files:**
- Create: `T/service/ImportDraftService.java`, `T/service/impl/ImportDraftServiceImpl.java`, `T/controller/ImportDraftController.java`, `T/dto/ImportDraftSummary.java`, `T/dto/DraftSavedResponse.java`
- Test: `TT/service/impl/ImportDraftServiceImplTest.java` (AC-004.1…AC-004.5, AC-004.7), `TT/controller/ImportDraftControllerTest.java`
**Dependencies:** Task 2
**Estimated size:** small

---

## Task 8: Web — Import Tickets: Jira tab and Drafts list

**Story:** STORY-001, STORY-004
**Feature:** jira-import-tickets-page
**Description:** Enable the Jira tab of `ImportTicketsPage` (D13 of `import-ticket-list` showed it disabled): Project select, Resolved from / to with MSG01 / MSG39, Pull from Jira → Import Preview; MSG04, MSG50 inline. Drafts list (Open, Delete with MSG42). MSW handlers.
**Files:**
- Create: `F/components/JiraPullForm.tsx`, `F/components/DraftsList.tsx`, `F/components/JiraPullForm.test.tsx`, `F/components/DraftsList.test.tsx`
- Modify: `F/pages/ImportTicketsPage.tsx`, `F/mocks/handlers.ts`
**Dependencies:** Task 1
**Estimated size:** medium

---

## Task 9: Web — Import Preview and popup for a Jira preview

**Story:** STORY-002, STORY-003, STORY-004
**Feature:** jira-import-preview-page
**Description:** `ImportPreviewPage` for a Jira preview: Summary with the date range and MSG35, Finish label and its disabled state (AC-002.10), MSG38 notice after Finish, Save as draft (MSG41), Cancel / leave with MSG37 (AC-002.11), 404 → back to Import Tickets (Edge 10). Popup: comments and the Jira link. Fill solution with MSG36 is the shared KAN-31 one.
**Files:**
- Modify: `F/pages/ImportPreviewPage.tsx`, `F/components/ImportTicketPreviewModal.tsx`, `F/mocks/handlers.ts`
- Test: `F/pages/JiraImportPreviewPage.test.tsx`
**Dependencies:** Task 1, Task 8, `import-ticket-list` `row-solution-popup`, `fill-solution-screen`
**Estimated size:** medium

---

## Task 10: Trace and end-to-end check

**Story:** STORY-001, STORY-002, STORY-003, STORY-004
**Feature:** jira-import-verify
**Description:** `bun harnessclaude/hooks/sdd.ts trace ticket-repository` shows a test for every AC; `mvn verify` and the frontend checks pass; one manual run against a real Jira site (service account scoped token, `JIRA_CLOUD_ID` in `.env`): pull a small range, write a Solution, Finish, and a Draft round trip — recorded in the session log.
**Files:**
- Test: none new (fill gaps found by `trace`)
**Dependencies:** Task 1–9, Task 11
**Estimated size:** small

---

## Task 11: Jira client — scoped token through the API gateway, and 429 / 503 retry

**Story:** STORY-001
**Feature:** jira-gateway-auth
**Description:** Change D2 of 2026-11-07 (RESEARCH-003). `JiraRestIssueSource`: base URL `https://api.atlassian.com/ex/jira/{cloudId}`, `Authorization: Bearer {token}`, no email; issue link stays `{siteUrl}/browse/{key}` (AC-001.12). Settings `JIRA_SITE_URL` (https only, trailing slash trimmed), `JIRA_CLOUD_ID`, `JIRA_API_TOKEN`; any of them empty → not configured (MSG50). Retry each GET on 429, or 503 with `Retry-After`, waiting `Retry-After` ≤ 5 s (429 without it = 1 s), at most 2 retries; above 5 s or still failing → `JiraUnavailableException` as today (AC-001.11, AC-001.7). Sleep through an injectable hook so tests run without waiting. Replace `JIRA_EMAIL` with `JIRA_CLOUD_ID` in `.env.example` with a comment on where to find the cloud id (`https://<site>.atlassian.net/_edge/tenant_info`). Existing `JiraRestIssueSourceTest` cases move to the gateway URL and Bearer header.
**Files:**
- Modify: `T/client/JiraRestIssueSource.java`, `.env.example`
- Test: `TT/client/JiraRestIssueSourceTest.java` (AC-001.7, AC-001.11, AC-001.12)
**Dependencies:** Task 3
**Estimated size:** small

## Task 12: Pull deadline — 60 s for every Jira call of one pull

**Story:** STORY-001
**Feature:** jira-pull-deadline
**Description:** Change of 2026-11-08 (PR #166 review, BUG-189). `JiraImportServiceImpl.read` sets one deadline, now + 60 s, and passes it with every page to `JiraIssueSource.resolvedIssues`. `JiraRestIssueSource` (now given the `Clock`) starts no request and waits for no retry past it, and throws `JiraUnavailableException("The pull took more than 60 seconds")`, so the pull answers MSG50 with a Failed batch as in AC-001.7 (AC-001.13). A request already running at the deadline still ends at its read timeout.
**Files:**
- Modify: `T/service/JiraIssueSource.java`, `T/client/JiraRestIssueSource.java`, `T/service/impl/JiraImportServiceImpl.java`
- Test: `TT/client/JiraRestIssueSourceTest.java`, `TT/service/impl/JiraImportServiceImplTest.java` (AC-001.13)
**Dependencies:** Task 11
**Estimated size:** small

## Task 13: Ticket status model — one state machine for submit, review and import

**Story:** STORY-005
**Feature:** ticket-status-model
**Description:** KAN-132, master design "Change 2026-11-13". `TicketStatus.canMoveTo` with the AC-005.1 table;
`Ticket.moveTo(to, at)` throws `BusinessRuleException("MSG06")` on a forbidden change; `applyReview` goes through it.
New `TicketTransitions.move(tickets, to, at)`: check all, then move all, then publish one `TicketsPublishedEvent`
(to PUBLISHED) or new `TicketsUnpublishedEvent` (to UNPUBLISHED) with every id. `TicketDecisionServiceImpl.decide`
uses it, so review Approve publishes `TicketsPublishedEvent`. Bulk SQL (expiry, send back) and import Finish unchanged.
**Files:**
- Create: `T/service/TicketTransitions.java`, `T/event/TicketsUnpublishedEvent.java`
- Modify: `T/entity/TicketStatus.java`, `T/entity/Ticket.java`, `T/service/impl/TicketDecisionServiceImpl.java`, `T/event/TicketsPublishedEvent.java`
- Test: `TT/entity/TicketStatusTest.java` (AC-005.1 every one of the 36 pairs, AC-005.2 `Ticket.imported`),
  `TT/service/TicketStatusTransitionsTest.java` (AC-005.3 mixed batch → MSG06 and no status changed, AC-005.4 one
  event with all ids, AC-005.5 one unpublished event, no event for REJECTED/CHANGES_REQUESTED); existing
  `review/TicketDecisionIT` and `ImportConfirm*` tests stay green.
**Dependencies:** none
**Estimated size:** small
