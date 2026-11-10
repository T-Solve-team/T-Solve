---
module: ticket-review
feature: review-storage
task: 5
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: ticket-review — Task 5: Shared UUID storage for Review (KAN-24)

## Summary

Pass. The tenant changelog turns the shared `ticket` key into the UUID `ticket_id`. The source key moves to `source_ticket_id`, and the Solution fields stay on `ticket`. The changelog adds `evidence` and `review_decision` with the columns, checks, FKs and indexes of `database/02_tenant.sql`. Their keys are UUIDs, named the way `backend/AGENTS.md` §4/§7 requires.

The upgrade path keeps existing data intact:
- Import decisions are remapped to the new UUID before the old key is dropped (`008-review-ticket-storage.sql:6-21`).
- Search's embedding and feedback rows move to the UUID key (`015`/`016`).
- `updated_at` and `public_id` keep their values (`010`-`012`).

Four PostgreSQL upgrade tests prove this, starting from three different prior states. Import regressions (`ImportConfirmIT`, `ImportConfirmServiceImplTest`) pass with UUID ticket ids in `TicketsPublishedEvent`.

Scope reviewed: `7e2e4c6c..77fa3a31` (squashed PR #137). The relevant files are changesets 008, 010-012 and 014-016, `db.changelog-master.xml`, `Ticket`, `TicketEvidence`, `ReviewDecisionRecord`, `TicketReviewDecision`, `TicketsPublishedEvent`, `ImportConfirmServiceImpl`, the Search entity/repository adaptations and `ReviewStorageIT`.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `ticket_id UUID` PK with `gen_random_uuid()`. `evidence` and `review_decision` match the reference DDL column for column, with UUID keys and refs (`app_user.id` is a UUID). Solution columns remain on `ticket`, and `writer_id`, `review_due_date`, `rejection_reason`, `change_request_comment`, `source_link` and `updated_at` are added as in the reference. |
| Completeness | pass | AC-009.9 is implemented. R13 (upgrade and import integrity) is covered by upgrade tests from a pre-Review main (`BUG_145`), an already-running Search (`BUG_144`), an already-running Review (`BUG_140`), and a database stopped mid-way through 008 with an import decision row (`AC_009_9…`). |
| Correctness | pass | All targeted suites pass at `77fa3a31` (see the test report). Hibernate `ddl-auto: validate` passes against the migrated schema in every Spring IT. |
| Integration | pass | Search (`SearchTicket` subselect, embedding/feedback repositories) and import confirm use the UUID key. `TicketsPublishedEvent` now carries `List<UUID>`, and its listener tests were updated. |

## Traceability

`bun harnessclaude/hooks/sdd.ts trace ticket-review STORY-009` (run 2026-11-07, HEAD):

```
✗ AC-009.9  ambiguous: backend/src/test/java/vn/tsolve/review/ReviewStorageIT.java (also import-ticket-list) — another module's spec has the same id; give each module `paths` in its module.md (its code and its tests) so trace knows whose test it is
```

Judgment: **covered by a ticket-review test.** The tool reports the clash only because `import-ticket-list/module.md` lists `ReviewStorageIT.java` in its `paths` (for its own AC-004.13), and import-ticket-list's AC-009.9 is something else entirely ("Cancel asks MSG37", `import-ticket-list/spec.md:174`). The test `ReviewStorageIT.upgradesExistingTicketsWithoutInventingQueueEntryTime_AC_009_9_AC_009_11_AC_004_13_BUG_140` (`ReviewStorageIT.java:192`) is in the review package. It asserts:
- the UUID ticket key, with the Solution still on `ticket`;
- the UUID Project id;
- the import decision relinked to the UUID;
- UUID keys returned by inserts into `review_decision` and `evidence`, which also exercise their FKs to `ticket` and `app_user`.

This is a tooling ambiguity, not a MISSING criterion. Suggested follow-up for the harness owner: remove `ReviewStorageIT.java` from import-ticket-list's `paths` (it is a ticket-review test that also tags AC-004.13) or split the AC-004.13 assertion into an import test.

## Issues

No CRITICAL or failing findings.

Warnings (no effect on the verdict):

1. **WARNING:** `backend/src/main/resources/db/changelog/db.changelog-master.xml:16-30` and `changes/008…016`. Changeset numbers are reused across modules (`008-search-public-ids` / `008-review-ticket-storage`, two `010-`, two `011-`, two `012-`), and the master runs them out of numeric order. The 010/011/012/014 changesets are conditional `DO` blocks that guard against each other's prior application. This is correct and tested, but fragile. `backend/AGENTS.md` §4 expects `NNN-<slug>` to order changesets, and §7 expects one logical change per changeset, which 008 bundles (key swap plus new columns plus two tables). Suggested fix: the next changeset uses the next free number (≥ 018), and the reuse is noted in `architecture.md`. Do not rewrite the applied changesets.
2. **WARNING:** `backend/src/main/java/vn/tsolve/ticket/entity/Ticket.java:20-26`. A ticket now has two UUIDs, `ticket_id` (PK) and Search's `public_id`. Review accepts either one (`TicketRepository.java:25,28`). This works and is tested (`ReviewDecisionApiIT…BUG_144`), but it is a second identity for one row, and every future consumer must handle it. Suggested fix: record in `architecture.md` which id is the API id (`ticket_id`, which the detail response returns) and plan to retire one.
3. **NOTE:** `database/02_tenant.sql` (the AC-009.9 reference) is untracked in this checkout (`git status`: `?? database/`). The criterion points at a file that is not under version control in this repo. This does not affect the code, but the reference cannot be reviewed from git history.

## Assumptions made

- AC-009.9's "use database/02_tenant.sql as the reference" means: same tables, columns, checks, FKs, cascade and indexes, with BIGSERIAL/BIGINT replaced by UUID under the backend naming convention. On that reading, `review_decision_id` (convention `<table>_id`) is correct, not the reference's `decision_id`. User may correct.
- AC-009.9 is judged covered by the ticket-review test above despite the trace tool's "ambiguous" result. User may correct.

## Test Report

See `task-5-review-storage-test-report.md`. Full `mvn -Dapi.version=1.44 verify` on HEAD: 807 unit + 165 integration, 0 failures, Spotless OK (dispatcher log, checked). The reviewer re-ran the targeted suites at `77fa3a31`: 31 unit + 65 integration, 0 failures.
