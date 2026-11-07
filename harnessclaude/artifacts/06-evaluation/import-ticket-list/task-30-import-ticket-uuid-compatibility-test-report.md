# Test Report: import-ticket-list — Task 30: import-ticket-uuid-compatibility

**Date:** 2026-11-07. The run is at HEAD `ef9913c` (branch `feat/KAN-31-ticket-import-type`) with no uncommitted change under `backend/`. The original feature commit is `dbfaea6` (KAN-24). It reached `main` squashed into `77fa3a31`, so the current code of the same files is the one tested.
**Commands** (Git Bash, in `backend/`, with `export JAVA_HOME="/c/Program Files/JetBrains/IntelliJ IDEA 2025.1.1.1/jbr"; export PATH="$JAVA_HOME/bin:$PATH"`):
1. Full suite (the dispatcher ran it, finished 2026-11-07 15:07 +07:00, after the HEAD commit at 15:03): `mvn -B -Dapi.version=1.44 clean verify`. This runs compile, surefire, failsafe (Testcontainers PostgreSQL / pgvector) and `spotless:check`. Log: `C:/Users/admin/AppData/Local/Temp/claude/53e9b9e089520e92/08fadbe9-4a97-41c3-b6f5-1a0f37957bd0/scratchpad/verify4.log`.
2. Reviewer's own run of the task's classes: `mvn -B -Dapi.version=1.44 verify -Dtest=ImportConfirmServiceImplTest -Dit.test='ReviewStorageIT,ImportConfirmIT' -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false`. Log: `C:/Users/admin/AppData/Local/Temp/claude/53e9b9e089520e92/08fadbe9-4a97-41c3-b6f5-1a0f37957bd0/scratchpad/task30-rerun.log`.

**Traceability:** `bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-004`

## Results
Full suite (run 1):
- Compile (main + test): pass
- Surefire (unit): 804 run, 0 failed, 0 errors, 0 skipped
- Failsafe (integration): 136 run, 0 failed, 0 errors, 0 skipped
- `spotless:check`: pass (13 files clean)
- `BUILD SUCCESS`, 3 min 36 s

Task classes (run 2, reviewer): `BUILD SUCCESS`, exit 0, 44 s.
- `ImportConfirmServiceImplTest` 22/22
- `ReviewStorageIT` 4/4 (each test starts its own PostgreSQL 16 + pgvector container and runs the real changelog)
- `ImportConfirmIT` 12/12
- `spotless:check`: pass

The `ERROR` log lines in run 2 come from tests that force a failure (`ImportConfirmServiceImplTest` recorder failure, `ImportConfirmIT` listener that throws after commit). They are expected.

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not reported | The spec is silent and no coverage plugin is configured |

## Acceptance Criteria → Tests
`sdd.ts trace` result: 13/13 acceptance criteria of STORY-004 have a test, exit 0.

| AC | Tests | Result |
|---|---|---|
| AC-004.10 | `ImportConfirmServiceImplTest:301` `publishesTheNewTicketIdsInsideTheImportTransaction_AC_004_10`: the event carries `List<UUID>` equal to the ids of the saved `Ticket`s (`UUID(0,101)`, `UUID(0,102)`), published before the commit, so AFTER_COMMIT listeners get it after it. `ImportConfirmIT:321` `eventArrivesAfterTheTicketsAreCommitted_AC_004_10`: a real `@TransactionalEventListener(AFTER_COMMIT)` sees 3 ids and, on its own connection, 3 committed tickets. `ImportConfirmIT:333`: a listener that throws after commit does not undo the import. | pass |
| AC-004.13 | `ReviewStorageIT:192` `upgradesExistingTicketsWithoutInventingQueueEntryTime_AC_009_9_AC_009_11_AC_004_13_BUG_140`: a database at changeset 007 gets a CSV, Published ticket `TEST-1` and an `APPROVE` decision on its BIGINT key; the whole changelog then runs. After it, the ticket is found by `source_ticket_id = 'TEST-1'` and `resolution_steps = 'Restart'` with a UUID key, its `project_id` is `UUID(0,1)`, the decision's `ticket_id` is that UUID, and `review_decision` / `evidence` rows can reference it. | pass (see notes) |
| AC-004.1–AC-004.9, AC-004.11, AC-004.12 | The import regression suite (`ImportConfirmServiceImplTest`, `ImportConfirmIT`, `ImportControllerTest`), unchanged in substance by this task. `ImportConfirmIT:417` still proves the unique constraint now on `source_ticket_id`. | pass |

Other migration tests in `ReviewStorageIT` (`:27` BUG-144, `:64` BUG-145, `:132` BUG-140) cover the upgrade paths from a database that already ran Search or Review: `public_id`, `project_id`, `updated_at`, embeddings and feedback keep their values, the temporary `id` / `review_legacy_id` columns are gone, and `008-review-ticket-storage` is applied once.

## Failures
None.

## Notes
- `ReviewStorageIT:192` uses one ticket and one decision. With one row, it cannot tell a correct ticket-to-decision remap from a wrong one. Of the Solution data it checks only `resolution_steps`. The migration SQL is correct on reading (review, issue 1).
- `ImportConfirmIT:321` records only the number of ids in the event, not their values. Only the mock-based unit test shows they are the row keys (review, issue 2).
