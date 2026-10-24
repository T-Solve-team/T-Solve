# Task 4: Rate an answer

**Module:** chatbot
**Assignee:** Trần Trung Hiếu
**Status:** completed
**Iteration:** 1

---

## Session 2026-10-24

### Working on: ratings (KAN-76, branch `feature/KAN-76-chatbot-ratings`)
- Contract: `PUT` / `DELETE /api/v1/chatbot/answers/{answerId}/rating` (`rateChatbotAnswer`, `removeChatbotAnswerRating`),
  `ChatbotRating`; client regenerated. MSW: two stateless handlers answering 204.
- `ChatTurn.rating?`, kept with the conversation; `chatbot-storage.ts` drops a turn with any other value (edge 26).
  `useConversations.setRating(conversationId, answerId, rating)`.
- `useRateAnswer`: `handlesOwnErrors`; a `busy` ref blocks a second click before the next render (edge 22); MSG06 on
  any error but a 401. The conversation id is taken at the click (edge 23).
- `RatingButtons` under every answer in `ChatAnswer`; a running request disables the buttons of the whole thread.
- The 401 rating test is in `KnowledgeChatbotPage.rating-401.test.tsx`: the app keeps one MSG07 dialog open per module
  (`app/providers/global-errors.ts`), so a second 401 test in the page test file cannot show its dialog.

```
trace chatbot (STORY-003): 6/6 acceptance criteria have a test
```

### Verify (frontend, one after another)
- `format:check`, `lint`, `typecheck` — clean · `test` — 27 files, 479 passed · `build` — OK · `generate:api` — no diff

### Hand-off (2026-10-24)
- done: ratings coded and reviewed (PASS, no critical findings), module `completed`
- open: nothing — the reviewer note (a save in flight when the page unmounts) was logged as BUG-008 and fixed by a
  quick change (`useRateAnswer` writes the rating to the session storage when the screen is gone)
- next: the next module in the manifest (`decomposed → plan`)
- branch: `feature/KAN-76-chatbot-ratings` (merged, deleted locally)
- PR: #56 merged as `c73c0c1`; KAN-76 is Done
