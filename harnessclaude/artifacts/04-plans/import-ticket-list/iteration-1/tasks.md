---
module: import-ticket-list
date: 2026-10-21
total_tasks: 13
owner: Dev
---

# Tasks: import-ticket-list — Iteration 1 (KAN-31 CSV import)

Spec: `artifacts/03-modules/import-ticket-list/spec.md`
Design: `artifacts/04-plans/import-ticket-list/iteration-1/design.md` (Master: `artifacts/04-plans/import-ticket-list/design.md`)

Paths are relative to the repo root. `T` = `backend/src/main/java/vn/tsolve/ticket`, `TT` = `backend/src/test/java/vn/tsolve/ticket`. Every test method name cites its AC id (`…_AC_001_2`).

## Task 1: OpenAPI contract for CSV import

**Story:** STORY-001, STORY-002, STORY-003, STORY-004, STORY-005
**Feature:** csv-import-contract
**Description:** Add every `/api/v1/imports/**` path from the spec's Interface Contracts to the contract: template, upload (multipart), rows page, row detail, remove-rows, confirm. Add the schemas `ImportPreviewResponse`, `ImportPreviewRowSummary`, `ImportPreviewRowResponse`, `RemoveRowsRequest`, `ImportResultResponse` and the error responses (400/403/404/409/413 as ProblemDetail with `code`). Run `npm run generate:api` in `frontend/` and commit the regenerated client.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`
- Modify: `frontend/src/shared/api/generated/**` (generated only)
**Dependencies:** none
**Estimated size:** small

---

## Task 2: Schema, entities and repositories

**Story:** STORY-004
**Feature:** csv-import-schema
**Description:**
- Changeset `002-ticket-import-schema.sql` as in design §Data Model. `ALTER SEQUENCE` on `ticket` and `ticket_review_decision` sets `INCREMENT BY 50`.
- Include the changeset in the master changelog.
- Entities and enums, with `@SequenceGenerator(allocationSize = 50)` on `Ticket` and `TicketReviewDecision`.
- Repositories:
  - `TicketRepository.findExistingTicketIds(Collection<String>)`
  - `ImportPreviewRepository.findByIdAndOwner`, `deleteByOwner`, `findIdsCreatedBefore(Instant)`
  - `ImportPreviewRowRepository` with a paged query by preview and status
- JDBC batching properties in `application.yml`.
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/002-ticket-import-schema.sql`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`, `backend/src/main/resources/application.yml`
- Create: `T/entity/{Ticket,ImportBatch,TicketReviewDecision,ImportPreview,ImportPreviewRow,TicketSource,TicketStatus,ImportBatchStatus,PreviewRowStatus}.java`
- Create: `T/repository/{TicketRepository,ImportBatchRepository,TicketReviewDecisionRepository,ImportPreviewRepository,ImportPreviewRowRepository}.java`
- Test: none (no in-memory DB, per `backend/AGENTS.md` §8). Check by starting the app against a local PostgreSQL: Liquibase applies the changeset and `ddl-auto: validate` passes.
**Dependencies:** none
**Estimated size:** medium

---

## Task 3: CSV parser

**Story:** STORY-001
**Feature:** csv-parser
**Description:** Add `commons-csv` 1.12.0 to the pom. `CsvTicketParser.parse(InputStream)` returns `ParsedCsv(header, rows)`:
- strict UTF-8 decoding, BOM stripped
- RFC 4180 parsing
- header names trimmed and lower-cased
- the required-column check (MSG29 lists every missing column)
- zero data rows → MSG04
- a malformed file or bad encoding → MSG27

`CsvColumns` holds the required and optional column names. Covers edge cases 1–6 and 9.
**Files:**
- Modify: `backend/pom.xml`
- Create: `T/csv/{CsvTicketParser,CsvColumns,ParsedCsv}.java`, `T/exception/CsvFileRejectedException.java`
- Test: `TT/csv/CsvTicketParserTest.java` (AC-001.3, AC-001.5, AC-001.6, AC-001.7, AC-001.8, AC-001.12)
**Dependencies:** none
**Estimated size:** medium

---

## Task 4: Row validator

**Story:** STORY-002
**Feature:** csv-row-validation
**Description:** `CsvRowValidator.validate(rows, existingIds)` is a pure function that returns each row with status and `RowError(code, field, params)`. It checks:
- MSG01: a required value is blank after trimming
- MSG02: a field is over its limit (lengths counted in code points), or the Solution sections exceed 10,000 characters in total
- MSG31: the Ticket ID already exists
- MSG78: the Ticket ID appears more than once in the file
- MSG39: `resolved_date` is not `yyyy-MM-dd` or is in the future (uses an injected `Clock`)

Ticket IDs are trimmed before comparison. Covers edge cases 7 and 12–17.
**Files:**
- Create: `T/csv/{CsvRowValidator,RowError}.java`
- Test: `TT/csv/CsvRowValidatorTest.java` (AC-002.1 – AC-002.7, AC-002.10)
**Dependencies:** Task 3
**Estimated size:** medium

---

## Task 5: ProjectAccess stub and ProblemDetail errors

**Story:** STORY-001
**Feature:** project-access-errors
**Description:**
- `ProjectAccess` interface: `boolean managesActive(String user, long projectId)` and `boolean isActive(long projectId)`.
- `StubProjectAccess` reads `tsolve.stub.projects: ${TSOLVE_STUB_PROJECTS:}` (`alice:1,2;bob:3`) and `tsolve.stub.inactive-projects: ${TSOLVE_STUB_INACTIVE_PROJECTS:}`, and fails closed when no mapping exists (design: Stub safety). Add both names to `.env.example`.
- Refactor `GlobalExceptionHandler` to `ProblemDetail` with `code`, `params` and `fieldErrors`. Add `MessageCodeException`.
- Map `MaxUploadSizeExceededException` to 413 + MSG28.
- Add the multipart limits to `application.yml`.
**Files:**
- Create: `T/service/ProjectAccess.java`, `T/service/impl/StubProjectAccess.java`, `backend/src/main/java/vn/tsolve/common/error/MessageCodeException.java`
- Modify: `.env.example`
- Modify: `backend/src/main/java/vn/tsolve/common/error/GlobalExceptionHandler.java`, `backend/src/main/resources/application.yml`
- Test: `TT/service/impl/StubProjectAccessTest.java` (fail-closed, risk review), `backend/src/test/java/vn/tsolve/common/error/GlobalExceptionHandlerTest.java` (AC-001.4 mapping)
**Dependencies:** none
**Estimated size:** small

---

## Task 6: Upload creates the preview

**Story:** STORY-001, STORY-002
**Feature:** csv-upload
**Description:** `CsvImportServiceImpl.upload(user, projectId, MultipartFile)`:
1. Check the `.csv` extension (MSG27) and the size limit (MSG28).
2. Check `ProjectAccess.managesActive` (403).
3. Parse the file, then run one `findExistingTicketIds` query and validate the rows.
4. Delete the user's previous preview.
5. Save the preview and its rows, and return the counts plus MSG30 when any row is Error, plus the first page (50 rows, snippets).

Covers edge cases 8 and 10.
**Files:**
- Create: `T/service/CsvImportService.java`, `T/service/impl/CsvImportServiceImpl.java`, `T/dto/{ImportPreviewResponse,ImportPreviewRowSummary}.java`, `T/mapper/ImportPreviewMapper.java`
- Test: `TT/service/impl/CsvImportServiceImplTest.java` (AC-001.1, AC-001.2, AC-001.4, AC-001.9, AC-001.11, AC-002.9)
**Dependencies:** Task 2, Task 3, Task 4, Task 5
**Estimated size:** medium

---

## Task 7: Preview rows: paging, detail, remove

**Story:** STORY-002, STORY-003
**Feature:** preview-rows
**Description:** `ImportPreviewServiceImpl`:
- `rows(user, previewId, status?, Pageable)`: size ≤ 100, ordered by `row_no`, Solution sections cut to 200 characters.
- `row(user, previewId, rowNo)`: the full row.
- `removeRows(user, previewId, rowNumbers)`: sets those rows to REMOVED and ignores unknown row numbers.

Every query filters by owner; a missing preview, or one that belongs to another user, → 404. Covers edge cases 18 and 19.
**Files:**
- Create: `T/service/ImportPreviewService.java`, `T/service/impl/ImportPreviewServiceImpl.java`, `T/dto/{ImportPreviewRowResponse,RemoveRowsRequest}.java`
- Test: `TT/service/impl/ImportPreviewServiceImplTest.java` (AC-002.8, AC-002.11, AC-003.1, AC-003.2, AC-003.3)
**Dependencies:** Task 6
**Estimated size:** small

---

## Task 8: Import controller and template download

**Story:** STORY-001, STORY-003, STORY-005
**Feature:** import-endpoints
**Description:** `ImportController` implements the contract from Task 1, with `@PreAuthorize("hasRole('PROJECT_MANAGER')")` on every method and the springdoc annotations required by AGENTS.md §6. `GET /imports/csv/template` returns the header line as `text/csv; charset=UTF-8`. A `@WebMvcTest` covers the role check (edge case 11), multipart binding, a 413 response, the template, and the paging parameters.
**Files:**
- Create: `T/controller/ImportController.java`
- Test: `TT/controller/ImportControllerTest.java` (AC-001.10, AC-005.1, AC-005.2)
**Dependencies:** Task 6, Task 7
**Estimated size:** small

---

## Task 9: Confirm the import

**Story:** STORY-004
**Feature:** csv-confirm
**Description:** `ImportConfirmServiceImpl.confirm(user, previewId)` runs in one `@Transactional`:
1. Load the preview by owner (404).
2. Reject with 409 when no VALID row is left.
3. Check `ProjectAccess.isActive` (MSG40).
4. Re-check the Ticket IDs; any now taken → `ImportFailedException(MSG33)`.
5. Save an `ImportBatch` (COMPLETED), the Tickets (PUBLISHED, source CSV, writer = author) and one APPROVE `TicketReviewDecision` per Ticket.
6. Delete the preview and publish `TicketsPublishedEvent`.
7. Return MSG32 with created and skipped counts.

If any step fails, the transaction rolls back, and `ImportBatchRecorder.recordFailed` (`@Transactional(propagation = REQUIRES_NEW)`) saves a FAILED batch with the error. The confirm endpoint is in Task 8's controller. Covers edge cases 20–25.
**Files:**
- Create: `T/service/ImportConfirmService.java`, `T/service/impl/{ImportConfirmServiceImpl,ImportBatchRecorder}.java`, `T/dto/ImportResultResponse.java`, `T/event/TicketsPublishedEvent.java`, `T/exception/{ProjectNotActiveException,ImportFailedException}.java`
- Modify: `T/controller/ImportController.java`
- Test: `TT/service/impl/ImportConfirmServiceImplTest.java` (AC-004.1 – AC-004.7, AC-004.9). AC-004.8 and AC-004.10 are covered by Task 13.
**Dependencies:** Task 7, Task 8
**Estimated size:** medium

---

## Task 10: Preview cleanup job

**Story:** STORY-006
**Feature:** preview-cleanup
**Description:** `TicketSchedulingConfig` (`@EnableScheduling`) and `PreviewCleanupJob`, `@Scheduled(cron = "0 0 1 * * *")`. The job deletes each preview created more than 24 h before `Clock.now()`, one at a time. If one deletion fails, it logs the error and continues. Covers edge case 26.
**Files:**
- Create: `T/config/TicketSchedulingConfig.java`, `T/service/impl/PreviewCleanupJob.java`
- Test: `TT/service/impl/PreviewCleanupJobTest.java` (AC-006.1, AC-006.2, AC-006.3)
**Dependencies:** Task 2
**Estimated size:** small

---

## Task 11: 5,000-row performance check

**Story:** STORY-002
**Feature:** csv-import-performance
**Description:** Generate a ~10 MB file with 5,000 rows of multilingual text, embedded commas, quotes and line breaks; the dataset `dataset-tickets-multi-lang3-4k.csv` can be mapped to the template. A unit test asserts that parsing and validation finish well under 60 s. Measure the end-to-end upload and Confirm against a local PostgreSQL and record the times in the test report (NFR 1, edge case 27).
**Files:**
- Create: `TT/csv/CsvImportPerformanceTest.java`
- Test: the file above
**Dependencies:** Task 4, Task 9
**Estimated size:** small

---

## Task 12: Retire the old snapshot import

**Story:** STORY-004
**Feature:** retire-snapshot-import
**Description:** Delete `ImportTicketRequest`, `ResolvedTicketRepository`, `ResolvedTicketSnapshot`, `TicketService` and `TicketController` (`/api/tickets/**`, which upserts in conflict with MSG31). Keep the `resolved_ticket_snapshot` table, because `solution_source` has an FK to it. Before deleting, confirm there are no callers (grep and impact analysis).
**Files:**
- Delete: `T/ImportTicketRequest.java`, `T/ResolvedTicketRepository.java`, `T/ResolvedTicketSnapshot.java`, `T/TicketController.java`, `T/TicketService.java`
**Dependencies:** Task 2
**Estimated size:** small

---

## Task 13: Real-database tests for Confirm

**Story:** STORY-004
**Feature:** csv-confirm-db-tests
**Description:**
- Add `org.testcontainers:postgresql` and `org.testcontainers:junit-jupiter` (test scope, versions from the Spring Boot BOM).
- Write `ImportConfirmIT`: `@SpringBootTest` with a `pgvector/pgvector:pg16` container and `@ServiceConnection`; Liquibase runs the real changesets. It proves:
  - a forced save failure leaves no ticket but one FAILED batch (AC-004.8);
  - the event listener fires only after commit (AC-004.10);
  - two Confirms of the same IDs → the second fails on the unique constraint (edge case 21).
- Add one line to `backend/AGENTS.md` §8: DB-dependent behaviour is tested with Testcontainers (`*IT`), and Docker is required.
**Files:**
- Modify: `backend/pom.xml`, `backend/AGENTS.md`
- Create: `TT/service/impl/ImportConfirmIT.java`
- Test: the file above (AC-004.8, AC-004.10)
**Dependencies:** Task 9
**Estimated size:** medium
