---
module: wiki
feature: wiki-upload-api
task: 6
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: wiki — Task 6: Upload API and download (commit acf89c50)

## Summary
This review checks the code against spec STORY-004 (AC-004.1 to AC-004.13 and AC-004.17) and against AC-002.12 and AC-002.13. It reads only `git show acf89c50`. The implementation follows the spec and the master design closely. There is no critical or major finding.

**The pass holds only if `WikiUploadIT` and the whole `mvn verify` (spotless included) are green.** The reviewer ran no tests, as the dispatcher instructed.

Verified by reading:
- **Manager check first (AC-004.17):** `extract` and `publish` both call `requireManager` before they read any file.
  - An unknown Space, a Hidden one, or one in a Deleted Department answers 404 MSG04. A user who is not the manager gets `AccessDeniedException`, which answers 403 MSG08.
  - `@PreAuthorize(KNOWLEDGE_MANAGER)` sits in front of both endpoints.
  - The check runs again inside the write transaction under `lockActiveSpace ... FOR UPDATE`. A manager removed between the first check and the write therefore stores nothing.
- **File type and signature (AC-004.4):** `TextExtractor` checks the extension (ignoring case) and the magic bytes (`%PDF-`, `PK\x03\x04`). A failure answers MSG89 with `file_name`. The server sets the stored content type from the extension and never takes it from the client header.
- **Size (AC-004.5):** each file is limited to 10 MiB. The limit is checked on `getSize()` and again on a bounded `readNBytes(MAX+1)`. `max-request-size` is 101 MB (W21). Oversize maps to the global 413 MSG28.
- **Unreadable files (AC-004.2):** each of these cases gives `UNREADABLE`, an empty excerpt and 0 words:
  - an encrypted PDF
  - a parser error
  - text that is only whitespace
  - POI's zip-bomb exception
  - a multipart read error (BUG-270)

  At publish they answer MSG90 (AC-004.9).
- **Metadata (AC-004.7, AC-004.10):** missing metadata, invalid JSON, or a row count that differs from the file count answers MSG01 on `metadata`. A blank title answers MSG01 and a title over 200 characters answers MSG02 with `max_length` 200, both on `documents[i].title`.
- **Duplicate titles (AC-004.8):**
  - Titles are compared within the request, ignoring case.
  - They are compared with the Published titles of the target folder, once before the transaction and again inside the locked transaction.
  - A `uq_wiki_document_published_title` violation maps to 409 MSG84 on the row.
- **Atomicity and cleanup (AC-004.11):**
  - The files are stored first. The rows and audit entries go into one `REQUIRES_NEW` transaction.
  - If anything fails before commit, every stored key is deleted. Delete errors are logged at WARN and do not hide the original exception.
  - The `committed` flag stops the cleanup from deleting the files of rows that did commit.
- **Audit (AC-004.12):** each document gets one `PUBLISH` / `WIKI_DOCUMENT` entry. Its details hold exactly `operation`, `title`, `spaceId` (the public UUID), `folderId` and `visibility`. The entry is written in the same transaction, because `AuditServiceImpl` opens no transaction of its own.
- **Enqueue (AC-004.13):** one `wiki-index` job per document, in `afterCommit` only.
- **Download (AC-002.12, AC-002.13):**
  - Access goes through `findAvailableDocuments`, which requires Published plus the view rule. A document in the trash therefore answers 404 MSG110, even for the manager.
  - The file name goes into `ContentDisposition.attachment().filename(name, UTF-8)` (RFC 5987 `filename*`) with CR and LF removed.
  - The response sends `X-Content-Type-Options: nosniff`.
  - The stream is closed with try-with-resources, and the Workspace context is bound again on the async MVC thread.
- **Dependencies:** `pdfbox` 3.0.4 and `poi-ooxml` 5.3.0 match design W3 (3.0.x and 5.3.x).
- **Contract and scope:** the contract is in `tsolve-api.yaml` together with the generated client. No scope creep found. Bug-log rows BUG-269 to BUG-275 are recorded.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | All Task 6 ACs are implemented as written |
| Completeness | pass | Every Task 6 AC is named in at least one test. The IT covers: rollback when the 2nd audit write fails, a storage failure, a race of two uploads with the same title, and download under the view rule |
| Correctness | pass (conditional) | Not run by the reviewer. The IT waits for the orchestrator's Docker run |
| Integration | pass | The `wiki-index` handler type matches Task 7. The generated client matches the multipart contract, and metadata sent as a string is accepted (BUG-272) |

## Traceability
```
trace wiki (STORY-004): 23/23 acceptance criteria have a test
```
For Task 6:
- AC-004.1 to AC-004.13 and AC-004.17 are tested in `TextExtractorTest`, `WikiUploadServiceImplTest`, `WikiUploadControllerTest` and `WikiUploadIT`.
- AC-002.12 is tested in `WikiUploadControllerTest.streamsOriginalWithSafeInternationalFilenameAndNosniff_AC_002_12` and in `WikiUploadIT`.

## Issues
All issues are minor.
1. `backend/src/main/java/vn/tsolve/wiki/service/TextExtractor.java:52`: minor: PDFBox can emit U+0000 for some fonts, and PostgreSQL TEXT rejects 0x00. Such a PDF previews as READY, but publish then fails with a 500 (the stored files are cleaned up). Fix: strip `\u0000` in `readable()`.
2. `backend/src/main/resources/db/changelog/changes/031-wiki-tables.sql:47` (reached through Task 6): minor: `to_tsvector` fails when the tsvector of a text would exceed 1 MB ("string is too long for tsvector"). A very text-heavy 10 MB file therefore publishes with a 500. Fix: build the vector from `left(content, N)`, or catch the error and map it to a coded error. Record a bug row either way.
3. `backend/src/main/java/vn/tsolve/wiki/service/impl/WikiUploadServiceImpl.java:155`: minor: `file_name` is `VARCHAR(255)`. An original file name over 255 characters fails at `saveAndFlush` with a 500. Fix: reject it with a coded 400, or shorten the stored name. The spec says nothing about this, see A3.
4. `backend/src/main/java/vn/tsolve/wiki/service/impl/WikiUploadServiceImpl.java:170`: minor: if `jobs.enqueue` throws in `afterCommit`, the caller gets a 500 even though the documents are committed. The later documents of the batch are then never enqueued and stay `PENDING`. Fix: catch and log the failure per document inside the loop.
5. `backend/src/main/java/vn/tsolve/wiki/service/impl/WikiUploadServiceImpl.java:100`: minor: `extract` runs in a read-only transaction. A database connection is therefore held while up to 10 files of 10 MB are parsed. Fix: drop `@Transactional` from `extract`; its two lookups need no transaction.
6. `backend/src/main/java/vn/tsolve/wiki/service/TextExtractor.java:33`: minor: POI's defaults (minimum inflate ratio 0.01, 4 GB maximum entry) still let a 10 MB DOCX inflate to about 1 GB of XML. `OutOfMemoryError` is not caught by `IOException | RuntimeException`. This matches design row 31, so it is not a defect of this task. Fix, if wanted: lower the limit at startup with `ZipSecureFile.setMaxEntrySize(…)`, for example to 100 MB.
7. `backend/src/main/java/vn/tsolve/wiki/dto/WikiPublishMetadata.java:11`: minor: the Bean Validation annotations never run, because the service parses the metadata by hand. They are dead code and suggest a check that does not happen. Fix: remove them.

## Assumptions made
- **A1.** `mvn` and `npm` were not run, per the dispatcher. The pass depends on two orchestrator results: `mvn verify` green (including `WikiUploadIT`) and `generate:api` leaving no diff.
- **A2.** The title length in AC-004.7 is measured as received, before stripping, the same way AC-002.9 measures `q`. The frontend measures it the same way. A title of 200 characters plus a trailing space is therefore rejected.
- **A3 (open judgment call).** The spec has no rule for file names longer than 255 characters. Issue 3 stays minor until the user decides which code to answer.
- **A4.** The feature status was not changed, per the dispatcher. If `mvn verify` is green, run `bun harnessclaude/hooks/sdd.ts feature wiki wiki-upload-api passing`.

## Test Report
See `task-6-wiki-upload-api-test-report.md`.
