---
module: jira-app
feature: ask-page-ui
task: 6
attempt: 1
iteration: 1
date: 2026-11-08
reviewer: harnessclaude-reviewer
---

# Test Report: jira-app — STORY-010 "Ask T-Solve" (Tasks 2–6, KAN-294)

Branch `feat/KAN-294-jira-app-ask-tsolve`, range `a4818be..7f5620d` (repo `default`, monorepo). Run by the reviewer on 2026-11-08.

## Results

| Check | Command (directory) | Result |
|-------|---------------------|--------|
| Backend tests for the story | `mvn test -Dtest='JiraAppQuestionControllerTest,JiraAppQuestionServiceImplTest,ConfigJiraAskerLookupTest,AccountLookupServiceImplTest'` (`backend/`) | PASS. 36 tests, 0 failures, 0 errors, 0 skipped |
| — JiraAppQuestionControllerTest | MockMvc and FIT auth | 13/13 |
| — JiraAppQuestionServiceImplTest | | 10/10 |
| — ConfigJiraAskerLookupTest | | 9/9 |
| — AccountLookupServiceImplTest | | 4/4 |
| Backend format | `mvn spotless:check` (`backend/`) | PASS |
| Forge app tests | `npx jest` (`jira-app/TSolve_JiraApp`) | PASS. 6 suites, 66 tests |
| Forge app ESLint | `npm run lint` (`jira-app/TSolve_JiraApp`) | PASS, no findings |
| Forge lint | `forge lint` (`jira-app/TSolve_JiraApp`) | PASS. "No issues found." (the CLI was logged in) |
| OpenAPI client | `npm run generate:api` (`frontend/`) | PASS. No diff in `src/shared/api/generated` (`git status` clean afterwards) |
| Web typecheck | `npm run typecheck` (`frontend/`) | PASS |
| Web format | `npm run format:check` (`frontend/`) | PASS |

## Not run

- **Full `mvn verify`**: not run. In this sandbox, the WireMock and Keycloak suites (`ai.client`, `identity.signin`) fail with `SocketException: Invalid argument: connect`, and the Testcontainers integration tests need Docker. These are limits of the environment, not code failures. The four suites for this story ran in full.
- **Web `npm run lint`, `npm run test` and `npm run build`**: not run. The only change under `frontend/` is the OpenAPI file and the generated client. Regenerating the client produced no diff, and the typecheck passes.
- **Coverage**: no coverage tool is set up for the Forge app (Jest runs without `--coverage` thresholds), and JaCoCo was not run for this narrow test selection. Traceability is checked below instead.

## Traceability

`bun harnessclaude/hooks/sdd.ts trace jira-app STORY-010` reports **12/12 acceptance criteria have a test** (AC-010.1 to AC-010.12). See the review for the per-criterion test files.

## Forge test names (Jest, verbose)

Every test passed. Tests per criterion:
- AC-010.1: 2 tests (page: box, send button and note; the manifest title appears once, BUG-182)
- AC-010.2: 6 tests (client, prefill, page)
- AC-010.3: 7 tests
- AC-010.4: 1 test (client)
- AC-010.5: 15 tests (validation, messages, client, page)
- AC-010.6: 4 tests
- AC-010.7: 3 tests
- AC-010.8: 9 tests
- AC-010.9: 9 tests
- AC-010.10: 2 tests
- AC-010.11: 3 tests
- AC-010.12: 2 tests

Three tests have no AC id: the idle start, the late-answer guard, and the fallback for an unknown code.
