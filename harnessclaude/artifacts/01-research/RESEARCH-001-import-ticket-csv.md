---
id: RESEARCH-001
title: CSV parsing and validation for ticket import (KAN-31)
question: How should the backend parse and validate an uploaded ticket CSV (UC-21) before the Import Preview and on Confirm?
status: complete
date: 2026-10-21
related: []
---

# CSV parsing and validation for ticket import (KAN-31)

## Research Question
How should the Spring Boot backend parse and validate a ticket CSV (UC-21 Import tickets from CSV)? The question covers the parser library, file checks, the column contract, row-level errors and the preview → confirm flow.

## Context
Jira task KAN-31 (epic KAN-29 Import ticket list) implements UC-21. The answers feed the spec and design of the import module.

Sources:
- SRS `T-Solve_Report3_Software_Requirement_Specification_EN_v3456` (local extract in `SEP490_G89/_context/Report/Minh_sua_report123/`). Sections used: UC-21, GBR-23, GBR-26, GBR-33, the "Ticket ID is unique" and "Each import is one Import Batch" rules, the Import Tickets / Import Preview screen fields, MSG27–MSG33 and MSG40–MSG41.
- Google Drive has a newer version, v3460, which this spike did not diff.
- Backend: Spring Boot 3.3.5, Java 21, `spring-boot-starter-validation`, JPA + Liquibase, PostgreSQL. No CSV library and no multipart settings exist yet.
- Test data: `SEP490_G89/dataset-tickets-multi-lang3-4k.csv` — 4,000 rows, 6.6 MB, multilingual, columns `subject, body, answer, type, queue, priority, language, …, tag_1..tag_9`.

## Findings

### 1. What the SRS requires (facts)
| Step | Rule | Message |
|---|---|---|
| Upload | One `.csv` file, UTF-8, at most 10 MB | MSG27 "Only CSV files are supported.", MSG28 "File size must not exceed 10 MB." |
| Header | The required columns must be present, including the required Solution sections | MSG29 "The file is missing required column(s): {column_names}." |
| Row | A row is invalid when a required value is missing, a text is too long, or its ticket ID already exists | MSG30 summary count; MSG31 "Ticket ID {ticket_id} already exists. Use a new ID." |
| Preview | Valid rows show a read-only Solution, invalid rows show Result = Error and the error details. The PM can remove rows and can save a Draft (GBR-26). Confirm is disabled when no valid row is left. | MSG41 |
| Confirm | Remaining valid rows become Published Tickets with source CSV, writer = `author` column (may be empty) and an approve decision by the PM (GBR-23). Invalid rows are skipped. | MSG32 "{created} published, {skipped} skipped" |
| Confirm fails | No ticket is imported (all-or-nothing). The failure is still recorded in Import History as Failed (Import Batch rule). | MSG33 |
| Project inactive | The Project was removed or its Department was deleted before Confirm, so nothing is imported | MSG40 |
| Limits known | Ticket ID: text, ≤ 50 characters. Solution: ≤ 10,000 characters in total. | MSG02 |

Columns named in UC-21: `symptoms, root_cause, resolution_steps, workaround, scope`. The Import Preview shows Ticket ID (`ticket_id`), Title, Author (`author`), Resolved date and Solution. The full template header is not written down anywhere.

### 2. Parser library
A hand-written `split(",")` is wrong for this data. Solution sections are multi-line, and the sample dataset has commas and quotes inside fields. The parser must follow RFC 4180: quoted fields, `""` escapes and newlines inside quotes.

| Option | Fit | Notes |
|---|---|---|
| **Apache Commons CSV** (`org.apache.commons:commons-csv`, 1.12.x) | Best | Small and has no transitive dependencies. Header-based access (`record.get("ticket_id")`) and `getHeaderMap()` cover the MSG29 check. `getRecordNumber()` gives row numbers for error details. It streams the input, so a 10 MB file is not loaded twice. Spring Boot does not manage its version, so pin one. |
| Jackson `jackson-dataformat-csv` | OK | Spring Boot manages its version. It binds rows to a POJO through a `CsvSchema`. A malformed row breaks the `MappingIterator`, which makes "collect every row error" awkward. |
| OpenCSV | Weaker | Heavier. Its bean binding pulls in commons-beanutils, which has a CVE history. The annotations duplicate Bean Validation. |

### 3. File-level checks
- **Size:** Spring's default `spring.servlet.multipart.max-file-size` is **1 MB** and `max-request-size` is 10 MB. Set the file limit to 10 MB and the request limit slightly higher. A larger file throws `MaxUploadSizeExceededException` before the controller runs. `GlobalExceptionHandler` must map it to MSG28 (a `ProblemDetail` with a `code`, per `backend/AGENTS.md`).
- **Type:** check the `.csv` extension. Do not rely on `Content-Type`: browsers send `text/csv`, `application/vnd.ms-excel` (Excel installed on Windows) or `application/octet-stream`.
- **Encoding:** Excel's "CSV UTF-8" export adds a BOM (`﻿`). Without stripping it, the first header becomes `﻿ticket_id` and MSG29 fires on a correct file. Decode with `CodingErrorAction.REPORT` so non-UTF-8 bytes (an ANSI/Windows-1258 export) fail fast instead of being silently replaced with `�`.
- **Delimiter:** Excel on machines with a Vietnamese or European locale saves `;`-separated "CSV". The SRS does not cover this.

### 4. Row-level validation
- Map each record to a row DTO and validate it with Jakarta Bean Validation (`@NotBlank`, `@Size`), which is already on the classpath. Collect `ConstraintViolation`s per row; do not throw on the first error.
- Ticket ID uniqueness: run one query for all IDs in the file (`WHERE ticket_id IN (:ids)`), not one query per row. The **same ID twice inside one file** must also be caught; the SRS does not say how.
- GBR-33 conflicts with the screen field "Solution \*". GBR-33 makes only Symptoms and Resolution steps required. The screen field makes every section except Workaround required (Symptoms, Root cause, Resolution steps, Scope), and the Solution Form flow (UC-19 step 10) agrees with the screen field.
- Total Solution length ≤ 10,000 characters is a cross-field rule. It needs a class-level constraint or an explicit service check.

### 5. Preview → Confirm flow
| Option | Pros | Cons |
|---|---|---|
| A. Stateless: re-upload the file on Confirm | No storage | Breaks Draft (GBR-26 requires reopening without uploading again). The file can change between preview and confirm. |
| B. Client posts parsed rows back on Confirm | No storage | The server must re-validate everything. Drafts still need storage. |
| **C. Server persists the parsed preview** (rows + per-row status, keyed by a preview id) | Draft is the same record with a flag. Confirm works from trusted, already-validated data. | Needs a table and cleanup of abandoned previews. |

Confirm must still re-check ticket-ID uniqueness (a row may have been taken since the preview) and that the Project is still Active (MSG40), inside one `@Transactional` that inserts all tickets. The Failed Import Batch must be written in a separate transaction (`REQUIRES_NEW`). Otherwise the rollback that guarantees "no ticket imported" also erases the failure record.

### 6. Test data
The 4k dataset does not match the T-Solve template. It has no `ticket_id`, and `answer` is one text with no Solution sections. It is useful for parser robustness tests: multilingual UTF-8, embedded commas, quotes and newlines, and the 6.6 MB size. A mapped fixture (`subject`→title, `body`→description/symptoms, `answer`→resolution_steps, generated ticket IDs) would turn it into a happy-path load test close to the 10 MB limit.

## Recommendations
1. Use **Apache Commons CSV** with `CSVFormat.RFC4180` + header, BOM stripped, strict UTF-8 decoding.
2. Set `spring.servlet.multipart.max-file-size: 10MB` and map `MaxUploadSizeExceededException` → MSG28. Check the extension, not the content type.
3. Validate rows with Bean Validation on a row DTO. Run one batch query for existing IDs. Report every error of every row with its row number.
4. Persist the parsed preview server-side (option C) so Draft and Confirm share it. Confirm re-checks uniqueness and Project status in one transaction, and the Failed batch is recorded in `REQUIRES_NEW`.
5. Settle the open questions below in the spec (`[NEEDS CLARIFICATION]`) before planning.

## Open Questions
- Exact CSV template header: which columns besides `ticket_id, title, author, symptoms, root_cause, resolution_steps, workaround, scope`? Are `description` and `resolved_date` included, and in what date format?
- Which Solution sections are required? GBR-33 says Symptoms + Resolution steps; the screen field and UC-19 say every section except Workaround.
- How is a ticket ID repeated inside the same file handled: first row valid and later rows invalid, or all of them invalid? With which message?
- Is a non-UTF-8 file or a `;`-delimited file rejected with MSG27, or with a new message?
- Max length of title, description and author (only ticket ID ≤ 50 and Solution ≤ 10,000 total are specified).
- Is there a max row count for a 10 MB file, and a time budget for the preview (NFR)?
- How long are abandoned (non-Draft) previews kept before cleanup?
- Does SRS v3460 on Drive change UC-21? This spike read v3456.

## Related Artifacts
- SRS UC-21, GBR-23, GBR-26, GBR-33, MSG27–MSG33, MSG40–MSG41 (Report 3)
- `artifacts/03-modules/architecture.md`
- `backend/AGENTS.md` (error handling: `GlobalExceptionHandler`, `ProblemDetail`)
