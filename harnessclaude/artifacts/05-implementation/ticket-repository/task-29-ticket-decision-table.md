# Task 29: One `ticket_decision` table

**Module:** ticket-repository
**Assignee:** dang minh
**Status:** in-progress
**Iteration:** 1

---

## Session 2026-11-15

### Working on: ticket-decision-table
- Branch `feat/KAN-137-ticket-repository-decision-table`, stacked on #248 (KAN-136, changeset 037): `main` + #248.
- Plan of PR #246 renumbered onto the current spec: STORY-007 → STORY-010, D17–D22 → D38–D43, Tasks 18–19 → 29–30
  (KAN-134–KAN-136 took the old numbers). Allowed decision values include UNPUBLISH / REPUBLISH (KAN-136).
- Changeset `038-ticket-decision.sql`: renames `review_decision` and its columns, PK, CHECK and two indexes; adds
  `reviewer` and the reviewer CHECK; copies `ticket_import_decision` with `CASE` and drops it.
- `TicketDecision` + `TicketDecisionRepository` replace `ReviewDecisionRecord`, `TicketReviewDecision`,
  `ReviewDecision` and both old repositories; imports and manual entry use `approvedByImport`.
- Review History (both queries and count), `findWriterNotification` and the dashboard `decisions` query also require
  `ticket.source = 'SOLUTION_FORM'` (AC-010.5).
- Tests: new `TicketDecisionMigrationIT` (AC-010.1, .2, .6) on its own database in the shared `TwoTenantDatabases`
  server (new `emptyDatabase` helper, no extra container); AC-010.5 tests added to `DashboardOutcomesIT`,
  `ReviewHistoryApiIT`, `ReviewNotificationIT`; AC-010.3 / AC-010.4 ids added to existing tests that already assert
  the rows; table, column and type names updated in 25 other test classes.

### Deviations from the plan (decided myself, logged here)
| Question | Options | Choice | Why |
|---|---|---|---|
| Base branch: `main` or #248 | main (conflicts with #248's changeset 037 and entity) / stack on #248 | Stack on #248 | #248 changes the same table and entity; the PR targets `main` after #248 merges |
| `--rollback` on 038 (design: none) | none / full reverse | Full reverse | `WorkspaceInfoRepositoryIT` BUG-243 rolls back every changeset after 035; without a rollback it fails |
| `TicketDecisionReadsIT` (plan) | new IT class / tests in the ITs that own each read | Existing ITs | No new Spring context or container (backend/AGENTS.md §8) |
| `dashboard-audit` spec changed since approval (E6) | wait for its approval / list the two files in this module's `paths` | `paths` | One predicate; the dashboard spec is not changed |

### Blockers
none

### Next up
Task 30
