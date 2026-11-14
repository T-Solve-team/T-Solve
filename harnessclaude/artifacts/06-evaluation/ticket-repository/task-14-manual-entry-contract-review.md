---
module: ticket-repository
feature: manual-entry-contract
task: 14
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: ticket-repository — Task 14: OpenAPI contract for manual entry and its Drafts

## Summary
`frontend/openapi/tsolve-api.yaml` adds the three manual-entry endpoints, the `ManualEntryForm` multipart body,
`ManualEntryRequest` (with `resolvedDate` as text and `keepEvidenceIds` nullable), `ManualEntryResponse`,
`ManualDraftResponse`, `DraftEvidenceResponse`, and `MANUAL` in `ImportSource`. These match the spec's Interface
Contracts "Manual entry" table. The generated client is up to date: `generate:api` leaves no diff. It sends `entry`
as `JSON.stringify(...)` text (`src/shared/api/generated/import/import.ts:1454`). The backend reads that part as a
`String` form field (`ManualEntryController.java:49`), so it needs no part content type. Verdict: pass.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | The paths, operation ids, request and response shapes match the spec table and the controller: 201 for Save, 200 for the Draft, and `ManualDraftResponse` with `evidence[]` of `{id, fileName, fileType, fileSize}` and no storage key |
| Completeness | pass | Errors 400/403/404/409/500 are listed as in the spec. 413 is listed too: the servlet multipart limit answers 413 MSG28 (see Task 17, Issue 1). `ImportDraftSummary.source` accepts `MANUAL` |
| Correctness | pass | `generate:api` gives no diff; typecheck, lint and build pass (shared run in the test report) |
| Integration | pass | `ManualEntryControllerTest` sends the same multipart shape; the MSW handlers and `ManualEntryForm.test.tsx` parse the generated requests |

## Traceability
`sdd.ts trace ticket-repository STORY-006`: 12/12 acceptance criteria have a test. `STORY-007`: 9/9 have a test.
Task 14 owns no criterion by itself; the contract serves AC-006.1–AC-006.12 and AC-007.1–AC-007.9. Full output is in
the test report.

## Issues
No blocking issue.

1. **[LOW]** The backend has no test that compares springdoc's `/v3/api-docs` with `tsolve-api.yaml`. Drift between
   the hand-written contract and the controllers would show only at run time. The project has not done this for
   other modules either. Suggested fix: none for this task; consider a contract-diff test project-wide.

## Assumptions made
- Listing 413 in the contract is accepted as documentation of the servlet limit; whether that limit itself may answer
  instead of MSG34 is a judgment call raised in Task 17.

## Test Report
`harnessclaude/artifacts/06-evaluation/ticket-repository/task-14-manual-entry-contract-test-report.md`: the shared
run. Backend `mvn clean verify`: 1725 unit and 555 integration tests pass, spotless passes. Frontend format, lint,
typecheck and build pass, and `generate:api` gives no diff. The full `npm run test` had 1 load timeout (BUG-213),
which passed in two solo reruns.
