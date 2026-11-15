# Task 30: Drop the unused Solution tables and the `knowledge` package

**Module:** ticket-repository
**Assignee:** dang minh
**Status:** in-progress
**Iteration:** 1

---

## Session 2026-11-15

### Working on: drop-legacy-solution-tables
- Changeset `039-drop-legacy-solution-tables.sql`: drops `solution_source`, `solution_approval`, `reuse_record`,
  `solution`, `resolved_ticket_snapshot` without `CASCADE`.
- Deleted `vn.tsolve.knowledge` (6 files); README loses the `knowledge/` line and the four `/api/solutions` rows.
- `TicketDecisionMigrationIT.hasNoLegacySolutionTableAndNoSolutionsEndpoint_AC_010_7`: no table, and no
  `@RestController` mapping starts with `/api/solutions` (classpath scan, no Spring context).
- `WorkspaceInfoRepositoryIT` (KAN-119): its test that the legacy tables still reference `legacy_workspace` now checks
  that the legacy rows stay and the two tables are gone; the BUG-243 rollback test still checks the foreign keys
  after rolling back, because 039's rollback recreates the five tables (empty) on `legacy_workspace`.

### Deviations from the plan (decided myself, logged here)
| Question | Options | Choice | Why |
|---|---|---|---|
| `--rollback` on 039 (design: none) | none / recreate empty tables | Recreate empty tables | BUG-243 test rolls back every changeset after 035; rows come back only from a backup |
| `WorkspaceInfoRepositoryIT` asserts the dropped tables exist | keep the tables / update the test | Update the test | User decisions D38 and D42 drop them; the test guarded KAN-119's rename, which still holds |

### Before merge (risk review)
Record in the pull request the row counts of the five tables in each deployed database: the drop cannot be undone.

### Blockers
none
