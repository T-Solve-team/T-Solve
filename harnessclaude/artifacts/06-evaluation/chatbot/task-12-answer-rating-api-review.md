---
module: chatbot
feature: answer-rating-api
task: 12
verdict: pass
attempt: 1
date: 2026-11-09
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: chatbot — Task 12: Rating API (KAN-182, STORY-008)

## Summary
I reviewed commit `6555eba` on `feat/KAN-182-chatbot-rating-api`. The schema commit before it, `b9079eb` (Task 11),
was read for context only. The commit adds `PUT` and `DELETE /api/v1/chatbot/answers/{answerId}/rating`
(`ChatbotRatingController`), `ChatbotRatingService` / `ChatbotRatingServiceImpl`, the request record, two module
exceptions and one `@Modifying` JPQL update on `ChatbotAnswerRepository`. I checked AC-008.1 to AC-008.5 against
the code and the tests. I checked the JSON and the operation ids against `frontend/openapi/tsolve-api.yaml`, and the
layering, naming and error rules against `backend/AGENTS.md`. I ran the controller test and the two rating ITs
myself, and they pass. There are no CRITICAL findings and no High security findings. The contract has no `400` for
`PUT`, but AC-008.4 requires one. I judge this a non-blocking contract follow-up (Issue 1, Assumption 1).
Verdict: PASS.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `rate` (`ChatbotRatingServiceImpl.java:27-35`) rejects a null body or a null `rating` with `InvalidChatbotRatingException` (400 MSG06, no field). It does this before any query. It then parses the id and runs one update. `remove` (`:39-44`) runs the same update with `null, null`. The update (`ChatbotAnswerRepository.java:13-20`) is `UPDATE ChatbotAnswer a SET a.rating = :rating, a.ratedAt = :ratedAt WHERE a.id = :id AND a.userId = :userId`. The owner check is in the `WHERE` clause, so there is no read-then-write race. Zero rows updated gives `ChatbotAnswerNotFoundException` (404 MSG04). An unknown id and another user's id give the same response. A non-UUID path id is taken as `String` and mapped to the same 404 (`:46-52`). The time is `Instant.now(clock)` from the one shared `Clock` bean (`ticket/config/TicketClockConfig`), as `AuditLogServiceImpl` does. `Instant` is zone-free, so the bean's `Asia/Ho_Chi_Minh` zone has no effect. The outcome is not filtered, so `NO_SOURCE` answers can be rated (R3). The same rating twice updates one row again and gives 204 (R2). Removing a rating that does not exist still matches the owner's row and gives 204 (AC-008.2). |
| Completeness | pass | AC-008.1 to AC-008.5 each have a named test (see Traceability). The design's edge cases R1 to R3 are covered: R1 in `ChatbotRatingIT.leavesOthersAnswerUnchanged_AC_008_3`, R2 in `savesRating_AC_008_1` (two calls) and the IT (`NOT_HELPFUL` twice), R3 in the IT (`noSourceId`). |
| Correctness | pass | I ran `ChatbotRatingControllerTest` (13/13) and `ChatbotRatingIT` (3/3) with `ChatbotRatingSchemaIT` (2/2), and all pass. The controller's full `mvn clean verify` (939 unit + 302 IT, 0 failures) is evidence that the full application context starts with the new bean. Several existing ITs are `@SpringBootTest`, so a second or missing `Clock` bean would fail them. |
| Integration | pass with a follow-up | The path, the `operationId`s `rateChatbotAnswer` and `removeChatbotAnswerRating` and the tag `chatbot` match the yaml. The body `{rating: HELPFUL \| NOT_HELPFUL}` matches `ChatbotRating`. 204 and 401/403/404 match. The yaml and the spec's Interface Contracts block do not list `400` for `PUT`, but AC-008.4 and the line "Rating errors (STORY-008): 400 code MSG06" require it (Issue 1). The module boundary holds: `chatbot.rating` uses only `chatbot.answer.repository` (same module) and `common`. |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-008.1 store rating + time, replace, 204; `ANSWERED` and `NO_SOURCE` | PASS | `ChatbotRatingControllerTest.savesRating_AC_008_1` (both enum values, sent twice, 204 each). `ChatbotRatingIT.replacesRatingOnOwnAnswerOnly_AC_008_1` uses a real Postgres with the full changelog. It checks that `HELPFUL` and then `NOT_HELPFUL` replace the rating with the fixed `rated_at`, that a `NO_SOURCE` answer can be rated, and that the other row is unchanged each time. |
| 2 | AC-008.2 clear rating + time, 204 also with no rating | PASS | `removesRating_AC_008_2` (204). `clearsRatingAndTime_AC_008_2` removes twice and checks that both columns are null each time. |
| 3 | AC-008.3 404 MSG04, nothing changed (non-UUID, no such answer, another user's answer) | PASS | The controller test checks 404 and `$.code` MSG04 on PUT and DELETE for three ids. The service is mocked there. The IT runs the real service: user B's PUT and DELETE on user A's answer throw `ChatbotAnswerNotFoundException("MSG04")`, and the row stays byte-for-byte the same. A random UUID and `not-a-uuid` give the same result for both verbs, and the row count stays 2. |
| 4 | AC-008.4 missing body / missing or unknown rating gives 400 MSG06, no field error, nothing changed | PASS | `rejectsBadBody_AC_008_4` covers no body, `{}`, `{"rating":null}`, `{"rating":"MAYBE"}` and `not json`. It checks 400, `$.code` MSG06 and no `$.fieldErrors`. For `MAYBE` and malformed JSON it also checks `verifyNoInteractions(ratings)`, which shows that Spring's `HttpMessageNotReadableException` is answered by `ResponseEntityExceptionHandler`, and `GlobalExceptionHandler.createResponseEntity` adds MSG06. The real service's null checks, with the row unchanged, are tested in the IT, but under the AC-008.3 test name (Issue 3). |
| 5 | AC-008.5 401 MSG07; any signed-in user | PASS | `answers401WithoutSession_AC_008_5` (PUT and DELETE, `verifyNoInteractions`). `letsUserWithNoAssignedRolesRate_AC_008_5` uses `roles = {}`, which leaves only the implicit `EMPLOYEE`, the weakest role. `@PreAuthorize("isAuthenticated()")` has no role condition, so every role passes. |

### Stage 2 — Quality and security

CRITICAL: none. Security High/Critical: none.

- IDOR: closed. The owner is in the update's `WHERE`, and a miss returns the same 404 for "not mine" and "does not
  exist". A non-UUID id also returns that 404, so a 400 or 500 does not show which ids are real.
- Transaction: one statement per call inside `@Transactional` on the service (proxied, since it is a `@Service`). A
  `@Modifying` query needs that transaction. Nothing is loaded before the update, so the missing
  `clearAutomatically` does no harm. The controller has no `@Transactional` and no repository, which follows AGENTS.md
  §1 and §3.
- Errors: both exceptions extend the `common/exception` bases. There is no `@ExceptionHandler` in the module and no
  try/catch in the controller. The 400 body has Spring's generic `detail` ("Failed to read request"), which has no
  user content.
- CSRF stays on for PUT and DELETE, and the tests use `csrf()`. A request without a CSRF token gets 403, which the
  contract lists.
- No logging and no content in exception messages.

Informational (does not block):
1. Low. [`ChatbotRatingServiceImpl.java:28-31`] The body is checked before the id. A bad body on someone else's id
   or a non-UUID id gets 400, not 404. The 400 does not depend on the id, so it shows nothing about the id. The spec
   does not say which check comes first (Assumption 2).
2. Low. [`ChatbotRatingServiceImpl.java:48`] `UUID.fromString` is lenient. For example, `1-2-3-4-5` parses to an id
   that does not exist, so the result is still 404. This has no impact.
3. Low. No test sends HTTP through the real service to the database. The controller test mocks the service, and the
   IT builds the service by hand inside its own `TransactionTemplate`, so it does not exercise the service's
   `@Transactional` proxy. The wiring is standard, and the full-context ITs show that the bean is created. The KAN-76
   manual check on the real backend is still to be done, as Task 12 says (Assumption 4).
4. Nit. [`ChatbotRatingController.java:33,46`] The `@ApiResponse` lists 401. AGENTS.md §6 lists only 400/403/404/409,
   but `ChatbotAnswerController` does the same, so the code follows the local pattern. DELETE has no `@ApiResponse`
   for 403 (CSRF). This affects the springdoc output only. The yaml is the source of truth.

## Assumptions made
1. **Contract drift on PUT 400 (open judgment call).** The yaml `rateChatbotAnswer` and the spec's Interface
   Contracts block list `204, 401, 403, 404` and no `400`. AC-008.4 and the spec's "Rating errors" line require 400
   MSG06. The code follows the acceptance criterion. I judged this a non-blocking follow-up and not a FAIL, for
   these reasons:
   (a) The drift is inside the spec itself, so the code cannot satisfy both texts. The criterion is the more
   specific of the two.
   (b) The typed frontend client cannot send a body that triggers the 400, so no runtime behaviour changes.
   (c) Task 12 and Non-Functional 4 say "no frontend change".
   Suggested follow-up: a contract quick change. Add `'400': $ref: '#/components/responses/Problem'` under `put` in
   `frontend/openapi/tsolve-api.yaml`, add `400: ProblemDetail code MSG06` to the spec's contract block, and run
   `npm run generate:api`. The user decides whether this goes into the KAN-182 PR or a separate change.
2. **Check order.** A bad body is answered 400 before the id is looked at (Informational 1). This is acceptable
   because the spec is silent and the order leaks nothing.
3. **Formatting.** `spotless:check` cannot run in this worktree because JGit cannot open a git worktree, and the
   pom's `ratchetFrom` cannot be overridden from the command line. I did not check the formatting myself. I rely on
   the controller's ratchet-free check of the changed files.
4. **Manual frontend check.** Task 12 says that a manual KAN-76 check with `VITE_API_MOCKING=false` must be noted in
   the PR. That check is outside what I can verify, and I did not see it.

## Traceability
```
  ✓ AC-008.1  backend/src/test/java/vn/tsolve/chatbot/rating/controller/ChatbotRatingControllerTest.java, backend/src/test/java/vn/tsolve/chatbot/rating/ChatbotRatingIT.java
  ✓ AC-008.2  backend/src/test/java/vn/tsolve/chatbot/rating/controller/ChatbotRatingControllerTest.java, backend/src/test/java/vn/tsolve/chatbot/rating/ChatbotRatingIT.java
  ✓ AC-008.3  backend/src/test/java/vn/tsolve/chatbot/rating/controller/ChatbotRatingControllerTest.java, backend/src/test/java/vn/tsolve/chatbot/rating/ChatbotRatingIT.java
  ✓ AC-008.4  backend/src/test/java/vn/tsolve/chatbot/rating/controller/ChatbotRatingControllerTest.java
  ✓ AC-008.5  backend/src/test/java/vn/tsolve/chatbot/rating/controller/ChatbotRatingControllerTest.java
  ✓ AC-008.6  backend/src/test/java/vn/tsolve/chatbot/rating/ChatbotRatingSchemaIT.java
trace chatbot (STORY-008): 6/6 acceptance criteria have a test
```
AC-008.6 belongs to Task 11 and is listed for completeness. I read every listed test. Each one checks the behaviour
of its criterion, and none only mentions the id.

## Issues
None blocking. Follow-ups:
1. Low (contract). [`frontend/openapi/tsolve-api.yaml:1099-1107`, spec Interface Contracts block] Add `400` to
   `rateChatbotAnswer` (Assumption 1).
2. Low (test). Add one HTTP-to-database test, or accept the manual KAN-76 check (Informational 3).
3. Low (traceability). The real service's 400 path (null body, null rating, row unchanged) is tested only inside
   `ChatbotRatingIT.leavesOthersAnswerUnchanged_AC_008_3`. Move those assertions into a test named `..._AC_008_4` so
   that `trace` points at the IT for AC-008.4.

## Test Report
- `mvn -q test -Dtest=ChatbotRatingControllerTest` (reviewer): 13 tests, 0 failures, 0 errors.
- `mvn -q verify -DargLine="-Dapi.version=1.44" -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false
  -Dit.test=ChatbotRatingIT,ChatbotRatingSchemaIT -Dspotless.check.skip=true` (reviewer, Testcontainers
  `pgvector/pgvector:pg16`): `ChatbotRatingIT` 3/3 and `ChatbotRatingSchemaIT` 2/2, exit 0. The reports are from
  this run (12:13).
- Full suite (controller's run): `mvn clean verify` 939 unit + 302 IT, 0 failures. The reviewer did not repeat it.
- `spotless:check`: not runnable in this worktree (JGit). See Assumption 3.
- `bun harnessclaude/hooks/sdd.ts trace chatbot STORY-008`: 6/6 (reviewer).
- Scope: HEAD is `6555eba`. `git status --porcelain` shows only `progress.json` and `events.jsonl`. I did not change
  any feature status, because the dispatcher asked for the review only.
