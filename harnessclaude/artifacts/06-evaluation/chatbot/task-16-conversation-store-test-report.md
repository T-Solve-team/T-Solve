# Test Report: chatbot — Task 16: conversation-store

- Date: 2026-11-18 · attempt 2 · range `default: da13f8ec..1808272d` (feature `4967050a` + fix `1808272d`, branch
  `feat/KAN-183-chatbot-persist-sessions`)
- Environment note: the shell's `JAVA_HOME` is wrong on this machine; the runs used
  `JAVA_HOME=C:\Program Files\Java\jdk-21.0.12` for the process only (no file changed).

## Backend (`backend/`, `mvn verify` — tests + `spotless:check`)

| Run | Result |
|-----|--------|
| 1 (`mvn -q verify` at `1808272d`) | BUILD FAILURE, all from one test. Surefire **3021 run, 0 failures, 0 errors**. Failsafe 935 run, 0 failures, **1 error**: `vn.tsolve.worker.EmailOutboxIT.concurrentClaims` `TimeoutException` at `EmailOutboxIT.java:442` (83 s). |
| 2 (`EmailOutboxIT` alone, with `spotless:check`) | `spotless:check` passed. `EmailOutboxIT`: 11 run, 1 error, the same `concurrentClaims:442` timeout (82 s). |

`EmailOutboxIT.concurrentClaims` is open **BUG-298** in `docs/bug-log.md` ("times out locally on main … at
EmailOutboxIT.java:442 … same on the KAN-167 branch, which changes no worker code"). See also BUG-317. The range
`da13f8ec..1808272d` changes nothing under `vn/tsolve/worker`. The test passed in attempt 1's run 2 on the same worker
code. I judged it a known environment failure, not one this change caused (Assumption A3 in the review).

Changed and task tests, all green in run 1:
- `ChatbotQuestionRequestTest` 8/8 (new: AC-007.3, 7 bad histories and the accepted boundary case)
- `ChatbotAnswerServiceImplTest` 60/60 (new: `followUpAfterALongStoredAnswer_AC_013_2_BUG_332`)
- `ChatbotConversationStoreIT` 7/7 (new: `refusesAConversationDeletedDuringTheAsk_AC_013_4_BUG_333`)
- `ChatbotAnswerControllerTest`, `ChatbotIssueControllerTest`, `ChatbotIssueServiceImplTest`, `ChatbotIssueIT`,
  `ChatbotAnswerTimeoutIT`, `IssueTargetRepositoryIT`, the `jiraapp` tests and the platform isolation ITs were all
  green (inside the surefire and failsafe totals).

## Frontend (`frontend/`)

`git diff 4967050a..1808272d --stat -- frontend` is empty: the fix commit changes no frontend file. Attempt 1's
frontend result is reused (format:check, lint with 0 errors, typecheck and build pass; `src/features/chatbot` Vitest
144/144; full suite green apart from one unrelated flaky timeout that passes alone; `generate:api` leaves no diff).
The backend controllers and DTOs did not change in the fix commit either, so the OpenAPI contract is unchanged.

## Traceability

`bun harnessclaude/hooks/sdd.ts trace chatbot STORY-013 STORY-007 STORY-010` gives 19/25 with a test. The untested
ones are AC-013.5 – AC-013.10, which belong to Tasks 17 and 18, not this task. AC-007.3 is now covered by
`ChatbotQuestionRequestTest`, which exercises its 400 MSG06 clause. The trace still warns about `AC-002.11` in
frontend tests (Task 18 scope).
