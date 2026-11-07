# Task 9: Production Review reads and web integration

**Module:** ticket-review
**Assignee:** Phạm Tùng Dương
**Status:** evaluating
**Iteration:** 1

## Session 2026-11-07 07:06

### Working on: review-production-api

- Connected Review Queue, its eligible IDs and Ticket Detail to the shared Ticket data. Queue and ID requests use the same verified managed Project and Department scope. Detail exposes published tickets to authorized Workspace Accounts and restricts other tickets to their author or managing Project Manager.
- The queue fetches scoped row projections, then sorts and paginates on the server. Project names and public IDs come from the trusted Workspace adapter. The detail reads stored Evidence metadata. The schema has no URL to serve for these records, so their URL is null.
- Made `enteredQueueAt` nullable in OpenAPI, regenerated the client, and reused the Review Queue column to show a dash for an unknown entry time.
- Two PostgreSQL API tests initially failed because the routes were absent. The frontend test initially failed because the empty timestamp rendered a blank cell.
- `mvn -B -Dapi.version=1.44 verify` passed 528 unit tests and 57 integration tests without failures or skips. `mvn -B spotless:check` passed. Trace links the web and database tests to AC-009.1, AC-009.2, AC-009.10 and AC-009.12.
- Frontend formatting, lint, typecheck and build passed. The first frontend suite failed while loading Vitest temporary files. With temporary files inside the workspace, `npm run test -- --maxWorkers=2` passed all 1,355 tests across 75 files. All 115 focused Review tests passed. Regenerating the API client produced identical contents in all 146 files.
- Code commit: `default@f7a7357`. `sdd.ts trace ticket-review STORY-009` found test references for 12 of 12 criteria. The Jira panel clause of AC-009.6 remains Task 10.

### Blockers

Production activation requires a Workspace and Project access adapter. The two-Workspace test supplies the adapter in test configuration. Jira MSG64 remains Task 10.

### Next up

Start Task 10 after verifying the Jira issue and writer context.

## Session 2026-11-07 17:10

### Working on: review-production-api (addendum: Project membership adapter)

- Found that no production `ReviewWorkspaceAccess` existed, so every Review endpoint answered 403 outside tests. User decision: follow `database/02_tenant.sql` and add `project_membership` with an adapter over it.
- Added changeset `017-project-membership` (UUID references to `app_user(id)` and `project(public_id)`, composite key, role check), the read-only `ProjectMembership` entity, `ProjectMembershipRepository` and `ProjectMembershipReviewAccess`. A missing or inactive Account and a foreign Workspace get empty (deny). Projects are keyed by `public_id` and by the legacy hex form of the numeric id.
- The two API ITs mark their test adapter `@Primary`. New `ProjectMembershipReviewAccessIT` covers AC-009.1, AC-009.2 and AC-009.3 on PostgreSQL; the three Review ITs pass (44 tests).
- `sdd.ts trace ticket-review STORY-009`: 11/12; AC-009.9 is reported ambiguous with import-ticket-list (same id in two specs), not a missing test.

### Blockers

Membership rows have no admin screen yet (UC-05/UC-16): the demo seed must insert the PM's `project_membership` rows.
