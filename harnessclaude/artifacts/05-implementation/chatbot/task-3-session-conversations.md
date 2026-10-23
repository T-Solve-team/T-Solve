# Task 3: Session conversations

**Module:** chatbot
**Assignee:** Trần Trung Hiếu
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-24 00:30

### Working on: session-conversations (KAN-72, branch `feature/KAN-72-chatbot-session-history`)
- `chatbot-storage.ts`: `{ version: 1, userId, conversations }` under `tsolve.session.chatbot.conversations`;
  another user's, broken, older or blocked data gives `[]` (the key is removed); a blocked write is ignored.
- `useConversations`: stored list (in memory, mirrored to storage), `openId` (`null` = the empty "New conversation",
  never stored), `addTurn(conversationId, turn)` creates the conversation with its first answer. The page fixes the
  conversation id when the question is sent, so the answer lands in its own conversation (edge 17).
- `ConversationList`: New conversation button + the list newest first, open one `aria-current`, all disabled while an
  answer is pending. Opening another conversation drops the empty one (not in the spec; keeps one empty entry).
- PR #51 finding 2: `.page` is a two-card grid of fixed height (`100vh - 88px` = header 64 + bottom padding 24, min 480px); the thread scrolls inside
  its card and `scrollIntoView` brings the newest turn into view. Browser check (mock mode, Employee): 3 questions,
  the thread scrolls inside the card, the page does not grow.
- Figma 12.1 list card compared (frame 4158:17930): "New conversation" is a default outlined button, every entry has a
  chat icon (`aria-hidden`), the open entry is tinted and primary-coloured, a long title wraps (the spec defines no
  length, so no spec change), the card ends after its last entry (`align-self: start`). Browser check repeated in mock
  mode: a 5-line title wraps, the card grows with it, the page stays fixed.
- Tests: 5 in `chatbot-storage.test.ts`, 12 new in the page test (AC-002.1–8, AC-002.11, edges 12–18, scroll).
- The user id is `useSession().session.user.id`; `?? 0` only keeps the types honest (the route guard has a session).

```
trace chatbot (STORY-002): 11/11 acceptance criteria have a test
```

### Verify (frontend, one after another)
- `format:check`, `lint`, `typecheck` — clean · `test` — 26 files, 455 passed · `build` — OK · `generate:api` — no diff

### Open
- Review by `harnessclaude-reviewer` after the commit.

### Next up
Commit (user agrees) → review → PR on "push".

### Hand-off (2026-10-24)
- done: session-conversations coded, Figma 12.1 matched, reviewed (PASS with notes, both fixed), module `completed`
- open: nothing in the chatbot module
- next: the next module in the manifest (`decomposed → plan`)
- branch: `feature/KAN-72-chatbot-session-history` (merged, deleted locally)
- PR: #53 merged as `4ac01af`; KAN-72 is Done
