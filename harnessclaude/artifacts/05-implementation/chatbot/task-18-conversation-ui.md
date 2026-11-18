# Task 18: Saved conversations in the web app

**Module:** chatbot
**Assignee:** bluebearccc
**Status:** evaluating
**Iteration:** 3

---

## Session 2026-11-18

### Working on: conversation-ui
- Implemented by Codex on `feat/KAN-183-chatbot-persist-sessions` (KAN-183), using Task 17's generated client.
- Conversations and turns now live in TanStack Query. Opening the page selects the empty conversation and loads
  the server list; opening a saved conversation loads its ordered turns, citations and current ratings.
- An ask sends the question and the selected server `chatSessionId`, with no history. A confirmed answer updates
  its conversation cache, opens its server id and invalidates the list/detail. Rating saves/removals update their
  own conversation cache, including after leaving the screen.
- Added per-row Delete, MSG103 confirmation, MSG06 failure handling and pending-answer guards. List/detail loads
  have loading and MSG06/Retry states; a new conversation remains available after a load error.
- Mocks keep conversations and ratings in memory per fixture user, support list/open/delete and unknown-id 404s,
  and clear all conversations on reset. A real browser reload still clears mock memory, as the design specifies.
- Removed the browser-storage implementation and tests, including the obsolete AC-002.11 tests/comment. Old
  storage is ignored. No `toHistory` remains in the feature.
- Split the existing rating tests into their own file, retained the scroll and BUG-008 regressions, and adapted
  their network fixtures to the saved-conversation contract. No self-review was performed.

### Files
Modified:
- `frontend/src/features/chatbot/components/ConversationList.tsx`
- `frontend/src/features/chatbot/components/Conversation.module.css`
- `frontend/src/features/chatbot/hooks/useConversations.ts`
- `frontend/src/features/chatbot/hooks/useAskQuestion.ts`
- `frontend/src/features/chatbot/hooks/useRateAnswer.ts`
- `frontend/src/features/chatbot/model.ts`
- `frontend/src/features/chatbot/pages/KnowledgeChatbotPage.tsx`
- `frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx`
- `frontend/src/features/chatbot/mocks/handlers.ts`
- `frontend/src/features/chatbot/mocks/handlers.test.ts`
- `docs/bug-log.md`

Added:
- `frontend/src/features/chatbot/pages/KnowledgeChatbotPage.conversations.test.tsx`
- `frontend/src/features/chatbot/pages/KnowledgeChatbotPage.delete-401.test.tsx`
- `frontend/src/features/chatbot/pages/KnowledgeChatbotPage.ratings.test.tsx`
- This implementation record.

Deleted:
- `frontend/src/features/chatbot/hooks/chatbot-storage.ts`
- `frontend/src/features/chatbot/hooks/chatbot-storage.test.ts`

### Tests and test-first evidence
- New AC-004.8 mock tests initially failed: 10 failed / 47 passed, showing that list/open still returned generated
  fake data and turns/ratings were not stored. Their implementation passed 57/57; the added user-isolation test
  failed before per-user mock storage was added.
- The saved UI/current-rating tests initially reported 17 failures / 4 passes / 49 filtered tests against the
  old storage implementation. The implementation replaced that storage with generated queries/mutations.
- The final full suite includes all 131 chatbot tests, with zero failures. Coverage includes AC-001.6,
  AC-002.1–AC-002.10, AC-002.12–AC-002.15, AC-003.4 and AC-004.8; earlier ask/citation/rating tests remain.
- `bun harnessclaude/hooks/sdd.ts trace chatbot STORY-001 STORY-002 STORY-003 STORY-004`: exit 0,
  **43/43 acceptance criteria have a test**.

### Verification
Commands run in `frontend/` (Windows uses the corresponding `.cmd` launcher):

| Check | Result |
|---|---|
| `npm run format:check` | Exit 0 |
| `npm run lint` | Exit 0; 0 errors, one existing Fast Refresh warning at `src/features/tickets/components/TicketListFilters.tsx:23:14` |
| `npm run typecheck` | Exit 0 |
| `npm run test` (default workers) | Exit 1: 1,924 passed / 15 failed, 113 passed / 9 failed files; 644.06 s. Lint and targeted verification overlapped part of this run |
| `npm run test -- --maxWorkers=1` | Exit 0: **1,939/1,939 tests, 122/122 files**, no skips; 2366.83 s |
| Seven file-only reruns below | All exit 0: **153/153 tests** |
| `npm run build` | Exit 0; 3,756 modules transformed. Chunk-size warning remains (over 1,000 kB) |
| `npm run generate:api` + `git diff --exit-code -- src/shared/api/generated` | Both exit 0; generated client has no diff |

All static checks, build and API regeneration were run again at delivery. The jsdom pseudo-element
`getComputedStyle` messages remain non-failing test-environment output. Raw command output is only under
`%TEMP%\codex-kan183\` (not committed).

Each outside file that failed in parallel was rerun alone with
`npx vitest run <file> --maxWorkers=1`:

| File (under `frontend/src/features/`) | Result |
|---|---|
| `my-profile/components/SolutionFilters.test.tsx` | 23/23 passed |
| `ticket-import/components/JiraPullForm.test.tsx` | 6/6 passed |
| `ticket-import/components/ManualEntryForm.test.tsx` | 18/18 passed |
| `ticket-import/pages/ImportPreviewPage.test.tsx` | 53/53 passed |
| `wiki/components/FolderModal.test.tsx` | 16/16 passed |
| `wiki/pages/UploadDocumentsPage.test.tsx` | 17/17 passed |
| `wiki/pages/WikiHomePage.test.tsx` | 20/20 passed |

### Initial parallel-run failures (exact captured lines)
All paths below are under `frontend/src/features/`. The later full serial run passes every case.

| Case | Failure and captured location |
|---|---|
| Chatbot AC-002.3 saved-list first load | `Unable to find role="button" and name "How do I connect to the VPN?"`; `chatbot/pages/KnowledgeChatbotPage.conversations.test.tsx:110:18`. First list wait now uses the guideline's 5 seconds (BUG-341) |
| Chatbot AC-002.14 delete-expiry first load | `Unable to find role="button" and name "VPN"`; `chatbot/pages/KnowledgeChatbotPage.delete-401.test.tsx:22:32`. The separate targeted run also exposed the repeated MSG07-title query fixed as BUG-342; isolated rerun passes 1/1 |
| My Solutions AC-003.22 | `Unable to find role="table" and name "My Solutions"`; `my-profile/components/SolutionFilters.test.tsx:43:30`, called at `:120:11` (existing BUG-127) |
| Jira import AC-001.1 | `Error: Test timed out in 15000ms.`; `ticket-import/components/JiraPullForm.test.tsx:62:3` (existing BUG-334) |
| Manual Evidence AC-006.18 / AC-006.19 | `Error: Test timed out in 15000ms.`; `ticket-import/components/ManualEntryForm.test.tsx:133:3` (existing BUG-310) |
| Manual Draft AC-006.11 | `Error: Test timed out in 15000ms.`; `ticket-import/components/ManualEntryForm.test.tsx:275:3` (BUG-337) |
| Manual Draft AC-006.12 | `Error: Test timed out in 15000ms.`; `ticket-import/components/ManualEntryForm.test.tsx:294:3` (existing BUG-335) |
| Manual field errors AC-006.14 | `Error: Test timed out in 15000ms.`; `ticket-import/components/ManualEntryForm.test.tsx:386:3` (existing BUG-335) |
| Manual duplicate Ticket ID AC-006.14 | `Unable to perform pointer interaction as the element has pointer-events: none`; helper `ticket-import/components/ManualEntryForm.test.tsx:28:3`, case at line 419 (BUG-337) |
| Import remove rows AC-009.3 | `Error: Test timed out in 15000ms.`; `ticket-import/pages/ImportPreviewPage.test.tsx:209:5` (BUG-338) |
| Import selection AC-015.5, edge 50 | `Error: Test timed out in 15000ms.`; `ticket-import/pages/ImportPreviewPage.test.tsx:586:5` (existing BUG-336) |
| Import selection after Solution save AC-015.5 | `Error: Test timed out in 15000ms.`; `ticket-import/pages/ImportPreviewPage.test.tsx:634:5` (existing BUG-336) |
| Folder roundtrip AC-003.15 / AC-003.16 | `Error: Test timed out in 15000ms.`; `wiki/components/FolderModal.test.tsx:306:3` (BUG-339) |
| Upload default visibility AC-004.18 | `Unable to find role="table" and name "Documents"`; `wiki/pages/UploadDocumentsPage.test.tsx:349:33` (BUG-340) |
| Wiki Home AC-002.14 | `AssertionError: expected ... to have a length of +0 but got 1`; `wiki/pages/WikiHomePage.test.tsx:68:50` (existing BUG-262) |

### Bugs
- Added BUG-337–BUG-340, **Open**: additional outside-feature timing failures. Their source/tests/timeouts were
  left alone; every affected file passes independently and in the serial full suite.
- Added BUG-341–BUG-342, **Fixed** in this uncommitted feature: saved-list initial wait and the delete-expiry
  dialog assertion. Regression names cite their bug ids.
- Existing BUG-127, BUG-262, BUG-310 and BUG-334–BUG-336 were reproduced and left unchanged.

### Commits
- Codex's sandbox could not write `.git/index.lock`, so the supervisor (Claude) re-ran the frontend checks outside
  it and committed: format, lint, typecheck, build exit 0; `generate:api` stable; full `npm run test` 1936/1939 —
  the 3 failures are ticket-import timeouts (BUG-335, BUG-336), whose two files pass 71/71 alone.
- `default@3a1b4d74` feat(chatbot): saved conversations in the web app (KAN-183) — with BUG-341 and BUG-342 rows.
- `default@1eb039f7` docs(bug-log): BUG-337 – BUG-340, kept apart from the feature commit.

### Open
- No Task 18 spec questions or backend changes needed.
- Commit/recording requires Git metadata write access outside this sandbox. No push, merge or branch change occurred.
- Live backend/browser sign-in persistence was not exercised; this session's verification is automated frontend/MSW
  coverage. Independent evaluation remains next.
- The initial workspace audit reported unrelated `dashboard-audit` spec approval drift and existing warnings.

### Next up
Independent evaluation of conversation-api and conversation-ui; then the paging change (Task 19) once its spec is
approved.
