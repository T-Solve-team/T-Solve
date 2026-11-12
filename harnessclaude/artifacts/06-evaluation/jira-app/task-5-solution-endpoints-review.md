---
module: jira-app
feature: solution-endpoints
task: 5
verdict: pass
attempt: 2
date: 2026-11-13
reviewer: harnessclaude-reviewer
iteration: 2
---

> Attempt 2. Attempt 1 (verdict fail) is this file at commit `2e46b72b`.

# Review: jira-app — Task 5: Jira App endpoints (solution-endpoints, KAN-126)

Scope: the original commit `47fa92fe` and the fix commit `ccfee5d6` (repo `default`), judged on the current HEAD
`ccfee5d6` of `feat/KAN-126-jira-app-solution-form-v2`. The working tree was clean.
Contract: `harnessclaude/artifacts/03-modules/jira-app/spec.md` (Interface Contracts) and `frontend/openapi/tsolve-api.yaml`.
Edge cases: `harnessclaude/artifacts/04-plans/jira-app/iteration-2/design.md`.
Passes run: code-review (spec compliance, then quality) and security-review. The endpoints are authenticated and handle
personal data and access rules.

## Summary

Attempt 1 failed on Integration for three blocking issues. All three are fixed:

1. **403 MSG08 on `solution-form` and `panel` is now declared.** Both the `@ApiResponse` annotations and the contract
   declare it, together with the 503 MSG06 that the security chain sends when it cannot check the token.
2. **The documented errors now match the code** on all seven endpoints. I checked every annotation against the YAML and
   against the code path that raises each error.
3. **`webUrl` can no longer be relative.** The backend refuses to start unless `TSOLVE_WEB_ORIGIN` is an absolute
   http(s) origin (BUG-205). A parameterised test covers the bad values.

The Forge client change (BUG-204) is also correct. After a retry, only a 409 **MSG31** counts as submitted. A 409 MSG40
is now passed back to the user, and a test proves it.

Did the fix break anything? I checked the risk the dispatcher raised:
- The test config `backend/src/test/resources/config/application.yml:3` now sets `TSOLVE_WEB_ORIGIN: https://web.example.test`.
  Every Spring context in the test suite loads that file, including the three ITs that start the app with
  `SpringApplicationBuilder` (`KeycloakItStack.java:100`, `KeycloakSignInEndToEndIT.java:104`, `TwoInstanceCallbackIT.java:110`).
- No test overrides the variable or `spring.config.location`.
- The only test that builds `JiraAppTicketServiceImpl` directly passes an absolute origin (`JiraAppTicketServiceImplTest.java:51`).
- `@WebMvcTest` controller tests mock the service, so the constructor never runs there.
- The backend is not in `docker-compose.yml`, and CI passes no environment to Maven, so the test config is what applies.

I found no regression. One new design-level concern is a judgment call for the user (Issue 1 below): the link uses one
origin for every Workspace. It does not block the verdict.

**The verdict assumes the backend suite passes.** I was told not to run Maven, so the pass depends on the main
session's `mvn verify` being green. If it fails, this review fails too.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | The business logic has not changed since attempt 1, and every Stage 1 row is still PASS. The only change is the start-up check in `JiraAppTicketServiceImpl.java:47,54-67`, which makes AC-009.1's "Open in T-Solve" link absolute (`:108`). |
| Completeness | pass | Every criterion of the task has a test that names it (Traceability). AC-005.3 belongs to Task 2 and is tested outside the module's trace paths (`SolutionDraftServiceImplTest.java:62`, `SolutionFormTicketIT.java:165`). |
| Correctness | pass (pending `mvn verify`) | I ran the Forge app: jest 125/125 passed across 11 suites, and `solutionClient.test.js` passed 14/14, including the BUG-204 test. `npm run lint` was clean. I did not run Maven (dispatcher rule). By reading, the new test `refusesToStartWithoutAnAbsoluteWebOrigin_BUG_205` (`JiraAppTicketServiceImplTest.java:126-133`) checks seven bad values, and the existing preview test checks the absolute link (`:98`). |
| Integration | pass | The annotations and `tsolve-api.yaml` agree for all seven endpoints (table below). `webUrl` now satisfies `format: uri` (`tsolve-api.yaml:2789-2791`). The generated client changed only where a `ProblemDetail` 403 was added. See Issue 1 for the multi-Workspace concern. |

### Attempt-1 blocking issues

| # | Attempt-1 issue | Status | Evidence (current lines) |
|---|-----------------|--------|--------------------------|
| 1 | No 403 MSG08 on `solution-form` and `panel` | Fixed | `JiraAppSolutionController.java:45-46`, `JiraAppPanelController.java:33-34`; `tsolve-api.yaml:899-910` (solution-form), `:1055-1066` (panel) |
| 2a | `search` contract said MSG76; the annotation said MSG08 | Fixed: both now say "MSG62 or MSG08" | `JiraAppTicketController.java:37`, `tsolve-api.yaml:1098-1099`. MSG76 is left to the panel flag `canSearch` (`JiraAppSolutionServiceImpl.java:129-135`), which matches AC-008.6 ("the panel SHALL show MSG76"). |
| 2b | `getJiraAppTicket` had no MSG62 and no 400 | Fixed | `JiraAppTicketController.java:49-50`, `tsolve-api.yaml:1132,1136-1143` |
| 2c | `saveSolutionDraft` had no 400 | Fixed | `JiraAppSolutionController.java:71` |
| 2d | `rewriteSolution` had no 400 | Fixed | `JiraAppSolutionController.java:85` |
| 2e | `submitSolution` 409 named only "already has a ticket" | Fixed: MSG31 and MSG40 | `JiraAppSolutionController.java:59`, `tsolve-api.yaml:950-957`; the code is at `SolutionFormTicketServiceImpl.java:103-104` |
| 3 | `webUrl` was relative when `TSOLVE_WEB_ORIGIN` was empty | Fixed (BUG-205) | `JiraAppTicketServiceImpl.java:54-67`; `.env.example:98-100`; test config `application.yml:3`; test `JiraAppTicketServiceImplTest.java:126-133` |

Every 503 MSG06 that was added is real. `JiraAppSecurityConfig.java:45-48` answers 503 MSG06 when the JWKS cannot be
fetched (`AuthenticationServiceException`), before any controller runs.

The BUG-204 fix (`tsolveClient.js:86`) relies on the backend sending `code: "MSG31"`. The controller test checks that
(`JiraAppSolutionControllerTest.java:100-110`), so the retry path still works.

### Stage 1 — Spec compliance (re-checked on HEAD)

The business logic has not changed since attempt 1, so the twelve rows of the attempt-1 table still hold. Line numbers
that moved:
- `openForm` is at `JiraAppSolutionServiceImpl.java:61-76`, and the panel is at `:109-127`.
- Search and preview are at `JiraAppTicketServiceImpl.java:69-110`.
- The PUBLISHED and ACTIVE-Department SQL is at `TicketRepository.java:106-117`, reached through `PublishedTicketServiceImpl.java:25-29`.

EXTRA (accepted in attempt 1, still present): `findStatusByTicketId` blocks the form for a ticket of any source
(`TicketRepository.java:119-121`).

## Traceability

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

AC-005.3 is not one of this task's criteria. Task 2 owns it, and it is tested in
`backend/src/test/java/vn/tsolve/ticket/service/impl/SolutionDraftServiceImplTest.java:62` (`returnsOnlyTheCreatorsOwnDraft_AC_005_3`)
and `backend/src/test/java/vn/tsolve/ticket/SolutionFormTicketIT.java:165` (`keepsOneDraftPerCreatorAndIssue_AC_005_1_AC_005_3`).
The trace does not see them because it searches only the module's own paths.

## Issues

### Blocking

None.

### New in attempt 2 (informational)

1. **[Integration, Medium — judgment call] "Open in T-Solve" uses one origin for every Workspace.**
   - `webUrl` is `webOrigin + "/tickets/" + id` (`JiraAppTicketServiceImpl.java:108`), where `webOrigin` is a single
     global `TSOLVE_WEB_ORIGIN` (`:42`).
   - Workspace sites are per slug (`.env.example:39-41`, `TSOLVE_SITE_ORIGIN_TEMPLATE`, production `https://{slug}.tsolve.io.vn`).
     The Jira App already maps several Jira sites to several Workspaces (`ConfigJiraWorkspaceLookup.java`, `TSOLVE_STUB_JIRA_SITES`).
     The `.env.example:98-99` comment itself says "e.g. https://<workspace>.tsolve.io.vn".
   - With more than one Workspace, the link for every Workspace except the configured one opens the wrong site. The user
     lands on a sign-in page or a "not found" page for a ticket that belongs to another tenant.
   - The spec (AC-009.1) asks only for "an 'Open in T-Solve' link", and design.md does not cover how the link is built.
     So I did not block on this.
   - Fix (if the user agrees): build the origin from `TSOLVE_SITE_ORIGIN_TEMPLATE` and the caller's Workspace slug
     (the Workspace is already resolved in `JiraIssueAccessImpl.java:55-59`). Keep the start-up check for the template.
2. **[Config, Low] The start-up check accepts an origin that has a path, query or fragment.**
   - `absoluteOrigin` (`JiraAppTicketServiceImpl.java:54-67`) checks only the scheme and the host. A value such as
     `https://acme.tsolve.io.vn/?x=1` passes and produces `https://acme.tsolve.io.vn/?x=1/tickets/<id>`.
   - Fix: also require `uri.getRawPath()` to be empty and `getRawQuery()` and `getRawFragment()` to be null. Add those
     values to the `@ValueSource` at `JiraAppTicketServiceImplTest.java:128`.
3. **[Config, Low] `.env.example:100` now ships `http://localhost:5173`.**
   - A deployment that copies `.env.example` and forgets this value passes the start-up check, but every link points to localhost.
   - This is acceptable for local development. Consider leaving it empty in a production checklist, or mentioning it in
     the deploy notes.

### Still open from attempt 1 (informational, unchanged)

4. **[Security, Medium] The issue key and the summary come from the client.**
   - `submit` creates a ticket for any issue key whose project prefix matches a Project the caller belongs to
     (`JiraIssueAccessImpl.java:62-71`). Its title is the client's `summary` (`JiraAppSolutionServiceImpl.java:83-89`),
     limited only by `@NotNull @Size(max = 255)` (`SubmitSolutionRequest.java:10`) and the 200-code-point cut
     (`SolutionFormTicketServiceImpl.java:93-94`).
   - Jira is never asked whether the issue exists. A member can therefore reserve a future issue key, which then gets
     409 MSG31 forever. Still a judgment call.
5. **[Test gap] The new SQL has no database test.**
   - `TicketRepository.findPublished` (`:106-117`, the PUBLISHED and ACTIVE-Department filter behind AC-009.2,
     including the legacy hex Project join) has no database test. Neither has `JiraProjectDepartmentRepository.departmentOf` (`:27`).
   - The unit tests mock both.
6. **[Test realism] The AC-008.8 503 path is mostly unreachable.**
   - `answers503Msg06WhenTheSearchIsUnavailable_AC_008_8` (`JiraAppSolutionControllerTest.java:175-181`) mocks an
     `AiUnavailableException`.
   - In production, `TicketRankingServiceImpl.java:49-52` catches that exception and falls back to keyword search, so a
     real failure answers 500 MSG06. The panel still shows MSG06 either way.
7. **[Test gap] The role check is not tested.**
   - No `@WebMvcTest` uses an authenticated caller without `ROLE_JIRA_APP`. Every test uses `fit()`
     (`JiraAppSolutionControllerTest.java:204`).
   - The only 401 test is `refusesACallWithoutFit` (`:135`).
   - The 503 MSG06 for a JWKS failure (`JiraAppSecurityConfig.java:45-48`) is documented on every endpoint now, but this
     controller test does not cover it.
8. **[Security, Low] The panel shows a ticket's status to linked non-members.**
   - `tickets.findStatus(issueKey)` runs for any linked caller before the membership check
     (`JiraAppSolutionServiceImpl.java:115`, returned at `:122`).
   - Fix: return the status only when `context.member()` is true.
9. **[DoS, Low] The AI rewrite has no size limit.**
   - `rewrite` needs only a linked account (`JiraAppSolutionServiceImpl.java:104-106`).
   - `SolutionSections` has no `@Size` (`SolutionSections.java:9-10`).
   - The pool has `JIRA_APP_AI_THREADS` threads and an equal-size queue (`SolutionFormAiImpl.java:204-214`). One user can
     fill it, so other users' drafts fall back to MSG67 and their rewrites fail with MSG68.
   - Fix: limit the total to 10,000 code points (400 MSG02), the same rule as submit.
10. **[Performance, Low] Search makes up to 20 extra queries and can return fewer than 20 results.**
    - `canAccessDepartment` runs once per result (`JiraAppTicketServiceImpl.java:74-78`).
    - The page of 20 is taken before the second filter, so fewer than 20 results can come back even when more match.
11. **[Consistency, Low] The submit response carries a different ticket id.**
    - `SubmittedTicketResponse.id` is the internal `ticket_id` (`RETURNING ticket_id`, `TicketRepository.java:64`;
      returned at `JiraAppSolutionServiceImpl.java:90`).
    - Search and preview return `public_id` (`TicketRepository.java:107`).
    - The contract calls both "the ticket's UUID".

## Assumptions made

- **One web origin for all Workspaces (Issue 1).** The spec does not say where "Open in T-Solve" leads beyond "the
  T-Solve web Ticket Detail", so I treated the single-origin link as non-blocking. **Judgment call for the user:**
  accept one origin while the site registry is a stub, or build the link from the caller's Workspace site now?
- **The issue key and summary come from the client (Issue 4).** Carried over from attempt 1, and still undecided.
  **Judgment call:** accept and record the risk in the design, or require a server-side check of the issue?
- **A ticket of any source blocks the form.** Carried over from attempt 1 (`TicketRepository.java:119-121`) and
  accepted, as the design justifies it. **Judgment call:** is this what the BA intended?
- **MSG76 for search.** The search endpoint no longer documents 403 MSG76. AC-008.6 places MSG76 on the panel, not on
  the endpoint, so I read this as correct and not as a removed requirement.
- **Correctness depends on the main session's `mvn verify`.** I did not run Maven (dispatcher rule). I ran the Forge
  app's jest suite and lint. I did not run `npm run generate:api`, because it rewrites (`clean: true`) the tracked
  `frontend/src/shared/api/generated` folder, and I may not edit code. The committed generated diff changes exactly the
  three operations that gained a `ProblemDetail` 403 (`openSolutionForm`, `saveSolutionDraft`, `getIssuePanel`), which
  is consistent with the YAML. The main session's frontend verify should confirm it leaves no diff.

## Test Report

See task-5-solution-endpoints-test-report.md (attempt 2 section)
