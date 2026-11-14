---
module: ticket-repository
feature: manual-ticket-contract
task: 14
verdict: pass
attempt: 1
date: 2026-11-14
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 14: Manual entry contract

## Summary
The OpenAPI contract for the three manual-entry endpoints, the `ManualTicketRequest` / `ManualTicketDraft` schemas,
`ImportSource` + `MANUAL`, and the matching backend DTO records (commit f12974cd, final state at 2bd4f203) match the
spec's Interface Contracts and the controller added in Tasks 15–16. No CRITICAL findings. **PASS.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `POST /api/v1/imports/manual` → 201 `ImportResultResponse`; `POST /api/v1/imports/manual/drafts` → 200 `DraftSavedResponse`; `GET /api/v1/imports/manual/drafts/{previewId}` → `ManualTicketDraft` (frontend/openapi/tsolve-api.yaml:1216-1304). Field shapes as in spec Interface Contracts lines 180-184. |
| Completeness | pass | All three operations, both schemas, `ImportSource` gains `MANUAL` (yaml:2747). DTO records `backend/.../ticket/dto/ManualTicketRequest.java`, `ManualTicketDraft.java` carry exactly the schema's properties. |
| Correctness | pass | operationIds `createManualTicket`, `saveManualTicketDraft`, `getManualTicketDraft` identical in yaml and `ManualTicketController.java:38,48,57-59`. Error statuses: create 400/403/404/409, drafts 400/403/404, get 403/404 — same in yaml and the controller's `@ApiResponse`s. Backend scoped run green (test report). |
| Integration | pass | `DraftsList.tsx:17-21` labels MANUAL "Manual" (needed so `Record<ImportSource,…>` type-checks). Generated client committed. |

## Traceability
Task 14 is the contract; it owns no AC by itself. `sdd.ts trace ticket-repository STORY-006`: 13/14 — AC-006.1…13
traced (Tasks 15–16), AC-006.14 missing as expected (Task 17).

## Issues

CRITICAL: none.

Informational:
1. `frontend/openapi/tsolve-api.yaml:2780` — `ManualTicketRequest.required: [projectId]`, but `POST /imports/manual`
   accepts a missing `projectId` and answers 400 MSG01 on it (AC-006.3). Consistent behaviour, but the generated
   TypeScript type makes `projectId` mandatory, so the Task 17 form must send it (or a cast) to receive the MSG01.
   Fix: none needed if Task 17 always sends the selected Project; otherwise drop it from `required`.
2. `ManualTicketDraft` / `ManualTicketRequest` do not follow AGENTS.md §2 `<Action><Entity>Request` /
   `<Entity>Response` naming — the names come from the spec's Interface Contracts, so kept.
3. The 400 on `saveManualTicketDraft` and 401 on every operation were added beyond the Task 14 description; both are
   real answers of the endpoint (session log), so not EXTRA behaviour.
4. Not re-verified here: `npm run generate:api` no-diff and the frontend checks (frontend busy with another agent);
   the session log reports them green twice.

## Assumptions made
- Contract-only task judged against Interface Contracts plus the final controller; no separate AC.

## Test Report
See `task-14-manual-ticket-contract-test-report.md`: scoped backend `mvn verify` — 297 unit + 29 IT, 0 failures,
Spotless clean.
