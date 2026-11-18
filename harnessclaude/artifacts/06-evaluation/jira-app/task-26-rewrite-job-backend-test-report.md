# Test report: jira-app — Task 26: rewrite-job-backend

Date: 2026-11-18. Source: `backend/target/surefire-reports` from the `mvn verify` the main session was running. The reviewer did not start Maven. The reports were written 03:39–03:40, after the last edits at 03:32.

| Test class | Tests | Failures | Errors | Skipped |
|------------|-------|----------|--------|---------|
| jiraapp.service.impl.SolutionRewriteJobTest (new) | 11 | 0 | 0 | 0 |
| jiraapp.service.impl.SolutionFormAiImplTest | 11 | 0 | 0 | 0 |
| jiraapp.service.impl.RedisAskJobStoreTest | 4 | 0 | 0 | 0 |
| jiraapp.service.impl.JiraAppSolutionServiceImplTest | 19 | 0 | 0 | 0 |
| jiraapp.controller.JiraAppSolutionControllerTest | 16 | 0 | 0 | 0 |
| jiraapp.service.impl.JiraAppQuestionServiceImplTest (Ask, regression) | 29 | 0 | 0 | 0 |
| jiraapp.controller.JiraAppQuestionControllerTest (Ask, regression) | 21 | 0 | 0 | 0 |

- Timing: each new test takes 0.001–0.104 s, and none sleeps. The only 0.11 s case in SolutionFormAiImplTest is the existing draft-deadline test, which uses a 100 ms timeout.
- Frontend: `npx prettier --check` on `openapi/tsolve-api.yaml` and the changed generated files passes, and `npm run typecheck` passes. I did not run `generate:api`, because it rewrites the tree.
- Traceability: `sdd.ts trace jira-app STORY-007 STORY-012` reports 15/15.
- Not checked by the reviewer: Spotless, ITs, and the final result of the full `mvn verify`.

## Re-run after the review fixes (separate rewrite pool, `JiraAppJobCompletions`, BUG-344)

Date: 2026-11-18, main session. Command (in `backend/`): `mvn verify -Dtest='vn.tsolve.jiraapp.**' -Dit.test=RedisWiringIT -Djacoco.skip=true`. Exit code 0.

- Unit (Surefire, fresh reports): 158 tests, 0 failures, 0 errors.
- Integration (Failsafe): `RedisWiringIT`, 3 tests, 0 failures. It boots the full Spring context with Postgres and Redis, so it checks the new `@Component` and the changed constructors.
- Spotless check: passed (it is part of `verify`).
- Not run: the full `mvn verify` (all ~940 ITs). The machine had 0.3 GB of free RAM, and the earlier full run was stopped for low memory. The last full run before the fixes passed (3969 tests, 0 failures). CI runs the full suite on the PR.
- Traceability: `sdd.ts trace jira-app STORY-007` reports 8/8.
