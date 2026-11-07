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
