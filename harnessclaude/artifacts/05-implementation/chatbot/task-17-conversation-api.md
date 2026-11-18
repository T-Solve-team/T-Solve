# Task 17: List, open and delete conversations; retention job

**Module:** chatbot
**Assignee:** bluebearccc
**Status:** evaluating
**Iteration:** 3

---

## Session 2026-11-18

### Working on: conversation-api
- Coded by Codex (`gpt-6.1-sol`, reasoning high) at the user's request, in a `workspace-write` sandbox with network.
  The first run was read-only and changed nothing; the second was stopped by a network loss and resumed.
- Codex could not reach Docker from the sandbox, so it ran the unit tests and Spotless only and left the change
  uncommitted. The supervisor (Claude) ran the full checks outside the sandbox and committed.
- Backend: `ChatbotConversationController` (list, open, delete), `ChatbotConversationService` +
  `ChatbotConversationServiceImpl`, response DTOs, `ConversationRetentionJob` (cron
  `CHATBOT_CONVERSATION_CLEANUP_CRON`, retention `CHATBOT_CONVERSATION_RETENTION`, both in `.env.example`),
  repository queries in `ChatSessionRepository`, `ChatMessageRepository`, `ChatbotAnswerRepository`.
- Contract: `frontend/openapi/tsolve-api.yaml` first, then `npm run generate:api` (regenerated client committed;
  a second run leaves no diff).
- Tests: `ChatbotConversationControllerTest` (6), `ChatbotConversationServiceImplTest` (5),
  `ConversationRetentionJobTest` (3), `ChatbotConversationIT` (6), `ConversationRetentionJobIT` (3), shared
  `ConversationDatabaseITSupport`. Covers AC-013.5 – AC-013.10.

### Verification (outside the sandbox)
- `mvn verify`: 3000 unit tests, 0 failures; 944 ITs, 1 error — `EmailOutboxIT.concurrentClaims` (open BUG-298,
  fails on `main`, worker module, not touched). All `vn.tsolve.chatbot.conversation` tests pass.
  `mvn spotless:check` passes.
- Frontend: `format:check`, `lint`, `typecheck`, `build`, `test` exit 0; `generate:api` stable.

### Commits
- `default@1ef4d425` feat(chatbot): list, open and delete saved conversations (KAN-183)
- `default@ac962717` docs(bug-log): BUG-334 – BUG-336 (frontend full-suite timeouts), evidence on BUG-100,
  BUG-105, BUG-252 — kept apart from the feature commit.

### Open
- A1 (from Task 16 review): AC-007.3 vs the Jira App's history validation — waiting on the user.
