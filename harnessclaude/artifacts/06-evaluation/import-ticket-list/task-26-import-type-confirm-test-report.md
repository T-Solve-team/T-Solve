# Test Report: import-ticket-list — Task 26: import-type-confirm

**Date:** 2026-11-07. The reviewer made the run at HEAD `6267856`. The feature commit is `a1093d8`. One run is shared by tasks 24, 25 and 26.
**Commands** (Git Bash, in `backend/`): `export JAVA_HOME="/c/Program Files/JetBrains/IntelliJ IDEA 2025.1.1.1/jbr"; export PATH="$JAVA_HOME/bin:$PATH"; mvn -B -Dapi.version=1.44 clean verify`. This runs compile, surefire, failsafe (Testcontainers PostgreSQL) and `spotless:check`.
**Traceability:** `bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-014 STORY-016`

## Results
- Compile (main + test): pass
- Surefire (unit): 635 run, 0 failed, 0 errors, 0 skipped
- Failsafe (integration): 43 run, 0 failed, 0 errors, 0 skipped
- `spotless:check`: pass
- `BUILD SUCCESS`, exit 0, 3 min 19 s

The classes this task touches: `ImportConfirmServiceImplTest` 22/22, `ImportConfirmIT` 11/11.

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not reported | The spec is silent and no coverage plugin is configured |

## Acceptance Criteria → Tests
`sdd.ts trace` result: 3/3 acceptance criteria have a test, exit 0.

| AC | Tests | Result |
|---|---|---|
| AC-014.1 | `ImportConfirmServiceImplTest:379`: 409 `MSG_NEW_4`, count 5 = 3 Needs Solution + 2 Error, no save, no batch, no transaction. `:396`: only Needs Solution rows → `MSG_NEW_4`, not MSG30 (edge case 47). `:410`: the second check under the lock rejects, and the transaction rolls back with no Failed batch (edge case 48). `:428`: every row Removed → MSG30 (edge case 49). `ImportConfirmIT:241` (DB): `MSG_NEW_4` count 1, no ticket, no batch, preview kept. | pass |
| AC-014.2 | `ImportConfirmServiceImplTest:439` (all Valid or Removed → 2 Published, commit) · `ImportConfirmIT:255` (DB: written Solution on the ticket, PUBLISHED, two APPROVE decisions, `TicketsPublishedEvent` after commit with 2 ids, preview deleted) | pass |
| AC-016.1 | `ImportConfirmServiceImplTest:451` (a file with Solutions still skips Error rows and never counts Needs Solution) · task 24 tests | pass |

## Failures
None.

## Notes
- The first run without `clean` failed on stale `target/` classes before any test ran (see the task 24 report). The `clean` run is the one reported.
- `ImportConfirmIT:255` does not check the `import_batch` row of a type 2 Confirm (AC-004.3 through AC-014.2). The batch code is shared with type 1, which `ImportConfirmIT:309` checks.
