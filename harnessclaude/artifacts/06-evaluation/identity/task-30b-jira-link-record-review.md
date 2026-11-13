---
module: identity
feature: jira-link-record
task: 30b
verdict: pass
attempt: 2
date: 2026-11-13
reviewer: tsolve-code-reviewer (attempt 1: Fable 5.2, medium; attempt 2: Sonnet, medium)
iteration: 1
---

# Review: identity — Task 30b: Record the Jira account link

PR #216, branch `feat/KAN-234-identity-jira-account-link-record`, head `f66b4908`. CI on this head (`gh pr checks 216`): all 14 checks pass, including the Testcontainers integration tests.

## Attempt 2 — approve (pass)

Findings 1 to 5 of attempt 1 are fixed as decided. Finding 6 is left open by decision. Nothing new blocks.

### Summary

Approve: 0 Critical, 0 Important, 2 Minor (below). Security: 0 Critical, 0 High.

### Checks

- Read the diff `8c1ddbcb..f66b4908` in full.
- 500 response: `GlobalExceptionHandler.coded` gives status 500 with code MSG06 and no detail from the exception, so no SQL text leaks. The log line keeps the exception class name only.
- The controller has no repository call and no try/catch.
- The OpenAPI contract no longer has the 501 sentence or response; a 500 response referencing Problem was added. The generated client changed by one description line.
- Tenant binding and AC-001.38 are unchanged.
- No tests were run locally (Docker not running on this machine). CI is the backend evidence.

### Issues (attempt 2)

#### Minor

A2-M1. `backend/src/main/java/vn/tsolve/identity/jiralink/JiraAccountLinkService.java:41-46` — the exception is a nested class in a concrete service. `backend/AGENTS.md` prefers `<module>/exception/` and a service interface with an `impl`. The sibling `JiraLinkRequestService` is also concrete, so this is consistent within the package. Optional.

A2-M2. `backend/src/main/java/vn/tsolve/identity/jiralink/JiraLinkRequestService.java:67` — `create` throws `IllegalArgumentException`, which becomes a 500 if the future `jira-app` caller does not catch it. No caller exists yet; check it when the create endpoint is built.

Flaky 403 double-click test (not caused by this delta): `userEvent.dblClick` sends two clicks, and if the mock's 403 answer lands between them the second click sends a second request. Fix when wanted: assert after both clicks without relying on timing.

## Attempt 1 — approve (pass), kept for reference

Changes needed: 0 Critical, 1 Important, 5 Minor. Findings 1 to 5 were fixed in attempt 2; finding 6 is open by decision (no log or audit line for a link or a replaced link).

### Issues (attempt 1)

#### Important

1. `frontend/openapi/tsolve-api.yaml:458-459, 477-478` — the contract still said a valid request answers 501 until the link is recorded, and did not list the new 500 MSG06. Fixed: sentence and 501 removed, 500 added, client regenerated.

#### Minor

2. `backend/src/main/java/vn/tsolve/identity/jiralink/JiraLinkController.java:97-104` — the controller called the repository directly and caught `DataAccessException`, against `backend/AGENTS.md`. Fixed by a service that throws MSG06.
3. `frontend/src/features/auth/pages/JiraLinkPage.tsx:61-65` — on a 500 the page enabled "Link" again although the request was used up. Fixed: no Link after 500.
4. `backend/src/test/java/vn/tsolve/identity/jiralink/JiraLinkControllerTest.java` (edge 3) — the `finally` dropped the trigger but left the function `fail_link_insert`. Fixed: function dropped.
5. `backend/src/main/java/vn/tsolve/identity/jiralink/JiraLinkController.java:99` with `JiraLinkRequestService.java:67` — the stored tracker account id was not checked at request creation. Fixed: non-blank and at most 128 characters.
6. `backend/src/main/java/vn/tsolve/identity/jiralink/JiraAccountLinkRepository.java:22-29` — a replace overwrites the earlier Account with no log or audit line. Open by decision; the row keeps the actor and time of the current link.

### What was checked and holds

- Tenant binding: the whole chain runs inside the session's Workspace scope, and a session whose Workspace is not the host's is refused. Tests assert the other Workspace stays empty on every path.
- AC-001.38: the Workspace comes from the host, the tracker account from the stored request, the Account from the session principal. Body ids are ignored.
- Replace: one atomic `INSERT ... ON CONFLICT DO UPDATE`.
- Single use: the request is consumed before the write and is gone after 200 and 500; it stays after 401 and 403.
- No session created: no `Set-Cookie`; the Redis key set equals before minus the request key.
- Changeset 029: FK to `app_user (id)`, unique on `tracker_account_id`, rollback present.
- AC-002.5: logout keeps the two link rows equal.

### Test Report

Verification is CI on `f66b4908` (14 of 14 pass) and the developer's local runs. Frontend `vitest` for `JiraLinkPage.test.tsx` passed in attempt 1 (10 tests).

Proposed status: `jira-link-record` → `passing`.
