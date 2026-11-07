# Task 23: Contract for the import type

**Module:** import-ticket-list
**Assignee:** Claude (with ngaidang53)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-11-05

### Working on: import-type-contract
- Contract: `ImportType`; optional `importType` on the template query and the upload body; `NEEDS_SOLUTION` in `ImportPreviewRowStatus`; `importType` and `needsSolutionCount` required in `ImportPreviewResponse`; `FillSolutionRequest` and `POST /imports/previews/{previewId}/fill-solution`; 409 `MSG_NEW_4` named in the Confirm summary. Client regenerated.
- `CsvImportForm` calls `downloadCsvTemplate(undefined, …)` because the generated signature now takes params first. It sends no type yet (Task 27).
- `mocks/data.ts` returns the new fields, with `WITH_SOLUTION` for every preview.
- Commit: default@0526620
- Branch: `feat/KAN-31-ticket-import-csv-screens`, by the user's choice on 2026-11-05 and the team's branch rule, not the `feat/import-ticket-list` that `sdd.ts status` records.

### Deviations from the plan
- `NEEDS_SOLUTION` in `status-tags.ts` moved here from Task 28: without it `tsc` fails on `cells.tag(row.status)` and `StatusTag`. `StatusTag.test.tsx` counts 18 statuses, so the module's `paths` now hold `frontend/src/shared/ui/StatusTag/**`. The colour `orange` is a placeholder until the Figma kit sets it; the FE Lead reviews it.
- The real backend does not return `importType` and `needsSolutionCount` until Task 24. Mock mode is not affected.

### Verification
- `npm run format:check`, `typecheck` and `build`: pass.
- `npm run lint`: 0 errors, 1 warning that was already there (`TicketListFilters.tsx`).
- `npm run generate:api`: run again, no generated file changed.
- `npm run test`: first run 1 failed and 1372 passed; second run, with no code change, 1373 of 1373 passed. The output of the first run was cut, so the failing test is not known and is not in `docs/bug-log.md`.
- `sdd.ts trace`: not applicable to this task. It adds no behavior, and the criteria of STORY-012..014 get their tests in Tasks 24–26.

### Blockers
none

### Next up
Task 24: upload a file without Solutions (`import-type-upload`).
