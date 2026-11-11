# Test Report: ticket-review — Task 7: review-decision-endpoints (attempt 1)

Date: 2026-11-07 · Reviewer: harnessclaude-reviewer · Range: `7e2e4c6c..77fa3a31` (squashed PR #137)

## Runs

| Run | Command | Result |
|---|---|---|
| Full verify on HEAD (dispatcher evidence; reviewer checked `verify2.log`) | `mvn -Dapi.version=1.44 verify` | 807 unit + 165 integration, 0 failures; Spotless OK; BUILD SUCCESS |
| Targeted run by reviewer at `77fa3a31` (detached scratch worktree, Docker PostgreSQL, two tenant databases via `TwoTenantDatabases`) | `mvn -B -Dapi.version=1.44 … -Dit.test=ReviewStorageIT,TicketDecisionIT,ReviewDecisionApiIT,… verify` | `ReviewDecisionApiIT` 23/23 pass; all 65 targeted ITs pass. Spotless was not evaluable in the scratch worktree; it passed on HEAD. |

## Suite

`vn.tsolve.review.ReviewDecisionApiIT`: 23 tests (`@SpringBootTest` + MockMvc, the real security filter chain, ACME and BETA tenant PostgreSQL databases).

Decision-endpoint tests:

| Test | Asserts |
|---|---|
| `deniesRequestsWithoutAuthentication_AC_009_3` | anonymous → 401 |
| `deniesAuthenticatedUsersWithoutProjectManagerRole_AC_009_3` | EMPLOYEE → 403 |
| `deniesUnboundAndUnmanagedRequests_AC_009_3` | no Workspace binding → 403; unmanaged Project → 403 MSG08; ticket unchanged |
| `cannotDecideAnotherWorkspacesTicket_AC_009_3_AC_009_7` | BETA id in an ACME batch → 404 MSG04; neither database changes |
| `validatesFeedbackAndUniqueIds_AC_009_7` | blank reason → 400 with fieldErrors `reason`/MSG01; duplicate ids → 400; U+2003 comment → 400; unchanged |
| `commitsEachHttpDecision_AC_009_4_AC_009_5_AC_009_6_AC_009_8` [approve, reject, request-changes] | 200 `{count:1}`, status, decision row with the session reviewer |
| `acceptsSearchPublicIdsWithoutDuplicatingDecisions_BUG_144` | `ticket_id` and `public_id` of the same ticket → 404, no write; `public_id` alone → one decision |

The remaining tests in the suite cover Task 9 and Task 10 reads and Jira feedback.

Not exercised through HTTP: empty or 501-id batches, a 1,001-character reason, and an absent adapter bean (see the review warnings).

Coverage: no coverage tool in `verify`.
