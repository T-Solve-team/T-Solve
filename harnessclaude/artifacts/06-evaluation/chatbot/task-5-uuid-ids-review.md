---
module: chatbot
feature: uuid-ids
task: 5
verdict: pass
attempt: 1
date: 2026-10-26
reviewer: harnessclaude-reviewer
---

# Review: chatbot Task 5: Ids are UUID strings (KAN-205, STORY-002, AC-002.12)

## Summary
AC-002.12 is met and has named tests. This is the chatbot module's own record of the review first filed under fe-foundation (`../fe-foundation/task-10-uuid-ids-review.md`). No critical finding. Verdict: PASS.

## Criteria
| Criterion | Result | Detail |
|---|---|---|
| Functionality | pass | `chatbot-storage.ts` writes version 2 and `readConversations` drops version 1, a non-string `userId` and a WIKI_DOCUMENT citation whose id is not a string (lines 7-15, 59-65); `handlers.ts:42` no longer maps ids to numbers; `useUnavailableDocuments`, `ConversationThread`, `CitationList`, `ChatAnswer` use `string` and `ReadonlySet<string>` |
| Completeness | pass | yaml: `wikiDocumentId`, `wikiDocumentIds` query parameter and `UnavailableDocuments.wikiDocumentIds` are `string`/`uuid`; generated files follow; mocks use UUID literals |
| Correctness | pass | scoped run: 4 files, 96 tests pass |
| Integration | pass | Q1: `useConversations(userId)` and `useRateAnswer(userId, setRating)` take a required `userId`, no `?? 0`; Q3: `KnowledgeChatbotPage` reads the session and renders `KnowledgeChatbot({userId})`, null without a session |

## Traceability
`trace chatbot (STORY-002): 12/12 acceptance criteria have a test`. AC-002.12: KnowledgeChatbotPage.test.tsx, handlers.test.ts, chatbot-storage.test.ts. No MISSING.

## Issues
Critical: none.
Minor:
1. `ConversationThread.tsx:64` `.sort()` on strings is fine for a stable query key, not for display.
2. `KnowledgeChatbotPage.tsx` returns null without a session; unreachable behind the guard (Q3, accepted).
3. Mock ids (`...9001`) are RFC-shaped fixtures, not generated; fine.

## Assumptions made
- Q1 and Q3 judged as decided by the user.
- Full suite (520) accepted from the run after commit 579c669; only `src/features/chatbot` re-run here.
- Version 1 data is dropped, not migrated, as AC-002.12 says.

## Test Report
See `task-5-uuid-ids-test-report.md`.
