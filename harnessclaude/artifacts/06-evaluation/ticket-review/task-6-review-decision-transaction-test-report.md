# Test Report: ticket-review — Task 6: review-decision-transaction (attempt 1)

Date: 2026-11-07 · Reviewer: harnessclaude-reviewer · Range: `7e2e4c6c..77fa3a31` (squashed PR #137)

## Runs

| Run | Command | Result |
|---|---|---|
| Full verify on HEAD (dispatcher evidence; reviewer checked `verify2.log`) | `mvn -Dapi.version=1.44 verify` | 807 unit + 165 integration, 0 failures; Spotless OK; BUILD SUCCESS. `TicketDecisionIT` 8/8 there. |
| Targeted run by reviewer at `77fa3a31` (detached scratch worktree, Docker PostgreSQL) | `mvn -B -Dapi.version=1.44 … -Dit.test=ReviewStorageIT,TicketDecisionIT,ReviewDecisionApiIT,ImportConfirmIT,SearchSchemaIT,TicketEmbeddingIT,TicketFeedbackRepositoryIT verify` | IT 65/65 pass. Spotless was not evaluable in the scratch worktree (no git dir for ratchet); it passed on HEAD. |

## Suite

`vn.tsolve.review.TicketDecisionIT`: 8 tests, 0 failures, 0 errors (`@DataJpaTest` against real PostgreSQL, non-transactional so commits and locks are real).

| Test | Covers |
|---|---|
| `commitsDecisionsAndPreservesTicketContent_AC_009_4_AC_009_5_AC_009_6_AC_009_8` [APPROVED, REJECTED, CHANGES_REQUESTED] | State change, feedback columns, Solution, Evidence and due date preserved, one decision row per ticket with reviewer, action, text and time |
| `refusesInvalidMissingUnmanagedAndStaleBatches_AC_009_7_BUG_139` | duplicate ids, blank, >1,000 chars, missing, unmanaged, stale, imported; no change, no decision |
| `rollsBackTicketStateIfDecisionCannotBeSaved_AC_009_8` | FK failure on the decision rolls back the ticket |
| `rollsBackDecisionsIfAnyTicketUpdateFails_AC_009_8` | Second-ticket failure rolls back everything |
| `commitsOnlyOneOfTwoCompetingDecisions_AC_009_7_AC_009_8` | Pessimistic lock: one commit, one MSG06 |
| `recordsNewQueueEntriesButPreservesTheTimeDuringEdits_AC_009_11` | (Task 8) |

Not exercised: empty list and 501-id batches (see review warning 4).

Coverage: no coverage tool in `verify`. Every branch of `validateBatch` and `validatedFeedback` is exercised except the size bounds.
