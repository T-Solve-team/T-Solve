---
module: ticket-review
feature: review-history-api
task: 12
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: ticket-review — Task 12: Review History in the production Workspace (STORY-011, KAN-159)

## Summary

Code range `31558050..e41f5330` (squashed PR #150). I reviewed it against `spec.md` STORY-011 (AC-011.1–011.6) and the KAN-159 interface contract under "Required from other modules". I used the design section "KAN-159 extension: production Review History" only to check edge cases.

I checked the three focus points directly.
- **Scope to managed Projects.** `ReviewReadController.history` requires `ROLE_PROJECT_MANAGER` and resolves Projects through `ReviewWorkspaceAccess.readableProjects` on the signed-in Account. Without a verified Account it denies with 403. `TicketReviewReadServiceImpl.history` (`:83-103`) keeps only `managed()` entries, narrowed by Department, before the query. An empty scope returns an empty page without querying. The native query binds `t.project_id in (:projectIds)` with parameters only, no string interpolation. The IT seeds an unmanaged-Project decision and a second-tenant decision, and neither leaks.
- **Filters.**
  - Department narrows the managed set.
  - `decision` maps APPROVE/REJECT/REQUEST_CHANGES to stored APPROVED/REJECTED/CHANGES_REQUESTED.
  - UNPUBLISH/REPUBLISH/SEND_BACK return an empty page, as the spec's Out of Scope note requires. Any other decision value is refused with MSG06.
  - Paired dates are parsed strictly (`uuuu-MM-dd`, `ResolverStyle.STRICT`, plus a `\d{4}-\d{2}-\d{2}` pre-check) into `[from 00:00, to+1 00:00)` in Asia/Ho_Chi_Minh. The IT checks the upper boundary with a 23:30 +07 and a 00:10 +07 decision.
- **MSG codes.** A missing pair, a non-calendar date, a `+2026` date or from > to gives 400 MSG39. A bad sort or decision, page < 0, or size outside 1–100 gives 400 MSG06. Unauthenticated callers get 401. Callers without the role or without a verified Workspace Account get 403.

The response shape matches `ReviewHistoryItem` / `ReviewHistoryPage` in `frontend/openapi/tsolve-api.yaml`. Paging is done in the database with a count query, and order is `decided_at` with `review_decision_id` as the tie-breaker in both directions. All 6 criteria are named in tests, and all tests pass. There are no CRITICAL findings, only minor test and convention gaps.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-011.1: managed scope, signed-in Workspace, newest first with UUID tie-breaker (`ReviewDecisionRecordRepository.java:37,48`), page/size limits 0+/1–100 (matching the queue). AC-011.2: decision mapping (`TicketReviewReadServiceImpl.historyItem`); reason only for REJECT, comment only for REQUEST_CHANGES; `coalesce(u.full_name, u.email)` as actorName; `t.writer` as authorName; source key, title, Project from the access map. AC-011.3: Department/decision/date filters, MSG39 cases, empty page for an unmanaged Department. AC-011.4: MSG06 for sort/page/size. AC-011.5: 401/403 with no decision exposed. AC-011.6: the frontend `useReviewHistory` (`frontend/src/features/review/hooks/useReviewHistory.ts:5`) calls the generated `useGetReviewHistory`. The YAML change is description-only, and the generated client was regenerated to match. |
| Completeness | pass | 6/6 criteria traced to named tests. There is no EXTRA behaviour. |
| Correctness | pass | `ReviewHistoryApiIT` 18/18 pass in my run (two-tenant PostgreSQL). The dispatcher's full `mvn verify` on HEAD reported 807 unit + 165 IT, 0 failures, Spotless OK. No CRITICAL findings. |
| Integration | pass | Uses the existing `ReviewWorkspaceAccess` port, so it works with the later `ProjectMembershipReviewAccess` adapter whose map keys both public and legacy Project ids. The existing Problem error contract (`MessageCodeException`) is reused. The OpenAPI operation `getReviewHistory` and the schemas are unchanged in shape. |

## Traceability

```
  ✓ AC-011.1  backend/src/test/java/vn/tsolve/review/ReviewHistoryApiIT.java
  ✓ AC-011.2  backend/src/test/java/vn/tsolve/review/ReviewHistoryApiIT.java
  ✓ AC-011.3  backend/src/test/java/vn/tsolve/review/ReviewHistoryApiIT.java
  ✓ AC-011.4  backend/src/test/java/vn/tsolve/review/ReviewHistoryApiIT.java
  ✓ AC-011.5  backend/src/test/java/vn/tsolve/review/ReviewHistoryApiIT.java
  ✓ AC-011.6  backend/src/test/java/vn/tsolve/review/ReviewHistoryApiIT.java
trace ticket-review (STORY-011): 6/6 acceptance criteria have a test
```

## Issues

No CRITICAL findings. The findings below do not block the verdict.

1. **Minor (test gap, AC-011.1):** the "decision UUID as tie-breaker" clause is implemented (`ReviewDecisionRecordRepository.java:37,48`), but no test seeds two decisions with the same `decided_at`. A real batch decision always produces equal timestamps (one `clock.instant()` per batch in `TicketDecisionServiceImpl`), so this is the common case in production. Fix: in `ReviewHistoryApiIT`, seed two decisions with an identical timestamp and assert UUID order in both directions and across a page boundary.
2. **Minor (convention, AC-011.4):** `TicketReviewReadServiceImpl.java:218-232` accepts only lowercase `timestamp,desc` / `timestamp,asc`. `timestamp,DESC` is refused with MSG06. The Review Queue (`sortBy`, `:276-281`) and Spring's sort convention compare direction case-insensitively. Fix: compare the direction with `equalsIgnoreCase`.
3. **Minor (test location, AC-011.6):** the criterion is about the frontend, but the trace points only at a backend IT (`ReviewHistoryApiIT.java:259`), which proves that a new HTTP decision appears in history. I verified the frontend usage by inspection. Fix: add the `AC_011_6` tag to the existing frontend hook/page test that exercises `useGetReviewHistory`.
4. **Informational (performance):** `review_decision` has only `idx_review_ticket (ticket_id)` (`008-review-ticket-storage.sql:54`). The history query and its count join `ticket` and sort by `decided_at` without an index. This is fine at demo volume. Consider an index on `(decided_at, review_decision_id)` when history grows.

## Assumptions made

- "Existing page and size conventions" means page ≥ 0 and size 1–100 with default 20, as on the Review Queue and in the OpenAPI `Size` parameter.
- Non-numeric `page`/`size` or a non-UUID `departmentId` are handled by Spring's type conversion and get 400 MSG06 from `GlobalExceptionHandler.createResponseEntity`. I verified this by code inspection, not by a test.
- AC-011.6 is satisfied by the frontend that already exists (Task 4) and the generated client, not by new frontend code in this range.
- Decisions on Projects the caller managed in the past but no longer manages are hidden, because scope comes from current membership. The spec ("Projects they manage") supports this. I did not raise it as a judgment call.

## Test Report

See `task-12-review-history-api-test-report.md`. Targeted run on HEAD: `ReviewHistoryApiIT` 18 tests, 0 failures, 0 errors. Full HEAD `mvn -Dapi.version=1.44 verify` (dispatcher evidence): 807 unit + 165 IT, 0 failures, Spotless OK.
