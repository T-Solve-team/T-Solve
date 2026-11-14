---
module: dashboard-audit
feature: dashboard-reuse-gaps
task: 6
verdict: pass
attempt: 1
date: 2026-11-09
reviewer: harnessclaude-reviewer (independent subagent)
iteration: 1
---

# Review: dashboard-audit — Task 6: dashboard-reuse-gaps

## Summary

Range `05cb9f8..9f778b9`. `DashboardRepository` adds three read queries and `DashboardServiceImpl` replaces the
Task 4 placeholders with them. Feedback reads `ticket_feedback` by `updated_at` in `[start, end)`. It goes through
the same legacy-aware ticket → Project → Department join as Task 5, and computes per-ticket and total rates in SQL
with `numeric`. Two `row_number()` windows pick the top and lowest 5. Gaps group `search_log` (`result_count = 0`)
and `chatbot_answer` (`NO_SOURCE`) by `lower(trim(...))`, using `distinct on` with a per-group window count. The
earliest trimmed spelling is shown. Scope is `search_log.department_id` and the asker's current
`app_user.department_id`; `projectId` is never applied. `chatbotNoSource` is a window sum computed before `LIMIT
10`. Suggestions count `suggestion_result` by `commented_at` (NULL is out by the range test), joined on
`project.public_id`, with solved share `nullif`-guarded. Only constant SQL fragments are concatenated; every value is
a named parameter. The commit also applies the three Task 4 fixes: rated-ticket `helpfulRate` is primitive `double`
(matches the frontend contract `dashboardRatedTicket.helpfulRate: number`), `@Operation` has a `summary`, and the
controller test imports `AccessDeniedException`. No blocking finding.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-004.10, AC-004.11, AC-004.12 behave as written (per-AC table). D5 (case / spaces) gives one gap row, tested for both lists. D3 (empty period) gives zeros, `0` and null rates, tested. |
| Completeness | pass | Every AC of the task has a test naming its id; trace 15/15 for STORY-004. |
| Correctness | pass | Controller reports `Dashboard*Test` 43/43, `DashboardIT` 6/6, `DashboardOutcomesIT` 6/6, `DashboardReuseGapsIT` 10/10. The reviewer did not re-run `mvn` (dispatcher constraint). Palantir dry-run (with a negative control) and the unused-import check pass on the 7 committed files. Worktree sources equal `9f778b9`. Every expected figure of the IT was hand-checked against its seed. |
| Integration | pass | Reads `ticket_feedback` (search), `search_log` (search), `chatbot_answer` (chatbot), `suggestion_result` (tracker) through read-only SQL; no write, no entity of another module mapped. `ticketId` = `ticket.public_id`, the same id `search` returns. The ticket detail route accepts it (`TicketRepository`: `t.id = :id or t.publicId = :id`), so the AC-005.9 links will resolve. Response records match the `KnowledgeDashboard` contract. |

### Per acceptance criterion

| AC | Verdict | Evidence |
|----|---------|----------|
| AC-004.10 | pass | `f.updated_at >= :start and < :end`, filtered by ticket scope (`p.department_id`, `p.public_id`), so the giver's Department does not matter (tested). One row per (ticket, user) by the unique key, so `count(*)` = ratings. Totals and rate from window sums over all rated tickets before the top/lowest filter. Rate null only when there are no rows. `topRated` is `rate desc, total desc, title, public_id`. `lowestRated` is `rate asc, total desc, title, public_id`: "more feedback first, then title" for both, with a deterministic last key. At most 5 each. Rates are numbers 0..1. Legacy-hex tickets are included (tested). |
| AC-004.11 | pass | Searches: `result_count = 0`, no channel filter (WEB, ISSUE_PANEL, CHATBOT tested), `lower(trim(query))` key, spelling = `trim(query)` of the earliest row (`created_at, search_log_id`). Department filter is `search_log.department_id`, so the snapshot is used, not the user's current Department (tested); Workspace includes NULL. Chatbot: `outcome = 'NO_SOURCE'`, `app_user.department_id` of the asker; Workspace includes askers with no Department. `sum(total) over ()` is evaluated before `LIMIT`, so `chatbotNoSource` counts every group (13 vs 11 shown, tested). Both lists are `total desc, grouping_key limit 10`. `findGaps` takes no `projectId`, and every Project combination was checked equal to the unfiltered result. |
| AC-004.12 | pass | `s.commented_at >= :start and < :end` (NULL out; `created_at` ignored, tested). Joined on `p.public_id = s.project_id` (the FK target since `019`), scoped by `p.department_id` / `p.public_id`. Solved = `reporter_answer = 'YES'`; a NULL answer is posted but not solved. Share `::numeric / nullif(count(*), 0)` → null at 0. |
| AC-004.15 (part) | pass | No logger in `vn.tsolve.analytics`. The IT asserts that captured output holds none of the query, question and title text returned in the response. Values travel as bind parameters, so a failing statement's SQL text holds no user text either. |

### Code-review checklist

- **SQL injection:** `TICKET_SCOPE_JOINS`, `scopeFilter`, the two inline department fragments and the text blocks
  are constants; ids, the department set and instants go through `MapSqlParameterSource`. `departments` is `null`
  or a singleton (`DashboardScope.resolve`), so `in (:departments)` never expands to `in ()`.
- **Fan-out:** a ticket matches one Project (random v4 `public_id` cannot equal a version-0 legacy hex id) and one
  Department; feedback is one row per (ticket, user). `chatbot_answer.user_id` is `NOT NULL` with an FK, so the inner
  join to `app_user` drops nothing.
- **`distinct on` + window:** `count(*) over (partition by key)` is computed before `distinct on` keeps the first
  row per key, so the count is the full group size and the spelling is the earliest row's.
- **Numeric types:** `count / count` cast to `numeric` (no integer division); `sum(bigint)` is `numeric`, read with
  `getLong` / `getDouble`; suggestion share read as `BigDecimal` so NULL stays null.
- **Read only:** class-level `@Transactional(readOnly = true)` on the service unchanged.
- **AGENTS.md:** constructor injection, records for row types, AC-id test names (`…_AC_004_10`), Testcontainers
  `pgvector/pgvector:pg16` with `@ServiceConnection`, no wildcard or unused imports, Palantir format clean. The
  `NamedParameterJdbcTemplate` and `@DataJpaTest` deviations were already accepted in the Task 4 and Task 5 reviews
  (design: "cross-module read model, aggregates only").

## Traceability

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

## Issues

### Blocking

None.

### Non-blocking

1. Worktree, not the commit: `backend/src/main/java/vn/tsolve/analytics/controller/DashboardTmpCheck.java`
   (untracked, 15:38, content `class DashboardTmpCheck {   int x;}`) and `backend/pom-spotless-tmp.xml`
   (untracked) are outside `9f778b9`. They look like a formatter negative control. The `.java` file is in
   `src/main`, so any `mvn verify` run while it exists compiles it, and `spotless:check` fails on it. Suggested:
   delete both before the next `mvn verify` or commit, and never `git add` them.
2. `backend/src/main/java/vn/tsolve/analytics/repository/DashboardRepository.java:184-185` — when fewer than 10
   tickets have feedback in the period, a ticket can be in both `topRated` and `lowestRated` (the IT expects
   `zulu, alpha, beta` in both). The spec does not say whether the lists may overlap; the frontend (Task 8) should
   not assume they are disjoint. Recorded as an assumption; change only if the user wants disjoint lists (spec
   first).
3. `DashboardRepository.java:178`, `:251` — `ticket_feedback.updated_at` and `chatbot_answer.created_at` have no
   index (only `search_log.created_at` has one), so both queries scan their table on every dashboard load. Fine at
   capstone volume; if they grow, add `(updated_at)` / `(created_at)` indexes in the owning modules' changelogs.
4. `backend/src/main/java/vn/tsolve/analytics/controller/DashboardController.java:27` — the new `summary`
   ("Knowledge Dashboard figures for a period and scope") differs from the hand-written contract
   `frontend/openapi/tsolve-api.yaml:40` ("… (PM: own Department; Admin: Workspace).") — cosmetic. Align the
   text if springdoc output is ever compared with the contract.

## Assumptions made

- **Tests not re-run by the reviewer.** The dispatcher forbade `mvn` (the controller runs the full suite). The pass
  relies on the controller's report (43/43, 6/6, 6/6, 10/10). The reviewer ran Palantir 2.39.0 directly (dry-run
  clean, negative control listed) and an unused-import check (clean), and confirmed the worktree sources equal the
  reviewed commit.
- **"Feedback given in the period" = `ticket_feedback.updated_at` in the period** (the design's choice). Only the
  latest rating per user is stored, so a user who re-rates after the period moves that feedback out of the earlier
  period. The schema cannot do better without a history table.
- **Top and lowest lists may overlap** (non-blocking 2). This is an open judgment call for the user.
- **Search spelling:** SQL `trim()` strips spaces only. The search module already `String.trim()`s and rejects
  blank queries before logging, and the chatbot trims questions, so tabs and newlines at the edges do not reach the
  table in practice.
- **Admin with only `projectId`:** gaps use the whole Workspace (departments `null`). This is taken literally from
  "`projectId` does not apply" and tested.
- Orphan tickets (a `project_id` with no Project) are excluded from feedback, as from outcomes and coverage (Task 5
  non-blocking 1, still open with the user).

## Test Report

See `task-6-dashboard-reuse-gaps-test-report.md`. Palantir dry-run PASS, unused imports PASS, `git diff --check`
PASS, `sdd.ts check` PASS, trace 15/15. The reviewer did not re-run `mvn verify` (dispatcher constraint); the
controller reports all four dashboard test classes passing.
