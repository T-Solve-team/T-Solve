---
module: import-ticket-list
date: 2026-10-21
total_tasks: 29
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

---

## Task 14: Re-check Project access on preview rows

**Story:** STORY-003
**Feature:** preview-rows-access
**Description:** Spec change of 2026-10-22 (AC-003.4, design E7). `ImportPreviewServiceImpl.rows`, `row` and `removeRows` call `ProjectAccess.managesActive(user, preview.projectId)` after loading the caller's preview, and throw `ProjectAccessDeniedException` (403 MSG08) without reading or changing rows. The csv-confirm fix (AC-004.11, AC-004.12, design E1–E6) is done in the fixing loop of Task 9, not here.
**Files:**
- Modify: `T/service/impl/ImportPreviewServiceImpl.java`
- Test: `TT/service/impl/ImportPreviewServiceImplTest.java` (AC-003.4)
**Dependencies:** Task 7
**Estimated size:** small

---

## Change 2026-11-01 — web screens (STORY-007..011, D10–D15)

Design: master design, section "Change 2026-11-01". `F` = `frontend/src/features/ticket-import`, `UI` = `frontend/src/shared/ui`. Frontend tests name their AC id (`it('AC-009.3 …')`) and run on the feature's MSW mocks through `renderApp` (guideline 10). Each task ends with the verify commands of the root `CLAUDE.md` for the part it touches.

## Task 15: UUID Project ids

**Story:** STORY-001
**Feature:** project-uuid
**Description:** D10.
- Changeset `005-ticket-import-project-uuid.sql`: `project_id` of `import_batch`, `ticket` and `import_preview` to `UUID USING lpad(to_hex(project_id), 32, '0')::uuid`; include it in the master changelog.
- `ProjectAccess` takes `UUID`; `StubProjectAccess` parses `user:<uuid>,<uuid>;…` and ignores a malformed id; `.env.example` comment shows the new format.
- `projectId` is `UUID` in the three entities, `ImportPreviewResponse`, `CsvImportService(Impl)`, `ImportConfirmServiceImpl`, `ImportPreviewServiceImpl` and the controller's `@RequestParam`.
- Contract: `projectId` `format: uuid` in the upload body and `ImportPreviewResponse`; `npm run generate:api`.
- Update the existing tests to UUID ids. Covers edge cases 31 and 32.
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/005-ticket-import-project-uuid.sql`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`, `.env.example`, `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**`
- Modify: `T/service/ProjectAccess.java`, `T/service/impl/{StubProjectAccess,CsvImportServiceImpl,ImportConfirmServiceImpl,ImportPreviewServiceImpl}.java`, `T/service/CsvImportService.java`, `T/entity/{ImportPreview,ImportBatch,Ticket}.java`, `T/dto/ImportPreviewResponse.java`, `T/controller/ImportController.java`
- Test: `TT/service/impl/StubProjectAccessTest.java` (malformed UUID grants nothing), `TT/controller/ImportControllerTest.java` (non-UUID `projectId` → 400), the other `TT` tests and `ImportConfirmIT` (changeset 005 runs on the real database)
**Dependencies:** none
**Estimated size:** medium

---

## Task 16: Read an existing preview

**Story:** STORY-007
**Feature:** preview-read
**Description:** D11. `ImportPreviewService.preview(user, previewId)`: the caller's preview through `owned()` (404, 403 MSG08) and the same `ImportPreviewResponse` as remove-rows, built by one private `summary(preview)` that `removeRows` also uses. `GET /api/v1/imports/previews/{previewId}` (`getImportPreview`) in the controller and the contract; `npm run generate:api`. Covers edge cases 29 and 30.
**Files:**
- Modify: `T/service/ImportPreviewService.java`, `T/service/impl/ImportPreviewServiceImpl.java`, `T/controller/ImportController.java`, `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**`
- Test: `TT/service/impl/ImportPreviewServiceImplTest.java` (AC-007.1, AC-007.2, AC-007.3), `TT/controller/ImportControllerTest.java` (the path and its role check)
**Dependencies:** Task 15
**Estimated size:** small

---

## Task 17: Duplicate Ticket ID code MSG_NEW_3

**Story:** STORY-002
**Feature:** duplicate-id-code
**Description:** D14. `CsvRowValidator` marks in-file duplicates with `MSG_NEW_3` (params `ticket_id`) instead of `MSG78`; MSG39 stays for the resolved date. The catalog entry is fe-foundation's (its AC-006.9, its own task): this task changes the backend only.
**Files:**
- Modify: `T/csv/CsvRowValidator.java`
- Test: `TT/csv/CsvRowValidatorTest.java` (AC-002.6)
**Dependencies:** none
**Estimated size:** small

---

## Task 18: CsvUpload kit component

**Story:** STORY-011
**Feature:** csv-upload-kit
**Description:** D12. `<CsvUpload name label required />` in `shared/ui` as in the design: one file by click or drag (`Upload.Dragger`, `maxCount={1}`, `accept=".csv"`), the file name listed with a remove action; `beforeUpload` returns `false` (never sends); a name not ending in `.csv` (any case) → MSG27, a file over 10 MB → MSG28, set on its own form field through `Form.useFormInstance()` without keeping the file; required → MSG01. Export it from `UI/index.ts`. Covers edge case 37. The pull request asks the FE Lead to review it.
**Files:**
- Create: `UI/CsvUpload/{CsvUpload.tsx,index.ts}`
- Modify: `UI/index.ts`
- Test: `UI/CsvUpload/CsvUpload.test.tsx` (AC-011.1, AC-011.2, AC-011.3)
**Dependencies:** none
**Estimated size:** small

---

## Task 19: Import Tickets screen — CSV tab

**Story:** STORY-008
**Feature:** import-tickets-screen
**Description:** `ImportTicketsPage` replaces the placeholder: antd `Tabs` Jira · CSV · Manual entry, CSV selected (`?tab=csv`, the default), Jira and Manual entry disabled. `CsvImportForm`: Project select from `useSession().projects` with `roleInProject === 'PROJECT_MANAGER'` (label `<jiraProjectKey> · <departmentName>`), Download template (`downloadCsvTemplate` + `lib/save-text-file.ts`, file `ticket-import-template.csv`), `CsvUpload`, Upload. `useUploadCsvImport` with `handlesOwnErrors`: success → `/import/preview?previewId=<id>`; field codes MSG27/MSG28 under the file field, inline codes (MSG29 with `column_names`, MSG04, MSG08) in an `Alert`; the form keeps its values; Upload shows loading. MSW handlers and data in `F/mocks/` for template and upload. Covers edge cases 34 (Upload) and 39.
**Files:**
- Create: `F/components/CsvImportForm.tsx`, `F/lib/save-text-file.ts`, `F/mocks/data.ts`
- Modify: `F/pages/ImportTicketsPage.tsx`, `F/mocks/handlers.ts`
- Test: `F/pages/ImportTicketsPage.test.tsx` (AC-008.1 – AC-008.8)
**Dependencies:** Task 15, Task 18
**Estimated size:** medium

---

## Task 20: Import Preview screen — Summary, rows and states

**Story:** STORY-009
**Feature:** import-preview-screen
**Description:** `ImportPreviewPage` replaces the placeholder. `?previewId` not a positive whole number → `<Navigate to={paths.importTickets} replace>`. `useGetImportPreview` → Summary (file name, `StatCard` Valid / Error / Removed, MSG30 `Alert`), `LoadingState`, `ErrorState` for 403 (MSG08) and 404 (MSG04). `ImportPreviewTable` on `useGetImportPreviewRows` (20 per page, local page state) with the AC-009.2 columns, Result as `StatusTag`, Error details as `msg(code, params)` (an unknown code → MSG06), checkbox selection with Removed rows disabled, a Preview button per row (opens the popup of Task 22; until then the button only sets the selected row). Mock handlers for the preview and rows paths. Covers edge cases 33 and 38.
**Files:**
- Create: `F/components/ImportPreviewTable.tsx`, `F/hooks/useImportPreview.ts`
- Modify: `F/pages/ImportPreviewPage.tsx`, `F/mocks/{data,handlers}.ts`
- Test: `F/pages/ImportPreviewPage.test.tsx` (AC-009.1, AC-009.2, AC-009.11, AC-009.12, AC-009.13)
**Dependencies:** Task 16, Task 17, Task 19
**Estimated size:** medium

---

## Task 21: Import Preview screen — Remove, Confirm, Cancel and leaving

**Story:** STORY-009
**Feature:** import-preview-actions
**Description:** Remove (disabled with no selection; refreshes the Summary and rows, clears the selection). Confirm (disabled while `validCount` is 0, loading while pending; success → MSG32 with `created` / `skipped` and Import Tickets without MSG37; error → the returned code, stay). Cancel → navigates to Import Tickets through the blocker. `useBlocker` + `ConfirmDialog` MSG37 (danger) for every move inside the app before Confirm. Mock handlers for remove-rows and confirm (MSG40 / MSG33 cases selectable in the mock data). Covers edge cases 34 (Confirm), 35 and 36.
**Files:**
- Modify: `F/pages/ImportPreviewPage.tsx`, `F/hooks/useImportPreview.ts`, `F/mocks/{data,handlers}.ts`
- Test: `F/pages/ImportPreviewPage.test.tsx` (AC-009.3, AC-009.5 – AC-009.10)
**Dependencies:** Task 20
**Estimated size:** medium

---

## Task 22: Import Ticket Preview popup

**Story:** STORY-010
**Feature:** import-ticket-preview-popup
**Description:** D15. `DetailModal` in `shared/ui` (Modal Header of kind Form, content, one Close button; exported from `UI/index.ts`; the FE Lead reviews it with `CsvUpload`). `ImportTicketPreviewModal` on `useGetImportPreviewRow`: Ticket ID, Title, Description, Author, Resolved date, the five Solution sections in full, Result, Error details, all read-only in `Descriptions`; loading and `ErrorState` inside the popup; Close keeps the Import Preview as it was. Wire it to the Preview button. Mock handler for the row path.
**Files:**
- Create: `UI/DetailModal/{DetailModal.tsx,index.ts}`, `F/components/ImportTicketPreviewModal.tsx`
- Modify: `UI/index.ts`, `F/pages/ImportPreviewPage.tsx`, `F/mocks/handlers.ts`
- Test: `F/pages/ImportPreviewPage.test.tsx` (AC-009.4, AC-010.1, AC-010.2, AC-010.3)
**Dependencies:** Task 20
**Estimated size:** small

---

## Task 23: Contract for the import type

**Story:** STORY-012, STORY-013, STORY-014
**Feature:** import-type-contract
**Description:** In the contract: `ImportType` enum; `importType` on the template query and the upload body (optional); `NEEDS_SOLUTION` in `ImportPreviewRowStatus`; `importType` and `needsSolutionCount` as required fields of `ImportPreviewResponse`; path `POST /imports/previews/{previewId}/fill-solution` (`fillImportPreviewSolution`, 200 / 400 / 403 / 404 / 409); 409 `MSG_NEW_4` on confirm. Regenerate the client. The feature's mocks return the new fields, so the frontend stays green.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (generated only), `F/mocks/{data,handlers}.ts`
**Dependencies:** none
**Estimated size:** small

---

## Task 24: Upload a file without Solutions

**Story:** STORY-012, STORY-016
**Feature:** import-type-upload
**Description:** Changeset `<next free number>-ticket-import-type.sql` (new `import_type` column; `import_preview_row.status` widened to `VARCHAR(20)`) and its include. `ImportType`, `PreviewRowStatus.NEEDS_SOLUTION`, `ImportPreview.importType`, the row constructor's type. `parse(in, type)` and `validate(rows, existing, type)`. Upload counts by status. Controller: `importType` with default `WITH_SOLUTION` on upload and template. `ImportPreviewResponse` and `summary` carry the type and `needsSolutionCount`, from the existing count query.
**Files:**
- Create: the changeset, `T/entity/ImportType.java`
- Modify: `T/entity/{PreviewRowStatus,ImportPreview,ImportPreviewRow}.java`, `T/csv/{CsvColumns,CsvTicketParser,CsvRowValidator}.java`, `T/dto/ImportPreviewResponse.java`, `T/service/CsvImportService.java`, `T/service/impl/{CsvImportServiceImpl,ImportPreviewServiceImpl}.java`, `T/controller/ImportController.java`, `db.changelog-master.xml`
- Test: `TT/csv/*`, `TT/service/impl/*`, `TT/controller/*` (AC-012.1 – AC-012.7, AC-016.1; edge cases 40, 41, 42, 52)
**Dependencies:** Task 23
**Estimated size:** medium

---

## Task 25: Fill solution endpoint

**Story:** STORY-013
**Feature:** preview-fill-solution
**Description:** `POST /imports/previews/{previewId}/fill-solution`: `FillSolutionRequest` with `@Valid` (`rowNumbers` not empty; MSG01 for each empty required section; MSG02 for over 10,000 characters in total); `ImportPreviewService.fillSolution`, in this order: `owned` (404, 403), `lockByIdAndOwner`, type and row checks (409 MSG06, 404), then `row.fillSolution` on every row, all in one transaction. Duplicate numbers count once.
**Files:**
- Create: `T/dto/FillSolutionRequest.java`
- Modify: `T/entity/ImportPreviewRow.java`, `T/service/ImportPreviewService.java`, `T/service/impl/ImportPreviewServiceImpl.java`, `T/controller/ImportController.java`
- Test: `TT/service/impl/*`, `TT/controller/*` (AC-013.1 – AC-013.7; edge cases 43, 44, 45, 46)
**Dependencies:** Task 24
**Estimated size:** medium

---

## Task 26: Confirm of a file without Solutions

**Story:** STORY-014, STORY-016
**Feature:** import-type-confirm
**Description:** `RowsNotReadyException` → 409 `MSG_NEW_4` with `count`, in `GlobalExceptionHandler`'s existing style for import errors. Checked first in `confirm` and again in `importRows` under the lock. No Import Batch. `toTicket` unchanged. A test that a filled row becomes a Ticket with the written Solution, Published, with an approve decision and a `TicketsPublishedEvent`.
**Files:**
- Create: `T/exception/RowsNotReadyException.java`
- Modify: `T/service/impl/ImportConfirmServiceImpl.java`, and the exception's handler
- Test: `TT/service/impl/ImportConfirmServiceImplTest` and the DB-backed confirm test (AC-014.1, AC-014.2, AC-016.1; edge cases 47, 48, 49)
**Dependencies:** Task 25
**Estimated size:** small

---

## Task 27: Import type on the CSV tab

**Story:** STORY-015
**Feature:** import-type-form
**Description:** `Radio.Group` "Import type" in a `FormField`, initial `WITH_SOLUTION`. Download template and Upload send the chosen type. Changing the type keeps the file.
**Files:**
- Modify: `F/components/CsvImportForm.tsx`, `F/mocks/handlers.ts`
- Test: `F/pages/ImportTicketsPage.test.tsx` (AC-015.1)
**Dependencies:** Task 23
**Estimated size:** small

---

## Task 28: Write the Solution of one row

**Story:** STORY-015
**Feature:** row-solution-popup
**Description:** `SolutionFormModal` on `FormModal` (the row's read-only fields, the five sections, Apply). `useFillPreviewSolution` refreshes the Summary and the rows; 400 field errors go to the form, and the popup stays open with what was written. Preview opens it for a Needs Solution or Valid row of a type 2 preview, and `ImportTicketPreviewModal` otherwise. `NEEDS_SOLUTION` in `status-tags.ts`; StatCard Needs Solution for type 2. Mock handler for fill-solution.
**Files:**
- Create: `F/components/SolutionFormModal.tsx`
- Modify: `F/pages/ImportPreviewPage.tsx`, `F/hooks/useImportPreview.ts`, `F/mocks/{data,handlers}.ts`, `frontend/src/shared/ui/StatusTag/status-tags.ts`
- Test: `F/pages/ImportPreviewPage.test.tsx` (AC-015.2, AC-015.3, AC-015.7; edge case 51)
**Dependencies:** Task 23
**Estimated size:** medium

---

## Task 29: Fill solution and the Confirm lock on screen

**Story:** STORY-015
**Feature:** fill-solution-screen
**Description:** `ImportPreviewTable` reports the selected rows, and the page keeps each selected row's status. Fill solution appears only on a type 2 preview and is enabled by AC-015.4. It opens `SolutionFormModal` empty, asks MSG36 with the count of selected Valid rows, and clears the selection on success. `MSG_NEW_4` in the catalog; Alert and Confirm disabled while `needsSolutionCount + errorCount > 0` on type 2.
**Files:**
- Modify: `F/components/{ImportPreviewTable,SolutionFormModal}.tsx`, `F/pages/ImportPreviewPage.tsx`, `F/mocks/handlers.ts`, `frontend/src/shared/messages/catalog.ts`, `frontend/src/shared/messages/catalog.test.ts`
- Test: `F/pages/ImportPreviewPage.test.tsx` (AC-015.4, AC-015.5, AC-015.6; edge case 50)
**Dependencies:** Task 28
**Estimated size:** medium
