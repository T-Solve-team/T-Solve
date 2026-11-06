---
module: search
feature: search-log-schema, ticket-feedback-schema
task: 7, 8
verdict: pass
attempt: 1
date: 2026-11-06
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: search — Tasks 7 and 8: search_log and ticket_feedback tables (KAN-141, STORY-005, AC-005.2, AC-005.3)

## Summary
I reviewed the uncommitted change against `d0bda72` on `feat/KAN-141-search-embedding`. It adds two changesets,
`011-search-log.sql` and `012-search-ticket-feedback.sql`, includes them in `db.changelog-master.xml`, and extends
`SearchSchemaIT` and `SearchChangesetRollbackIT`. I compared each column, type, nullability rule, FK, CHECK, unique
constraint and index with the spec's "Search tables" Interface Contract. All of them match. The rollback of each
changeset is `DROP TABLE`, and the rollback IT checks that both tables are gone after a rollback by count and come
back after a new update. All test names that the plan asks for are present and cite `AC_005_2`. Each negative test
asserts the constraint name in the error message. The diff touches no file outside the scope. There are no CRITICAL
findings. Verdict: PASS.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `search_log` (`011-search-log.sql:4-17`): the PK is `search_log_id UUID DEFAULT gen_random_uuid()`. `user_id UUID NOT NULL` has an FK to `app_user(id)`. `department_id BIGINT` is nullable, with an FK to `department(department_id)`. `query TEXT NOT NULL`. `channel VARCHAR(20)` has `ck_search_log_channel` (WEB, ISSUE_PANEL, CHATBOT). `result_count INTEGER` has `ck_search_log_result_count >= 0`. `is_fallback BOOLEAN NOT NULL`. `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`. The table has `idx_search_log_created_at`, which the plan asks for. `ticket_feedback` (`012-search-ticket-feedback.sql:4-17`): the PK is `ticket_feedback_id UUID`. `ticket_id BIGINT NOT NULL` has an FK to `ticket(id)` with no cascade; the contract names none. `user_id UUID NOT NULL` has an FK to `app_user(id)`. `rating` has `ck_ticket_feedback_rating`, `comment TEXT` has `ck_ticket_feedback_comment` (`char_length <= 500`), and `channel` has `ck_ticket_feedback_channel` (WEB, ISSUE_PANEL). `created_at` and `updated_at` are `TIMESTAMPTZ NOT NULL DEFAULT now()`. `uq_ticket_feedback_ticket_user` is `UNIQUE (ticket_id, user_id)`. The referenced keys have the right types: `app_user.id` is UUID (006), `department.department_id` is BIGSERIAL (002) and `ticket.id` is BIGSERIAL (003). |
| Completeness | pass | AC-005.2 for `search_log` and `ticket_feedback`, and AC-005.3 for both changesets. As the user decided, there is no row policy, because the platform guard is not on `main`. Each table is singular, has `<table>_id UUID` as its PK and has no `workspace_id`. |
| Correctness | pass | The controller ran `SearchSchemaIT` (16 tests) and `SearchChangesetRollbackIT` (1 test) on Docker, and both pass. Spotless is clean. I did not run them myself, as the dispatcher asked (Assumption 1). |
| Integration | pass | The changesets follow `NNN-<slug>`, author `search` and one logical change per file (`backend/AGENTS.md` §2, §7). They are included in order after 010. `SearchChangesetRollbackIT` counts the `search` author's changesets, so 011 and 012 are rolled back with the rest (AC-005.3). |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-005.2 `search_log` columns and constraints | PASS | `SearchSchemaIT.java:175` `createsSearchLogWithTenantConvention_AC_005_2` checks the exact column set, that only `department_id` is nullable, that `created_at` is `timestamptz NOT NULL DEFAULT now()`, the generated ids, and that `idx_search_log_created_at` exists on `(created_at)`. `:232` `refusesUnknownChannel_AC_005_2` asserts `ck_search_log_channel`, and `ck_ticket_feedback_channel` for both UNKNOWN and CHATBOT. `:249` asserts `ck_search_log_result_count`. `:259` asserts `search_log_user_id_fkey` and `search_log_department_id_fkey`. |
| 2 | AC-005.2 `ticket_feedback` columns and constraints | PASS | `:272` `createsTicketFeedbackWithTenantConvention_AC_005_2` checks the column set, that only `comment` is nullable, and both timestamps. `:320` asserts `uq_ticket_feedback_ticket_user`. `:333` asserts `ck_ticket_feedback_comment` at 501 characters and accepts 500. `:348` asserts `ck_ticket_feedback_rating`. `:360` asserts both FK names. |
| 3 | AC-005.3 rollback undoes each changeset | PASS | Each file has `--rollback DROP TABLE <table>;`. `SearchChangesetRollbackIT.java:58-62` checks that the tables exist before the rollback, `:93-103` that they are gone after it, and `:73-77` that they are back after a new update. |

### Stage 2 — Quality

CRITICAL: none.

Informational (does not block):
1. [`SearchSchemaIT.java:197-218`, `:293-314`] The tests check column names and nullability, and the data type
   only for the timestamps. They do not check `VARCHAR(20)`, `INTEGER`, `TEXT`, `UUID` or `BIGINT`. I checked these
   types by reading the SQL, and they match. A type drift would not fail any test.
   Fix, optional: assert `data_type` and `character_maximum_length` for each column, as the embedding test does.
2. [`SearchSchemaIT.java:259-269`, `:360-371`] The FK names come from PostgreSQL's default naming
   (`<table>_<column>_fkey`), not from a named `CONSTRAINT` in the changeset. Changeset 002 names its FK
   (`fk_department_default_project`). The default names are stable in PostgreSQL 16, so this is cosmetic.
3. [`SearchSchemaIT.java:197`, `:293`] The `information_schema.columns` queries do not filter on `table_schema`.
   Each test uses its own database with only `public`, so this is harmless.
4. The tests go beyond the plan: `refusesNegativeResultCount`, `enforcesSearchLogReferences`, `refusesUnknownRating`
   and `enforcesTicketFeedbackReferences` are extra. This is welcome.

Security: these are DDL changes only. There is no row policy on these tables, as the user decided. The retention and
access of `search_log` are Out of Scope in the spec (Changelog 2026-11-06, deferred to KAN-150).

## Assumptions made
1. **Tests not run by the reviewer (open judgment call).** The dispatcher told me not to run Docker or mvn. The
   evidence for Correctness is the controller's run: `SearchSchemaIT` 16/16, `SearchChangesetRollbackIT` 1/1, and a
   clean Spotless. The evaluate skill says to run all tests, so this pass relies on that run.
2. **BIGINT references.** `backend/AGENTS.md` §2 says a reference is `<entity>_id (UUID)`. Here `ticket_id` and
   `department_id` are BIGINT, because the `ticket` and `department` tables have BIGINT keys and the spec contract
   gives BIGINT. I judged against the spec.
3. **No `ON DELETE CASCADE` on `ticket_feedback.ticket_id`.** The contract gives `FK ticket(id)` without a cascade
   (unlike `ticket_embedding`). I accept the code as written. Deleting a ticket that has Feedback will fail. A
   later ticket-delete flow must handle this.
4. **The `created_at` index on `search_log`** is not in the spec contract. The plan (Task 7) asks for it, and it
   does not conflict with the spec.

## Traceability
```
  ✓ AC-005.1  backend/src/test/java/vn/tsolve/search/SearchSchemaIT.java
  ✓ AC-005.2  backend/src/test/java/vn/tsolve/search/SearchSchemaIT.java
  ✓ AC-005.3  backend/src/test/java/vn/tsolve/search/SearchChangesetRollbackIT.java
  ✓ AC-005.4 … AC-005.9  (other tasks)
trace search (STORY-005): 9/9 acceptance criteria have a test
```
I read each test for AC-005.2 and AC-005.3. Each test checks its constraint by name, and none only mentions the id.

## Issues
None blocking. Informational items 1-4 are under Stage 2.

## Test Report
- `SearchSchemaIT`: 16/16 pass, and `SearchChangesetRollbackIT`: 1/1 pass. The controller ran both on Docker; the
  reviewer did not run them (Assumption 1).
- `mvn spotless:check`: clean (controller).
- `bun harnessclaude/hooks/sdd.ts trace search STORY-005`: 9/9 (reviewer).
- Scope: `git diff HEAD --stat` and the untracked files list only the two changesets, the master changelog, the two
  ITs, `progress.json` and `events.jsonl`.
