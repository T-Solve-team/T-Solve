---
module: project-integration
feature: project-reconnect
task: 4
verdict: pass
attempt: 1
date: 2026-11-13
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: project-integration — Task 4: Reconnect the same Removed Project safely (STORY-003, STORY-002, STORY-007, KAN-127)

## Summary

I reviewed code commit `560a7dae` and harness commit `3b860534`, diffed against `1a0795ac`. The change touches 3 main
files (`ProjectServiceImpl`, `ProjectLinkRepository`, the Javadoc of `ProjectService`) and 3 test files
(`ProjectServiceImplTest`, `ProjectControllerTest` renames, new `ProjectReconnectIT`). I checked it against:
- spec STORY-003 (AC-003.1–AC-003.10), plus the STORY-002 and STORY-007 criteria Task 4 cites (AC-002.2, AC-002.3,
  AC-002.5, AC-002.7, AC-002.17, AC-002.18, AC-007.6)
- the `createProject` 200 row and the Persistence contract in the spec's Interface Contracts
- Task 4 in `iteration-1/tasks.md`
- the master design's flow, SQL block and lost-insert paragraph (`design.md:60-82,150-191`), and edge-case rows 10,
  11, 16, 17, 23, 27, 32, 34 and 36
- D11, D22, D27 and D41 in `kan-127-decisions.md`

I read the session log only for the commit ids, the listed deviations and the claimed test counts.

An eligible Removed Project of the caller's Department now reconnects in place
(`ProjectServiceImpl.java:123-166`). Everything before the write transaction is the Task 3 path, unchanged. In one
`TransactionTemplate` the service:
1. re-reads the caller's Department
2. locks each found link `FOR UPDATE` and re-reads it (requested key, then canonical key)
3. runs one conditional `UPDATE … SET status='ACTIVE', jira_project_key=:canonical, done_status, review_range WHERE
   public_id=:id AND status='REMOVED'` (`ProjectLinkRepository.java:119-131`), where zero rows gives MSG49
4. changes memberships only when the caller is not the previous manager: deletes only that user's `PROJECT_MANAGER`
   row, then upserts the caller
5. reads the stored projection and records one `RECONNECT` audit built from it
6. returns `created=false`, which the Task 3 controller answers with 200

If the canonical-key update meets a concurrent insert of that key, the `DuplicateKeyException` on
`uq_project_jira_key` is caught after the rollback and becomes MSG49 naming the committed winner (D41). Any other
constraint propagates as 500 MSG06.

The three listed deviations are acceptable:
- **One `UPDATE` instead of `reconnectProject` plus `canonicaliseKey`:** setting an unchanged key is a no-op, and the
  key still persists in the same statement and transaction.
- **The Javadoc-only change to `ProjectService.java`:** no behaviour change.
- **The post-rollback translation:** it is exactly what the design and D41 ask for.

There are no CRITICAL, HIGH or MEDIUM findings. All targeted tests pass, and `ProjectReconnectIT` passed 3 times out of
3. Three LOW findings are test gaps on the database:
- LOW-1: the two race guards, and the unique-violation translation, are not each forced on PostgreSQL
- LOW-2: the canonical key on reconnect is proven only by a race
- LOW-3: the AC-007.6 IT runs no membership write and does not compare the other tenant's membership and audit rows

My mutations confirm all three (Test Report). Findings already accepted under D50 (T3 LOW-1, INFO-2, INFO-3) are not
raised again. LOW-1 is the reconnect counterpart of T3 LOW-1 and fits the same hardening pass.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | All 10 STORY-003 criteria and the cited STORY-002/STORY-007 criteria hold for the reconnect path (Stage 1 table). |
| Completeness | pass | Every Task 4 criterion has a test that names it (trace 10/10 for STORY-003, 29/29 for STORY-002 and STORY-007). Every Task 4 test bullet (rows 10, 11, 16, 17, 23, 27, 32, 34, 36) has a matching test. Nothing EXTRA: the only unlisted file is a Javadoc change. |
| Correctness | pass | `ProjectServiceImplTest` 121/121, `ProjectControllerTest` 68/68, `ProjectReconnectIT` 19/19 and the other Project ITs (51/51 in total) pass. Spotless passes. `ProjectReconnectIT` is stable over 3 runs. My `skip-delete` mutation is killed by both layers. The status-guard mutation survives every test (LOW-1). |
| Integration | pass | The 200 response matches the `Project` schema; the IT compares the whole body. The contract already has the 200 row, so the generated client is unchanged. The audit goes through `AuditService.record` in the same transaction: project, caller membership and audit rows share one `xmin`. Tracker is used only through `TrackerProjectService`. `findActivePublicIdByJiraProjectKey` returns the retained id. |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-003.1 eligible Removed row → ACTIVE, canonical key, saved ids and range, STORY-002 validation and tracker rules | PASS (LOW-2) | `ProjectLinkRepository.java:119-131`. IT `reconnectsForTheSamePreviousManagerKeepingEverything…` checks status, deduplicated ids and range 5. The fresh-read and validation refusals are tested in the unit tests (`refusesReconnectWhenTheFreshJiraReadFails…` for NOT_FOUND/DENIED/UNAVAILABLE, `refusesReconnectWithAnUnknownIdOrInvalidRange…`) and in the IT (`refusesReconnectAfterAFailedFreshJiraCheck…`: NOT_FOUND, unknown id, whole snapshot unchanged). The canonical key is checked only with a mock argument and the race (LOW-2). |
| 2 | AC-003.2 keep `projectId`, Department, tickets, resolver memberships, `field_mapping` | PASS | The `UPDATE` never names `department_id`, `field_mapping` or `public_id`. The IT compares `project_id`, `department_id`, key and `field_mapping::text` before and after, counts the ticket, and compares every membership row's JSON. |
| 3 | AC-003.3 same previous PM → memberships unchanged | PASS | `ProjectServiceImpl.java:159` skips every membership statement. IT: `row_to_json` of all memberships identical, including `date_added`. Unit: `never().deleteManager`/`upsertManager` |
| 4 | AC-003.4 delete old PM row, then upsert the caller; caller already MEMBER | PASS | `deleteManager` filters on `project_id`, `user_id` and `role_in_project='PROJECT_MANAGER'` (`ProjectLinkRepository.java:134-139`), so only that row goes. `upsertManager` `ON CONFLICT (user_id, project_id) DO UPDATE` promotes a MEMBER row in place. IT variants none/deactivated/moved/role-removed/caller-member: exactly `(CALLER, PROJECT_MANAGER)` plus the unchanged MEMBER row (JSON compared), one `xmin`. Unit test checks delete-before-upsert order. Mutation `skip-delete` → 500 in 4 IT variants. |
| 5 | AC-003.5 one RECONNECT audit, signed-in actor, retained id, exact details | PASS | `:131-133`, built from the stored projection (`auditDetails`). `assertReconnectAudit` matches actor id and email, action, target type and `details = CAST(? AS jsonb)`, filtered by the retained `target_id`. Exactly one row (`containsExactly`). |
| 6 | AC-003.6 200 with the updated `Project` | PASS | `ProjectSaveResult(saved, false)`. The IT compares the whole JSON body; controller test `answers200WithTheReconnectedProject_AC_003_6` |
| 7 | AC-003.7 foreign Department or protected PM → 409 MSG49 owner name, no writes | PASS | `refuseIfLinked` before Jira. IT protected/foreign/active: exact params and field, `tracker.calls()` empty, whole snapshot unchanged. |
| 8 | AC-003.8 competing Saves → one 200 with one audit, loser 409 MSG49 | PASS (LOW-1) | Lock and re-read (`:221-228`), conditional `UPDATE` (`:156-158`), post-rollback translation (`:136-143`). IT same-key / alias / alias-and-create: one success, one MSG49, one Active row, one PM, one audit. Stable 3/3. The individual guards are proven only with mocks (LOW-1). |
| 9 | AC-003.9 membership or audit failure → 500 MSG06, original row, settings and memberships restored, no audit | PASS | IT `restoresTheRemovedProjectAndMembersWhenTheAuditFails_AC_003_9` fails the real audit after all three writes, then checks that the project row JSON and all 3 memberships (former PM, MEMBER caller, MEMBER) are byte-identical and that no audit was written. Delete and upsert failures use Mockito (rollback, never commit), the technique the task prescribes. The controller maps both to 500 MSG06 `{}`. |
| 10 | AC-003.10 lookup returns the retained id for the canonical key | PASS (LOW-2) | IT `scope.runIn(ACME, findActivePublicIdByJiraProjectKey("PRCROLD"))` contains the retained id. Only with an unchanged key (LOW-2). |
| 11 | AC-002.2/AC-002.18 fresh Jira read, outside any transaction | PASS | The fake tracker throws inside a transaction. Unit `InOrder`: tracker, then `getTransaction`. |
| 12 | AC-002.3/AC-002.5/AC-002.7 on reconnect | PASS | Rows 1 and 7 above. |
| 13 | AC-002.17 recheck inside the transaction | PASS | IT role removed, manager claimed, Project activated during the Jira read: MSG08/MSG49, row unchanged, no caller membership, no audit. Unit `rechecksReconnectEligibilityInsideTheTransaction…` |
| 14 | AC-007.6 bound database only | PASS (LOW-3) | IT `reconnectsOnlyInTheBoundWorkspaceDatabase_AC_007_6`: the same key is Removed in ACME and BETA. ACME turns ACTIVE; the BETA project row and the main snapshot are unchanged. |

### Stage 2 — Quality and security pass

Checked and found sound:
- **Lock order and deadlocks.**
  - Each Save locks at most two rows, always requested key first, then canonical key. A canonical key is its own
    canonical, so no Save locks (canonical, alias) after another locked (alias, canonical). Two distinct rows always
    end in MSG49, never in a write.
  - The new key change adds no cycle. A reconnect X→Y and a create (or a second reconnect) of Y meet only on the
    `uq_project_jira_key` index entry for Y: the second writer waits for the first, and the first never waits for
    the second.
  - A cycle would need Jira to map X→Y for one Save and Y→X for the other, which can only happen during a rename.
    PostgreSQL would then abort one Save (500 MSG06, full rollback). No finding.
- **Re-read after the lock.**
  - Under READ COMMITTED each `findLink` after `lockProject` takes a new snapshot, so a waiter sees the winner's
    status and manager.
  - If the winner changed the row's key, the waiter's re-read of the alias finds nothing. The canonical lookup then
    finds the now-Active row, so the result is still MSG49.
- **Membership rules.**
  - Same manager: no statement runs.
  - No previous manager: upsert only.
  - Another previous manager: delete scoped to that user and role, then upsert.
  - A concurrent manager insert from another module would hit `uq_membership_one_manager`: 500 and rollback, as
    AC-003.9 asks.
- **Rollback completeness.**
  - Every write is inside the one `TransactionTemplate`.
  - The catch block runs after `TransactionTemplate` has rolled back and re-thrown, and only reads (`lostInsert`).
  - The audit shares the transaction: one `xmin`, and the IT restores the rows byte for byte.
- **Matching on the constraint name.** `getMostSpecificCause()` is the `PSQLException`. For SQLSTATE 23505
  PostgreSQL always puts the quoted constraint name in the primary message, also when `lc_messages` is translated.
  No other constraint name contains `uq_project_jira_key`. The `Detail` line holds key values that match
  `[A-Z][A-Z0-9_]*` or UUIDs, so it cannot contain the lowercase name by accident. `String.valueOf` guards a null
  message. This is the same pattern as `UserRoleUpdateServiceImpl.java:204-210`. The exact alternative is
  `PSQLException#getServerErrorMessage().getConstraint()`, which would tie the code to the driver. No finding.
- **SQL:** every statement uses named parameters. The ids are written as Jackson JSON through `CAST(:ids AS jsonb)`.
- **Authorization and tenancy:** unchanged from Task 3. Scope comes from `TenantScope` and the caller's own row, so
  there is no id from the request and no IDOR surface. No new log statement.

## Traceability

Run from the main repo root (HEAD `3b860534`; the Developer's uncommitted Task 5 work does not touch these ids).

```
  ✓ AC-003.1  ProjectServiceImplTest.java, ProjectReconnectIT.java
  ✓ AC-003.2  ProjectReconnectIT.java
  ✓ AC-003.3  ProjectServiceImplTest.java, ProjectReconnectIT.java
  ✓ AC-003.4  ProjectServiceImplTest.java, ProjectReconnectIT.java
  ✓ AC-003.5  ProjectServiceImplTest.java, ProjectReconnectIT.java
  ✓ AC-003.6  ProjectServiceImplTest.java, ProjectControllerTest.java, ProjectReconnectIT.java
  ✓ AC-003.7  ProjectServiceImplTest.java, ProjectControllerTest.java, ProjectReconnectIT.java
  ✓ AC-003.8  ProjectServiceImplTest.java, ProjectControllerTest.java, ProjectReconnectIT.java
  ✓ AC-003.9  ProjectServiceImplTest.java, ProjectControllerTest.java, ProjectReconnectIT.java
  ✓ AC-003.10  ProjectReconnectIT.java
trace project-integration (STORY-003): 10/10 acceptance criteria have a test
```

`bun harnessclaude/hooks/sdd.ts trace project-integration STORY-002 STORY-007` →
`29/29 acceptance criteria have a test`.

## Issues

No CRITICAL, HIGH or MEDIUM findings. Non-blocking:

1. **LOW-1 — the AC-003.8 race IT proves the outcome but not either guard on its own**
   - Location: `backend/src/test/java/vn/tsolve/project/ProjectReconnectIT.java:263-334`. The guards are
     `ProjectLinkRepository.java:124` (`AND status = 'REMOVED'`) and `ProjectServiceImpl.java:227` (re-read after
     `FOR UPDATE`). The translation is `ProjectServiceImpl.java:136-143`.
   - What happens:
     - The barrier sits in the Jira read, so the loser can reach its transaction before or after the winner commits.
     - Under READ COMMITTED a stale link would also be caught by the `UPDATE`'s own re-check of the row (0 rows), and
       a missing guard is caught by the re-read. Each guard hides the loss of the other.
   - Mutations (Test Report):
     - Dropping the status guard survives every test, unit and IT. The repository is mocked in the unit tests, so the
       SQL guard of the persistence contract ("Reconnect uses a conditional `UPDATE … WHERE status = 'REMOVED'` … No
       updated row means 409 MSG49") has no test that can fail.
     - Dropping the re-read is killed only by the Mockito tests.
     - Dropping both is killed by the IT.
     - Task 4's bullet "Prove waiter re-reads membership/state after lock" is met only with mocks. The
       `uq_project_jira_key` translation is met on PostgreSQL only when the `alias-and-create` timing happens to take
       that branch. The unit test feeds a hand-written message, not a real `PSQLException`.
   - The test is correct and stable (3/3), so this is a coverage gap, not a defect. Edge row 10 is Critical.
   - Fix (fold into the D50 hardening pass with T3 LOW-1):
     1. A repository IT: `reconnectProject` on an ACTIVE row returns 0 and changes nothing.
     2. A deterministic waiter IT: on a separate autocommit-off connection, lock the Removed row, set it ACTIVE (or
        insert a protected PM for `SECOND`) and keep the transaction open. Start the Save. Poll
        `pg_locks`/`pg_stat_activity` until it waits. Commit. Expect MSG49 and no writes.
     3. The same with an uncommitted `INSERT` of `PRCRCANON` while `PRCRALIAS` reconnects. Expect MSG49 naming the
        holder's Department, which runs the real `DuplicateKeyException` path.
2. **LOW-2 — the canonical key on reconnect (AC-003.1, AC-003.10) is proven only by a race**
   - Location: `ProjectReconnectIT.java:132-181` (deterministic, but the key is unchanged) and `:263-334` (the only
     alias reconnect). The code is `ProjectLinkRepository.java:122`.
   - Mutation `no-key-update` (drop `jira_project_key = :key`) survives the unit tests and every deterministic IT. It
     was killed only by `alias-and-create`, because in that run the reconnect committed before the create looked up
     `PRCRCANON`. If the create commits first, the reconnect is refused through the canonical link and the mutant
     passes.
   - No test checks, after an alias reconnect, the stored key, the audit's `jiraProjectKey`, or AC-003.10's lookup by
     the canonical key. This is the reconnect counterpart of the Task 2 review's MINOR-2, which was fixed for create.
   - Fix: add a non-racing alias case to `reconnectsForTheSamePreviousManagerKeepingEverything…`. Request
     `PRCRALIAS` (stub → `PRCRCANON`, already at `:129`), then assert:
     - the row's `jira_project_key` is `PRCRCANON`
     - the 200 body and the audit say `PRCRCANON`
     - `findActivePublicIdByJiraProjectKey("PRCRCANON")` contains the retained id
3. **LOW-3 — the AC-007.6 IT runs no membership write and does not compare BETA's memberships or audit**
   - Location: `ProjectReconnectIT.java:432-446`.
   - The caller is the previous manager in both databases, so no membership statement runs (AC-003.3).
   - Only BETA's `project` row and the main snapshot are compared. ACME's audit is not asserted.
   - AC-007.6 names Project, membership and audit data. A stray membership or audit write in BETA would go unnoticed.
     The risk is low: all writes share one routed connection.
   - Fix: seed a deactivated `OLD_PM` as the previous manager in both databases. Compare `snapshot(tenantB())` (it
     already covers project, membership and audit) before and after, and assert one audit in ACME.

## Assumptions made

- **AC-003.8 "owning Department name" in the translated race.** When the canonical-key update loses to a concurrent
  create, MSG49 names the Department of the committed winner, the Project that now owns the key. This follows D41,
  the design ("reading the winning Department") and AC-002.11. If the creator is in another Department, this is not
  the Removed row's own Department. I read "owning" as the owner of the conflicting Project. **Open judgment call
  (minor).**
- **LOW-1 and LOW-2 do not block.** The AC outcomes are asserted on real PostgreSQL with real concurrency, and each
  unforced branch is short and unit-tested. If the user wants Critical edge row 10 proven on the database along every
  branch, LOW-1 becomes a required fix. LOW-2 is cheap (one test case) and could go in with the Task 4 fix-up or the
  D50 hardening pass. **Open judgment call: now or in the hardening pass.**
- **AC-003.9 membership failures.** These are shown with Mockito only (rollback, never commit), as Task 4 prescribes.
  The real-database restore is shown for the audit failure, which runs after every membership write, so it covers
  the restore of a deleted former-PM row and a promoted MEMBER row.
- The accepted D50 items (T3 LOW-1 create race, INFO-2 Department at Save time, INFO-3 READ COMMITTED) and the other
  known caveats (Basic auth vs scoped tokens, KAN-119; GBR-04 end to end; external DDL) are not findings.

## Test Report

Isolated worktree at `3b860534` (`git worktree add …/scratchpad/wt-review-t4 3b860534`). The main checkout, where the
Task 5 Developer is working, was not used for Maven. Every run was from `backend/` in Git Bash, with JAVA_HOME set to
the IntelliJ JBR.

- `mvn -B -Dapi.version=1.44 -Dtest='Project*Test,*Project*Test' -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='Project*IT' verify`
  - The first attempt (09:54) failed before any IT ran, because Testcontainers found no Docker environment (the daemon
    was not reachable yet). It was an environment failure: 48 context-load errors, unit tests 231/231 green.
  - The rerun, with Docker 29.1.3 reachable:
  ```
  Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.controller.ProjectConnectionSecrecyTest
  Tests run: 68, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.controller.ProjectControllerTest
  Tests run: 121, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.service.impl.ProjectServiceImplTest
  Tests run: 231, Failures: 0, Errors: 0, Skipped: 0
  Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.ProjectConnectionIT
  Tests run: 15, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.ProjectCreationIT
  Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.ProjectDoneStatusesMigrationIT
  Tests run: 19, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.ProjectReconnectIT
  Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.review.ProjectMembershipReviewAccessIT
  Tests run: 51, Failures: 0, Errors: 0, Skipped: 0
  spotless:3.2.0:check (default) … BUILD SUCCESS
  ```
- Stability check: `ProjectReconnectIT` alone (`-Dtest=none -Dit.test=ProjectReconnectIT … verify`) ran two more times.
  Each gave `Tests run: 19, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.ProjectReconnectIT`,
  `BUILD SUCCESS`. With the targeted run, that makes 3/3 green.
- Mutations: each one was applied in the worktree, then `-Dtest=ProjectServiceImplTest -Dit.test=ProjectReconnectIT`
  was run with failures ignored, then `git checkout -- backend/src/main` restored the code (`git status` clean
  afterwards).

  | Mutation | Unit (`ProjectServiceImplTest`) | `ProjectReconnectIT` | Result |
  |----------|------|----|--------|
  | `skip-delete`: comment out `links.deleteManager(...)` (`ProjectServiceImpl.java:161`) | 2 failures (`replacesAnUnprotectedPreviousManager…_AC_003_4`, `rollsBackTheReconnectWhenAWriteFails_AC_003_9`) | 5 failures: 4 × `replacesAnIneligiblePreviousManager…` "expected 200 but was 500", plus `restoresTheRemovedProject…_AC_003_9` | killed |
  | `no-status-guard`: drop `AND status = 'REMOVED'` (`ProjectLinkRepository.java:124`) | 121/121 pass | 19/19 pass | **survives** (LOW-1) |
  | `no-reread`: `lockedLink` returns the pre-lock read (`ProjectServiceImpl.java:227`) | 5 failures (`locksAndRereads…_AC_002_17` ×2, `loserRereadsStateAndManagerAfterTheLock_AC_003_8` ×2, `reconnectsForThePreviousManager…`) | 19/19 pass | killed by mocks only (LOW-1) |
  | `no-status-guard` + `no-reread` | 5 failures (as above) | 2 failures in `competingSaves…_AC_003_8` | killed |
  | `no-key-update`: drop `jira_project_key = :key` (`ProjectLinkRepository.java:122`) | 121/121 pass | 1 failure: `competingSaves…_AC_003_8` (`alias-and-create`, two successes) | killed by the race only, timing-dependent (LOW-2) |
- `bun harnessclaude/hooks/sdd.ts trace project-integration STORY-003`: 10/10 (above). STORY-002 and STORY-007: 29/29.
- Full `mvn verify`: not rerun by me. The session log reports unit 1626 and IT 481 passing on this tree.
