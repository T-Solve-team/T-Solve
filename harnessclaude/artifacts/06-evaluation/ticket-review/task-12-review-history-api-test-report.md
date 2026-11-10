---
module: ticket-review
feature: review-history-api
task: 12
attempt: 1
date: 2026-11-07
reviewer: harnessclaude-reviewer
iteration: 1
---

# Test Report: ticket-review — Task 12: review-history-api (STORY-011)

## Environment

- Monorepo `D:\T-Solve`, checkout on `feat/KAN-24-review-project-membership` (HEAD contains `31558050..e41f5330`). The branch was not switched.
- Maven 3.9.14, `-Dapi.version=1.44`, Docker with Testcontainers PostgreSQL. `ReviewHistoryApiIT` uses `TwoTenantDatabases` (two tenant databases, routing `TenantScope`).

## Targeted run (reviewer)

This was the same invocation as Task 11 (in `backend/`):

```
mvn -q -Dapi.version=1.44 -Dtest=ReviewNotificationJobHandlerTest,ReviewNotificationSenderTest \
    -Dit.test=ReviewNotificationIT,ReviewHistoryApiIT -Dsurefire.failIfNoSpecifiedTests=false \
    -Dfailsafe.failIfNoSpecifiedTests=false -Dspotless.check.skip=true verify
```

Exit code 0. Log: `C:\Users\anime\AppData\Local\Temp\claude\584c2bddc50ec8f0\6ef146ab-e139-4e4d-9e44-5bcce5e47a13\scratchpad\review-t11-t12.log`.

| Test class | Run | Failures | Errors | Skipped |
|------------|-----|----------|--------|---------|
| `vn.tsolve.review.ReviewHistoryApiIT` | 18 | 0 | 0 | 0 |

The 18 tests are:
- default order and content
- ascending sort with paging
- filters by Department, decision, Vietnam date range, UNPUBLISH and an unmanaged Department
- 5 MSG39 date cases
- 6 MSG06 cases: sort title, sort direction, decision DELETE, page −1, size 0, size 101
- anonymous 401
- non-manager 403
- unverified or other-Workspace 403
- a new HTTP decision showing in history

## Full suite (dispatcher evidence on HEAD)

`mvn -Dapi.version=1.44 verify`: 807 unit + 165 integration tests, 0 failures, 0 errors, Spotless check OK. Log: `C:\Users\anime\AppData\Local\Temp\claude\584c2bddc50ec8f0\6ef146ab-e139-4e4d-9e44-5bcce5e47a13\scratchpad\verify2.log`.

Frontend checks were not re-run. The range changes only the OpenAPI description/summary text and the matching generated JSDoc in `frontend/src/shared/api/generated/review/review.ts`.

## Traceability

`bun harnessclaude/hooks/sdd.ts trace ticket-review STORY-011`: 6/6 acceptance criteria have a test.

## Coverage notes

- No coverage tool is configured for this run, and I did not measure line coverage.
- Not covered:
  - The equal-timestamp UUID tie-breaker.
  - Uppercase sort direction.
  - Non-numeric page/size and a non-UUID `departmentId` (expected 400 MSG06 via `GlobalExceptionHandler`).
  - The lower date boundary (00:00 +07 on `dateFrom`). Only the upper boundary is asserted.
