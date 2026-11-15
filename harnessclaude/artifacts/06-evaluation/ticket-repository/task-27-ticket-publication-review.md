---
module: ticket-repository
feature: ticket-publication
task: 27
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
---

# Review: ticket-repository — Task 27: ticket-publication

## Summary
Backend of STORY-009 (KAN-136), `default` ce4dad2..da60828. `POST /api/v1/reviews/unpublish` and `/republish` in
`ReviewDecisionController`, one private `changePublication` in `TicketDecisionServiceImpl`, changeset 037 widening
`ck_review_decision`, and the two new decision values. Every one of AC-009.1 to AC-009.7 is met by the code and
checked by a test that really asserts it. A refused batch changes nothing, and no ticket in a status other than
the required one gets through either endpoint. The new tests pass (unit 15/15, IT 6/6). The full run had one IT
failure, a timing wait in `ReviewQueueEventsIT`, which this change does not touch and which passed 18/18 when
re-run. No CRITICAL findings. Four informational ones, listed below.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-009.1–AC-009.7 checked one by one against the code (Stage 1 table below) |
| Completeness | pass | No MISSING criterion; no EXTRA behaviour. The audit action of re-publish is `PUBLISH` as AC-009.2 says |
| Correctness | pass | `TicketPublicationTest` 15/15, `TicketPublicationIT` 6/6, unit 2500/2500, Spotless pass. The one IT failure is unrelated and passed on re-run (test report) |
| Integration | pass | Contract of Task 26 matches: operationIds `unpublishTickets` / `republishTickets`, body `ApproveRequest` (= `ApproveTicketsRequest`), answer `DecisionResult` (= `ReviewDecisionResult`), 400/403/404/409. Every reader of `review_decision` filters by decision (see P9) |

### Stage 1 — spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-009.1 unpublish: one tx, Unpublished, one UNPUBLISH decision (caller, time, no reason), one UNPUBLISH audit per ticket, count | PASS | `TicketDecisionServiceImpl.java:133-166` (`@Transactional`, `transitions.move`, `saveAll` with `null` feedback and `now`, `audit.record(UNPUBLISH, "TICKET", id)`, `return batch.size()`). Tests: `unpublishChangesStatusStoresDecisionAndAudit_AC_009_1` (asserts every decision field, both audits, the `TicketsUnpublishedEvent`, no `ReviewFeedbackDecidedEvent`); `unpublishThenRepublishStoresDecisionsAndAudit_AC_009_1_AC_009_2` (stored rows in the real DB, `audit_log_entry` rows with the caller as actor) |
| 2 | AC-009.2 re-publish without a new review, Solution / Evidence / feedback / due date kept, one REPUBLISH decision, one PUBLISH audit | PASS | Status goes straight to `PUBLISHED`; `Ticket.moveTo` (`Ticket.java:181-187`) sets only `status` and `updatedAt`. Tests: `republishPublishesAgainAndKeepsEverythingElse_AC_009_2` (due date, rejection reason, change comment, Solution sections, decision row, `PUBLISH` audit, `TicketsPublishedEvent`); the IT compares the Solution, feedback columns, due date and Evidence rows before and after |
| 3 | AC-009.3 wrong status → 409 MSG06, no ticket changed, no decision, no event | PASS | Explicit `from` check before any write, `TicketDecisionServiceImpl.java:152-155`; it also stops re-publish of a Pending review ticket, which `TicketStatus` alone would let through (PENDING_REVIEW → PUBLISHED is allowed). Tests: two parameterised tests over every other status, each a mixed batch, asserting no `saveAll`, no audit, no event (`verifyNoInteractions(audit, events)`); IT `aSecondUnpublishIsAConflictAndChangesNothing_AC_009_3` (409 + `code` MSG06 over HTTP, Pending review in a re-publish batch refused) |
| 4 | AC-009.4 id not in the Workspace → 404 MSG04, nothing changed | PASS | `validateScope`, `TicketDecisionServiceImpl.java:189-192`. Tests: `anIdOutsideTheWorkspaceIsNotFound_AC_009_4`, IT `aTicketOfAnotherWorkspaceIsNotFound_AC_009_4` |
| 5 | AC-009.5 Project not managed (D37: Active only) → 403 MSG08 | PASS | `validateScope` line 193-195; managed Projects come from `ProjectMembershipReviewAccess` → `ProjectMembershipRepository.activeProjects` (`p.status = 'ACTIVE'`). Tests: unit `aTicketOfAProjectTheCallerDoesNotManageIsForbidden_AC_009_5`; IT `anotherProjectAndARemovedProjectAreForbidden_AC_009_5` (see Issue 1) |
| 6 | AC-009.6 no PM role → 403 | PASS | Class-level `@PreAuthorize("hasRole('PROJECT_MANAGER')")`, `ReviewDecisionController.java:29`. Test: IT `aTicketResolverIsForbidden_AC_009_6` (both endpoints, status unchanged) |
| 7 | AC-009.7 no id, > 500, duplicate → 400, nothing changed | PASS | Bean validation `@NotEmpty @Size(max = 500)` (`ReviewDecisionController.java:109`) and `validatedIds` (`TicketDecisionServiceImpl.java:168-177`) before the lock. Tests: unit (null, empty, 501, duplicate; `lockForReview` never called), IT (empty and 501 over HTTP, both endpoints) |

Error order of the contract (400, then 404, then 403, then 409) holds: validation runs before the method body,
then `validateScope` gives 404 before 403, then the status check gives 409.

### Edge cases of the design (P1–P9 belong to this task)

| # | Result |
|---|--------|
| P1 concurrency | `lockForReview` is `PESSIMISTIC_WRITE` (`TicketRepository.java:30-32`), so a second request waits and then sees Unpublished → 409. Proven only sequentially (Issue 4) |
| P2 every wrong status | Parameterised over all statuses, both endpoints |
| P3 other Project / other Workspace / Ticket Resolver | 403 MSG08 / 404 MSG04 / 403, tested |
| P4 Removed Project | 403 MSG08, tested with a non-Active status (Issue 1) |
| P5 mixed batch | Every AC-009.3 test is a mixed batch |
| P6 nothing else changes | Unit and IT, incl. Evidence rows |
| P7 search | Not in this task (existing `search` tests); `TicketsPublishedListener` is `AFTER_COMMIT`, so a rolled-back request never re-embeds |
| P8 constraint | Changeset 037 runs in every IT; only widens the check. Rollback: Issue 2 |
| P9 readers of `review_decision` | All read paths checked: `ReviewDecisionRecordRepository` history (`d.decision in (:decisions)`, and `TicketReviewReadServiceImpl.storedDecisions` maps UNPUBLISH / REPUBLISH to an empty list), `findWriterNotification` (only reached through `ReviewFeedbackDecidedEvent`, which these decisions never publish; `kind()` maps other values to empty), `TicketRepository.findJiraFeedback` / `findJiraRejection` (filter by decision), `DashboardRepository.findOutcomeCounts` (`r.decision in ('APPROVED','REJECTED','CHANGES_REQUESTED')`). None counts the new rows |

### Atomicity
The status check runs before `transitions.move`, `saveAll` and `audit.record`, so a refused batch writes nothing,
stores no decision, records no audit entry and publishes no event. `AuditServiceImpl.record` refuses to run
outside a transaction and writes through the same `JdbcTemplate` connection, so a later failure rolls everything
back; the "tickets published" listener is `AFTER_COMMIT`.

### Files outside the module's `paths`
- `backend/src/test/java/vn/tsolve/review/ReviewStorageIT.java` — see Issue 3. Justified.
- `ReviewDecisionController.java` is in `paths`. Its lines 111-117 (two records of other endpoints) were
  reformatted by Spotless only, with no change in behaviour.

## Traceability
```
  ✓ AC-009.1  backend/src/test/java/vn/tsolve/ticket/service/impl/TicketPublicationTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java
  ✓ AC-009.2  backend/src/test/java/vn/tsolve/ticket/service/impl/TicketPublicationTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java
  ✓ AC-009.3  backend/src/test/java/vn/tsolve/ticket/service/impl/TicketPublicationTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java
  ✓ AC-009.4  backend/src/test/java/vn/tsolve/ticket/service/impl/TicketPublicationTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java
  ✓ AC-009.5  backend/src/test/java/vn/tsolve/ticket/service/impl/TicketPublicationTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java
  ✓ AC-009.6  backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java
  ✓ AC-009.7  backend/src/test/java/vn/tsolve/ticket/service/impl/TicketPublicationTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java
  ✓ AC-009.8  frontend/src/features/tickets/components/TicketPublicationActions.test.tsx
  ✓ AC-009.9  frontend/src/features/tickets/components/TicketPublicationActions.test.tsx
  ✓ AC-009.10  frontend/src/features/tickets/components/TicketPublicationActions.test.tsx
trace ticket-repository (STORY-009): 10/10 acceptance criteria have a test
```

## Issues

CRITICAL (blocks pass): none.

Informational (does not block):

1. `backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java:164` — the D37 case sets
   `project.status = 'INACTIVE'`, a value `ProjectStatus` does not have (`ACTIVE`, `REMOVED`).
   Expected: the test uses the real Removed status. Actual: it passes only because the managed query keeps
   `status = 'ACTIVE'`, so it proves "not Active" but not "Removed".
   Fix: `update project set status = 'REMOVED' …`.
2. `backend/src/main/resources/db/changelog/changes/037-review-decision-publication.sql:7-8` — the rollback
   re-adds the four-value check, which fails once any `UNPUBLISH` / `REPUBLISH` row exists, because `ADD CONSTRAINT`
   checks existing rows. Expected (design P8): rollback restores the four values. Actual: that works only while no
   such row exists.
   Fix: add `--rollback DELETE FROM review_decision WHERE decision IN ('UNPUBLISH', 'REPUBLISH');` before the
   re-add, or state in the changeset that the rollback needs those rows removed first. The changeset itself is
   correct: number 037 follows 036, it is in the master changelog, and the author is the module.
3. `backend/src/test/java/vn/tsolve/review/ReviewStorageIT.java:70` is outside the module's `paths` (`module.md`
   lists only `ReviewDecisionApiIT.java` under `review` tests). The change is needed: that test rebuilds the
   changelog of `main` without the Review changesets, and 037 alters `ck_review_decision`, which Review's 008
   creates, so 037 has to leave with them.
   Fix: add the file to `paths`, or note it in the session log as a shared-file change.
4. No concurrent test for P1. The pessimistic lock makes it safe and the sequential second unpublish gives 409.
   Fix (optional): an IT with two threads on one ticket, asserting one 200, one 409 and one decision row.

## Assumptions made
- "Feedback" in AC-009.2 is read as the review feedback kept on the ticket (`rejection_reason`,
  `change_request_comment`), which the tests check. If it also means end-user Feedback on the ticket, it is still
  kept, because the code changes only `status` and `updatedAt`, but no test names it. The user may want to confirm.
- "A Ticket of the caller's Workspace" (AC-009.4): each Workspace has its own database (ADR-0002), so a random
  UUID is accepted as proof for an id of another Workspace.
- I took the `ReviewQueueEventsIT` failure as load-related flakiness, not a regression: it passed on re-run, and
  this change touches no code it uses.

## Test Report
See `task-27-ticket-publication-test-report.md`. `mvn -o verify`: Surefire Tests run: 2500, Failures: 0;
Failsafe Tests run: 771, Failures: 1 (`ReviewQueueEventsIT` …`_AC_012_2`, 5000 ms wait) → BUILD FAILURE.
Re-run `-Dit.test=ReviewQueueEventsIT`: Tests run: 18, Failures: 0 → BUILD SUCCESS. `mvn -o spotless:check`:
BUILD SUCCESS. `TicketPublicationTest` 15/15, `TicketPublicationIT` 6/6. Coverage not measured (no tool bound;
spec gives no target).
