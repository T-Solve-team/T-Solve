---
module: tracker
date: 2026-10-21
total_tasks: 9
owner: Dev
---

# Tasks: tracker — iteration 1

Spec: `artifacts/03-modules/tracker/spec.md` · Design: `artifacts/04-plans/tracker/design.md`.
Base package `backend/src/main/java/vn/tsolve/tracker/` (below: `tracker/`), tests in
`backend/src/test/java/vn/tsolve/tracker/`. Every test method name cites its AC id (e.g.
`rejectsWrongAudience_AC_001_4`). Each task ends with `mvn spotless:apply` and `mvn verify` green.

> **Update 2026-11-01 — read first.** One database per tenant (DB design 31/10); the tracker runs inside one
> tenant database and writes no background job (user). Dropped tasks, kept as numbers so ids stay stable:
> Task 1 (spike — its checks stay in step 4 below), Task 4 (site registration → discovery / Workspace Setup),
> Task 5 (token cipher), Task 8 (worker), Task 9 (triage + MSG80), Task 10 (AI draft on Done), Task 14 (clean-up).
> Their features in `progress.json` stay `pending` and are not worked on.
> Code from the earlier slice that these tasks remove: `IssueEvent*`, `JiraSite*`, `WorkspaceContext`,
> `WorkspaceTenantResolver`, `JiraSiteLookup`, `TokenCipher`, `TokenUnreadableException`, changeset
> `002-jira-issue-events` (design, File Structure).
>
> **Create-ticket slice (KAN-22), in this order:**
> 1. Task 2 — schema, `project` module, `SuggestionResult`, OpenAPI; remove the tenant/event code.
> 2. Task 3 — FIT chain without the site lookup.
> 3. Task 6 — receive `issue-created` into `suggestion_result`.
> 4. Manifest without `appSystemToken` (quick change), deploy, create an issue in a T-Solve Project → one
>    `suggestion_result` row; check the real payload has `issue.fields.project.key`, that a retry gives no second
>    row, and the FIT claims. A difference from the design is fixed in the spec/design before going on.

## Task 2: Contract, schema and module skeleton

**Story:** STORY-003
**Feature:** tracker-foundation
**Description:** OpenAPI: `issue-created` body = `issue.key` + `issue.fields.project.key` (+ `timestamp`), no
`x-forge-oauth-system` header, remove `issue-updated`; run `npm run generate:api`. Replace changeset
`002-jira-issue-events` with `002-tenant-project-suggestion` (design Data Model) and its include line. Minimal
`project` module: `Project` entity (mapped columns only), `ProjectStatus`, `ProjectRepository`,
`ProjectService.findActiveIdByJiraProjectKey` + impl. Tracker: `SuggestionResult` entity and repository.
Remove `TSOLVE_TOKEN_KEY` from `.env.example` and `TrackerProperties`. Delete the tenant/event/token classes
and their tests (update note above).
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `.env.example`, `backend/src/main/resources/db/changelog/db.changelog-master.xml`, `common/config/TrackerProperties.java`
- Create: `backend/src/main/resources/db/changelog/changes/002-tenant-project-suggestion.sql`, `project/entity/*`, `project/repository/ProjectRepository.java`, `project/service/ProjectService.java`, `project/service/impl/ProjectServiceImpl.java`, `tracker/entity/SuggestionResult.java`, `tracker/repository/SuggestionResultRepository.java`
- Delete: `002-jira-issue-events.sql`, `tracker/entity/IssueEvent*`, `tracker/entity/JiraSite.java`, `tracker/repository/IssueEventRepository.java`, `tracker/repository/JiraSiteRepository.java`, `tracker/service/impl/TokenCipher.java`, `tracker/service/impl/JiraSiteServiceImpl.java`, `tracker/exception/TokenUnreadableException.java`, `common/security/WorkspaceContext.java`, `common/config/WorkspaceTenantResolver.java`, `common/security/JiraSiteLookup.java` and their tests
- Test: `project/service/impl/ProjectServiceImplTest.java` (ACTIVE only; REMOVED → empty)
**Dependencies:** none
**Estimated size:** medium

---

## Task 3: FIT verification chain

**Story:** STORY-001
**Feature:** fit-verification
**Description:** Keep `JiraAppSecurityConfig` (`@Order(1)`, `/api/v1/jira-app/**`, stateless), `FitJwtDecoderConfig`
(RS256 only, 60 s skew), `FitValidators` (issuer, audience = app id, `context.cloudId` present → else 401).
`FitAuthenticationConverter` builds `JiraAppPrincipal(cloudId, accountId)` + `ROLE_JIRA_APP` with no site
lookup and no 403 path (AC-001.6 removed). ProblemDetail MSG08 for 401, MSG06 for 503 (JWKS down). Never log
tokens. Edge cases 1–7.
**Files:**
- Modify: `common/security/FitAuthenticationConverter.java`, `common/security/JiraAppPrincipal.java`, `common/security/JiraAppSecurityConfig.java`
- Test: `common/security/FitValidatorsTest.java`, `common/security/JiraAppSecurityConfigTest.java` (remove the unregistered-site test)
**Dependencies:** Task 2
**Estimated size:** small

---

## Task 6: Receive "issue created" events

**Story:** STORY-003
**Feature:** receive-issue-events
**Description:** `JiraAppEventController` POST `issue-created` (`@PreAuthorize("hasRole('JIRA_APP')")`, no system
token header) → `IssueCreatedService.receive(payload)`: valid body (AC-003.6, issue key ≤ 50) →
`ProjectService.findActiveIdByJiraProjectKey`; none → 202 nothing stored (AC-003.8); else
`saveAndFlush(new SuggestionResult(projectId, issueKey))` → 202 (AC-003.1); only `uq_suggestion_issue`
→ 202 (AC-003.4), any other integrity error rethrown. Log issue key and project key only. Edge cases 7, 11–17.
**Files:**
- Modify: `tracker/controller/JiraAppEventController.java`, `tracker/dto/IssueEventPayload.java` → `IssueCreatedPayload.java`
- Create: `tracker/service/IssueCreatedService.java`, `tracker/service/impl/IssueCreatedServiceImpl.java`
- Delete: `tracker/service/IssueEventService.java`, `tracker/service/impl/IssueEventServiceImpl.java`
- Test: `tracker/service/impl/IssueCreatedServiceImplTest.java`, `tracker/controller/JiraAppEventControllerTest.java`
**Dependencies:** Tasks 2, 3
**Estimated size:** small

---

## Task 7: Jira client and ADF text

**Story:** STORY-007
**Feature:** jira-client
**Description:** `TicketTrackerClient` + `TicketTrackerClientFactory` + `JiraClient` (`RestClient`, connect 5 s /
read 10 s, no redirects, `apiBaseUrl` host allowlist) used by panel calls with the request's system token:
`getIssue` (summary, description, reporter accountId), `getComments` (all pages), `addComment` (ADF). Never keep
response bodies. `AdfTextExtractor` (paragraphs, lists, code, headings, mentions as display text; media skipped;
null → ""). Edge case 36.
**Files:**
- Create: `tracker/service/TicketTrackerClientFactory.java`, `tracker/client/JiraClient.java`, `tracker/client/AdfTextExtractor.java`, `tracker/client/TicketTrackerClient.java`, `tracker/dto/TrackerIssue.java`, `tracker/dto/TrackerComment.java`
- Test: `tracker/client/JiraClientTest.java` (MockRestServiceServer), `tracker/client/AdfTextExtractorTest.java`
**Dependencies:** Task 2
**Estimated size:** medium

---

## Task 11: Issue Panel data and search again

**Story:** STORY-007
**Feature:** issue-panel
**Description:** `JiraAppIssueController` GET panel + POST refresh → `JiraPanelService`: identity (unlinked →
MSG62 state, deactivated → MSG10), project (not connected), search (suggestions or MSG76), ticket (Solution Form
state MSG64/65/67/77), MSG81 question for the reporter from `suggestion_result` (STORY-010); refresh reads current
summary/description with the request's system token (never stored); 20 s budget → 503 MSG06; `issueKey`
pattern → 400. Edge cases 28–30, token-not-persisted test.
**Files:**
- Create: `tracker/controller/JiraAppIssueController.java`, `tracker/service/JiraPanelService.java`, `tracker/service/impl/JiraPanelServiceImpl.java`, `tracker/dto/PanelResponse.java`
- Test: `tracker/service/impl/JiraPanelServiceImplTest.java`, `tracker/controller/JiraAppIssueControllerTest.java`
**Dependencies:** Tasks 3, 7
**Estimated size:** medium

---

## Task 12: Solution Form actions

**Story:** STORY-008
**Feature:** solution-form
**Description:** PUT solution-draft, POST solution (Finish or close with empty Solution — MSG70) → ticket service
with the linked T-Solve user as author; unlinked/deactivated → 403 MSG62 without calling it; its errors returned
unchanged. Edge cases 31–32.
**Files:**
- Modify: `tracker/controller/JiraAppIssueController.java`, `tracker/service/JiraPanelService.java`, `tracker/service/impl/JiraPanelServiceImpl.java`
- Create: `tracker/dto/SolutionDraftRequest.java`, `tracker/dto/SubmitSolutionRequest.java`
- Test: `tracker/service/impl/JiraPanelServiceSolutionTest.java`
**Dependencies:** Task 11
**Estimated size:** small

---

## Task 13: Reporter answers the suggestion

**Story:** STORY-010
**Feature:** suggestion-answer
**Description:** MSG81 in panel data when the principal is the reporter and the issue's `suggestion_result` has a
suggested solution and no answer (even if unlinked); POST suggestion-answer: reporter check via Jira read with
the request's system token (403 MSG08 otherwise), unanswered check (409 MSG06), store `reporter_answer` and its
time in `suggestion_result` (needs `answered_at` from the DB owner), return MSG82/MSG83. Edge cases 33–34.
**Files:**
- Modify: `tracker/controller/JiraAppIssueController.java`, `tracker/service/impl/JiraPanelServiceImpl.java`, `tracker/entity/SuggestionResult.java`
- Create: `tracker/dto/SuggestionAnswerRequest.java`, `tracker/dto/SuggestionAnswerResponse.java`
- Test: `tracker/service/impl/JiraPanelServiceAnswerTest.java`
**Dependencies:** Task 11
**Estimated size:** small

---

## Task 15: Suggestion job for a new issue (KAN-22)

**Story:** STORY-011
**Feature:** suggestion-job
**Description:** Branch `feat/KAN-22-tracker-suggestion-job`. First confirm with `forge tunnel` that the real
`avi:jira:created:issue` payload has `issue.fields.summary` and `issue.fields.description` (design, STORY-011).
OpenAPI `IssueCreatedPayload` gets optional `summary`, `description` (ADF object) and `labels`; run
`npm run generate:api`. `receive` enqueues one `SuggestionJobRequest` through `SuggestionJobQueue` only after a new
row is saved (AC-011.1, 011.2, 011.8). `SuggestionJobServiceImpl.run`: guard (AC-011.7), call `SuggestionSearch`
(AC-011.3), store sources (triage's `sources_used` format) and suggested solution with a conditional update
(AC-011.4), nothing on no suggestion (AC-011.5) or on failure, logging issue key + error type only (AC-011.6).
Stand-ins: `InProcessSuggestionJobQueue` on its own bounded pool, `StubSuggestionSearch` from
`TSOLVE_STUB_SUGGESTION`. No JobRunr, no `pom.xml` change (worker KAN-161). Edge cases 37–44.
**Files:**
- Modify: `tracker/dto/IssueCreatedPayload.java`, `tracker/service/impl/IssueCreatedServiceImpl.java`, `tracker/entity/SuggestionResult.java`, `tracker/repository/SuggestionResultRepository.java`, `backend/src/main/resources/application.yml`, `.env.example`, `frontend/openapi/tsolve-api.yaml` (+ generated client)
- Create: `tracker/dto/SuggestionJobRequest.java`, `tracker/dto/IssueSuggestion.java`, `tracker/dto/SuggestionSource.java`, `tracker/service/SuggestionJobQueue.java`, `tracker/service/SuggestionSearch.java`, `tracker/service/SuggestionJobService.java`, `tracker/service/impl/SuggestionJobServiceImpl.java`, `tracker/service/impl/InProcessSuggestionJobQueue.java`, `tracker/service/impl/StubSuggestionSearch.java`; `tracker/client/AdfTextExtractor.java` filled in (was empty). No `SuggestionJobConfig` (design)
- Test: `tracker/service/impl/IssueCreatedServiceImplTest.java` (AC-011.1, 011.2, 011.8), `tracker/service/impl/SuggestionJobServiceImplTest.java` (AC-011.3–011.7), `tracker/service/impl/StubSuggestionSearchTest.java`, `tracker/service/impl/InProcessSuggestionJobQueueTest.java`, `tracker/controller/JiraAppEventControllerTest.java` (payload with the new fields), `tracker/repository/SuggestionResultRepositoryIT.java` (conditional fill), `tracker/client/AdfTextExtractorTest.java`
**Dependencies:** Task 6 (merged in #98)
**Estimated size:** medium

---

## Task 16: Run the suggestion job on the worker (KAN-22, KAN-102)

**Story:** STORY-011
**Feature:** suggestion-job-worker
**Description:** Changeset `017-tracker-suggestion-job-input` (job_ref, issue_summary, issue_description). `receive`
stores the text and a `job_ref`, enqueues `SuggestionJobRequest(jobRef, issueKey)` (AC-011.1). `SuggestionJobService
.run(jobRef)` reads the text from the row and throws `SuggestionJobFailedException` (key + error type, no cause) on any
failure (AC-011.6). `SuggestionJobHandler` ("tracker.suggestion") runs it for the worker; `WorkerSuggestionJobQueue`
enqueues through `TenantJobService`, binding `TSOLVE_TRACKER_DEV_WORKSPACE_ID` when no Workspace is bound, and never
throws; `InProcessSuggestionJobQueue` only while `tsolve.worker.enabled` is false (AC-011.9). Edge cases 45–48.
**Files:** design "Change 2026-11-07: run the job on the worker".
**Test:** `IssueCreatedServiceImplTest`, `SuggestionJobServiceImplTest`, `SuggestionJobHandlerTest`,
`WorkerSuggestionJobQueueTest`, `InProcessSuggestionJobQueueTest`, `SuggestionResultRepositoryIT`.
**Dependencies:** Task 15, worker KAN-102 (merged)
**Estimated size:** medium
