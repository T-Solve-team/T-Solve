---
module: identity
date: 2026-10-25
total_tasks: 20
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

Tasks 7–12 (with 9b) build STORY-001 in feedback-first order (design: "STORY-001: central sign-in and the session per
Workspace site"); Task 14 is the platform follow-up. Every AC-001.1–AC-001.50 is in exactly one task. One PR per task, each on its own branch
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
- Modify: `backend/pom.xml`, `backend/src/main/resources/application.yml`, `backend/src/main/resources/db/changelog/db.changelog-master.xml`, `.env.example`; ticket's `backend/src/test/java/vn/tsolve/ticket/service/impl/ImportConfirmIT.java` and `backend/src/test/java/vn/tsolve/ticket/controller/ImportControllerTest.java` (test properties only: the six new required env values and the new security chain; Q5, because the PR cannot be green without it)
- Test: `backend/src/test/java/vn/tsolve/identity/signin/{SignInStateServiceTest,SignedTokenTest,SignInStartControllerTest,AuthorizeControllerTest,LoginPageTest,SignInPropertiesTest}.java`, `backend/src/test/java/vn/tsolve/identity/AuthSecurityConfigTest.java`, `SignInStateRepositoryIT` (Testcontainers, the `ImportConfirmIT` pattern, needs Docker), test doubles in `backend/src/test/java/vn/tsolve/identity/signin/port/`
**Decisions (Hiếu, 2026-10-29):** Q1 `AuthSecurityConfig` also adds a second, lowest-order `SecurityFilterChain` (`anyRequest().authenticated()`, 401, no form login, no HTTP Basic), tested in `AuthSecurityConfigTest`; be-foundation `roles-security` replaces it later. Q2 `GET /api/v1/auth/sign-in` on a host that is no Workspace site and not the Platform Console origin answers 404, status only, no state. Q3 `SignInStateRepositoryIT`: the check constraint rejects `WORKSPACE` without `workspace_id` and `PLATFORM_CONSOLE` with one; `markUsed` returns 1 then 0, and 0 after `expires_at`. Q4 before the QA tests, a developer step adds empty skeletons of the Task 8 classes with the agreed signatures (methods throw `UnsupportedOperationException`, controllers mapped but answer 501). Q5 as in Modify above.
**Dependencies:** Task 7 is not needed. Platform Tasks 1 and 5 (KAN-99) and Task 4 (KAN-98) only through the ports (test doubles until they merge). be-foundation `problem-errors` is not needed: the failures are the HTML page and redirects, and the 404 test asserts only the status.
**PR / Jira:** PR 2, branch `feat/KAN-230-identity-sign-in-start`, Jira KAN-230 (Subtask of KAN-103).
**Estimated size:** medium–large
**Verification:** Tests name AC-001.1–.6, .28 and .39; design risk rows 19, 20, 38, 43 and 44. Targeted: `cd backend && mvn -Dtest='SignInStateServiceTest,SignedTokenTest,SignInStartControllerTest,AuthorizeControllerTest,LoginPageTest,SignInPropertiesTest,AuthSecurityConfigTest' -Dit.test=SignInStateRepositoryIT verify` (the IT needs Docker); full `mvn verify` before the commit.

## Task 9: The callback refuses forged, replayed or cookie-less returns and Google failures — KAN-231

**Story:** STORY-001
**Feature:** sign-in-callback
**Acceptance criteria:** AC-001.7, AC-001.8, AC-001.9, AC-001.44, AC-001.45, AC-001.46, AC-001.47, AC-001.48, AC-001.49, AC-001.50
**Description:** First of the two Task 9 PRs (KAN-231 Q11). Login CSRF (design row 45, KAN-231 Q1–Q5): add `authorized_at` and `browser_binding_hash` to `sign_in_states` in a new changeset (Task 8's `004-identity-sign-in-states.sql` is not edited). `AuthorizeController` marks the state as authorized with one conditional update (the `IdentityClock` time passed as a parameter, `expires_at > :now`, never the database `now()`) and sets `__Host-TSOLVE_SIGNIN` (32 random bytes, base64url; HttpOnly, Secure, SameSite=Lax, Path `/`, no Domain, Max-Age 600) with its 302 to Google, which carries `Cache-Control: no-store`; a state already authorized gets the 400 MSG141 page and no cookie (`SignInCookie` makes, hashes and expires the cookie). Build `CallbackController` on `login.`: consume the state, take the target only from it, compare the cookie hash in constant time (MSG141 page without a match), find the state's Workspace through `WorkspaceLookup.findById` (gone → MSG141 page, AC-001.50; no Google domain → MSG141 page, AC-001.49), then Google's `error`, the code exchange (`GoogleOidcClient`, `RestClient`, 5 s / 10 s timeouts) and the ID token (`IdTokenVerifier` on `NimbusJwtDecoder`): any failure → `/login?error=MSG06` on the Workspace site (AC-001.8/.9). Every callback answer expires the cookie (AC-001.47). A Workspace with no Google domain is refused at sign-in start too (302 `/login?error=MSG141`, no state, `WARN` with the Workspace id only; AC-001.48) and at `authorize` (AC-001.49). Add `IdentityClock` (bean in `SignInConfig`) and move `SignInStateService` off the ticket module's `Clock` (KAN-231 Q9). Add the cookie to the `authorize` and `callback` operations of the OpenAPI file and regenerate. Interim answers (design "Sign-in and session rules"): a Platform Console state runs the shared checks (state, cookie, Google), then gets the 400 MSG141 page whatever their result until Task 11 (KAN-231 Q12); a Workspace state that passes the Google step, and a Jira-link state after the shared checks, get the same page until Task 9b and Task 12. Nothing is logged in by this task: no session, no Account, no link, no hand-off code. Tests use the fake issuer (WireMock + test JWKS, Q10); AC-001.7 is a two-Workspace test.
**What Hiếu can see or try:** the tests only (fake issuer), because `NoWorkspaceLookup` still answers 404 on every Workspace site (KAN-231 Q10). They show: the callback without the sign-in cookie, with another browser's cookie, or for a state never authorized → MSG141 page; a second `authorize` for one state → MSG141 page and no cookie; every callback answer expires the cookie; a Google cancel, a failed code exchange, each bad ID token → `/login?error=MSG06` on the Workspace site; a `?workspace=beta` parameter or a spoofed host is ignored; a Workspace with no Google domain → `/login?error=MSG141` at sign-in start and the MSG141 page at `authorize` and the callback; a deleted Workspace → MSG141 page.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/signin/{CallbackController,IdTokenVerifier,SignInCookie,IdentityClock}.java`, `backend/src/main/resources/db/changelog/changes/<next>-identity-sign-in-state-binding.sql`
- Modify: `backend/src/main/java/vn/tsolve/identity/signin/{AuthorizeController,SignInStartController,SignInStateService,SignInState,SignInStateRepository,GoogleOidcClient,SignInConfig}.java`, `backend/pom.xml` (WireMock, test scope), `backend/src/main/resources/db/changelog/db.changelog-master.xml`, `frontend/openapi/tsolve-api.yaml` (the cookie on `authorize` and `callback`)
- Generate only: `frontend/src/shared/api/generated/**`
- Test: `backend/src/test/java/vn/tsolve/identity/signin/{CallbackControllerTest,IdTokenVerifierTest,SignInCookieTest,FakeGoogleIssuer}.java`; extend `AuthorizeControllerTest`, `SignInStartControllerTest`, `SignInStateServiceTest`, `SignInTestBeans` and `SignInStateRepositoryIT` (the authorize-once update: 1 then 0; 0 after `used_at` or `expires_at`; the both-null-or-both-set constraint)
**Decisions (Hiếu, 2026-10-31, KAN-231 plan):** Q1 = A (`authorize` sets the cookie, the callback checks it, authorize-once); Q2 = B (separate random value, only its SHA-256 stored, constant-time compare); Q3 = A (one cookie, Lax, 600 s; two tabs at once → one or both tabs get MSG141 and start again: the later tab signs in only if its callback comes first, because the earlier tab's MSG141 answer expires the cookie; design row 47); Q4 = A (MSG141 page, state consumed); Q5 = A (all three targets); Q8 = A (no Google domain refused at all three steps); Q9 = C (`IdentityClock`); Q10 = A (ports and doubles); Q11 = B (this PR); Q12 = A (Console state → MSG141 page until Task 11); Q14 = A (Workspace gone → MSG141 page). Decided in planning: no `code` and no `error` → MSG06; no `email` claim → MSG06; new changeset for the new columns; the Workspace and Jira-link interim page (builder check, same rule as Q12).
**Dependencies:** Task 8 (merged). The ports and test doubles only; no platform task (KAN-231 Q10). Hiếu's approval of the 2026-10-31 spec change before any code.
**PR / Jira:** PR 3 (first of the two Task 9 PRs), branch `feat/KAN-231-identity-sign-in-callback`, Jira KAN-231.
**Estimated size:** medium–large
**Verification:** Tests name AC-001.7–.9 and AC-001.44–.50; design risk rows 20–23, 26, 34, 42, 45 and 47–52 (row 34: the tests assert the `__Host-TSOLVE_SIGNIN` attributes; the local browser check of the cookie on `http://login.localhost` and its `.env.example` note come with Task 10's local run, since no local sign-in reaches `authorize` while `NoWorkspaceLookup` answers 404). Targeted: `cd backend && mvn -Dtest='CallbackControllerTest,IdTokenVerifierTest,SignInCookieTest,AuthorizeControllerTest,SignInStartControllerTest,SignInStateServiceTest' -Dit.test=SignInStateRepositoryIT verify` (the IT needs Docker); `cd frontend && npm run generate:api` with no diff after the YAML edit; full `mvn verify` before the commit.

## Task 9b: Workspace and Account checks, the Account store and the hand-off code — KAN-235

**Story:** STORY-001
**Feature:** account-checks
**Acceptance criteria:** AC-001.10, AC-001.11, AC-001.12, AC-001.13, AC-001.14, AC-001.15, AC-001.16
**Description:** Second of the two Task 9 PRs (KAN-231 Q11). Add the `accounts` and `account_roles` tables (KAN-231 Q10: each with its own `workspace_id` column; no base entity, no foreign key, no row-level security yet; `accounts.email` stored lower-case and unique on the whole platform, Q13) and `identity/account` (`Account`, `AccountRole`, `AccountStatus`, `AccountRepository` whose every method takes the Workspace id, e.g. `findByWorkspaceIdAndEmailIgnoreCase`). Add `LocalTenantScope` (the interim production `TenantScope`, a `ThreadLocal` cleared in a `finally`). In `CallbackController`, replace Task 9's interim MSG141 page for a Workspace state by the rest of the AC-001.14 order: Workspace Active (MSG120), `hd` against the Workspace's Google domain ignoring case (MSG122 with `google_domain`), Account exists in the state's Workspace (MSG09), Account Active (MSG10), each as `/login?error=…` on the Workspace site; the Account lookup and update run inside `TenantScope.runIn(state.workspaceId)` and filter on that Workspace id. On success set the full name from `name` (unchanged when empty), record the sign-in time and the first sign-in time when there is none, issue a hand-off code (`hand_off_codes` table, `HandOffCodeService.issue`; redemption is Task 10) and answer 302 to `/api/v1/auth/complete?code=…` on the Workspace site. Jira-link and Console states keep Task 9's interim page (Tasks 12 and 11). AC-001.15 is a two-Workspace test: Workspace B's Account email signing in on Workspace A gets MSG09, and B's Account is untouched (Q13). No demo Accounts and no edit to platform Task 15's seed; they come with the platform follow-up (design "Ports").
**What Hiếu can see or try:** the tests only (fake issuer), while `NoWorkspaceLookup` answers 404. They show each failure in the AC-001.14 order (suspended → MSG120, wrong or missing `hd` → MSG122 with the domain, unknown email → MSG09, Deactivated → MSG10, and only the first failure reported), the case-insensitive match of email and `hd`, the full name and sign-in times written on success, and a 302 to `complete` with a hand-off code; B's Account is never found from Workspace A.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/account/{Account,AccountRole,AccountStatus,AccountRepository}.java`, `backend/src/main/java/vn/tsolve/identity/signin/{HandOffCode,HandOffCodeRepository,HandOffCodeService}.java`, `backend/src/main/java/vn/tsolve/identity/signin/port/LocalTenantScope.java`, `backend/src/main/resources/db/changelog/changes/<next>-identity-accounts.sql`, `backend/src/main/resources/db/changelog/changes/<next>-identity-hand-off-codes.sql`
- Modify: `backend/src/main/java/vn/tsolve/identity/signin/{CallbackController,SignInConfig}.java` (`SignInConfig` declares the `LocalTenantScope` bean), `backend/src/main/resources/db/changelog/db.changelog-master.xml`
- Test: extend `backend/src/test/java/vn/tsolve/identity/signin/CallbackControllerTest.java`; create `backend/src/test/java/vn/tsolve/identity/signin/HandOffCodeServiceTest.java`, `backend/src/test/java/vn/tsolve/identity/signin/port/LocalTenantScopeTest.java`, `backend/src/test/java/vn/tsolve/identity/account/AccountRepositoryIT.java` (Testcontainers, the `SignInStateRepositoryIT` pattern, needs Docker: the platform-wide unique lower-case email, the Workspace filter)
**Decisions (Hiếu, 2026-10-31, KAN-231 plan):** Q10 = A (own `workspace_id` columns, explicit Workspace id, `LocalTenantScope`, later platform follow-up, no Task 15 seed edit); Q11 = B (this PR); Q13 = A (email unique on the whole platform; design row 24 and the AC-001.15 test reworded).
**Dependencies:** Task 9 merged. The ports and test doubles only; no platform task (KAN-231 Q10).
**PR / Jira:** PR 3b (second of the two Task 9 PRs), branch `feat/KAN-235-identity-account-checks`, Jira KAN-235 (Subtask of KAN-103).
**Estimated size:** medium–large
**Verification:** Tests name AC-001.10–.16; design risk rows 24–26 and 42. Targeted: `cd backend && mvn -Dtest='CallbackControllerTest,HandOffCodeServiceTest,LocalTenantScopeTest' -Dit.test=AccountRepositoryIT verify` (the IT needs Docker); full `mvn verify` before the commit.

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
**Dependencies:** Task 9b (it redeems the hand-off codes Task 9b issues); be-foundation `roles-security` (`TSolveUser`, the deny-by-default chain, ProblemDetail 401 bodies) on `main`.
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
**Dependencies:** Task 9b (`accounts` and the full AC-001.14 order; Task 10 is not needed).
**PR / Jira:** PR 5 with Task 11 if both are small, else its own PR; branch `feat/KAN-103-identity-jira-link-sign-in`, Jira KAN-103.
**Estimated size:** small–medium
**Verification:** Tests name AC-001.35–.38; design risk row 37. Targeted: `cd backend && mvn -Dtest='JiraLinkSignInTest,JiraAccountLinkRepositoryTest' test`; full `mvn verify` before the commit.

## Task 14: Platform adapters and Account tenancy — Jira issue when KAN-98 / KAN-99 land (stub)

**Story:** STORY-001
**Feature:** platform-adapters
**Acceptance criteria:** none new. It ends the backend AGENTS.md §7 exception of design Key decisions "Platform gap" (KAN-231 Q10) and meets the spec's Interface Contracts "Database" paragraph (tenant-table convention, platform AC-001.8); the AC-001.7 and AC-001.15 two-Workspace tests keep passing on the platform adapters.
**Description:** Stub, planned in detail when the platform tasks are on `main`. Replace the interim production beans by the platform adapters: `PlatformWorkspaceLookup` replaces `NoWorkspaceLookup` and `PlatformTenantScope` replaces `LocalTenantScope` (the `runIn` calls stay; only the beans change). Move `Account` and `account_roles` onto the platform base entity (`TenantEntity`) and add a new changeset with the foreign key `workspace_id → workspaces (id)` and row-level security (enabled and forced, the platform's tenant policy) for `accounts` and `account_roles`; Task 9b's changeset is never edited. Add demo Accounts for the seed Workspaces of platform Task 15. Afterwards the whole sign-in can be tried on the local stack.
**What Hiếu can see or try:** a sign-in on the local stack against a seed Workspace (fake issuer), and the convention guard test of platform AC-001.8 passing for `accounts` and `account_roles`.
**Files (to confirm when planned):**
- Create: `backend/src/main/java/vn/tsolve/identity/signin/port/{PlatformWorkspaceLookup,PlatformTenantScope}.java`, `backend/src/main/resources/db/changelog/changes/<next>-identity-accounts-tenancy.sql`, the demo Accounts changeset next to platform Task 15's seed
- Modify: `backend/src/main/java/vn/tsolve/identity/account/Account.java`, `backend/src/main/java/vn/tsolve/identity/signin/SignInConfig.java`, `backend/src/main/resources/db/changelog/db.changelog-master.xml`
- Delete: `backend/src/main/java/vn/tsolve/identity/signin/port/{NoWorkspaceLookup,LocalTenantScope}.java` and `LocalTenantScopeTest`
- Test: `backend/src/test/java/vn/tsolve/identity/signin/port/{PlatformWorkspaceLookupTest,PlatformTenantScopeTest}.java`; extend `AccountRepositoryIT` (row-level security hides the other Workspace's rows)
**Dependencies:** platform KAN-98 (Task 2 base entity, Task 4) and KAN-99 (Tasks 1 and 5, `runIn`) on `main`; Task 9b.
**PR / Jira:** its own PR; the Jira issue is created when KAN-98 and KAN-99 land.
**Estimated size:** small–medium
**Verification:** to be set when planned; at least the AC-001.7 and AC-001.15 tests and the platform convention guard test, then the full `mvn verify`. identity cannot reach `completed` before this task is done.

## Task 13: Verify and complete included Project handover — KAN-111 (draft)

**Story:** STORY-008
**Feature:** project-handover
**Acceptance criteria:** AC-008.1–AC-008.9
**Description:** After the current spec is approved, update the issue branch from main with merged KAN-109 and map the existing included handover to the nine criteria. Reuse its popup, generated hooks, eligibility lookup, status/role mutations and atomic MSW handling. Add only missing tests or fixes; if every behavior already exists, deliver verification and traceability rather than duplicate code. Preserve AC-006/007 tests and Space handover behavior.
**Files:** Existing `frontend/src/features/users/components/HandOverResponsibilitiesModal.tsx`, `UserStatusActions.tsx`, `hooks/useRoleUpdateFlow.ts`, `hooks/useUserMutations.ts`, `mocks/state.ts`, `mocks/handover.ts`, `mocks/status-handlers.ts`, `mocks/roles-handler.ts`, and relevant tests in `pages/`, `mocks/` or `components/`. `docs/bug-log.md` only for reproduced defects. No new endpoint, route, dependency or manual generated-code edit.
**Dependencies:** User approval of STORY-008; KAN-109 merged into main for the first caller slice, KAN-110 merged before its caller and final completion; branch `feat/KAN-111-identity-project-handover` refreshed from that main. User decision 2026-10-29: wait, do not import dependency branches now. Do not mark the feature in-progress or approve the spec automatically during preparation.
**Estimated size:** small verification/gap-filling slice, reassess after examining the merged dependencies.
**Verification:** Map each AC-008 criterion to tests; run `bun harnessclaude/hooks/sdd.ts trace identity STORY-008`; run frontend `format:check`, `lint`, `typecheck`, `test`, `build`, and `generate:api` with no generated diff. Browser-check caller flows, errors, cancellation and recipients at 1366 × 768. These checks are pending. Report actual changed files and results; propose `feat(identity): complete project handover (KAN-111)` only if implementation changes are needed, or `test(identity): verify project handover (KAN-111)` for a tests-only result. Ask for commit approval before committing or starting KAN-112; never push or merge main without the user's instruction.

## STORY-003 tasks (KAN-160 plan, 2026-10-31; stacked on `chore/KAN-231-identity-plan`, PR #94)

Tasks 15–18 build My Profile (STORY-003, KAN-160 "[UC-03] Xem profile của tôi", 2 points, sprint S3 08/11–15/11) on
the web app with MSW mocks (D1), plus the "Log out" button of STORY-002's AC-002.7 (Q7). Design: "STORY-003: My
Profile — KAN-160 plan, 2026-10-31". Every web-app criterion — AC-003.1, .2, .3, .11, .12, .13, .14 and AC-002.7 —
is in exactly one task; the backend criteria AC-003.4–AC-003.10 are in Task 19 (stub, no feature yet, Q11).
AC-002.1–AC-002.6 (logout on the backend) are not planned here; they wait for a KAN-106 plan. PRs, vertical slices
first (D2, Q10): PR 1 = Task 15, PR 2 = Task 16, PR 3 = Tasks 17 + 18. Each PR on its own branch
`feat/KAN-<n>-identity-<desc>` from an up-to-date `main`; the Jira issues (one per PR, under KAN-160) are created
after this plan is final ("Jira: to create"). Before each commit: the full frontend verify (CLAUDE.md project rules).

## Task 15: My Profile account, Projects and managed Spaces, with Log out — Jira: to create

**Story:** STORY-003, STORY-002 (AC-002.7 only)
**Feature:** profile-account
**Acceptance criteria:** AC-003.1, AC-003.2, AC-002.7
**Description:** Replace the 1.2.1 placeholder with the Figma layout (design "Figma layout per frame"), all from the session, no request. Account card: its own header row "Account" with a "Log out" button at the top right (`useSignOut()`, loading until it settles, one request); Full name, Email, Roles (`ROLE_LABELS` in `ROLES` order), Department (PM only), Departments. Projects card: a `DataTable` without paging — Jira project key, Department, Role in Project (`PROJECT_ROLE_LABELS`: "Project Manager" / "Ticket Resolver", Q3). Spaces card with "Spaces I manage" for a Knowledge Manager (`SpaceList`, folder icons). Every empty row, list or card is hidden (Q4). No contract change.
**What Hiếu can see or try:** `/profile` in mock mode for each dev user: `tr-two-departments` shows Departments and its Projects with "Ticket Resolver"; `pm` shows the Department row and "Project Manager" rows and no Spaces card; `km` and `pm-km` show "Spaces I manage"; `employee` shows only the Account card. "Log out" opens Login, and the browser's back button shows no profile.
**Files:**
- Modify: `frontend/src/features/my-profile/pages/MyProfilePage.tsx`
- Create: `frontend/src/features/my-profile/components/{AccountSection,ProjectsSection,SpacesSection,SpaceList}.tsx`, `frontend/src/features/my-profile/lib/labels.ts`
- Test: `frontend/src/features/my-profile/pages/MyProfilePage.test.tsx`
**Dependencies:** user approval of the identity spec (current); this plan merged, or the branch rebased onto `main` after PR #94.
**PR / Jira:** PR 1, branch `feat/KAN-<n>-identity-profile-account`; Jira: to create.
**Estimated size:** small–medium (~220 lines)
**Verification:** Tests name AC-003.1, AC-003.2, AC-002.7; design risk rows 53–57, 72, 74, 80, 81. Targeted: `cd frontend && npm run test -- src/features/my-profile`. Browser check at 1366 × 768 in mock mode against frames (b) and (c).
**Done when:** the three criteria's tests pass; the full frontend verify passes; the browser check matches the frames except the design's "Figma vs decisions" items.

## Task 16: "Spaces I can view": contract, mock and list — Jira: to create

**Story:** STORY-003
**Feature:** profile-viewable-spaces
**Acceptance criteria:** AC-003.3. Builds and tests the Spaces half of AC-003.14 (MSG06 in the Spaces card, account still shown); AC-003.14 is formally covered only in Task 18, where both sections exist. Contract mirror (not passed here) of AC-003.4, AC-003.5, AC-003.10.
**Description:** Add `GET /api/v1/me/spaces` (`getMyViewableSpaces`, new tag `profile`, 200 `SpaceRef[]` sorted by name, 401 `Problem`) to the OpenAPI file and regenerate. Add the feature's viewable-Space fixtures per dev user (design "Mock data") and the handler: the user's Spaces sorted by name, 401 `MSG07` with no dev user, extra query parameters ignored. `useMyViewableSpaces` loads on page open; the Spaces card gets "Spaces I can view" below "Spaces I manage", with its own spinner, MSG06 on failure (no retry, Q9), and no list when empty (Q4); the card is not rendered when both lists are empty after a successful load.
**What Hiếu can see or try:** `/profile` for `tr`, `km` (its managed Space in both lists) and `admin-tr` (a long name cut with "…"); `employee` has no Spaces card. The YAML shows `getMyViewableSpaces` under `profile`.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/features/my-profile/components/SpacesSection.tsx`, `frontend/src/features/my-profile/pages/MyProfilePage.tsx`, `frontend/src/features/my-profile/mocks/handlers.ts`
- Create: `frontend/src/features/my-profile/hooks/useMyViewableSpaces.ts`, `frontend/src/features/my-profile/mocks/data.ts`
- Generate only: `frontend/src/shared/api/generated/**`
- Test: `frontend/src/features/my-profile/pages/MyProfilePage.test.tsx`, `frontend/src/features/my-profile/mocks/handlers.test.ts`
**Dependencies:** Task 15.
**PR / Jira:** PR 2, branch `feat/KAN-<n>-identity-profile-viewable-spaces`; Jira: to create.
**Estimated size:** small–medium (~200 lines, generated code excluded)
**Verification:** Tests name AC-003.3 and "AC-003.14 spaces"; handler tests "contract mirror of AC-003.4" (sorting) and "of AC-003.10" (401); design risk rows 58, 62, 66, 67, 76, 78, 79. Targeted: `cd frontend && npm run test -- src/features/my-profile`, then `npm run generate:api` with no diff. Browser check at 1366 × 768 in mock mode against frames (main) and (b).
**Done when:** the AC-003.3 and Spaces error tests pass; `generate:api` leaves no diff; the full frontend verify passes.

## Task 17: My Solutions contract and mock — Jira: to create

**Story:** STORY-003
**Feature:** my-solutions-contract
**Acceptance criteria:** none passed in the web app. Contract mirror (not passed here) of AC-003.6, AC-003.7, AC-003.8, AC-003.9, AC-003.10.
**Description:** Add `GET /api/v1/me/solutions` (`getMySolutions`, tag `profile`, the shared `Page` and `Size` parameters, no `Sort`; 200 `MySolutionPage`, 400 `Problem` with `fieldErrors`, 401 `Problem`) and the schemas `MySolutionPage` and `MySolution` exactly as the spec, reusing `PageMetadata` and `TicketStatus`; regenerate. Add the My Solutions fixtures per dev user (0 / 3 / 5 / 20 / 21 / 45 rows, all six statuses, Vietnamese and 200-character titles, `canOpen: false` rows, own UUIDs) and the handler: newest `updatedAt` first, defaults 0 / 20, 400 `MSG06` `fieldErrors` per wrong parameter, empty `content` past the end with the real totals, 401 `MSG07`, extra parameters ignored. No screen change (Task 18 uses it in the same PR).
**What Hiếu can see or try:** the YAML shows `getMySolutions`, `MySolutionPage` and `MySolution`; the handler tests are green.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/features/my-profile/mocks/data.ts`, `frontend/src/features/my-profile/mocks/handlers.ts`
- Generate only: `frontend/src/shared/api/generated/**`
- Test: `frontend/src/features/my-profile/mocks/handlers.test.ts`
**Dependencies:** Task 16 (the `profile` tag, `mocks/data.ts`).
**PR / Jira:** PR 3 with Task 18, one branch `feat/KAN-<n>-identity-my-solutions`. About 450 lines with Task 18, over the ~400 guide: the contract, its mock and the table are one behaviour a user can try, so they stay one vertical slice (Q10). Jira: to create.
**Estimated size:** small–medium (~200 lines, generated code excluded)
**Verification:** Handler tests named "contract mirror of AC-003.6 / .7 / .9 / .10"; design risk rows 60, 61, 70, 71, 78. Targeted: `cd frontend && npm run test -- src/features/my-profile/mocks`, then `npm run generate:api` with no diff.
**Done when:** the handler tests pass and `generate:api` leaves no diff; the full frontend verify passes (format:check, lint, typecheck, test, build, `generate:api` no diff) — Task 18's Done-when repeats it, since both are in PR 3.

## Task 18: My Solutions table — Jira: to create

**Story:** STORY-003
**Feature:** my-solutions-table
**Acceptance criteria:** AC-003.11, AC-003.12, AC-003.13, AC-003.14
**Description:** Add the My Solutions card: `useMySolutions` (generated hook, `keepPreviousData`) with `{ page: urlPage - 1, size: 20 }` from `useListSearchParams({ field: 'updatedAt', order: 'desc' })` (the hook requires a default sort; it is passed only to satisfy the signature and its `sort` / `apiPaging` are ignored; the request has no `sort`); `DataTable` columns Ticket ID (`cells.link` to `paths.ticketDetail(ticketId)` when `canOpen`, plain text otherwise; the row is not clickable and the shared `DataTable` is not changed, R1 A), Title, Project, Status (`cells.tag`), Updated (`formatDate`), fixed widths for 1366 px (120 / 180 / 170 / 120, decided by Hiếu 31/10); pagination 20, no size changer; MSG04 when empty; MSG06 on failure with no retry; a page past the last goes to the last page (history replace, decided by Hiếu 31/10). The account and Spaces stay when My Solutions fails (AC-003.14, which closes with the Spaces half built in Task 16).
**What Hiếu can see or try:** `/profile` for `tr`: 20 rows, three pages, `?page=2` in the URL, back from Ticket Detail keeps the page; clicking the Ticket ID of an openable row opens Ticket Detail; the `canOpen: false` rows are plain text and do nothing. `employee` and `km` show "No results found." (MSG04). `pm` shows exactly one page.
**Files:**
- Modify: `frontend/src/features/my-profile/pages/MyProfilePage.tsx`
- Create: `frontend/src/features/my-profile/components/MySolutionsSection.tsx`, `frontend/src/features/my-profile/hooks/useMySolutions.ts`
- Test: `frontend/src/features/my-profile/components/MySolutionsSection.test.tsx`, `frontend/src/features/my-profile/pages/MyProfilePage.test.tsx`
**Dependencies:** Task 17.
**PR / Jira:** PR 3 with Task 17, branch `feat/KAN-<n>-identity-my-solutions`; Jira: to create.
**Estimated size:** medium (~250 lines)
**Verification:** Tests name AC-003.11–.14; design risk rows 58, 59, 61, 63–69, 72–75, 77, 79, 81. Targeted: `cd frontend && npm run test -- src/features/my-profile`. Browser check at 1366 × 768 in mock mode against frames (main) and (d).
**Done when:** the four criteria's tests pass; the full frontend verify passes with `generate:api` leaving no diff; `bun harnessclaude/hooks/sdd.ts trace identity STORY-003` shows AC-003.1–.3 and .11–.14 covered.

## Task 19: My Profile backend endpoints — deferred (stub)

**Story:** STORY-003
**Feature:** none yet (no `progress.json` feature until the task has a Jira issue, Q11)
**Acceptance criteria:** AC-003.4, AC-003.5, AC-003.6, AC-003.7, AC-003.8, AC-003.9, AC-003.10
**Description:** Stub, planned in detail when its dependencies are ready. The backend side of `GET /api/v1/me/spaces` and `GET /api/v1/me/solutions`, exactly as the contract of Tasks 16 and 17: Spaces sorted by name from the `wiki` viewable Space lookup, empty while it is missing; the written-ticket lookup's page, newest `updatedAt` first, empty while it is missing; 400 `fieldErrors` `MSG06` for a bad `page` or `size`; 401 `MSG07` with no session; both lookups called only with the signed-in Account's id in its own Workspace, nothing taken from the request (GBR-37).
**What Hiếu can see or try:** nothing now (record only); later the same screens on the real backend.
**Files (to confirm when planned):** a `vn.tsolve.identity` controller for the two endpoints, and its tests (MockMvc; two Workspaces for AC-003.7).
**Dependencies:** `ticket-repository` written-ticket lookup (KAN-132 ticket state machine, To Do); `wiki` viewable Space lookup; be-foundation (session, ProblemDetail errors). No Jira issue yet.
**PR / Jira:** none now; no Jira issue yet.
**Estimated size:** small–medium (to confirm when planned)
**Verification:** to be set when planned; JUnit names end with the AC id; then the full `mvn verify`.
