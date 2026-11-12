---
module: ticket-review
feature: review-production-api
task: 9
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude-reviewer
---

# Review: ticket-review — Task 9: Production Review reads and web integration (KAN-24), including the Project membership adapter

## Summary

Pass. Queue, eligible ids and Ticket Detail read persisted data through one Project scope, and the new `ProjectMembershipReviewAccess` supplies that scope from `project_membership` in the bound tenant database.

- **Fail-closed scope:** a null, missing or DEACTIVATED Account gets `Optional.empty()` (`ProjectMembershipReviewAccess.java:57`). The controllers turn that into 403. An Account from another Workspace is missing from the bound database, so it is denied the same way. The tests cover the inactive, unknown and BETA-Workspace Accounts.
- **Same scope on queue and ids:** queue and eligible ids both call `queueRows` with the same Department filter over the managed Projects (`TicketReviewReadServiceImpl.java:111,131,171-179`). The decision endpoint gets its managed set from the same database rows (`managedProjectIds`).
- **Dual Project key:** each ACTIVE Project is keyed by its `public_id` and its legacy hex id `lpad(to_hex(project_id),32,'0')::uuid`. Both keys point to the same `ProjectAccess`, and the response always reports `public_id`. `public_id` comes from `gen_random_uuid()`, so it is version 4. A legacy key has version 0. The two forms cannot collide unless someone writes a `public_id` by hand. The legacy key only grants a ticket whose `project_id` is that Project's own legacy form, and only to someone who manages that Project. No unmanaged Project can be reached this way under normal data (see the low-severity Issue 2).

There are no CRITICAL findings and no failing tests. Four minor findings are listed below.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-009.1: `PROJECT_MANAGER` role (`@PreAuthorize`), PROJECT_MANAGER membership on an ACTIVE Project and `PENDING_REVIEW` + `SOLUTION_FORM` all apply. Earliest due date comes first by default, and queue and ids share the Department filter. AC-009.2: Detail returns persisted ticket, Solution and Evidence data plus `canDecide`, which needs manager, managed Project, Solution Form and Pending review. Unpublished tickets are 404 to anyone who is neither the writer nor the managing PM. AC-009.10: the frontend uses the generated client and honours `canDecide`. AC-009.12: `enteredQueueAt` is nullable in OpenAPI and shown as "—" in the frontend. |
| Completeness | pass | All four Task 9 criteria are traced to named tests. The adapter task's verification (managed and readable Projects, inactive Account, foreign Project) is covered by `ProjectMembershipReviewAccessIT`. |
| Correctness | pass | Backend verify passes (807 unit + 165 IT, Spotless OK). Targeted Review ITs re-run: 56 tests, 0 failures. Frontend typecheck and lint have 0 errors, and the review tests pass 116/116. |
| Integration | pass | `ReviewWorkspaceAccess` contract unchanged. Changeset 017 is appended to the master changelog, and `ReviewStorageIT` excludes it from the pre-UUID upgrade master. The test adapters are marked `@Primary` so the new `@Service` does not clash with them. OpenAPI routes and operation ids are unchanged. |

## Traceability

```
  ✓ AC-009.1  ProjectMembershipReviewAccessIT, ReviewDecisionApiIT
  ✓ AC-009.2  frontend ReviewTicketDetail.test.tsx, ProjectMembershipReviewAccessIT, ReviewDecisionApiIT
  ✓ AC-009.3  ProjectMembershipReviewAccessIT, ReviewDecisionApiIT
  ✓ AC-009.4  ReviewDecisionApiIT, TicketDecisionIT
  ✓ AC-009.5  ReviewDecisionApiIT, TicketDecisionIT
  ✓ AC-009.6  JiraReviewFeedbackControllerTest, ReviewDecisionApiIT, TicketDecisionIT
  ✓ AC-009.7  ReviewDecisionApiIT, TicketDecisionIT
  ✓ AC-009.8  ReviewDecisionApiIT, TicketDecisionIT
  ✗ AC-009.9  ambiguous: ReviewStorageIT (also import-ticket-list) — give each module `paths` in module.md
  ✓ AC-009.10  frontend ReviewQueuePage.test.tsx
  ✓ AC-009.11  TicketDecisionIT
  ✓ AC-009.12  frontend ReviewQueuePage.test.tsx, ReviewDecisionApiIT
trace ticket-review (STORY-009): 11/12 acceptance criteria have a test
```

Task 9 covers AC-009.1, 009.2, 009.10 and 009.12, and all four are traced. AC-009.9 (Task 8) is only an id clash between modules. The fix is to add `paths` in module.md. It does not affect this verdict.

## Issues

None blocking. Minor findings for the developer:

1. **MINOR: no HTTP test runs the production adapter.** `backend/src/test/java/vn/tsolve/review/ReviewDecisionApiIT.java:131` and `ReviewHistoryApiIT.java:78` replace `ProjectMembershipReviewAccess` with `@Primary` stubs. `ProjectMembershipReviewAccessIT` tests the adapter only on its own. Nothing calls `/review-queue`, `/review-queue/ticket-ids` or `/tickets/{id}` with real `project_membership` rows, such as a ticket keyed by the legacy hex id through the real adapter. The code that joins them is simple, which is why this is not a blocker. Suggested fix: add one HTTP IT that seeds `project`/`project_membership` and asserts that queue and ids return the same set for a Department, including a legacy-keyed ticket and the exclusion of a MEMBER-only Project.
2. **LOW: two Project keys can collide silently.** `backend/src/main/java/vn/tsolve/review/service/impl/ProjectMembershipReviewAccess.java:49-50`. If a hand-written `project.public_id` equals another Project's legacy form `0000…<hex>`, the second `put` overwrites the first and attributes that key to the wrong Project. That could be a managed one. Generated ids cannot do this, because v4 and v0 differ. Suggested fix: if a key is already present for a different Project, fail closed (drop the key or throw), or add a CHECK that `public_id` is not of the legacy form.
3. **MINOR: schema differs from the team design.** `backend/src/main/resources/db/changelog/changes/017-project-membership.sql:13` adds `ck_membership_role ('PROJECT_MANAGER','MEMBER')`, which `database/02_tenant.sql:67-73` does not have. It matches `00-baseline/T-Solve_Database_Tables.md:134`. That same baseline also requires a unique index allowing at most one PROJECT_MANAGER per Project, and 017 does not create it. Review works with several managers, so this does not affect AC-009.1. Suggested fix: confirm with identity, which owns the table, whether to add the one-manager index now or with UC-05/UC-16.
4. **INFO: frontend fallback when `canDecide` is missing.** `frontend/src/features/review/components/ReviewTicketDetail.tsx:26-29` falls back to the client-side role and Project check when `canDecide` is absent. The production backend always sends `canDecide` (`TicketReviewView.java:75`), and the server still rejects forged mutations, so no change is needed.

## Assumptions made

These are judgment calls the reviewer could not ask the user about. Please confirm or correct them.

- **A1. Workspace binding:** "uses the signed-in Workspace" (AC-009.1) is checked at the adapter level only. The adapter queries whatever tenant database is bound to the request. `TSolveUser` carries no Workspace id, and request-to-database routing is a platform concern outside this task (design risk verdict CAUTION). The reviewer treated the two-database test routing (`TwoTenantDatabases`) as enough evidence.
- **A2. Legacy key:** legacy hex `project_id` on a ticket refers to the Project with that numeric id. This is the existing convention of changeset 010 and the search module. Tickets imported under the old stub ids would be attributed to whichever Project has that numeric id. The reviewer accepted this as an existing data convention, not a leak.
- **A3. INACTIVE Projects:** these drop out of the readable set completely, so even their writer or a reader of a PUBLISHED ticket gets 404 on Ticket Detail. The spec says nothing about this case. The reviewer accepted it as fail-closed.
- **A4. Role and membership both required:** a Project is managed only when the user holds the global `PROJECT_MANAGER` role and has a PROJECT_MANAGER membership. A membership without the role gives 403. The reviewer read AC-009.1 as requiring both.
- **A5. Checks not re-run:** the full frontend suite, `format:check`, `build` and `generate:api` diff were not re-run, because the adapter range changes no frontend file. The PR #137 frontend changes were accepted on the basis of the merged CI.

## Test Report

See `task-9-review-production-api-test-report.md`. Backend verify is green (807 unit + 165 IT, Spotless OK). The reviewer re-ran the targeted Review ITs: 56 tests, 0 failures. Frontend typecheck passes, lint has 0 errors, and review tests pass 116/116.
