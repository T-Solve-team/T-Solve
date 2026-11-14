---
module: ticket-repository
feature: manual-entry-evidence
task: 17
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: ticket-repository — Task 17: Evidence on a manual entry

## Summary
`EvidenceFiles` (`service/impl/EvidenceFiles.java`) checks the files before anything is stored:
- More than 5 files, counting a Draft's kept files, is MSG72 (`:50`).
- The type is the text after the last dot, lower-cased with `Locale.ROOT`, checked against the D18 allow-list
  (`:112-119`). `jpeg` is stored as `jpg`, and the browser's content type is never used.
- `MultipartFile.getSize()` over 10 MiB is MSG34 with the file name (`:54`).

It then stores each file through `platform` `FileStorage` outside any transaction (`:61-83`). If file N fails,
files 1..N-1 are deleted. `ManualEntryServiceImpl` deletes the stored keys on MSG31, MSG33 and 404 paths, and
inserts one `TicketEvidence` per file in the save transaction.

The file name is only stored. The object key is a server-made UUID under the workspace prefix
(`ObjectFileStorage.java:83-103`). `FileStorage` caps a file at `FILE_STORAGE_MAX_BYTES` = 10,485,760, the same
limit. A failed delete logs only the key and the exception class. Verdict: pass, with one Medium finding and one
judgment call for the user.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-006.2: Evidence rows with storage key, name, type, size (IT checks rows and keys). AC-006.6: edge 17 `x.pdf.exe` → MSG34, `A.PNG` accepted, `../../a.png` stored as a name only (`aFileNameIsOnlyStoredNeverUsedAsTheStorageKey_AC_006_6`). AC-006.7: the sixth file is MSG72 and nothing is stored. AC-006.8/AC-006.11: stored keys deleted. Edge 19: storage down at file 3 deletes files 1–2, records a Failed batch and saves no Ticket |
| Completeness | pass, with Issue 1 | Each criterion has a named test (`ManualEntryEvidenceTest` 28, `ManualEntryControllerTest`, `ManualEntryIT`). Edge 26 (5 × 10 MB) is tested at the service level. The AC-006.6 size rule is met for 10–11 MB, but not above 11 MB (Issue 1) |
| Correctness | pass | Every test passes in the full verify |
| Integration | pass | `FileStorage` is used through `Optional<FileStorage>`. With no storage configured, a request with files gets MSG33 and a Failed batch, not a silent loss (`filesWithoutAConfiguredStorageAreAFailureNotASilentLoss_AC_006_11`). A request without files still works |

## Security (uploads)
- **D18 type check:** server (`EvidenceFiles.java:112-119`) and client (`ManualEntryForm.tsx:45-49`) both use the
  text after the last dot, case-insensitive. Content is not sniffed; the spec puts that out of scope.
- **File name never used as a path:** confirmed. The key is a UUID from `ObjectFileStorage.store`, and the object
  path is `{workspaceId}/{uuid}`. The name only goes into `evidence.file_name` or the Draft JSON. React escapes it
  when shown.
- **10 MB / 5 files:** checked by the service before any store, for both Save and Draft.
- **Multipart and proxy limits:** servlet 11 MB per file and 56 MB per request (`application.yml:39-40`). Caddy
  allows 57 MB only for `/api/v1/imports/manual*` and 12 MB elsewhere. `ObjectFileStorage` buffers one file (10 MB)
  at a time in memory. Spring writes the parts to disk first, so a 56 MB request is not held in memory.
- **Logs:** the key and exception class only; no file name or content.
- No Critical or High finding.

## Traceability
`sdd.ts trace ticket-repository STORY-006`: 12/12 have a test. For this task: AC-006.2 (Evidence test, controller,
IT), AC-006.6 (Evidence test, controller), AC-006.7 (Evidence test, IT), AC-006.8 and AC-006.11 (Evidence test:
stored keys deleted).

## Issues
1. **[MEDIUM] Files over 11 MB get 413 MSG28, not 400 MSG34 with the file name (AC-006.6).**
   `backend/src/main/resources/application.yml:39-40` and `GlobalExceptionHandler.java:157-161`.
   - Expected (AC-006.6): any Evidence file larger than 10 MB is rejected with HTTP 400 and MSG34 naming that file.
   - Actual: a file over `max-file-size: 11MB` is refused by the servlet before the controller runs, and the answer is
     413 MSG28 ("File size must not exceed 10 MB.") with no file name. The same happens for a request over 56 MB.
   - The design records this trade-off, and the web app checks sizes first (AC-006.12), so the screen never sends
     such a file. Only a direct API client sees it, and nothing is saved either way.
   - Suggested fix (code): set `max-file-size` equal to `max-request-size` (56 MB). Then every single file reaches
     `EvidenceFiles.check` and gets MSG34 by name, and only an oversized request gets 413. The CSV upload keeps its own
     10 MB check and the 12 MB proxy limit.
   - Suggested fix (spec, if the trade-off is kept): add to AC-006.6 or the contract that a file over 11 MB or a
     request over 56 MB is answered 413 MSG28.
2. **[LOW] A file name over 255 characters fails the save with MSG33.** `008-review-ticket-storage.sql:37` defines
   `file_name VARCHAR(255)`, and nothing checks the name length. The insert then fails, the save answers MSG33 and
   records a Failed batch, instead of rejecting the file. A Draft accepts such a name (JSONB), so a Save from that
   Draft would always fail. Most file systems cap names at 255, so this is rare. Suggested fix: count a name over 255
   as MSG34 in `EvidenceFiles.check`.
3. **[LOW] No test sends an 11 MB+ part through MockMvc.** So the boundary of Issue 1 is not pinned. Suggested fix:
   add one when Issue 1 is decided.

## Assumptions made
- **Open judgment call for the user:** Issue 1 is a Medium deviation from the literal AC-006.6, not a blocking
  failure. The approved design chose it, the web app prevents it, and the response still rejects and saves nothing.
  If the user wants AC-006.6 met as written, this feature should become `fixing` with the one-line config change
  above.
- "10 MB" is read as 10 MiB (10,485,760 bytes), as the code, `FILE_STORAGE_MAX_BYTES` and the web app use.

## Test Report
`task-17-manual-entry-evidence-test-report.md`, which refers to the shared run in
`task-14-manual-entry-contract-test-report.md`.
