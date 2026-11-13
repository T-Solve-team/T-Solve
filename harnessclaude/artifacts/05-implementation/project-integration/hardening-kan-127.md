# Hardening pass: KAN-127 test hardening (D50, D51, D52, D54)

**Module:** project-integration (and tracker STORY-013)
**Assignee:** Đặng Tấn Bình (Developer: Claude, D49, no Codex)
**Status:** in-progress (feature statuses unchanged: all `passing`)
**Iteration:** 1

---

## Session 2026-11-13

### Working on: test-hardening pass (STORY-002, STORY-003, STORY-004, tracker STORY-013)

Branch `feat/KAN-127-project-integration-add-project`, from HEAD `7465d274`. Backend only. Only items 3 and 8 change
`src/main`. Every other item adds tests. One shared IT context (D43): no new container, `@MockBean`,
`@TestPropertySource` or `@DirtiesContext`. The waiter ITs borrow a second connection from the existing tenant pool
(`TwoTenantDatabases.tenantA().getDataSource()`).

**Shared helper.** `ProjectApiITConfiguration.awaitBlockedBy(JdbcTemplate db, Connection holder)` reads the holder's
`pg_backend_pid()`. It then polls `pg_stat_activity WHERE holderPid = ANY(pg_blocking_pids(pid))` every 20 ms, for at
most 10 s, and returns as soon as some session is blocked by the holder. There is no blind sleep, and the test commits
only once the Save really waits. On a timeout it throws, and the holder rolls back in `finally`.

#### 1. T19 LOW-9: log of the catch-all (tracker, AC-013.12)
- Test: `TrackerProjectServiceImplTest.logsOnlyTheExceptionClassForAnUnexpectedClientFailure_AC_013_12`. The mocked
  client throws `IllegalStateException("planted@mail.test planted-token-42 ftp://planted.example")`. Using
  `CapturedOutput` (the class's existing style), the test asserts that the output contains
  `Jira project lookup ITSUP: UNAVAILABLE (IllegalStateException)` and none of the planted strings, nor `planted`.
- Mutation M1 (log `e.getMessage()` instead of the class name): **killed** by this test.

#### 2. T3 LOW-1: forced `ON CONFLICT` lost insert (AC-002.11)
- Test: `ProjectCreationIT.refusesALostInsertNamingTheCommittedWinnerWithoutWrites_AC_002_11`.
  1. A holder connection with autocommit off inserts `PCRLOST` for `PCR Support` and does not commit.
  2. The Save starts on another thread. It passes the pre-transaction check (the row is uncommitted), then its
     `INSERT … ON CONFLICT DO NOTHING` waits.
  3. `awaitBlockedBy` returns, and the holder commits.
  4. Result: 409 MSG49 with `department_name = PCR Support` and field `jiraProjectKey`. Exactly one `PCRLOST` row
     (the holder's), no membership for the caller, the audit count unchanged, and one Jira call.
- This runs the real `RETURNING`-empty → `lostInsert` path on PostgreSQL, which before was shown only with mocks.

#### 3. T3 INFO-3: READ COMMITTED pinned on the write template (`src/main`)
- `ProjectServiceImpl` constructor: `writeTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED)`.
  A comment says why: the lost insert's re-read must see the winner committed after `ON CONFLICT` waited, and the
  reconnect re-reads the row after its lock. Under REPEATABLE READ both would become 500 instead of MSG49.
- Unit test (red first, 1 failure): `ProjectServiceImplTest.writesUnderReadCommittedSoTheLostInsertReReadSeesTheWinner_AC_002_11`
  checks the `TransactionDefinition` passed to `getTransaction`.
- Behaviour change: none today, because PostgreSQL's default is already READ COMMITTED. This guards against a change
  of the default.
- Mutations:
  - M2 (`ISOLATION_REPEATABLE_READ`): **killed** by the unit test. Run IT-only, it is also killed by
    `refusesALostInsert…_AC_002_11`, `refusesAReconnectThatWaited…_AC_003_8` and both race ITs.
  - M2b (line removed): **killed** by the unit test.

#### 4. D51 A2-2: the secrecy test must see T-Solve's own logs
- `ProjectConnectionSecrecyTest`: "logs were captured" now requires at least one event from a `vn.tsolve.tracker`
  logger (`anyMatch(… startsWith("vn.tsolve.tracker"))`). The `sun.net.www` exclusion and `application.yml` were not
  touched; A2-1 is still a separate quick change.
- Mutation M3 (the `TrackerProjectServiceImpl` logger renamed to `com.example.Elsewhere`): **killed** in all 8
  parameter cases. Before this change, only the WireMock lines would have satisfied the check.

#### 5. T4 LOW-1: the reconnect guards, each on its own (AC-003.8)
- Repository IT: `ProjectReconnectIT.conditionalReconnectUpdatesNothingOnAnActiveRow_AC_003_8`. It autowires
  `ProjectLinkRepository` from the shared context. On an ACTIVE row, `reconnectProject` returns 0 and the row is
  unchanged (`row_to_json` before and after).
- Waiter IT: `ProjectReconnectIT.refusesAReconnectThatWaitedForAConcurrentActivation_AC_003_8`.
  1. A holder takes `SELECT … FOR UPDATE` on the Removed `PRCRWAIT` row.
  2. The Save starts and blocks in `lockProject`.
  3. `awaitBlockedBy` returns, and the holder sets the row ACTIVE and commits.
  4. Result: 409 MSG49 naming `PRC Operations`. Settings are kept (range 9, `["10000"]`), memberships are unchanged,
     and there is no audit.
- Mutations:
  - M4 (drop `AND status = 'REMOVED'`): **killed** by `conditionalReconnectUpdatesNothingOnAnActiveRow_AC_003_8`.
    It survived every test before.
  - M5 (drop the re-read after `FOR UPDATE`): survives the ITs, because the conditional `UPDATE` then returns 0 →
    MSG49. This is expected: each guard hides the loss of the other. It is **killed** by the unit tests
    `locksAndRereadsRequestedOrCanonicalLinkInsideTransaction_AC_002_17`, `loserRereadsStateAndManagerAfterTheLock_AC_003_8`
    and `reconnectsForThePreviousManagerWithoutTouchingMemberships_…`.
  - M5b (both dropped): **killed** by the waiter IT, the repository IT and the race IT.

#### 6. T4 LOW-2: canonical key on a non-racing reconnect (AC-003.1, AC-003.10)
- Test: `ProjectReconnectIT.reconnectsAnAliasUnderJirasCanonicalKey_AC_003_1_AC_003_10`. A Removed `PRCRALIAS` row,
  with the fake tracker answering `PRCRCANON`. It asserts:
  - 200 with the same `projectId`, `jiraProjectKey = PRCRCANON` and `status = ACTIVE`
  - the stored key is `PRCRCANON`
  - the RECONNECT audit's `jiraProjectKey` is `PRCRCANON`
  - `findActivePublicIdByJiraProjectKey("PRCRCANON")` returns the id
- Mutation M6 (drop `jira_project_key = :key`): **killed** deterministically by this test (also by the race IT, as
  before).

#### 7. T4 LOW-3: AC-007.6 reconnect with a membership write
- `ProjectReconnectIT.reconnectsOnlyInTheBoundWorkspaceDatabase_AC_007_6`, rewritten:
  - The previous manager is `OLD_PM`, DEACTIVATED in both ACME and BETA, so the ACME reconnect deletes the old PM
    row and upserts the caller.
  - The test compares a full `snapshot(tenantB())` (project, membership, audit) and the main database before and
    after.
  - It asserts that ACME's memberships are exactly `CALLER` PM plus `MEMBER`, and exactly one RECONNECT audit in ACME
    (`assertReconnectAudit`).
- No production mutation applies: the database is chosen by the routing data source, which is test infrastructure.

#### 8. T5 LOW-1 / D54: `project.status` through `ProjectStatus` (`src/main`)
- The enum exists: `vn.tsolve.project.entity.ProjectStatus {ACTIVE, REMOVED}`. No new public type.
- `ProjectLinkRepository`: a private `status(String stored)` returns `ProjectStatus.valueOf(stored).name()`. It is
  used by `listProjects` and `readProject` (Task 3). The DTOs keep `String status`, so the contract does not change.
- Tests (red first):
  - `ProjectListIT.failsLoudlyOnAnUnknownStoredStatusInsteadOfSendingOrHidingIt_AC_004_1`: a `DISABLED` row
    inserted by SQL. Before the fix: 200. After: 500 MSG06 with empty params and no `projects`.
  - `ProjectCreationIT.refusesToReadAProjectWithAnUnknownStoredStatus_AC_002_13`: a repository-level test of
    `readProject`, because no create path can write a third status. The root cause is `IllegalArgumentException`
    mentioning `DISABLED`. The `@Repository` translation wraps it in `InvalidDataAccessApiUsageException`, which
    still reaches the catch-all as 500 MSG06.
- Mutations:
  - M8a (raw string back): **killed** by both tests.
  - M8b (hide the row with `AND p.status IN ('ACTIVE','REMOVED')`): **killed** by the list IT.

### Files
- main: `backend/src/main/java/vn/tsolve/project/service/impl/ProjectServiceImpl.java`,
  `backend/src/main/java/vn/tsolve/project/repository/ProjectLinkRepository.java`
- test: `ProjectApiITConfiguration.java`, `ProjectCreationIT.java`, `ProjectListIT.java`, `ProjectReconnectIT.java`,
  `controller/ProjectConnectionSecrecyTest.java`, `service/impl/ProjectServiceImplTest.java` (all under
  `backend/src/test/java/vn/tsolve/project/`), and
  `backend/src/test/java/vn/tsolve/tracker/service/impl/TrackerProjectServiceImplTest.java`

### Verify
Git Bash, `backend/`, JAVA_HOME = IntelliJ JBR, Docker 29.1.3:
- `mvn -B spotless:apply`, then `mvn -B -q spotless:check`: exit 0
- `mvn -B -Dapi.version=1.44 -Dtest='Project*Test,*Project*Test,TrackerProjectServiceImplTest,JiraClientTest' -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='Project*IT' verify`,
  3 consecutive runs, each:
  ```
  [INFO] Tests run: 309, Failures: 0, Errors: 0, Skipped: 0
  [INFO] Tests run: 67, Failures: 0, Errors: 0, Skipped: 0
  [INFO] BUILD SUCCESS
  ```
- `bun harnessclaude/hooks/sdd.ts trace project-integration`: `103/103 acceptance criteria have a test`
- `bun harnessclaude/hooks/sdd.ts trace tracker STORY-013`: `13/13 acceptance criteria have a test`. It also warns
  `ids in tests but not in the spec: AC-001.72`, which was there before; no test added here cites it.
- After the mutations, `git diff --stat backend/src/main` shows only the two intended files (+12 −2).
- The full `mvn verify` is left to the main session.

### Deviations
- Item 6 is a new test method rather than an extra case inside `reconnectsForTheSamePreviousManagerKeepingEverything…`,
  so each test proves one thing.
- Item 8's `readProject` check is repository-level. The create flow always writes ACTIVE, so no HTTP path can reach
  a bad status there.
- The waiter polling lives in one static helper in `ProjectApiITConfiguration`, shared by both waiter ITs.
- No bug found: no `docs/bug-log.md` row.


### Next up
The main session runs the full verify and commits (items 3 and 8 change no behaviour today; see the proposed header).

### Main session additions (D56, Task 7 review)
- LOW-1: `AddProjectModalErrors.test.tsx` gains `AC-006.19 leaves a 401 to the shared MSG07 dialog with no toast of its own`.
  Mutation: removing `|| error.status === 401` from `showError` → this test fails (1 failed | 17 passed); restored.
- LOW-2: `JiraIntegrationPage.test.tsx` — the row-order test is renamed to AC-005.2 and AC-005.3 is restored on the first
  test (the only one checking the manager's `fullName`); D55's wording corrected.

### Full verify (main session)
- Backend `mvn -B spotless:apply && mvn -B -Dapi.version=1.44 verify` → unit `Tests run: 1643, Failures: 0, Errors: 0,
  Skipped: 0`; IT `Tests run: 497, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS`.
- Frontend: format:check, lint (1 pre-existing warning in `TicketListFilters.tsx`), typecheck, build exit 0;
  `Test Files 90 passed (90)`, `Tests 1556 passed (1556)`; `generate:api` → no diff.

### Commit
default@9f47fd34 — `fix(project-integration): fail on unknown Project status, pin READ COMMITTED, harden ITs (KAN-127)`, committed after the user agreed (13/11).
