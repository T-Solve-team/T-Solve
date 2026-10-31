---
module: identity
date: 2026-10-25
total_tasks: 12
owner: Dev
---

# Tasks: identity

Spec: artifacts/03-modules/identity/spec.md
Master: artifacts/04-plans/identity/design.md

## Task 1: Workspace User List — KAN-107

**Story:** STORY-004
**Feature:** user-list
**Description:** Replace the User List placeholder with search, role/status/Department filtering, pagination and sorting. Enforce the 100-character search limit with MSG02 and retain overlong input without changing the current query or URL. Preserve URL state, use the registered Admin guard, and handle loading, retry and MSG04. Add only the list and lookup contract needed by this screen; generate its hooks and implement Workspace-scoped MSW data.
**Files:**
- Modify: `frontend/src/features/users/pages/UserListPage.tsx`
- Create as needed: `frontend/src/features/users/components/UserListToolbar.tsx`, `frontend/src/features/users/components/UserListTable.tsx`, `frontend/src/features/users/hooks/useUsers.ts`, `frontend/src/features/users/mocks/data.ts`
- Modify: `frontend/src/features/users/mocks/handlers.ts`, `frontend/openapi/tsolve-api.yaml`
- Generate only: `frontend/src/shared/api/generated/**`
- Test: `frontend/src/features/users/pages/UserListPage.test.tsx`, `frontend/src/features/users/mocks/handlers.test.ts`
**Dependencies:** User-run spec approval, plan validation and normal module status transitions.
**Estimated size:** medium
**Verification:** Tests name AC-004.1–AC-004.8 and cover master risk rows 1–6, 17 and 18. Run all required frontend checks and regeneration drift check. Inspect browser rendering at 1366 × 768. Stop for user approval of `feat(identity): add workspace user list (KAN-107)` before committing and before Task 2.

## Task 2: Add user — KAN-108

**Story:** STORY-005
**Feature:** add-user
**Description:** Implement User Detail creation mode, email validation, required role assignments and creation feedback using generated types and mutable MSW handlers. Preserve form input on failed requests. Include duplicate and Deactivated-account email errors.
**Files:**
- Modify: `frontend/src/features/users/pages/UserDetailPage.tsx`, `frontend/src/features/users/mocks/handlers.ts`, `frontend/src/features/users/mocks/data.ts`, `frontend/openapi/tsolve-api.yaml`
- Create as needed: `frontend/src/features/users/hooks/useUserMutations.ts`
- Generate only: `frontend/src/shared/api/generated/**`
- Test: `frontend/src/features/users/pages/UserDetailPage.test.tsx`, `frontend/src/features/users/mocks/handlers.test.ts`
**Dependencies:** Task 1 verified and committed with user approval; next issue branch follows team conventions.
**Estimated size:** medium
**Verification:** AC-005.1–AC-005.8; master risks 7–10, 17 and 18; all frontend checks and browser inspection. Stop for approval of `feat(identity): add workspace user accounts (KAN-108)` before committing and before Task 3.

## Task 3: Account status and required handover — KAN-109

**Story:** STORY-006
**Feature:** account-status
**Description:** Add existing-user loading and status controls, confirmations, self/last-Admin protection, and required atomic handover for managed Projects and Spaces. Retain history and unchanged state on failure/cancel.
**Files:**
- Modify: `frontend/src/features/users/pages/UserDetailPage.tsx`, `frontend/src/features/users/hooks/useUserMutations.ts`, `frontend/src/features/users/mocks/handlers.ts`, `frontend/src/features/users/mocks/data.ts`, `frontend/openapi/tsolve-api.yaml`
- Create as needed: `frontend/src/features/users/hooks/useUserDetail.ts`, `frontend/src/features/users/components/HandOverResponsibilitiesModal.tsx`
- Generate only: `frontend/src/shared/api/generated/**`
- Test: `frontend/src/features/users/pages/UserDetailPage.test.tsx`, `frontend/src/features/users/mocks/handlers.test.ts`
**Dependencies:** Task 2 verified and committed with user approval; next issue branch follows team conventions.
**Estimated size:** medium
**Verification:** AC-006.1–AC-006.8; master risks 11–14, 17 and 18; all frontend checks and browser inspection. Stop for approval of `feat(identity): manage account status and handover (KAN-109)` before committing and before Task 4.

## Task 4: Edit roles and assignments — KAN-110

**Story:** STORY-007
**Feature:** user-roles
**Description:** Complete editing mode for roles, Department and managed Spaces; reuse handover, protect own Admin role, confirm Ticket Resolver removal, validate assignments atomically and invalidate affected session/list/detail queries.
**Files:**
- Modify: `frontend/src/features/users/pages/UserDetailPage.tsx`, `frontend/src/features/users/hooks/useUserDetail.ts`, `frontend/src/features/users/hooks/useUserMutations.ts`, `frontend/src/features/users/components/HandOverResponsibilitiesModal.tsx`, `frontend/src/features/users/mocks/handlers.ts`, `frontend/src/features/users/mocks/data.ts`, `frontend/openapi/tsolve-api.yaml`
- Generate only: `frontend/src/shared/api/generated/**`
- Test: `frontend/src/features/users/pages/UserDetailPage.test.tsx`, `frontend/src/features/users/mocks/handlers.test.ts`
**Dependencies:** Task 3 verified and committed with user approval; next issue branch follows team conventions.
**Estimated size:** medium
**Verification:** AC-007.1–AC-007.7; master risks 15–18; all frontend checks and browser inspection. Stop for approval of `feat(identity): update user roles and assignments (KAN-110)` before committing. Record production backend enforcement as unverified.

## Task 1R: Address User List review — KAN-107

**Story:** STORY-004
**Feature:** user-list-review
**Description:** Merge main without rewriting published history (user decision), preserve UC-01–03 and give UC-04–07 unique ids, change UserAccount.id to UUID, document 400, extract the toolbar, reset URL search validation, normalize filter values, show refetch pending state, and repair tests and defect records. Refresh PR #60 description and screenshot.
**Files:** `frontend/src/features/users/**`, `frontend/openapi/tsolve-api.yaml`, generated API output, `docs/bug-log.md`, identity harness artifacts.
**Dependencies:** Merged spec approved by user through harness CLI.
**Verification:** AC-004.1–AC-004.8, BUG-015 red/green, full frontend verify and deterministic API regeneration. Commit and push to existing KAN-107 branch as authorized. No merge into main.

## Task 6: Resolve PR #77 integration conflicts — KAN-108

**Story:** STORY-005
**Feature:** add-user-review
**Description:** Merge latest main into the published KAN-108 branch, preserve incoming UUID and site/session updates, reconcile mutable creation fixtures and history, align creation Department/Space references and validation with UUID contracts, and regenerate API output. Preserve both bug histories with unique ids.
**Files:** users feature, OpenAPI and generated output, docs/bug-log.md, identity tracking/reports and merged event stream.
**Verification:** Reproduce incompatible UUID Department validation, then full frontend checks,8/8 STORY-005 trace, browser creation with Department and Space, independent review, pushed PR #77 mergeability and exact-head CI. User authorized merge/push; never merge into main or force-push.

## STORY-001 tasks (KAN-103 / KAN-104 plan, 2026-10-28)

Tasks 7–12 build STORY-001 in feedback-first order (design: "STORY-001: central sign-in and the session per
Workspace site"). Every AC-001.1–AC-001.43 is in exactly one task. One PR per task, each on its own branch
`<type>/KAN-<n>-identity-<desc>` from an up-to-date `main`; several branches per Jira key are fine (Q12). Before
each commit: the full verify of the side the task touches (CLAUDE.md project rules).

## Task 7: Login screen, sign-in contract and mock sign-in — KAN-229

**Story:** STORY-001
**Feature:** login-screen
**Acceptance criteria:** AC-001.40, AC-001.41, AC-001.42, AC-001.43
**Description:** Replace the Login placeholder with a centred antd card from the shared kit: logo, title, one "Sign in with Google" button and a message area that shows `useSignInMessage()` as text (SRS III.1.1.1 wording; no Figma frame, Q13). The button calls `startSignIn` with the `returnTo` of the Login URL and is pending after the click. In mock mode `startSignIn` opens the dev-only mock sign-in page `/dev/sign-in` (Q5), which offers each dev user and the outcomes cancel → MSG06, MSG09, MSG10, MSG122, MSG141 and suspended, then sets the dev user and opens `returnTo`, or opens `/login?error=…`. Add the four redirect endpoints to the OpenAPI file under tag `session` (Q6) and regenerate.
**What Hiếu can see or try:** `/login` shows the card with one button and no email or password field. `/login?error=MSG122&google_domain=company.example` and the other codes show their texts; `error=MSG07`, `error=MSG120` and `error=xyz` show an empty area. In mock mode the click opens the mock sign-in; each outcome lands on the role's page (or `returnTo`) or back on Login with its message, and "suspended" shows the MSG120 full page. The YAML shows the whole redirect contract.
**Files:**
- Modify: `frontend/src/features/auth/pages/LoginPage.tsx`, `frontend/src/shared/auth/sign-in.ts`, `frontend/src/app/router/routes.tsx` (the `/dev/sign-in` route, dev only), `frontend/openapi/tsolve-api.yaml`
- Create: `frontend/src/app/dev/MockSignInPage.tsx`
- Generate only: `frontend/src/shared/api/generated/**`
- Test: `frontend/src/features/auth/pages/LoginPage.test.tsx`, `frontend/src/app/dev/MockSignInPage.test.tsx`, `frontend/src/shared/auth/sign-in.test.ts`
**Dependencies:** user approval of the reworded spec (AC-001.42); the FE Lead's agreement to the `shared/auth/sign-in.ts` and `app/dev` edits (given 28/10, Q5).
**PR / Jira:** PR 1, branch `feat/KAN-229-identity-login-screen`, Jira KAN-229.
**Estimated size:** small–medium (~250 lines)
**Verification:** Tests name AC-001.40–.43; design risk rows 39 and 40. Targeted: `cd frontend && npm run test -- src/features/auth src/app/dev/MockSignInPage.test.tsx src/shared/auth/sign-in.test.ts`, then `npm run generate:api` with no diff. Browser check at 1366 × 768 in mock mode.

## Task 8: State values, sign-in start, authorize and the minimal login page — KAN-103

**Story:** STORY-001
**Feature:** sign-in-start
**Acceptance criteria:** AC-001.1, AC-001.2, AC-001.3, AC-001.4, AC-001.5, AC-001.6, AC-001.28, AC-001.39
**Description:** Build the state core in `identity/signin`: the `sign_in_states` table, `SignedToken` (HMAC-SHA256 with `TSOLVE_SIGNING_KEY`), `SignInStateService` (create with exactly one target, `returnTo` filter, nonce and PKCE verifier on the server, 10 minutes, single use), `SignInStartController` for a Workspace site and for `admin.` (no `hd`), `AuthorizeController` on `login.` only (302 to Google with scope, state, nonce, PKCE `S256`, the redirect URI built from `TSOLVE_LOGIN_ORIGIN`, `hd` for a Workspace), `AuthHosts` (404 off `login.`), and `LoginPage` (the minimal HTML page with the exact Report3 text, no link, no script). Add `AuthSecurityConfig` for the four redirect paths, the env values of the design's Configuration table (startup fails without them) and `spring-security-oauth2-jose` in the pom. Written against the `WorkspaceLookup` / `TenantScope` ports with test doubles (Q11); the platform adapters come in this task only if platform Tasks 1, 4 and 5 are already on `main`.
**What Hiếu can see or try:** the tests show every state rule: a Workspace and an `admin.` start each answer 302 to `login.` `authorize`; `authorize` answers 302 to Google with `hd` for the Workspace only; a replayed, expired or edited state answers 400 with the MSG141 page; `authorize` on any other host answers 404. Once the platform adapters are in, `acme.localhost/api/v1/auth/sign-in` on the local stack leads to Google's account chooser for the company domain.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/AuthSecurityConfig.java`, `backend/src/main/java/vn/tsolve/identity/signin/{SignInStateService,SignInState,SignInStateRepository,SignedToken,SignInStartController,AuthorizeController,GoogleOidcClient,AuthHosts,LoginPage,SignInProperties}.java`, `backend/src/main/java/vn/tsolve/identity/signin/port/{WorkspaceLookup,WorkspaceRef,TenantScope}.java`, `backend/src/main/resources/db/changelog/changes/<next>-identity-sign-in-states.sql`
- Modify: `backend/pom.xml`, `backend/src/main/resources/application.yml`, `backend/src/main/resources/db/changelog/db.changelog-master.xml`, `.env.example`
- Test: `backend/src/test/java/vn/tsolve/identity/signin/{SignInStateServiceTest,SignInStartControllerTest,AuthorizeControllerTest,LoginPageTest,SignInPropertiesTest}.java`, test doubles in `backend/src/test/java/vn/tsolve/identity/signin/port/`
**Dependencies:** Task 7 is not needed. Platform Tasks 1 and 5 (KAN-99) and Task 4 (KAN-98) only through the ports (test doubles until they merge). be-foundation `problem-errors` is not needed: the failures are the HTML page and redirects, and the 404 test asserts only the status.
**PR / Jira:** PR 2, branch `feat/KAN-103-identity-sign-in-start`, Jira KAN-103.
**Estimated size:** medium–large
**Verification:** Tests name AC-001.1–.6, .28 and .39; design risk rows 19, 20 and 38. Targeted: `cd backend && mvn -Dtest='SignInStateServiceTest,SignInStartControllerTest,AuthorizeControllerTest,LoginPageTest,SignInPropertiesTest' test`; full `mvn verify` before the commit.

## Task 9: Callback checks and the Account store — KAN-103

**Story:** STORY-001
**Feature:** sign-in-callback
**Acceptance criteria:** AC-001.7, AC-001.8, AC-001.9, AC-001.10, AC-001.11, AC-001.12, AC-001.13, AC-001.14, AC-001.15, AC-001.16
**Description:** Add the `accounts` and `account_roles` tenant tables and `identity/account` (entity, repository with the email compared ignoring case, demo Accounts for the two seed Workspaces of platform Task 15). Build `CallbackController` on `login.`: consume the state, take the target only from it, run the Google error / code exchange / ID token checks (`GoogleOidcClient`, `IdTokenVerifier` on `NimbusJwtDecoder`), then Workspace Active, `hd`, Account exists and Account Active in the AC-001.14 order inside `TenantScope.runIn(state.workspaceId)`, and redirect to `/login?error=…` on the Workspace site at the first failure. On success update the full name and sign-in times, and issue a hand-off code (`hand_off_codes` table, `HandOffCodeService.issue`) with a 302 to `complete` on the Workspace site. Write the platform adapters `PlatformWorkspaceLookup` and `PlatformTenantScope` here if Task 8 did not. Tests use the fake issuer (WireMock + test JWKS, Q10); AC-001.7 and .15 are two-Workspace tests.
**What Hiếu can see or try:** a sign-in (fake issuer locally, real Google on staging) comes back to `/login?error=…` for each failure: cancel → MSG06, wrong domain → MSG122, unknown → MSG09, deactivated → MSG10, suspended → MSG120; a good one reaches `/api/v1/auth/complete?code=…`. The full name and sign-in times appear in the database.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/account/{Account,AccountRole,AccountStatus,AccountRepository}.java`, `backend/src/main/java/vn/tsolve/identity/signin/{CallbackController,IdTokenVerifier,HandOffCode,HandOffCodeRepository,HandOffCodeService}.java`, `backend/src/main/java/vn/tsolve/identity/signin/port/{PlatformWorkspaceLookup,PlatformTenantScope}.java` (if not in Task 8), `backend/src/main/resources/db/changelog/changes/<next>-identity-accounts.sql`, `backend/src/main/resources/db/changelog/changes/<next>-identity-hand-off-codes.sql`
- Modify: `backend/src/main/java/vn/tsolve/identity/signin/GoogleOidcClient.java`, `backend/pom.xml` (WireMock, test scope), `backend/src/main/resources/db/changelog/db.changelog-master.xml`, the demo seed of platform Task 15
- Test: `backend/src/test/java/vn/tsolve/identity/signin/{CallbackControllerTest,IdTokenVerifierTest,HandOffCodeServiceTest,FakeGoogleIssuer}.java`, `backend/src/test/java/vn/tsolve/identity/account/AccountRepositoryTest.java`
**Dependencies:** Task 8; platform Task 2 (tenant base entity, KAN-98) and Task 1 `runIn` (KAN-99) on `main`; platform Tasks 4 and 5 for the adapters.
**PR / Jira:** PR 3, branch `feat/KAN-103-identity-sign-in-callback`, Jira KAN-103. May pass ~400 lines; the reason goes in the PR.
**Estimated size:** large
**Verification:** Tests name AC-001.7–.16; design risk rows 20–26 and 42. Targeted: `cd backend && mvn -Dtest='CallbackControllerTest,IdTokenVerifierTest,HandOffCodeServiceTest,AccountRepositoryTest' test`; full `mvn verify` before the commit.

## Task 10: Hand-off, session cookie and the per-request session rules — KAN-104

**Story:** STORY-001
**Feature:** workspace-session
**Acceptance criteria:** AC-001.17, AC-001.18, AC-001.19, AC-001.20, AC-001.21, AC-001.22, AC-001.23, AC-001.24, AC-001.25, AC-001.26, AC-001.27
**Description:** Add the `web_sessions` table and `identity/session`: `CompleteController` redeems a hand-off code once (atomic `used_at` update, 60 seconds, only on the site it names), creates a new session and sets `__Host-TSOLVE_SESSION` (HttpOnly, Secure, SameSite=Lax, Path `/`, no Domain), then redirects to `returnTo` or `/`. `WebSessionFilter` applies, in the design's order: no session on `login.`/`api.`; MSG123 on a host or kind mismatch without ending the session; MSG07 past 8 hours or 60 idle minutes; the Account re-read with MSG10 for a Deactivated Account; a `TSolveUser` with the current Roles. A request without `X-TSolve-Background: 1` counts as activity. Add `SessionCleanup`. AC-001.17 and .20 are two-Workspace tests.
**What Hiếu can see or try:** a full sign-in end to end: the browser lands on `returnTo`, or on the role's page through `/`. DevTools shows a host-only HttpOnly `__Host-TSOLVE_SESSION` cookie. The same cookie copied to the other Workspace site or to `admin.` → MSG123. After 60 idle minutes → MSG07. Deactivating the Account → MSG10 at the next request.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/session/{CompleteController,WebSession,WebSessionRepository,WebSessionFilter,SessionCleanup,SessionCookie}.java`, `backend/src/main/resources/db/changelog/changes/<next>-identity-web-sessions.sql`
- Modify: `backend/src/main/java/vn/tsolve/identity/AuthSecurityConfig.java`, `backend/src/main/java/vn/tsolve/identity/signin/HandOffCodeService.java` (redeem), `backend/src/main/resources/db/changelog/db.changelog-master.xml`, `.env.example` (the local-cookie note of risk row 34)
- Test: `backend/src/test/java/vn/tsolve/identity/session/{CompleteControllerTest,WebSessionFilterTest,WebSessionRepositoryTest,SessionCleanupTest}.java`
**Dependencies:** Task 9; be-foundation `roles-security` (`TSolveUser`, the deny-by-default chain, ProblemDetail 401 bodies) on `main`.
**PR / Jira:** PR 4, branch `feat/KAN-104-identity-workspace-session`, Jira KAN-104.
**Estimated size:** large
**Verification:** Tests name AC-001.17–.27; design risk rows 27–34, 41 and 42. Targeted: `cd backend && mvn -Dtest='CompleteControllerTest,WebSessionFilterTest,WebSessionRepositoryTest,SessionCleanupTest' test`; full `mvn verify` before the commit. Local browser run of the whole sign-in (risk row 34).

## Task 11: Platform Console sign-in — KAN-103

**Story:** STORY-001
**Feature:** operator-sign-in
**Acceptance criteria:** AC-001.29, AC-001.30, AC-001.31, AC-001.32, AC-001.33, AC-001.34
**Description:** Finish the Platform Console target: at the callback, after the AC-001.6 and AC-001.9 checks, an email in `PLATFORM_OPERATOR_EMAILS` (trimmed, ignoring case) gets a hand-off code for `admin.` and a 302 to `complete` on `TSOLVE_ADMIN_ORIGIN`; any other email → `/login?error=MSG135` on `admin.`; a Google or token failure → MSG06 on `admin.`. The session made there is an Operator session (an `OperatorPrincipal`, no Account, no Workspace, no Role). `WebSessionFilter`: an Operator session on a Workspace site → MSG123; an email no longer in the list → end + MSG135; the 8 h / 60 min rules apply. AC-001.32 is a two-Workspace test.
**What Hiếu can see or try:** on `admin.localhost`, an allow-listed email reaches the Platform Console placeholder; any other email → MSG135; the Operator cookie copied to a Workspace site → MSG123.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/session/{OperatorAllowList,OperatorPrincipal}.java`
- Modify: `backend/src/main/java/vn/tsolve/identity/signin/CallbackController.java`, `backend/src/main/java/vn/tsolve/identity/signin/HandOffCodeService.java`, `backend/src/main/java/vn/tsolve/identity/session/WebSessionFilter.java`, `.env.example`
- Test: `backend/src/test/java/vn/tsolve/identity/signin/OperatorSignInTest.java`, `backend/src/test/java/vn/tsolve/identity/session/OperatorSessionTest.java`
**Dependencies:** Task 10.
**PR / Jira:** PR 5 (with Task 12 if both are small), branch `feat/KAN-103-identity-operator-sign-in`, Jira KAN-103.
**Estimated size:** small–medium
**Verification:** Tests name AC-001.29–.34; design risk rows 29, 35 and 36. Targeted: `cd backend && mvn -Dtest='OperatorSignInTest,OperatorSessionTest' test`; full `mvn verify` before the commit.

## Task 12: Jira account link as a sign-in target — KAN-103

**Story:** STORY-001
**Feature:** jira-link-sign-in
**Acceptance criteria:** AC-001.35, AC-001.36, AC-001.37, AC-001.38
**Description:** Add `SignInStateService.createJiraLinkUrl(workspaceId, trackerAccountId)` for `jira-app`, which returns the `authorize` URL on the central sign-in origin with a state value that carries both. At the callback, a Jira-link state runs the AC-001.14 checks and, when all pass, records the link in that Workspace (`jira_account_links`, replacing an earlier link of the same tracker account) and shows the minimal page with MSG75 (`{tracker_name}` = Jira), with no web session. A failure shows the minimal page with the first failure's message (MSG121 for a Workspace that is not Active; MSG122 with `{google_domain}`) and records no link. The tracker account comes only from the state.
**What Hiếu can see or try:** nothing on screen until `jira-app` exists; the tests (or a dev-only call of `createJiraLinkUrl`) show the MSG75, MSG121 and MSG122 pages on `login.`.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/jiralink/{JiraAccountLink,JiraAccountLinkRepository}.java`, `backend/src/main/resources/db/changelog/changes/<next>-identity-jira-account-links.sql`
- Modify: `backend/src/main/java/vn/tsolve/identity/signin/SignInStateService.java`, `backend/src/main/java/vn/tsolve/identity/signin/CallbackController.java`, `backend/src/main/java/vn/tsolve/identity/signin/LoginPage.java`, `backend/src/main/resources/db/changelog/db.changelog-master.xml`
- Test: `backend/src/test/java/vn/tsolve/identity/jiralink/{JiraLinkSignInTest,JiraAccountLinkRepositoryTest}.java`
**Dependencies:** Task 9 (Task 10 is not needed).
**PR / Jira:** PR 5 with Task 11 if both are small, else its own PR; branch `feat/KAN-103-identity-jira-link-sign-in`, Jira KAN-103.
**Estimated size:** small–medium
**Verification:** Tests name AC-001.35–.38; design risk row 37. Targeted: `cd backend && mvn -Dtest='JiraLinkSignInTest,JiraAccountLinkRepositoryTest' test`; full `mvn verify` before the commit.

## Task 13: Verify and complete included Project handover — KAN-111 (draft)

**Story:** STORY-008
**Feature:** project-handover
**Acceptance criteria:** AC-008.1–AC-008.9
**Description:** After the current spec is approved, update the issue branch from main with merged KAN-109 and map the existing included handover to the nine criteria. Reuse its popup, generated hooks, eligibility lookup, status/role mutations and atomic MSW handling. Add only missing tests or fixes; if every behavior already exists, deliver verification and traceability rather than duplicate code. Preserve AC-006/007 tests and Space handover behavior.
**Files:** Existing `frontend/src/features/users/components/HandOverResponsibilitiesModal.tsx`, `UserStatusActions.tsx`, `hooks/useRoleUpdateFlow.ts`, `hooks/useUserMutations.ts`, `mocks/state.ts`, `mocks/handover.ts`, `mocks/status-handlers.ts`, `mocks/roles-handler.ts`, and relevant tests in `pages/`, `mocks/` or `components/`. `docs/bug-log.md` only for reproduced defects. No new endpoint, route, dependency or manual generated-code edit.
**Dependencies:** User approval of STORY-008; KAN-109 merged into main for the first caller slice, KAN-110 merged before its caller and final completion; branch `feat/KAN-111-identity-project-handover` refreshed from that main. User decision 2026-10-29: wait, do not import dependency branches now. Do not mark the feature in-progress or approve the spec automatically during preparation.
**Estimated size:** small verification/gap-filling slice, reassess after examining the merged dependencies.
**Verification:** Map each AC-008 criterion to tests; run `bun harnessclaude/hooks/sdd.ts trace identity STORY-008`; run frontend `format:check`, `lint`, `typecheck`, `test`, `build`, and `generate:api` with no generated diff. Browser-check caller flows, errors, cancellation and recipients at 1366 × 768. These checks are pending. Report actual changed files and results; propose `feat(identity): complete project handover (KAN-111)` only if implementation changes are needed, or `test(identity): verify project handover (KAN-111)` for a tests-only result. Ask for commit approval before committing or starting KAN-112; never push or merge main without the user's instruction.
