---
module: ticket-repository
feature: jira-pull
task: 4
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 4: Pull from Jira — service and endpoint

## Summary
Commit `5281b56d`. `JiraImportServiceImpl.pull` and `POST /api/v1/imports/jira` meet AC-001.1 to AC-001.10, in the
order the design asks for: dates first, then project access, then Jira; the database writes come only after the Jira
calls, in one transaction. Every rejection is decided before Jira is called. A Jira failure records a Failed batch
of source JIRA_IMPORT with 0 published and leaves the caller's existing preview alone. No blocking issues. There is
no session log for this task.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-001.4: MSG01 for a missing from/to (`JiraImportServiceImpl.java:81-86`). AC-001.5: MSG39 for a reversed range or `to` after today, Vietnam time (`:87-90`). A `from` after today is caught because it implies one of those two. AC-001.8: 403 when the Project is not managed or not Active (`:91-93`). AC-001.7: no Jira key → MSG50 "not connected" with no call (`:106-110`). AC-001.1: per-page duplicate filter with one `IN` query (`:117-118`); at most 100 kept, oldest first. AC-001.3: a 101st kept issue, or the 20-page cap with a token left, → `moreAvailable` → MSG35 with no count (`:123-131`, `ImportPreviewResponse.java:41-42`). AC-001.6: none kept → 404 MSG04 and no preview (`:96-98`). AC-001.7: `JiraUnavailableException` → `recordJiraFailed` (REQUIRES_NEW, 0 published) and 502 (`:132-136`, `ImportBatchRecorder.java:41-44`). AC-001.10: `deleteByOwner` (non-Draft only) then insert, in one transaction (`:139-148`). AC-001.2: row snapshot (`:144-176`). AC-001.9: class-level `@PreAuthorize(hasRole('PROJECT_MANAGER'))` (`JiraImportController.java:22`). |
| Completeness | pass | All ten of the task's criteria are implemented and tested. |
| Correctness | pass | `JiraImportServiceImplTest` 13/13, `JiraImportControllerTest` 3/3, CSV `ImportControllerTest` 13/13 after the shared `ImportPreviewResponse.of` change. |
| Integration | pass (note) | The response is the shared `ImportPreviewResponse` with `source`, dates, `draftSavedAt` and `needsSolutionCount`. The contract also requires `importType`, which the backend does not send (cross-module; Task 1 review Issue 1). |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-001` → `12/12 acceptance criteria have a test`.
- ✓ AC-001.1, AC-001.2, AC-001.3, AC-001.4, AC-001.5, AC-001.6, AC-001.7, AC-001.8, AC-001.10 `JiraImportServiceImplTest`
- ✓ AC-001.1, AC-001.7, AC-001.9 `JiraImportControllerTest`
(AC-001.11 and AC-001.12 belong to Task 11.)

## Issues
No blocking issues.

Informational:
1. NFR 1 (100 issues with comments within 60 s): `JiraImportServiceImpl.java:114-129`. Each call has a 10 s connect
   and 30 s read timeout, plus up to 2 retries of ≤ 5 s, across up to 20 pages and their comment pages, with no
   overall deadline. A slow Jira can hold a request thread for many minutes, and no test measures NFR 1.
   Fix (optional): an overall pull deadline that ends in MSG50, or a measured run in Task 10.
2. `JiraImportServiceImpl.java:100, :140-142`. A concurrent CSV upload or second pull by the same user can make the
   insert hit `uq_import_preview_owner_open`. The `DataIntegrityViolationException` then becomes a generic 500, not
   a coded answer. CSV upload has the same pattern already.
3. Pulled issues that sit in one of the caller's Drafts are not skipped (AC-001.1 skips only existing Ticket IDs),
   so the same key can be in a Draft and in a new preview. This is spec-conformant, and Finish's MSG38 handles the
   overlap. Noted for the user only.
4. Process: there is no `05-implementation/ticket-repository/task-4-jira-pull.md` session log, and progress.json
   shows the feature as `evaluating` without one.

Assumptions made:
- AC-001.5 "either date is after today" is met by `from > to || to > today`, which implies `from ≤ today`.
- MSG35 is suppressed when MSG30 applies (`ImportPreviewResponse.java:39-43`). A Jira preview never has Error rows,
  so this cannot hide MSG35.

## Test Report
See `task-4-jira-pull-test-report.md`. 632 unit + 48 IT, 0 failures.
