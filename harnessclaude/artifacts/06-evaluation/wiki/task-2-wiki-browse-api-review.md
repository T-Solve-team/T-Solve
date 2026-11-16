---
module: wiki
feature: wiki-browse-api
task: 2
verdict: fail
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: wiki — Task 2: Browse, search and read API

Commit reviewed: `24df32dd` (diff `24df32dd^..24df32dd`). Contract: spec STORY-002 (AC-002.1 to AC-002.11, AC-002.13), design edge-case rows 4, 14 and 18.

## Summary
The production code does what AC-002.1 to AC-002.11 and AC-002.13 say. Every read uses the shared `AccessSql` fragments, all SQL is built from constants with named parameters, and the error codes match the spec's table (403 MSG08, 404 MSG04, 404 MSG110, 400 MSG01 and MSG02 on field `q`, 401 MSG07). The verdict is still **fail**, because two of the new integration tests cannot pass. They insert a second Space for a Department that already has one, and `space.department_id` is `UNIQUE` (`020-identity-space-manager.sql:8`, GBR-29). The commit says the ITs were never run. Both tests are still the same at HEAD.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Each AC is implemented as written. The view rule comes from `AccessSql.VIEWABLE_SPACE`, `VIEWABLE_DOCUMENT` and `MANAGES_SPACE` on every read, `canDelete` from `MANAGES_SPACE`, search uses `websearch_to_tsquery` plus the best-chunk similarity with a keyword-only fallback, and `q` is length-checked before it is stripped |
| Completeness | pass | All 12 ACs of the task and edge rows 4, 14 and 18 have tests that name them |
| Correctness | fail | `WikiBrowseIT.listsOnlyViewableSpacesByNameAndComputesManagerAccess_AC_002_1` and `WikiSearchIT.filtersAccessStatusAndDeletedDepartmentsForBothSearchPaths_AC_002_7_AC_002_8` fail with a duplicate-key error on `space.department_id`. Tests were not run (dispatcher's instruction); this was found by reading the code |
| Integration | pass | The contract in `tsolve-api.yaml` matches the spec's schemas, operationIds and errors; the generated client was regenerated. Only the `WikiAccessIT` context was narrowed, to explicit `register` calls |

## Traceability
```
✓ AC-002.1 … ✓ AC-002.13 (backend: WikiBrowseServiceImplTest, WikiSearchServiceImplTest,
  WikiBrowseControllerTest, WikiDocumentControllerTest, WikiBrowseIT, WikiSearchIT)
trace wiki (STORY-002): 21/21 acceptance criteria have a test
```

## Issues
1. **major** `backend/src/test/java/vn/tsolve/wiki/WikiBrowseIT.java:20`: `space(department, "Alpha Wiki")` inserts a second Space for the Department that `prepare()` already gave "Support Wiki". `space.department_id` is `UNIQUE`, so the insert throws `DuplicateKeyException` and the AC-002.1 IT fails before it asserts anything. Fix: put "Alpha Wiki" in its own Department (`space(department("Alpha"), "Alpha Wiki")`) and make it viewable through a `space_permission` row for `user` (or a Department permission), so the test still checks the name order and the VIEW/KNOWLEDGE_MANAGER levels.
2. **major** `backend/src/test/java/vn/tsolve/wiki/WikiSearchIT.java:81`: `space(department, "Hidden Wiki")` hits the same unique constraint. Fix: `space(department("Hidden"), "Hidden Wiki")`. The document there is Company-wide, so hiding the Space still tests the Hidden exclusion.
3. minor `backend/src/main/java/vn/tsolve/wiki/controller/WikiBrowseController.java:53`: a `spaceId`, `folderId` or `documentId` that is not a UUID gets Spring's type-mismatch 400, not 404 MSG04 or MSG110. The spec says "All ids are UUIDs" and gives no code for a malformed one. Fix: none needed unless the user wants 404. See Assumptions.
4. minor `backend/src/main/java/vn/tsolve/wiki/repository/WikiSearchRepository.java:22`: the class also holds every browse and read query (spaces, folders, documents, detail). A name like `WikiReadRepository` would fit better. Fix: optional rename.

## Assumptions made
- Malformed (non-UUID) ids answering 400 is acceptable. The spec only covers ids that do not exist.
- The 200-character limit of AC-002.9 counts UTF-16 code units (`String.length()`). The web app's `value.length` counts the same way, so the two agree; a query full of emoji reaches the limit sooner.
- `hasRole('EMPLOYEE')` counts as "signed in": `TSolveUser` gives every user `EMPLOYEE`.
- Embedding the search query without PII masking follows every other `EmbeddingClient` caller (the model is local Ollama). Not flagged.

## Test Report
See `task-2-wiki-browse-api-test-report.md`. No test was run (dispatcher's instruction: no mvn, Docker off). The two failing ITs above were found by reading the code against the schema.
