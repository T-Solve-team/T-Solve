# Task 2: Citations of an answer

**Module:** chatbot
**Assignee:** Trần Trung Hiếu
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-23 21:30

### Working on: citations (KAN-73, branch `feature/KAN-73-chatbot-citations`)
- Contract: `GET /api/v1/chatbot/unavailable-documents` (`wikiDocumentIds`, exploded) and `UnavailableDocuments`;
  client regenerated. Mock: `UNAVAILABLE_DOCUMENT_IDS = [9003]` (the expense guide); the answers and this list
  never change, so `resetMockData()` stays empty and AC-004.5 checks the initial data after it.
- `CitationList` (number order; Wiki link to `paths.wikiDocument`, MSG110 in secondary text when unavailable;
  ticket: "Solution from a ticket" with Show / Hide, `aria-expanded`), `TicketSolution` (`<dl>`, Workaround only when
  not null), `useUnavailableDocuments` (one query per shown conversation, sorted unique ids, no toast on error).
- Finding 1 of the PR #51 review: AC-001.7 reworded (box emptied only if it still holds the sent question); spec
  approved again (3c134168); the page compares the box value before clearing it.
- BUG-007, found in the browser check: leaving the empty box showed MSG01 (AppForm checks on blur). `QuestionBox`
  now checks on change and on send only. Logged in `docs/bug-log.md`; test `… (BUG-007)` failed before the fix.
- Tests: AC-001.7 (number order, text kept), AC-001.8 (HTML in a citation), AC-001.9–11, AC-002.9–10, AC-004.4–5,
  BUG-007. The "held answer" setup of AC-001.6 became the `holdAnswer` helper, reused by AC-001.7.
- Browser check (mock mode, Employee) against Figma (b) and (f): matches. The page still grows with long chats
  (PR #51 review finding 2) — KAN-72.
- Commits: default@c0d46fa (citations), default@b64f804 (BUG-007)

```
trace chatbot (STORY-001, STORY-004): 19/19 acceptance criteria have a test
```

### Verify (frontend, one after another)
- `npm run format:check`, `lint`, `typecheck` — clean
- `npm run test` — 25 files, 435 tests passed
- `npm run build` — OK · `npm run generate:api` — no diff

### Blockers
none

### Next up
Review by `harnessclaude-reviewer`; then Task 3 (KAN-72, session conversations).

## Session 2026-10-23 22:30

### Working on: citations — review attempt 1 FAIL, fixing (attempt 2)
- Finding 1 (critical): the AC-002.10 test now waits until the availability query for [9003] is in `error` before
  asserting the links and that no message shows.
- Finding 2: `useUnavailableDocuments` keeps the previous result (`placeholderData: keepPreviousData`) while a
  follow-up's documents are checked; new test "checks the Wiki documents of every turn and keeps MSG110 while it
  checks again" failed before the fix (MSG110 disappeared).
- Finding 3: test for citations that arrive out of order.
- Finding 5: AC-002.9 tests assert the ids sent ([9003], then [9001, 9003]).
- Finding 4 (the BUG-007 test waits 500 ms for a message that must not come): kept, a negative check needs a wait.
- Guideline 09 (FE Lead's call): one line on the question box's `validateTrigger="onChange"` (BUG-007), as a quick
  change on that file only.
- Commits: default@4c9f297 (fixes), default@a5fe25b (guideline 09)
- Verify: format, lint, typecheck clean; `npm run test` 25 files, 437 passed; build OK; `generate:api` no diff.

### Next up
Review attempt 2.

