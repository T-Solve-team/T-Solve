---
module: import-ticket-list
date: 2026-10-21
---

# Design: import-ticket-list

Spec: `artifacts/03-modules/import-ticket-list/spec.md` (approved, version 494d550f). This design covers KAN-31 CSV import (STORY-001..006). It is **backend + OpenAPI contract only**: the React screens are a separate frontend task that generates its client from the contract (decided 2026-10-21). The change of 2026-11-01 (STORY-007..011, D10-D15) adds the web screens: see **Change 2026-11-01** at the end.

## Architecture Overview

Package `vn.tsolve.ticket`, layered per `backend/AGENTS.md` §1.

```
POST /imports/csv ──► ImportController ──► CsvImportService
                                               │ 1. ProjectAccess.managesActive(user, projectId)  (403)
                                               │ 2. CsvTicketParser.parse(stream) → header + rows   (MSG27/29/04)
                                               │ 3. CsvRowValidator.validate(rows, existingIds)      (MSG01/02/31/39/78)
                                               │ 4. replace caller's old preview; save ImportPreview + rows
                                               ▼
                                          ImportPreviewResponse

POST /imports/previews/{id}/confirm ──► ImportConfirmService
     @Transactional: re-check Project Active (MSG40) and ticket ids (MSG33)
                     → save Tickets (PUBLISHED) + TicketReviewDecision(APPROVE) + ImportBatch(COMPLETED)
                     → delete preview → publish TicketsPublishedEvent (delivered AFTER_COMMIT)
     on failure: ImportBatchRecorder.recordFailed(...) in REQUIRES_NEW → MSG33

@Scheduled daily ──► PreviewCleanupJob → deletes previews older than 24 h, one by one (AC-006.3)
```

- **Parsing** (`CsvTicketParser`, no Spring):
  - Wraps the upload stream in a strict UTF-8 `CharsetDecoder` (`CodingErrorAction.REPORT`) and strips a leading `﻿`.
  - Parses with Commons CSV, `CSVFormat.RFC4180` with the header taken from the first record.
  - A `MalformedInputException` or a CSV parse error raises `CsvFileRejectedException(MSG27)`.
  - Header names are trimmed and lower-cased. A missing required column raises `MSG29` listing the missing columns. Zero records raises `MSG04`.
- **Validation** (`CsvRowValidator`, no Spring, pure function):
  - Takes the parsed rows and the set of `ticket_id`s that already exist, which the service loads with one `findExistingTicketIds(Collection)` query.
  - Returns each row with status VALID/ERROR and its list of `RowError(code, field, params)`.
  - Bean Validation is not used, because the rules span fields and need the file-wide duplicate check.
- **Size limit:** `spring.servlet.multipart.max-file-size=10MB` and `max-request-size=11MB`. `GlobalExceptionHandler` maps `MaxUploadSizeExceededException` to 413 with `code=MSG28`. The service also checks `file.getSize()` (AC-001.4), which keeps the rule testable without the servlet.
- **Paging (risk review):** the upload returns counts and the first page (size 50) of `ImportPreviewRowSummary`, with each Solution section cut to 200 characters. `GET /imports/previews/{id}/rows?page&size&status` pages through the rest (size ≤ 100, ordered by `row_no`). Full text comes only from the row-detail endpoint.
- **Errors:** `GlobalExceptionHandler` moves to RFC 9457 `ProblemDetail` with `code`, `params` and `fieldErrors` properties (the shape `frontend/src/shared/api/errors.ts` already reads), as `backend/AGENTS.md` §5 requires. Message text comes only from the SRS. A new `MessageCodeException` (abstract, carrying `code` + `params`) is the parent of the module's exceptions.

## Data Model

New changeset `003-ticket-import-schema.sql`:

```sql
CREATE TABLE import_batch (               -- never updated or deleted
    id               BIGSERIAL PRIMARY KEY,
    project_id       BIGINT       NOT NULL,
    performed_by     VARCHAR(150) NOT NULL,           -- authenticated user name
    source           VARCHAR(20)  NOT NULL,           -- CSV (JIRA_IMPORT, MANUAL later)
    file_name        VARCHAR(255),
    published_count  INT          NOT NULL,
    error_count      INT          NOT NULL,
    removed_count    INT          NOT NULL,           -- skipped = error + removed
    status           VARCHAR(20)  NOT NULL,           -- COMPLETED, FAILED
    error            TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_import_batch_project ON import_batch (project_id, created_at DESC);
-- ticket and ticket_import_decision: ALTER SEQUENCE … INCREMENT BY 50 (batching, see Key Decisions)

CREATE TABLE ticket (
    id                BIGSERIAL PRIMARY KEY,
    ticket_id         VARCHAR(50)  NOT NULL,
    project_id        BIGINT       NOT NULL,            -- FK added by the workspace module
    source            VARCHAR(20)  NOT NULL,            -- SOLUTION_FORM, JIRA_IMPORT, CSV, MANUAL
    status            VARCHAR(20)  NOT NULL,            -- PUBLISHED for imports
    title             VARCHAR(200) NOT NULL,
    description       TEXT,
    writer            VARCHAR(100),
    resolved_date     DATE,
    symptoms          TEXT NOT NULL,
    root_cause        TEXT NOT NULL,
    resolution_steps  TEXT NOT NULL,
    workaround        TEXT,
    scope             TEXT NOT NULL,
    import_batch_id   BIGINT REFERENCES import_batch (id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ticket_ticket_id UNIQUE (ticket_id)
);
CREATE INDEX idx_ticket_project_status ON ticket (project_id, status);

CREATE TABLE ticket_import_decision (
    id           BIGSERIAL PRIMARY KEY,
    ticket_id    BIGINT       NOT NULL REFERENCES ticket (id),
    reviewer     VARCHAR(150) NOT NULL,
    decision     VARCHAR(30)  NOT NULL,               -- APPROVE (REJECT, REQUEST_CHANGES later)
    reason       TEXT,
    decided_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE import_preview (
    id           BIGSERIAL PRIMARY KEY,
    owner        VARCHAR(150) NOT NULL,
    project_id   BIGINT       NOT NULL,
    source       VARCHAR(20)  NOT NULL,               -- CSV
    file_name    VARCHAR(255) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_import_preview_owner ON import_preview (owner);   -- one preview per PM (NFR 2)
CREATE INDEX idx_import_preview_created ON import_preview (created_at);

CREATE TABLE import_preview_row (
    preview_id        BIGINT NOT NULL REFERENCES import_preview (id) ON DELETE CASCADE,
    row_no            INT    NOT NULL,                -- data row number in the file, from 1
    status            VARCHAR(10) NOT NULL,           -- VALID, ERROR, REMOVED
    ticket_id         TEXT, title TEXT, description TEXT, author TEXT, resolved_date TEXT,
    symptoms          TEXT, root_cause TEXT, resolution_steps TEXT, workaround TEXT, scope TEXT,
    errors            JSONB NOT NULL DEFAULT '[]',    -- [{code, field, params}]
    PRIMARY KEY (preview_id, row_no)
);
```

The preview rows keep raw text without length limits, so an Error row can still show what the file contained. Limits apply only to `ticket`.

`ticket.ticket_id` holds the business Ticket ID. Uniqueness is the DB constraint, plus the check in the validator and at Confirm.

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Required Solution sections (D1) | Symptoms, Root cause, Resolution steps, Scope | GBR-33 two sections | User, 2026-10-21: same completeness as the Solution Form |
| Ticket model (D2) | New `ticket` table with the Solution inside it | Extend `resolved_ticket_snapshot` + `solution` | One Solution per Ticket, reject duplicates. The old upsert contradicts MSG31 |
| Old snapshot code | Delete `ResolvedTicketSnapshot`, `ResolvedTicketRepository`, `ImportTicketRequest`, `TicketService`, `TicketController` (`/api/tickets/**`). **Keep** the `resolved_ticket_snapshot` table | Drop the table as well | No caller outside the package (checked). `solution_source` has an FK to the table, so dropping it would break `knowledge`. A later cleanup changeset can drop it. |
| Project source (D3) | `ProjectAccess` interface + `StubProjectAccess` (`@ConditionalOnMissingBean`; projects from `tsolve.stub.projects` config) | Minimal `project` table | User decision. No FK until the workspace module exists |
| CSV parser | Apache Commons CSV 1.12.0 | Jackson CSV, OpenCSV | RESEARCH-001: RFC 4180, streaming, header map, record numbers, no transitive dependencies |
| Header contract (D4) | Required `ticket_id,title,symptoms,root_cause,resolution_steps,scope`; optional `description,author,resolved_date,workaround`; extras ignored | Minimal set | User decision |
| Duplicates in the file (D5) | All copies ERROR, MSG78 | First row wins | User decision |
| Bad files (D6) | MSG27 / MSG29 / MSG04 | Auto-detect `;` | User decision; no new behaviour beyond the SRS |
| Lengths (D7) | 50 / 200 / 10,000 / 100; Solution ≤ 10,000 in total | 500 / none / 150 | User decision |
| Preview storage (D8) | DB tables, rows as columns + `errors` JSONB | In-memory cache | User decision; the later Drafts feature becomes a flag |
| Cleanup (D9) | Replace on next upload + `@Scheduled(cron = "0 0 1 * * *")` deleting previews older than 24 h | Replace only | User decision |
| All-or-nothing Confirm | One `@Transactional`. The Failed batch is written by `ImportBatchRecorder` in `REQUIRES_NEW` | Per-row commits | MSG33 says "No tickets were imported", and the Import Batch rule records failures |
| Search hand-off | `ApplicationEventPublisher` + `TicketsPublishedEvent`; listeners use `@TransactionalEventListener(AFTER_COMMIT)` | Direct call to the search module | Search is not built yet; this keeps Confirm independent of indexing |
| Error format | `ProblemDetail` + `code`, refactoring the existing `GlobalExceptionHandler` | Keep the `Map` body | `backend/AGENTS.md` §5 already requires it |
| Stub safety (risk review) | `StubProjectAccess` is always a bean (`@ConditionalOnMissingBean(ProjectAccess.class)`; the real implementation replaces it). It reads `tsolve.stub.projects: ${TSOLVE_STUB_PROJECTS:}` in the form `alice:1,2;bob:3`. When the value is empty or the user is not listed, the user manages no Project → 403 (fails closed) | A stub that lets every PM use every Project | Otherwise any PM could import into any Project, which is an authorization bypass |
| Insert batching (risk review) | `spring.jpa.properties.hibernate.jdbc.batch_size=100` + `order_inserts=true`. Preview rows (composite key) batch fully. `ticket` and `ticket_import_decision` ids use `@SequenceGenerator(allocationSize = 50)` on sequences altered to `INCREMENT BY 50` in 003 | Plain IDENTITY | IDENTITY turns off JDBC batching; 10,000 single inserts on Confirm would be slow |
| Who is the PM | `Authentication.getName()` stored as text (`performed_by`, `owner`, `reviewer`); role check `@PreAuthorize("hasRole('PROJECT_MANAGER')")` | User FK | No user table yet (identity deferred, architecture.md) |
| Decided in planning: approve decision table (builder check) | `ticket_import_decision`, created here (not `ticket_review_decision`, which `feat/ticket-review` creates; changeset 003 because both branches used 002 — PR review 24/10); the review module takes it over | Reuse `solution_approval` | `solution_approval` points at `solution`, not `ticket` |
| Decided in planning: audit event (builder check) | Not recorded in KAN-31; `AuditService` does not exist yet. Added when the audit module lands | Build a minimal AuditService here | Not in the approved spec. `backend/AGENTS.md` §5 lists import as audited, so this is a known gap |

## Builder-check decisions (2026-10-21)
| # | Item | Decided |
|---|------|---------|
| B1 | Failed Import Batch | Only for the two MSG33 cases (user). MSG40, 409 and 404 record nothing. A Failed batch has `published_count = 0`, `error_count`/`removed_count` from the preview, and `error` = exception class + MSG code, with no row content (AGENTS §9) |
| B2 | Real-DB tests | Testcontainers `postgresql` + `junit-jupiter` (Boot BOM versions), image `pgvector/pgvector:pg16`, one `ImportConfirmIT` (user). `backend/AGENTS.md` §8 gets a line saying so |
| B3 | Message codes | SRS v3456 numbering (user). The frontend catalog is still on v2; fixing it is a separate frontend task |
| B4 | Message payload | `ProblemDetail.code` = MSG code, `params` = placeholder values (MSG02: `field_name`, `max_length`; MSG29: `column_names`; MSG31/78: `ticket_id`; MSG30: `count`; MSG32: `created`, `skipped`). Response `message` fields carry `{code, params}`, never text. Texts live in SRS V.2 |
| B5 | Status codes | As in spec Interface Contracts |
| B6 | Upload check order | Role (403) → extension, size (MSG27/28) → Project access (403) → parse → validate |
| B7 | Parser details | Duplicate header name → MSG27 (Commons CSV rejects it). Blank lines are skipped (`ignoreEmptyLines`). `row_no` = CSV record number (a quoted multi-line field counts as one row). Missing file name → MSG27. Empty `rowNumbers` → 400 validation |
| B8 | Snippet length | 200 code points, like the length limits |
| B9 | Identity | Spring Security `Authentication` is the identity interface until the identity module exists (architecture.md updated) |
| B10 | Error rows can be removed | Yes; they then count as Removed (spec AC-003.1). MSG30 is recomputed on every response |

## Evaluation attempt 1 — fix decisions (2026-10-22)
| # | Item | Decided |
|---|------|---------|
| E1 | Confirm access (AC-004.11) | Before the transaction: `isActive` → MSG40 (409), then `managesActive(user, project)` → `ProjectAccessDeniedException` (403 MSG08). Neither records an Import Batch (user: MSG40 first) |
| E2 | Double Confirm (AC-004.12) | Inside the transaction the preview is re-loaded with `@Lock(PESSIMISTIC_WRITE)` (`ImportPreviewRepository.lockByIdAndOwner`) and its Valid rows are re-read. The second Confirm waits for the first; it then finds the preview gone → `NotFoundException` (404), which is re-thrown without recording a Failed batch (user decision) |
| E3 | Taken id caught by the unique constraint | A `DataIntegrityViolationException` is treated like the pre-check: 409 MSG33 with error `TicketIdTaken: MSG33` (spec Interface Contracts) |
| E4 | Event delivery | `TicketsPublishedEvent` is published inside the import transaction, and listeners use `@TransactionalEventListener(phase = AFTER_COMMIT)` (Javadoc on the event). This replaces Task 9's "publish after execute": with that, the AFTER_COMMIT listeners the design asks for were never called. A listener that fails after commit does not undo the import |
| E5 | Ticket content in logs | `logging.level` for `org.hibernate.engine.jdbc.spi.SqlExceptionHelper` and `org.hibernate.orm.jdbc.batch` set to OFF in `application.yml`, because their messages carry the bound values (AGENTS §9). The service logs its own line instead: exception type and SQLState, no values |
| E6 | Recorder failure | If `recordFailed` itself throws, it is logged (no content) and the client still gets `ImportFailedException` (MSG33) |
| E7 | Rows endpoints access (AC-003.4) | `ImportPreviewServiceImpl` checks `managesActive(user, preview.projectId)` after the owner check → 403 MSG08. Task 14 |
| E8 | `IN (...)` bind limit (> 65,535 ids) | Not fixed now: the NFR file (10 MB, about 5,000 rows) stays far below it. Follow-up: chunk `findExistingTicketIds` |

## File Structure
```
backend/
├── pom.xml                                                     (+ commons-csv 1.12.0)
├── src/main/resources/application.yml                          (+ multipart limits, tsolve.stub.projects)
├── src/main/resources/db/changelog/db.changelog-master.xml     (+ include 002)
├── src/main/resources/db/changelog/changes/003-ticket-import-schema.sql
├── src/main/java/vn/tsolve/common/error/
│   ├── GlobalExceptionHandler.java      (ProblemDetail + code; MaxUploadSizeExceeded → MSG28)
│   └── MessageCodeException.java        (code + params)
└── src/main/java/vn/tsolve/ticket/
    ├── config/TicketSchedulingConfig.java          (@EnableScheduling)
    ├── controller/ImportController.java
    ├── service/CsvImportService.java · ImportPreviewService.java · ImportConfirmService.java · ProjectAccess.java
    ├── service/impl/CsvImportServiceImpl.java · ImportPreviewServiceImpl.java · ImportConfirmServiceImpl.java
    │               · StubProjectAccess.java · ImportBatchRecorder.java · PreviewCleanupJob.java
    ├── csv/CsvTicketParser.java · CsvRowValidator.java · CsvColumns.java · ParsedCsv.java · RowError.java
    ├── repository/TicketRepository.java · ImportBatchRepository.java · TicketReviewDecisionRepository.java
    │              · ImportPreviewRepository.java · ImportPreviewRowRepository.java
    ├── entity/Ticket.java · ImportBatch.java · TicketReviewDecision.java · ImportPreview.java · ImportPreviewRow.java
    │          · TicketSource.java · TicketStatus.java · ImportBatchStatus.java · PreviewRowStatus.java
    ├── dto/ImportPreviewResponse.java · ImportPreviewRowSummary.java · ImportPreviewRowResponse.java · RemoveRowsRequest.java · ImportResultResponse.java
    ├── mapper/ImportPreviewMapper.java
    ├── event/TicketsPublishedEvent.java
    └── exception/CsvFileRejectedException.java · ProjectNotActiveException.java · ImportFailedException.java
backend/src/test/java/vn/tsolve/ticket/...   (mirrors main; ImportConfirmIT uses Testcontainers)
frontend/openapi/tsolve-api.yaml              (+ /api/v1/imports/** paths and schemas)
```
Deleted: `ticket/ImportTicketRequest.java`, `ResolvedTicketRepository.java`, `ResolvedTicketSnapshot.java`, `TicketController.java`, `TicketService.java`.

## External Dependencies
- `org.apache.commons:commons-csv:1.12.0` — RFC 4180 parsing (RESEARCH-001).
- Spring Boot already provides: web (multipart), data-jpa, validation, security, scheduling, `ProblemDetail`, events.

## Edge Cases & Risks
<!-- Filled by the edge-cases skill (and risk-review for high-risk modules).
     Every Critical/High row maps to a test task in tasks.md. -->

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-001 | Encoding | Excel "CSV UTF-8" with BOM | High | BOM ignored, header matches (AC-001.7) | Task 3 |
| 2 | STORY-001 | Encoding | Windows-1258/ANSI export | High | MSG27, no preview (AC-001.3) | Task 3 |
| 3 | STORY-001 | Format | Quoted field with comma, `""`, CRLF and LF line breaks | High | Value read intact (AC-001.12) | Task 3 |
| 4 | STORY-001 | Format | `;`-delimited file | Medium | Header check fails → MSG29 (D6) | Task 3 |
| 5 | STORY-001 | Format | Header has different case/spaces (`Ticket_ID `) | Medium | Trimmed + lower-cased, accepted | Task 3 |
| 6 | STORY-001 | Format | Unclosed quote at end of file | High | MSG27 | Task 3 |
| 7 | STORY-001 | Format | Row with fewer/more cells than header | Medium | Missing cells read as empty → MSG01 on required fields; extra cells ignored | Task 4 |
| 8 | STORY-001 | Size | File exactly 10 MB vs 10 MB + 1 byte | Medium | Accepted vs MSG28 | Task 6 |
| 9 | STORY-001 | Empty | Header only / zero-byte file | Medium | MSG04 / MSG27 (no header) | Task 3 |
| 10 | STORY-001 | Auth | PM uploads to a Project they do not manage | Critical | 403, nothing saved (AC-001.9) | Task 6 |
| 11 | STORY-001 | Auth | Staff/Admin calls any import endpoint | Critical | 403 (AC-001.10) | Task 8 |
| 12 | STORY-002 | Data | Whitespace-only required value | High | MSG01 (AC-002.2) | Task 4 |
| 13 | STORY-002 | Data | Ticket ID differs only by surrounding spaces | Medium | Treated as same id after trim (AC-002.10) | Task 4 |
| 14 | STORY-002 | Data | Same id 3 times in file and also in DB | Medium | All 3 rows: MSG78 and MSG31 | Task 4 |
| 15 | STORY-002 | Data | Solution total exactly 10,000 vs 10,001 | Medium | Valid vs MSG02 | Task 4 |
| 16 | STORY-002 | Data | resolved_date `2026-02-30`, `21/10/2026`, tomorrow | Medium | MSG39 | Task 4 |
| 17 | STORY-002 | Unicode | Vietnamese/emoji text near a length limit | Medium | Limit counts characters (code points), not bytes | Task 4 |
| 18 | STORY-003 | Auth | PM opens/removes rows of another PM's preview | Critical | 404 (AC-003.2) | Task 7 |
| 19 | STORY-003 | Data | Remove a row number that does not exist | Low | Ignored, others removed | Task 7 |
| 20 | STORY-004 | Concurrency | Same id imported by another preview between preview and Confirm | High | Whole Confirm fails MSG33, Failed batch recorded (AC-004.7) | Task 9 |
| 21 | STORY-004 | Concurrency | Double-click Confirm (two requests) | High | Second request: preview gone → 404; DB unique constraint is the last guard | Task 9 |
| 22 | STORY-004 | State | Project deactivated after preview | High | MSG40, nothing imported (AC-004.6) | Task 9 |
| 23 | STORY-004 | Failure | DB error mid-insert | High | No ticket saved, Failed batch survives the rollback (AC-004.8) | Task 9 |
| 24 | STORY-004 | Data | All rows removed | Medium | 409, nothing imported (AC-004.5) | Task 9 |
| 25 | STORY-004 | Event | Listener throws after commit | Medium | Tickets stay imported; error logged | Task 9 |
| 26 | STORY-006 | Time | Preview 23 h 59 m vs 24 h 01 m old | Low | Kept vs deleted (uses an injected `Clock`) | Task 10 |
| 27 | STORY-002 | Performance | 5,000-row, ~10 MB file | High | Preview within 60 s; one existing-id query; batched inserts | Task 11 |
| 28 | STORY-004 | Security | Row text contains `<script>` or `=cmd` | Low | Stored as plain text; React escapes on display; no CSV export in scope | — |

## Risk Review — 2026-10-21 — Verdict: CAUTION

Agreements:
- The all-or-nothing Confirm with the Failed batch in `REQUIRES_NEW` is correct, and it is the riskiest code: it needs a test against a real transaction rollback, not only mocks.
- Owner checks on every preview endpoint are the main authorization boundary, because preview ids are sequential.
- The stub `ProjectAccess` must fail closed.

| Topic | Architect | Security | Performance | User | Devil's advocate | Resolution |
|-------|-----------|----------|-------------|------|------------------|------------|
| Preview payload | Contract grows | — | 5,000 × 10k chars = tens of MB per response | Slow table | Most files are small | **Page the rows** (user, 2026-10-21; spec AC-001.1/002.8/002.11) |
| Stub ProjectAccess | Temporary coupling | Every PM on every Project = bypass | — | — | Why not a real table? | Per-user mapping, fail closed |
| ProblemDetail refactor | Required by AGENTS.md | — | — | Frontend already parses it | Touches existing `knowledge` errors | Do it; `knowledge` endpoints get the same shape |
| Insert speed | — | — | IDENTITY disables batching | Confirm feels stuck | Measure first | Sequence allocation 50 + batch size 100; perf test task |
| Audit event missing | AGENTS.md lists import as audited | Import is untraceable in the audit log until the audit module exists | — | — | Import Batch already records who/when | Accept for KAN-31; Import Batch is the trace. Follow-up when AuditService exists |

| Risk | Severity | Mitigation | Task |
|------|----------|------------|------|
| Oversized preview responses | High | Paging + 200-character snippets | Task 7, Task 8 |
| Authorization bypass via the stub | Critical | Fail-closed per-user mapping | Task 5 |
| Cross-user access by preview id | Critical | Owner check → 404 in every preview query | Task 7, Task 9 |
| Rollback erases the Failed record | High | `REQUIRES_NEW` recorder + rollback test | Task 9 |
| Slow Confirm on 5,000 rows | Medium | JDBC batching + sequence allocation | Task 2, Task 11 |

## Change 2026-11-01 — web screens (STORY-007..011, D10–D15)

Spec decisions D10–D14 (spec "Decisions taken for the web screens"). D15 is decided here with the user.

### Backend

```
GET /imports/previews/{id} ──► ImportController.getImportPreview ──► ImportPreviewServiceImpl.preview
      owned(user, id)  → 404 (other owner / gone) · 403 MSG08 (no longer manages the Active Project)
      summary(preview) → the same ImportPreviewResponse as remove-rows (counts, MSG30, first 50 rows)
```

- **STORY-007:** `ImportPreviewService.preview(user, previewId)`. `removeRows` and `preview` share one private `summary(preview)`, which builds what `removeRows` builds today. No new query: the three counts and the first page already exist.
- **D10, UUID Project ids:** `ProjectAccess.managesActive(String, UUID)` / `isActive(UUID)`. `projectId` becomes `UUID` in `ImportPreview`, `ImportBatch`, `Ticket`, `ImportPreviewResponse`, `CsvImportService.upload` and the controller's `@RequestParam UUID projectId`. A value that is not a UUID fails binding → 400 from the existing `GlobalExceptionHandler` (be-foundation). `StubProjectAccess` reads `user:<uuid>,<uuid>;…`; a malformed id grants nothing (fails closed, as before). `.env.example` gets the new format in its comment.
- **Changeset `005-ticket-import-project-uuid.sql`** (003 is applied and stays as it is): `ALTER TABLE import_batch|ticket|import_preview ALTER COLUMN project_id TYPE UUID USING lpad(to_hex(project_id), 32, '0')::uuid`. Old stub ids keep a stable value (`1` → `00000000-0000-0000-0000-000000000001`), so dev data survives. PostgreSQL rebuilds `idx_import_batch_project` and `idx_ticket_project_status` itself. 005 is the next free number on every branch (checked 2026-11-01: `main` has 001, 003, 004; open branches add only 002).
- **D14:** `CsvRowValidator` sends `MSG_NEW_3` instead of `MSG78`; MSG39 stays for the resolved date.
- **Contract:** `projectId` `format: uuid` in the upload body and in `ImportPreviewResponse`; new path `GET /imports/previews/{previewId}` (`getImportPreview`, 200 / 403 / 404). `npm run generate:api` regenerates the client.

### Web app — `frontend/src/features/ticket-import/`

```
/import?tab=csv ─ ImportTicketsPage ─ Tabs (Jira ✗, CSV, Manual entry ✗)
                       └─ CsvImportForm: Project select · Download template · CsvUpload · Upload
                              │ useUploadCsvImport (handlesOwnErrors)
                              ▼ success → navigate(/import/preview?previewId=<id>)
/import/preview?previewId=<id> ─ ImportPreviewPage
       ├─ previewId not a positive integer → <Navigate to="/import" replace> (AC-009.11)
       ├─ useGetImportPreview(id) → Summary: file name, StatCards Valid / Error / Removed, Alert MSG30
       ├─ useGetImportPreviewRows(id, page) → DataTable (20 rows, checkbox; Removed rows not selectable)
       ├─ Remove (selection) · Confirm (validCount > 0) · Cancel (MSG37)
       ├─ Preview on a row → ImportTicketPreviewModal → useGetImportPreviewRow(id, rowNo)
       └─ useBlocker while not finished → ConfirmDialog MSG37 (AC-009.10)
```

- **Project select (AC-008.2):** `useSession().projects` filtered by `roleInProject === 'PROJECT_MANAGER'`; label `<jiraProjectKey> · <departmentName>`, value `projectId`. No new request: the session already holds the list.
- **Download template (AC-008.3):** `downloadCsvTemplate({ headers: { Accept: 'text/csv' } })` returns the text; a small `saveTextFile(text, 'ticket-import-template.csv')` in `ticket-import/lib/` builds a Blob URL, clicks a hidden link and revokes the URL. One use, so it stays in the feature.
- **Upload errors (AC-008.7)** follow the catalog type of the returned code: field codes (MSG27, MSG28) go under the CSV file field with `form.setFields`; inline codes (MSG29 with `column_names`, MSG04, MSG08) go to an `Alert` above the form. Project and file stay in the form. Loading: `Button loading={upload.isPending}`, which also blocks a second click (AC-008.8).
- **Rows table (AC-009.2):** columns checkbox · Ticket ID · Title · Author · Resolved date · Solution (the five sections as returned, label + text, cut with "…") · Result (`StatusTag` VALID / ERROR / REMOVED) · Error details (`msg(code, params)` per error, one per line; a code the catalog does not know shows MSG06) · Preview (link button). Paging is local `useState`: the page is reached only from Upload, and its URL carries only `previewId` (D11).
- **Remove (AC-009.3):** `useRemoveImportPreviewRows` → on success invalidate the preview query and its rows queries, clear the selection. **Confirm (AC-009.5–009.8):** `useConfirmImport` with `handlesOwnErrors`; success → `showMessage('MSG32', { created, skipped })`, mark finished, `navigate(paths.importTickets)`; error → `showMessage(error.code, error.params)` and stay.
- **Leaving (AC-009.9, 009.10):** one `isFinished` ref, set before the navigation that a successful Confirm makes. `useBlocker(() => !isFinished.current)` opens `ConfirmDialog code="MSG37" isDanger`; OK → `blocker.proceed()`, Cancel → `blocker.reset()`. The Cancel button only navigates to Import Tickets and lets the blocker ask, so MSG37 has one code path. No `beforeunload` (spec Out of Scope; guideline 08 §7 asks for it, the approved spec wins for this change).
- **States (AC-009.12, 009.13):** the preview query's `LoadingState`; its 403 / 404 → `ErrorState` with the `ApiError` (the backend sends MSG08 on 403; a 404 without a code is shown as MSG04). The rows table uses `DataTable`'s own loading and error.
- **Popup (AC-010.1–010.3):** `ImportTicketPreviewModal` (row number or null) → antd `Descriptions` with every field read-only, the full Solution sections, `StatusTag`, error texts; loading and `ErrorState` inside the popup; Close keeps the page as it was.
- **Mocks:** `mocks/data.ts` holds one in-memory preview (Valid, Error and Removed rows, a duplicate id with `MSG_NEW_3`) that upload replaces and remove / confirm change; `resetMockData()` restores it. Handlers for every import path, so dev mode and the page tests run on the same data (guideline 10 §5).

### Kit — `frontend/src/shared/ui/` (D12, D15; the FE Lead reviews this part)

- **`CsvUpload`** (Figma "Web/Upload"): a form field, `<CsvUpload name="file" label="CSV file" required />`. It renders `FormField` + antd `Upload.Dragger` with `accept=".csv"`, `maxCount={1}` and `beforeUpload` returning `false`, so nothing is sent (AC-011.3). A file whose name does not end in `.csv` (any case) or is over 10 MB is not kept: `form.setFields([{ name, value: undefined, errors: [msg('MSG27' | 'MSG28')] }])` through `Form.useFormInstance()` (AC-011.2). The field value is the `File`; removing it clears the value. Required → `rules.required(label)` (MSG01). Exported from `shared/ui/index.ts`.
- **`DetailModal`** (D15, user 2026-11-01): Figma Modal Header (Form kind) + content + a footer with one **Close** button; props `isOpen`, `title`, `width?`, `onClose`, `children`. The read-only popup the kit lacked (FormModal always has Cancel + OK); Triage Result Detail can reuse it.
- **Catalog** (fe-foundation AC-006.9): `MSG_NEW_3` (inline) with the text of spec "New messages"; the AC-006.7 test allows it.

### Key decisions (change)

| Decision | Choice | Alternatives considered | Rationale |
|----------|--------|------------------------|-----------|
| D10 changeset | `ALTER COLUMN … TYPE UUID USING lpad(to_hex(id),32,'0')::uuid` in a new 005 | Drop and recreate the tables; edit 003 | 003 is applied (never edit). Recreating loses dev data; the mapping keeps it |
| D11 preview read | `preview()` reuses `summary()` of remove-rows | A separate counts endpoint | Same response type, so the screen has one Summary source |
| Upload error placement | By the code's catalog type (field → under the file; inline → `Alert`) | A toast for everything | Guideline 06 maps message types to places; MSG29 lists columns and must stay visible |
| D14 duplicate code | `MSG_NEW_3` (user) | MSG31; keep MSG78 and show the raw code | MSG78 is another message in v3460; `MSG_NEW_2` is the precedent |
| D15 read-only popup | New `DetailModal` in `shared/ui` (user) | FormModal with OK = Close; an `isReadOnly` prop on FormModal | One Close button as in SRS 5.3; FormModal stays as it is |
| Leave guard | `useBlocker` + `ConfirmDialog`; Cancel goes through the same blocker | A separate dialog for the Cancel button | One MSG37 path; no `beforeunload` (spec Out of Scope) |
| Rows page state | Local `useState` | `?page=` in the URL | The screen's URL carries only `previewId` (D11); a reload returns to page 1 |

### Edge cases (change)

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 29 | STORY-007 | Auth | PM reads another PM's preview id | Critical | 404, nothing returned (AC-007.2) | Task 16 |
| 30 | STORY-007 | Auth | PM lost the Project between upload and reload | High | 403 MSG08 (AC-007.3) | Task 16 |
| 31 | STORY-007 | Data | Stub setting holds a malformed UUID | High | That id grants nothing (fails closed) | Task 15 |
| 32 | STORY-007 | Data | Upload with `projectId=1` (not a UUID) | Medium | 400, no preview | Task 15 |
| 33 | STORY-009 | Input | `?previewId=abc`, `0`, `-3`, `1.5`, missing | Medium | Redirect to Import Tickets (AC-009.11) | Task 20 |
| 34 | STORY-008/009 | Concurrency | Double click on Upload / Confirm | High | One request (loading button) (AC-008.8, AC-009.8) | Task 19, Task 21 |
| 35 | STORY-009 | State | Every Valid row removed | Medium | Confirm disabled (AC-009.5) | Task 21 |
| 36 | STORY-009 | Navigation | Confirm succeeds | High | Goes straight to Import Tickets, no MSG37 (AC-009.6) | Task 21 |
| 37 | STORY-011 | Input | `REPORT.CSV`, `data.csv.txt`, 10 MB exactly vs 10 MB + 1 byte | Medium | Kept / MSG27 / kept / MSG28 (AC-011.2) | Task 18 |
| 38 | STORY-009 | Data | Row error with a code the catalog does not know | Low | MSG06 text, no crash | Task 20 |
| 39 | STORY-008 | Data | User manages no Project | Medium | Empty select; Upload shows MSG01 at Project | Task 19 |

### File structure (change)
```
backend/src/main/resources/db/changelog/changes/005-ticket-import-project-uuid.sql   (+ include in the master changelog)
backend/src/main/java/vn/tsolve/ticket/  ProjectAccess · StubProjectAccess · ImportPreview · ImportBatch · Ticket
                                         · ImportPreviewResponse · CsvImportService(Impl) · ImportConfirmServiceImpl
                                         · ImportPreviewService(Impl) (+ preview) · ImportController (+ GET preview)
                                         · CsvRowValidator (MSG_NEW_3)
.env.example                              (stub format comment)
frontend/openapi/tsolve-api.yaml · frontend/src/shared/api/generated/**
frontend/src/shared/messages/catalog.ts · catalog.test.ts            (MSG_NEW_3)
frontend/src/shared/ui/CsvUpload/{CsvUpload.tsx,CsvUpload.test.tsx,index.ts}
frontend/src/shared/ui/DetailModal/{DetailModal.tsx,index.ts} · frontend/src/shared/ui/index.ts
frontend/src/features/ticket-import/
├── pages/ImportTicketsPage.tsx (+ .test.tsx) · ImportPreviewPage.tsx (+ .test.tsx)
├── components/CsvImportForm.tsx · ImportPreviewTable.tsx · ImportTicketPreviewModal.tsx
├── hooks/useImportPreview.ts            (queries + remove / confirm with cache refresh)
├── lib/save-text-file.ts
└── mocks/data.ts · handlers.ts
```

## Change 2026-11-05 — import type (STORY-012..016, spec D15–D20)

Spec decisions D15–D20 are in the spec, section "Decisions taken for the import type". They are not the D15 of the change above (`DetailModal`), which is a design decision of 2026-11-01.

### Backend

```
POST /imports/csv (importType?) ──► parser(type) ──► validator(type) ──► rows: VALID | NEEDS_SOLUTION | ERROR
POST /imports/previews/{id}/fill-solution
      owned(user, id) → 404 · 403 MSG08
      lockByIdAndOwner → waits for a running Confirm, then 404 when it is gone (AC-013.4)
      type WITH_SOLUTION, or a row ERROR / REMOVED → 409 MSG06 · a row number that does not exist → 404
      row.fillSolution(trimmed sections) → VALID · summary(preview)
POST /imports/previews/{id}/confirm
      WITHOUT_SOLUTION with NEEDS_SOLUTION + ERROR > 0 → 409 MSG_NEW_4, no batch (before and inside the transaction)
      otherwise as today
```

- **`ImportType`** (`WITH_SOLUTION`, `WITHOUT_SOLUTION`), a new enum in `T/entity`, holds its required columns and template columns, taken from `CsvColumns`. `import_preview.import_type` stores it.
- **Changeset `<next free number>-ticket-import-type.sql`:** `ALTER TABLE import_preview ADD COLUMN import_type VARCHAR(20) NOT NULL DEFAULT 'WITH_SOLUTION'`, and `ALTER TABLE import_preview_row ALTER COLUMN status TYPE VARCHAR(20)`: the column is `VARCHAR(10)` and `NEEDS_SOLUTION` has 14 characters. Previews that exist today stay files with Solutions (AC-012.1).
- **`PreviewRowStatus.NEEDS_SOLUTION`:** the `ImportPreviewRow` constructor gets the type: errors → `ERROR`; none → `VALID` or, for `WITHOUT_SOLUTION`, `NEEDS_SOLUTION`.
- **`ImportPreviewRow`:** the five Solution columns lose `updatable = false`. `fillSolution(symptoms, rootCause, resolutionSteps, workaround, scope)` stores the trimmed sections and sets `VALID`. `errors` stays not updatable: a fillable row has none.
- **Parser and validator:** `parse(in, type)` and `validate(rows, existing, type)` use the type's required columns. For `WITHOUT_SOLUTION` the parser leaves the five sections null, which is how AC-012.4 ignores them. Every other rule of `check` runs unchanged.
- **Upload:** counts come from the rows by status; today's `rows.size() - valid` would count Needs Solution as Error.
- **Fill solution:** `ImportPreviewService.fillSolution`, one endpoint for one row and for several. `FillSolutionRequest` validates with `@Valid`, so a rejection uses the existing field errors of `GlobalExceptionHandler`. The 10,000-character total is checked on the request (MSG02 for the Solution), with the same limit as `CsvRowValidator.SOLUTION_MAX_LENGTH`.
- **Confirm:** `RowsNotReadyException` (409, `MSG_NEW_4`, `count`). The check comes first, so a file with only Needs Solution rows answers `MSG_NEW_4`, not MSG30. It runs again in `importRows` under the preview lock, because Remove and Fill solution do not wait for the first check. Tickets are built from the row values as today, so `toTicket` does not change.

### Web app — `frontend/src/features/ticket-import/`

```
CsvImportForm: Project · Import type (Radio) · Download template (type) · CsvUpload · Upload (type)
ImportPreviewPage (WITHOUT_SOLUTION only):
  StatCard Needs Solution · Alert MSG_NEW_4 + Confirm disabled while needsSolutionCount + errorCount > 0
  Fill solution (selected rows, none Error / Removed) ──► SolutionFormModal (empty) ──► MSG36 when some have a Solution
  Preview on a Needs Solution / Valid row ─────────────► SolutionFormModal (the row, Apply)
  Preview on an Error / Removed row ───────────────────► ImportTicketPreviewModal (read-only, as today)
```

- **`SolutionFormModal`** is one `FormModal` for both uses: `rowNumbers`, and with one row from Preview the row's read-only fields and its sections as start values. One new hook, `useFillPreviewSolution`, refreshes the Summary and the rows like `useRemovePreviewRows` and handles its own errors: 400 field errors go to `form.setFields`, others to the message the backend returned, and the popup stays open.
- **Selection:** `ImportPreviewTable` reports the selected rows, not only their numbers, and the page keeps the status of each selected row. Fill solution and the MSG36 count need the status of rows on other pages too. Fill solution clears the selection, like Remove.
- **Import type:** `Radio.Group` in a `FormField`, initial value `WITH_SOLUTION`. Changing it keeps the chosen file. The downloaded file name stays `ticket-import-template.csv` (AC-008.3).
- **Catalog and tag:** `MSG_NEW_4` (inline) in `catalog.ts` and `NEEDS_SOLUTION` in `status-tags.ts`. These two shared files are added to the module's `paths`.

### Key decisions (change)

| Decision | Choice | Alternatives considered | Rationale |
|----------|--------|------------------------|-----------|
| One write endpoint | `fill-solution` with `rowNumbers` | A second `PUT .../rows/{rowNo}/solution` | One row is a list of one; UC-20 can use it later |
| Where the type lives | A column on `import_preview` | Infer it from the row statuses | The screen and Confirm must know it after every row is Valid |
| Solution columns of a type 2 file | The parser leaves them null | Keep them and let the validator skip them | They cannot reach a Ticket or a screen by accident (AC-012.4) |
| Concurrency of Fill solution | The lock Confirm already takes | A version column | A Solution cannot change while Confirm reads it; no new column |

### Edge cases (change)

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 40 | STORY-012 | Input | Type 2 file with Solution columns filled | Medium | Row is Needs Solution, sections empty (AC-012.4) | Task 24 |
| 41 | STORY-012 | Input | Type 1 file uploaded as type 2, or the reverse | Medium | Type 2: sections ignored. Type 1 with the type 2 template: MSG29 | Task 24 |
| 42 | STORY-012 | Data | Type 2 row with a duplicate or existing `ticket_id` | High | Error, not Needs Solution (AC-012.5) | Task 24 |
| 43 | STORY-013 | Input | Sections of only spaces; 10,000 characters exactly, then 10,001 | High | MSG01; accepted; MSG02 (AC-013.2) | Task 25 |
| 44 | STORY-013 | Concurrency | Fill solution while Confirm of the same preview runs | Critical | Fill waits, then 404; Tickets hold what Confirm read (AC-013.4) | Task 25 |
| 45 | STORY-013 | Input | `rowNumbers` empty, or a number twice | Medium | Empty: 400. Twice: saved once | Task 25 |
| 46 | STORY-013 | Atomicity | Several rows, one of them Error, Removed or not found | High | 409 or 404, no row changed (AC-013.3, AC-013.4) | Task 25 |
| 47 | STORY-014 | State | Type 2 with only Needs Solution rows | High | `MSG_NEW_4`, not MSG30 (AC-014.1) | Task 26 |
| 48 | STORY-014 | Concurrency | A row changes between the first check and the transaction | Medium | Second check under the lock rejects it, no batch | Task 26 |
| 49 | STORY-014 | State | Type 2, every row Removed | Low | Not blocked by `MSG_NEW_4`; AC-004.5 answers 409 MSG30 | Task 26 |
| 50 | STORY-015 | State | Selection spans pages, some rows already have a Solution | Medium | MSG36 counts every selected row (AC-015.5) | Task 29 |
| 51 | STORY-015 | Navigation | Reload after writing some Solutions | Medium | Written Solutions are shown again (AC-013.7) | Task 28 |
| 52 | STORY-016 | Regression | Upload with no `importType` | High | Behaves as today (AC-012.1, AC-016.1) | Task 24 |

### File structure (change)
```
backend/src/main/resources/db/changelog/changes/<next free number>-ticket-import-type.sql   (+ include in the master changelog)
backend/src/main/java/vn/tsolve/ticket/
├── entity/   ImportType (new) · PreviewRowStatus · ImportPreview · ImportPreviewRow
├── csv/      CsvColumns · CsvTicketParser · CsvRowValidator
├── dto/      FillSolutionRequest (new) · ImportPreviewResponse
├── exception/ RowsNotReadyException (new)
├── service/  CsvImportService(Impl) · ImportPreviewService(Impl) · ImportConfirmServiceImpl
└── controller/ ImportController
frontend/openapi/tsolve-api.yaml · frontend/src/shared/api/generated/**
frontend/src/shared/messages/catalog.ts · catalog.test.ts      (MSG_NEW_4)
frontend/src/shared/ui/StatusTag/status-tags.ts                (NEEDS_SOLUTION)
frontend/src/features/ticket-import/
├── components/ CsvImportForm · ImportPreviewTable · SolutionFormModal (new)
├── hooks/      useImportPreview
├── pages/      ImportPreviewPage · both page tests
└── mocks/      data · handlers
```

The schema alignment plan (`artifacts/04-plans/platform/schema-ddl-alignment.md`, KAN-248) renames `import_preview` columns and rewrites the same entities. Whichever lands second adapts to the other: the new column and status carry over as they are.
