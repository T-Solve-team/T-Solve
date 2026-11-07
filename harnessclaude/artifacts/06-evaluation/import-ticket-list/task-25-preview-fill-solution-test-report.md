# Test Report: import-ticket-list — Task 25: preview-fill-solution

**Date:** 2026-11-07. The reviewer made the run at HEAD `6267856`. The feature commit is `9204467`. One run is shared by tasks 24, 25 and 26.
**Commands** (Git Bash, in `backend/`): `export JAVA_HOME="/c/Program Files/JetBrains/IntelliJ IDEA 2025.1.1.1/jbr"; export PATH="$JAVA_HOME/bin:$PATH"; mvn -B -Dapi.version=1.44 clean verify`. This runs compile, surefire, failsafe (Testcontainers PostgreSQL) and `spotless:check`.
**Traceability:** `bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-013`

## Results
- Compile (main + test): pass
- Surefire (unit): 635 run, 0 failed, 0 errors, 0 skipped
- Failsafe (integration): 43 run, 0 failed, 0 errors, 0 skipped
- `spotless:check`: pass
- `BUILD SUCCESS`, exit 0, 3 min 19 s

The classes this task touches: `ImportControllerTest` 21/21, `ImportPreviewServiceImplTest` 25/25, `ImportConfirmIT` 11/11.

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not reported | The spec is silent and no coverage plugin is configured |

## Acceptance Criteria → Tests
`sdd.ts trace` result: 7/7 acceptance criteria have a test, exit 0.

| AC | Tests | Result |
|---|---|---|
| AC-013.1 | `ImportPreviewServiceImplTest:298` `storesATrimmedCopyOfTheSolutionInEachRowAndMarksThemValid_AC_013_1` (also edge case 45: a number given twice is looked up once) · `ImportControllerTest:288` | pass |
| AC-013.2 | `ImportControllerTest:317` (MSG01 on every empty section, service not called) · `ImportPreviewServiceImplTest:325` (exactly 10,000 code points accepted) · `:338` (10,001 → 400 MSG02, no row read) (edge case 43) | pass |
| AC-013.3 | `ImportPreviewServiceImplTest:352` (file with Solutions → MSG06) · `:364` (Error or Removed row among fillable ones → MSG06, the fillable row unchanged) · `ImportControllerTest:344` (409) (edge case 46) | pass |
| AC-013.4 | `ImportPreviewServiceImplTest:390` (missing row number → 404, no change) · `:401` (another user / gone) · `:410` (gone after the lock) · `ImportConfirmIT:212` (waits for a held `FOR UPDATE` lock, then 404 — edge case 44) | pass |
| AC-013.5 | `ImportPreviewServiceImplTest:420` (403 MSG08, no row read) · `ImportControllerTest:358` (role) | pass |
| AC-013.6 | `ImportPreviewServiceImplTest:433` (Valid row's Solution replaced, workaround cleared) | pass |
| AC-013.7 | `ImportConfirmIT:197` (DB: counts, row 3 Valid with trimmed sections, row 2 unchanged) | pass |

`ImportControllerTest:333` `fillSolutionRejectsAnEmptyRowList` covers edge case 45 (empty list → 400). It names no AC.

## Failures
None.

## Notes
- The first run without `clean` failed on stale `target/` classes (`NoClassDefFoundError: SuggestionResultRecord` in a triage test, before any test ran). The `clean` run is the one reported.
- `ImportConfirmIT:212` polls `pg_locks` for a waiting lock (at most 5 s) instead of sleeping for a fixed time, so it is not timing-dependent. It passed in this run.
- Not covered by any test: a required section made only of Unicode whitespace such as U+3000 or U+2003. See review issue 1.
