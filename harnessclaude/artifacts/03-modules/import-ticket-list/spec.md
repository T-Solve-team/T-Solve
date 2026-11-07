---
module: import-ticket-list
date: 2026-10-21
owner: BA
---

# Spec: import-ticket-list

Source: SRS Report 3 v3456 — UC-21, GBR-23, GBR-26, GBR-33, business rules "Ticket ID is unique" and "Each import is one Import Batch", the Import Tickets / Import Preview screens, MSG01–MSG04 and MSG27–MSG41, Performance §2.2.
This version covers only **KAN-31 Import tickets from CSV (UC-21)**. Jira import (UC-20), manual entry (UC-22), Import History screen (UC-23) and Drafts (GBR-26) are added later through Change Mode.
The web app screens (STORY-008..010) follow the SRS v3460 screen layouts III.5.1 Import Tickets, III.5.2 Import Preview and III.5.3 Import Ticket Preview (popup).

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-10-21 | STORY-001..006 | Initial spec for KAN-31 CSV import | — |
| 2026-10-21 | STORY-001, STORY-002 | AC-001.1 returns the first page of rows; AC-002.8 lists rows page by page with Solution sections cut to 200 characters; new AC-002.11 (row filter by result) | Risk review: 5,000 rows × 10,000 characters made one response tens of MB |
| 2026-10-21 | STORY-002..004 | AC-002.1 names AC-002.7; AC-002.7 uses Vietnam time; AC-003.1 allows removing Error rows; AC-003.2 also covers reading rows; AC-004.1 saves description and resolved date and trims values; AC-004.7/004.8 define the Failed batch; status code per rejection | Builder check (user: Failed batch only for the MSG33 cases) |
| 2026-10-22 | STORY-003, STORY-004 | New AC-003.4 (rows list, row detail and remove-rows re-check that the caller manages an Active Project → 403 MSG08); new AC-004.11 (Confirm by a caller who no longer manages the Active Project → 403 MSG08, after the MSG40 check); new AC-004.12 (a second Confirm of the same preview → 404, no Import Batch); AC-004.6 says it records no Import Batch | Evaluation attempt 1 of csv-confirm: NFR 3 had no criterion for Confirm and the rows endpoints; design edge case 21 was not in the spec (user decisions 2026-10-22) |
| 2026-11-01 | — | Project ids are UUIDs in the API (`projectId` of the upload and of `ImportPreviewResponse`) and in the database (D10) | The web app takes the Project from the session (`CurrentUser.projects`), whose ids are UUIDs; the import used `int64` (user decision 2026-11-01) |
| 2026-11-01 | STORY-007 | New STORY-007: read an existing preview (counts and first page) — `GET /imports/previews/{previewId}` | The Import Preview screen must show its Summary again after a reload (user decision 2026-11-01) |
| 2026-11-01 | STORY-008..010 | New STORY-008 (Import Tickets screen, CSV tab), STORY-009 (Import Preview screen), STORY-010 (Import Ticket Preview popup); the shared `CsvUpload` kit component (D12) | KAN-31 had no web screens; the user asked for the CSV screens with the Jira and Manual entry tabs shown disabled (user decision 2026-11-01) |
| 2026-11-01 | STORY-002 | AC-002.6 uses `MSG_NEW_3` instead of MSG78 (D14); New messages lists `MSG_NEW_3` | System_Messages v3460 gave MSG78 to another message, so the Import Preview would show the wrong text (user decision 2026-11-01) |
| 2026-11-05 | STORY-012..016 | New import type: a file of tickets without a Solution, whose Solutions the Project Manager writes on the Import Preview (D15–D20); new row result Needs Solution; AC-002.1, AC-002.2, AC-003.3, AC-005.1 and AC-010.1 limited to the file type they apply to; Out of Scope updated; `MSG_NEW_4` | The user wants to choose between importing tickets that have a Solution and tickets that do not (user decision 2026-11-05) |
| 2026-11-07 | STORY-008 | AC-008.1 and D13: the Jira tab works and the Drafts list is shown, as `ticket-repository` STORY-001 and STORY-004 say; only Manual entry stays disabled; Out of Scope updated | KAN-45 (`ticket-repository`) builds the Jira tab and the Drafts list on this screen; its evaluation found AC-008.1 contradicting the code (user decision 2026-11-07) |

## Decisions taken while planning (2026-10-21, with the user)
| # | Question | Decision |
|---|----------|----------|
| D1 | Required Solution sections (GBR-33 vs screen field / UC-19) | Symptoms, Root cause, Resolution steps and Scope are required; Workaround is optional. GBR-33 in the SRS must be corrected. |
| D2 | Existing `resolved_ticket_snapshot` / upsert model | Replaced by a new `ticket` table with an embedded Solution; the old snapshot code in `vn.tsolve.ticket` is retired. |
| D3 | Project source | A `ProjectAccess` interface with a stub implementation; `ticket.project_id` has no FK until the workspace module exists. |
| D4 | CSV template header | Required: `ticket_id, title, symptoms, root_cause, resolution_steps, scope`. Optional: `description, author, resolved_date, workaround`. Unknown columns are ignored. |
| D5 | Same `ticket_id` twice in one file | Every row with that ID is invalid (new message `MSG_NEW_3`, D14). |
| D6 | Non-UTF-8 / `;`-delimited / empty file | Non-UTF-8 or unparseable → MSG27; `;` → fails the header check (MSG29); no data rows → MSG04. |
| D7 | Text limits | ticket_id ≤ 50, title ≤ 200, description ≤ 10,000, author ≤ 100, Solution sections ≤ 10,000 in total. |
| D8 | Preview storage | Database tables (`import_preview`, `import_preview_row`). |
| D9 | Abandoned previews | A new upload by the same PM replaces their unconfirmed preview; a daily job deletes unconfirmed previews older than 24 hours. |

## Decisions taken for the web screens (2026-11-01, with the user)
| # | Question | Decision |
|---|----------|----------|
| D10 | Project id type (`int64` in the import, UUID in the session) | UUID everywhere: the API `projectId` fields, `ProjectAccess`, the stub setting (`user:<uuid>,<uuid>`) and the `project_id` columns (new changeset; the applied one is not edited). |
| D11 | Import Preview after a reload | New endpoint `GET /imports/previews/{previewId}` returns the same `ImportPreviewResponse`; the screen keeps the preview id in its URL (`/import/preview?previewId=<id>`). |
| D12 | File picker (raw antd `Upload` is banned outside `shared/ui`; the kit's `CsvUpload` was not built) | This change adds `CsvUpload` to `src/shared/ui/` (Figma "Web/Upload": Idle / Dragging / File listed / File error; .csv and 10 MB checks with MSG27 / MSG28). The FE Lead reviews the `shared/` part of the pull request. |
| D13 | Scope of the Import Tickets screen | Tabs Jira, CSV and Manual entry are shown; Manual entry is disabled. The Jira tab and the Drafts list come from `ticket-repository` (KAN-45). No "Save as draft" for a CSV preview. |
| D14 | Code of the duplicate-in-file error (System_Messages v3460 gave MSG78 to another message) | `MSG_NEW_3` until System_Messages numbers it, like `MSG_NEW_2` (fe-foundation AC-006.9). A bad `resolved_date` keeps MSG39 (its date-range text is shown as it is). |

## Decisions taken for the import type (2026-11-05, with the user)
| # | Question | Decision |
|---|----------|----------|
| D15 | Where do tickets without a Solution go? | Nowhere until they have one: the Project Manager writes the Solution on the Import Preview, as in Jira import (UC-20). Tickets stay Published at once (GBR-23) and always have a Solution (GBR-33); no new Ticket status. |
| D16 | Fill several rows at once | Yes, as "Fill solution" in UC-20: one set of sections is copied into each selected row; MSG36 asks first when some of them already have a Solution. |
| D17 | Confirm while rows are not ready | Blocked, as Finish in UC-20: Confirm needs every row of the preview to be Valid or Removed. A row with an error cannot get a Solution, so the Project Manager removes it. |
| D18 | Columns of a file without a Solution | Required: `ticket_id, title`. Optional: `description, author, resolved_date`. Solution columns in such a file are ignored, like other columns outside its template (AC-001.8). |
| D19 | Row result | New result Needs Solution: the row passes every other rule and has no complete Solution yet. It is not an Error, so it does not count in MSG30. |
| D20 | Message for the blocked Confirm | New `MSG_NEW_4` until System_Messages numbers it, like `MSG_NEW_3` (D14). A Fill solution request that cannot apply returns MSG06: the screen never sends one. |

## Requirements

### Functional
1. A Project Manager uploads one CSV file for one of the Projects they manage, sees an Import Preview with valid and invalid rows, removes rows they do not want, and confirms.
2. Confirm saves the remaining valid rows as Published Tickets with an approve decision by the PM (GBR-23), and records exactly one Import Batch (Completed or Failed).
3. The import is all-or-nothing: either every remaining valid row becomes a Ticket, or none does.
4. The Project Manager chooses the import type: a file of tickets that already have a Solution, or a file of tickets without one, whose Solutions they write on the Import Preview before confirming.

### Non-Functional
1. A 10 MB CSV file (about 5,000 rows) is validated and its preview returned within 60 seconds (SRS §2.2).
2. Each PM has at most one unconfirmed preview at a time.
3. Every endpoint is restricted to the Project Manager role and to Projects the caller manages; the backend re-checks this on every call.

## User Stories

### STORY-001: Upload a CSV file
- As a Project Manager, I want to upload a CSV file of resolved tickets for one of my Projects so that I can bring past knowledge into T-Solve.
- Acceptance:
  - AC-001.1: WHEN a Project Manager uploads a file with the `.csv` extension that is valid UTF-8, at most 10 MB, has every required column and at least one data row, for a Project they manage, THE system SHALL create an Import Preview and return its counts and the first page of its rows.
  - AC-001.2: IF the uploaded file name does not end in `.csv` (case-insensitive), THEN THE system SHALL reject it with MSG27 and create no preview.
  - AC-001.3: IF the uploaded file is not valid UTF-8 or cannot be parsed as RFC 4180 CSV, THEN THE system SHALL reject it with MSG27 and create no preview.
  - AC-001.4: IF the uploaded file is larger than 10 MB, THEN THE system SHALL reject it with MSG28 and create no preview.
  - AC-001.5: IF the header row lacks any of `ticket_id, title, symptoms, root_cause, resolution_steps, scope`, THEN THE system SHALL reject the file with MSG29 listing every missing column and create no preview.
  - AC-001.6: IF the file has a header row but no data rows, THEN THE system SHALL reject it with MSG04 and create no preview.
  - AC-001.7: WHEN the file starts with a UTF-8 byte-order mark, THE system SHALL ignore the mark when it reads the header.
  - AC-001.8: WHEN the header contains columns that are not in the template, THE system SHALL ignore those columns.
  - AC-001.9: IF the selected Project is not managed by the caller or is not Active, THEN THE system SHALL reject the upload with HTTP 403 and create no preview.
  - AC-001.10: IF the caller does not have the Project Manager role, THEN THE system SHALL reject the upload with HTTP 403.
  - AC-001.11: WHEN a Project Manager uploads a file while they already have an unconfirmed preview, THE system SHALL delete the previous preview and keep only the new one.
  - AC-001.12: WHEN a field value is enclosed in double quotes, THE system SHALL read commas, doubled quotes and line breaks inside it as part of the value.

### STORY-002: See which rows are valid
- As a Project Manager, I want each row checked and its errors shown so that I know exactly what will be imported.
- Acceptance:
  - AC-002.1: WHEN a preview of a file with Solutions is created, THE system SHALL mark a row Valid when every rule in AC-002.2 to AC-002.7 passes, and Error otherwise, listing every failed rule of that row.
  - AC-002.2: IF a row of a file with Solutions has an empty `ticket_id`, `title`, `symptoms`, `root_cause`, `resolution_steps` or `scope` (blank after trimming), THEN THE system SHALL mark the row Error with MSG01 for each empty field.
  - AC-002.3: IF a row has `ticket_id` over 50, `title` over 200, `description` over 10,000 or `author` over 100 characters, THEN THE system SHALL mark the row Error with MSG02 for each field over its limit.
  - AC-002.4: IF the five Solution sections of a row together exceed 10,000 characters, THEN THE system SHALL mark the row Error with MSG02 for the Solution.
  - AC-002.5: IF a row's `ticket_id` already exists as a Ticket, THEN THE system SHALL mark the row Error with MSG31.
  - AC-002.6: IF the same `ticket_id` appears in more than one row of the file, THEN THE system SHALL mark every one of those rows Error with `MSG_NEW_3`.
  - AC-002.7: IF a row has a non-empty `resolved_date` that is not an ISO date (`yyyy-MM-dd`) or is after today in Vietnam time (Asia/Ho_Chi_Minh), THEN THE system SHALL mark the row Error with MSG39.
  - AC-002.8: THE system SHALL list the preview rows page by page (page size at most 100, ordered by row number), giving for each row its row number in the file, Ticket ID, Title, Author, Resolved date, the first 200 characters of each Solution section, its result (Valid / Error / Removed) and its error details.
  - AC-002.9: WHEN at least one row is Error, THE system SHALL return MSG30 with the number of Error rows.
  - AC-002.10: THE system SHALL compare Ticket IDs exactly as written, after trimming surrounding spaces.
  - AC-002.11: WHEN the Project Manager lists the rows with a result filter (Valid, Error or Removed), THE system SHALL return only the rows with that result.

### STORY-003: Remove rows before importing
- As a Project Manager, I want to remove rows from the preview so that only the tickets I choose are imported.
- Acceptance:
  - AC-003.1: WHEN the Project Manager removes rows from their preview, THE system SHALL mark those rows Removed, whether they were Valid or Error, and exclude them from Confirm.
  - AC-003.2: IF the preview belongs to another user, has been confirmed, or no longer exists, or the requested row number does not exist in it, THEN THE system SHALL reject the read or change with HTTP 404 and change nothing.
  - AC-003.3: WHEN the Project Manager opens one row of their preview, THE system SHALL return its full content, including every Solution section.
  - AC-003.4: IF the caller no longer manages the preview's Project or the Project is no longer Active, THEN THE system SHALL reject listing rows, opening a row and removing rows with HTTP 403 (MSG08) and change nothing.

### STORY-004: Confirm the import
- As a Project Manager, I want to confirm the preview so that the valid tickets are published immediately.
- Acceptance:
  - AC-004.1: WHEN the Project Manager confirms a preview with at least one Valid, non-removed row, THE system SHALL save each such row as a Ticket of the preview's Project with source CSV, status Published, writer = the `author` value (none when absent), its description, resolved date and Solution sections, with surrounding spaces trimmed from every value.
  - AC-004.2: WHEN Tickets are saved on Confirm, THE system SHALL record, for each Ticket, an approve decision by the confirming Project Manager (GBR-23).
  - AC-004.3: WHEN Confirm succeeds, THE system SHALL record one Import Batch with the Project, the Project Manager, source CSV, the file name, the number of tickets published, the number of Error and Removed rows, status Completed and the date, and link each new Ticket to it.
  - AC-004.4: WHEN Confirm succeeds, THE system SHALL return MSG32 with the created count and the skipped count (Error + Removed rows) and delete the preview.
  - AC-004.5: IF the preview has no Valid, non-removed row, THEN THE system SHALL reject Confirm with HTTP 409 and import nothing.
  - AC-004.6: IF the preview's Project is no longer Active at Confirm, THEN THE system SHALL reject Confirm with MSG40, import nothing and record no Import Batch.
  - AC-004.7: IF a Valid row's `ticket_id` has become an existing Ticket since the preview was created, THEN THE system SHALL fail the whole Confirm with MSG33, import nothing, and record one Import Batch with status Failed.
  - AC-004.8: IF saving any Ticket fails, THEN THE system SHALL import no Ticket, return MSG33, and still record one Import Batch with status Failed, 0 published, and an error description that contains no row content.
  - AC-004.9: IF the preview belongs to another user or no longer exists, THEN THE system SHALL reject Confirm with HTTP 404.
  - AC-004.10: WHEN Confirm succeeds, THE system SHALL publish a "tickets published" event with the new Ticket ids after the transaction commits.
  - AC-004.11: IF the preview's Project is Active but the caller no longer manages it, THEN THE system SHALL reject Confirm with HTTP 403 (MSG08), import nothing and record no Import Batch. (The MSG40 check of AC-004.6 comes first.)
  - AC-004.12: IF the same preview is confirmed again while or after a Confirm of it succeeds, THEN THE system SHALL reject the second Confirm with HTTP 404, record no Import Batch for it, and leave the first import unchanged.

### STORY-005: Download the CSV template
- As a Project Manager, I want to download a CSV template so that my file has the right columns.
- Acceptance:
  - AC-005.1: WHEN a Project Manager downloads the template of a file with Solutions, THE system SHALL return a UTF-8 CSV file whose header row is `ticket_id,title,description,author,resolved_date,symptoms,root_cause,resolution_steps,workaround,scope` and no data rows.
  - AC-005.2: IF the caller does not have the Project Manager role, THEN THE system SHALL reject the download with HTTP 403.

### STORY-006: Clean up abandoned previews
- As the system, I want abandoned previews deleted so that the database does not grow with unused uploads.
- Acceptance:
  - AC-006.1: WHEN the daily cleanup job runs, THE system SHALL delete every unconfirmed preview created more than 24 hours earlier, with its rows.
  - AC-006.2: WHILE a preview is younger than 24 hours, THE system SHALL keep it.
  - AC-006.3: IF deleting one preview fails, THEN THE system SHALL log the error and continue deleting the other expired previews.

### STORY-007: Read an existing preview
- As a Project Manager, I want to read my preview again so that the Import Preview screen still shows its Summary after a reload.
- Acceptance:
  - AC-007.1: WHEN a Project Manager reads their unconfirmed preview, THE system SHALL return its Project, file name, Valid, Error and Removed counts, MSG30 when at least one row is Error, and the first page of its rows, as in AC-001.1.
  - AC-007.2: IF the preview belongs to another user, has been confirmed, or no longer exists, THEN THE system SHALL reject the read with HTTP 404.
  - AC-007.3: IF the caller no longer manages the preview's Project or the Project is no longer Active, THEN THE system SHALL reject the read with HTTP 403 (MSG08).

### STORY-008: Import Tickets screen — CSV tab (web app, III.5.1)
- As a Project Manager, I want a screen to pick my Project and a CSV file so that I can start an import without calling the API.
- Acceptance:
  - AC-008.1: WHEN a Project Manager opens Import Tickets, THE web app SHALL show the tabs Jira, CSV and Manual entry with CSV selected and Manual entry disabled; the Jira tab and the Drafts list behave as `ticket-repository` STORY-001 and STORY-004 say.
  - AC-008.2: THE web app SHALL offer in the Project drop-down only the Projects of the signed-in user whose role in the Project is Project Manager, each shown with its Jira project key and Department name.
  - AC-008.3: WHEN the Project Manager clicks Download template, THE web app SHALL save the file returned by the template endpoint as `ticket-import-template.csv`.
  - AC-008.4: IF the Project Manager clicks Upload without a Project or without a file, THEN THE web app SHALL show MSG01 at each missing field and send nothing.
  - AC-008.5: IF the chosen file name does not end in `.csv` (case-insensitive), THEN THE web app SHALL show MSG27 at the CSV file field and not keep the file; IF the file is larger than 10 MB, THEN it SHALL show MSG28 at the field and not keep the file.
  - AC-008.6: WHEN the upload succeeds, THE web app SHALL open the Import Preview of the new preview (`/import/preview?previewId=<id>`).
  - AC-008.7: IF the upload is rejected, THEN THE web app SHALL show the message the backend returned (MSG27, MSG28, MSG29 with the missing columns, MSG04 or MSG08) and stay on Import Tickets with the chosen Project and file kept.
  - AC-008.8: WHILE the upload is in progress, THE web app SHALL show Upload as loading and not send a second upload.

### STORY-009: Import Preview screen (web app, III.5.2)
- As a Project Manager, I want to review, remove and confirm the rows on screen so that only the tickets I choose are published.
- Acceptance:
  - AC-009.1: WHEN the Import Preview opens with a preview id, THE web app SHALL show a Summary with the file name, the number of Valid, Error and Removed rows, and MSG30 when at least one row is Error.
  - AC-009.2: THE web app SHALL list the rows page by page with the columns: a selection checkbox, Ticket ID, Title, Author, Resolved date, Solution (the sections Symptoms, Root cause, Resolution steps, Workaround and Scope as returned), Result (Valid / Error / Removed) and Error details (each row error as its MSG text, from the message catalog with the returned parameters).
  - AC-009.3: WHEN the Project Manager selects rows and clicks Remove, THE web app SHALL remove those rows, refresh the Summary and the rows, and clear the selection; Remove SHALL be disabled while no row is selected, and Removed rows SHALL not be selectable.
  - AC-009.4: WHEN the Project Manager clicks Preview on a row, THE web app SHALL open the Import Ticket Preview popup of that row.
  - AC-009.5: WHILE the preview has no Valid row, THE web app SHALL disable Confirm.
  - AC-009.6: WHEN Confirm succeeds, THE web app SHALL show MSG32 with the created and skipped counts and open Import Tickets.
  - AC-009.7: IF Confirm is rejected (MSG30, MSG33 or MSG40), THEN THE web app SHALL show the message the backend returned and stay on the Import Preview.
  - AC-009.8: WHILE Confirm is in progress, THE web app SHALL show Confirm as loading and not send a second Confirm.
  - AC-009.9: WHEN the Project Manager clicks Cancel, THE web app SHALL ask MSG37; on confirmation it SHALL open Import Tickets without importing, otherwise stay.
  - AC-009.10: WHEN the Project Manager leaves the Import Preview for another screen of the web app without Confirm or Cancel, THE web app SHALL ask MSG37 and leave only on confirmation.
  - AC-009.11: IF the preview id in the address is missing or is not a positive whole number, THEN THE web app SHALL open Import Tickets.
  - AC-009.12: IF the preview cannot be read (HTTP 404) or the caller may not read it (HTTP 403), THEN THE web app SHALL show the error state with MSG04 or MSG08 instead of the Summary and the rows.
  - AC-009.13: WHILE the Summary or a page of rows is loading, THE web app SHALL show the loading state.

### STORY-010: Import Ticket Preview popup (web app, III.5.3)
- As a Project Manager, I want to open one row in full so that I can read its whole Solution before importing.
- Acceptance:
  - AC-010.1: WHEN the popup opens for a row, except a row that AC-015.2 makes editable, THE web app SHALL show, read-only, its Ticket ID, Title, Description, Author, Resolved date, every Solution section in full (Symptoms, Root cause, Resolution steps, Workaround, Scope), its Result and its Error details.
  - AC-010.2: WHEN the Project Manager clicks Close, THE web app SHALL close the popup and keep the Import Preview as it was.
  - AC-010.3: IF the row cannot be read, THEN THE web app SHALL show the error state inside the popup.

### STORY-011: CSV file picker (web app kit)
- As a developer, I want one CSV file picker in the kit so that every CSV upload checks the same limits.
- Acceptance:
  - AC-011.1: THE `CsvUpload` component SHALL accept one file by clicking or by dragging, show the chosen file's name, and let the user remove it.
  - AC-011.2: IF the dropped or chosen file does not end in `.csv` (case-insensitive) or is larger than 10 MB, THEN `CsvUpload` SHALL not keep it and SHALL report MSG27 or MSG28 to its form field.
  - AC-011.3: THE `CsvUpload` component SHALL never send the file by itself; the form sends it.

### STORY-012: Upload a file of tickets without a Solution
- As a Project Manager, I want to upload a file of tickets that have no Solution yet so that I can bring them into T-Solve and write their Solutions myself.
- Acceptance:
  - AC-012.1: WHEN the upload does not name an import type, THE system SHALL treat the file as a file with Solutions.
  - AC-012.2: WHEN a Project Manager downloads the template of a file without Solutions, THE system SHALL return a UTF-8 CSV file whose header row is `ticket_id,title,description,author,resolved_date` and no data rows.
  - AC-012.3: IF the header row of a file without Solutions lacks `ticket_id` or `title`, THEN THE system SHALL reject the file with MSG29 listing every missing column and create no preview.
  - AC-012.4: WHEN a file without Solutions has Solution columns, THE system SHALL ignore those columns.
  - AC-012.5: WHEN a preview of a file without Solutions is created, THE system SHALL mark a row Needs Solution when every rule in AC-012.6 and AC-002.3, AC-002.5, AC-002.6 and AC-002.7 passes, and Error otherwise, listing every failed rule of that row.
  - AC-012.6: IF a row of a file without Solutions has an empty `ticket_id` or `title` (blank after trimming), THEN THE system SHALL mark the row Error with MSG01 for each empty field.
  - AC-012.7: THE system SHALL return the import type and the number of Needs Solution rows with every `ImportPreviewResponse`, and SHALL accept Needs Solution as a result filter when listing rows (AC-002.11).

### STORY-013: Write the Solution of rows on the preview
- As a Project Manager, I want to write one Solution for one or several rows so that the rows can be imported.
- Acceptance:
  - AC-013.1: WHEN the Project Manager saves a Solution for rows of a preview of a file without Solutions, THE system SHALL store a separate copy of the sections, with surrounding spaces trimmed, in each row and mark each row Valid.
  - AC-013.2: IF the saved Solution has an empty `symptoms`, `root_cause`, `resolution_steps` or `scope`, or its five sections together exceed 10,000 characters, THEN THE system SHALL reject the request with HTTP 400 and MSG01 for each empty section or MSG02 for the Solution, and change no row.
  - AC-013.3: IF the preview is a file with Solutions, or any requested row is Error or Removed, THEN THE system SHALL reject the request with HTTP 409 (MSG06) and change no row.
  - AC-013.4: IF the preview belongs to another user, has been confirmed, or no longer exists, or a requested row number does not exist in it, THEN THE system SHALL reject the request with HTTP 404 and change no row.
  - AC-013.5: IF the caller no longer manages the preview's Project or the Project is no longer Active, THEN THE system SHALL reject the request with HTTP 403 (MSG08) and change no row.
  - AC-013.6: WHEN a Solution is saved for a Valid row, THE system SHALL replace the row's Solution.
  - AC-013.7: WHEN a Project Manager reads the preview again, THE system SHALL return the Solutions saved so far.

### STORY-014: Confirm a preview of a file without Solutions
- As a Project Manager, I want Confirm blocked until every row is ready so that no ticket is left out without my knowing.
- Acceptance:
  - AC-014.1: IF a preview of a file without Solutions has a Needs Solution or Error row, THEN THE system SHALL reject Confirm with HTTP 409 and `MSG_NEW_4` with the number of those rows, import nothing and record no Import Batch.
  - AC-014.2: WHEN the Project Manager confirms a preview of a file without Solutions whose rows are all Valid or Removed, with at least one Valid, THE system SHALL save the Valid rows with their saved Solutions as AC-004.1 to AC-004.4 and AC-004.10 say.

### STORY-015: Import type and Solution writing on screen (web app)
- As a Project Manager, I want to choose the import type and write Solutions on screen so that I do not need a second tool.
- Acceptance:
  - AC-015.1: WHEN a Project Manager opens the CSV tab, THE web app SHALL show an Import type choice of "Tickets with a Solution" (selected) and "Tickets without a Solution", SHALL download the template of the chosen type, and SHALL send the chosen type with the upload.
  - AC-015.2: WHEN the popup opens for a Needs Solution or Valid row of a preview of a file without Solutions, THE web app SHALL show the Solution sections as editable fields with Apply; WHEN Apply succeeds, it SHALL close the popup and refresh the Summary and the rows.
  - AC-015.3: IF Apply is rejected, THEN THE web app SHALL show the message the backend returned at its field and keep the popup open with what was written.
  - AC-015.4: WHILE a preview of a file without Solutions has a selected row and no selected Error or Removed row, THE web app SHALL enable Fill solution; otherwise it SHALL disable Fill solution. Previews of a file with Solutions SHALL not show it.
  - AC-015.5: WHEN the Project Manager clicks Fill solution, THE web app SHALL open the popup with empty Solution sections for the selected rows; WHEN some selected rows already have a Solution, it SHALL ask MSG36 with their number before saving, and on refusal save nothing and keep the popup open.
  - AC-015.6: WHILE a preview of a file without Solutions has a Needs Solution or Error row, THE web app SHALL disable Confirm and show `MSG_NEW_4` with the number of those rows.
  - AC-015.7: THE web app SHALL show the number of Needs Solution rows in the Summary, and Needs Solution as a Result, for a preview of a file without Solutions.

### STORY-016: Keep the file-with-Solutions import unchanged
- As a Project Manager, I want the import of a file with Solutions to work as before so that existing files and steps stay valid.
- Acceptance:
  - AC-016.1: WHILE a preview is of a file with Solutions, THE system SHALL keep its rows read-only and SHALL let Confirm skip Error rows, as STORY-002 to STORY-004 say.

## Out of Scope
- Save as Draft / reopen / delete Draft on the Import Preview (GBR-26, UC-21 step 7b) — feature "Import Drafts". Until then, preview cleanup (STORY-006) treats every preview as non-Draft.
- Import History screen (UC-23) — this spec only records the Import Batch.
- Jira import (UC-20) and manual entry with Evidence (UC-22).
- Editing a row of a file with Solutions on screen (UC-21 step 6: read-only; the PM fixes the file and uploads again), and editing any field other than the Solution of a row of a file without Solutions.
- Keeping written Solutions past the preview's life: a new upload (AC-001.11) and the 24-hour cleanup (AC-006.1) delete them, and writing a Solution does not extend it.
- A Ticket without a Solution, and any new Ticket status (D15).
- Search indexing of new tickets — the search module consumes the "tickets published" event.
- Auto-detecting `;` as a delimiter.
- The real `project` table, Department and Google OIDC sign-in (workspace / identity modules).
- On the web screens: the Manual entry tab contents, "Save as draft" of a CSV preview (the Jira tab and the Drafts list are `ticket-repository`), the Import History screen (5.4), a result filter on the Import Preview, and asking MSG37 when the browser tab is closed or reloaded (AC-009.10 covers moves inside the web app only).

## Interface Contracts

### Exposed by this module
All under `/api/v1`, role Project Manager, errors as RFC 9457 `ProblemDetail` with `code` = MSGxx. The exact schema goes into `frontend/openapi/tsolve-api.yaml` first.

| Method + path | Request | Response |
|---|---|---|
| `GET /imports/csv/template` | `importType`? (`WITH_SOLUTION` default, `WITHOUT_SOLUTION`) | `text/csv` template (AC-005.1, AC-012.2) |
| `POST /imports/csv` | multipart: `file`, `projectId` (UUID), `importType`? (`WITH_SOLUTION` default, `WITHOUT_SOLUTION`) | `ImportPreviewResponse` (previewId, projectId (UUID), fileName, importType, validCount, errorCount, removedCount, needsSolutionCount, message MSG30?, first page of rows) |
| `GET /imports/previews/{previewId}` | — | `ImportPreviewResponse` (AC-007.1) |
| `GET /imports/previews/{previewId}/rows` | `page`, `size` (≤ 100), `status`? (Valid, Error, Removed, Needs Solution) | paged `ImportPreviewRowSummary` (Solution sections cut to 200 characters) |
| `GET /imports/previews/{previewId}/rows/{rowNo}` | — | `ImportPreviewRowResponse` (full row, read-only) |
| `POST /imports/previews/{previewId}/remove-rows` | `{ rowNumbers: [] }` | `ImportPreviewResponse` |
| `POST /imports/previews/{previewId}/fill-solution` | `{ rowNumbers: [], symptoms, rootCause, resolutionSteps, workaround?, scope }` | `ImportPreviewResponse` (STORY-013; one row or several) |
| `POST /imports/previews/{previewId}/confirm` | — | `ImportResultResponse` (importBatchId, created, skipped, message MSG32) |
| Event `TicketsPublishedEvent` | in-process, after commit | `{ ticketIds: [] }` |

Rejections: MSG27, MSG29, MSG04 → 400 · MSG28 → 413 · no PM role or Project not managed → 403 (MSG08) · preview or row not found, or the preview already confirmed → 404 (MSG04) · no Valid row left → 409 (MSG30) · rows not ready on a file without Solutions → 409 (`MSG_NEW_4`) · Fill solution that cannot apply → 409 (MSG06) · MSG40 → 409 · MSG33 because a Ticket ID was taken → 409, because saving failed → 500. Only the two MSG33 cases record a Failed Import Batch.

### Required from other modules
| Need | Interface (in `vn.tsolve.ticket.service`) | Until the module exists |
|---|---|---|
| Current user id and role | Spring Security `Authentication` | `@WithMockUser` in tests |
| Projects the user manages; whether a Project is Active | `ProjectAccess` | Stub implementation reading a seeded list |

## New messages (to add to SRS V.2)
| Code | Where | Text |
|---|---|---|
| `MSG_NEW_3` | Inline (error row in the preview) | *Ticket ID [ticket_id] appears more than once in this file.* (placeholder written as in SRS V.2 with braces; provisional key until System_Messages numbers it, D14) |
| `MSG_NEW_4` | Inline (Import Preview of a file without Solutions) | *[count] row(s) still need a Solution or have errors. Write their Solutions or remove them before you confirm.* (placeholder written as in SRS V.2 with braces; provisional key until System_Messages numbers it, D20) |

## Definition of Done
- [ ] Every acceptance criterion has a test whose name starts with its AC id
- [ ] `mvn verify` and `mvn spotless:check` pass
- [ ] `frontend/openapi/tsolve-api.yaml` updated before the controller code
- [ ] Liquibase changeset added; no applied changeset edited
- [ ] The SRS owner is told about D1 (GBR-33 fix), `MSG_NEW_3` and MSG39 used for the resolved date (D14), and about the new import type: UC-21, the CSV sentence of the business rules ("each row must contain the required Solution sections") and `MSG_NEW_4` (D15–D20)
- [ ] Web app: `npm run format:check`, `lint`, `typecheck`, `test` and `build` pass, and `npm run generate:api` leaves no diff in `src/shared/api/generated`
- [ ] The pull request asks the FE Lead to review `src/shared/ui/CsvUpload` (D12) and `src/shared/ui/DetailModal` (design D15)

<!-- ACCEPTANCE CRITERIA: one testable statement per item, id AC-<story>.<n> (never reuse a
     removed id), in EARS form: THE <system> SHALL <response>, optionally led by
     WHEN <trigger>, WHILE <state>, IF <unwanted condition> THEN, or WHERE <feature is included>.
     A story with a WHEN criterion also gets an IF … THEN criterion for its failure path.
     OUT OF SCOPE: name what is excluded, so it is not built by accident; adding it later is a
     spec change (Changelog row, re-approval).
     OPEN QUESTIONS: where the answer is missing, write [NEEDS CLARIFICATION: <question>] and ask
     the user — never fill the gap with a guess. `bun harnessclaude/hooks/sdd.ts check <module>`
     fails while any remain.
     SPEC EDIT RULE: Any edit to this file that changes business logic, requirements, stories, or
     acceptance criteria MUST add a new row to the Changelog table above (Date, Story if
     applicable, Change summary, Reason). Once the user has approved the spec, any edit locks the
     module's code until they approve the new version (`sdd.ts approve <module>`). -->
