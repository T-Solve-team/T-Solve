# Task 17: Duplicate Ticket ID code MSG_NEW_3

**Module:** import-ticket-list
**Assignee:** Claude (with ngaidang53)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-11-01

### Working on: duplicate-id-code
- D14: `CsvRowValidator` marks in-file duplicates with `MSG_NEW_3` (params `ticket_id`) instead of `MSG78`. MSG39 stays for the resolved date. `features/users` uses MSG78 for its role-removal dialog, which confirms the clash.
- Tests first: the two AC_002_6 tests of `CsvRowValidatorTest` expect `MSG_NEW_3` (2 failures before the fix); one renamed `everyRowSharingATicketIdIsMsgNew3_AC_002_6`.
- Deviation: the contract's `RowError` description also named MSG78; it now names `MSG_NEW_3` (doc only; `rowError.ts` comment regenerated). Not in the task's file list.
- The catalog entry is fe-foundation Task 11 (`catalog-msg-new-3`).

### Verify
- `mvn verify` (same environment flags as Task 15): 233 unit tests + 10 IT, 0 failures, Spotless OK.
- Frontend: `format:check`, `typecheck`, `lint`, `test`, `build` pass; `generate:api` is stable.

### Commit
- default@da59564

### Next up
fe-foundation Task 11 catalog-msg-new-3, then Task 18 csv-upload-kit

## Session 2026-11-04

### Working on: duplicate-id-code — Low findings of review attempt 1
- Review issue 1: `everyRowSharingATicketIdIsMsgNew3_AC_002_6` asserts `MSG_NEW_3` on rows 1, 3 and 4.
- Review issue 2 (MSG78 in the design diagram) not changed: a design edit goes through the plan skill.
- Verify: `mvn verify` BUILD SUCCESS (235 unit tests, 1 skipped as before; 10 ITs; spotless) with JDK 24 and `-Dapi.version=1.44` for Docker Desktop 29; frontend `format:check`, `lint` (0 errors, the old `TicketListFilters.tsx` warning), `typecheck`, `test` (58 files, 1083 passed), `build` pass; `generate:api` leaves no diff.
- Commit: default@d8abe06

### Next up
Module completion
