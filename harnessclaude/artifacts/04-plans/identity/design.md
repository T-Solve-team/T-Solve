---
module: identity
date: 2026-10-25
---

# Design: identity

Spec: artifacts/03-modules/identity/spec.md

## Architecture Overview

Implement only the frontend delivery of KAN-107–110. Keep the existing Admin routes and shell. Pages use feature hooks wrapping generated API hooks; TanStack Query owns server data. MSW implements the proposed contract with synthetic data while the real identity backend remains outside this delivery.

User List starts with search, role/status/Department filters and the Add user action, followed by the shared DataTable. User Detail has creation and editing modes on the existing routes. Account status and role changes use the shared confirmation patterns and the feature-local Hand Over Responsibilities popup when responsibilities must move. Required labels and messages follow the existing SRS guidance; detailed field layout must be checked against the team's source layouts before implementation.

Delivery is sequential: KAN-107, KAN-108, KAN-109, KAN-110. Each gets its issue branch and feature commit with a separate harness commit. Later stories build on previously reviewed code; do not start them before the preceding feature's user-approved commit. No push, merge or PR is authorized.

## Data Model

Use generated request/response models only. The frontend stub is a proposal for the backend owner, not a claim that these endpoints exist in production.

| Contract area | Required information | Existing convention |
|---|---|---|
| User list | User identity, email, roles, Active/Deactivated status and Department | Spring-style paged response; 20 rows by default |
| User detail | User identity, status, roles, Department, managed Spaces and responsibilities relevant to handover | UUID string user identifiers and existing Role enum |
| Creation | Company email, roles and their required assignments | Employee always included; generated request model |
| Role/assignment update | Requested roles, Department, managed Spaces and required handover choices | Validate entire change before changing mock records |
| Status update | Target status and any required handover choices | Retain account/history; prevent self-deactivation and last Active Admin deactivation |
| Lookup data | Workspace domain, Departments, Spaces, eligible replacement managers | API responses, rather than hard-coded business lists in components |
| Failures | Catalog code, parameters and field errors | Existing RFC 9457 Problem Details model |

Reuse the shared API generation pipeline, errors, paging, roles and messages. Exact endpoint names and schema property names must be documented with the contract change before implementing the corresponding hook. Keep lookups scoped to this feature's mock proposal until real backend interfaces are agreed.

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Backend availability | Mutable MSW data behind generated hooks | Wait for real backend; static decorative screens | User explicitly selected working MSW screens, prepared for later integration |
| UI foundation | Existing shared UI kit and antd primitives | New components in shared; new UI library | Project rules and Ponytail: reuse existing kit without dependency changes |
| Server state | TanStack Query through generated hooks | Local copies; hand-written fetch calls | Existing frontend contract and invalidation conventions |
| List state | Existing useListSearchParams | Component-only filters | Reload, Back and shareable URLs use the same state |
| Mutation behavior | Wait for response, then invalidate affected keys | Optimistic changes | Existing MVP convention; failed changes never appear successful |
| Handover | Existing D3 popup flow for managed responsibilities | Deactivate first; generic confirmation only | KAN-109/110 and guideline 09 require completed handover |
| Authentication and real security | Existing route guards; mock authorization tests; production enforcement remains backend work | Treat browser checks as security | Scope explicitly excludes production identity endpoints |
| Delivery | One feature and issue branch at a time | Combine all four issues into one feature commit | Team commit/branch conventions take precedence over harness defaults |

## File Structure

```text
frontend/src/features/users/
  pages/UserListPage.tsx
  pages/UserListPage.test.tsx
  pages/UserListPage.module.css             # only if tokens/antd layout do not suffice
  pages/UserDetailPage.tsx
  pages/UserDetailPage.test.tsx
  components/UserListToolbar.tsx
  components/UserListTable.tsx
  components/HandOverResponsibilitiesModal.tsx
  hooks/useUsers.ts
  hooks/useUserDetail.ts
  hooks/useUserMutations.ts
  mocks/data.ts
  mocks/handlers.ts
  mocks/handlers.test.ts
frontend/openapi/tsolve-api.yaml            # only the contract required by each story
frontend/src/shared/api/generated/         # generator output only
```

This is a maximum outline, not a scaffolding requirement: create a file only when it is needed. Routes, menu, central mock registration and shared API exports already exist. Any necessary shared/app changes require FE Lead review; do not silently edit those files.

## External Dependencies

No new dependencies. Use installed React, React Router, antd, TanStack Query, orval, MSW, Vitest and React Testing Library. No live Jira mutations are part of this work.

## Edge Cases & Risks

Dimensions analyzed: user types, input extremes, timing, scale, state transitions, environment, error cascades, authorization, data integrity, integration, compliance and business logic. Infrastructure failures, migrations and real session revocation belong to the real backend and are not implemented by this mock delivery.

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-004 | Authorization | Employee or signed-out user opens a users URL | High | Existing guard blocks or redirects access | Task 1 |
| 2 | STORY-004 | Data integrity | Fixture contains accounts from another Workspace | High | List response never returns those accounts | Task 1 |
| 3 | STORY-004 | Scale | Empty result; more than 20 matching users; last page | Medium | MSG04, correct totals and page boundaries | Task 1 |
| 4 | STORY-004 | State transitions | Change filter while on page 2; Back after searching | Medium | Filter resets page; URL restores visible state | Task 1 |
| 5 | STORY-004 | Timing | Earlier search response finishes after a newer query | Medium | Generated query keys keep the results separate | Task 1 |
| 6 | STORY-004 | Error cascades | List or lookup request fails | Medium | Catalog error and retry; no misleading empty result | Task 1 |
| 7 | STORY-005 | Input extremes | Blank, malformed, overlong or wrong-domain email | High | Reject with field message; preserve input | Task 2 |
| 8 | STORY-005 | Data integrity | Email belongs to Active or Deactivated account | High | MSG11 or MSG111; create no duplicate | Task 2 |
| 9 | STORY-005 | Business logic | Missing PM Department or occupied KM Space | High | Reject invalid assignments; Employee remains | Task 2 |
| 10 | STORY-005 | Timing | Submit twice while creation is pending | High | Disable submit and create at most one account | Task 2 |
| 11 | STORY-006 | Authorization | Deactivate self or last Active Admin | High | MSG112 or MSG56; retain original status | Task 3 |
| 12 | STORY-006 | Business logic | Managed Projects/Spaces have no replacement | High | MSG59/MSG118; no partial handover | Task 3 |
| 13 | STORY-006 | State transitions | Cancel confirmation or fail status request | High | Status and responsibilities remain unchanged | Task 3 |
| 14 | STORY-006 | Compliance | Deactivate an account with historical references | High | Retain account/history; do not delete records | Task 3 |
| 15 | STORY-007 | Data integrity | Role removal affects responsibilities and memberships | High | Handover and MSG78 where required; atomic update | Task 4 |
| 16 | STORY-007 | Authorization | Remove own Admin role or reuse stale session data | High | MSG113; invalidate affected session queries after success | Task 4 |
| 17 | STORY-007 | Integration | Contract regeneration changes generated exports | High | Regenerate, typecheck and verify no regeneration drift | Tasks 1–4 |
| 18 | STORY-004..007 | Environment | Keyboard operation at 1366 × 768; network failure | Medium | Accessible controls, no clipping, retry without losing edits | Tasks 1–4 |

## Risk Review — 2026-10-25 — Verdict: CAUTION

| Topic | Architect | Security | Performance | User | Devil's advocate | Resolution |
|-------|-----------|----------|-------------|------|------------------|------------|
| Contract | Generated stub fits current pipeline | Mock contract does not enforce production security | Paged queries limit list payloads | Working data enables usable screens | Static screens cannot prove interactions | Keep contract explicitly provisional and use MSW tests |
| Responsibility changes | Keep mutations in users feature | Reject invalid changes before mutation | Invalidate only affected query keys | Explain missing recipients with existing messages | Do not add a general workflow engine | Feature-local popup and atomic mock updates |
| Shared ownership | No shell/router rewiring is needed | Preserve central guards | Add no dependencies | Reuse familiar kit | Existing components cover this scope | Generated-only shared output; FE Lead review where required |

Agreements: reuse the existing stack and kit; use synthetic data; retain production-security limitations; verify failure flows rather than only happy paths. Security correctness takes precedence over shortcuts in responsibility changes. Contract/backend alignment remains a delivery limitation, mitigated by generated schemas and contract review before real integration. High-severity mitigations map to Tasks 1–4 above.

## Implementation gates

### Task 1 contract and source clarification — 2026-10-25

The SRS v3460 layout reference was located in the existing extracted source at `_ai/cache/Report/T-Solve_Report3_Software_Requirement_Specification_EN_v3460.docx.md` in the supplied SEP490_G89 documents folder, section III.1.3.1 and Table 98 (lines 1512–1519). Use its Full name, Email, Department, Roles, Status and Last sign-in columns, Add user action, 100-character search, and Employee only role filter. Report3/D3 remain authoritative for business rules where the older layout differs.

The user selected case-insensitive substring matching on name/email, Full name ascending by default, and sorting by Full name, Email and Last sign-in. This clarifies STORY-004 without changing its acceptance criteria. Missing names/sign-in timestamps stay empty; UUID user id breaks ties lexicographically in every mock sort.

Proposed stub endpoints: `GET /api/v1/users` (`getUsers`) accepts `q`, `role`, `status`, `departmentId`, `page`, `size`, `sort`. `role=EMPLOYEE` means Employee only; other roles mean membership. `GET /api/v1/users/departments` (`getUserDepartments`) returns selectable DepartmentRef records for this Workspace. List rows use a generated UserAccount with a UUID string id, nullable fullName, email, roles, DepartmentRef or null, status and nullable lastSignInAt; UserAccountPage reuses PageMetadata. The fixture Workspace is id 1, scoped inside mock resolvers before search/filter/paging; a foreign fixture proves isolation. Existing dev identities admin/admin-tr are mock Admins; non-Admins receive 403 and signed-out requests receive 401. These choices are mock contracts, not new shared session fields.

Builder check: source-table evidence and search/sort decisions are resolved. Reuse existing UI components, route guards and central mock registration. No new dependencies or direct shared/app edits are needed beyond generator output.

The user approved the spec and requested all four tasks in chat on 2026-10-25, then completed the user-run approval command. Approval is now present in approvals.json. Normal status transitions advanced identity through planned to implementing. The team issue branch `feat/KAN-107-identity-user-list` takes precedence over the harness default `feat/identity`; do not rewrite tracking statuses manually to change that default.

## KAN-107 review repair — 2026-10-26
Main's sign-in, logout and My Profile keep STORY-001–003. This frontend plan covers STORY-004–007. AC-004.8 explicitly requires the 100-character trimmed search limit: display MSG02, retain the input, and do not change the query, URL or displayed results on overlong submission. The mock rejects overlong q with 400 MSG02. UserAccount.id is a UUID string (OpenAPI format uuid), and the UUID breaks sorting ties lexicographically.

## KAN-108 PR #77 integration — 2026-10-26
Main32ce8cb includes KAN-205 UUID shared references and KAN-206 site/session behavior. Merge that history into the existing published KAN-108 branch. CreateUserRequest.departmentId is now nullable string(format uuid), spaceIds is string[](format uuid), and creation options/fixtures reuse the shared DepartmentRef/SpaceRef UUID shape. String required validation replaces the obsolete numeric Department rule. Keep both event streams and all incoming site/session behavior. Preserve main's BUG-017 and renumber the branch-local creation UUID defect to BUG-020; BUG-021 records the integration regression. No spec acceptance criterion changes.
## Approved mock-session bridge — KAN-109/110
The user explicitly allowed a small integration in frontend/src/mocks/session.ts for KAN-109/110 so status and role changes reach the mock /me response, with FE Lead review called out. Keep the central file change to a small adapter call; put mutable user-management fixture logic in feature-local mocks. Preserve untouched central MOCK_USERS fixtures, because their first-eight account roles/assignments do not fully match the list dataset. Only changed accounts should override session data. UserAccount ids are UUIDs; existing shared session/Department/Space references keep their current contracts. Re-read account status/roles on subsequent users mock requests, and reject deactivated actors. Production enforcement remains backend scope.

## Task 3 contract proposal — KAN-109

Before adding generated hooks, extend the mock proposal with `GET /api/v1/users/{id}` (`getUserDetail`), returning UserDetail: account fields, `isCurrentUser`, Projects and managed Spaces. `isCurrentUser` is computed by the API, without requiring the page to know development fixture mappings. `GET /api/v1/users/{id}/handover-options` (`getUserHandoverOptions`) returns eligible recipients per responsibility. `PUT /api/v1/users/{id}/status` (`updateUserStatus`) accepts the requested status and `projectHandovers` / `spaceHandovers`, and returns confirmed UserDetail. All account, Project, Department and Space references are UUID strings after main's KAN-205 migration.

Eligibility is Active, same Workspace, different user, with Project Manager in the Project's Department or Knowledge Manager for a Space. Validate all handovers and guards before any account/ownership mutation. Preserve history references separately from current responsibility ownership. The mock rechecks actor access, target scope, last Active Admin, self-deactivation and current recipient eligibility at submission. No eligible replacement produces MSG59 / MSG118; missing choices keep the popup open. No production backend guarantee is inferred from these mocks.

## STORY-001: central sign-in and the session per Workspace site — KAN-103 / KAN-104 plan, 2026-10-28

This section covers identity STORY-001 only (decision D1); STORY-002 and STORY-003 get their own plan. It builds
the backend that the sections above leave out (the real sign-in, sessions and the `accounts` store) and the Login
screen: Tasks 7–12 of `iteration-1/tasks.md`. KAN-104's leftovers (D2) map onto STORY-001: the cookie valid only on
the company's own site is AC-001.18/.20/.21/.22/.32; the role's default page after sign-in is AC-001.18 (`returnTo`
or `/`) plus fe-foundation AC-003.1. The Jira line "Callback chỉ về `login.`" is AC-001.4/.5 (Q1).

### Flow

```text
Workspace site (acme.)              login. (TSOLVE_LOGIN_ORIGIN)                  Google
 Login "Sign in with Google"
  └ startSignIn(returnTo) ─► GET /api/v1/auth/sign-in ─ state row ─► 302 ─► GET /api/v1/auth/authorize?state
                                         └ 302 (scope, state, nonce, PKCE S256, hd) ─► accounts.google.com
                                   GET /api/v1/auth/callback?code&state ◄──────────────────── 302
                                    ├ consume state · code exchange (RestClient) · ID token (NimbusJwtDecoder)
                                    ├ checks in the AC-001.14 order, inside runIn(state.workspace)
                                    └ hand-off code row ─► 302 acme./api/v1/auth/complete?code
 GET /api/v1/auth/complete?code ─ consume code · web_sessions row · Set-Cookie __Host-TSOLVE_SESSION ─► 302 returnTo or /
 every later request: WebSessionFilter ─ host/kind check · 8 h / 60 min · re-read Account ─► TSolveUser
```

`admin.` (`TSOLVE_ADMIN_ORIGIN`) follows the same path with a Platform Console state value (no `hd`, the allow-list
check). A Jira-link state value is made by `SignInStateService` for `jira-app` and ends on the minimal `login.`
page (MSG75 or the failure), never on a Workspace site.

### Components and packages

| Where | Component | Task |
|-------|-----------|------|
| `frontend/src/features/auth/pages/LoginPage.tsx` | Login (1.1.1): a centred antd `Card` from the shared kit — logo, title, one "Sign in with Google" button, and a message area (`aria-live="polite"`) that renders `msg(code, params)` from `useSignInMessage()` as text. Labels follow SRS III.1.1.1 (Q13: no Figma frame). The button calls `startSignIn(searchParams.get('returnTo') ?? undefined)` and shows its pending state after the click | T7 |
| `frontend/src/app/dev/MockSignInPage.tsx` | Dev-only mock sign-in (Q5), see below | T7 |
| `frontend/src/shared/auth/sign-in.ts` | `startSignIn` goes to `/dev/sign-in?returnTo=…` in mock mode and to `/api/v1/auth/sign-in?returnTo=…` otherwise (the same `returnTo` filter) | T7 |
| `frontend/openapi/tsolve-api.yaml` | The redirect contract (Q6), see "Contract" | T7 |
| `vn.tsolve.identity.signin` | `SignInStateService` (create, check and consume state values; `createJiraLinkUrl` for `jira-app`), `SignInStartController` (`/sign-in` on a Workspace site or `admin.`), `AuthorizeController` and `CallbackController` (`login.` only), `GoogleOidcClient` (authorization URL, code exchange), `IdTokenVerifier`, `HandOffCodeService` (issue), `LoginPage` (the minimal `login.` HTML page), `AuthHosts` (which configured origin a request is on), `SignedToken` (HMAC-SHA256 sign and verify) | T8, T9 |
| `vn.tsolve.identity.signin.port` | `WorkspaceLookup` and `TenantScope` (Q11), see "Ports" | T8 |
| `vn.tsolve.identity.account` | `Account` (tenant entity), `AccountRole`, `AccountStatus`, `AccountRepository` (`findByEmailIgnoreCase` in the current tenant) | T9 |
| `vn.tsolve.identity.session` | `CompleteController` (`/complete`, redeems hand-off codes), `WebSession`, `WebSessionRepository`, `WebSessionFilter` (the per-request rules), `SessionCleanup` (scheduled), `OperatorAllowList` | T10, T11 |
| `vn.tsolve.identity.jiralink` | `JiraAccountLink`, `JiraAccountLinkRepository` (one link per tracker account and Workspace) | T12 |
| `vn.tsolve.identity.AuthSecurityConfig` | An `@Order(1)` `SecurityFilterChain` whose `securityMatcher` is the four redirect paths: `permitAll`, stateless, no CSRF (all four are GET). Logout and every other path stay under be-foundation's deny-by-default chain (`roles-security`) | T8 |

#### Mock sign-in (Q5, mock mode only)

- Mock mode is `import.meta.env.DEV && import.meta.env.VITE_API_MOCKING === 'true'`, as for the DevUserSwitcher;
  the production build drops the page and the mock branch of `startSignIn`.
- `/dev/sign-in` is registered in `frontend/src/app/router/routes.tsx` next to `/dev/kit`, in the same dev-only way.
- The page lists each dev user of `DEV_USERS` and these outcomes:
  - Cancel at Google → `/login?error=MSG06`;
  - Unknown account → `/login?error=MSG09`;
  - Deactivated account → `/login?error=MSG10`;
  - Wrong Google domain → `/login?error=MSG122&google_domain=company.example` (the dev users' domain);
  - Expired link → `/login?error=MSG141`;
  - Suspended Workspace → `setDevSite('suspended-workspace')`, then `/login?error=MSG120` (the MSG120 full page shows).
- Choosing a dev user: `setDevUser(id)`, drop the cached queries (as the DevUserSwitcher does), then open `returnTo`
  when it is a same-site path, else `/` (fe-foundation AC-003.1 then picks the landing page).
- The FE Lead (Hiếu) agreed to the edits of `shared/auth/sign-in.ts` and `app/dev` (28/10). The route line in
  `app/router/routes.tsx` is read as part of that agreement (Decided in planning; confirm at the T7 review).
- Its tests cite no identity AC (dev tooling); the Login screen tests cite AC-001.40–.43.

#### Contract (Q6)

All four redirect endpoints go into `frontend/openapi/tsolve-api.yaml`, tag `session`:
- `GET /api/v1/auth/sign-in` (exists): the `returnTo` query value, 302 with `Location`;
- `GET /api/v1/auth/authorize?state`, with a path-level `servers` entry for `login.`: 302; 400 (`text/html`, the
  MSG141 page); 404;
- `GET /api/v1/auth/callback?code&state&error`, with the same `servers` entry: 302 to `complete` or to
  `/login?error=…&google_domain=…` (the `error` values MSG06, MSG09, MSG10, MSG120, MSG122, MSG135, MSG141 listed in
  the description); 200 and 400 `text/html` (the minimal page); 404;
- `GET /api/v1/auth/complete?code`: 302 with `Set-Cookie: __Host-TSOLVE_SESSION`; failure 302
  `/login?error=MSG141`.

orval generates unused functions for them; `generate:api` must leave no diff.

### Data model (Q8)

Three tables in the shared (non-tenant) schema, because each is read before any tenant context exists. Ids are
UUIDs (platform AC-001.8). Liquibase SQL files in `backend/src/main/resources/db/changelog/changes/` with the next
free number, listed in `db.changelog-master.xml`.

| Table | Columns | Rules |
|-------|---------|-------|
| `sign_in_states` | `id`, `target` (`WORKSPACE`, `PLATFORM_CONSOLE`, `JIRA_LINK`), `workspace_id` (null for the Console), `tracker_account_id` (Jira link only), `return_to`, `nonce`, `pkce_verifier`, `created_at`, `expires_at` (+10 min), `used_at` | A check constraint allows exactly one target shape (AC-001.2). The browser gets `<id>.<HMAC>`; nonce and verifier never leave the server |
| `hand_off_codes` | `id`, `target` (`WORKSPACE`, `PLATFORM_CONSOLE`), `workspace_id`, `account_id`, `operator_email`, `site_host`, `return_to`, `created_at`, `expires_at` (+60 s), `used_at` | The code is `<id>.<HMAC>`. Redeemed by `UPDATE … SET used_at = now() WHERE id = ? AND used_at IS NULL AND expires_at > now()`: one row updated, or MSG141 (AC-001.17/.19) |
| `web_sessions` | `id`, `token_hash` (SHA-256 of the cookie value, unique), `kind` (`WORKSPACE`, `OPERATOR`), `workspace_id`, `account_id`, `operator_email`, `site_host`, `created_at`, `last_activity_at`, `ended_at` | The cookie holds 32 random bytes (base64url); only the hash is stored. "End the session" sets `ended_at` |

`SessionCleanup` (`@Scheduled`, every 15 minutes) deletes states and codes one hour after `expires_at`, and
sessions one day after `ended_at` or after the 8-hour limit (Decided in planning).

Tenant tables, under the platform convention (platform Task 2 base entity, Task 3 row-level security):
- `accounts` (T9): `id`, `workspace_id`, `email` (stored lower-cased, unique per Workspace), `full_name`, `status`
  (`ACTIVE`, `DEACTIVATED`), `first_sign_in_at`, `last_sign_in_at`, `created_at`. The Department column comes with
  the story that needs it (STORY-003 or STORY-007), not here (Decided in planning).
- `account_roles` (T9): `id`, `account_id`, `role` (the five be-foundation Role constants), unique per pair.
- `jira_account_links` (T12): `id`, `workspace_id`, `tracker_account_id` (unique per Workspace), `account_id`,
  `linked_at`. A new link of the same tracker account replaces the row (AC-001.36).

### Ports (Q11)

T8 starts before the platform tasks merge. identity owns two small interfaces in `identity.signin.port`, with test
doubles in the test sources:
- `WorkspaceLookup`: `findBySiteHost(host)` and `findById(id)` → `WorkspaceRef(id, siteHost, googleDomain, status)`.
  Its adapter reads the Workspace registry (platform Task 4, KAN-98) and the Tenant Resolver (Task 5, KAN-99).
- `TenantScope`: `currentWorkspaceId()` and `runIn(workspaceId, supplier)`. Its adapter calls `TenantContext` and
  `runIn` (platform Task 1, KAN-99).

The adapters (`PlatformWorkspaceLookup`, `PlatformTenantScope`) are written in the first identity task that starts
after those platform tasks are on `main`; T9 at the latest, because `accounts` needs the base entity (Task 2,
KAN-98). Until then T8's behaviour is shown by its tests, not on the local stack.

T8 does not wait for be-foundation `problem-errors`: its failures are the minimal `login.` page (HTML) and
redirects, and the 404 of AC-001.5 is the framework's 404, which becomes a ProblemDetail once `problem-errors`
merges (the test asserts only the status). T10 waits for `roles-security` (`TSolveUser`, the chain it plugs into,
the ProblemDetail 401 bodies).

### Libraries (Q9)

- Add `org.springframework.security:spring-security-oauth2-jose` (version from the Spring Boot BOM). `IdTokenVerifier`
  wraps `NimbusJwtDecoder.withJwkSetUri(<Google JWKS>)` with validators for the issuer (`https://accounts.google.com`
  or `accounts.google.com`), the audience (`GOOGLE_CLIENT_ID`) and the expiry; nonce and `email_verified` are
  checked on the decoded claims (AC-001.9). Only RS256 is accepted.
- The code exchange is a `RestClient` form POST to Google's token endpoint (`code`, `code_verifier`, `client_id`,
  `client_secret`, `redirect_uri`, `grant_type=authorization_code`) with a 5 s connect and 10 s read timeout
  (Decided in planning); a non-2xx answer, a timeout or a missing `id_token` is MSG06.
- Google's endpoint URLs (authorization, token, JWKS, issuer) are Spring properties `tsolve.oidc.google.*` with
  Google's published values in `application.yml`, so tests can point them at the fake issuer. They are neither our
  hosts nor secrets, so NFR 4 does not apply to them (Decided in planning).
- No `spring-boot-starter-oauth2-client` and no Google client library.

### Configuration (NFR 4, Q7)

All from the environment with no default: the application fails at startup when one is missing or invalid. Each is
listed in `.env.example` with a local value.

| Name | Meaning | Local dev |
|------|---------|-----------|
| `TSOLVE_LOGIN_ORIGIN` | the central sign-in origin (`https://login.tsolve.io.vn` in production) | `http://login.localhost:<port>` |
| `TSOLVE_ADMIN_ORIGIN` | the Platform Console origin (`https://admin.tsolve.io.vn` in production) | `http://admin.localhost:<port>` |
| `TSOLVE_SIGNING_KEY` | HMAC-SHA256 key for state values and hand-off codes, at least 32 bytes (base64) | a random local key |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | the OAuth client | a developer's own client, or the fake issuer |
| `PLATFORM_OPERATOR_EMAILS` | the comma-separated allow-list; entries trimmed, compared ignoring case | a developer's email |

The redirect URI is always `TSOLVE_LOGIN_ORIGIN` + `/api/v1/auth/callback`. AC-001.21, .28, .29, .30 and .31 name the
configured Platform Console origin (`TSOLVE_ADMIN_ORIGIN`); the code builds those URLs from it (Q7, Hiếu, 2026-10-28).

### Sign-in and session rules (how the criteria are met)

- Host check (`AuthHosts`): the request host (after the proxy's forwarded headers) is compared with the hosts of
  `TSOLVE_LOGIN_ORIGIN` and `TSOLVE_ADMIN_ORIGIN`; any other host is a Workspace site, resolved by
  `WorkspaceLookup`. `authorize` and `callback` on a host other than `login.` → 404 (AC-001.5).
- `returnTo` (AC-001.3): kept only if it starts with exactly one `/` and holds no `\`, no scheme, and no ASCII control
  or whitespace character (the rule of fe-foundation AC-012.2), and is at most 2,048 characters; else `/` (Decided
  in planning).
- Callback order (AC-001.14): state → Google `error`, code exchange and ID token → Workspace Active → `hd` →
  Account exists → Account Active. The state is consumed first (single use), whatever the outcome. The Account
  lookup and update run inside `TenantScope.runIn(state.workspaceId)` (AC-001.15).
- Per request (`WebSessionFilter`, after the platform's Tenant Resolver): on `login.` or `api.` → no session
  (AC-001.22); then a host or kind mismatch → 401 MSG123 without ending the session (AC-001.20/.21/.32); then the
  8 h / 60 min limits → end + 401 MSG07 (AC-001.23); then the Account re-read → Deactivated: end + 401 MSG10
  (AC-001.27), else a `TSolveUser` with the current Roles (AC-001.26). A request without `X-TSolve-Background: 1`
  updates `last_activity_at` (AC-001.24/.25). The order follows from AC-001.20, which keeps a mismatched session
  alive (Decided in planning). Time comes from an injected `Clock`, so tests move it.
- Operator sessions (T11): the principal is an `OperatorPrincipal(email)` with no authorities and none of the five
  Roles; if be-foundation `roles-security` defines an operator principal, T11 uses that one instead (Decided in
  planning). The allow-list is checked again at every request (AC-001.33); a change of the list takes effect after a
  restart.
- The minimal `login.` page (AC-001.39): a fixed HTML string with the escaped Report3 text of one message, no link
  and no script, sent with `Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'` and
  `Cache-Control: no-store`. The texts (MSG06, MSG09, MSG10, MSG75, MSG121, MSG122, MSG141) are copied from the
  Report3 catalog into `LoginPage`; its test asserts each exact text.
- Nothing logs a state value, hand-off code, authorization code, ID token or cookie value (NFR 3).

### Test approach (Q10)

- CI never calls Google. A fake issuer: WireMock (`org.wiremock:wiremock-standalone`, test scope) serves the token
  endpoint and a JWKS made from a test RSA key (nimbus `RSAKeyGenerator`); tests sign their own ID tokens to cover
  every AC-001.9 failure, plus an unreachable token endpoint.
- Controller tests use MockMvc with the `Host` set per site (`acme.localhost`, `beta.localhost`, `login.localhost`,
  `admin.localhost`). The two-Workspace tests (NFR 5) use two Workspaces with Accounts of the same Roles:
  AC-001.7 and .15 (T9), AC-001.17 and .20 (T10), AC-001.32 (T11).
- Repository and migration tests run against Postgres with the database test setup of platform Task 2.
- JUnit names end with the AC id (`…_AC_001_6`); Vitest `it` names start with it.
- The real Google client is used on staging only.

**Open item for the staging demo (not a spec question):** the Google Workspace domain whose `hd` the staging
Workspace uses, and the Google Cloud OAuth client (with the redirect URI of staging's `login.`), are not chosen
yet. Hiếu chooses them before the staging demo of T9/T10; until then demos run on the fake issuer or a developer's
own client.

### Key decisions (STORY-001)

| Decision | Choice | Alternatives considered | Rationale |
|----------|--------|------------------------|-----------|
| Scope (D1) | STORY-001 only | STORY-001..003 together | One story per plan; STORY-002/003 later |
| Cookie (Q2) | `__Host-TSOLVE_SESSION`, host-only (AC-001.18 reworded) | Keep `TSOLVE_SESSION`; add an Origin-check AC | The browser itself refuses a Domain attribute or another path; CSRF (be-foundation AC-002.11/13) covers cross-site mutations |
| After sign-in (Q3) | `returnTo` if given, else `/` → the role's landing page (fe-foundation AC-003.1) | Always the role's page | Deep links from Jira and email keep working |
| Login codes (Q4) | AC-001.42 reworded to fe-foundation's list; MSG120 is the full page | Add MSG07/MSG120 to `useSignInMessage` | Agrees with AC-012.3/.4 and AC-011.4; no change in another module |
| Mock mode (Q5) | Dev-only mock sign-in page at `/dev/sign-in` | Vite middleware; no click-through | Hiếu can try every outcome from the button; Vitest can test it |
| Contract (Q6) | All four redirect endpoints in the YAML, tag `session` | Only the web app's calls; an orval filter | One contract for the backend team |
| Origins (Q7) | `TSOLVE_LOGIN_ORIGIN`, `TSOLVE_ADMIN_ORIGIN`, no default | Production-only wording | NFR 4; local dev and tests use other origins |
| Stores (Q8) | Own Postgres tables in the shared schema, single-use `used_at`, scheduled cleanup | Spring Session JDBC; in-memory | Survives restarts and a second instance; the background-header and re-read rules are custom anyway |
| OIDC (Q9) | Own controllers + `spring-security-oauth2-jose` + `RestClient` | `oauth2Login`; Google's client library | Full control of the signed single-use state and the hand-off; every check explicit and testable |
| Tests (Q10) | Fake issuer (WireMock + test JWKS) in CI; real Google on staging only | `mock-oauth2-server` in compose; manual checks | Every failure path in CI with no Docker extra |
| Start (Q11) | T8 now against the `WorkspaceLookup` / `TenantScope` ports with doubles | Wait for platform; identity builds be-foundation pieces | `login.` has no Workspace, so the state core can be built first |
| PRs (Q12) | One PR per task (T11 + T12 together if small); Jira key per task in tasks.md | Three PRs; one PR per Jira key | Vertical slices first; reviews stay small |
| Layout (Q13) | A centred antd card from the shared kit, SRS 1.1.1 wording | A Figma frame | No frame exists |
| Signing key | `TSOLVE_SIGNING_KEY`, HMAC-SHA256, no default | Asymmetric keys; one key per use | Only the server makes and checks the values; one secret is enough |
| Exception to backend AGENTS.md rules 1, 3, 4 (Hiếu, 2026-10-29) | `identity.signin` keeps the approved shape: a concrete `SignInStateService` (no interface), the `SignInState` entity used by the controllers, a flat `signin` package | Interface plus implementation, controller-facing DTOs, `controller/service/dto` sub-packages (the AGENTS.md rules) | The sign-in endpoints are redirects with no request or response DTOs, and the service has one implementation; the approved design and the QA tests already use this shape. Scope: `identity.signin` only |

### Edge cases and risks (STORY-001)

Dimensions analyzed: user types, input extremes, timing, scale, state transitions, environment, error cascades,
authorization, data integrity, integration, compliance. Skipped: business logic (sign-in has no amounts or quotas).

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 19 | STORY-001 | Input extremes | `returnTo` = `//evil.example`, `/\evil`, `https://evil`, a tab, 3,000 characters | High | `/` is kept (AC-001.3) | Task 8 |
| 20 | STORY-001 | Authorization | State value edited, unsigned, expired (10 min at Google) or replayed after use | Critical | 400 MSG141 page, no session (AC-001.6) | Tasks 8, 9 |
| 21 | STORY-001 | Authorization | Callback with `?workspace=beta`, a spoofed `Host` / `X-Forwarded-Host`, or another site's cookie | Critical | Ignored; the target comes only from the state (AC-001.7) | Task 9 |
| 22 | STORY-001 | Integration | ID token with a wrong `aud` or `iss`, expired, a nonce mismatch, `email_verified` false, `alg: none`, a key not in the JWKS | Critical | MSG06, no session (AC-001.9) | Task 9 |
| 23 | STORY-001 | Error cascades | Google's token endpoint or JWKS down or slow | High | MSG06 after the timeout, no 500 (AC-001.9) | Task 9 |
| 24 | STORY-001 | Data integrity | The same email has an Account in both Workspaces | Critical | The callback for A finds and updates only A's Account (AC-001.15) | Task 9 |
| 25 | STORY-001 | Input extremes | Email `Lan.Tran@Company.Example`, `hd` `ACME.com` | High | Matched ignoring case (AC-001.11/.12) | Task 9 |
| 26 | STORY-001 | Timing | Two callbacks with the same state (back button, a repeated redirect) | High | One goes on; the other gets MSG141 (AC-001.2/.6) | Task 9 |
| 27 | STORY-001 | Timing | A hand-off code redeemed twice (two tabs, a reload of `complete`) | Critical | One session; the second gets `/login?error=MSG141` (AC-001.17/.19) | Task 10 |
| 28 | STORY-001 | Authorization | A hand-off code of Workspace A presented on site B or on `admin.` | Critical | MSG141 on that site, no session (AC-001.17/.19) | Task 10 |
| 29 | STORY-001 | Authorization | A's cookie copied to B or to `admin.`; an Operator cookie on a Workspace site | Critical | 401 MSG123, endpoint not run, A's session not ended (AC-001.20/.21/.32) | Tasks 10, 11 |
| 30 | STORY-001 | Authorization | Any session cookie sent to `login.` or `api.` | High | No session (AC-001.22) | Task 10 |
| 31 | STORY-001 | State transitions | Idle 59 vs 61 minutes; only background polling for 2 h; 8 h with activity | High | MSG07 just past each limit (AC-001.23–.25), with the injected `Clock` | Task 10 |
| 32 | STORY-001 | State transitions | Account deactivated or its Roles changed during a session | High | Next request: MSG10, or the new Roles (AC-001.26/.27) | Task 10 |
| 33 | STORY-001 | Compliance | A state value, code, ID token or cookie value in a log or in browser storage | High | Never; session ids stored only as hashes (NFR 3) | Task 10 |
| 34 | STORY-001 | Environment | A `__Host-` + `Secure` cookie on `http://acme.localhost` in local dev | Medium | Check in T10's local run which browsers accept it; note the result in `.env.example` | Task 10 |
| 35 | STORY-001 | User types | Allow-list entry ` Ops@X.com `, or an email removed from the list | High | Trimmed and compared ignoring case (AC-001.29/.30); removed → MSG135 at the next request (AC-001.33) | Task 11 |
| 36 | STORY-001 | User types | An Operator who also has a Workspace Account | High | Each site gets only its own kind of session (AC-001.29/.32) | Task 11 |
| 37 | STORY-001 | Authorization | Jira link: a tracker account in a callback parameter; a relink; a suspended Workspace | Critical | Parameter ignored (AC-001.38); link replaced (AC-001.36); MSG121 (AC-001.37) | Task 12 |
| 38 | STORY-001 | Environment | A required env value missing, or `TSOLVE_SIGNING_KEY` shorter than 32 bytes | High | Startup fails and names the value (NFR 4) | Task 8 |
| 39 | STORY-001 | Input extremes | `/login?error=MSG122&google_domain=<script>`; `error=MSG07`; `error=MSG120` | High | MSG122 dropped and text never rendered as HTML; empty message area (AC-001.42/.43) | Task 7 |
| 40 | STORY-001 | Timing | A double click on "Sign in with Google" | Low | The button is pending after the first click; one navigation | Task 7 |
| 41 | STORY-001 | Scale | Rows of old states, codes and ended sessions pile up | Medium | `SessionCleanup` removes them | Task 10 |
| 42 | STORY-001 | Error cascades | The database is unavailable during the callback or `complete` | Medium | No session (one transaction); the browser gets be-foundation's 500 ProblemDetail (accepted: generic 500 (Hiếu, 2026-10-28)) | Tasks 9, 10 |
| 43 | STORY-001 | Authorization | Q1: a path outside the four redirect paths (for example `/api/v1/anything`) | High | Anonymous: 401, no form login, no HTTP Basic; signed in: reaches it (the lowest-order chain `anyRequest().authenticated()`) | Task 8 |
| 44 | STORY-001 | Input extremes | Q2: `GET /api/v1/auth/sign-in` on a host that is no Workspace site and not the Platform Console origin (an unknown host, `login.`, `api.`) | High | 404, status only, no state created (same rule as AC-001.5) | Task 8 |
| 45 | STORY-001 | Authorization | Login CSRF: an attacker starts a sign-in and gets a callback URL (state + code) for the attacker's own Google account, then makes the victim's browser open it | High | Open item, spec change pending (owner Hiếu, before Task 9): a short-lived `__Host-` cookie bound to the state value is set at sign-in start and checked at the callback; a callback without the matching cookie is refused. Not yet an AC; `spec.md` is not edited here | Task 9 |

Q1-Q5 (Hiếu, 2026-10-29): Q1 `AuthSecurityConfig` also adds a second, lowest-order `SecurityFilterChain`
(`anyRequest().authenticated()`, 401, no form login, no HTTP Basic); be-foundation `roles-security` replaces it later.
Q2 sign-in start on a non-Workspace, non-Console host answers 404 and creates no state. Q3 `SignInStateRepositoryIT`
(Testcontainers, needs Docker) covers the check constraint and `markUsed`. Q4 a developer step adds empty skeletons
of the Task 8 classes before the QA tests. Q5 the developer may edit ticket's `ImportConfirmIT` and
`ImportControllerTest` test properties only.

Task 8 review (Hiếu, 2026-10-29): the review is `06-evaluation/identity/task-8-sign-in-start-review.md`. The AGENTS.md
exception is recorded in Key decisions above. Login CSRF is row 45: a Change Mode spec edit (owner Hiếu) must add the
cookie criteria and be approved before Task 9. Task 9 also takes the two other review findings (see tasks.md Task 9).

Open questions for the spec owner: login CSRF cookie (row 45), spec change pending before Task 9. Row 42 is accepted (C3, Hiếu, 2026-10-28): a database failure during a
sign-in redirect keeps be-foundation's generic 500.
## Task 4 contract proposal — KAN-110
`PUT /api/v1/users/{userId}` (`updateUserRoles`) accepts generated UpdateUserRolesRequest: roles, nullable UUID Department, UUID Space ids, optional Project/Space handovers and `confirmTicketResolverRemoval`. It returns confirmed UserDetail or400/401/403/404/409 Problem. UserDetail adds `ticketResolverProjectIds` (UUID[]) for the memberships that MSG78 actually removes; preserve historical references separately from current membership and management.

Reuse D3 for only removed obligations: managed Active Projects when PM is removed or Department changes; managed Spaces omitted from the requested set or when KM is removed. Project replacement eligibility uses the Project's original Department. Validate Employee retention, PM Department, required KM Spaces, occupied Spaces, actor/Workspace, self Admin removal MSG113 and last Admin MSG56 before committing any role, assignment, handover or membership change. Removing actual TR memberships requires MSG78 confirmation. Retained Project management is not removed by dropping TR. Failed requests preserve user edits and all server state; success invalidates users and session queries. No new shared UI, dependency or production backend code.

## KAN-111 preparation — 2026-10-29 (draft; no code authorized)

Spec: `artifacts/03-modules/identity/spec.md`, STORY-008 / AC-008.1–008.9.

### Decisions and scope

- Chosen: included Project handover from KAN-109 and KAN-110, with their existing popup and mutations. Alternative: a standalone page or endpoint. Rationale: Jira KAN-111 explicitly names the two including use cases; the separate workflow previously suggested in chat is not a requirement.
- Chosen by the user: wait until both issues merge into main, then update `feat/KAN-111-identity-project-handover` from main. Alternative: import their branch now. No dependency branch is imported. On 2026-10-30, KAN-109 is on main at 19e7c51; the user requested continuing now. Work on its caller first; KAN-110 caller verification and final completion wait for its merge.
- Chosen: existing generated hooks, mutable MSW and shared UI. No new libraries, routes, API schemas, production authorization, audit persistence or Review Queue integration.
- Keep KAN-112 separate. Existing Space handover required by the including flows remains unchanged.

### Dependency evidence and smallest implementation

The inspected main base is `7296269`. Historical source inspected on `feat/KAN-110-identity-user-roles` includes `HandOverResponsibilitiesModal.tsx`, `UserStatusActions.tsx`, `useRoleUpdateFlow.ts`, `mocks/state.ts` and `mocks/handover.ts`, under `frontend/src/features/users/`. These files already contain the popup, caller flows, eligibility filtering and atomic mock transfer. Its tests cover same-Department recipients, transfers, cancellation and reactivation. These are reuse candidates, not evidence that the files exist on main or that STORY-008 has passed.

After both dependencies merge, read the actual merged code and map each AC-008 criterion to evidence. Add focused tests or fixes only for uncovered behavior. If merged behavior already meets all criteria, deliver verification and traceability without adding another implementation. Existing AC-006/007 test names remain intact; new focused tests name the AC-008 criteria they prove.

### Contract and files

Reuse the merged user-detail and handover-options contracts, `projectHandovers` payload and status/role mutation contracts. No new endpoint or manual edit to generated code. Expected inspection/edit scope after the dependency gate:

- `frontend/src/features/users/components/HandOverResponsibilitiesModal.tsx`
- `frontend/src/features/users/components/UserStatusActions.tsx`
- `frontend/src/features/users/hooks/useRoleUpdateFlow.ts`
- `frontend/src/features/users/hooks/useUserMutations.ts`
- `frontend/src/features/users/mocks/{state,handover,status-handlers,roles-handler}.ts`
- Existing tests under `frontend/src/features/users/pages/` and `mocks/`; a focused `components/HandOverResponsibilitiesModal.test.tsx` only if necessary.
- `docs/bug-log.md` only for defects established during implementation; each fix gets a bug-id regression test.

### Edge cases and verification

| Risk | Criteria | Evidence required after dependencies merge |
|------|----------|-------------------------------------------|
| Cross-Department, inactive, self or other-Workspace recipient | AC-008.3–008.5 | Lookup and mock rejection tests; an ineligible choice changes nothing |
| Department changed before handover eligibility is calculated | AC-008.2 | Recipients come from the Project's existing Department |
| Multiple Active Projects, Removed Projects, Projects owned by someone else | AC-008.4, AC-008.7 | Every affected Project transfers once; other records and tracker ids remain unchanged |
| Cancel or failed mutation | AC-008.6 | No partial transfer/account update; failed request preserves selections for retry |
| Failed/pending lookup and double click | AC-008.8 | Disabled completion, visible loading/error and retry; one mutation only |
| Reactivation | AC-008.9 | Recipient keeps the transferred Projects |

Builder check (Light UI scope): no new technology, screen text or contract is selected. Existing caller, lookup, payload, failure and cache conventions are reused. Production enforcement is explicitly excluded. Implementation eligibility remains dependent on main and spec approval.

Validation after implementation: `sdd.ts trace identity STORY-008`, targeted tests, all frontend verification commands, deterministic API generation, and browser inspection at 1366 × 768. Until then these are planned checks, not passing results. Commit only after the user reviews the feature results; do not start KAN-112 before that checkpoint.

## KAN-112 — Space handover — 2026-10-31

Spec: `artifacts/03-modules/identity/spec.md`, STORY-009 / AC-009.1–AC-009.9. Approved version confirmed by `sdd.ts diff identity` on 2026-10-31. Branch: `feat/KAN-112-identity-space-handover`, based on main `4cf0594`; the existing KAN-109/110/111 implementation is present.

### Decisions and implementation boundaries

- Chosen by the user: frontend with mutable MSW and a proposed OpenAPI contract awaiting backend integration. Alternative: backend plus frontend. Rationale: the selected delivery scope; mock checks are not production security or persistence.
- Chosen: reuse `HandOverResponsibilitiesModal`, `UserStatusActions`, `useRoleUpdateFlow`, existing generated hooks and atomic mock helpers. Alternative: a new Space page or endpoint. Rationale: UC-09 is included by UC-06 and UC-07, and both callers already support Spaces.
- Chosen: eligibility is another Active Knowledge Manager in the current Workspace, regardless of Department; revalidate it when submitting. Alternative: reuse Project's same-Department restriction. Rationale: the approved Space criteria and existing lookup distinguish the two rules.
- Chosen: retain the existing `SpaceRef`, `SpaceHandover` and `UserHandoverOptions` shapes. Expand OpenAPI descriptions to explain Space eligibility, full affected-set validation, failure atomicity, caller-specific scope and history retention. Alternative: add duplicate models or a mutation endpoint. Rationale: current payloads already express the required choices. Generate all API output with `npm run generate:api`; never edit generated files manually.
- Chosen: test existing behavior first and change runtime code only for a reproduced gap against AC-009. Alternative: rewrite working code to create a larger feature diff. Rationale: existing helpers validate all choices before synchronous writes; meaningful evidence is the missing deliverable.

### Data flow and contract proposal

UC-06 gets every managed Space from `GET /api/v1/users/{userId}/handover-options`, then sends `spaceHandovers` alongside deactivation to `PUT /api/v1/users/{userId}/status`. UC-07 uses the same lookup but displays only removed Spaces, and sends their handovers together with requested roles, Department and retained Space ids to `PUT /api/v1/users/{userId}`. Unchanged Spaces stay assigned to the original user. Both mutations return confirmed UserDetail and use existing user/session query invalidation.

Each `SpaceHandover` remains `{ spaceId: UUID, recipientId: UUID }`. Each affected Space must appear exactly once; the same eligible recipient may receive several Spaces. Missing, duplicate, unrelated or ineligible choices use the existing 400 MSG01 validation; unavailable recipients use 409 MSG118. Existing malformed-payload, authentication, permission and missing-user errors remain unchanged. The Workspace comes from the current session/mock context, never a caller-selected field.

Mock validation runs before any account, Project or Space writes. `store.spaceHistory` retains the Space id, previous manager and recipient; account reactivation does not reverse ownership. Contract descriptions specify the required behavior for future backend integration, including a transaction spanning all accompanying changes, without claiming that backend transaction is implemented here.

### Edge Cases & Risks — STORY-009

Dimensions analyzed: user types, input extremes, timing, scale, state transitions, environment, error cascades, authorization, data integrity, integration, compliance and business logic. Compliance is limited to preserving existing mock references; production audit retention and deletion policies are outside this slice.

| # | Dimensions | Scenario | Severity | Expected behavior / criteria | Test task |
|---|------------|----------|----------|------------------------------|-----------|
| S1 | User types, authorization | Lookup includes self, inactive, wrong-role or foreign-Workspace candidates; a different-Department KM exists | High | Exclude the first four; allow the other-Department KM; reject forged choices on both mutations (AC-009.3–.4) | Task 14 |
| S2 | Input extremes, scale, business logic | Zero, one or several affected Spaces; incomplete, repeated or unrelated Space ids; one recipient receives several Spaces | High | Require exactly the affected set; allow one eligible recipient for multiple Spaces; preserve unrelated assignments (AC-009.1–.4, .7) | Task 14 |
| S3 | Timing, error cascades | Recipient loses KM or becomes inactive after options load; one of several choices becomes invalid | High | Revalidate on submit; reject without partial account, Space or Project writes (AC-009.4, .6–.7) | Task 14 |
| S4 | State transitions, data integrity | Remove KM, remove only some Spaces, cancel, then retry after failure | High | Transfer only removed obligations; cancellation preserves confirmed data and failure preserves choices (AC-009.2, .6) | Task 14 |
| S5 | Timing, environment | Pending/refetching/failed lookup, offline response, rapid double submission | High | Disable choices/completion while lookup is unusable; retry state and a single pending mutation (AC-009.8) | Task 14 |
| S6 | Business logic, error cascades | No eligible recipient for one Space | High | MSG118; neither caller can complete; mock writes nothing (AC-009.5) | Task 14 |
| S7 | Data integrity, compliance | Success followed by reactivation or restoring KM with other valid Spaces | High | Preserve ids/history; transferred Spaces stay with recipients; session/detail show confirmed assignments (AC-009.7–.9) | Task 14 |
| S8 | Integration, environment | API generated code drifts; popup keyboard use or long Space labels | Medium | Deterministic generation; shared accessible form labels and browser inspection at 1366 × 768 (AC-009.1–.2, .8) | Task 14 |

Open questions for the spec owner: none.

### Risk Review — 2026-10-31 — Verdict: CAUTION

Independent persona findings:

- Architect: the combined caller mutation fits D3 and the existing feature boundary; duplicate endpoints would add coupling without behavior.
- Security: lookup filtering alone is insufficient; submit-time Workspace, role and status checks must be tested. MSW cannot establish production authorization.
- Performance: reuse one options query and one mutation; no new dependency, polling, bundle subsystem or per-Space request is needed.
- User: unavailable recipients need MSG118; cancellation and retry must preserve confirmed data and entered selections respectively. Verify both caller button labels and keyboard access.
- Devil's advocate: runtime support already exists. Deliver contract clarity and missing evidence; only modify runtime behavior when a failing criterion demonstrates a gap.

Agreements: reuse existing flow and payloads; test full affected-set validation and atomicity; keep production backend integration explicitly deferred. No conflict requires a new product decision. Correctness of mixed Project/Space writes takes priority over minimizing the test matrix.

| Risk | Severity | Mitigation | Task |
|------|----------|------------|------|
| Stale or forged choices cause partial reassignment | High | Negative contract cases for each caller and mixed Project/Space state snapshots | Task 14 |
| Mock success is mistaken for deployed security/persistence | High | Mark OpenAPI behavior as a proposal; report backend integration as pending | Task 14 |

### Proposed Task 14 and iteration placement

Add one feature, `space-handover`, to the existing identity iteration 1 after Task 13. Story: STORY-009; criteria: AC-009.1–AC-009.9. Reuse merged dependencies and add focused contract/UI cases for S1–S8, OpenAPI descriptions plus generated output, and only demonstrated runtime fixes. Candidate files are under `frontend/src/features/users/{components,pages,mocks}/`, `frontend/openapi/tsolve-api.yaml`, generated API output, and `docs/bug-log.md` for reproduced bugs. No dependency, route or production backend change.

Verification: `sdd.ts trace identity STORY-009`; focused users tests; frontend format, lint, typecheck, full tests, build and deterministic API generation; browser-check UC-06 and UC-07 at 1366 × 768. Preserve all AC-006/007/008 behavior. Write the task session log and test/review reports with actual results. Propose one feature commit only after verification and obtain user agreement before committing; push only if requested.

Builder check: proposed Task 14 supplies the callers, models, eligibility rule, payload scope, errors, state invariants, test targets and exclusions. There is no unresolved build decision. The user approved the design and iteration placement on 2026-10-31; Task 14 is appended to iteration 1. Implementation and verification are recorded in its session and evaluation reports.
