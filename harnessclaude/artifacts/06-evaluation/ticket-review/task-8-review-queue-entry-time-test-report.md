# Test Report: ticket-review — Task 8: review-queue-entry-time (attempt 1)

Date: 2026-11-07 · Reviewer: harnessclaude-reviewer · Range: `7e2e4c6c..77fa3a31` (squashed PR #137)

## Runs

| Run | Command | Result |
|---|---|---|
| Full verify on HEAD (dispatcher evidence; reviewer checked `verify2.log`) | `mvn -Dapi.version=1.44 verify` | 807 unit + 165 integration, 0 failures; Spotless OK; BUILD SUCCESS |
| Targeted run by reviewer at `77fa3a31` (detached scratch worktree, Docker PostgreSQL) | `mvn -B -Dapi.version=1.44 … -Dit.test=ReviewStorageIT,TicketDecisionIT,… verify` | `TicketDecisionIT` 8/8, `ReviewStorageIT` 4/4; all 65 targeted ITs pass. Spotless was not evaluable in the scratch worktree; it passed on HEAD. |

## Criterion tests

| Test | Asserts |
|---|---|
| `TicketDecisionIT.recordsNewQueueEntriesButPreservesTheTimeDuringEdits_AC_009_11` | insert as Pending review → time set; title edit and same-status update → unchanged; Request Changes → unchanged; re-entry → later time; `review_due_date` unchanged |
| `ReviewStorageIT.upgradesExistingTicketsWithoutInventingQueueEntryTime_AC_009_9_AC_009_11_AC_004_13_BUG_140` | a Pending review row created before changeset 009 → `review_queue_entered_at` null after upgrade |
| `ReviewStorageIT.movesSearchEmbeddingAndFeedbackToTheUuidTicketKey_BUG_145` / `upgradesAlreadyRunningSearch…_BUG_144` | upgrade paths that run 009 with existing rows (trigger present, no spurious time changes to `updated_at`) |

Coverage: no coverage tool in `verify`. Every branch of the trigger condition is exercised (insert, status change, unchanged status, non-status edit).
