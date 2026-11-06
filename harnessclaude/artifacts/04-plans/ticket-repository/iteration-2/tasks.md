---
module: ticket-repository
date: 2026-11-07
total_tasks: 10
owner: Dev
---

# Tasks: ticket-repository — Iteration 2 (KAN-45 Jira import)

Spec: `artifacts/03-modules/ticket-repository/spec.md`
Design: `artifacts/04-plans/ticket-repository/iteration-2/design.md` (Master: `artifacts/04-plans/ticket-repository/design.md`)

Paths are relative to the repo root. `T` = `backend/src/main/java/vn/tsolve/ticket`, `TT` = `backend/src/test/java/vn/tsolve/ticket`,
`F` = `frontend/src/features/ticket-import`. Every test method name cites its AC id (`…_AC_001_2`).

## Task 1: OpenAPI contract for Jira import and Drafts

**Story:** STORY-001, STORY-002, STORY-003, STORY-004
**Feature:** jira-import-contract
**Description:** Add every endpoint of the spec's Interface Contracts (pull, issues page, issue detail, solutions, remove-issues, finish, draft, drafts list, delete draft) with their schemas and ProblemDetail errors (400/403/404/409/500/502, `code` = MSGxx). Run `npm run generate:api` in `frontend/` and commit the regenerated client.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`
- Modify: `frontend/src/shared/api/generated/**` (generated only)
**Dependencies:** none
**Estimated size:** small

---

## Task 2: Schema, entities, repositories, Project Jira key

**Story:** STORY-001, STORY-004
**Feature:** jira-import-schema
**Description:** Liquibase `008-ticket-jira-import.sql` exactly as the design's Data Model. Entity `ImportPreviewIssue` (comments as JSONB list), new fields on `ImportPreview`, repository queries: issues page by position, `findExistingTicketIds`, caller's non-Draft preview, caller's Drafts, `findByIdAndOwnerForUpdate`. `ProjectService.findJiraProjectKey(long)` + `ProjectAccess.jiraProjectKey(long)`. Update `PreviewCleanupJob` and the CSV upload's "replace previous preview" to skip Drafts (AC-004.6). Repository tests on Testcontainers Postgres.
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/008-ticket-jira-import.sql`, `T/entity/ImportPreviewIssue.java`, `T/repository/ImportPreviewIssueRepository.java`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`, `T/entity/ImportPreview.java`, `T/repository/ImportPreviewRepository.java`, `T/repository/TicketRepository.java`, `T/service/ProjectAccess.java`, `T/service/impl/StubProjectAccess.java`, `T/service/impl/PreviewCleanupJob.java`, `T/service/impl/CsvImportServiceImpl.java`, `backend/src/main/java/vn/tsolve/project/service/ProjectService.java`, `backend/src/main/java/vn/tsolve/project/service/impl/ProjectServiceImpl.java`
- Test: `TT/repository/ImportPreviewIssueRepositoryTest.java`, `TT/service/impl/PreviewCleanupJobTest.java` (AC-004.6)
**Dependencies:** none
**Estimated size:** medium

---

## Task 3: Jira client — `JiraIssueSource` and `JiraRestIssueSource`

**Story:** STORY-001
**Feature:** jira-issue-source
**Description:** Port `JiraIssueSource.resolvedIssues(projectKey, from, to)` returning issues oldest first page by page (`JiraIssue` record: key, summary, description text, assignee, resolvedAt, url, comments). `JiraRestIssueSource` with `RestClient` (10 s / 30 s timeouts, basic auth), `search/jql` + `nextPageToken`, extra comment pages, Vietnam-time re-check of `resolutiondate`, `AdfText`. `JiraProperties` (`tsolve.jira.site-url|email|api-token`, no defaults, trailing slash trimmed, non-https rejected). Any 4xx/5xx, timeout or missing config → `JiraUnavailableException` whose message holds only status / error type. Add the keys to `application.yml` and `.env.example`. Tests with `MockRestServiceServer`: paging, comment paging, ADF cases, time-zone boundary, each error → exception, token absent from exception and logs (Edge 1, 2, 4, 5, 16).
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
**Description:** `JiraImportService.pull(user, projectId, from, to)` per the design's Pull flow: MSG01 / MSG39 before any call, 403 checks, Jira key, duplicate filter per page, 100 kept + "more" (MSG35), 20-page scan cap (Edge 3), MSG04, replace the caller's non-Draft preview, insert preview + issues; `JiraUnavailableException` → Failed batch (`ImportBatchRecorder`) + MSG50 (502). `JiraImportController` `POST /api/v1/imports/jira`, PM role. Unit tests with a fake `JiraIssueSource`, controller slice test for status codes.
**Files:**
- Create: `T/service/JiraImportService.java`, `T/service/impl/JiraImportServiceImpl.java`, `T/controller/JiraImportController.java`, `T/dto/JiraPullRequest.java`, `T/dto/JiraImportPreviewResponse.java`, `T/dto/JiraPreviewIssueSummary.java`, `T/exception/InvalidDateRangeException.java`
- Modify: `T/service/impl/ImportBatchRecorder.java`, the global exception handler mapping (MSG39, MSG50)
- Test: `TT/service/impl/JiraImportServiceImplTest.java` (AC-001.1…AC-001.10), `TT/controller/JiraImportControllerTest.java`
**Dependencies:** Task 2, Task 3
**Estimated size:** medium

---

## Task 5: Preview — list, open, apply Solution, remove issues

**Story:** STORY-002
**Feature:** jira-preview
**Description:** `JiraPreviewService`: issues page (≤ 100, position order, counts), issue detail with comments, apply Solution to one or more keys (shared `SolutionRules` extracted from `CsvRowValidator`; MSG01 lists missing sections, MSG02 > 10,000 total; keys no longer in the preview ignored — Edge 8), remove issues (hard delete). 404 for another user's / missing / CSV preview, 403 when the Project is no longer managed. Endpoints on `JiraImportController`.
**Files:**
- Create: `T/service/JiraPreviewService.java`, `T/service/impl/JiraPreviewServiceImpl.java`, `T/service/impl/SolutionRules.java`, `T/dto/JiraPreviewIssueResponse.java`, `T/dto/ApplySolutionRequest.java`, `T/dto/RemoveIssuesRequest.java`
- Modify: `T/controller/JiraImportController.java`, `T/csv/CsvRowValidator.java` (use `SolutionRules`)
- Test: `TT/service/impl/JiraPreviewServiceImplTest.java` (AC-002.1…AC-002.5, AC-002.7…AC-002.9), `TT/service/impl/SolutionRulesTest.java`
**Dependencies:** Task 4
**Estimated size:** medium

---

## Task 6: Finish — publish the Tickets

**Story:** STORY-003
**Feature:** jira-finish
**Description:** `JiraFinishService.finish(user, previewId)` per the design: pre-checks (404, 409 MSG01, 409 MSG40, 403), one transaction with `FOR UPDATE`, skip keys already Tickets (MSG38 with keys), unique-violation retry once (Edge 6), Tickets `JIRA_IMPORT` + APPROVE decisions + Completed batch, title cut to 200 (Edge 13), delete preview (Draft included), `TicketsPublishedEvent` after commit when ≥ 1 Ticket; failure → rollback, keep preview, Failed batch, MSG33. Endpoint `POST …/finish`. Integration test on Testcontainers for the concurrency cases (Edge 7) and Draft-of-inactive-Project (Edge 11).
**Files:**
- Create: `T/service/JiraFinishService.java`, `T/service/impl/JiraFinishServiceImpl.java`, `T/dto/JiraImportResultResponse.java`, `T/exception/SolutionMissingException.java`
- Modify: `T/controller/JiraImportController.java`
- Test: `TT/service/impl/JiraFinishServiceImplTest.java` (AC-003.1…AC-003.10), `TT/service/impl/JiraFinishConcurrencyIT.java`
**Dependencies:** Task 5
**Estimated size:** medium

---

## Task 7: Drafts — save, list, reopen, delete

**Story:** STORY-004
**Feature:** jira-drafts
**Description:** `ImportDraftService`: save (`draft_saved_at = now`, no validation, MSG41; saving again updates the same Draft), list the caller's Drafts newest first (type, Project, item count, saved at), delete (404 for another user's — Edge 12). Reopen = the existing preview endpoints (no Jira call). `ImportDraftController`: `POST /imports/previews/{id}/draft`, `GET /imports/drafts`, `DELETE /imports/drafts/{id}`.
**Files:**
- Create: `T/service/ImportDraftService.java`, `T/service/impl/ImportDraftServiceImpl.java`, `T/controller/ImportDraftController.java`, `T/dto/ImportDraftSummary.java`, `T/dto/DraftSavedResponse.java`
- Test: `TT/service/impl/ImportDraftServiceImplTest.java` (AC-004.1…AC-004.7), `TT/controller/ImportDraftControllerTest.java`
**Dependencies:** Task 4
**Estimated size:** small

---

## Task 8: Web — Import Tickets: Jira tab and Drafts list

**Story:** STORY-001, STORY-004
**Feature:** jira-import-tickets-page
**Description:** `ImportTicketsPage` with the Jira tab (Project select of managed Active Projects, Resolved from / to with MSG01 / MSG39 under the fields, Pull from Jira → Import Preview; MSG04, MSG50 inline) and the Drafts list (Open → Import Preview, Delete with MSG42). Query hooks over the generated client, MSW handlers, component tests citing AC ids.
**Files:**
- Modify: `F/pages/ImportTicketsPage.tsx`, `F/mocks/handlers.ts`, `F/index.ts`
- Create: `F/components/JiraPullForm.tsx`, `F/components/DraftsList.tsx`, `F/api/jiraImport.ts`, `F/api/drafts.ts`, tests next to each component
**Dependencies:** Task 1
**Estimated size:** medium

---

## Task 9: Web — Import Preview and Import Ticket Preview popup (Jira)

**Story:** STORY-002, STORY-003, STORY-004
**Feature:** jira-import-preview-page
**Description:** `ImportPreviewPage` in Jira mode: summary (counts, MSG35), issues table with checkboxes and paging, Preview / Fill solution (MSG36 when overwriting) / Remove, `ImportTicketPreviewDialog` (read-only snapshot, comments, source link, `SolutionFields` with Workaround optional, Apply), Finish disabled until every issue has a Solution (AC-002.10), result toast MSG32 / inline MSG38 / MSG40 / MSG33, Save as draft (MSG41), Cancel / leave with MSG37 (AC-002.11), 404 → back to Import Tickets (Edge 10).
**Files:**
- Modify: `F/pages/ImportPreviewPage.tsx`, `F/mocks/handlers.ts`
- Create: `F/components/JiraIssuesTable.tsx`, `F/components/ImportTicketPreviewDialog.tsx`, `F/components/SolutionFields.tsx`, tests next to each component
**Dependencies:** Task 1, Task 8
**Estimated size:** medium

---

## Task 10: Trace and end-to-end check

**Story:** STORY-001, STORY-002, STORY-003, STORY-004
**Feature:** jira-import-verify
**Description:** `bun harnessclaude/hooks/sdd.ts trace ticket-repository` shows a test for every AC; `mvn verify` and the frontend checks pass; one manual run against a real Jira site (service account in `.env`) pulling a small range, writing a Solution, Finish, and a Draft round trip — recorded in the session log.
**Files:**
- Test: none new (fill gaps found by `trace`)
**Dependencies:** Task 1–9
**Estimated size:** small
