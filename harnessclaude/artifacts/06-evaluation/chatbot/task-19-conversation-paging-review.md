---
module: chatbot
feature: conversation-paging
task: 19
verdict: pass
attempt: 1
date: 2026-11-18
reviewer: harnessclaude-reviewer
iteration: 3
---

# Review: chatbot — Task 19: Page the conversation list with Load more

## Summary
Reviewed feature commit `e77bdca2` (base `84c87fe4`) against the changed AC-013.5, AC-002.3 and the new AC-002.16 and
AC-004.8.

Backend:
- `listChatbotConversations` takes a `Pageable` with `@PageableDefault(size = 20)` and caps the size at 100.
- It drops any client sort and orders `started_at DESC, chat_session_id DESC` through the derived query.
- It returns `PagedModel` (`content` + `page{size, number, totalElements, totalPages}`), with only the caller's
  rows.

Frontend:
- `useInfiniteQuery` with `getNextPageParam` on `number + 1 < totalPages`.
- A Load more button that shows as loading while fetching and is hidden at the end.
- A failed next page gives an MSG06 toast and keeps the rows and the button.
- An ask or a delete invalidates the key prefix, so the loaded pages are refetched in order.

The mock pages the same way. The contract uses the shared `Page` / `Size` parameters and `PageMetadata`.

All tests pass and traceability is 56/56. Two Low findings, neither blocking. **Pass.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | **AC-013.5:** `ChatbotConversationController.java:41-44`. Default 0/20; `Math.min(size, 100)`. `PageRequest.of` with no sort, so `sort=title,asc` is ignored. Spring's resolver reads a negative page as 0 and a size below 1 as the default. `findByUserIdOrderByStartedAtDescIdDesc(user.id(), page)` returns own rows only, ties ordered by id, and an empty page past the end (IT). **AC-002.3:** first page `size` 20; the open row is highlighted. **AC-002.16:** `hasNextPage` controls the button. `loading={isFetchingNextPage}`. `loadMore` shows MSG06 when `isFetchNextPageError`. In `ConversationList.tsx:46,69` a failed *next* page keeps the rows and hides the full-list error. Invalidating `getListChatbotConversationsQueryKey()` (a prefix of `[…, {size: 20}]`) refetches every loaded page, and TanStack v5 stops at a page whose `getNextPageParam` is undefined, so a delete that shrinks the total fetches no phantom page. **AC-004.8:** the mock list pages with default 20, a 100 cap, `totalPages` and an empty page past the end |
| Completeness | pass | Every task item is present: contract first, regenerated client (`chatbotConversationSummaryPage.ts`, `listChatbotConversationsParams.ts`), controller, service and repository, hook, list, mocks, and the tests named in the task (controller defaults and cap, IT own rows / order / ties / past the end, page tests for append, hide, loading, MSG06, refetch after ask (P1) and after delete (P2)). Design edge cases P1–P4 all have tests |
| Correctness | pass | Backend unit tests 3001/3001; `ChatbotConversationControllerTest` 7/7, `ChatbotConversationIT` 7/7. Frontend 1946/1946. format, lint, typecheck and build pass. `generate:api` leaves no diff. spotless passes |
| Integration | pass | The response shape matches the team's `PagedModel` pattern (`AuditLogController`, `MyProfileController`) and the shared `PageMetadata` schema. The contract's `Size` parameter has `maximum: 100`, which matches the server cap. Only chatbot uses the regenerated client (scout) |

### Security review (paging parameters)
- `size` is capped at 100, so one request cannot fetch an unbounded number of rows.
- The client `sort` is discarded, so no other property can be sorted on (no property-name probing).
- `page` cannot leave the user's scope: the query is always `WHERE user_id = :principal`.

## Traceability
`bun harnessclaude/hooks/sdd.ts trace chatbot STORY-001 STORY-002 STORY-003 STORY-004 STORY-013` gives
**56/56**. For this task:
- AC-013.5: `ChatbotConversationControllerTest`, `ChatbotConversationIT`, `ChatbotConversationServiceImplTest`,
  `mocks/handlers.test.ts`
- AC-002.3 and AC-002.16: `KnowledgeChatbotPage.conversations.test.tsx` (`paging` describe)
- AC-004.8: `mocks/handlers.test.ts` ("pages the list as AC-013.5 …")

## Issues
No blocking issue.

1. **Low: a huge `page` gives 500, not an empty page.**
   `backend/src/main/java/vn/tsolve/chatbot/conversation/controller/ChatbotConversationController.java:43`.
   A `page` whose offset exceeds `Integer.MAX_VALUE` (for example `?page=200000000&size=20`) makes Spring Data JPA
   throw `InvalidDataAccessApiUsageException` ("Page offset exceeds Integer.MAX_VALUE"). `GlobalExceptionHandler`
   maps that to 500, but AC-013.5 says "a page past the end has no rows". Found by code reading, not reproduced.
   Only a hand-crafted request can hit it; the web app never sends such a page. Suggested fix: when
   `(long) page * size > Integer.MAX_VALUE`, return an empty `PageImpl` with the real total, or clamp `page`, and add
   one controller test.
2. **Low (judgment call): Load more shows MSG06 on a 401 too.**
   `frontend/src/features/chatbot/hooks/useConversations.ts:50-53`. When the next page fails with a 401,
   `loadMore` shows an MSG06 toast and the global handler also opens the MSG07 dialog. AC-002.16 says only "IF the
   next page cannot be loaded, THEN … MSG06". The code follows it literally. But AC-001.13, AC-002.14 and AC-003.5
   all say "a 401 instead signs the user out with MSG07, with no MSG06". If the same rule is meant here: skip the
   toast when `error.status === 401` (as `remove.onError` does) and add a 401 test. See Assumptions.

## Assumptions made
- AC-002.16 "WHILE the list shows fewer conversations than the page's `totalElements`" is implemented as
  `number + 1 < totalPages` of the last loaded page. These are the same as long as the loaded pages are full, which
  holds with the refetch-all invalidation. Accepted.
- Issue 2 is recorded as a judgment call rather than a failure, because AC-002.16 does not exclude a 401. The user
  should decide whether the "401 gives MSG07 with no MSG06" rule of AC-002.14 / AC-003.5 also applies to Load more.
  If it does, that is a spec wording change plus a small code fix.
- Known open item A1 is not counted.

## Test Report
See `task-19-conversation-paging-test-report.md`. Backend surefire 3001/3001. Failsafe 945 with 1 error: BUG-298,
`EmailOutboxIT.concurrentClaims`, known, outside chatbot, and it also failed when rerun alone. Spotless passes.
Frontend verify is green: 1946/1946, build passes, `generate:api` leaves no diff. Traceability 56/56.
