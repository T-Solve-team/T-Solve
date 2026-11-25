---
module: chatbot
feature: conversation-ui
task: 18
verdict: pass
attempt: 1
date: 2026-11-18
reviewer: harnessclaude-reviewer
iteration: 3
---

# Review: chatbot — Task 18: Saved conversations in the web app

## Summary
Reviewed feature commit `3a1b4d74` (base `dbd2af72`) and the final code at `e77bdca2`. Task 19 changed the list
query to `useInfiniteQuery`; AC-002.3 and AC-002.16 are judged in the Task 19 review.

The rest of this task matches the current spec:
- Conversations come only from TanStack Query: `useGetChatbotConversation` for the open conversation and the list
  query.
- `chatbot-storage.ts` and its test are deleted, so nothing is kept in browser storage.
- The ask sends `{ question, chatSessionId }` and no history. On success it writes the answer into the open
  conversation's cache, invalidates the list and the conversation, and opens the answer's `chatSessionId`.
- A rating writes the open conversation's query data.
- Delete asks MSG103 with the title and removes the row after success. It shows MSG06 on failure but not on a 401.
- Delete, the list, New conversation and Load more are disabled while an answer is pending.
- The list and the conversation each have their own loading, MSG06 and Retry states.
- The mocks keep conversations per dev user in memory.

All tests pass and traceability is 56/56. **Pass.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | **AC-001.6:** `KnowledgeChatbotPage.tsx:35` sends `chatSessionId: openId ?? undefined`; Send is disabled while the ask is pending. **AC-002.1 / AC-002.6:** with `openId === null` there is a single selected "New conversation" row. **AC-002.2:** `open(answer.chatSessionId)` plus list invalidation. **AC-002.4:** `getChatbotConversation` with `staleTime: 0`. **AC-002.5:** the request has no `history`. **AC-002.7:** no storage read or write; the old key is ignored (tested). **AC-002.8:** list `Spin`, MSG06 `Alert` + Retry; conversation MSG06 + Retry; New conversation stays usable. **AC-002.9 / AC-002.10:** unchanged availability check, tested on reopen. **AC-002.12:** UUIDs passed through. **AC-002.13:** `ConfirmDialog` MSG103 `{title}`; after success the row goes, the open conversation goes back to empty, and the cached conversation is removed; Cancel changes nothing. **AC-002.14:** `onError` shows MSG06 unless the status is 401; the global handler shows MSG07. **AC-002.15:** `isDisabled = disabled \|\| remove.isPending` on every list button, and `onConfirm` re-checks it. **AC-003.4:** the server rating, or the newer saved one via `setQueryData` on the right conversation key (`useRateAnswer.ts:25-38`). **AC-004.8:** `mocks/handlers.ts` keeps an in-memory map per dev user; 404 MSG04 for an unknown id; ratings stored on the turn; `resetMockData()` clears it |
| Completeness | pass | Every file the task names was changed; `chatbot-storage.ts` and its test are gone, and `toHistory` is removed. The task text says "list loading = `Spin`, error = MSG06 + Retry" (both done). The Delete button is an icon button with `aria-label="Delete"` (done) |
| Correctness | pass | Frontend: 1946/1946 tests, 138 chatbot tests. format:check, lint (0 errors), typecheck and build pass. `generate:api` leaves no diff |
| Integration | pass | Uses only `@/shared/api` generated hooks; no `shared/` or `app/` edits (Non-Functional 4/5). Query keys do not collide: list `['/api/v1/chatbot/conversations', …]` vs. conversation `['/api/v1/chatbot/conversations/{id}']`. The previous user's cache is dropped by `resetSession` (`removeQueries`), and the page is keyed by user id, so there is no cross-user cache leak |

### Security review (frontend)
- Nothing is stored in the browser. A user switch drops every non-session query (`shared/auth/useSession.ts:109`).
- Answer text and citations are still rendered as text (AC-001.8 tests unchanged and passing).

## Traceability
`bun harnessclaude/hooks/sdd.ts trace chatbot STORY-001 STORY-002 STORY-003 STORY-004 STORY-013` gives
**56/56**. For this task:
- AC-001.6: `KnowledgeChatbotPage.test.tsx`, `KnowledgeChatbotPage.conversations.test.tsx`
- AC-002.1 – AC-002.15: `KnowledgeChatbotPage.conversations.test.tsx`; AC-002.14 also in
  `KnowledgeChatbotPage.delete-401.test.tsx`
- AC-003.4: `KnowledgeChatbotPage.ratings.test.tsx`
- AC-004.8: `mocks/handlers.test.ts`

## Issues
No blocking issue.

1. **Info: a stale test name.** `KnowledgeChatbotPage.ratings.test.tsx:322`
   "AC-003.4 keeps the rating in memory when storage is blocked (edge 25)" still talks about browser storage, which
   no longer exists after this task. The test still passes. Suggested: rename it, or drop it if it no longer proves
   anything.
2. **Info: a short gap before a new conversation appears in the list.** After the first answer of a new
   conversation, the "New conversation" row disappears (`openId` is set) before the list refetch adds the new row
   (`useAskQuestion.ts:26`). This matches AC-002.2 once the refetch lands, which is tested. No action needed.

## Assumptions made
- AC-002.2 "add the conversation … to the list" is met by refetching the server list rather than inserting the row
  locally. The spec does not say how, and a refetch keeps one source of truth.
- Known open item A1 is not counted.

## Test Report
See `task-18-conversation-ui-test-report.md`. Frontend verify is green: 1946/1946 tests; generate:api leaves no
diff. Backend chatbot tests all pass; the only failure is BUG-298, outside chatbot. BUG-341 and BUG-342 rows match
their named tests.
