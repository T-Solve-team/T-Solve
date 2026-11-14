# Test Report: dashboard-audit — Task 4: dashboard-api

**Date:** 2026-11-09
**Branch:** feat/KAN-150-dashboard-metrics-api
**Range:** `72556bd..7fe616d` (6b0425c contract + generated client, 7fe616d backend)
**Reviewer:** harnessclaude-reviewer subagent (independent; inputs: spec, design, commit range)

## Results

| Check | Result | Evidence |
|-------|--------|----------|
| `mvn verify` (unit + DashboardIT) | NOT RE-RUN BY REVIEWER | Dispatcher forbade `mvn` here (another process edits backend files; Docker tests run by the controller). Controller reports DashboardControllerTest, DashboardServiceImplTest and DashboardIT passed. |
| Palantir Java Format 2.39.0 (the pom's Spotless step), `--dry-run` on the 11 committed Java files | PASS | Run directly from `~/.m2` on copies of the files at `7fe616d` in the scratchpad: no file listed. Negative control (re-indented copy) was listed, so the check is live. |
| Unused imports (Spotless `removeUnusedImports`) | PASS | Script over every import of the 11 files: none unused. |
| `npm run typecheck` (frontend, generated client) | PASS | `tsc -b --noEmit`, exit 0, no output. |
| `npm run generate:api` no-diff | NOT RUN | Would write into the worktree; the reviewer is read only. Left to the controller's frontend verify. |
| `git diff --check 72556bd 7fe616d` | PASS | No whitespace errors. |
| `sdd.ts check dashboard-audit` | PASS | 5 stories, 61 acceptance criteria — OK. |
| `sdd.ts trace dashboard-audit STORY-004` | PASS for this task | 10/15; AC-004.1–004.7 and 004.13–004.15 traced; AC-004.8–004.12 missing as expected (Tasks 5–6). |

## Trace output

```
✓ AC-004.1  DashboardControllerTest, DashboardServiceImplTest
✓ AC-004.2  DashboardServiceImplTest
✓ AC-004.3  DashboardControllerTest, DashboardServiceImplTest
✓ AC-004.4  DashboardServiceImplTest
✓ AC-004.5  DashboardControllerTest, DashboardServiceImplTest
✓ AC-004.6  DashboardServiceImplTest
✓ AC-004.7  DashboardControllerTest, DashboardServiceImplTest, DashboardIT
✗ AC-004.8 .. AC-004.12  no test (later features dashboard-outcomes, dashboard-reuse-gaps)
✓ AC-004.13 DashboardIT
✓ AC-004.14 DashboardControllerTest
✓ AC-004.15 DashboardIT
trace dashboard-audit (STORY-004): 10/15 acceptance criteria have a test
```

## Tests in the range

- `DashboardControllerTest` (`@WebMvcTest` + SecurityConfig + GlobalExceptionHandler): contract JSON shape for Admin and
  PM, raw filters passed through, 400 MSG39/MSG06, 404 MSG04, 403 MSG08 out of scope, 403 MSG08 employee and 401
  MSG07 anonymous with no service call.
- `DashboardServiceImplTest` (Mockito, fixed clock 2026-11-07 VN): 30-day default, 15 invalid date pairs incl. one
  date, malformed, reversed, future, 367 days, empty, timestamp; exactly 366 days across a leap year; VN day bounds;
  zero skeleton; PM own scope, other Department, other Department's Project, no Department; Admin four scopes and
  Admin+PM precedence; 6 malformed UUID forms; unknown ids 404.
- `DashboardIT` (Testcontainers `pgvector/pgvector:pg16`, Liquibase): Admin options only active Departments and their
  active Projects (D6); PM options own Department incl. inactive; inactive ids resolve; row counts unchanged;
  controller has only the four optional filters and no header.

## Defects

None logged. No bug found against an existing criterion.
