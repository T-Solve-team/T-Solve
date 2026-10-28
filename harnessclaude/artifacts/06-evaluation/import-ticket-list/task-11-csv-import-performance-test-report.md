# Test Report: import-ticket-list — Task 11: 5,000-row performance check

**Date:** 2026-10-22
**Commands** (JDK 24 via `JAVA_HOME`; Byte Buddy experimental flag for JDK 24 — environment quirks, not code issues):
`cd frontend && npm run typecheck` · `npm run lint` · `cd backend && mvn -q verify -DargLine="-Dnet.bytebuddy.experimental=true"` (Surefire + Failsafe/Testcontainers, `pgvector/pgvector:pg16`) · `mvn -q spotless:check` · `bun harnessclaude/hooks/sdd.ts trace import-ticket-list`

**One shared full run backs all 13 test reports of this module** (run once by the reviewer at `d15e991` on 2026-10-22; results in `backend/target/surefire-reports` and `backend/target/failsafe-reports`).

## Results
- Total: 91 · Passed: 91 · Failed: 0 · Skipped: 0 · `mvn verify` exit 0 (unit tests ≈ 8.2 s, `ImportConfirmIT` 24.9 s incl. container start)
- Typecheck: pass (`tsc -b --noEmit`) · Lint: pass (ESLint, no issues) · Spotless: pass

| Test class | Tests | Fail/Err/Skip |
|---|---|---|
| GlobalExceptionHandlerTest | 6 | 0/0/0 |
| ImportControllerTest | 11 | 0/0/0 |
| CsvImportPerformanceTest | 1 | 0/0/0 |
| CsvRowValidatorTest | 15 | 0/0/0 |
| CsvTicketParserTest | 15 | 0/0/0 |
| CsvImportServiceImplTest | 12 | 0/0/0 |
| ImportConfirmServiceImplTest | 11 | 0/0/0 |
| ImportPreviewServiceImplTest | 8 | 0/0/0 |
| PreviewCleanupJobTest | 3 | 0/0/0 |
| StubProjectAccessTest | 5 | 0/0/0 |
| ImportConfirmIT (Failsafe) | 4 | 0/0/0 |

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not measured — no coverage plugin (JaCoCo) in `backend/pom.xml` | spec silent (open question for the user) |

## Acceptance Criteria → Tests
`sdd.ts trace import-ticket-list` output: `trace import-ticket-list: 0/41 acceptance criteria have a test` (every AC listed as ✗ "no test under this module's paths"). This is the known harness gap: the tool scans only `*.test.*/*.spec.*/*_test.*` files and the `AC-001.1` spelling, while JUnit methods cite `_AC_001_1` (backend/AGENTS.md §2). The manual trace (grep of `backend/src/test` for `_AC_XXX_Y`) found a test for **41/41** ACs; the rows for this task follow.

| AC / NFR | Tests | Result |
|---|---|---|
| AC-002.1 / NFR 1 | CsvImportPerformanceTest.checksATenMegabyteFileWellWithinSixtySeconds_AC_002_1 — ~9.7 MB, 5,000 multilingual rows with quotes, commas and line breaks; parse + validate asserted < 10 s; the class took 0.401 s in this run | pass |
| NFR 1 end to end | Not re-run by the reviewer (needs a local PostgreSQL and the running app). The session log `artifacts/05-implementation/import-ticket-list/task-11-csv-import-performance.md` records upload + preview 7.98 s and Confirm 1.46 s for 5,000 rows, within the 60 s budget. | reported, not independently verified |

## Failures
None.

## Notes
- No flaky, skipped or slow (>5 s) unit tests. `ImportConfirmIT` takes ~25 s because it starts PostgreSQL in Docker.
- Expected ERROR log lines during `ImportConfirmIT` (forced trigger failure, unique-constraint race) are the tests working, but they print ticket row values — see `security-review.md` finding 2.
