---
module: chatbot
feature: conversation-store
task: 16
verdict: pass
attempt: 2
date: 2026-11-18
reviewer: harnessclaude-reviewer
iteration: 3
---

# Review: chatbot — Task 16: conversation-store

## Summary
Fix commit `1808272d` resolves both issues that failed attempt 1, plus the Low issue 3:
- AC-007.3 has a test again (`ChatbotQuestionRequestTest`), and it exercises the 400 MSG06 clause.
- Stored turns no longer pass through the request validator (BUG-332).
- `record` throws 404 MSG04 when `touch` updates no row, which rolls back the answer (BUG-333).

Each fix has a test named with its bug id, and both bug-log rows are filled in.

The full backend run is green apart from `EmailOutboxIT.concurrentClaims`. That is open BUG-298, in the worker
module, which this range does not touch. It failed again when run alone, at the same line, as the bug log says it
does on `main`. Two Minor issues from attempt 1 are still open (4 and 5). They do not block. The verdict is
**pass**.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-013.1–.4, .11, .12, AC-007.1–.3, AC-010.3 and AC-010.8 behave as specified. AC-013.2 now holds for a stored answer longer than 10,000 characters: `ask` passes the DB turns straight to `answer(String, List, …)` (`ChatbotAnswerServiceImpl.java:176-198`). |
| Completeness | pass | Every AC of the task has a test that names it. AC-007.3 is exercised by `ChatbotQuestionRequestTest` (7 bad histories give MSG06, 400, with no field; 10 turns at 1,000/10,000 characters, empty turns and an empty list are accepted). AC-013.5–.10 belong to Tasks 17/18. |
| Correctness | pass | Surefire 3021/3021. Failsafe 934/935: the one error is BUG-298 (`EmailOutboxIT.concurrentClaims:442` timeout, worker module, not in range, open on `main`). spotless:check passes. The frontend did not change in `1808272d`, so attempt 1's green frontend result is reused. |
| Integration | pass | The fix commit changes no controller, DTO or OpenAPI file, so the contract and the generated client stay as checked in attempt 1. `askForProject` (jiraapp) still builds `ChatbotQuestionRequest`, so its validation is unchanged. |

### Attempt 1 issues
1. **AC-007.3 untested: resolved.** `backend/src/test/java/vn/tsolve/chatbot/answer/dto/ChatbotQuestionRequestTest.java:21-41`
   covers null history, 11 turns, a null turn, a null question or answer, 1,001 and 10,001 characters, and the accepted
   limits. The constructor throws before `askForProject` is called, so the clauses "call no AI, store nothing" hold
   by construction.
2. **A long stored answer blocks follow-ups: resolved.** `ChatbotAnswerServiceImpl.java:180-182` no longer builds
   `ChatbotQuestionRequest` from the DB turns. Test: `ChatbotAnswerServiceImplTest.followUpAfterALongStoredAnswer_AC_013_2_BUG_332`
   (10,001-character stored answer gives a normal outcome, the same `chatSessionId`, and the embed text
   "earlier\nquestion").
3. **Session deleted between the check and the write: resolved.**
   `ChatConversationServiceImpl.java:62-66` throws `ChatbotConversationNotFoundException` when `touch` returns 0.
   It runs inside `TransactionTemplate.execute` on the request thread (`ChatbotAnswerServiceImpl.save`, line 316),
   which rethrows the RuntimeException unwrapped, so the client gets 404 MSG04. The IT
   `refusesAConversationDeletedDuringTheAsk_AC_013_4_BUG_333` checks the 404 and that both `chatbot_answer` and
   `chat_message` are empty afterwards.
4. **Stale OpenAPI 400 on `draftChatbotIssue`: still open (Minor).** See Issues.
5. **Test names the removed AC-002.11: still open (Minor, Task 18 scope).** See Issues.

### Stage 2: checklist (fix commit)
- The refactor of `answer(...)` to take `(question, history)` is mechanical. Every `request.history()` use was
  replaced, and no path skips the `askForProject` validation.
- There are no new queries and no logging of content.

### Security review (per-user ownership, IDOR)
- Nothing changed in this area since attempt 1. `history(user.id(), id)` is still scoped by `user_id` and runs
  before any AI call. `touch(id)` still has no `user_id` filter, but its only caller passes an id that was already
  checked for ownership. It now also fails closed when the id matches no row. There are no Critical or High findings.

## Traceability
```
✓ AC-007.1  ChatbotAnswerServiceImplTest
✓ AC-007.2  AnswerPromptTest
✓ AC-007.3  ChatbotQuestionRequestTest, ChatbotAnswerControllerTest
✓ AC-007.4  ChatbotAnswerServiceImplTest
✓ AC-010.1 … AC-010.9  (ChatbotIssueServiceImplTest, ChatbotIssueControllerTest, IssueTargetRepositoryIT, ChatbotConversationStoreIT)
✓ AC-013.1  ChatbotAnswerServiceImplTest, ChatbotConversationStoreIT
✓ AC-013.2  ChatbotAnswerServiceImplTest, ChatbotAnswerControllerTest, ChatbotConversationStoreIT
✓ AC-013.3  ChatbotAnswerServiceImplTest, ChatbotConversationStoreIT
✓ AC-013.4  ChatbotAnswerServiceImplTest, ChatbotAnswerControllerTest, ChatbotConversationStoreIT
✗ AC-013.5 … AC-013.10  no test (Tasks 17/18, not counted here)
✓ AC-013.11 ChatbotConversationStoreIT
✓ AC-013.12 ChatbotAnswerServiceImplTest
! ids in tests but not in the spec: AC-002.11
trace chatbot (STORY-013, STORY-007, STORY-010): 19/25 acceptance criteria have a test
```

## Issues
None blocking. Open Minor items:

1. **Stale OpenAPI annotation (Minor, carried over from attempt 1, issue 4).**
   `backend/src/main/java/vn/tsolve/chatbot/issue/controller/ChatbotIssueController.java:35` still declares
   `@ApiResponse(responseCode = "400", description = "MSG06")` on `draftChatbotIssue`. That endpoint has no body any
   more, and the spec lists only 401/403/404/409 for it. *Fix:* remove the line and regenerate the client (Task 17 or
   Task 18 is fine).
2. **Tests name the removed AC-002.11 (Minor, carried over from attempt 1, issue 5).**
   `frontend/src/features/chatbot/hooks/chatbot-storage.test.ts:187`,
   `frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx:846` (and the comment at
   `chatbot-storage.ts:98`). The `sessionStorage` code goes away in Task 18. *Fix:* delete or retag these tests there.

## Assumptions made
- **A1 (open judgment call for the user, already raised):** AC-007.3 says a bad `askForProject` history gives 400
  **MSG06 with no field error**. The Jira App endpoint (`jiraapp`, outside this range) answers **MSG02 with
  `fieldErrors[0].field = history`** for more than 10 turns, and treats a missing `history` as empty. I judged only
  the chatbot side, at the `ChatbotQuestionRequest` / `askForProject` boundary, and it matches AC-007.3. The user
  decides whether AC-007.3 should defer to jira-app's validation (a spec change) or `jiraapp` should change.
- A2: the frontend sending `chatSessionId` from the last answer, and the mock echoing it, are interim until Task 18.
- A3: `EmailOutboxIT.concurrentClaims` (TimeoutException at line 442, both runs) was judged to be open BUG-298, a
  local environment failure that also happens on `main`. The range changes no worker code, and the test passed in
  attempt 1's run 2. It was not counted against this task.
- A4: the AC-007.3 test is on the `ChatbotQuestionRequest` constructor, not through the HTTP endpoint, because
  `askForProject` is a Java interface and the web endpoint no longer accepts `history`. The HTTP mapping of
  `InvalidChatbotRequestException` to 400 MSG06 is existing shared handling.
- A5: changeset file name and XML master differ from the task text; the spec does not name them (as in attempt 1).

## Test Report
See `task-16-conversation-store-test-report.md`. Surefire 3021/3021. Failsafe 934/935 (the one error is BUG-298,
which reproduced when run alone and is not in this range). spotless:check passes. The frontend did not change, so
attempt 1's result is reused.
