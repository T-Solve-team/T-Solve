---
module: import-ticket-list
feature: csv-import-contract
task: 1
verdict: pass
attempt: 1
date: 2026-10-22
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: import-ticket-list — Task 1: OpenAPI contract for CSV import

## Summary
The contract adds all six `/api/v1/imports/**` operations and the schemas named in the spec's Interface Contracts, with ProblemDetail errors for 400/401/403/404/409/413/500 and the status codes the spec assigns (MSG28 → 413, MSG30/MSG40/MSG33-taken → 409, MSG33-save → 500). The regenerated client compiles and lints. Controller operationIds, paths, parameters and DTO fields match the contract.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Every path/response in the spec's Interface Contracts table is present (`frontend/openapi/tsolve-api.yaml`); `size` ≤ 100 and `status` filter declared; `RemoveRowsRequest.rowNumbers` minItems 1. |
| Completeness | pass | Template, upload (multipart `file` + `projectId`), rows page, row detail, remove-rows, confirm; `ImportPreviewResponse`, `ImportPreviewRowSummary`, `ImportPreviewRowResponse`, `RemoveRowsRequest`, `ImportResultResponse`. Event is in-process (not an HTTP contract). |
| Correctness | pass | `npm run typecheck`, `npm run lint` pass; generated client committed (18 files). |
| Integration | pass | operationIds equal the `@Operation` ids in `ImportController.java`; DTO field names equal the schema properties. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list` → `trace import-ticket-list: 0/41 acceptance criteria have a test` (all ✗). Known harness gap: it scans only `*.test.*/*.spec.*/*_test.*` files and the `AC-001.1` spelling; JUnit methods here cite `_AC_001_1` (backend/AGENTS.md §2). Manual trace (grep of `backend/src/test` for `_AC_XXX_Y`, then reading each test): 41/41 ACs have at least one test that checks the criterion. This task's rows:

No AC is implemented by the contract alone; the STORY-001..005 criteria are traced in tasks 3–9.

## Issues
No issues found.

## Assumptions made
- The `sdd.ts trace` result (0/41) is a tool limitation, not missing tests; the manual `_AC_XXX_Y` trace was used instead (dispatcher instruction).
- Coverage was not measured (no JaCoCo in `backend/pom.xml`) and the spec sets no threshold — open question for the user whether a coverage figure is required.

## Test Report
Shared full run (all 13 tasks), see `task-1-csv-import-contract-test-report.md`: `mvn verify` 91/91 passed (87 unit + 4 `ImportConfirmIT` on PostgreSQL 16), 0 failed/skipped; `spotless:check` pass; frontend `typecheck` and `lint` pass. Coverage not measured (no coverage plugin). Security: `security-review.md`.
