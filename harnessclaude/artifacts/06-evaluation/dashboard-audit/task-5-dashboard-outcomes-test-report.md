# Test Report: dashboard-audit — Task 5: dashboard-outcomes

**Date:** 2026-11-09
**Branch:** feat/KAN-150-dashboard-metrics-api
**Range:** `7fe616d..2cc0e41` (one commit: repository queries, service assembly, `DashboardOutcomesIT`)
**Reviewer:** harnessclaude-reviewer subagent (independent; inputs: spec, design, commit range)

## Results

| Check | Result | Evidence |
|-------|--------|----------|
| `mvn verify` (unit + ITs) | NOT RE-RUN BY REVIEWER | Dispatcher forbade `mvn` here (another process edits backend files; Docker tests run by the controller). Controller reports `Dashboard*Test` 43/43, `DashboardIT` 6/6, `DashboardOutcomesIT` 6/6 passed on Testcontainers `pgvector/pgvector:pg16`. |
| Palantir Java Format 2.39.0 (the pom's Spotless step), `--palantir --dry-run` on the 3 committed Java files | PASS | Run directly from `~/.m2` on copies of the files at `2cc0e41` in the scratchpad: no file listed. Negative control (a re-indented copy of `DashboardServiceImpl`) was listed, so the check is live. |
| Unused imports (Spotless `removeUnusedImports`) | PASS | Script over every import of the 3 files: none unused. |
| Analytics sources in the worktree equal `2cc0e41` | PASS | `git diff --quiet 2cc0e41 -- backend/src/{main,test}/java/vn/tsolve/analytics` exit 0 — the reviewed code is the tested code. |
| `git diff --check 7fe616d 2cc0e41` | PASS | No whitespace errors. |
| `sdd.ts check dashboard-audit` | PASS | 5 stories, 61 acceptance criteria — OK. |
| `sdd.ts trace dashboard-audit STORY-004` | PASS for this task | 12/15; AC-004.8 and AC-004.9 now traced to `DashboardOutcomesIT`; AC-004.10–004.12 missing as expected (Task 6). |

## Trace output

```
✓ AC-004.1  DashboardControllerTest, DashboardServiceImplTest
✓ AC-004.2  DashboardServiceImplTest
✓ AC-004.3  DashboardControllerTest, DashboardServiceImplTest
✓ AC-004.4  DashboardServiceImplTest
✓ AC-004.5  DashboardControllerTest, DashboardServiceImplTest
✓ AC-004.6  DashboardServiceImplTest
✓ AC-004.7  DashboardControllerTest, DashboardServiceImplTest, DashboardIT
✓ AC-004.8  DashboardOutcomesIT
✓ AC-004.9  DashboardOutcomesIT
✗ AC-004.10 .. AC-004.12  no test (later feature dashboard-reuse-gaps, Task 6)
✓ AC-004.13 DashboardIT
✓ AC-004.14 DashboardControllerTest
✓ AC-004.15 DashboardIT
trace dashboard-audit (STORY-004): 12/15 acceptance criteria have a test
```

## Tests in the range

`DashboardOutcomesIT` (`@DataJpaTest` + `Replace.NONE`, Testcontainers `pgvector/pgvector:pg16`, Liquibase, fixed
clock 2026-11-06T18:00Z = 2026-11-07 VN, period 2026-11-01..03 VN = `[2026-10-31T17:00Z, 2026-11-03T17:00Z)`):

- `countsDecisionsAndImportsPerDayAndProject_AC_004_8` — Admin Workspace: decisions exactly at the VN start, at
  23:59 VN, at 00:00 VN of the next day and at the last millisecond are in; one millisecond before the start and at
  the exclusive end are out (for all three decisions and all three import sources). CSV / MANUAL / JIRA_IMPORT count
  as approved by `created_at`; a SOLUTION_FORM ticket created in the period does not. Legacy-hex `project_id` ticket
  counted. Totals 5/3/1, rate 5/9, current pending 3, expired 2; per day; per Project ordered case-insensitively
  (`APP`, `app`, `ZZZ`) with pk tie-break. Hand-checked: every expected number matches the seed.
- `restrictsDecisionsImportsAndCurrentStatusesToDepartmentAndProject_AC_004_8` — Admin Department, PM own scope
  (equal), Admin Project, PM own Project (equal); Admin Department + Project of another Department → zeros, null
  rate, three zero days, no Project rows (accepted planning call).
- `listsEveryEmptyDayAndKeepsCurrentCountsOutsidePeriod_AC_004_8` — period with no events: five zero days, null
  rate, pending/expired still current (D3).
- `countsCurrentCoverageByDepartmentIncludingDeactivatedDepartment_AC_004_9` — Workspace 3/6 = 0.5; per Department
  incl. the deactivated `Zulu` (D6), which is absent from the options; coverage independent of the period; Admin
  scoped to the deactivated Department.
- `scopesCoverageToDepartmentAndPublicProjectIdIncludingLegacyTickets_AC_004_9` — Department, PM, Project with a
  legacy ticket, PM Project, disjoint Department/Project → `(0, 0, null, [])`.
- `returnsNullShareWhenScopeHasNoTickets_AC_004_9` — empty Workspace, empty Project, empty Department → null share.

## Defects

None logged. No bug found against an existing criterion.
