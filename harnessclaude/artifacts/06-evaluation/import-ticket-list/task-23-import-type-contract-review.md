---
module: import-ticket-list
feature: import-type-contract
task: 23
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: import-ticket-list — Task 23: Contract for the import type

## Summary
Commit `0526620` (repo `default`, range `0526620^..0526620`), read at HEAD `6267856`.

`frontend/openapi/tsolve-api.yaml` now has the following:
- **Enum and parameters:** an `ImportType` enum (`WITH_SOLUTION`, `WITHOUT_SOLUTION`; "names none is WITH_SOLUTION"). It is an optional `importType` on `downloadCsvTemplate` (query) and on the `uploadCsvImport` multipart body.
- **Row status:** `NEEDS_SOLUTION` in `ImportPreviewRowStatus`, which is also the schema of the rows `status` filter.
- **Preview response:** `importType` and `needsSolutionCount` as required fields of `ImportPreviewResponse`.
- **New endpoint:** `POST /api/v1/imports/previews/{previewId}/fill-solution` (`fillImportPreviewSolution`). Its `FillSolutionRequest` has `rowNumbers`: `minItems: 1`, items `minimum: 1`; four required sections; `workaround` nullable. Responses are 200 `ImportPreviewResponse` / 400 / 401 / 403 / 404 / 409.
- **Confirm:** a `MSG_NEW_4` (409) sentence on `confirmImport`.

The generated client was regenerated, and `generate:api` leaves no diff. The feature mock seeds `importType: 'WITH_SOLUTION'` and counts `needsSolutionCount`. `CsvImportForm` moves to the new `downloadCsvTemplate(params, options)` signature, and `StatusTag` gets `NEEDS_SOLUTION`.

The contract matches the spec's Interface Contracts and the backend built against it (tasks 24–26). The frontend and backend runs are both green. I found no CRITICAL issue. **Verdict pass.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Every contract item that STORY-012/013/014 name is present; see the Stage 1 table. |
| Completeness | pass | Nothing in the task description is missing. The fill-solution mock handler of the feature (`mocks/handlers.ts`) came later (it is there at HEAD, `:118`). The task asked only that the mocks return the new fields, and they do. |
| Correctness | pass | Frontend: `format:check`, `lint` (0 errors), `typecheck`, vitest 1442/1442 and `build` pass; `generate:api` leaves no diff. Backend `mvn clean verify` exit 0. |
| Integration | pass | The backend matches the contract field by field. `ImportController` has the `importType` defaults, `fillImportPreviewSolution` with the same operationId and status codes, and `FillSolutionRequest` with `@NotEmpty` / `@Min(1)` / `@NotBlank`. `ImportPreviewResponse` has `importType` and `needsSolutionCount`, and `PreviewRowStatus.NEEDS_SOLUTION` is the filter. |

### Stage 1 — spec compliance (the contract part of each AC)

| # | AC | Status | Evidence in `tsolve-api.yaml` |
|---|----|--------|------|
| 1 | AC-012.1 type optional, default with Solutions | PASS | `ImportType` description; `importType` not in `required` on the upload body or the template query |
| 2 | AC-012.2 template by type | PASS | `downloadCsvTemplate` `importType` query parameter |
| 3 | AC-012.7 type + Needs Solution count on every response, filter value | PASS | `ImportPreviewResponse.required` has both; `ImportPreviewRowStatus` has `NEEDS_SOLUTION`; the rows `status` parameter refers to it |
| 4 | AC-013.1 – 013.5 fill-solution request and rejections | PASS | path, `FillSolutionRequest`, 400 / 403 / 404 / 409, the summary text naming MSG01 / MSG02 / MSG06 / 404 |
| 5 | AC-014.1 `MSG_NEW_4` 409 on Confirm | PASS | `confirmImport` summary and its 409 response |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-012 STORY-013 STORY-014` → `16/16 acceptance criteria have a test`. The criteria are proven by the backend tests of tasks 24–26:

```
✓ AC-012.1  ImportControllerTest
✓ AC-012.2  CsvImportServiceImplTest, ImportControllerTest
✓ AC-012.3  CsvTicketParserTest
✓ AC-012.4  CsvTicketParserTest
✓ AC-012.5  CsvImportServiceImplTest, CsvRowValidatorTest, ImportConfirmIT
✓ AC-012.6  CsvRowValidatorTest
✓ AC-012.7  CsvImportServiceImplTest, ImportPreviewServiceImplTest
✓ AC-013.1  ImportPreviewServiceImplTest, ImportControllerTest
✓ AC-013.2  ImportPreviewServiceImplTest, ImportControllerTest
✓ AC-013.3  ImportPreviewServiceImplTest, ImportControllerTest
✓ AC-013.4  ImportPreviewServiceImplTest, ImportConfirmIT
✓ AC-013.5  ImportPreviewServiceImplTest, ImportControllerTest
✓ AC-013.6  ImportPreviewServiceImplTest
✓ AC-013.7  ImportConfirmIT
✓ AC-014.1  ImportConfirmServiceImplTest, ImportConfirmIT
✓ AC-014.2  ImportConfirmServiceImplTest, ImportConfirmIT
```

## Issues
CRITICAL (blocks pass): none.

Informational (does not block):
1. [Info] `frontend/src/shared/ui/StatusTag/status-tags.ts:26`: the new `NEEDS_SOLUTION` tag (colour `orange`, marked as a placeholder) is a change to `src/shared/`. Under `frontend/AGENTS.md` rule 10 it needs the FE Lead's review in the pull request. The spec's Definition of Done names only `CsvUpload` and `DetailModal` for that review. Fix: add `StatusTag` (and `messages/catalog.ts` for `MSG_NEW_4`, added later) to the FE Lead review request.
2. [Info] `frontend/openapi/tsolve-api.yaml` (`ImportPreviewResponse.message`): the description still says only "MSG30 … when at least one row is an Error". That stays true for both types, because `MSG_NEW_4` is a Confirm rejection and not a preview message. No change needed; I note it only because the screen shows `MSG_NEW_4` from the counts (AC-015.6), not from this field.

## Assumptions made
- A contract-only task has no behaviour of its own to test. I judged it by the shape of the contract against the spec's Interface Contracts, by the `generate:api` no-diff check, by the frontend suite, and by the backend tests that implement it.
- I ran the checks at HEAD (tasks 23–29), not at `0526620` alone. What matters is that the contract and the client are in step, and HEAD shows that.

## Test Report
`task-23-import-type-contract-test-report.md` results:
- Frontend: `format:check` 0, `lint` 0 (0 errors, 1 unrelated warning), `typecheck` 0, vitest 1442/1442 0, `build` 0. `generate:api` exit 0 with an empty diff in `src/shared/api/generated`.
- Backend: `mvn -B -Dapi.version=1.44 clean verify` exit 0 (635 + 43 tests, spotless clean).
