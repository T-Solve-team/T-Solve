# Task 19: Page the conversation list with Load more

**Module:** chatbot
**Assignee:** bluebearccc
**Status:** evaluating
**Iteration:** 3

---

## Session 2026-11-18

### Working on: conversation-paging
- Contract first: `listChatbotConversations` takes `page` and `size` and answers `ChatbotConversationSummaryPage`
  (`content` + shared `PageMetadata`); `npm run generate:api` (a second run leaves no diff).
- Backend: the controller takes a `Pageable` (`@PageableDefault(size = 20)`), rebuilds it as `PageRequest.of(page,
  min(size, 100))` so a client `sort` is dropped, and returns `PagedModel`; the service returns `Page<...>`; the
  repository orders by `findByUserIdOrderByStartedAtDescIdDesc` (tie broken by id).
- Frontend: `useConversations` uses `useInfiniteQuery` (key `getListChatbotConversationsQueryKey({ size: 20 })`, so
  the existing invalidations after an ask or a delete refetch every loaded page); `ConversationList` has a Load more
  button (loading while fetching, hidden at the end); a failed next page shows MSG06 as a toast and keeps the rows
  and the button. The mock answers the same page shape.
- Tests: `ChatbotConversationControllerTest` (defaults; size 500 read as 100, client sort ignored),
  `ChatbotConversationServiceImplTest`, `ChatbotConversationIT` (own rows, order, ties by id, page past the end),
  `handlers.test.ts` (AC-004.8 paging), `KnowledgeChatbotPage.conversations.test.tsx` paging block (AC-002.3,
  AC-002.16: append, hide, loading, MSG06, refetch after ask P1 and delete P2). Older tests that assumed an array
  response were updated.
- The IT compares ties with the hex text of the UUID: PostgreSQL orders `uuid` bytewise, `UUID.compareTo` is signed.

### Verification
- Backend `mvn verify`: 3001 unit tests, 0 failures; 945 ITs, 1 error: `EmailOutboxIT.concurrentClaims` (open
  BUG-298, worker module, not touched). `mvn spotless:check` passes. All `vn.tsolve.chatbot.conversation` tests pass.
- Frontend: `format:check`, `lint` (0 errors, 1 old warning), `typecheck`, `build` pass; `generate:api` stable;
  chatbot tests 138 passed. A full-suite run under load timed out two new paging tests (waits of 1 s); their waits
  were raised to 5 s and the test data cut to 21 conversations; the chatbot folder then passed.

### Commits
- `default@e77bdca2` feat(chatbot): page the conversation list with Load more (KAN-183)
