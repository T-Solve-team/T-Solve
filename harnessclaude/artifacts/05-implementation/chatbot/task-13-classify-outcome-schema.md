# Task 13: New outcomes in the schema and the contract

**Module:** chatbot
**Assignee:** anhtranhai835
**Status:** evaluating
**Iteration:** 3

---

## Session 2026-11-17 12:45

### Working on: classify-outcome-schema
- Changeset `043-chatbot-answer-outcomes.sql` (author `chatbot`): drops and re-adds `ck_chatbot_answer_outcome`
  with `ANSWERED`, `NO_SOURCE`, `GREETING`, `OFF_TOPIC`, `INJECTION`; the rollback restores the two-value check
  and its comment says classified rows must be deleted first (edge case Q9).
- Included in `db.changelog-master.xml` and in the test changelog `search-only.xml` (used by `ChatbotSchemaIT`).
- `ChatbotAnswerResponse.Outcome` gains the three values. Contract `ChatbotAnswerOutcome` enum widened; the
  "empty when NO_SOURCE" descriptions now say "unless ANSWERED"; `npm run generate:api` regenerated
  `chatbotAnswer.ts`, `chatbotAnswerOutcome.ts`, `chatbotTurn.ts`.
- Tests (`ChatbotSchemaIT`): `acceptsClassifiedOutcomes_AC_009_8`, `rejectsUnknownOutcome_AC_009_8`,
  `rollsBackToTwoOutcomes_AC_009_8` — red before the changeset (GREETING rejected), green after.
- Trace: `sdd.ts trace chatbot STORY-009` → 1/8 (AC-009.8 by `ChatbotSchemaIT`); AC-009.1–AC-009.7 are Task 14.

### Verify
- Local JDK: `JAVA_HOME` points to a missing `jdk-21`; ran with `jdk-24` (`release 21`) and
  `-DargLine=-Dnet.bytebuddy.experimental=true` (Mockito cannot instrument JDK 24 otherwise).
- `mvn verify`: unit tests all pass; ITs 898/899. The one error is `vn.tsolve.worker.EmailOutboxIT.concurrentClaims`
  (`TimeoutException` at line 442 after ~90 s), reproduced alone; this task touches no worker code. Not confirmed on
  JDK 21 — CI decides.
- `mvn spotless:check`: pass. `ChatbotSchemaIT` + `ChatbotRatingSchemaIT`: 8/8.
- Frontend: `format:check`, `lint` (1 existing warning in `tickets/TicketListFilters.tsx`), `typecheck`, `build`
  pass; `test` 1889/1890 — `ReviewQueuePage.test.tsx` "AC-002.2 / AC-002.5 … (BUG-100)" failed under the full run
  and passes alone (11/11); chatbot tests 96/96. `generate:api` committed with the contract.

### Commit
- default@807452ff

### Deviations
- The test changelog `search-only.xml` gains the 043 include so `ChatbotSchemaIT` checks the new constraint.

### Blockers
none

### Next up
Task 14 `classify-question`.
