---
module: ticket-repository
feature: jira-import-contract
task: 1
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 1: OpenAPI contract for Jira import and Drafts

## Summary
Reviewed `frontend/openapi/tsolve-api.yaml` as of `423b8f17` (commits `0e99166f`, then the redo `3a69ae6f`) against
the spec's Interface Contracts, and checked the backend DTOs and controllers against it. Every new endpoint, field
and rejection the spec lists is in the contract, and the backend serves the same shapes. One required field,
`ImportPreviewResponse.importType`, came from `import-ticket-list` (KAN-31 commit `0526620a`) and is not sent by the
backend on this branch. That is a cross-module gap, not this task's (informational). This backend reviewer did not
run `npm run generate:api` or the frontend checks; the parallel frontend reviewer runs them.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `POST /api/v1/imports/jira` (`JiraPullRequest` with `projectId` uuid and nullable dates) → `ImportPreviewResponse`; `POST …/previews/{id}/draft` → `DraftSavedResponse`; `GET /imports/drafts` → `ImportDraftSummary[]`; `DELETE /imports/drafts/{id}` → 204. `ImportPreviewResponse` adds `source`, `resolvedFrom`, `resolvedTo`, `draftSavedAt`, and `fileName` is nullable. `ImportPreviewRowResponse` adds `sourceUrl` and `comments` (`IssueComment`). `ImportResultResponse` adds `notice` (MSG38). Rejections: 400 (MSG01/MSG39), 403, 404 (MSG04), 502 (MSG50). All as the spec lists them. |
| Completeness | pass | Every item in the spec's "Exposed by this module" table is present. The first version's separate Jira preview endpoints (issues, solutions, finish) were removed in the redo, as D7 requires. |
| Correctness | pass | The backend records match the schema field names: `ImportPreviewResponse.java`, `ImportPreviewRowResponse.java`, `ImportResultResponse.java`, `ImportDraftSummary.java`, `DraftSavedResponse.java`, `JiraPullRequest.java`. The operationIds match the controllers (`pullJiraImport`, `saveImportDraft`, `listImportDrafts`, `deleteImportDraft`, `fillImportPreviewSolution`). Backend suite green (see Test Report). |
| Integration | pass (with note) | `importType` is required in the contract but missing from the backend DTO (Issue 1). The frontend does not read it yet outside its mocks. |

## Traceability
A contract task has no tests of its own. `bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-001 STORY-002 STORY-003 STORY-004`:

- STORY-001: 12/12 · STORY-003: 10/10 · STORY-004: 7/7
- STORY-002: 8/11. ✗ AC-002.6 (MSG36 overwrite confirm), ✗ AC-002.10 (Finish disabled), ✗ AC-002.11 (MSG37 on leave). All three are "THE web app SHALL" criteria. tasks.md gives them to Task 9 (`jira-import-preview-page`, unfinished, out of scope), and none of them depends on the contract.
- Module total: `trace ticket-repository: 37/40 acceptance criteria have a test`.

## Issues
No blocking issues.

Informational:
1. `frontend/openapi/tsolve-api.yaml:2337` makes `importType` (`WITH_SOLUTION | WITHOUT_SOLUTION`) required on
   `ImportPreviewResponse`, and the `pullJiraImport` summary (`:1023`) promises "import type WITHOUT_SOLUTION". The
   backend `ImportPreviewResponse.java:16-29` has no `importType`, so the real `POST /imports/jira` response is
   missing a field the generated client types as required. The field belongs to `import-ticket-list`
   `import-type-upload` (KAN-31, not merged into this branch).
   Fix: when KAN-31's backend lands, set `importType = WITHOUT_SOLUTION` for a Jira preview, or make it optional
   until then.
2. `tsolve-api.yaml:960` says fill-solution serves "a WITHOUT_SOLUTION preview", but the backend gate is
   `source == JIRA_IMPORT` (`ImportPreviewServiceImpl.java:113`), and a CSV preview gets 409 MSG06. The task 5
   session log records this as the user's decision for this branch. Reconcile it when PR #110 merges.

Assumptions made:
- AC-002.6, AC-002.10 and AC-002.11 are not counted against this task. They are web-app criteria assigned to Task 9.
- "Contract matches" was judged by reading the YAML against the backend DTOs. `generate:api` with no diff is the
  frontend reviewer's check.

## Test Report
See `task-1-jira-import-contract-test-report.md`. Backend: 632 unit + 48 integration tests, 0 failures. Spotless is
clean on all 50 changed Java files (equivalent check; the in-worktree `spotless:check` cannot run, see the report).
