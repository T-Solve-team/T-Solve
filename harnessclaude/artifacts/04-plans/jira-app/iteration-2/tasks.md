---
module: jira-app
date: 2026-11-11
total_tasks: 27
owner: Dev
---

# Tasks: jira-app — iteration 2 (KAN-126)

Spec: `artifacts/03-modules/jira-app/spec.md` · Design: `artifacts/04-plans/jira-app/iteration-2/design.md`.
Branch `feat/KAN-126-jira-app-solution-form-v2`. Backend tests name their AC id (`refusesNonMember_AC_001_2`); Forge
tests start with it (`it('AC-002.3 shows MSG01 under an empty section', …)`). Each task ends with
`mvn spotless:apply` + `mvn verify` (backend), and `npm test` + `npm run lint` + `forge lint` in
`jira-app/TSolve_JiraApp` (Forge). The Forge spike of 01/11 is done (RESEARCH-003) and is not repeated.

## Task 1: Contract

**Story:** STORY-001, STORY-002, STORY-004, STORY-005, STORY-007, STORY-008
**Feature:** solution-contract
**Description:** Add the seven paths of the spec's Exposed table to the OpenAPI file, tag `jira-app`, FIT security:
`POST /issues/{issueKey}/solution-form` (body `{summary, description}` → `{mayOpen, origin: DRAFT|AI|ISSUE,
sections, aiUnavailable}`), `POST /issues/{issueKey}/solution` (five sections → 201 `{ticketId, status}`; 400
MSG01 with `field_name`/MSG02, 403 MSG08, 409), `PUT /issues/{issueKey}/solution-draft` (204), `POST
/solution-rewrites` (five → five; 503 MSG68), `GET /issues/{issueKey}/panel` (`{linked, ticketStatus, hasDraft,
mayOpen, searchAllowed}`), `GET /tickets/search?q=` (list of `{ticketId, publicId, title, department}`; 503 MSG06),
`GET /tickets/{ticketId}` (title, Ticket ID, Department, sections, `webUrl`; 403 MSG08). Reuse the 01/11 schemas of
`feat/KAN-126-jira-app-solution-form` where they still fit. Run `npm run generate:api`; add `TSOLVE_STUB_AI` to
`.env.example`.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**`, `.env.example`
**Dependencies:** none
**Estimated size:** small

---

## Task 2: Solution Form ticket storage and Drafts

**Story:** STORY-002, STORY-004, STORY-005
**Feature:** solution-ticket-storage
**Description:** Changeset `033-jira-app-solution-draft.sql` (table `solution_draft`; no new ticket index — `uq_ticket_ticket_id`
keeps one ticket per issue key, of any source, as the design decides). In `ticket`:
`Ticket.submitted(...)`; `SolutionFormTicketService.submit(project, issueKey, title, writer, sections)` — validates
required sections (MSG01 with `field_name`) and 10,000 code points (MSG02) unless all five are empty (Close), sets
`review_due_date` = today in Asia/Ho_Chi_Minh + the Project's `review_range` (3 when not positive), saves
`SOLUTION_FORM`/`PENDING_REVIEW`, maps the unique violation to 409, deletes the writer's Draft for the issue;
`findStatus(issueKey)` (the issue's ticket, any source: AC-001.2, AC-008.2). `SolutionDraftService.save/find(creator, project, issueKey)`. IT (shared
Testcontainers base): the new ticket appears in `TicketReviewReadService.queue` with entry time and due date;
two concurrent submits → one ticket. Tests: AC-002.5, AC-004.1, AC-004.3, AC-005.1, AC-005.3, AC-006.2.
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/033-jira-app-solution-draft.sql`,
  `backend/src/main/java/vn/tsolve/ticket/{entity/SolutionDraft,repository/SolutionDraftRepository,service/SolutionFormTicketService,service/SolutionDraftService,service/impl/*Impl}.java`, tests
- Modify: `db.changelog-master.xml`, `ticket/entity/Ticket.java`, `ticket/repository/TicketRepository.java`
**Dependencies:** none
**Estimated size:** medium

---

## Task 3: Jira issue access (shared with ticket-review)

**Story:** STORY-001, STORY-008
**Feature:** jira-issue-access
**Description:** `JiraIssueAccess.resolve(principal, issueKey)` → `TSolveUser` (via `JiraAskerLookup`), the ACTIVE
Project whose `jira_project_key` is the key's prefix, and the user's membership (`MEMBER`/`PROJECT_MANAGER`);
not linked → MSG62, not a member → MSG08. `JiraIssueAccessReviewAdapter implements ReviewJiraAccess`
(`inWriterIssue` refuses a non-member, `accountName` reads `app_user.full_name`), so `ticket-review`'s
`GET …/review-feedback` works. Tests: AC-001.2, AC-001.3, AC-008.1, AC-008.7 (feedback endpoint answers MSG64 for
the writer's Changes requested ticket and nothing for another writer).
**Files:**
- Create: `backend/src/main/java/vn/tsolve/jiraapp/service/JiraIssueAccess.java`, `.../service/impl/JiraIssueAccessImpl.java`,
  `.../service/impl/JiraIssueAccessReviewAdapter.java`, tests
**Dependencies:** none
**Estimated size:** medium

---

## Task 4: AI draft and rewrite

**Story:** STORY-002, STORY-007
**Feature:** solution-ai
**Description:** `SolutionAiService.draft(summary, description)` and `rewrite(sections)` → five sections, through
`LlmClient.chat` with a JSON schema of the five sections and a prompt that treats issue text as data; a deadline
inside the 20 s UI budget; any failure → `AiUnavailableException`. `StubSolutionAiService` when
`TSOLVE_STUB_AI=available|unavailable` is set. Issue text and Solution text are never logged. Tests: AC-002.1,
AC-002.2 (unavailable → caller falls back), AC-007.4 (timeout, malformed JSON).
**Files:**
- Create: `backend/src/main/java/vn/tsolve/jiraapp/service/SolutionAiService.java`, `.../impl/LlmSolutionAiService.java`,
  `.../impl/StubSolutionAiService.java`, `.../impl/SolutionPrompt.java`, tests
- Modify: `backend/src/main/resources/application.yml`
**Dependencies:** none
**Estimated size:** medium

---

## Task 5: Jira App endpoints

**Story:** STORY-001, STORY-002, STORY-004, STORY-005, STORY-007, STORY-008
**Feature:** solution-endpoints
**Description:** `JiraAppSolutionController` (solution-form: may-open rule + Draft, else AI draft, else Symptoms from
summary + description with `aiUnavailable`; solution; solution-draft; solution-rewrites), `JiraAppPanelController`
(panel), `JiraAppTicketController` (search via `TicketSearchService` with status PUBLISHED, MSG06 on its failure;
preview of a PUBLISHED ticket of the user's Departments, else 403 MSG08, `webUrl` from `TSOLVE_WEB_ORIGIN`). All
`@PreAuthorize(JIRA_APP)`, user from the FIT principal only. `@WebMvcTest`: 401/403 paths and one error mapping per
resource. Tests: AC-001.2, AC-002.1, AC-002.2, AC-004.3, AC-005.2, AC-008.2 to AC-008.6, AC-008.8, AC-009.2.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/jiraapp/controller/{JiraAppSolutionController,JiraAppPanelController,JiraAppTicketController}.java`,
  `backend/src/main/java/vn/tsolve/jiraapp/dto/*`, tests
**Dependencies:** Task 1, Task 2, Task 3, Task 4
**Estimated size:** large

---

## Task 6: Forge client, manifest and background script

**Story:** STORY-001, STORY-004
**Feature:** solution-forge-client
**Description:** Extend `tsolveClient.js` with the seven calls and the review-feedback call; retry network errors
and 5xx 3 times (1 s, 2 s, 4 s) then MSG69, never a 4xx; a 409 after a retry counts as submitted. Manifest:
`jira:issueViewBackgroundScript` (UI Kit) and endpoint routes; replace the hello-world issue panel. Background
script: on `JIRA_ISSUE_CHANGED` with the viewer's own `atlassianId`, read the status category with `requestJira`,
and only on a change into `done` ask `solution-form` and open the form Modal. Tests: AC-001.1, AC-001.5, AC-004.4.
**Files:**
- Modify: `jira-app/TSolve_JiraApp/manifest.yml`, `src/frontend/api/tsolveClient.js`, `src/frontend/messages.js`
- Create: `jira-app/TSolve_JiraApp/src/frontend/background/index.jsx`, tests
**Dependencies:** Task 1
**Estimated size:** medium

---

## Task 7: Solution Form and AI Rewrite Preview

**Story:** STORY-002, STORY-004, STORY-005, STORY-007
**Feature:** solution-form-ui
**Description:** 4.1 in a UI Kit Modal: five `TextArea`s (Workaround optional), Finish, Save as draft, Close,
Rewrite with AI; client checks MSG01 under the section and MSG02 (code points); MSG67 banner when prefilled from
the issue; MSG41 / MSG06 on Save as draft; MSG70 confirm on Close (cancel keeps content); MSG66 on success; MSG69
after retries with content kept. 4.2: current and rewritten Solution section by section, Accept / Discard; MSG68.
Tests: AC-002.3, AC-002.4, AC-004.2, AC-004.4, AC-005.2, AC-005.4, AC-006.1, AC-006.3, AC-007.1 to AC-007.4.
**Files:**
- Create: `jira-app/TSolve_JiraApp/src/frontend/form/{SolutionForm.jsx,validation.js,RewritePreview.jsx}`, tests
**Dependencies:** Task 6
**Estimated size:** large

---

## Task 8: Issue Panel and Published Ticket Preview

**Story:** STORY-001, STORY-008
**Feature:** issue-panel-ui
**Description:** 3.3: MSG62 only when not linked; MSG65 (Pending review); MSG64 from review-feedback with the PM's
name and comment; MSG77 + "reopen" button for a Draft; open-form button while Done, allowed and no ticket; search box
(MSG76 when not allowed), results with title, Ticket ID, Department, MSG04 when empty, MSG06 when search fails. 3.4
in a Modal: title, Ticket ID, Department, sections, "Open in T-Solve" link. Tests: AC-001.4, AC-008.1 to AC-008.8,
AC-009.1. Check the whole flow on the dev site with `forge tunnel` and `forge deploy` (manifest changes).
**Files:**
- Create: `jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.jsx`, `src/frontend/preview/TicketPreview.jsx`, tests
- Modify: `jira-app/TSolve_JiraApp/src/frontend/index.jsx`
**Dependencies:** Task 6, Task 7
**Estimated size:** medium

---

## Task 9: Remove the Published-ticket search from the Jira App

**Story:** STORY-008
**Feature:** remove-jira-search
**Description:** Spec change of 14/11 (AC-008.4, AC-008.5, AC-008.6, AC-008.8 and the Published Ticket Preview story removed). Jira App: the
panel shows no search box, results, MSG04/MSG76 or 3.4 preview; drop `searchTickets` and the ticket preview call
from `tsolveClient.js`, `TicketPreviewModal.jsx` and their tests. Backend: delete `JiraAppTicketController`,
`JiraAppTicketService(Impl)`, `JiraAppTicketResult`, `JiraAppTicketPreview`, `PublishedTicketService(Impl)`,
`PublishedTicketView`, the `TicketRepository` Published-ticket query, `JiraProjectDepartmentRepository`, the
`canSearch` field of `IssuePanelResponse` and their tests; `TSOLVE_WEB_ORIGIN` goes from `.env.example` and the test
config. OpenAPI: remove `GET /jira-app/tickets/search`, `GET /jira-app/tickets/{ticketId}`, their schemas and
`IssuePanel.canSearch`; regenerate the client. The remaining panel tests (AC-001.4, AC-008.1 to AC-008.3, AC-008.7)
stay green.
**Files:**
- Delete: `backend/src/main/java/vn/tsolve/jiraapp/{controller/JiraAppTicketController,service/JiraAppTicketService,service/impl/JiraAppTicketServiceImpl,dto/JiraAppTicketResult,dto/JiraAppTicketPreview,repository/JiraProjectDepartmentRepository}.java`,
  `backend/src/main/java/vn/tsolve/ticket/{service/PublishedTicketService,service/impl/PublishedTicketServiceImpl,dto/PublishedTicketView}.java`,
  `jira-app/TSolve_JiraApp/src/frontend/panel/TicketPreviewModal.jsx`, their tests
- Modify: `IssuePanelResponse.java`, `JiraAppSolutionServiceImpl.java`, `TicketRepository.java`, `IssuePanel.jsx`,
  `tsolveClient.js`, `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**`, `.env.example`
**Dependencies:** Task 8
**Estimated size:** medium

---

## Task 10: Close keeps a Draft; no empty ticket

**Story:** STORY-005
**Feature:** close-keeps-draft
**Description:** Spec change of 14/11 (AC-005.1 reworded, AC-005.5 new, the Close story retired). Jira App: the Solution Form
has Rewrite with AI, Close and Finish only; Close and the dialog's close control save the Draft (`PUT …/solution-draft`),
close the form and show the flag "Saved as draft"; on failure MSG06 and the form stays open (AC-005.4); no MSG70. Backend:
`SolutionFormTicketServiceImpl.submit` always checks the required sections, so an all-empty submission is 400 MSG01
and never reaches the Review Queue; the AC-006.2 tests go and AC-005.5 tests come in. OpenAPI: the solution endpoint
no longer describes "all empty = Close". Re-check Close, the Draft and Finish on the dev site.
**Files:**
- Modify: `jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.jsx`, its test, `src/frontend/messages.js`,
  `backend/src/main/java/vn/tsolve/ticket/service/impl/SolutionFormTicketServiceImpl.java`, its tests,
  `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**`
**Dependencies:** Task 9
**Estimated size:** small

---

## Task 11: Open Drafts for the reminder

**Story:** STORY-011
**Feature:** draft-reminder-query
**Description:** In `ticket`: `SolutionDraftService` gains the open Drafts of the current Workspace — per writer and for
one writer — as `(creatorId, issueKey, updatedAt)`, newest first: `solution_draft` rows whose `jira_issue_key` has no
`ticket` of any source (whether the creator is still ACTIVE is the job's check through `identity`, so `ticket` reads
no `identity` table). Read-only, no schema change. Tests: AC-011.2 (a Draft with a
ticket and a writer with none are left out; an inactive creator is left out by the job, Task 12), ordering for AC-011.3; an IT on PostgreSQL.
**Files:**
- Modify: `backend/src/main/java/vn/tsolve/ticket/service/SolutionDraftService.java`, its impl,
  `backend/src/main/java/vn/tsolve/ticket/repository/SolutionDraftRepository.java`
- Create: `backend/src/main/java/vn/tsolve/ticket/dto/OpenDraft.java`, tests
**Dependencies:** Task 10

## Task 13: Resubmit — contract, ticket storage and endpoints

**Story:** STORY-012
**Feature:** resubmit-backend
**Description:** OpenAPI first: `resubmit` (boolean, default false) on `SolutionFormRequest` and
`SubmitSolutionRequest`, `mayResubmit` on `IssuePanel`, origin `TICKET`, 409 MSG06 on `submitSolution`; regenerate the
frontend client. `ticket`: the resubmittable read and the guarded UPDATE of the design (due date reset, Draft deleted,
409 MSG06 on a guard miss). `jiraapp`: `openForm`, `submit` and `panel` as the design says. Tests: service unit tests
and one IT on the real database for the UPDATE (status, sections, due date, entry time, Draft gone, guard misses).
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**`,
  `backend/src/main/java/vn/tsolve/ticket/repository/TicketRepository.java`, `ticket/service/SolutionFormTicketService.java`,
  `ticket/service/impl/SolutionFormTicketServiceImpl.java`, `jiraapp/dto/SolutionFormRequest.java`,
  `jiraapp/dto/SubmitSolutionRequest.java`, `jiraapp/dto/SolutionFormResponse.java`, `jiraapp/dto/IssuePanelResponse.java`,
  `jiraapp/service/impl/JiraAppSolutionServiceImpl.java`, their tests, `ticket/SolutionFormTicketIT.java`
- Create: `ticket/exception/TicketNotResubmittableException.java`
**Dependencies:** Task 10
**Estimated size:** medium

## Task 14: Resubmit — Issue Panel and Solution Form

**Story:** STORY-012
**Feature:** resubmit-forge
**Description:** The panel shows "Edit and resubmit" with MSG64 when `mayResubmit` and opens the form with
`resubmit: true`; the form shows MSG64 above the sections, sends `resubmit` on Finish, keeps a Draft on Close, and
checks, rewrites and retries as before (AC-012.7). The client's retry counts 409 MSG06 + panel `PENDING_REVIEW` as
submitted. Check the flow on the dev site (Request changes → Edit and resubmit → Pending review).
**Files:**
- Modify: `jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.jsx`, `form/SolutionFormModal.jsx`,
  `api/tsolveClient.js`, their tests
**Dependencies:** Task 13
**Estimated size:** small

## Task 15: Issue Panel — Rejected ticket

**Story:** STORY-008
**Feature:** panel-rejected
**Description:** AC-008.9: while the writer's Solution Form ticket is Rejected the panel reads the feedback endpoint and
shows `Rejected by {manager_name}: "{reason}".` (code `REJECTED`, a Jira App text) with no button.
**Files:**
- Modify: `jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.jsx`, `messages.js`, their tests
**Dependencies:** ticket-review Task 22
**Estimated size:** small

## Task 16: Issue Panel — Published ticket

**Story:** STORY-008
**Feature:** panel-published
**Description:** AC-008.10: while the issue's ticket is Published, whatever its source, the panel shows "Solution
approved and published." (a Jira App text) with no button. No server change: the panel already reads `ticketStatus`.
**Files:**
- Modify: `jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.jsx`, its test
**Dependencies:** none
**Estimated size:** small

---

## Task 12: Reminder job and email

**Story:** STORY-011
**Feature:** draft-reminder-job
**Description:** In `vn.tsolve.jiraapp`: `DraftReminderRegistration` (recurring id `jira-app-draft-reminder`,
`0 8 * * *`, Asia/Ho_Chi_Minh, only with `tsolve.worker.enabled`), `DraftReminderDispatchHandler` (one Workspace job per
active Workspace), `DraftReminderWorkspaceHandler` (one writer job per writer, job id = name-based UUID of workspace +
account + date), `DraftReminderEmailHandler` (active Account email + open Drafts → plain-text email of AC-011.3,
link from `JIRA_SITE_URL`, queued with `EmailOutboxService.queue` inside the Workspace; nothing when the writer has
none left; changed 15/11 from an own SMTP mailer). Tests: AC-011.1 to AC-011.7 (mail content, singular/plural,
no link without the site URL, same-day dedupe, per-writer retry of the queue call, a rejected address dropped, the
email queued inside the Workspace, logs without addresses).
**Files:**
- Create: `backend/src/main/java/vn/tsolve/jiraapp/dto/DraftReminder*Request.java`, `jiraapp/service/impl/DraftReminder*.java`, tests
**Dependencies:** Task 11
**Estimated size:** medium


## Task 17: Hand the app token to the backend (KAN-349)

**Story:** STORY-013
**Feature:** app-token-trigger
**Description:** Manifest: a `scheduledTrigger` (interval `hour`) and a `trigger` on `avi:forge:installed:app` /
`avi:forge:upgraded:app`, both to a new endpoint `tsolve-app-token-endpoint` on remote `tsolve-backend`, route
`/api/v1/jira-app/app-token`, `auth.appSystemToken.enabled: true` (AC-013.1); scopes `read:jira-work`,
`write:jira-work` (AC-013.2); no Forge function in between, no retry (AC-013.3). The backend side is workspace Task 12.
**Files:** Modify `jira-app/TSolve_JiraApp/manifest.yml`; test `jira-app/TSolve_JiraApp/test/manifest.test.js` (the
three ACs read from the parsed manifest); `forge lint`.
**Estimated size:** small

---

## Task 18: Ask T-Solve without a T-Solve sign-in (KAN-294)

**Story:** STORY-010
**Feature:** ask-without-sign-in
**Description:** Backend: the FIT's `context.extension.project.key` becomes part of `JiraAppPrincipal` (AC-010.13);
`JiraAppQuestionServiceImpl` binds the site's Workspace, finds the Active Project of that key and calls
`ChatbotAnswerService.askForProject` with the asker's linked T-Solve user, if any, as `reader` (AC-010.4); a site with no Workspace answers 403 MSG124, no Jira user, no or an unknown project
403 MSG08, with no AI call (AC-010.11); `GET /questions/eligibility` answers `status` (AC-010.14). Contract first:
`JiraAskerEligibility` becomes `{ status }`. Forge page: `CONNECTED` shows the box for everyone; `PROJECT_NOT_CONNECTED`
shows "This Jira project is not connected to T-Solve."; `SITE_NOT_CONNECTED` shows MSG124; both keep "Not solved,
create ticket" with nothing prefilled (AC-010.2). MSG62 is no longer used on the page. `PUT /questions/{answerId}/rating`: "Solved" sends HELPFUL, "Not solved, create ticket" NOT_HELPFUL for the last answer; a failure is silent (AC-010.15).
**Files:** Modify `common/security/JiraAppPrincipal.java` and the FIT conversion that builds it,
`jiraapp/service/impl/JiraAppQuestionServiceImpl.java`, `jiraapp/dto/JiraAskerEligibility.java`,
`jiraapp/controller/JiraAppQuestionController.java`, `frontend/openapi/tsolve-api.yaml` (+ `npm run generate:api`),
`jira-app/TSolve_JiraApp/src/frontend/askTsolve/*`, `jira-app/TSolve_JiraApp/src/frontend/messages.js`; tests
`JiraAppQuestionServiceImplTest`, `JiraAppQuestionControllerTest`, the FIT conversion test, the Ask page tests.
**Estimated size:** medium

---

## Task 19: Read the linked Account instead of the stub (STORY-014)

**Story:** STORY-014
**Feature:** linked-asker-lookup
**Description:** Design "Change 2026-11-17: link the Jira account". `LinkedJiraAskerLookup implements JiraAskerLookup`
over identity's `JiraAccountLinkLookup` (identity Task 37); delete `ConfigJiraAskerLookup`, its test and the
`TSOLVE_STUB_JIRA_ASKERS` line of `.env.example` (AC-014.11); the staging `.env` line is removed on the VPS after deploy.
**Files:** Create `jiraapp/service/impl/LinkedJiraAskerLookup.java`; delete `jiraapp/service/impl/ConfigJiraAskerLookup.java`;
modify `.env.example`; tests `LinkedJiraAskerLookupTest` (AC-014.11)
and the existing callers' tests.
**Dependencies:** identity Task 37
**Estimated size:** small

---

## Task 20: Account link endpoints (STORY-014)

**Story:** STORY-014
**Feature:** account-link-endpoints
**Description:** Contract first: `GET` and `POST /api/v1/jira-app/account-link` in `frontend/openapi/tsolve-api.yaml`
(`JiraAccountLinkStatus { status, fullName?, email?, linkedAt? }`, `JiraAccountLinkStart { url }`), then
`npm run generate:api`. `JiraAccountLinkController`, `JiraAccountLinkPageService` (+ impl) and
`jiraapp/client/JiraUserClient` as the design says; AC-014.2, AC-014.6, AC-014.9, AC-014.10.
**Files:** Create `jiraapp/controller/JiraAccountLinkController.java`, `jiraapp/service/JiraAccountLinkPageService.java`,
`jiraapp/service/impl/JiraAccountLinkPageServiceImpl.java`, `jiraapp/client/JiraUserClient.java`,
`jiraapp/dto/JiraAccountLinkStatus.java`, `jiraapp/dto/JiraAccountLinkStart.java`; modify
`frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**`; tests
`JiraAccountLinkPageServiceImplTest`, `JiraAccountLinkControllerTest`, `JiraUserClientTest`.
**Dependencies:** Task 19
**Estimated size:** medium

---

## Task 21: T-Solve account page and the panel link (STORY-014)

**Story:** STORY-014
**Feature:** account-settings-page
**Description:** Manifest: `jira:personalSettingsPage` "T-Solve account" (AC-014.1) and scope `read:jira-user`
(AC-013.2). The page: states of AC-014.3 to AC-014.5, the click of AC-014.6 and AC-014.7, polling of AC-014.8,
failures of AC-014.9. The panel's MSG62 state adds "Open T-Solve account settings" (AC-008.1).
**Files:** Modify `jira-app/TSolve_JiraApp/manifest.yml`, `src/frontend/api/tsolveClient.js`,
`src/frontend/messages.js`, the issue panel component; create `src/frontend/accountSettings/*`; tests
`AccountSettingsPage.test.jsx`, the panel test, `test/manifest.test.js`; `forge lint`.
**Dependencies:** Task 20
**Estimated size:** medium

---

## Task 22: Move the T-Solve account page to the Apps section (STORY-014)

**Story:** STORY-014
**Feature:** account-page-global
**Description:** Manifest: replace `jira:personalSettingsPage` with `jira:globalPage` (same key, resource, resolver and
title) (AC-014.1). `settingsPagePath` builds `/jira/apps/{appId}/{envId}` for the panel's MSG62 link (AC-008.1).
**Files:** Modify `jira-app/TSolve_JiraApp/manifest.yml`, `src/frontend/accountSettings/settingsPage.js`; tests
`src/manifest.test.js`, `settingsPage.test.js`, `IssuePanel.test.jsx`; `forge lint`.
**Dependencies:** Task 21
**Estimated size:** small

---

## Task 23: Draft only while the issue is Done (STORY-008)

**Story:** STORY-008
**Feature:** draft-only-when-done
**Description:** The panel shows MSG77 and "Continue draft" only while the issue's status category is Done
(AC-008.3); otherwise it shows neither, and "Continue draft" re-checks the status before it opens the form. The
Draft is not touched (AC-008.11). Jira App only; the panel already reloads on `JIRA_ISSUE_CHANGED` (BUG-231).
**Files:** Modify `jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.jsx`; test `IssuePanel.test.jsx`.
**Dependencies:** none
**Estimated size:** small

## Task 24: The form opens with the issue text, no AI draft (KAN-371)

**Story:** STORY-002, STORY-005
**Feature:** form-from-issue
**Description:** `openForm` without a Draft returns `Origin.ISSUE` with the issue text (summary, blank line,
description, cut to 10,000) and no message; no AI call (AC-002.1). The Draft still wins (AC-005.2). Move
`fromIssue` into `IssueText` (Task 25 reuses it); remove `SolutionFormAi.draft`, `SolutionFormDraft`, `Origin.AI_DRAFT`
and MSG67 from the response; keep `rewrite`. OpenAPI: drop `AI_DRAFT` from the origin enum and the MSG67 wording of
the solution-form path and `message`; `npm run generate:api`. Forge: no behaviour change (a null `message` shows no
notice) — remove MSG67 tests/fixtures that assumed an AI draft. Rewrite or delete the `*_AC_002_1` / `*_AC_002_2`
tests; new tests name AC-002.1 and AC-005.2.
**Files:** Modify `backend/src/main/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImpl.java`,
`.../service/SolutionFormAi.java`, `.../service/impl/SolutionFormAiImpl.java`, `.../dto/SolutionFormResponse.java`;
create `.../service/impl/IssueText.java`; delete `.../dto/SolutionFormDraft.java`; tests
`JiraAppSolutionServiceImplTest`, `SolutionFormAiImplTest`, new `IssueTextTest`; `frontend/openapi/tsolve-api.yaml`
+ `frontend/src/shared/api/generated`; Forge `SolutionFormModal.test.jsx`, `BackgroundApp.test.jsx` if they use
`AI_DRAFT`/MSG67.
**Dependencies:** none
**Estimated size:** small

## Task 25: Record the Done state and create the Draft at Done (KAN-371)

**Story:** STORY-005
**Feature:** draft-at-done-backend
**Description:** Changeset `048-jira-app-issue-done-state.sql` (`jira_issue_done_state`: `project_id` → project,
`jira_issue_key`, `done`, `updated_at`, PK (project_id, jira_issue_key)) and its entity/repository in `ticket`;
`SolutionDraftService.recordIssueDone` (upsert) and `createIfAbsent` (`ON CONFLICT DO NOTHING`). OpenAPI path
`POST /api/v1/jira-app/events/issue-updated` (`IssueUpdatedPayload`, 202). `JiraAppIssueEventController`
(`ROLE_JIRA_APP`) → `IssueUpdatedService`: Workspace from the FIT site, Project from the key, Done state, move
detection (recorded state, else `JiraStatusClient` for the `from` status's category, a failed read = a move), owner =
assignee else `atlassianId`, linked + member + no ticket, Draft = `IssueText` of the summary and the ADF description
(a `jiraapp` ADF-to-text matching `watchDone.js#issueText`). Jira read outside the transaction; both writes in one.
Tests: AC-005.6 (role, Workspace from the FIT not the payload), AC-005.7 (assignee, mover without assignee, content),
AC-005.8 (each refusal leaves an existing Draft unchanged, automation without assignee), AC-005.9 (in/out of Done,
Draft kept), AC-005.10 (unreadable event → 202 no change; write failure → 5xx, nothing stored); an IT for the
migration and `createIfAbsent` against a concurrent Close.
**Files:** Create `backend/src/main/resources/db/changelog/changes/048-jira-app-issue-done-state.sql` (+ master),
`backend/src/main/java/vn/tsolve/ticket/entity/JiraIssueDoneState.java`,
`.../ticket/repository/JiraIssueDoneStateRepository.java`, `.../jiraapp/controller/JiraAppIssueEventController.java`,
`.../jiraapp/dto/IssueUpdatedPayload.java`, `.../jiraapp/service/IssueUpdatedService.java`,
`.../jiraapp/service/impl/IssueUpdatedServiceImpl.java`, `.../jiraapp/service/impl/AdfText.java`,
`.../jiraapp/client/JiraStatusClient.java`, `.../jiraapp/client/RestJiraStatusClient.java`; modify
`.../ticket/service/SolutionDraftService.java`, `.../impl/SolutionDraftServiceImpl.java`,
`.../ticket/repository/SolutionDraftRepository.java`; `frontend/openapi/tsolve-api.yaml` + generated; tests.
**Dependencies:** Task 24 (`IssueText`)
**Estimated size:** large

## Task 26: Forge trigger on issue updates (KAN-371)

**Story:** STORY-005
**Feature:** issue-updated-trigger
**Description:** Manifest `trigger` `tsolve-issue-updated-trigger` on `avi:jira:updated:issue` with
`filter.ignoreSelf: true`, straight to remote endpoint `tsolve-issue-updated-endpoint` (route
`/api/v1/jira-app/events/issue-updated`), as the issue-created trigger (AC-005.6). `manifest.test.js` checks it;
`forge lint` passes. No new scope (`read:jira-work` covers the event). Dev-site check: drag a card to Done on the
board → the assignee's panel shows MSG77 "Continue draft".
**Files:** Modify `jira-app/TSolve_JiraApp/manifest.yml`, `jira-app/TSolve_JiraApp/src/manifest.test.js`.
**Dependencies:** Task 25
**Estimated size:** small

## Task 27: Remind only while the issue is Done (KAN-371)

**Story:** STORY-011
**Feature:** draft-reminder-done-only
**Description:** Both `findOpenDrafts` queries require a `jira_issue_done_state` row with `done = true` for the
Draft's Project and issue (AC-011.2); a Draft of an issue moved out of Done or never recorded gets no email. Tests
named AC-011.2: Done → reminded, moved out → not, never recorded → not, back in Done → reminded again.
**Files:** Modify `backend/src/main/java/vn/tsolve/ticket/repository/SolutionDraftRepository.java`; tests of the
reminder query/handlers (`DraftReminder*Test`, the open-Draft repository IT).
**Dependencies:** Task 25 (the table)
**Estimated size:** small
