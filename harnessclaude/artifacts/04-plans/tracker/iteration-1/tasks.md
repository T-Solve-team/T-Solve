---
module: tracker
date: 2026-10-21
total_tasks: 12
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

## Task 17: UUID keys for suggestion_result (KAN-22)

**Story:** STORY-003, STORY-011
**Feature:** suggestion-uuid-keys
**Description:** Changeset `019-tracker-suggestion-uuid-keys`: `suggestion_id` becomes a `UUID` key, `project_id` a
`UUID` that references `project (public_id)`, `job_ref` is dropped; existing rows keep their data (edge case 49).
`ProjectService` returns the Project's `public_id`; the suggestion job carries `suggestion_id` and loads the row by it
(AC-011.1, AC-011.7, edge case 50); `SuggestionSearch.suggest` takes the Project UUID. Triage's read-only
`SuggestionResultRecord` and its history map the same columns as `UUID`. No acceptance criterion changes.
**Files:** design "Change 2026-11-07: UUID keys for the tracker's table".
**Test:** `IssueCreatedServiceImplTest`, `SuggestionJobServiceImplTest`, `SuggestionJobHandlerTest`,
`SuggestionResultRepositoryIT` (UUID columns and the foreign key to `project (public_id)`), triage
`SuggestionResultRecordRepositoryIT` and
`SuggestionResultServiceImplTest`. Edge case 49 (existing rows) checked by running `002`, `008`, `017`, then `019`
on a scratch PostgreSQL with two rows: both kept their text and suggestion and joined their Project by `public_id`.
**Dependencies:** Task 16
**Estimated size:** medium

## Task 18: Post the suggestion as the MSG80 comment (KAN-131)

**Story:** STORY-012, STORY-011 (AC-011.7)
**Feature:** suggestion-comment
**Description:** Changeset `020-tracker-suggestion-comment` (`jira_comment_id`, `commented_at`). After the suggestion
job stores a suggestion — or when it runs for a row that has a suggestion and no comment id — it posts MSG80 (without
"Related documents", naming no source) on the issue with the Workspace's Jira service account, finds a comment that
was posted but not recorded instead of posting twice, and records the comment id and time. Jira 5xx/429/timeout fail
the job for a retry; 401/403/404 or no service account end it without a retry. `JiraServiceAccount` port with a
stand-in reading `JIRA_SITE_URL`, `JIRA_SERVICE_ACCOUNT_EMAIL`, `JIRA_SERVICE_ACCOUNT_TOKEN`. Edge cases 51–57.
**Files:** design "STORY-012: Post the suggestion as the MSG80 comment".
**Test:** `SuggestionCommentServiceImplTest`, `Msg80Test`, `JiraClientTest` (ADF body, Basic auth, status mapping, no
secret in errors), `SuggestionJobServiceImplTest` (AC-011.7, AC-012.3, AC-012.6), `SuggestionResultRepositoryIT`
(`markCommented` never overwrites), `ConfiguredJiraServiceAccountTest`.
**Dependencies:** Task 16, Task 17
**Estimated size:** medium

## Task 19: Read a Jira project and its statuses (KAN-127)

**Story:** STORY-013
**Feature:** jira-project-read
**Description:** Dependency 1 for KAN-127's Project connection/create flow; the body belongs to this tracker plan, not the Project task count. Branch `feat/KAN-127-project-integration-add-project`. Write failing JiraClient HTTP tests and Mockito service tests first, then implement the read-only `TrackerProjectService.findProject(String key)` boundary and its implementation. It obtains `JiraServiceAccount.current()`, reads the project, then reads statuses using its canonical key. `TicketTrackerClient.findProject(JiraAccount, String)` returns Optional<String>, with project 404 mapped locally to empty (D38; tracker spec interface row corrected); `projectStatuses` returns List<TrackerStatus>. Keep the existing status-only exception mapping for other failures. Flatten issue-type statuses; validate required key/id/name; service deduplicates by id in first-seen order, first name/category wins. Missing category means done=false; only category key `done` means true. No new REST endpoint, persistence, caller-to-Project lookup, outer transaction or credential source. FOUND carries canonical key/statuses; other outcomes have null key and empty statuses. No raw JSON or exception crosses the service boundary. Existing Basic 5s-connect/10s-read setup stays. Existing comment operations keep their semantics, including their 404 mapping.
**Files:**
- Modify: `backend/src/main/java/vn/tsolve/tracker/client/TicketTrackerClient.java`
- Modify: `backend/src/main/java/vn/tsolve/tracker/client/JiraClient.java`
- Create: `backend/src/main/java/vn/tsolve/tracker/service/TrackerProjectService.java`
- Create: `backend/src/main/java/vn/tsolve/tracker/service/impl/TrackerProjectServiceImpl.java`
- Create: `backend/src/main/java/vn/tsolve/tracker/dto/TrackerProjectLookup.java` (nested Outcome enum)
- Create: `backend/src/main/java/vn/tsolve/tracker/dto/TrackerStatus.java`
- Test (Modify): `backend/src/test/java/vn/tsolve/tracker/client/JiraClientTest.java`
- Test (Create): `backend/src/test/java/vn/tsolve/tracker/service/impl/TrackerProjectServiceImplTest.java`
**Tests** (tracker master edge rows 58–65):
- AC-013.1: MockRestServiceServer on the existing RestClient.Builder constructor asserts exact GET project/status URLs, Accept and Basic auth; canonical key feeds the second read; no HTTP mocks/containers added (row 58). Service Mockito verifies ordered calls and FOUND payload.
- AC-013.2/AC-013.3: repeated id across issue types returns once, first name/category wins; Done key true, other/missing category false; status id/name are exact nonblank strings (row 59).
- AC-013.4: project 404 → Optional.empty → NOT_FOUND; statuses 404 → existing JiraRejectedException(404) → NOT_FOUND, no partial result (row 60).
- AC-013.5: absent account → DENIED, verifyNoInteractions(client), no persistence (row 60).
- AC-013.6: each read independently returns 401/403/other 4xx except 404/429 → DENIED. Assert service boundary returns an outcome, not a client exception (row 60).
- AC-013.7: each read's 429, 5xx, SocketTimeoutException and connection/I/O failure → UNAVAILABLE with empty payload; 429 precedes generic 4xx. Inject transport failures through MockRestServiceServer rather than waiting for real network timeouts (row 61).
- AC-013.8: missing/blank/non-text canonical key, null/non-array/unreadable statuses body and missing/blank/non-text status id/name → UNAVAILABLE, no partial FOUND (row 62).
- AC-013.9: valid empty statuses array → FOUND with empty list (row 63).
- AC-013.10: retain existing 5-second connect/10-second read configuration, Basic header and same configured account port; no test sleeps for 10 seconds (row 58).
- AC-013.11: every failed project read, including empty/malformed, has no statuses request; fail test on unexpected server interaction (row 64).
- AC-013.12/AC-013.13: capture success/failure service log events and inspect returned DTO/client exception metadata: only key/status/outcome, never account email/token/Jira body; no persisted writes and no Project service dependency. Test synthetic secret markers in Jira error bodies for both reads. Retain all existing comment tests (row 65).
**Verify:** in `backend/`, `mvn spotless:apply` and `mvn verify`; `bun harnessclaude/hooks/sdd.ts check tracker` from root and STORY-013 trace using tracker paths. After checks, report feature/files/results and proposed `feat(tracker): read Jira projects and statuses (KAN-127)` with STORY-013 body and AI Co-Authored-By trailer; stop for user commit agreement before the next feature. One feature plus its harness commit, no push/merge/PR without request. Current tracker spec must be approved separately before code; this planning run performs no approval/status operation.
**Dependencies:** Task 18.
**Estimated size:** medium.
