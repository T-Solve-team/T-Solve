---
module: wiki
feature: wiki-upload-api
task: 6
verdict: fail
attempt: 2
date: 2026-11-15
reviewer: main session (evaluate, test run)
iteration: 1
---

# Review: wiki — Task 6: Upload API and download (attempt 2)

## Summary
Attempt 1 passed on a static read and depended on `mvn verify` being green. Attempt 2 ran `mvn verify` on `feat/KAN-163-wiki-space-permissions` (HEAD `7e6155cd`). It is red, and this task causes it. Verdict fail.

## Criteria
| Criterion | Result | Notes |
|---|---|---|
| Functionality | pass (static) | Attempt-1 reading of AC-004.1 to AC-004.13 and AC-004.17 still holds |
| Completeness | pass | Every task AC has a named test (attempt 1 trace) |
| Correctness | fail | `mvn verify` fails; see Issues |
| Integration | fail | Breaks the application context when object storage is not configured |

## Traceability
Unchanged from attempt 1: each task AC has a test. The tests could not run green (see Test Report).

## Issues
1. `backend/src/main/java/vn/tsolve/wiki/service/impl/WikiUploadServiceImpl.java:77` and `WikiTrashServiceImpl.java:45`: critical (BUG-288). Both inject `FileStorage` directly, but `StorageConfig` creates that bean only when `tsolve.storage.endpoint` is set ("Empty endpoint = no storage beans"). With the default empty endpoint the context fails with `NoSuchBeanDefinitionException: vn.tsolve.platform.service.FileStorage`. About 200 full-context unit tests and almost every IT (542 errors / 739) fail, including all wiki ITs. Ticket services use `ObjectProvider<FileStorage>`. Fix: use `ObjectProvider<FileStorage>` and fail the request when storage is missing, or set a storage endpoint in the test config (decision needed).

2. `backend/pom.xml:45`: critical (BUG-287). `poi-ooxml` 5.3.0 is declared before `commons-csv` 1.12.0, so Maven resolves `commons-io` 2.16.1 (main resolves 2.17.0). `CsvTicketParserTest`, `CsvImportServiceImplTest` and `CsvImportPerformanceTest` fail with `ClassNotFoundException: org.apache.commons.io.input.UnsynchronizedBufferedReader`; CSV import breaks at runtime. Fix: pin `commons-io` 2.17.0 in `dependencyManagement`.

## Assumptions made
- Fixing BUG-288 needs a decision (see the bug row). The evaluator changed no code.

## Test Report
See `task-6-wiki-upload-api-test-report.md`.
