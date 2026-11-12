---
module: dashboard-audit
feature: audit-writer
task: 2
verdict: pass
attempt: 1
date: 2026-11-10
reviewer: harnessclaude-reviewer (subagent)
iteration: 1
---

# Review: dashboard-audit — Task 2: Record audit entries (KAN-152)

## Summary

Writer side of PR #156, range `35c102a9..60078bbe`: `common/audit` (`AuditAction`, `AuditService`,
`impl/AuditServiceImpl`), changeset `018-audit-log-entry.sql` and the master changelog, the two callers
`TicketDecisionServiceImpl.decide` and `ImportConfirmServiceImpl.importRows`, and the tests `AuditServiceImplTest`,
`AuditLogEntryIT` and `AuditRecordingIT`. Task 3 (the read API) is reviewed in `task-3-audit-log-api-review.md`.

The writer inserts with `JdbcTemplate` and opens no transaction of its own. Before writing it checks for an active
transaction and a signed-in `TSolveUser`, serialises `details` with Jackson to `?::jsonb`, and stamps
`Instant.now()` into the `TIMESTAMPTZ` `created_at`. The app has one business `DataSource` (JobRunr keeps its own
Hikari pool, `worker/config/JobRunrRuntime.java:15`), so the `JdbcTemplate` uses the JPA transaction's connection.
`AuditLogEntryIT` proves this with the real `PlatformTransactionManager`: a rollback leaves no row. The database
enforces append-only: a row trigger refuses UPDATE and DELETE, and a statement trigger refuses TRUNCATE. Both
callers record inside their business transaction, after the business writes. A failing audit insert rolls back
the whole import (`databaseFailureAfterSavingRowsRollsBackImport_AC_002_10`: zero tickets afterwards). The tests
read stored rows back, not just calls.

Since the merge, `git diff 60078bbe HEAD` touches only `TicketDecisionServiceImpl`, to add `sendBack`. The identity
callers added since (`UserCreationServiceImpl:183`, `UserRoleUpdateServiceImpl:202`,
`UserStatusUpdateServiceImpl:149`) call `record(ROLE_CHANGE, "USER", id, details)` from `@Transactional` methods
that have a signed-in caller, so they fit the writer contract. No defect against a criterion and no Critical
finding.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-002.1 to AC-002.10 implemented as specified (per-criterion list below). |
| Completeness | pass | All 11 criteria are traced to tests that name them. Weaker AC-002.6 assertions (column types, CHECK) are recorded as Minor. |
| Correctness | pass | Tests read rows back from real PostgreSQL 16 (`pgvector/pgvector:pg16`) with Liquibase. Full `mvn verify` result: see the test report. |
| Integration | pass | The interface matches the master design. Later identity callers fit the contract. No Workspace routing is needed with a single DataSource; the spec says no `workspace_id`. |

### Per-criterion verification

- **AC-002.1** — `AuditServiceImpl.java:33-61` reads the actor from `TSolveUser.current()` (`id` and `email` are
  non-null, `TSolveUser.java:17-20`) and inserts `Timestamp.from(Instant.now())`. `AuditLogEntryIT.storesRowAndOptionalDetails_AC_002_1`
  checks every stored field: UUID primary key, `created_at` within [before, after], `details->>'created' = '2'`,
  and `details IS NULL` when no details are given. The unit test checks the JSON and that null arguments are refused.
- **AC-002.2** — `isActualTransactionActive()` is checked first (`:34`). The IT gets `IllegalStateException` and
  count 0 with no transaction; the unit test shows no JDBC call is made.
- **AC-002.3** — `:37-38`. With the security context cleared, inside a transaction, the IT gets `IllegalStateException` and count 0.
- **AC-002.4** — `rollsBackWithCaller_AC_002_4`: real transaction manager with `setRollbackOnly`, then count 0.
  This also proves the writer joins the caller's transaction.
- **AC-002.5** — `018-audit-log-entry.sql:21-32`. A parameterised IT runs UPDATE, DELETE and TRUNCATE, expects
  `DataAccessException` each time, and finds the row unchanged.
- **AC-002.6** — The SQL matches the spec: `gen_random_uuid()` default, nullable `actor_id`, JSONB `details`,
  TIMESTAMPTZ `created_at`, CHECK on the 10 actions, `idx_audit_log_entry_created`. The test checks that
  `audit_event` is gone, the exact column set (no `updated_at`, no `workspace_id`) and the index. It does not check
  types or the CHECK (Issue 1).
- **AC-002.7** — `TicketDecisionServiceImpl.java:91-99` maps APPROVED → APPROVE, REJECTED → REJECT and
  CHANGES_REQUESTED → REQUEST_CHANGES. It records once per ticket of the locked batch, after `saveAll`, in
  `@Transactional decide`. `recordsEachTicketInMaximumBatch_AC_002_7` runs 3 actions × 500 tickets and checks the
  stored `target_id`s exactly, which also rules out duplicates.
- **AC-002.8** — Every refusal (wrong status, foreign Project, blank feedback) throws before `record`. The IT runs
  all three: 0 entries, ticket still `PENDING_REVIEW`. A failure after `record` rolls back the shared transaction
  (AC-002.4); there is no direct test of that (Issue 4).
- **AC-002.9** — `ImportConfirmServiceImpl.java:212-216`, inside the import transaction. The IT checks the stored
  row: action, target, `importBatchId` matching the response, `created = 1`, `skipped = 2` (one ERROR row and one
  REMOVED row).
- **AC-002.10** — Inactive Project: 0 entries. Ticket ID taken: 0 entries and a FAILED batch. Audit insert forced
  to fail after the rows are saved: 0 entries, 0 tickets and a FAILED batch.
- **AC-002.11** — Both ITs use `@DataJpaTest` with `Replace.NONE`, `@ServiceConnection` and the pg16 image.
  Liquibase runs through Boot, which the AC-002.6 assertions confirm. `runsOnPostgres16_AC_002_11` checks the server version.

## Traceability

```
✓ AC-002.1   AuditServiceImplTest, AuditLogEntryIT
✓ AC-002.2   AuditServiceImplTest, AuditLogEntryIT
✓ AC-002.3   AuditServiceImplTest, AuditLogEntryIT
✓ AC-002.4   AuditLogEntryIT
✓ AC-002.5   AuditLogEntryIT
✓ AC-002.6   AuditLogEntryIT
✓ AC-002.7   AuditRecordingIT
✓ AC-002.8   AuditRecordingIT
✓ AC-002.9   AuditRecordingIT
✓ AC-002.10  AuditRecordingIT
✓ AC-002.11  AuditLogEntryIT
trace dashboard-audit (STORY-002): 11/11 acceptance criteria have a test
```

## Issues

No blocking issue.

1. **Minor (test strength)** — `backend/src/test/java/vn/tsolve/analytics/AuditLogEntryIT.java:234-253` checks only
   column names and the index for AC-002.6. It does not check the UUID, JSONB and TIMESTAMPTZ types, the nullable
   `actor_id`, the `gen_random_uuid()` default, or the `action` CHECK. The SQL is correct today. Suggested fix:
   assert `data_type` and `is_nullable` from `information_schema.columns`, and test that an unknown `action` is
   rejected.
2. **Minor (drift guard)** — `backend/src/main/java/vn/tsolve/common/audit/AuditAction.java:3-14` against
   `018-audit-log-entry.sql:14-17`. Nothing ties the enum to the CHECK list. A new enum value added without a
   changeset makes every `record` with that value fail with a check violation, which rolls back the caller's
   business action. Suggested fix: a test that inserts every `AuditAction.values()` entry.
3. **Minor (error mapping)** — `backend/src/main/java/vn/tsolve/ticket/service/impl/ImportConfirmServiceImpl.java:120`
   maps any `DataIntegrityViolationException` to `TicketIdTaken` (409). An audit insert that breaks a constraint
   would also land there (for example Issue 2, or an `actor_email` over `VARCHAR(254)`), and the user would be told
   a Ticket ID is taken. The rollback is correct, so AC-002.10 holds. Suggested fix: map only the ticket insert's
   violation to `TicketIdTaken`.
4. **Info** — `AuditRecordingIT.java:140-155`: no decision test fails after `record`. Rollback is covered by the
   writer-level AC-002.4 test and `@Transactional decide`; the import side has an equivalent test.
5. **Info** — `AuditServiceImpl.java:60` uses `Instant.now()`, not an injected `Clock`. This matches the spec
   ("current UTC time"), but the stamp can differ slightly from the decision's `clock.instant()`.
6. **Info** — `018-audit-log-entry.sql:21-32`: the table owner can still disable the triggers or drop the table.
   AC-002.5 is met as written. Full tamper-proofing would need an app role that does not own the table.
7. **Info** — `TicketDecisionServiceImpl.java:106` (HEAD): `sendBack`, added after the merge, records no entry.
   AC-002.7 does not list it. Whether Send Back should be audited is a decision for the review module's spec.

## Assumptions made

- **Workspace routing:** judged against the code as it stands: one business DataSource, no routing DataSource, and
  no `workspace_id` in the spec. When per-Workspace routing arrives, the `JdbcTemplate` must resolve to the same
  routed DataSource as JPA.
- **AC-002.8 "fails":** read as covered by AC-002.4 plus `@Transactional decide` (Issue 4).
- **AC-002.6:** the missing type and CHECK assertions are scored Minor, because the table replacement, columns and
  index are asserted against the real schema.
- The reviewer did not run Maven. The dispatcher's `mvn verify` result is in the test report.

## Test Report

See `task-2-audit-writer-test-report.md`: full `mvn verify` on `main` `29a42b7e` (BUILD SUCCESS) — 1086 unit + 395 IT, 0 failures/errors/skips; the feature's classes all green; formatter clean on the PR's Java files (BUG-130 workaround).
