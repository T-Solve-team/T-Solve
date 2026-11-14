---
module: platform
task: 24
feature: e2e-smoke
date: 2026-11-14
---

# Test report: platform — KAN-186 (STORY-015): isolation IT and E2E smoke run of 14/11

Branch `test/KAN-186-platform-e2e-isolation`, PR #227. Run by Claude Code on the developer machine
(Phạm Tùng Dương), after the code freeze of 12/11.

## Part 1 — `CrossModuleIsolationIT` (Task 23, AC-015.1 – AC-015.7)
- Commit `8f75599b`. `mvn -Dit.test=CrossModuleIsolationIT verify`: **13/13 pass** (42.6 s).
- Full `mvn verify` on the same commit: **496 tests, 0 failures, 0 errors**, Spotless clean.
- The two Workspace databases use the test routing of `TwoTenantDatabases`. The ACME Workspace session is a real one
  (Redis, `WebSessionFilter`). Search's production `VisibleScope` is still fail-closed, so the IT supplies a test
  adapter for that seam only; access goes through the real `ScopeAccessService`.

| AC | Test | Result |
|----|------|--------|
| AC-015.1 | `session_readsRowExistingOnlyInAcme_AC_015_1` | pass |
| AC-015.2 | `ticketList_listsOnlyAcmeTickets_AC_015_2` | pass |
| AC-015.3 | `ticketDetail_betaIdLooksExactlyLikeUnknownId_AC_015_3`, `feedback_betaIdLooksExactlyLikeUnknownIdAndWritesNothing_AC_015_3` | pass |
| AC-015.4 | `search_returnsOnlyAcmeTicketsAndLogsInAcme_AC_015_4` | pass |
| AC-015.5 | `chatbot_contextCitationsAndStoredAnswerBelongOnlyToAcme_AC_015_5` | pass |
| AC-015.6 | `reviewQueue_listsOnlyAcmeTicketsAsProjectManager_AC_015_6` | pass |
| AC-015.7 | `missingSession_returns401AndWritesNothingInEitherWorkspace_AC_015_7` (6 endpoints) | pass |

## Part 2 — Playwright suite on the local stack (Task 24, AC-015.8 – AC-015.12)
- Stack: Docker Compose `db`, `redis`, `keycloak`, `fake-google`; backend `dev` profile with `SEARCH_DEV_SCOPE=all` and
  `TSOLVE_STUB_WORKSPACE_READY=true`; Vite dev server (`VITE_API_MOCKING=false`). Browser: installed Edge 157
  (`E2E_BROWSER_CHANNEL=msedge`), because the Playwright Chromium download stalled on this network. `@playwright/test` 1.64.1.
- `npm run e2e` at commit `3f58bab4` plus the uncommitted `e2e/` package: **17/17 pass** (19.5 s); after the review fixes **24/24 pass** (13.9 s). The HTML report was
  written to `e2e/playwright-report/` (not committed).

| AC | Test | Result |
|----|------|--------|
| AC-015.8 | `suite-config.spec.ts` (the `e2e` script, the README) and the run itself | pass |
| AC-015.9 | home → User List landing, Ticket List, Search, Chatbot, User List (5 tests, real sign-in through Keycloak + fake Google as `alice@acme.test`) | pass |
| AC-015.10 | ACME's session cookie sent with Host `beta.localhost:8080` → 401, no BETA data; same cookie on ACME → 200 | pass |
| AC-015.10 (helper) | Node site requests keep the site Host and only that origin's cookies (2 tests) | pass |
| AC-015.11 | the first unreachable service is named (7 tests) | pass |
| AC-015.12 | `suite-config.spec.ts` (html reporter to `playwright-report`, ignored) ; report written ; this file records the run | pass |

## Fixed during the run (in the suite, not the product)
- Edge 157 refused Playwright 1.48's `--headless=old`; upgraded to 1.64.1.
- Node cannot resolve `*.localhost` on Windows; Node-side calls now go to `127.0.0.1` with the site `Host` header.
- The helper forwarded every context cookie (Keycloak's included); it now sends only the cookies of the given origin.

## Not covered (spec Out of Scope)
- The Jira App: no Workspace is bound to a Forge call before KAN-101 (STORY-006, AC-006.14).
- Production database routing (STORY-002, KAN-99). The browser run uses one dev database for both dev Workspaces.

Defects to log in `docs/bug-log.md`: none. No product defect was found; the three fixes above are in the new test suite.
