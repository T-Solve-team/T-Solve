---
module: chatbot
feature: cited-documents
task: 9
verdict: pass
attempt: 1
date: 2026-11-09
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: chatbot — Task 9: Unavailable cited documents (KAN-180, STORY-006)

## Summary
I reviewed the untracked Task 9 files on `feat/KAN-180-chatbot-citations` against `f629dfd`. The change adds
`UnavailableDocumentsController` (`GET /api/v1/chatbot/unavailable-documents`) and the chatbot
`InvalidChatbotRequestException` (400 MSG06, no field error). It also adds two records under `chatbot/source/dto/`:
`UnavailableDocumentsRequest` parses and dedupes the ids, and `UnavailableDocumentsResponse` keeps only the ids the
port returned, in request order. `NoWikiSources` (from Task 7) gets its AC-006.7 test. I checked AC-006.5 to AC-006.7
against the spec, the contract and edge case B11. Each criterion has a test named with its id, and all the tests
pass.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-006.5: the controller passes the deduped ids to `WikiSources.unavailable(user, ids)`. It answers `{wikiDocumentIds}` with only the given ids the port returned, in first-occurrence order. An extra id from the port is dropped (test: `EXTRA_ID`). AC-006.6: the request is refused with 400 MSG06 and no `fieldErrors` when the ids are missing, empty, more than 50 (counted before dedupe), blank or not a canonical UUID (`UnavailableDocumentsRequest.java:12-23`, the same canonical check as search's `TicketSearchQuery`). A request with no session gets 401 MSG07 through `SecurityConfig`. On a refused request the port is never called. AC-006.7: `NoWikiSources.nearest` returns no passage, and `unavailable` returns every given id. |
| Completeness | pass | The test names asked for by the task are present: `listsWhatThePortReturns_AC_006_5` (for every `Role`), `rejectsMissingTooManyOrBadIds_AC_006_6` (9 refused inputs plus the 1 and 50 limits accepted), `answers401WithoutSession_AC_006_6` and `failsClosed_AC_006_7`. B11 (repeated ids, more than 50 ids) is covered. |
| Correctness | pass | 73 focused tests pass. Only the files the dispatcher allowed are added: the controller, the exception and two dto records. The controller has `@PreAuthorize("isAuthenticated()")`, `@Tag("chatbot")` and the operationId `listUnavailableCitedDocuments`, and it holds no repository and no transaction. Nothing is logged. There is no mock data. |
| Integration | pass | The response shape `{ "wikiDocumentIds": uuid[] }` matches `UnavailableDocuments` in `frontend/openapi/tsolve-api.yaml`. The query style (`wikiDocumentIds=<uuid>&wikiDocumentIds=<uuid>`) matches the contract. The contract has no `maxItems: 50` yet; per the design, Task 10 fixes that drift. `InvalidChatbotRequestException` extends `MessageCodeException`, so the global handler renders it, with no module `@ExceptionHandler`. |

## Traceability
```
  ✓ AC-006.5  backend/src/test/java/vn/tsolve/chatbot/source/controller/UnavailableDocumentsControllerTest.java
  ✓ AC-006.6  backend/src/test/java/vn/tsolve/chatbot/source/controller/UnavailableDocumentsControllerTest.java
  ✓ AC-006.7  backend/src/test/java/vn/tsolve/chatbot/source/service/impl/NoWikiSourcesTest.java
trace chatbot (STORY-006): 7/7 acceptance criteria have a test
```

## Issues
None blocking.

Non-blocking notes:
1. `UnavailableDocumentsController.java:37-39`, `UnavailableDocumentsResponse.java:8-10`: the controller parses the
   request and intersects the result through dto static factories, not through a service. AGENTS.md §3 says a
   controller holds no business logic. This follows the existing search pattern (`TicketSearchQuery.parse` in the
   controller), and the design draws the call as controller → `WikiSources.unavailable`. I accept it as it is.
2. `UnavailableDocumentsController.java:36`: because the parameter is `@RequestParam List<String>`, Spring also
   splits a single comma-separated value (`?wikiDocumentIds=a,b`) into two ids. The 50-id limit and the UUID checks
   still run after the split, so this is lenient but safe.
3. `UnavailableDocumentsController.java:34`: `@ApiResponse 403 MSG08` on a GET. A GET has no CSRF check. The design
   keeps the 403 responses in the contract, so this is consistent.

## Assumptions made
- "More than 50 ids" (AC-006.6) counts the ids as sent, before dedupe: 51 copies of one id give 400. This matches the
  contract's array `maxItems` (Task 10) and is tested on purpose. The other reading, counting distinct ids only, would
  accept that request. The user may want to confirm which reading is meant.
- An uppercase UUID is accepted and deduped with its lowercase form. The answer uses the lowercase canonical form.

## Test Report
See `task-9-cited-documents-test-report.md`. `UnavailableDocumentsControllerTest` passed 17 and `NoWikiSourcesTest`
passed 1, with 0 failures. I did not run `mvn spotless:check` or the full `mvn verify` here (BUG-130). They were
reported from a separate clone.
