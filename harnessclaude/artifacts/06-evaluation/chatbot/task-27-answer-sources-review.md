---
module: chatbot
feature: answer-sources
task: 27
verdict: pass
attempt: 1
date: 2026-11-26
reviewer: harnessclaude-reviewer
iteration: 4
---

# Review: chatbot — Task 27: Store the sources of each answer (STORY-014)

Scope: commit `112f6f28` (`default` repo, branch `feat/KAN-463-chatbot-rag-eval`), plus the uncommitted refactor of
`ChatbotSource` (it is now public; `kind()` was extracted from `stored()`). Checked against chatbot spec STORY-014
(AC-014.1–014.5), Requirement 12, the Interface Contracts `chatbot_answer.sources` row, and search spec AC-010.2.

## Summary
The code meets STORY-014. `ChatbotAnswerServiceImpl.answer` builds the stored list from `kept`, in number order,
with `kind`, `id` (`ticketId` / `wikiDocumentId`), similarity rounded to 4 decimals, and `cited`. `cited` is true
when the number is in `Citations.of(...).citations()`. `Citations.of` keeps the kept numbering and removes only
markers above `kept.size()`, so `cited` means "the `[n]` marker is in the stored text". The AC-005.5 `NO_SOURCE` row
stores the kept list with every `cited` false. Every early return (greeting, refused, no kept source) uses the entity
default `List.of()`. A failed ask saves nothing. `RetrievedSolution.ticketId` is read only inside the chatbot. No
response DTO has a `sources` component, and `TicketSolutionCitation` has no id (AC-006.3, AC-010.2). The changeset and
its rollback match AC-014.4. `056` is the next free number on `origin/main` (055 is the last one there).

One minor gap: no test writes a non-empty `sources` list through Hibernate to Postgres and reads it back.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-014.1–014.5 hold for `ask` and `askForProject` (same `answer` path) |
| Completeness | pass | All 5 ACs and search AC-010.2 are covered; trace 5/5 |
| Correctness | pass | Targeted unit tests pass (run by the reviewer); no persistence round-trip test for non-empty `sources` (Issue 1) |
| Integration | pass | `RetrievedSolution` has no consumer outside chatbot; `SolutionRetrievalRepository.nearest` now returns `TicketSolutionRow` with `public_id` |

## Traceability
```
✓ AC-014.1  backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/ChatbotAnswerServiceImplTest.java
✓ AC-014.2  backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/ChatbotAnswerServiceImplTest.java
✓ AC-014.3  backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/ChatbotAnswerServiceImplTest.java
✓ AC-014.4  backend/src/test/java/vn/tsolve/chatbot/ChatbotSchemaIT.java
✓ AC-014.5  backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/ChatbotAnswerServiceImplTest.java
trace chatbot (STORY-014): 5/5
✓ AC-010.2 (search)  SolutionRetrievalImplTest.java, SolutionRetrievalRepositoryIT.java
```

## Issues
1. **minor — AC-014.1 — no persistence round-trip test for a non-empty `sources`.**
   `ChatbotAnswer.java` (the `@JdbcTypeCode(SqlTypes.JSON) List<AnswerSource> sources` field, added in 112f6f28).
   The AC-014.1 tests check the entity field through a mocked `ChatbotAnswerRepository`. `ChatbotSchemaIT` only
   checks the `'[]'` default through JDBC. No IT returns a solution from the mocked `SolutionRetrieval`, so no test
   writes a filled list to Postgres or reads it back. `ChatbotIssueServiceImpl` loads `ChatbotAnswer` entities
   (`findByIdAndUserId`), so the read path is live code.
   *Failure scenario:* a Hibernate FormatMapper or Jackson naming change writes different keys (for example
   `number` instead of `n`), or fails to read `List<AnswerSource>` when an answer is loaded for a Jira issue draft.
   No test fails.
   *Fix:* in `ChatbotConversationStoreIT` (real Postgres), have the mocked `SolutionRetrieval.nearest` return one
   `RetrievedSolution` and the LLM cite `[1]`. Then assert
   `SELECT sources::text` = `[{"n": 1, "kind": "TICKET", "id": "<uuid>", "similarity": 0.9, "cited": true}]` and that
   `answers.findById(id).getSources()` equals the stored list. Name the test `..._AC_014_1`.

## Assumptions made
- AC-014.1 "this holds for the `NO_SOURCE` of AC-005.4": when no source reaches the threshold, nothing is kept, so
  `[]` is stored (as AC-014.2 says). I read the two criteria as consistent, not conflicting.
- The working-tree change that makes `ChatbotSource` public belongs to Task 28. I reviewed it here only to confirm
  that `stored()` behaves as before.

## Test Report
See `task-27-answer-sources-test-report.md`.
