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
