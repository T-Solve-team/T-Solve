---
module: jira-app
feature: remove-jira-search
task: 9
verdict: pass
attempt: 1
date: 2026-11-14
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: jira-app — Task 9: Remove the Published-ticket search from the Jira App (remove-jira-search, KAN-126)

Scope: commit `94dd213a` (repo `default`, monorepo), checked again at the branch head `622c71e6` of
`feat/KAN-126-jira-app-solution-form-v2`. The working tree had one change unrelated to this task
(`jira-app/TSolve_JiraApp/package-lock.json`).
Contract: `harnessclaude/artifacts/03-modules/jira-app/spec.md` (Changelog row 2026-11-14 for STORY-008 and STORY-009,
STORY-008, Out of Scope, Interface Contracts).
Edge cases: `harnessclaude/artifacts/04-plans/jira-app/iteration-2/design.md`, section "Change — 2026-11-14: no
Published-ticket search in Jira".
Passes run: run-tests and trace, then scout (the removal touches shared code: `TicketRepository`, the OpenAPI contract,
the generated client), then code-review. No security review: the change only removes endpoints and adds no input path.

## Summary

The commit removes everything the spec change asks for, and nothing at HEAD still uses the removed code:

- **Backend:** these are gone: `JiraAppTicketController`, `JiraAppTicketService(Impl)`, `JiraAppTicketResult`,
  `JiraAppTicketPreview`, `JiraProjectDepartmentRepository`, `PublishedTicketService(Impl)`, `PublishedTicketView`,
  `TicketRepository.findPublished` with its `PublishedTicketRow` projection, and `IssuePanelResponse.canSearch`.
  `JiraAppSolutionServiceImpl` no longer depends on `ScopeAccessService` or the Department repository. `JiraIssueAccessImpl`
  still uses `ScopeAccessService`, but only for issue access, not for search.
- **Contract:** `GET /api/v1/jira-app/tickets/search` (`searchJiraAppTickets`) and
  `GET /api/v1/jira-app/tickets/{ticketUuid}` (`getJiraAppTicket`) are gone, with their schemas. `IssuePanel` no longer
  has `canSearch` and its `required` list was updated. `npm run generate:api` leaves no diff in
  `frontend/src/shared/api/generated`.
- **Forge app:** these are gone: `searchTickets`, `getTicket`, `TicketPreviewModal.jsx`, MSG04 and MSG76 in
  `messages.js`, and the search UI in `IssuePanel.jsx`. The panel now shows MSG62, MSG65, MSG64 (feedback), MSG77 with
  Continue draft, and Write Solution.
- **Config:** `TSOLVE_WEB_ORIGIN` is gone from `.env.example` and from the test `application.yml`.
- **Tests:** the tests for AC-008.4/5/6/8 and AC-009.1/2 are deleted. A new test checks that the panel has no search box
  (`IssuePanel.test.jsx`, "shows no Published-ticket search"). A controller test checks that `$.canSearch` no longer
  exists (`JiraAppSolutionControllerTest.java:166`).

Results: jest 141/141 passed in 12 suites, lint is clean, `mvn -q -B test-compile` succeeds, and the trace shows 4 of 4
STORY-008 criteria with a test. The dispatcher reports that CI #648 on `aa8ae95e` (which includes `94dd213a`) was green.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `IssuePanel.jsx` at HEAD shows only the Solution status. AC-008.1: MSG62 only (`:84-86`). AC-008.2: MSG65 (`:91-95`). AC-008.7: MSG64 feedback (`:96-100`). AC-008.3: MSG77 with Continue draft (`:101-112`). AC-001.4: Write Solution (`:113-119`). There is no search box, no results, no MSG04/MSG76 and no preview, which matches the changelog row of 2026-11-14 and Out of Scope. |
| Completeness | pass | Every item in the spec change is done: the two endpoints, the "search allowed" flag, the UI and the preview are removed, and `search` is no longer a dependency. No `vn.tsolve.search` import is left in `backend/src/main/java/vn/tsolve/jiraapp`. Every remaining STORY-008 criterion has a test (Traceability). |
| Correctness | pass | jest 141/141, lint clean, backend `test-compile` OK (the dispatcher allowed no `mvn verify`; CI #648 green). No test at HEAD names a removed criterion in this module. The `AC_008_4/5/6` and `AC_009_*` matches in `chatbot` and `review` belong to other modules' specs. |
| Integration | pass | OpenAPI, the generated client and the backend agree: `generate:api` leaves no diff. No other module used `PublishedTicketService`, `findPublished` or `JiraProjectDepartmentRepository`; the backend compiles and `git grep` at HEAD finds no reference. The web app's own search (`TicketSearchController.searchTickets`, `TicketRankingService`) has not changed. |

### Residual-reference check at HEAD (`622c71e6`)

`git grep -i -E "JiraAppTicket|PublishedTicketService|PublishedTicketView|JiraProjectDepartment|canSearch|TicketPreview|TSOLVE_WEB_ORIGIN|webOrigin|jira-app/tickets|searchJiraAppTickets|getJiraAppTicket|web-origin"`
over the whole repo except `harnessclaude/` and `docs/`:

| Area | Result |
|------|--------|
| `backend/` | Only `JiraAppSolutionControllerTest.java:166` (`$.canSearch` doesNotExist). This assertion is intended. The `getWebOrigins` matches are in Keycloak realm ITs and have nothing to do with this task. |
| `frontend/openapi/tsolve-api.yaml` | none |
| `frontend/src/shared/api/generated` | none (the generated `model/index.ts` no longer exports the three removed models) |
| `jira-app/TSolve_JiraApp/src` | none; no `MSG04`, `MSG76`, `searchTickets` or `/tickets` either |
| `.env.example` | none |
| `e2e/`, `infra/` | Only `webOrigin` in the e2e settings and `webOrigins` in the Keycloak realms. Neither is related. |
| `docs/bug-log.md:229` | BUG-216 (formerly BUG-205 on the branch) is Fixed and notes that "The code and test went away with the Jira search (spec change 14/11, commit 94dd213a)". This is consistent. |

## Traceability

```
  ✓ AC-008.1  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraIssueAccessImplTest.java
  ✓ AC-008.2  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-008.3  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java
  ✓ AC-008.7  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraIssueAccessReviewAdapterTest.java
trace jira-app (STORY-008): 4/4 acceptance criteria have a test
```

## Issues

### Blocking

None.

### Informational

1. **[Docs, Low] The Javadoc still cites a removed criterion.**
   - `backend/src/main/java/vn/tsolve/jiraapp/service/JiraAppSolutionService.java:26` reads
     `/** What the Issue Panel shows (AC-001.4, AC-008.1 to AC-008.3, AC-008.6). */`, but AC-008.6 was removed on 14/11.
   - Fix: replace `AC-008.6` with `AC-008.7`, or drop it.
2. **[UX, Low — judgment call] The panel can be empty for a linked user.**
   - The search box used to be the one thing a linked user always saw. Now `IssuePanel.jsx:88-129` renders an empty
     `Stack` in some states:
     - the issue is not Done and has no Draft;
     - the issue's ticket is Published, Rejected or in any status other than Pending review or Changes requested.
   - The spec defines no panel content for those states, so this is not a spec violation.
   - Possible fix (only if the user wants one): show a one-line status, such as the ticket status or "Write a Solution
     when the issue is Done".
3. **[Hygiene] An unrelated file is modified in the working tree.**
   - `jira-app/TSolve_JiraApp/package-lock.json` is modified at HEAD but not committed. It is not part of `94dd213a`.
   - It should not go into this feature's commit.

## Assumptions made

- **The empty panel is acceptable (Issue 2).**
  - STORY-008 now lists only the states that show something. I read the states it does not list as "show nothing".
  - **Judgment call for the user:** keep the panel empty in those states, or add a short status line (this would be a
    spec change)?
- **The SRS deviation is accepted.** The changelog row says that removing UC-26 search, 3.4 and MSG76 from the Jira App
  is a deviation from the SRS, which the user will agree with the team. I reviewed against the spec as approved and did
  not judge the deviation itself.
- **Correctness relies on CI for the backend tests.** The dispatcher allowed only `mvn -q -B test-compile` (it
  succeeded). The removed and changed backend tests (`JiraAppSolutionControllerTest`, `JiraAppSolutionServiceImplTest`)
  were not run here. Their pass rests on CI #648 on `aa8ae95e`, which the dispatcher reports as green. Since then the
  branch merged `main` twice (`55353f8d`, `3cbab3db`) and took the Task 10 commit `ffebe905`. The compile at HEAD shows
  that those merges did not bring back any reference to the removed code.

## Test Report

See task-9-remove-jira-search-test-report.md
