---
module: jira-app
date: 2026-11-11
total_tasks: 9
owner: Dev
---

# Tasks: jira-app — iteration 2 (KAN-126)

Spec: `artifacts/03-modules/jira-app/spec.md` · Design: `artifacts/04-plans/jira-app/iteration-2/design.md`.
Branch `feat/KAN-126-jira-app-solution-form-v2`. Backend tests name their AC id (`refusesNonMember_AC_001_2`); Forge
tests start with it (`it('AC-002.3 shows MSG01 under an empty section', …)`). Each task ends with
`mvn spotless:apply` + `mvn verify` (backend), and `npm test` + `npm run lint` + `forge lint` in
`jira-app/TSolve_JiraApp` (Forge). The Forge spike of 01/11 is done (RESEARCH-003) and is not repeated.

## Task 1: Contract

**Story:** STORY-001, STORY-002, STORY-004, STORY-005, STORY-006, STORY-007, STORY-008
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

**Story:** STORY-002, STORY-004, STORY-005, STORY-006
**Feature:** solution-ticket-storage
**Description:** Changeset `027-jira-app-solution-draft.sql` (table `solution_draft`; no new ticket index — `uq_ticket_ticket_id`
keeps one ticket per issue key, of any source, as the design decides). In `ticket`:
`Ticket.submitted(...)`; `SolutionFormTicketService.submit(project, issueKey, title, writer, sections)` — validates
required sections (MSG01 with `field_name`) and 10,000 code points (MSG02) unless all five are empty (Close), sets
`review_due_date` = today in Asia/Ho_Chi_Minh + the Project's `review_range` (3 when not positive), saves
`SOLUTION_FORM`/`PENDING_REVIEW`, maps the unique violation to 409, deletes the writer's Draft for the issue;
`findStatus(issueKey)` (the issue's ticket, any source: AC-001.2, AC-008.2). `SolutionDraftService.save/find(creator, project, issueKey)`. IT (shared
Testcontainers base): the new ticket appears in `TicketReviewReadService.queue` with entry time and due date;
two concurrent submits → one ticket. Tests: AC-002.5, AC-004.1, AC-004.3, AC-005.1, AC-005.3, AC-006.2.
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/027-jira-app-solution-draft.sql`,
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

**Story:** STORY-001, STORY-002, STORY-004, STORY-005, STORY-006, STORY-007, STORY-008
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

**Story:** STORY-002, STORY-004, STORY-005, STORY-006, STORY-007
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
