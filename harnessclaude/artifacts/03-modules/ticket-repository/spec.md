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
GBR-08, GBR-10, GBR-11, GBR-23, UC-25), and **KAN-133 Create a ticket with its Solution manually** (STORY-006,
UC-22, screen 5.1 Manual entry tab, MSG31, MSG34, MSG41, MSG72, with Evidence through the Workspace file storage of
`platform` STORY-007, KAN-96), and **KAN-134 View import history**
(STORY-007, UC-23, screen 5.4, MSG04, MSG08, MSG39) and **KAN-135 View ticket detail** (STORY-008, UC-24, GBR-02,
screen 5.5, MSG04, MSG06, MSG08) and **KAN-136 Unpublish / re-publish a
ticket** (STORY-009, UC-25, GBR-11, screen 5.5 Ticket Detail, MSG21–MSG24) and **KAN-137 Unify the ticket model in
code** (STORY-010: one `ticket_decision` table for import and review decisions, and the unused Solution tables of
changeset 001 removed).
CSV import (UC-21) lives in `import-ticket-list`; the Solution Form submission (UC-19) is added later through
Change Mode.

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
| 2026-11-14 | STORY-006 | New STORY-006 (KAN-133, UC-22 manual entry): AC-006.1–AC-006.14, decisions D12–D16; manual entry leaves Out of Scope, Evidence stays out (D13) | KAN-133; user decisions 2026-11-14 |
| 2026-11-14 | STORY-006 | Evidence of manual entry: new AC-006.15–AC-006.22, decisions D17–D21; D13 reworded (Evidence no longer deferred); the save and Save as draft requests become multipart (Interface Contracts); Evidence leaves Out of Scope | File storage (KAN-96, #193) is on `main`; the user asked for Evidence on 2026-11-14 |
| 2026-11-14 | STORY-007, STORY-008 | New STORY-007 (KAN-134, UC-23 Import History): AC-007.1–AC-007.10; new STORY-008 (KAN-135, UC-24 Ticket Detail): AC-008.1–AC-008.10; decisions D22–D32; Import History and Ticket Detail leave Out of Scope, their excluded parts are listed there; Interface Contracts gain `GET /imports/history` and the extended `GET /tickets/{ticketId}` | KAN-134, KAN-135; user decisions 2026-11-14 |
| 2026-11-15 | STORY-009 | New STORY-009 (KAN-136, UC-25 unpublish / re-publish): AC-009.1–AC-009.10, decisions D33–D37; unpublish / re-publish leaves Out of Scope | KAN-136; user decisions 2026-11-15 |
| 2026-11-15 | STORY-010 | New STORY-010 (KAN-137): AC-010.1–AC-010.7, decisions D38–D43; "Required from other modules" names `ticket_decision` | KAN-137: two decision tables and the unused Solution tables of changeset 001 are left from before the ticket model was one; user decisions 2026-11-15 (planned as STORY-007 / D17–D22 in PR #246, renumbered after KAN-134–KAN-136 took those numbers) |
| 2026-11-16 | STORY-001 | KAN-345: D2 and AC-001.12 reworded, new AC-001.14 — a pull takes the Workspace's Jira service account from `workspace` (`JiraServiceAccount.current()`: site URL, cloud id, scoped token; KAN-344) for every pull, instead of the environment (`JIRA_SITE_URL`, `JIRA_CLOUD_ID`, `JIRA_API_TOKEN` removed); Out of Scope and Required from other modules follow | BUG-308: on staging the env was empty, so every pull answered MSG50 although the Admin had entered the service account; user decision 16/11 (one account per Workspace, KAN-344 part 1) |
| 2026-11-17 | STORY-001 | KAN-349: D2 notes that the account behind `JiraServiceAccount` is now the T-Solve Jira App's app token; no acceptance criterion changes | RESEARCH-005, user decision 17/11 |

## Decisions taken while planning (2026-11-07, with the user)
| # | Question | Decision |
|---|----------|----------|
| D1 | Scope of this plan | Only UC-20 (KAN-45). UC-19, UC-22–25 come later through Change Mode. |
| D2 | How T-Solve reads Jira | The Workspace Jira service account (KAN-45 update of 01/11, DB tables doc 31/10), not a per-Project token (SRS GBR-14 is outdated). The import calls a `JiraIssueSource` interface. The credential is a scoped API token (the only kind a service account has), sent as a Bearer token to the Atlassian API gateway `https://api.atlassian.com/ex/jira/{cloudId}`; no email is used. The account is the Workspace's, read from `workspace` for each pull (`JiraServiceAccount.current()`: the site URL for the issue links, the site's cloud id and the token — since KAN-349 the T-Solve Jira App's app token, no customer service account; KAN-344, KAN-345); nothing comes from the environment. Classic unscoped tokens with basic auth are not supported (user decision 2026-11-07, RESEARCH-003). |
| D3 | Drafts | Included. A Draft is an Import Preview marked as a Draft; the daily cleanup skips Drafts. |
| D4 | Writer of an imported ticket | The Jira assignee's display name at pull time; empty when the issue is unassigned (no Jira ↔ T-Solve account linking yet). |
| D5 | Comments shown in the popup | Every comment of the issue at pull time, as plain text with author and date. The "mapped resolution comments field" of `project-integration` is not used yet. |
| D6 | Requirements shared with CSV import | Required Solution sections, text limits, preview storage and the "one unconfirmed preview per PM" rule are those of `import-ticket-list` (D1, D7, D8, D9 there). |
| D7 | Separate Jira preview or the CSV one | The CSV Import Preview of PR #110: one row per issue (Ticket ID = Jira key, result Needs Solution until it has a Solution), with two new row fields, the issue's link and its comments. Rows list, row detail, Fill solution, remove-rows, Confirm and the screens are reused; only the pull and Drafts are new endpoints. |
| D8 | MSG35's text in Report3 names how many more issues match, but the pull stops at 100 kept issues or 20 Jira pages, and Jira's `search/jql` gives no total | MSG35 names no count. Its Report3 text drops the count parameter (Report3 change by the team; then `report3-messages.json` and the web app's catalog follow). The pull does not count the remaining issues. |
| D9 | Which status changes are allowed (KAN-132) | The table of AC-005.1, taken from SRS v3460 GBR-08, GBR-10, GBR-11, GBR-23, UC-19, UC-25 and UC-30. Rejected is final; Unpublished only goes back to Published (user decision 2026-11-13). |
| D10 | Where the "tickets published / unpublished" events are published | The rule lives in `TicketStatus` and `Ticket`; one shared transition step moves a batch of Tickets and publishes ONE event with all their ids, as Finish already does (AC-003.9). Not one Spring `@DomainEvents` event per Ticket: it needs `save()`, which review does not call, and it would split a 100-ticket import into 100 events (user decision 2026-11-13). |
| D11 | Answer to a status change the table does not allow | HTTP 409 with MSG06, no Ticket of the request changed — the answer review already gives for a Ticket that is not Pending review (UC-30 step 3). |
| D12 | Scope of KAN-133 | The manual entry endpoints, their Drafts, and the Manual entry tab of screen 5.1 (user decision 2026-11-14). |
| D13 | Evidence of a manual ticket (UC-22 step 3, MSG34, MSG72) | First deferred while file storage did not exist; added by the Change of 2026-11-14 (AC-006.15–AC-006.22) once KAN-96 was on `main` (user decision 2026-11-14). |
| D17 | How files reach the server | In the same request as the entry: Save and Save as draft are `multipart/form-data` with the entry as JSON and 0–5 files, so a ticket and its Evidence are saved together. Files go to `platform` `FileStorage` before the database transaction and are deleted again if it fails (no outbound call inside a transaction, backend AGENTS.md §4). |
| D18 | File rules on Save as draft | The file rules (MSG34, MSG72) apply on Save as draft too, although other fields are not checked: a file must be stored to be kept, and the storage limit is the same (decided 2026-11-14). |
| D19 | Allowed types | By file extension, case-insensitive: .png, .jpg, .jpeg, .pdf, .docx, .xlsx, .txt, .log (MSG34's list; .jpeg counts as JPG). |
| D20 | Evidence of a Draft | Kept with the Draft (one record per file with its storage key); listed when the Draft is reopened; removed by leaving it out of the kept list on the next save. On Save they become the new Ticket's Evidence, which no endpoint can remove ("locks the Evidence"). |
| D21 | File storage not configured or failing | A request with files is rejected with HTTP 503 and MSG06 and saves nothing; a request without files works as before. |
| D14 | Where a manual entry Draft is stored | As an Import Preview of source Manual with one row holding the entered fields, marked a Draft when it is created. The Drafts list and Delete of STORY-004 serve it as they are (user decision 2026-11-14). |
| D15 | Writer of a manual ticket ("writer = Project Manager") | `writer` = the caller's full name, `writer_id` = the caller's user id (user decision 2026-11-14). |
| D16 | Message for a Resolved date in the future (screen 5.1 names none) | MSG39 (user decision 2026-11-14). |
| D22 | Who sees Import History and which batches (KAN-134) | Only a Project Manager. The list holds the Import Batches of the Projects where the caller is `PROJECT_MANAGER` in the table `project_membership` (the real table, not `StubProjectAccess`), newest first (`created_at` DESC), page by page as `GET /api/v1/tickets` (page from 0, size 1–100, default 20). No PM role → 403 (MSG08) by the role guard the import endpoints already use (user decision 2026-11-14). |
| D23 | Filters and columns of Import History | Filters: Project, source (JIRA_IMPORT / CSV / MANUAL), date range on `created_at`. Columns: date, Project Manager, Project, Department, source, date range (Jira) or file name (CSV), published count, removed / error count, status (COMPLETED / FAILED), error. No result → MSG04 "No results found." (user decision 2026-11-14). |
| D24 | Name in the Project Manager column (`import_batch.performed_by` is a string) | When `performed_by` is a UUID that is the id of an `app_user`, that user's full name; otherwise the stored value as it is (user decision 2026-11-14). |
| D25 | Date range of a Jira import in history (`import_batch` has no such column) | A new Liquibase changeset adds `date_from` and `date_to` (DATE, nullable) to `import_batch`. Jira Finish and a failed Jira pull or Finish store the pull's Resolved from / Resolved to; other sources leave them empty. No backfill of older batches. Changeset number 032: 029–031 are taken by open branches, 030 is the highest on `main` (user decision 2026-11-14). |
| D26 | Error column of Import History | Only the MSG code stored in `import_batch.error` (e.g. MSG50), never the rest of the stored text and no per-row error details (user decision 2026-11-14). |
| D27 | Import History endpoint and screen | `GET /api/v1/imports/history` next to the other `/api/v1/imports` endpoints; the web app replaces the placeholder `ImportHistoryPage` with the table, through the generated API client (user decision 2026-11-14). |
| D28 | Who may open a ticket's detail (KAN-135, UC-24) | GBR-02 only: a Ticket Resolver or Project Manager. A Published ticket → every Ticket Resolver / Project Manager who can access the ticket's Department (the Project's `department_id`, checked by `identity` `ScopeAccessService.canAccessDepartment`, KAN-118). Not Published → only the Solution writer (`writer_id`) and the Project Manager of the ticket's Project (`PROJECT_MANAGER` in `project_membership`). A ticket whose Department is deleted or not Active → nobody (user decision 2026-11-14). |
| D29 | Where Ticket Detail is served | Extend the existing `GET /api/v1/tickets/{ticketId}` (operationId `getReviewTicketDetail`: `review/controller/ReviewReadController`, `ticket/service/impl/TicketReviewReadServiceImpl.detail`, `TicketReviewView.Detail`) with `department {id, name}`, `resolvedDate` and `sourceLink`; no new endpoint. `canDecide` and the review decision buttons (KAN-24) stay as they are. These files are in the `paths` of module `ticket-review` (owner Phạm Tùng Dương); the user decided to extend them anyway (user decision 2026-11-14). |
| D30 | Source link of a ticket | Map the existing column `ticket.source_link`; Jira Finish copies the preview row's link to the issue into it. Tickets imported before this change keep no link (no backfill) (user decision 2026-11-14). |
| D31 | Answers of Ticket Detail | Not permitted → 403 (MSG08) "You do not have permission to access this page.", shown inline; no ticket with that id → 404 (MSG04) as today; any other load failure → MSG06 toast. The 403 replaces the 404 the endpoint gave for a ticket the caller may not see; the mock-API 404 of `ticket-review` AC-006.6 no longer matches the real API (user decision 2026-11-14). |
| D32 | UC-24 lets an Employee, Admin or Knowledge Manager open a ticket from a Chatbot citation; GBR-02 limits tickets to Ticket Resolvers and Project Managers | GBR-02 wins: only Ticket Resolvers and Project Managers. Access through a Chatbot citation is out of scope (user decision 2026-11-14). |
| D33 | Scope of KAN-136 | The unpublish and re-publish endpoints and the Unpublish / Re-publish buttons of Ticket Detail (screen 5.5) (user decision 2026-11-15). |
| D34 | "Removed from the index" (KAN-136) | The ticket's vector is kept. Search, AI Solution Suggestion and the Knowledge Chatbot already return only Published tickets (`search` AC-007.1, AC-010.3, AC-011.3), so an Unpublished ticket leaves them at once and a re-published one is back at once. Nothing changes in `search` (user decision 2026-11-15). |
| D35 | Review History and audit log (UC-25 postcondition) | This module stores one review decision (UNPUBLISH or REPUBLISH) and one audit entry per ticket. Showing these decisions in the Review History list is a Change of `ticket-review` (user decision 2026-11-15). |
| D36 | Where the endpoints live | `POST /api/v1/reviews/unpublish` and `POST /api/v1/reviews/republish`, next to send-back in `ReviewDecisionController`, with the same request (`ticketIds`, 1–500) and answer (`count`); the rule lives in `TicketDecisionService` of this module. The managed Projects of the caller come from `review`, as for every review decision (user decision 2026-11-15). |
| D37 | Tickets of a Removed Project (GBR-21) | Not supported yet: "manages the Project" means what it means for Approve, Reject and Send back today — Active Projects only — so such a request answers 403 (MSG08). GBR-21 for Removed Projects is fixed once for all decisions in `ticket-review` (user decision 2026-11-15). |
| D38 | Scope of KAN-137 (written 25/10; the `ticket` table is already one) | One `ticket_decision` table replaces `ticket_import_decision` and `review_decision`; the unused tables of changeset 001 and the `knowledge` package go. The Solution stays in the columns of `ticket` (no `ticket_solution`), `evidence` keeps its name, and no `notification` table is added: each would change `search`, `chatbot`, `triage`, `ticket-review` and `dashboard-audit` without changing any behavior (user decision 2026-11-15). |
| D39 | Do the approve decisions of imports show in Review History once the tables are one | No. Review History, the writer's feedback, the writer's email and the dashboard figures stay as they are: only decisions on Solution Form Tickets (user decision 2026-11-15). |
| D40 | Who made a decision: review stores a user id, import stores the sign-in name | `ticket_decision` keeps both: `reviewer_id` (user id, review decisions) and `reviewer` (sign-in name, import decisions); a row has at least one of them. No row loses its reviewer in the move (user decision 2026-11-15). |
| D41 | "Renumber changesets 002 / 003" of KAN-137 | Not done. An applied changeset is never edited (`backend/AGENTS.md` §7); the change is one new changeset after the latest one. |
| D42 | `reuse_record` (changeset 001; not named in KAN-137) | Dropped with the others: it has a foreign key to `solution` and no code reads or writes it. |
| D43 | How a read tells a review decision from an import decision in the one table | By the Ticket's source: a review decision is a decision on a Ticket of source Solution Form (an imported Ticket is never reviewed, AC-005.2). No extra column. Unpublish / re-publish decisions (D35) are not read by any of these reads today (user decision 2026-11-15; re-checked against KAN-136 on 2026-11-15). |

## Requirements

### Functional
1. A Project Manager selects one of their Active Projects and a resolved date range, and pulls the resolved Jira issues of that Project's Jira project that have no Ticket yet: at most 100, oldest first (GBR-15).
2. On the Import Preview the Project Manager reads each issue (summary, description, comments, assignee, resolved date, link to Jira), writes its Solution one issue at a time or for several issues at once, and removes issues they do not want.
3. Finish (Confirm of a Jira preview) saves every remaining issue as a Published Ticket with an approve decision by the Project Manager (GBR-23), skips issues that got a Ticket in the meantime, and records exactly one Import Batch.
4. The Project Manager can save the import as a Draft, reopen it later without pulling from Jira again, and delete it (GBR-26).
5. A Project Manager enters one resolved ticket with its Solution by hand into one of their Active Projects; it is Published at once with an approve decision by them (UC-22, GBR-23), and the entry can be saved as a Draft first (GBR-26).
6. A Project Manager reads the history of the imports of the Projects they manage, newest first, filtered by Project, source and date (UC-23).
7. A Ticket Resolver or Project Manager opens the detail of a ticket they may see under GBR-02, with its Department, resolved date and link to its source (UC-24).
8. A Project Manager takes a Published ticket of a Project they manage out of use (Unpublish) and brings an Unpublished one back (Re-publish) from the Ticket Detail; no new review happens (UC-25, GBR-11).
9. Every decision on a Ticket — the approve decision of an import and each review decision — is kept in one place, and the database holds no table that nothing uses (KAN-137).

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
  - AC-001.12: WHEN the system calls Jira for a pull, THE system SHALL take the bound Workspace's service account from `JiraServiceAccount.current()` at the start of that pull, send its token as `Authorization: Bearer` to `https://api.atlassian.com/ex/jira/{cloudId}` with its cloud id, and build each row's link to the issue as its site URL + `/browse/{key}`.
  - AC-001.14: IF `JiraServiceAccount.current()` returns no account (the Workspace is not set up, has no cloud id yet, or its token cannot be decrypted), THEN THE system SHALL make no Jira call and answer as in AC-001.7 for a connection that is not configured: MSG50, no preview, one Failed Import Batch of source Jira with 0 published.
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
- As a Project Manager, I want to enter a resolved ticket with its Solution by hand so that knowledge that is not in Jira can also be reused.
- Acceptance:
  - AC-006.1: WHEN a Project Manager saves a manual entry with valid fields for an Active Project they manage, THE system SHALL, in one transaction, save a Ticket of that Project with the entered Ticket ID, title, description, resolved date and Solution, source Manual, status Published, writer = the caller's full name and writer id = the caller's user id (D15); record an approve decision by the caller; record one Import Batch of source Manual with status Completed, 1 published, 0 skipped and 0 removed; and return MSG32 (1 published, 0 skipped).
  - AC-006.2: WHEN a manual entry is saved, THE system SHALL publish one "tickets published" event with the new Ticket's id, for listeners to handle after the transaction commits.
  - AC-006.3: IF Project, Ticket ID, Title, Description, Symptoms, Root cause, Resolution steps or Scope is missing or blank after trimming, THEN THE system SHALL reject the save with HTTP 400 and MSG01 for each missing field and save nothing.
  - AC-006.4: IF the Ticket ID is longer than 50 characters, the Title longer than 200, the Description longer than 10,000, or the Solution sections longer than 10,000 characters in total, THEN THE system SHALL reject the save with HTTP 400 and MSG02 for each such field with its limit, and save nothing.
  - AC-006.5: IF the Resolved date is after today (Vietnam time), THEN THE system SHALL reject the save with HTTP 400 and MSG39 for that field and save nothing (D16). A missing Resolved date is allowed.
  - AC-006.6: WHEN a save is rejected for invalid fields, THE system SHALL report every invalid field of the request in the one answer, each with its message.
  - AC-006.7: IF the Ticket ID (after trimming) is already a Ticket ID, including when another request takes it while this one is saved, THEN THE system SHALL reject the save with HTTP 409 and MSG31 with that Ticket ID, save nothing and record no Import Batch.
  - AC-006.8: IF the caller does not have the Project Manager role, or the selected Project is not Active or not managed by the caller, THEN THE system SHALL reject the save or the Save as draft with HTTP 403 (MSG08) and save nothing.
  - AC-006.9: WHEN the Project Manager clicks Save as draft on the Manual entry tab with a Project they manage, THE system SHALL save the entered fields as a Draft of type Manual with the save time without validating any other field (D14), and return MSG41 with the Draft's id.
  - AC-006.10: WHEN the Project Manager saves as draft an entry reopened from a Draft, THE system SHALL update that same Draft, its fields and its save time instead of creating a second one.
  - AC-006.11: WHEN the Project Manager reopens a Manual Draft from the Drafts list, THE system SHALL return its Project and every field as saved, and THE web app SHALL show them on the Manual entry tab.
  - AC-006.12: WHEN a manual entry reopened from a Draft is saved successfully, THE system SHALL delete that Draft in the same transaction; IF the save is rejected, THEN THE system SHALL keep the Draft unchanged.
  - AC-006.13: IF a request names a Manual Draft that belongs to another user, does not exist or is not a Manual Draft, THEN THE system SHALL reject it with HTTP 404 (MSG04); Delete of a Manual Draft works as AC-004.4 and AC-004.5.
  - AC-006.14: WHEN a save or Save as draft is rejected, THE web app SHALL show each message next to its field and keep everything the Project Manager entered; WHEN a save succeeds, THE web app SHALL show MSG32 and clear the form.
  - AC-006.15: WHEN a manual entry with Evidence files is saved (AC-006.1), THE system SHALL store each file in the Workspace file storage and save it as Evidence of the new Ticket with its file name, type and size, together with the Evidence the reopened Draft kept (D17, D20).
  - AC-006.16: IF an Evidence file's extension is not one of .png, .jpg, .jpeg, .pdf, .docx, .xlsx, .txt or .log, or a file is larger than 10 MB, THEN THE system SHALL reject the save or the Save as draft with HTTP 400 and MSG34 with that file name, store no file and save nothing (D18, D19).
  - AC-006.17: IF a save or Save as draft would leave the entry with more than 5 Evidence files (the Draft's kept files and the new ones together), THEN THE system SHALL reject it with HTTP 400 and MSG72, store no file and save nothing.
  - AC-006.18: WHEN the Project Manager saves as draft with Evidence files, THE system SHALL store them with the Draft, and WHEN the Draft is reopened, THE system SHALL return each of its Evidence files with its id, file name and size (D20).
  - AC-006.19: WHEN a save or Save as draft of a reopened Draft leaves out some of the Draft's Evidence files, THE system SHALL drop them from the Draft and delete their stored files after the change commits; WHEN a Manual Draft is deleted, THE system SHALL delete its stored files as well.
  - AC-006.20: IF a save or Save as draft fails after some of its files were stored, THEN THE system SHALL delete those files and keep the Draft and its Evidence as they were.
  - AC-006.21: IF the file storage is not configured or does not answer, THEN THE system SHALL reject a save or Save as draft that carries files with HTTP 503 and MSG06 and save nothing (D21).
  - AC-006.22: WHILE an entry has Evidence files, THE web app SHALL list them with their names under Evidence, let the Project Manager add files and remove files before saving, refuse a 6th file with MSG72 and a file of another type or larger than 10 MB with MSG34 before sending, and show a reopened Draft's files in the list.

### STORY-007: View import history (KAN-134)
- As a Project Manager, I want to see the history of the imports into my Projects so that I can check what was published, when, by whom and what failed.
- Acceptance:
  - AC-007.1: WHEN a Project Manager requests Import History, THE system SHALL return the Import Batches of every Project where the caller is `PROJECT_MANAGER` in `project_membership`, newest first by creation time, page by page (page from 0, size 1–100, default 20) (D22).
  - AC-007.2: WHEN Import History returns an Import Batch, THE system SHALL include its creation time, Project Manager name (AC-007.3), Project, Department of the Project, source, Jira date range (Resolved from / Resolved to) or file name, published count, removed count, error count, status (Completed or Failed) and error code (AC-007.4) (D23).
  - AC-007.3: WHEN the stored `performed_by` of an Import Batch is the id of an `app_user`, THE system SHALL return that user's full name as the Project Manager; IF it is not a UUID or no `app_user` has that id, THEN THE system SHALL return the stored value unchanged (D24).
  - AC-007.4: WHEN an Import Batch has a stored error, THE system SHALL return only the MSG code it contains (e.g. `MSG50`) as the error, and no error when it contains none (D26).
  - AC-007.5: WHEN the request names a Project, a source (JIRA_IMPORT, CSV or MANUAL), a date from or a date to, THE system SHALL return only the Import Batches that match every given filter; the dates are calendar days in Vietnam time and both are included; a Project the caller does not manage gives an empty page.
  - AC-007.6: IF the date from is after the date to, THEN THE system SHALL reject the request with HTTP 400 and MSG39.
  - AC-007.7: IF the caller does not have the Project Manager role, THEN THE system SHALL reject the request with HTTP 403 (MSG08).
  - AC-007.8: WHEN a Jira Finish succeeds or fails, or a Jira pull fails, THE system SHALL store the pull's Resolved from and Resolved to on the Import Batch it records (D25); Import Batches of other sources SHALL keep no date range.
  - AC-007.9: WHEN the Import History screen loads, THE web app SHALL show one row per Import Batch with the columns of AC-007.2 — the date range for a Jira import and the file name for a CSV import in the same column — and the filters Project (the Projects the user manages), Source and Date range.
  - AC-007.10: IF Import History returns no Import Batch, THEN THE web app SHALL show MSG04 "No results found." instead of the table rows.

### STORY-008: View ticket detail (KAN-135)
- As a Ticket Resolver or Project Manager, I want to open the detail of a ticket I may see so that I can read its Solution with its Department, resolved date and source.
- Acceptance:
  - AC-008.1: WHEN a Ticket Resolver or Project Manager opens a Published ticket and `ScopeAccessService.canAccessDepartment` allows the caller the Department of the ticket's Project, THE system SHALL return the Ticket Detail (D28).
  - AC-008.2: WHEN a ticket is not Published, THE system SHALL return its Ticket Detail only to its Solution writer (`writer_id` = the caller) and to a Project Manager who is `PROJECT_MANAGER` of the ticket's Project in `project_membership` (D28).
  - AC-008.3: IF the ticket's Project or its Department does not exist, or the Department is deleted or not Active, THEN THE system SHALL return the Ticket Detail to nobody and answer HTTP 403 (MSG08) (D28).
  - AC-008.4: IF the caller has neither the Ticket Resolver nor the Project Manager role, or AC-008.1 and AC-008.2 do not allow the caller the ticket, THEN THE system SHALL answer HTTP 403 (MSG08) with no ticket data (D31).
  - AC-008.5: IF no ticket has the requested id, THEN THE system SHALL answer HTTP 404 (MSG04).
  - AC-008.6: WHEN the system returns a Ticket Detail, THE system SHALL include the Department of the ticket's Project (id and name), the ticket's resolved date and its source link, together with the fields and `canDecide` it returned before (D29).
  - AC-008.7: WHEN a Jira Finish saves a Ticket, THE system SHALL store the preview row's link to the Jira issue as the Ticket's source link (D30).
  - AC-008.8: WHEN Ticket Detail shows a ticket, THE web app SHALL show its Department, resolved date and source link; IF the source link is not an `http` or `https` URL, THEN THE web app SHALL show it as plain text, not as a link; an `http`/`https` link SHALL open in a new tab with `rel="noopener noreferrer"`.
  - AC-008.9: WHILE the ticket is Pending review, THE web app SHALL show its review due date; WHILE it is in any other status, THE web app SHALL not show a review due date.
  - AC-008.10: IF Ticket Detail answers HTTP 403, THEN THE web app SHALL show MSG08 inline and no ticket content; IF it fails for another reason than 403 or 404, THEN THE web app SHALL show MSG06 as a toast (D31).

### STORY-009: Unpublish / re-publish a ticket (KAN-136)
- As a Project Manager, I want to take a Published ticket out of use and bring it back later so that a wrong or outdated Solution is not reused.
- Acceptance:
  - AC-009.1: WHEN a Project Manager unpublishes one or more Published Tickets of Projects they manage, THE system SHALL, in one transaction, change each Ticket to Unpublished, store one review decision UNPUBLISH per Ticket with the caller, the time and no reason, record one audit entry of action UNPUBLISH per Ticket, and return the number of Tickets changed.
  - AC-009.2: WHEN a Project Manager re-publishes one or more Unpublished Tickets of Projects they manage, THE system SHALL, in one transaction, change each Ticket to Published without a new review, leaving its Solution, Evidence, feedback and review due date unchanged, store one review decision REPUBLISH per Ticket with the caller, the time and no reason, record one audit entry of action PUBLISH per Ticket, and return the number of Tickets changed.
  - AC-009.3: IF a Ticket of an unpublish request is not Published, or a Ticket of a re-publish request is not Unpublished, THEN THE system SHALL reject the request with HTTP 409 (MSG06) and change no Ticket, store no decision and publish no event (D11).
  - AC-009.4: IF an id of the request is not a Ticket of the caller's Workspace, THEN THE system SHALL reject the request with HTTP 404 (MSG04) and change no Ticket.
  - AC-009.5: IF a Ticket of the request belongs to a Project the caller does not manage (D37), THEN THE system SHALL reject the request with HTTP 403 (MSG08) and change no Ticket.
  - AC-009.6: IF the caller does not have the Project Manager role, THEN THE system SHALL reject the request with HTTP 403.
  - AC-009.7: IF the request names no Ticket, more than 500 Tickets, or the same Ticket twice, THEN THE system SHALL reject it with HTTP 400 and change no Ticket.
  - AC-009.8: WHILE the Ticket Detail shows a Published Ticket of a Project the user is Project Manager of, THE web app SHALL show an Unpublish button; WHEN the user clicks it, THE web app SHALL ask MSG21, and on confirmation send the unpublish request, show MSG22 and show the Ticket as Unpublished; on cancel it SHALL send nothing.
  - AC-009.9: WHILE the Ticket Detail shows an Unpublished Ticket of a Project the user is Project Manager of, THE web app SHALL show a Re-publish button; WHEN the user clicks it, THE web app SHALL ask MSG23, and on confirmation send the re-publish request, show MSG24 and show the Ticket as Published; on cancel it SHALL send nothing.
  - AC-009.10: IF the unpublish or re-publish request is rejected, THEN THE web app SHALL show the message of the error (MSG06 when it names none) and reload the Ticket Detail.

The events of these changes are AC-005.4 and AC-005.5. That an Unpublished ticket is not returned by Search, AI Solution Suggestion and the Knowledge Chatbot, and that a re-published one is, are `search` AC-007.1, AC-010.3 and AC-011.3 (D34).

### STORY-010: One decision table, no unused Solution tables (KAN-137)
- As a developer, I want every decision on a Ticket in one table and the unused tables gone, so that import and review share one ticket model and nobody builds on a table that nothing uses.
- Acceptance:
  - AC-010.1: WHEN the database schema is migrated, THE system SHALL have a `ticket_decision` table and SHALL have no `ticket_import_decision` table and no `review_decision` table.
  - AC-010.2: WHEN the migration runs on a database that holds import decisions and review decisions, THE system SHALL copy every one of them into `ticket_decision` with its Ticket, decision time and feedback text unchanged; a review decision SHALL keep its id, its reviewer's user id and its decision value, and an import decision SHALL keep its reviewer's sign-in name and SHALL have the decision value APPROVED.
  - AC-010.3: WHEN a CSV import, a Jira import or a manual entry publishes a Ticket, THE system SHALL record its approve decision (AC-003.1, AC-006.1) as one row of `ticket_decision` with the decision value APPROVED and the caller's sign-in name as reviewer.
  - AC-010.4: WHEN a review Approve, Reject, Request changes or Send back, or an Unpublish or Re-publish (STORY-009), is saved, THE system SHALL record it as one row of `ticket_decision` per Ticket with the reviewer's user id.
  - AC-010.5: WHEN Review History, the feedback shown to the writer, the writer's review email or the dashboard's outcome figures are read, THE system SHALL use only the decisions on Tickets of source Solution Form, so that the approve decision of an import is listed in none of them and an imported Ticket is counted once in the dashboard figures (D39, D43).
  - AC-010.6: IF a decision is saved with neither a reviewer user id nor a reviewer sign-in name, or with a decision value other than APPROVED, REJECTED, CHANGES_REQUESTED, SEND_BACK, UNPUBLISH or REPUBLISH, THEN THE database SHALL reject the row.
  - AC-010.7: WHEN the database schema is migrated, THE system SHALL have no `solution`, `solution_source`, `solution_approval`, `resolved_ticket_snapshot` or `reuse_record` table, and THE system SHALL expose no endpoint under `/api/solutions`.

## Out of Scope
- CSV import (UC-21) — `import-ticket-list`; Drafts of a CSV import (GBR-26, UC-21 step 7b) are a later Change there.
- Downloading or previewing Evidence (Ticket Detail, UC-24).
- Solution Form submission (UC-19). (Manual entry, UC-22, is STORY-006 with its Evidence; Import History, UC-23,
  is STORY-007; Ticket Detail, UC-24, is STORY-008; unpublish / re-publish, UC-25, is STORY-009.)
- Import History: per-row error details of an import (D26); a date range for Import Batches recorded before D25.
- Ticket Detail: the Feedback summary and Give feedback; counting dashboard views; the Send
  back button (UC-30; the Unpublish and Re-publish buttons are STORY-009); access by an Employee, Admin or Knowledge
  Manager through a Chatbot citation (D32); a source link for tickets imported before D30 (no backfill).
- Storing the Jira service account in the database and the UC-10 screen — `workspace` (KAN-344); this module only reads it (D2).
- The Jira field mapping and "resolution comments field" of a Project — `project-integration` (D5).
- Linking a Jira assignee to a T-Solve user (D4).
- Evidence and attachments of Jira issues.
- Search indexing of new tickets — `search` consumes the "tickets published" event. An Unpublished ticket leaves search, AI Solution Suggestion and the chatbot through their Published filter (D34); deleting its vector on the "tickets unpublished" event is not done.
- The Solution Form submission (UC-19): STORY-005 gives it the transition step and the events only.
- Unpublish / re-publish of several tickets at once from a list, and a reason for the decision: UC-25 has neither.
- Unpublish / re-publish of the tickets of a Removed Project (GBR-21, D37) and showing these decisions in the Review History list (D35) — `ticket-review`.
- From KAN-137 as written on 25/10 (D38, D41): a separate `ticket_solution` table, renaming `evidence` to `ticket_evidence`, a `notification` table, and renumbering applied changesets.

## Interface Contracts

### Exposed by this module
All under `/api/v1`, role Project Manager, errors as RFC 9457 `ProblemDetail` with `code` = MSGxx. The exact schema goes into `frontend/openapi/tsolve-api.yaml` first.

New endpoints:

| Method + path | Request | Response |
|---|---|---|
| `POST /imports/jira` | `{ projectId (uuid), resolvedFrom, resolvedTo }` (ISO dates) | `ImportPreviewResponse` (source JIRA_IMPORT, message MSG35?) |
| `POST /imports/previews/{previewId}/draft` | — | `{ previewId, savedAt, message MSG41 }` |
| `GET /imports/drafts` | — | list of `ImportDraftSummary` (previewId, source, projectId, rowCount, savedAt) |
| `DELETE /imports/drafts/{previewId}` | — | 204 (also a Manual Draft, AC-006.13) |
| `POST /imports/manual` | `ManualTicketRequest { projectId (uuid), ticketId, title, description, resolvedDate?, symptoms, rootCause, resolutionSteps, workaround?, scope, draftId? }` | 201 `ImportResultResponse` (MSG32) |
| `POST /imports/manual/drafts` | `ManualTicketRequest` (only `projectId` required; `draftId` updates that Draft) | `DraftSavedResponse` (MSG41) |
| `GET /imports/manual/drafts/{previewId}` | — | `ManualTicketDraft { previewId, projectId, ticketId?, title?, description?, resolvedDate?, symptoms?, rootCause?, resolutionSteps?, workaround?, scope?, savedAt }` |
| `POST /reviews/unpublish` | `ApproveTicketsRequest { ticketIds (uuid, 1–500, unique) }` | 200 `ReviewDecisionResult { count }` |
| `POST /reviews/republish` | `ApproveTicketsRequest` | 200 `ReviewDecisionResult { count }` |

Unpublish / re-publish rejections (tag `review`, D36): invalid `ticketIds` → 400 · no PM role or Project not managed → 403 (MSG08) · unknown Ticket → 404 (MSG04) · wrong status → 409 (MSG06). A request with several faults gets the first of these in that order (400, then 404, then 403, then 409), as send-back does. Every rejected request changes nothing.

| `GET /imports/history` | query `projectId?` (uuid), `source?` (JIRA_IMPORT, CSV, MANUAL), `dateFrom?`, `dateTo?` (ISO dates), `page`, `size` | `ImportHistoryPage { content: ImportHistoryItem[], page: PageMetadata }`; `ImportHistoryItem { batchId, createdAt, performedBy, project { projectId, projectKey }, department { id, name }, source, dateFrom?, dateTo?, fileName?, publishedCount, removedCount, errorCount, status (COMPLETED, FAILED), errorCode? }` |

Import History rejections: date from after date to → 400 (MSG39) · no PM role → 403 (MSG08).

Extended endpoint (D29), owned by `ticket-review` code: `GET /tickets/{ticketId}` (operationId `getReviewTicketDetail`,
roles Ticket Resolver or Project Manager) — `ReviewTicketDetail` gains `department { id, name }`, `resolvedDate`
(ISO date, nullable) and `sourceLink` (string, nullable); every existing field and `canDecide` stay. Rejections: not
allowed (AC-008.3, AC-008.4) → 403 (MSG08) · unknown id → 404 (MSG04).

Manual entry rejections: invalid fields → 400 ProblemDetail listing every field with its MSG01 / MSG02 / MSG39 (AC-006.6) · Ticket ID taken → 409 (MSG31) · no PM role, Project not Active or not managed → 403 (MSG08) · Draft not found, another user's or not Manual → 404 (MSG04). `ImportDraftSummary.source` gains `MANUAL`.

Evidence (Change 2026-11-14, D17): `POST /imports/manual` and `POST /imports/manual/drafts` take `multipart/form-data` with part `entry` (JSON `ManualTicketRequest`, which gains `keepEvidenceIds` (uuid[]) — the Draft's Evidence to keep) and part `files` (0–5 binary files). `ManualTicketDraft` gains `evidence: [{ evidenceId (uuid), fileName, fileSize }]`. Rejections: MSG34 / MSG72 → 400 (a `fieldErrors` entry on `files`) · file storage not configured or failing → 503 (MSG06).

Endpoints of `import-ticket-list` that also serve a Jira preview: `GET /imports/previews/{previewId}`,
`GET …/rows`, `GET …/rows/{rowNo}`, `POST …/fill-solution`, `POST …/remove-rows`, `POST …/confirm` (Finish).
Their schemas gain, all optional for a CSV preview: `ImportPreviewResponse.source`, `resolvedFrom`, `resolvedTo`,
`draftSavedAt` (and `fileName` becomes nullable); `ImportPreviewRowResponse.sourceUrl`, `comments`;
`ImportResultResponse.notice` (MSG38 with the count and the keys).

Rejections of the new endpoints: MSG01, MSG39 → 400 · MSG04 on pull → 404 · MSG50 → 502 · no PM role or Project not managed → 403 (MSG08) · preview or Draft not found, another user's, or a CSV preview → 404 (MSG04). Finish: as Confirm of `import-ticket-list`, plus AC-003.2/AC-003.3 (skip, MSG38) instead of its MSG33 for a taken Ticket ID. Only MSG50 and MSG33 record a Failed Import Batch.

### Required from other modules
- `project` (existing code, owned by `project-integration` later): the Project's Jira project key, through `ProjectService`.
- `import-ticket-list` (PR #110, `vn.tsolve.ticket`): `ProjectAccess`, the `ticket`, `ticket_decision` (STORY-010; `ticket_import_decision` before it), `import_batch`, `import_preview` and `import_preview_row` tables, the preview endpoints and screens, and `TicketsPublishedEvent`, extended by this module.
- `workspace` (KAN-344): `tracker.service.JiraServiceAccount.current()` → `tracker.dto.JiraAccount(siteUrl, cloudId, apiToken)`, the bound Workspace's account, read by `JiraIssueSource` for each pull (D2).
- `identity` (KAN-118): `ScopeAccessService.canAccessDepartment(accountId, departmentId)` for Ticket Detail (D28).
- `ticket-review`: the Ticket Detail endpoint, its controller, service, DTO and web components, extended here (D29).
- `project-integration`: the `project` and `project_membership` tables (Department of a Project, its Project Managers).
- `ticket-review` (`vn.tsolve.review`): `ReviewDecisionController` and its `ReviewWorkspaceAccess` (the caller's managed Projects), the `ticket_decision` table (STORY-010; `review_decision` before it), and the Ticket Detail screen (`ReviewTicketDetail`) the buttons of STORY-009 sit above.
- `search`: returns only Published tickets (D34).

## Definition of Done
- [ ] Every acceptance criterion has a test whose name starts with its AC id
- [ ] `mvn verify` passes (tests + `mvn spotless:check`)
- [ ] Frontend `format:check`, `lint`, `typecheck`, `test`, `build` pass and `npm run generate:api` leaves no diff
- [ ] The Jira connection variables are listed in `.env.example` with no real value
