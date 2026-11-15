---
module: wiki
feature: wiki-folder-api
task: 4
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: wiki — Task 4: Folder API

## Summary
This review covers commit d74e1ba0, checked against spec STORY-003 (AC-003.1 to AC-003.12 and AC-003.17). No tests were run: the dispatcher said not to run mvn or npm, and Docker is off. The verdict is pass **on the condition** that `mvn verify` (including `WikiFolderIT` and `spotless:check`) and the frontend checks are green.

Checked by reading the code:
- **Create, update, delete.** `WikiFolderServiceImpl` is one `@Transactional` class behind `WikiFolderService`. The name is stripped before it is saved (AC-003.1, AC-003.2). A move recomputes the level of the whole sub-tree with a recursive CTE and does not touch the documents (AC-003.2).
- **Name validation.** `@NotBlank` gives MSG01, and `@Size(max=100)` gives MSG02 with `max_length` 100, both on `name`. A name made only of Unicode whitespace is turned into "" so that it gets MSG01 (AC-003.3).
- **Duplicate names.** MSG85 on `name` is returned only when the broken constraint is `uq_folder_parent_name`; other integrity errors are rethrown (AC-003.4, AC-003.12). The design's index uses a sentinel value for the top level, so top-level siblings collide too.
- **Depth and cycles.** MSG86 on `parentId` is returned when a new folder, or the deepest folder of a moved sub-tree, would be deeper than level 3. It is also returned when the new parent is the folder itself or one of its sub-folders (AC-003.5, AC-003.6).
- **Not found.** 404 MSG04 is returned for an unknown folder, an unknown or inactive Space, and a parent in another Space (AC-003.7).
- **Delete.** It counts the direct sub-folders and all documents of the folder; Published and DELETED are the only document statuses, so trashed documents count. A non-empty folder gets 409 MSG87 with `folder_count` and `document_count`, and nothing is deleted (AC-003.8, AC-003.9).
- **Manager check.** Every endpoint has `@PreAuthorize` for KNOWLEDGE_MANAGER. The service then checks `AccessSql.MANAGES_SPACE` again in the database for the target Space: the role, the `space_manager` row, an Active user, and an Active Space and Department. A failure gives 403 MSG08 through the global handler (AC-003.10). The IT covers another Space's manager, a removed role, a deactivated user, a hidden Space and a deleted Department.
- **Audit.** Each change writes one `FOLDER_CHANGE` / `WIKI_FOLDER` entry, with exactly `operation`, `name`, `spaceId` and `parentId`, in the same transaction. The IT makes the audit insert fail and checks the rollback for all three operations (AC-003.11).
- **Concurrency.** `SELECT ... FOR UPDATE OF s` on the Space row makes a Space's tree changes run one at a time, which also blocks two moves from creating a cycle together. The unique index is the final guard. The IT runs two creates at once, at the top level and under a parent (AC-003.12).
- **Schema, contract and label.** Changeset 032 extends `ck_audit_log_entry_action` the way 027 does, with a rollback. The enum, the contract (`createWikiFolder`, `updateWikiFolder`, `deleteWikiFolder`), the generated client and the "Folder change" label (AC-003.17) are all added.
- **Injection, scope, dead code.** All SQL uses bound parameters, and the `AccessSql` fragments are constants. No scope creep or dead code was found.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-003.1 to AC-003.12 and AC-003.17 are implemented as written |
| Completeness | pass | All 13 of the task's ACs have tests named with the AC id. Edge-case rows 6, 15, 16 and 17 are covered in `WikiFolderIT` |
| Correctness | pass (conditional) | The reviewer did not run the tests. The result depends on `mvn verify` and the frontend checks |
| Integration | pass | The contract and generated client match the controller. Task 5 uses them |

## Traceability
```
trace wiki (STORY-003): 17/17 acceptance criteria have a test
```
Task 4 owns AC-003.1 to AC-003.12 and AC-003.17.

## Issues
1. **Minor.** `backend/src/main/java/vn/tsolve/wiki/dto/CreateWikiFolderRequest.java:8` (also `UpdateWikiFolderRequest.java:9`): the 100-character limit is checked on the name as received, before stripping. So `" " + 100×"x"` gets MSG02, although the stored name would be 100 characters. This reading is defensible and the contract documents it (see A1). Fix, if the user picks the other reading: check the length after stripping.
2. **Minor.** `backend/src/main/java/vn/tsolve/wiki/service/impl/WikiFolderServiceImpl.java:105`: the Space row lock is taken before the manager check. Any Knowledge Manager can therefore hold another Space's lock for a moment. Fix: call `managesSpace` first (it takes no lock), then lock.
3. **Minor.** `backend/src/main/java/vn/tsolve/wiki/service/impl/WikiFolderServiceImpl.java:93-98`: if an upload into the folder commits between the count and the delete, the delete hits the FK `RESTRICT` and the user gets a 500, not MSG87. Fix: make the Task 6 publish take the same Space lock, or map that FK violation to MSG87.

## Assumptions made
- **A1.** AC-003.3 "longer than 100 characters" is read as the name as received, not the stripped name. The spec does not say which; ask the user.
- **A2.** A `PUT` without a `parentId` property is rejected with 400 (the contract marks it required) instead of being read as "top level". The spec only says "a `parentId` (null for the top level)"; rejecting it avoids moving a folder by accident.
- **A3.** AC-003.17 is tested on the label map (`ACTION_LABELS`, `isAuditAction`), which the Audit Log screen uses.
- **A4.** Tests were not run (dispatcher instruction). The feature status was not changed. The caller sets `passing` once `mvn verify` and the frontend checks are green, or `fixing` if they are not.

## Test Report
See `task-4-wiki-folder-api-test-report.md`.
