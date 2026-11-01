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
| 2026-10-31 | STORY-009 | Draft KAN-112 Space handover criteria AC-009.1–009.9, reusing the UC-06/07 popup and atomic mutations | User selected frontend, MSW and an OpenAPI proposal pending backend integration; Jira requires every Space to be handed over before deactivation or role removal |
| 2026-10-29 | STORY-008 | Draft KAN-111 frontend/MSW Project handover criteria AC-008.1–008.9; extend requirement 10 and delivery exclusions, clarify that there is no standalone handover screen | User requested one branch per task from main, then selected waiting for KAN-109/110 to merge while preparing scope and spec; reuse their existing handover instead of duplicating it |
| 2026-10-25 | STORY-001..STORY-003 | Initial spec of the UC-01…03 slice: central sign-in through `login.` with Google OIDC, the session per Workspace site, the Platform Operator and Jira-link sign-ins, log out, My Profile with solution history. Written from Report3 (GBR-03, GBR-19, GBR-28, GBR-37, D1, D9, D13, D14, D22, D23, D36), Stories TS-020, TS-021, TS-023, TS-127 and the planning decisions Q1–Q16 of 25/10 | KAN-197 (Phase 1, multi-tenant baseline) |
| 2026-10-25 | — | UC-04…09 (View user list, Add Account, Update Account status, Update roles and Department, Hand over Projects, Hand over Spaces) will be added as STORY-004 onwards by their owner Phạm Tùng Dương (KAN-107…112) | One module, two owners (D40: the Jira assignee owns the Story) |
| 2026-10-25 | STORY-001, STORY-003 | Interface Contracts only: `MySolution.ticketId` is a UUID string (`format: uuid`); the Account table is `accounts` and the Roles table `account_roles`, each with a UUID `id` under the platform convention (platform AC-001.8); `jira_account_links` keeps its D1 name. No acceptance criterion added, reworded or removed (`SpaceRef` follows the fe-foundation contract) | Change request KAN-196 Q2 (the user, 25/10): UUID primary keys and plural table names in every module (Phan_cong v6 §1); SPEC-P1-7 |

| 2026-10-25 | STORY-004..007 | Initial frontend scope for KAN-107–110, with working MSW data | User requested frontend screens for their four In Progress Jira stories and selected MSW before backend integration |
| 2026-10-26 | STORY-004..STORY-007 | Merge the UC-04–07 frontend/MSW slice after main's unchanged STORY-001–003; renumber its AC ids to AC-004–007; add AC-004.8 for the 100-character search limit and align UserAccount ids with the UUID convention | User-requested KAN-107 review repair: preserve the other owner's sign-in/logout/profile specification and align the account contract with main |
| 2026-10-28 | STORY-001 | Reword Functional req. 1, AC-001.1, AC-001.4, AC-001.5, AC-001.29 and AC-001.35: the central sign-in origin comes from `TSOLVE_LOGIN_ORIGIN` and the Platform Console origin from `TSOLVE_ADMIN_ORIGIN` (the production URLs stay as examples). Reword AC-001.18: the session cookie is `__Host-TSOLVE_SESSION`. Reword AC-001.21, AC-001.28, AC-001.30 and AC-001.31 to name the configured Platform Console origin the same way, and AC-002.1 and the Interface Contracts row for `GET /api/v1/auth/complete` to use `__Host-TSOLVE_SESSION` (wording only); NFR 4 names `TSOLVE_LOGIN_ORIGIN`, `TSOLVE_ADMIN_ORIGIN` and `TSOLVE_SIGNING_KEY`. Reword AC-001.42: the Login codes are fe-foundation's list (MSG06, MSG09, MSG10, MSG122, MSG123, MSG141, without MSG07 and MSG120); MSG122 needs a valid `google_domain`; `error=MSG120` leaves the message area empty and the MSG120 full page shows instead | KAN-103 plan (Hiếu, 28/10): Q7 = A (NFR 4: hosts come from the environment; local dev and tests use other origins); Q2 = B (KAN-104: the session cookie is valid only on the company's own site, enforced by the browser); Q4 = A (agree with fe-foundation AC-012.3, AC-012.4 and AC-011.4) |
| 2026-10-31 | STORY-001 | Add AC-001.44–.47 (login CSRF: `authorize` sets the sign-in cookie `__Host-TSOLVE_SIGNIN` and accepts each state value once; the callback refuses a state value without the matching cookie and expires the cookie in every answer), AC-001.48–.49 (a Workspace with no Google domain is refused at sign-in start, `authorize` and the callback) and AC-001.50 (the state's Workspace no longer exists at the callback). Reword AC-001.2 (the state keeps the cookie hash and the "authorized" mark), AC-001.14 (the first step also covers the sign-in cookie and the state's Workspace), NFR 3 (the browser also holds the short-lived sign-in cookie), and the Interface Contracts rows for sign-in start, `authorize` and `callback`; reworded AC-001.1, .4, .14, .29, .31 (spec review) | Task 8 review (Hiếu, 29/10), design row 45; KAN-231 plan Q1–Q14 (Hiếu, 31/10); KAN-231 spec review (Hiếu, 31/10) |
| 2026-11-01 | STORY-003 | Add AC-003.15 (the Spaces card is always shown; spinner at once; MSG142 "No Spaces found" when "Spaces I can view" is empty) and AC-003.16 (the Projects card is always shown; MSG143 "Not a member of any Project." in place of the table). Reword AC-003.1 (the Projects list is read-only; its empty state is AC-003.16) and AC-003.3. Messages MSG142 and MSG143 are new. AC-003.11 to .13 (My Solutions) unchanged | Hiếu, 1/11 (KAN-237 QA): reverses plan decision Q4 for the Spaces and Projects cards only; Account rows and "Spaces I manage" keep Q4; My Solutions is for KAN-238 |
| 2026-10-31 | STORY-003 | AC-003.12 reworded: the Ticket ID, not the whole row, is the link (no shared DataTable change) | KAN-160 plan review; FE Lead 31/10 (R1 A, R1' A) |

## Requirements

Planning note, 2026-10-29: the user selected waiting for KAN-109 and KAN-110 to merge into `main`, while preparing KAN-111 scope and spec now. STORY-008 requires approval of this version. On 2026-10-30 the user requested continuing KAN-111 while KAN-110 approaches main: verify the KAN-109 caller first on merged main; the KAN-110 caller and final completion still require KAN-110 on main. It does not authorize backend work or advancing KAN-112.

### Functional
1. People sign in only with a Google account of their company's Google domain, through the configured central sign-in origin
   (`https://login.tsolve.io.vn` in production). That site has no Workspace of its own. It learns the target only from a signed,
   short-lived, single-use state value that the server creates (GBR-03, GBR-37, D9). The OIDC redirect URI is
   on `login.` only (TS-020).
2. Sign-in has three targets, all through the same callback:
   - a Workspace, started from its Workspace site;
   - the Platform Console, started from `admin.` and allowed only for an allow-listed email (D13, D36);
   - a Jira account link, started by the T-Solve app through `api.` (GBR-19, D1).
3. After a Workspace sign-in, the session is handed to the Workspace site with a one-time hand-off code. It is
   valid only on that Workspace site and never on `admin.`; an Operator session is valid only on `admin.`
   (GBR-03, D13). A session ends after 8 hours, or after 60 minutes without activity (MSG07).
4. Each request re-reads the Account. A Deactivated Account loses its session at once. A Role change applies from
   the next request (GBR-03, TS-027).
5. After sign-in the browser opens `returnTo` or `/`. The landing page per Role and the Workspace Setup redirect
   are fe-foundation AC-003.1 and AC-003.2 (not repeated here).
6. Logging out ends the server session. The chat history of the session is cleared in the browser by
   fe-foundation AC-008.1 and chatbot AC-002.7, and on the server through the `SessionEnded` event of this
   module (UC-02, GBR-36).
7. My Profile shows the account information from the session (`/me`), the Spaces the user manages and can view,
   and the Tickets whose Solution the user wrote, with their status (UC-03, TS-127).
8. This slice creates the Account store that sign-in reads: email, full name, status, Roles, the Department of a
   Project Manager, and the sign-in times. The UC-04…09 stories extend it (decision Q8). The full name comes from
   Google at every sign-in (decision Q7).

9. Implement the Admin User List (1.3.1), User Detail (1.3.2, creation and editing), and required Hand Over Responsibilities popup (1.3.3).
10. Follow KAN-107, KAN-108, KAN-109 and KAN-110 as read from the signed-in user's Jira board on 2026-10-25. KAN-111, inspected in Jira on 2026-10-29, extends that frontend/MSW delivery with explicit Project-handover criteria in STORY-008. KAN-112, inspected in Jira on 2026-10-31, adds Space handover in STORY-009 using MSW and the proposed OpenAPI contract pending backend integration; STORY-001–003 keep their separately specified backend and authentication scope.
11. Use the existing English screen names, shared UI kit, message catalog, role guards, form validation and registered routes.
12. Use generated API hooks with TanStack Query and realistic, mutable MSW handlers. Mock checks demonstrate business rules; they do not establish production security guarantees.

### Non-Functional
1. Every acceptance criterion has a test whose name cites its id. Backend: JUnit `…_AC_001_1`. Web app: `it`
   names start with the id. `sdd.ts trace identity` passes for STORY-001…003.
2. `mvn verify` passes on every commit. The frontend `verify` passes too: `format:check`, `lint`,
   `typecheck`, `test`, `build`, and `generate:api` with no diff.
3. No token, ID token, Google credential, state value or hand-off code is kept in browser storage. The browser
   holds only the HttpOnly session cookie and, on `login.` during a sign-in, the short-lived HttpOnly sign-in cookie
   `__Host-TSOLVE_SIGNIN`, whose random value is neither a token nor a state value (AC-001.44).
4. Secrets and hosts come only from the environment, with no default (architecture: Configuration):
   - `GOOGLE_CLIENT_ID`
   - `GOOGLE_CLIENT_SECRET`
   - `TSOLVE_SIGNING_KEY` (the key that signs state values and hand-off codes)
   - `TSOLVE_LOGIN_ORIGIN` (the central sign-in origin; `https://login.tsolve.io.vn` in production)
   - `TSOLVE_ADMIN_ORIGIN` (the Platform Console origin; `https://admin.tsolve.io.vn` in production)
   - `PLATFORM_OPERATOR_EMAILS` (the allow-list, a comma-separated list)
5. Two-Workspace tests cover the session rules: AC-001.7, AC-001.15, AC-001.17, AC-001.20 and AC-001.32. Each
   uses two Workspaces with Accounts of the same Roles.
6. Error bodies follow be-foundation (`ProblemDetail` with a Report3 `code`). Sign-in failures are redirects with
   a code, never backend text.

7. THE frontend SHALL render these screens at the project's minimum desktop viewport of 1366 × 768 without inaccessible actions or clipped form controls.
8. THE frontend SHALL provide keyboard-accessible controls, visible field labels, pending states, and disabled duplicate submission controls.
9. THE frontend SHALL keep server data in TanStack Query and filters, sorting and pagination in URL search parameters.
10. THE implementation SHALL add no npm dependencies and reuse existing feature, API generation, form and UI patterns.

## User Stories

### STORY-001: Sign in with Google through the central sign-in
- As an Employee, I want to sign in with my company Google account on my company's Workspace site and land on the
  page of my Roles, so that I use T-Solve without a separate password and only in my own Workspace. (KAN-103
  central sign-in, KAN-104 session per Workspace site and Login screen.)
- Acceptance:
  - AC-001.1: WHEN a browser opens `GET /api/v1/auth/sign-in` on a Workspace site whose Workspace has a Google domain (AC-001.48), THE backend SHALL create a state value for the Workspace that the platform resolved from the host and answer 302 to `/api/v1/auth/authorize?state=…` on the central sign-in origin configured in `TSOLVE_LOGIN_ORIGIN` (`https://login.tsolve.io.vn` in production), without a signed-in user.
  - AC-001.2: THE backend SHALL create every state value on the server only, signed with its key, valid for 10 minutes and usable once. The state SHALL carry exactly one target (a Workspace, the Platform Console, or a Workspace plus a ticket tracker account) and the kept `returnTo`. The OIDC nonce and PKCE code verifier SHALL stay on the server (D9). The state SHALL also keep the hash of its sign-in cookie value and whether `authorize` has accepted it (AC-001.44, AC-001.45).
  - AC-001.3: WHERE `returnTo` is given to the sign-in start, THE backend SHALL keep it only if it is a relative path of the same site (one leading `/`, not `//`, no scheme, no host) and SHALL use `/` otherwise.
  - AC-001.4: WHEN `GET /api/v1/auth/authorize` on `login.` gets a valid state value that is not yet marked as authorized (AC-001.45) and, for a Workspace or Jira-link target, whose Workspace has a Google domain (AC-001.49), THE backend SHALL answer 302 to Google's authorization endpoint with:
    - `scope` `openid email profile`, the state value, the OIDC nonce and a PKCE `S256` code challenge;
    - the redirect URI `/api/v1/auth/callback` on the configured central sign-in origin (`https://login.tsolve.io.vn/api/v1/auth/callback` in production);
    - for a Workspace or Jira-link target, `hd` set to the Workspace's Google domain.
  - AC-001.5: THE backend SHALL register and accept only `/api/v1/auth/callback` on the configured central sign-in origin (`https://login.tsolve.io.vn/api/v1/auth/callback` in production) as the OIDC redirect URI. A request to the authorize or callback path on any other host SHALL be answered 404.
  - AC-001.6: IF `authorize` or the callback gets a state value that is missing, not signed by the server, expired or already used, THEN THE backend SHALL answer 400 with the minimal `login.` page showing MSG141 and create no session (GBR-03).
  - AC-001.7: THE callback SHALL take the target Workspace only from the verified state value and SHALL ignore every other parameter, header or cookie that names a Workspace or a site (GBR-03, GBR-37).
  - AC-001.8: IF Google returns an error or the user cancels, with a valid state value for a Workspace, THEN THE backend SHALL redirect to `/login?error=MSG06` on that Workspace site and create no session.
  - AC-001.9: IF the code exchange fails, or the ID token fails verification (signature, issuer, audience, expiry, nonce, or `email_verified` not true), THEN THE backend SHALL redirect to `/login?error=MSG06` on the Workspace site and create no session.
  - AC-001.10: IF the state value's Workspace is not Active at the callback, THEN THE backend SHALL redirect to `/login?error=MSG120` on its Workspace site and create no session.
  - AC-001.11: IF the `hd` claim is missing or differs (ignoring case) from the Workspace's Google domain, THEN THE backend SHALL redirect to `/login?error=MSG122&google_domain=<the Workspace's Google domain>` on its Workspace site and create no session (GBR-03, D22).
  - AC-001.12: IF the Workspace has no Account with the verified email (compared ignoring case), THEN THE backend SHALL redirect to `/login?error=MSG09` on its Workspace site and create no session.
  - AC-001.13: IF the Account with that email is Deactivated, THEN THE backend SHALL redirect to `/login?error=MSG10` on its Workspace site and create no session.
  - AC-001.14: THE callback SHALL check a state value for a Workspace or Jira-link target (not the Platform Console, whose checks are AC-001.29 to AC-001.31) in this order and report only the first failure: state value, sign-in cookie and the state's Workspace (MSG141; AC-001.6, AC-001.46, AC-001.49, AC-001.50), Google result and ID token (MSG06), Workspace Active (MSG120), `hd` (MSG122), Account exists (MSG09), Account Active (MSG10).
  - AC-001.15: THE callback SHALL look up the Account only among the Accounts of the state value's Workspace (in that Workspace's tenant context), so an Account of another Workspace is never found or changed (GBR-37).
  - AC-001.16: WHEN every check passes for a Workspace target, THE backend SHALL:
    - set the Account's full name from the `name` claim (unchanged when the claim is empty);
    - record the sign-in time, and the first sign-in time when the Account has none (D14);
    - redirect to `/api/v1/auth/complete?code=…` on the Workspace site, with a one-time hand-off code.
  - AC-001.17: THE backend SHALL sign every hand-off code. A code SHALL be valid for 60 seconds, usable once, and redeemable only on the site of the target it names (a Workspace site, or `admin.` for an Operator).
  - AC-001.18: WHEN a valid hand-off code is redeemed on its site, THE backend SHALL:
    - create a new session with a new session id for that Account and Workspace (or for that Operator);
    - set the cookie `__Host-TSOLVE_SESSION` (HttpOnly, Secure, SameSite=Lax, Path `/`, no Domain attribute; with the `__Host-` prefix the browser refuses the cookie otherwise, so it is sent only to that host);
    - redirect to the kept `returnTo`, or to `/` when there is none.
  - AC-001.19: IF a hand-off code is missing, invalid, expired, already used or presented on another site, THEN THE backend SHALL redirect to `/login?error=MSG141` on the site where it was presented and create no session.
  - AC-001.20: IF a request to a Workspace site carries a session created on the Workspace site of another Workspace, THEN THE backend SHALL answer 401 with `code` `MSG123`, not run the endpoint, and not end that session (GBR-03).
  - AC-001.21: IF a request to the configured Platform Console origin (`https://admin.tsolve.io.vn` in production) carries a Workspace session, THEN THE backend SHALL answer 401 with `code` `MSG123` and not run the endpoint (D13).
  - AC-001.22: THE backend SHALL treat a request to `login.` or `api.` as having no session, whatever session cookie it carries.
  - AC-001.23: IF a request carries a session created more than 8 hours ago, or whose last activity was more than 60 minutes ago, THEN THE backend SHALL end the session and answer 401 with `code` `MSG07` (GBR-03).
  - AC-001.24: WHEN a request with a valid session comes without the header `X-TSolve-Background: 1`, THE backend SHALL count it as activity and restart the 60-minute limit.
  - AC-001.25: WHEN a request with a valid session carries the header `X-TSolve-Background: 1`, THE backend SHALL answer it as usual but SHALL not count it as activity. The 8-hour limit applies to it as well (decision Q6: polling and real-time updates do not keep a session alive).
  - AC-001.26: WHEN a request carries a valid Workspace session, THE backend SHALL re-read the Account at that request and put a `TSolveUser` with its current id, email, full name and Roles in the security context (be-foundation AC-002.2), so a Role change applies from the next request.
  - AC-001.27: IF the Account of a Workspace session is Deactivated, THEN THE backend SHALL end the session at its next request and answer 401 with `code` `MSG10` (GBR-03).
  - AC-001.28: WHEN a browser opens `GET /api/v1/auth/sign-in` on the configured Platform Console origin (`https://admin.tsolve.io.vn` in production), THE backend SHALL create a state value naming the Platform Console and answer 302 to `authorize` on the configured central sign-in origin (as AC-001.1, without `hd` at Google, D13).
  - AC-001.29: WHEN every check of AC-001.6, AC-001.46 and AC-001.9 passes for a Platform Console target and the verified email is in `PLATFORM_OPERATOR_EMAILS` (compared ignoring case), THE backend SHALL redirect to `/api/v1/auth/complete?code=…` on the Platform Console origin configured in `TSOLVE_ADMIN_ORIGIN` (`https://admin.tsolve.io.vn` in production). The session made there SHALL be an Operator session with no Account, no Workspace and none of the five Roles.
  - AC-001.30: IF the verified email of a Platform Console sign-in is not in the allow-list, THEN THE backend SHALL redirect to `/login?error=MSG135` on the configured Platform Console origin (`https://admin.tsolve.io.vn` in production) and create no session (GBR-03).
  - AC-001.31: IF a Google error, a failed code exchange or a failed ID token check happens for a Platform Console target whose state value passes every check of AC-001.6 and AC-001.46, THEN THE backend SHALL redirect to `/login?error=MSG06` on the configured Platform Console origin (`https://admin.tsolve.io.vn` in production) and create no session.
  - AC-001.32: IF a request to a Workspace site carries an Operator session, THEN THE backend SHALL answer 401 with `code` `MSG123` and not run the endpoint (D13).
  - AC-001.33: IF a request carries an Operator session whose email is no longer in the allow-list, THEN THE backend SHALL end the session and answer 401 with `code` `MSG135`.
  - AC-001.34: THE backend SHALL apply the limits of AC-001.23 to AC-001.25 to Operator sessions as well.
  - AC-001.35: WHEN `jira-app` asks `SignInStateService` for a sign-in link with a Workspace and a ticket tracker account taken from a verified Forge Invocation Token, THE backend SHALL return `/api/v1/auth/authorize?state=…` on the configured central sign-in origin (`https://login.tsolve.io.vn` in production) with a state value that carries both (GBR-19, D1).
  - AC-001.36: WHEN every check of AC-001.14 passes for a Jira-link state value, THE backend SHALL:
    - record in that Workspace the Jira account link between the state value's ticket tracker account and the Account, replacing any earlier link of that ticket tracker account in the Workspace;
    - show the minimal `login.` page with MSG75 (`{tracker_name}` = Jira);
    - create no web session.
  - AC-001.37: IF a check fails for a Jira-link state value, THEN THE backend SHALL show the minimal `login.` page with the message of the first failure of AC-001.14 and record no link. A Workspace that is not Active SHALL show MSG121 instead of MSG120, and MSG122 SHALL have `{google_domain}` filled in.
  - AC-001.38: THE backend SHALL take the ticket tracker account of a link only from the verified state value, never from a callback parameter (GBR-19).
  - AC-001.39: THE minimal `login.` page SHALL show only the message's exact Report3 text, with no link to any Workspace site, and SHALL not load the web app.
  - AC-001.40: THE Login screen (1.1.1) SHALL show one button "Sign in with Google" and a message area, and no email, password or sign-up field.
  - AC-001.41: WHEN the user clicks "Sign in with Google", THE Login screen SHALL start the sign-in through fe-foundation's `startSignIn` with the `returnTo` of the Login URL.
  - AC-001.42: WHEN the Login URL has `error` set to MSG06, MSG09, MSG10, MSG122, MSG123 or MSG141, THE Login screen SHALL show that message's catalog text in its message area, as given by fe-foundation's `useSignInMessage` (AC-012.3), with `{google_domain}` filled in from `google_domain` for MSG122. MSG122 SHALL be shown only with a valid `google_domain` (fe-foundation AC-012.4). `error=MSG120` SHALL leave the message area empty; on a suspended Workspace the web app shows the MSG120 full page instead (fe-foundation AC-011.4).
  - AC-001.43: IF the Login URL has an `error` value that is not one of the codes of AC-001.42, THEN THE Login screen SHALL show an empty message area.
  - AC-001.44: WHEN `authorize` on `login.` accepts a valid state value that is not yet marked as authorized, THE backend SHALL, in the same answer as its 302 to Google (AC-001.4), sent with `Cache-Control: no-store`, set the cookie `__Host-TSOLVE_SIGNIN` (HttpOnly, Secure, SameSite=Lax, Path `/`, no Domain attribute, Max-Age 600 seconds) holding a new random value of 32 bytes (base64url), keep only the SHA-256 hash of that value with the state value, and mark the state value as authorized.
  - AC-001.45: IF `authorize` gets a state value that is valid under AC-001.6 but already marked as authorized, THEN THE backend SHALL answer 400 with the minimal `login.` page showing MSG141, set no cookie and not redirect to Google.
  - AC-001.46: IF the callback gets a valid state value without a `__Host-TSOLVE_SIGNIN` cookie whose SHA-256 hash matches the hash kept with that state value (also when the state value was never authorized), THEN THE backend SHALL consume the state value, answer 400 with the minimal `login.` page showing MSG141, create no session, change no Account and record no Jira account link.
  - AC-001.47: THE callback SHALL expire the cookie `__Host-TSOLVE_SIGNIN` (`Max-Age=0`, Secure, Path `/`, no Domain attribute) in every answer it gives on `login.`, whatever the outcome.
  - AC-001.48: IF `GET /api/v1/auth/sign-in` on a Workspace site resolves a Workspace that has no Google domain, THEN THE backend SHALL answer 302 to `/login?error=MSG141` on that Workspace site, create no state value, and log a `WARN` entry that names only the Workspace id.
  - AC-001.49: IF `authorize` or the callback gets a valid state value for a Workspace or Jira-link target whose Workspace has no Google domain, THEN THE backend SHALL answer 400 with the minimal `login.` page showing MSG141, not redirect to Google, set no sign-in cookie at `authorize`, and create no session; the callback SHALL consume the state value and record no Jira account link.
  - AC-001.50: IF the callback gets a valid state value whose Workspace no longer exists (the platform's lookup finds no Workspace with its id), THEN THE backend SHALL consume the state value, answer 400 with the minimal `login.` page showing MSG141, and create no session, change no Account and record no Jira account link.

### STORY-002: Log out
- As an Employee, I want to log out so that my session ends and the next person at this computer sees nothing of
  mine. (KAN-106)
- Acceptance:
  - AC-002.1: WHEN a signed-in user sends `POST /api/v1/auth/logout` with a valid CSRF token, on a Workspace site or on `admin.`, THE backend SHALL end the session, expire the cookie `__Host-TSOLVE_SESSION` (`Max-Age=0`, with `Secure` and `Path=/` and no `Domain`) and answer 204.
  - AC-002.2: WHEN a session has been ended by logout, THE backend SHALL answer every later request that carries its cookie with 401 and `code` `MSG07`.
  - AC-002.3: WHEN a Workspace session ends, THE backend SHALL publish exactly one `SessionEnded` event with the Account id, the Workspace id, the session id and the reason, after the session is gone. The reason SHALL be `LOGOUT` (AC-002.1), `EXPIRED` (AC-001.23) or `DEACTIVATED` (AC-001.27).
  - AC-002.4: WHILE a Workspace session is past a limit of AC-001.23 and no request uses it, THE backend SHALL still end it and publish `SessionEnded` with `EXPIRED` at most 15 minutes after the limit passed.
  - AC-002.5: WHEN a user logs out, THE backend SHALL keep every Jira account link of the Account unchanged.
  - AC-002.6: IF `POST /api/v1/auth/logout` comes with no valid session, THEN THE backend SHALL answer 401 with `code` `MSG07` (be-foundation AC-002.4) and publish no event. The web app still clears its data and opens Login (fe-foundation AC-008.3).
  - AC-002.7: THE My Profile screen SHALL have a "Log out" button that signs out through fe-foundation's `useSignOut()`, exactly like the user menu (fe-foundation AC-008.1 to AC-008.3).

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
  - AC-003.6: WHEN a signed-in user calls `GET /api/v1/me/solutions` with `page` (from 0, default 0) and `size` (1 to 100, default 20), THE backend SHALL answer 200 with a `MySolutionPage`: that page of the written-ticket lookup for the signed-in Account, newest `updatedAt` first.
  - AC-003.7: THE backend SHALL call the written-ticket lookup and the viewable Space lookup only with the signed-in Account's id, in its own Workspace, and SHALL take no user id or Workspace from the request (GBR-37).
  - AC-003.8: WHILE the written-ticket lookup has no implementation (`ticket-repository` not built), THE backend SHALL answer `GET /api/v1/me/solutions` with 200 and an empty page.
  - AC-003.9: IF `page` is negative, or `size` is below 1 or above 100, THEN THE backend SHALL answer 400 with a `fieldErrors` entry for that parameter with `code` `MSG06` (be-foundation AC-001.5).
  - AC-003.10: IF nobody is signed in, THEN THE backend SHALL answer `GET /api/v1/me/solutions` and `GET /api/v1/me/spaces` with 401 and `code` `MSG07`. Every signed-in user (Employee) SHALL be allowed to call them.
  - AC-003.11: WHEN My Solutions has rows, THE My Profile screen SHALL list them with Ticket ID, title, Project, status as a Status Tag (fe-foundation STORY-007) and date, with paging of 20 rows.
  - AC-003.12: WHEN the user clicks the Ticket ID of a row whose `canOpen` is true, THE web app SHALL open Ticket Detail at `/tickets/:ticketId`. The Ticket ID of a row whose `canOpen` is false SHALL not be a link.
  - AC-003.13: WHILE My Solutions has no rows, THE My Profile screen SHALL show MSG04 in that section.
  - AC-003.14: IF loading My Solutions or "Spaces I can view" fails, THEN THE My Profile screen SHALL show MSG06 in that section and still show the account information of AC-003.1.
  - AC-003.15: THE My Profile screen SHALL always show the Spaces card, whatever the user's Roles:
    - WHILE "Spaces I can view" loads, the card shows a loading spinner at once, with no delay;
    - WHEN "Spaces I can view" has loaded and is empty, the card shows, under "Spaces I can view", the grey text MSG142 ("No Spaces found");
    - "Spaces I manage" shows only for a Knowledge Manager with managed Spaces (AC-003.2); when loading fails, AC-003.14 applies.
  - AC-003.16: THE My Profile screen SHALL always show the Projects card; WHEN the user has no Projects, it SHALL show, in place of the table, the grey text MSG143 ("Not a member of any Project.").

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

## Out of Scope
- Standalone Project or Space handover pages, routes, menu actions or mutation endpoints are outside KAN-111 and KAN-112. Reuse the combined popup required by STORY-006–007. KAN-112 does not implement production backend persistence or authorization.
- The Workspace resolver, MSG119 and MSG120 on ordinary requests, the Forge Invocation Token check, RLS →
  `platform` (KAN-196). A Suspended Workspace's site is refused there; its sessions are not ended by this
  module.
- The T-Solve app's panel states MSG62 and MSG74, and the `api.` endpoint that asks `SignInStateService` for a
  sign-in link → `jira-app` (Đặng Tấn Bình, KAN-124).
- The Platform Console pages, its Login page that shows MSG135 and MSG06, and what an Operator may do →
  `platform-console` (Đặng Tấn Bình, KAN-113…116).
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
- Signing the user out of Google; self-registration; any OIDC provider other than Google; any sign-in with a
  password.
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
| Workspace site, `admin.` | `GET /api/v1/auth/sign-in?returnTo=<path>` (full-page navigation, no session needed) | 302 to `login.` `authorize`; a Workspace with no Google domain: 302 `/login?error=MSG141` (AC-001.48) |
| `login.` | `GET /api/v1/auth/authorize?state=…` | 302 to Google with `Set-Cookie: __Host-TSOLVE_SIGNIN` (AC-001.44), or 400 MSG141 page (invalid or already authorized state value, or a Workspace with no Google domain; no cookie) |
| `login.` | `GET /api/v1/auth/callback?code=…&state=…` (needs the `__Host-TSOLVE_SIGNIN` cookie set by `authorize`, AC-001.46) | 302 to `complete` / `login?error=…`, or the minimal page (MSG141, MSG75, Jira-link failures); every answer expires `__Host-TSOLVE_SIGNIN` (AC-001.47) |
| Workspace site, `admin.` | `GET /api/v1/auth/complete?code=…` | sets `__Host-TSOLVE_SESSION`, 302 to `returnTo` or `/`; failure 302 `/login?error=MSG141` |
| Workspace site, `admin.` | `POST /api/v1/auth/logout` (CSRF) | 204 (unchanged contract) |
| Workspace site | `GET /api/v1/me/solutions?page&size` (`operationId` `getMySolutions`) | `MySolutionPage` |
| Workspace site | `GET /api/v1/me/spaces` (`operationId` `getMyViewableSpaces`) | `SpaceRef[]` |

**Schemas:**

  ```yaml
  MySolutionPage: { content: MySolution[], page: PageMetadata }   # both required, as ReviewQueuePage
  MySolution:
    required: [ticketId, ticketKey, title, projectName, status, updatedAt, canOpen]
    ticketId:    string (uuid)   # path id of Ticket Detail
    ticketKey:   string     # the Ticket ID
    title:       string
    projectName: string
    status:      TicketStatus
    updatedAt:   date-time
    canOpen:     boolean    # true when UC-24 lets this user open the Ticket
  TicketStatus: enum [PENDING_REVIEW, CHANGES_REQUESTED, PUBLISHED, REJECTED, EXPIRED, UNPUBLISHED]   # GBR-07
  ```

**Sign-in error codes:**
- Sent back as `/login?error=<code>` on the Workspace site: MSG06, MSG09, MSG10, MSG120, MSG122 (with
  `google_domain`) and MSG141.
- Sent back on `admin.`: MSG06 and MSG135.
- Shown on the minimal `login.` page: MSG141 when no target is known or the sign-in request is refused (AC-001.6,
  AC-001.45, AC-001.46, AC-001.49, AC-001.50), and the Jira-link results MSG75, MSG06,
  MSG09, MSG10, MSG121 and MSG122.

**Security:**
- identity adds its own `SecurityFilterChain` for `/api/v1/auth/**`, ordered before be-foundation's chain. It
  allows the paths above without a session (decision Q11, as be-foundation Q5 does for Forge). be-foundation's
  criteria are unchanged.
- A Workspace session puts be-foundation's `TSolveUser` in the security context.
- An Operator session puts a separate Operator principal (email, full name) in the security context, with none of
  the five Roles. `platform-console` checks for it.

**Services:**
- `SignInStateService`: for `jira-app` (a link state for a Workspace and a ticket tracker account) and the
  sign-in start.
- `JiraAccountLinkLookup`: the Account linked to a ticket tracker account in a Workspace, for `jira-app`. The
  table is `jira_account_links` (D1); one link per ticket tracker account per Workspace.
- `AccountDirectory`:
  - for `platform-console`: the number of Active and Deactivated Accounts of a Workspace (UC-54), and whether
    any Admin of a Workspace has signed in (D14, UC-56);
  - for every module: an Account by id (a `UUID`), in the caller's Workspace.
- The Department lookup of be-foundation (`common`): the Department of a Project Manager.
- `SessionEnded` (Spring application event): Account id, Workspace id, session id, reason `LOGOUT` /
  `EXPIRED` / `DEACTIVATED`.

**Database.** One Liquibase changeset. Every table follows the platform tenant-table convention (platform AC-001.8):
a plural name, UUID ids (`id` and every `<entity>_id`), `workspace_id` and row-level security.
- `accounts`: email unique on the whole platform and stored lower-case (GBR-37, D7); full name, empty until the
  first sign-in; status `ACTIVE` / `DEACTIVATED`; the Department of a Project Manager; first and last sign-in
  time.
- `account_roles`: the Account's Roles, the four Roles besides Employee, since Employee is implied (be-foundation
  AC-002.2).
- `jira_account_links` (the D1 name).

**Header.** `X-TSolve-Background: 1` marks a request that is not user activity (AC-001.25).

### UC-04–07 frontend/MSW exposed contracts
- Existing Admin routes: `/users`, `/users/new`, `/users/:userId` from `users/routes.ts`.
- An API contract stub in `frontend/openapi/tsolve-api.yaml`, generated through orval, for user list/detail and mutations plus the lookup data required by these screens. Endpoint and schema details belong in the design and are frontend mock proposals until agreed with the backend owner.
- Pagination and errors follow guideline 06: `{ content, page: { size, number, totalElements, totalPages } }`; RFC 9457 Problem Details with catalog `code`, `params` and optional `fieldErrors`.
- Mutable MSW handlers live in `frontend/src/features/users/mocks/handlers.ts`, using synthetic Workspace-scoped fixtures and validating rejected operations before changing mock records.
- `UserAccount.id` is a UUID string (`type: string`, `format: uuid`), consistent with the `accounts.id` convention; `/users/:userId` carries that UUID. DepartmentRef and SpaceRef follow the current shared contracts.
- The user-list query `q` has a 100-character maximum after trimming; the mock rejects longer values with 400 MSG02 and parameters `field_name: Search`, `max_length: 100`.

### Required from other modules
- `platform` (KAN-196):
  - `TenantResolver` and `TenantContext` for Workspace-site requests;
  - a way to run the callback's lookups in the context of the state value's Workspace;
  - the Workspace record (site name, Google domain, status);
  - the convention for tenant tables.
- `be-foundation`: `TSolveUser`, the five Role constants, `ProblemDetail` errors, CSRF (AC-002.11, AC-002.13),
  and `/me` (STORY-003 of be-foundation), whose Department lookup this module implements.
- `fe-foundation` change request KAN-198: `startSignIn(returnTo)` (full-page `GET /api/v1/auth/sign-in`),
  `useSignInMessage()`, the catalog codes of AC-001.42, the global 401 handling, and the
  `X-TSolve-Background: 1` header on requests the web app sends on its own.
- `ticket-repository` (Đặng Nhật Minh, KAN-132): a written-ticket lookup in `common`. Given an Account and a
  page, it returns the Tickets whose Solution that Account wrote, as `MySolution` rows:
  - no Drafts (GBR-26);
  - none of a Deleted Department (GBR-17);
  - none after the Ticket Resolver Role is removed, unless the Account is a Project Manager (GBR-02, D32);
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
