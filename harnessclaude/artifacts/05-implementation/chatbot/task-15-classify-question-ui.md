# Task 15: Show the classified answers in the web app

**Module:** chatbot
**Assignee:** anhtranhai835
**Status:** passing
**Iteration:** 3

---

## Session 2026-11-17 14:00

### Working on: classify-question-ui
- Coded by a Codex subagent (`gpt-6.1-sol`, reasoning high), at the user's request; reviewed by
  `harnessclaude-reviewer` (Opus, effort high): verdict pass, attempt 1
  (`06-evaluation/chatbot/task-15-classify-question-ui-review.md`).
- `ChatAnswer.tsx`: `GREETING` / `OFF_TOPIC` / `INJECTION` → `MSG_NEW_5` / `MSG_NEW_6` / `MSG_NEW_7`, no citations,
  no rating buttons; `chatbot-storage.ts` accepts the five outcomes and still drops unknown ones; the mock
  (`data.ts`, `handlers.ts`) answers AC-004.7 after the 400 checks, `INJECTION` before `OFF_TOPIC`.
- Message ids renamed from MSG157–MSG159 to `MSG_NEW_5`–`MSG_NEW_7` (user, 17/11): the fe-foundation catalog test
  AC-006.7 rejects unknown codes, decision D3 gives unnumbered messages provisional keys, and the workspace spec
  plans MSG157/MSG158. fe-foundation AC-006.7 and the chatbot spec reworded and re-approved; `catalog.test.ts`
  lists `MSG_NEW_5`–`MSG_NEW_7`. The quick change opened for `catalog.ts` was closed unused.
- BUG-321 (review finding): the mock normalizer also strips `\p{Cf}` and U+FE00–U+FE0F; test "AC-004.7 strips emoji
  joiners and selectors … (BUG-321)" red before, green after.

### Verify
- Chatbot feature tests 145/145 (`--maxWorkers=1`), catalog tests 211/211, typecheck, lint (1 existing warning in
  `tickets/TicketListFilters.tsx`), format pass. With parallel workers the test setup `beforeAll` of
  `src/test/setup.ts` can pass its 10 s hook timeout on this machine; files pass alone.
- Full `npm run test`: 120 files, 1939 tests pass; `lint` 0 errors; `build` passes; `generate:api` leaves no diff.

### Deviations
- `frontend/src/shared/messages/catalog.test.ts` (fe-foundation) changed, through the approved AC-006.7 rewording.

### Blockers
none

### Commit

- `2a4ee786` feat(chatbot): show classified answers in the web app (KAN-243). Review passed (attempt 1); BUG-321 fixed in the mock too.
