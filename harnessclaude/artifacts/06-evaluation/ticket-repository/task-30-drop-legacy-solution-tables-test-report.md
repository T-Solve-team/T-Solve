# Test report: ticket-repository — Task 30: drop-legacy-solution-tables

Date: 2026-11-17 · Scope: HEAD `7554caca` (PR #251, `f3f782f3`) · JDK: IntelliJ JBR 21

## Commands
- `bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-010`: the trace found a test for each of the 7 acceptance criteria. For AC-010.7 it is `TicketDecisionMigrationIT`.
- `gh pr checks 251`: all checks passed (backend, unit, it-keycloak-a, it-keycloak-b, it-other).
- Greps of `backend/src`, `frontend/src` and `README.md`:
  - No reference is left to the five dropped tables outside the changesets and their tests.
  - No `vn.tsolve.knowledge` package and no `@Table` maps a dropped table.
  - `/api/solutions` appears only as a sample path in `OperatorSessionTest`.

## Not run here
- `TicketDecisionMigrationIT` and `WorkspaceInfoRepositoryIT` need Testcontainers. The dispatcher said not to run them, because a full `mvn verify` is running in this tree.
- This task added no unit test. The shared unit run is in `task-29-ticket-decision-table-test-report.md`: 118 tests, 0 failures.
