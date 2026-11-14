# Test Report: dashboard-audit — Task 6: dashboard-reuse-gaps

**Date:** 2026-11-09
**Branch:** feat/KAN-150-dashboard-metrics-api
**Range:** `05cb9f8..9f778b9` (one commit: feedback, gaps and suggestions queries, service wiring, `DashboardReuseGapsIT`,
and three Task 4 review fixes)
**Reviewer:** harnessclaude-reviewer subagent (independent; inputs: spec, design, commit)

## Results

| Check | Result | Evidence |
|-------|--------|----------|
| `mvn verify` (unit + ITs) | NOT RE-RUN BY REVIEWER | Dispatcher forbade `mvn` (the controller runs the full suite). Controller reports `Dashboard*Test` 43/43, `DashboardIT` 6/6, `DashboardOutcomesIT` 6/6, `DashboardReuseGapsIT` 10/10 on Testcontainers `pgvector/pgvector:pg16`. |
| Palantir Java Format 2.39.0 (the pom's Spotless step), `--palantir --dry-run` on the 7 committed Java files | PASS | Run directly from `~/.m2` on copies of the files at `9f778b9` in the scratchpad: no file listed (exit 0). Negative control (a re-indented copy of `DashboardServiceImpl`) was listed, so the check is live. |
| Unused imports (Spotless `removeUnusedImports`) | PASS | Script over every import of the 7 files: none unused (the three imports removed from `DashboardServiceImpl` are no longer referenced; `List` still is). |
| Reviewed sources equal the tested sources | PASS | `git diff --quiet 9f778b9 --` over `analytics/{repository,service,dto}`, `DashboardController.java` and `src/test/java/vn/tsolve/analytics`: exit 0. |
| `git diff --check 05cb9f8 9f778b9` | PASS | No whitespace errors. |
| `sdd.ts check dashboard-audit` | PASS | 5 stories, 61 acceptance criteria — OK. |
| `sdd.ts trace dashboard-audit STORY-004` | PASS | 15/15. AC-004.10, AC-004.11, AC-004.12 traced to `DashboardReuseGapsIT`; AC-004.15 to `DashboardIT` and `DashboardReuseGapsIT`. |

## Trace output

```
✓ AC-004.1   DashboardControllerTest, DashboardServiceImplTest
✓ AC-004.2   DashboardServiceImplTest
✓ AC-004.3   DashboardControllerTest, DashboardServiceImplTest
✓ AC-004.4   DashboardServiceImplTest
✓ AC-004.5   DashboardControllerTest, DashboardServiceImplTest
✓ AC-004.6   DashboardServiceImplTest
✓ AC-004.7   DashboardControllerTest, DashboardServiceImplTest, DashboardIT
✓ AC-004.8   DashboardOutcomesIT
✓ AC-004.9   DashboardOutcomesIT
✓ AC-004.10  DashboardReuseGapsIT
✓ AC-004.11  DashboardReuseGapsIT
✓ AC-004.12  DashboardReuseGapsIT
✓ AC-004.13  DashboardIT
✓ AC-004.14  DashboardControllerTest
✓ AC-004.15  DashboardIT, DashboardReuseGapsIT
trace dashboard-audit (STORY-004): 15/15 acceptance criteria have a test
```

## Tests in the range

`DashboardReuseGapsIT` (`@DataJpaTest` + `Replace.NONE`, Testcontainers `pgvector/pgvector:pg16`, Liquibase, fixed
clock 2026-11-06T18:00Z = 2026-11-07 VN, period 2026-11-01..03 VN = `[2026-10-31T17:00Z, 2026-11-03T17:00Z)`).
Every expected figure was hand-checked against its seed.

- `countsLatestRatingsWithinHalfOpenPeriodAndReturnsNumericRate_AC_004_10` — `updated_at` exactly at start and at
  the last millisecond in; one millisecond before and exactly at the end out (a row created in the period but
  updated at the end is out). 2/1, rate 2/3; a period with no feedback → `(0, 0, null, [], [])`.
- `ranksAtMostFiveByRateThenCountTitleAndPublicIdIncludingLegacyTickets_AC_004_10` — 8 rated tickets incl. a
  legacy-hex Project ticket; totals 13/3, 13/16; top 5 = rate desc, count desc, title, public id (the 6th, `Small`,
  cut); lowest 5 = rate asc, count desc, title.
- `scopesFeedbackToTicketDepartmentAndProjectAndReturnsNullWhenEmpty_AC_004_10` — Workspace, Admin Department, PM
  (equal), Admin / PM Project (equal), disjoint Department + Project and an empty Project → zeros and null rate.
  The feedback giver's Department is deliberately another one, so scope follows the ticket.
- `groupsSearchVariantsUsingEarliestSpellingAcrossChannelsAndDepartmentSnapshot_AC_004_11` — `" vpn ERROR "`,
  `"VPN error"`, `" VpN Error "` over WEB / ISSUE_PANEL / CHATBOT → one row `"VPN error"` (earliest, trimmed) count
  3; `result_count = 1`, before-start and at-end rows out; a search by a user of another Department but logged with
  this Department counts here (snapshot); `projectId` ignored (PM + empty Project, Admin + other Project, Admin
  Workspace + Project); Workspace includes another Department and a NULL `department_id`.
- `countsAndGroupsOnlyNoSourceQuestionsByAskerDepartmentAndPeriod_AC_004_11` — `ANSWERED` and out-of-period rows
  excluded; asker Department from `app_user`; Department 5, Workspace 7 incl. a user with no Department.
- `limitsBothGapListsToTenWithCountThenGroupingKeyOrder_AC_004_11` — 12 groups → 10 per list, count desc then
  grouping key; `chatbotNoSource` = 13, larger than the 11 shown, so the total is not cut by `LIMIT`.
- `returnsEmptyGapsForPeriodWithoutEvents_AC_004_11` — `([], 0, [])`.
- `countsOnlyCommentedSuggestionsAndYesAnswersWithinHalfOpenPeriod_AC_004_12` — by `commented_at`, NULL
  `commented_at` out even when `created_at` is in the period, NULL answer counted as posted not solved; 4/2/0.5;
  empty period → `(0, 0, null)`.
- `scopesSuggestionsByProjectPublicIdAndDepartmentAndReturnsNullWhenEmpty_AC_004_12` — Workspace 5/3/0.6,
  Department and PM 4/2/0.5, Project 3/1/⅓, disjoint and empty Project → `(0, 0, null)`.
- `neverLogsQueryQuestionOrTicketTitle_AC_004_15` — captured stdout/stderr contains none of the distinctive query,
  question and title strings that the response does contain.

`DashboardServiceImplTest.returnsZeroFiguresAndNullRates_AC_004_1` now stubs the three new repository calls with the
exact period instants; `DashboardControllerTest` only swaps a fully qualified name for an import.

## Defects

None logged. No bug found against an existing criterion.
