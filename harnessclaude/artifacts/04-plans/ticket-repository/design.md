---
module: ticket-repository
date: 2026-11-07
---

# Design: ticket-repository

Scope: KAN-45 Jira import (UC-20) with Drafts — spec version 8468bbe0. Builds on the CSV import code of
`import-ticket-list` in `vn.tsolve.ticket` (same tables, same Import Preview, same Import Batch).

## Architecture Overview

```
web (features/ticket-import)                 backend vn.tsolve.ticket
 Import Tickets: Jira tab + Drafts  ──REST──▶ JiraImportController
 Import Preview (Jira)                         ├─ JiraImportService      pull → preview      (STORY-001)
 Import Ticket Preview popup                   ├─ JiraPreviewService     list/open/solution/remove (STORY-002)
                                               ├─ JiraFinishService      finish → Tickets    (STORY-003)
                                               ├─ ImportDraftService     draft save/list/delete (STORY-004)
                                               │
                                               ├─ ProjectAccess ──▶ project.ProjectService (Jira key, Active)
                                               └─ JiraIssueSource (port)
                                                    └─ JiraRestIssueSource ──HTTPS──▶ Jira Cloud REST v3
                                                         (site URL, email, API token from env — D2)
```

- **Pull** (`POST /imports/jira`): validate dates (MSG01 / MSG39) → `ProjectAccess.managesActive` (403) → Jira key
  from `ProjectAccess.jiraProjectKey` → `JiraIssueSource.resolvedIssues(key, from, to)` streams issues oldest first →
  drop keys that are already a `ticket.ticket_id` (one `IN` query per Jira page) → stop at 100 kept; one more kept
  issue means "more" (MSG35) → none kept: MSG04, no preview → otherwise, in one transaction, delete the caller's
  non-Draft preview (AC-001.10) and insert the preview with its issues and comments.
  A `JiraUnavailableException` from the source (credentials, timeout, 4xx/5xx, not configured) → `ImportBatchRecorder`
  writes a Failed batch of source `JIRA_IMPORT` in its own transaction → MSG50 (502).
- **Solutions** are written on preview rows; one request may target several Jira keys (Fill solution) — one
  `UPDATE … WHERE jira_key IN (…)` copies the five sections into each row (AC-002.3).
- **Finish** follows `ImportConfirmServiceImpl`: checks outside the transaction (404, 409 MSG01 when a row lacks a
  Solution, 409 MSG40, then 403), then one transaction: lock the preview row (`SELECT … FOR UPDATE`, a second
  Finish waits and then finds no preview → 404, AC-003.8), find the keys that are now Tickets → skipped (MSG38),
  insert the other Tickets + APPROVE decisions + Completed batch, delete the preview, publish
  `TicketsPublishedEvent` after commit (only when ≥ 1 Ticket). Any other failure: rollback, keep the preview, Failed
  batch through `ImportBatchRecorder`, MSG33 (500).
- **Draft** = the same preview with `draft_saved_at` set. Saving again updates `draft_saved_at` (AC-004.7).
  Cleanup job and the "replace my previous preview" step both filter `draft_saved_at IS NULL` (AC-004.6).

## Data Model

Liquibase `008-ticket-jira-import.sql` (author `ticket`), on top of `003-ticket-import-schema.sql`:

```sql
-- import_preview: Jira previews have no file; Drafts survive cleanup and replacement
ALTER TABLE import_preview ALTER COLUMN file_name DROP NOT NULL;
ALTER TABLE import_preview ADD COLUMN resolved_from  DATE;          -- Jira only
ALTER TABLE import_preview ADD COLUMN resolved_to    DATE;          -- Jira only
ALTER TABLE import_preview ADD COLUMN more_available BOOLEAN NOT NULL DEFAULT false;   -- MSG35
ALTER TABLE import_preview ADD COLUMN draft_saved_at TIMESTAMPTZ;   -- NULL = not a Draft
DROP INDEX uq_import_preview_owner;
CREATE UNIQUE INDEX uq_import_preview_owner_open ON import_preview (owner) WHERE draft_saved_at IS NULL;
CREATE INDEX idx_import_preview_owner_draft ON import_preview (owner, draft_saved_at DESC)
    WHERE draft_saved_at IS NOT NULL;

-- one row per pulled issue; Solution columns NULL until written
CREATE TABLE import_preview_issue (
    preview_id        BIGINT       NOT NULL REFERENCES import_preview (id) ON DELETE CASCADE,
    jira_key          VARCHAR(50)  NOT NULL,
    position          INT          NOT NULL,     -- resolution-date order, from 1
    summary           TEXT         NOT NULL,
    description       TEXT,                      -- plain text from ADF
    assignee          VARCHAR(100),              -- display name, NULL when unassigned (D4)
    resolved_at       TIMESTAMPTZ  NOT NULL,
    source_url        TEXT         NOT NULL,     -- {site}/browse/{key}
    comments          JSONB        NOT NULL DEFAULT '[]',   -- [{author, created, body}] (D5)
    symptoms          TEXT,
    root_cause        TEXT,
    resolution_steps  TEXT,
    workaround        TEXT,
    scope             TEXT,
    PRIMARY KEY (preview_id, jira_key)
);
CREATE INDEX idx_import_preview_issue_order ON import_preview_issue (preview_id, position);
```

- CSV keeps `import_preview_row`; Jira gets its own table because its rows are edited (Solutions) and keyed by Jira
  key, while CSV rows are read-only and keyed by row number.
- `ticket` gets `source = JIRA_IMPORT`, `ticket_id = jira_key`, `title = summary` cut to 200, `writer = assignee`,
  `resolved_date = resolved_at` in Vietnam time, `import_batch_id`. `uq_ticket_ticket_id` stays the guard against a
  key that became a Ticket during Finish (caught → that key is skipped, see Edge 6).
- `import_batch` for Jira: `file_name = NULL`, `published_count`, `removed_count = 0`, `error_count = skipped`.
- Ids stay `BIGSERIAL`: the UUID switch of KAN-248 (schema alignment) is a separate change that will convert these
  tables with the others.

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Jira access (D2, user) | `JiraIssueSource` port in `ticket.service`; `JiraRestIssueSource` reads `tsolve.jira.site-url`, `.email`, `.api-token` (env `JIRA_SITE_URL`, `JIRA_EMAIL`, `JIRA_API_TOKEN`, no defaults) | Wait for `workspace` / `project-integration`; per-Project token (SRS GBR-14) | UC-20 ships now; `workspace` replaces only the credential lookup later |
| Drafts (D3, user) | `import_preview.draft_saved_at` | A separate `import_draft` table with a JSON copy | Draft and preview are the same data; reopen = read the preview |
| Jira search API | `GET /rest/api/3/search/jql` with `jql=project = "{key}" AND resolved >= "{from}" AND resolved < "{to+1}" ORDER BY resolved ASC, key ASC`, `fields=summary,description,assignee,resolutiondate,comment`, `maxResults=100`, `nextPageToken` | Old `/rest/api/3/search` | The old endpoint is removed by Atlassian; the new one pages by token |
| Date boundaries | JQL dates as `yyyy-MM-dd`; the service account's Jira time zone must be Asia/Ho_Chi_Minh — `JiraRestIssueSource` also re-checks `resolutiondate` against the range in Vietnam time and drops issues outside it | Convert to UTC instants in JQL | JQL dates follow the account's profile time zone; the re-check keeps AC-001.1 exact even if the profile is wrong |
| Comments | Use the `comment` field of the search; when `comment.total > comment.comments.length`, page `GET /rest/api/3/issue/{key}/comment` | One comment call for every issue | Most issues have few comments: ~1 search call per 100 issues instead of 101 calls |
| ADF to text | `ticket.jira.AdfText` — walks `content` arrays, joins `text` nodes, newline per paragraph / list item / heading, `mention` → its text, unknown nodes skipped | Store raw ADF; render ADF in the web app | The popup shows plain text (spec AC-001.2); `tracker.AdfTextExtractor` is empty and can reuse it later |
| HTTP client | Spring `RestClient` with 10 s connect / 30 s read timeout (AC-001.7), basic auth `email:token` | WebClient, Feign | Already used in `ai` and `identity`; no new dependency |
| Project data | Add `Optional<String> jiraProjectKey(long projectId)` to `ticket.service.ProjectAccess`; implemented with a new `ProjectService.findJiraProjectKey(long)` in `project` | Read `ProjectRepository` from `ticket` | backend/AGENTS.md: other modules only through `service/` interfaces |
| Duplicate filter | `TicketRepository.findExistingTicketIds(Collection<String>)` per Jira page | Load every Ticket ID of the Project | Bounded memory; one query per 100 issues |
| Solution validation | Reuse the CSV rules (required sections trimmed non-blank, ≤ 10,000 characters in total) via a shared `SolutionRules` extracted from `CsvRowValidator` | Copy the checks | One rule, two callers; MSG01 lists missing sections, MSG02 for length |
| Endpoints | New `JiraImportController` + `ImportDraftController`; `ImportController` (CSV) unchanged | Grow `ImportController` | Keeps the finished CSV module's code and tests untouched |

Decided in planning (minor): previews of source Jira answer only the Jira endpoints (`/issues…`, `/solutions`,
`/remove-issues`, `/finish`) and CSV previews only the CSV ones; the wrong kind → 404. `/draft` and
`/imports/drafts` accept both kinds, but only Jira previews can be saved until CSV Drafts are added (a CSV preview
→ 404).

## File Structure
```
backend/src/main/resources/db/changelog/changes/008-ticket-jira-import.sql          new
backend/src/main/resources/db/changelog/db.changelog-master.xml                     + include
backend/src/main/resources/application.yml                                          + tsolve.jira.* (shared file)
.env.example                                                                         + JIRA_SITE_URL, JIRA_EMAIL, JIRA_API_TOKEN (shared file)
backend/src/main/java/vn/tsolve/project/service/ProjectService.java                 + findJiraProjectKey (project-integration)
backend/src/main/java/vn/tsolve/project/service/impl/ProjectServiceImpl.java        + findJiraProjectKey
backend/src/main/java/vn/tsolve/ticket/
  controller/JiraImportController.java, ImportDraftController.java
  dto/JiraPullRequest, JiraImportPreviewResponse, JiraPreviewIssueSummary, JiraPreviewIssueResponse,
      ApplySolutionRequest, RemoveIssuesRequest, JiraImportResultResponse, ImportDraftSummary, DraftSavedResponse
  entity/ImportPreviewIssue.java (+ ImportPreview: resolvedFrom, resolvedTo, moreAvailable, draftSavedAt)
  repository/ImportPreviewIssueRepository.java (+ ImportPreviewRepository, TicketRepository queries)
  service/JiraIssueSource.java, JiraIssue.java (record), JiraImportService, JiraPreviewService,
          JiraFinishService, ImportDraftService (+ ProjectAccess.jiraProjectKey)
  service/impl/…Impl for each, SolutionRules.java, ProjectServiceAccess.java (replaces StubProjectAccess for the key)
  jira/JiraRestIssueSource.java, JiraProperties.java, AdfText.java
  exception/JiraUnavailableException.java, InvalidDateRangeException.java, SolutionMissingException.java
backend/src/test/java/vn/tsolve/ticket/…  one test class per service + controller slice tests + JiraRestIssueSource with MockRestServiceServer
frontend/openapi/tsolve-api.yaml                                                     + endpoints of the spec
frontend/src/shared/api/generated/**                                                 regenerated
frontend/src/features/ticket-import/
  pages/ImportTicketsPage.tsx (Jira tab + Drafts list), pages/ImportPreviewPage.tsx (Jira mode)
  components/JiraPullForm.tsx, JiraIssuesTable.tsx, ImportTicketPreviewDialog.tsx, SolutionFields.tsx, DraftsList.tsx
  api/*.ts (TanStack Query hooks over the generated client), mocks/handlers.ts (+ Jira handlers)
```

## External Dependencies
- Jira Cloud REST API v3 (search/jql, issue comments) — reached with the Workspace service account (D2).
- No new library.

## Edge Cases & Risks

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | 001 | Security | API token in a log line or MSG50 text (e.g. exception message with the auth header) | Critical | MSG50 carries only HTTP status / error type; token never logged (NFR 2, AC-001.7) | Task 3 |
| 2 | 001 | External | Jira returns 401/403/429/5xx, times out, or env vars are empty | High | MSG50, Failed batch, no preview (AC-001.7) | Task 3, Task 4 |
| 3 | 001 | Data volume | Range holds 5,000 issues, most already Tickets | High | Pages through Jira until 101 kept issues or the end; cap the scan at 20 pages (2,000 issues) and set "more" if the cap is hit | Task 4 |
| 4 | 001 | Time | Issue resolved 23:30 on the "to" date in Vietnam, Jira profile in UTC | High | Kept (Vietnam-time re-check, Key Decisions) | Task 3 |
| 5 | 001 | Data | Issue with no description, no assignee, ADF with tables/mentions/code blocks | Medium | Description empty / writer empty / readable plain text | Task 3 |
| 6 | 003 | Concurrency | The Solution Form (UC-19) or another Finish creates a Ticket with the same key during Finish | High | Pre-check skips known keys; a unique-key violation on insert → re-read existing keys, retry once without them (MSG38); a second failure → MSG33 | Task 6 |
| 7 | 003 | Concurrency | Double click on Finish | High | `FOR UPDATE` on the preview; second request → 404, no second batch (AC-003.8) | Task 6 |
| 8 | 002 | Concurrency | Two tabs: one removes an issue while the other applies a Solution to it | Medium | Solution update ignores keys no longer in the preview; Finish reads the current rows | Task 5 |
| 9 | 002 | Validation | Solution with only whitespace in a required section; Workaround alone 10,001 characters | Medium | MSG01 / MSG02, no row changed | Task 5 |
| 10 | 001 | State | PM pulls again while an unsaved preview is open in another tab | Medium | Old non-Draft preview deleted; the old tab gets 404 → web app returns to Import Tickets | Task 4, Task 9 |
| 11 | 004 | State | Draft reopened after its Project became inactive | Medium | Opens; Finish → MSG40, Draft kept | Task 6 |
| 12 | 004 | Authorization | User B calls GET/DELETE on user A's Draft id | High | 404 (AC-004.5) | Task 7 |
| 13 | 003 | Data | Summary longer than 200 characters | Medium | Title cut to 200 characters (`ticket.title` limit); the popup and preview show the full summary | Task 6 |
| 14 | 001 | Data | Jira key longer than 50 characters | Low | Not possible in Jira (key ≤ 10 + number); stored as is | — |
| 15 | 004 | Cleanup | Cleanup job runs while a Draft is older than 24 h | High | Kept (AC-004.6) | Task 7 |
| 16 | 001 | Config | `JIRA_SITE_URL` with a trailing slash or without https | Low | Trailing slash trimmed; non-https rejected at start-up | Task 3 |

## Risks
- **Schema alignment (KAN-248)** will move ids to UUID and may rename these tables; whoever merges second rebases
  their migration. Keep `008` small.
- **Shared files** (`application.yml`, `.env.example`, `project/service`) need their owners' approval in the PR.
- **Service account permissions:** the account needs Browse projects on every linked Jira project; a missing
  permission looks like "no issues" in JQL, not an error. The web app's MSG04 text cannot tell these apart.
