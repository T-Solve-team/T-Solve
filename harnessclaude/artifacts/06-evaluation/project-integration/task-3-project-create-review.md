---
module: project-integration
feature: project-create
task: 3
verdict: pass
attempt: 1
date: 2026-11-13
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: project-integration — Task 3: Create a Project with membership and audit atomically (STORY-002, STORY-007, KAN-127)

## Summary

I reviewed commit `15b491d9` (`15b491d9^..15b491d9`, 10 backend files: 6 main, 4 test). I checked it against:
- spec STORY-002 (AC-002.1–AC-002.18, AC-002.21) and STORY-007 (AC-007.1–AC-007.8)
- the rules of AC-001.1–AC-001.7 and AC-001.19 that AC-002.1 applies
- the `createProject` row and the `CreateProjectRequest`/`Project` schemas in the spec's Interface Contracts
- Task 3 in `iteration-1/tasks.md`
- the master design's create flow, the lost-insert and audit paragraphs (`design.md:190-193`), and the edge rows
  Task 3 names (1, 6, 7, 9, 11, 22, 25–27, 31, 32, 34, 38)

I read the session log only for the commit id and the claimed test counts.

`POST /api/v1/projects` does what the spec says. The checks run in this order (`ProjectServiceImpl.java:105-137`):
1. Bean Validation (`@Valid`, before `@PreAuthorize`)
2. Workspace
3. caller and own eligible Department
4. key format
5. range (MSG01, then MSG44)
6. null, blank or empty ids
7. requested-key link
8. a fresh Jira read outside any transaction
9. canonical-key link
10. ids missing from the fresh statuses

One `TransactionTemplate` then re-reads the caller's Department and eligibility, and locks and re-reads both links. It
inserts with `ON CONFLICT (jira_project_key) DO NOTHING RETURNING public_id`, upserts the caller's `PROJECT_MANAGER`
membership, reads the stored projection, and records one audit entry built from that projection. A lost insert
re-reads the committed winner and answers MSG49 naming its Department; it never reconnects. The controller answers 201
with the `Project` body. The range is parsed as `BigDecimal` and never truncated: `1.0` is stored as 1; `2.5`, `0`, `-1`
and `2147483648` give MSG44.

The PostgreSQL IT shows the AC-002.18 claim directly. The tracker double throws if called inside a transaction, and the
project, membership and audit rows share one `xmin`, so they come from one write transaction. The AC-002.12 IT makes
the real `AuditService` fail after both inserts and finds no row left. The AC-002.17 IT changes the caller or the key
during the Jira read in five ways, and each is refused with no write.

The Task 2 review's MINOR-2 is done: `storesTheCanonicalKeyAndFindsTheCommittedProjectByIt_AC_002_8_AC_002_14`
(`ProjectCreationIT.java:200-212`) saves the alias `PCRALIAS` over HTTP. Jira answers the canonical `PCRCANON`. After
the commit, `findActivePublicIdByJiraProjectKey("PCRCANON")` returns the 201's `projectId`.

There are no CRITICAL, HIGH or MEDIUM findings. All targeted tests pass. Known accepted caveats are not findings:
- An eligible Removed Project reaches `UnsupportedOperationException` inside the transaction (500 MSG06, nothing
  written) until Task 4 builds reconnect.
- Basic auth instead of scoped tokens (KAN-119).
- GBR-04 end to end.
- The external DDL file.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | All 29 STORY-002/STORY-007 criteria hold for the create path (Stage 1 table). Reconnect (AC-003.*) is out of this task and answers 500 without writing (accepted caveat). |
| Completeness | pass | Every Task 3 criterion has a test that names it (trace 29/29 at `0dd4f8cc`). Nothing EXTRA: `ProjectSaveResult.created=false` (200) is reachable only by Task 4, and the controller test `answers200WhenTheServiceReportsAnExistingProject` prepares it without claiming an AC. `CreateProjectRequest` is unchanged. |
| Correctness | pass | `ProjectServiceImplTest` 103/103 and `ProjectControllerTest` 68/68 pass, as do `ProjectCreationIT` 15/15 and `ProjectConnectionIT` 9/9 (Task 2 unchanged). Spotless passes. `ProjectCreationIT` passed again on two more runs (Test Report). No CRITICAL finding. |
| Integration | pass | Response fields match the `Project` schema exactly (the IT compares the whole JSON body). The YAML already holds `createProject` with 201/200 (Task 2), and `ProjectResponse` matches it, so the generated client is unchanged. Audit goes through `AuditService.record` in the same transaction (one shared `xmin`). Project uses tracker only through `TrackerProjectService`. `findActivePublicIdByJiraProjectKey` is unchanged for tracker's callers. |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-002.1 AC-001.1–7/19 on create | PASS | Shares `checkScopeAndKey`/`refuseIfLinked`/`checkCanonicalLink` with Task 2 (`ProjectServiceImpl.java:139-182,241-247`). Tests: `refusesBlankCreateKey_AC_002_1`, `refusesLongCreateKey_AC_002_1`, `refusesCreateFormatBeforeLinksOrJira…`, `refusesCreateLinkedProjectsBeforeJira_AC_002_1` (4 link states), `refusesCreateCanonicalConflicts_AC_002_1`, `refusesCreateDistinctProjectsNamingCanonicalDepartment_AC_002_1`, `normalizesCreateKeyWithRootLocale_AC_002_1` |
| 2 | AC-002.2 fresh read before any write, with no prior test | PASS | `:115` always calls `readJiraProject`. `readsJiraOnceBeforeTheSingleWriteTransaction_AC_002_2_AC_002_18` (InOrder: link, tracker, `getTransaction`, insert, commit) |
| 3 | AC-002.3 NOT_FOUND/DENIED → 409 MSG48, UNAVAILABLE → 503 MSG06, no write | PASS | `:152-161`. Service tests assert no transaction and no write; controller tests check the exact envelopes |
| 4 | AC-002.4 missing/null/empty ids → 400 MSG01 `doneStatusIds` | PASS | `@NotEmpty` (HTTP) and `requireSelectedIds` (direct callers) `:220-225`. MVC test checks params `{field_name: "doneStatusIds"}` and that the service is never called |
| 5 | AC-002.5 null/blank/unknown id → MSG01 | PASS | Null/blank before the link check (`:111`); unknown only after Jira (`:117-120`). `refusesDirectCreateBlankIds_AC_002_5_AC_002_21`, `refusesUnknownCreateIdAfterJira_AC_002_5_AC_002_21` |
| 6 | AC-002.6 missing/null range → MSG01 `reviewRange` | PASS | `@NotNull` plus `:208-210`. `requiresCreateReviewRange_AC_002_6` (missing and null) |
| 7 | AC-002.7 fractional, <1, >MAX → 400 MSG44 `{}` on `reviewRange` | PASS | `:211-216` (`stripTrailingZeros().scale() > 0`, compare, then `intValueExact`). Tests: 0, -1, 2.5, 1.5, 2147483648, 1E+10 refused; 1, 1.0, 2147483647 stored; the controller passes `2.5` and `2147483648` through without truncation |
| 8 | AC-002.8 one row: own Department, public id, canonical key, JSONB ids, range, ACTIVE, NULL mapping | PASS | `ProjectLinkRepository.java:95-113`. The IT checks each column, plus the canonical key through the alias |
| 9 | AC-002.9 one PM membership in the same transaction | PASS | `upsertManager` `:115-121`. The IT finds exactly one `(CALLER, PROJECT_MANAGER)` row with the same `xmin` |
| 10 | AC-002.10 one audit entry with the exact details | PASS | `:134,231-239`, built from the stored projection. The IT checks actor id and email, action, target type and `details = CAST(? AS jsonb)` |
| 11 | AC-002.11 race: one 201, one MSG49 naming the winner, one row each | PASS (LOW-1) | `insertProject` returns empty on conflict, then `lostInsert` `:201-205`. IT variants: same Department, other Department, two aliases. Unit test `refusesLostInsertWithoutReconnectingAndRollsBack_AC_002_11` |
| 12 | AC-002.12 membership/audit failure → full rollback, 500 MSG06 | PASS | Mockito for membership and for audit failure (rollback, never commit); the IT's real audit failure leaves no Project or membership row; MVC maps both to 500 MSG06 `{}` |
| 13 | AC-002.13 201 with `Project` | PASS | `ProjectController.java:54-58`; the IT compares the whole body |
| 14 | AC-002.14 committed Project found by the canonical key | PASS | `ProjectCreationIT.java:200-212` (Task 2 MINOR-2 closed) |
| 15 | AC-002.15 dedupe in first-seen order | PASS | `LinkedHashSet` `:224`. The IT stores `["10001","10002"]` from `["10001","10002","10001"]`; unit test with 4 ids |
| 16 | AC-002.16 unreadable body, non-array ids, unreadable number → 400 MSG06 | PASS | `refusesUnreadableCreateBody_AC_002_16`: 8 bodies, including `"doneStatusIds":"10002"`, `{}`, `5`, `"reviewRange":"abc"`, `{}`. `"3"` is coerced by Jackson, as the task allows |
| 17 | AC-002.17 recheck eligibility and links in the write transaction | PASS (INFO-2, INFO-4) | `:123-129,185-199` (lock, then re-read). IT: account, role, Department null, Department inactive and key claimed, each changed during the Jira read; no write |
| 18 | AC-002.18 Jira outside any transaction; one write transaction | PASS | The fake tracker throws inside a transaction (`ProjectApiITConfiguration`); `xmin` equality (`ProjectCreationIT.java:214-227`) |
| 19 | AC-002.21 range and blank-id refusals before the link check, no Jira; unknown ids after | PASS | `checksCreateEligibilityBeforeRange…`, `checksCreateFormatBeforeRange…`, `checksCreateRangeBeforeActiveLink…`, `checksBlankCreateIdBeforeActiveLink…`, `checksActiveLinkBeforeUnknownCreateId…` |
| 20 | AC-007.1 `@PreAuthorize(Roles.PROJECT_MANAGER)` on create | PASS | `ProjectController.java:53`; `declaresCreateProjectManagerAccess_AC_007_1` |
| 21 | AC-007.2 anonymous → 401 MSG07 | PASS | `refusesAnonymousCreate_AC_007_2` |
| 22 | AC-007.3 wrong role with a valid body → 403 MSG08 | PASS | Employee and Admin tests; the two invalid-body tests are named `_AC_002_1`, not `_AC_007_3` |
| 23 | AC-007.4 ineligible caller → 403 MSG08 before Jira or write | PASS | `findCallerDepartment` SQL (`ProjectLinkRepository.java:38-54`: account ACTIVE, Department ACTIVE, role row); the IT for an inactive Department; the in-transaction recheck |
| 24 | AC-007.5 unbound Workspace → 500 MSG06, no Jira | PASS | `:140`; the IT shows the main database is untouched and no tracker call is made |
| 25 | AC-007.6 bound database only; same key in ACME and BETA | PASS | `savesTheSameKeyInEachWorkspaceDatabaseOnly_AC_007_6` (one row per tenant, main database snapshot unchanged) |
| 26 | AC-007.7 scope from `TenantScope` and `app_user.department_id` | PASS | The request has no scope field; the Department comes from the caller's row |
| 27 | AC-007.8 Workspace and eligibility before format, link and Jira | PASS | `checksCreateWorkspaceBeforeCallerDepartment_AC_007_8`, `refusesMissingCreatePrincipalBeforeInvalidSettings…`, IT `refusesAnInactiveOwnDepartmentBeforeJiraOrWrites_AC_007_4_AC_007_8` |

### Stage 2 — Quality and security pass

Checked and found sound:
- **SQL:** every statement uses named parameters, and the ids are written with `CAST(:ids AS jsonb)` from Jackson
  output, so quotes and backslashes are safe.
- **Lost insert:** `ON CONFLICT (jira_project_key) DO NOTHING RETURNING` works because `uq_project_jira_key` (`002-tenant-project-suggestion.sql:24`) is a
  full unique constraint; the IT would fail otherwise.
- **Locks:** rows are locked in the order requested key, then canonical key. A canonical key is its own canonical, so
  two Saves cannot lock the same pair in opposite orders.
- **Authorization and tenancy:** `@PreAuthorize`, the DB eligibility check, and the Workspace bound before any read.
  No id or scope comes from the request, so there is no IDOR surface. CSRF is enforced: the tests send `csrf()`.
- **Errors:** an unexpected failure (including the Task 4 placeholder) reaches `GlobalExceptionHandler.handleUnexpected`,
  which logs only the class name and answers 500 MSG06 `{}`. Project code adds no log statement, so no credential or
  Jira body can reach a log from here.
- **Exception mapping:** `DataIntegrityViolationException` from the membership insert has no specific handler and
  becomes 500 MSG06, which is what AC-002.12 asks for.

## Traceability

Run in the worktree at `0dd4f8cc`. The main checkout holds uncommitted Task 2 fix work, but its result is the same 29/29.

```
  ✓ AC-002.1  ProjectServiceImplTest.java, ProjectControllerTest.java
  ✓ AC-002.2  ProjectServiceImplTest.java
  ✓ AC-002.3  ProjectServiceImplTest.java, ProjectControllerTest.java
  ✓ AC-002.4  ProjectServiceImplTest.java, ProjectControllerTest.java
  ✓ AC-002.5  ProjectServiceImplTest.java, ProjectControllerTest.java
  ✓ AC-002.6  ProjectServiceImplTest.java, ProjectControllerTest.java
  ✓ AC-002.7  ProjectServiceImplTest.java, ProjectControllerTest.java
  ✓ AC-002.8  ProjectServiceImplTest.java, ProjectCreationIT.java
  ✓ AC-002.9  ProjectServiceImplTest.java, ProjectCreationIT.java
  ✓ AC-002.10  ProjectServiceImplTest.java, ProjectCreationIT.java
  ✓ AC-002.11  ProjectServiceImplTest.java, ProjectControllerTest.java, ProjectCreationIT.java
  ✓ AC-002.12  ProjectServiceImplTest.java, ProjectControllerTest.java, ProjectCreationIT.java
  ✓ AC-002.13  ProjectServiceImplTest.java, ProjectControllerTest.java, ProjectCreationIT.java
  ✓ AC-002.14  ProjectServiceImplTest.java, ProjectCreationIT.java
  ✓ AC-002.15  ProjectServiceImplTest.java, ProjectCreationIT.java
  ✓ AC-002.16  ProjectControllerTest.java
  ✓ AC-002.17  ProjectServiceImplTest.java, ProjectCreationIT.java
  ✓ AC-002.18  ProjectServiceImplTest.java, ProjectCreationIT.java
  ✓ AC-002.19  ProjectDoneStatusesMigrationIT.java
  ✓ AC-002.21  ProjectServiceImplTest.java
  ✓ AC-002.20  ProjectDoneStatusesMigrationIT.java
  ✓ AC-007.1  ProjectControllerTest.java
  ✓ AC-007.2  ProjectControllerTest.java
  ✓ AC-007.3  ProjectControllerTest.java
  ✓ AC-007.4  ProjectServiceImplTest.java, ProjectControllerTest.java, ProjectConnectionIT.java, ProjectCreationIT.java
  ✓ AC-007.5  ProjectServiceImplTest.java, ProjectControllerTest.java, ProjectConnectionIT.java, ProjectCreationIT.java
  ✓ AC-007.6  ProjectConnectionIT.java, ProjectCreationIT.java
  ✓ AC-007.7  ProjectServiceImplTest.java
  ✓ AC-007.8  ProjectServiceImplTest.java, ProjectCreationIT.java
trace project-integration (STORY-002, STORY-007): 29/29 acceptance criteria have a test
```

The whole module gives `51/103`. All 52 missing ids belong to later tasks: AC-003.* (Task 4, 10), AC-004.* (8),
AC-005.* (10) and AC-006.* (24). None is a Task 3 criterion.

## Issues

No CRITICAL, HIGH or MEDIUM findings. Non-blocking:

1. **LOW-1 — the race IT does not force the `ON CONFLICT` lost-insert path**
   - Location: `backend/src/test/java/vn/tsolve/project/ProjectCreationIT.java:229-281`.
   - Why it is not forced:
     - The `CyclicBarrier` sits in the Jira read, before the write transaction. Both Saves pass the local check, but
       after the barrier the loser can reach the in-transaction recheck either before the winner commits (then its
       `INSERT … ON CONFLICT DO NOTHING` waits and returns empty: the `lostInsert` path) or after it (then
       `refuseIfLinked` refuses the committed row).
     - In the `aliases` variant, the loser can even be refused by the canonical check before its transaction starts.
   - Both paths give the same 201/409 and row counts, so the test is correct and stable, but which branch runs is
     timing-dependent. The `lostInsert` branch (`ProjectServiceImpl.java:130-131,201-205`) is proven only with mocks
     (`refusesLostInsertWithoutReconnectingAndRollsBack_AC_002_11`). Nothing shows against PostgreSQL that the
     `RETURNING` really comes back empty and that the winner is then visible under READ COMMITTED. Edge row 9 is
     Critical.
   - Fix: add one deterministic IT case:
     1. On a separate `DataSource` connection with autocommit off, insert a `PCRLOST` row for the other Department and
        keep the transaction open.
     2. Start the Save in another thread.
     3. Wait (polling `pg_stat_activity` / `pg_locks`, no sleep) until its backend waits on a lock.
     4. Commit the holding connection.
     5. Expect 409 MSG49 naming the holder's Department, with no membership or audit from the Save.
2. **INFO-2 — a caller moved to another active Department during the Jira read creates the Project there**
   - Location: `ProjectServiceImpl.java:123-130`. The code is explicit about this:
     `usesDepartmentReReadInsideTransaction_AC_002_17` asserts it.
   - The in-transaction recheck uses the re-read Department for the link rules, the insert and the audit, so the
     result is consistent. AC-002.8 says "the caller's Department", and at write time that is the new one.
   - Fix: none, unless the user wants a Department change during the read to refuse the Save (see Assumptions).
3. **INFO-3 — the lost-insert read relies on READ COMMITTED**
   - Location: `ProjectServiceImpl.java:72,201-205`.
   - `lostInsert` re-reads the winner in the same transaction after `ON CONFLICT DO NOTHING`. That sees the
     committed row only under PostgreSQL's default READ COMMITTED. Under REPEATABLE READ the insert itself fails
     with a serialization error, so the answer would be 500 instead of MSG49. The template sets no isolation, so the
     default applies today.
   - Fix (optional): `writeTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED)`, or a
     one-line comment, so a later default change cannot silently turn MSG49 into 500.
4. **INFO-4 — the eligibility recheck is a plain read, not a lock**
   - Location: `ProjectServiceImpl.java:123-124`, `ProjectLinkRepository.java:38-54`.
   - A deactivation or role removal that commits after the recheck but before the Save's commit does not stop the
     Save. AC-002.17 asks only for a recheck when the transaction begins, and the window is a few statements wide.
   - Fix: none required. `FOR SHARE` on the `app_user` row would close the window if ever needed.

## Assumptions made

- **INFO-2:** I read AC-002.17 ("recheck the caller's eligibility") together with AC-002.8 ("the caller's
  Department") to mean the Department current at write time, after the recheck. The other reading is that a
  Department change during the Jira read must refuse the Save (403 or 409). The spec does not settle this. **Open
  judgment call (minor).**
- **AC-002.16 coercion:** `"reviewRange":"3"` and numeric ids such as `[10002]` are accepted through Jackson's default
  coercion, as the task says ("respect Jackson coercion instead of inventing stricter scalar rejection"). The spec's
  example of an unreadable value is `"abc"`, which gives MSG06.
- **LOW-1** does not block: the AC-002.11 outcome is asserted on real PostgreSQL with real concurrency, and the code
  path that is not forced is short and unit-tested. If the user wants each Critical edge row proven on the database
  along every branch, LOW-1 becomes a required fix.
- Known accepted caveats (reconnect still 500 until Task 4; Basic auth vs scoped tokens, KAN-119; GBR-04 end to end;
  external DDL) are not findings.

## Test Report

Isolated worktree at `0dd4f8cc` (`git worktree add … 0dd4f8cc`; the main checkout, where a developer is working, was
not used). Run from `backend/` in Git Bash with JAVA_HOME set to the IntelliJ JBR.

- `mvn -B -Dapi.version=1.44 -Dtest='JiraClientTest,TrackerProjectServiceImplTest,ProjectServiceImplTest,ProjectControllerTest' -Dsurefire.failIfNoSpecifiedTests=false test`
  ```
  Tests run: 68, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.controller.ProjectControllerTest
  Tests run: 103, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.service.impl.ProjectServiceImplTest
  Tests run: 61, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.tracker.client.JiraClientTest
  Tests run: 24, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.tracker.service.impl.TrackerProjectServiceImplTest
  Tests run: 256, Failures: 0, Errors: 0, Skipped: 0
  BUILD SUCCESS
  ```
- `mvn -B -Dapi.version=1.44 -Dit.test='ProjectDoneStatusesMigrationIT,ProjectCreationIT,ProjectConnectionIT' -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false verify`
  ```
  Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.ProjectConnectionIT
  Tests run: 15, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.ProjectCreationIT
  Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.ProjectDoneStatusesMigrationIT
  Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
  spotless:check (default) … BUILD SUCCESS
  ```
- `mvn -B spotless:check`: `BUILD SUCCESS`
- Flakiness check: `ProjectCreationIT` alone, two more runs (`-Dit.test=ProjectCreationIT -Dtest=none … verify`), each `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.ProjectCreationIT`, `BUILD SUCCESS`; the race and recheck cases are stable over 3 runs
- `bun harnessclaude/hooks/sdd.ts trace project-integration STORY-002 STORY-007`: 29/29 (above).
- Full `mvn verify`: not rerun by me. The session log reports unit 1600 and IT 462 passing on this tree.
