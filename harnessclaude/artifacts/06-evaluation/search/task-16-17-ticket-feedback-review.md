---
module: search
feature: ticket-feedback-api, give-feedback-connect
task: 16, 17
verdict: pass
attempt: 1
date: 2026-11-06
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: search — Tasks 16 and 17: Feedback API and Give Feedback connection (KAN-148, STORY-008, STORY-003, AC-006.10)

## Summary
I reviewed the uncommitted new files against `72699fd` on `feat/KAN-148-search-feedback`. Task 16 adds seven main
classes under `backend/src/main/java/vn/tsolve/search/feedback/` and three test classes. Task 17 changes no frontend
file. I checked that claim: the backend matches the existing `giveFeedback` operation and `TicketFeedbackRequest`
schema in `frontend/openapi/tsolve-api.yaml`, the generated client, and `frontend/src/features/feedback/`. Every
STORY-008 criterion is met and has a test that names it. The controller and service tests cover AC-008.3 to
AC-008.5 and AC-006.10. The IT covers the upsert and the concurrency of AC-008.2. The main code adds no mock data.
There are no CRITICAL findings. Verdict: PASS. The findings below are Low or Informational.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `TicketFeedbackController.java:33-46`: `PUT /api/v1/tickets/{ticketId}/feedback` with `@Tag("feedback")`, `operationId = "giveFeedback"`, `@PreAuthorize` for TICKET_RESOLVER and PROJECT_MANAGER, and a 204 with no body. `ticketId` is a `UUID` path variable, so a non-UUID gives 400 MSG06. `TicketFeedbackRequest.java:7-8`: `@NotNull Boolean isHelpful` (MSG01) and `@Size(max = 500) String comment` (MSG02), mapped by `GlobalExceptionHandler`. `TicketFeedbackServiceImpl.java:31-37`: an empty scope, a missing ticket, a status other than `PUBLISHED`, a Department that is not `ACTIVE`, and a Department outside `scope.departmentIds()` each throw `NotFoundException("MSG04")` before any write. `:38-40`: the comment is trimmed, a blank comment becomes null, the rating is `HELPFUL` or `NOT_HELPFUL`, and the channel is `WEB`. `TicketFeedbackRepository.java:12-27`: one native `INSERT … ON CONFLICT (ticket_id, user_id) DO UPDATE SET rating, comment, channel, updated_at = now()` with named bound parameters, so there is no string concatenation. |
| Completeness | pass | AC-008.1 to AC-008.5 and AC-006.10 are covered. The UI side of AC-003.8 and AC-003.9 is already met by `GiveFeedbackModal.tsx:80-85`: `applyApiErrors` puts field errors on their fields, other errors call `showMessage(error.code)`, and 401 is left to the global handler. |
| Correctness | pass | The controller reports 21 unit tests passing (`TicketFeedbackControllerTest`, `TicketFeedbackServiceImplTest`). The dispatcher runs `mvn verify` with `TicketFeedbackRepositoryIT` separately. I did not run mvn or Docker (Assumption 1). |
| Integration | pass | Contract (spec Interface Contracts and yaml `:1207-1235`, `:1938-1947`): path, PUT, tag `feedback`, operationId `giveFeedback`, 204, 400/401/403/404, `isHelpful` boolean required, and `comment` string or null with maxLength 500. All of them match. The generated `feedback.ts:25,57` calls `PUT /api/v1/tickets/${ticketId}/feedback`. The backend field errors are `{field: "isHelpful"|"comment", code}`, which is the shape `applyApiErrors` reads. `user.id()` is written as `user_id`, which has an FK to `app_user(id)`, as the spec says. |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-008.1 store HELPFUL/NOT_HELPFUL, trimmed comment, WEB, 204 | PASS | `TicketFeedbackServiceImplTest.storesRatingTrimmedCommentAndWebChannel_AC_008_1` covers 6 cases: trim, null, empty, white space and 500 characters. `TicketFeedbackControllerTest.returnsNoBody_AC_008_1` checks the 204 and the empty body. |
| 2 | AC-008.2 replace rating, comment, channel and `updated_at`; one row under concurrency | PASS | `TicketFeedbackRepositoryIT.replacesFeedbackOfSameUser_AC_008_2` checks that the same row id is kept, that `created_at` is kept, that `updated_at` moves forward, that channel `ISSUE_PANEL` becomes `WEB`, and that another user gets a second row. `keepsOneRowForConcurrentPuts_AC_008_2` checks that two latched threads leave one row. |
| 3 | AC-008.3 404 MSG04, nothing stored | PASS | `answers404ForTicketOutsideScope_AC_008_3` covers 7 scenarios: empty scope, missing ticket, unpublished, deleted Department, outside scope, managed-only scope and writer-only scope. Each one checks `verifyNoInteractions(feedback)`. The IT checks unpublished and deleted Department against a real database, with a count of zero. The controller test checks the mapping to MSG04. |
| 4 | AC-008.4 MSG01 / MSG02 / MSG06, nothing stored | PASS | `rejectsMissingIsHelpful_AC_008_4` covers `{}` and `null`, `rejectsLongComment_AC_008_4` covers 501 characters, and `rejectsNonUuidTicketId_AC_008_4` covers a non-UUID id. Each test asserts the field and code and checks `verifyNoInteractions`. |
| 5 | AC-008.5 401 MSG07 / 403 MSG08 | PASS | `answers401And403_AC_008_5` covers an anonymous caller and a user without either role, and checks `verifyNoInteractions`. |
| 6 | AC-006.10 no Workspace input | PASS | The request has no Workspace field. `ignoresWorkspaceParameter_AC_006_10` sends `workspaceId` as a query parameter and as a header, gets 204, and checks that the service gets the unchanged body and the principal's id. |

### Stage 2 — Quality

CRITICAL: none. HIGH: none.

Low:
1. [`TicketFeedbackServiceImpl.java:38-39`] `String.trim()` strips only characters at or below U+0020. The web app
   trims with JS `trim()`, which also strips Unicode spaces. Some comments are therefore stored with leading or
   trailing Unicode spaces, for example an em space (U+2003) or an ideographic space (U+3000). `isBlank()` still
   turns a comment that holds only those characters into null. An NBSP-only comment is stored as-is, because
   neither `trim()` nor `isBlank()` treats U+00A0 as white space. The web app always sends a comment that is
   already trimmed, so this affects only other clients.
   Fix, optional: use `strip()`, and also handle U+00A0 if that matters.

Informational (does not block):
2. [`TicketFeedbackServiceImpl.java:33-40`] The searchable check and the upsert do not lock the ticket. A ticket
   that is unpublished between the check and the write can still get one Feedback. The window is small, and the
   spec asks for no lock.
3. [`TicketFeedbackRepository.java:12-21`] `backend/AGENTS.md` §3 limits native SQL to pgvector and full-text.
   The design chose this native upsert on purpose ("Feedback (STORY-008)"), and it is the simplest way to make
   AC-008.2 atomic. The embedding upsert uses the same pattern.
4. [`TicketFeedback.java:28,34`] `rating` and `channel` are `String`, not enums with `@Enumerated(EnumType.STRING)`
   (AGENTS §3). The entity is read only in tests, and the DB CHECKs guard the values. This is acceptable.
5. [`FeedbackTicketRepository.java:9`] `findByPublicId` loads the whole `SearchTicket` subselect, including the
   Solution text, only to check 3 columns. This is one row per request, so the cost is negligible.
6. [`TicketFeedbackController.java:35`] `@ResponseStatus(NO_CONTENT)` repeats `ResponseEntity.noContent()`. Keep
   it, because springdoc needs it to document 204 instead of 200.
7. A comment that holds `\u0000` would fail in PostgreSQL and give a 500 MSG06. The handler logs only the
   exception class at ERROR, so no comment text reaches the log. The feedback code itself does not log.

Security: the endpoint needs authentication and one of two roles (`@PreAuthorize`). The scope is fail-closed
(`NoVisibleScope` gives 404). The SQL uses bound parameters, and no comment text is logged. The test classes use
Mockito and IT fixture rows only. No mock data is added to main code or to the frontend.

## Assumptions made
1. **Tests not run by the reviewer (open judgment call).** The dispatcher told me not to run Docker or mvn. The
   evidence for Correctness is the controller's run (21 unit tests) and its separate `mvn verify` with
   `TicketFeedbackRepositoryIT`. This pass depends on that IT, and on Spotless, passing.
2. **Task 17 accepted without the live check (open judgment call).** The plan asks for `npm run api:pull` against
   a running backend, and for a mount check with `VITE_API_MOCKING=false` (404 under the stand-in scope, and 400
   field errors). Neither was done. I accept Task 17 on a static match of the controller, the DTO, the yaml, the
   generated client and the popup. The frontend `verify` was not re-run, because no frontend file changed.
3. **Raw comment length.** `@Size(max = 500)` checks the comment before it is trimmed, in UTF-16 units. AC-008.4
   says "`comment` is longer than 500 characters", which I read as the value that was sent. UTF-16 units are never
   fewer than `char_length`, so the DB CHECK cannot fail. A comment of 500 characters plus padding gets MSG02.
4. **Scope is Departments only.** AC-008.3 lists "not one of the user's Departments". So a ticket that is visible
   only through a managed Project or the writer gets 404. That is what the code and the tests do.
5. **Branch.** The plan names separate branches, `feat/KAN-148-search-feedback-api` and `-fe`. The work is on
   `feat/KAN-148-search-feedback`. Task 17 changed no frontend file, so one branch keeps one Jira key. The
   dispatcher may rename it.

## Traceability
```
  ✓ AC-008.1  …/feedback/service/impl/TicketFeedbackServiceImplTest.java, …/feedback/controller/TicketFeedbackControllerTest.java
  ✓ AC-008.2  …/feedback/repository/TicketFeedbackRepositoryIT.java
  ✓ AC-008.3  …/feedback/service/impl/TicketFeedbackServiceImplTest.java, …/feedback/controller/TicketFeedbackControllerTest.java, …/feedback/repository/TicketFeedbackRepositoryIT.java
  ✓ AC-008.4  …/feedback/controller/TicketFeedbackControllerTest.java
  ✓ AC-008.5  …/feedback/controller/TicketFeedbackControllerTest.java
trace search (STORY-008): 5/5 acceptance criteria have a test
  ✓ AC-003.8  frontend/src/features/feedback/components/GiveFeedbackModal.test.tsx
  ✓ AC-003.9  frontend/src/features/feedback/components/GiveFeedbackModal.test.tsx
```
I read every test. Each one asserts the behaviour named in its criterion, and none only mentions the id.

## Issues
None blocking. Low item 1 and Informational items 2-7 are under Stage 2.

## Test Report
- `TicketFeedbackControllerTest` and `TicketFeedbackServiceImplTest`: 21 pass (the controller ran them; the
  reviewer did not, see Assumption 1).
- `TicketFeedbackRepositoryIT` and `mvn spotless:check`: running separately in `mvn verify`; the reviewer has no
  result.
- Frontend: no file changed. The existing AC-003.* and AC-004.4 tests were not re-run.
