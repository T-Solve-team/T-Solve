---
module: ticket-repository
feature: ticket-publication-contract
task: 26
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
---

# Review: ticket-repository — Task 26: ticket-publication-contract

## Summary
`default` 566fb3f..ce4dad2 adds `POST /api/v1/reviews/unpublish` (`unpublishTickets`) and
`POST /api/v1/reviews/republish` (`republishTickets`) to `frontend/openapi/tsolve-api.yaml`, tag `review`, with the
regenerated client. It matches the Interface Contracts of the spec (D36): request `ApproveTicketsRequest`
(`ticketIds` uuid, 1–500, `uniqueItems`), 200 `ReviewDecisionResult { count }`, responses 400, 401, 403, 404 and 409.
The descriptions state the status rule, MSG04 / MSG08 / MSG06, the error order and that a refused batch changes
nothing. `npm run generate:api` leaves no diff, so the generated files were not edited by hand. Format, lint,
typecheck and build pass. Verdict: pass.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Paths, operationIds, request and answer schemas, and responses as in the spec's Interface Contracts and D36; same shape as `sendBackTickets` (`tsolve-api.yaml:994-1027`) |
| Completeness | pass | Both endpoints; the descriptions carry AC-009.1 / AC-009.2 (decision, audit, unchanged fields) and AC-009.3–AC-009.7 (400 / 404 MSG04 / 403 MSG08 / 409 MSG06, all-or-nothing) |
| Correctness | pass | `generate:api` without diff; `format:check`, `lint` (0 errors), `typecheck`, `build` pass. Full `npm run test` had timeouts in other files only (test report) |
| Integration | pass | The Task 27 controller uses the same operationIds and the records `ApproveRequest` / `DecisionResult`, whose JSON shape is that of `ApproveTicketsRequest` / `ReviewDecisionResult`; Task 28 uses the generated `useUnpublishTickets` / `useRepublishTickets` |

## Traceability
This task has no criterion of its own (contract only). Output for the story:
```
  ✓ AC-009.1  backend/src/test/java/vn/tsolve/ticket/service/impl/TicketPublicationTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java
  ✓ AC-009.2  backend/src/test/java/vn/tsolve/ticket/service/impl/TicketPublicationTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java
  ✓ AC-009.3  backend/src/test/java/vn/tsolve/ticket/service/impl/TicketPublicationTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java
  ✓ AC-009.4  backend/src/test/java/vn/tsolve/ticket/service/impl/TicketPublicationTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java
  ✓ AC-009.5  backend/src/test/java/vn/tsolve/ticket/service/impl/TicketPublicationTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java
  ✓ AC-009.6  backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java
  ✓ AC-009.7  backend/src/test/java/vn/tsolve/ticket/service/impl/TicketPublicationTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java
  ✓ AC-009.8  frontend/src/features/tickets/components/TicketPublicationActions.test.tsx
  ✓ AC-009.9  frontend/src/features/tickets/components/TicketPublicationActions.test.tsx
  ✓ AC-009.10  frontend/src/features/tickets/components/TicketPublicationActions.test.tsx
trace ticket-repository (STORY-009): 10/10 acceptance criteria have a test
```

## Issues
No issues found. All three changed files (`frontend/openapi/tsolve-api.yaml`,
`frontend/src/shared/api/generated/review/review.ts`, `review.msw.ts`) are in the module's `paths`.

## Assumptions made
- The spec's request "`ApproveTicketsRequest { ticketIds (uuid, 1–500, unique) }`" is met by reusing the existing
  schema, which has `minItems: 1`, `maxItems: 500`, `uniqueItems: true` (`tsolve-api.yaml:3442-3453`).

## Test Report
See `task-26-ticket-publication-contract-test-report.md`. `npm run generate:api` → no diff in
`src/shared/api/generated`; `format:check` pass; `lint` 0 errors, 1 warning (pre-existing, another file);
`typecheck` exit 0; `build` exit 0. Full `npm run test`: 5 failed | 1475 passed | 323 skipped, all timeouts under load
in files outside this range, which passed when re-run.
