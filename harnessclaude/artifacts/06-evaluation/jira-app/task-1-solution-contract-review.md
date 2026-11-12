---
module: jira-app
feature: solution-contract
task: 1
verdict: fail
attempt: 1
date: 2026-11-13
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: jira-app — Task 1: Solution Form and Issue Panel contract

## Summary

Commit f16c8a02 adds the seven Jira App paths of the spec's Exposed table to `frontend/openapi/tsolve-api.yaml`, with
their schemas and the regenerated client, plus `TSOLVE_STUB_AI` and `TSOLVE_WEB_ORIGIN` in `.env.example`. None of
the 21 later commits on the branch changed the jira-app part of the contract. At HEAD (221d53ca) I checked:

- Paths, methods, operationIds, schemas and field names match the backend controllers and DTOs.
- `npm run generate:api` leaves no diff.
- `npm run typecheck` passes.

The task's own check fails: "every error the backend can raise for each operation is documented". Five operations
leave out status codes or message codes that the backend really returns:

- `openSolutionForm` and `getIssuePanel` document no 403. The backend returns 403 MSG08 for a Jira site with no
  registered Workspace (design edge case 13).
- Five operations leave out the 503 MSG06 that the FIT security chain returns when the JWKS cannot be fetched. The
  older jira-app paths in the same file do document this 503.
- The search 403 names MSG76, which the search endpoint never returns, and leaves out MSG08.
- The submit 409 does not name its codes and leaves out MSG40.

The verdict is fail, because these gaps are exactly what this task delivers.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | fail | The seven paths exist with the right methods and request/response schemas, but the error responses do not cover every error the backend raises (Issues 1 to 5). |
| Completeness | fail | 403 is missing on 2 operations, 503 on 5 operations, and message codes are wrong or missing on 3 operations. `.env.example` has `TSOLVE_STUB_AI` (and `TSOLVE_WEB_ORIGIN`). |
| Correctness | pass | `npm run generate:api`: no diff in `src/shared/api/generated`. `npm run typecheck`: exit 0. Maven was not run (the main session runs it, see the test report). |
| Integration | fail | Backend DTOs (`IssuePanelResponse`, `SolutionFormResponse`, `SubmittedTicketResponse`, `JiraAppTicketResult`, `JiraAppTicketPreview`, `SolutionSections`) and the Forge client use the contract's field names. But the 409 of `submitSolution` has two meanings and the contract does not say so, and the Forge client treats every 409 after a retry as a success (Issue 5). |

### Stage 1 — contract rows owned by this task

| # | Item (spec Exposed table) | Status | Evidence |
|---|---------------------------|--------|----------|
| 1 | `POST …/issues/{issueKey}/solution-form` → may open, Draft/AI draft/prefill, MSG67 flag | PARTIAL | Schemas OK (`SolutionForm`: `mayOpen`, `origin` DRAFT/AI_DRAFT/ISSUE, `sections`, `message` = MSG67) match `SolutionFormResponse.java`. Responses at yaml:886-898 have only 200/400/401: 403 and 503 are missing (Issues 1, 2). |
| 2 | `POST …/issues/{issueKey}/solution` (five sections; all empty = Close) | PARTIAL | 201 `SubmittedTicket {id, ticketId, status}`, 400 MSG01 with `field_name`/MSG02, 403 MSG62/MSG08 and 409 are documented. The 409 at yaml:939 names no code and leaves out MSG40; 503 is missing (Issues 2, 5). |
| 3 | `PUT …/issues/{issueKey}/solution-draft` | PARTIAL | 204/400/401/403 are documented; 503 is missing (Issue 2). |
| 4 | `POST …/solution-rewrites` (five → five; 503 MSG68) | PASS | 200/400/401/403/503 are documented. Minor: the 503 text at yaml:995 names only MSG68, but a JWKS failure gives 503 MSG06 (Issue 7). |
| 5 | `GET …/issues/{issueKey}/panel` → linked, ticket status, Draft, may open, search allowed | PARTIAL | `IssuePanel {linked, ticketStatus, hasDraft, mayOpenForm, canSearch}` matches `IssuePanelResponse.java`. Responses at yaml:1008-1019 have only 200/400/401: 403 and 503 are missing (Issues 1, 2). |
| 6 | `GET …/tickets/search?q=` (503 MSG06) | PARTIAL | The `q` limits (1..200), at most 20 results and 503 are documented. The 403 text at yaml:1052 is wrong (Issue 3). |
| 7 | `GET …/tickets/{ticketId}` (403 MSG08) | PARTIAL | The path parameter is named `ticketUuid` (same URL template). The 403 text at yaml:1090 leaves out MSG62, and 503 is missing (Issues 2, 4). |
| 8 | `npm run generate:api` leaves no diff | PASS | Run on 2026-11-13: `git status` clean under `frontend/`. |
| 9 | `TSOLVE_STUB_AI` in `.env.example` | PASS | `.env.example` (added in f16c8a02, later reworded). |

## Traceability

`bun harnessclaude/hooks/sdd.ts trace jira-app STORY-001 STORY-002 STORY-004 STORY-005 STORY-006 STORY-007 STORY-008 STORY-009`:

```
  ✓ AC-001.1  jira-app/TSolve_JiraApp/src/frontend/background/BackgroundApp.test.jsx, jira-app/TSolve_JiraApp/src/frontend/background/watchDone.test.js
  ✓ AC-001.2  backend/src/test/java/vn/tsolve/jiraapp/service/impl/ConfigJiraWorkspaceLookupTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraIssueAccessImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-001.3  jira-app/TSolve_JiraApp/src/frontend/background/BackgroundApp.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/ConfigJiraWorkspaceLookupTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraIssueAccessImplTest.java
  ✓ AC-001.4  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java
  ✓ AC-001.5  jira-app/TSolve_JiraApp/src/frontend/background/BackgroundApp.test.jsx, jira-app/TSolve_JiraApp/src/frontend/background/watchDone.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java
  ✓ AC-002.1  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/SolutionFormAiImplTest.java
  ✓ AC-002.2  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx, jira-app/TSolve_JiraApp/src/frontend/background/watchDone.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/SolutionFormAiImplTest.java
  ✓ AC-002.3  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
  ✓ AC-002.4  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
  ✓ AC-002.5  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-004.1  jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-004.2  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
  ✓ AC-004.3  jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-004.4  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js
  ✓ AC-005.1  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-005.2  backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java
  ✗ AC-005.3  no test under this module's paths (jira-app/**, backend/src/main/java/vn/tsolve/jiraapp/**, backend/src/test/java/vn/tsolve/jiraapp/**, frontend/openapi/**, frontend/src/shared/api/generated/**, docs/bug-log.md) — tests elsewhere? add their folder to paths mentions it
  ✓ AC-005.4  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
  ✓ AC-006.1  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
  ✓ AC-006.2  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
  ✓ AC-006.3  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
  ✓ AC-007.1  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/SolutionFormAiImplTest.java
  ✓ AC-007.2  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
  ✓ AC-007.3  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
  ✓ AC-007.4  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/SolutionFormAiImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-008.1  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraIssueAccessImplTest.java
  ✓ AC-008.2  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-008.3  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java
  ✓ AC-008.4  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppTicketServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-008.5  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppTicketServiceImplTest.java
  ✓ AC-008.6  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java
  ✓ AC-008.7  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraIssueAccessReviewAdapterTest.java
  ✓ AC-008.8  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-009.1  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppTicketServiceImplTest.java
  ✓ AC-009.2  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppTicketServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
trace jira-app (STORY-001, STORY-002, STORY-004, STORY-005, STORY-006, STORY-007, STORY-008, STORY-009): 34/35 acceptance criteria have a test
```

About AC-005.3: this task does not own it, so it does not count against this task. Its tests are in the `ticket`
module (Task 2): `backend/src/test/java/vn/tsolve/ticket/service/impl/SolutionDraftServiceImplTest.java` and
`backend/src/test/java/vn/tsolve/ticket/SolutionFormTicketIT.java`. Those folders are outside the module's trace
paths, which is why the trace shows ✗. Task 2's review should confirm it.

This task owns no runtime criterion. Its deliverable is the contract, checked in the Stage 1 table above.

## Issues

### CRITICAL (blocks pass)

1. **`openSolutionForm` and `getIssuePanel` have no 403.** See `frontend/openapi/tsolve-api.yaml:886-898` and
   `:1008-1019`. Both operations run inside `JiraIssueAccess.inIssue`, which first calls `workspace(caller)`
   (`backend/src/main/java/vn/tsolve/jiraapp/service/impl/JiraIssueAccessImpl.java:54-59`). That call throws
   `JiraIssueAccessDeniedException` (403 MSG08) when the FIT has no `accountId` or comes from a Jira site with no
   registered Workspace (design edge case 13, `TSOLVE_STUB_JIRA_SITES`). The FIT chain's `AccessDeniedHandler`
   (`backend/src/main/java/vn/tsolve/common/security/JiraAppSecurityConfig.java:42-43`) can also answer 403 MSG08.
   The 200 descriptions say "mayOpen false / linked false". That is true for an unlinked user, but not for an
   unregistered site, so a client written from the contract will not expect a 403 here.
   Fix: add `'403'` with the text "MSG08 (the Jira site has no registered Workspace, or the FIT has no user)" to both
   operations.

2. **503 MSG06 is missing on five operations.** The FIT chain answers 503 MSG06 when the JWKS cannot be fetched
   (`JiraAppSecurityConfig.java:45-48`). The two older jira-app operations in the same file document it
   (`tsolve-api.yaml:832-833`, `:867`), but the new operations do not:
   - `openSolutionForm` (`:886-898`)
   - `submitSolution` (`:917-943`)
   - `saveSolutionDraft` (`:958-967`)
   - `getIssuePanel` (`:1008-1019`)
   - `getJiraAppTicket` (`:1080-1094`)

   For `submitSolution` this also matters to the Forge client, whose retry rule (AC-004.4) depends on 5xx.
   Fix: add `'503': $ref: '#/components/responses/Problem'` to each of the five.

3. **The search 403 lists the wrong codes.** At `tsolve-api.yaml:1052` the text is "MSG62 (not linked) or MSG76 (a
   member of no Project in a Department)". The search path never raises MSG76:
   - `JiraAppTicketServiceImpl.search` (`backend/src/main/java/vn/tsolve/jiraapp/service/impl/JiraAppTicketServiceImpl.java:49-60`)
     only filters by Department.
   - MSG76 is a panel flag (`IssuePanel.canSearch`, `tsolve-api.yaml:2703`).
   - The search raises MSG62 (`JiraIssueAccessImpl.java:51`) and MSG08 (`:54-59`).
   - The controller's own annotation says "MSG62 or MSG08"
     (`backend/src/main/java/vn/tsolve/jiraapp/controller/JiraAppTicketController.java:38`).

   Fix: change the text to "MSG62 (not linked) or MSG08 (unregistered Jira site)".

4. **The preview 403 leaves out MSG62.** At `tsolve-api.yaml:1090` the text is "MSG08 — not Published, not in one of
   the viewer's Departments, or no such ticket". But `preview` runs in `access.asUser`, which throws
   `JiraAskerNotLinkedException` (403 MSG62) for an unlinked Jira account (`JiraIssueAccessImpl.java:51`), and
   MSG08 is also returned for an unregistered site.
   Fix: name MSG62 as well, and the unregistered-site case.

5. **The `submitSolution` 409 does not name its codes and leaves out MSG40.** At `tsolve-api.yaml:939` the only text
   is "The issue already has a Solution Form ticket in its Project". The backend has two 409s:
   - MSG31 (`TicketAlreadyExistsException`, mapped to 409 by `GlobalExceptionHandler.java:82-85`).
   - MSG40 (`ProjectNotActiveException`) when the conditional insert creates no row because the Project stopped being
     ACTIVE (`backend/src/main/java/vn/tsolve/ticket/service/impl/SolutionFormTicketServiceImpl.java:100-105`).

   This is not only wording. The Forge client treats any 409 after a retry as "submitted"
   (`jira-app/TSolve_JiraApp/src/frontend/api/tsolveClient.js:85-86`). A MSG40 after a retry would therefore show
   MSG66 success when no ticket was created, breaking AC-004.2 and GBR-04.
   Fix: document "MSG31 (the issue already has a ticket) or MSG40 (the Project is no longer active)". The Forge client
   (Task 6) should count a 409 as success only when `code === 'MSG31'`.

### Informational (does not block)

6. **`webUrl` can be a relative path.** The schema at `tsolve-api.yaml:2734-2737` says `format: uri`. But when
   `TSOLVE_WEB_ORIGIN` is empty (the `.env.example` default), the backend returns the relative path
   `/tickets/<uuid>` (`JiraAppTicketServiceImpl.java:45`, `:87`). Inside Jira, "Open in T-Solve" would then point
   at the Atlassian host. Either make the backend refuse to start without it, or say in the contract that the link
   is empty or relative when the variable is unset.

7. **The rewrite 503 text is incomplete.** `rewriteSolution` 503 (`tsolve-api.yaml:995`) names only MSG68, but a
   JWKS failure gives 503 MSG06. The text could say "MSG68, or MSG06 when the FIT cannot be verified".

8. **Names differ from the task text.** The contract differs from the Task 1 text in several names:
   - `origin` `AI_DRAFT` instead of `AI`
   - a `message` (MessageRef MSG67) instead of an `aiUnavailable` boolean
   - `mayOpenForm`/`canSearch` instead of `mayOpen`/`searchAllowed`
   - `SubmitSolutionRequest` carries `summary` as well as the sections
   - the path parameter `{ticketUuid}` instead of `{ticketId}`

   The backend DTOs and the Forge client use the contract's names, and the spec's Exposed table fixes none of these
   names, so this is not a defect. The `{ticketUuid}` choice has a stated reason (`tsolve-api.yaml:1068`).

9. **`SolutionSections` is stricter in the contract than in the backend.** The contract requires all five
   properties, but the backend treats a missing one as empty
   (`backend/src/main/java/vn/tsolve/ticket/dto/SolutionSections.java:14-20`). A stricter contract is harmless.

## Assumptions made

- **What "every error" covers.** I counted as a contract gap every status and message code the backend can actually
  return for an operation, including:
  - the FIT chain's 503 MSG06 (JWKS);
  - the 403 MSG08 for an unregistered Jira site (a stand-in until platform's registry).

  If the team treats the JWKS 503 as a global response that does not need listing per operation, then Issue 2 goes
  away. Issues 1, 3, 4 and 5 still fail the task. **Open judgment call for the user.**
- **Renamed parameter.** I took the spec's `GET /api/v1/jira-app/tickets/{ticketId}` as met by `{ticketUuid}`,
  because the URL template is the same and the reason is written down. **Open judgment call for the user** if the
  spec's name was meant literally.
- **Later commits.** I judged the contract at HEAD 221d53ca. The 21 commits after f16c8a02 changed only the session
  (Jira link) paths and `.env.example` comments, not the jira-app paths.

## Test Report

See task-1-solution-contract-test-report.md

Checks I ran myself on 2026-11-13 (no Maven):

- `npm run generate:api` in `frontend/`: completed, no diff under `src/shared/api/generated` (`git status` clean
  apart from untracked harness artifacts that were already there).
- `npm run typecheck` in `frontend/`: exit 0.
