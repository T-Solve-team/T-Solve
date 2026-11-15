---
module: wiki
feature: wiki-trash-api
task: 9
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: wiki — Task 9: Trash API

Commit reviewed: `2a1c6c2b` (KAN-173). Reviewed by reading the code only; the reviewer ran no tests (see Test Report).

## Summary
All four endpoints (`trashWikiDocument`, `listWikiTrash`, `restoreWikiDocument`, `deleteWikiDocumentPermanently`) and the AC-005.4 read are built as the spec says. Every read path and port filters on `w.status = 'PUBLISHED'` through `AccessSql.PUBLISHED_DOCUMENT` / `VIEWABLE_DOCUMENT`: lists, Company-wide, search (keyword and vector), download (`findAvailableDocuments`), `WikiSources.nearest/unavailable`, `WikiSourceRetrieval.nearest` and `WikiDocumentAccess.available`. That means a trashed document drops out in the same request that trashes it. Manager checks read the database on every call (`MANAGES_SPACE` = Active Space + Active Department + Active user + `space_manager` + role). A non-manager gets 403 MSG08 only when they can view the document (and only Published documents can be viewed), otherwise 404 MSG110. The row is locked with `PESSIMISTIC_WRITE` before any state change. The audit entry is written in the same transaction, and for a permanent delete it is written before the row is deleted (BUG-233). The file is deleted in `afterCommit`, with a WARN log that names the key if the delete fails. A restore that loses its title to a concurrent request maps only `uq_wiki_document_published_title` to 409 MSG84 and rolls back. No critical or major findings.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-005.1–AC-005.12 and AC-005.17 are built as written. The OpenAPI contract was added with the code, and the generated client was regenerated. |
| Completeness | pass | All task ACs are covered. `WikiBrowseServiceImpl.getDocument` falls back to `findTrashedDocument` with `canDelete` set to false (AC-005.4). Download still answers 404 for trashed documents (AC-002.13). |
| Correctness | pass (static) | Unit tests, controller tests and the IT name each AC. The IT covers concurrent restore, an upload that takes the probed title, audit rollback and a storage failure. The reviewer did not run them. |
| Integration | pass | Chatbot, triage and AI Suggestion ports all filter by status. The only FK to `wiki_document` is `wiki_chunk ON DELETE CASCADE`, so the permanent delete cannot be blocked by another module's FK. |

## Traceability
```
  ✓ AC-005.1  WikiTrashServiceImplTest, WikiTrashControllerTest, WikiTrashIT
  ✓ AC-005.2  WikiTrashIT
  ✓ AC-005.3  WikiTrashServiceImplTest, WikiTrashControllerTest, WikiTrashIT
  ✓ AC-005.4  WikiTrashServiceImplTest, WikiTrashIT
  ✓ AC-005.5  WikiTrashServiceImplTest, WikiTrashControllerTest, WikiTrashIT
  ✓ AC-005.6  WikiTrashServiceImplTest, WikiTrashControllerTest, WikiTrashIT
  ✓ AC-005.7  WikiTrashServiceImplTest, WikiTrashIT
  ✓ AC-005.8  WikiTrashServiceImplTest, WikiTrashControllerTest, WikiTrashIT
  ✓ AC-005.9  WikiTrashServiceImplTest, WikiTrashControllerTest, WikiTrashIT
  ✓ AC-005.10 WikiTrashServiceImplTest, WikiTrashControllerTest, WikiTrashIT
  ✓ AC-005.11 WikiTrashServiceImplTest, WikiTrashIT
  ✓ AC-005.12 WikiTrashServiceImplTest, WikiTrashIT
  ✓ AC-005.17 WikiTrashServiceImplTest, WikiTrashControllerTest, WikiTrashIT
trace wiki (STORY-005): 17/17 acceptance criteria have a test
```

## Issues
Minor only (they do not fail the task):
1. `backend/src/main/java/vn/tsolve/wiki/service/impl/WikiTrashServiceImpl.java:82`: minor. `substring` cuts by UTF-16 units, so it can split a surrogate pair (emoji or CJK extension characters) and leave a lone surrogate. It can also leave trailing whitespace before the suffix (`"abc  (restored)"`). Fix: cut on a code-point boundary (`original.offsetByCodePoints`) and `stripTrailing()` the base before adding the suffix.
2. `backend/src/main/java/vn/tsolve/wiki/service/impl/WikiTrashServiceImpl.java:101`: minor. If `jobs.enqueue` throws in `afterCommit`, the exception reaches the caller, so a restore that has already committed answers 500. Upload behaves the same way. Fix: catch and log at WARN inside `afterCommit` (the document can still be found by keyword, Non-Functional 4).
3. `backend/src/main/java/vn/tsolve/wiki/entity/WikiDocument.java:116,124`: minor. Trash and restore both overwrite `updatedAt`, so the Updated column (AC-002.10) shows the restore time instead of the last content change. The spec does not say which is right; see Assumptions.
4. `backend/src/main/java/vn/tsolve/wiki/controller/WikiTrashController.java:47`: minor. `listWikiTrash` is guarded by role `KNOWLEDGE_MANAGER`, while the other three endpoints use `EMPLOYEE` plus a service check. An Employee therefore gets 403 MSG08 even for an unknown Space, and a Knowledge Manager gets 404 MSG04 for one. This is coherent, but it is not spelled out by AC-005.10, which is worded per document. See Assumptions.

## Assumptions made
- AC-005.10 for the Space-scoped `GET /spaces/{id}/trash`: answering 403 MSG08 (non-manager) and 404 MSG04 (unknown, Hidden, or Deleted-Department Space) is accepted, following AC-002.5 and AC-002.6 and the OpenAPI entry. The user may decide otherwise.
- Updating `updatedAt` on trash and restore is accepted. The spec does not cover it.
- The reviewer ran no tests, as the dispatcher instructed. This PASS depends on `WikiTrashIT`, the unit and controller tests, and `mvn verify` (with spotless) being green in the orchestrator's Docker run.

## Test Report
See `task-9-wiki-trash-api-test-report.md`.
