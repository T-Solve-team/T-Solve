---
module: identity
date: 2026-10-25
owner: BA
---

# Spec: identity

UC-01…03 (owner Trần Trung Hiếu, Jira KAN-103, KAN-104, KAN-106, KAN-160) retain STORY-001…003.
UC-04…07 (KAN-107…110, owner Phạm Tùng Dương) are the frontend/MSW delivery in STORY-004…007. STORY-008 specifies the included Project handover of KAN-111 (Jira UC-08). STORY-009 specifies the included Space handover of KAN-112 (Jira UC-09); neither adds a standalone screen.

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-11-07 | STORY-004 | **User List backend (KAN-107).** New AC-004.9 to AC-004.18: `GET /api/v1/users` and `GET /api/v1/users/departments` against PostgreSQL in the bound Workspace's database (filters, sort, paging, 400/401/403, tenant isolation), the interim `identity-mock` sign-in of a configured Account and its demo seed. The frontend contract in `tsolve-api.yaml` is unchanged; AC-004.1 to AC-004.8 unchanged. Stores: the `user_role` table (baseline names; UUID keys like `app_user`) and `app_user.department_id` are added by this change, in the shape Task 26b describes, so Task 26b reuses them | User (Dương), 07/11 for the demo of 08/11: Q1 endpoint on `TSolveUser` + `TenantScope` with a profile-only mock filler (not a session filter; PR #119's `identity/session` files are not revived), Q2 add `user_role` and `department_id` now, Q3 the frontend pass-through is a separate quick change, Q4 mock-profile seed and a note to the identity owners (KAN-275) |
| 2026-11-05 | STORY-001, STORY-002 | **Sign-in on Keycloak (K1–K14, ADR-0003; step 2 of KAN-275).** Retired, ids never reused: AC-001.2, .4, .5 (replaced by .52–.55), AC-001.17 and .19 (the hand-off code), AC-001.35, .36, .37 and .39 (replaced by .69–.72 and .67–.68), AC-001.44, .45, .46, .47 (the `login.` sign-in cookie; replaced by .53), AC-001.49, .50 and AC-001.51 (the start cookie of the uncommitted Task 10 change is superseded and not approved in git). Reworded: AC-001.1, .6–.16, .18, .22, .28–.31, .38, .48; AC-002.1 (200 with `logoutUrl`, Q7). Unchanged: AC-001.3, .20, .21, .23–.27, .32–.34, .40–.43; AC-002.2–.7. New: AC-001.52–.72 (authorization request, redirect URI, no HTTP session, `sub` refusal, the provisioning port and its adapter, SSO limits, Keycloak hardening and mappers, the `tsolve-ops` realm, the theme, the Jira link confirm), AC-002.8. Requirements 1–3 and 6, NFR 3–5, Out of Scope and Interface Contracts reworded. AC-001.73 (a Workspace `PENDING_SIGN_IN` until a test sign-in) was written and retired the same day: Q4 revised, a Workspace is Active at creation, there is no `PENDING_SIGN_IN` status and no `activate` operation (the test sign-in is a runbook step) | KAN-275 plan: step-1 questions Q1 = B, Q2 = A, Q3 = A, Q4 = A, Q5–Q9 = A, Q10 = C, Q11–Q14 = A, Q15 (drop PR #119's filter), Q16 = A (Hiếu, 5/11) |
| 2026-11-04 | STORY-001 | **Login CSRF at `complete`.** Add AC-001.51: sign-in start on a Workspace site sets the host-only cookie `__Host-TSOLVE_START` (Max-Age 600), its hash travels state → hand-off code, and `complete` compares it in constant time, clears the cookie in every answer, and answers `/login?error=MSG141` (code still consumed, no session) when the cookie is missing or another browser's. Interface Contracts rows for `sign-in` and `complete` name the cookie (wording only). AC-001.17 to .19 are not reworded | KAN-232 Task 10 plan, Q7 (Hiếu, 4/11) |
| 2026-11-04 | new STORY-010; STORY-001 Requirement 8 and Interface Contracts | **The platform's `FirstAdminCreator` port (KAN-98, platform STORY-013).** New STORY-010 with AC-010.1…AC-010.8: `identity` implements `FirstAdminCreator.createFirstAdmin(workspaceId, email)` and returns the Account's `UUID`; the Account is created Active with the Role Admin in the bound Workspace's own database, in one transaction with its Role row; it does not claim the email again (the platform claimed it at step 4 of platform AC-013.1) and creates no password, token or invitation link; a wrong or unbound Workspace, a bad email or a duplicate Account is refused, creating nothing, so that platform AC-013.16 can undo the Workspace. Requirement 8 and the `Services` list of Interface Contracts name the port; Stores and Required from `platform` reworded (the port is settled; the first Admin is not created by a screen); Out of Scope adds the invitation email, the job and the Console. No existing criterion is changed, removed or renumbered | User decision of 4/11: the port belongs to `platform`'s `WorkspaceProvisioning`, `identity` implements it, `createFirstAdmin` returns the Account `UUID`, the first Admin is Active and invited by email, the email is claimed through the registry's `workspace_emails` (platform STORY-013: AC-013.13…AC-013.19; step-1 answers Q1…Q5 of KAN-98, all recommendations) |
| 2026-10-31 | STORY-009 | Draft KAN-112 Space handover criteria AC-009.1–009.9, reusing the UC-06/07 popup and atomic mutations | User selected frontend, MSW and an OpenAPI proposal pending backend integration; Jira requires every Space to be handed over before deactivation or role removal |
| 2026-10-29 | STORY-008 | Draft KAN-111 frontend/MSW Project handover criteria AC-008.1–008.9; extend requirement 10 and delivery exclusions, clarify that there is no standalone handover screen | User requested one branch per task from main, then selected waiting for KAN-109/110 to merge while preparing scope and spec; reuse their existing handover instead of duplicating it |
| 2026-10-25 | STORY-001..STORY-003 | Initial spec of the UC-01…03 slice: central sign-in through `login.` with Google OIDC, the session per Workspace site, the Platform Operator and Jira-link sign-ins, log out, My Profile with solution history. Written from Report3 (GBR-03, GBR-19, GBR-28, GBR-37, D1, D9, D13, D14, D22, D23, D36), Stories TS-020, TS-021, TS-023, TS-127 and the planning decisions Q1–Q16 of 25/10 | KAN-197 (Phase 1, multi-tenant baseline) |
| 2026-10-25 | — | UC-04…09 (View user list, Add Account, Update Account status, Update roles and Department, Hand over Projects, Hand over Spaces) will be added as STORY-004 onwards by their owner Phạm Tùng Dương (KAN-107…112) | One module, two owners (D40: the Jira assignee owns the Story) |
| 2026-10-25 | STORY-001, STORY-003 | Interface Contracts only: `MySolution.ticketId` is a UUID string (`format: uuid`); the Account table is `accounts` and the Roles table `account_roles`, each with a UUID `id` under the platform convention (platform AC-001.8); `jira_account_links` keeps its D1 name. No acceptance criterion added, reworded or removed (`SpaceRef` follows the fe-foundation contract) | Change request KAN-196 Q2 (the user, 25/10): UUID primary keys and plural table names in every module (Phan_cong v6 §1); SPEC-P1-7 |

| 2026-10-25 | STORY-004..007 | Initial frontend scope for KAN-107–110, with working MSW data | User requested frontend screens for their four In Progress Jira stories and selected MSW before backend integration |
| 2026-10-26 | STORY-004..STORY-007 | Merge the UC-04–07 frontend/MSW slice after main's unchanged STORY-001–003; renumber its AC ids to AC-004–007; add AC-004.8 for the 100-character search limit and align UserAccount ids with the UUID convention | User-requested KAN-107 review repair: preserve the other owner's sign-in/logout/profile specification and align the account contract with main |
| 2026-10-28 | STORY-001 | Reword Functional req. 1, AC-001.1, AC-001.4, AC-001.5, AC-001.29 and AC-001.35: the central sign-in origin comes from `TSOLVE_LOGIN_ORIGIN` and the Platform Console origin from `TSOLVE_ADMIN_ORIGIN` (the production URLs stay as examples). Reword AC-001.18: the session cookie is `__Host-TSOLVE_SESSION`. Reword AC-001.21, AC-001.28, AC-001.30 and AC-001.31 to name the configured Platform Console origin the same way, and AC-002.1 and the Interface Contracts row for `GET /api/v1/auth/complete` to use `__Host-TSOLVE_SESSION` (wording only); NFR 4 names `TSOLVE_LOGIN_ORIGIN`, `TSOLVE_ADMIN_ORIGIN` and `TSOLVE_SIGNING_KEY`. Reword AC-001.42: the Login codes are fe-foundation's list (MSG06, MSG09, MSG10, MSG122, MSG123, MSG141, without MSG07 and MSG120); MSG122 needs a valid `google_domain`; `error=MSG120` leaves the message area empty and the MSG120 full page shows instead | KAN-103 plan (Hiếu, 28/10): Q7 = A (NFR 4: hosts come from the environment; local dev and tests use other origins); Q2 = B (KAN-104: the session cookie is valid only on the company's own site, enforced by the browser); Q4 = A (agree with fe-foundation AC-012.3, AC-012.4 and AC-011.4) |
| 2026-10-31 | STORY-001 | Add AC-001.44–.47 (login CSRF: `authorize` sets the sign-in cookie `__Host-TSOLVE_SIGNIN` and accepts each state value once; the callback refuses a state value without the matching cookie and expires the cookie in every answer), AC-001.48–.49 (a Workspace with no Google domain is refused at sign-in start, `authorize` and the callback) and AC-001.50 (the state's Workspace no longer exists at the callback). Reword AC-001.2 (the state keeps the cookie hash and the "authorized" mark), AC-001.14 (the first step also covers the sign-in cookie and the state's Workspace), NFR 3 (the browser also holds the short-lived sign-in cookie), and the Interface Contracts rows for sign-in start, `authorize` and `callback`; reworded AC-001.1, .4, .14, .29, .31 (spec review) | Task 8 review (Hiếu, 29/10), design row 45; KAN-231 plan Q1–Q14 (Hiếu, 31/10); KAN-231 spec review (Hiếu, 31/10) |
| 2026-11-01 | STORY-003 | My Solutions grows to frame (d). Reword AC-003.6 (filters `status`, `projectId`, `from`, `to`; Ticket Resolver or Project Manager only), AC-003.7, AC-003.8, AC-003.9, AC-003.10 (401 for both My Solutions endpoints and the summary; `/me/spaces` stays open to every Employee), AC-003.11 (six columns, Latest decision, Date), AC-003.13 (MSG04 also when the filters match nothing) and AC-003.14 (MSG06 per failing part). Add AC-003.17 (403 MSG08 without the role), AC-003.18 (`GET /api/v1/me/solutions/summary`), AC-003.19 (filter semantics), AC-003.20 (section only for the role), AC-003.21 (six counters), AC-003.22 (three filters, URL state), AC-003.23 (Latest decision cell). Interface Contracts: `MySolution` + `projectId`, `latestDecision`; new `MySolutionSummary`, `ProjectRef`, `ReviewDecision`; the summary endpoint row; the list parameters. Dependency note: the written-ticket lookup also returns `latestDecision`, filters and counts; the Project Manager line now matches AC-003.20. AC-003.16 unchanged | KAN-238 plan; FE Lead 1/11 (all recommendations) |
| 2026-11-01 | STORY-003 | Add AC-003.15 (the Spaces card is always shown; spinner at once; MSG142 "No Spaces found" when "Spaces I can view" is empty) and AC-003.16 (the Projects card is always shown; MSG143 "Not a member of any Project." in place of the table). Reword AC-003.1 (the Projects list is read-only; its empty state is AC-003.16) and AC-003.3. Messages MSG142 and MSG143 are new. AC-003.11 to .13 (My Solutions) unchanged | Hiếu, 1/11 (KAN-237 QA): reverses plan decision Q4 for the Spaces and Projects cards only; Account rows and "Spaces I manage" keep Q4; My Solutions is for KAN-238 |
| 2026-10-31 | STORY-003 | AC-003.12 reworded: the Ticket ID, not the whole row, is the link (no shared DataTable change) | KAN-160 plan review; FE Lead 31/10 (R1 A, R1' A) |
| 2026-11-01 | STORY-003 | AC-003.14: a 403 on My Solutions shows MSG08 | KAN-238 Task 18 QA plan Q2; FE Lead 1/11 |
| 2026-11-04 | STORY-001 | Interface Contracts "Stores" wording only: the Database Tables baseline (Markdown companion `00-baseline/T-Solve_Database_Tables.md`; the source PDF is kept outside the repo) wins on names: keys are `id` (not `<table>_id`), the Registry's table is `workspace_email` (singular), `app_user.full_name` is at most 100 characters (was 255), and `app_user.first_sign_in_at`, `last_sign_in_at` and `hand_off_code` are additions the baseline lists. No acceptance criterion added, reworded or removed | KAN-235 Task 9b, Hiếu 04/11 |
| 2026-11-04 | STORY-001 | Interface Contracts "Stores" wording only: the Account table is `app_user`, the Roles table `user_role` and the hand-off table `hand_off_code`, singular with `<table>_id` keys (the KAN-33 DDL, platform AC-001.20); the Java names stay `Account`, `AccountRole`, `HandOffCode`. No acceptance criterion added, reworded or removed | KAN-235 Task 9b QA plan Q1 = A (Hiếu) |
| 2026-11-03 | STORY-001, STORY-002 | Align with the multi-tenant HLA (29/10). Reword AC-001.15 (the Account lookup runs in the Workspace's own database, reached only through the bound tenant context) and AC-002.4 (the session lives in the shared session store, and the 15-minute bound for `EXPIRED` stays). Reword NFR 5 (the two-Workspace tests use two databases), Out of Scope (the Tenant Resolver, the Tenant Context and Database Router and the per-database RLS guard → `platform`) and Required from `platform` (the Tenant Context bound before the transaction with no default fallback, the Registry lookup, the first-Admin invitation). Rewrite the Interface Contracts "Database" paragraph: one database per company, so `accounts`, `account_roles` and `jira_account_links` have no `workspace_id`; platform-wide email uniqueness goes through the Tenant Registry; sign-in states and hand-off codes live in the Platform / Tenant Registry database; sessions live in Redis. The Platform Console on `admin.` stays (AC-001.21, .28–.34, NFR 4). No criterion is added or removed | KAN-231 HLA change, step 2 (Hiếu, 3/11: Q1–Q4 = A); step-1 mismatches H1–H14 |

## Requirements

Planning note, 2026-10-29: the user selected waiting for KAN-109 and KAN-110 to merge into `main`, while preparing KAN-111 scope and spec now. STORY-008 requires approval of this version. On 2026-10-30 the user requested continuing KAN-111 while KAN-110 approaches main: verify the KAN-109 caller first on merged main; the KAN-110 caller and final completion still require KAN-110 on main. It does not authorize backend work or advancing KAN-112.

### Functional
1. People sign in only with a Google account of their company's Google domain, through Keycloak 26.x (realm `tsolve`, at
   `TSOLVE_LOGIN_ORIGIN`, `https://login.tsolve.io.vn` in production), which brokers to Google. Keycloak replaces the
   sign-in pages that `identity` built on `login.`: the backend no longer serves `login.`, keeps no signed state value
   and no hand-off code, and has no hand-written OIDC client (K1, K7, K12; ADR-0003). Each Workspace site is an OIDC
   client of Keycloak (the confidential client `tsolve-web`, PKCE `S256`, the site's own redirect URI) and the sign-in
   ends on that site's own callback (GBR-03, GBR-37, D9).
2. Sign-in has three targets:
   - a Workspace, started from its Workspace site, through the realm `tsolve`, one Keycloak Organization and one Google
     identity provider per Workspace (K2, K3);
   - the Platform Console, started from `admin.` through the separate realm `tsolve-ops`, with a second factor, and
     allowed only for an allow-listed email (D13, D36, Q1);
   - a Jira account link, requested by the T-Solve app through `api.` and confirmed by the person on the Workspace
     site (GBR-19, D1, Q2).
3. After a Workspace sign-in the callback on the Workspace site creates the session itself; there is no hand-off. The
   session is valid only on that Workspace site and never on `admin.`; an Operator session is valid only on `admin.`
   (GBR-03, D13). A session ends after 8 hours, or after 60 minutes without activity (MSG07); Keycloak's own SSO
   session has the same limits, so an expired session never signs in again silently (K9).
4. Each request re-reads the Account. A Deactivated Account loses its session at once. A Role change applies from
   the next request (GBR-03, TS-027).
5. After sign-in the browser opens `returnTo` or `/`. The landing page per Role and the Workspace Setup redirect
   are fe-foundation AC-003.1 and AC-003.2 (not repeated here).
6. Logging out ends the server session and Keycloak's SSO session (RP-initiated logout, K10). The chat history of the session is cleared in the browser by
   fe-foundation AC-008.1 and chatbot AC-002.7, and on the server through the `SessionEnded` event of this
   module (UC-02, GBR-36).
7. My Profile shows the account information from the session (`/me`), the Spaces the user manages and can view,
   and the Tickets whose Solution the user wrote, with their status (UC-03, TS-127).
8. This slice creates the Account store that sign-in reads: email, full name, status, Roles, the Department of a
   Project Manager, and the sign-in times. The UC-04…09 stories extend it (decision Q8). The full name comes from
   Google at every sign-in (decision Q7). The first Admin Account of a new Workspace is created in the same store,
   through the port `FirstAdminCreator` that `platform` declares and this module implements (STORY-010; platform
   AC-013.14).

9. Implement the Admin User List (1.3.1), User Detail (1.3.2, creation and editing), and required Hand Over Responsibilities popup (1.3.3).
10. Follow KAN-107, KAN-108, KAN-109 and KAN-110 as read from the signed-in user's Jira board on 2026-10-25. KAN-111, inspected in Jira on 2026-10-29, extends that frontend/MSW delivery with explicit Project-handover criteria in STORY-008. KAN-112, inspected in Jira on 2026-10-31, adds Space handover in STORY-009 using MSW and the proposed OpenAPI contract pending backend integration; STORY-001–003 keep their separately specified backend and authentication scope.
11. Use the existing English screen names, shared UI kit, message catalog, role guards, form validation and registered routes.
12. Use generated API hooks with TanStack Query and realistic, mutable MSW handlers. Mock checks demonstrate business rules; they do not establish production security guarantees.

### Non-Functional
1. Every acceptance criterion has a test whose name cites its id. Backend: JUnit `…_AC_001_1`. Web app: `it`
   names start with the id. `sdd.ts trace identity` passes for STORY-001…003.
2. `mvn verify` passes on every commit. The frontend `verify` passes too: `format:check`, `lint`,
   `typecheck`, `test`, `build`, and `generate:api` with no diff.
3. No token, ID token, Google credential or authorization-request value is kept in browser storage. The browser
   holds only the HttpOnly session cookie and, during a sign-in, the short-lived HttpOnly cookie
   `__Host-TSOLVE_AUTHREQ`, whose random value is neither a token nor a state value (AC-001.53). The ID token stays
   on the server, inside the session, as the `id_token_hint` (AC-001.18).
4. Secrets and hosts come only from the environment, with no default (architecture: Configuration):
   - `TSOLVE_LOGIN_ORIGIN` (Keycloak's public origin; `https://login.tsolve.io.vn` in production)
   - `TSOLVE_ADMIN_ORIGIN` (the Platform Console origin; `https://admin.tsolve.io.vn` in production)
   - `KEYCLOAK_WEB_CLIENT_SECRET` (the secret of the client `tsolve-web`; the client id is fixed) and
     `KEYCLOAK_CONSOLE_CLIENT_SECRET` (the client `tsolve-console` of the realm `tsolve-ops`)
   - `PLATFORM_OPERATOR_EMAILS` (the allow-list, a comma-separated list)
   - `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`
   - only in the profiles that create or remove Workspaces (`ops`, the Platform Console; AC-001.61):
     `KEYCLOAK_ADMIN_CLIENT_SECRET` (the client `tsolve-provisioner`), `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`
   - `TSOLVE_SIGNING_KEY` and the `tsolve.oidc.google.*` endpoints no longer exist (K12). At start the backend reads
     the discovery document of each realm at the issuer it puts in tokens and refuses to start when it cannot (R11).
5. Two-Workspace tests cover the session and sign-in rules: AC-001.7, AC-001.11, AC-001.15, AC-001.20 and AC-001.32.
   Each uses two Workspaces, each with its own database (or the Database Router test double), Accounts of the same
   Roles, and, for the sign-in, two Google identity providers of one Keycloak (Testcontainers, brokering to the
   WireMock fake issuer; CI never calls Google, K14).
6. Error bodies follow be-foundation (`ProblemDetail` with a Report3 `code`). Sign-in failures are redirects with
   a code, never backend text.

7. THE frontend SHALL render these screens at the project's minimum desktop viewport of 1366 × 768 without inaccessible actions or clipped form controls.
8. THE frontend SHALL provide keyboard-accessible controls, visible field labels, pending states, and disabled duplicate submission controls.
9. THE frontend SHALL keep server data in TanStack Query and filters, sorting and pagination in URL search parameters.
10. THE implementation SHALL add no npm dependencies and reuse existing feature, API generation, form and UI patterns.

## User Stories

### STORY-001: Sign in with Google through Keycloak
- As an Employee, I want to sign in with my company Google account on my company's Workspace site and land on the
  page of my Roles, so that I use T-Solve without a separate password and only in my own Workspace. (KAN-103
  central sign-in, KAN-104 session per Workspace site and Login screen.)
- Acceptance:
  - AC-001.1: WHEN a browser opens `GET /api/v1/auth/sign-in` on a Workspace site whose Workspace passes AC-001.48, THE backend SHALL save an authorization request for the Workspace that the platform resolved from the host (AC-001.53) and answer 302 to the authorization endpoint of the Keycloak realm `tsolve` (`TSOLVE_LOGIN_ORIGIN` + `/realms/tsolve/protocol/openid-connect/auth`; `https://login.tsolve.io.vn` in production), without a signed-in user.
  - AC-001.3: WHERE `returnTo` is given to the sign-in start, THE backend SHALL keep it only if it is a relative path of the same site (one leading `/`, not `//`, no scheme, no host) and SHALL use `/` otherwise.
  - AC-001.6: IF the callback on a Workspace site gets no `__Host-TSOLVE_AUTHREQ` cookie, or a cookie or `state` value that matches no saved authorization request (unknown, expired, already used, or another browser's), or a saved request whose Workspace is not the Workspace of the callback's host, THEN THE backend SHALL redirect to `/login?error=MSG141` on that site, create no session, change no Account and record no Jira account link (GBR-03).
  - AC-001.7: THE callback SHALL take the target Workspace only from the Workspace that the platform resolved from the host and the saved authorization request, and SHALL ignore every other parameter, header or cookie that names a Workspace or a site (GBR-03, GBR-37).
  - AC-001.8: IF Keycloak returns an `error` to the callback (for example the person cancelled at Google), with a valid saved authorization request, THEN THE backend SHALL redirect to `/login?error=MSG06` on that Workspace site and create no session.
  - AC-001.9: IF the code exchange with Keycloak fails, or the ID token fails verification (signature, issuer equal to the realm `tsolve`, audience `tsolve-web`, expiry, nonce, or `email_verified` not true), THEN THE backend SHALL redirect to `/login?error=MSG06` on the Workspace site and create no session.
  - AC-001.10: IF the callback's Workspace is not Active, THEN THE backend SHALL redirect to `/login?error=MSG120` on its Workspace site and create no session.
  - AC-001.11: IF the ID token's `organization` claim does not name the Keycloak Organization of the callback's Workspace, or its `hd` claim is missing or differs (ignoring case) from the Workspace's Google domain, THEN THE backend SHALL redirect to `/login?error=MSG122&google_domain=<the Workspace's Google domain>` on its Workspace site and create no session (GBR-03, D22); both claims are required.
  - AC-001.12: IF the Workspace's database has no Account whose Keycloak `sub` equals the token's `sub` and no Account with no `sub` yet whose email equals the verified email (compared ignoring case), THEN THE backend SHALL redirect to `/login?error=MSG09` on its Workspace site and create no session.
  - AC-001.13: IF the matched Account is Deactivated, THEN THE backend SHALL redirect to `/login?error=MSG10` on its Workspace site and create no session.
  - AC-001.14: THE callback SHALL check a Workspace sign-in in this order and report only the first failure: the saved authorization request (MSG141; AC-001.6), the Keycloak result and ID token (MSG06; AC-001.8, AC-001.9), the Organization and `hd` (MSG122), Workspace Active (MSG120), the Account match (MSG09; AC-001.12, AC-001.56), Account Active (MSG10).
  - AC-001.15: THE callback SHALL look up the Account only in the database of the callback's Workspace, reached through that Workspace's tenant context bound before the transaction, so an Account of another Workspace is never found or changed (GBR-37).
  - AC-001.16: WHEN every check of AC-001.14 passes, THE backend SHALL:
    - match the Account by `sub`, or, on a first sign-in only, by the verified email, and bind the token's `sub` to that Account (AC-001.12, D14);
    - set the Account's full name from the `name` claim (unchanged when the claim is empty);
    - record the sign-in time, and the first sign-in time when the Account has none;
    - create the session of AC-001.18 at once, with no hand-off code.
  - AC-001.18: WHEN the sign-in of AC-001.16 succeeds, THE backend SHALL:
    - create a new session with a new session id in Redis for that Account and Workspace, and keep the ID token inside it as the `id_token_hint` for AC-002.1;
    - set the cookie `__Host-TSOLVE_SESSION` (HttpOnly, Secure, SameSite=Lax, Path `/`, no Domain attribute; with the `__Host-` prefix the browser refuses the cookie otherwise, so it is sent only to that host);
    - redirect to the kept `returnTo`, or to `/` when there is none.
  - AC-001.20: IF a request to a Workspace site carries a session created on the Workspace site of another Workspace, THEN THE backend SHALL answer 401 with `code` `MSG123`, not run the endpoint, and not end that session (GBR-03).
  - AC-001.21: IF a request to the configured Platform Console origin (`https://admin.tsolve.io.vn` in production) carries a Workspace session, THEN THE backend SHALL answer 401 with `code` `MSG123` and not run the endpoint (D13).
  - AC-001.22: THE backend SHALL treat a request to `api.` as having no session, whatever session cookie it carries; `login.` is served by Keycloak and a request that still reaches the backend with that host SHALL be treated the same way (K1).
  - AC-001.23: IF a request carries a session created more than 8 hours ago, or whose last activity was more than 60 minutes ago, THEN THE backend SHALL end the session and answer 401 with `code` `MSG07` (GBR-03).
  - AC-001.24: WHEN a request with a valid session comes without the header `X-TSolve-Background: 1`, THE backend SHALL count it as activity and restart the 60-minute limit.
  - AC-001.25: WHEN a request with a valid session carries the header `X-TSolve-Background: 1`, THE backend SHALL answer it as usual but SHALL not count it as activity. The 8-hour limit applies to it as well (decision Q6: polling and real-time updates do not keep a session alive).
  - AC-001.26: WHEN a request carries a valid Workspace session, THE backend SHALL re-read the Account at that request and put a `TSolveUser` with its current id, email, full name and Roles in the security context (be-foundation AC-002.2), so a Role change applies from the next request.
  - AC-001.27: IF the Account of a Workspace session is Deactivated, THEN THE backend SHALL end the session at its next request and answer 401 with `code` `MSG10` (GBR-03).
  - AC-001.28: WHEN a browser opens `GET /api/v1/auth/sign-in` on the configured Platform Console origin (`https://admin.tsolve.io.vn` in production), THE backend SHALL save an authorization request naming the Platform Console (AC-001.53) and answer 302 to the authorization endpoint of the Keycloak realm `tsolve-ops`, for the client `tsolve-console`, with PKCE `S256`, a state value, a nonce, and the redirect URI `/api/v1/auth/callback` on `TSOLVE_ADMIN_ORIGIN` (D13).
  - AC-001.29: WHEN the checks of AC-001.6, AC-001.8 and AC-001.9 pass for a Platform Console sign-in against the realm `tsolve-ops` and the verified email is in `PLATFORM_OPERATOR_EMAILS` (compared ignoring case), THE backend SHALL create an Operator session at that callback on `admin.`, with no hand-off code, set `__Host-TSOLVE_SESSION` as AC-001.18 says, and redirect to the kept `returnTo` or `/`. The session SHALL have no Account, no Workspace and none of the five Roles.
  - AC-001.30: IF the verified email of a Platform Console sign-in is not in the allow-list, THEN THE backend SHALL redirect to `/login?error=MSG135` on the configured Platform Console origin and create no session (GBR-03).
  - AC-001.31: IF Keycloak returns an error, or the code exchange or the ID token check fails, for a Platform Console sign-in whose saved authorization request is valid, THEN THE backend SHALL redirect to `/login?error=MSG06` on the configured Platform Console origin and create no session.
  - AC-001.32: IF a request to a Workspace site carries an Operator session, THEN THE backend SHALL answer 401 with `code` `MSG123` and not run the endpoint (D13).
  - AC-001.33: IF a request carries an Operator session whose email is no longer in the allow-list, THEN THE backend SHALL end the session and answer 401 with `code` `MSG135`.
  - AC-001.34: THE backend SHALL apply the limits of AC-001.23 to AC-001.25 to Operator sessions as well.
  - AC-001.38: THE backend SHALL take the Workspace and the ticket tracker account of a Jira account link only from the verified link request of AC-001.69, never from a request parameter, header or cookie (GBR-19).
  - AC-001.40: THE Login screen (1.1.1) SHALL show one button "Sign in with Google" and a message area, and no email, password or sign-up field.
  - AC-001.41: WHEN the user clicks "Sign in with Google", THE Login screen SHALL start the sign-in through fe-foundation's `startSignIn` with the `returnTo` of the Login URL.
  - AC-001.42: WHEN the Login URL has `error` set to MSG06, MSG09, MSG10, MSG122, MSG123 or MSG141, THE Login screen SHALL show that message's catalog text in its message area, as given by fe-foundation's `useSignInMessage` (AC-012.3), with `{google_domain}` filled in from `google_domain` for MSG122. MSG122 SHALL be shown only with a valid `google_domain` (fe-foundation AC-012.4). `error=MSG120` SHALL leave the message area empty; on a suspended Workspace the web app shows the MSG120 full page instead (fe-foundation AC-011.4).
  - AC-001.43: IF the Login URL has an `error` value that is not one of the codes of AC-001.42, THEN THE Login screen SHALL show an empty message area.
  - AC-001.48: IF `GET /api/v1/auth/sign-in` on a Workspace site resolves a Workspace that has no Google domain or has no Keycloak Organization id, THEN THE backend SHALL answer 302 to `/login?error=MSG141` on that Workspace site, create no authorization request, and log a `WARN` entry that names only the Workspace id.
  - AC-001.52: THE authorization request that sign-in start sends to Keycloak SHALL carry `client_id` `tsolve-web`, `response_type` `code`, `scope` `openid email profile`, a random `state` value, a random nonce, a PKCE `S256` code challenge, the redirect URI of AC-001.54 and `kc_idp_hint` set to `google-<slug>` (the alias of AC-001.58), and the PKCE verifier and the nonce SHALL stay on the server (K5, K7, D9).
  - AC-001.53: THE backend SHALL keep each authorization request only on the server, in Redis, under a key that is the SHA-256 of a random 32-byte value (base64url) that sign-in start sets in the cookie `__Host-TSOLVE_AUTHREQ` (HttpOnly, Secure, SameSite=Lax, Path `/`, no Domain attribute, Max-Age 600 seconds) in the same 302, sent with `Cache-Control: no-store`; the request SHALL hold the target, the Workspace id, the state, the nonce, the PKCE verifier and the kept `returnTo`, SHALL be read and deleted in one step so that it is usable once, and the callback SHALL expire the cookie (`Max-Age=0`) in every answer it gives.
  - AC-001.54: THE backend SHALL send and accept, as the redirect URI of a Workspace sign-in, only `https://<slug>.tsolve.io.vn/api/v1/auth/callback` of the Workspace site that started it (the site's own origin, exact match, no wildcard), and SHALL answer 404 to the callback path on any host that is neither a Workspace site nor the Platform Console origin (K5).
  - AC-001.55: THE sign-in start and the callback SHALL create no replica-local HTTP session and keep no `SecurityContext` outside Redis, so that any API replica can answer the callback (K7).
  - AC-001.56: IF the token's `sub` is already bound to another Account of the Workspace's database, or the Account matched by email already holds a different `sub`, THEN THE backend SHALL redirect to `/login?error=MSG09` on its Workspace site, create no session, change no Account, and log a `WARN` entry that names only the Account id (K8, Q11).
  - AC-001.57: THE identity module SHALL declare the port `WorkspaceSignInProvisioner` with `provision(workspaceId, siteName, googleDomain)`, which returns the Keycloak Organization id, `check(workspaceId)`, which reports which of the four Keycloak objects of AC-001.58 exist, and `remove(workspaceId)`, which the platform's `WorkspaceProvisioning` calls when it creates, checks, undoes or removes a Workspace (K6).
  - AC-001.58: WHEN `provision` runs, THE Keycloak adapter SHALL create through the Keycloak Admin REST API, in the realm `tsolve`: one Organization whose domain is the Workspace's Google domain; one Google identity provider with the alias `google-<slug>`, the Google client id and secret of the provisioning profile, `hostedDomain` set to the Workspace's Google domain and the link to that Organization, hidden on the login page; and, in the client `tsolve-web`, the exact redirect URI of AC-001.54 and the post-logout redirect URI `https://<slug>.tsolve.io.vn/login` (K2, K3, K5).
  - AC-001.59: THE Keycloak adapter SHALL run `provision` and `remove` one at a time (an advisory lock), SHALL read the client `tsolve-web` again after each write to its redirect URI list and verify that every earlier URI is still there, and IF a step fails THEN SHALL delete the objects it created in reverse order and throw, each delete idempotent by the alias `google-<slug>`, so that the platform can undo the Workspace (K6, Q12).
  - AC-001.60: WHILE the platform's provisioning does not exist or no Keycloak Admin credential is configured, THE identity module SHALL provide a no-op bean of the port that creates nothing and returns no Organization id, so that the backend starts and Workspace sign-in is refused under AC-001.48 (K6).
  - AC-001.61: THE Keycloak adapter SHALL authenticate with a dedicated confidential client `tsolve-provisioner` whose service account holds only the realm-management roles for identity providers, clients, organizations and users, and THE Google client id and secret and the `tsolve-provisioner` secret SHALL be loaded only in the profiles that create or remove Workspaces (`ops` and the one that serves the Platform Console), never by a process that serves a Workspace site (K6, Q14, Q16).
  - AC-001.62: WHEN `remove(workspaceId)` runs, THE Keycloak adapter SHALL delete the Google identity provider, the two redirect URIs, the Keycloak users of the Organization and the Organization, and a call for a Workspace that has none of them SHALL succeed and change nothing (K6, R12).
  - AC-001.63: THE Keycloak realm `tsolve` SHALL have an SSO Session Max of at most 8 hours and an SSO Session Idle of at most 60 minutes, so that an expired T-Solve session never signs in again silently (K9).
  - AC-001.64: THE Keycloak realm `tsolve` SHALL allow no self-registration and no username and password form, SHALL hide every identity provider on its login page, SHALL use a first-broker-login flow that creates the user automatically, shows no profile review and never links to an existing user by email, SHALL add each new user to the Organization of the identity provider, and SHALL have brute-force detection on; WHEN the authorization request has no `kc_idp_hint`, THE login page SHALL list no identity provider and no form (K13, Q13).
  - AC-001.65: THE Keycloak client `tsolve-web` SHALL be confidential, SHALL require PKCE `S256`, and SHALL map the Organization (`organization` claim) and the Google `hd` attribute into the ID token, so that a token without them is refused by AC-001.11 (K5, Q10).
  - AC-001.66: THE Keycloak realm `tsolve-ops` SHALL have one Google identity provider without `hd`, no tenant identity provider, no self-registration, a required second factor (TOTP or WebAuthn) for every user, and the confidential client `tsolve-console` with PKCE `S256` and the exact redirect URI `TSOLVE_ADMIN_ORIGIN` + `/api/v1/auth/callback` (Q1).
  - AC-001.67: THE Keycloak login and error pages SHALL use the minimal T-Solve theme, and WHERE Keycloak cannot hand a failure back to the callback (Google `redirect_uri_mismatch`, an expired Keycloak login session, an unknown identity provider hint, a Keycloak internal error), THE themed page SHALL show the exact Report3 text of MSG06 and a "Back to sign-in" link to the base URL of the client that started the request (K13, Q6).
  - AC-001.68: THE Keycloak theme SHALL load no script and no resource from a host other than Keycloak's own and SHALL show no link to any Workspace site except that "Back to sign-in" link (K13).
  - AC-001.69: WHEN `jira-app` asks `JiraLinkRequestService` for a link with a Workspace and a ticket tracker account taken from a verified Forge Invocation Token, THE backend SHALL store a link request in Redis (random 32-byte id, 10 minutes, usable once) holding both values and return `https://<slug>.tsolve.io.vn/jira-link?request=<id>` (GBR-19, D1, Q2).
  - AC-001.70: WHEN a browser opens `/jira-link?request=<id>` on that Workspace site and has no session, THE web app SHALL send the person through the sign-in of AC-001.1 with that URL as `returnTo`; WHEN the person is signed in as an Account of that Workspace, THE web app SHALL show "Link Jira user X (site S) to your account Y?" with a "Link" button and a "Cancel" button, and SHALL record nothing.
  - AC-001.71: WHEN a signed-in person of that Workspace sends `POST /api/v1/auth/jira-link/confirm` with the request id and a valid CSRF token, THE backend SHALL consume the link request, record in that Workspace the Jira account link between the request's ticket tracker account and the session's Account, replacing any earlier link of that ticket tracker account in the Workspace, and answer with MSG75 (`{tracker_name}` = Jira), creating no new session.
  - AC-001.72: IF the link request is missing, expired, already used or made for another Workspace, THEN THE backend SHALL record no link and answer 400 with `code` `MSG141`; IF the confirm POST has no valid session, THEN THE backend SHALL answer 401 with `code` `MSG07`, and IF it has no valid CSRF token, THEN 403 (be-foundation AC-002.11); in every case no link SHALL be recorded.

### STORY-002: Log out
- As an Employee, I want to log out so that my session ends and the next person at this computer sees nothing of
  mine. (KAN-106)
- Acceptance:
  - AC-002.1: WHEN a signed-in user sends `POST /api/v1/auth/logout` with a valid CSRF token, on a Workspace site or on `admin.`, THE backend SHALL end the Redis session, expire the cookie `__Host-TSOLVE_SESSION` (`Max-Age=0`, with `Secure` and `Path=/` and no `Domain`) and answer 200 with `{ logoutUrl }`, the Keycloak end-session URL of the realm that signed the person in (`tsolve`, or `tsolve-ops` on `admin.`) carrying the kept `id_token_hint` and `post_logout_redirect_uri` set to `/login` on the same site (K10, Q7).
  - AC-002.2: WHEN a session has been ended by logout, THE backend SHALL answer every later request that carries its cookie with 401 and `code` `MSG07`.
  - AC-002.3: WHEN a Workspace session ends, THE backend SHALL publish exactly one `SessionEnded` event with the Account id, the Workspace id, the session id and the reason, after the session is gone. The reason SHALL be `LOGOUT` (AC-002.1), `EXPIRED` (AC-001.23) or `DEACTIVATED` (AC-001.27).
  - AC-002.4: WHILE a Workspace session is past a limit of AC-001.23 and no request uses it, THE backend SHALL still remove it from the shared session store and publish `SessionEnded` with `EXPIRED` at most 15 minutes after the limit passed.
  - AC-002.5: WHEN a user logs out, THE backend SHALL keep every Jira account link of the Account unchanged.
  - AC-002.6: IF `POST /api/v1/auth/logout` comes with no valid session, THEN THE backend SHALL answer 401 with `code` `MSG07` (be-foundation AC-002.4) and publish no event. The web app still clears its data and opens Login (fe-foundation AC-008.3).
  - AC-002.7: THE My Profile screen SHALL have a "Log out" button that signs out through fe-foundation's `useSignOut()`, exactly like the user menu (fe-foundation AC-008.1 to AC-008.3).
  - AC-002.8: THE `logoutUrl` SHALL be built only from the session's own `id_token_hint` and the site of the request, never from a request parameter, and the web app SHALL navigate the browser to it so that Keycloak ends its own SSO session (K10, fe-foundation `useSignOut`, change request KAN-198).

### STORY-003: My profile with solution history
- As an Employee, I want to see my account, what I can reach and the Solutions I wrote with their status, so that I
  know my Roles, Departments, Projects and Spaces and can follow what happened to my work. (KAN-160)
- Acceptance:
  - AC-003.1: WHEN a signed-in user opens My Profile (`/profile`), THE web app SHALL show, read-only and from the session (fe-foundation AC-001.2):
    - full name and email;
    - the Roles with the labels of fe-foundation AC-001.1 (Employee always);
    - the Department (only for a Project Manager) and the Departments;
    - the Projects, each with its Department and the user's role in it (the empty case is AC-003.16).
  - AC-003.2: WHERE the user is a Knowledge Manager, THE My Profile screen SHALL list "Spaces I manage" from the session's managed Spaces.
  - AC-003.3: WHEN My Profile opens, THE web app SHALL load `GET /api/v1/me/spaces` and list "Spaces I can view" by name (the loading and empty states are AC-003.15).
  - AC-003.4: WHEN a signed-in user calls `GET /api/v1/me/spaces`, THE backend SHALL answer 200 with a list of `SpaceRef` (`spaceId`, `name`), sorted by name, exactly as the viewable Space lookup returns them for the signed-in Account.
  - AC-003.5: WHILE the viewable Space lookup has no implementation (`wiki` not built), THE backend SHALL answer `GET /api/v1/me/spaces` with 200 and an empty list.
  - AC-003.6: WHEN a signed-in Ticket Resolver or Project Manager calls `GET /api/v1/me/solutions` with `page` (from 0, default 0), `size` (1 to 100, default 20) and the optional filters of AC-003.19, THE backend SHALL answer 200 with a `MySolutionPage`: that page of the written-ticket lookup for the signed-in Account, newest `updatedAt` first.
  - AC-003.7: THE backend SHALL call the written-ticket lookup (list and summary) and the viewable Space lookup only with the signed-in Account's id, in its own Workspace, and SHALL take no user id or Workspace from the request (GBR-37).
  - AC-003.8: WHILE the written-ticket lookup has no implementation (`ticket-repository` not built), THE backend SHALL answer `GET /api/v1/me/solutions` with 200 and an empty page, and `GET /api/v1/me/solutions/summary` with 200, all six counts 0 and no Projects.
  - AC-003.9: IF `page` is negative, `size` is below 1 or above 100, `status` is not a `TicketStatus`, `projectId` is not a UUID, `from` or `to` is not a date (YYYY-MM-DD), or `to` is before `from`, THEN THE backend SHALL answer 400 with a `fieldErrors` entry for that parameter (`to` when it is before `from`) with `code` `MSG06` (be-foundation AC-001.5).
  - AC-003.10: IF nobody is signed in, THEN THE backend SHALL answer `GET /api/v1/me/solutions`, `GET /api/v1/me/solutions/summary` and `GET /api/v1/me/spaces` with 401 and `code` `MSG07`; this check comes before AC-003.17. Every signed-in user (Employee) SHALL be allowed to call `GET /api/v1/me/spaces`.
  - AC-003.11: WHEN My Solutions has rows, THE My Profile screen SHALL list them with the columns Ticket ID, Title, Project, Status (a Status Tag, fe-foundation STORY-007), Latest decision (AC-003.23) and Date (`updatedAt`, dd/MM/yyyy), with paging of 20 rows.
  - AC-003.12: WHEN the user clicks the Ticket ID of a row whose `canOpen` is true, THE web app SHALL open Ticket Detail at `/tickets/:ticketId`. The Ticket ID of a row whose `canOpen` is false SHALL not be a link.
  - AC-003.13: WHILE My Solutions has no rows, whether the user has no Solutions or the filters match none, THE My Profile screen SHALL show MSG04 in the table area and still show the counters and the filters.
  - AC-003.14: IF loading the My Solutions table, the My Solutions counters or "Spaces I can view" fails, THEN THE My Profile screen SHALL show MSG06 in the failing part only (the counters, the table or the Spaces card) and still show the account information of AC-003.1 and the other parts.
    - A 403 on `GET /api/v1/me/solutions` or `GET /api/v1/me/solutions/summary` while the page is open (for example the Role was removed) is the exception: the My Solutions section SHALL show MSG08 in an `ErrorState` in that section only, and the other cards stay; any other failure still shows MSG06.
  - AC-003.15: THE My Profile screen SHALL always show the Spaces card, whatever the user's Roles:
    - WHILE "Spaces I can view" loads, the card shows a loading spinner at once, with no delay;
    - WHEN "Spaces I can view" has loaded and is empty, the card shows, under "Spaces I can view", the grey text MSG142 ("No Spaces found");
    - "Spaces I manage" shows only for a Knowledge Manager with managed Spaces (AC-003.2); when loading fails, AC-003.14 applies.
  - AC-003.16: THE My Profile screen SHALL always show the Projects card; WHEN the user has no Projects, it SHALL show, in place of the table, the grey text MSG143 ("Not a member of any Project.").
  - AC-003.17: IF a signed-in user who is neither a Ticket Resolver nor a Project Manager (by the Account's current Roles, checked on every call) calls `GET /api/v1/me/solutions` or `GET /api/v1/me/solutions/summary`, THEN THE backend SHALL answer 403 with `code` `MSG08`.
  - AC-003.18: WHEN a signed-in Ticket Resolver or Project Manager calls `GET /api/v1/me/solutions/summary` (`getMySolutionSummary`), THE backend SHALL answer 200 with a `MySolutionSummary`: `counts` with all six statuses (the number of the Account's Solutions in each status, ignoring every filter) and `projects`, the distinct Projects of those Solutions as `ProjectRef` (`projectId`, `name`) sorted by name.
  - AC-003.19: WHEN `GET /api/v1/me/solutions` has `status`, `projectId`, `from` or `to`, THE backend SHALL return only the Solutions with exactly that status and that Project, and whose `updatedAt` falls inside the inclusive whole days `from` to `to` in the Workspace's local time (Asia/Ho_Chi_Minh); each filter is optional and they combine with AND; a well-formed `projectId` that is not one of the user's Projects gives an empty page.
  - AC-003.20: THE My Profile screen SHALL show the My Solutions section (counters, filters and table) only WHEN the session's Roles include Ticket Resolver or Project Manager; otherwise it SHALL not render the section at all and SHALL send neither My Solutions request.
  - AC-003.21: WHEN the My Solutions section is shown, THE My Profile screen SHALL show six counters with the Status Tag labels, each the count of AC-003.18 and ignoring the filters; WHILE the counters load they SHALL show a loading spinner at once, with no delay; the counters are not clickable.
  - AC-003.22: WHEN the My Solutions section is shown, THE My Profile screen SHALL show three filters: Status (single select, "All statuses" by default, then the six statuses), Project (single select, "All Projects" by default, from `projects` of AC-003.18) and Date range (dd/MM/yyyy, `to` not before `from`), each clearable and labelled "Status", "Project" and "Date range"; the filters SHALL live in the URL (`status`, `projectId`, `from`, `to`) next to `page`, any filter change SHALL reset to page 1, going back from Ticket Detail SHALL keep the filters and the page, and an invalid value in the URL SHALL be dropped silently.
  - AC-003.23: THE My Solutions table SHALL show in the Latest decision column the comment of the Solution's latest review decision on one line, with an ellipsis and the full text in a tooltip, or "—" WHEN there is no decision or the decision has no comment.

### STORY-004: View Workspace users (KAN-107 / UC-04)
- As an Admin, I want to search and filter the users of my Workspace so that I can manage their accounts.
- Acceptance:
  - AC-004.1: WHEN an Admin opens `/users`, THE frontend SHALL display a User List containing only accounts returned for the current Workspace.
  - AC-004.2: WHEN an Admin submits a search or changes role, status or Department filters, THE frontend SHALL request and display the matching users using URL-backed filter state.
  - AC-004.3: WHEN an Admin changes the page or sort order, THE frontend SHALL request the corresponding results with 20 rows per page by default and preserve that state in the URL.
  - AC-004.4: WHEN the users request is pending, THE frontend SHALL display the existing loading state.
  - AC-004.5: IF the users request fails, THEN THE frontend SHALL display the catalog error and a retry action.
  - AC-004.6: IF no users match the request, THEN THE frontend SHALL display MSG04.
  - AC-004.7: IF the signed-in user lacks the Admin role, THEN THE existing route guard SHALL prevent access to the User List and User Detail routes.
  - AC-004.8: IF an Admin submits a search longer than 100 characters after trimming, THEN THE frontend SHALL display MSG02 for Search with a limit of 100 characters, preserve the entered text and leave the current query, URL and displayed results unchanged.
- Acceptance, backend (KAN-107, 2026-11-07):
  - AC-004.9: WHEN a signed-in Admin requests `GET /api/v1/users`, THE backend SHALL return one `UserAccountPage` of the Accounts in the Workspace bound to the request, each with its UUID `id`, `fullName` (null when empty), `email`, `roles` (EMPLOYEE plus the Account's `user_role` rows, in the `Role` enum order), `department` (`DepartmentRef` with the Department's UUID public id and name, or null), `status` and `lastSignInAt` (or null).
  - AC-004.10: WHEN `q`, `role`, `status` or `departmentId` is given, THE backend SHALL return only the Accounts matching all of them: `q` trimmed as a case-insensitive substring of the full name or the email, with `%`, `_` and `\` matched literally; `role=EMPLOYEE` as Accounts with no `user_role` row, any other `role` as Accounts holding it; `status` as equal; `departmentId` as the Department's UUID public id; an empty `q` as no search.
  - AC-004.11: WHEN `sort` names `fullName`, `email` or `lastSignInAt` with `asc` or `desc`, THE backend SHALL order by that field ignoring case, with a missing value ordered as an empty value (first ascending, last descending) and the Account `id` as the final tie-breaker; WHEN `sort` is absent or names another field, THE backend SHALL order by `fullName,asc`.
  - AC-004.12: THE backend SHALL page the result with `page` from 0 (default 0) and `size` default 20, a `size` above 100 read as 100, and SHALL report `size`, `number`, `totalElements` and `totalPages` of the filtered result; a page past the end SHALL return empty `content` with the same totals.
  - AC-004.13: IF `q` is longer than 100 characters after trimming, THEN THE backend SHALL answer 400 MSG02 with parameters `field_name: Search` and `max_length: 100` and read no Account; IF `role`, `status` or `departmentId` is not a valid value, THEN THE backend SHALL answer 400 and read no Account.
  - AC-004.14: WHEN a signed-in Admin requests `GET /api/v1/users/departments`, THE backend SHALL return the bound Workspace's Active Departments as `DepartmentRef` records (UUID public id, name), ordered by name ignoring case.
  - AC-004.15: IF a request to `GET /api/v1/users` or `GET /api/v1/users/departments` is not signed in, THEN THE backend SHALL answer 401 MSG07; IF the signed-in user lacks the Admin role, THEN THE backend SHALL answer 403 MSG08; in both cases it SHALL read no Account or Department.
  - AC-004.16: THE backend SHALL read the Accounts, Roles and Departments of AC-004.9 to AC-004.14 only inside the Workspace bound by `TenantScope`, in that Workspace's own database, so an Account of another Workspace is never returned or counted; IF no Workspace is bound, THEN THE backend SHALL answer 500 MSG06 and return no row.
  - AC-004.17: WHILE the `identity-mock` profile is active (interim, until the Workspace session of Tasks 26a and 26b, KAN-275, signs requests in), THE backend SHALL sign each `/api/**` request that has no authentication yet in as the configured mock Account bound to the configured Workspace, re-reading the Account and its Roles on every request, and SHALL leave the request signed out IF that Account does not exist or is Deactivated; WHILE the profile is not active, THE backend SHALL sign no request in this way.
  - AC-004.18: WHEN the backend starts with the `identity-mock` profile, THE backend SHALL ensure, in the configured Workspace's database, the mock Admin Account, two Active Departments and at least six demo Accounts with mixed Roles, Departments and statuses, adding only the rows that are missing; WHILE the profile is not active, THE backend SHALL seed no such row.

### STORY-005: Add a user account (KAN-108 / UC-05)
- As an Admin, I want to add an account using a company email so that the employee can sign in.
- Acceptance:
  - AC-005.1: WHEN an Admin chooses to add a user, THE frontend SHALL open the creation mode of User Detail at `/users/new`.
  - AC-005.2: WHEN an Admin submits a new account, THE frontend SHALL validate required inputs, email format and the SRS email limit of 100 characters using catalog messages.
  - AC-005.3: IF an email is outside the Workspace domain or already belongs to an account, THEN THE mock API SHALL reject creation and THE frontend SHALL display the corresponding catalog validation message without losing form input.
  - AC-005.4: WHEN a new account is created, THE mock API SHALL include the Employee role and THE frontend SHALL prevent removing that baseline role.
  - AC-005.5: IF the Project Manager role is selected without a Department, THEN THE frontend SHALL prevent submission and identify the missing field.
  - AC-005.6: WHEN an Admin assigns Knowledge Manager Spaces, THE frontend SHALL use API-provided options and THE mock API SHALL enforce at most one Knowledge Manager per Space, returning MSG117 for conflicts.
  - AC-005.7: WHEN creation succeeds, THE frontend SHALL invalidate affected user queries and display the catalog success feedback.
  - AC-005.8: IF the email belongs to a Deactivated account, THEN THE mock API SHALL reject duplicate creation with MSG111 so the Admin can reactivate the existing account.

### STORY-006: Change account status (KAN-109 / UC-06)
- As an Admin, I want to deactivate departed employees while retaining their account history.
- Acceptance:
  - AC-006.1: WHEN an Admin activates a Deactivated account, THE frontend SHALL submit the change and refresh its detail and affected list queries after success.
  - AC-006.2: WHEN an Admin deactivates an account with no Active Project or managed Space requiring handover, THE frontend SHALL request confirmation using MSG57 before submitting the change.
  - AC-006.3: IF the target is the last Active Admin, THEN THE mock API SHALL reject deactivation and THE frontend SHALL display MSG56.
  - AC-006.4: IF the target is the current Admin, THEN THE frontend SHALL disable self-deactivation with MSG112.
  - AC-006.5: WHEN a Project Manager with Active Projects or a Knowledge Manager with managed Spaces is deactivated, THE frontend SHALL open Hand Over Responsibilities with MSG_NEW_2 and require eligible recipients for every responsibility before completion.
  - AC-006.6: IF no eligible replacement exists, THEN THE frontend SHALL block completion and display MSG59 for Project Managers or MSG118 for Knowledge Managers.
  - AC-006.7: WHEN deactivation succeeds, THE mock API SHALL retain the account and its responsibility/history references rather than delete the user.
  - AC-006.8: IF a status update fails or is cancelled, THEN THE frontend SHALL retain the last confirmed status and allow retry without reporting success.

### STORY-007: Update roles, Department and Spaces (KAN-110 / UC-07)
- As an Admin, I want to update a user's roles and assignments so that their responsibilities match their work.
- Acceptance:
  - AC-007.1: WHEN an Admin opens an existing User Detail, THE frontend SHALL load the selected user's roles, status, Department and managed Spaces from the API.
  - AC-007.2: WHEN an Admin saves role and assignment changes, THE mock API SHALL retain Employee, require a Department for Project Managers and enforce one Knowledge Manager per Space.
  - AC-007.3: WHEN a role or assignment change removes managed responsibilities, THE frontend SHALL require the existing D3 handover flow before submitting the completed change.
  - AC-007.4: IF an Admin attempts to remove their own Admin role, THEN THE frontend SHALL disable that action with MSG113.
  - AC-007.5: WHEN removing a Ticket Resolver role affects Project membership, THE frontend SHALL request confirmation with MSG78 before submitting the change.
  - AC-007.6: WHEN a role update succeeds, THE frontend SHALL invalidate affected list, detail and session queries so subsequent mock requests use the updated permissions.
  - AC-007.7: IF updating roles or assignments fails, THEN THE frontend SHALL preserve the unsaved form and display the API's catalog error without committing partial changes.

### STORY-008: Hand over Active Projects (KAN-111 / Jira UC-08)
- As an Admin, I want to assign every Active Project managed by a departing or reassigned Project Manager to an eligible replacement so that the including account change leaves no Active Project without its manager.
- Sources: Jira KAN-111 inspected on 2026-10-29 (same-Department Project Managers; called from KAN-109/UC-06 and KAN-110/UC-07); existing AC-006.5–006.8 and AC-007.3, AC-007.6–007.7. Report3 v3460 calls this included use case UC-65; its numbering is not substituted for Jira's UC ids and its unrelated changes are not adopted here.
- Acceptance:
  - AC-008.1: WHEN an Admin deactivates a user who manages Active Projects, THE frontend SHALL open the existing Hand Over Responsibilities popup for those Projects before submitting the deactivation, through the KAN-109 flow.
  - AC-008.2: WHEN an Admin removes the Project Manager role or changes the Department of a user who manages Active Projects, THE frontend SHALL require handover of those Projects in their existing Departments before submitting the role or Department change, through the KAN-110 flow.
  - AC-008.3: WHEN Project handover choices are loaded, THE frontend and mock API SHALL offer only other Active accounts of the current Workspace that hold Project Manager and belong to the respective Project's Department, excluding the outgoing manager.
  - AC-008.4: WHEN an Admin confirms Project handover, THE frontend and mock API SHALL require exactly one eligible recipient for every affected Active Project and SHALL leave Removed Projects and Projects managed by other users unchanged.
  - AC-008.5: IF a Project has no eligible recipient, THEN THE frontend SHALL display MSG59 with that Project's Department name and prevent completion of the including change without altering account data or Project assignments.
  - AC-008.6: IF the Admin cancels the popup or the including mutation fails, THEN THE frontend and mock API SHALL leave the last confirmed account and Project assignments unchanged; a failed mutation SHALL show its catalog error and keep the entered handover selections available for retry.
  - AC-008.7: WHEN the including mutation succeeds, THE mock API SHALL transfer all affected Projects to their selected recipients and include those recipients in their Project memberships, retaining Project ids and tracker references; THE frontend SHALL refresh affected account list, detail and session queries using the existing mutation hooks.
  - AC-008.8: WHILE the recipient lookup is pending or failed, THE frontend SHALL show the existing loading or error-with-retry state and prevent submission of the including change; WHILE the mutation is pending, THE frontend SHALL prevent duplicate submission.
  - AC-008.9: WHEN a previously deactivated account is reactivated, THE mock API SHALL retain the confirmed Project handovers rather than return the Projects to the former manager.

### STORY-009: Hand over Spaces (KAN-112 / Jira UC-09)
- As an Admin, I want to transfer every Space managed by a departing or reassigned Knowledge Manager to eligible replacements before completing the including account change.
- Sources: Jira KAN-112 inspected on 2026-10-31 (all Spaces before deactivation or role removal; called from UC-06 and UC-07); existing AC-006.5–006.8, AC-007.2–007.3 and AC-007.6–007.7. Delivery decision: frontend, mutable MSW and OpenAPI proposal, with backend integration deferred.
- Acceptance:
  - AC-009.1: WHEN an Admin deactivates a Knowledge Manager with managed Spaces through KAN-109 / UC-06, THE frontend SHALL open the existing Hand Over Responsibilities popup and require recipients for every managed Space before submitting deactivation.
  - AC-009.2: WHEN an Admin removes Knowledge Manager or removes managed Spaces through KAN-110 / UC-07, THE frontend SHALL require handover of every removed Space before submitting that change and SHALL retain the selected Spaces that remain assigned to the user.
  - AC-009.3: WHEN Space recipients are loaded, THE frontend and mock API SHALL offer only other Active Knowledge Managers in the current Workspace, without a Department restriction, excluding the outgoing manager.
  - AC-009.4: WHEN the including change is submitted, THE mock API SHALL revalidate current eligibility and require exactly one eligible recipient for each affected Space, rejecting missing, duplicate, unrelated or ineligible assignments without changing any account or responsibility data.
  - AC-009.5: IF any affected Space has no eligible recipient, THEN THE frontend SHALL display MSG118 and prevent completion, and THE mock API SHALL reject the including change without changing account or responsibility data.
  - AC-009.6: IF the Admin cancels or the including mutation fails, THEN THE frontend and mock API SHALL retain the last confirmed account and responsibility data; a failed mutation SHALL show its catalog error and keep entered handover choices available for retry.
  - AC-009.7: WHEN the including mutation succeeds, THE mock API SHALL transfer every affected Space exactly once, retain Space ids and former-manager history, and commit the account change and any accompanying Project handovers together; THE frontend SHALL invalidate affected user and session queries using the existing hooks.
  - AC-009.8: WHILE recipient lookup is pending, refreshing or failed, THE frontend SHALL prevent submission and show the existing loading or error-with-retry state; WHILE the including mutation is pending, THE frontend SHALL prevent duplicate submission.
  - AC-009.9: WHEN a former manager is reactivated or their Knowledge Manager role is restored, THE mock API SHALL retain confirmed Space handovers rather than return those Spaces automatically.

### STORY-010: The first Admin Account of a new Workspace (`FirstAdminCreator`; platform STORY-013)
- As the T-Solve operations team, we want the `platform` provisioning service to ask `identity` for the first Admin
  Account of a new Workspace, so that every Workspace starts with one Active Admin who can sign in with Google.
- Sources: platform `spec.md` AC-013.1 (step 5), AC-013.13…AC-013.19 and Interface Contracts (`FirstAdminCreator`,
  `InvitationJobQueue`); decisions of 4/11 (KAN-98 Q1…Q5: the port is declared by `platform` and implemented here; the
  first Admin is Active and invited by email; the invitation is outside the all-or-nothing rule); GBR-16, GBR-37, D7.
- Acceptance:
  - AC-010.1: WHEN `platform` calls `createFirstAdmin(workspaceId, email)` inside `TenantContext.runIn(workspaceId)` (platform AC-013.14), THE identity module SHALL create in the bound Workspace's own database one Account with the email in lower case, status `ACTIVE`, an empty full name and no sign-in time, and the Role Admin (Employee is implied, be-foundation AC-002.2), and SHALL return the Account's `UUID` and nothing else.
  - AC-010.2: THE identity module SHALL write the Account and its Role row in one transaction, so that either both exist or neither does (platform AC-013.16).
  - AC-010.3: IF no Workspace is bound, or the bound Workspace is not `workspaceId`, THEN THE identity module SHALL throw an exception and create nothing (platform AC-013.14: the call runs only inside the Workspace's own `runIn`).
  - AC-010.4: IF the email is null, blank or not a valid email address, THEN THE identity module SHALL throw a `BusinessRuleException` with the field `email` and create nothing (a second check behind platform AC-013.4).
  - AC-010.5: IF the Workspace's database already has an Account with that email (compared in lower case), THEN THE identity module SHALL throw a `BusinessRuleException` with the field `email` and create nothing, and THE exception SHALL reach the provisioning service unchanged so that it undoes the creation (platform AC-013.16).
  - AC-010.6: THE identity module SHALL NOT call `WorkspaceCatalog.claimEmail` from `createFirstAdmin`, because the email is claimed for the new Workspace at step 4 of platform AC-013.1, before the call, and a lost race ends there (platform AC-013.19).
  - AC-010.7: THE identity module SHALL create no password, token, link or other credential for the first Admin, and SHALL log neither the email nor the Account's name: the Admin signs in with Google under AC-001.11 to AC-001.13 like any Account, and the invitation email and its job belong to `platform` and `worker` (platform AC-013.17, AC-013.18).
  - AC-010.8: THE identity module SHALL offer `createFirstAdmin` as a Spring bean implementing the `platform` port `FirstAdminCreator` in every profile that serves a Workspace creation (`ops` and the one that serves the Platform Console), so that the backend does not refuse to start (platform AC-013.15).

## Retired ids

These acceptance-criterion ids are retired and never reused. STORY-001: AC-001.2, AC-001.4, AC-001.5, AC-001.17, AC-001.19, AC-001.35, AC-001.36, AC-001.37, AC-001.39, AC-001.44, AC-001.45, AC-001.46, AC-001.47, AC-001.49, AC-001.50, AC-001.51 (K1–K14, Changelog 2026-11-05), AC-001.73 (Q4 revised: a Workspace is Active at creation; no `PENDING_SIGN_IN` status).

## Out of Scope
- The invitation email, the job that carries it (`InvitationJobQueue`) and its retries → `platform` (the queue) and `worker` (the handler); the Platform Console screen and endpoint that start a creation → `platform-console` (platform AC-013.17, AC-013.18, AC-013.20).
- Standalone Project or Space handover pages, routes, menu actions or mutation endpoints are outside KAN-111 and KAN-112. Reuse the combined popup required by STORY-006–007. KAN-112 does not implement production backend persistence or authorization.
- The Tenant Resolver, MSG119 and MSG120 on ordinary requests, the Forge Invocation Token check, the Tenant
  Context and Database Router, the per-database RLS guard, the Platform / Tenant Registry → `platform` (KAN-196). A Suspended Workspace's site is refused there; its sessions are not ended by this
  module.
- The T-Solve app's panel states MSG62 and MSG74, and the `api.` endpoint that asks `JiraLinkRequestService` for a
  link request → `jira-app` (Đặng Tấn Bình, KAN-124).
- Keycloak itself: its upgrade, its health check and restart, its database backup and the pinned image → DevOps
  (`docs/runbooks/onboard-company.md` for the per-company steps; ADR-0003 for the hosting).
- Adding the Google redirect URI of a company in Google Cloud Console: a manual step of the runbook, not code (K4).
- Which Workspace statuses exist and the call that marks a Workspace Active after a passing test sign-in, the
  Organization id and the Google domain on the Workspace record → `platform` (change request, see the design).
- The Platform Console pages, its Login page that shows MSG135 and MSG06, and what an Operator may do →
  `platform-console` (restored with the HLA, 3/11; owner and issues per the module map).
- The web-app sign-in mechanism → `fe-foundation` change request KAN-198 (decision Q16):
  - `startSignIn`, `useSignInMessage`;
  - the catalog entries;
  - the guards;
  - the global handling of 401 / MSG07 / MSG123 / MSG119 / MSG120;
  - sending `X-TSolve-Background: 1` on polling and real-time requests.
- The landing page per Role, menus and the Workspace Setup redirect → `fe-foundation` (AC-003.1, AC-003.2, done).
- Clearing the chat history in the browser → `fe-foundation` AC-008.1 and `chatbot` AC-002.7. What the chatbot
  backend deletes on `SessionEnded` → `chatbot` (Trần Hải Anh).
- Which Tickets count as written by the user, and the GBR-02 visibility of each row → `ticket-repository` (the
  written-ticket lookup). Which Spaces a user can view → `wiki` (the viewable Space lookup).
- Recording sign-ins in the audit log (decision Q15). Sign-in is not an `AuditAction` of be-foundation.
- Signing the user out of Google; self-registration; any identity provider other than Google (Keycloak is the broker,
  not a second provider); any sign-in with a password; an Admin button that resets a person's `sub` (Q11: a runbook
  step until then).
- "My Wiki documents" and the review decision of each Solution (v3460 screen 1.2.1). They are not in UC-03 or
  TS-127, and the Solution list has no filters (decision Q12).
- Managing the Platform Operator allow-list (D36: no use case manages it).

### UC-04–08 frontend delivery exclusions
- For STORY-004–008 only: real backend identity endpoints, authentication, session invalidation, audit persistence and production authorization remain unimplemented by this frontend/MSW delivery. The real guarantees belong to the separately specified backend/authentication slices; mock behavior is not evidence of production enforcement.
- Workspace or Department administration, Jira integration, Wiki management, new shared components, dependency changes or redesigning the application shell.
- Real user data or a browser-persistent mock database; the MSW dataset resets with the mock runtime.

## Interface Contracts

### Exposed by this module

**REST.** Each endpoint is added to `frontend/openapi/tsolve-api.yaml` in the same PR as its code.

| Host | Endpoint | Answer |
|------|----------|--------|
| Workspace site, `admin.` | `GET /api/v1/auth/sign-in?returnTo=<path>` (full-page navigation, no session needed) | 302 to Keycloak's authorization endpoint (realm `tsolve`; `tsolve-ops` on `admin.`) with `Set-Cookie: __Host-TSOLVE_AUTHREQ` (AC-001.1, AC-001.28, AC-001.53); a Workspace with no Google domain, no Organization id or not ready: 302 `/login?error=MSG141` (AC-001.48) |
| Workspace site, `admin.` | `GET /api/v1/auth/callback?code=…&state=…` (needs the `__Host-TSOLVE_AUTHREQ` cookie, AC-001.6) | the session is created here: sets `__Host-TSOLVE_SESSION`, 302 to `returnTo` or `/` (AC-001.18, AC-001.29); failure 302 `/login?error=…`; every answer expires `__Host-TSOLVE_AUTHREQ` (AC-001.53). On `login.` (Keycloak's host) the backend serves nothing |
| Workspace site | `GET /jira-link?request=<id>` (web app route) and `POST /api/v1/auth/jira-link/confirm` (CSRF) | the confirm page (AC-001.70) and the answer with MSG75 or its failures (AC-001.71, AC-001.72) |
| Workspace site, `admin.` | `POST /api/v1/auth/logout` (CSRF) | 200 `{ logoutUrl }` (AC-002.1); the endpoints `GET /api/v1/auth/authorize` and `GET /api/v1/auth/complete` no longer exist (K12) |
| Workspace site | `GET /api/v1/me/solutions?page&size&status&projectId&from&to` (`operationId` `getMySolutions`; 403 `MSG08` without the role) | `MySolutionPage` |
| Workspace site | `GET /api/v1/me/solutions/summary` (`operationId` `getMySolutionSummary`; 403 `MSG08` without the role) | `MySolutionSummary` |
| Workspace site | `GET /api/v1/me/spaces` (`operationId` `getMyViewableSpaces`) | `SpaceRef[]` |

**Schemas:**

  ```yaml
  MySolutionPage: { content: MySolution[], page: PageMetadata }   # both required, as ReviewQueuePage
  MySolution:
    required: [ticketId, ticketKey, title, projectId, projectName, status, updatedAt, canOpen, latestDecision]
    ticketId:    string (uuid)   # path id of Ticket Detail
    ticketKey:   string     # the Ticket ID
    title:       string
    projectId:   string (uuid)
    projectName: string
    status:      TicketStatus
    updatedAt:   date-time
    canOpen:     boolean    # true when UC-24 lets this user open the Ticket
    latestDecision: { action: ReviewDecision, comment: string|null, decidedAt: date-time } | null   # required key; null = no decision yet; an Expired Solution keeps its last reviewer decision
  ReviewDecision: enum [APPROVE, REQUEST_CHANGES, REJECT]   # ticket-review's decisions
  ProjectRef:     { projectId: string (uuid), name: string }
  MySolutionSummary:   # both required
    counts:   { PENDING_REVIEW, CHANGES_REQUESTED, PUBLISHED, REJECTED, EXPIRED, UNPUBLISHED: integer }   # all six required
    projects: ProjectRef[]
  TicketStatus: enum [PENDING_REVIEW, CHANGES_REQUESTED, PUBLISHED, REJECTED, EXPIRED, UNPUBLISHED]   # GBR-07
  ```

**Sign-in error codes:**
- Sent back as `/login?error=<code>` on the Workspace site: MSG06, MSG09, MSG10, MSG120, MSG122 (with
  `google_domain`) and MSG141.
- Sent back on `admin.`: MSG06 and MSG135.
- Shown on Keycloak's themed pages (AC-001.67): MSG06 text, for the failures Keycloak cannot hand back to the callback.
- On the confirm page of a Jira link: MSG75, MSG141 and the 401 and 403 of AC-001.72.

**Security:**
- identity adds its own `SecurityFilterChain` for the sign-in start and the callback, ordered before be-foundation's
  chain, built on Spring Security's OAuth2 client against Keycloak (K7): stateless, no HTTP session, the authorization
  request kept in Redis (AC-001.53, AC-001.55). It allows those two paths without a session (decision Q11, as
  be-foundation Q5 does for Forge). be-foundation's criteria are unchanged.
- A Workspace session puts be-foundation's `TSolveUser` in the security context.
- An Operator session puts a separate Operator principal (email, full name) in the security context, with none of
  the five Roles. `platform-console` checks for it.

**Services:**
- `JiraLinkRequestService`: for `jira-app`, a single-use link request for a Workspace and a ticket tracker account
  (AC-001.69).
- `WorkspaceSignInProvisioner`: `provision`, `check` and `remove` of the Keycloak objects of a Workspace (AC-001.57),
  called by `platform`'s `WorkspaceProvisioning`.
- `JiraAccountLinkLookup`: the Account linked to a ticket tracker account in a Workspace, for `jira-app`. The
  table is `jira_account_links` (D1); one link per ticket tracker account per Workspace.
- `AccountDirectory`:
  - for `platform-console`: the number of Active and Deactivated Accounts of a Workspace (UC-54), and whether
    any Admin of a Workspace has signed in (D14, UC-56);
  - for every module: an Account by id (a `UUID`), in the caller's Workspace.
- `FirstAdminCreator` (the port is declared by `platform`, implemented here; STORY-010): `UUID createFirstAdmin(UUID
  workspaceId, String email)`, called by `platform`'s `WorkspaceProvisioning` inside `TenantContext.runIn(workspaceId)`
  after the email is claimed (platform AC-013.14, AC-013.19). It creates the Account Active with the Role Admin and
  returns its id; the id is the only thing the invitation job carries besides the Workspace id.
- The Department lookup of be-foundation (`common`): the Department of a Project Manager.
- `SessionEnded` (Spring application event): Account id, Workspace id, session id, reason `LOGOUT` /
  `EXPIRED` / `DEACTIVATED`.

**Stores.** The HLA gives each company its own PostgreSQL database (database per company, ADR-0002), so the account
tables below have no `workspace_id`; the Workspace is the database the tenant context is bound to. Three places:
- **Each company database** (the tenant Liquibase changelog; singular names and `id` keys as the Database Tables baseline (`00-baseline/T-Solve_Database_Tables.md`), but no
  `workspace_id` and no foreign key to a Workspace; the RLS guard in each database is `platform`'s convention):
  - `app_user` (the Account; Java `Account`): email stored lower-case and unique inside that database (GBR-37, D7); the
    Keycloak `sub`, unique and empty until the first sign-in (AC-001.12; no `workspace_id`: the `sub` is unique inside
    one company database); full name (at most 100 characters), empty until the first sign-in; status `ACTIVE` /
    `DEACTIVATED`; the Department of a Project Manager; first and last sign-in time. Email uniqueness across the whole
    platform (one email in one Workspace) is checked through the Tenant Registry's `workspace_email` (a `platform`
    service that identity calls when an Account is added; for the first Admin, `platform` claims the email itself
    before it calls `FirstAdminCreator`, AC-010.6); no index can span the company databases.
  - `user_role` (Java `AccountRole`): the Account's Roles, the four Roles besides Employee, since Employee is implied
    (be-foundation AC-002.2).
  - `jira_account_links` (the D1 name).
- **Keycloak** (its own database `keycloak` on the PostgreSQL server; ADR-0003): the realms `tsolve` and `tsolve-ops`,
  one Organization and one Google identity provider per Workspace, the users it creates at first sign-in, and the
  clients. The tables `sign_in_states` and `hand_off_code` of the earlier design are dropped (K12).
- **Redis** (the shared session store of the HLA): the Workspace and Operator sessions, the in-flight authorization
  requests (AC-001.53) and the Jira link requests (AC-001.69), so no API replica keeps a session of its own.

**Header.** `X-TSolve-Background: 1` marks a request that is not user activity (AC-001.25).

### UC-04–07 frontend/MSW exposed contracts
- Existing Admin routes: `/users`, `/users/new`, `/users/:userId` from `users/routes.ts`.
- An API contract stub in `frontend/openapi/tsolve-api.yaml`, generated through orval, for user list/detail and mutations plus the lookup data required by these screens. Endpoint and schema details belong in the design and are frontend mock proposals until agreed with the backend owner.
- Pagination and errors follow guideline 06: `{ content, page: { size, number, totalElements, totalPages } }`; RFC 9457 Problem Details with catalog `code`, `params` and optional `fieldErrors`.
- Mutable MSW handlers live in `frontend/src/features/users/mocks/handlers.ts`, using synthetic Workspace-scoped fixtures and validating rejected operations before changing mock records.
- `UserAccount.id` is a UUID string (`type: string`, `format: uuid`), consistent with the UUID type of `app_user.id`; `/users/:userId` carries that UUID. DepartmentRef and SpaceRef follow the current shared contracts.
- The user-list query `q` has a 100-character maximum after trimming; the mock rejects longer values with 400 MSG02 and parameters `field_name: Search`, `max_length: 100`.

### Required from other modules
- `platform` (KAN-196):
  - the Tenant Resolver for Workspace-site requests, which refuses an unknown or suspended Workspace;
  - the Tenant Context bound before the transaction, with no default fallback, and the Database Router behind it,
    and a way to run the callback's lookups in the context of the state value's Workspace;
  - the Registry lookup: the Workspace record (site name, Google domain, the Keycloak Organization id, status) and
    the `workspace_email` check of email uniqueness across the platform;
  - the two columns `google_domain` and `keycloak_organization_id` on `tenant_workspace`, and a call of
    `WorkspaceSignInProvisioner` inside `WorkspaceProvisioning` create, undo and remove, with the new `create`
    parameter (see the design, "Platform change requests"); a Workspace is Active at creation (platform AC-013.1);
  - the per-database RLS guard convention and the Registry database datasource;
  - the port `FirstAdminCreator` and the `WorkspaceProvisioning` service that calls it (platform AC-013.13,
    AC-013.14): the first Admin of a new Workspace is created by this module through that port, after `platform` has
    claimed the email in `workspace_email`; the invitation email and its job (`InvitationJobQueue`) are `platform`'s
    and `worker`'s (platform AC-013.17).
- `be-foundation`: `TSolveUser`, the five Role constants, `ProblemDetail` errors, CSRF (AC-002.11, AC-002.13),
  and `/me` (STORY-003 of be-foundation), whose Department lookup this module implements.
- `fe-foundation` change request KAN-198: `startSignIn(returnTo)` (full-page `GET /api/v1/auth/sign-in`),
  `useSignInMessage()`, the catalog codes of AC-001.42, the global 401 handling, and the
  `X-TSolve-Background: 1` header on requests the web app sends on its own.
- `ticket-repository` (Đặng Nhật Minh, KAN-132): a written-ticket lookup in `common`. Given an Account and a
  page and the filters of AC-003.19, it returns the Tickets whose Solution that Account wrote, as `MySolution` rows (with `projectId` and `latestDecision`), and a summary of the six counts and the Projects (AC-003.18):
  - no Drafts (GBR-26);
  - none of a Deleted Department (GBR-17);
  - none after the Ticket Resolver Role is removed, unless the Account is a Project Manager (GBR-02, D32); the My Solutions section is shown to exactly these two Roles (AC-003.20), so a Project Manager who used to resolve still sees the history;
  - still listed after the Account leaves the Project (GBR-02);
  - `canOpen` as UC-24 decides.
- `wiki` (Đặng Nhật Minh): a viewable Space lookup in `common`. It returns the Spaces the Account or its
  Department has View access to, plus the Spaces it manages (Permission Matrix footnote ⁸).
- `jira-app` (Đặng Tấn Bình): asks for sign-in links only after verifying the Forge Invocation Token, and opens
  the returned URL in a new tab.
- `chatbot` (Trần Hải Anh): listens to `SessionEnded` for its server-side retention (chatbot Requirement 7).

### UC-04–07 frontend/MSW dependencies
- `fe-foundation`: Admin route guards, `useSession`, shared UI kit, form rules, message catalog, generated API infrastructure and pre-registered feature mocks.
- `workspace`: Workspace email domain, Department choices and Space references, supplied by mock contracts for this delivery.
- `be-foundation`: real authorization and session/error enforcement for future backend integration.

## Definition of Done
- [ ] Every acceptance criterion has a test whose name starts with or cites its AC id; `sdd.ts trace identity`
      passes for STORY-001…003
- [ ] `mvn verify` and the frontend `verify` pass
- [ ] The YAML contract has the endpoints and schemas above; `generate:api` leaves no diff
- [ ] The owners of `platform`, `platform-console`, `jira-app`, `ticket-repository`, `wiki`, `chatbot` and
      `fe-foundation` have been told what they call or implement

### UC-04–07 frontend delivery checks
- [ ] Every acceptance criterion has a test whose name starts with its AC id; backend-only guarantees are identified as unverified in production.
- [ ] Each Jira story is delivered on its own issue branch and reviewed with the user before its feature commit and before starting the next story.
- [ ] UI verification covers the existing Admin shell at 1366 × 768, keyboard operation, loading, empty, failure and mutation feedback.
- [ ] Frontend format, lint, typecheck, test and build checks pass.
- [ ] API regeneration leaves no diff in generated files after the committed generation output.
- [ ] Shared/API ownership review requirements are preserved; generated files are never hand-edited.

## Sources and decisions — UC-04–07 frontend delivery
- Jira: KAN-107, KAN-108, KAN-109, KAN-110, read from the current user's filtered board on 2026-10-25.
- User decision on 2026-10-25: working frontend with MSW data, prepared for later API integration.
- `frontend/AGENTS.md`; guidelines 01, 02, 06, 07, 08 and 09; existing message catalog including MSG56, MSG57, MSG59, MSG78, MSG111–113, MSG117–118 and MSG_NEW_2.
- `module.md`, `../architecture.md` and `../../02-discovery/context-brief.md`.

<!-- ACCEPTANCE CRITERIA: one testable statement per item, id AC-<story>.<n> (never reuse a
     removed id), in EARS form: THE <system> SHALL <response>, optionally led by
     WHEN <trigger>, WHILE <state>, IF <unwanted condition> THEN, or WHERE <feature is included>.
     A story with a WHEN criterion also gets an IF … THEN criterion for its failure path.
     OUT OF SCOPE: name what is excluded, so it is not built by accident; adding it later is a
     spec change (Changelog row, re-approval).
     OPEN QUESTIONS: where the answer is missing, write [NEEDS CLARIFICATION: <question>] and ask
     the user — never fill the gap with a guess. `bun harnessclaude/hooks/sdd.ts check <module>`
     fails while any remain.
     SPEC EDIT RULE: Any edit to this file that changes business logic, requirements, stories, or
     acceptance criteria MUST add a new row to the Changelog table above (Date, Story if
     applicable, Change summary, Reason). Once the user has approved the spec, any edit locks the
     module's code until they approve the new version (`sdd.ts approve <module>`). -->
