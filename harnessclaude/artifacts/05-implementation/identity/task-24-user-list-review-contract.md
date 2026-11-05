# Task 24: User List tenant read contract review

**Module:** identity
**Assignee:** Phạm Tùng Dương
**Status:** passing
**Iteration:** 1

## Session 2026-11-05

### Working on user-list-review-contract

- The backend reviewer withdrew the UUID request. The user approved limiting the follow-up to HTTP 503, OpenAPI and BUG-115's PR reference. The prior Department query, fixture and production changelogs stay in place.
- An unavailable tenant transaction or missing or mismatched guard returns 503 MSG06. Unexpected query errors after the guard passes still return 500 MSG06.
- OpenAPI describes enabled read handlers, removes unused response entries and retains the current request and success shapes.

### Verification

- Before the fix, the new guard assertion expected 503 and received 500.
- The final backend run passed 489 unit and 46 integration tests. Frontend passed 1,313 tests, and regeneration from the final OpenAPI descriptions matched the tested client across all 137 files. STORY-004 trace is 19/19.

### Commit

- `default@0a764cb` contains the verified scope for PR #119.
