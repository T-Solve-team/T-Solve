# Test report: ticket-repository — Task 29: ticket-decision-table

Date: 2026-11-17 · Scope: HEAD `7554caca` (PR #251, `f3f782f3`) · JDK: IntelliJ JBR 21

The tests ran in a copy of `backend/` placed in a scratch folder, not in this tree. A `mvn verify` (failsafe) was running in this tree's `backend/target`, and running the tests there would have overwritten its output.

## Commands
- `mvn -B -q -Dtest='JiraFinishTest,ImportConfirmServiceImplTest,ManualTicketServiceImplTest,ManualTicketDraftTest,ManualTicketEvidenceTest,TicketDetailVisibilityTest,TicketPublicationTest' test`
- `bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-010`
- `gh pr checks 251`

## Results

| Suite | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| vn.tsolve.ticket.service.impl.ImportConfirmServiceImplTest | 23 | 0 | 0 | 0 |
| vn.tsolve.ticket.service.impl.JiraFinishTest | 19 | 0 | 0 | 0 |
| vn.tsolve.ticket.service.impl.ManualTicketDraftTest | 11 | 0 | 0 | 0 |
| vn.tsolve.ticket.service.impl.ManualTicketEvidenceTest | 19 | 0 | 0 | 0 |
| vn.tsolve.ticket.service.impl.ManualTicketServiceImplTest | 13 | 0 | 0 | 0 |
| vn.tsolve.ticket.service.impl.TicketDetailVisibilityTest | 18 | 0 | 0 | 0 |
| vn.tsolve.ticket.service.impl.TicketPublicationTest | 15 | 0 | 0 | 0 |

The trace found a test for each of the 7 STORY-010 acceptance criteria.

The CI checks of PR #251 all passed: backend, backend-tests unit, it-keycloak-a, it-keycloak-b and it-other.

## Not run here
- Integration tests, by the dispatcher's instruction: `TicketDecisionMigrationIT`, `ReviewStorageIT`, `WorkspaceInfoRepositoryIT`, `DashboardOutcomesIT`, `ReviewHistoryApiIT`, `ReviewNotificationIT`, `TicketDecisionIT`, `SendBackApiIT`, `TicketPublicationIT`, `ImportConfirmIT` and `ManualTicketControllerIT`.
- `spotless:check`: the ratchet needs `origin/main`, which the copy does not have. The verify running in this tree covers both.
