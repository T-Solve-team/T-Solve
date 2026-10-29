# Task 11: 5,000-row performance check

**Module:** import-ticket-list
**Assignee:** bluebearccc (with Claude)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-22 21:00

### Working on: csv-import-performance
- `CsvImportPerformanceTest`:
  - builds a 5,000-row CSV of 9–10 MB in memory: Vietnamese, German and Japanese text and emoji, with commas, doubled quotes, line breaks inside quoted values, and CRLF row ends;
  - parses and validates it against one existing id;
  - asserts 5,000 results with exactly 1 Error row (MSG31), done in under 10 s. The NFR budget is 60 s; 10 s leaves room on a slow CI machine, and the assert is marked with a `ponytail:` comment.
  - Measured: about 0.37 s for the whole test on this machine.
  - The first run failed only because the generated file was over 10 MB; the repeat counts were reduced so it is 9.66 MB.
- End-to-end timing:
  - Setup: a temporary `pgvector/pgvector:pg16` on port 25432 and the backend on 18080 with `TSOLVE_STUB_PROJECTS=perf-pm:1`. The login was a throwaway Spring Boot user with a random password, never printed. The CSRF token came from the login page.
  - Script (not committed): `scratchpad/perf/e2e.sh` + `gen_csv.py`. Its file is the same shape as the unit test's, with ids `E2E-n`.

  | Step | File | Result | Time |
  |---|---|---|---|
  | Upload + preview | 9,660,124 bytes, 5,000 rows | 200; 5,000 valid, 0 error; first page 50 of 100 pages | **7.98 s** (NFR ≤ 60 s) |
  | Confirm | 5,000 valid rows | 200; MSG32 created 5,000, skipped 0 | **1.46 s** |

  - Database afterwards: `ticket` 5,000 rows, `ticket_review_decision` 5,000, `import_batch` one COMPLETED with 5,000, `import_preview` 0.
  - The container and the backend were stopped afterwards, and the cookie jar was deleted.
- Verify: `mvn spotless:apply`, then `mvn verify -DargLine="-Dnet.bytebuddy.experimental=true"` → exit 0; 87 tests, 0 failures.
- The real dataset (`dataset-tickets-multi-lang3-4k.csv`) was not used: it has no `ticket_id` and no Solution sections, so the synthetic file covers the same hard cases more directly.

- Commit: default@b688142 (feat/KAN-31-import-ticket-csv)

### Blockers
none (the trace gap stands, FEEDBACK.md 2026-10-22)

### Next up
Task 12 retire-snapshot-import.
