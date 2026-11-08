---
module: chatbot
feature: cited-documents
task: 9
verdict: pass
attempt: 1
date: 2026-11-08
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: chatbot — Task 9: Unavailable cited documents (KAN-180, STORY-006, AC-006.5 – AC-006.7)

## Summary
Commit `a19abbb2` adds `GET /api/v1/chatbot/unavailable-documents` (`listUnavailableCitedDocuments`, `isAuthenticated()`).
- `UnavailableDocumentsRequest.of` parses the raw strings into an ordered set of canonical UUIDs. It throws `InvalidChatbotRequestException` (400 MSG06, no field) when the list is missing or empty, an id is bad or in short form, or there are more than 50 distinct ids.
- The controller passes the set to `WikiSources.unavailable`.
- `UnavailableDocumentsResponse.of` keeps only the ids that were asked about, in the order asked.

The new `NoWikiSourcesTest` pins the fail-closed stand-in, whose code predates this range. The full `mvn verify` passes (953 unit tests, 297 ITs, Spotless). Each AC in scope is implemented and covered by a test that names it. I found no CRITICAL or security issues. One integration gap between the 50-id cap and the web app is left for Task 10 and the spec owner.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-006.5 – AC-006.7 checked against the code (Stage 1 table below) |
| Completeness | pass | No MISSING or EXTRA behaviour. The diff stays within Task 9's files, plus one module exception and two DTOs that the controller needs. AC-006.1 – .4 were Task 8 |
| Correctness | pass | `mvn -B -Dapi.version=1.44 verify` → `BUILD SUCCESS`. A probe of edge cases (uppercase, spaces, braces, null element, too long) behaves correctly |
| Integration | pass (with warning) | Path, operationId, tag, the repeated-param array form and the `UnavailableDocuments` shape match `frontend/openapi/tsolve-api.yaml:1052-1080,2455-2463`. The generated client repeats `wikiDocumentIds=` per id, which Spring binds. Warning 1 below: the contract and the web app do not know the 50-id cap |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-006.5: signed-in user gets `UnavailableDocuments` with exactly the given ids that `WikiSources.unavailable` returns, 1 to 50 ids | PASS | `UnavailableDocumentsController.java:29-44`; `UnavailableDocumentsResponse.java:9-12` filters to the ids asked (an extra id from the port is dropped) and keeps the order asked. Test: `UnavailableDocumentsControllerTest.java:59` `listsWhatThePortReturns_AC_006_5`. The port returns `{C, A, NOT_GIVEN}` for `C, A, B, A`, and the response is exactly `[C, A]`. The test also checks that the port receives `{C, A, B}` (deduplicated, ordered) and the actual principal |
| 2 | AC-006.6: missing, more than 50, or non-UUID → 400 MSG06 with no field error; no session → 401 MSG07 | PASS | `UnavailableDocumentsRequest.java:14-39`; `InvalidChatbotRequestException.java:8-11` (no field, so `GlobalExceptionHandler.handleMessageCode` writes no `fieldErrors`). Tests: `:91` `rejectsMissingTooManyOrBadIds_AC_006_6` covers 6 cases (no param, `""`, 51 distinct, `not-a-uuid`, `1-1-1-1-1`, a trailing `""`). It asserts `code` MSG06, that `fieldErrors` does not exist, and that the port is not called. `:78` `acceptsFiftyIdsAfterDuplicates_AC_006_6` checks the upper bound. `:100` `answers401WithoutSession_AC_006_6` checks MSG07 and that the port is not called |
| 3 | AC-006.7: `NoWikiSources` returns no passage and lists every given id as unavailable | PASS | `NoWikiSources.java:13-21`. Test: `NoWikiSourcesTest.java:15` `failsClosed_AC_006_7` |

### Stage 2 — Checklist (backend/AGENTS.md)
- Layers: the controller calls one port method and has no repository, transaction or try/catch. The validation sits in a DTO factory that throws a module exception extending `MessageCodeException`. This follows existing precedent (`identity/dto/UserListQuery`, `analytics/exception/InvalidAuditFilterException`).
- `@PreAuthorize("isAuthenticated()")` follows the spec: any signed-in user (Requirement 1), the same as `askChatbot`. `@Tag`, `@Operation(operationId)` and `@ApiResponse` 400/401/403 are present.
- No Lombok, field injection, wildcard imports or logging of ids. Spotless passes.
- Security (the module controls who can see documents): no Workspace or user id is taken from the request. Unknown, deleted and inaccessible documents all show as "unavailable", so the endpoint cannot be used to discover which documents exist. Input is bounded: at most 50 distinct ids, and Tomcat caps the request-line length. The fail-closed stand-in is the only `WikiSources` bean.

## Traceability
```
  ✓ AC-006.1  backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/ChatbotAnswerServiceImplTest.java, backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/CitationsTest.java, backend/src/test/java/vn/tsolve/chatbot/answer/controller/ChatbotAnswerControllerTest.java
  ✓ AC-006.2  backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/ChatbotAnswerServiceImplTest.java, backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/CitationsTest.java
  ✓ AC-006.3  backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/CitationsTest.java
  ✓ AC-006.4  backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/CitationsTest.java
  ✓ AC-006.5  backend/src/test/java/vn/tsolve/chatbot/source/controller/UnavailableDocumentsControllerTest.java
  ✓ AC-006.6  backend/src/test/java/vn/tsolve/chatbot/source/controller/UnavailableDocumentsControllerTest.java
  ✓ AC-006.7  backend/src/test/java/vn/tsolve/chatbot/source/service/impl/NoWikiSourcesTest.java
trace chatbot (STORY-006): 7/7 acceptance criteria have a test
```

## Issues

CRITICAL (blocks pass): none.

WARNING (does not block; left for Task 10 and the spec owner):
1. **The 50-id cap is known only to the backend.**
   - `frontend/openapi/tsolve-api.yaml:1062-1067` declares `minItems: 1` but no `maxItems`.
   - `frontend/src/features/chatbot/components/ConversationThread.tsx:60-65` sends every distinct Wiki id cited in the whole conversation, with no cap.
   - A conversation whose answers cite more than 50 distinct Wiki documents gets 400 from `UnavailableDocumentsRequest.java:22`. AC-002.10 then shows every Wiki citation as a link, including the ones the user can no longer view, so MSG110 silently stops working.
   - Today this cannot happen: `NoWikiSources` returns no passages, so there are no Wiki citations. It becomes reachable once wiki KAN-169 lands.

   Suggested fix: Task 10 adds `maxItems: 50` to the `wikiDocumentIds` parameter in the yaml and regenerates. Whether the web app should cap, split or otherwise handle more than 50 ids is a spec decision (AC-002.9 says nothing about it), recorded under Assumptions.

Informational:
2. `UnavailableDocumentsController.java:40`: `required = false` with `List<String>` means springdoc's `/v3/api-docs` describes the parameter as an optional string array without `format: uuid`. The yaml describes it as required, `uuid`, `minItems: 1`. This is deliberate (the comment at `:39`: MSG06 with no type-mismatch field error), and the yaml is the source of truth that the frontend is generated from. When Task 10 compares the two, it should keep the yaml and not "align" it to springdoc. Acceptable to leave for Task 10.
3. `UnavailableDocumentsController.java:40`: one comma-joined value (`?wikiDocumentIds=a,b`) is split by Spring's String → List conversion and accepted as two ids. This is harmless: the same validation applies, and the generated client sends repeated params. No fix needed.

## Assumptions made
1. **"More than 50 ids" counts distinct ids.** The spec (AC-006.6) does not say whether duplicates count. I followed design edge case B11 ("set semantics"), so 50 distinct ids plus a repeat is accepted (`acceptsFiftyIdsAfterDuplicates_AC_006_6`). **Open judgment call:** is that the intended reading?
2. **More than 50 cited Wiki documents in one conversation (Warning 1).** I judged this non-blocking because it cannot happen until wiki implements the port, and Task 10 owns the contract. **Open judgment call for the user / spec owner:** should AC-002.9 cap or split the ids (for example, the 50 most recent), or should the backend limit be raised?
3. **Response order.** The spec does not fix the order of `wikiDocumentIds` in the response. The implementation uses the order asked. I accept this as an unspecified detail.
4. **Iteration.** I recorded `iteration: 2`, following the plan path (`04-plans/chatbot/iteration-2/tasks.md`), the dispatcher and the Task 8 review.
5. **Coverage.** The backend has no coverage tool and the spec sets no threshold. I report this rather than set a target.

## Test Report
See `task-9-cited-documents-test-report.md`. In brief, from `backend/` in Git Bash:
`export JAVA_HOME="/c/Program Files/JetBrains/IntelliJ IDEA 2025.1.1.1/jbr"; export PATH="$JAVA_HOME/bin:$PATH"; mvn -B -Dapi.version=1.44 verify`
- `[INFO] Tests run: 953, Failures: 0, Errors: 0, Skipped: 0` (Surefire)
- `[INFO] Tests run: 297, Failures: 0, Errors: 0, Skipped: 0` (Failsafe)
- `spotless:2.43.0:check` → `[INFO] BUILD SUCCESS`, `Total time: 08:38 min`, exit 0
- `UnavailableDocumentsControllerTest` 9/9 and `NoWikiSourcesTest` 1/1 passed. `ReviewQueueEventsIT` (BUG-186) passed 18/18 on the first run, so no rerun was needed
- Coverage: not measured (no plugin)
