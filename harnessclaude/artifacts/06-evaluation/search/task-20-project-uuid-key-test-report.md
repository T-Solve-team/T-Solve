# Test Report: search — Task 20: project-uuid-key (attempt 1, 2026-11-07)

## Execution

The reviewer did not run the tests. The dispatcher forbade Maven and Docker, because a full `mvn clean verify`
(tests and `spotless:check`) runs separately on the same working tree. The result of that run decides the verdict.

| Check | Command | Result |
|-------|---------|--------|
| Traceability | `bun harnessclaude/hooks/sdd.ts trace search STORY-005` | 10/10 criteria have a test (AC-005.10 in `SearchSchemaIT`) |
| Traceability (module) | `bun harnessclaude/hooks/sdd.ts trace search` | 75/90. The criteria without a test are AC-007.x and AC-009.x, outside this task. |
| Backend verify | `mvn clean verify` | Not run by the reviewer. It is running separately. |
| Frontend | n/a | `frontend/` has no diff, so no frontend check is needed. |

## Tests that cover the change (read, not run)

- `backend/src/test/java/vn/tsolve/search/SearchSchemaIT.java`: `projectKeyIsTheFormerPublicId_AC_005_10`,
  `rewritesHexProjectIds_AC_005_10`, and the adapted `givesEveryExistingRowItsOwnPublicId_AC_005_1` and unique-id test
  (for `project`, these now expect `project_pkey`).
- `backend/src/test/java/vn/tsolve/search/SearchChangesetRollbackIT.java`: `rollsBackEverySearchChangesetByCount_AC_005_3`
  (`search-only.xml` now includes 017).
- Updated for UUID Project ids:
  - `ProjectServiceImplTest`, `IssueCreatedServiceImplTest`
  - `SuggestionResultServiceImplTest`, `SuggestionResultRecordRepositoryIT`
  - `TicketListRepositoryIT`, `TicketListServiceImplTest`, `TicketSummaryMapperTest`
  - `SolutionRetrievalRepositoryIT`, `VisibleScopeSelectionTest`
  - `TicketEmbeddingIT`, `TicketFeedbackRepositoryIT`, `TicketFeedbackServiceImplTest`
  - review: `TicketDecisionIT`, `ReviewDecisionApiIT`, `ReviewStorageIT`, `JiraReviewFeedbackControllerTest`

## Coverage

Not measured, because Maven was not run.
