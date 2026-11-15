# Task 26: Unpublish / re-publish contract

**Module:** ticket-repository
**Assignee:** dang minh
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-11-15

### Working on: ticket-publication-contract
- `POST /api/v1/reviews/unpublish` (`unpublishTickets`) and `/republish` (`republishTickets`) in `frontend/openapi/tsolve-api.yaml`, request `ApproveTicketsRequest`, answer `ReviewDecisionResult`, responses 400 / 401 / 403 / 404 / 409 as `sendBackTickets`.
- `npm run generate:api`: `frontend/src/shared/api/generated/review/review.ts`, `review.msw.ts`; a second run leaves no diff.
- Commit: default@ce4dad2
- No deviation from the plan.

### Blockers
none

### Next up
Task 27
