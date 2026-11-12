# Task 1: Preserve legacy Done statuses as JSONB (KAN-127)

**Module:** project-integration
**Assignee:** Đặng Tấn Bình (Developer: Claude, supervising Codex CLI)
**Status:** in-progress
**Iteration:** 1

---

## Session 2026-11-12

### Working on: project-done-statuses-schema (STORY-002, AC-002.19, AC-002.20)

Branch `feat/KAN-127-project-integration-add-project`. Changeset `029-project-done-statuses.sql` (author `project`)
changes `project.done_status` from VARCHAR(100) to JSONB: a non-null scalar becomes a singleton string array, and
SQL NULL stays NULL. It is included last in `db.changelog-master.xml`, after 026. No applied changeset was edited,
and `review_range` gets no CHECK, default or backfill.

**Who wrote what.** The Codex CLI wrote the code test-first in two runs (`codex exec -s workspace-write --ephemeral`).
Claude wrote the prompts, reviewed each diff against AC-002.19/20, D19, D20, D34 and D43 and `backend/AGENTS.md`, and
ran every check.
- Codex run 1 (tests only): wrote `ProjectDoneStatusesMigrationIT` with 5 methods. Red confirmed (Codex run, JDK 24
  offline): 4 tests failed with `AssertionError: [029 changeset resource] Expecting actual not to be null`, and
  `includesChangesetLast_AC_002_19` failed with `expected: "changes/029-project-done-statuses.sql" but was:
  "changes/026-identity-app-user-keycloak-sub.sql"`.
- Codex run 2 (production): created the changeset with the design's exact text and added the one include line.
  Its sandbox could not reach Docker, so Claude started Docker Desktop and ran the IT green.
- Claude's edits: none to the code. `spotless:apply` reflowed the IT (formatting only). Nothing was reverted: both
  runs wrote only files on the task's list.

### Decisions
- The IT is a plain JUnit class with no Spring context (D43). It borrows one connection from
  `TwoTenantDatabases.main()` (the shared server's unbound database) and creates a random `mig029_<uuid>` scratch
  schema with the pre-029 `project` columns plus `public_id`. It sets `search_path`, then runs the forward and
  rollback statements parsed from `029-project-done-statuses.sql` on the classpath. In `finally` it drops the schema
  and resets `search_path`. It starts no container, does not touch `tenant_a`/`tenant_b` and runs no Liquibase.
- Fixtures: scalar `10002`, the JSON-looking texts `["10001","10002"]` and `{"id":"3"}`, and SQL NULL. Review
  ranges are NULL, 0, -1 and 3, with mixed `field_mapping` (JSON and NULL) and `status` (`ACTIVE` and `REMOVED`).
  The test snapshots every column of every row before the migration and compares after.
- The test reads master-changelog order with the JDK DOM parser, with secure processing on and external DTD and
  schema access off.
- Rollback is lossy: it keeps element zero only (`["10001","10002","10003"]` → `10001`).
  `documentsLossyRollback_AC_002_19` pins this. Deploy forward in normal operation; rollback is for development and
  scratch databases.
- **DDL owner action (not done here):** the external DDL
  `SEP490_G89-TaiLieu/SEP490_G89/Notes/DDL_01-11/02_tenant.sql` must change `done_status` to JSONB. This task does
  not edit it.

### Files
- Create: `backend/src/main/resources/db/changelog/changes/029-project-done-statuses.sql` (+7)
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml` (+1)
- Create: `backend/src/test/java/vn/tsolve/project/ProjectDoneStatusesMigrationIT.java`

### Verify
- `mvn -B -Dapi.version=1.44 -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ProjectDoneStatusesMigrationIT -Dspotless.check.skip=true verify`
  → `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS.
- `mvn -B spotless:apply && mvn -B -Dapi.version=1.44 verify` → unit (Surefire) `Tests run: 1430, Failures: 0, Errors: 0, Skipped: 0`;
  IT (Failsafe) `Tests run: 438, Failures: 0, Errors: 0, Skipped: 0` (including `SendBackApiIT` 7/7 and
  `ProjectDoneStatusesMigrationIT` 5/5); `spotless:check` passed; `BUILD SUCCESS` (12:49 min). Every IT that
  migrates a database applied the full changelog with 029.
- During the run, the working tree also held uncommitted tracker changes from a parallel BUG-203 fix (`JiraClient`,
  `TrackerProjectServiceImpl` and their tests, `docs/bug-log.md`). They are not part of this task and were not
  touched; the result above includes them.
- `bun harnessclaude/hooks/sdd.ts check project-integration` → `spec project-integration: 7 stories, 103 acceptance criteria — OK` (exit 0).
- `bun harnessclaude/hooks/sdd.ts trace project-integration STORY-002` → `2/21 acceptance criteria have a test`
  (exit 1). It shows ✓ for AC-002.19 and AC-002.20 (`ProjectDoneStatusesMigrationIT.java`) and ✗ for AC-002.1–18
  and AC-002.21. Those belong to Tasks 2–4, so STORY-002's trace can reach exit 0 only once those tasks land.

### Deviations
None from the task or design.

### Bugs
None found.

### Commit
default@180caebc — `feat(project-integration): store Project Done statuses as JSONB (KAN-127)`, committed after the user agreed (12/11). The session log itself first landed in the tracker harness commit 1b6801cf (staged together by the main session).

### Blockers
none

### Next up
Commit after the user agrees; then Task 2 `project-connection-test` (needs tracker Task 19).

## Session 2026-11-12 (attempt 2, fixing)

### Working on: project-done-statuses-schema — review findings (D47)
- LOW-1: `includesChangesetOnceAfter026_AC_002_19` asserts the 029 include appears once and after 026, so the changesets of the open pull requests can be included after it.
- INFO-2: one sentence on the rollback's edge cases added to the changeset 029 note in `04-plans/project-integration/design.md`. INFO-3 left (D43).
- Who wrote what: Codex wrote both in one run (prompt `kan127-fix-d46-d47.md`), run and reviewed by the main session.
- Verify: inside Task 3's full `mvn verify` (same working tree): unit 1600, IT 462, 0 failures, BUILD SUCCESS.
- Commit: default@db144c55

### Next up
Re-evaluate project-done-statuses-schema (attempt 2).
