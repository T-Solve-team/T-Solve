# Task 10: Preview cleanup job

**Module:** import-ticket-list
**Assignee:** bluebearccc (with Claude)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-22 20:30

### Working on: preview-cleanup
- `PreviewCleanupJob.deleteExpiredPreviews()`:
  - runs on `@Scheduled(cron = "0 0 1 * * *", zone = "Asia/Ho_Chi_Minh")`;
  - cut-off = `Instant.now(clock) − 24 h`; `findIdsCreatedBefore(cutoff)` returns the expired previews;
  - each one is deleted with `deleteById`, a Spring Data call with its own transaction, so one failure doesn't affect the rest. Its rows go by ON DELETE CASCADE;
  - a failing deletion is logged (id only) and the loop continues;
  - it returns the number deleted.
- `ticket/config/TicketSchedulingConfig` has `@EnableScheduling`.
- The cron zone is explicit, so the job runs at 01:00 Vietnam time whatever the server's zone is.
- Tests (TDD: red on compile, then green): `PreviewCleanupJobTest`, 3 tests: AC-006.1 (deletes everything before now − 24 h), AC-006.2 (the cut-off is exactly 24 h, so a younger preview is kept), AC-006.3 (one failure, the others are still deleted).
- Verify: `mvn spotless:apply`, then `mvn verify -DargLine="-Dnet.bytebuddy.experimental=true"` → exit 0; 86 tests, 0 failures. The flag is only needed on this machine's JDK 24 (see task 9).

- Commit: default@93dfb5c (feat/KAN-31-import-ticket-csv)

### Blockers
none (the trace gap stands, FEEDBACK.md 2026-10-22)

### Next up
Task 11 csv-import-performance.
