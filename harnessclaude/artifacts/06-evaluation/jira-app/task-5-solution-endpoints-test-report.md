---
module: jira-app
feature: solution-endpoints
task: 5
attempt: 1
iteration: 2
date: 2026-11-13
reviewer: main session (harnessclaude evaluate, run-tests)
---

# Test Report: jira-app — Task 5 "Jira App endpoints" (KAN-126)

Branch `feat/KAN-126-jira-app-solution-form-v2`, head `221d53ca` (repo `default`, monorepo). The feature's code is at `47fa92fe`. Run on 2026-11-13 by the main session; the review is `task-5-solution-endpoints-review.md`.

## Results

| Check | Command (directory) | Result |
|-------|---------------------|--------|
| JiraAppSolutionControllerTest | `mvn verify` (`backend/`), MockMvc + FIT auth | PASS. 12/12 |
| JiraAppSolutionServiceImplTest | | PASS. 13/13 |
| JiraAppTicketServiceImplTest | | PASS. 4/4 |
| Backend format | `mvn spotless:check` (`backend/`) | PASS |

## Not run

- **Full `mvn verify`**: it ran on the branch head `221d53ca` and stopped in the unit-test phase with 1411 tests, 3 failures and 129 errors, all in `ai.client`, `identity.*`, `common.security.SecurityConfigCsrfOrderTest` and `ticket.client.JiraRestIssueSourceTest`. Their cause is the sandbox: WireMock and the Keycloak/Redis clients fail with `Unable to establish loopback connection` / `SocketException: Invalid argument: connect`. None of them is a test of this feature, and none of those files is changed by KAN-126. The CI backend job is the check for them.
- **Integration tests (Testcontainers)**: not run. Docker Desktop is not running on this machine (`Could not find a valid Docker environment`), so `failsafe` could not start PostgreSQL.
- **Coverage**: JaCoCo did not run, because `verify` stopped before the report. Traceability is checked below instead.


## Traceability

`sdd.ts trace jira-app` over STORY-001..009: every criterion the task lists (AC-001.2, AC-002.1, AC-002.2, AC-004.3, AC-005.2, AC-008.2 to AC-008.6, AC-008.8, AC-009.2) has a test.

## Attempt 2

Run on 2026-11-13 by the main session, on `ccfee5d6` (the fix for attempt 1: contract and `@ApiResponse` aligned, BUG-204, BUG-205).

| Check | Command (directory) | Result |
|-------|---------------------|--------|
| jira-app backend unit tests | `mvn test -Dtest='vn.tsolve.jiraapp.**.*Test,SolutionFormTicketServiceImplTest,SolutionDraftServiceImplTest,SolutionSectionsTest,SecurityConfigTest'` (`backend/`) | PASS. JiraAppSolutionControllerTest 12/12, JiraAppSolutionServiceImplTest 13/13, JiraAppTicketServiceImplTest 11/11 (with `refusesToStartWithoutAnAbsoluteWebOrigin_BUG_205`, 7 cases), JiraIssueAccessImplTest 7/7, SolutionFormAiImplTest 11/11, SecurityConfigTest 15/15, and the ticket and Ask suites |
| BUG-205 test first | same, before the fix | FAIL as expected (7 of 7 new cases), then PASS after |
| Backend format | `mvn spotless:check` (`backend/`) | PASS |
| Forge app tests | `npx jest` (`jira-app/TSolve_JiraApp`) | PASS. 11 suites, 125 tests (with the BUG-204 test, which failed before the fix) |
| Forge app ESLint | `npm run lint` (`jira-app/TSolve_JiraApp`) | PASS |
| OpenAPI client | `npm run generate:api` (`frontend/`) | PASS. The regenerated `jira-app.ts` is committed; a second run leaves no diff |
| Web format, lint, typecheck, build | `npm run format:check`, `lint`, `typecheck`, `build` (`frontend/`) | PASS |

Not run: the Testcontainers ITs (Docker is not running) and the full `mvn verify` (the sandbox loopback limits of attempt 1 still apply); `forge lint`. The CI backend job is the check for them.

Web tests: `npm run test` (`frontend/`) ran 1499 tests: 1490 passed, 9 failed with "Test timed out in 15000ms", in `ReviewQueuePage.test.tsx`, `JiraPullForm.test.tsx` and `ImportPreviewPage.test.tsx`. This change touches none of them. Run alone (`npx vitest run` on those three files), all 70 pass. They time out only when the whole suite runs on this machine at once.
