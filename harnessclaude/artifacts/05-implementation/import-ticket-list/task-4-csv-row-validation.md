# Task 4: Row validator

**Module:** import-ticket-list
**Assignee:** bluebearccc (with Claude)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-22 17:30

### Working on: csv-row-validation
- `CsvRowValidator` (`@Component`, takes a `Clock`):
  - `validate(rows, existingTicketIds)` returns one `Result(row, errors)` per row, with every failed rule, in this order: MSG01 required (template order) → MSG02 field limits → MSG02 Solution total → MSG31 → MSG78 → MSG39.
  - Values are trimmed (`strip()`); blank counts as missing.
  - Lengths are counted in code points: ticket_id 50, title 200, description 10,000, author 100; the five Solution sections ≤ 10,000 in total (`field` = null, `field_name` = "Solution").
  - MSG31 when the trimmed id is in `existingTicketIds`. MSG78 on every row whose trimmed id appears more than once in the file; a row can get both.
  - MSG39 when `resolved_date` is not ISO `yyyy-MM-dd` or is after `LocalDate.now(clock)`.
  - Static `ticketIdsOf(rows)` gives the distinct trimmed ids, so the service can run one `findExistingTicketIds` query.
- `ticket/config/TicketClockConfig`: a `Clock` bean in Asia/Ho_Chi_Minh (spec AC-002.7). Not in the task's file list; it was needed to inject the clock.
- Messages carry only `code` + `params` (design B4): MSG01 `field_name`; MSG02 `field_name`, `max_length`; MSG31/MSG78 `ticket_id`.
- Tests (TDD: red on compile, then green): `CsvRowValidatorTest`, 15 tests, covering AC-002.1 – AC-002.7, AC-002.10 and edge cases 7 and 12–17 (whitespace-only values, trimming, an id that is a duplicate in the file and also existing, boundaries at 50/200/10,000, emoji length, invalid and future dates, the UTC vs Vietnam date boundary).
- Verify: `mvn spotless:apply`, then `mvn verify` → exit 0; 30 tests, 0 failures (validator 15 + parser 15).
- `sdd.ts trace` still cannot see JUnit tests (FEEDBACK.md, 2026-10-22). Coverage was checked by hand from the test names.

- Commit: default@230a332 (feat/KAN-31-import-ticket-csv)

### Blockers
none (the trace gap stands)

### Next up
Task 5 project-access-errors.
