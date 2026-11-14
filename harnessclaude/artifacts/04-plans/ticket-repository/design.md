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

## Change 2026-11-15: Unpublish / re-publish (KAN-136, STORY-007)

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
  `TicketDecisionServiceImpl`, in this order, as `sendBack` does: `validatedIds` (400, AC-007.7) →
  `tickets.lockForReview(ids)` → `validateScope` (404 MSG04, then 403 MSG08; AC-007.4, AC-007.5) →
  `transitions.move(batch, to, now)` — it checks the whole batch first and throws MSG06 (409) before it changes any
  Ticket, then publishes the one event (AC-007.3, AC-005.4, AC-005.5) → one `ReviewDecisionRecord` per Ticket
  (`UNPUBLISH` / `REPUBLISH`, no feedback, `now`) → `audit.record(UNPUBLISH | PUBLISH, "TICKET", id)` per Ticket
  (AC-007.1, AC-007.2). `Ticket.moveTo` sets only `status` and `updatedAt`, so the Solution, Evidence, feedback and
  review due date stay (AC-007.2). No source rule: a ticket of any source can be unpublished.
- **Controller.** `ReviewDecisionController` (`review`, D20) gets `@PostMapping("/unpublish")` (operationId
  `unpublishTickets`) and `@PostMapping("/republish")` (`republishTickets`), body `ApproveRequest`, answer
  `DecisionResult`, managed Projects from `managedProjects(actor)` — the same three lines as `sendBack`. The class
  already carries `@PreAuthorize` Project Manager (AC-007.6).
- **Data.** Liquibase changeset `ticket:0NN-review-decision-publication` (next free number in
  `db/changelog/changes`, 034 on 2026-11-15) replaces `ck_review_decision` to allow `'UNPUBLISH', 'REPUBLISH'` beside
  the four values of changeset 024, with a `--rollback` that restores them. `ReviewDecisionRecord.Decision` gains
  `UNPUBLISH`, `REPUBLISH` (the column is `VARCHAR(20)`). No new table or column.
- **Review History (D19).** The history queries of `ReviewDecisionRecordRepository` filter
  `d.decision in (:decisions)`, so the new rows stay out of the list until `ticket-review` adds them to its filter.
  Nothing to change here.
- **Search (D18).** Nothing. `TicketsPublishedListener` embeds a re-published ticket again; the upsert makes that
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
  so the page shows the stored status (AC-007.8–AC-007.10). Errors use the global error toast of the app (code of the
  ProblemDetail, else MSG06). MSW: the generated handlers answer `{ count: 1 }`.
  `ponytail:` the mock ticket data belongs to `features/review` and does not change status in mock mode; the tests
  drive the status with per-test MSW overrides. Share the mock state when a demo on mocks needs it.

| Decision | Choice | Alternatives considered | Rationale |
|----------|--------|-------------------------|-----------|
| Endpoints (D20, user) | `POST /reviews/unpublish`, `/reviews/republish` in `ReviewDecisionController`, batch body | `POST /tickets/{id}/unpublish` in a new `ticket` controller | `ticket` gets the managed Projects of the caller from `review`; a `ticket` controller would call `review` (wrong direction) or need a real `ProjectAccess` |
| Index (D18, user) | Keep the vector, rely on the Published filter of `search` | Delete the vector on `TicketsUnpublishedEvent` | No change in `search`; re-publish is instant and needs no Ollama |
| History (D19, user) | Store `review_decision` rows + audit | Audit only; also list them in Review History | UC-25 needs the record; listing is the contract of `ticket-review` |
| Removed Project (D21, user) | 403 as every review decision today | Count Removed Projects as managed | One fix for all decisions, in `ticket-review` |
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
| P1 | Concurrency | Two PMs (or a double click) unpublish the same ticket | High | Row lock (`lockForReview`); the second sees Unpublished → 409 MSG06, one decision row, one event | Task 19 |
| P2 | State | Unpublish a Pending review, Rejected, Expired or already Unpublished ticket; re-publish a Published one | High | 409 MSG06, nothing changed, no decision, no event (AC-007.3) | Task 19 |
| P3 | Authorization | PM of another Project; ticket of another Workspace; Ticket Resolver calls the endpoint | High | 403 MSG08; 404 MSG04; 403 (AC-007.4–AC-007.6) | Task 19 |
| P4 | Authorization | Ticket of a Removed Project (GBR-21) | Medium | 403 MSG08 (D21) — known gap, `ticket-review` | Task 19 |
| P5 | Data | Batch with one wrong-status ticket among valid ones | High | Whole batch refused, none changed (AC-007.3) | Task 19 |
| P6 | Data | Re-publish keeps Solution, Evidence, feedback, review due date; no new review | Medium | Only `status` and `updatedAt` change (AC-007.2) | Task 19 |
| P7 | Integration | Unpublished ticket in Search / Suggestion / Chatbot; re-published one | High | Left out / returned — `search` AC-007.1, AC-010.3, AC-011.3 (existing tests) | — |
| P8 | Data | Existing rows and the new check constraint | Medium | Constraint only widens; rollback restores the four values | Task 19 (migration runs in the IT) |
| P9 | Integration | A report that counts `review_decision` rows without filtering the decision | Medium | Grep the readers of `review_decision` before merging; history and dashboard queries filter by decision | Task 19 |
| P10 | UI state | Status changed elsewhere while the page is open (stale button) | Medium | 409 → error toast, detail reloaded, the right button shows (AC-007.10) | Task 20 |
| P11 | UI | Double click on OK in the dialog | Medium | `isConfirming` disables OK while the request runs | Task 20 |
| P12 | UI roles | Ticket Resolver, or PM of another Project, opens the detail | High | No button (AC-007.8, AC-007.9) | Task 20 |

**Decided in planning (builder check, 2026-11-15).**
- Decided in planning: `TicketTransitions` (`T/service/impl/`) and `TicketsUnpublishedEvent` already exist (Task 13); Task 19 creates neither (builder check).
- Decided in planning: the buttons use only the session rule `hasRole('PROJECT_MANAGER') && isProjectManagerOf(projectId)`; `ticket.canDecide` is about review decisions and is not read. A Removed Project the session still lists gets the button and then the 403 of D21, shown as AC-007.10 (builder check).
- Decided in planning: the button is disabled while the detail query is fetching or the request runs (builder check).
- Decided in planning: AC-007.7 has two tests — bean validation of the body over HTTP (empty, more than 500) in `TicketPublicationIT`, the duplicate id in `TicketPublicationTest` (builder check).
- Decided in planning: the tests of AC-007.2 tell re-publish from Approve by the `REPUBLISH` decision row; the audit action is `PUBLISH` for both (builder check).
- Decided in planning: `TicketPublicationIT` also checks that an UNPUBLISH / REPUBLISH row publishes no `ReviewFeedbackDecidedEvent`, so no writer email is sent (P9) (builder check).
- Decided in planning: the YAML reuses the schemas `ApproveTicketsRequest` and `ReviewDecisionResult`; the Java records stay `ApproveRequest` / `DecisionResult` (builder check).
- Decided in planning: MSG21 / MSG23 are shown without params — `msg()` fills `{tracker_name}` from `shared/config/tracker.ts` (builder check).
- Decided in planning: errors go to the global mutation handler; the component passes `handlesOwnErrors` only if that handler shows nothing for a 4xx without a code, and then shows MSG06 itself (AC-007.10) (builder check).
- Decided in planning: invalidation uses the generated `getGetReviewTicketDetailQueryKey(ticketId)`, the root of `getGetTicketsQueryKey()` and `getGetReviewHistoryQueryKey()`, as `useReviewDecisions.ts` does (builder check).
- Decided in planning: the tests of AC-007.8 / AC-007.9 assert the refetched status on screen (the MSW override answers Unpublished / Published on the second read), not only the invalidation (builder check).
- Decided in planning: the changeset takes the next free number after rebasing on `main`; its rollback restores the four values of changeset 024 (builder check).
