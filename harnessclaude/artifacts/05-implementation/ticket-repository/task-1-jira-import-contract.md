# Session Log: Task 1 — OpenAPI contract for Jira import and Drafts

**Module:** ticket-repository
**Assignee:** Đặng Nhật Minh
**Iteration:** 1
**Status:** in-progress

## Session 2026-11-07
### Working on: jira-import-contract
- Added 9 endpoints to `frontend/openapi/tsolve-api.yaml`: `POST /imports/jira`, `GET /imports/previews/{previewId}/issues`,
  `GET …/issues/{jiraKey}`, `POST …/solutions`, `POST …/remove-issues`, `POST …/finish`, `POST …/draft`,
  `GET /imports/drafts`, `DELETE /imports/drafts/{previewId}`; parameter `JiraKey`; 14 schemas
  (`JiraPullRequest`, `JiraImportPreviewResponse`, `JiraPreviewIssuePage`, `JiraPreviewIssueSummary`,
  `JiraPreviewIssueResponse`, `JiraIssueComment`, `ImportSolution`, `ApplySolutionRequest`, `RemoveIssuesRequest`,
  `JiraImportResultResponse`, `ImportSource`, `ImportDraftSummary`, `DraftSavedResponse`).
- Deviation from the spec's contract table: Finish returns `JiraImportResultResponse` with a `messages` array
  (MSG32 and/or MSG38) and `skippedKeys`, instead of the CSV `ImportResultResponse` with one `message` — AC-003.2
  needs both messages and the skipped keys. Same endpoints and status codes as the spec.
- `resolvedFrom` / `resolvedTo` are nullable in the schema so a missing date reaches the backend and gets MSG01
  (AC-001.4) instead of a generic 400.
- Regenerated `frontend/src/shared/api/generated/**` (orval 8.41.0).
- Verify (Node 24.22.2, `frontend/`): `format:check` 0, `lint` 0 (1 old warning in `features/tickets/TicketListFilters.tsx`),
  `typecheck` 0, `test` 0 — 75 files, 1354 tests passed, `build` 0; `generate:api` twice → identical output.
- `sdd.ts trace ticket-repository`: 0/38 — expected, this task adds no tests. It also reports AC-004.6 / AC-004.7 as
  ambiguous: `import-ticket-list` and `ticket-repository` both claim `backend/src/test/java/vn/tsolve/ticket/**`.
  To settle before Task 2 (narrow `import-ticket-list` paths to its CSV files).

### Next up
Task 2 — jira-import-schema
