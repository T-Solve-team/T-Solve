---
module: jira-app
feature: solution-endpoints
task: 5
verdict: fail
attempt: 1
date: 2026-11-13
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: jira-app — Task 5: Jira App endpoints (solution-endpoints, KAN-126)

Scope: commit `47fa92fe` (repo `default`), judged on the current HEAD of `feat/KAN-126-jira-app-solution-form-v2`.
Commits after it changed only formatting in these files (`221d53ca`, `0077864a`).
Contract: `harnessclaude/artifacts/03-modules/jira-app/spec.md`, the Interface Contracts section and
`frontend/openapi/tsolve-api.yaml`. Edge cases come from `harnessclaude/artifacts/04-plans/jira-app/iteration-2/design.md`.
Review passes run: code-review (spec compliance, then quality) and security-review (STRIDE). These endpoints are
authenticated and act on personal data and access rules.

## Summary

The business logic does what the spec asks for every criterion this task lists:
- the may-open rule;
- the Draft, then the AI draft, then the issue text with MSG67;
- 409 on a second ticket;
- the panel flags;
- search limited to the user's Departments and to PUBLISHED tickets;
- 403 MSG08 for a preview outside those Departments.

Authorization is correct on every endpoint:
- All seven endpoints carry `@PreAuthorize(hasRole(JIRA_APP))`, and the FIT chain also requires the role.
- The user comes only from the FIT principal (`@AuthenticationPrincipal JiraAppPrincipal`). No request body or parameter carries a user id.
- The AI draft and the AI rewrite run outside any transaction. `JiraAppSolutionServiceImpl` has no `@Transactional`, each ticket or Draft call has its own short transaction, and `open-in-view` is false.

The feature fails on **Integration**, for two reasons:
1. **The code and the contract disagree on errors.** The source of truth, `tsolve-api.yaml`, does not list errors the code returns, and it lists one error the code never returns. The `@ApiResponse` annotations are also incomplete. The most important gap: `solution-form` and `panel` can answer 403 MSG08 (from the issue access check), but neither the annotations nor the contract declare it.
2. **`webUrl` is broken when `TSOLVE_WEB_ORIGIN` is empty,** which is the default in `.env.example`. The preview then returns a relative path, which breaks the "Open in T-Solve" link and violates the contract's `format: uri`.

Neither is a security hole, but the project rules make the contract binding (backend/AGENTS.md §6), so both must be fixed.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Every criterion in Stage 1 below is met by the code; see the evidence column. |
| Completeness | pass | Every criterion of the task has a test that names it. AC-005.3 is not one of this task's criteria; it is tested in the `ticket` module (`SolutionDraftServiceImplTest:62`, `SolutionFormTicketIT:165`). |
| Correctness | not judged here | I was told not to run Maven; the main session runs the backend suite and writes the test report. Reading the tests: they check behaviour, not only status codes. Gaps are listed under Issues (items 5 to 7). |
| Integration | **fail** | The documented errors do not match the code or `tsolve-api.yaml` (Issue 1, 2). `webUrl` does not match `format: uri` when the origin is empty (Issue 3). |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-001.2 the form only for a Project's issue, a member, and no ticket | PASS | `JiraAppSolutionServiceImpl.java:63`, `JiraIssueAccessImpl.java:62-71`; tests `doesNotOpenForANonMemberOrAnIssueWithATicket_AC_001_2_AC_001_5`, `refusesToSubmitForANonMember_AC_001_2`, `answers403Msg08ForANonMember_AC_001_2` |
| 2 | AC-002.1 AI draft when there is no Draft | PASS | `JiraAppSolutionServiceImpl.java:71-73`; `opensWithTheAiDraftWithoutADraft_AC_002_1` |
| 3 | AC-002.2 issue text in Symptoms and MSG67 when the AI is unavailable | PASS | `JiraAppSolutionServiceImpl.java:74`, `SolutionFormAiImpl.java:91-105`; `opensWithTheIssueTextAndMsg67WhenTheAiIsUnavailable_AC_002_2` |
| 4 | AC-004.3 no second ticket, 409 | PASS | `SolutionFormTicketServiceImpl.java:63,101-104` (`ON CONFLICT DO NOTHING`, Task 2); `answers409WhenTheIssueAlreadyHasATicket_AC_004_3`; the race is tested in `SolutionFormTicketIT:134` |
| 5 | AC-005.2 the Draft before the AI draft | PASS | `JiraAppSolutionServiceImpl.java:67-70`; `opensWithTheUsersDraftFirst_AC_005_2` checks that no AI call is made |
| 6 | AC-008.2 MSG65 state (ticket status) | PASS | `JiraAppSolutionServiceImpl.java:115,120-125`; `showsTheTicketStatusAndNoFormButtonOnceSubmitted_AC_008_2`, `showsThePanelState_AC_008_2` |
| 7 | AC-008.3 Draft and no ticket → MSG77 flag | PASS | `JiraAppSolutionServiceImpl.java:116-119`; `showsTheDraftAndTheFormButtonWhileNoTicketExists_AC_001_4_AC_008_3` |
| 8 | AC-008.4 PUBLISHED tickets of the user's Departments, in rank order | PASS | `JiraAppTicketServiceImpl.java:49-73`, `TicketRepository.java:106-117`; `listsPublishedTicketsOfTheUsersDepartmentsInRankOrder_AC_008_4`, `listsTheSearchResults_AC_008_4` |
| 9 | AC-008.5 nothing found → empty list | PASS | `findsNothingWhenNoResultIsVisible_AC_008_5` |
| 10 | AC-008.6 MSG76 flag | PASS | `JiraAppSolutionServiceImpl.java:130-135`; `hidesTheSearchWhenTheUserSeesNoDepartmentOfTheIssue_AC_008_6` |
| 11 | AC-008.8 search unavailable → MSG06 | PASS (weak) | `answers503Msg06WhenTheSearchIsUnavailable_AC_008_8` mocks an `AiUnavailableException`. In production, `TicketRankingServiceImpl.java:47-52` catches that exception and falls back to keyword search, so a real failure is a database error, which answers 500 MSG06. The panel still gets MSG06 either way (Issue 6). |
| 12 | AC-009.2 not Published or not in the user's Departments → 403 MSG08 | PASS | `JiraAppTicketServiceImpl.java:78-88`, `TicketRepository.java:115`; `refusesATicketOfAnotherDepartmentOrNotPublished_AC_009_2`, `answers403Msg08ForATicketOutsideTheUsersDepartments_AC_009_2`. The SQL filter itself has no database test (Issue 5). |

EXTRA: `findStatus` blocks the form for a ticket of **any** source with the same Ticket ID, not only a Solution Form
ticket (`TicketRepository.java:119-121`). The design (Task 2, 12/11) justifies this: `uq_ticket_ticket_id` would
refuse the insert anyway. I accepted it; see Assumptions.

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

Every criterion this task lists has a test. AC-005.3 is outside this task. It belongs to Task 2 and is tested in
`backend/src/test/java/vn/tsolve/ticket/service/impl/SolutionDraftServiceImplTest.java:62` and
`backend/src/test/java/vn/tsolve/ticket/SolutionFormTicketIT.java:165`. The trace only searches the module's own paths, so it does not see them.

## Issues

### Blocking (must fix before pass)

1. **Undeclared 403 MSG08 on `solution-form` and `panel`.**
   - `JiraIssueAccessImpl.java:55-59` throws `JiraIssueAccessDeniedException` (403 MSG08) when the FIT has no `accountId` or the site has no registered Workspace. The filter chain (`JiraAppSecurityConfig.java:42-43`) also answers 403 MSG08 when the role is missing.
   - Neither endpoint declares a 403:
     - `JiraAppSolutionController.java:44` (`openSolutionForm`) declares only 400.
     - `JiraAppPanelController.java:32` (`getIssuePanel`) declares only 400.
     - `frontend/openapi/tsolve-api.yaml:869-897` (solution-form) and `:1000-1019` (panel) list only 200, 400 and 401.
   - Fix: add `'403'` (MSG08: no Jira user, or no Workspace registered for the site) to both paths in the YAML first. Then add `@ApiResponse(responseCode = "403", description = "MSG08")` to both methods, and run `npm run generate:api`.

2. **The documented errors do not match the code on other endpoints.**
   - `searchJiraAppTickets`:
     - The contract says 403 is "MSG62 or MSG76" (`tsolve-api.yaml:1050-1055`), but no code path returns MSG76. The search endpoint never applies the AC-008.6 rule; only the panel flag `canSearch` does.
     - The code answers 403 MSG08 for an unregistered site, which the contract does not mention. The annotation says "MSG62 or MSG08" (`JiraAppTicketController.java:37`), so the annotation and the contract disagree.
     - Fix: make the contract say "MSG62 or MSG08". Alternatively, if the server should enforce MSG76, enforce it in `JiraAppTicketServiceImpl.search` and test it.
   - `getJiraAppTicket`: `asUser` can also answer 403 MSG62 for an unlinked account (`JiraIssueAccessImpl.java:50-51`). The annotation (`JiraAppTicketController.java:47`) and the contract (`tsolve-api.yaml:1086`) say MSG08 only. A malformed UUID answers 400, which the contract lists but the annotation does not. Fix: add MSG62 to both and add a 400 `@ApiResponse`.
   - `saveSolutionDraft`: a malformed issue key answers 400 (the test `refusesAMalformedIssueKey` proves it), but the annotations at `JiraAppSolutionController.java:68` have no 400. Fix: add it.
   - `rewriteSolution`: a malformed JSON body answers 400, but the annotations at `JiraAppSolutionController.java:80-81` have no 400. Fix: add it.
   - `submitSolution`: 409 can also be MSG40 when the Project stopped being ACTIVE (`SolutionFormTicketServiceImpl.java:101-104`). The contract's 409 says only "already has a Solution Form ticket" (`tsolve-api.yaml:936-941`). Fix: name both MSG31 and MSG40.

3. **`webUrl` is a relative path when `TSOLVE_WEB_ORIGIN` is empty.**
   - `JiraAppTicketServiceImpl.java:45,87` builds `webOrigin + "/tickets/" + id`. With the shipped default (`.env.example:99`, `TSOLVE_WEB_ORIGIN=`), this returns `/tickets/<uuid>`.
   - That value breaks the contract (`tsolve-api.yaml:2734-2737`: required, `format: uri`).
   - It is also truthy, so `TicketPreviewModal.jsx:65-66` renders an "Open in T-Solve" link that resolves against the Jira site and goes nowhere.
   - No test covers the empty origin: `JiraAppTicketServiceImplTest:48` always passes `https://acme.tsolve.io.vn/`.
   - Fix, pick one, and add a test named for AC-009.1:
     - refuse to start without an absolute `http(s)` origin; or
     - make `webUrl` nullable in the contract and return `null` when the origin is empty, so the modal hides the link.

### Informational (does not block)

4. **[Security, Medium] The server trusts the issue key and the summary sent by the client.**
   - `submitSolution` creates a ticket for any issue key whose project prefix matches a Project the caller is a member of (`JiraIssueAccessImpl.java:62-77`). Its title is whatever `summary` the client sends (`JiraAppSolutionServiceImpl.java:86`). The summary is validated for length only: `@NotNull @Size(max = 255)` on `SubmitSolutionRequest.java:10`, cut to 200 code points, with the issue key used when it is blank (`SolutionFormTicketServiceImpl.java:92-98`).
   - Neither the key nor the title is checked against Jira. A member who calls `invokeRemote` with a forged path could:
     - create a Pending review ticket for an issue that does not exist yet (for example `ITSUP-99999`), which, because of `uq_ticket_ticket_id`, stops the real issue from ever getting its Solution Form ticket (AC-004.3 answers 409); or
     - give a ticket a misleading title.
   - The risk is limited to insiders who are already members of that Project. The spec does not require the issue to be verified, and design.md chose "the backend needs no Jira token".
   - Fix (needs a decision): check the issue against the FIT context, if Forge puts the issue key in it, or read it from Jira with the app's token in `x-forge-oauth-system`. Otherwise record the accepted risk in the design. See Assumptions.

5. **[Test gap] The new SQL has no database test.**
   - The PUBLISHED and ACTIVE-Department filter that enforces AC-009.2 lives in `TicketRepository.findPublished` (`TicketRepository.java:106-117`), including the legacy hex Project join. Every test mocks it (`JiraAppTicketServiceImplTest:64,84,100-101`).
   - `JiraProjectDepartmentRepository.departmentOf` (used for AC-008.6) is untested as well.
   - Fix: add a Testcontainers IT that seeds one PUBLISHED, one PENDING_REVIEW and one deleted-Department ticket, and checks that only the first is returned.

6. **[Test realism] The AC-008.8 controller test cannot happen in production.**
   - `answers503Msg06WhenTheSearchIsUnavailable_AC_008_8` (`JiraAppSolutionControllerTest:174-181`) mocks `AiUnavailableException`. The search module catches that exception and falls back to keyword search (`TicketRankingServiceImpl.java:47-52`).
   - A real search failure is a database error, which answers 500 MSG06, not the 503 the contract documents.
   - The panel still shows MSG06, so AC-008.8 holds, but the contract's 503 for search is mostly unreachable.

7. **[Test gap] The role check is not tested.** The task asks for 401 and 403 paths in `@WebMvcTest`.
   - 401 is tested for `panel` only (`refusesACallWithoutFit`, `JiraAppSolutionControllerTest:134-138`).
   - The only 403 tested is one the service throws. No test uses an authenticated caller without `ROLE_JIRA_APP`.
   - `openSolutionForm` and `getJiraAppTicket` have no request-validation test (for example, a summary over 255 characters).

8. **[Security, Low] The panel reveals a ticket's status to linked non-members.**
   - `JiraAppSolutionServiceImpl.java:115` reads `tickets.findStatus(issueKey)` for any linked caller, before checking membership. A linked user can therefore learn the status of the ticket for any issue key in the Workspace, even in a Project they are not a member of.
   - Fix: return `ticketStatus` only when `context.member()` is true, or when the user is the writer.

9. **[DoS, Low] The AI rewrite is open to any linked user and has no size limit.**
   - `rewrite` needs only a linked account (`JiraAppSolutionServiceImpl.java:104-106`), not membership of any Project.
   - `SolutionSections` has no `@Size` limit, so any size of text reaches the LLM. Forge's 500 KB limit is the only bound.
   - The pool has 4 threads and a queue of 4 (`SolutionFormAiImpl.java:204-221`), so one user can make everyone else's drafts fall back to MSG67 and their rewrites fail with MSG68.
   - Fix: reject sections that total more than 10,000 code points with 400 MSG02, the same rule as submit. Consider a per-user rate limit.

10. **[Performance, Low] Search has extra queries and can return fewer than 20 results.**
    - `JiraAppTicketServiceImpl.java:54-56` calls `canAccessDepartment` once per result, up to 20 queries. Results usually share a few Departments; check each distinct Department once.
    - The page of 20 is taken before the second filter, so the panel can show fewer than 20 results even when more match.

11. **[Consistency, Low] The submit response carries a different ticket id.**
    - `SubmittedTicketResponse.id` is the internal `ticket.ticket_id` (from `RETURNING ticket_id`, `TicketRepository.java:64`).
    - Search and preview return `ticket.public_id`.
    - The contract calls both "the ticket's UUID". A client that sends the submit id to `getJiraAppTicket` gets 403.
    - Fix: return `public_id`.

## Assumptions made

- **Issue key and summary taken from the client (Issue 4).** The spec says only that the user comes from the FIT principal (NF 1). It does not require the server to check that the issue exists or that its summary is the real one. I treated this as non-blocking. **Judgment call for the user:** accept the risk as documented, or require a server-side check of the issue?
- **A ticket of any source blocks the form.** AC-001.2 and AC-004.3 speak of a "Solution Form ticket". The code blocks on a ticket of any source with the same Ticket ID (`TicketRepository.java:119-121`). The design justifies this with `uq_ticket_ticket_id`. I accepted it. **Judgment call:** is this what the BA intended?
- **Mismatched error documentation fails the Integration criterion.** backend/AGENTS.md §6 makes `tsolve-api.yaml` the source of truth, so I failed Integration on Issues 1 and 2 rather than listing them as informational.
- **"Departments the user is a member of" (AC-008.4, AC-009.2)** is read as identity's `canAccessDepartment`. That rule also lets a Project Manager see their own Department without a membership row. I accepted it as the shared scope rule (design, Task 3).
- **I did not run the tests.** The dispatcher forbade Maven, and the main session writes the test report, so my verdict here rests on reading the code and tests. If the suite fails, that is a further reason to fail.

## Test Report

See task-5-solution-endpoints-test-report.md
