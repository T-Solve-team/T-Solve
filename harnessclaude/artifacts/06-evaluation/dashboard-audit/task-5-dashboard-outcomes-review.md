---
module: dashboard-audit
feature: dashboard-outcomes
task: 5
verdict: pass
attempt: 1
date: 2026-11-09
reviewer: harnessclaude-reviewer (independent subagent)
iteration: 1
---

# Review: dashboard-audit — Task 5: dashboard-outcomes

## Summary

Range `7fe616d..2cc0e41`. `DashboardRepository` adds two scoped aggregate queries: outcomes (`review_decision`
by `decided_at` UNION ALL non-`SOLUTION_FORM` tickets by `created_at` as `APPROVED`, grouped by Vietnam-time day and
Project) and current ticket status counts per Department. `DashboardServiceImpl` builds per day (every day of the
period, zeros filled in Java), per Project, totals, approval rate (null at 0), current `PENDING_REVIEW` / `EXPIRED`,
and coverage (Published / all, share null at 0, per Department incl. deactivated). Period bounds are the Task 4
`[from 00:00 VN, to+1 00:00 VN)` instants on `TIMESTAMPTZ` columns, and the day bucket uses
`at time zone 'Asia/Ho_Chi_Minh'`, so the bucket and the bounds agree. Scope filters are `p.department_id in
(:departments)` and `p.public_id = :projectId`, applied after the legacy-aware Project join, so legacy-hex tickets
follow their Project. Only fixed string fragments are concatenated; every value is a named parameter. No blocking
finding.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-004.8 and AC-004.9 behave as written (per-AC table). Edge cases D3 (no data) and D6 (deactivated Department) handled and tested. |
| Completeness | pass | Both ACs of the task have tests naming their ids (trace 12/15; AC-004.10–004.12 belong to Task 6). |
| Correctness | pass | Controller reports `Dashboard*Test` 43/43, `DashboardIT` 6/6, `DashboardOutcomesIT` 6/6; reviewer could not re-run `mvn` (dispatcher constraint). Palantir dry-run and unused-import check pass on the committed files; worktree analytics sources equal `2cc0e41`. Expected figures of the IT hand-checked against its seed. |
| Integration | pass | Reads `ticket`, `review_decision`, `project`, `department` with the same legacy-aware join as `search` (`TicketRankingRepository`, `SolutionRetrievalRepository`, `SearchTicket`). Response records unchanged from the Task 4 contract. Review decisions are only made on `SOLUTION_FORM` tickets (`TicketDecisionServiceImpl:127`), so an import is never also counted as a decision. |

### Per acceptance criterion

| AC | Verdict | Evidence |
|----|---------|----------|
| AC-004.8 | pass | Decisions `decided_at >= :start and < :end`; imports `source <> 'SOLUTION_FORM'` and `created_at` in the same bounds, counted as approved; `union all` then `count(*) filter` per decision — no join fan-out (one Project row per ticket, see Assumptions). `perDay` from `period.days()` with `getOrDefault` zeros; `perProject` only Projects with data, ordered `lower(jira_project_key), project_id`; rate `approved / (approved+rejected+changesRequested)`, null at 0; `pendingReview` / `expired` from current status counts of the scope, not the period. IT: boundary instants in/out, all three import sources, legacy ticket, Department / Project / PM / disjoint scopes, empty period. |
| AC-004.9 | pass | Current status counts (no period filter) per Department, `department` joined without a status filter (D6); Published / all, share null at 0; totals summed from the per-Department rows. IT: Workspace, deactivated Department present in figures but not in options, period independence, Department, PM, Project with a legacy ticket, empty scopes. |

### Code-review checklist

- **SQL injection:** `TICKET_SCOPE_JOINS`, `scopeFilter` output and the literal SQL blocks are constants; ids, set and
  instants go through `MapSqlParameterSource`. `departments` is `null` or a singleton (`DashboardScope.resolve`), so
  `in (:departments)` never expands to `in ()`.
- **Double counting:** decisions and imports are disjoint rows of a `union all`; each ticket matches one Project
  (random v4 `public_id` cannot equal a version-0 legacy hex id).
- **Time zone:** `decided_at` and `created_at` are `TIMESTAMPTZ`; `at time zone 'Asia/Ho_Chi_Minh'` yields the VN
  wall-clock date; bounds from `DashboardPeriod.start()/end()` in the same zone.
- **Read only / logging:** class-level `@Transactional(readOnly = true)` unchanged; nothing logged.
- **AGENTS.md:** constructor injection, records for row types, AC-id test names (`…_AC_004_8`), Testcontainers
  `pgvector/pgvector:pg16` with `@ServiceConnection`; Palantir format clean.

## Traceability

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
✗ AC-004.10 .. AC-004.12  no test — later feature dashboard-reuse-gaps (Task 6)
✓ AC-004.13 DashboardIT
✓ AC-004.14 DashboardControllerTest
✓ AC-004.15 DashboardIT
trace dashboard-audit (STORY-004): 12/15 acceptance criteria have a test
```

## Issues

### Blocking

None.

### Non-blocking

1. `backend/src/main/java/vn/tsolve/analytics/repository/DashboardRepository.java:20` — `TICKET_SCOPE_JOINS` is an
   inner join, so a ticket whose `project_id` matches no Project row (e.g. a pre-`010` stub id such as
   `00000000-…-000000000001` with no Project behind it) is left out of every figure, including the Admin Workspace
   totals. The `search` module drops such tickets the same way, and they have no Department to show. Suggested:
   decide with the user whether Workspace totals must include them; if so, `left join` and filter Department /
   Project only when a scope is set (spec first if the rule changes).
2. `backend/src/main/java/vn/tsolve/analytics/repository/DashboardRepository.java:22` — the `or lpad(to_hex(...))`
   join condition cannot use an index or a hash join, so both queries do a ticket × project nested loop on every
   dashboard load, and the coverage query reads the whole `ticket` table. Fine at capstone volumes and identical to
   `search`; if it gets slow, join a `select project_id, public_id, lpad(...)::uuid as legacy_id from project` CTE
   with `t.project_id in (public_id, legacy_id)` or store the legacy id as a column.
3. `backend/src/test/java/vn/tsolve/analytics/DashboardOutcomesIT.java:38` — `@DataJpaTest` while AGENTS.md §8 says
   "No `@DataJpaTest` for now". The rule's reason (no in-memory database) does not apply with `Replace.NONE` and
   Testcontainers, and `DashboardIT`, `AuditLogIT`, `TicketDecisionIT` already do the same. Accepted; noted for the
   rule's owner.
4. `backend/src/test/java/vn/tsolve/analytics/DashboardOutcomesIT.java:209` — D6 is tested for coverage only; a
   review decision on a ticket of the deactivated Department is not asserted in outcomes. The same join makes it
   count, so this is a test gap, not a defect. Optional: add one decision on `inactiveProject` to `seedOutcomes`.

## Assumptions made

- **Tests not re-run by the reviewer.** The dispatcher forbade `mvn` (another process edits backend files; the
  controller runs the Docker tests). Pass relies on the controller's report (43/43, 6/6, 6/6). The reviewer ran
  Palantir 2.39.0 directly (dry-run, clean, negative control listed), an unused-import check (clean), and confirmed
  the worktree analytics sources equal the reviewed commit.
- **"Imported or created manually" = `source <> 'SOLUTION_FORM'`** (JIRA_IMPORT, CSV, MANUAL), whatever the
  ticket's current status, bucketed by `created_at`. Taken literally from AC-004.8.
- **`pendingReview` / `expired` count every source** with that current status in the scope; only Solution Form
  tickets reach `PENDING_REVIEW` today, so this equals the review queue.
- **Orphan tickets excluded** (non-blocking 1) — open judgment call for the user.
- Accepted per dispatcher: `perProject` / `perDepartment` list only rows with data; Admin `departmentId` +
  `projectId` of another Department → AND, zeros, no error (tested).

## Test Report

See `task-5-dashboard-outcomes-test-report.md`. Palantir dry-run PASS, unused imports PASS, `git diff --check` PASS,
`sdd.ts check` PASS, trace 12/15 (expected). `mvn verify` not re-run by the reviewer (dispatcher constraint);
controller reports the backend tests passed.
