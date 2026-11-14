---
module: ticket-repository
feature: manual-entry-save
task: 16
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: ticket-repository — Task 16: Save a manual entry (without Evidence)

## Summary
`POST /api/v1/imports/manual` (`ManualEntryController.java:41-53`) has class-level `@PreAuthorize` PM. It reads
`entry` as text and parses it with the application `ObjectMapper`; unreadable JSON or `null` gives 400 MSG06.

`ManualEntryServiceImpl.save` (`:125-168`) follows the design order:
1. Validator: MSG01, then MSG02 in code points, then MSG39 against Vietnam "today" from the `Clock`.
2. `managesActive`, else 403.
3. The Draft lookup and the Evidence check.
4. The MSG31 pre-check.
5. Files stored outside the transaction.
6. One `TransactionTemplate` with: the Completed batch first, the Ticket (`enteredManually`, writer = full name,
   writerId = id), Evidence, the approve decision, the Draft deleted, `TicketsPublishedEvent`, and audit IMPORT.

A `DataIntegrityViolationException` naming `uq_ticket_ticket_id` gives MSG31 409 with no batch. Any other failure
deletes the stored files and records a Failed MANUAL batch with only the exception class (no ticket content), in its
own `REQUIRES_NEW` transaction, then answers MSG33 500. `recordJiraFailed` is renamed to
`recordFailedWithoutPreview(…, source, …)`, and the Jira pull passes `JIRA_IMPORT`. The ITs prove the transaction,
the after-commit event and the unique-index race on real PostgreSQL. Verdict: pass.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-006.1: trimmed values, source MANUAL, PUBLISHED, the optional resolved date, writer and writerId, approve decision, one Completed batch with 1 published, 201 + MSG32 `{created:1, skipped:0}`. AC-006.3/4/5 are in `ManualEntryValidator`. AC-006.8: pre-check plus the constraint catch. AC-006.9: role and `managesActive`. AC-006.10: event inside the transaction for AFTER_COMMIT listeners. AC-006.11: rollback, Failed batch, MSG33 |
| Completeness | pass | Each criterion of the task has a test that names it: the validator (22), service (11), controller (9) and IT tests. Edge 20 (trim, 50 emoji counted as 50) and edge 21 (today at 00:30 Vietnam) are tested in `ManualEntryValidatorTest`. For edge 22 the backend side is tested (`aMissingFieldWinsOverATooLongOneAndABadDate_AC_006_3`) |
| Correctness | pass | Every test passes in the full `mvn clean verify`. `aTicketIdSavedByAnotherRequestWhileThisOneRunsIsMsg31_AC_006_8` holds an uncommitted insert on a second connection, waits until the save blocks in `pg_locks`, then commits. It asserts 409 MSG31, no batch, no decision, no event and no stored object, so it is a real race test |
| Integration | pass | `ImportBatchRecorder` rename: the Jira pull and its test are updated (`JiraImportServiceImplTest`). Audit, `TicketsPublishedEvent` and `ProjectAccess` are used as the other import flows use them |

## Traceability
`sdd.ts trace ticket-repository STORY-006`: 12/12 have a test. For this task: AC-006.1 (service, IT, controller,
validator), AC-006.3, AC-006.4, AC-006.5 (validator), AC-006.8 (service, controller, IT), AC-006.9 (service,
controller), AC-006.10 (service, IT), AC-006.11 (service, IT).

## Issues
No blocking issue.

1. **[LOW] MSG01 names the JSON keys, not the field labels.** `ManualEntryRejectedException.java:16-19`.
   `field_name` holds keys such as `"ticketId, title"`, so a client that shows the top-level message shows the JSON
   names. The web app is not affected for single fields: it checks first and puts the field error under the first
   field. Suggested fix: send labels in `field_name`, or one field error per missing field, as
   `withValidationErrors` does.
2. **[LOW] The design and code assume unreadable `entry` JSON is MSG06**, a code the spec's rejection list for manual
   entry does not include. The spec lists only MSG01/02/34/39/72 as 400s; MSG06 is the project's generic "request
   not readable" code. Not a defect; recorded as an assumption.

## Assumptions made
- Unreadable `entry` JSON giving 400 MSG06 is accepted: the spec is silent on malformed requests, and MSG06 is the
  project-wide code for them.
- "MSG02 naming the field and its limit" (AC-006.4) is read as the first field over its limit, as CSV import does.
  Every too-long field at once is not required. For the Solution total, `field` is null, so the web app shows a
  toast, not an inline error.

## Test Report
`task-16-manual-entry-save-test-report.md`, which refers to the shared run in
`task-14-manual-entry-contract-test-report.md`.
