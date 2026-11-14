---
module: ticket-repository
date: 2026-11-07
owner: BA
---

# Spec: ticket-repository

Source: SRS Report 3 v3460 — UC-20, GBR-15, GBR-23, GBR-26, GBR-33, screens 5.1 Import Tickets (Jira tab), 5.2 Import
Preview, 5.3 Import Ticket Preview, MSG01, MSG02, MSG04, MSG08, MSG32, MSG33, MSG35–MSG42, MSG50; Jira story KAN-45.
This version covers **KAN-45 Import resolved issues from Jira (UC-20)**, including Drafts of a Jira import, and
**KAN-132 Ticket status model** (STORY-005: the one Ticket state machine shared by submit, review and import, GBR-07,
GBR-08, GBR-10, GBR-11, GBR-23, UC-25), and **KAN-133 Create a ticket with its Solution manually (UC-22)**
(STORY-006, STORY-007: manual entry with Evidence and its Drafts; GBR-13, GBR-23, GBR-26, GBR-33, screen 5.1 Manual
entry tab, MSG31, MSG34, MSG72).
CSV import (UC-21) lives in `import-ticket-list`; Import History screen (UC-23), Ticket Detail (UC-24), unpublish /
re-publish (UC-25) and the Solution Form submission (UC-19) are added later through Change Mode.

This spec builds on `import-ticket-list` as changed by PR #110 (KAN-31): the Import Preview, its rows, the
Needs Solution result, Fill solution, remove-rows, Confirm and the Import Ticket Preview popup. A pulled Jira issue
is one row of such a preview.

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-11-07 | STORY-001..004 | Initial spec for KAN-45 Jira import | — |
| 2026-11-07 | STORY-001..004 | A pulled issue is a row of the Import Preview of `import-ticket-list` (D7): AC-001.1, AC-001.2, AC-002.1–AC-002.3, AC-002.7, AC-002.10, AC-003.1, AC-003.6 and the Interface Contracts reworded to its rows, Fill solution, remove-rows and Confirm; Project ids are UUIDs; a row still Needs Solution at Finish gives `MSG_NEW_4` as in CSV import | KAN-45 is stacked on PR #110, which already has these parts (user decision 2026-11-07) |
| 2026-11-07 | STORY-001 | AC-001.3: MSG35 also after the 20-page scan cap, and without a count (D8) | Jira gives no total and counting would need every page (NFR 1); user decision 2026-11-07 |
| 2026-11-07 | STORY-001 | D2 reworded: scoped API token as Bearer through the Atlassian API gateway (`api.atlassian.com/ex/jira/{cloudId}`), env `JIRA_SITE_URL`, `JIRA_CLOUD_ID`, `JIRA_API_TOKEN`; no email. New AC-001.11 (retry 429/503 with `Retry-After`) and AC-001.12 (gateway + Bearer, link on the site); AC-001.7 reworded to apply after those retries | RESEARCH-003: a service account has only scoped tokens, which get 401 with basic auth on the site URL; user decisions 2026-11-07 (scoped only, retry twice) |
| 2026-11-08 | STORY-001 | New AC-001.13: a pull stops reading Jira after 60 seconds in total and answers MSG50 as in AC-001.7 | PR #166 review (trhieudev1309), BUG-189: up to 20 pages plus comment calls, each with a 30 s timeout and 2 retries, could hold a request thread for minutes; user decision 2026-11-08 |
| 2026-11-13 | STORY-005 | New STORY-005 (KAN-132): AC-005.1–AC-005.5, decisions D9–D11 | One Ticket state machine for submit, review and import; review Approve did not publish the "tickets published" event, so approved tickets were never indexed for search; user decisions 2026-11-13 |
| 2026-11-14 | STORY-006, STORY-007 | New STORY-006 (manual entry, AC-006.1–AC-006.12) and STORY-007 (its Drafts, AC-007.1–AC-007.8) for KAN-133; decisions D12–D18; manual entry leaves Out of Scope; new endpoints under `/imports/manual`; Drafts list and delete also serve a Manual Draft | KAN-133 (UC-22); decisions taken by Claude under the user's standing instruction of 2026-11-11, logged in `04-plans/ticket-repository/kan-133-decisions.md` |

## Decisions taken while planning (2026-11-07, with the user)
| # | Question | Decision |
|---|----------|----------|
| D1 | Scope of this plan | Only UC-20 (KAN-45). UC-19, UC-22–25 come later through Change Mode. |
| D2 | How T-Solve reads Jira | The Workspace Jira service account (KAN-45 update of 01/11, DB tables doc 31/10), not a per-Project token (SRS GBR-14 is outdated). The import calls a `JiraIssueSource` interface. The credential is a scoped API token (the only kind a service account has), sent as a Bearer token to the Atlassian API gateway `https://api.atlassian.com/ex/jira/{cloudId}`; no email is used. Until `workspace` stores the service account, the implementation reads the Jira site URL (for the issue links), the site's cloud id and the API token from the environment (`JIRA_SITE_URL`, `JIRA_CLOUD_ID`, `JIRA_API_TOKEN`). Classic unscoped tokens with basic auth are not supported (user decision 2026-11-07, RESEARCH-003). |
| D3 | Drafts | Included. A Draft is an Import Preview marked as a Draft; the daily cleanup skips Drafts. |
| D4 | Writer of an imported ticket | The Jira assignee's display name at pull time; empty when the issue is unassigned (no Jira ↔ T-Solve account linking yet). |
| D5 | Comments shown in the popup | Every comment of the issue at pull time, as plain text with author and date. The "mapped resolution comments field" of `project-integration` is not used yet. |
| D6 | Requirements shared with CSV import | Required Solution sections, text limits, preview storage and the "one unconfirmed preview per PM" rule are those of `import-ticket-list` (D1, D7, D8, D9 there). |
| D7 | Separate Jira preview or the CSV one | The CSV Import Preview of PR #110: one row per issue (Ticket ID = Jira key, result Needs Solution until it has a Solution), with two new row fields, the issue's link and its comments. Rows list, row detail, Fill solution, remove-rows, Confirm and the screens are reused; only the pull and Drafts are new endpoints. |
| D8 | MSG35's text in Report3 names how many more issues match, but the pull stops at 100 kept issues or 20 Jira pages, and Jira's `search/jql` gives no total | MSG35 names no count. Its Report3 text drops the count parameter (Report3 change by the team; then `report3-messages.json` and the web app's catalog follow). The pull does not count the remaining issues. |
| D9 | Which status changes are allowed (KAN-132) | The table of AC-005.1, taken from SRS v3460 GBR-08, GBR-10, GBR-11, GBR-23, UC-19, UC-25 and UC-30. Rejected is final; Unpublished only goes back to Published (user decision 2026-11-13). |
| D10 | Where the "tickets published / unpublished" events are published | The rule lives in `TicketStatus` and `Ticket`; one shared transition step moves a batch of Tickets and publishes ONE event with all their ids, as Finish already does (AC-003.9). Not one Spring `@DomainEvents` event per Ticket: it needs `save()`, which review does not call, and it would split a 100-ticket import into 100 events (user decision 2026-11-13). |
| D11 | Answer to a status change the table does not allow | HTTP 409 with MSG06, no Ticket of the request changed — the answer review already gives for a Ticket that is not Pending review (UC-30 step 3). |
| D12 | Evidence in KAN-133 (2026-11-14) | Included. Files go through the `platform` `FileStorage` (STORY-007 of `platform`, KAN-96, PR #193); only the storage key, file name, type and size are kept in the database. The Evidence parts are built after PR #193 is merged. |
| D13 | Where a Manual Draft lives | An Import Preview of source Manual with exactly one row holding the entered fields, plus the Draft's Evidence list on the preview. The Drafts list, delete, ownership and the cleanup exemption of STORY-004 apply to it. |
| D14 | Writer of a manual Ticket | The caller: writer = their full name, writer id = their user id (UC-22 step 6). |
| D15 | Resolved date in the future | Rejected with MSG39 (its text already says no date may be in the future); the web app's date picker offers no future day. SRS screen 5.1 names no other message. |
| D16 | What a Manual Draft validates | Only what is needed to keep it safely: a Project the caller manages (MSG01 when missing, 403 otherwise) and the Evidence rules (MSG34, MSG72). Every other field is kept as entered (GBR-26). |
| D17 | Import Batch of a manual entry | One Completed Import Batch of source Manual with 1 published per Save. A rejected Save (validation, MSG31, 403) records none; a Save that fails after validation records one Failed batch (MSG33), as in AC-003.7. |
| D18 | Evidence file type check | By file-name extension, case-insensitive: `.png`, `.jpg`, `.jpeg`, `.pdf`, `.docx`, `.xlsx`, `.txt`, `.log`; the stored type follows the extension, not the browser's content type. |

## Requirements

### Functional
1. A Project Manager selects one of their Active Projects and a resolved date range, and pulls the resolved Jira issues of that Project's Jira project that have no Ticket yet: at most 100, oldest first (GBR-15).
2. On the Import Preview the Project Manager reads each issue (summary, description, comments, assignee, resolved date, link to Jira), writes its Solution one issue at a time or for several issues at once, and removes issues they do not want.
3. Finish (Confirm of a Jira preview) saves every remaining issue as a Published Ticket with an approve decision by the Project Manager (GBR-23), skips issues that got a Ticket in the meantime, and records exactly one Import Batch.
4. The Project Manager can save the import as a Draft, reopen it later without pulling from Jira again, and delete it (GBR-26).
5. On the Manual entry tab the Project Manager enters one resolved ticket of a Project they manage — Ticket ID, title, description, resolved date, Solution and up to 5 Evidence files — and saves it as a Published Ticket with an approve decision by themselves (UC-22, GBR-23).
6. The Project Manager can save a manual entry as a Draft without validation, reopen it, change it, remove its Evidence files and save or delete it (GBR-26).

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
  - AC-001.7: IF Jira rejects the service account credentials, does not answer within 30 seconds, returns an error (for 429 and 503: after the retries of AC-001.11), or the Jira connection is not configured, THEN THE system SHALL return MSG50 with an error message that contains no credential, create no preview, and record one Import Batch of source Jira with status Failed and 0 published.
  - AC-001.8: IF the selected Project is not managed by the caller or is not Active, THEN THE system SHALL reject the pull with HTTP 403 (MSG08), call Jira not at all and create no preview.
  - AC-001.9: IF the caller does not have the Project Manager role, THEN THE system SHALL reject the pull with HTTP 403 (MSG08).
  - AC-001.10: WHEN a pull creates a preview, THE system SHALL delete the caller's previous unconfirmed preview that is not a Draft, if any.
  - AC-001.11: IF a Jira call answers HTTP 429, or HTTP 503 with a `Retry-After` header, and `Retry-After` is at most 5 seconds, THEN THE system SHALL wait that many seconds and send the same call again, at most 2 times per call; a missing `Retry-After` on a 429 counts as 1 second, and a `Retry-After` above 5 seconds ends the retries at once.
  - AC-001.12: WHEN the system calls Jira for a pull, THE system SHALL send the configured API token as `Authorization: Bearer` to `https://api.atlassian.com/ex/jira/{cloudId}` with the configured cloud id, and SHALL build each row's link to the issue as the configured Jira site URL + `/browse/{key}`.
  - AC-001.13: IF reading Jira for one pull — every page, comment call and retry together — takes more than 60 seconds, THEN THE system SHALL stop reading and answer as in AC-001.7: MSG50 with an error message that contains no credential, no preview, and one Import Batch of source Jira with status Failed and 0 published.

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

### STORY-005: One Ticket state machine (KAN-132)
- As a developer, I want one Ticket state machine shared by submit, review and import so that no flow can put a Ticket into a status the business rules forbid, and other modules learn when a Ticket enters or leaves use.
- Acceptance:
  - AC-005.1: WHEN the status of a Ticket changes, THE system SHALL allow only these changes: Pending review to Published, Rejected, Changes requested or Expired; Changes requested to Pending review or Expired; Expired to Pending review; Published to Unpublished; Unpublished to Published (D9). Rejected SHALL change to no other status.
  - AC-005.2: WHEN a Ticket is created by an import, THE system SHALL create it in Published (GBR-23).
  - AC-005.3: IF a request would change a Ticket's status in a way AC-005.1 does not allow, THEN THE system SHALL reject the request with HTTP 409 (MSG06) and change the status of none of the Tickets of that request (D11).
  - AC-005.4: WHEN a request moves one or more Tickets to Published (review Approve, re-publish), THE system SHALL publish one "tickets published" event with the ids of all those Tickets, for listeners to handle after the transaction commits.
  - AC-005.5: WHEN a request moves one or more Tickets to Unpublished, THE system SHALL publish one "tickets unpublished" event with the ids of all those Tickets, for listeners to handle after the transaction commits.

### STORY-006: Create a ticket with its Solution manually (KAN-133)
- As a Project Manager, I want to enter a resolved ticket with its Solution and optional Evidence by hand so that knowledge that is not in Jira can also be reused.
- Acceptance:
  - AC-006.1: WHEN a Project Manager saves a manual entry for an Active Project they manage with a Ticket ID, title, description and a Solution whose required sections are filled, and every check of AC-006.3–AC-006.8 passes, THE system SHALL, in one transaction, save a Ticket of that Project with source Manual, status Published, the trimmed Ticket ID, title, description and Solution sections, the resolved date if one was entered, writer = the caller's full name and writer id = the caller's user id (D14); record an approve decision by the caller; record one Import Batch of source Manual with status Completed and 1 published (D17); and return HTTP 201 with the new Ticket's id and MSG32 (1 created, 0 skipped).
  - AC-006.2: WHEN a manual entry with Evidence files is saved as in AC-006.1, THE system SHALL store each file through the Workspace file storage (D12) and record it as Evidence of the new Ticket with its file name, type (D18) and size, in the same save.
  - AC-006.3: IF the Project, Ticket ID, title, description, Symptoms, Root cause, Resolution steps or Scope is missing or blank after trimming, THEN THE system SHALL reject the save with HTTP 400 and MSG01 naming every missing field, save nothing, store no file and record no Import Batch.
  - AC-006.4: IF the Ticket ID is longer than 50 characters, the title longer than 200, the description longer than 10,000, or the Solution sections longer than 10,000 characters in total, THEN THE system SHALL reject the save with HTTP 400 and MSG02 naming the field and its limit, save nothing, store no file and record no Import Batch.
  - AC-006.5: IF the resolved date is after today (Vietnam time), THEN THE system SHALL reject the save with HTTP 400 and MSG39 (D15), save nothing, store no file and record no Import Batch.
  - AC-006.6: IF an Evidence file has a name whose extension is not one of D18, or is larger than 10 MB, THEN THE system SHALL reject the save with HTTP 400 and MSG34 naming that file, save nothing, store no file and record no Import Batch.
  - AC-006.7: IF the entry has more than 5 Evidence files, THEN THE system SHALL reject the save with HTTP 400 and MSG72, save nothing, store no file and record no Import Batch.
  - AC-006.8: IF the trimmed Ticket ID is already the Ticket ID of a Ticket, including one saved by another request while this save runs, THEN THE system SHALL reject the save with HTTP 409 and MSG31 with that Ticket ID, save no Ticket, keep no file it stored for this save, and record no Import Batch.
  - AC-006.9: IF the caller does not have the Project Manager role, or the Project is not Active or not managed by the caller, THEN THE system SHALL reject the save with HTTP 403 (MSG08), save nothing and store no file.
  - AC-006.10: WHEN a manual entry is saved as in AC-006.1, THE system SHALL publish one "tickets published" event with the new Ticket's id, for listeners to handle after the transaction commits.
  - AC-006.11: IF saving the Ticket, its Evidence or its files fails after the checks of AC-006.3–AC-006.9 pass, THEN THE system SHALL save no Ticket and no Evidence, keep no file it stored for this save, return MSG33, and record one Import Batch of source Manual with status Failed, 0 published and an error description that contains no ticket content.
  - AC-006.12: WHEN the Project Manager uses the Manual entry tab, THE web app SHALL offer only the caller's Active managed Projects and no future resolved date, check the Evidence type, size and count before sending (MSG34, MSG72), show each rejection next to its field, and keep every entered value and chosen file after a rejection.

### STORY-007: Save and reopen a Draft of a manual entry (KAN-133)
- As a Project Manager, I want to save an unfinished manual entry as a Draft so that I do not lose what I entered.
- Acceptance:
  - AC-007.1: WHEN the Project Manager clicks Save as draft on the Manual entry tab with a Project they manage, THE system SHALL save every entered field as entered and every Evidence file as a Draft of type Manual owned by the caller with the save time, check nothing else (D16), and return MSG41.
  - AC-007.2: IF a Manual Draft is saved without a Project, THEN THE system SHALL reject it with HTTP 400 and MSG01; IF its Project is not managed by the caller, THEN with HTTP 403 (MSG08); IF its Evidence breaks AC-006.6 or AC-006.7, THEN with MSG34 or MSG72 — and in each case save nothing and store no file.
  - AC-007.3: WHEN the Project Manager opens Import Tickets, THE system SHALL list the caller's Manual Drafts together with their other Drafts (AC-004.2), each with type Manual, its Project, 1 row and the date saved.
  - AC-007.4: WHEN the Project Manager reopens a Manual Draft, THE system SHALL return its fields as saved and its Evidence files (name, type, size), and THE web app SHALL show them on the Manual entry tab.
  - AC-007.5: WHEN the Project Manager saves a reopened Manual Draft again, THE system SHALL update that same Draft and its save time, add the new Evidence files, and delete from the Draft and from file storage the Evidence files the Project Manager removed; the kept and new files together SHALL obey AC-006.7.
  - AC-007.6: WHEN a reopened Manual Draft is saved as a Ticket as in AC-006.1, THE system SHALL make the Draft's kept Evidence files and the new ones the Ticket's Evidence, delete the files the Project Manager removed, and delete the Draft in the same transaction; IF the save is rejected or fails, THEN the Draft SHALL stay as it was saved.
  - AC-007.7: WHEN the Project Manager deletes a Manual Draft after confirming MSG42, THE system SHALL delete the Draft and its Evidence files from file storage.
  - AC-007.8: IF a Manual Draft request names a Draft of another user, a preview that is not a Manual Draft, or none at all, THEN THE system SHALL reject it with HTTP 404 (MSG04); and the Import Preview endpoints (`GET /imports/previews/{previewId}` and its rows, fill-solution, remove-rows, draft, confirm) SHALL reject a Manual Draft with HTTP 404 (MSG04).

## Out of Scope
- CSV import (UC-21) — `import-ticket-list`; Drafts of a CSV import (GBR-26, UC-21 step 7b) are a later Change there.
- Import History screen (UC-23), Ticket Detail (UC-24), unpublish / re-publish (UC-25), Solution Form submission (UC-19).
- Showing or downloading the Evidence of a Ticket (UC-24) and adding Evidence after changes are requested (UC-19); STORY-006 only stores it.
- Checking an Evidence file's content against its extension (D18).
- Storing the Jira service account in the database and the UC-10 screen — `workspace`. Until then the connection comes from the environment (D2).
- The Jira field mapping and "resolution comments field" of a Project — `project-integration` (D5).
- Linking a Jira assignee to a T-Solve user (D4).
- Evidence and attachments of Jira issues.
- Search indexing of new tickets — `search` consumes the "tickets published" event. Removing an Unpublished ticket from search, triage and the chatbot — `search` and the others consume the "tickets unpublished" event later.
- The unpublish / re-publish endpoints and screens (UC-25) and the Solution Form submission (UC-19): STORY-005 gives them the transition step and the events only.

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

Manual entry (KAN-133) — multipart requests: part `entry` (JSON) and zero or more parts `files`:

| Method + path | Request | Response |
|---|---|---|
| `POST /imports/manual` | `entry`: `ManualEntryRequest { projectId (uuid), ticketId, title, description, resolvedDate?, symptoms, rootCause, resolutionSteps, workaround?, scope, draftId?, keepEvidenceIds? (uuid[]) }`; `files` | 201 `ManualEntryResponse { ticketId (uuid), message MSG32 }` |
| `POST /imports/manual/drafts` | the same parts; every `entry` field but `projectId` optional; `draftId` given = update that Draft (AC-007.5) | `DraftSavedResponse` (MSG41) |
| `GET /imports/manual/drafts/{previewId}` | — | `ManualDraftResponse { previewId, projectId, ticketId?, title?, description?, resolvedDate?, symptoms?, rootCause?, resolutionSteps?, workaround?, scope?, evidence: [{ id (uuid), fileName, fileType, fileSize }], savedAt }` |

`GET /imports/drafts` also lists Manual Drafts (`source` MANUAL, `rowCount` 1) and `DELETE /imports/drafts/{previewId}`
also deletes them. `keepEvidenceIds` names the Draft's Evidence files to keep; the others are removed. A `resolvedDate`
the Draft holds that is not a valid date is kept as text and rejected only on Save (MSG39).
Rejections: MSG01, MSG02, MSG34, MSG39, MSG72 → 400 · MSG31 → 409 · no PM role or Project not Active / not managed →
403 (MSG08) · Draft not found, another user's or not Manual → 404 (MSG04) · MSG33 → 500. Only MSG33 records a Failed
Import Batch.

Rejections of the new endpoints: MSG01, MSG39 → 400 · MSG04 on pull → 404 · MSG50 → 502 · no PM role or Project not managed → 403 (MSG08) · preview or Draft not found, another user's, or a CSV preview → 404 (MSG04). Finish: as Confirm of `import-ticket-list`, plus AC-003.2/AC-003.3 (skip, MSG38) instead of its MSG33 for a taken Ticket ID. Only MSG50 and MSG33 record a Failed Import Batch.

### Required from other modules
- `project` (existing code, owned by `project-integration` later): the Project's Jira project key, through `ProjectService`.
- `import-ticket-list` (PR #110, `vn.tsolve.ticket`): `ProjectAccess`, the `ticket`, `ticket_import_decision`, `import_batch`, `import_preview` and `import_preview_row` tables, the preview endpoints and screens, and `TicketsPublishedEvent`, extended by this module.
- `workspace` (later): the stored Jira service account behind `JiraIssueSource` (D2).
- `platform` (KAN-96, PR #193): `vn.tsolve.platform.service.FileStorage` — `store(InputStream)` → a key, `delete(key)` — for Evidence files (D12).
- `common.security.TSolveUser`: the caller's user id and full name (D14).

## Definition of Done
- [ ] Every acceptance criterion has a test whose name starts with its AC id
- [ ] `mvn verify` passes (tests + `mvn spotless:check`)
- [ ] Frontend `format:check`, `lint`, `typecheck`, `test`, `build` pass and `npm run generate:api` leaves no diff
- [ ] The Jira connection variables are listed in `.env.example` with no real value
