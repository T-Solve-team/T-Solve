# Task 4: Reconnect the same Removed Project safely (KAN-127)

**Module:** project-integration
**Assignee:** Đặng Tấn Bình (Developer: Claude, D49, no Codex)
**Status:** in-progress
**Iteration:** 1

---

## Session 2026-11-13

### Working on: project-reconnect (STORY-003, STORY-002 fresh validation, STORY-007)

Branch `feat/KAN-127-project-integration-add-project`, from HEAD `1a0795ac`. `POST /api/v1/projects` now reconnects an
eligible Removed Project of the caller's Department instead of answering 500. Everything before the write transaction
is unchanged from Task 3: Workspace, caller, Department, key format, range and blank ids, the requested-key link,
the fresh Jira read outside any transaction, the canonical-key link and the ids checked against the fresh statuses.
Inside the one `TransactionTemplate`, the service re-reads the caller's Department, then locks and re-reads both links
(`recheckLinks`/`lockedLink` from Task 3). For a Removed link that is still eligible it then:
1. Runs `reconnectProject`: one conditional `UPDATE project SET status='ACTIVE', jira_project_key=:canonical,
   done_status, review_range WHERE public_id=:id AND status='REMOVED'`. Zero rows → 409 MSG49 naming the owning
   Department.
2. Changes memberships only when the caller is not the previous manager. With another previous manager it calls
   `deleteManager` (only that user's `PROJECT_MANAGER` row) and then `upsertManager` for the caller (`ON CONFLICT ...
   DO UPDATE`, so a caller who is already a MEMBER is promoted with no second row). With no previous manager it only
   upserts. The same previous manager → no membership statement at all.
3. Reads the saved projection and records one audit entry, `TRACKER_CONFIGURATION_CHANGE` / `PROJECT` / the retained
   public id, with details `{operation: "RECONNECT", jiraProjectKey, departmentId, doneStatusIds, reviewRange}`.
4. Returns `ProjectSaveResult(saved, false)`, which the Task 3 controller already answers as 200.

Public id, `project_id`, Department, tickets, other memberships and `field_mapping` are not touched by any statement.
A `DuplicateKeyException` on `uq_project_jira_key` (only the reconnect's canonical-key update can raise it, when a
concurrent create inserts that key) is caught outside the transaction, after the rollback, and becomes MSG49 naming the
committed winner's Department (D41). Any other constraint propagates as 500 MSG06. The contract already had the 200
response for `createProject`, so neither the contract nor the generated client changed.

**Who wrote what.** Claude wrote all code and tests (Codex is out of quota, D49).

**Red step.** The two repository methods came first, so the tests could compile; the service kept Task 3's
`UnsupportedOperationException`. Unit run: 189 tests, 9 failures, all in the new reconnect success, rollback and
translation tests, which failed by assertion (`UnsupportedOperationException` instead of a result or MSG49). The new
refusal tests (AC-003.7, the re-read loser, fresh-read failures, recheck) passed, because those paths end before the
placeholder. `ProjectReconnectIT`: 19 tests, 11 failures (200 expected, 500 received; the races had no success; the
AC-003.9 test saw `UnsupportedOperationException` instead of the audit failure). The 8 refusal cases passed.

### Decisions
- One `UPDATE` sets the canonical key together with the status and settings, where the design has two statements
  (`reconnectProject`, then `canonicaliseKey` only if the key changed). When the key is unchanged, setting it again
  changes nothing, so the second statement and its condition add nothing. The key still persists in the same
  transaction (AC-001.22, AC-003.1).
- The previous manager is `Link.managerId()` from the locked re-read, so a waiter in a race sees the winner's state:
  ACTIVE or a new protected manager → MSG49 from `refuseIfLinked`, and the conditional `UPDATE` is never reached.
- Unique-violation translation filters on the constraint name in the message, the same way as
  `UserRoleUpdateServiceImpl`/`UserStatusUpdateServiceImpl` do for `uq_membership_one_manager`. The winner is read
  with the existing `lostInsert` helper.
- AC-003.9: the membership failures (delete, upsert, and an upsert `DuplicateKeyException` on another constraint) are
  shown with the repository mock. The audit failure is shown in the IT with the security context cleared during the
  Jira read (the AC-002.12 technique). No new container, `@MockBean` or `@TestPropertySource`:
  `ProjectReconnectIT` has the same properties and `@Import` as `ProjectCreationIT` and reuses the cached context
  (19 tests in about 1.3 s).
- The race IT `alias-and-create` lets one Save reconnect `PRCRALIAS` onto Jira's canonical `PRCRCANON` while the
  other creates `PRCRCANON`. The order is not forced, so either the lost insert or the unique-violation translation
  decides; the test accepts one 200 or 201 and one MSG49 with one Active row, one manager and one audit. The
  translation itself is unit-tested deterministically (`translatesALostCanonicalKeyUpdateAfterRollback_AC_003_8`).
  Barrier only, no sleeps; `*Test` classes do not wait on the clock.
- `ProjectService.create`'s Javadoc now also names the reconnect (doc only).

### Tests added (name → AC)
`ProjectServiceImplTest` (Mockito):
- `reconnectsForThePreviousManagerWithoutTouchingMemberships_AC_003_1_AC_003_3_AC_003_5_AC_003_6`: Jira before the
  transaction, lock then re-read, conditional update with deduplicated ids, RECONNECT audit, commit; no membership
  statement, no insert.
- `replacesAnUnprotectedPreviousManagerBeforeUpsertingTheCaller_AC_003_4` (with/without previous manager): delete
  before upsert.
- `storesJiraCanonicalKeyOnTheReconnectedRow_AC_003_1`
- `refusesForeignProtectedOrActiveProjectsBeforeJira_AC_003_7` (3 cases): no Jira call, no transaction.
- `refusesWhenTheConditionalReconnectMatchesNoRow_AC_003_8`, `loserRereadsStateAndManagerAfterTheLock_AC_003_8`
  (activated / new manager), `translatesALostCanonicalKeyUpdateAfterRollback_AC_003_8`
- `rollsBackTheReconnectWhenAWriteFails_AC_003_9` (delete / upsert with another constraint / audit)
- `refusesReconnectWhenTheFreshJiraReadFails_AC_002_3_AC_003_1` (NOT_FOUND, DENIED, UNAVAILABLE),
  `refusesReconnectWithAnUnknownIdOrInvalidRange_AC_002_5_AC_002_7_AC_003_1`,
  `rechecksReconnectEligibilityInsideTheTransaction_AC_002_17_AC_003_1`
- Removed: the Task 3 placeholder test `doesNotPretendToReconnectAnEligibleRemovedProject`.

`ProjectControllerTest` (renamed only, no behaviour change): `answers200WithTheReconnectedProject_AC_003_6` (was
`answers200WhenTheServiceReportsAnExistingProject`), `rendersCreateLostInsertEnvelope_AC_002_11_AC_003_7_AC_003_8`,
`rendersCreateWriteFailure_AC_002_12_AC_003_9`.

`ProjectReconnectIT` (new, real PostgreSQL, ACME/BETA tenant databases):
- `reconnectsForTheSamePreviousManagerKeepingEverything_AC_003_1_AC_003_2_AC_003_3_AC_003_5_AC_003_6_AC_003_10`:
  exact 200 body, same `project_id`/Department/key/`field_mapping`, memberships identical including `date_added`,
  ticket kept, stored settings, one audit, lookup returns the retained id (row 16).
- `replacesAnIneligiblePreviousManagerKeepingOtherMembers_AC_003_4_AC_002_18` (none, deactivated, moved, role removed,
  caller already MEMBER): exactly caller PM plus the unchanged MEMBER row; project, membership and audit rows share
  one `xmin` (row 17).
- `refusesProtectedForeignOrActiveProjectsBeforeJiraOrWrites_AC_003_7` (row 36).
- `competingSavesCommitOneReconnectOrCreateAndRefuseTheOther_AC_003_8` (same key, alias to the same row, alias against
  a create of the canonical key) (row 10).
- `restoresTheRemovedProjectAndMembersWhenTheAuditFails_AC_003_9`: row JSON and all three memberships (former PM,
  MEMBER caller, MEMBER) restored, no audit (row 23).
- `rechecksEligibilityAndLinkBeforeReconnecting_AC_002_17_AC_003_1` (role removed, manager claimed, activated during
  the Jira read) (row 11).
- `refusesReconnectAfterAFailedFreshJiraCheck_AC_002_2_AC_002_3_AC_002_5_AC_003_1` (NOT_FOUND, unknown id) (row 32).
- `reconnectsOnlyInTheBoundWorkspaceDatabase_AC_007_6` (row 27). The audit assertions cover row 34.

### Files
- Modify: `backend/src/main/java/vn/tsolve/project/service/impl/ProjectServiceImpl.java` (+42 −13),
  `repository/ProjectLinkRepository.java` (+26: `reconnectProject`, `deleteManager`),
  `service/ProjectService.java` (+4 −1, Javadoc)
- Test: `backend/src/test/java/vn/tsolve/project/service/impl/ProjectServiceImplTest.java` (+197 −6),
  `controller/ProjectControllerTest.java` (+3 −3, renames)
- Test (create): `backend/src/test/java/vn/tsolve/project/ProjectReconnectIT.java` (580 lines)
- Unchanged: identity, the JPA entity and repository, `ProjectApiITConfiguration`, the contract and the generated
  client.

### Verify
Backend, from `backend/` (JAVA_HOME = IntelliJ jbr 21):
- `mvn -B spotless:apply`, then
  `mvn -B -Dapi.version=1.44 -Dtest='Project*Test,*Project*Test' -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='Project*IT' verify`
  → Surefire `Tests run: 231, Failures: 0, Errors: 0, Skipped: 0` (ProjectServiceImplTest 121,
  ProjectControllerTest 68, ProjectConnectionSecrecyTest 8, and others); Failsafe
  `Tests run: 51, Failures: 0, Errors: 0, Skipped: 0` (ProjectReconnectIT 19, ProjectCreationIT 15,
  ProjectConnectionIT 9, ProjectDoneStatusesMigrationIT 5, ProjectMembershipReviewAccessIT 3); `spotless:check`
  passed; `BUILD SUCCESS`.
- Stability: `-Dit.test='ProjectReconnectIT,ProjectCreationIT'` 3 more times → 19 + 15 green each run.
- Full `mvn verify`: left to the main session.

Harness, from the repo root:
- `bun harnessclaude/hooks/sdd.ts trace project-integration STORY-003` →
  `trace project-integration (STORY-003): 10/10 acceptance criteria have a test` (exit 0).
- `bun harnessclaude/hooks/sdd.ts trace project-integration STORY-002 STORY-007` →
  `trace project-integration (STORY-002, STORY-007): 29/29 acceptance criteria have a test` (exit 0).

Secrets: `gitleaks detect --no-git --redact --no-banner --source <file>` on each of the 6 changed or new files →
`no leaks found` for every file.

### Deviations
- One `UPDATE` instead of the design's separate `canonicaliseKey` statement (see Decisions); same effect, same
  transaction.
- `ProjectService.java` is not in Task 4's file list; only its Javadoc changed, so it no longer says "new Project".
- `sdd.ts feature … implementing` is rejected (`unknown feature status 'implementing'`; the feature statuses are
  pending, in-progress, evaluating, passing, fixing, blocked). The feature was set to `in-progress`, the implement
  skill's status.

### Bugs
None found.

### Commit
default@560a7dae — `feat(project-integration): reconnect a Removed Project in place with its manager and audit (KAN-127)`, committed after the user agreed (13/11).

### Blockers
none

### Next up
Full `mvn verify` and commit after the user agrees; then Task 5 `project-list`.

### Full verify (main session)
- `mvn -B spotless:apply && mvn -B -Dapi.version=1.44 verify` → unit `Tests run: 1626, Failures: 0, Errors: 0, Skipped: 0`;
  IT `Tests run: 481, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS`.
