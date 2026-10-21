# Task 1: OpenAPI contract for CSV import

**Module:** import-ticket-list
**Assignee:** bluebearccc (with Claude)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-22 00:50

### Working on: csv-import-contract
- Added tag `import` and six paths to `frontend/openapi/tsolve-api.yaml`:
  - `GET /api/v1/imports/csv/template`
  - `POST /api/v1/imports/csv` (multipart: `file`, `projectId`)
  - `GET /api/v1/imports/previews/{previewId}/rows` (`status`, `page`, `size` ≤ 100)
  - `GET /api/v1/imports/previews/{previewId}/rows/{rowNo}`
  - `POST /api/v1/imports/previews/{previewId}/remove-rows`
  - `POST /api/v1/imports/previews/{previewId}/confirm`
- New schemas: `MessageRef` (`{code, params}`, design B4), `ImportPreviewRowStatus`, `RowError`, `ImportPreviewRowSummary`, `ImportPreviewRowResponse`, `ImportPreviewRowPage`, `ImportPreviewResponse`, `RemoveRowsRequest`, `ImportResultResponse`. New parameter: `PreviewId`.
- Status codes follow the spec's Interface Contracts: 400 MSG27/29/04, 413 MSG28, 403, 404, 409 MSG30/40/33, 500 MSG33.
- Raw preview values are nullable, because an Error row can have missing cells. `resolvedDate` stays a string, as written in the file.
- Ran `npm run generate:api` (orval 8.41.0). The regenerated client is under `frontend/src/shared/api/generated/` (import/ + 13 model files).
- Verify: `npm run format:check && npm run lint && npm run typecheck && npm run test` → all passed (10 files, 64 tests).
- `sdd.ts trace import-ticket-list STORY-001` → 0/12. This is expected for a contract-only task: the ACs get their tests in Tasks 3–9 and 13.
- Deviation: the code goes on `feat/KAN-31-import-ticket-csv` (user decision, team Jira naming), not on the recorded module branch `feat/import-ticket-list`.
- Note: the separate catalog-renumbering session also regenerates `src/shared/api/generated/` in its own worktree. If both branches change it, re-run `npm run generate:api` after merging instead of hand-resolving conflicts.

- Commit: default@b791cc4 (feat/KAN-31-import-ticket-csv)

### Blockers
none

### Next up
Task 2 csv-import-schema.
