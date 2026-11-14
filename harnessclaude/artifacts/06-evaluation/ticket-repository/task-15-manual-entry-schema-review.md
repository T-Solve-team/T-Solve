---
module: ticket-repository
feature: manual-entry-schema
task: 15
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: ticket-repository — Task 15: Schema and upload limits for manual entry

## Summary
Changeset `031-ticket-manual-entry.sql` adds `evidence.storage_key VARCHAR(200)` and
`import_preview.evidence JSONB NOT NULL DEFAULT '[]'`, with rollbacks, and is included in the master changelog.
The entity changes are:
- `TicketEvidence` maps `storage_key` and the NOT NULL `file_type` column and gets a factory `of(…)` that assigns a
  UUID.
- `ImportPreview` gets `evidence` (JSON), `manualDraft(…)` (sets `draft_saved_at` at creation, so cleanup keeps it)
  and `saveManualDraftAgain`.
- `ImportPreviewRow` gets `manualEntry` and `replaceValues`.
- `Ticket.enteredManually` (MANUAL, PUBLISHED, writer cut to 100, `writer_id` now insertable).

Multipart limits are 11 MB per file and 56 MB per request. In the Caddyfile, `handle /api/v1/imports/manual*`
allows 57 MB and sits before `handle /api/*` (12 MB). The whole migration chain ran in every IT (555 pass).
Verdict: pass.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | The schema matches the design's data model. No new table, so the tenant-table guard is unchanged. `writer_id` is a FK to `app_user`, and the IT inserts the PM user and checks `writer_id` = caller id |
| Completeness | pass | Every file of the task is changed. `ManualEntryTicketTest` covers the factory (AC-006.1 writer and writerId, writer cut to 100), Evidence fields (AC-006.2), the Manual Draft (AC-007.1) and the Evidence list replacement (AC-007.5) |
| Correctness | pass | `ManualEntryTicketTest` 5/5. Every IT runs the full changelog on PostgreSQL. `ReviewStorageIT` is updated so its "main before Review" changelog leaves out 031, and it passes |
| Integration | pass | `ImportPreview.projectId` is now updatable (a Draft saved again takes the form's Project). No other code changes it. The global multipart raise to 56 MB also applies to the CSV upload, which keeps its own 10 MB check (`CsvImportServiceImpl.java:80`) and stays at 12 MB at the proxy |

## Traceability
STORY-006 12/12 and STORY-007 9/9 have a test (`sdd.ts trace`). This task's criteria (AC-006.1 factory, AC-006.2,
AC-007.1, AC-007.5) are in `ManualEntryTicketTest`.

## Issues
No blocking issue.

1. **[LOW] Edge 29 has no test.** `infra/caddy/Caddyfile:70-79`. Edge 29 (a 30 MB manual entry through the staging
   proxy) is assigned to Task 15. `infra/tests/deploy-docker-config.test.sh` does not check body limits, so the task's
   "update infra/tests if it checks the body limit" did not apply, and nothing proves the routing. From reading the
   file: Caddy sorts `handle` blocks with a single path matcher longest path first, so `/api/v1/imports/manual*`
   wins over `/api/*`. That is correct. Suggested fix: add a `caddy adapt` check to `infra/tests` (both manual paths
   at 57 MB, `/api/v1/imports/jira` still at 12 MB).
2. **[LOW] The Caddy matcher is wider than designed.** `infra/caddy/Caddyfile:70`. `/api/v1/imports/manual*` also
   matches paths such as `/api/v1/imports/manualX`. The design asked for exactly `/api/v1/imports/manual` and
   `/api/v1/imports/manual/*`. No such endpoint exists today, so nothing is exposed. Suggested fix:
   `@manual path /api/v1/imports/manual /api/v1/imports/manual/*`.
3. **[LOW] A comment says the opposite of the code.** `TicketEvidence.java:38`. The comment says the id is given so
   "a Draft's Evidence keeps its id when it becomes a Ticket's". But `of(…)` always assigns a new random UUID, and
   `ManualEntryServiceImpl.insert` does not pass the Draft id. Suggested fix: correct the comment, or pass the
   `DraftEvidence.id` through.
4. **[LOW] The writer cut can break a character.** `Ticket.java:149`. `substring(0, 100)` counts UTF-16 units, so a
   full name with an emoji or surrogate pair at position 100 is cut in half. The column (`writer VARCHAR(100)`)
   counts characters. Suggested fix: cut by code points (`offsetByCodePoints`).

## Assumptions made
- With no Caddy binary available, the reviewer accepts that Caddy's directive sort works as documented (longest
  single-path matcher first), without running it.

## Test Report
`task-15-manual-entry-schema-test-report.md`, which refers to the shared run in
`task-14-manual-entry-contract-test-report.md`.
