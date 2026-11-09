---
module: identity
date: 2026-10-25
total_tasks: 31
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

**HLA change, 2026-11-03 (KAN-231 step 2, Hiếu: Q1–Q4 = A; design "HLA change").** The multi-tenant HLA changes Tasks
9b, 10, 11 (kept), 12 and 14; Tasks 8 and 9 keep their tables and tests until Task 14 moves them. Task 9 is unchanged.
Code conflicts of the step-1 check and their owners: C1 `sign_in_states` in the tenant changelog and C2 its JPA entity
and repository on the default datasource → Task 14 (the move to the registry database); C3 one datasource and one
changelog → `platform` (Task 14 only adapts); C4 `SignInStartController` resolves the host itself and does not check the
Workspace's status → Task 14 (`PlatformWorkspaceLookup` reads the bound Tenant Context; AC-001.10 still refuses a
suspended Workspace at the callback); C5 `AuthSecurityConfig`'s interim chain has no tenant step → be-foundation
`roles-security` and `platform` replace it (Task 10 waits for `roles-security`; Task 14 re-checks that the tenant step
runs before `WebSessionFilter`).

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

## Task 8: State values, sign-in start, authorize and the minimal login page — KAN-230

> **Superseded 2026-11-05 (KAN-275).** Done and merged; its state values, `authorize`, `login.` page and `sign_in_states` are removed by Task 24 (K12). The sign-in start and the `returnTo` filter stay (Task 23b).

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
**HLA note (2026-11-03, Q2):** this task is done and unchanged. Its `sign_in_states` table and `SignInStateRepository` move to the Platform / Tenant Registry database and datasource in Task 14 (conflicts C1, C2); the `SignInState` javadoc and the `004` comment ("shared (non-tenant)") then say "Registry DB".
**Dependencies:** Task 7 is not needed. Platform Tasks 1 and 5 (KAN-99) and Task 4 (KAN-98) only through the ports (test doubles until they merge). be-foundation `problem-errors` is not needed: the failures are the HTML page and redirects, and the 404 test asserts only the status.
**PR / Jira:** PR 2, branch `feat/KAN-230-identity-sign-in-start`, Jira KAN-230 (Subtask of KAN-103).
**Estimated size:** medium–large
**Verification:** Tests name AC-001.1–.6, .28 and .39; design risk rows 19, 20, 38, 43 and 44. Targeted: `cd backend && mvn -Dtest='SignInStateServiceTest,SignedTokenTest,SignInStartControllerTest,AuthorizeControllerTest,LoginPageTest,SignInPropertiesTest,AuthSecurityConfigTest' -Dit.test=SignInStateRepositoryIT verify` (the IT needs Docker); full `mvn verify` before the commit.

## Task 9: The callback refuses forged, replayed or cookie-less returns and Google failures — KAN-231

> **Superseded 2026-11-05 (KAN-275).** Done and merged; the callback on `login.`, `SignInCookie` and `IdTokenVerifier` are removed by Task 24; the Workspace-site callback is Task 23c (K7, K12).

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

> **Superseded in part 2026-11-05 (KAN-275).** Done and merged. `app_user`, `Account*`, `LocalTenantScope` and the two-Workspace test stay (adapted by Tasks 25a and 25b: `sub`, new check order). The hand-off code and `hand_off_code` are removed by Task 24 (K12).

**Story:** STORY-001
**Feature:** account-checks
**Acceptance criteria:** AC-001.10, AC-001.11, AC-001.12, AC-001.13, AC-001.14, AC-001.15, AC-001.16
**Description:** Second of the two Task 9 PRs (KAN-231 Q11). Add the `app_user` table (KAN-235 Q1 = A, then Hiếu 04/11: the Database Tables PDF wins on names, so the key is `id`, `full_name` is `VARCHAR(100)`, and the table has the two extra columns `first_sign_in_at` and `last_sign_in_at`, all in `00-baseline/T-Solve_Database_Tables.md`; the Java class stays `Account`; `user_role` and `AccountRole` move to Task 10) (HLA Q4, 2026-11-03: one database per company, so **no** `workspace_id` column; no base entity, no foreign key, no row-level security yet; `app_user.email` stored lower-case and unique inside the database; one email in one Workspace across the platform is the registry's `workspace_email`, called when an Account is added in STORY-005, not here) and `identity/account` (`Account`, `AccountStatus`, `AccountRepository`, e.g. `findByEmail` on the lower-cased column, and `AccountSignInService` for the lookup and the update, reached only inside `TenantScope.runIn`). Task 9b only reads Accounts: it has no `user_role` table and writes no role row (the table comes in Task 10; it stores four Roles, Employee is implicit). Add `LocalTenantScope` (the interim production `TenantScope`, a `ThreadLocal` cleared in a `finally`; `currentWorkspaceId()` throws outside `runIn`, and a nested `runIn` with another Workspace throws `IllegalStateException` without running the inner supplier, KAN-235 Q3 = A; design row 93). In `CallbackController`, replace Task 9's interim MSG141 page for a Workspace state by the rest of the AC-001.14 order: Workspace Active (MSG120), `hd` against the Workspace's Google domain ignoring case (MSG122 with `google_domain`), Account exists in the state's Workspace (MSG09), Account Active (MSG10), each as `/login?error=…` on the Workspace site; the Account lookup and update run inside `TenantScope.runIn(state.workspaceId)`, in that Workspace's database. On success set the full name from `name` (unchanged when the claim is absent, `""` or whitespace-only, else stored trimmed, KAN-235 Q2 = A; a name of more than 100 code points, the `VARCHAR(100)` column of the PDF, leaves the old name unchanged, see the design AC-001.16 note), record the sign-in time and the first sign-in time when there is none, update the Account first, then issue a hand-off code (KAN-235 Q5 = A: if issuing fails the sign-in time stays recorded, no session, 500) (`hand_off_code` table, key `id`, not in the PDF and listed in the companion under "Additions not in the PDF"; interim in today's only changelog and moved to the Registry DB by Task 14, HLA Q2; `HandOffCodeService.issue`; redemption is Task 10) and answer 302 to `/api/v1/auth/complete?code=…` on the Workspace site. Jira-link and Console states keep Task 9's interim page (Tasks 12 and 11). AC-001.15 is a two-Workspace test on two databases (HLA Q4: two databases of one Testcontainers Postgres, routed by a test `TenantScope` double and a test routing `DataSource`, in test sources only): Workspace B's Account email signing in on Workspace A gets MSG09, and B's Account is untouched because A's database does not hold it. No demo Accounts and no edit to platform Task 15's seed; they come with the platform follow-up (design "Ports").
**What Hiếu can see or try:** the tests only (fake issuer), while `NoWorkspaceLookup` answers 404. They show each failure in the AC-001.14 order (suspended → MSG120, wrong or missing `hd` → MSG122 with the domain, unknown email → MSG09, Deactivated → MSG10, and only the first failure reported), the case-insensitive match of email and `hd`, the full name and sign-in times written on success, and a 302 to `complete` with a hand-off code; B's Account is never found from Workspace A (two databases).
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/account/{Account,AccountStatus,AccountRepository,AccountSignInService}.java`, `backend/src/main/java/vn/tsolve/identity/signin/{HandOffCode,HandOffCodeRepository,HandOffCodeService}.java`, `backend/src/main/java/vn/tsolve/identity/signin/port/LocalTenantScope.java`, `backend/src/main/resources/db/changelog/changes/<next>-identity-app-user.sql` (table `app_user` only), `backend/src/main/resources/db/changelog/changes/<next>-identity-hand-off-code.sql`
- Modify: `backend/src/main/java/vn/tsolve/identity/signin/{CallbackController,SignInConfig}.java` (`SignInConfig` declares the `LocalTenantScope` bean), `backend/src/main/resources/db/changelog/db.changelog-master.xml`
- Test: extend `backend/src/test/java/vn/tsolve/identity/signin/CallbackControllerTest.java`; create `backend/src/test/java/vn/tsolve/identity/signin/CallbackAccountIsolationIT.java` (two Workspaces on two databases, AC-001.15, row 24), `backend/src/test/java/vn/tsolve/identity/signin/HandOffCodeServiceTest.java`, `backend/src/test/java/vn/tsolve/identity/signin/port/LocalTenantScopeTest.java`, `backend/src/test/java/vn/tsolve/identity/account/AccountRepositoryIT.java` (Testcontainers, the `SignInStateRepositoryIT` pattern, needs Docker: the unique lower-case email inside one database; two databases, so an Account of one is not found in the other)
**Decisions (Hiếu, 2026-10-31, KAN-231 plan):** Q10 = A (`LocalTenantScope`, later platform follow-up, no Task 15 seed edit); Q11 = B (this PR); Q13 = A (one email in one Workspace across the platform; design row 24 and the AC-001.15 test reworded). **HLA change (Hiếu, 2026-11-03, Q4 = A):** the interim `workspace_id` columns and the explicit Workspace id in repository methods are dropped; email uniqueness across the platform goes through the registry (Task 14 adds the adapter; STORY-005 calls it).
**Size (KAN-235 Q4 = B, Hiếu; KAN-231 Q11 = B):** stays one PR, accepted at about 28 tests and 14 production files.
**Dependencies:** Task 9 merged. The ports and test doubles only; no platform task (KAN-231 Q10).
**PR / Jira:** PR 3b (second of the two Task 9 PRs), branch `feat/KAN-235-identity-account-checks`, Jira KAN-235 (Subtask of KAN-103).
**Estimated size:** medium–large
**Verification:** Tests name AC-001.10–.16; design risk rows 24–26, 42 and 93. Targeted: `cd backend && mvn -Dtest='CallbackControllerTest,HandOffCodeServiceTest,LocalTenantScopeTest' -Dit.test=AccountRepositoryIT verify` (the IT needs Docker); full `mvn verify` before the commit.

## Task 10: Hand-off, session cookie and the per-request session rules — KAN-232

> **Superseded in part 2026-11-05 (KAN-275).** Never committed. Salvaged into Tasks 26a and 26b (Redis session, `WebSessionFilter`, `user_role`, dev seeder); `CompleteController`, `StartCookie`, the changesets `008` and `009`, and the dev fake Google are dropped (K9, K12, Q5, Q8).

**Story:** STORY-001
**Feature:** workspace-session
**Acceptance criteria:** AC-001.17, AC-001.18, AC-001.19, AC-001.20, AC-001.21, AC-001.22, AC-001.23, AC-001.24, AC-001.25, AC-001.26, AC-001.27, AC-001.51
**Description:** HLA change (Hiếu, 2026-11-03, Q3 = A): sessions live in **Redis**, not in a `web_sessions` table. Add the first read of Roles (Hiếu 04/11, moved from Task 9b): the `user_role` table (PDF names: `id`, `user_id`, `role`, `assigned_at`, unique per `(user_id, role)`; no `workspace_id`) in a new changeset, the `AccountRole` class and the read of an Account's Roles for the `TSolveUser`. Add `identity/session` with `WebSessionStore` (one Redis key per session, named by the SHA-256 of the cookie value, holding `kind`, `workspace_id`, `account_id`, `operator_email`, `site_host`, `created_at`, `last_activity_at`; design "Data model"). `CompleteController` redeems a hand-off code once (atomic `used_at` update with the `IdentityClock` time as a parameter, not the database `now()`, 60 seconds, only on the site it names; the update runs first, so a code shown on the wrong site is consumed and the site check then fails with MSG141, KAN-232 Q5, Q6), checks the start cookie (below), creates a new session and sets `__Host-TSOLVE_SESSION` (HttpOnly, Secure, SameSite=Lax, Path `/`, no Domain), then redirects to `returnTo` or `/`. `WebSessionFilter` applies, in the design's order: no session on `login.`/`api.`; MSG123 on a host or kind mismatch without ending the session; MSG07 past 8 hours or 60 idle minutes; the Account re-read with MSG10 for a Deactivated Account; a `TSolveUser` with the current Roles. The limits are checked in the application with `IdentityClock` from the stored times; the key's time to live is a backstop. A request without `X-TSolve-Background: 1` counts as activity. `SessionCleanup` is not built (N API replicas must not each run a scheduler). The `SessionEnded` `EXPIRED` event within 15 minutes (AC-002.4, STORY-002) is planned with KAN-106, not here: keyspace events or a worker job, chosen then, because an expiry event does not carry the Account id and Workspace id; this task stores both in the session so that either works. Add Redis to the local stack: a `redis` service in `docker-compose.yml` (Docker Desktop), `spring-boot-starter-data-redis` and `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` in `application.yml` and `.env.example`. Redis for sessions is not an eviction cache. AC-001.17 and .20 are two-Workspace tests.
**Added by the KAN-232 plan (Hiếu, 2026-11-04):**
- **Login CSRF at `complete` (Q7, AC-001.51).** Sign-in start on a Workspace site sets the host-only cookie `__Host-TSOLVE_START` (Secure, HttpOnly, SameSite=Lax, Path `/`, Max-Age 600, no Domain). Its SHA-256 travels sign-in state → hand-off code: a new `start_hash` column on each, each its own changeset (`<next>-identity-sign-in-state-start-hash.sql`, `<next>-identity-hand-off-code-start-hash.sql`). `SignInStateService`, `HandOffCodeService` and `CallbackController` (copy the hash) and the sign-in start controller are modified for it. `complete` reads the cookie, compares the hash in constant time and clears the cookie in every answer; no cookie or another browser's → `/login?error=MSG141`, no session, the code still consumed. The Platform Console target is Task 11's.
- **Filter rules (Q1, Q2, Q10).** Account row gone at the re-read → end the session, 401 MSG07. A session cookie that names no session on a Workspace site → anonymous request and `__Host-TSOLVE_SESSION=; Max-Age=0`. A session is honoured only on `admin.` or on a host that `WorkspaceLookup` resolves as a Workspace site; no `TSOLVE_API_ORIGIN`.
- **Dev profile only (Q2 earlier; Google refused `http://login.localhost:<port>/...` as a redirect URI, so the local run does not use real Google).** Spring profile `dev`: a dev-only `WorkspaceLookup` for `acme.localhost` and `beta.localhost`; hard-coded FAKE Google domains and emails for them; a dev seed of Accounts and Roles (idempotent; Liquibase `context: dev` or a dev-profile `ApplicationRunner`, as the QA plan says); and a dev-profile fake Google served by the backend itself (authorization, token and JWKS endpoints, signed with a throw-away key, letting the person pick a seeded Account). Real Google is used only from the staging deploy on, with the existing `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`.
- **`common/security` (Q11).** A small edit, the BE lead has agreed: `SecurityConfig` registers `WebSessionFilter`; `SecurityProblemWriter` gets the MSG10 and MSG123 texts.
- **Scope.** One task, not split (Hiếu), about 22+ production files. Logout (Q8) stays with KAN-106.
**What Hiếu can see or try:** sign in through the dev fake issuer on `http://acme.localhost:<port>` and see the `__Host-TSOLVE_SESSION` cookie set (`HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`, no `Domain`): a full sign-in end to end, the browser lands on `returnTo`, or on the role's page through `/`. DevTools shows a host-only HttpOnly `__Host-TSOLVE_SESSION` cookie (and `__Host-TSOLVE_START` cleared). Opening the same `complete?code=…` link in a second browser → `/login?error=MSG141`, no session. The same cookie copied to the other Workspace site or to `admin.` → MSG123. After 60 idle minutes → MSG07. Deactivating the Account → MSG10 at the next request.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/session/{CompleteController,WebSession,WebSessionStore,WebSessionFilter,SessionCookie}.java`, `backend/src/main/java/vn/tsolve/identity/account/AccountRole.java`, the dev-profile classes under `backend/src/main/java/vn/tsolve/identity/dev/` (`DevWorkspaceLookup`, `DevAccountSeeder`, and the fake Google issuer: controller and its security chain), `backend/src/main/resources/db/changelog/changes/<next>-identity-user-role.sql` (table `user_role`), `<next>-identity-sign-in-state-start-hash.sql` and `<next>-identity-hand-off-code-start-hash.sql`
- Modify: `backend/src/main/java/vn/tsolve/identity/AuthSecurityConfig.java`, `backend/src/main/java/vn/tsolve/identity/signin/{HandOffCodeService,SignInStateService,CallbackController}.java` and `SignInStartController` (redeem; the `start_hash` and the start cookie), `backend/src/main/java/vn/tsolve/common/security/{SecurityConfig,SecurityProblemWriter}.java`, `backend/src/main/java/vn/tsolve/identity/account/{Account,AccountRepository}.java` (the Roles), `backend/src/main/resources/db/changelog/db.changelog-master.xml`, `backend/pom.xml` (`spring-boot-starter-data-redis`), `backend/src/main/resources/application.yml`, `docker-compose.yml` (the `redis` service), `.env.example` (the Redis values and the local-cookie note of risk row 34)
- Test: `backend/src/test/java/vn/tsolve/identity/session/{CompleteControllerTest,WebSessionFilterTest,WebSessionStoreIT}.java` (`WebSessionStoreIT` on Testcontainers Redis, the `SignInStateRepositoryIT` pattern, needs Docker; Redis down and Redis restarted: design rows 90 and 91)
**Dependencies:** Task 9b (it redeems the hand-off codes Task 9b issues); be-foundation `roles-security` (`TSolveUser`, the deny-by-default chain, ProblemDetail 401 bodies; it also replaces `AuthSecurityConfig`'s interim chain, conflict C5) on `main`; a Redis for the local stack and the tests (Docker Desktop).
**PR / Jira:** PR 4, branch `feat/KAN-232-identity-workspace-session`, Jira KAN-232 (Subtask of KAN-104).
**Estimated size:** large
**Verification:** Tests name AC-001.17–.27 and AC-001.51; design risk rows 27–34, 41, 42, 90, 91 and 100–102. Targeted: `cd backend && mvn -Dtest='CompleteControllerTest,WebSessionFilterTest' -Dit.test=WebSessionStoreIT verify` (the IT needs Docker); full `mvn verify` before the commit. Local browser run of the whole sign-in through the dev fake issuer on `http://acme.localhost:<port>` (risk row 34, shown here, not in Task 14).

## Task 11: Platform Console sign-in — KAN-233

> **Superseded 2026-11-05 (KAN-275).** Rebuilt as Task 29 on the realm `tsolve-ops` (Q1 = B).

**Story:** STORY-001
**Feature:** operator-sign-in
**Acceptance criteria:** AC-001.29, AC-001.30, AC-001.31, AC-001.32, AC-001.33, AC-001.34
**Description:** Unchanged by the HLA change (Q1 = A: the Platform Console on `admin.` stays). Finish the Platform Console target: at the callback, after the AC-001.6 and AC-001.9 checks, an email in `PLATFORM_OPERATOR_EMAILS` (trimmed, ignoring case) gets a hand-off code for `admin.` and a 302 to `complete` on `TSOLVE_ADMIN_ORIGIN`; any other email → `/login?error=MSG135` on `admin.`; a Google or token failure → MSG06 on `admin.`. The session made there is an Operator session (an `OperatorPrincipal`, no Account, no Workspace, no Role). `WebSessionFilter`: an Operator session on a Workspace site → MSG123; an email no longer in the list → end + MSG135; the 8 h / 60 min rules apply. AC-001.32 is a two-Workspace test.
**What Hiếu can see or try:** on `admin.localhost`, an allow-listed email reaches the Platform Console placeholder; any other email → MSG135; the Operator cookie copied to a Workspace site → MSG123.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/session/{OperatorAllowList,OperatorPrincipal}.java`
- Modify: `backend/src/main/java/vn/tsolve/identity/signin/CallbackController.java`, `backend/src/main/java/vn/tsolve/identity/signin/HandOffCodeService.java`, `backend/src/main/java/vn/tsolve/identity/session/WebSessionFilter.java`, `.env.example`
- Test: `backend/src/test/java/vn/tsolve/identity/signin/OperatorSignInTest.java`, `backend/src/test/java/vn/tsolve/identity/session/OperatorSessionTest.java`
**Dependencies:** Task 10.
**PR / Jira:** PR 5 (with Task 12 if both are small), branch `feat/KAN-233-identity-operator-sign-in`, Jira KAN-233 (Subtask of KAN-103; reopened 2026-11-04 after HLA Q1 = A brought the Console back).
**Estimated size:** small–medium
**Verification:** Tests name AC-001.29–.34; design risk rows 29, 35 and 36. Targeted: `cd backend && mvn -Dtest='OperatorSignInTest,OperatorSessionTest' test`; full `mvn verify` before the commit.

## Task 12: Jira account link as a sign-in target — KAN-234

> **Superseded 2026-11-05 (KAN-275).** Replaced by Task 30: a link request and a confirm page, no sign-in state (Q2 = A; design row 46 becomes row 121).

**Story:** STORY-001
**Feature:** jira-link-sign-in
**Acceptance criteria:** AC-001.35, AC-001.36, AC-001.37, AC-001.38
**Description:** Add `SignInStateService.createJiraLinkUrl(workspaceId, trackerAccountId)` for `jira-app`, which returns the `authorize` URL on the central sign-in origin with a state value that carries both. At the callback, a Jira-link state runs the AC-001.14 checks and, when all pass, records the link in that Workspace's database (`jira_account_links`, no `workspace_id` column, HLA Q4; replacing an earlier link of the same tracker account) and shows the minimal page with MSG75 (`{tracker_name}` = Jira), with no web session. A failure shows the minimal page with the first failure's message (MSG121 for a Workspace that is not Active; MSG122 with `{google_domain}`) and records no link. The tracker account comes only from the state.
**What Hiếu can see or try:** nothing on screen until `jira-app` exists; the tests (or a dev-only call of `createJiraLinkUrl`) show the MSG75, MSG121 and MSG122 pages on `login.`.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/jiralink/{JiraAccountLink,JiraAccountLinkRepository}.java`, `backend/src/main/resources/db/changelog/changes/<next>-identity-jira-account-links.sql`
- Modify: `backend/src/main/java/vn/tsolve/identity/signin/SignInStateService.java`, `backend/src/main/java/vn/tsolve/identity/signin/CallbackController.java`, `backend/src/main/java/vn/tsolve/identity/signin/LoginPage.java`, `backend/src/main/resources/db/changelog/db.changelog-master.xml`
- Test: `backend/src/test/java/vn/tsolve/identity/jiralink/{JiraLinkSignInTest,JiraAccountLinkRepositoryTest}.java`
**Dependencies:** Task 9b (`app_user` and the full AC-001.14 order; Task 10 is not needed).
**PR / Jira:** PR 5 with Task 11 if both are small, else its own PR; branch `feat/KAN-234-identity-jira-link-sign-in`, Jira KAN-234 (Subtask of KAN-103).
**Estimated size:** small–medium
**Verification:** Tests name AC-001.35–.38; design risk row 37. Targeted: `cd backend && mvn -Dtest='JiraLinkSignInTest,JiraAccountLinkRepositoryTest' test`; full `mvn verify` before the commit.

## Task 14: Platform adapters, registry stores and Account tenancy — KAN-247 (stub)

> **Changed 2026-11-05 (KAN-275).** The sign-in tables no longer move to the registry: they are dropped by Task 24 (so the parts of this stub about `sign_in_states` and `hand_off_code` fall away). The adapters, `workspace_email`, the tenant row guard and the `FirstAdminCreator` stay. `WorkspaceSignInProvisioner` moves into the platform's public API when `WorkspaceProvisioning` exists (design, "Platform change requests").

**Story:** STORY-001, STORY-010
**Feature:** platform-adapters
**Acceptance criteria:** AC-010.1…AC-010.8 (the port `FirstAdminCreator`, added 2026-11-04; traces to platform AC-013.14…AC-013.19). For STORY-001 none new. It ends the backend AGENTS.md §7 exception of design Key decisions "Platform gap" (KAN-231 Q10, HLA Q4) and meets the HLA change of 2026-11-03: the sign-in states and hand-off codes in the Platform / Tenant Registry database (Q2), one database per company for the account tables (Q4). The AC-001.7 and AC-001.15 two-Workspace tests keep passing on the platform adapters.
**Description:** Stub, planned in detail when the platform tasks are on `main`. It carries the fixes for the code conflicts of the step-1 check (tasks.md header): (C1, C2) move `sign_in_states` and `hand_off_code` into the registry changelog (`db/changelog/catalog/`) with a new changeset, give `SignInStateRepository` and `HandOffCodeRepository` the registry datasource, never edit Task 8's `004` or Task 9's changesets, and change the `SignInState` javadoc and the `004` comment to "Registry DB"; (C4) replace `NoWorkspaceLookup` by `PlatformWorkspaceLookup`, which reads the Registry DB, and make `SignInStartController` read the Workspace the Tenant Resolver bound (site, status, database mapping) instead of resolving the host itself (`WorkspaceLookup.findBySiteHost`); an unknown or suspended Workspace is refused by the resolver, and AC-001.10 still refuses a suspended Workspace at the callback; (C3) adapt to the Registry DB, the Database Router and the Jobs DB that `platform` builds (identity's own datasource and changelog settings move with them); (C5) check that the platform's tenant step runs before `WebSessionFilter` once be-foundation `roles-security` and `platform` have replaced `AuthSecurityConfig`'s interim chain. Replace `LocalTenantScope` by `PlatformTenantScope` (the `runIn` calls stay; only the beans change), so every `Account`, `user_role` and `jira_account_links` query goes to the bound Workspace's database through the Database Router. Add the adapter for the registry's `workspace_email` (one email in one Workspace across the platform; STORY-005 calls it when an Account is added). If `platform` defines a per-database RLS guard, apply it to `app_user` and `user_role` in a new changeset (no foreign key to a Workspace). Add demo Accounts for the seed Workspaces of platform Task 15. **The port (STORY-010):** implement `vn.tsolve.platform.service.FirstAdminCreator` with `AccountFirstAdminCreator` in `identity.account` (design "Ports", `FirstAdminCreator`): `UUID createFirstAdmin(UUID workspaceId, String email)` runs in one `@Transactional` method, checks the bound Workspace against `workspaceId` (`TenantScope.currentWorkspaceId()`), lower-cases and validates the email, refuses a duplicate Account, saves the `Account` (ACTIVE, empty full name, no sign-in time) and its `AccountRole` Admin, and returns the id; it never calls `claimEmail`, creates no credential, and logs no email. Test-first, with a test double `TenantScope` and then the two-database router test. Write the start-up test that the `ops` profile boots with this bean. Afterwards the whole sign-in can be tried on the local stack.
**What Hiếu can see or try:** a sign-in on the local stack against a seed Workspace (fake issuer), the sign-in states and hand-off codes found in the Registry DB and not in a company database, and the platform's convention guard test passing for `app_user` and `user_role`.
**Files (to confirm when planned):**
- Create: `backend/src/main/java/vn/tsolve/identity/signin/port/{PlatformWorkspaceLookup,PlatformTenantScope,PlatformWorkspaceEmails}.java`, `backend/src/main/java/vn/tsolve/identity/account/AccountFirstAdminCreator.java`, `backend/src/main/resources/db/changelog/catalog/<next>-identity-sign-in-states-and-codes.sql` (the move), `backend/src/main/resources/db/changelog/changes/<next>-identity-app-user-rls-guard.sql` (only if `platform` defines the guard), the demo Accounts changeset next to platform Task 15's seed
- Modify: `backend/src/main/java/vn/tsolve/identity/signin/{SignInState,SignInStateRepository,HandOffCode,HandOffCodeRepository,SignInStartController,SignInConfig}.java`, `backend/src/main/java/vn/tsolve/identity/account/Account.java` (if the platform convention needs it), `backend/src/main/resources/db/changelog/db.changelog-master.xml`, `backend/src/main/resources/application.yml` (C3)
- Delete: `backend/src/main/java/vn/tsolve/identity/signin/port/{NoWorkspaceLookup,LocalTenantScope}.java` and `LocalTenantScopeTest`
- Test: `backend/src/test/java/vn/tsolve/identity/signin/port/{PlatformWorkspaceLookupTest,PlatformTenantScopeTest}.java`, `backend/src/test/java/vn/tsolve/identity/account/AccountFirstAdminCreatorTest.java` and `…IT.java` (names cite AC-010.1…AC-010.8; design risk rows 94 to 99); extend `SignInStateRepositoryIT` (the registry datasource) and `AccountRepositoryIT` (two company databases through the router; the RLS guard if any)
**Dependencies:** platform KAN-98 (Task 4 registry, and the tenant-database convention and guard of Task 2) and KAN-99 (Tasks 1 and 5, `runIn`, Tenant Resolver, Database Router) on `main`; platform Task 7 (the `FirstAdminCreator` port and `WorkspaceProvisioning`, KAN-98 numbering of the platform plan) for the port interface; Task 9b; Task 10 (so the sessions already run on Redis).
**PR / Jira:** its own PR, branch `feat/KAN-247-identity-platform-adapters`, Jira KAN-247 (Subtask of KAN-103, created 2026-11-04 as a stub, blocked by KAN-98 and KAN-99; the key is filled in by a follow-up after the Jira round).
**Estimated size:** medium
**Verification:** to be set when planned; at least the AC-001.7 and AC-001.15 tests on the router, design risk rows 92 to 99 (94 to 99: the first Admin), the tests of AC-010.1…AC-010.8, and the platform convention guard test, then the full `mvn verify`. identity cannot reach `completed` before this task is done.

## Task 13: Verify and complete included Project handover — KAN-111 (draft)

**Story:** STORY-008
**Feature:** project-handover
**Acceptance criteria:** AC-008.1–AC-008.9
**Description:** After the current spec is approved, update the issue branch from main with merged KAN-109 and map the existing included handover to the nine criteria. Reuse its popup, generated hooks, eligibility lookup, status/role mutations and atomic MSW handling. Add only missing tests or fixes; if every behavior already exists, deliver verification and traceability rather than duplicate code. Preserve AC-006/007 tests and Space handover behavior.
**Files:** Existing `frontend/src/features/users/components/HandOverResponsibilitiesModal.tsx`, `UserStatusActions.tsx`, `hooks/useRoleUpdateFlow.ts`, `hooks/useUserMutations.ts`, `mocks/state.ts`, `mocks/handover.ts`, `mocks/status-handlers.ts`, `mocks/roles-handler.ts`, and relevant tests in `pages/`, `mocks/` or `components/`. `docs/bug-log.md` only for reproduced defects. No new endpoint, route, dependency or manual generated-code edit.
**Dependencies:** User approval of STORY-008; KAN-109 merged into main for the first caller slice, KAN-110 merged before its caller and final completion; branch `feat/KAN-111-identity-project-handover` refreshed from that main. User decision 2026-10-29: wait, do not import dependency branches now. Do not mark the feature in-progress or approve the spec automatically during preparation.
**Estimated size:** small verification/gap-filling slice, reassess after examining the merged dependencies.
**Verification:** Map each AC-008 criterion to tests; run `bun harnessclaude/hooks/sdd.ts trace identity STORY-008`; run frontend `format:check`, `lint`, `typecheck`, `test`, `build`, and `generate:api` with no generated diff. Browser-check caller flows, errors, cancellation and recipients at 1366 × 768. These checks are pending. Report actual changed files and results; propose `feat(identity): complete project handover (KAN-111)` only if implementation changes are needed, or `test(identity): verify project handover (KAN-111)` for a tests-only result. Ask for commit approval before committing or starting KAN-112; never push or merge main without the user's instruction.

## Task 22: Verify and complete included Space handover — KAN-112

**Story:** STORY-009
**Feature:** space-handover
**Acceptance criteria:** AC-009.1–AC-009.9
**Description:** Reuse the UC-06/07 popup, generated hooks and atomic MSW mutations. Add missing multi-Space, eligibility, invalid-set, stale-recipient, cancellation, retry, history and reactivation evidence. Clarify the existing OpenAPI proposal without new routes or models. Change runtime code only for a reproduced criterion gap; log every reproduced bug with its regression test.
**Files:** `frontend/src/features/users/components/HandOverResponsibilitiesModal.test.tsx`, `frontend/src/features/users/pages/{UserStatusPage,UserRolesPage}.test.tsx`, focused tests under `frontend/src/features/users/mocks/`, existing implementation files only if a failing test establishes a gap; `frontend/openapi/tsolve-api.yaml` and generated output. `docs/bug-log.md` for established defects.
**Dependencies:** STORY-009 approved; user approved master design and placement in iteration 1 on 2026-10-31; KAN-109/110/111 are on main. Branch `feat/KAN-112-identity-space-handover` from main `4cf0594`.
**Estimated size:** one verification and contract-clarification slice.
**Verification:** Cover master design risk rows S1–S8, trace all AC-009 criteria, run focused and full frontend tests, format/lint/typecheck/build, and generate the API twice without drift. Browser-check both callers at 1366 × 768, including errors, cancellation and eligible choices. Record actual results in task-22-space-handover session/test/review reports. Report the feature and propose a conventional commit for user agreement before committing; no push unless requested.

## STORY-003 tasks (KAN-160 plan, 2026-10-31; stacked on `chore/KAN-231-identity-plan`, PR #94)

Tasks 15–18 build My Profile (STORY-003, KAN-160 "[UC-03] Xem profile của tôi", 2 points, sprint S3 08/11–15/11) on
the web app with MSW mocks (D1), plus the "Log out" button of STORY-002's AC-002.7 (Q7). Design: "STORY-003: My
Profile — KAN-160 plan, 2026-10-31". Every web-app criterion — AC-003.1, .2, .3, .11, .12, .13, .14 and AC-002.7 —
is in exactly one task; the backend criteria AC-003.4–AC-003.10 are in Task 19 (stub, no feature yet, Q11).
AC-002.1–AC-002.6 (logout on the backend) are not planned here; they wait for a KAN-106 plan. PRs, vertical slices
first (D2, Q10): PR 1 = Task 15, PR 2 = Task 16, PR 3 = Tasks 17 + 18. Each PR on its own branch
`feat/KAN-<n>-identity-<desc>` from an up-to-date `main`; the Jira issues (one per PR, under KAN-160) are created
after this plan is final ("Jira: to create"). Before each commit: the full frontend verify (CLAUDE.md project rules).

## Task 15: My Profile account, Projects and managed Spaces, with Log out — KAN-236

**Story:** STORY-003, STORY-002 (AC-002.7 only)
**Feature:** profile-account
**Acceptance criteria:** AC-003.1, AC-003.2, AC-002.7
**Description:** Replace the 1.2.1 placeholder with the Figma layout (design "Figma layout per frame"), all from the session, no request. Account card: its own header row "Account" with a "Log out" button at the top right (`useSignOut()`, loading until it settles, one request); Full name, Email, Roles (`ROLE_LABELS` in `ROLES` order), Department (PM only), Departments. Projects card: a `DataTable` without paging — Jira project key, Department, Role in Project (`PROJECT_ROLE_LABELS`: "Project Manager" / "Ticket Resolver", Q3). Spaces card with "Spaces I manage" for a Knowledge Manager (`SpaceList`, folder icons). Every empty Account row and an empty "Spaces I manage" list is hidden (Q4). The Projects card is always shown; for a user with no Projects it shows the grey text MSG143 "Not a member of any Project." in place of the table (AC-003.16, decided 1/11). That Projects empty state is a follow-up Quick change after Task 15's merge (PR #96), not part of PR 2 (KAN-237). No contract change.
**What Hiếu can see or try:** `/profile` in mock mode for each dev user: `tr-two-departments` shows Departments and its Projects with "Ticket Resolver"; `pm` shows the Department row and "Project Manager" rows; `km` and `pm-km` show "Spaces I manage"; `employee` shows the Account card and, after the follow-up Quick change, a Projects card with "Not a member of any Project.". "Log out" opens Login, and the browser's back button shows no profile.
**Files:**
- Modify: `frontend/src/features/my-profile/pages/MyProfilePage.tsx`
- Create: `frontend/src/features/my-profile/components/{AccountSection,ProjectsSection,SpacesSection,SpaceList}.tsx`, `frontend/src/features/my-profile/lib/labels.ts`
- Test: `frontend/src/features/my-profile/pages/MyProfilePage.test.tsx`
**Dependencies:** user approval of the identity spec (current); this plan merged, or the branch rebased onto `main` after PR #94.
**PR / Jira:** PR 1, branch `feat/KAN-236-identity-profile-account`; Jira: KAN-236.
**Estimated size:** small–medium (~220 lines)
**Verification:** Tests name AC-003.1, AC-003.2, AC-002.7; design risk rows 53–57, 72, 74, 80, 81. Targeted: `cd frontend && npm run test -- src/features/my-profile`. Browser check at 1366 × 768 in mock mode against frames (b) and (c).
**Done when:** the three criteria's tests pass; the full frontend verify passes; the browser check matches the frames except the design's "Figma vs decisions" items.

## Task 16: "Spaces I can view": contract, mock and list — KAN-237

**Story:** STORY-003
**Feature:** profile-viewable-spaces
**Acceptance criteria:** AC-003.3, AC-003.15. Builds and tests the Spaces half of AC-003.14 (MSG06 in the Spaces card, account still shown); AC-003.14 is formally covered only in Task 18, where both sections exist. Contract mirror (not passed here) of AC-003.4, AC-003.5, AC-003.10.
**Description:** Add `GET /api/v1/me/spaces` (`getMyViewableSpaces`, new tag `profile`, 200 `SpaceRef[]` sorted by name, 401 `Problem`) to the OpenAPI file and regenerate. Add the feature's viewable-Space fixtures per dev user (design "Mock data") and the handler: the user's Spaces sorted by name, 401 `MSG07` with no dev user, extra query parameters ignored. `useMyViewableSpaces` loads on page open; the Spaces card gets "Spaces I can view" below "Spaces I manage", with its own spinner shown at once (no 150 ms delay), MSG06 on failure (no retry, Q9), and, when it loaded empty, the grey text MSG142 "No Spaces found" (AC-003.15). The Spaces card is rendered for every user (decided 1/11); "Spaces I manage" still shows only for a Knowledge Manager with managed Spaces.
**What Hiếu can see or try:** `/profile` for `tr`, `km` (its managed Space in both lists) and `admin-tr` (a long name cut with "…"); `employee` shows the Spaces card with "No Spaces found"; no user has a hidden Spaces card. The YAML shows `getMyViewableSpaces` under `profile`.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/features/my-profile/components/SpacesSection.tsx`, `frontend/src/features/my-profile/pages/MyProfilePage.tsx`, `frontend/src/features/my-profile/mocks/handlers.ts`
- Create: `frontend/src/features/my-profile/hooks/useMyViewableSpaces.ts`, `frontend/src/features/my-profile/mocks/data.ts`
- Generate only: `frontend/src/shared/api/generated/**`
- Test: `frontend/src/features/my-profile/pages/MyProfilePage.test.tsx`, `frontend/src/features/my-profile/mocks/handlers.test.ts`
**Dependencies:** Task 15.
**PR / Jira:** PR 2, branch `feat/KAN-237-identity-profile-viewable-spaces`; Jira: KAN-237.
**Estimated size:** small–medium (~200 lines, generated code excluded)
**Verification:** Tests name AC-003.3, AC-003.15 and "AC-003.14 spaces"; handler tests "contract mirror of AC-003.4" (sorting) and "of AC-003.10" (401); design risk rows 58, 62, 66, 67, 76, 78, 79. Targeted: `cd frontend && npm run test -- src/features/my-profile`, then `npm run generate:api` with no diff. Browser check at 1366 × 768 in mock mode against frames (main) and (b).
**Done when:** the AC-003.3, AC-003.15 (spinner at once; "No Spaces found" for `employee`; card shown for every user) and Spaces error tests pass; `generate:api` leaves no diff; the full frontend verify passes.

## Task 17: My Solutions contract and mock — KAN-238

**Story:** STORY-003
**Feature:** my-solutions-contract
**Acceptance criteria:** none passed in the web app. Contract mirror (not passed here) of AC-003.6, AC-003.7, AC-003.8, AC-003.9, AC-003.10, AC-003.17, AC-003.19.
**Description:** Add `GET /api/v1/me/solutions` (`getMySolutions`, tag `profile`, the shared `Page` and `Size` parameters, no `Sort`, plus `status` (`TicketStatus`), `projectId` (uuid), `from` and `to` (date); 200 `MySolutionPage`, 400 `Problem` with `fieldErrors`, 401 and 403 `Problem`) and the schemas `MySolutionPage`, `MySolution` (with `projectId` and the required nullable `latestDecision`) and `ReviewDecision` (APPROVE, REQUEST_CHANGES, REJECT, ticket-review's names), reusing `PageMetadata` and `TicketStatus`; regenerate. Add the My Solutions fixtures per dev user: `tr` gets 45 rows with all six statuses, decisions with and without a comment, Vietnamese and 200-character titles, `canOpen: false` rows and several Projects; a resolver with 0 Solutions (so the empty state can still be tried); `pm` follows AC-003.20 (Project Manager sees the section); `employee` and `km` have no Solutions and no access. The handler: newest `updatedAt` first, defaults 0 / 20, filters of AC-003.19 (inclusive days in UTC+7, AND), 400 `MSG06` `fieldErrors` per wrong parameter (`to` before `from` on `to`), a well-formed foreign `projectId` gives an empty page, empty `content` past the end with the real totals, 401 `MSG07`, 403 `MSG08` for a user without Ticket Resolver or Project Manager, extra parameters ignored. No screen change (Task 18 uses it in the same PR).
**What Hiếu can see or try:** the YAML shows `getMySolutions` with the four filters, `MySolutionPage`, `MySolution` and `ReviewDecision`; the handler tests are green.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/features/my-profile/mocks/data.ts`, `frontend/src/features/my-profile/mocks/handlers.ts`
- Generate only: `frontend/src/shared/api/generated/**`
- Test: `frontend/src/features/my-profile/mocks/handlers.test.ts`
**Dependencies:** Task 16 (the `profile` tag, `mocks/data.ts`).
**PR / Jira:** PR 3 with Tasks 18, 20 and 21, one branch `feat/KAN-238-identity-profile-my-solutions`. About 1,100–1,300 lines for the four tasks, far over the ~400 guide: decided by Hiếu (D3, 1/11) as one PR, because the contract, counters, filters and table are one behaviour a user can try. Jira: KAN-238.
**Estimated size:** small–medium (~200 lines, generated code excluded); about 14 tests
**Verification:** Handler tests named "contract mirror of AC-003.6 / .7 / .9 / .10 / .17 / .19"; design risk rows 60, 61, 70, 71, 78. Targeted: `cd frontend && npm run test -- src/features/my-profile/mocks`, then `npm run generate:api` with no diff.
**Done when:** the handler tests pass and `generate:api` leaves no diff; the full frontend verify passes (format:check, lint, typecheck, test, build, `generate:api` no diff), repeated in Tasks 18, 20 and 21 since all four are in PR 3.

## Task 18: My Solutions table and role gating — KAN-238

**Story:** STORY-003
**Feature:** my-solutions-table
**Acceptance criteria:** AC-003.11, AC-003.12, AC-003.13, AC-003.14, AC-003.20, AC-003.23
**Description:** Add the My Solutions card, rendered only when the session Roles include Ticket Resolver or Project Manager (the check uses the role list; no heading, no placeholder and no request otherwise; if the role is removed while the page is open, the next call is 403 and the section shows MSG08 in an `ErrorState`, the other cards stay). `useMySolutions` (generated hook, `keepPreviousData`) with `{ page: urlPage - 1, size: 20 }` from `useListSearchParams({ field: 'updatedAt', order: 'desc' })` (the hook requires a default sort; it is passed only to satisfy the signature and its `sort` / `apiPaging` are ignored; the request has no `sort`); `DataTable` columns Ticket ID (`cells.link` to `paths.ticketDetail(ticketId)` when `canOpen`, plain text otherwise; the row is not clickable and the shared `DataTable` is not changed, R1 A), Title, Project, Status (`cells.tag`), Latest decision (the comment on one line, ellipsis and a tooltip with the full text, "—" when none) and Date (`updatedAt` with `formatDate`), fixed widths for 1366 px (Ticket ID 120, Project 170, Status 140, Latest decision 260, Date 120, Title the rest with ellipsis and tooltip; to confirm in the browser check); pagination 20, no size changer; immediate spinner while loading; MSG04 when empty (also used for no match, with counters and filters still shown); MSG06 on failure with no retry; a page past the last goes to the last page (history replace, decided by Hiếu 31/10). The account and Spaces stay when My Solutions fails (AC-003.14).
**What Hiếu can see or try:** `/profile` for `tr`: 20 rows, three pages, `?page=2` in the URL, the Latest decision column with a comment, an ellipsis with tooltip, and "—"; back from Ticket Detail keeps the page; clicking the Ticket ID of an openable row opens Ticket Detail; the `canOpen: false` rows are plain text. The resolver with 0 Solutions shows "No results found." (MSG04). `employee` and `km` have no My Solutions section at all.
**Files:**
- Modify: `frontend/src/features/my-profile/pages/MyProfilePage.tsx`
- Create: `frontend/src/features/my-profile/components/MySolutionsSection.tsx`, `frontend/src/features/my-profile/hooks/useMySolutions.ts`, `frontend/src/features/my-profile/lib/canSeeMySolutions.ts`
- Test: `frontend/src/features/my-profile/components/MySolutionsSection.test.tsx`, `frontend/src/features/my-profile/pages/MyProfilePage.test.tsx`
**Dependencies:** Task 17.
**PR / Jira:** PR 3 with Tasks 17, 20 and 21, branch `feat/KAN-238-identity-profile-my-solutions`; Jira: KAN-238.
**Estimated size:** medium (~300 lines); about 12 tests
**Verification:** Tests name AC-003.11–.14, AC-003.20 and AC-003.23 (including "no request without the role" and the 403 MSG08 case); design risk rows 58, 59, 61, 63–69, 72–75, 77, 79, 81 (design row 68 reworded: 403 `MSG08` is now expected on the My Solutions endpoints). Targeted: `cd frontend && npm run test -- src/features/my-profile`. Browser check at 1366 × 768 in mock mode against frames (main) and (d).
**Done when:** the six criteria's tests pass; the full frontend verify passes with `generate:api` leaving no diff; `bun harnessclaude/hooks/sdd.ts trace identity STORY-003` shows AC-003.1–.3, .11–.14, .20 and .23 covered.

## Task 19: My Profile backend endpoints — deferred (stub)

**Story:** STORY-003
**Feature:** none yet (no `progress.json` feature until the task has a Jira issue, Q11)
**Acceptance criteria:** AC-003.4, AC-003.5, AC-003.6, AC-003.7, AC-003.8, AC-003.9, AC-003.10, AC-003.17, AC-003.18, AC-003.19
**Description:** Stub, planned in detail when its dependencies are ready. The backend side of `GET /api/v1/me/spaces`, `GET /api/v1/me/solutions` and `GET /api/v1/me/solutions/summary`, exactly as the contract of Tasks 16 and 17: Spaces sorted by name from the `wiki` viewable Space lookup, empty while it is missing; the written-ticket lookup's page, newest `updatedAt` first, empty while it is missing; 400 `fieldErrors` `MSG06` for a bad `page` or `size`; 401 `MSG07` with no session (checked before the role); 403 `MSG08` for an Account that is neither Ticket Resolver nor Project Manager (AC-003.17); the summary's six counts and distinct Projects (AC-003.18); the `status`, `projectId`, `from`, `to` filters on `updatedAt` in the Workspace day (AC-003.19); `latestDecision` from the written-ticket lookup; both lookups called only with the signed-in Account's id in its own Workspace, nothing taken from the request (GBR-37).
**What Hiếu can see or try:** nothing now (record only); later the same screens on the real backend.
**Files (to confirm when planned):** a `vn.tsolve.identity` controller for the two endpoints, and its tests (MockMvc; two Workspaces for AC-003.7).
**Dependencies:** `ticket-repository` written-ticket lookup (KAN-132 ticket state machine, To Do); `wiki` viewable Space lookup; be-foundation (session, ProblemDetail errors). No Jira issue yet.
**PR / Jira:** Jira KAN-301 (deferred).
**Estimated size:** small–medium (to confirm when planned)
**Verification:** to be set when planned; JUnit names end with the AC id; then the full `mvn verify`.

## Task 20: My Solutions counters — KAN-238

**Story:** STORY-003
**Feature:** my-solutions-counters
**Acceptance criteria:** AC-003.21 (and AC-003.14 for the counters). Contract mirror (not passed here) of AC-003.8, AC-003.10, AC-003.17, AC-003.18.
**Description:** Add `GET /api/v1/me/solutions/summary` (`getMySolutionSummary`, tag `profile`; 200 `MySolutionSummary`, 401 and 403 `Problem`) with `MySolutionSummary` and `ProjectRef`; regenerate. Mock handler: the six counts over all the user's Solutions (ignoring every filter) and the distinct Projects sorted by name; 401 `MSG07`; 403 `MSG08`. `StatusCounters` shows six counters with the Status Tag labels (not clickable), a spinner at once while loading, MSG06 in the counters only when they fail, loaded once per visit and not refetched on paging or filtering.
**What Hiếu can see or try:** `/profile` for `tr`: six counters that match the fixtures (every status above 0); the resolver with 0 Solutions shows six 0s; a counters failure shows MSG06 only there.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/features/my-profile/mocks/handlers.ts`, `frontend/src/features/my-profile/mocks/data.ts`, `frontend/src/features/my-profile/components/MySolutionsSection.tsx`
- Create: `frontend/src/features/my-profile/components/StatusCounters.tsx`, `frontend/src/features/my-profile/hooks/useMySolutionSummary.ts`
- Generate only: `frontend/src/shared/api/generated/**`
- Test: `frontend/src/features/my-profile/components/StatusCounters.test.tsx`, `frontend/src/features/my-profile/mocks/handlers.test.ts`
**Dependencies:** Task 18.
**PR / Jira:** PR 3 with Tasks 17, 18 and 21; Jira: KAN-238.
**Estimated size:** medium (~250 lines); about 8 tests
**Verification:** Tests name AC-003.21 and "AC-003.14 counters"; handler tests "contract mirror of AC-003.18 / .17 / .10". Targeted: `cd frontend && npm run test -- src/features/my-profile`, then `npm run generate:api` with no diff. Browser check at 1366 × 768 against frame (d).
**Done when:** the tests pass; the full frontend verify passes with `generate:api` leaving no diff.

## Task 21: My Solutions filters — KAN-238

**Story:** STORY-003
**Feature:** my-solutions-filters
**Acceptance criteria:** AC-003.22, AC-003.13 (no match). Contract mirror (not passed here) of AC-003.19, AC-003.9.
**Description:** `SolutionFilters` with Status (single select, "All statuses" + six), Project (single select, "All Projects" + `projects` of the summary) and Date range (dd/MM/yyyy, `to` not before `from`), each clearable, with `aria-label`s "Status", "Project" and "Date range" and no "Clear filters" button. The filters live in the URL (`status`, `projectId`, `from`, `to`) next to `page`; any change resets to page 1 (history replace); back from Ticket Detail keeps filters and page; a bad value in the URL is dropped silently. `useMySolutions` sends the filters. MSG04 when nothing matches, counters and filters stay.
**What Hiếu can see or try:** `/profile` for `tr`: each filter narrows the table; `?status=EXPIRED` and the other parameters appear in the URL; "back" from Ticket Detail keeps them; a filter change goes to page 1; a match of nothing shows "No results found." with the filters still there.
**Files:**
- Modify: `frontend/src/features/my-profile/components/MySolutionsSection.tsx`, `frontend/src/features/my-profile/hooks/useMySolutions.ts`
- Create: `frontend/src/features/my-profile/components/SolutionFilters.tsx`, `frontend/src/features/my-profile/hooks/useSolutionFilters.ts`
- Test: `frontend/src/features/my-profile/components/SolutionFilters.test.tsx`, `frontend/src/features/my-profile/components/MySolutionsSection.test.tsx`
**Dependencies:** Task 20 (the Projects come from the summary).
**PR / Jira:** PR 3 with Tasks 17, 18 and 20; Jira: KAN-238.
**Estimated size:** medium (~300 lines); about 12 tests
**Verification:** Tests name AC-003.22 and AC-003.13 (no match). Targeted: `cd frontend && npm run test -- src/features/my-profile`. Browser check at 1366 × 768 against frames (main) and (d).
**Done when:** the tests pass; the full frontend verify passes with `generate:api` leaving no diff; `bun harnessclaude/hooks/sdd.ts trace identity STORY-003` shows AC-003.11–.14 and .20–.23 covered.

## Keycloak re-plan tasks (KAN-275 plan, 2026-11-05)

Spec: artifacts/03-modules/identity/spec.md
Master: artifacts/04-plans/identity/design.md ("Keycloak re-plan: STORY-001 sign-in and STORY-002 logout")
ADR: `docs/adr/0003-keycloak-sign-in.md` (K1–K14). Runbook: `docs/runbooks/onboard-company.md`.

Tasks 23a, 23, 23b, 23c, 24, 25a, 25b, 26a, 26b and 27–31 replace the sign-in mechanism of Tasks 8, 9, 9b, 10, 11 and 12 (Tasks 7, 14 and 15–22 stay). Order: feedback
first (Task 23 is a Keycloak Hiếu can open, 23b a sign-in start he can follow to the account chooser, 23c the whole sign-in), then the removal, then the `sub` binding and the Workspace checks (25a, 25b), the session and `user_role` (26a, 26b), logout, provisioning,
Operators, the Jira link and the theme. One PR per task, each on its own branch `<type>/KAN-<n>-identity-<desc>` from an
up-to-date `main`; at most about 8 production files and 12 tests each (Task 26a carries the salvage).
Before each commit: the full verify of the side the task touches (CLAUDE.md project rules). Every AC id below is in
exactly one task (AC-001.16 is in Task 26a, which traces it; its Account update is written in Task 25a); ids retired by the 2026-11-05 spec change are in none (Task 24 removes their code and tests).
Every code card's "Read first" carries the K-ids and AC ids of its task in full text. All code tasks and the reviewer
run Opus, high (auth).

**Jira and the old tasks (Q5 = A).** KAN-229, 230, 231 and 235 stay Done (their code is removed by Task 24). KAN-232 is
re-scoped to Tasks 26a and 26b, KAN-233 to Task 29, KAN-234 to Task 30, KAN-106 to Task 27; KAN-247 keeps the platform adapters
and the `FirstAdminCreator` (Task 14). Task 23 is KAN-276; Task 23b is KAN-288 and Task 23c is KAN-289 (Subtasks of KAN-275); Task 23a is KAN-287 (a Subtask of the Story KAN-275, a sibling of KAN-276; already created in Jira); new issues are still to be created for Tasks 24, 28 and 31. Salvage of the uncommitted Task 10 tree (61 files on `feat/KAN-232-identity-workspace-session`):
save it as a patch outside the repo (do not revert, stash or commit it); Tasks 26a and 26b re-apply, on a new branch from
`main`, `identity/session` without `CompleteController`, `AccountRole*`, the `user_role` changeset, Redis in compose,
pom and yml, `SecurityProblemWriter`, and the dev seeder; it drops `StartCookie`, the changesets `008` and `009`, and
the hand-off edits, and does not commit the start-cookie spec change (superseded). Task 26a also drops PR #119's second
session filter on identity's side (Q15).

**Before Task 25b starts:** the platform owner has the change requests of the design ("Platform change requests": the two
columns on `tenant_workspace`, the provisioning-port call and the `create` parameter). There is no `PENDING_SIGN_IN`
status and no `activate` operation; a Workspace is Active at creation, and the test sign-in is a runbook step. Tasks 23a, 23
and 25a need nothing from the platform.

## Task 23a: Prerequisites of the Keycloak sign-in: Redis, the dev Workspace lookup and the Workspace ref fields — Jira KAN-287 (Subtask of KAN-275)

**Story:** STORY-001
**Feature:** keycloak-prerequisites
**Decisions owned:** QA-1 (B), K3 (the Workspace slug), K7 (Redis for the authorization request; the session itself stays Task 26a)
**Acceptance criteria:** none passed; it supplies what AC-001.1, AC-001.52 and AC-001.53 need in Task 23b. The refusal of AC-001.48 stays in Task 25b.
**Description:** Main has no Redis, no `identity/dev` package, and a `WorkspaceRef` with no `slug` and no Organization id. Add `spring-boot-starter-data-redis` (and the Testcontainers Redis test support), a `redis` service in `docker-compose.yml` (pinned image, no password in git), the `REDIS_*` values in `application.yml`, `.env.example` and the test config. Add `slug` and `organizationId` to `WorkspaceRef`; `NoWorkspaceLookup` and `InMemoryWorkspaceLookup` follow. Create `identity/dev` with `DevWorkspaceLookup` (dev profile only) answering `acme` (`acme.test`) and `beta` (`beta.test`) with fixed ids, slugs and Organization ids, plus `DevAccounts` for the matching dev users, so that Task 23's realm import and tests read the same values. Nothing here changes what a browser sees: no sign-in behaviour, no Keycloak. Salvage from the Task 10 patch only the Redis wiring and the lookup idea (see the header); do not carry `identity/session`, `StartCookie` or the changesets `008`/`009`.
**What Hiếu can see or try:** `docker compose up redis` is healthy; `mvn verify` is green; with profile `dev` the app resolves `acme.localhost` and `beta.localhost` to a Workspace (a unit test now, and the sign-in start for `acme` once Task 23b lands); without `dev` the lookup answers nothing, as before.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/dev/{DevWorkspaceLookup,DevAccounts}.java`
- Modify: `backend/src/main/java/vn/tsolve/identity/signin/port/{WorkspaceRef,NoWorkspaceLookup}.java` (and every caller of the `WorkspaceRef` constructor), `backend/pom.xml`, `backend/src/main/resources/application.yml`, `docker-compose.yml`, `.env.example`, `backend/src/test/resources/config/application.yml`
- Test: `backend/src/test/java/vn/tsolve/identity/dev/DevWorkspaceLookupTest.java`, `backend/src/test/java/vn/tsolve/identity/RedisWiringIT.java` (the context starts against a Testcontainers Redis; write, read and expire a key); modify `backend/src/test/java/vn/tsolve/identity/signin/port/InMemoryWorkspaceLookup.java`
**Dependencies:** Docker Desktop; none of the other tasks.
**PR / Jira:** branch `feat/KAN-287-identity-keycloak-prerequisites`; Jira KAN-287 (Subtask of KAN-275). Tasks 23, 23b and 23c depend on it.
**Estimated size:** small (2 new and 2 modified production Java files, plus build and config files); about 6 tests in 2 new test files and one modified fixture.
**Test scope:** `DevWorkspaceLookupTest` (acme and beta resolve by host, an unknown host answers nothing, the ids match `DevAccounts`, not a bean outside `dev`) and `RedisWiringIT` (Docker) carry the task; no acceptance-criterion id is traced here. QA step 1 must be run on this task before its code starts.
**Verification:** Targeted: `cd backend && mvn -Dtest='DevWorkspaceLookupTest' -Dit.test='RedisWiringIT' verify` (the IT needs Docker); full `mvn verify` (tests and `spotless:check`) before the commit.

## Task 23: Keycloak and the fake Google on the local stack: the realm, compose and the start-up check — KAN-276

**Split (user, 6/11):** the old Task 23 (84 tests) is three slices in feedback-first order, each its own Subtask, branch and PR: **Task 23** (this one: Keycloak, the realm and the fake Google you can open in a browser), **Task 23b** (the sign-in start up to the fake Google account chooser), **Task 23c** (the callback and the whole round trip). All three depend on Task 23a. Boundaries were drawn from the files: the Redis authorization-request repository is saved at the start, so it moves to 23b; `AuthSecurityConfig` wires the callback, so it goes with the callback in 23c; the end-to-end test needs all three, so it is in 23c; the start-up discovery check and `SignInProperties` need only Keycloak, so they stay here. Shared build and config files (`pom.xml`, `application.yml`, the test `application.yml`, `docker-compose.yml`, `.env.example`) are edited by each slice only for its own entries.

**Story:** STORY-001
**Feature:** keycloak-realm
**Decisions owned:** K1, K3 (the dev IdPs), K5, K14, Q3, Q9, Q10 (realm mappers), Q13; QA answers 4, 5, 6 and 7 below
**Acceptance criteria:** AC-001.54, AC-001.63, AC-001.64, AC-001.65
**Description:** Add Keycloak 26.x to `docker-compose.yml` (pinned image, heap cap, health check, its own database `keycloak`) and a WireMock `fake-google` service, with the realm import `infra/keycloak/realm-tsolve.json` (the client `tsolve-web` with PKCE `S256` and the `acme` and `beta` redirect URIs, the `organization` scope and the `hd` mapper, SSO Session Max 8 h and Idle 60 min, registration off, no password form, IdPs hidden, the custom first-broker-login flow, brute-force detection, the IdPs `google-acme` and `google-beta` of type `oidc` pointing at `fake-google`, and users that match `DevAccounts` from Task 23a). In the backend: add `testcontainers-keycloak`; add `KEYCLOAK_WEB_CLIENT_SECRET` to `SignInProperties` as required and to `.env.example`, compose and the test config; add the start-up check of the discovery document of the realm `tsolve` (in `SignInConfig`). The old `login.` classes keep answering on `login.` until Task 24 deletes them. Nothing here changes the sign-in start or the callback: the old behaviour stays until Tasks 23b and 23c.

Answers that bind this task (QA step 1 questions, user, 5/11):
- **Discovery (Q4 = A).** "Both discovery documents" means the realms `tsolve` and `tsolve-ops`; this task checks `tsolve` only, Task 29 adds `tsolve-ops` with its import.
- **Keycloak down at start (Q5 = B).** The backend starts with a WARN that names the issuer; a reachable discovery document whose issuer differs from `TSOLVE_LOGIN_ORIGIN` + `/realms/tsolve` still fails start-up; there is no later re-check.
- **`SignInProperties` (Q6 = A).** `TSOLVE_SIGNING_KEY` and `GOOGLE_CLIENT_ID/SECRET` stay required for the old classes; `KEYCLOAK_WEB_CLIENT_SECRET` is added as required; Task 24 removes the old ones.
- **Fake Google (Q7 = A).** WireMock with response templating and a JWT-signing extension: the authorization stub encodes the nonce into the code, the token stub signs a fresh ID token per request. The same import serves the dev compose and CI. The dev IdPs are type `oidc` (Keycloak's `google` type has fixed Google URLs), so Google's `hostedDomain` enforcement is not testable here (runbook test sign-in, Task 28).
- **Discovery strictness (user, 7/11).** The check also fails start-up on a 3xx and on a 4xx other than 404, stricter than the Q2 A list; accepted.

**What Hiếu can see or try:** `docker compose up`: Keycloak is healthy; the admin console shows the realm `tsolve` with the client `tsolve-web`, the Organizations `acme` and `beta` and their IdPs; opening the realm's authorization URL for `tsolve-web` with `kc_idp_hint=google-acme` lands on the fake Google account chooser for the `acme` domain (the way back to the site answers 404 until Task 23c). A request without the hint shows no list of companies and no password form. Starting the backend with a wrong `TSOLVE_LOGIN_ORIGIN` fails and names the issuer.
**Files:**
- Create: `infra/keycloak/realm-tsolve.json`, `infra/keycloak/fake-google/mappings/*.json` (plus the JWT-signing WireMock extension, if it needs a small build of its own under `infra/keycloak/fake-google/`)
- Modify: `backend/src/main/java/vn/tsolve/identity/signin/{SignInConfig,SignInProperties}.java`, `backend/pom.xml`, `backend/src/main/resources/application.yml`, `docker-compose.yml`, `.env.example`, `backend/src/test/resources/config/application.yml`
- Test: `backend/src/test/java/vn/tsolve/identity/signin/{RealmImportIT,DiscoveryCheckTest}.java`; modify `SignInPropertiesTest`, `FakeGoogleIssuer` (the upstream that Keycloak brokers to)
**Dependencies:** Task 23a merged (Redis, the dev Workspace lookup, `DevAccounts`); Docker Desktop; none of the platform tasks.
**PR / Jira:** Jira KAN-276, branch `feat/KAN-276-identity-keycloak-sign-in`.
**Estimated size:** medium (2 modified production Java files, the realm import, the fake Google and the build and config files); 22 tests in 2 new test files and 2 modified.
**Test scope:** 22 test methods against the rule of about 12 per task (RealmImport 11, DiscoveryCheck 6, SignInProperties 5; the fixture has none). Over the rule, kept together because the realm import and its check are one result Hiếu can see. AC-001.54 and AC-001.63–.65 by `RealmImportIT` (token and client settings, the mappers `organization` and `hd`, no password form, IdPs hidden, the custom first-broker-login flow); edge 111 by `DiscoveryCheckTest`; the required secret by `SignInPropertiesTest`. QA step 1 must be re-run on this slice before its code starts.
**Verification:** Tests name AC-001.54, .63–.65; design risk rows 103 (the mappers), 104, 105, 110, 111, 114. Targeted: `cd backend && mvn -Dtest='DiscoveryCheckTest,SignInPropertiesTest' -Dit.test='RealmImportIT' verify` (the IT needs Docker); full `mvn verify` before the commit. Local browser check of the realm at `localhost`.

## Task 23b: Sign-in start through Keycloak: the authorization request in Redis — Jira KAN-288 (Subtask of KAN-275)

**Story:** STORY-001
**Feature:** keycloak-sign-in-start
**Decisions owned:** K5 (PKCE and the hint), K7 (the authorization request in Redis), Q3, Q8; QA answers 3 and 8 below
**Acceptance criteria:** AC-001.1, AC-001.3, AC-001.52, AC-001.53
**Description:** Add `spring-boot-starter-oauth2-client`. `KeycloakAuthorizationRequestResolver` (`kc_idp_hint` from the Workspace, PKCE `S256`, the redirect URI built from the request's scheme, host and port) and `RedisAuthorizationRequestRepository` with the `__Host-TSOLVE_AUTHREQ` cookie (read and delete in one step; the Redis wiring comes from Task 23a) are `@Component`s, so `SignInConfig` is not touched again. `SignInStartController` builds the request through them, saves it and redirects to Keycloak; it does not need the Spring OAuth2 login filter, so `AuthSecurityConfig` stays for Task 23c. The AC-001.2 test in `SignInStartControllerTest` is deleted and its id retired. The callback does not exist yet: the redirect back to the site answers 404 until Task 23c.

Answers that bind this task (QA step 1 questions, user, 5/11):
- **`admin.` (Q3 = A).** Sign-in start on `admin.` keeps the old state + `login.` authorize path and its AC-001.28 test stays green; the callback on `admin.` answers 404 until Task 29.
- **Redirect URI (Q8 = A).** The origin of `redirect_uri` comes from the request; set `server.forward-headers-strategy=framework` so the origin behind Caddy is the public one. The host has already resolved to a Workspace, and Keycloak's exact match of the registered URIs is the guard.

**What Hiếu can see or try:** with the stack of Task 23 up, `http://acme.localhost:<port>/api/v1/auth/sign-in` goes to Keycloak and then to the fake Google account chooser for the `acme` domain; a Redis key holds the authorization request and the browser has the `__Host-TSOLVE_AUTHREQ` cookie; an unknown host or `admin.` behaves as before. The page after the chooser is a 404 until Task 23c.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/signin/{KeycloakAuthorizationRequestResolver,RedisAuthorizationRequestRepository}.java`
- Modify: `backend/src/main/java/vn/tsolve/identity/signin/SignInStartController.java`, `backend/pom.xml`, `backend/src/main/resources/application.yml`, `backend/src/test/resources/config/application.yml`
- Test: `backend/src/test/java/vn/tsolve/identity/signin/{KeycloakAuthorizationRequestResolverTest,RedisAuthorizationRequestRepositoryIT}.java`; modify `SignInStartControllerTest`
**Dependencies:** Task 23a and Task 23 merged (the realm and the client `tsolve-web`); Docker Desktop.
**PR / Jira:** branch `feat/KAN-288-identity-keycloak-sign-in-start`; Jira KAN-288 (Subtask of KAN-275).
**Estimated size:** medium (2 new and 1 modified production Java files, plus build and config files); 28 tests in 2 new test files and 1 modified.
**Test scope:** 28 test methods against the rule of about 12 per task (Resolver 8, StartController 11, Redis IT 9). Over the rule, kept together because the start cannot be tried without the saved request. AC-001.1, .3, .52 by `KeycloakAuthorizationRequestResolverTest` and `SignInStartControllerTest`; AC-001.53 (cookie, read and delete in one step) by `RedisAuthorizationRequestRepositoryIT`. Whether S8 and S9, the refusal of AC-001.48, stay here or move to Task 25b is settled in the QA re-run (S9 needs `organizationId` from Task 23a). QA step 1 must be re-run on this slice before its code starts.
**Verification:** Tests name AC-001.1, .3, .52, .53. Targeted: `cd backend && mvn -Dtest='KeycloakAuthorizationRequestResolverTest,SignInStartControllerTest' -Dit.test='RedisAuthorizationRequestRepositoryIT' verify` (the IT needs Docker); full `mvn verify` before the commit. Local browser run at `acme.localhost` up to the account chooser.

## Task 23c: Keycloak callback and the whole sign-in round trip — Jira KAN-289 (Subtask of KAN-275)

**Story:** STORY-001
**Feature:** keycloak-callback
**Decisions owned:** K7 (the callback and the stateless chain), Q2; QA answer 2 below
**Acceptance criteria:** AC-001.6, AC-001.8, AC-001.9, AC-001.55 (and AC-001.53, .54, .63–.65 end to end)
**Description:** `OidcCallbackHandler` (request check → MSG141, Keycloak error and token failures → MSG06, cookie always expired; on success it forwards in the same request to a dev-profile `DevClaimsController` that shows the claims) and `AuthSecurityConfig` (the OAuth2 login callback, stateless, `NullSecurityContextRepository`, no HTTP session; the new chain ignores the `login.` host; the callback on `admin.` answers 404). The Account checks, `sub` binding and the session are Tasks 25 and 26.

Answers that bind this task (QA step 1 questions, user, 5/11):
- **Success without a session (Q2 = A).** The success handler forwards to `DevClaimsController` (dev profile only), which answers 200 with `sub`, email, `organization` and `hd`; nothing is stored and nothing is put in a URL. Outside `dev` a success answers 302 `/login?error=MSG06` with one WARN ("session not built yet") until Task 26a.

Notes, not scope of Tasks 23, 23b or 23c: (1) an optional real-Google IdP switched on by `.env` (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`) for a manual smoke test belongs to the Keycloak provisioning task (Task 28) and the runbook `docs/runbooks/onboard-company.md`; (2) a real Google Workspace or Cloud Identity domain is a prerequisite of the staging smoke test and of production readiness (`hd` exists only for Workspace domains), not of these tasks; (3) Microsoft Entra ID is NOT in scope and needs a separate Change Mode decision (spec and ADR).

**What Hiếu can see or try:** `docker compose up`, then `http://acme.localhost:<port>/api/v1/auth/sign-in` goes to Keycloak, then to the fake Google account chooser for the `acme` domain, back to the site's callback, and the dev page shows the verified claims (`sub`, email, `organization`, `hd`). A callback opened in a second browser answers `/login?error=MSG141`. A sign-in request with the hint removed shows no list of companies and no password form.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/signin/OidcCallbackHandler.java`, `backend/src/main/java/vn/tsolve/identity/dev/DevClaimsController.java`
- Modify: `backend/src/main/java/vn/tsolve/identity/AuthSecurityConfig.java`
- Test: `backend/src/test/java/vn/tsolve/identity/signin/{OidcCallbackHandlerTest,TwoInstanceCallbackIT,KeycloakSignInEndToEndIT}.java`; modify `AuthSecurityConfigTest`
**Dependencies:** Task 23a, Task 23 and Task 23b merged; Docker Desktop; none of the platform tasks (the dev lookup answers for `acme` and `beta`).
**PR / Jira:** branch `feat/KAN-289-identity-keycloak-callback`; Jira KAN-289 (Subtask of KAN-275).
**Estimated size:** medium (2 new and 1 modified production Java files); 34 tests in 3 new test files and 1 modified.
**Test scope:** 34 test methods against the rule of about 12 per task (OidcCallbackHandler 15, AuthSecurityConfig 5, TwoInstance 3, EndToEnd 11). Over the rule; the unit tests are fast and the two ITs are the slow part. AC-001.6, .8, .9 by `OidcCallbackHandlerTest`; AC-001.55 by `AuthSecurityConfigTest` and `TwoInstanceCallbackIT`; AC-001.53, .54 and .63–.65 end to end by `KeycloakSignInEndToEndIT`. QA step 1 must be re-run on this slice before its code starts.
**Verification:** Tests name AC-001.6, .8, .9, .55 and the end-to-end ids; design risk rows 115 and 122. Targeted: `cd backend && mvn -Dtest='OidcCallbackHandlerTest,AuthSecurityConfigTest' -Dit.test='KeycloakSignInEndToEndIT,TwoInstanceCallbackIT' verify` (the ITs need Docker); full `mvn verify` before the commit. Local browser run through Keycloak at `acme.localhost`.

## Task 24: Remove the custom sign-in — Jira KAN-295 (Subtask of KAN-275)

**Story:** STORY-001
**Feature:** remove-custom-sign-in
**Decisions owned:** K12
**Acceptance criteria:** none passed. It retires the code and tests of AC-001.2, .4, .5, .17, .19, .35, .36, .37, .39, .44, .45, .46, .47, .49, .50 and .51 (ids never reused).
**Description:** Delete `AuthorizeController`, the `login.` `CallbackController`, `GoogleIdentity`, `GoogleOidcClient`, `IdTokenVerifier`, the backend `LoginPage`, `SignInCookie`, `SignInState`, `SignInStateRepository`, `SignInStateService`, `SignedToken`, `HandOffCode`, `HandOffCodeRepository`, `HandOffCodeService`, and their tests (`AuthorizeControllerTest`, `IdTokenVerifierTest`, `LoginPageTest`, `SignInCookieTest`, `SignInStateRepositoryIT`, `SignInStateServiceTest`, `SignedTokenTest`, `CallbackControllerTest`, `CallbackFailureLoggingTest`, `HandOffCodeSchemaIT`, `HandOffCodeServiceTest`, `HandOffStore`). New changesets drop `sign_in_states` and `hand_off_code` (the files `004`, `005`, `006`, `007` are never edited). Remove `TSOLVE_SIGNING_KEY`, `tsolve.oidc.google.*` and the `GOOGLE_*` values from the web process; remove `/api/v1/auth/authorize` and `/api/v1/auth/complete` from the OpenAPI file and regenerate; `AuthHosts` keeps only Workspace site vs `admin.`. The build and the suites stay green.
**What Hiếu can see or try:** `git diff --stat` shows the removal; `mvn verify` is green; the Task 23c sign-in still works; `docker compose down -v` then `up` starts clean (note for developers whose database ran the unreleased `008` and `009`, row 120).
**Files:**
- Delete: the classes and tests above
- Modify: `backend/src/main/java/vn/tsolve/identity/{AuthSecurityConfig}.java`, `backend/src/main/java/vn/tsolve/identity/signin/{AuthHosts,SignInConfig,SignInProperties}.java`, `backend/src/main/resources/db/changelog/db.changelog-master.xml`, `backend/src/main/resources/application.yml`, `.env.example`, `frontend/openapi/tsolve-api.yaml`
- Create: `backend/src/main/resources/db/changelog/changes/<next>-identity-drop-sign-in-states.sql`, `<next>-identity-drop-hand-off-code.sql`
- Generate only: `frontend/src/shared/api/generated/**`
- Test: modify `AuthHostsTest` if any; a changelog test that both tables are gone
**Dependencies:** Tasks 23, 23b and 23c merged.
**PR / Jira:** branch `refactor/KAN-295-identity-remove-custom-sign-in`; Jira KAN-295.
**Estimated size:** medium (about 8 modified production files; mostly deletions); 2 new tests
**Verification:** `mvn verify`; `cd frontend && npm run generate:api` leaves no diff and the frontend `verify` passes; `sdd.ts trace identity STORY-001` lists no retired id; design risk row 120.

## Task 25a: The `sub` binding of the Account — Jira KAN-296 (Subtask of KAN-275)

**Story:** STORY-001
**Feature:** account-sub-binding
**Decisions owned:** K8, Q11
**Acceptance criteria:** AC-001.12, AC-001.13, AC-001.15, AC-001.56
**Description:** New tenant changeset: `app_user.keycloak_sub` (unique). `AccountSignInService` (inside `TenantScope.runIn`) matches the Account by `sub`, else on a first sign-in by the verified email (ignoring case), binds the `sub` and refuses a `sub` bound to another Account (MSG09, `WARN` with the Account id); a Deactivated Account is refused (MSG10); it records the full name and the sign-in times (the Account update named by AC-001.16, which is traced in Task 26a). `app_user.email` is not updated by a later sign-in. `OidcCallbackHandler` calls the service in Task 25b; until then only its tests run it.
**What Hiếu can see or try:** the tests only: the first sign-in of a seeded Account binds its `sub`; the same Keycloak user after `sub` was bound to another Account → MSG09; a Deactivated Account → MSG10; an Account of Workspace B is never found from Workspace A (two databases).
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/<next>-identity-app-user-keycloak-sub.sql`
- Modify: `backend/src/main/java/vn/tsolve/identity/account/{Account,AccountRepository,AccountSignInService}.java`, `backend/src/main/resources/db/changelog/db.changelog-master.xml`
- Test: `backend/src/test/java/vn/tsolve/identity/account/{AccountRepositoryIT,AccountSubBindingTest}.java`, `backend/src/test/java/vn/tsolve/identity/signin/CallbackAccountIsolationIT.java` (two Workspaces, two databases, two IdPs: rows 106, 24)
**Dependencies:** Task 24 merged.
**PR / Jira:** branch `feat/KAN-296-identity-account-sub-binding`; Jira KAN-296 (KAN-235 stays Done).
**Estimated size:** small (3 production files, 1 changeset); about 8 tests
**Verification:** Tests name AC-001.12, .13, .15, .56; design risk rows 113, 119. Targeted: `cd backend && mvn -Dtest='AccountSubBindingTest' -Dit.test='CallbackAccountIsolationIT,AccountRepositoryIT' verify`; full `mvn verify`.

## Task 25b: The Workspace checks of the callback and the sign-in start — Jira KAN-297 (Subtask of KAN-275)

**Story:** STORY-001
**Feature:** callback-workspace-checks
**Decisions owned:** K2, K11, Q10
**Acceptance criteria:** AC-001.7, AC-001.10, AC-001.11, AC-001.14, AC-001.48
**Description:** The `slug`, the Organization id and `DevWorkspaceLookup` come from Task 23a (KAN-287); this task uses them. `OidcCallbackHandler` runs the AC-001.14 order inside `TenantScope.runIn`: Organization and `hd` (both) → Workspace Active → Account (Task 25a) → Account Active; only the first failure is reported. `SignInStartController` refuses a Workspace with no Google domain or no Organization id (AC-001.48). There is no status gate: a Workspace is Active at creation; if Google has not got the redirect URI yet, Google refuses and the Workspace site shows the normal sign-in error (runbook).
**What Hiếu can see or try:** locally, the dev claims page of Task 23c now ends in each outcome: `beta`'s Google user on `acme` → `/login?error=MSG122`; a user with no Account → MSG09; a Deactivated Account → MSG10; a suspended Workspace → MSG120; a Workspace with no Organization id → MSG141 at sign-in start.
**Files:**
- Modify: `backend/src/main/java/vn/tsolve/identity/signin/port/{WorkspaceLookup,NoWorkspaceLookup}.java`, `backend/src/main/java/vn/tsolve/identity/signin/{OidcCallbackHandler,SignInStartController}.java`
- Used, not modified (done in Task 23a): `WorkspaceRef` (`slug`, `organizationId`), `identity/dev/DevWorkspaceLookup.java`, `identity/dev/DevAccounts.java`
- Test: `backend/src/test/java/vn/tsolve/identity/signin/CallbackAccountChecksTest.java`, `backend/src/test/java/vn/tsolve/identity/signin/port/InMemoryWorkspaceLookup.java` (modify), `SignInStartControllerTest` (modify)
**Dependencies:** Task 25a merged; the platform change requests sent (the real lookup waits for them; the dev and in-memory lookups do not).
**PR / Jira:** branch `feat/KAN-297-identity-callback-workspace-checks`; Jira KAN-297.
**Estimated size:** small (4 production files); about 8 tests
**Verification:** Tests name AC-001.7, .10, .11, .14, .48; design risk rows 103, 106, 124. Targeted: `cd backend && mvn -Dtest='CallbackAccountChecksTest,SignInStartControllerTest' verify`; full `mvn verify`.

## Task 26a: Redis session and `WebSessionFilter` — KAN-232 (re-scoped)

**Story:** STORY-001
**Feature:** workspace-session
**Decisions owned:** K9, Q15
**Acceptance criteria:** AC-001.16, AC-001.18, AC-001.20, AC-001.21, AC-001.22, AC-001.23, AC-001.24, AC-001.25, AC-001.27
**Description:** Salvage of the uncommitted Task 10 tree (see the header): `WebSessionStore` (Redis, `tsolve:session:<hash>`; gets the `id_token_hint`), `WebSession`, `SessionCookie`, `WebSessionFilter` with the rules of the old design (site and kind MSG123, 8 h / 60 min MSG07, the Account re-read with MSG10, the `X-TSolve-Background` header, a cookie that names no session is anonymous and is cleared), `SecurityProblemWriter` texts, the `WebSessionFilter` registration in `SecurityConfig`, Redis in compose, pom and yml (already there: Redis comes from Task 23a (KAN-287)). `OidcCallbackHandler` now creates the session (AC-001.18) and redirects to `returnTo` or `/`. Until Task 26b the `TSolveUser` has no Roles. AC-001.16 is traced here: its Account update is written in Task 25a, its last bullet (the session) in this task. Dropped: `CompleteController`, `StartCookie`, the changesets `008` and `009`, the hand-off edits and the dev fake Google classes. PR #119's `UserListSessionFilter` and `WorkspaceSessionBinding` are not carried into identity (Q15); the note to its owner is Hiếu's, outside this plan.
**What Hiếu can see or try:** a full local sign-in through Keycloak ends on `/` with the `__Host-TSOLVE_SESSION` cookie set (host-only, `HttpOnly`, `Secure`, `Lax`); the same cookie copied to `beta.localhost` or `admin.localhost` → MSG123; 60 idle minutes → MSG07; Deactivating the Account → MSG10 at the next request.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/session/{WebSession,WebSessionStore,WebSessionFilter,SessionCookie}.java`
- Modify: `backend/src/main/java/vn/tsolve/identity/signin/OidcCallbackHandler.java`, `backend/src/main/java/vn/tsolve/common/security/{SecurityConfig,SecurityProblemWriter}.java`, `docker-compose.yml`, `backend/pom.xml`, `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/vn/tsolve/identity/session/{WebSessionFilterTest,WebSessionStoreIT,SessionRedisDownTest,AbstractSessionIT,SessionTestFixtures,WhoAmITestController}.java`, `backend/src/test/java/vn/tsolve/identity/signin/SessionCreationTest.java`
**Dependencies:** Task 25b merged; be-foundation `roles-security` on `main` (`TSolveUser`, the deny-by-default chain, ProblemDetail 401 bodies).
**PR / Jira:** branch `feat/KAN-232-identity-workspace-session` (the existing branch name; a new branch from `main` with the salvaged patch applied); Jira KAN-232.
**Estimated size:** medium (7 production Java files, most salvaged from the uncommitted tree, plus compose, pom and yml); 7 tests
**Verification:** Tests name AC-001.16, .18, .20–.25, .27; AC-001.20 and .21 are two-Workspace tests; design risk rows 114, 123 and the older rows 27–32, 90, 91, 102 that survive. Targeted: `cd backend && mvn -Dtest='WebSessionFilterTest,SessionCreationTest,SessionRedisDownTest' -Dit.test='WebSessionStoreIT' verify`; full `mvn verify`; the local sign-in run.

## Task 26b: `user_role` and the Roles in the session — Jira KAN-298 (Subtask of KAN-275)

**Story:** STORY-001
**Feature:** user-role
**Decisions owned:** K11
**Acceptance criteria:** AC-001.26
**Description:** Salvage of the uncommitted Task 10 tree: `AccountRole`, `AccountRoleRepository`, the `user_role` changeset, `Account` (its Roles), the dev Account and Role seeder (`DevAccountSeeder`, `DevAccounts`). `WebSessionFilter` puts a `TSolveUser` with the Account's current id, email, full name and Roles in the security context at each request, so a Role change applies from the next request (AC-001.26; Task 26a's filter built it without Roles).
**What Hiếu can see or try:** the dev seeder creates Accounts with Roles that match the Keycloak realm import users; after a sign-in the whoami test endpoint shows the Roles; changing a Role in the database shows at the next request.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/account/{AccountRole,AccountRoleRepository}.java`, `backend/src/main/resources/db/changelog/changes/<next>-identity-user-role.sql`
- Modify: `backend/src/main/java/vn/tsolve/identity/account/Account.java`, `backend/src/main/java/vn/tsolve/identity/session/WebSessionFilter.java`, `backend/src/main/java/vn/tsolve/identity/dev/{DevAccountSeeder,DevAccounts}.java`, `backend/src/main/resources/db/changelog/db.changelog-master.xml`
- Test: `backend/src/test/java/vn/tsolve/identity/dev/{DevAccountSeederIT,DevSessionWhoAmITest}.java` (modified), `backend/src/test/java/vn/tsolve/identity/session/RoleChangeTest.java`, `backend/src/test/java/vn/tsolve/identity/account/AccountRoleRepositoryIT.java`
**Dependencies:** Task 26a merged.
**PR / Jira:** branch `feat/KAN-298-identity-user-role`; Jira KAN-298 (own issue).
**Estimated size:** small (7 production files incl. the changeset); about 5 tests
**Verification:** Tests name AC-001.26; design risk row 32. Targeted: `cd backend && mvn -Dtest='RoleChangeTest,DevSessionWhoAmITest' -Dit.test='DevAccountSeederIT,AccountRoleRepositoryIT' verify`; full `mvn verify`.

## Task 27: Logout with Keycloak end-session — KAN-106

**Story:** STORY-002
**Feature:** logout-rp-initiated
**Decisions owned:** K10, Q7
**Acceptance criteria:** AC-002.1, AC-002.2, AC-002.3, AC-002.4, AC-002.5, AC-002.6, AC-002.8
**Description:** `POST /api/v1/auth/logout` (CSRF) ends the Redis session, expires the cookie, publishes `SessionEnded` (`LOGOUT`) once, and answers 200 `{ logoutUrl }` built from Keycloak's `end_session_endpoint`, the session's `id_token_hint` and `post_logout_redirect_uri` (the site's `/login`; `tsolve-ops` on `admin.`). The `EXPIRED` event within 15 minutes (keyspace events on a shadow key, or a worker job over an expiry-ordered index: chosen when the card is written, because an expiry event carries no Account id) and the `DEACTIVATED` event. The OpenAPI answer changes from 204 to 200 and is regenerated. fe-foundation's `useSignOut` must navigate to `logoutUrl` (a change request to KAN-198 before this task's merge, owner of fe-foundation).
**What Hiếu can see or try:** log out on `acme.localhost`: the browser goes through Keycloak's end-session page back to `/login`; "Sign in" afterwards asks Google again (Keycloak's SSO session is gone); the old cookie answers MSG07.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/session/{LogoutController,KeycloakLogoutUrlBuilder,SessionEnded,SessionExpiryPublisher}.java`
- Modify: `backend/src/main/java/vn/tsolve/identity/session/{WebSessionStore,WebSessionFilter}.java`, `frontend/openapi/tsolve-api.yaml`
- Generate only: `frontend/src/shared/api/generated/**`
- Test: `backend/src/test/java/vn/tsolve/identity/session/{LogoutControllerTest,KeycloakLogoutUrlBuilderTest,SessionEndedEventTest,SessionExpiryPublisherIT}.java`
**Dependencies:** Task 26a merged; the fe-foundation change request for `useSignOut`.
**PR / Jira:** branch `feat/KAN-106-identity-logout`; Jira KAN-106.
**Estimated size:** medium (6 production files); about 10 tests
**Verification:** Tests name AC-002.1–.6 and .8; design risk rows 114, 116. Targeted: `cd backend && mvn -Dtest='LogoutControllerTest,KeycloakLogoutUrlBuilderTest,SessionEndedEventTest' -Dit.test=SessionExpiryPublisherIT verify`; `cd frontend && npm run generate:api` with no diff; full `mvn verify`.

## Task 28: Keycloak provisioning port and Admin API adapter — Jira KAN-299 (Subtask of KAN-275)

**Story:** STORY-001
**Feature:** keycloak-provisioning
**Decisions owned:** K2, K3, K4, K6, Q3, Q4, Q12, Q14, Q16
**Acceptance criteria:** AC-001.57, AC-001.58, AC-001.59, AC-001.60, AC-001.61, AC-001.62
**Description:** The port `WorkspaceSignInProvisioner` (`provision`, `check`, `remove`), the no-op bean, and `KeycloakWorkspaceSignInProvisioner` over the Keycloak Admin REST API with the client `tsolve-provisioner` (least roles, in the realm import): Organization with the domain, the Google IdP `google-<slug>` (client id and secret from the provisioning profile, `hostedDomain`, hidden on the login page, the first-broker-login flow, the link to the Organization), the two URIs in `tsolve-web`; serialised by an advisory lock, a re-read and verify after each write, compensating deletes by alias; `remove` for the runbook. The credentials load only in the `ops` and Console profiles. The design's "Platform change requests" (status, columns, the call from `WorkspaceProvisioning`) are made by the platform owner; until then only the no-op bean is wired. The runbook `docs/runbooks/onboard-company.md` is updated if the adapter changes a step.
**What Hiếu can see or try:** a test or an `ops`-profile call creates the Organization, the IdP and the URIs for a new slug, and the Keycloak admin console shows them; the call twice changes nothing; a failure midway leaves nothing; `provision` for two slugs at once keeps all four URIs; a Workspace web profile starts without any Keycloak Admin or Google value.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/signin/port/{WorkspaceSignInProvisioner,NoopWorkspaceSignInProvisioner}.java`, `backend/src/main/java/vn/tsolve/identity/keycloak/{KeycloakWorkspaceSignInProvisioner,KeycloakAdminClient,ProvisioningLock,KeycloakProvisioningProperties}.java`
- Modify: `infra/keycloak/realm-tsolve.json` (the `tsolve-provisioner` client), `backend/src/main/resources/application.yml`, `.env.example`
- Test: `backend/src/test/java/vn/tsolve/identity/keycloak/{KeycloakWorkspaceSignInProvisionerIT,ProvisioningRaceIT,ProvisioningUndoIT,KeycloakAdminClientTest,ProvisioningProfileTest,NoopWorkspaceSignInProvisionerTest,FakeWorkspaceSignInProvisioner}.java`
**Dependencies:** Task 23 (Keycloak in compose and Testcontainers); the platform's `WorkspaceProvisioning` only for the real call (Task 14).
**PR / Jira:** branch `feat/KAN-299-identity-keycloak-provisioning`; Jira KAN-299.
**Estimated size:** medium (8 production files); about 9 tests
**Verification:** Tests name AC-001.57–.62; design risk rows 107–109, 112, 118. Targeted: `cd backend && mvn -Dtest='KeycloakAdminClientTest,ProvisioningProfileTest,NoopWorkspaceSignInProvisionerTest' -Dit.test='KeycloakWorkspaceSignInProvisionerIT,ProvisioningRaceIT,ProvisioningUndoIT' verify`; full `mvn verify`.

## Task 29: Platform Console Operators on `tsolve-ops` — KAN-233 (re-scoped)

**Story:** STORY-001
**Feature:** operator-sign-in
**Decisions owned:** Q1 (B), K9
**Acceptance criteria:** AC-001.28, AC-001.29, AC-001.30, AC-001.31, AC-001.32, AC-001.33, AC-001.34, AC-001.66
**Description:** The realm import `infra/keycloak/realm-tsolve-ops.json` (Google IdP without `hd`, a required second factor, the client `tsolve-console`, the exact redirect URI of `TSOLVE_ADMIN_ORIGIN`), the second client registration and request resolver for `admin.`, `OperatorAllowList` (trimmed, ignoring case), `OperatorPrincipal`, the allow-list check at the callback (MSG135) and at every request, and the Operator session on `admin.` only (MSG123 on a Workspace site). Console failures go to `/login?error=MSG06` on `admin.`.
**What Hiếu can see or try:** on `admin.localhost`, an allow-listed Operator signs in through Keycloak, sets up the second factor once, and reaches the Console placeholder; any other Google user → MSG135; the Operator cookie copied to a Workspace site → MSG123; no tenant IdP is on the `tsolve-ops` login page.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/session/{OperatorAllowList,OperatorPrincipal}.java`, `infra/keycloak/realm-tsolve-ops.json`
- Modify: `backend/src/main/java/vn/tsolve/identity/AuthSecurityConfig.java`, `backend/src/main/java/vn/tsolve/identity/signin/{OidcCallbackHandler,KeycloakAuthorizationRequestResolver,SignInProperties}.java`, `backend/src/main/java/vn/tsolve/identity/session/WebSessionFilter.java`, `docker-compose.yml`, `.env.example`
- Test: `backend/src/test/java/vn/tsolve/identity/signin/OperatorSignInTest.java`, `backend/src/test/java/vn/tsolve/identity/session/OperatorSessionTest.java`, `backend/src/test/java/vn/tsolve/identity/signin/OpsRealmImportIT.java`
**Dependencies:** Task 26b merged.
**PR / Jira:** branch `feat/KAN-233-identity-operator-sign-in`; Jira KAN-233.
**Estimated size:** medium (about 8 production files); about 8 tests
**Verification:** Tests name AC-001.28–.34 and .66; AC-001.32 is a two-Workspace test; design older rows 29, 35, 36. Targeted: `cd backend && mvn -Dtest='OperatorSignInTest,OperatorSessionTest' -Dit.test=OpsRealmImportIT verify`; full `mvn verify`.

## Task 30: Jira account link with a confirm page — KAN-234 (re-scoped)

**Story:** STORY-001
**Feature:** jira-link-sign-in
**Decisions owned:** Q2 (A), K5, K7, K8, K9
**Acceptance criteria:** AC-001.38, AC-001.69, AC-001.70, AC-001.71, AC-001.72
**Description:** `JiraLinkRequestService` (a single-use request in Redis, 10 minutes, holding the Workspace and the tracker account from the verified Forge token) for `jira-app`; the web-app route `/jira-link` with the confirm page "Link Jira user X (site S) to your account Y?" (it sends a signed-out person through the sign-in of Task 23c with that URL as `returnTo`); `POST /api/v1/auth/jira-link/confirm` with CSRF and a Workspace session, which consumes the request, records the link in the Workspace's database (`jira_account_links`, replacing an earlier link of the same tracker account) and answers MSG75. Failures as AC-001.72. Design row 121 stays "mitigated"; option B (a typed code) is documented in the design if the team wants the Critical row closed.
**What Hiếu can see or try:** a dev call of `JiraLinkRequestService` prints the URL; opened signed out it goes through Keycloak and back to the confirm page; "Link" shows MSG75 and the link row exists; a second open of the same URL, a URL made for `beta`, or a POST with no CSRF token records nothing.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/jiralink/{JiraLinkRequestService,JiraLinkController,JiraAccountLink,JiraAccountLinkRepository}.java`, `backend/src/main/resources/db/changelog/changes/<next>-identity-jira-account-links.sql`, `frontend/src/features/auth/pages/JiraLinkPage.tsx`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`, `frontend/src/app/router/routes.tsx`, `frontend/openapi/tsolve-api.yaml`
- Generate only: `frontend/src/shared/api/generated/**`
- Test: `backend/src/test/java/vn/tsolve/identity/jiralink/{JiraLinkRequestServiceTest,JiraLinkControllerTest,JiraAccountLinkRepositoryIT}.java`, `frontend/src/features/auth/pages/JiraLinkPage.test.tsx`
**Dependencies:** Task 26a merged (the Workspace session and CSRF); `jira-app` (KAN-124) calls the service once it exists.
**PR / Jira:** branch `feat/KAN-234-identity-jira-link-confirm`; Jira KAN-234.
**Estimated size:** medium (8 production files incl. frontend); about 9 tests
**Verification:** Tests name AC-001.38 and .69–.72; design row 121. Targeted: `cd backend && mvn -Dtest='JiraLinkRequestServiceTest,JiraLinkControllerTest' -Dit.test=JiraAccountLinkRepositoryIT verify`; frontend `npm run test -- src/features/auth`, then `generate:api` with no diff; full verify of both sides.

## Task 31: Keycloak theme, failure pages and the Login screen check — Jira KAN-300 (Subtask of KAN-275)

**Story:** STORY-001
**Feature:** keycloak-theme
**Decisions owned:** K13, Q6
**Acceptance criteria:** AC-001.67, AC-001.68. Verifies the unchanged AC-001.40, .41, .42 and .43 (their tests from Task 7 stay green).
**Description:** The minimal T-Solve theme `infra/keycloak/themes/tsolve/login/` (logo, colours, no external resource, no script) on Keycloak's login and error pages; the error page shows the MSG06 text "Something went wrong. Please try again later." and a "Back to sign-in" link to `client.baseUrl` (falling back to a neutral URL kept in `theme.properties`); the logo is an inline SVG in `template.ftl`; the realm import sets `loginTheme: tsolve` and `tsolve-web.baseUrl` (dev `http://localhost:5173/`, prod `https://tsolve.io.vn/`). The tests (Task 23's Keycloak container) trigger each failure Keycloak cannot hand back (an expired login session, made by shortening `accessCodeLifespanLogin` to 2 s through the admin client and restoring it; an unknown `kc_idp_hint`; an internal error, made by the fake token endpoint answering 500, a test-only stub; the person cancelling at Google, made by sending `error=access_denied` to Keycloak's broker endpoint) and record which land on the themed page and which reach `/login?error=` (Q6). The fake Google copies Google's `redirect_uri_mismatch` (a 400 on its own host) and the test records it as "upstream page"; the Google cancel (`access_denied`) does not reach the callback: Keycloak 26.4.7 shows its own 400 page, so T8 checks that the themed page shows the MSG06 text and the "Back to sign-in" link. The callback side of AC-001.8 is pinned by the existing `prompt=none` test in `KeycloakSignInEndToEndIT`, not by a control row here. The mock sign-in page and `startSignIn` stay. The last card of the item, a `tsolve-doc-writer` card, updates the authentication guide to this design and links ADR-0003 and the runbook.
**What Hiếu can see or try:** a screenshot of Keycloak's login (when the hint is wrong), error and logout pages in the T-Solve theme; the failure table in the test output.
**Files:**
- Create: `infra/keycloak/themes/tsolve/login/{theme.properties,template.ftl,error.ftl,messages/messages_en.properties,resources/css/tsolve.css}`
- Modify: `infra/keycloak/realm-tsolve.json` (`loginTheme: tsolve`, `tsolve-web.baseUrl`), `docker-compose.yml` (the theme mount), `backend/src/test/java/vn/tsolve/identity/signin/KeycloakSignInEndToEndIT.java` (the theme mount only)
- Create (test-only helpers): `KeycloakItStack` (one singleton stack holder for both new ITs), `ThemePageScan`, `TestBrowser.followUntilSettled`, under `backend/src/test/java/vn/tsolve/identity/signin/`
- Test: `backend/src/test/java/vn/tsolve/identity/signin/KeycloakThemeIT.java`, `backend/src/test/java/vn/tsolve/identity/signin/KeycloakFailurePlacementIT.java`
**Dependencies:** Task 23 merged; Task 28 not required.
**PR / Jira:** branch `feat/KAN-300-identity-keycloak-theme`; Jira KAN-300.
**Estimated size:** small–medium (5 theme files, no production Java; test helpers); 8 tests (T1-T3 theme, T4 the upstream-page recording, T5-T8 themed-page failures incl. the Google cancel); no control row
**Verification:** Tests name AC-001.67 and .68; `cd frontend && npm run test -- src/features/auth src/app/dev` stays green for AC-001.40–.43. Targeted: `cd backend && mvn -Dit.test='KeycloakThemeIT,KeycloakFailurePlacementIT' verify`.

## Task 32: User List backend on PostgreSQL — KAN-107

**Story:** STORY-004
**Feature:** user-list-backend
**Decisions owned:** KAN-107 Q1–Q4 (Dương, 07/11)
**Acceptance criteria:** AC-004.9, AC-004.10, AC-004.11, AC-004.12, AC-004.13, AC-004.14, AC-004.15, AC-004.16, AC-004.17, AC-004.18
**Description:** Design "User List backend — KAN-107, Task 32". `GET /api/v1/users` and `GET /api/v1/users/departments` exactly as `frontend/openapi/tsolve-api.yaml` (no contract change), Admin only through `TSolveUser`, read in the `TenantScope` Workspace; changeset 019 (`user_role`, `app_user.department_id`); the `identity-mock` profile's caller filter and demo seed. Not in scope: a session filter, `/api/v1/me`, KAN-108/110 writes, the frontend MSW pass-through (separate quick change).
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/{controller/UserListController,dto/UserListQuery,dto/UserAccountResponse,dto/DepartmentRefResponse,service/UserListService,service/impl/UserListServiceImpl,repository/UserListRepository}.java`, `backend/src/main/java/vn/tsolve/identity/mock/{MockSignedInCallerFilter,MockIdentityConfiguration,MockIdentityProperties,MockUserListSeeder}.java`, `backend/src/main/resources/db/changelog/changes/019-identity-user-role-department.sql`, `backend/src/main/resources/application-identity-mock.yml`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`
- Test: `backend/src/test/java/vn/tsolve/identity/{dto/UserListQueryTest,controller/UserListControllerTest,UserListIT,mock/MockSignedInCallerIT}.java`
**Dependencies:** none on `main`; Tasks 26a/26b later replace the mock and reuse `user_role`.
**PR / Jira:** branch `feat/KAN-107-identity-user-list-backend`; Jira KAN-107.
**Estimated size:** medium (12 production files); about 25 tests
**Verification:** Tests name AC-004.9 to AC-004.18; `bun harnessclaude/hooks/sdd.ts trace identity STORY-004`; `cd backend && mvn verify`; `cd frontend && npm run generate:api` leaves no diff.

## Task 33: Add user backend on PostgreSQL — KAN-108

**Story:** STORY-005
**Feature:** add-user-backend
**Decisions owned:** KAN-108 Q1–Q3 (Dương, 07/11)
**Acceptance criteria:** AC-005.9, AC-005.10, AC-005.11, AC-005.12, AC-005.13, AC-005.14, AC-005.15, AC-005.16, AC-005.17
**Description:** Design "Add user backend — KAN-108, Task 33". `POST /api/v1/users` and `GET /api/v1/users/creation-options` exactly as `frontend/openapi/tsolve-api.yaml`; changeset 020 (`space`, `space_manager`); the `WorkspaceEmailRegistry` port; the mock seed's Spaces. Not in scope: invitation email, KAN-110 updates, the frontend MSW pass-through.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/{controller/UserCreationController,dto/CreateUserRequest,dto/UserCreationOptionsResponse,dto/SpaceRefResponse,service/UserCreationService,service/WorkspaceEmailRegistry,service/impl/UserCreationServiceImpl,service/impl/AcceptAllWorkspaceEmailRegistry,repository/UserCreationRepository}.java`, `backend/src/main/resources/db/changelog/changes/020-identity-space-manager.sql`
- Modify: `db.changelog-master.xml`, `identity/mock/{MockIdentityProperties,MockUserListSeeder}.java`, `application-identity-mock.yml`
- Test: `backend/src/test/java/vn/tsolve/identity/{service/impl/UserCreationServiceImplTest,controller/UserCreationControllerTest,UserCreationIT}.java`
**Dependencies:** Task 32 (branch `feat/KAN-107-identity-user-list-backend`, PR #162).
**PR / Jira:** branch `feat/KAN-108-identity-add-user-api` stacked on KAN-107; Jira KAN-108.
**Estimated size:** medium (10 production files); about 25 tests
**Verification:** Tests name AC-005.9 to AC-005.17; `sdd.ts trace identity STORY-005`; `cd backend && mvn verify`; `generate:api` no diff.
## Task 34: Update roles, Department and Spaces backend — KAN-110

**Story:** STORY-007
**Feature:** user-roles-backend
**Decisions owned:** KAN-110 Q1–Q10 (Dương, 07/11: all recommendations)
**Acceptance criteria:** AC-007.8, AC-007.9, AC-007.10, AC-007.11, AC-007.12, AC-007.13, AC-007.14, AC-007.15, AC-007.16, AC-007.17, AC-007.18, AC-007.19, AC-007.20, AC-007.21, AC-007.22, AC-007.23
**Description:** Design "Update roles backend — KAN-110, Task 34". `GET /api/v1/users/{userId}`, `GET /api/v1/users/{userId}/handover-options` and `PUT /api/v1/users/{userId}` exactly as `frontend/openapi/tsolve-api.yaml` and the MSW mock; changeset 023 (one Project Manager per Project); the mock seed's Projects. Not in scope: KAN-109 `PUT /users/{id}/status` (next feature), history tables, the frontend MSW pass-through.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/{controller/UserDetailController,dto/UserDetailResponse,dto/UserProjectResponse,dto/UserHandoverOptionsResponse,dto/UpdateUserRolesRequest,service/UserDetailService,service/UserRoleUpdateService,service/impl/UserDetailServiceImpl,service/impl/UserRoleUpdateServiceImpl,repository/UserDetailRepository,repository/UserRoleUpdateRepository}.java`, `backend/src/main/resources/db/changelog/changes/023-identity-one-project-manager.sql`
- Modify: `db.changelog-master.xml`, `identity/mock/MockUserListSeeder.java`
- Test: `backend/src/test/java/vn/tsolve/identity/{service/impl/UserRoleUpdateServiceImplTest,controller/UserDetailControllerTest,UserDetailIT,UserRoleUpdateIT}.java`
**Dependencies:** Task 32 (PR #162) and Task 33 (`feat/KAN-108-identity-add-user-api`: `space`, `space_manager`); this branch is rebased onto Task 33 before its ITs run.
**PR / Jira:** branch `feat/KAN-110-identity-user-roles-backend` stacked on KAN-108; Jira KAN-110.
**Estimated size:** large (13 production files); about 40 tests
**Verification:** Tests name AC-007.8 to AC-007.23; `sdd.ts trace identity STORY-007`; `cd backend && mvn verify`; `generate:api` no diff.
