---
module: identity
date: 2026-10-25
owner: BA
---

# Spec: identity

Slice UC-01…03 (owner Trần Trung Hiếu, Jira KAN-103, KAN-104, KAN-106, KAN-160). UC-04…09 are added to this
spec by their owner (Changelog).

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-10-25 | STORY-001..STORY-003 | Initial spec of the UC-01…03 slice: central sign-in through `login.` with Google OIDC, the session per Workspace site, the Platform Operator and Jira-link sign-ins, log out, My Profile with solution history. Written from Report3 (GBR-03, GBR-19, GBR-28, GBR-37, D1, D9, D13, D14, D22, D23, D36), Stories TS-020, TS-021, TS-023, TS-127 and the planning decisions Q1–Q16 of 25/10 | KAN-197 (Phase 1, multi-tenant baseline) |
| 2026-10-25 | — | UC-04…09 (View user list, Add Account, Update Account status, Update roles and Department, Hand over Projects, Hand over Spaces) will be added as STORY-004 onwards by their owner Phạm Tùng Dương (KAN-107…112) | One module, two owners (D40: the Jira assignee owns the Story) |
| 2026-10-25 | STORY-001, STORY-003 | Interface Contracts only: `MySolution.ticketId` is a UUID string (`format: uuid`); the Account table is `accounts` and the Roles table `account_roles`, each with a UUID `id` under the platform convention (platform AC-001.8); `jira_account_links` keeps its D1 name. No acceptance criterion added, reworded or removed (`SpaceRef` follows the fe-foundation contract) | Change request KAN-196 Q2 (the user, 25/10): UUID primary keys and plural table names in every module (Phan_cong v6 §1); SPEC-P1-7 |

## Requirements

### Functional
1. People sign in only with a Google account of their company's Google domain, through the central sign-in site
   `login.tsolve.io.vn`. `login.` has no Workspace of its own. It learns the target only from a signed,
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

### Non-Functional
1. Every acceptance criterion has a test whose name cites its id. Backend: JUnit `…_AC_001_1`. Web app: `it`
   names start with the id. `sdd.ts trace identity` passes for STORY-001…003.
2. `mvn verify` passes on every commit. The frontend `verify` passes too: `format:check`, `lint`,
   `typecheck`, `test`, `build`, and `generate:api` with no diff.
3. No token, ID token, Google credential, state value or hand-off code is kept in browser storage. The browser
   holds only the HttpOnly session cookie.
4. Secrets and hosts come only from the environment, with no default (architecture: Configuration):
   - `GOOGLE_CLIENT_ID`
   - `GOOGLE_CLIENT_SECRET`
   - the key that signs state values and hand-off codes
   - `PLATFORM_OPERATOR_EMAILS` (the allow-list, a comma-separated list)
5. Two-Workspace tests cover the session rules: AC-001.7, AC-001.15, AC-001.17, AC-001.20 and AC-001.32. Each
   uses two Workspaces with Accounts of the same Roles.
6. Error bodies follow be-foundation (`ProblemDetail` with a Report3 `code`). Sign-in failures are redirects with
   a code, never backend text.

## User Stories

### STORY-001: Sign in with Google through the central sign-in
- As an Employee, I want to sign in with my company Google account on my company's Workspace site and land on the
  page of my Roles, so that I use T-Solve without a separate password and only in my own Workspace. (KAN-103
  central sign-in, KAN-104 session per Workspace site and Login screen.)
- Acceptance:
  - AC-001.1: WHEN a browser opens `GET /api/v1/auth/sign-in` on a Workspace site, THE backend SHALL create a state value for the Workspace that the platform resolved from the host and answer 302 to `https://login.tsolve.io.vn/api/v1/auth/authorize?state=…`, without a signed-in user.
  - AC-001.2: THE backend SHALL create every state value on the server only, signed with its key, valid for 10 minutes and usable once. The state SHALL carry exactly one target (a Workspace, the Platform Console, or a Workspace plus a ticket tracker account) and the kept `returnTo`. The OIDC nonce and PKCE code verifier SHALL stay on the server (D9).
  - AC-001.3: WHERE `returnTo` is given to the sign-in start, THE backend SHALL keep it only if it is a relative path of the same site (one leading `/`, not `//`, no scheme, no host) and SHALL use `/` otherwise.
  - AC-001.4: WHEN `GET /api/v1/auth/authorize` on `login.` gets a valid state value, THE backend SHALL answer 302 to Google's authorization endpoint with:
    - `scope` `openid email profile`, the state value, the OIDC nonce and a PKCE `S256` code challenge;
    - the redirect URI `https://login.tsolve.io.vn/api/v1/auth/callback`;
    - for a Workspace or Jira-link target, `hd` set to the Workspace's Google domain.
  - AC-001.5: THE backend SHALL register and accept only `https://login.tsolve.io.vn/api/v1/auth/callback` as the OIDC redirect URI. A request to the authorize or callback path on any other host SHALL be answered 404.
  - AC-001.6: IF `authorize` or the callback gets a state value that is missing, not signed by the server, expired or already used, THEN THE backend SHALL answer 400 with the minimal `login.` page showing MSG141 and create no session (GBR-03).
  - AC-001.7: THE callback SHALL take the target Workspace only from the verified state value and SHALL ignore every other parameter, header or cookie that names a Workspace or a site (GBR-03, GBR-37).
  - AC-001.8: IF Google returns an error or the user cancels, with a valid state value for a Workspace, THEN THE backend SHALL redirect to `/login?error=MSG06` on that Workspace site and create no session.
  - AC-001.9: IF the code exchange fails, or the ID token fails verification (signature, issuer, audience, expiry, nonce, or `email_verified` not true), THEN THE backend SHALL redirect to `/login?error=MSG06` on the Workspace site and create no session.
  - AC-001.10: IF the state value's Workspace is not Active at the callback, THEN THE backend SHALL redirect to `/login?error=MSG120` on its Workspace site and create no session.
  - AC-001.11: IF the `hd` claim is missing or differs (ignoring case) from the Workspace's Google domain, THEN THE backend SHALL redirect to `/login?error=MSG122&google_domain=<the Workspace's Google domain>` on its Workspace site and create no session (GBR-03, D22).
  - AC-001.12: IF the Workspace has no Account with the verified email (compared ignoring case), THEN THE backend SHALL redirect to `/login?error=MSG09` on its Workspace site and create no session.
  - AC-001.13: IF the Account with that email is Deactivated, THEN THE backend SHALL redirect to `/login?error=MSG10` on its Workspace site and create no session.
  - AC-001.14: THE callback SHALL check in this order and report only the first failure: state value (MSG141), Google result and ID token (MSG06), Workspace Active (MSG120), `hd` (MSG122), Account exists (MSG09), Account Active (MSG10).
  - AC-001.15: THE callback SHALL look up the Account only among the Accounts of the state value's Workspace (in that Workspace's tenant context), so an Account of another Workspace is never found or changed (GBR-37).
  - AC-001.16: WHEN every check passes for a Workspace target, THE backend SHALL:
    - set the Account's full name from the `name` claim (unchanged when the claim is empty);
    - record the sign-in time, and the first sign-in time when the Account has none (D14);
    - redirect to `/api/v1/auth/complete?code=…` on the Workspace site, with a one-time hand-off code.
  - AC-001.17: THE backend SHALL sign every hand-off code. A code SHALL be valid for 60 seconds, usable once, and redeemable only on the site of the target it names (a Workspace site, or `admin.` for an Operator).
  - AC-001.18: WHEN a valid hand-off code is redeemed on its site, THE backend SHALL:
    - create a new session with a new session id for that Account and Workspace (or for that Operator);
    - set the cookie `TSOLVE_SESSION` (HttpOnly, Secure, SameSite=Lax, Path `/`, no Domain attribute, so it is sent only to that host);
    - redirect to the kept `returnTo`, or to `/` when there is none.
  - AC-001.19: IF a hand-off code is missing, invalid, expired, already used or presented on another site, THEN THE backend SHALL redirect to `/login?error=MSG141` on the site where it was presented and create no session.
  - AC-001.20: IF a request to a Workspace site carries a session created on the Workspace site of another Workspace, THEN THE backend SHALL answer 401 with `code` `MSG123`, not run the endpoint, and not end that session (GBR-03).
  - AC-001.21: IF a request to `admin.tsolve.io.vn` carries a Workspace session, THEN THE backend SHALL answer 401 with `code` `MSG123` and not run the endpoint (D13).
  - AC-001.22: THE backend SHALL treat a request to `login.` or `api.` as having no session, whatever session cookie it carries.
  - AC-001.23: IF a request carries a session created more than 8 hours ago, or whose last activity was more than 60 minutes ago, THEN THE backend SHALL end the session and answer 401 with `code` `MSG07` (GBR-03).
  - AC-001.24: WHEN a request with a valid session comes without the header `X-TSolve-Background: 1`, THE backend SHALL count it as activity and restart the 60-minute limit.
  - AC-001.25: WHEN a request with a valid session carries the header `X-TSolve-Background: 1`, THE backend SHALL answer it as usual but SHALL not count it as activity. The 8-hour limit applies to it as well (decision Q6: polling and real-time updates do not keep a session alive).
  - AC-001.26: WHEN a request carries a valid Workspace session, THE backend SHALL re-read the Account at that request and put a `TSolveUser` with its current id, email, full name and Roles in the security context (be-foundation AC-002.2), so a Role change applies from the next request.
  - AC-001.27: IF the Account of a Workspace session is Deactivated, THEN THE backend SHALL end the session at its next request and answer 401 with `code` `MSG10` (GBR-03).
  - AC-001.28: WHEN a browser opens `GET /api/v1/auth/sign-in` on `admin.tsolve.io.vn`, THE backend SHALL create a state value naming the Platform Console and answer 302 to `authorize` on `login.` (as AC-001.1, without `hd` at Google, D13).
  - AC-001.29: WHEN every check of AC-001.6 and AC-001.9 passes for a Platform Console target and the verified email is in `PLATFORM_OPERATOR_EMAILS` (compared ignoring case), THE backend SHALL redirect to `https://admin.tsolve.io.vn/api/v1/auth/complete?code=…`. The session made there SHALL be an Operator session with no Account, no Workspace and none of the five Roles.
  - AC-001.30: IF the verified email of a Platform Console sign-in is not in the allow-list, THEN THE backend SHALL redirect to `https://admin.tsolve.io.vn/login?error=MSG135` and create no session (GBR-03).
  - AC-001.31: IF a Google error, a failed code exchange or a failed ID token check happens for a Platform Console target, THEN THE backend SHALL redirect to `https://admin.tsolve.io.vn/login?error=MSG06` and create no session.
  - AC-001.32: IF a request to a Workspace site carries an Operator session, THEN THE backend SHALL answer 401 with `code` `MSG123` and not run the endpoint (D13).
  - AC-001.33: IF a request carries an Operator session whose email is no longer in the allow-list, THEN THE backend SHALL end the session and answer 401 with `code` `MSG135`.
  - AC-001.34: THE backend SHALL apply the limits of AC-001.23 to AC-001.25 to Operator sessions as well.
  - AC-001.35: WHEN `jira-app` asks `SignInStateService` for a sign-in link with a Workspace and a ticket tracker account taken from a verified Forge Invocation Token, THE backend SHALL return `https://login.tsolve.io.vn/api/v1/auth/authorize?state=…` with a state value that carries both (GBR-19, D1).
  - AC-001.36: WHEN every check of AC-001.14 passes for a Jira-link state value, THE backend SHALL:
    - record in that Workspace the Jira account link between the state value's ticket tracker account and the Account, replacing any earlier link of that ticket tracker account in the Workspace;
    - show the minimal `login.` page with MSG75 (`{tracker_name}` = Jira);
    - create no web session.
  - AC-001.37: IF a check fails for a Jira-link state value, THEN THE backend SHALL show the minimal `login.` page with the message of the first failure of AC-001.14 and record no link. A Workspace that is not Active SHALL show MSG121 instead of MSG120, and MSG122 SHALL have `{google_domain}` filled in.
  - AC-001.38: THE backend SHALL take the ticket tracker account of a link only from the verified state value, never from a callback parameter (GBR-19).
  - AC-001.39: THE minimal `login.` page SHALL show only the message's exact Report3 text, with no link to any Workspace site, and SHALL not load the web app.
  - AC-001.40: THE Login screen (1.1.1) SHALL show one button "Sign in with Google" and a message area, and no email, password or sign-up field.
  - AC-001.41: WHEN the user clicks "Sign in with Google", THE Login screen SHALL start the sign-in through fe-foundation's `startSignIn` with the `returnTo` of the Login URL.
  - AC-001.42: WHEN the Login URL has `error` set to MSG06, MSG07, MSG09, MSG10, MSG120, MSG122, MSG123 or MSG141, THE Login screen SHALL show that message's catalog text in its message area, as given by fe-foundation's `useSignInMessage`, with `{google_domain}` filled in from `google_domain` for MSG122.
  - AC-001.43: IF the Login URL has an `error` value that is not one of the codes of AC-001.42, THEN THE Login screen SHALL show an empty message area.

### STORY-002: Log out
- As an Employee, I want to log out so that my session ends and the next person at this computer sees nothing of
  mine. (KAN-106)
- Acceptance:
  - AC-002.1: WHEN a signed-in user sends `POST /api/v1/auth/logout` with a valid CSRF token, on a Workspace site or on `admin.`, THE backend SHALL end the session, expire the cookie `TSOLVE_SESSION` (`Max-Age=0`) and answer 204.
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
    - the Projects, each with its Department and the user's role in it.
  - AC-003.2: WHERE the user is a Knowledge Manager, THE My Profile screen SHALL list "Spaces I manage" from the session's managed Spaces.
  - AC-003.3: WHEN My Profile opens, THE web app SHALL load `GET /api/v1/me/spaces` and list "Spaces I can view" by name.
  - AC-003.4: WHEN a signed-in user calls `GET /api/v1/me/spaces`, THE backend SHALL answer 200 with a list of `SpaceRef` (`spaceId`, `name`), sorted by name, exactly as the viewable Space lookup returns them for the signed-in Account.
  - AC-003.5: WHILE the viewable Space lookup has no implementation (`wiki` not built), THE backend SHALL answer `GET /api/v1/me/spaces` with 200 and an empty list.
  - AC-003.6: WHEN a signed-in user calls `GET /api/v1/me/solutions` with `page` (from 0, default 0) and `size` (1 to 100, default 20), THE backend SHALL answer 200 with a `MySolutionPage`: that page of the written-ticket lookup for the signed-in Account, newest `updatedAt` first.
  - AC-003.7: THE backend SHALL call the written-ticket lookup and the viewable Space lookup only with the signed-in Account's id, in its own Workspace, and SHALL take no user id or Workspace from the request (GBR-37).
  - AC-003.8: WHILE the written-ticket lookup has no implementation (`ticket-repository` not built), THE backend SHALL answer `GET /api/v1/me/solutions` with 200 and an empty page.
  - AC-003.9: IF `page` is negative, or `size` is below 1 or above 100, THEN THE backend SHALL answer 400 with a `fieldErrors` entry for that parameter with `code` `MSG06` (be-foundation AC-001.5).
  - AC-003.10: IF nobody is signed in, THEN THE backend SHALL answer `GET /api/v1/me/solutions` and `GET /api/v1/me/spaces` with 401 and `code` `MSG07`. Every signed-in user (Employee) SHALL be allowed to call them.
  - AC-003.11: WHEN My Solutions has rows, THE My Profile screen SHALL list them with Ticket ID, title, Project, status as a Status Tag (fe-foundation STORY-007) and date, with paging of 20 rows.
  - AC-003.12: WHEN the user clicks a row whose `canOpen` is true, THE web app SHALL open Ticket Detail at `/tickets/:ticketId`. A row whose `canOpen` is false SHALL not be a link.
  - AC-003.13: WHILE My Solutions has no rows, THE My Profile screen SHALL show MSG04 in that section.
  - AC-003.14: IF loading My Solutions or "Spaces I can view" fails, THEN THE My Profile screen SHALL show MSG06 in that section and still show the account information of AC-003.1.

## Out of Scope
- UC-04…09: user list, adding Accounts, account status, Roles and Department, hand-overs. They are owned by Phạm
  Tùng Dương (KAN-107…112) and added to this spec as STORY-004 onwards. This slice only creates the Account
  store they extend.
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

## Interface Contracts

### Exposed by this module

**REST.** Each endpoint is added to `frontend/openapi/tsolve-api.yaml` in the same PR as its code.

| Host | Endpoint | Answer |
|------|----------|--------|
| Workspace site, `admin.` | `GET /api/v1/auth/sign-in?returnTo=<path>` (full-page navigation, no session needed) | 302 to `login.` `authorize` |
| `login.` | `GET /api/v1/auth/authorize?state=…` | 302 to Google, or 400 MSG141 page |
| `login.` | `GET /api/v1/auth/callback?code=…&state=…` | 302 to `complete` / `login?error=…`, or the minimal page (MSG141, MSG75, Jira-link failures) |
| Workspace site, `admin.` | `GET /api/v1/auth/complete?code=…` | sets `TSOLVE_SESSION`, 302 to `returnTo` or `/`; failure 302 `/login?error=MSG141` |
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
- Shown on the minimal `login.` page: MSG141 when no target is known, and the Jira-link results MSG75, MSG06,
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

## Definition of Done
- [ ] Every acceptance criterion has a test whose name starts with or cites its AC id; `sdd.ts trace identity`
      passes for STORY-001…003
- [ ] `mvn verify` and the frontend `verify` pass
- [ ] The YAML contract has the endpoints and schemas above; `generate:api` leaves no diff
- [ ] The owners of `platform`, `platform-console`, `jira-app`, `ticket-repository`, `wiki`, `chatbot` and
      `fe-foundation` have been told what they call or implement

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
