---
module: ticket-repository
date: 2026-11-07
---

# Design: ticket-repository

Scope: KAN-45 Jira import (UC-20) with Drafts. Stacked on PR #110 (KAN-31, `import-ticket-list`): a pulled Jira
issue is a row of that module's Import Preview (spec D7).

## Architecture Overview

```
web (features/ticket-import)                    backend vn.tsolve.ticket
 Import Tickets: Jira tab + Drafts list  ──REST─▶ JiraImportController  POST /imports/jira          (STORY-001)
 Import Preview (shared with CSV)        ──REST─▶ ImportController      rows, row, fill-solution,   (STORY-002)
 Import Ticket Preview popup (shared)                                     remove-rows, confirm=Finish (STORY-003)
                                         ──REST─▶ ImportDraftController draft, drafts, delete       (STORY-004)
   JiraImportService ─┬─ ProjectAccess (import-ticket-list)       Active + managed
                      ├─ project.ProjectService.findJiraProjectKey
                      └─ JiraIssueSource (port) ── JiraRestIssueSource ──HTTPS──▶ Jira Cloud REST v3
                                                   (Bearer scoped token via api.atlassian.com/ex/jira/{cloudId}; site URL, cloud id, token from env — D2)
   ImportConfirmServiceImpl: source JIRA_IMPORT → skip taken keys (MSG38) instead of MSG33
   ImportPreviewServiceImpl / fill-solution: accept a JIRA_IMPORT preview like a WITHOUT_SOLUTION one
```

- **Pull** (`POST /imports/jira`): validate dates (MSG01 / MSG39) → `ProjectAccess` (403) → Jira key from
  `ProjectService.findJiraProjectKey` (none → MSG50 "not connected", Failed batch) →
  `JiraIssueSource.resolvedIssues(key, from, to)` page by page, oldest first → drop keys already a
  `ticket.ticket_id` (one `IN` query per Jira page) → stop at 100 kept; one more kept issue = MSG35 → none kept:
  MSG04, no preview → in one transaction: delete the caller's non-Draft preview (AC-001.10), insert the preview
  (source `JIRA_IMPORT`, `file_name` NULL, the dates) and one `import_preview_row` per issue, status
  `NEEDS_SOLUTION`. The Jira calls run before the transaction (AGENTS.md §4).
  `JiraUnavailableException` → `ImportBatchRecorder` writes a Failed batch of source `JIRA_IMPORT` → MSG50 (502).
- **Rows, popup, Fill solution, remove-rows**: the `import-ticket-list` code of PR #110 (features
  `preview-fill-solution`, `row-solution-popup`, `fill-solution-screen`). This module only makes them accept a
  `JIRA_IMPORT` preview the way they accept a `WITHOUT_SOLUTION` one, and adds `sourceUrl` and `comments` to the
  row detail.
- **Finish** = `POST …/confirm` on a Jira preview, in `ImportConfirmServiceImpl` (feature `import-type-confirm`
  of `import-ticket-list` adds the Needs Solution check, `MSG_NEW_4`). For `JIRA_IMPORT`, inside the locked
  transaction: Valid rows whose key is now a Ticket are skipped and returned as `notice` MSG38 (AC-003.2/003.3); a
  unique-key violation on insert re-reads the taken keys and retries once without them (Edge 6). Batch and event
  as for CSV.
- **Draft** = the preview with `draft_saved_at` set. Saving again updates it (AC-004.7). The cleanup job, the
  CSV upload and the pull delete only previews with `draft_saved_at IS NULL` (AC-004.6). Reopen = the existing
  `GET /imports/previews/{previewId}`.

## Data Model

Liquibase `009-ticket-jira-import.sql` (author `ticket`), after PR #110's `008-ticket-import-project-uuid.sql`.
No new table: both tables are existing ones of `import-ticket-list`, which KAN-248 converts to the AC-001.20
convention with the other ticket tables.

```sql
ALTER TABLE import_preview ALTER COLUMN file_name DROP NOT NULL;           -- a Jira preview has no file
ALTER TABLE import_preview ADD COLUMN resolved_from  DATE;                 -- Jira only
ALTER TABLE import_preview ADD COLUMN resolved_to    DATE;                 -- Jira only
ALTER TABLE import_preview ADD COLUMN more_available BOOLEAN NOT NULL DEFAULT false;  -- MSG35
ALTER TABLE import_preview ADD COLUMN draft_saved_at TIMESTAMPTZ;          -- NULL = not a Draft
DROP INDEX uq_import_preview_owner;
CREATE UNIQUE INDEX uq_import_preview_owner_open ON import_preview (owner) WHERE draft_saved_at IS NULL;
CREATE INDEX idx_import_preview_owner_draft ON import_preview (owner, draft_saved_at DESC)
    WHERE draft_saved_at IS NOT NULL;

ALTER TABLE import_preview_row ADD COLUMN source_url TEXT;                 -- Jira only: {site}/browse/{key}
ALTER TABLE import_preview_row ADD COLUMN comments   JSONB NOT NULL DEFAULT '[]';  -- [{author, created, body}]
```

Row mapping of an issue: `row_no` = position (1…100), `ticket_id` = Jira key, `title` = summary (raw; cut to 200
only on the Ticket), `description` = plain text, `author` = assignee display name or NULL, `resolved_date` =
`yyyy-MM-dd` in Vietnam time, `errors` = `[]`. `import_batch` for Jira: `file_name` NULL, `error_count` = skipped.

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Jira access (D2, user) | `JiraIssueSource` port in `ticket.service`; `JiraRestIssueSource` reads `tsolve.jira.site-url`, `.email`, `.api-token` (env `JIRA_SITE_URL`, `JIRA_EMAIL`, `JIRA_API_TOKEN`, no defaults) | Wait for `workspace` / `project-integration`; per-Project token (SRS GBR-14) | UC-20 ships now; `workspace` replaces only the credential lookup later |
| Preview model (D7, user) | Rows of the CSV Import Preview of PR #110 | A separate `import_preview_issue` table and Jira endpoints | One preview flow, one screen; no new table; the Fill solution / popup / Confirm work of KAN-31 is reused |
| Drafts (D3, user) | `import_preview.draft_saved_at` | A separate `import_draft` table | Draft and preview are the same data |
| Jira search API | `GET /rest/api/3/search/jql`, `jql=project = "{key}" AND resolved >= "{from-1}" AND resolved < "{to+2}" ORDER BY resolved ASC, key ASC`, `fields=summary,description,assignee,resolutiondate,comment`, `maxResults=100`, `nextPageToken` | Old `/rest/api/3/search` | The old endpoint is removed by Atlassian |
| Date boundaries | JQL dates `yyyy-MM-dd`; `JiraRestIssueSource` re-checks `resolutiondate` in Vietnam time and drops issues outside the range | UTC instants in JQL | JQL follows the account's profile time zone, at most 19 h from Vietnam time: the JQL asks for a day more on each side (BUG-188, 2026-11-08) and the re-check keeps AC-001.1 exact |
| Comments | `comment` field of the search; when `comment.total` > returned, page `GET /rest/api/3/issue/{key}/comment` | One comment call per issue | ~1 call per 100 issues instead of 101 |
| ADF to text | `ticket.jira.AdfText`: `text` nodes joined, newline per paragraph / list item / heading, `mention` → its text, unknown nodes skipped | Store ADF | The popup shows plain text (AC-001.2) |
| HTTP client | Spring `RestClient`, 10 s connect / 30 s read | WebClient, Feign | Already used in `ai` and `identity` |
| Jira credential (D2, user 2026-11-07) | Scoped API token as `Authorization: Bearer`, base URL `https://api.atlassian.com/ex/jira/{JIRA_CLOUD_ID}`; `JIRA_SITE_URL` only builds `/browse/{key}` links; no `JIRA_EMAIL` | Basic `email:token` on the site URL (classic tokens only); both modes | A service account has only scoped tokens, which get 401 on the site URL (RESEARCH-003); one mode, fewer config paths |
| Rate limit retry (AC-001.11, user 2026-11-07) | In `JiraRestIssueSource`, around each GET: 429, or 503 with `Retry-After`, ≤ 5 s → sleep and resend, at most 2 retries; 429 without `Retry-After` = 1 s; > 5 s → stop. Sleep behind a `Sleeper` hook so tests do not wait | Spring Retry / Resilience4j; no retry | Two sequential retries need no library; worst case +10 s stays inside NFR 1 |
| Jira key of a Project | New `ProjectService.findJiraProjectKey(UUID)` in `project` | Read `ProjectRepository` from `ticket` | AGENTS.md: other modules only through `service/` |
| Pull deadline (AC-001.13, user 2026-11-08) | One deadline per pull, now + 60 s, passed with every page; `JiraRestIssueSource` starts no request and waits for no retry past it → `JiraUnavailableException` (MSG50) | A thread with a timeout around the whole read; no deadline | No extra thread (the request's database context stays on it); a request already running still ends at its 30 s read timeout (BUG-189) |
| Scan cap | At most 20 Jira pages (2,000 issues) per pull; reaching the cap sets "more available" (MSG35) | No cap | Bounded time when most issues are already Tickets (Edge 3) |

## File Structure
```
backend/src/main/resources/db/changelog/changes/009-ticket-jira-import.sql          new
backend/src/main/resources/db/changelog/db.changelog-master.xml                     + include
backend/src/main/resources/application.yml, .env.example                            + tsolve.jira.* / JIRA_* (shared files)
backend/src/main/java/vn/tsolve/project/service/ProjectService.java (+ impl)        + findJiraProjectKey
backend/src/main/java/vn/tsolve/ticket/
  controller/JiraImportController.java, ImportDraftController.java                  new
  dto/JiraPullRequest, ImportDraftSummary, DraftSavedResponse, IssueComment          new
  dto/ImportPreviewResponse, ImportPreviewRowResponse, ImportResultResponse          + Jira fields
  entity/ImportPreview (+ dates, moreAvailable, draftSavedAt), ImportPreviewRow (+ sourceUrl, comments)
  repository/ImportPreviewRepository (Draft-aware queries), TicketRepository
  service/JiraIssueSource, JiraIssue, JiraImportService, ImportDraftService (+ impls)
  service/impl/ImportConfirmServiceImpl, ImportPreviewServiceImpl, CsvImportServiceImpl, PreviewCleanupJob  (Jira / Draft branches)
  jira/JiraRestIssueSource, JiraProperties, AdfText
  exception/JiraUnavailableException
backend/src/test/java/vn/tsolve/ticket/…  Jira*Test / Jira*IT / *Draft*Test / jira/** (paths in module.md)
frontend/openapi/tsolve-api.yaml, frontend/src/shared/api/generated/**
frontend/src/features/ticket-import/
  components/JiraPullForm.tsx, DraftsList.tsx (+ tests)                              new
  pages/ImportTicketsPage.tsx (Jira tab enabled, Drafts), ImportPreviewPage.tsx (Jira summary, Save as draft, MSG38)
  components/ImportTicketPreviewModal.tsx (comments + Jira link)                    shared with KAN-31
  pages/JiraImport*.test.tsx                                                         Jira page tests
```

## External Dependencies
- Jira Cloud REST API v3 (search/jql, issue comments) through the Atlassian API gateway — reached with the Workspace service account's scoped token (D2).
- `import-ticket-list` features of PR #110 not built yet: `import-type-upload`, `preview-fill-solution`,
  `import-type-confirm`, `row-solution-popup`, `fill-solution-screen`. Tasks 5, 6 and 9 start after them.
- No new library.

## Edge Cases & Risks

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | 001 | Security | API token in a log line or MSG50 text | Critical | MSG50 carries only HTTP status / error type; token never logged (NFR 2, AC-001.7) | Task 3 |
| 2 | 001 | External | Jira returns 401/403/429/5xx, times out, or env vars are empty | High | 429 / 503 with `Retry-After` ≤ 5 s retried twice (AC-001.11); then MSG50, Failed batch, no preview (AC-001.7) | Task 3, Task 4, Task 11 |
| 3 | 001 | Data volume | Range holds 5,000 issues, most already Tickets | High | Scan cap of 20 pages; "more available" when hit | Task 4 |
| 4 | 001 | Time | Issue resolved 23:30 on the "to" date in Vietnam, Jira profile in UTC | High | Kept (Vietnam-time re-check) | Task 3 |
| 5 | 001 | Data | No description, no assignee, ADF with tables/mentions/code blocks | Medium | Empty description / writer; readable plain text | Task 3 |
| 6 | 003 | Concurrency | The Solution Form (UC-19) or another Finish creates a Ticket with the same key during Finish | High | Pre-check skips known keys; a unique violation → re-read, retry once without them (MSG38); second failure → MSG33 | Task 6 |
| 7 | 003 | Concurrency | Double click on Finish | High | `FOR UPDATE` on the preview (existing); second → 404, no second batch (AC-003.8) | Task 6 |
| 8 | 001 | Config | Project has no Jira project key (stub Project ids) | High | MSG50 with "not connected", Failed batch, no Jira call | Task 4 |
| 9 | 002 | Validation | Whitespace-only required section; 10,001 characters in total | Medium | MSG01 / MSG02, no row changed (existing fill-solution) | Task 5 |
| 10 | 001 | State | PM pulls again while an unsaved preview is open in another tab | Medium | Old non-Draft preview deleted; old tab gets 404 → web app returns to Import Tickets | Task 4, Task 9 |
| 11 | 004 | State | Draft reopened after its Project became inactive | Medium | Opens; Finish → MSG40, Draft kept | Task 6 |
| 12 | 004 | Authorization | User B reads or deletes user A's Draft | High | 404 (AC-004.5) | Task 7 |
| 13 | 003 | Data | Summary longer than 200 characters | Medium | Ticket title cut to 200; the row keeps the full summary | Task 6 |
| 14 | 004 | Cleanup | Cleanup job runs while a Draft is older than 24 h | High | Kept (AC-004.6) | Task 2 |
| 15 | 004 | State | CSV upload or Jira pull while the same PM has a Draft | High | Draft kept, partial unique index allows it (AC-004.6) | Task 2 |
| 16 | 001 | Config | `JIRA_SITE_URL` with a trailing slash or plain http | Low | Trailing slash trimmed; non-https rejected at start-up | Task 3 |

## Risks
- **Order of PRs:** KAN-45 merges after PR #110. If #110 changes the preview rows or Confirm again, rebase and
  re-run Tasks 5–6.
- **Schema alignment (KAN-248)** renames these tables and columns; whoever merges second updates their changeset.
- **Shared files** (`application.yml`, `.env.example`, `project/service`) need their owners' approval in the PR.
- **Service account permissions:** a missing Browse permission looks like "no issues" (MSG04), not an error.

## Change 2026-11-13: Ticket status model (KAN-132, STORY-005)

**Rule in the enum.** `TicketStatus` gets `boolean canMoveTo(TicketStatus to)` backed by one `EnumMap` table — exactly
AC-005.1 (D9). Creation is not a transition: `Ticket.imported` keeps setting `PUBLISHED` (AC-005.2).

**One transition step.** `Ticket.moveTo(TicketStatus to, Instant at)` throws `BusinessRuleException("MSG06")` (→ 409,
D11) when `canMoveTo` is false, else sets `status` and `updatedAt`. `recordReviewFeedback(outcome, feedback)` sets the
reason/comment and throws `IllegalStateException` unless `moveTo` already reached `outcome`, so a reason never sits on a
ticket in another status. A new module-internal `@Component TicketTransitions` (`T/service/impl/TicketTransitions.java`,
like `ImportBatchRecorder`) with
`List<UUID> move(List<Ticket> tickets, TicketStatus to, Instant at)`:
1. checks every Ticket first and throws MSG06 before changing any (AC-005.3 — no half-moved batch in memory either);
2. calls `moveTo` on each;
3. publishes one `TicketsPublishedEvent(ids)` when `to == PUBLISHED`, one new `TicketsUnpublishedEvent(ids)` when
   `to == UNPUBLISHED` (AC-005.4, AC-005.5, D10). Published inside the caller's transaction; listeners use
   `@TransactionalEventListener(AFTER_COMMIT)` as `TicketsPublishedListener` already does.

`TicketDecisionServiceImpl.decide` uses `transitions.move(batch, outcome, now)` and then sets each Ticket's review
feedback — so review Approve now publishes `TicketsPublishedEvent` and approved tickets get indexed. Its own status
pre-check (MSG06 when not Pending review) stays: it also guards the source rule.

**Not routed through the step (and why).** Bulk SQL updates keep their own `WHERE` guard, which encodes the same
table: `ReviewExpiryRepository.expireOverdue` (`status IN ('PENDING_REVIEW','CHANGES_REQUESTED')` → EXPIRED) and
`TicketRepository.sendBack` (`t.status = 'EXPIRED'`, and the service checks EXPIRED first under the row lock). Import Finish keeps its batched event (AC-003.9).
`ponytail:` loading every expired Ticket as an entity just to run `moveTo` would cost one row read per Ticket for no
new protection.

**Files.** Modify `T/entity/TicketStatus.java`, `T/entity/Ticket.java`, `T/service/impl/TicketDecisionServiceImpl.java`,
`T/event/TicketsPublishedEvent.java` (javadoc: not only import). Create `T/service/impl/TicketTransitions.java`,
`T/event/TicketsUnpublishedEvent.java`. Tests `TT/entity/TicketStatusTest.java`, `TT/service/impl/TicketTransitionsTest.java`.

## Change 2026-11-14: Manual entry with Evidence and Drafts (KAN-133, STORY-006, STORY-007)

Decisions: spec D12–D18; the full log with options is `04-plans/ticket-repository/kan-133-decisions.md`.

```
web (features/ticket-import)                 backend vn.tsolve.ticket
 Import Tickets: Manual entry tab  ─multipart─▶ ManualEntryController  POST /imports/manual          (STORY-006)
   ManualEntryForm (+ SolutionFields)                                   POST /imports/manual/drafts   (STORY-007)
 Drafts list: Open on a Manual Draft ──REST──▶                          GET  /imports/manual/drafts/{id}
                                     ──REST──▶ ImportDraftController    GET /imports/drafts, DELETE …/{id} (+ MANUAL)
   ManualEntryServiceImpl ─┬─ ProjectAccess.managesActive (403)
                           ├─ EvidenceFiles (check D18 / 10 MB / ≤ 5, then FileStorage.store outside the transaction)
                           ├─ platform.service.FileStorage (PR #193)
                           └─ TransactionTemplate: ticket + evidence + approve decision + Completed batch + delete Draft
                                                    + TicketsPublishedEvent + audit IMPORT
```

**Save** (`POST /imports/manual`, AC-006.1–006.11), in this order:
1. `@PreAuthorize` PM (AC-006.9 role); `ManualEntryValidator` collects MSG01 (blank after trim, AC-006.3), then
   MSG02 (lengths in code points, AC-006.4), then MSG39 (`resolvedDate` after today in `Asia/Ho_Chi_Minh` from the
   injected `Clock`, AC-006.5) — the first message kind found wins, naming every field of that kind, as CSV import
   does. All checks run before any file is stored.
2. `projects.managesActive(user, projectId)` else 403 (AC-006.9). `draftId` given → the caller's Manual Draft or 404
   (AC-007.8); its Evidence minus `keepEvidenceIds` = files to remove later.
3. Evidence (AC-006.6, 006.7): kept + new count ≤ 5 else MSG72; each new file: extension in D18 and size ≤ 10 MB else
   MSG34 with its name. Then each new file → `FileStorage.store` (outside any transaction: object storage is an HTTP
   call, AGENTS.md §4). A failure while storing deletes the keys already stored and goes to step 6.
4. `tickets.existsByTicketId(trimmed)` → MSG31 409 before the transaction (AC-006.8); the race is caught in step 6.
5. Transaction: `Ticket.enteredManually(…)` (new factory beside `Ticket.imported`: source MANUAL, PUBLISHED, writer =
   `TSolveUser.fullName()` cut to 100, `writerId` = `TSolveUser.id()`); `ImportBatch.completed(projectId, user, MANUAL,
   null, 1, 0, 0)` saved first, as Finish does; one `TicketEvidence.of(ticketId, key, name, type, size)` per kept and
   new file; `TicketReviewDecision.approve`; the Draft deleted (AC-007.6); `TicketsPublishedEvent(List.of(id))`
   (AC-006.10); `audit.record(IMPORT, "PROJECT", projectId, {importBatchId, created: 1})`.
6. Failure of step 3 or 5: a unique violation (SQLState 23505 on `ticket_id`) → delete the new keys, MSG31 409, no
   batch (AC-006.8). Any other failure → delete the new keys, `ImportBatchRecorder.recordManualFailed(projectId, user,
   reason)` in its own transaction, MSG33 500 (AC-006.11). The Draft is untouched (AC-007.6).
7. After commit: `FileStorage.delete` for the Draft files the PM removed. A delete failure is logged (key only) and
   ignored — an orphan object, never a lost one. `ponytail:` no orphan sweeper; add one if orphans show up in storage.

**Draft** (`POST /imports/manual/drafts`, AC-007.1–007.5): Project required (MSG01) and managed (403); Evidence step 3
as above; nothing else checked. New Draft = `ImportPreview.manualDraft(owner, projectId, now)` (source MANUAL,
`import_type` WITH_SOLUTION, no file name, `draft_saved_at` = now) with one `import_preview_row` (row 1, status VALID,
raw values; `resolved_date` stays text). `draftId` given → update that row, `draft_saved_at` and the Evidence list,
then delete the removed files after commit. Reopen = `GET /imports/manual/drafts/{id}` (AC-007.4). Delete =
`ImportDraftServiceImpl.delete`, now accepting MANUAL too, deleting the Draft's files after commit (AC-007.7).
The preview endpoints (`ImportPreviewServiceImpl`, `ImportConfirmServiceImpl`, `ImportDraftServiceImpl.save`) look a
preview up only when its source is not MANUAL, so a Manual Draft is 404 there (AC-007.8).

### Data model — changeset `031-ticket-manual-entry.sql` (author `ticket`; the next free number when built)

```sql
ALTER TABLE evidence ADD COLUMN storage_key VARCHAR(200);  -- FileStorage key; NULL only on rows from before KAN-133
ALTER TABLE import_preview ADD COLUMN evidence JSONB NOT NULL DEFAULT '[]';
    -- Manual Draft only: [{id (uuid), storageKey, fileName, fileType, fileSize}]
```

`TicketEvidence` stays `@Immutable` (never updated, BR "Evidence is permanent") and gets a factory and a generated
UUID id for inserts. No new table, so the tenant-table convention guard (AC-001.20 of `platform`) is unchanged.
`application.yml`: `spring.servlet.multipart.max-file-size: 11MB`, `max-request-size: 56MB` (5 × 10 MB + the JSON
part). The 10 MB rule of MSG34 is checked by the service, so a file between 10 and 11 MB gets MSG34 by name; only a
file above 11 MB meets the global MSG28 answer, and the web app checks sizes first (AC-006.12).

### Key Decisions (KAN-133)

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Evidence storage (D12) | `platform` `FileStorage` of PR #193; Evidence tasks wait for its merge | Ship without Evidence; module-local storage | The story's AC includes Evidence; the storage already exists in #193 |
| Manual Draft (D13) | `import_preview` source MANUAL + one row + JSONB `evidence` | New `ticket_draft` table | Drafts list, delete, 404 and cleanup rules already work there; no new table |
| Request shape | One multipart request: JSON part `entry` + `files` parts | Upload files first to a temp endpoint, then save | One round trip, no temp-file lifecycle; 56 MB worst case is fine for a PM screen |
| Storage vs. transaction | Store files before the transaction, delete them on failure; delete removed Draft files after commit | Store inside the transaction | No outbound call inside `@Transactional` (AGENTS.md §4); worst case is an orphan object, never a Ticket without its file |
| Writer (D14) | `@AuthenticationPrincipal TSolveUser` full name + id | Free text | UC-22 step 6 |
| File type (D18) | Extension allow-list, type from extension | Content sniffing (Tika) | No new library; files are not shown inline yet (`ponytail:` sniff when UC-24 shows them inline) |
| Taken Ticket ID (AC-006.8) | Pre-check + unique-violation catch → MSG31 | Pre-check only | The Solution Form or another PM can take the id between check and insert |
| Decided in planning: validation order (builder check) | MSG01, then MSG02, then MSG39, then Project access, then Evidence, then MSG31 | All errors at once | Same as CSV import; the web app shows every field error inline from its own checks |

### Edge Cases & Risks (KAN-133)

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 17 | 006 | Security | File named `x.pdf.exe`, `../../a.png`, or `A.PNG` | High | Extension = text after the last dot, lower-cased: `.exe` → MSG34; `.PNG` accepted; the name is only stored, never used as a storage path (FileStorage AC-007.2 of `platform`) | Task 17 |
| 18 | 006 | Concurrency | Two PMs save the same Ticket ID at once | High | One wins; the other gets MSG31 409, its stored files deleted, no batch | Task 16, Task 17 |
| 19 | 006 | External | Object storage down while storing file 3 of 5 | High | Files 1–2 deleted; MSG33, Failed batch, no Ticket | Task 17 |
| 20 | 006 | Data | Ticket ID with spaces around it; 50 emoji (100 UTF-16 units) | Medium | Trimmed before the uniqueness check; limits counted in code points | Task 16 |
| 21 | 006 | Time | Resolved date = today at 00:30 Vietnam (17:30 UTC the day before) | Medium | Accepted; "today" is Vietnam time | Task 16 |
| 22 | 006 | Validation | Ticket ID blank and title 300 characters | Medium | Backend: MSG01 for Ticket ID (first kind wins); the web app shows both inline | Task 16, Task 19 |
| 23 | 007 | State | Draft reopened in two tabs, saved as Ticket in one, saved as Draft in the other | Medium | The Draft is gone after the Ticket save; the other tab's Draft save names a missing Draft → 404, web app returns to Import Tickets | Task 18, Task 19 |
| 24 | 007 | Authorization | `GET /imports/previews/{id}` or `/confirm` on a Manual Draft id | High | 404 (AC-007.8) — a Manual Draft never goes through Confirm | Task 18 |
| 25 | 007 | Data | Draft with resolved date text "2026-13-40" | Low | Kept as text in the Draft; Save → MSG39 | Task 18 |
| 26 | 006 | Size | 5 files of 10 MB each | Medium | One 50 MB request fits 56 MB; accepted | Task 15, Task 17 |
| 27 | 007 | Cleanup | Daily cleanup with a Manual Draft older than 24 h | High | Kept (AC-004.6: `draft_saved_at` is set at creation) | Task 18 |

### Risks (KAN-133)
- **PR #193 (KAN-96)** must merge first for Tasks 17 and 18; Tasks 14–16 and 19 do not need it. If #193 changes
  `FileStorage`, only `EvidenceFiles` changes.
- **Shared file** `application.yml` (multipart limits) also governs the CSV upload; its own 10 MB check (MSG28) stays.

### File Structure (KAN-133)
```
backend/src/main/resources/db/changelog/changes/031-ticket-manual-entry.sql (+ master include)     new
backend/src/main/resources/application.yml                                                         multipart limits
T/controller/ManualEntryController.java                                                            new
T/dto/ManualEntryRequest, ManualEntryResponse, ManualDraftResponse, DraftEvidence                   new
T/service/ManualEntryService.java; T/service/impl/ManualEntryServiceImpl, ManualEntryValidator, EvidenceFiles  new
T/entity/Ticket (+ enteredManually), TicketEvidence (+ of, storageKey), ImportPreview (+ manualDraft, evidence)
T/service/impl/ImportBatchRecorder (+ recordManualFailed), ImportDraftServiceImpl, ImportPreviewServiceImpl, ImportConfirmServiceImpl (MANUAL → 404)
TT/…/Manual*Test, Manual*IT                                                                         tests
frontend/openapi/tsolve-api.yaml, frontend/src/shared/api/generated/**
F/components/ManualEntryForm.tsx (+ test), SolutionFields.tsx (extracted from SolutionFormModal), DraftsList.tsx (Open Manual)
F/pages/ImportTicketsPage.tsx (Manual entry tab), F/api/** (manual entry calls), F/mocks/** (handlers)
```
