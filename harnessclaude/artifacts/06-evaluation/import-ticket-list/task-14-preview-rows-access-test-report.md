# Test Report: import-ticket-list — Task 14: preview-rows-access

**Date:** 2026-10-23 (run by the reviewer at `58e0a06`; the same run backs `task-9-csv-confirm-test-report.md`, attempt 2)
**Commands** (JDK 24 via `JAVA_HOME`, Byte Buddy experimental flag; these are environment quirks): `cd backend && mvn -q verify -DargLine="-Dnet.bytebuddy.experimental=true"` (Surefire, plus Failsafe `*IT` with Testcontainers 1.21.4 on PostgreSQL 16) · `mvn -q spotless:check` · `bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-003 STORY-004`

## Results
- Total: 99 · Passed: 99 · Failed: 0 · Skipped: 0 · `mvn verify` exit 0, about 61 s wall clock
- Compile: pass · Spotless: pass
- Feature tests: `ImportPreviewServiceImplTest` 9/9 (0.1 s), including the new `callerWhoNoLongerManagesTheProjectCannotReadOrChangeRows_AC_003_4`. `ImportControllerTest` 11/11 shows the Spring context still wires `ImportPreviewServiceImpl` with its new `ProjectAccess` dependency.

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not measured (no JaCoCo in `backend/pom.xml`) | spec silent |

## Acceptance Criteria → Tests
`sdd.ts trace` output: `trace import-ticket-list (STORY-003, STORY-004): 0/16 acceptance criteria have a test`. Every AC shows ✗ "no test under this module's paths", which is the known harness gap for JUnit `_AC_XXX_Y` names. Manual trace:

| AC | Tests (`ImportPreviewServiceImplTest`) | Result |
|---|---|---|
| AC-003.1 | removesValidAndErrorRows_AC_003_1 | pass |
| AC-003.2 | rejectsAnotherUsersPreview_AC_003_2, rejectsAPreviewThatNoLongerExists_AC_003_2, rejectsARowNumberNotInThePreview_AC_003_2 | pass |
| AC-003.3 | returnsOneRowInFull_AC_003_3 | pass |
| AC-003.4 | callerWhoNoLongerManagesTheProjectCannotReadOrChangeRows_AC_003_4 | pass |

## Failures
None.

## Notes
- No flaky, skipped or slow unit tests in this feature.
