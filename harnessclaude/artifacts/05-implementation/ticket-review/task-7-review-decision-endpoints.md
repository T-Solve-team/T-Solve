# Task 7: Review decision endpoints

**Module:** ticket-review
**Assignee:** Phạm Tùng Dương
**Status:** evaluating
**Iteration:** 1

## Session 2026-11-07 06:38

### Working on: review-decision-endpoints

- Wired Approve, Reject and Request Changes to the existing atomic Ticket decision service under the existing OpenAPI routes. Spring Security checks the Project Manager role and the endpoint validates UUID batches and feedback.
- Added the internal `ReviewWorkspaceAccess` contract. Its adapter must verify the signed-in Account against the bound Workspace and resolve managed Project IDs in that Workspace. The endpoint refuses requests before calling the Ticket service when the adapter or verified scope is absent.
- The HTTP integration tests supply the access adapter and routing with the existing two-database PostgreSQL fixture. They cover each decision, unauthenticated and wrong-role requests, absent Workspace binding, unmanaged Projects, cross-Workspace UUIDs and validation. The initial run showed seven expected failures before the endpoints existed. All eight cases passed after implementation.
- `mvn -B -Dapi.version=1.44 verify` passed 528 unit tests and 54 integration tests with no failures or skips. `mvn -B spotless:check` passed.
- Trace links the API cases to AC-009.3–009.8. AC-009.6 still needs the separate Jira visibility check.
- Code commit: `default@4a90831`.

### Blockers

Production requires the adapter backed by Workspace routing and Project membership. The test adapter is limited to integration tests.

### Next up

Implement Review Queue, eligible IDs and Ticket Detail, then verify the web integration and MSG64 in Jira.
