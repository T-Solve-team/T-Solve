# Test report: jira-app — Task 24 ask-job-backend (attempt 1)

Date: 2026-11-18. The change is uncommitted in the working tree on `fix/KAN-294-jira-app-ask-job`.

## Execution
I did not run Maven, because the dispatcher said another `mvn verify` was running in `backend/`. The results below come from `backend/target/surefire-reports`. Those reports are dated 2026-11-18 01:50, which is after the last edit to the sources and tests (01:42).

| Class | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| vn.tsolve.jiraapp.service.impl.JiraAppQuestionServiceImplTest | 21 | 0 | 0 | 0 |
| vn.tsolve.jiraapp.service.impl.RedisAskJobStoreTest | 3 | 0 | 0 | 0 |
| vn.tsolve.jiraapp.controller.JiraAppQuestionControllerTest | 21 | 0 | 0 | 0 |

Not verified by me: `mvn spotless:check`, the rest of the suite, ITs, and the frontend `npm run generate:api` no-diff check.

## Timing
No `*Test` sleeps, polls or waits on the clock. The service tests use a queued `Executor`, and an injected deadline whose timeout is simulated with `completeExceptionally(new TimeoutException())`.

## Traceability
`bun harnessclaude/hooks/sdd.ts trace jira-app STORY-010`: 18/18 acceptance criteria have a test. Also printed: "ids in tests but not in the spec: AC-001.77", an id that existed before this change.
