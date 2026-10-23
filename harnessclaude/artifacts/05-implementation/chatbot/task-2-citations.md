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
