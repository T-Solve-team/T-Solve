# Test Report: ticket-review — Task 5: review-storage (attempt 1)

Date: 2026-11-07 · Reviewer: harnessclaude-reviewer · Range: `7e2e4c6c..77fa3a31` (squashed PR #137)

## Runs

| Run | Command | Result |
|---|---|---|
| Full verify on HEAD (dispatcher evidence; reviewer checked the log `verify2.log`) | `mvn -Dapi.version=1.44 verify` | 807 unit + 165 integration, 0 failures, 0 errors; `spotless:check` OK; BUILD SUCCESS |
| Targeted run by reviewer at `77fa3a31` (detached scratch worktree, PostgreSQL pgvector:pg16 via Docker; worktree removed afterwards) | `mvn -B -Dapi.version=1.44 -Dtest=TicketEmbeddingServiceImplTest,ImportConfirmServiceImplTest,StubProjectAccessTest,EmbeddingCatchUpJobTest -Dit.test=ReviewStorageIT,TicketDecisionIT,ReviewDecisionApiIT,ImportConfirmIT,SearchSchemaIT,TicketEmbeddingIT,TicketFeedbackRepositoryIT verify` | Unit 31/31, IT 65/65, 0 failures. Spotless failed only with "Cannot find git repository in any parent directory", an artefact of the scratch worktree (ratchet needs a git dir). It does not affect the code; Spotless passed on HEAD in the full verify. |

## Suites relevant to this task (at `77fa3a31`)

| Suite | Tests | Result |
|---|---|---|
| `vn.tsolve.review.ReviewStorageIT` | 4 | pass |
| `vn.tsolve.ticket.service.impl.ImportConfirmIT` | 8 | pass |
| `vn.tsolve.ticket.service.impl.ImportConfirmServiceImplTest` | 16 | pass |
| `vn.tsolve.search.SearchSchemaIT` | 16 | pass |
| `vn.tsolve.search.embedding.TicketEmbeddingIT` | 3 | pass |
| `vn.tsolve.search.feedback.repository.TicketFeedbackRepositoryIT` | 3 | pass |
| `TicketEmbeddingServiceImplTest`, `EmbeddingCatchUpJobTest`, `StubProjectAccessTest` | 6 + 1 + 8 | pass |

## Criterion tests

| Criterion | Test |
|---|---|
| AC-009.9 | `ReviewStorageIT.upgradesExistingTicketsWithoutInventingQueueEntryTime_AC_009_9_AC_009_11_AC_004_13_BUG_140` |
| R13 upgrade integrity | `ReviewStorageIT.upgradesAlreadyRunningSearchAndPreservesItsTimestamp_BUG_144`, `movesSearchEmbeddingAndFeedbackToTheUuidTicketKey_BUG_145`, `upgradesADatabaseAlreadyRunningReviewWithoutReapplyingStorage_BUG_140` |
| Import regression | `ImportConfirmIT` (incl. `databaseRejectsADuplicateTicketId`, after-commit listener test) |

Traceability: `sdd.ts trace` reports AC-009.9 "ambiguous" (the id is shared with import-ticket-list, and `ReviewStorageIT` is in both modules' paths). The reviewer judged it covered by the ticket-review test above; see the review.

Coverage: no coverage tool runs in `verify` (no JaCoCo report). Every changeset in scope runs in at least one upgrade test.
