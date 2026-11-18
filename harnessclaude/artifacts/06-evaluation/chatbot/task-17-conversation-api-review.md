---
module: chatbot
feature: conversation-api
task: 17
verdict: pass
attempt: 1
date: 2026-11-18
reviewer: harnessclaude-reviewer
iteration: 3
---

# Review: chatbot — Task 17: List, open and delete conversations; retention job

## Summary
Reviewed feature commit `1ef4d425` (base `1423cd13`). The final code at `e77bdca2` / HEAD `574b9cff` was checked
against the current spec (approved version 24e6b0d5). Task 19 later rewrote the AC-013.5 list endpoint, and that is
judged in the Task 19 review. Everything else in this task holds:
- Open and delete are scoped to the owner.
- A malformed, unknown or another user's id gets 404 MSG04 and changes nothing.
- Delete removes the session (the messages go by cascade), then only the unrated `ANSWERED` / `GREETING` /
  `OFF_TOPIC` answers, all in one transaction with the audit row.
- The retention job deletes only sessions idle past the cutoff. It re-checks the cutoff under a row lock, uses one
  transaction per session and logs ids only.
- 401 MSG07 without a session, CSRF on DELETE, no role needed.

All chatbot tests pass. Traceability is 56/56. No critical, high or medium finding. **Pass.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | **AC-013.6:** `ChatbotConversationServiceImpl.get` returns turns oldest first (`findByChatSessionIdOrderByCreatedAtAsc`), stored citations from JSONB, the live rating from `chatbot_answer` (one `findAllById`, no N+1), and no `chatSessionId` inside a turn (5-arg record, `NON_NULL`). `rating` is always written, also as null (`@JsonInclude(ALWAYS)`). **AC-013.7:** `delete` locks the own session (`findForUpdateByIdAndUserId`), collects the answer ids, deletes and flushes the session (DB `ON DELETE CASCADE`), then runs `deleteDisposableByIds` (`rating IS NULL AND outcome IN ('ANSWERED','GREETING','OFF_TOPIC')`), all in one `@Transactional`; it answers 204. **AC-013.8:** `parseId` maps a malformed id to MSG04, and own-user lookups give 404 for unknown or foreign ids. **AC-013.9:** `ConversationRetentionJob` has `@Scheduled(cron = "${CHATBOT_CONVERSATION_CLEANUP_CRON:0 0 2 * * *}", zone = "Asia/Ho_Chi_Minh")`, retention from `CHATBOT_CONVERSATION_RETENTION:30d`, strict `<` cutoff, `deleteExpired` in `REQUIRES_NEW` with `findExpiredForUpdate` (the cutoff is re-checked under the lock), and failures are caught per id and logged by id only. **AC-013.10:** `@PreAuthorize("isAuthenticated()")` with no role; the 401 and MSG07 come from the security chain |
| Completeness | pass | Every task deliverable is present: controller, DTOs, service and impl, job with the `ponytail:` comment, repository methods, contract paths and schemas, regenerated client, both settings in `.env.example` (the repo-root file; there is no `backend/.env.example`). The Out of Scope note on running the job per Workspace is respected |
| Correctness | pass | Backend unit tests 3001/3001. Chatbot ITs all pass. The only IT error is `EmailOutboxIT.concurrentClaims` (known BUG-298, worker, untouched by this range; it also failed when rerun alone). `spotless:check` passes. Frontend verify is green; `generate:api` leaves no diff |
| Integration | pass | The contract paths `listChatbotConversations` / `getChatbotConversation` / `deleteChatbotConversation` match spec Interface Contracts (401/403/404 Problem). `ChatbotConversationTurn.rating` is a nullable enum. The generated client changes are additive; no other feature imports them (scout) |

### Security review (per-user scoping, retention, logs)
- **IDOR:** `get` uses `findByIdAndUserId(id, user.id())` and `delete` uses `findForUpdateByIdAndUserId`; the user id
  always comes from the principal. Tested with another user's id, a random UUID and `bad-id`
  (`ChatbotConversationIT.unknownAndOtherUsersIdsChangeNothing_AC_013_8`), and the counts are unchanged.
- **Retention deletes only idle sessions.** The candidate query and the locked re-check both use
  `last_message_at < cutoff`. A concurrent ask's `touch()` either commits first (the re-check skips the session) or
  waits and then finds no row (BUG-333 path, 404). Covered by
  `ConversationRetentionJobIT.rechecksLastMessageAtAfterCandidateSelection_AC_013_9` and the boundary case.
- **Delete vs. a concurrent rating:** under READ COMMITTED, the `DELETE … WHERE rating IS NULL` re-evaluates the row
  after the rating's row lock is released, so a just-rated answer is kept.
- **No content in logs:** the job logs `id` only. `readCitations` throws without Jackson's message. Covered by
  output-capture tests (`logsNoConversationContent_AC_013_10`, `continuesAfterFailureAndLogsOnlyTheId_…`).
- CSRF is enforced on DELETE (`rejectsDeleteWithoutCsrf_AC_013_10`).

## Traceability
`bun harnessclaude/hooks/sdd.ts trace chatbot STORY-001 STORY-002 STORY-003 STORY-004 STORY-013` gives
**56/56 acceptance criteria have a test**. For this task:
- AC-013.6, AC-013.7 and AC-013.8: `ChatbotConversationServiceImplTest`, `ChatbotConversationControllerTest`,
  `ChatbotConversationIT`
- AC-013.9: `ChatbotConversationServiceImplTest`, `ConversationRetentionJobTest`, `ConversationRetentionJobIT`
- AC-013.10: `ConversationRetentionJobTest`, `ChatbotConversationControllerTest`, `ChatbotConversationIT`
- AC-013.5: see the Task 19 review

## Issues
No blocking issue.

1. **Info: test names differ from the task text.** The task names `list_AC_013_5`, `open_AC_013_6` and so on. The
   tests use longer names that still carry the ids (for example
   `deletesOnlyDisposableAnswersOfTheConversation_AC_013_7`). Traceability is satisfied; no action needed.
2. **Info: the job loads all candidate ids at once.**
   `ConversationRetentionJob.deleteExpiredConversations` (`ConversationRetentionJob.java:35`) calls
   `findExpiredIds`. The daily run bounds the set; no action needed.

## Assumptions made
- AC-013.10 "no web session": checked through `@WebMvcTest` with the real `SecurityConfig` (401 MSG07 on all three
  endpoints). I did not repeat it against a running server.
- The extra audit row on a user delete (`AuditAction.DELETE, "chat_session", id`) is accepted. The spec neither asks
  for it nor forbids it, and it holds no content. A failed audit rolls the delete back
  (`deletionRollsBackIfAuditFails_AC_013_7`).
- Known open item A1 (AC-007.3 vs. jiraapp history validation) is a pending user decision outside this task and is
  not counted.

## Test Report
See `task-17-conversation-api-test-report.md`. Backend:
- Surefire 3001/3001.
- Failsafe 945 with 1 error: BUG-298, known and outside chatbot.
- Spotless passes.

Frontend: format, lint, typecheck, test (1946/1946), build and generate:api (no diff) all pass.
Traceability 56/56.
