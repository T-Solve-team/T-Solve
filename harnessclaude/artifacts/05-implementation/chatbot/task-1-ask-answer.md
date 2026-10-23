# Task 1: Ask a question and show the answer

**Module:** chatbot
**Assignee:** Trần Trung Hiếu
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-23 20:30

### Working on: ask-answer (KAN-71, branch `feature/KAN-71-chatbot-ask-answer`)
- Contract: tag `chatbot`, `POST /api/v1/chatbot/answers`, 9 schemas. orval made `ChatbotCitation` a real TS union
  (`WikiDocumentCitation | TicketSolutionCitation`), so the design's fallback (one object type) was not needed.
- Mocks: VPN / leave / expense answers (Figma texts for VPN), `NO_SOURCE` otherwise, 400 MSG01 / MSG02 on `question`.
  A plain `http.post` handler, as review's decision handlers: orval's typed handler cannot return an error response.
- Screen: `QuestionBox` (TextArea, count 1,000, `rules.required` / `rules.maxLength` "Question", Enter sends,
  Shift+Enter adds a line, `aria-label="Question"` since Figma has no visible label), `ConversationThread`
  (pending question with `aria-busy` answer and `Spin`), `ChatAnswer` (plain text or MSG109), page with
  `handlesOwnErrors`: field errors under the box, otherwise MSG06; a 401 is left to the global handler.
- Found by the tests before the commit:
  - Two quick Enters sent the question twice (both submits run before the re-render) → a ref blocks the second.
  - `form.resetFields()` re-mounts the field and the box loses focus → `form.setFieldValue('question', '')`.
- Tests: `mocks/handlers.test.ts` (AC-004.1–3), `pages/KnowledgeChatbotPage.test.tsx` (AC-001.1–8, 001.12–14, edge
  cases 1–8, 11, 21). jsdom never ends antd's fade-out of the loading icon, so the test finds Send by `/Send/`.
- Commit: default@a5c323a

### Deviations from the plan
- The left list card is not shown yet (it would be empty); KAN-72 adds the list column.
- AC-004.5 (`resetMockData()`) moves to Task 2: the ask mock keeps no state; the unavailable-document list is the
  first data to reset (tasks.md updated).
- `sdd.ts trace chatbot STORY-001 STORY-004` cannot exit 0 yet: STORY-001 and STORY-004 are shared with Task 2.
  Every criterion of this task has a test:

```
  ✓ AC-001.1 … AC-001.8, AC-001.12, AC-001.13, AC-001.14  frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx
  ✓ AC-004.1, AC-004.2, AC-004.3  frontend/src/features/chatbot/mocks/handlers.test.ts
  ✗ AC-001.9, AC-001.10, AC-001.11, AC-004.4, AC-004.5  (Task 2, KAN-73)
trace chatbot (STORY-001, STORY-004): 14/19 acceptance criteria have a test
```

### Verify (frontend, one after another)
- `npm run format:check` — all files use Prettier code style
- `npm run lint` — clean · `npm run typecheck` — clean
- `npm run test` — 25 files, 421 tests passed
- `npm run build` — built in 30.7 s
- `npm run generate:api` — generated folder hash unchanged (`1154d3a…`)

### Blockers
none

### Next up
Review by `harnessclaude-reviewer`; then Task 2 (KAN-73, citations).
