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
