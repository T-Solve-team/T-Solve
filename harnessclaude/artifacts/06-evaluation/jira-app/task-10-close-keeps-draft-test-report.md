---
module: jira-app
feature: close-keeps-draft
task: 10
attempt: 1
iteration: 2
date: 2026-11-14
reviewer: harnessclaude-reviewer (evaluate, run-tests)
---

# Test Report: jira-app — Task 10 "Close keeps a Draft; no empty ticket" (KAN-126)

Run on branch `feat/KAN-126-jira-app-solution-form-v2` at head `622c71e6` (repo `default`, monorepo). The feature's
code is at `ffebe905`, and none of its files changed after that. Run on 2026-11-14 by the reviewer subagent. The review
is `task-10-close-keeps-draft-review.md`.

## Results

| Check | Command (directory) | Result |
|-------|---------------------|--------|
| Forge app tests | `npx jest` (`jira-app/TSolve_JiraApp`) | PASS. 12 suites, 141 tests. `SolutionFormModal.test.jsx` has the tests for AC-005.1 (×3 + doneFlag), AC-005.4 and AC-005.5. |
| Forge app ESLint | `npm run lint` (`jira-app/TSolve_JiraApp`) | PASS, no findings |
| SolutionFormTicketServiceImplTest | `mvn -q -B test -Dtest='SolutionFormTicketServiceImplTest,SolutionSectionsTest,JiraAppSolution*Test' -Dsurefire.failIfNoSpecifiedTests=false "-DargLine=-Djdk.net.unixdomain.tmpdir=C:/tmp/jdk-uds"` (`backend/`) | PASS. 8/8, with `refusesAnAllEmptySolutionSoItNeverReachesTheReviewQueue_AC_005_5` |
| SolutionSectionsTest | same | PASS. 2/2 (`treatsMissingSectionsAsEmptyText_AC_002_5`) |
| JiraAppSolutionServiceImplTest | same | PASS. 12/12 |
| JiraAppSolutionControllerTest | same | PASS. 9/9 |

The surefire reports `backend/target/surefire-reports/*.txt` for the four classes were written at 15:16 on this run,
with 0 failures and 0 errors.

## Not run

- **Full `mvn verify`, `mvn spotless:check`, JaCoCo**: not run, by dispatcher rule (other reviewers run in parallel).
  The main session's verify is the check for them.
- **Integration tests (`SolutionFormTicketIT`, Testcontainers)**: not run. By reading, the IT has no all-empty
  submission that the new unconditional check would break.
- **`npm run generate:api` and the web checks (`frontend/`)**: not run. Generation rewrites the tracked generated
  folder, and the reviewer may not edit code. The committed `jira-app.ts` diff changes only the two summaries, which
  matches the YAML.
- **`forge lint` and a live dev-site check**: not run by the reviewer. The session log records a live check on 5.8.0
  and 5.9.0 (Close and Escape keep the Draft with no ticket, Continue draft reopens it, Finish submits it).

## Traceability

`bun harnessclaude/hooks/sdd.ts trace jira-app STORY-005`: 4/5. AC-005.1, AC-005.2, AC-005.4 and AC-005.5 each have a
test in the module paths. AC-005.3 (Task 2) is tested in `backend/src/test/java/vn/tsolve/ticket/service/impl/SolutionDraftServiceImplTest.java:62`
and `backend/src/test/java/vn/tsolve/ticket/SolutionFormTicketIT.java:165`, outside the module's trace paths. The
server half of AC-005.5 is in `backend/src/test/java/vn/tsolve/ticket/service/impl/SolutionFormTicketServiceImplTest.java:107`.
