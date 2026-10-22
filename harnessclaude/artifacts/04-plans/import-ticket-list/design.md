---
module: import-ticket-list
date: 2026-10-21
---

# Design: import-ticket-list

Spec: `artifacts/03-modules/import-ticket-list/spec.md` (approved, version 494d550f). This design covers KAN-31 CSV import (STORY-001..006). It is **backend + OpenAPI contract only**: the React screens are a separate frontend task that generates its client from the contract (decided 2026-10-21).

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

New changeset `002-ticket-import-schema.sql`:

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
-- ticket and ticket_review_decision: ALTER SEQUENCE … INCREMENT BY 50 (batching, see Key Decisions)

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

CREATE TABLE ticket_review_decision (
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
| Insert batching (risk review) | `spring.jpa.properties.hibernate.jdbc.batch_size=100` + `order_inserts=true`. Preview rows (composite key) batch fully. `ticket` and `ticket_review_decision` ids use `@SequenceGenerator(allocationSize = 50)` on sequences altered to `INCREMENT BY 50` in 002 | Plain IDENTITY | IDENTITY turns off JDBC batching; 10,000 single inserts on Confirm would be slow |
| Who is the PM | `Authentication.getName()` stored as text (`performed_by`, `owner`, `reviewer`); role check `@PreAuthorize("hasRole('PROJECT_MANAGER')")` | User FK | No user table yet (identity deferred, architecture.md) |
| Decided in planning: approve decision table (builder check) | `ticket_review_decision`, created here; the review module takes it over | Reuse `solution_approval` | `solution_approval` points at `solution`, not `ticket` |
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
├── src/main/resources/db/changelog/changes/002-ticket-import-schema.sql
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
