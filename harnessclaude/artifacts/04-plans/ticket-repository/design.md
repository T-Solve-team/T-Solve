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
`T/event/TicketsPublishedEvent.java` (javadoc: not only import). Create `T/service/TicketTransitions.java`,
`T/event/TicketsUnpublishedEvent.java`. Tests `TT/entity/TicketStatusTest.java`, `TT/service/TicketStatusTransitionsTest.java`.

## Change 2026-11-14: Manual entry (KAN-133, STORY-006)

**Shape.** New `ManualTicketController` (`/api/v1/imports/manual`, PM role) → `ManualTicketService` → existing
repositories. No new table, no migration.

- **Save** (`POST /imports/manual`): validate every field at once (AC-006.3–AC-006.6) → 400 ProblemDetail with
  `fieldErrors` (`ProblemDetails.withFieldErrors`; one entry per field: MSG01 / MSG02 with `max_length` / MSG39).
  Then `ProjectAccess.managesActive` (403 MSG08). In one transaction: `findExistingTicketIds` → 409 MSG31;
  `ImportBatch.completed(project, user, MANUAL, null, 1, 0, 0)`; `Ticket.imported(…, source MANUAL, writer =
  fullName)` with `writerId`; `TicketReviewDecision.approve`; publish `TicketsPublishedEvent([id])`; audit IMPORT.
  A unique violation on insert (race) → 409 MSG31, nothing kept (AC-006.7). Manual entry records no Failed batch.
  - chosen: a dedicated service; alternatives: a one-row preview + the existing Confirm. Rationale: Confirm carries
    CSV/Jira rules (MSG30, MSG_NEW_4, skip/retry) that do not apply, and field errors need the request's fields.
- **Writer (D15).** The controller takes `@AuthenticationPrincipal TSolveUser`: `getName()` = id (the owner string the
  previews already use), `fullName()` → `writer` (cut to 100 code points as BUG-190), `id()` → `writer_id`.
  `Ticket.writerId` becomes insertable and is set by the manual factory.
- **Drafts (D14).** A Manual Draft = an `import_preview` of source MANUAL, import_type WITH_SOLUTION, `draft_saved_at`
  set at creation (so the partial unique index `uq_import_preview_owner_open` never counts it), with one
  `import_preview_row` (row_no 1, status VALID, values as typed, not validated). Save as draft with `draftId` rewrites
  that row and moves `draft_saved_at` (AC-006.10). `GET /imports/manual/drafts/{id}` returns the fields. A save with
  `draftId` deletes the Draft in its transaction (AC-006.12). `ImportDraftServiceImpl.list` / `delete` accept MANUAL
  (today they filter to JIRA_IMPORT). `PreviewCleanupJob` already skips Drafts. The `/previews/{id}/…` endpoints keep
  answering 404 for a Manual preview.
- **Web app.** `ManualEntryForm` on the now-enabled Manual entry tab: Project select (`useManagedProjects`), Ticket
  ID, Title, Description, Resolved date (antd DatePicker, future days disabled), the five Solution sections (reuse
  the section fields of `SolutionFormModal` when they can be shared without changing its behavior; otherwise plain
  `Input.TextArea`s). Save → MSG32 toast and reset; Save as draft → MSG41 toast, keep `draftId`, refresh Drafts.
  Server `fieldErrors` → `form.setFields`. Drafts list: type label "Manual"; Open → Import Tickets with
  `?tab=manual&draftId=N`, which loads the Draft into the form.

**Edge cases.** Whitespace-only fields count as missing (trim). Lengths count code points, as CSV does. Resolved date
is compared with today in `Asia/Ho_Chi_Minh` through the injected `Clock`. Two tabs saving the same Draft: the second
save answers 404 (Draft gone) and saves nothing. Draft of a Project the caller no longer manages: save / Save as draft
answer 403 (MSG08), the Draft stays.

## Change 2026-11-14 (2): Evidence of manual entry (KAN-133, STORY-006, AC-006.15–AC-006.22)

**Storage.** `platform` `FileStorage` (`store(InputStream) → StoredFile(key, size)`, `delete(key)`), injected as
`ObjectProvider<FileStorage>`: the bean exists only when `FILE_STORAGE_ENDPOINT` is set (KAN-96 D9), so no bean
→ a request with files answers 503 MSG06 (AC-006.21). Until `platform` binds a `TenantContext`, `store` itself
refuses ("no Workspace bound"); that also maps to 503. Tests mock `FileStorage`.

**Schema** (new changeset `NNN-manual-entry-evidence.sql`, author ticket):
- `evidence` gains `storage_key VARCHAR(100)` (nullable: rows from the review module have none).
- New `import_preview_evidence`: `import_preview_evidence_id UUID PK DEFAULT gen_random_uuid()`, `preview_id` FK →
  `import_preview` ON DELETE CASCADE, `file_name`, `file_type`, `file_size`, `storage_key` NOT NULL, timestamps as
  the tenant-table convention (platform AC-001.20) asks; index on `preview_id`. Follow the convention guard test.

**Request shape (D17).** Multipart: part `entry` = `ManualTicketRequest` JSON (+ `keepEvidenceIds`), part `files`
= `List<MultipartFile>` (optional). Controller: `@RequestPart("entry") ManualTicketRequest`, `@RequestPart(value =
"files", required = false) List<MultipartFile>`; `consumes = MULTIPART_FORM_DATA_VALUE`.

**Flow of save and Save as draft.**
1. Existing checks (fields 400 for save only, Project 403, Draft 404) — before any file is stored.
2. File rules (AC-006.16, AC-006.17): extension in the D19 list (case-insensitive), size ≤ 10 MB (10,485,760 bytes),
   kept + new ≤ 5. `keepEvidenceIds` not belonging to the Draft are ignored. Rejection → 400 `InvalidFieldsException`
   with one entry on field `files` (MSG34 `{file_name}` or MSG72) — nothing stored yet.
3. Store each new file (outside the transaction) → list of keys. Any storage failure → delete the keys stored so
   far, 503 MSG06 (`StorageUnavailableException extends MessageCodeException`, status 503; module exception).
4. The existing transaction (`TransactionTemplate`, not `@Transactional` on the method, so steps 3 and 5 sit
   outside it): writes Ticket/Draft as today plus Evidence rows (save: `evidence` rows for kept Draft files + new
   files, with `storage_key`; Save as draft: `import_preview_evidence` rows for new files, delete rows left out of
   `keepEvidenceIds`). If it throws → delete the keys stored in step 3 (AC-006.20), rethrow.
5. After commit: delete the stored files of Draft Evidence that was left out (AC-006.19). On a save, kept Draft
   files are NOT deleted — their keys move to the Ticket's Evidence; the Draft rows go with the Draft (cascade).
   `ImportDraftServiceImpl.delete` of a Manual Draft deletes its files after commit. A failing delete is logged
   (key only) and never fails the request. ponytail: an orphan object stays if the process dies between store and
   commit; a storage sweep can collect keys without a row later.

**Reopen.** `ManualTicketDraft.evidence` = the Draft's `import_preview_evidence` rows (id, file name, size).
`file_type` = the file's content type as sent (fallback by extension).

**Web app.**
- New kit component `shared/ui/EvidenceUpload` (antd `Upload` is banned outside `shared/ui`; FE Lead review
  needed, AGENTS.md rule 10): controlled list of picked files + kept Draft files, `beforeUpload` returns false (no
  auto upload), checks type/size (MSG34) and count (MSG72) on add, remove button per file.
- `ManualEntryForm`: Evidence field after the Solution sections; sends multipart through the generated client
  (orval generates `FormData` for multipart bodies; check the generated signature after `generate:api`).
  Reopened Draft → its files listed as kept; removing one drops its id from `keepEvidenceIds`. Server
  `fieldErrors` on `files` show under Evidence.

## Change 2026-11-14: Import History and Ticket Detail (KAN-134 STORY-007, KAN-135 STORY-008)

### Import History (STORY-007)

**Shape.** New `ImportHistoryController` (`GET /api/v1/imports/history`, `@PreAuthorize(ImportController.PROJECT_MANAGER)`
→ 403 MSG08, AC-007.7) → `ImportHistoryService.list(TSolveUser, ImportHistoryQuery)` → one native page query in
`ImportBatchRepository`. Answer `PagedModel<ImportHistoryItem>` as `GET /api/v1/tickets` (page from 0, size 1–100,
default 20; D22).

- **Query.** `import_batch b JOIN project p ON (p.public_id = b.project_id OR lpad(to_hex(p.project_id),32,'0')::uuid =
  b.project_id) JOIN department d ON d.department_id = p.department_id LEFT JOIN app_user u ON u.id::text =
  b.performed_by WHERE EXISTS (project_membership m WHERE m.project_id = p.public_id AND m.user_id = :caller AND
  m.role_in_project = 'PROJECT_MANAGER')` + the optional filters, `ORDER BY b.created_at DESC, b.id DESC`, with a
  count query. The legacy hex form matches the way `ProjectMembershipReviewAccess` keys a Project. `performedBy` =
  `coalesce(u.full_name, b.performed_by)` (AC-007.3; the `::text` compare never fails on a non-UUID value).
  - chosen: one native SQL query in `ticket`; alternatives: per-row calls to `project` / `identity` services.
    Rationale: one page = one query; `ReviewDecisionRecordRepository` already joins `app_user` the same way.
- **Filters (AC-007.5, AC-007.6).** `projectId` is a filter only: a Project the caller does not manage is cut by the
  `EXISTS`, so the page is empty. Decided in planning: empty page, not 403 (builder check). `source` binds to
  `TicketSource` (SOLUTION_FORM gives an empty page; an unknown value → 400 as any bad enum). `dateFrom` / `dateTo` =
  `LocalDate`, turned into `[from 00:00, to+1 00:00)` in `Asia/Ho_Chi_Minh` through the injected `Clock` zone;
  `from > to` → `InvalidPullRangeException.invalid()` (400 MSG39).
- **Error code (AC-007.4, D26).** The service returns the first `MSG\w+` match of `import_batch.error` (stored as
  "ExceptionName MSGxx" by `ImportBatchRecorder`), else null.
- **Date range (AC-007.8, D25).** Changeset `032-ticket-import-batch-date-range.sql` (author `ticket`):
  `ALTER TABLE import_batch ADD COLUMN date_from DATE, ADD COLUMN date_to DATE;` + include in
  `db.changelog-master.xml`. `ImportBatch` gets both fields; `completed(…)` / `failed(…)` take them (null for CSV and
  Manual). `ImportBatchRecorder.recordJiraFailed(projectId, user, from, to, error)` gets the pull request's range;
  `recordFailed(preview, …)` and the Finish `completed(…)` in `ImportConfirmServiceImpl` pass
  `preview.getResolvedFrom()` / `getResolvedTo()` (null on a CSV preview). No new index:
  `idx_import_batch_project (project_id, created_at DESC)` serves the per-Project page.
- **Web app (AC-007.9, AC-007.10).** `ImportHistoryPage` replaces the placeholder: antd `Table` fed by the generated
  `useGetImportHistory` hook, server paging; filters Project (`useManagedProjectOptions`), Source (Select), Date range
  (`DatePicker.RangePicker`). Column "Date range / file" = `dateFrom – dateTo` for JIRA_IMPORT, `fileName` for CSV,
  `—` otherwise. Column "Removed / errors" = `removedCount / errorCount`. Empty page → `EmptyState` (MSG04). Load
  errors → the app's global query error handler (MSG06 toast). MSW handler and data in `F/mocks/`.

### Ticket Detail (STORY-008)

**Shape.** Extend, do not add (D29). `ReviewReadController.detail` keeps its path and operationId; its guard becomes
`hasAnyRole(TICKET_RESOLVER, PROJECT_MANAGER)` (AC-008.4 role part; today it is EMPLOYEE). The files are
`ticket-review`'s (owner Phạm Tùng Dương); module.md `paths` of this module lists them so the gate opens them with
this spec's approval.

- **Department of the ticket.** New native query `TicketRepository.findPlacement(UUID projectId)` →
  `Optional<Placement(UUID projectPublicId, String projectKey, UUID departmentId, String departmentName, boolean
  departmentActive)>`: `project p JOIN department d`, matched by `public_id` or the legacy hex id (as above), with no
  `p.status` condition: the Department decides (D28). No row = no Department = nobody (AC-008.3).
- **Visibility in `TicketReviewReadServiceImpl.detail`.** Order: ticket by id, else 404 MSG04 (AC-008.5) → placement
  missing or `!departmentActive` → 403 MSG08 (AC-008.3) → PUBLISHED: `scopeAccess.canAccessDepartment(accountId,
  departmentId)`, else 403 (AC-008.1) → not PUBLISHED: `writerId == accountId`, or `managed` of the ticket's Project
  in the `projects` map (`ReviewWorkspaceAccess.readableProjects` = `project_membership` PROJECT_MANAGER), else 403
  (AC-008.2). The 403 is Spring's `AccessDeniedException`, which `GlobalExceptionHandler` already
  answers with 403 MSG08 (`NO_PERMISSION`). `canDecide` and `lastReviewComment` keep their rules.
  - chosen: `identity` `ScopeAccessService` (a service interface, AGENTS.md); alternatives: the `departmentId` of the
    `projects` map. Rationale: the map lists only ACTIVE Projects and knows neither Department status nor the GBR-02
    membership rules KAN-118 already encodes.
- **New fields (AC-008.6).** `TicketReviewView.Detail` + `Department department` (`record Department(UUID id, String
  name)`), `LocalDate resolvedDate`, `String sourceLink`; `project` now comes from the placement, so a Published
  ticket of an inactive Project still shows its key. `TicketReviewMapper.detail` fills them. OpenAPI
  `ReviewTicketDetail`: the three fields optional and nullable. Decided in planning: optional, so the `ticket-review`
  mock data still type-checks without edits (builder check).
- **Source link (AC-008.7, D30).** `Ticket` maps `source_link` (`VARCHAR(500)`, insertable). `Ticket.imported(…)`
  takes `sourceLink`; `ImportConfirmServiceImpl.toTicket` passes `row.getSourceUrl()` for a Jira preview, null for CSV;
  manual entry passes null.
- **Web app (AC-008.8–AC-008.10).** `ReviewTicketContent` adds Department, Resolved date and Source link items; the
  link renders as `<a target="_blank" rel="noopener noreferrer">` only when `new URL(link).protocol` is `http:` or
  `https:`, else plain text (the rule of the Evidence links, `ticket-review` AC-006.5). The review due date item
  renders only for `PENDING_REVIEW`. `ReviewTicketDetail`: `error.status === 403` → MSG08 inline and no content,
  before the existing 404 → MSG04 branch; other failures keep `ErrorState` with retry plus the global query error
  handler's MSG06 toast. The tests live in `frontend/src/features/tickets/pages/TicketDetailPage.test.tsx`, which only
  this module claims, so AC-008.x does not clash in trace with `ticket-review`'s own AC-008.x.

### Edge cases

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 17 | 007 | Data | `performed_by` = "stub-user", or a UUID with no `app_user` | Medium | Stored value shown | Task 22 |
| 18 | 007 | Authorization | Batch of a Project the PM no longer manages | High | Not listed | Task 22 |
| 19 | 007 | Data | Batch whose `project_id` is the legacy hex form | Medium | Listed with Project and Department | Task 22 |
| 20 | 007 | Time | Batch created 23:30 Vietnam time on `dateTo` | Medium | Included | Task 22 |
| 21 | 008 | Authorization | Published ticket, Ticket Resolver of another Department | High | 403 MSG08 | Task 24 |
| 22 | 008 | Authorization | Pending ticket, PM of the same Department but not of the Project | High | 403 MSG08 | Task 24 |
| 23 | 008 | State | Published ticket of an inactive Project in an Active Department | Medium | Visible to that Department | Task 24 |
| 24 | 008 | State | Department soft-deleted | High | 403 for everyone, the writer and the PM too | Task 24 |
| 25 | 008 | Security | `sourceLink` = `javascript:alert(1)` | Critical | Plain text, no link | Task 25 |
| 26 | 008 | Tenancy | Ticket id of another Workspace | High | 404, the same answer as an unknown id (platform AC-015.3) | Task 24 |

### Risks

- `ReviewDecisionApiIT.readsPersistedSolutionAndEvidenceAndHidesUnmanagedPendingTickets_AC_009_2_AC_009_3`
  (`ticket-review`) expects 404 for an unmanaged pending ticket and 200 for a Published ticket of project
  `new UUID(0, 20)`, which has no `project` row: both answers become 403. Task 24 updates that test; tell its owner.
- `CrossModuleIsolationIT` (platform) reads a Published ticket as a Ticket Resolver MEMBER of the Project: still
  allowed by `canAccessDepartment`; it must stay green.
- `ticket-review` AC-006.6 describes a mock-API 404 for a hidden ticket; the real API now answers 403 (D31). Its spec
  is not changed here.

## Change 2026-11-15: Unpublish / re-publish (KAN-136, STORY-009)

**Shape.** Two more decisions next to send-back; no new class on the backend.

```
web  features/tickets  TicketDetailPage ── TicketPublicationActions (Unpublish / Re-publish + ConfirmDialog)
                                              │ POST /api/v1/reviews/unpublish | /republish  { ticketIds: [id] }
backend  review.ReviewDecisionController ──▶ ticket.TicketDecisionService.unpublish / republish
             (managed Projects of the caller)      └─ lockForReview → scope check → TicketTransitions.move
                                                      → review_decision rows → audit
```

- **Service.** `TicketDecisionService` gets `int unpublish(UUID reviewerId, Set<UUID> managedProjectIds, List<UUID>
  ticketIds)` and `int republish(…)`. Both call one private `changePublication(…, TicketStatus to)` in
  `TicketDecisionServiceImpl`, in this order, as `sendBack` does: `validatedIds` (400, AC-009.7) →
  `tickets.lockForReview(ids)` → `validateScope` (404 MSG04, then 403 MSG08; AC-009.4, AC-009.5) →
  `transitions.move(batch, to, now)` — it checks the whole batch first and throws MSG06 (409) before it changes any
  Ticket, then publishes the one event (AC-009.3, AC-005.4, AC-005.5) → one `ReviewDecisionRecord` per Ticket
  (`UNPUBLISH` / `REPUBLISH`, no feedback, `now`) → `audit.record(UNPUBLISH | PUBLISH, "TICKET", id)` per Ticket
  (AC-009.1, AC-009.2). `Ticket.moveTo` sets only `status` and `updatedAt`, so the Solution, Evidence, feedback and
  review due date stay (AC-009.2). No source rule: a ticket of any source can be unpublished.
- **Controller.** `ReviewDecisionController` (`review`, D36) gets `@PostMapping("/unpublish")` (operationId
  `unpublishTickets`) and `@PostMapping("/republish")` (`republishTickets`), body `ApproveRequest`, answer
  `DecisionResult`, managed Projects from `managedProjects(actor)` — the same three lines as `sendBack`. The class
  already carries `@PreAuthorize` Project Manager (AC-009.6).
- **Data.** Liquibase changeset `ticket:0NN-review-decision-publication` (next free number in
  `db/changelog/changes`, 034 on 2026-11-15) replaces `ck_review_decision` to allow `'UNPUBLISH', 'REPUBLISH'` beside
  the four values of changeset 024, with a `--rollback` that restores them. `ReviewDecisionRecord.Decision` gains
  `UNPUBLISH`, `REPUBLISH` (the column is `VARCHAR(20)`). No new table or column.
- **Review History (D35).** The history queries of `ReviewDecisionRecordRepository` filter
  `d.decision in (:decisions)`, so the new rows stay out of the list until `ticket-review` adds them to its filter.
  Nothing to change here.
- **Search (D34).** Nothing. `TicketsPublishedListener` embeds a re-published ticket again; the upsert makes that
  harmless. No listener is added for `TicketsUnpublishedEvent`.
- **Contract.** `frontend/openapi/tsolve-api.yaml`, tag `review`: the two paths with `ApproveTicketsRequest` →
  `ReviewDecisionResult`, responses 400, 401, 403, 404, 409 as `sendBackTickets`. `npm run generate:api`.
- **Web app.** New `features/tickets/components/TicketPublicationActions.tsx`, rendered by `TicketDetailPage` above
  `ReviewTicketDetail` (not changed: it belongs to `ticket-review`). It reads the ticket with
  `useGetReviewTicketDetail(ticketId)` — the same query key as `ReviewTicketDetail`, so one request — and shows
  Unpublish when `status === 'PUBLISHED'`, Re-publish when `'UNPUBLISHED'`, both only when
  `hasRole('PROJECT_MANAGER') && isProjectManagerOf(ticket.project.projectId)` (the fallback rule `canDecide` uses);
  otherwise it renders nothing. Click → shared `ConfirmDialog` (`MSG21`, `isDanger`; `MSG23`) → generated
  `useUnpublishTickets` / `useRepublishTickets` with `{ ticketIds: [ticketId] }` → `showMessage('MSG22' | 'MSG24')`.
  `onSettled` (success or error): invalidate the detail query, the Ticket List root key and the Review History key,
  so the page shows the stored status (AC-009.8–AC-009.10). Errors use the global error toast of the app (code of the
  ProblemDetail, else MSG06). MSW: the generated handlers answer `{ count: 1 }`.
  `ponytail:` the mock ticket data belongs to `features/review` and does not change status in mock mode; the tests
  drive the status with per-test MSW overrides. Share the mock state when a demo on mocks needs it.

| Decision | Choice | Alternatives considered | Rationale |
|----------|--------|-------------------------|-----------|
| Endpoints (D36, user) | `POST /reviews/unpublish`, `/reviews/republish` in `ReviewDecisionController`, batch body | `POST /tickets/{id}/unpublish` in a new `ticket` controller | `ticket` gets the managed Projects of the caller from `review`; a `ticket` controller would call `review` (wrong direction) or need a real `ProjectAccess` |
| Index (D34, user) | Keep the vector, rely on the Published filter of `search` | Delete the vector on `TicketsUnpublishedEvent` | No change in `search`; re-publish is instant and needs no Ollama |
| History (D35, user) | Store `review_decision` rows + audit | Audit only; also list them in Review History | UC-25 needs the record; listing is the contract of `ticket-review` |
| Removed Project (D37, user) | 403 as every review decision today | Count Removed Projects as managed | One fix for all decisions, in `ticket-review` |
| Audit action of re-publish | `AuditAction.PUBLISH` | A new `REPUBLISH` action | The enum already has PUBLISH / UNPUBLISH as a pair; the review decision row tells re-publish from Approve |
| Buttons | Own component in `features/tickets` above `ReviewTicketDetail` | Add them to the action bar of `ReviewTicketDetail` | Keeps `ticket-review` code untouched; the detail query is shared by its key |

**Files.** Modify `T/service/TicketDecisionService.java`, `T/service/impl/TicketDecisionServiceImpl.java`,
`T/entity/ReviewDecisionRecord.java`, `backend/src/main/java/vn/tsolve/review/controller/ReviewDecisionController.java`,
`db.changelog-master.xml`, `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**`,
`frontend/src/features/tickets/pages/TicketDetailPage.tsx`. Create `0NN-review-decision-publication.sql`,
`frontend/src/features/tickets/components/TicketPublicationActions.tsx` (+ `.test.tsx`). Tests
`TT/service/impl/TicketPublicationTest.java`, `TT/controller/TicketPublicationIT.java` (module.md `paths` gains
`**/TicketPublication*` and the controller file).

**Edge cases.**

| # | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-----------|----------|----------|-------------------|-----------|
| P1 | Concurrency | Two PMs (or a double click) unpublish the same ticket | High | Row lock (`lockForReview`); the second sees Unpublished → 409 MSG06, one decision row, one event | Task 27 |
| P2 | State | Unpublish a Pending review, Rejected, Expired or already Unpublished ticket; re-publish a Published one | High | 409 MSG06, nothing changed, no decision, no event (AC-009.3) | Task 27 |
| P3 | Authorization | PM of another Project; ticket of another Workspace; Ticket Resolver calls the endpoint | High | 403 MSG08; 404 MSG04; 403 (AC-009.4–AC-009.6) | Task 27 |
| P4 | Authorization | Ticket of a Removed Project (GBR-21) | Medium | 403 MSG08 (D37) — known gap, `ticket-review` | Task 27 |
| P5 | Data | Batch with one wrong-status ticket among valid ones | High | Whole batch refused, none changed (AC-009.3) | Task 27 |
| P6 | Data | Re-publish keeps Solution, Evidence, feedback, review due date; no new review | Medium | Only `status` and `updatedAt` change (AC-009.2) | Task 27 |
| P7 | Integration | Unpublished ticket in Search / Suggestion / Chatbot; re-published one | High | Left out / returned — `search` AC-007.1, AC-010.3, AC-011.3 (existing tests) | — |
| P8 | Data | Existing rows and the new check constraint | Medium | Constraint only widens; rollback restores the four values | Task 27 (migration runs in the IT) |
| P9 | Integration | A report that counts `review_decision` rows without filtering the decision | Medium | Grep the readers of `review_decision` before merging; history and dashboard queries filter by decision | Task 27 |
| P10 | UI state | Status changed elsewhere while the page is open (stale button) | Medium | 409 → error toast, detail reloaded, the right button shows (AC-009.10) | Task 28 |
| P11 | UI | Double click on OK in the dialog | Medium | `isConfirming` disables OK while the request runs | Task 28 |
| P12 | UI roles | Ticket Resolver, or PM of another Project, opens the detail | High | No button (AC-009.8, AC-009.9) | Task 28 |

**Decided in planning (builder check, 2026-11-15).**
- Decided in planning: `TicketTransitions` (`T/service/impl/`) and `TicketsUnpublishedEvent` already exist (Task 13); Task 27 creates neither (builder check).
- Decided in planning: the buttons use only the session rule `hasRole('PROJECT_MANAGER') && isProjectManagerOf(projectId)`; `ticket.canDecide` is about review decisions and is not read. A Removed Project the session still lists gets the button and then the 403 of D37, shown as AC-009.10 (builder check).
- Decided in planning: the button is disabled while the detail query is fetching or the request runs (builder check).
- Decided in planning: AC-009.7 has two tests — bean validation of the body over HTTP (empty, more than 500) in `TicketPublicationIT`, the duplicate id in `TicketPublicationTest` (builder check).
- Decided in planning: the tests of AC-009.2 tell re-publish from Approve by the `REPUBLISH` decision row; the audit action is `PUBLISH` for both (builder check).
- Decided in planning: `TicketPublicationIT` also checks that an UNPUBLISH / REPUBLISH row publishes no `ReviewFeedbackDecidedEvent`, so no writer email is sent (P9) (builder check).
- Decided in planning: the YAML reuses the schemas `ApproveTicketsRequest` and `ReviewDecisionResult`; the Java records stay `ApproveRequest` / `DecisionResult` (builder check).
- Decided in planning: MSG21 / MSG23 are shown without params — `msg()` fills `{tracker_name}` from `shared/config/tracker.ts` (builder check).
- Decided in planning: errors go to the global mutation handler; the component passes `handlesOwnErrors` only if that handler shows nothing for a 4xx without a code, and then shows MSG06 itself (AC-009.10) (builder check).
- Decided in planning: invalidation uses the generated `getGetReviewTicketDetailQueryKey(ticketId)`, the root of `getGetTicketsQueryKey()` and `getGetReviewHistoryQueryKey()`, as `useReviewDecisions.ts` does (builder check).
- Decided in planning: the tests of AC-009.8 / AC-009.9 assert the refetched status on screen (the MSW override answers Unpublished / Published on the second read), not only the invalidation (builder check).
- Decided in planning: the changeset takes the next free number after rebasing on `main`; its rollback restores the four values of changeset 024 (builder check).

## Change 2026-11-15 (2): One decision table, unused Solution tables removed (KAN-137, STORY-010)

Scope is D38: the `ticket` table is already one, so the change is the decision tables and the dead tables. No endpoint,
screen or message changes; the OpenAPI file and the web app are not touched.

**Data model.** `ticket_decision` — every decision on a Ticket, never updated:

| Column | Type | From |
|---|---|---|
| `ticket_decision_id` | UUID PK, default `gen_random_uuid()` | `review_decision.review_decision_id` (ids kept) |
| `ticket_id` | UUID NOT NULL → `ticket (ticket_id)` | both |
| `reviewer_id` | UUID NULL → `app_user (id)` | `review_decision.project_manager_id` |
| `reviewer` | VARCHAR(150) NULL | `ticket_import_decision.reviewer` (sign-in name) |
| `decision` | VARCHAR(20) NOT NULL, CHECK in APPROVED, REJECTED, CHANGES_REQUESTED, SEND_BACK, UNPUBLISH, REPUBLISH (changeset 037, KAN-136) | both; import `APPROVE` becomes `APPROVED` |
| `feedback` | TEXT NULL | `review_decision.reason_or_comment`, `ticket_import_decision.reason` |
| `decided_at` | TIMESTAMPTZ NOT NULL | both |

`CHECK (reviewer_id IS NOT NULL OR reviewer IS NOT NULL)` (D40, AC-010.6). Indexes on `ticket_id` and `decided_at`
(the two that `review_decision` has, renamed).

**Two new changesets** (D41; one logical change each), with the next free numbers (038 and 039, after `037-review-decision-publication` of KAN-136), included last
in `db.changelog-master.xml`.

`NNN-ticket-decision.sql`:
1. `ALTER TABLE review_decision RENAME TO ticket_decision`, then rename its columns, constraint and two indexes,
   drop NOT NULL on `reviewer_id`, add `reviewer` and the reviewer CHECK. Renaming keeps every review row, its id and
   its foreign keys with no copy.
2. `INSERT INTO ticket_decision (ticket_id, reviewer, decision, feedback, decided_at) SELECT ticket_id, reviewer,
   CASE decision WHEN 'APPROVE' THEN 'APPROVED' ELSE decision END, reason, decided_at FROM ticket_import_decision`
   — a value the CHECK does not allow fails the changeset instead of being relabelled (risk review) — then
   `DROP TABLE ticket_import_decision` (its sequence goes with it).

`NNN-drop-legacy-solution-tables.sql`:
3. `DROP TABLE solution_source, solution_approval, reuse_record, solution, resolved_ticket_snapshot` (D42). No
   `CASCADE`: if anything else still references them the changeset must fail, not silently drop it.

Rollback (changed while implementing, Tasks 29–30 logs): both changesets carry a `--rollback`, because
`WorkspaceInfoRepositoryIT` (BUG-243) rolls back every changeset after 035. 038's reverses the rename and moves the
import rows back; 039's recreates the five tables empty. A deployed database is still fixed forward and its dropped
rows come back only from its backup (`backend/AGENTS.md` §7).

**Code.** One entity `TicketDecision` (`T/entity/TicketDecision.java`) replaces `ReviewDecisionRecord` and
`TicketReviewDecision`: the fields above, the `Decision` enum of `ReviewDecisionRecord`, the existing review
constructor, and `static TicketDecision approvedByImport(UUID ticketId, String reviewer)` (sets `decidedAt` to now).
The id is a UUID made by Hibernate, so the batched insert of an import Finish stays batched without a sequence.
One repository `TicketDecisionRepository` (the renamed `ReviewDecisionRecordRepository`, native queries moved to the
new table and column names). `ReviewDecision` (enum with only `APPROVE`) and `TicketReviewDecisionRepository` are
deleted. Writers change only the type they build: `ImportConfirmServiceImpl`, `ManualTicketServiceImpl`
(`approvedByImport`), `TicketDecisionServiceImpl` (review constructor). `ReviewFeedbackDecidedEvent` keeps carrying
decision ids — they are the same ids.

**Reads stay review-only (D39, D43).** Each read that means "review decisions" also requires
`ticket.source = 'SOLUTION_FORM'`: the two Review History queries and their count, `findWriterNotification`, and the
`decisions` part of `DashboardRepository.findOutcomeCounts` (its `imports` part already takes
`t.source <> 'SOLUTION_FORM'`, so an imported Ticket is counted once). `TicketRepository.findJiraFeedback` and
`findJiraRejection` already have that filter; only the entity name changes.

**Dead code.** Delete the package `vn.tsolve.knowledge` (6 files: `Solution`, `SolutionController` at
`/api/solutions`, `SolutionRepository`, `SolutionRequest`, `SolutionService`, `SolutionStatus`). Nothing imports it
and it has no test; the web app and the Jira App do not call `/api/solutions`.

**Decided in planning (builder check).**
- A migration test follows `review/ReviewStorageIT`, which already seeds rows before a changeset and checks them after.
- Tests of other modules that name the old tables in SQL (`TRUNCATE ticket_import_decision`, inserts into
  `review_decision`) are updated in the same task; they assert nothing new.
- `evidence` and the `workspace` table of changeset 001 are left as they are (D38; `workspace` is not named in KAN-137).

**Edge cases & risks.**

| # | Case | Severity | Handling |
|---|---|---|---|
| E1 | A database holds import decisions whose Ticket was deleted | Low | Not possible: `ticket_import_decision.ticket_id` has a foreign key to `ticket` (changeset 008). |
| E2 | Another branch adds a changeset or a query that still names `review_decision` or `ticket_import_decision` and merges later | High | Task 29 greps `backend/src` for both names after rebasing on `main`, before the commit; the PR text names the rename for reviewers. |
| E3 | The dashboard double-counts imports after the merge | High | AC-010.5 test: one imported Ticket and one approved Solution Form Ticket give `approved = 2`. |
| E4 | Review History or the writer's email picks up an import row | High | AC-010.5 tests on both queries with an import decision present. |
| E5 | A view, trigger or function references a dropped or renamed table | Medium | Drops without `CASCADE` fail loudly; the migration test runs every changeset on an empty PostgreSQL and on a seeded one. |
| E6 | The `dashboard-audit` spec is "changed since approval", so the code gate blocks `analytics/repository/DashboardRepository.java` | Medium | `DashboardRepository.java` and `DashboardOutcomesIT.java` are added to this module's `paths` (a one-predicate change on a query that already reads `ticket`); the `dashboard-audit` spec is not changed. |
| E7 | Rows written by old code against the new schema during a deploy | Low | One deployable (modular monolith): schema and code ship together. |

## Risk Review — 2026-11-15 — Verdict: CAUTION

Scope: "Change 2026-11-15" (KAN-137, STORY-010) — a schema change on existing data in every Workspace database.
Checked against the code on `main` (14c3de0) and the open pull requests of that day.

Agreements (four or more personas):
- Renaming `review_decision` beats creating a table and copying: ids, foreign keys, indexes, grants and any later row
  policy stay on the table, and only the import rows move.
- The drops are safe in code: nothing but the `knowledge` package reads the five tables, no foreign key points into
  them from outside changeset 001, and no view, trigger or function names any table of this change.
- The real risk is merge order, not the SQL. Re-checked 2026-11-15 after rebasing: #234, #235, #236 and #241 are
  merged; #248 (KAN-136 code, changeset 037, `ReviewDecisionRecord` gains UNPUBLISH / REPUBLISH) merged too, so this
  work now sits on `main` directly.
- AC-010.5 is the one place where behavior could change by accident; its tests are not optional.

| Topic | Architect | Security | Performance | User | Devil's advocate | Resolution |
|-------|-----------|----------|-------------|------|------------------|------------|
| One table for two kinds of decision | Fits the one ticket model; `reviewer_id` / `reviewer` is a small wart, the CHECK keeps it honest | No new exposure: no API returns `reviewer`; Review History joins `app_user` on `reviewer_id` | Import rows (up to 100 per Finish) now sit in the table Review History scans | Nothing visible changes | Two tables work today; the gain is only tidiness | Keep. The source filter (D43) plus the existing `decided_at` index is enough at this size; `ponytail:` a partial index `WHERE reviewer_id IS NOT NULL` if Review History slows |
| Telling review rows from import rows by the Ticket's source (D43) | No extra column; relies on "an imported Ticket is never reviewed" | — | One more predicate on a join that already exists | — | UC-25 re-publish (KAN-136, PR #241) may record a decision on an imported Ticket; the filter would hide it from Review History | Keep D43. KAN-136 must say whether a re-publish is a decision row; if it is and it must show, that Change adds a column then |
| Hard-coded `'APPROVED'` for copied import rows | — | — | — | — | The column has no CHECK; a value other than `APPROVE` would be relabelled silently | Design change: copy with `CASE decision WHEN 'APPROVE' THEN 'APPROVED' ELSE decision END`, so an unknown value hits the CHECK and the changeset fails |
| Dropping five tables for good | Dead since the ticket model replaced them | Removes `/api/solutions`, which any signed-in user could call (`/api/**` is only authenticated) and which ignores Project scope | — | README still documents `/api/solutions` | A deployed database may hold rows made through that endpoint; a drop cannot be undone | Keep the drop (user decision, AC-010.7). Task 30 records the row counts of the deployed databases in the pull request and updates README |
| One changeset run on every Workspace database at startup | Matches `architecture.md` (tenant changelog, applied per database) | Runs as the owner user as every changeset does | `RENAME` and `DROP` take a short exclusive lock; the copy is one `INSERT … SELECT` of at most one row per imported Ticket | A failed changeset stops the app from starting for that database | — | Liquibase runs each formatted-SQL changeset in one transaction and PostgreSQL DDL is transactional: a failure leaves the database as it was. The migration test runs on an empty and on a seeded database |
| Renaming the entity to `TicketDecision` | The name must say what the table is | — | — | — | Keeping `ReviewDecisionRecord` would touch fewer files and conflict less with open pull requests | Rename, but land Task 29 after #234–#236 and #241 are merged or rebased onto it, in one pull request of its own |

| Risk | Severity | Mitigation | Task |
|------|----------|------------|------|
| An open pull request adds a changeset or a query naming `review_decision`, `ticket_import_decision`, `ReviewDecisionRecord` or `TicketReviewDecision`, or takes changeset number 034/035 | High | Task 29 starts from an up-to-date `main` after the open pull requests of this module merge (or their authors agree to rebase); number chosen at that moment; grep before the commit; the pull request text names the rename | 29 |
| Dashboard figures or Review History change after the merge (AC-010.5) | High | Tests with an import decision present for Review History, the writer's email and the dashboard figures | 29 |
| An import decision with an unexpected value is relabelled as APPROVED | Medium | `CASE` mapping; the CHECK rejects the rest and the changeset rolls back | 29 |
| `dashboard-audit` code is locked (spec changed since approval), and Task 29 edits its `DashboardRepository` | Medium | The two files are added to this module's `paths` (E6) | 29 |
| Rows in the dropped tables of a deployed database are lost | Medium | Row counts of each deployed database recorded in the pull request before merge; the regular backup is the way back | 30 |
| README documents endpoints that no longer exist | Low | README's `/api/solutions` rows removed | 30 |
| A later re-publish decision on an imported Ticket is hidden by the source filter | Low | Settled by KAN-136 (D35): unpublish / re-publish rows carry the reviewer id and Review History leaves them out (`HISTORY_ONLY_DECISIONS`); when `ticket-review` shows them, that Change looks at D43 again | — |

No STOP condition: no auth bypass or data exposure is added (one unscoped endpoint is removed), the change fits the
architecture, the cost is small, and the assumptions were checked in the code.

## Change 2026-11-16: the pull reads the Workspace's service account (KAN-345)

| Decision | Chosen | Alternatives | Rationale |
|---|---|---|---|
| Credential source | `tracker.service.JiraServiceAccount.current()` once at the start of each pull (AC-001.12): site URL for the `/browse/{key}` links, cloud id and scoped token, Bearer through the gateway | `JIRA_SITE_URL`/`JIRA_CLOUD_ID`/`JIRA_API_TOKEN` (removed) | BUG-308; one account per Workspace, the same one tracker uses (KAN-344) |
| No account | `JiraUnavailableException("The Jira connection is not configured")` before any call → MSG50 + Failed batch (AC-001.14), unchanged path of AC-001.7 | A new message | AC-001.7 already covers "not configured" |
| Client per pull | `JiraRestIssueSource` builds the `RestClient` from the account in `resolvedIssues` and passes it with the site URL down to the issue and comment reads; the builder (timeouts, retries, `Sleeper`, `Clock`) is unchanged | Cache a client per Workspace | A pull is a few calls; nothing to invalidate when the Admin replaces the token |
