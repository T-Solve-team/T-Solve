# Task 9: Unavailable cited documents

**Module:** chatbot
**Assignee:** bluebearccc
**Status:** evaluating
**Iteration:** 2

---

## Session 2026-11-08

### Working on: cited-documents (KAN-180, branch `feat/KAN-180-chatbot-citations`, after Task 8)
- Story STORY-006; AC-006.5 – AC-006.7 (edge case B11).
- `UnavailableDocumentsController` `GET /api/v1/chatbot/unavailable-documents` (`listUnavailableCitedDocuments`,
  `isAuthenticated()`), calling the `WikiSources.unavailable` port.
- `wikiDocumentIds` is bound as `List<String>`, not `List<UUID>`: a bad id must be 400 MSG06 with no field error
  (AC-006.6), and Spring's type mismatch would answer a field error.
- `UnavailableDocumentsRequest.of` (dto): missing or empty → MSG06; each value a canonical UUID (`UUID.fromString`
  plus a round-trip check, as search's `TicketSearchQuery`, so `1-1-1-1-1` is rejected); duplicates removed in first
  occurrence order; more than 50 distinct ids → MSG06 (B11: set semantics, so 50 ids plus a repeat is accepted).
- `UnavailableDocumentsResponse.of`: the port's result intersected with the given ids, in the order given
  (design "Decided in planning").
- `InvalidChatbotRequestException` (new, `vn.tsolve.chatbot.exception`): 400 MSG06, no field error, like search's
  `InvalidSearchQueryException`; Task 10 reuses it for `history`.
- AC-006.7 code was already in `NoWikiSources` (Task 7); this task adds `NoWikiSourcesTest`.

### Deviations and open points
- Files beyond the task list: two dto records and the exception class, so the controller keeps no parsing or
  filtering logic (backend/AGENTS.md §3).
- The contract `frontend/openapi/tsolve-api.yaml` has `minItems: 1` but no `maxItems: 50` on `wikiDocumentIds`. Not
  changed here: Task 10 compares the backend with the contract and fixes drift.

### Tests
- `UnavailableDocumentsControllerTest` (9): `listsWhatThePortReturns_AC_006_5`, `acceptsFiftyIdsAfterDuplicates_AC_006_6`,
  `rejectsMissingTooManyOrBadIds_AC_006_6` (6 cases), `answers401WithoutSession_AC_006_6`.
- `NoWikiSourcesTest`: `failsClosed_AC_006_7`.

```
trace chatbot (STORY-006): 7/7 acceptance criteria have a test
```

### Verify
- `mvn -B -Dapi.version=1.44 verify`: 953 unit tests pass; integration 296/297 — `ReviewQueueEventsIT.edge9_…_AC_012_3`
  timed out waiting 5 s for the first heartbeat (review module, no chatbot code involved). Rerun alone
  (`-Dit.test=ReviewQueueEventsIT`): 18/18, BUILD SUCCESS. Logged as BUG-186 (Open, Phạm Tùng Dương). The same IT
  passed in the two earlier full runs on this branch.

### Commits
- default@a19abbb `feat(chatbot): list the cited Wiki documents a user can no longer view (KAN-180)`
- default@b1b39c4 `docs(bug-log): log the flaky ReviewQueueEventsIT heartbeat wait (KAN-180)` (BUG-186 only;
  `lastCommit` records this HEAD)

### Next up
Review of Task 9; then Task 10 `follow-up-context` (KAN-181, its own branch).
