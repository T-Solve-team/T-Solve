---
module: ticket-repository
feature: manual-evidence-backend
task: 19
verdict: pass
attempt: 1
date: 2026-11-14
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 19: Store Evidence on save and Save as draft

## Summary
Commit default@4aebbca2 switches both manual-entry writes to multipart. `entry` is read as a String part and parsed
with ObjectMapper, and `files` is optional. The commit adds the D19 file rules (MSG34 / MSG72) and stores files through
`ObjectProvider<FileStorage>` outside a `TransactionTemplate` transaction. Stored files are deleted if the transaction
throws, and a missing or failing storage answers 503 MSG06. On save it writes `evidence` rows for kept and new files;
on Save as draft it writes and drops `import_preview_evidence` rows. Dropped objects are deleted after commit, and
Draft delete deletes the Draft's objects. Reopen lists the Evidence. The multipart limits go to 20 MB per file and
52 MB per request.

Every AC-006.15–AC-006.21 behaves as the spec says and has a named unit test and IT. No FileStorage call happens inside
a transaction, and the tests assert this with `TransactionSynchronizationManager`. No CRITICAL findings. **PASS.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | See the per-focus checks below. |
| Completeness | pass | Every file in the Task 19 list is changed or created; `EvidenceUpload` DTO and `EvidenceFiles` helper added; `TicketEvidenceRepository` now `JpaRepository` (task-18 Info 4 closed). YAML summaries mention MSG34/MSG72/MSG06 (task-18 Info 3 closed). |
| Correctness | pass | 137/137 (ManualTicket* incl. ManualTicketControllerIT 19, ImportDraftServiceImplTest, CsvImportServiceImplTest, ImportControllerTest, GlobalExceptionHandlerTest). |
| Integration | pass | `ImportConfirmServiceImpl` and `ImportPreviewServiceImpl` exclude MANUAL previews; `deleteByOwner` and the cleanup job touch only non-Drafts. The only Draft deletion paths are `ManualTicketServiceImpl.create` (moves or deletes keys) and `ImportDraftServiceImpl.delete` (deletes keys), so no cascade orphans objects. |

### Focus checks
- **Check order 400 → 403 → 404 → file rules.** `create`: `validate` (400), `managesActive` (403), `checkDraft` (404,
  then `EvidenceFiles.validate`), and only then `write` stores
  (`ManualTicketServiceImpl.java:116-130`). `saveDraft` is the same with only `projectId` checked (`:199-208`).
  The test is `AC_006_16_existingFieldProjectAndDraftChecksRunBeforeFileChecks` (both endpoints).
- **No FileStorage in a transaction.** `create`, `saveDraft` and `ImportDraftServiceImpl.delete` lost `@Transactional`.
  `write` stores before `transaction.execute` and deletes after it returns or throws (`:307-318`). The only callers are
  the controllers, which have no transaction. Unit mocks assert `isActualTransactionActive()` is false in `store` and
  `delete`, and true in `saveAndFlush` and `draftEvidence.save`.
- **Compensation.** If storage fails mid-list, the keys stored so far are deleted, then 503
  (`EvidenceFiles.java` store catch; test `AC_006_20_secondStoreFailure…`). A DB or commit failure deletes all new
  keys and never the Draft's keys (unit `AC_006_20_commitFailure…`; IT `AC_006_20_databaseFailure…` checks that the
  Draft row and its evidence row survive). A Ticket ID 409 race (`saveAndFlush` unique violation → `ticketIdTaken`) and a
  Draft found gone under the lock (404 in `lockedDraft`) throw inside the same callback, so the same catch covers them
  (see Info 3).
- **Draft lock.** The pre-check reads without a lock. After the files are stored, the transaction re-reads the Draft
  and its Evidence under `lockByIdAndOwner` and runs the file rules again (`:137-140`, `:214-217`), so a concurrent
  save that changed or consumed the Draft is seen. The keys stored by this request are then compensated.
- **Kept Draft files survive Save.** `kept` rows become `TicketEvidence` with the same storage key; the Draft's rows
  go by cascade; only `previous − kept` keys are deleted, after commit. Tests
  `AC_006_15_savesKeptAndNewEvidenceAndNeverDeletesTheKeptObject` (unit) and `AC_006_15_keepsDraftEvidenceOnTheTicketWhenSaving` (IT).
- **Foreign keepEvidenceIds.** `kept()` filters the locked Draft's own rows by the ids (`:293-298`), so an id of another
  Draft or a random id is ignored and never becomes a row (unit tests pass random UUIDs).
- **No files → no storage.** `store` returns before `getIfAvailable` on an empty list; `delete` returns on an empty
  list (`AC_006_21_missingStorageWithFilesIs503AndWithoutFilesWorks` asserts `verifyNoInteractions(storageProvider)`).
  A save without files that drops Draft Evidence does call delete after commit, which is correct (AC-006.19). Without
  storage, that delete is only logged.
- **Logging.** Only `key=` is logged on a failed or skipped delete. No file names, content or exception messages are
  logged. The MSG34 `file_name` param goes back to the caller as the spec requires.
- **Entry without Content-Type.** `@RequestPart("entry") String` plus ObjectMapper; the IT posts a `MockPart` with no
  content type (task-18 Info 2 closed). Bad JSON, `null` and a bad uuid give 400 through the bad-body ProblemDetail
  (`AC_006_16_unreadableEntryUsesTheBadBodyProblemDetail`).
- **CSV / MSG28.** `CsvImportServiceImpl` still rejects > 10 MB with `UploadTooLargeException` (413 MSG28), and the servlet
  limit still maps to 413 MSG28. The MSG28 tests pass. There is one edge-order change (Info 2).

## Traceability
`sdd.ts trace ticket-repository STORY-006`: 21/22. AC-006.15…AC-006.21 are each traced in `ManualTicketEvidenceTest`
and `ManualTicketControllerIT`. AC-006.22 (web app) belongs to Task 20. Pre-existing warning: "AC-008.1 in tests but not in spec".

## Issues

CRITICAL: none.

Informational:
1. **Evidence over 20 MB is not MSG34.** A single file over 20 MB, or a request over 52 MB, is rejected by the servlet
   multipart limit as 413 MSG28 (`GlobalExceptionHandler.handleMaxUploadSizeExceededException`), not 400 MSG34 with
   the file name that AC-006.16 says. Files between 10 MB and 20 MB get the correct MSG34. The web app checks size
   before sending (AC-006.22), so only direct API callers see this. A servlet limit cannot be avoided. Suggested fix: a spec
   note that a payload above the server limit answers 413 MSG28, or map the exception to MSG34 for `/imports/manual*`
   (the file name is not known at that point). This is a judgment call; see Assumptions.
2. **CSV order edge.** Before, any upload over 10 MB died at the servlet with MSG28. Now a 10–20 MB file reaches
   `CsvImportServiceImpl`, which checks the extension first, so a 15 MB `.txt` answers MSG27 instead of MSG28. A
   `.csv` of that size still gets 413 MSG28. Harmless, but it is a behaviour change.
3. **Two compensation paths have no own test.** The Ticket ID unique-violation race (`ManualTicketServiceImpl.java:167-170`)
   and the "Draft gone under lock" 404 after the files are stored rely on the same `write` catch as the tested commit
   failure. They work by construction. Suggested: one unit case each, e.g. `saveAndFlush` throwing a 23505
   `DataIntegrityViolationException` → `storage.delete(newKey)` and 409.
4. **Store failures are silent.** `EvidenceFiles.store` turns any `IOException` or `RuntimeException` (including
   "No Workspace is bound" and `FileTooLargeException`) into 503 without logging anything. An operator cannot tell
   why uploads fail. Suggested: `log.warn("Evidence store failed: {}", e.getClass().getName())`, class name only,
   as `GlobalExceptionHandler` does.
5. **Config coupling.** If `FILE_STORAGE_MAX_BYTES` is set below 10 MB, a valid ≤ 10 MB file fails in
   `ObjectFileStorage` and answers 503 MSG06 instead of being stored. `.env.example` / the deploy notes should keep that value ≥ 10 MB.
6. **Orphans.** If the process dies between store and commit, or an `Error` (not a `RuntimeException`) escapes the
   transaction, the stored objects stay. The design accepts this (ponytail note, later storage sweep).
7. **Two-argument overloads.** The `default create(user, request)` and `saveDraft(user, request)` on `ManualTicketService`
   are used only by tests. backend AGENTS.md §9 says "no unused code" in main. Suggested: drop them and pass `List.of()` in the tests.

## Assumptions made
- Info 1 is not a FAIL. The 10 MB rule of AC-006.16 is met for every file the servlet accepts, and some server-wide
  payload ceiling is unavoidable. **Judgment call for the user:** accept 413 MSG28 above 20 MB (add a spec note), or
  require MSG34 there too.
- "Store no file" in AC-006.16/17 is read as "no file left stored". The rules run before any store. The re-check under
  the lock can reject after storing (a race), and in that case the stored files are deleted.
- The Docker IT `ManualTicketControllerIT` matched the `ManualTicket*` test pattern and ran (19/19 passing). This was
  not intended by the dispatcher but confirms the session log.

## Test Report
See `task-19-manual-evidence-backend-test-report.md`.
