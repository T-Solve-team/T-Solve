---
module: project-integration
feature: project-done-statuses-schema
task: 1
verdict: pass
attempt: 2
date: 2026-11-13
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: project-integration — Task 1: Store Project Done statuses as JSONB (STORY-002, KAN-127)

## Summary

Reviewed commit `180caebc` (`180caebc^..180caebc`, 3 backend files) against spec AC-002.19, AC-002.20 and the
"Changeset 029" paragraph (`spec.md:97,99,429`), the design's Data Model section and edge rows 28 and 29, decisions
D7, D19, D20, D34 and D43, and `backend/AGENTS.md` §7. Codex CLI (read-only, ephemeral) gave a second opinion. I
checked each Codex finding myself and kept only the ones I confirmed.

The changeset is the design's text, character for character. The forward cast turns every non-null VARCHAR into a
one-element JSONB array holding a string, and keeps SQL NULL as NULL. It is included last, and no applied changeset
is touched. Nothing reads `done_status` today:
- no entity maps it (`Project` maps only id, `public_id`, key and status, so `ddl-auto: validate` is unaffected)
- no native SQL, seeder or test fixture uses it
- jira-app has no reference to it

The IT runs the changeset's own statements against legacy fixtures on real PostgreSQL. It would fail for the
plausible wrong casts: `::jsonb`, `to_jsonb`, or a missing NULL branch. The targeted tests pass, and so does the full
verify. No CRITICAL finding. All findings are LOW or INFO.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-002.19 and AC-002.20 hold (table below). The forward cast keeps each legacy value verbatim (empty, padded, quoted and JSON-looking text stay one string each; probe below). |
| Completeness | pass | Both ACs have tests that assert them. Nothing EXTRA: no CHECK, default, backfill, index or entity change. The external DDL (`SEP490_G89/Notes/DDL_01-11/02_tenant.sql:57`) still says `VARCHAR(100)`; the task leaves that to the DDL owner (design line 126). |
| Correctness | pass | 5/5 migration IT, 7/7 `SendBackApiIT` and spotless pass in an isolated worktree at `180caebc`. The full `mvn verify` log shows 1430 unit and 438 IT passing. No CRITICAL findings. |
| Integration | pass | No current reader or writer of `done_status` in backend main, backend test or jira-app (grep of the whole repo outside `harnessclaude/`, `node_modules`, `target`). Later tasks' SQL (design lines 141-176) already writes `CAST(:ids AS jsonb)`. `SendBackApiIT` (review range 0 and -1) stays green on the migrated schema. |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-002.19 each non-null legacy scalar becomes a JSONB singleton string array, SQL NULL stays NULL, no other column, row or reference changes | PASS | `029-project-done-statuses.sql:4-5`. `preservesLegacyRows_AC_002_19` (IT:31-80): the column type is `jsonb`; for `SCALAR` `10002`, `ARRAY_TEXT` `["10001","10002"]` and `OBJECT_TEXT` `{"id":"3"}`, element 0 equals the old text, the length is 1 and the element type is `string`; `SQL_NULL` stays SQL NULL; every other column of every row (`project_id`, `public_id`, `department_id`, key, `field_mapping`, `review_range`, `status`) is equal before and after. `includesChangesetLast_AC_002_19` (IT:84-103): 029 is the last include and appears once. `rollsBackAndReapplies_AC_002_19` and `documentsLossyRollback_AC_002_19`: rollback restores VARCHAR(100) and the exact values, reapplying gives the same arrays, and several values keep only element zero. |
| 2 | AC-002.20 `review_range` stays nullable, no new CHECK, default or backfill | PASS | The changeset touches only `done_status`. `preservesUnrestrictedReviewRanges_AC_002_20` (IT:107-166): NULL, 0, -1 and 3 are unchanged; 0, -1 and an omitted value insert after the migration; `column_default` is NULL and `is_nullable` is `YES`; the table has no check constraint. `SendBackApiIT` 7/7 on the full changelog. |

Design edge rows: row 28 (Critical) is covered for `project`'s own columns. Tickets and memberships are not in the
scratch table, but a type change on a column no key or foreign key uses cannot touch them, and every IT that seeds
them runs on the migrated schema. Row 29 (High) is covered by the AC-002.20 test and `SendBackApiIT`.

## Traceability

```
  ✓ AC-002.19  backend/src/test/java/vn/tsolve/project/ProjectDoneStatusesMigrationIT.java
  ✓ AC-002.20  backend/src/test/java/vn/tsolve/project/ProjectDoneStatusesMigrationIT.java
trace project-integration (STORY-002): 2/21 acceptance criteria have a test
```

The other 19 STORY-002 criteria belong to later tasks. Task 1 names only AC-002.19 and AC-002.20, and both have a
test.

## Issues

No CRITICAL findings. Non-blocking:

1. **LOW-1 — the "029 is last" test will break the next legitimate changeset** (Claude and Codex independently)
   - `ProjectDoneStatusesMigrationIT.java:99-101` asserts that 029 is the last include in the master changelog,
     forever.
   - Open pull requests #202 and #206 add `027-workspace-*` and `028-workspace-*`, and #203 and #178 add other `027`
     changesets (D19). Whichever merges after KAN-127 appends its include after 029, and this IT then fails on that
     branch, owned by another team.
   - The spec's "included last" (`spec.md:429`) is about placement when 029 is added: after 026 and every applied
     changeset.
   - Fix: assert that 029 appears once and after every include present today (for example, after
     `changes/026-identity-app-user-keycloak-sub.sql`), not that it is the last element.
2. **INFO-2 — the rollback limits beyond "several values" are not written down** (Claude, probe on PostgreSQL 16)
   - `029-project-done-statuses.sql:7` (`done_status ->> 0`):
     - `[]`, `[null]` and any non-array JSONB become SQL NULL
     - an element longer than 100 characters makes the rollback fail with "value too long for type character
       varying(100)"
   - Only the several-values loss is documented (IT:197-216, design line 126).
   - Spec AC-002.4 asks for one or more statuses, and Jira status ids are short, so neither case arises from T-Solve's
     own writes. The rollback is also meant for scratch and dev databases only.
   - Fix (optional): add one line to the design's rollback note.
3. **INFO-3 — Liquibase itself never runs the rollback** (Claude)
   - The IT splits the SQL file with its own parser (IT:260-283) and runs the statements over JDBC.
   - Liquibase runs the forward statement in every IT, but no test runs `--rollback` through Liquibase. The repo's
     earlier pattern, `SearchChangesetRollbackIT`, uses `liquibase.rollback(...)`.
   - D43 chose the scratch-schema approach on purpose, and the IT's parser uses the same `--rollback ` prefix rule,
     so the risk is a future syntax change that only Liquibase would reject.
   - Fix (optional): none required; noted for the record.

Codex (second opinion, `kan127-t1-review-codex.md` in the session scratchpad) reported one finding, LOW-1 above,
which I had found too. It found no issue with the cast, the rollback, the Liquibase syntax, the readers or the IT's
strength.

## Assumptions made

- **Edge row 28 "tickets, members":** I accept the scratch `project` table without `ticket` or `project_membership`
  rows as enough. An `ALTER COLUMN … TYPE` on a column outside any key cannot change other tables, and the ITs that
  seed tickets and memberships all start on the migrated schema. Task 1's test list asks only for the `project`
  columns.
- **"Included last" (spec line 429):** I read it as placement at the time 029 is added. That is why LOW-1 is
  non-blocking. If the user means "always last", LOW-1 is not an issue.
- **External DDL:** the task says to notify the main session and not to edit
  `SEP490_G89/Notes/DDL_01-11/02_tenant.sql`. I checked it: line 57 is still `done_status VARCHAR(100)`. Whether
  that notification was sent is not visible in the code. **Open item for the user:** the DDL owner must change line
  57 to JSONB.

## Test Report

Run fresh in an isolated worktree at `180caebc` (`git worktree add … 180caebc`; the main checkout was not used),
from `backend/`, with JAVA_HOME set to the IntelliJ JBR:

- `mvn -B -Dapi.version=1.44 -Dtest='JiraClientTest,TrackerProjectServiceImplTest' -Dit.test='ProjectDoneStatusesMigrationIT,SendBackApiIT' -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false verify`
  - `ProjectDoneStatusesMigrationIT`: Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
  - `SendBackApiIT`: Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
  - Failsafe total: Tests run: 12, Failures: 0, Errors: 0, Skipped: 0 (Surefire 84/84 in the same run)
  - `spotless:check` passed; BUILD SUCCESS
- Full `mvn verify` at this state (implementer's log `kan127-t1-verify.log` in the session scratchpad, which I read):
  Surefire `Tests run: 1430, Failures: 0, Errors: 0, Skipped: 0`; Failsafe `Tests run: 438, Failures: 0, Errors: 0,
  Skipped: 0`; `ProjectDoneStatusesMigrationIT` 5/5 and `SendBackApiIT` 7/7 in it; spotless check; BUILD SUCCESS.
- `bun harnessclaude/hooks/sdd.ts trace project-integration STORY-002`: AC-002.19 and AC-002.20 have a test.
- Claude's probe (throwaway `postgres:16-alpine` container, not committed). Changeset 029's forward and rollback
  statements on a minimal `project` table:
  - forward: `''` → `[""]`; `' 10002 '` → `[" 10002 "]`; `a"b\c` → `["a\"b\\c"]`; NULL → NULL. Every element has type `string`.
  - rollback: `[]`, `[null]` and `{"a":1}` → NULL; `[10002]` → `10002`; a 101-character element → ERROR value too
    long.
- Readers: `grep -rli done_status` over the repo (outside `harnessclaude/`, `.git`, `node_modules`, `target`)
  finds only `002-tenant-project-suggestion.sql`, `029-project-done-statuses.sql` and the new IT (plus copies in
  other worktrees under `.claude/worktrees/`).

---

# Attempt 2 — 2026-11-13

**Verdict: pass** (attempt 2). Fix commit `db144c55` (`db144c55^..db144c55`, one test file). The design note comes
from harness commit `41fae565`. Decision D47 chose to fix LOW-1, add INFO-2 to the design's rollback note as one line,
and leave INFO-3 (D43).

## Summary

LOW-1 is fixed. The test is renamed `includesChangesetOnceAfter026_AC_002_19`
(`ProjectDoneStatusesMigrationIT.java:84`). It no longer requires 029 to be the last include. It asserts that
`changes/029-project-done-statuses.sql` appears once and comes after `changes/026-identity-app-user-keycloak-sub.sql`
(`containsOnlyOnce` plus `containsSubsequence`, `:98-101`). Any 027 or 028 include that the open pull requests append
after 029 now passes. Removing 029, including it twice, or moving it before 026 still fails the test. The changeset
and the master changelog are unchanged (`git diff db144c55 0dd4f8cc -- backend/src/main/resources/db` is empty;
029 is at `db.changelog-master.xml:51`, after 026 at `:50`).

INFO-2 is documented. The master design's rollback note (`04-plans/project-integration/design.md:126`) now says that
the rollback turns `[]`, `[null]` and non-array values into SQL NULL and fails on an element longer than 100
characters. INFO-3 is unchanged, as D43 and D47 decided.

No new findings. The diff changes only the one assertion and the test's display name.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Changeset 029 unchanged; the attempt-1 checks still hold. |
| Completeness | pass | AC-002.19 and AC-002.20 still have tests (trace below). |
| Correctness | pass | `ProjectDoneStatusesMigrationIT` 5/5 in an isolated worktree, spotless passes. |
| Integration | pass | The IT no longer fails once another team's 027/028 changesets are appended after 029. |

### Attempt-1 findings

| Finding | D47 decision | Status | Evidence |
|---------|--------------|--------|----------|
| LOW-1 "029 is last" breaks the next changeset | fix | **Fixed** | `ProjectDoneStatusesMigrationIT.java:84,98-101` |
| INFO-2 rollback limits not written down | one design line | **Fixed** | `design.md:126` (commit `41fae565`) |
| INFO-3 Liquibase never runs the rollback | leave (D43) | Unchanged, as decided | — |

## Traceability

Run in the worktree at `0dd4f8cc`:

```
  ✓ AC-002.19  project/ProjectDoneStatusesMigrationIT.java
  ✓ AC-002.20  project/ProjectDoneStatusesMigrationIT.java
```

## Issues

None.

## Assumptions made

- Spec line 429 says "included last". As in attempt 1, I read it as where 029 goes when it is added (after 026 and
  every changeset applied so far), not as "last forever". The new assertion matches that reading. If the user means
  "always last", the old assertion was right, and it would break every later changeset.
- External DDL (`SEP490_G89/Notes/DDL_01-11/02_tenant.sql:57`) is a known, accepted caveat and not part of this
  review.

## Test Report

Isolated worktree at `0dd4f8cc` (it contains `db144c55`, and the IT file is identical). Run from `backend/` with
JAVA_HOME set to the IntelliJ JBR:

- `mvn -B -Dapi.version=1.44 -Dit.test='ProjectDoneStatusesMigrationIT,ProjectCreationIT,ProjectConnectionIT' -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false verify`
  - `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.ProjectDoneStatusesMigrationIT`
  - Failsafe total: `Tests run: 29, Failures: 0, Errors: 0, Skipped: 0`
  - `spotless:check` in the same run; `BUILD SUCCESS`
- `mvn -B spotless:check`: `BUILD SUCCESS`
