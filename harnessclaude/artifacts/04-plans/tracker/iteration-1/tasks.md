---
module: tracker
date: 2026-10-21
total_tasks: 13
owner: Dev
---

# Tasks: tracker — iteration 1

Spec: `artifacts/03-modules/tracker/spec.md` · Design: `artifacts/04-plans/tracker/iteration-1/design.md`
(master: `artifacts/04-plans/tracker/design.md`). Base package `backend/src/main/java/vn/tsolve/tracker/`
(below: `tracker/`), tests in `backend/src/test/java/vn/tsolve/tracker/`. Every test method name cites its AC id
(e.g. `rejectsWrongAudience_AC_001_4`). Each task ends with `mvn spotless:apply` and `mvn verify` green.

> **Update 2026-10-22 — read first.**
> - **File locations:** the design's File Structure wins over the paths listed in the tasks below. Security
>   config, FIT validators, `JiraAppPrincipal` → `common/security/`; `TrackerProperties` → `common/config/`;
>   `TokenCipher` → `tracker/service/impl/`; tracker exceptions → `tracker/exception/`; `TicketTrackerClient`
>   → `tracker/client/`. No `service/port/` package: other modules are called through their own `service/`
>   interfaces, agreed with each owner before Tasks 8–9.
> - **Branches and Jira:** one branch per Jira issue (`<type>/KAN-<n>-<module>-<desc>`). KAN-22 (ticket created):
>   Tasks 2–9 (+ the Task 1 checks). KAN-23 (ticket Done): Tasks 10–14. Sub-tasks may split these further.
> - **Task 1 spike is dropped as a separate step** (user, 2026-10-22). Its checks move into slice step 4 below:
>   the first real Forge delivery to the receive endpoint confirms the header, FIT claims, retry and
>   `timestamp`; a difference from the design is fixed in the spec/design before going on.
> - **Create-ticket slice (KAN-22), in this order** — each step ends runnable:
>   1. Task 2, only what receiving needs: the `issue-created` path in OpenAPI, the changeset (`jira_site`,
>      `issue_event`), entities, repositories, `TrackerProperties`, `.env.example`. **Multi-tenancy (2026-10-27):**
>      `WorkspaceContext` + `WorkspaceTenantResolver` in `common/`, `@TenantId workspaceId` on both entities,
>      `issue_event.workspace_id`; tests prove inserts take the current Workspace and queries see only it
>      (AC-003.7, AC-004.9) — design "Workspace per transaction".
>   2. Task 3 (FIT). During development the site row is inserted by SQL; Task 4 comes later.
>   3. Task 5 (`TokenCipher`) + Task 6 (receive events, `issue-created` only first).
>   4. `jira-app/manifest.yml` trigger to the endpoint (quick change), deploy, create a ticket → `RECEIVED` row;
>      run the Task 1 checks.
>   5. Task 7 (`getIssue`, `addComment` first) + Task 8 (worker).
>   6. Task 9 (triage + MSG80) once the triage interface is agreed.

## Task 1: Spike — verify Forge Remote behaviour

**Story:** STORY-003
**Feature:** forge-remote-spike
**Description:** On the Forge dev site, point `avi:jira:created:issue` and `avi:jira:updated:issue` triggers and
one panel call at a throw-away endpoint (local server through a tunnel) that logs only header names, FIT claim
names/values that are ids (`iss`, `aud`, `app.id`, `context.cloudId`, `apiBaseUrl` host, `exp`), payload top-level
keys, `timestamp`, `issue.id`/`key`, changelog field ids — no content. Confirm: system token header present,
retry on non-2xx, `timestamp` identical across retries, `GET {site}/_edge/tenant_info` returns `cloudId`.
Record findings in `harnessclaude/artifacts/01-research/RESEARCH-001-forge-remote.md`; if any differ from the
design, stop and change spec/design first (risk review). Agree the port signatures with the owners of
`project`, `identity`, `triage`, `search`, `ticket` and the filter-chain order with `identity`; note them there.
**Files:**
- Create: `harnessclaude/artifacts/01-research/RESEARCH-001-forge-remote.md`
**Dependencies:** none (needs the Forge app from `jira-app/AGENTS.md` setup)
**Estimated size:** small

---

## Task 2: Contract, schema and module skeleton

**Story:** STORY-003
**Feature:** tracker-foundation
**Description:** Add the seven Jira App API paths and their schemas to the OpenAPI contract (contract first,
AGENTS rule 8). Liquibase changeset with `jira_site` and `issue_event` (design Data Model). Entities
`IssueEvent` (intention methods: `claim`, `markProcessed`, `markIgnored`, `markFailed`, `scheduleRetry`,
`markCommentPosted`), `JiraSite`, enums, repositories (incl. native claim query and stale-claim release),
`TrackerProperties` record, port interfaces and `stub-ports` stubs, `.env.example` names.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `.env.example`, `backend/src/main/resources/db/changelog/db.changelog-master.xml`, `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/db/changelog/changes/002-jira-issue-events.sql`, `tracker/entity/*`, `tracker/repository/*`, `tracker/config/TrackerProperties.java`, `tracker/service/port/*`, `tracker/service/port/stub/*`
- Test: `tracker/entity/IssueEventTest.java` (state transitions used by AC-004.5–004.8)
**Dependencies:** Task 1
**Estimated size:** medium

---

## Task 3: FIT verification chain

**Story:** STORY-001
**Feature:** fit-verification
**Description:** Add `spring-boot-starter-oauth2-resource-server` (flag in the PR — new dependency).
`JiraAppSecurityConfig` (`@Order(1)`, matcher `/api/v1/jira-app/**`, stateless), `FitJwtDecoderConfig`
(`NimbusJwtDecoder` with JWKS URI, RS256 only, 60 s skew), `FitValidators` (issuer, audience = app id, cloudId →
`jira_site`), `FitAuthenticationConverter` → `JiraAppPrincipal` + `ROLE_JIRA_APP`, ProblemDetail MSG08 for
401/403 and MSG06 for 503 (JWKS down). Never log tokens. Covers edge cases 1–7.
**Files:**
- Modify: `backend/pom.xml`
- Create: `tracker/config/JiraAppSecurityConfig.java`, `tracker/config/FitJwtDecoderConfig.java`, `tracker/config/FitValidators.java`, `tracker/security/JiraAppPrincipal.java`, `tracker/security/FitAuthenticationConverter.java`
- Test: `tracker/config/FitValidatorsTest.java`, `tracker/config/JiraAppSecurityConfigTest.java` (`@WebMvcTest`, signed test tokens from a local JWK set)
**Dependencies:** Task 2
**Estimated size:** medium

---

## Task 4: Jira site registration

**Story:** STORY-002
**Feature:** jira-site-registration
**Description:** `JiraSiteService.registerSite(workspaceId, siteUrl)`: URL regex
`^https://[a-z0-9-]+\.atlassian\.net/?$` → else MSG61; `JiraTenantInfoClient` (`RestClient`, 10 s timeout,
no redirects) reads `cloudId`; unique cloudId across Workspaces → MSG61; replace previous site. Edge cases 8–9.
**Files:**
- Create: `tracker/service/JiraSiteService.java`, `tracker/service/impl/JiraSiteServiceImpl.java`, `tracker/client/JiraTenantInfoClient.java`
- Test: `tracker/service/impl/JiraSiteServiceImplTest.java`, `tracker/client/JiraTenantInfoClientTest.java` (MockRestServiceServer)
**Dependencies:** Task 2
**Estimated size:** small

---

## Task 5: Token encryption

**Story:** STORY-004
**Feature:** token-cipher
**Description:** `TokenCipher`: AES-256-GCM, random 12-byte IV, `iv || ciphertext || tag`; key from
`TSOLVE_TOKEN_KEY` (base64, 32 bytes) — application fails to start if missing or wrong length; decrypt failure
→ `TokenUnreadableException`. Edge case 20.
**Files:**
- Create: `tracker/security/TokenCipher.java`
- Test: `tracker/security/TokenCipherTest.java`
**Dependencies:** Task 2
**Estimated size:** small

---

## Task 6: Receive issue events

**Story:** STORY-003
**Feature:** receive-issue-events
**Description:** `JiraAppEventController` (two POST endpoints, `@PreAuthorize("hasRole('JIRA_APP')")`) →
`IssueEventService.receive(principal, payload, systemToken)`: validate body (AC-003.6), require
`x-forge-oauth-system` (AC-003.5), skip updated events without a status change (AC-003.3), encrypt token, insert;
unique violation → 202 (AC-003.4). Store the FIT `apiBaseUrl`. Edge cases 7, 11–15.
**Files:**
- Create: `tracker/controller/JiraAppEventController.java`, `tracker/service/IssueEventService.java`, `tracker/service/impl/IssueEventServiceImpl.java`, `tracker/dto/IssueEventPayload.java`
- Test: `tracker/service/impl/IssueEventServiceImplTest.java`, `tracker/controller/JiraAppEventControllerTest.java`
**Dependencies:** Tasks 3, 5
**Estimated size:** medium

---

## Task 7: Jira client and ADF text

**Story:** STORY-004
**Feature:** jira-client
**Description:** `TicketTrackerClient` interface + `TicketTrackerClientFactory` + `JiraClient` (`RestClient`,
connect 5 s / read 10 s, no redirects, `apiBaseUrl` host allowlist): `getIssue` (fields of AC-004.2),
`getComments` (all pages), `addComment` (ADF body, built from nodes). Map 5xx/429/timeouts to a retryable error,
401/403/404 to non-retryable; never keep response bodies. `AdfTextExtractor` (paragraphs, lists, code, headings,
mentions as display text; media skipped; null → ""). Edge cases 25, 26, 36.
**Files:**
- Create: `tracker/service/TicketTrackerClient.java`, `tracker/service/TicketTrackerClientFactory.java`, `tracker/client/JiraClient.java`, `tracker/client/AdfTextExtractor.java`, `tracker/dto/TrackerIssue.java`, `tracker/dto/TrackerComment.java`
- Test: `tracker/client/JiraClientTest.java` (MockRestServiceServer), `tracker/client/AdfTextExtractorTest.java`
**Dependencies:** Task 2
**Estimated size:** medium

---

## Task 8: Event worker

**Story:** STORY-004
**Feature:** event-worker
**Description:** `IssueEventWorker` (`@Scheduled(fixedDelay = 5 s)`): release stale PROCESSING claims (10 min),
claim due batch (tx), for each: decrypt token, `IssueEventProcessor.process` outside a transaction, finish (tx).
Read issue (AC-004.2); no site/project → IGNORED (AC-004.3, edge case 10); updated + not Done → IGNORED
(AC-004.4); retryable → 1/5/15 min then FAILED (AC-004.5); non-retryable → FAILED (AC-004.6); 2 h → FAILED
`TOKEN_EXPIRED` (AC-004.7); delete token on finish (AC-004.8); WARN log id + error type on FAILED.
Edge cases 16–21.
**Files:**
- Create: `tracker/service/impl/IssueEventWorker.java`, `tracker/service/impl/IssueEventProcessor.java`
- Test: `tracker/service/impl/IssueEventWorkerTest.java`, `tracker/service/impl/IssueEventProcessorTest.java`
**Dependencies:** Tasks 6, 7
**Estimated size:** medium

---

## Task 9: Triage a created ticket and post MSG80

**Story:** STORY-005
**Feature:** triage-on-create
**Description:** In the processor's CREATED path: triage port → search port (compute suggestions) → if Simple
with a suggestion, post MSG80 (public) built as ADF nodes, without the "Related documents" sentence when there
are no links; set `comment_posted` in its own transaction right after posting; skip posting when already set.
Edge cases 22, 24.
**Files:**
- Modify: `tracker/service/impl/IssueEventProcessor.java`
- Create: `tracker/service/impl/SuggestionCommentBuilder.java`
- Test: `tracker/service/impl/IssueEventProcessorTriageTest.java`, `tracker/service/impl/SuggestionCommentBuilderTest.java`
**Dependencies:** Task 8
**Estimated size:** medium

---

## Task 10: AI draft when a ticket is Done

**Story:** STORY-006
**Feature:** draft-on-done
**Description:** Processor UPDATED + Done path: read all comments, convert to text, call ticket port
`createOrUpdateAiDraft`; draft failure → PROCESSED with draft-unavailable state (AC-006.2); never comment
(AC-006.3). Edge cases 25, 27.
**Files:**
- Modify: `tracker/service/impl/IssueEventProcessor.java`
- Test: `tracker/service/impl/IssueEventProcessorDoneTest.java`
**Dependencies:** Task 8
**Estimated size:** small

---

## Task 11: Issue Panel data and search again

**Story:** STORY-007
**Feature:** issue-panel
**Description:** `JiraAppIssueController` GET panel + POST refresh → `JiraPanelService`: identity port (unlinked →
MSG62 state, deactivated → MSG10), project port (not connected), search port (suggestions or MSG76), ticket port
(Solution Form state MSG64/65/67/77), triage port (MSG81 question for the reporter — STORY-010 reads it here);
refresh reads current summary/description with the request's system token (never stored); 20 s budget → 503
MSG06; `issueKey` pattern → 400. Edge cases 28–30, token-not-persisted test.
**Files:**
- Create: `tracker/controller/JiraAppIssueController.java`, `tracker/service/JiraPanelService.java`, `tracker/service/impl/JiraPanelServiceImpl.java`, `tracker/dto/PanelResponse.java`
- Test: `tracker/service/impl/JiraPanelServiceImplTest.java`, `tracker/controller/JiraAppIssueControllerTest.java`
**Dependencies:** Tasks 3, 7
**Estimated size:** medium

---

## Task 12: Solution Form actions

**Story:** STORY-008
**Feature:** solution-form
**Description:** PUT solution-draft, POST solution (Finish or close with empty Solution — MSG70) → ticket port
with the linked T-Solve user as author; unlinked/deactivated → 403 MSG62 without calling the port; ticket
port errors returned unchanged. Edge cases 31–32.
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
**Description:** MSG81 in panel data when the principal is the reporter and the MSG80 suggestion is unanswered
(even if unlinked); POST suggestion-answer: reporter check via Jira read (403 MSG08 otherwise), unanswered check
(409 MSG06), pass to triage port, return MSG82/MSG83. Edge cases 33–34.
**Files:**
- Modify: `tracker/controller/JiraAppIssueController.java`, `tracker/service/impl/JiraPanelServiceImpl.java`
- Create: `tracker/dto/SuggestionAnswerRequest.java`, `tracker/dto/SuggestionAnswerResponse.java`
- Test: `tracker/service/impl/JiraPanelServiceAnswerTest.java`
**Dependencies:** Task 11
**Estimated size:** small

---

## Task 14: Clean up old events

**Story:** STORY-009
**Feature:** event-cleanup
**Description:** `TrackerCleanupJob` (`@Scheduled` daily 03:00): delete PROCESSED/IGNORED/FAILED older than 30 days
in batches of 5 000; never RECEIVED/PROCESSING; on failure log error type and stop until the next run. Edge case 35.
**Files:**
- Create: `tracker/service/impl/TrackerCleanupJob.java`
- Test: `tracker/service/impl/TrackerCleanupJobTest.java`
**Dependencies:** Task 2
**Estimated size:** small
