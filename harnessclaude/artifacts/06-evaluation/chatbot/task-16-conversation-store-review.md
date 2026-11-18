---
module: chatbot
feature: conversation-store
task: 16
verdict: fail
attempt: 1
date: 2026-11-18
reviewer: harnessclaude-reviewer
iteration: 3
---

# Review: chatbot — Task 16: conversation-store

## Summary
Most of the conversation store is right. The migration matches Interface Contracts. The ownership check runs before
any AI call. The turn is saved in the same transaction as its `chatbot_answer` row, the Jira App path never touches
a conversation, and the issue draft reads the turns before the answer from the database. I found no IDOR: every read
of a session is scoped by `user_id`, and `record` only takes an id that `history` has already checked.

The verdict is **fail** for two reasons. (1) The commit deleted the only tests of AC-007.3's main clause (400 MSG06
for a bad `history`) and replaced them with a test of a different sentence of the same AC. The 400 clause, which now
applies to `askForProject`, has no test anywhere. (2) A real bug on the AC-013.2 path: the backend now runs its own
stored turns through the *request* validator. A conversation whose stored answer is over 10,000 characters then
gets 400 MSG06 on every follow-up for the next 10 turns.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | fail | AC-013.1, .3, .4, .11, .12, AC-007.1/.2, AC-010.3/.8 behave as specified. AC-013.2 fails for a conversation that holds an answer longer than 10,000 characters (Issue 2). |
| Completeness | fail | AC-007.3 ("IF the `history` of an `askForProject` request is bad, THEN 400 MSG06, no field error, no AI, nothing stored") has no test (Issue 1). AC-013.5–.10 are Tasks 17/18 and not counted. |
| Correctness | pass | `mvn verify` green on rerun (2977 unit + 934 IT, spotless ok). Run 1 had one failure in the `review` module, a timing-sensitive test under load and unrelated. Frontend format, lint, typecheck and build pass; chatbot Vitest 144/144; `generate:api` leaves no diff. |
| Integration | pass | The contract (`ChatbotAsk`, optional `ChatbotAnswer.chatSessionId`, 404 on `askChatbot`, `draftChatbotIssue` without a body, `ChatbotIssueDraftRequest` removed) matches spec Interface Contracts. The generated client was regenerated. `jiraapp/` is unchanged and its ITs pass. `IsolationFixtures` cleans `chat_session`. |

### Stage 1: spec compliance per AC
- AC-013.1: pass. `ChatConversationServiceImpl.record` (line 60) creates the session with the trimmed question as title, and the DB defaults set `started_at = last_message_at`. The id is returned with `@JsonInclude(NON_NULL)`.
- AC-013.2: partial. It reads the last 10 rows oldest first (`ChatConversationServiceImpl.java:45`, `:79-87`) and returns the same id. It breaks for long stored answers (Issue 2).
- AC-013.3: pass. `save()` runs `answers.save` + flush + `turn.record` in one `TransactionTemplate`. The rollback is shown by `ChatbotConversationStoreIT.rollsBackTheAnswerAndTheConversationWhenTheTurnFails_AC_013_3`. A failed ask never reaches `save`.
- AC-013.4: pass. A bad UUID, a missing session or another user's session gives 404 MSG04 on the request thread, before the classifier (`ChatbotAnswerServiceImpl.java:176-177`). The IT checks it with all three kinds of id.
- AC-013.11: pass. Columns, PK defaults, FKs, `ON DELETE CASCADE`, `UNIQUE chatbot_answer_id`, the five-value check, both indexes and the rollback (applied and re-migrated in the IT) all match. The file is `047-chatbot-chat-session.sql` and it is registered in `db.changelog-master.xml`. The task text says `.yaml` and `047-chat-session.sql`, but the spec does not name the file, so this is fine.
- AC-013.12: pass. `askForProject` passes a no-op `Turn` (`ChatbotAnswerServiceImpl.java:218-219`), and the unit test checks there are no interactions.
- AC-007.1 / AC-007.2: pass. The retrieval text is "last question\nnew question" from the stored turns (IT: `embed("Turn 10\nfollow-up")`), and the stored turns go to the prompt as context.
- AC-007.3: **untested** (Issue 1). The code path does exist: `ChatbotQuestionRequest.validateHistory` throws `InvalidChatbotRequestException` (MSG06).
- AC-010.3: pass. `turnsBefore` returns the last 10 rows with `created_at` before the answer's message, none when there is no message (IT `givesTheDraftOnlyTheTurnsBeforeItsAnswer_AC_010_3`).
- AC-010.8: pass. No `@RequestBody` (`ChatbotIssueController.java:38-40`), 404 for unknown or another user's answers, 401 without a session.

### Stage 2: checklist
- Transactions: the session lookup runs in its own read-only transaction and the write runs later in the answer's transaction. A session deleted in between (possible once Task 17 adds delete) gives an FK violation, so a 500 after the AI call rather than 404 (Issue 3, Low).
- N+1 or performance: the indexes cover `(chat_session_id, created_at)` and `findByIdAndUserId` uses the PK. No N+1.
- Logging: no title, question, answer or citation is logged. The only new exception message is "Citations not serializable".
- Workspace routing: every DB read happens on the request thread before the AI call (`ChatbotIssueServiceImpl.java:122-124`, `ChatbotAnswerServiceImpl.java:176`).

### Security review (personal data, per-user ownership)
- Another user's `chatSessionId` on ask: 404, no AI call, nothing stored. Checked by the IT with a real DB (design C1).
- `record` → `touch(sessionId)` has no `user_id` filter, but the only caller passes an id that `history(user.id(), …)` has already verified. That is acceptable. A `user_id` guard in `touch` would be cheap defence in depth (optional).
- Draft: the answer is looked up by `(id, user_id)` first, and `turnsBefore` follows that answer's own `chat_message`, which always belongs to the same user's session. No cross-user read.
- The frontend `chatSessionId` comes from the last answer of the open conversation (`KnowledgeChatbotPage.tsx:36`), and the server checks ownership whatever the client sends.
- No Critical or High security findings.

## Traceability
```
✓ AC-007.1  ChatbotAnswerServiceImplTest
✓ AC-007.2  AnswerPromptTest
✓ AC-007.3  ChatbotAnswerControllerTest            <- tag only; the 400 clause is not exercised (Issue 1)
✓ AC-007.4  ChatbotAnswerServiceImplTest
✓ AC-010.1 … AC-010.9  (ChatbotIssueServiceImplTest, ChatbotIssueControllerTest, IssueTargetRepositoryIT, ChatbotConversationStoreIT)
✓ AC-013.1  ChatbotAnswerServiceImplTest, ChatbotConversationStoreIT
✓ AC-013.2  ChatbotAnswerServiceImplTest, ChatbotAnswerControllerTest, ChatbotConversationStoreIT
✓ AC-013.3  ChatbotAnswerServiceImplTest, ChatbotConversationStoreIT
✓ AC-013.4  ChatbotAnswerServiceImplTest, ChatbotAnswerControllerTest, ChatbotConversationStoreIT
✗ AC-013.5 … AC-013.10  no test (Tasks 17/18, not counted here)
✓ AC-013.11 ChatbotConversationStoreIT
✓ AC-013.12 ChatbotAnswerServiceImplTest
! ids in tests but not in the spec: AC-002.11   (removed from the spec in this range; a test still names it)
trace chatbot (STORY-013, STORY-007, STORY-010): 19/25 acceptance criteria have a test
```

## Issues

1. **AC-007.3 lost its tests (MISSING, blocks the verdict).**
   `backend/src/test/java/vn/tsolve/chatbot/answer/controller/ChatbotAnswerControllerTest.java:89-104`. The commit
   deleted `rejectsBadHistory_AC_007_3` (8 bad bodies → 400 MSG06, no `fieldErrors`, no service call) and
   `acceptsHistoryWithinLimits_AC_007_3` (10 turns of 1,000/10,000 characters accepted). The replacement
   `ignoresHistoryOnAWebQuestionAndPassesTheConversation_AC_007_3_AC_013_2` only checks that a web request ignores
   `history`. No test anywhere now asserts the clause "IF the `history` of an `askForProject` request is bad, THEN
   400 MSG06, no field error, call no AI, store nothing" (`grep InvalidChatbotRequestException|validateHistory`
   under `src/test` finds nothing).
   *Fix:* add an `AC_007_3` test at the `askForProject` boundary: `ChatbotQuestionRequest` with a null history,
   11 turns, a null turn, a 1,001-character question or a 10,001-character answer → `InvalidChatbotRequestException`
   (MSG06), with no LLM, embedding or repository call. Also add the boundary case that is accepted (10 turns at the
   limits). See Assumption A1 on the Jira App endpoint.

2. **A stored long answer blocks the follow-ups of its conversation (Major, AC-013.2).**
   `backend/src/main/java/vn/tsolve/chatbot/answer/service/impl/ChatbotAnswerServiceImpl.java:176-180` builds
   `new ChatbotQuestionRequest(request.question(), history)` from the DB turns. Its constructor
   (`backend/src/main/java/vn/tsolve/chatbot/answer/dto/ChatbotQuestionRequest.java:15-31`) rejects any turn whose
   answer is over 10,000 characters with `InvalidChatbotRequestException`, a 400 MSG06. Nothing limits the stored
   `ANSWERED` text: `Citations.of` does not truncate, there is no output-token limit in `ai/`, and
   `chat_message.answer_text` is `TEXT`. One long model reply therefore makes every later question in that
   conversation fail with 400 MSG06 until 10 more turns have pushed it out, and the user cannot add those turns. The
   check is meant for client input (AC-007.3), not for the server's own data. AC-013.2 says the backend SHALL take
   the last 10 rows as the earlier turns.
   *Fix:* do not run stored turns through the request validator. For example, build the internal request without
   `validateHistory` (a separate factory or an internal record), or clip each stored turn to the limits when reading
   (`ChatConversationServiceImpl.oldestFirst`). Add a test: a stored answer of 10,001 characters followed by a
   follow-up gets a 200.

3. **The session can be deleted between the check and the write (Low, design C3; reachable once Task 17 adds delete).**
   `backend/src/main/java/vn/tsolve/chatbot/conversation/service/impl/ChatConversationServiceImpl.java:61-66`.
   `sessions.touch(sessionId)` ignores its row count. If the session was deleted after `history()`, the
   `chat_message` insert fails on the FK and the ask ends in a 500 after the AI call, instead of the 404 MSG04 that
   design C3 expects.
   *Fix:* if `touch` returns 0, throw `ChatbotConversationNotFoundException` (the answer row rolls back with it).
   Optionally also filter `touch` by `user_id`.

4. **Stale OpenAPI annotation (Minor).**
   `backend/src/main/java/vn/tsolve/chatbot/issue/controller/ChatbotIssueController.java:35` still declares
   `@ApiResponse(responseCode = "400", description = "MSG06")` for `draftChatbotIssue`. The endpoint has no body any
   more and the spec lists 401/403/404/409 only. *Fix:* remove the line.

5. **A test still names an AC that no longer exists (Minor).** The trace reports `AC-002.11` in tests although this
   range removed it from the spec. *Fix:* rename or retag that test (frontend chatbot tests, Task 18 scope is fine).

## Assumptions made
- **A1 (judgment call for the user):** AC-007.3 now says a bad `askForProject` history gives 400 **MSG06 with no
  field error**. The Jira App endpoint (`jiraapp`, outside this range) already gives **MSG02 with
  `fieldErrors[0].field = history`** for more than 10 turns (`JiraAppQuestionControllerTest.rejectsMoreThanTenHistoryTurns_AC_010_3`),
  and treats a missing `history` as empty (`JiraAppQuestionRequest.toChatbotQuestion`). So the HTTP behaviour seen
  by the Jira App contradicts AC-007.3's wording. I judged the chatbot side only, at the `askForProject` service
  boundary, and did not fail the task for the `jiraapp` behaviour. The user should decide whether AC-007.3 should
  defer to jira-app's validation (a spec change) or whether `jiraapp` should change.
- A2: the frontend sending `chatSessionId` from the last answer, and the mock echoing any id, are treated as interim
  until Task 18 (AC-001.6, AC-002.x, AC-004.8 belong to Task 18).
- A3: the failure in the `review` module (run 1) and the 40 Vitest timeouts (run 1, during the Maven run) were
  judged to be load flakiness. Backend run 2 and the idle frontend rerun are the results that count (test report).
- A4: the changeset file name and the XML master differ from the task text, but the spec does not name them, so
  they were not counted against the task.

## Test Report
See `task-16-conversation-store-test-report.md`. Backend `mvn verify` BUILD SUCCESS on rerun (2977 + 934 tests,
spotless ok). Frontend format, lint, typecheck and build pass, chatbot tests 144/144, and `generate:api` gives no
diff.
