# Task 10 Request Changes feedback in Jira

**Module:** ticket-review
**Assignee:** Phạm Tùng Dương
**Status:** in-progress
**Iteration:** 1

## Session 2026-11-07 07:36

### Working on review-jira-feedback

- Added GET `/api/v1/jira-app/issues/{issueKey}/review-feedback`. It requires the Jira App role and a user principal. It returns nullable feedback or MSG64 params `manager_name` and `comment` from the stored decision and trusted Account directory.
- Reused TicketReviewReadService and the shared Ticket and ReviewDecisionRecord storage. Read one latest matching decision for the current Changes requested Solution Form under the verified Project, issue key and writer. Other states and import sources have no feedback.
- ReviewJiraAccess is the production integration boundary. Its callback must resolve the verified cloudId to a registered active Workspace, verify the active linked Account and Jira issue permission, bind that Workspace's database for the entire read and resolve the reviewer's stored display name there. Missing integration refuses access before querying tickets.
- Updated OpenAPI before implementation and regenerated the client. No schema migration is needed. Database reference files were read directly. Graphify refreshed the local code graph to 8,924 nodes, but SQL symbol extraction is unavailable because tree_sitter_sql is absent.
- The first PostgreSQL test failed with the expected 404 for the missing route. After implementation, coverage includes the latest decision when an older reviewer used the same text, another writer, another issue, another Project, import sources, resubmission and terminal statuses, missing authentication, product events, unlinked or deactivated Accounts and invalid issue keys. Two real Workspace databases have the same issue key and writer UUID and return their own manager and comment.
- `mvn -B -Dapi.version=1.44 verify` passed 529 unit tests and 66 integration tests, with zero failures, errors or skips. The new no-adapter unit test proves ticket reads are not invoked. Formatting passed through Spotless. The API integration test uses an authenticated Jira principal and a test access adapter. FIT signature verification remains covered by the existing security suite.
- Frontend format, lint, typecheck and build passed. Lint retains the existing TicketListFilters fast-refresh warning. The first full suite passed 1,354 of 1,355 tests and failed the BUG-100 loading-class assertion in ReviewQueueRecovery. The unchanged file passed all 8 tests in isolation. Review UI and generated Review endpoints match HEAD. This run is recorded as a follow-up to open BUG-070 in docs/bug-log.md. The second full run passed all 1,355 tests across 75 files in 582.19 seconds. Regenerating the API client produced identical contents. Assertions, timeouts and source are unchanged.
- `sdd.ts trace ticket-review STORY-009` found test references for 12 of 12 criteria. The Jira rendering clause of AC-009.6 is still outstanding.
- The user chose to complete only the API at this checkpoint. A proposed Jira App spec and plans were removed after that choice. Jira App stays decomposed with its existing source.
- The user approved the API commit and push. Code commit `default@0fec48f`. Keep the full Jira feedback feature in progress because the panel rendering clause remains outside this API checkpoint.

### Blockers

Production activation requires a ReviewJiraAccess adapter. Its test implementation routes to separate PostgreSQL Workspace databases. The actual Forge panel is outside the user's current scope, so MSG64 display in live Jira has not been verified.

### Next up

Push the approved commits on feat/KAN-24-review-backend and prepare the PR description. Keep review-jira-feedback in progress until its Jira rendering clause is implemented and verified.

## PR #137 review follow-up — 2026-11-07

- Integrated origin/main with an ordinary merge; retained both branches' HarnessClaude events and validated the JSON/JSONL. Existing local AGENTS.md/database/graphify-out changes remain outside the commits.
- Addressed BUG-135–140 and BUG-143: Project UUIDs through Review and CSV import; missing manager names produce null feedback; durable decision lookup does not compare comment text; Detail exposes canDecide and rejection feedback; unsupported sorts and service validation return 400; both access methods are mandatory.
- Reused the exact KAN-31 008-ticket-import-project-uuid SQL, path and changeset identity. The master declares ordering explicitly. Existing applied Review SQL is unchanged. PostgreSQL regressions verify legacy import preservation and upgrade from an already-running Review schema without replaying storage migration, followed by a second no-op update.
- The user explicitly selected API fixes only. BUG-141 tracks production Workspace/Jira adapters; both endpoints continue to deny unverifiable access. BUG-142 tracks the absent backend CI job (KAN-6). Local verification is not described as backend CI coverage. In-memory queue sorting remains bounded by the managed queue and is documented with its database-paging upgrade point.
- Missing-name regression first failed with IllegalStateException. Final mvn -B spotless:apply -Dapi.version=1.44 verify passed: 595 unit tests, 71 integration tests, zero failures/errors/skips, Spotless clean. Java 21 and the process-local Docker Linux pipe/API workaround were used; no test was skipped.
- Frontend format, lint, typecheck and build passed. Lint has the existing TicketListFilters warning. A second API generation was byte-identical. Full frontend test result follows after the active run.
- Graphify updated the code graph to 8,960 nodes. SQL extraction lacks tree_sitter_sql; migration findings were verified directly against SQL and PostgreSQL tests.
- Full frontend verification finished: 75 test files and 1,356 tests passed, zero failures, in 437.62 seconds. No assertion, timeout, retry or global setting was changed. STORY-009 trace remains 12/12.
- Code commit: default@692044a. Conflict integration commit: d268c13. Production adapter and backend CI dependencies remain open; the module is not marked completed.
