---
module: jira-app
feature: solution-contract
task: 1
verdict: pass
attempt: 2
date: 2026-11-13
reviewer: harnessclaude-reviewer
iteration: 2
---

> Attempt 2. Attempt 1 (verdict fail) is this file at commit `2e46b72b`.

# Review: jira-app — Task 1: Solution Form and Issue Panel contract

## Summary

Attempt 1 failed because the contract left out errors the backend really returns. The fix commit ccfee5d6 changes
`frontend/openapi/tsolve-api.yaml`, the three controllers' `@ApiResponse`, the Forge client's 409 rule (BUG-204) and
the web-origin check (BUG-205). I judged HEAD ccfee5d6.

All five CRITICAL issues of attempt 1 are fixed, and so are Informational issues 6 and 7. Issues 8 and 9 were
informational and are unchanged; they still do not block.

I traced every error path again for the seven operations. The sources were:

- the FIT security chain;
- `JiraIssueAccessImpl`;
- `JiraIssueContext.requireMember`;
- the solution, ticket, Draft, AI and search services;
- bean validation on the controllers;
- `GlobalExceptionHandler`.

Every 4xx/503 status and message code they can raise is now in the contract, and each one matches the controller's
`@ApiResponse`. `npm run generate:api` leaves no diff, and `npm run typecheck` passes.

The verdict is pass. Three informational notes remain (Issues 1 to 3 below).

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | The seven paths, methods, operationIds and schemas match the controllers and DTOs. Every error the backend raises is documented (error matrix below). |
| Completeness | pass | The Exposed table rows are all present. `.env.example` has `TSOLVE_STUB_AI` and, now, a required absolute `TSOLVE_WEB_ORIGIN` (`.env.example:100`). |
| Correctness | pass | `npm run generate:api`: no diff. `npm run typecheck`: no errors. I did not run Maven or the Forge app's tests, as the dispatcher told me. The new tests `refusesToStartWithoutAnAbsoluteWebOrigin_BUG_205` and `gives MSG40 back after a retry … (BUG-204)` must pass in the main session's run. |
| Integration | pass | The Forge client now treats a 409 after a retry as "submitted" only when the code is MSG31 (`jira-app/TSolve_JiraApp/src/frontend/api/tsolveClient.js:86`), so a MSG40 is passed back to the caller (test at `solutionClient.test.js:77`). Backend DTO field names still match the contract. |

### Attempt-1 issues

| # | Attempt-1 issue | Status | Evidence at HEAD |
|---|-----------------|--------|------------------|
| 1 | `openSolutionForm` / `getIssuePanel` had no 403 | Fixed | `tsolve-api.yaml:899`, `:1055` (MSG08). Controllers: `JiraAppSolutionController.java:45`, `JiraAppPanelController.java:33` |
| 2 | 503 MSG06 (JWKS) missing on five operations | Fixed | `tsolve-api.yaml:905`, `:958`, `:992`, `:1061`, `:1144`; rewrite `:1028` and search `:1104` name MSG06 as well. Controllers: `JiraAppSolutionController.java:46`, `:60`, `:73`, `:87`; `JiraAppPanelController.java:34`; `JiraAppTicketController.java:38-40`, `:51` |
| 3 | Search 403 named MSG76 | Fixed | `tsolve-api.yaml:1098-1099`: "MSG62 … or MSG08". Matches `JiraAppTicketController.java:37` |
| 4 | Preview 403 left out MSG62 | Fixed | `tsolve-api.yaml:1136-1139`. Matches `JiraAppTicketController.java:50` |
| 5 | Submit 409 had no codes and left out MSG40; the Forge client counted any 409 after a retry as a success | Fixed | `tsolve-api.yaml:950-953` names MSG31 and MSG40; `JiraAppSolutionController.java:59`; `tsolveClient.js:86`; BUG-204 at `docs/bug-log.md:214` |
| 6 | `webUrl` could be relative | Fixed | `JiraAppTicketServiceImpl.java:47`, `:54-67` refuse to start without an absolute http(s) origin. Test at `JiraAppTicketServiceImplTest.java:126-133`; BUG-205 at `docs/bug-log.md:215`; test config `backend/src/test/resources/config/application.yml:3` |
| 7 | Rewrite 503 named only MSG68 | Fixed | `tsolve-api.yaml:1028-1030`; `JiraAppSolutionController.java:87` |
| 8 | Names differ from the task text | Unchanged (informational) | The spec's Exposed table fixes none of these names. The backend and the Forge client use the contract's names. |
| 9 | `SolutionSections` stricter in the contract | Unchanged (informational) | Harmless. |

### Error matrix (backend source → contract)

| Operation | Errors the backend can raise | Contract | Controller `@ApiResponse` |
|-----------|------------------------------|----------|---------------------------|
| `openSolutionForm` | 400 (bad key `@Pattern`/`@Size`, body `@NotNull`/`@Size`); 401 MSG08 (FIT entry point); 403 MSG08 (role check `JiraAppSecurityConfig.java:42-43`; no accountId / unregistered site `JiraIssueAccessImpl.java:55-60`); 503 MSG06 (JWKS `JiraAppSecurityConfig.java:45-48`). No MSG62: an unlinked or non-member viewer gets 200 `mayOpen:false` (`JiraAppSolutionServiceImpl.java` `openForm`), and the AI draft never throws (`SolutionFormAiImpl` `draft` catches every failure) | 400/401/403/503 | 400/403/503 |
| `submitSolution` | 400 MSG01 + field / MSG02 (`SolutionFormTicketServiceImpl.java:75-83`, bean validation); 401; 403 MSG62 (`JiraIssueContext.java:22`) / MSG08 (`:23-25`, the role check, issue access); 409 MSG31 / MSG40 (`SolutionFormTicketServiceImpl.java:101-105`, `GlobalExceptionHandler.java:82-84`); 503 MSG06 (JWKS) | 400/401/403/409/503 | 400/403/409/503 |
| `saveSolutionDraft` | 400 (key, body); 401; 403 MSG62/MSG08 (`requireMember`, issue access); 503 MSG06 (JWKS). The Draft save itself adds no 4xx | 400/401/403/503 | 400/403/503 |
| `rewriteSolution` | 400 (body); 401; 403 MSG62 (`JiraIssueAccessImpl.java:51`) / MSG08; 503 MSG68 (`SolutionFormAiImpl` `rewrite`) / MSG06 (JWKS) | 400/401/403/503 | 400/403/503 |
| `getIssuePanel` | 400 (key); 401; 403 MSG08 (issue access, the role check); 503 MSG06 (JWKS). An unlinked viewer gets 200 `linked:false`, not MSG62 | 400/401/403/503 | 400/403/503 |
| `searchJiraAppTickets` | 400 MSG01/MSG02 with field `q` (`TicketSearchQuery.parse`, called at `JiraAppTicketServiceImpl.java:71`; the fixed page/size cannot give the other 400 codes); 401; 403 MSG62/MSG08 (`asUser`, `:72`); 503 MSG06 (JWKS) | 400/401/403/503 | 400/403/503 |
| `getJiraAppTicket` | 400 (not a UUID); 401; 403 MSG62 / MSG08 (`asUser` at `JiraAppTicketServiceImpl.java:99`, not visible / not Published / no such ticket at `:109`); 503 MSG06 (JWKS) | 400/401/403/503 | 400/403/503 |

No controller lists 401. The contract lists 401 for every operation. This follows the older `JiraAppQuestionController`
(`:43-45`), which also leaves 401 out, so I do not count it as a mismatch. A 500 with MSG06
(`GlobalExceptionHandler.java:106-112`) can happen on any operation. It is global, and the contract lists it on none.

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

About AC-005.3 ("THE server SHALL show a Draft only to its creator"): this task does not own it. Its tests are in the
`ticket` module (Task 2), outside the module's trace paths:

- `backend/src/test/java/vn/tsolve/ticket/service/impl/SolutionDraftServiceImplTest.java:62`
  `returnsOnlyTheCreatorsOwnDraft_AC_005_3`
- `backend/src/test/java/vn/tsolve/ticket/SolutionFormTicketIT.java:165`
  `keepsOneDraftPerCreatorAndIssue_AC_005_1_AC_005_3`

So it does not count against this task. Task 1 owns no runtime criterion; its deliverable is the contract, checked in
the error matrix above.

## Issues

### CRITICAL (blocks pass)

No issues found.

### Informational (does not block)

1. **The search 503 text promises a case the backend never answers with 503.**
   - Where: `frontend/openapi/tsolve-api.yaml:1104-1105` and
     `backend/src/main/java/vn/tsolve/jiraapp/controller/JiraAppTicketController.java:38-40` both say "MSG06 — the
     search is unavailable, or the Forge token could not be checked".
   - What the backend does: when the AI embedding fails, the search falls back to keyword search
     (`backend/src/main/java/vn/tsolve/search/query/service/impl/TicketRankingServiceImpl.java:49`). Any other search
     failure is a 500 MSG06 (`backend/src/main/java/vn/tsolve/common/exception/GlobalExceptionHandler.java:106-112`).
     So 503 on search means only the JWKS failure.
   - Why it does not block: AC-008.8 is met, because the panel shows MSG06 for any failure.
   - Fix: say "MSG06 — the Forge token could not be checked; a search failure is a 500 MSG06", or leave as is.
2. **`TSOLVE_WEB_ORIGIN` is now a start-up requirement (BUG-205).**
   - The backend refuses to start without an absolute http(s) origin
     (`backend/src/main/java/vn/tsolve/jiraapp/service/impl/JiraAppTicketServiceImpl.java:54-67`).
   - No deployment file in the repo sets it. Only `.env.example:100` and the test config
     `backend/src/test/resources/config/application.yml:3` do, which is the same pattern as `OLLAMA_BASE_URL`.
   - Every deployed environment must add it before this branch is deployed, or the backend will not start.
   - The main session's `mvn verify` confirms that the Spring test contexts still start.
3. **Names differ from the task text (attempt-1 Issue 8, unchanged).**
   - `origin` `AI_DRAFT`, `message` MSG67 instead of `aiUnavailable`, `mayOpenForm`/`canSearch`, and `{ticketUuid}`.
   - The spec fixes none of these names, and the backend and the Forge client agree with the contract.

## Assumptions made

- **What "every error" covers.** As in attempt 1, I counted every 4xx and 503 the backend can return for an operation:
  - the JWKS 503 MSG06;
  - the 403 MSG08 for an unregistered site.

  I did not count the global 500 MSG06 or the 401, which the controllers leave out on every jira-app operation, the
  older ones included. **Open judgment call for the user:** should the controllers list 401 too? This does not change
  the verdict.
- **Renamed parameter.** I took `GET /api/v1/jira-app/tickets/{ticketId}` in the spec as met by `{ticketUuid}`: the
  URL template is the same, and the reason is written at `tsolve-api.yaml:1115`. **Open judgment call for the user**,
  carried over from attempt 1.
- **Tests I did not run.** The dispatcher told me not to run Maven, and to run only `generate:api` and `typecheck` in
  `frontend/`. The pass assumes the main session's backend run passes, including
  `JiraAppTicketServiceImplTest.refusesToStartWithoutAnAbsoluteWebOrigin_BUG_205`. It also assumes the Forge app's
  `solutionClient.test.js` (BUG-204 test) passes. If either fails, the verdict becomes fail.

## Test Report

See task-1-solution-contract-test-report.md (attempt 2 section)

Checks I ran myself on 2026-11-13 at HEAD ccfee5d6 (no Maven):

- `npm run generate:api` in `frontend/`: it completed, and `git status` is clean afterwards (no diff in
  `src/shared/api/generated`).
- `npm run typecheck` in `frontend/`: no errors.
- `bun harnessclaude/hooks/sdd.ts trace …`: 34/35, see Traceability.
