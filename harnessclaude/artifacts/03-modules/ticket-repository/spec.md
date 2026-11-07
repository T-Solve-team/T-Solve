---
module: ticket-repository
date: 2026-11-07
owner: BA
---

# Spec: ticket-repository

Source: SRS Report 3 v3460 — UC-20, GBR-15, GBR-23, GBR-26, GBR-33, screens 5.1 Import Tickets (Jira tab), 5.2 Import
Preview, 5.3 Import Ticket Preview, MSG01, MSG02, MSG04, MSG08, MSG32, MSG33, MSG35–MSG42, MSG50; Jira story KAN-45.
This version covers only **KAN-45 Import resolved issues from Jira (UC-20)**, including Drafts of a Jira import.
CSV import (UC-21) lives in `import-ticket-list`; manual entry (UC-22), Import History screen (UC-23), Ticket Detail
(UC-24), unpublish / re-publish (UC-25) and the Solution Form submission (UC-19) are added later through Change Mode.

This spec builds on `import-ticket-list` as changed by PR #110 (KAN-31): the Import Preview, its rows, the
Needs Solution result, Fill solution, remove-rows, Confirm and the Import Ticket Preview popup. A pulled Jira issue
is one row of such a preview.

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-11-07 | STORY-001..004 | Initial spec for KAN-45 Jira import | — |
| 2026-11-07 | STORY-001..004 | A pulled issue is a row of the Import Preview of `import-ticket-list` (D7): AC-001.1, AC-001.2, AC-002.1–AC-002.3, AC-002.7, AC-002.10, AC-003.1, AC-003.6 and the Interface Contracts reworded to its rows, Fill solution, remove-rows and Confirm; Project ids are UUIDs; a row still Needs Solution at Finish gives `MSG_NEW_4` as in CSV import | KAN-45 is stacked on PR #110, which already has these parts (user decision 2026-11-07) |
| 2026-11-07 | STORY-001 | AC-001.3: MSG35 also after the 20-page scan cap, and without a count (D8) | Jira gives no total and counting would need every page (NFR 1); user decision 2026-11-07 |

## Decisions taken while planning (2026-11-07, with the user)
| # | Question | Decision |
|---|----------|----------|
| D1 | Scope of this plan | Only UC-20 (KAN-45). UC-19, UC-22–25 come later through Change Mode. |
| D2 | How T-Solve reads Jira | The Workspace Jira service account (KAN-45 update of 01/11, DB tables doc 31/10), not a per-Project token (SRS GBR-14 is outdated). The import calls a `JiraIssueSource` interface; until `workspace` stores the service account, the implementation reads the Jira site URL, account email and API token from the environment. |
| D3 | Drafts | Included. A Draft is an Import Preview marked as a Draft; the daily cleanup skips Drafts. |
| D4 | Writer of an imported ticket | The Jira assignee's display name at pull time; empty when the issue is unassigned (no Jira ↔ T-Solve account linking yet). |
| D5 | Comments shown in the popup | Every comment of the issue at pull time, as plain text with author and date. The "mapped resolution comments field" of `project-integration` is not used yet. |
| D6 | Requirements shared with CSV import | Required Solution sections, text limits, preview storage and the "one unconfirmed preview per PM" rule are those of `import-ticket-list` (D1, D7, D8, D9 there). |
| D7 | Separate Jira preview or the CSV one | The CSV Import Preview of PR #110: one row per issue (Ticket ID = Jira key, result Needs Solution until it has a Solution), with two new row fields, the issue's link and its comments. Rows list, row detail, Fill solution, remove-rows, Confirm and the screens are reused; only the pull and Drafts are new endpoints. |
| D8 | MSG35's text in Report3 names how many more issues match, but the pull stops at 100 kept issues or 20 Jira pages, and Jira's `search/jql` gives no total | MSG35 names no count. Its Report3 text drops the count parameter (Report3 change by the team; then `report3-messages.json` and the web app's catalog follow). The pull does not count the remaining issues. |

## Requirements

### Functional
1. A Project Manager selects one of their Active Projects and a resolved date range, and pulls the resolved Jira issues of that Project's Jira project that have no Ticket yet: at most 100, oldest first (GBR-15).
2. On the Import Preview the Project Manager reads each issue (summary, description, comments, assignee, resolved date, link to Jira), writes its Solution one issue at a time or for several issues at once, and removes issues they do not want.
3. Finish (Confirm of a Jira preview) saves every remaining issue as a Published Ticket with an approve decision by the Project Manager (GBR-23), skips issues that got a Ticket in the meantime, and records exactly one Import Batch.
4. The Project Manager can save the import as a Draft, reopen it later without pulling from Jira again, and delete it (GBR-26).

### Non-Functional
1. A pull of 100 issues with their comments returns the Import Preview within 60 seconds.
2. The Jira API token is never logged, never returned by the API, and never stored in the database by this module.
3. Every endpoint is restricted to the Project Manager role and to Projects the caller manages; the backend re-checks this on every call.
4. Logs about Jira calls hold only the Project id, Jira project key, HTTP status and error type — no issue content.

## User Stories

### STORY-001: Pull resolved issues from Jira
- As a Project Manager, I want to pull the resolved Jira issues of my Project for a date range so that I can record their Solutions in T-Solve.
- Acceptance:
  - AC-001.1: WHEN a Project Manager pulls issues for an Active Project they manage with a valid date range, THE system SHALL read from Jira the issues of the Project's Jira project whose resolution date falls within the range (both dates included, Vietnam time), skip every issue whose Jira key is already a Ticket ID, keep at most 100 issues ordered by resolution date oldest first, and create an Import Preview of source Jira with one row per kept issue, numbered from 1 in that order, each with the result Needs Solution.
  - AC-001.2: WHEN the Import Preview is created, THE system SHALL store on each row a snapshot of its issue — Ticket ID = Jira key, title = summary, description as plain text, author = assignee display name, resolved date, link to the issue in Jira, and every comment as plain text with its author and date — and SHALL not read the issue from Jira again for this preview.
  - AC-001.3: WHEN more than 100 issues without a Ticket match the date range, or the pull stops after 20 Jira pages with issues left to read, THE system SHALL return MSG35, with no count of the remaining issues (D8), with the preview.
  - AC-001.4: IF Resolved from or Resolved to is missing, THEN THE system SHALL reject the pull with MSG01 and call Jira not at all.
  - AC-001.5: IF Resolved from is after Resolved to, or either date is after today (Vietnam time), THEN THE system SHALL reject the pull with MSG39 and call Jira not at all.
  - AC-001.6: IF no issue without a Ticket matches the date range, THEN THE system SHALL return MSG04 and create no preview.
  - AC-001.7: IF Jira rejects the service account credentials, does not answer within 30 seconds, returns an error, or the Jira connection is not configured, THEN THE system SHALL return MSG50 with an error message that contains no credential, create no preview, and record one Import Batch of source Jira with status Failed and 0 published.
  - AC-001.8: IF the selected Project is not managed by the caller or is not Active, THEN THE system SHALL reject the pull with HTTP 403 (MSG08), call Jira not at all and create no preview.
  - AC-001.9: IF the caller does not have the Project Manager role, THEN THE system SHALL reject the pull with HTTP 403 (MSG08).
  - AC-001.10: WHEN a pull creates a preview, THE system SHALL delete the caller's previous unconfirmed preview that is not a Draft, if any.

### STORY-002: Write Solutions on the Import Preview
- As a Project Manager, I want to write the Solution of each pulled issue, alone or for several issues at once, so that every imported ticket has a reusable Solution.
- Acceptance:
  - AC-002.1: WHEN the Project Manager lists the rows of a Jira preview, THE system SHALL return them page by page (at most 100 per page) in row-number order with the Jira key, summary, assignee, resolved date and result (Needs Solution or Valid), and the preview SHALL report its source Jira, its date range, the number of rows and the number of Needs Solution rows.
  - AC-002.2: WHEN the Project Manager opens one row of a Jira preview, THE system SHALL return its full snapshot (AC-001.2) and its Solution, if any.
  - AC-002.3: WHEN the Project Manager fills a Solution into one or several Needs Solution or Valid rows of a Jira preview, THE system SHALL store a separate copy of the Solution on each of those rows, replacing any Solution they had, and mark each of them Valid.
  - AC-002.4: IF a filled Solution lacks Symptoms, Root cause, Resolution steps or Scope (blank after trimming), THEN THE system SHALL reject it with MSG01 naming the missing sections and change no row.
  - AC-002.5: IF the sections of a filled Solution are longer than 10,000 characters in total, THEN THE system SHALL reject it with MSG02 and change no row.
  - AC-002.6: WHEN the Project Manager selects several issues and clicks Fill solution while some of them already have a Solution, THE web app SHALL ask MSG36 with the number of issues that will be overwritten before it applies the Solution.
  - AC-002.7: WHEN the Project Manager removes rows from a Jira preview, THE system SHALL mark them Removed, never save them as Tickets, and keep no record of them after the preview is finished or deleted, so that a later pull can return them again.
  - AC-002.8: IF a preview request names a preview that belongs to another user or does not exist, THEN THE system SHALL reject it with HTTP 404 (MSG04).
  - AC-002.9: IF the preview's Project is no longer managed by the caller, THEN THE system SHALL reject every preview request with HTTP 403 (MSG08).
  - AC-002.10: WHILE at least one row of a Jira preview Needs Solution, or the preview has no Valid row, THE web app SHALL keep Finish disabled.
  - AC-002.11: WHEN the Project Manager cancels or leaves the Import Preview, THE web app SHALL ask MSG37 and, on confirmation, return to Import Tickets without importing and without changing a saved Draft.

### STORY-003: Finish the Jira import
- As a Project Manager, I want Finish to publish the issues with their Solutions so that the knowledge base has them at once.
- Acceptance:
  - AC-003.1: WHEN the Project Manager clicks Finish on a Jira preview whose every row is Valid or Removed and at least one row is Valid, THE system SHALL, in one transaction, save each Valid row as a Ticket of the Project with Ticket ID = Jira key, source Jira import, status Published, the snapshot's summary as title, its description, writer and resolved date, and the row's Solution; record an approve decision by the caller for each Ticket; record one Import Batch of source Jira with status Completed and the numbers published, skipped and removed; delete the preview; and return MSG32.
  - AC-003.2: IF a Valid row's Jira key has become a Ticket ID since the pull, THEN THE system SHALL skip that row, import the others, and return MSG38 with the number and keys of the skipped issues together with MSG32.
  - AC-003.3: IF every Valid row of the preview has become a Ticket since the pull, THEN THE system SHALL import nothing, record one Completed Import Batch with 0 published, delete the preview, and return MSG38.
  - AC-003.4: IF the preview's Project is no longer Active at Finish, THEN THE system SHALL reject Finish with HTTP 409 (MSG40), import nothing, keep the preview and record no Import Batch.
  - AC-003.5: IF the preview's Project is Active but the caller no longer manages it, THEN THE system SHALL reject Finish with HTTP 403 (MSG08), import nothing and record no Import Batch. (The MSG40 check of AC-003.4 comes first.)
  - AC-003.6: IF at least one row of the preview Needs Solution, or the preview has no Valid row, THEN THE system SHALL reject Finish with HTTP 409 and `MSG_NEW_4` with the number of Needs Solution rows, import nothing and record no Import Batch.
  - AC-003.7: IF saving any Ticket fails, THEN THE system SHALL import no Ticket, keep the preview, return MSG33, and record one Import Batch of source Jira with status Failed, 0 published and an error description that contains no issue content.
  - AC-003.8: IF the same preview is finished again while or after a Finish of it succeeds, THEN THE system SHALL reject the second Finish with HTTP 404 and record no Import Batch for it.
  - AC-003.9: WHEN Finish publishes at least one Ticket, THE system SHALL publish a "tickets published" event with the new Ticket ids after the transaction commits.
  - AC-003.10: WHEN Finish succeeds on a preview that was a Draft, THE system SHALL delete the Draft.

### STORY-004: Save and reopen a Draft of a Jira import
- As a Project Manager, I want to save an unfinished Jira import as a Draft so that I do not lose the Solutions I wrote.
- Acceptance:
  - AC-004.1: WHEN the Project Manager clicks Save as draft on a Jira preview, THE system SHALL mark the preview as a Draft with the save time, keep its rows and their Solutions without validating them, and return MSG41.
  - AC-004.2: WHEN the Project Manager opens Import Tickets, THE system SHALL list only the caller's own Drafts with their type (Jira), Project, number of rows and date saved, newest first.
  - AC-004.3: WHEN the Project Manager reopens a Draft, THE system SHALL return it as an Import Preview with its rows and Solutions as saved, without reading Jira again.
  - AC-004.4: WHEN the Project Manager deletes a Draft after confirming MSG42, THE system SHALL delete the Draft with its rows.
  - AC-004.5: IF the Draft belongs to another user or does not exist, THEN THE system SHALL reject the reopen or delete with HTTP 404 (MSG04).
  - AC-004.6: WHILE a preview is a Draft, THE daily preview cleanup and a new pull or CSV upload by the same user SHALL keep it.
  - AC-004.7: WHEN the Project Manager saves a reopened Draft again, THE system SHALL update that same Draft and its save time instead of creating a second one.

## Out of Scope
- CSV import (UC-21) — `import-ticket-list`; Drafts of a CSV import (GBR-26, UC-21 step 7b) are a later Change there.
- Manual entry (UC-22), Import History screen (UC-23), Ticket Detail (UC-24), unpublish / re-publish (UC-25), Solution Form submission (UC-19).
- Storing the Jira service account in the database and the UC-10 screen — `workspace`. Until then the connection comes from the environment (D2).
- The Jira field mapping and "resolution comments field" of a Project — `project-integration` (D5).
- Linking a Jira assignee to a T-Solve user (D4).
- Evidence and attachments of Jira issues.
- Search indexing of new tickets — `search` consumes the "tickets published" event.

## Interface Contracts

### Exposed by this module
All under `/api/v1`, role Project Manager, errors as RFC 9457 `ProblemDetail` with `code` = MSGxx. The exact schema goes into `frontend/openapi/tsolve-api.yaml` first.

New endpoints:

| Method + path | Request | Response |
|---|---|---|
| `POST /imports/jira` | `{ projectId (uuid), resolvedFrom, resolvedTo }` (ISO dates) | `ImportPreviewResponse` (source JIRA_IMPORT, message MSG35?) |
| `POST /imports/previews/{previewId}/draft` | — | `{ previewId, savedAt, message MSG41 }` |
| `GET /imports/drafts` | — | list of `ImportDraftSummary` (previewId, source, projectId, rowCount, savedAt) |
| `DELETE /imports/drafts/{previewId}` | — | 204 |

Endpoints of `import-ticket-list` that also serve a Jira preview: `GET /imports/previews/{previewId}`,
`GET …/rows`, `GET …/rows/{rowNo}`, `POST …/fill-solution`, `POST …/remove-rows`, `POST …/confirm` (Finish).
Their schemas gain, all optional for a CSV preview: `ImportPreviewResponse.source`, `resolvedFrom`, `resolvedTo`,
`draftSavedAt` (and `fileName` becomes nullable); `ImportPreviewRowResponse.sourceUrl`, `comments`;
`ImportResultResponse.notice` (MSG38 with the count and the keys).

Rejections of the new endpoints: MSG01, MSG39 → 400 · MSG04 on pull → 404 · MSG50 → 502 · no PM role or Project not managed → 403 (MSG08) · preview or Draft not found, another user's, or a CSV preview → 404 (MSG04). Finish: as Confirm of `import-ticket-list`, plus AC-003.2/AC-003.3 (skip, MSG38) instead of its MSG33 for a taken Ticket ID. Only MSG50 and MSG33 record a Failed Import Batch.

### Required from other modules
- `project` (existing code, owned by `project-integration` later): the Project's Jira project key, through `ProjectService`.
- `import-ticket-list` (PR #110, `vn.tsolve.ticket`): `ProjectAccess`, the `ticket`, `ticket_import_decision`, `import_batch`, `import_preview` and `import_preview_row` tables, the preview endpoints and screens, and `TicketsPublishedEvent`, extended by this module.
- `workspace` (later): the stored Jira service account behind `JiraIssueSource` (D2).

## Definition of Done
- [ ] Every acceptance criterion has a test whose name starts with its AC id
- [ ] `mvn verify` passes (tests + `mvn spotless:check`)
- [ ] Frontend `format:check`, `lint`, `typecheck`, `test`, `build` pass and `npm run generate:api` leaves no diff
- [ ] The Jira connection variables are listed in `.env.example` with no real value
