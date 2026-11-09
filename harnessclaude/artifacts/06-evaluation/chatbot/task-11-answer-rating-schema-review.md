---
module: chatbot
feature: answer-rating-schema
task: 11
verdict: pass
attempt: 1
date: 2026-11-09
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: chatbot — Task 11: Rating columns (KAN-182, chatbot STORY-008, AC-008.6)

## Summary
I reviewed commit `b9079eb` on `feat/KAN-182-chatbot-rating-api`. It adds changeset `024-chatbot-answer-rating.sql`
and includes it last in the master changelog. It adds the enum `ChatbotRating` and the `rating` / `ratedAt` fields
of `ChatbotAnswer`, and the new `ChatbotRatingSchemaIT`. It also edits two existing ITs that changeset 024 broke:
`ChatbotAnswerTimeoutIT` and `ReviewStorageIT`. The files of Task 11 have no diff between `b9079eb` and `HEAD`
(`6555eba`, Task 12), so my test run on the working tree is a run of this commit's code. I judged the change against
AC-008.6, the design section "Backend ratings" and `backend/AGENTS.md` §7. Both edits to the existing ITs are
correct and minimal. There are no CRITICAL findings. Verdict: PASS.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `024-chatbot-answer-rating.sql:3` is `--changeset chatbot:024-chatbot-answer-rating`: the author is the module and the id is `NNN-<slug>` (AGENTS.md naming table). `:4-6` add `rating VARCHAR(20) NULL` and `rated_at TIMESTAMPTZ NULL`. `:8-10` add `ck_chatbot_answer_rating` (`rating IN ('HELPFUL', 'NOT_HELPFUL')`; a NULL rating passes, because a CHECK that is NULL passes) and `ck_chatbot_answer_rated` (`(rating IS NULL) = (rated_at IS NULL)`). `:12-13` roll back both constraints, then both columns. The SQL matches the design word for word. `db.changelog-master.xml:45` includes it after 023, the last line. `ChatbotAnswer.java:43-47` maps `@Enumerated(EnumType.STRING) @Column(length = 20) ChatbotRating rating` and `@Column(name = "rated_at") Instant ratedAt`. `:82-88` add getters only, with no setter (AGENTS.md entity rule). `ChatbotRating` (`chatbot/rating/entity/`) has exactly `HELPFUL` and `NOT_HELPFUL`, the values of the DB check. |
| Completeness | pass | AC-008.6 is fully covered: two nullable columns, the allowed values, "both null or both set", and a `--rollback` that drops both columns. The plan's edge case R4 (`rated_at` without `rating`) is covered by the DB check and its test. The files match the Task 11 file list, plus the two IT fixes. |
| Correctness | pass | I re-ran 5 ITs: `ChatbotRatingSchemaIT` 2/2, `ChatbotAnswerTimeoutIT` 1/1, `ReviewStorageIT` 4/4, `ChatbotSchemaIT` 3/3, `SearchChangesetRollbackIT` 1/1. All pass, 0 failures. The controller reports a full `mvn clean verify` with 939 unit tests and 302 ITs, 0 failures. `trace chatbot STORY-008`: 6/6. |
| Integration | pass | `ChatbotAnswer` imports the enum from `chatbot.rating.entity`, the same module, so the module boundary holds. The new columns are insertable and nullable. The existing `save` of a new answer writes both as NULL, which passes both checks. Hibernate `validate` of `ChatbotAnswer` against the full schema now runs in `ChatbotAnswerTimeoutIT` and passes. This shows the entity mapping (string enum to `varchar(20)`, `Instant` to `timestamptz`) agrees with the changeset. |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-008.6 nullable `rating` / `rated_at`, types | PASS | `addsNullableRatingColumnsWithChecks_AC_008_6` → `assertRatingColumns()`: `character varying(20)` and `timestamp with time zone`, `is_nullable = YES`, scoped to `table_schema = 'public'`. A new answer has both NULL. |
| 2 | AC-008.6 values `HELPFUL` / `NOT_HELPFUL` only | PASS | The same test sets each value with `rated_at = now()` and reads it back. `'UNKNOWN'` fails, and the message names `ck_chatbot_answer_rating` (`ChatbotRatingSchemaIT.java:91-94`). |
| 3 | AC-008.6 both null or both set (R4) | PASS | Setting both to NULL succeeds. `rating` without `rated_at` fails, and so does `rated_at` without `rating`. Both failures name `ck_chatbot_answer_rated` (`:95-104`). |
| 4 | AC-008.6 `--rollback` drops both columns | PASS | `rollsBackTheColumns_AC_008_6` finds the index of `024-chatbot-answer-rating` and rolls back from it (`:108-119`). It asserts that `chatbot_answer` still exists with exactly its 6 columns from 013 and that neither constraint is in `pg_constraint`. It then updates again and re-checks the columns, so the changeset can be re-applied after a rollback. |

Each test fails if its part of the changeset is removed. With no check, `assertThatThrownBy` fails. With no
`--rollback`, Liquibase cannot roll back a formatted-SQL `ALTER` and the test throws. With a wrong type or nullability,
`assertRatingColumns` fails. With an `ORDINAL` mapping, Hibernate `validate` fails in `ChatbotAnswerTimeoutIT`.

### Edits to the existing ITs

1. `ChatbotAnswerTimeoutIT.java:52`: `search-only.xml` becomes `db.changelog-master.xml`. This is **correct and
   minimal.** The test builds an EntityManagerFactory over `vn.tsolve.chatbot.answer.entity` with
   `hbm2ddl.auto = validate`. The test changelog `search-only.xml` stops at 013, so `validate` fails on the missing
   `rating` column. There were two ways to fix this:
   - Add 024 to `search-only.xml`. That file is a frozen fixture of an "already-running search" database. It is
     shared by `ChatbotSchemaIT`, `SearchChangesetRollbackIT` and `ReviewStorageIT` BUG_144, so changing it would
     change three other tests.
   - Point this test at the real master changelog. The commit does this. Hibernate is then validated against the
     schema that production runs.

   The choice is the better one, and it is one line. Its cost is a slower setup (about 60 s for this IT in my run,
   because it applies all 24 changesets). The `ChatbotRatingIT` of Task 12 uses the same pattern.
2. `ReviewStorageIT.java:143`: `013-chatbot` becomes `0\d\d-chatbot`. This is **correct and minimal.** BUG_140
   builds an "old" master that leaves out 013, so `chatbot_answer` does not exist. Changeset 024 would then fail on
   `ALTER TABLE chatbot_answer`. The wider pattern removes both 013 and 024, and the later full `update` applies both
   in order. That is the real upgrade path. Any future `0NN-chatbot-*` changeset also depends on `chatbot_answer`, so
   leaving out all of them is the right rule. `ReviewStorageIT` passes 4/4.

### Stage 2 — Quality

CRITICAL: none.

Informational (does not block):
1. Low. [`ReviewStorageIT.java:142-144`] The "old database" regex is a growing deny-list. Every new changeset that
   needs a table left out of the fixture must be added to it. The pattern `0\d\d` also stops matching at changeset
   100. This debt existed before this commit, and the commit does not make it worse. A later fix could cut the master
   changelog at a fixed changeset id, the way `SearchChangesetRollbackIT` does.
2. Low. [`ChatbotRatingSchemaIT.java:119`] The rollback undoes every changeset from 024 to the end. Today that is
   only 024. A later changeset with no `--rollback` would break this test, although 024 is fine. The same pattern is
   accepted in `SearchChangesetRollbackIT`.
3. Low. [`ChatbotAnswer.java:43-47`] `rating` and `ratedAt` are updatable but have no mutator. This is on purpose:
   Task 12 writes them through one JPQL `UPDATE`, as the design says. A managed `ChatbotAnswer` loaded in the same
   persistence context before that update would hold stale values. No current code path does that.
4. Low. [`024-chatbot-answer-rating.sql:12`] Dropping the constraints before the columns is not needed, because
   PostgreSQL drops a column's single-column constraints with it. The design asks for it, and it is harmless.

Security: this is a schema-only change. It builds no SQL from strings, and the rating values are fixed by a DB check.
The column holds no personal data beyond the existing `user_id` link.

## Assumptions made
1. **Full suite not re-run by the reviewer.** The dispatcher told me not to run `mvn clean`. I re-ran the 5 ITs that
   this commit touches or depends on, and they pass. For the rest of the suite, my evidence is the controller's
   `mvn clean verify` (939 unit tests and 302 ITs, 0 failures). I did not see that run's output.
2. **Spotless (open judgment call).** `mvn spotless:check` cannot run in a git worktree: JGit reports
   `Cannot find git repository` because of `ratchetFrom`. The controller reports that a check of the 14 changed Java
   files without the ratchet is clean. I did not run it. The project rule says `verify` includes `spotless:check`.
   The user should confirm that the worktree limit is acceptable, or run it in the main checkout before the push.
3. **Rollback scope.** AC-008.6 asks only that the `--rollback` drops both columns. Dropping the two checks as well,
   as the design says, is in scope.

## Traceability
```
  ✓ AC-008.1  backend/src/test/java/vn/tsolve/chatbot/rating/controller/ChatbotRatingControllerTest.java, backend/src/test/java/vn/tsolve/chatbot/rating/ChatbotRatingIT.java
  ✓ AC-008.2  backend/src/test/java/vn/tsolve/chatbot/rating/controller/ChatbotRatingControllerTest.java, backend/src/test/java/vn/tsolve/chatbot/rating/ChatbotRatingIT.java
  ✓ AC-008.3  backend/src/test/java/vn/tsolve/chatbot/rating/controller/ChatbotRatingControllerTest.java, backend/src/test/java/vn/tsolve/chatbot/rating/ChatbotRatingIT.java
  ✓ AC-008.4  backend/src/test/java/vn/tsolve/chatbot/rating/controller/ChatbotRatingControllerTest.java
  ✓ AC-008.5  backend/src/test/java/vn/tsolve/chatbot/rating/controller/ChatbotRatingControllerTest.java
  ✓ AC-008.6  backend/src/test/java/vn/tsolve/chatbot/rating/ChatbotRatingSchemaIT.java
trace chatbot (STORY-008): 6/6 acceptance criteria have a test
```
Task 11 covers only AC-008.6. AC-008.1 to AC-008.5 are Task 12 (`6555eba`), which this review does not cover. I read
both AC-008.6 tests, and each one checks the behaviour of the criterion.

## Issues
None blocking. Informational items 1 to 4 are under Stage 2.

## Test Report
See `task-11-answer-rating-schema-test-report.md`. In short: 5 focused ITs, 11/11 tests pass (reviewer run).
The full suite has 939 unit tests and 302 ITs, all green (controller run). `trace chatbot STORY-008` gives 6/6.
Feature statuses were not changed: the dispatcher asked me to write only the review.
