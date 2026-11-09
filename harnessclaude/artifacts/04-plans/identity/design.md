---
module: identity
date: 2026-10-25
---

# Design: identity

> **Table and column names (KAN-235 Q1 = A, then Hiếu 04/11): the Database Tables PDF wins** (kept outside the repo; its Markdown companion `artifacts/00-baseline/T-Solve_Database_Tables.md` is the source of truth for the team). Primary keys are `id`, not `<table>_id`: `app_user.id` and `hand_off_code.id`. The PDF's Catalog database is what this plan calls the Registry, and its table is `workspace_email` (singular). `app_user.full_name` is `VARCHAR(100)`. `app_user` has two columns the PDF lacks, `first_sign_in_at` and `last_sign_in_at` (AC-001.16), and `hand_off_code` is not in the PDF; both are listed in the companion under "Additions not in the PDF (decided by Hiếu, 2026-11-04)". `user_role` (PDF: `id`, `user_id`, `role`, `assigned_at`) and `AccountRole` are **not** part of Task 9b: they come in Task 10, where Roles are first read. The Java names stay `Account`, `AccountRole`, `HandOffCode`. Task 9b only reads Accounts and writes no role row. The text below says `app_user` and `hand_off_code` for Task 9b. `sign_in_states` (Tasks 8 and 9) and `jira_account_links` (Task 12, with the column `app_user.linked_jira_account_id`) are settled by their own tasks.

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

> **Superseded in part, 2026-11-05 (KAN-275).** The sign-in mechanism below (signed state values, `authorize`, hand-off
> codes, the `login.` page, the sign-in and start cookies, `sign_in_states`, `hand_off_code`, `TSOLVE_SIGNING_KEY`, the
> Google OIDC code) is replaced by Keycloak: see "Keycloak re-plan: STORY-001 sign-in and STORY-002 logout" further
> down. Sessions in Redis, `WebSessionFilter`, tenant isolation, `app_user`, `user_role` and the Platform Console
> stay as described here.

This section covers identity STORY-001 only (decision D1); STORY-002 and STORY-003 get their own plan. It builds
the backend that the sections above leave out (the real sign-in, sessions and the `app_user` store) and the Login
screen: Tasks 7–12 of `iteration-1/tasks.md`. KAN-104's leftovers (D2) map onto STORY-001: the cookie valid only on
the company's own site is AC-001.18/.20/.21/.22/.32; the role's default page after sign-in is AC-001.18 (`returnTo`
or `/`) plus fe-foundation AC-003.1. The Jira line "Callback chỉ về `login.`" is AC-001.4/.5 (Q1).

**HLA change, 2026-11-03 (KAN-231 step 2, Hiếu: Q1–Q4 = A).** The multi-tenant HLA (drawio, 29/10) changes four
things
in this plan; each is marked "HLA" where it lands: (Q1) the Platform Console on `admin.` stays (Task 11); (Q2) sign-in
states and hand-off codes live in the Platform / Tenant Registry database, moved there by Task 14 while Tasks 8–9 keep
their tables in the one changelog until then; (Q3) sessions live in Redis, not in a Postgres `web_sessions` table
(Task 10); (Q4) one database per company, so the account tables have no `workspace_id` and platform-wide email
uniqueness goes through the registry (Tasks 9b and 14). Task 9 (KAN-231) is unchanged.

### Flow

```text
Workspace site (acme.)              login. (TSOLVE_LOGIN_ORIGIN)                  Google
 Login "Sign in with Google"
  └ startSignIn(returnTo) ─► GET /api/v1/auth/sign-in ─ state row (Registry DB) ─► 302 ─► GET /api/v1/auth/authorize?state
                                         ├ mark authorized (once) · Set-Cookie __Host-TSOLVE_SIGNIN
                                         └ 302 (scope, state, nonce, PKCE S256, hd) ─► accounts.google.com
                                   GET /api/v1/auth/callback?code&state ◄──────────────────── 302
                                    ├ consume state · sign-in cookie hash · state's Workspace (exists, Google domain)
                                    ├ code exchange (RestClient) · ID token (NimbusJwtDecoder)
                                    ├ checks in the AC-001.14 order, inside runIn(state.workspace)
                                    ├ every answer: Set-Cookie __Host-TSOLVE_SIGNIN Max-Age=0
                                    └ hand-off code row (Registry DB) ─► 302 acme./api/v1/auth/complete?code
 GET /api/v1/auth/complete?code ─ consume code · session key in Redis · Set-Cookie __Host-TSOLVE_SESSION ─► 302 returnTo or /
 every later request: WebSessionFilter ─ host/kind check · 8 h / 60 min (session from Redis) · re-read Account (tenant DB) ─► TSolveUser
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
| `vn.tsolve.identity.signin` | `SignInStateService` (create, check and consume state values; `createJiraLinkUrl` for `jira-app`), `SignInStartController` (`/sign-in` on a Workspace site or `admin.`), `AuthorizeController` and `CallbackController` (`login.` only), `GoogleOidcClient` (authorization URL, code exchange), `IdTokenVerifier`, `HandOffCodeService` (issue), `LoginPage` (the minimal `login.` HTML page), `AuthHosts` (which configured origin a request is on), `SignedToken` (HMAC-SHA256 sign and verify), `SignInCookie` (makes, hashes and expires `__Host-TSOLVE_SIGNIN`), `IdentityClock` (identity's time, KAN-231 Q9) | T8, T9, T9b |
| `vn.tsolve.identity.signin.port` | `WorkspaceLookup` and `TenantScope` (Q11), `LocalTenantScope` (interim production `TenantScope`, KAN-231 Q10; one database until Task 14, HLA Q4), see "Ports" | T8, T9b |
| `vn.tsolve.identity.account` | `AccountSignInService` (T9b: the Account lookup and the sign-in update, inside `TenantScope.runIn`), `Account` (table `app_user`; no `workspace_id`: one database per company, HLA Q4; no base entity yet), `AccountStatus`, `AccountRepository` (`findByEmail` on the lower-cased column; reached only inside `TenantScope.runIn`) | T9b (`AccountRole`, table `user_role`, comes in T10) |
| `vn.tsolve.identity.session` | `CompleteController` (`/complete`, redeems hand-off codes), `WebSession`, `WebSessionStore` (Redis, HLA Q3; replaces `WebSessionRepository`), `WebSessionFilter` (the per-request rules), `SessionCleanup` is not built; the `SessionEnded` `EXPIRED` publisher is planned with KAN-106 (see Data model), `OperatorAllowList` | T10, T11 |
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
- `GET /api/v1/auth/authorize?state`, with a path-level `servers` entry for `login.`: 302 with a `Set-Cookie`
  header for `__Host-TSOLVE_SIGNIN` (AC-001.44); 400 (`text/html`, the MSG141 page); 404;
- `GET /api/v1/auth/callback?code&state&error`, with the same `servers` entry and the `__Host-TSOLVE_SIGNIN` cookie
  parameter (`in: cookie`): 302 to `complete` or to `/login?error=…&google_domain=…` (the `error` values MSG06, MSG09,
  MSG10, MSG120, MSG122, MSG135, MSG141 listed in the description); 200 and 400 `text/html` (the minimal page); 404;
  every answer carries the `Set-Cookie` header that expires the cookie (AC-001.47). Added in T9 (KAN-231 Q1–Q3);
- `GET /api/v1/auth/complete?code`: 302 with `Set-Cookie: __Host-TSOLVE_SESSION`; failure 302
  `/login?error=MSG141`.

orval generates unused functions for them; `generate:api` must leave no diff.

### Data model (Q8, HLA Q2–Q4)

The HLA has one PostgreSQL database per company and a Platform / Tenant Registry database. identity's data goes to
three places (HLA, 2026-11-03):

- **Platform / Tenant Registry database** (non-tenant; the catalog changelog): `sign_in_states` and `hand_off_code`
  (HLA Q2), because both are read on `login.` or at `complete` with no tenant bound, and with "no default fallback"
  there is no tenant database to reach. **Interim:** Tasks 8 and 9 keep `sign_in_states` in today's only changelog
  (`db.changelog-master.xml`, one database until `platform` builds the registry and the router), and Task 9's new
  column changeset goes there too; Task 9b's `hand_off_code` changeset likewise. Task 14 moves both tables into the
  registry changelog and gives their repositories the registry datasource.
- **Each company database** (the tenant changelog): `app_user`, `user_role`, `jira_account_links`.
- **Redis** (HLA Q3): the sessions. No `web_sessions` table.

Ids are UUIDs (platform AC-001.8). Liquibase SQL files in `backend/src/main/resources/db/changelog/changes/` with the
next free number, listed in `db.changelog-master.xml` (until Task 14 moves the two registry tables).

| Store | Columns / shape | Rules |
|-------|-----------------|-------|
| `sign_in_states` (Registry DB; interim: the one changelog) | `id`, `target` (`WORKSPACE`, `PLATFORM_CONSOLE`, `JIRA_LINK`), `workspace_id` (null for the Console), `tracker_account_id` (Jira link only), `return_to`, `nonce`, `pkce_verifier`, `created_at`, `expires_at` (+10 min), `used_at`, and from T9 `authorized_at` and `browser_binding_hash` (SHA-256 of the sign-in cookie value, 32 bytes; both null until `authorize`), and from T10 `start_hash` (SHA-256 of the `__Host-TSOLVE_START` cookie value, 32 bytes; set when the Workspace sign-in start creates the state, null for Console and Jira-link states) | A check constraint allows exactly one target shape (AC-001.2), and a second one that `authorized_at` and `browser_binding_hash` are both null or both set. The browser gets `<id>.<HMAC>`; nonce and verifier never leave the server. `authorize` sets both new columns with `UPDATE … SET authorized_at = :now, browser_binding_hash = :hash WHERE id = :id AND authorized_at IS NULL AND used_at IS NULL AND expires_at > :now`, where `:now` is the `IdentityClock` time passed as a parameter, never the database `now()` (state times come from `IdentityClock`, so tests with `Clock.fixed` stay consistent; spec review, KAN-231): one row updated, or the MSG141 page (AC-001.44/.45). The two columns come in a new changeset (`<next>-identity-sign-in-state-binding.sql`); Task 8's `004-identity-sign-in-states.sql` is never edited (Decided in planning, KAN-231). `start_hash` is its own changeset (`<next>-identity-sign-in-state-start-hash.sql`, Task 10; AC-001.51, KAN-232 Q7). Here `workspace_id` names the target Workspace in the registry; it is not a tenant column |
| `hand_off_code` (Registry DB; interim: the one changelog) | `id`, `target` (`WORKSPACE`, `PLATFORM_CONSOLE`), `workspace_id`, `account_id`, `operator_email`, `site_host`, `return_to`, `created_at`, `expires_at` (+60 s), `used_at`, and from T10 `start_hash` (copied from the state at the callback; null for an Operator code) | The code is `<id>.<HMAC>`. Redeemed by `UPDATE … SET used_at = :now WHERE id = :id AND used_at IS NULL AND expires_at > :now`, where `:now` is the `IdentityClock` time passed as a parameter, never the database `now()` (as for `sign_in_states`, so tests with `Clock.fixed` stay consistent; KAN-232 Q6): one row updated, or MSG141 (AC-001.17/.19). The update runs first, whatever happens next (KAN-232 Q5): a code shown on the wrong site is consumed by the update, then the site check fails (MSG141 on that site) and the code is dead on its own site too. AC-001.19 names a code "presented on another site" but not that it is consumed, so this is the design's reading of AC-001.17 ("usable once"), not a widened AC. Then the `__Host-TSOLVE_START` check (AC-001.51): the cookie's SHA-256 against `start_hash` with `MessageDigest.isEqual`; a missing cookie or a mismatch is MSG141, no session, the code stays consumed. `start_hash` is its own changeset (`<next>-identity-hand-off-code-start-hash.sql`, Task 10) |
| Session (Redis, HLA Q3) | Key `tsolve:session:<SHA-256 of the cookie value, hex>`, a hash: `kind` (`WORKSPACE`, `OPERATOR`), `workspace_id`, `account_id`, `operator_email`, `site_host`, `created_at`, `last_activity_at` | The cookie holds 32 random bytes (base64url); only the hash is in the key. The limits of AC-001.23 are checked in the application from `created_at` and `last_activity_at` with `IdentityClock` (so tests use `Clock.fixed`); the key's time to live (the 60-minute idle window, renewed by activity, never past the 8-hour limit) is only a backstop that removes it. "End the session" deletes the key. Sessions are kept in a Redis that is not an eviction cache (HLA: apart from the cache) |

Expiry and `SessionEnded` (AC-002.4, HLA Q3): the key's time to live removes an unused session; the 15-minute bound
for `SessionEnded` `EXPIRED` needs the session's Account id and Workspace id, which a Redis expiry event does not
carry. How `EXPIRED` is produced (keyspace events on a shadow key, or a recurring worker job over an expiry-ordered
index, the JobRunr worker of the HLA) is settled when KAN-106 (STORY-002, AC-002.4) is planned, because Task 10 only stores the session; it must keep the
15-minute bound
and the single event of AC-002.3. `SessionCleanup` (`@Scheduled`, every 15 minutes) goes: N API replicas must not each
run it. Old states and hand-off codes (one hour after `expires_at`) are deleted by a recurring job in the `worker`
process (JobRunr) once the tables are in the registry, and until then by a `@Scheduled` job in the web process
(Decided in planning; the sessions are no longer part of it).

Tenant tables (HLA Q4, Hiếu, 2026-11-03): one database per company, so `app_user`, `user_role` and
`jira_account_links` have **no** `workspace_id` and no foreign key to a Workspace; the Workspace is the database the
tenant context is bound to (`TenantScope.runIn(workspaceId, …)`, the Database Router of `platform` later). This
replaces the interim `workspace_id` columns of KAN-231 Q10. The per-database RLS guard is whatever convention
`platform` defines (no FK; Task 14).
- `app_user` (T9b; Java `Account`): `id`, `email` (stored lower-cased, unique inside the company database; one email in one Workspace
  across the platform goes through the registry's `workspace_email`, GBR-16, GBR-37, D7: a `platform` service that
  identity calls when an Account is added, STORY-005; no index can span the databases), `full_name` (`VARCHAR(100)`), `status`
  (`ACTIVE`, `DEACTIVATED`), `first_sign_in_at`, `last_sign_in_at`, `created_at`. The Department column comes with
  the story that needs it (STORY-003 or STORY-007), not here (Decided in planning).
- `user_role` (T10, not T9b; Java `AccountRole`): `id`, `user_id`, `role` (the four stored Roles; Employee is implicit, as the DDL says), unique per pair, plus `assigned_at` (the PDF). Moved to Task 10, where Roles are first read (Hiếu 04/11); Task 9b only reads Accounts and writes no role row (decided KAN-235 Q1 = A); the Admin role row is written by the `FirstAdminCreator` (STORY-010) and STORY-005.
- `jira_account_links` (T12): `id`, `tracker_account_id` (unique in the company database), `account_id`, `linked_at`.
  A new link of the same tracker account replaces the row (AC-001.36).

### Ports (Q11)

T8 starts before the platform tasks merge. identity owns two small interfaces in `identity.signin.port`, with test
doubles in the test sources:
- `WorkspaceLookup`: `findBySiteHost(host)` and `findById(id)` → `WorkspaceRef(id, siteHost, googleDomain, status)`.
  Its adapter reads the Platform / Tenant Registry database (platform Task 4, KAN-98) and the Tenant Resolver
  (Task 5, KAN-99).
- `TenantScope`: `currentWorkspaceId()` and `runIn(workspaceId, supplier)`. Its adapter calls `TenantContext` and
  `runIn` (platform Task 1, KAN-99).

**`FirstAdminCreator` (STORY-010, KAN-98 Change Mode 2026-11-04; Task 14).** Unlike the two ports above, this one is
declared by `platform` (`vn.tsolve.platform.service.FirstAdminCreator`, platform Task 7) and implemented here, because
`identity` depends on `platform` and not the other way round. Chosen: `identity.account.AccountFirstAdminCreator`, one
`@Service` with `UUID createFirstAdmin(UUID workspaceId, String email)` in a single `@Transactional` method. It checks
`TenantScope.currentWorkspaceId()` equals `workspaceId` (AC-010.3), lower-cases and validates the email (AC-010.4),
refuses an existing Account (AC-010.5), saves the `Account` (ACTIVE, empty full name) and its Admin `AccountRole`
(AC-010.1, AC-010.2) and returns the id only. Alternatives: an event that identity listens to (breaks the all-or-nothing
rule of platform Requirement 4); platform writing the `app_user` rows itself (platform would own identity's tables).
It does not claim the email: `WorkspaceProvisioning` does at step 4 (platform AC-013.19), so a second claim here would
only repeat it (AC-010.6). The invitation job carries the returned id; `worker` reads the Account through
`AccountDirectory` (an Account by id in the caller's Workspace). No status `Invited` is added (platform Q2 = A); the
invitation creates no credential (AC-010.7). Until Task 14 there is no bean: only platform's `ops` and Console
profiles require it (platform AC-013.15). Decided in planning: class name `AccountFirstAdminCreator` and its package
`identity.account` (no new package for one class).

The adapters (`PlatformWorkspaceLookup`, `PlatformTenantScope`) are written in the first identity task that starts
after those platform tasks are on `main`. Until then T8's behaviour is shown by its tests, not on the local stack.

Platform gap (KAN-231 Q10, Hiếu, 2026-10-31; reworded for the HLA, Q4, 2026-11-03): KAN-98 and KAN-99 are still To
Do, so T9 and T9b are built against the ports and test doubles too, like T8:
- Production beans until the adapters exist: `NoWorkspaceLookup` (every lookup finds nothing, so every Workspace
  site answers 404, as today) and, new in T9b, `LocalTenantScope` in `identity.signin.port`: a `ThreadLocal` holding
  the Workspace id, set by `runIn(workspaceId, supplier)` and cleared in a `finally`; `currentWorkspaceId()` throws
  when no scope is open. A nested `runIn` with another Workspace id throws `IllegalStateException` and never runs the
  inner supplier (row 93; KAN-235 Q3 = A, Hiếu: a sign-in never needs two tenants on one thread). It keeps the `runIn`
  calls in place, so the platform adapter later replaces only the bean.
- No `workspace_id` filter (HLA Q4): isolation is the tenant's database, reached only inside `TenantScope.runIn`.
  Until Task 14 production has one database and `NoWorkspaceLookup` answers 404 on every Workspace site, so no
  Workspace is reachable; the isolation is shown by the two-Workspace tests (AC-001.7, AC-001.15), which run on two
  databases routed by a test `TenantScope` double and a test routing `DataSource` (test sources only; Decided in
  planning, builder check: two databases of one Testcontainers Postgres).
- The follow-up after the platform lands is Task 14 (tasks.md): `PlatformWorkspaceLookup`, `PlatformTenantScope` (and
  the Database Router behind it), the move of `sign_in_states` and `hand_off_code` to the registry database (HLA Q2),
  the registry's `workspace_email` adapter (HLA Q4), the per-database RLS guard for `app_user` and `user_role`
  if `platform` defines one, the `FirstAdminCreator` implementation (STORY-010, above), and demo Accounts for the seed Workspaces. Platform Task 15's demo seed is not edited by
  T9 or T9b. identity cannot reach `completed` before Task 14.
- While `NoWorkspaceLookup` answers 404, Hiếu sees T9 and T9b through their tests (fake issuer) only.

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
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | the OAuth client | any placeholder; the `dev` profile's fake issuer ignores them. The real client is used from the staging deploy on |
| `PLATFORM_OPERATOR_EMAILS` | the comma-separated allow-list; entries trimmed, compared ignoring case | a developer's email |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | the session store (HLA Q3), Spring's `spring.data.redis.*`; Task 10 (Decided in planning: names; `platform` or be-foundation may define the same values first, then they are reused) | `localhost`, `6379`, none (the Docker Compose Redis service) |

The redirect URI is always `TSOLVE_LOGIN_ORIGIN` + `/api/v1/auth/callback`. AC-001.21, .28, .29, .30 and .31 name the
configured Platform Console origin (`TSOLVE_ADMIN_ORIGIN`; the Console on `admin.` stays, HLA Q1); the code builds those URLs from it (Q7, Hiếu,
2026-10-28).

### Sign-in and session rules (how the criteria are met)

- Host check (`AuthHosts`): the request host (after the proxy's forwarded headers) is compared with the hosts of
  `TSOLVE_LOGIN_ORIGIN` and `TSOLVE_ADMIN_ORIGIN`; any other host is a Workspace site, resolved by
  `WorkspaceLookup`. `authorize` and `callback` on a host other than `login.` → 404 (AC-001.5).
- `returnTo` (AC-001.3): kept only if it starts with exactly one `/` and holds no `\`, no scheme, and no ASCII control
  or whitespace character (the rule of fe-foundation AC-012.2), and is at most 2,048 characters; else `/` (Decided
  in planning).
- Sign-in cookie (login CSRF, AC-001.44–.47, KAN-231 Q1–Q5): `authorize` on `login.` makes 32 random bytes
  (`SecureRandom`, base64url without padding), stores only their SHA-256 as `browser_binding_hash` and sets
  `authorized_at` in one conditional update (see Data model), then answers 302 to Google with
  `Set-Cookie: __Host-TSOLVE_SIGNIN=<value>; Max-Age=600; Path=/; Secure; HttpOnly; SameSite=Lax` (no Domain) and
  `Cache-Control: no-store` (AC-001.44; no cache or proxy keeps the answer that carries the cookie; spec review,
  KAN-231). `SameSite=Lax` is required: Google's redirect back is a cross-site top-level GET, which sends a `Lax` cookie and
  not
  a `Strict` one. The callback hashes the received cookie value and compares it with `browser_binding_hash` in
  constant time (`MessageDigest.isEqual`); a missing cookie, a missing hash (never authorized) or a mismatch is
  MSG141 (AC-001.46). Every callback answer on `login.`, of any outcome, sets
  `__Host-TSOLVE_SIGNIN=; Max-Age=0; Path=/; Secure; HttpOnly; SameSite=Lax` (AC-001.47). One rule for all three
  targets (Workspace, Platform Console, Jira link; Q5).
- Authorize once (AC-001.45): a state value already marked as authorized gets the 400 MSG141 page, no cookie and no
  redirect. This is what stops the attack of row 45: the attacker's own trip to `authorize` (needed to reach Google
  with the server's nonce and PKCE challenge) uses the state value up, so the victim's browser can never get the
  matching cookie. Two `authorize` requests at once for one state: the conditional update lets exactly one through.
  The update compares `expires_at` with the `IdentityClock` time passed as a parameter, not the database `now()`
  (see Data model).
- No Google domain (AC-001.48/.49, KAN-231 Q8): sign-in start on a Workspace site whose `WorkspaceRef.googleDomain`
  is null or blank answers 302 `/login?error=MSG141` on that site, creates no state and logs
  `WARN identity sign-in refused: workspace <id> has no Google domain` (the Workspace id only). `authorize` and the
  callback answer the 400 MSG141 page for such a Workspace or Jira-link state (a domain cleared after the state was
  made); the callback consumes the state first. Never sent to Google without `hd`.
- Callback order (AC-001.14): state (missing, unsigned, expired, used → MSG141; the state is consumed first, single
  use, whatever the outcome) → sign-in cookie (AC-001.46) → for a Workspace or Jira-link state, the state's
  Workspace through `WorkspaceLookup.findById`: not found → MSG141 (AC-001.50), no Google domain → MSG141
  (AC-001.49) → Google `error`, code exchange and ID token (MSG06) → Workspace Active → `hd` → Account exists →
  Account Active. Every MSG141 in this first step is the 400 minimal page, because a target may not be known; the
  Workspace must be found before the MSG06 step, whose redirect needs the Workspace site. The Account lookup and
  update run inside `TenantScope.runIn(state.workspaceId)`, i.e. in that Workspace's database, with the tenant bound
  before the transaction (AC-001.15, HLA Q4).
- AC-001.16 design note (KAN-235 Q2 = A, Q5 = A, Hiếu): the `name` claim counts as empty when it is absent, `""` or
  whitespace-only, and then the full name stays unchanged; any other value is stored trimmed. A `name` of more than 100 code points (the `app_user.full_name` column is `VARCHAR(100)`, as in the PDF) leaves the old name unchanged (the sign-in still succeeds; nothing is truncated; Hiếu 04/11). Order of the writes: the Account update (full name, sign-in
  times) first, then `HandOffCodeService.issue`. The two cannot share one transaction (the Account is in the company
  database, the code in the registry, row 42). If issuing fails, the sign-in time stays recorded, no session is made and
  the answer is the 500 ProblemDetail. `AccountSignInService` (in `identity.account`) holds the lookup and the update, so
  `CallbackController` stays thin.
- Decided in planning (KAN-231 step 1): a callback with neither `code` nor `error` counts as a failed code exchange
  (MSG06, AC-001.9); an ID token with no `email` claim fails verification (MSG06).
- Interim answers until the later tasks exist (KAN-231 Q12): a Platform Console state value runs the shared checks
  (state, sign-in cookie, Google), then gets the 400 MSG141 page whatever their result, until T11 replaces it;
  nothing is logged in. Decided in planning (builder check, same rule): in T9, a Workspace state value that passes
  the Google step, and a Jira-link state value after the shared checks, get the same 400 MSG141 page until T9b
  (Workspace and Account checks, hand-off) and T12 (link) replace it. No session, no Account change, no link.
- Start cookie (login CSRF at `complete`, AC-001.51, KAN-232 Q7): sign-in start on a Workspace site makes 32 random
  bytes (`SecureRandom`, base64url without padding), stores only their SHA-256 as the state's `start_hash` and sets
  `Set-Cookie: __Host-TSOLVE_START=<value>; Max-Age=600; Path=/; Secure; HttpOnly; SameSite=Lax` (no Domain) in the
  same 302. The callback copies `start_hash` from the state to the hand-off code it issues. `complete` consumes the
  code first (conditional update, see Data model), then checks the site, then compares the hash of the received
  cookie with the code's `start_hash` in constant time; a missing cookie or a mismatch → `/login?error=MSG141`, no
  session. Every `complete` answer on a Workspace site sets `__Host-TSOLVE_START=; Max-Age=0; Path=/; Secure;
  HttpOnly; SameSite=Lax`. `SameSite=Lax` is enough: the redirect back from `login.` to `complete` is a top-level GET.
  This is separate from `__Host-TSOLVE_SIGNIN`, which lives on `login.` and guards the callback; the start cookie lives
  on the Workspace site and guards `complete`, so an attacker's hand-off code opened in the victim's browser fails
  there. Decided in planning (builder check): the sign-in start answer also carries `Cache-Control: no-store`, as
  `authorize` does (AC-001.44). A second sign-in start in the same browser overwrites the cookie; the first sign-in
  then fails at `complete` with MSG141 and starts again. The Platform Console target has no start cookie in this task:
  an Operator code has `start_hash` null and is handled in Task 11 (see Open questions).
- Session filter rules (KAN-232 Q1, Q2, Q10): if the Account row is gone when the filter re-reads it, the session is
  ended and the answer is 401 `MSG07` (the same as expiry; not `MSG10`, since the Account is absent, not
  Deactivated). A session cookie that names no session (unknown,
  expired in Redis, or Redis restarted) on a Workspace site is treated as anonymous, and the answer carries
  `__Host-TSOLVE_SESSION=; Max-Age=0; Path=/; Secure; HttpOnly; SameSite=Lax` so the browser drops it. A session is
  honoured only on `admin.` or on a host that `WorkspaceLookup` resolves as a Workspace site; there is no
  `TSOLVE_API_ORIGIN` (Q10). `SecurityConfig` registers `WebSessionFilter` and `SecurityProblemWriter` gets the MSG10
  and MSG123 texts (Q11, a small edit in `common/security`, the BE lead agrees).
- Per request (`WebSessionFilter`, after the platform's Tenant Resolver): on `login.` or `api.` → no session
  (AC-001.22); then a host or kind mismatch → 401 MSG123 without ending the session (AC-001.20/.21/.32); then the
  8 h / 60 min limits (read from the Redis session) → end (delete the key) + 401 MSG07 (AC-001.23); then the
  Account re-read → Deactivated: end + 401 MSG10
  (AC-001.27), else a `TSolveUser` with the current Roles (AC-001.26). A request without `X-TSolve-Background: 1`
  updates `last_activity_at` (AC-001.24/.25). The order follows from AC-001.20, which keeps a mismatched session
  alive (Decided in planning). Time comes from `IdentityClock` (KAN-231 Q9): a small identity type wrapping a
  `java.time.Clock`, whose bean (`Clock.systemUTC()`) is defined in `SignInConfig`; every identity class that needs
  time injects it (from T9, `SignInStateService` stops using the ticket module's `Clock`), and tests build it from
  `Clock.fixed` (or the test `MutableClock`). identity defines no second `Clock` bean, so no other module's
  unqualified `Clock` injection becomes ambiguous.
- Operator sessions (T11): the principal is an `OperatorPrincipal(email)` with no authorities and none of the five
  Roles; if be-foundation `roles-security` defines an operator principal, T11 uses that one instead (Decided in
  planning). The allow-list is checked again at every request (AC-001.33); a change of the list takes effect after a
  restart.
- The minimal `login.` page (AC-001.39): a fixed HTML string with the escaped Report3 text of one message, no link
  and no script, sent with `Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'` and
  `Cache-Control: no-store`. The texts (MSG06, MSG09, MSG10, MSG75, MSG121, MSG122, MSG141) are copied from the
  Report3 catalog into `LoginPage`; its test asserts each exact text.
- Nothing logs a state value, hand-off code, authorization code, ID token or cookie value, including the
  `__Host-TSOLVE_SIGNIN` value and its hash (NFR 3).

### Test approach (Q10)

- CI never calls Google. A fake issuer: WireMock (`org.wiremock:wiremock-standalone`, test scope) serves the token
  endpoint and a JWKS made from a test RSA key (nimbus `RSAKeyGenerator`); tests sign their own ID tokens to cover
  every AC-001.9 failure, plus an unreachable token endpoint.
- Controller tests use MockMvc with the `Host` set per site (`acme.localhost`, `beta.localhost`, `login.localhost`,
  `admin.localhost`). The two-Workspace tests (NFR 5) use two Workspaces, each with its own database
  (HLA Q4: two databases on one Testcontainers Postgres, routed by a test `TenantScope` double) and Accounts of the
  same Roles: AC-001.7 (T9), AC-001.15 (T9b), AC-001.17 and .20 (T10), AC-001.32 (T11). The AC-001.15 test: Workspace
  B's Account email signing in on Workspace A gets MSG09, and B's Account is untouched, because A's database does not
  hold it (an email lives in one Workspace on the whole platform; the registry's `workspace_email` enforces it when
  an Account is added, KAN-231 Q13, HLA Q4).
- Repository and migration tests run against Postgres with Testcontainers, the `SignInStateRepositoryIT` pattern
  (needs Docker), until the database test setup of platform Task 2 is on `main` (KAN-231 Q10).
- Session tests (Task 10, HLA Q3) run against Redis with Testcontainers Redis (the same pattern, needs Docker); on a
  developer machine the local stack runs Redis as a Docker Compose service on Docker Desktop. The limits are tested
  with `Clock.fixed`, not with Redis time.
- JUnit names end with the AC id (`…_AC_001_6`); Vitest `it` names start with it.
- The real Google client is used on staging only.

**Open item for the staging demo (not a spec question):** the Google Workspace domain whose `hd` the staging
Workspace uses, and the Google Cloud OAuth client (with the redirect URI of staging's `login.`), are not chosen
yet. Hiếu chooses them before the staging demo of T9/T10; until then demos run on the dev fake issuer.

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
| Stores (Q8, reworded by the HLA, Q2/Q3, Hiếu 2026-11-03) | Sign-in states and hand-off codes: own Postgres tables, single-use `used_at`, in the Platform / Tenant Registry database (interim: the one changelog until Task 14); sessions: Redis, own keys, with the 8 h / 60 min limits checked in the application | Spring Session JDBC / Redis (the background-header and re-read rules are custom anyway); states and codes in a tenant DB, in Redis (`GETDEL`), or "decide later"; sessions in Postgres `web_sessions` (a recorded deviation from the HLA) | The HLA: no tenant is bound on `login.`, so a tenant table cannot hold them (Q2); replicas keep no local session and Redis HA is the shared store (Q3). States and codes keep the conditional-UPDATE design already built and tested; Redis for sessions changes Task 10, which has not started |
| OIDC (Q9) | Own controllers + `spring-security-oauth2-jose` + `RestClient` | `oauth2Login`; Google's client library | Full control of the signed single-use state and the hand-off; every check explicit and testable |
| Tests (Q10) | Fake issuer (WireMock + test JWKS) in CI; real Google on staging only | `mock-oauth2-server` in compose; manual checks | Every failure path in CI with no Docker extra |
| Start (Q11) | T8 now against the `WorkspaceLookup` / `TenantScope` ports with doubles | Wait for platform; identity builds be-foundation pieces | `login.` has no Workspace, so the state core can be built first |
| PRs (Q12) | One PR per task (T11 + T12 together if small); Jira key per task in tasks.md | Three PRs; one PR per Jira key | Vertical slices first; reviews stay small |
| Layout (Q13) | A centred antd card from the shared kit, SRS 1.1.1 wording | A Figma frame | No frame exists |
| Signing key | `TSOLVE_SIGNING_KEY`, HMAC-SHA256, no default | Asymmetric keys; one key per use | Only the server makes and checks the values; one secret is enough |
| Exception to backend AGENTS.md rules 1, 3, 4 (Hiếu, 2026-10-29) | `identity.signin` keeps the approved shape: a concrete `SignInStateService` (no interface), the `SignInState` entity used by the controllers, a flat `signin` package | Interface plus implementation, controller-facing DTOs, `controller/service/dto` sub-packages (the AGENTS.md rules) | The sign-in endpoints are redirects with no request or response DTOs, and the service has one implementation; the approved design and the QA tests already use this shape. Scope: `identity.signin` only |
| Login CSRF cookie (KAN-231 Q1–Q5, Hiếu, 2026-10-31) | `authorize` on `login.` sets `__Host-TSOLVE_SIGNIN` (HttpOnly, Secure, Lax, Path `/`, no Domain, 600 s) holding 32 random bytes; only their SHA-256 is kept on the state (`browser_binding_hash`); `authorize` accepts each state once (`authorized_at`); the callback compares hashes in constant time, answers the 400 MSG141 page without a match, and expires the cookie in every answer; all three targets (AC-001.44–.47) | Set at sign-in start and checked at `complete` (the cookie of one host never reaches `login.`; the callback's side effects would still run; no Jira-link cover); a `Domain=` cookie (drops `__Host-`); the state value or its HMAC in the cookie (state in browser storage, against NFR 3; deterministic); one cookie per state; `SameSite=Strict`; a 302 to the Workspace Login instead of the page | One host, the check stays at the callback, the `web_sessions.token_hash` pattern, NFR 3 holds with a small rewording; authorize-once is what makes the cookie bind the browser that went to Google. Cost: two parallel sign-ins in one browser → one or both tabs get MSG141 and start again (row 47) |
| No Google domain (KAN-231 Q8) | Refused at sign-in start (302 `/login?error=MSG141`, no state, `WARN` with the Workspace id), at `authorize` and at the callback (400 MSG141 page) (AC-001.48/.49) | Refuse at `authorize` only; 404 at sign-in start | Never sent to Google without `hd`; the user sees a known code on their own site. Hiếu accepts MSG141's text for this case |
| Identity time (KAN-231 Q9) | `IdentityClock` wrapping a `java.time.Clock`, bean in `SignInConfig`; tests pass `Clock.fixed` | A second `Clock` bean `identityClock` with `@Qualifier`; one shared `Clock` bean in `common` | No second `Clock` bean, so no other module's wiring can break; identity owns its time; the shared bean would edit the ticket module |
| Platform gap (KAN-231 Q10; Q4 = A, Hiếu 2026-11-03) | T9 and T9b against the ports and doubles; `app_user` and `user_role` with **no** `workspace_id` (one database per company, HLA); no Workspace filter in the repository, the tenant database is reached only inside `TenantScope.runIn`; interim production `LocalTenantScope` (one database, every Workspace site answers 404 until Task 14); Task 14 (after KAN-98 / KAN-99 are on `main`) brings the adapters, the Database Router, the move of the two registry tables, the registry's `workspace_email` adapter and the per-database RLS guard if `platform` defines one; no edit to platform Task 15's seed. Exception to backend AGENTS.md §7 (the tenant-table convention of platform AC-001.8, which assumed a shared pool) until Task 14: `app_user` and `user_role` have no `workspace_id`, and `Account` does not extend `TenantEntity` (it may not need to: the convention is the platform's to restate for a database per company). Scope: these two tables only; Task 14 ends the exception, and identity cannot reach `completed` before it | Wait for KAN-98 / KAN-99; a smaller T9 and the Account part after the platform | The pattern T8 proved; the rework is small and mechanical; isolation never relies on RLS alone. Cost: no local-stack demo while `NoWorkspaceLookup` answers 404 |
| Task 9 PR split (KAN-231 Q11) | Two PRs: T9 (KAN-231, the callback refuses forged, replayed, cookie-less returns and Google failures: AC-001.7–.9, .44–.50) and T9b (KAN-235, Workspace and Account checks, `app_user`, hand-off: AC-001.10–.16) | One large PR; three PRs with the review follow-ups alone first | Each PR is a behaviour Hiếu can test from the callback URL; reviews stay small; one branch per Jira issue. Replaces "one PR per task" (Q12 above) for Task 9 only |
| Console state before T11 (KAN-231 Q12) | Shared checks, then the 400 MSG141 page; nothing logged in | Bring AC-001.29–.31 forward; 501 | Safe and small; T11 stays as planned |
| Account email uniqueness (KAN-231 Q13; HLA Q4, Hiếu 2026-11-03) | One email in one Workspace on the whole platform (GBR-37, D7), kept by the Tenant Registry's `workspace_email` through a `platform` service that identity calls when an Account is added; inside a company database the email is unique and lower-case | A unique index on `app_user.email` across all rows (impossible with one database per company); per-Workspace uniqueness only (a spec change against D7) | The spec is the contract (GBR-37, D7); no index can span the databases |
| Platform Console stays (HLA Q1, Hiếu 2026-11-03) | The Console on `admin.` is back as in the HLA; identity keeps requirements 2–3, AC-001.21, AC-001.28–.34, NFR 4 and Task 11; architecture.md is reworded | The 31/10 removal wins (drop AC-001.21 and .28–.34, Task 11); the Console as "future" | Hiếu's rule: the HLA wins. It costs Task 9 nothing |
| Workspace gone at the callback (KAN-231 Q14) | State consumed, 400 MSG141 page, no session (AC-001.50) | Treat as not Active (MSG120 page) | "MSG141 when no target is known" in Interface Contracts; there is no site to redirect to |

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
| 24 | STORY-001 | Data integrity | Workspace B's Account email signs in on Workspace A (an email exists only once on the whole platform, GBR-37, D7; HLA: each in its own database) | Critical | MSG09 on A's site; A's database does not hold B's Account, so it is neither found nor changed (AC-001.12/.15; two-database test) | Task 9b |
| 25 | STORY-001 | Input extremes | Email `Lan.Tran@Company.Example`, `hd` `ACME.com` | High | Matched ignoring case (AC-001.11/.12) | Task 9b |
| 26 | STORY-001 | Timing | Two callbacks with the same state (back button, a repeated redirect) | High | One goes on; the other gets MSG141 (AC-001.2/.6) | Tasks 9, 9b |
| 27 | STORY-001 | Timing | A hand-off code redeemed twice (two tabs, a reload of `complete`) | Critical | One session; the second gets `/login?error=MSG141` (AC-001.17/.19) | Task 10 |
| 28 | STORY-001 | Authorization | A hand-off code of Workspace A presented on site B or on `admin.` | Critical | MSG141 on that site, no session (AC-001.17/.19) | Task 10 |
| 29 | STORY-001 | Authorization | A's cookie copied to B or to `admin.`; an Operator cookie on a Workspace site | Critical | 401 MSG123, endpoint not run, A's session not ended (AC-001.20/.21/.32) | Tasks 10, 11 |
| 30 | STORY-001 | Authorization | Any session cookie sent to `login.` or `api.` | High | No session (AC-001.22) | Task 10 |
| 31 | STORY-001 | State transitions | Idle 59 vs 61 minutes; only background polling for 2 h; 8 h with activity | High | MSG07 just past each limit (AC-001.23–.25), with the injected `IdentityClock`, whatever the Redis time to live | Task 10 |
| 32 | STORY-001 | State transitions | Account deactivated or its Roles changed during a session | High | Next request: MSG10, or the new Roles (AC-001.26/.27) | Task 10 |
| 33 | STORY-001 | Compliance | A state value, code, ID token or cookie value in a log or in browser storage | High | Never; session ids stored only as hashes (NFR 3) | Task 10 |
| 34 | STORY-001 | Environment | A `__Host-` + `Secure` cookie on `http://acme.localhost` in local dev | Medium | Check in T10's local run which browsers accept it, for `__Host-TSOLVE_SESSION` and for the sign-in cookie `__Host-TSOLVE_SIGNIN` on `http://login.localhost` (KAN-231); note the result in `.env.example`; also `__Host-TSOLVE_START` on `http://acme.localhost`. The local run does not use real Google (Google refused a `.localhost` redirect URI): the `dev` profile serves a fake Google from the backend itself. Shown by the local browser run in Task 10 (it stays in Task 10, not Task 14): sign in through the dev fake issuer on `http://acme.localhost:<port>` and see the `__Host-TSOLVE_SESSION` cookie set (`HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`, no `Domain`) | Tasks 9, 10 |
| 35 | STORY-001 | User types | Allow-list entry ` Ops@X.com `, or an email removed from the list | High | Trimmed and compared ignoring case (AC-001.29/.30); removed → MSG135 at the next request (AC-001.33) | Task 11 |
| 36 | STORY-001 | User types | An Operator who also has a Workspace Account | High | Each site gets only its own kind of session (AC-001.29/.32) | Task 11 |
| 37 | STORY-001 | Authorization | Jira link: a tracker account in a callback parameter; a relink; a suspended Workspace | Critical | Parameter ignored (AC-001.38); link replaced (AC-001.36); MSG121 (AC-001.37) | Task 12 |
| 38 | STORY-001 | Environment | A required env value missing, or `TSOLVE_SIGNING_KEY` shorter than 32 bytes | High | Startup fails and names the value (NFR 4) | Task 8 |
| 39 | STORY-001 | Input extremes | `/login?error=MSG122&google_domain=<script>`; `error=MSG07`; `error=MSG120` | High | MSG122 dropped and text never rendered as HTML; empty message area (AC-001.42/.43) | Task 7 |
| 40 | STORY-001 | Timing | A double click on "Sign in with Google" | Low | The button is pending after the first click; one navigation | Task 7 |
| 41 | STORY-001 | Scale | Rows of old states and codes pile up; session keys of users who never return | Medium | A cleanup job deletes old states and codes (once in the registry, by the worker); a session key's time to live removes the session (HLA Q3) | Task 10 |
| 42 | STORY-001 | Error cascades | The database is unavailable during the callback or `complete` | Medium | No session (one transaction); the browser gets be-foundation's 500 ProblemDetail (accepted: generic 500 (Hiếu, 2026-10-28)). In T9b the Account update and the hand-off code are in two stores, so if issuing the code fails the sign-in time stays recorded, still no session and the 500 (KAN-235 Q5 = A) | Tasks 9, 9b, 10 |
| 43 | STORY-001 | Authorization | Q1: a path outside the four redirect paths (for example `/api/v1/anything`) | High | Anonymous: 401, no form login, no HTTP Basic; signed in: reaches it (the lowest-order chain `anyRequest().authenticated()`) | Task 8 |
| 44 | STORY-001 | Input extremes | Q2: `GET /api/v1/auth/sign-in` on a host that is no Workspace site and not the Platform Console origin (an unknown host, `login.`, `api.`) | High | 404, status only, no state created (same rule as AC-001.5) | Task 8 |
| 45 | STORY-001 | Authorization | Login CSRF: an attacker starts a sign-in and gets a callback URL (state + code) for the attacker's own Google account, then makes the victim's browser open it | High | Closed by the 2026-10-31 spec change (KAN-231 Q1–Q7): `authorize` sets `__Host-TSOLVE_SIGNIN` and accepts each state once; the callback without the matching cookie consumes the state and answers the 400 MSG141 page; no session, no Account change, no link; every callback answer expires the cookie (AC-001.44–.47) | Task 9 |
| 46 | STORY-001 | Authorization | Jira-link phishing: the attacker sends the victim the attacker's own link URL (`authorize?state=<Workspace + attacker's tracker account>`); the victim signs in, and the attacker's Jira account is linked to the victim's Account. The victim's own browser makes every request, so the sign-in cookie does not help | Critical | Open, for Task 12 (KAN-231 Q6): for example a confirm page on `login.` ("Link Jira account X to your account Y?") before the link is recorded; needs its own criterion when Task 12 is planned. Not in the 2026-10-31 spec change | Task 12 |
| 47 | STORY-001 | Timing | Two sign-ins in two tabs of one browser: the later `authorize` overwrites `__Host-TSOLVE_SIGNIN` | Medium | One or both tabs get the 400 MSG141 page and start again (AC-001.46, KAN-231 Q3). Both `authorize` calls before either callback: if the later tab's callback comes first, it signs in and the earlier tab's callback finds no cookie; if the earlier tab's callback comes first, the cookie does not match it, and its answer expires the cookie (AC-001.47), so the later tab gets MSG141 too | Task 9 |
| 48 | STORY-001 | Integration | A link preview or an email scanner opens an `authorize` URL first | Low | It uses the state value up; the person gets the MSG141 page and starts again (AC-001.45). Workspace and Console `authorize` URLs exist only as redirects; Jira-link URLs are shown by `jira-app` (Decided in planning, KAN-231) | Task 9 |
| 49 | STORY-001 | State transitions | The state's Workspace is deleted, or no longer found by the lookup, before the callback | High | State consumed, 400 MSG141 page on `login.`, no session (AC-001.50) | Task 9 |
| 50 | STORY-001 | Input extremes | A Workspace with no Google domain (never set, or cleared after a state was made) | High | Sign-in start: 302 `/login?error=MSG141`, no state, `WARN` with the Workspace id only; `authorize` and callback: 400 MSG141 page; never sent to Google without `hd` (AC-001.48/.49) | Task 9 |
| 51 | STORY-001 | Timing | Two `authorize` requests for the same state at once (a double load), or a reload of `authorize` | High | The conditional update lets one through (cookie + 302 to Google); the other gets the 400 MSG141 page and no cookie (AC-001.44/.45) | Task 9 |
| 52 | STORY-001 | Input extremes | Callback with neither `code` nor `error`; an ID token with no `email` claim | Medium | MSG06, no session (AC-001.9; Decided in planning, KAN-231) | Task 9 |
| 90 | STORY-001 | Error cascades | Redis down, or slow, at `complete` or on a request (HLA Q3) | High | Fail closed: no session is created (`complete` redirects to `/login?error=MSG141`) and no request is treated as signed in; the Redis failure is logged without any session value; the browser gets be-foundation's generic error, as row 42 | Task 10 |
| 91 | STORY-001 | State transitions | Redis restarts or evicts keys: every session is lost (HLA Q3) | High | Each next request is 401 `MSG07` and the person signs in again; Redis for sessions has persistence on and no eviction (`noeviction`), kept apart from any cache (HLA) | Task 10 |
| 92 | STORY-001 | Data integrity | Registry database unavailable at `authorize`, the callback or `complete` after states and codes moved there (HLA Q2) | Medium | As row 42: the transaction fails, no session; generic 500 (accepted) | Task 14 |
| 93 | STORY-001 | Authorization | A request with no tenant bound reaches an account table (HLA "no default fallback") | Critical | No tenant database can be reached; `TenantScope.currentWorkspaceId()` throws outside `runIn` (test in T9b); the router's own guard is Task 14 | Tasks 9b, 14 |
| 94 | STORY-010 | Authorization | `createFirstAdmin(A, email)` runs while Workspace B is bound, or with nothing bound (a wrong call from the provisioning service) | Critical | Throws; no row is written in B's database (AC-010.3) | Task 14 |
| 95 | STORY-010 | Data integrity | The email already has an Account in that database (a retry after a half-done creation, or a seed Account) | High | `BusinessRuleException` on `email`, nothing created; the service undoes the Workspace (AC-010.5, platform AC-013.16) | Task 14 |
| 96 | STORY-010 | Error cascades | The Role row cannot be written after the Account row | High | One transaction: both rolled back, so no Account without the Admin Role (AC-010.2) | Task 14 |
| 97 | STORY-010 | Compliance | The email or name reaches a log line, an exception message or the returned value | High | Only the `UUID` is returned; logs and exceptions carry the Account id and the field name, never the address (AC-010.1, AC-010.7) | Task 14 |
| 98 | STORY-010 | Timing | Two creations race for one email | High | The platform's unique constraint on `workspace_email` lets one through at step 4; `createFirstAdmin` does not claim and is never reached by the loser (AC-010.6, platform AC-013.19) | Task 14 |
| 99 | STORY-010 | Input extremes | Email `Lan.Tran@Company.Example`, blank, or without `@` | High | Stored lower-case and found by the sign-in lookup (AC-001.12); blank or malformed refused on `email` (AC-010.1, AC-010.4) | Task 14 |
| 100 | STORY-001 | Authorization | Login CSRF at `complete`: an attacker signs in with the attacker's own Google account in the attacker's browser, keeps the hand-off code, and makes the victim's browser open `complete?code=…` on the Workspace site (the victim then works inside the attacker's Account) | Medium | Closed by AC-001.51: the victim's browser has no `__Host-TSOLVE_START` cookie, or another one, so the hash differs; `/login?error=MSG141`, no session, the code stays consumed, the cookie is cleared (the code is also valid for 60 seconds only and works once, AC-001.17) | Task 10 |
| 101 | STORY-001 | Authorization | A hand-off code shown on the wrong site (Workspace B's host or `admin.`) | High | The conditional update runs first, then the site check fails: MSG141 on that site; the code is dead on its own site too (KAN-232 Q5; AC-001.17/.19) | Task 10 |
| 102 | STORY-001 | State transitions | A session cookie that names no session on a Workspace site (Redis restarted, key expired); the Account row gone when the filter re-reads it | High | Anonymous request plus `__Host-TSOLVE_SESSION=; Max-Age=0`; for a vanished Account the session is ended and the answer is 401 `MSG07` (KAN-232 Q1, Q2) | Task 10 |

Q1-Q5 (Hiếu, 2026-10-29): Q1 `AuthSecurityConfig` also adds a second, lowest-order `SecurityFilterChain`
(`anyRequest().authenticated()`, 401, no form login, no HTTP Basic); be-foundation `roles-security` replaces it later.
Q2 sign-in start on a non-Workspace, non-Console host answers 404 and creates no state. Q3 `SignInStateRepositoryIT`
(Testcontainers, needs Docker) covers the check constraint and `markUsed`. Q4 a developer step adds empty skeletons
of the Task 8 classes before the QA tests. Q5 the developer may edit ticket's `ImportConfirmIT` and
`ImportControllerTest` test properties only.

Task 8 review (Hiếu, 2026-10-29): the review is `06-evaluation/identity/task-8-sign-in-start-review.md`. The AGENTS.md
exception is recorded in Key decisions above. Login CSRF is row 45, closed by the Change Mode spec edit of 2026-10-31
(AC-001.44–.47), which Hiếu approves before Task 9 code starts. The two other review findings are in Task 9: the
Workspace with no Google domain (AC-001.48/.49, Key decisions "No Google domain") and `IdentityClock` (Key decisions
"Identity time").

HLA change (2026-11-03): rows 24, 31 and 41 reworded; rows 90–93 added (rows 53–89 belong to STORY-003); every
Critical or High row has its test task (Task 10: 90, 91; Task 9b: 93; Task 14: 92, 93).

FirstAdminCreator change (2026-11-04, KAN-98): rows 94–99 added for STORY-010, all tested in Task 14.

KAN-232 Task 10 change (2026-11-04, Hiếu Q1–Q11): rows 100–102 added (login CSRF at `complete`, a code on the wrong site, a session cookie that names no session); AC-001.51 added; every row is tested in Task 10. Open question for Task 11: the Operator target has no start cookie in Task 10 (an Operator code has `start_hash` null).

Open questions for the spec owner: none for Task 9 / 9b once the 2026-10-31 spec change is approved (row 45 closed; row 46 waits for the Task 12 plan). Row 42 is accepted (C3, Hiếu, 2026-10-28): a database failure during a
sign-in redirect keeps be-foundation's generic 500.

## Keycloak re-plan: STORY-001 sign-in and STORY-002 logout — KAN-275 plan, 2026-11-05

Master: this file. Spec: `artifacts/03-modules/identity/spec.md`, Changelog row 2026-11-05 (AC-001.52–.73, AC-002.8).
Decisions: ADR `docs/adr/0003-keycloak-sign-in.md` (K1–K14). Runbook: `docs/runbooks/onboard-company.md`.
Step 1 (inventory, verdicts, risks R1–R20, questions Q1–Q16): `prompts/handoffs/KAN-275-plan-step1.md`.

**This section replaces the sign-in mechanism of the "STORY-001: central sign-in…" section above** (flow, signed state
values, `authorize`, hand-off codes, the `login.` page, `__Host-TSOLVE_SIGNIN`, `__Host-TSOLVE_START`, `sign_in_states`,
`hand_off_code`, `TSOLVE_SIGNING_KEY`, the Google OIDC libraries and the rows 19–23, 26–28, 34 (cookies), 38, 41 (state
cleanup), 43–52, 92, 100–101 of its edge-case table). What stays: sessions in Redis and `WebSessionFilter` (K9), tenant
isolation (K11), `LocalTenantScope`, the `app_user` and `user_role` tables, the `IdentityClock`, the Login screen
(Task 7), the `FirstAdminCreator` (STORY-010) and the Platform Console on `admin.`. Where the older section and this
one differ, this one wins.

### Flow (Workspace sign-in)

```text
Workspace site (acme.)                    Keycloak (TSOLVE_LOGIN_ORIGIN, realm tsolve)            Google
 Login "Sign in with Google"
  └ GET /api/v1/auth/sign-in ─ checks AC-001.48 · auth request → Redis · Set-Cookie __Host-TSOLVE_AUTHREQ
      └ 302 ─► /realms/tsolve/protocol/openid-connect/auth  (client tsolve-web, PKCE S256, kc_idp_hint=google-acme)
                       ├ broker to the Workspace's Google IdP (hostedDomain = the company domain) ─────────────► account chooser
                       └ first broker login: create user, add to the Organization, no review, no email link ◄── back
 GET /api/v1/auth/callback?code&state ◄─ 302 (the site's exact redirect URI)
   ├ read + delete the auth request (cookie hash) · expire the cookie · state check (Spring)
   ├ code exchange + ID token (Spring OAuth2 client; issuer, audience, nonce, email_verified)
   ├ organization + hd · Workspace Active · inside runIn(Workspace): match sub / first-time email, bind sub · Active
   └ new Redis session (id_token kept as id_token_hint) · Set-Cookie __Host-TSOLVE_SESSION ─► 302 returnTo or /
 every later request: WebSessionFilter (unchanged rules, K9)
 POST /api/v1/auth/logout ─ end session ─► 200 { logoutUrl } ─► the browser opens Keycloak's end-session URL ─► /login
```

`admin.` runs the same path against the realm `tsolve-ops` (client `tsolve-console`, Google IdP without `hd`, a second
factor) and ends in an Operator session after the allow-list check (AC-001.28–.34).

### Components and packages (what happens to each class)

| Where | Verdict | Task |
|-------|---------|------|
| `identity/AuthSecurityConfig` | modify: one chain for sign-in start + callback on Workspace sites and `admin.`: OAuth2 client (`oauth2Login` with a custom `AuthorizationRequestRepository` and `OAuth2AuthorizationRequestResolver`), `NullSecurityContextRepository`, `SessionCreationPolicy.STATELESS`, success handler = session creation; the `authorize` and `complete` paths are dropped | 23, 24 |
| `signin/KeycloakAuthorizationRequestResolver` (new) | Spring's resolver plus `kc_idp_hint=google-<slug>` from the resolved Workspace, PKCE `S256` (Spring's default for public and confidential clients is set on), the exact redirect URI of the site, the kept `returnTo` as an attribute | 23 |
| `signin/RedisAuthorizationRequestRepository`, with the `__Host-TSOLVE_AUTHREQ` cookie (new) | `saveAuthorizationRequest`, `removeAuthorizationRequest` (read + `GETDEL` in one step); key `tsolve:authreq:<SHA-256 hex of the cookie value>`, 600 s | 23 |
| `signin/OidcCallbackHandler` (new) | success: the Account checks (Tasks 25a and 25b) then the session; failure: maps Spring's `OAuth2AuthenticationException` to MSG141 or MSG06 by cause; always expires the cookie | 23, 25, 26 |
| `signin/SignInStartController` | modify: same URL and `returnTo` filter; checks AC-001.48; hands over to the resolver (302 comes from Spring's `OAuth2AuthorizationRequestRedirectFilter` or the controller calls the resolver; Decided in planning: the controller, so the AC-001.48 refusal and the cookie stay in one place) | 23 |
| `signin/SignInConfig`, `SignInProperties`, `AuthHosts` | modify: Keycloak issuer and client values; `AuthHosts` keeps Workspace site vs `admin.` only (K1) | 23, 24 |
| `signin/port/WorkspaceLookup`, `WorkspaceRef`, `NoWorkspaceLookup` | modify: `WorkspaceRef(id, slug, siteHost, googleDomain, organizationId, status)`; `NoWorkspaceLookup` follows | 25 |
| `signin/port/WorkspaceSignInProvisioner` (new) + `NoopWorkspaceSignInProvisioner`, `KeycloakWorkspaceSignInProvisioner` (new, in `identity/keycloak`) | the port of K6 and its two beans (AC-001.57–.62) | 28 |
| `account/Account`, `AccountRepository`, `AccountSignInService` | modify: the `sub` column, `findBySub`, the first-sign-in email match, the bind, the refusal (AC-001.12, .16, .56) | 25 |
| `account/AccountRole`, `AccountRoleRepository`, `session/WebSessionFilter`, `SessionCookie` | keep (salvage of the uncommitted Task 10 tree) | 26 |
| `session/WebSession`, `WebSessionStore` | salvage, plus the `id_token_hint` field | 26 |
| `session/OperatorAllowList`, `OperatorPrincipal` | new (Task 11's classes, rebuilt) | 29 |
| `jiralink/JiraLinkRequestService`, `JiraLinkController`, `JiraAccountLink*` | new | 30 |
| deleted | `AuthorizeController`, `signin/CallbackController` (the one on `login.`), `GoogleIdentity`, `GoogleOidcClient`, `IdTokenVerifier`, `LoginPage` (backend), `SignInCookie`, `SignInState*`, `SignedToken`, `HandOffCode*`, `session/CompleteController`, `signin/StartCookie`, the `dev/DevGoogle*` classes and `DevSecurityConfig` | 24, 26 |
| `common/security/SecurityProblemWriter` (MSG10, MSG123) | keep | 26 |
| frontend `LoginPage.tsx`, `MockSignInPage.tsx`, `shared/auth/sign-in.ts` | keep; the mock page keeps its outcomes | 31 |
| `identity/dev/DevWorkspaceLookup`, `DevAccountSeeder`, `DevAccounts`, `DevStartupGuard` | modify: Organization id and slug for `acme` and `beta`; the seeded Accounts match the realm-import users (their `sub` is bound at the first sign-in) | 23, 26 |
| `infra/keycloak/realm-tsolve.json`, `realm-tsolve-ops.json`, `fake-google/` (WireMock mappings), `themes/tsolve/` (new) | the realm imports (K14), the fake Google upstream (Q8), the theme (K13) | 23, 29, 31 |

### Data model

- **Dropped by new changesets** (the files `004`, `005`, `006`, `007` on `main` are never edited): `sign_in_states`,
  `hand_off_code`. The uncommitted `008` and `009` (never released) are not kept (Q5). A developer database that
  already ran them is reset with `docker compose down -v` (R20).
- **`app_user.keycloak_sub`**: a nullable `VARCHAR(64)`, unique inside the company database (one new tenant changeset;
  K8, K11). Filled at the first sign-in; never cleared except by the runbook's rebind (Q11).
- **Redis keys** (one Redis, persistence on, `noeviction`): `tsolve:session:<hex>` (as before; plus the field
  `id_token_hint` and `kind`), `tsolve:authreq:<hex>` (a hash: `target`, `workspace_id`, `state`, `nonce`,
  `pkce_verifier`, `return_to`; 600 s), `tsolve:jiralink:<id>` (`workspace_id`, `tracker_account_id`; 600 s).
- **Keycloak**: its own database `keycloak` and user on the existing PostgreSQL server (Q3). Realm `tsolve`: client
  `tsolve-web`; client `tsolve-provisioner` (service account); one Organization and one IdP `google-<slug>` per
  Workspace; the `organization` client scope and a mapper for the user attribute `hd`. Realm `tsolve-ops`: client
  `tsolve-console`; the IdP `google-operators`. Both realms come from the realm-import files (K14) and are changed
  at run time only by the provisioning adapter.
- **Platform record** (change request, below): `google_domain` and `keycloak_organization_id`; the
  Workspace is `ACTIVE` at creation (platform AC-013.1).

### Configuration

| Name | Meaning | Local dev |
|------|---------|-----------|
| `TSOLVE_LOGIN_ORIGIN` | Keycloak's public origin; the issuer is this + `/realms/tsolve` | `http://login.localhost:8081` (compose `keycloak`, `KC_HOSTNAME` the same) |
| `TSOLVE_ADMIN_ORIGIN` | the Platform Console origin | `http://admin.localhost:<port>` |
| `KEYCLOAK_WEB_CLIENT_SECRET`, `KEYCLOAK_CONSOLE_CLIENT_SECRET` | the two confidential clients | the value in the realm-import file |
| `PLATFORM_OPERATOR_EMAILS` | allow-list, second check after Keycloak (Q1) | a developer's email |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | as Task 10 | compose Redis |
| `KEYCLOAK_ADMIN_CLIENT_SECRET`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | only the profiles that create or remove Workspaces (AC-001.61); absent elsewhere | a placeholder; the dev fake Google ignores them |
| removed | `TSOLVE_SIGNING_KEY`, `tsolve.oidc.google.*` | |

The backend reaches Keycloak at the issuer it expects in tokens (the Docker network name and `KC_HOSTNAME` are set
together; a start-up check reads both discovery documents, R11).

### Rules (how the criteria are met)

- **Authorization request (AC-001.52, .53, .55, R15).** Sign-in start makes 32 random bytes (`SecureRandom`, base64url
  without padding), sets `Set-Cookie: __Host-TSOLVE_AUTHREQ=<value>; Max-Age=600; Path=/; Secure; HttpOnly; SameSite=Lax`
  and stores the request under the SHA-256 of the value. `SameSite=Lax` is required: Keycloak's redirect back is a
  cross-site top-level GET. The callback hashes the received value, reads and deletes the key in one `GETDEL`, and
  expires the cookie in every answer. A missing cookie, another browser's cookie (no key), a used or expired request
  are all MSG141. This cookie is the login-CSRF binding that the old `__Host-TSOLVE_SIGNIN` and `__Host-TSOLVE_START`
  were (rows 45 and 100 are closed by it). Spring's `state` check still runs against the stored request.
  `oauth2Login` is configured with `STATELESS` and a `NullSecurityContextRepository`; the success handler, not Spring,
  makes the session.
- **Callback order (AC-001.14).** request → Keycloak error / token → organization and `hd` → Workspace Active → `runIn(
  Workspace)`: match by `sub`, else (first sign-in, no `sub` yet) by verified email ignoring case, then bind →
  Account Active → session. The Workspace is the one the platform resolved from the host; the Tenant Resolver already
  refuses an unknown or suspended site (the old rows 49 and 50 go away, K5).
- **Email drift (R19).** Decided in planning: `app_user.email` is not changed by a later sign-in; the token's email is
  used only for the first match. A changed Google address keeps the Account through its `sub`; the Admin's user list
  keeps showing the old address until STORY-007 edits it.
- **K9 limits.** The realm sets SSO Session Max 8 h and Idle 60 min (AC-001.63). T-Solve's own 8 h / 60 min checks
  stay in `WebSessionFilter`.
- **Logout (K10, Q7 = A).** `logoutUrl` = Keycloak's `end_session_endpoint` + `id_token_hint` + `post_logout_redirect_uri`
  (the site's `/login`, registered per Workspace by AC-001.58). `SessionEnded` is unchanged. fe-foundation's
  `useSignOut` navigates to the URL (change request KAN-198). The Operator's logout uses the `tsolve-ops` endpoint.
- **Operators (Q1 = B).** A separate realm and client, a Google IdP without `hd`, TOTP or WebAuthn required, the
  allow-list as the backend's second check. A mistake in a tenant IdP can never grant the Console.
- **Failures (Q6 = A).** Failures that reach the callback go to `/login?error=` on the site. The others show on
  Keycloak's themed page (AC-001.67); which case lands where is fixed by the Testcontainers tests of Tasks 23 and 31 (K14). Google's own `redirect_uri_mismatch` page is not among them: Google never redirects back. A cancel at Google (`error=access_denied`) is a themed-page case too: Keycloak 26.4.7 does not return it to the callback but shows its own 400 page, which the theme replaces with the MSG06 text; the callback side of AC-001.8 is pinned by the `prompt=none` test (probe, Task 31 QA 2). The themed page's "Back to sign-in" link uses `client.baseUrl` (the realm sets `tsolve-web.baseUrl` to one neutral T-Solve URL: dev `http://localhost:5173/`, prod `https://tsolve.io.vn/`) and falls back to the same URL, kept in `theme.properties`, when Keycloak does not know the client (K13).
  The error list of the Login screen does not change (Q11 = A adds no message).
- **Keycloak hardening (Q13 = A).** Realm import: registration off, no password form, IdPs hidden on the login page,
  a custom first-broker-login flow, brute-force detection on (AC-001.64); the provisioning adapter sets
  "hide on login page" and the flow on each IdP it creates. Tests: a request with no hint, a hint for an unknown
  alias and a hint edited to another Workspace.
- **Claims (Q10 = C).** `organization` and `hd` are both required (AC-001.11). The realm import carries the
  `organization` scope and the `hd` mapper (AC-001.65); a test signs in with a token that lacks each of them.
- **Provisioning (K6, Q12, Q14, Q16).** `provision` runs under a PostgreSQL advisory lock taken on the registry
  connection, creates Organization → IdP → client URIs, re-reads the client and checks that the earlier URIs are all
  still there, and on any failure deletes in reverse order, idempotent by alias. The adapter's credentials are only
  in the `ops` and Console profiles. Until the platform exists the no-op bean answers (AC-001.60).
- **Jira link (Q2 = A).** `jira-app` calls `JiraLinkRequestService` after verifying the Forge token; the person
  confirms on the Workspace site. The confirm page is a web-app route (`/jira-link`); the POST needs CSRF and a
  Workspace session. Design row 46 changes from Open to mitigated: a victim who confirms without reading the page is
  still exposed; the option B (a code typed into the Jira panel) stays available if the team wants it closed.
- **Dev and tests (K14, Q8 = A).** Compose gets `keycloak` (26.x pinned, heap capped, health check) with the database
  `keycloak`, and a WireMock `fake-google` that Keycloak brokers to as `google-acme` and `google-beta`. The realm import
  holds users that match `DevAccounts`. Tests use `testcontainers-keycloak` with the same fake issuer
  (`FakeGoogleIssuer`, modified); CI never calls Google. `DevGoogleIssuer` and `DevGoogleController` are deleted.
- **Redis is the only shared state**; no sign-in table remains in a database.

### Platform change requests (Q4 revised, 5/11) — for the platform owner (KAN-98 / KAN-99 / KAN-240)

Decision (Q4, revised): the Platform Console is the site of the one platform admin, who provisions the Workspaces and
their first Admin. A Workspace is `ACTIVE` at creation, as platform AC-013.1 already says. There is no
`PENDING_SIGN_IN` status and no `activate` operation. The test sign-in and the Google redirect-URI step are steps of the
runbook `onboard-company.md`, done by the platform admin before the company is told; they are not a gate in the data
model. If sign-in is tried before Google has the redirect URI, Google refuses and the Workspace site shows the normal
sign-in error. Identity edits no platform file; the requests are:
1. Columns `google_domain` and `keycloak_organization_id` on `tenant_workspace` (identity's spec reads the Google domain
   from the platform's Workspace record; the platform spec does not list it), and the two fields in `WorkspaceSummary`.
2. `WorkspaceProvisioning.create` calls `WorkspaceSignInProvisioner.provision` after step 4 and before the first Admin;
   AC-013.6 and AC-013.16 undo calls `remove`; a `remove` for the "remove a company" runbook. `create(siteName,
   adminEmail)` gets the Google domain as a new `create` parameter.
3. Where the port is declared: K6 says identity-owned; platform cannot depend on identity, so once the platform's
   `WorkspaceProvisioning` exists the interface moves to the platform's public API (the pattern of `FirstAdminCreator`)
   and identity implements it (Decided in planning; confirm with the platform owner).
Other cross-module changes (R17): `jira-app` (KAN-124) calls `JiraLinkRequestService`, not `SignInStateService`;
fe-foundation (KAN-198): `useSignOut` navigates to `logoutUrl`; PR #119 (KAN-107 backend) adds a second session filter
in `identity/session` and `common/tenant/TenantContext`: identity is the code owner, so `WebSessionFilter` wins and
`UserListSessionFilter` and `WorkspaceSessionBinding` are dropped from identity's side, planned as part of Task 26a
(a note to the owner of PR #119, that their code touches identity, is written by Hiếu, not in this plan; who owns
`common/tenant/TenantContext` is agreed with platform).

### Test approach

- `testcontainers-keycloak` (26.x, the realm-import files) brokering to the WireMock fake issuer, the pattern of
  `SignInStateRepositoryIT`; needs Docker. Controller tests use MockMvc with the `Host` per site. Redis tests use
  Testcontainers Redis. Two-Workspace tests (NFR 5): AC-001.7, .11, .15, .20, .32.
- JUnit names end with the AC id (`…_AC_001_6`). Retired ids have no test left.

### Key decisions (Keycloak re-plan)

| Decision | Chosen | Alternatives | Rationale |
|----------|--------|--------------|-----------|
| K1 Where sign-in lives | Keycloak 26.x at `login.`, realm `tsolve` | Keep the custom `login.` backend | Less security code to own; ADR-0003 |
| K2 Tenant in the identity layer | One Organization per Workspace, id kept on the platform's record | A user attribute only | Organizations give a claim the backend can check (AC-001.11) |
| K3 Google | One IdP per Workspace, alias `google-<slug>`, one Google client, `hostedDomain` | One IdP for all, `hd` checked in the backend | `hd` is enforced twice (Google and AC-001.11) |
| K4 Redirect URIs at Google | Manual runbook step per company | Automation with the Google API | Google has no API for it; Q4 = A blocks Active until it works |
| K5 Site clients | One confidential client `tsolve-web`, exact URI per site, PKCE `S256`, `kc_idp_hint` | One client per Workspace (Q12 B) | One secret, one definition; the URI list is serialised (Q12) |
| K6 Provisioning | Admin REST behind a port, no-op until the platform exists | Manual setup in the console | Workspace creation stays all-or-nothing; Q12, Q14, Q16 below |
| K7 Backend client | Spring Security OAuth2 client, request kept in Redis (Q9) | Own OIDC code (the old design) | No hand-written crypto or OIDC; replica-safe |
| K8 Account match | Callback on the Workspace site; org + `hd`, Workspace, Account by `sub` (first time by email), Active | Email only (the old design) | `sub` survives an email change; a bound `sub` is not re-takeable (Q11) |
| K9 Sessions | As Task 10: Redis, 8 h / 60 min, plus Keycloak SSO limits | Keycloak sessions only | Per-request Account re-read must stay in T-Solve |
| K10 Logout | RP-initiated, `id_token_hint` kept in the session; Q7 = A: 200 `{ logoutUrl }` | Back-channel call (Q7 B, keeps 204) | K10's wording; needs the fe-foundation change |
| K11 Tenancy | Unchanged | | One database per company |
| K12 Deletions | The custom classes, tables and cookies, by new changesets | Keep them dormant | Dead security code is a liability |
| K13 Theme | Minimal T-Solve theme on Keycloak's login and error pages | Default theme | Report3 text on failures (Q6 A) |
| K14 Dev and CI | Compose Keycloak + realm import; Testcontainers + WireMock fake (Q8 A) | Real Google (Q8 C); keep the backend's fake (Q8 B) | One broker path in dev and CI; CI never calls Google |
| Q1 Operators | Separate realm `tsolve-ops`, own client, Google IdP without `hd`, second factor, allow-list second check | Role in `tsolve` (A); allow-list only (C) | Operator privileges never mix with tenant IdPs |
| Q2 Jira link | Single-use link request in Redis, confirm page, POST with CSRF | Code check (B); confirm on Keycloak's theme (C) | Closes the "no click" phishing; no signing key |
| Q3 Hosting | Same VPS behind Caddy, own database and user, 1–1.5 GB heap cap, in the nightly `pg_dump`, backup owner DevOps | Separate Postgres (B); separate VPS (C) | One container, one backup job |
| Q4 Redirect URI not added | No gate in the data model: the Workspace is Active at creation; the runbook does the test sign-in before the company is told; Google's refusal shows the normal sign-in error | Block until a test sign-in (A, a `PENDING_SIGN_IN` status); a separate flag (C) | One platform admin follows the runbook; no extra status or operation |
| Q5 In-progress work | Salvage the Task 10 tree by patch; Jira per the step-1 hand-off (KAN-229/230/231/235 stay Done; KAN-232 → Tasks 26a and 26b, KAN-233 → Task 29, KAN-234 → Task 30, KAN-106 → Task 27, KAN-247 keeps adapters and `FirstAdminCreator`; new issues for Tasks 23, 24, 28, 31) | Discard (B); commit then remove (C) | Keeps about 20 working files and tests |
| Q6 Failure display | Callback failures on `/login?error=`; the rest on Keycloak's themed page | All on Keycloak (C); all in the app (B, not possible) | B is not fully possible |
| Q7 Logout | 200 `{ logoutUrl }` | Back-channel | See K10 |
| Q8 Dev upstream | WireMock fake in compose as `google-acme` / `google-beta` | Keep `DevGoogleIssuer` (B) | The backend serves no IdP |
| Q9 Authorization request store | Redis, key = SHA-256 of the `__Host-TSOLVE_AUTHREQ` cookie | Signed cookie (B) | Reuses Redis, no new key |
| Q10 Claim | `organization` and `hd` both | One of them | Catches a misconfigured IdP or Organization |
| Q11 `sub` bound elsewhere | MSG09 and a `WARN` with the Account id; rebind is a runbook step | A new message and an Admin button (B); auto-rebind (C) | C reopens takeover |
| Q12 Provisioning race | Advisory lock, re-read and verify, compensating deletes by alias | A client per Workspace (B) | Keeps K5 |
| Q13 Hardening | Realm and IdP settings of AC-001.64, with tests | Defaults (B) | Closes R4 and R5 |
| Q14 Admin credentials | `tsolve-provisioner`, least roles, only in `ops` / Console | Master admin (B) | Least privilege |
| Q15 PR #119 | `WebSessionFilter` wins; their second filter is dropped on our side in Task 26a; a note to the owner (written by Hiếu) | Merge as is (B) | One session model |
| Q16 Google client values | Provisioning profile only | By hand in the console (B) | The only fully automatic K6 |

### Edge cases and risks (Keycloak re-plan)

Dimensions analyzed: user types, input extremes, timing, state transitions, environment, error cascades,
authorization, data integrity, integration, compliance. Skipped: scale (the volume is a few sign-ins per minute per
company), business logic (no amounts). Rows 103–130; the rows of the older section that this section replaces are
listed in its banner. Every Critical or High row has its test task.

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 103 | STORY-001 | Authorization | The token has no `organization` or no `hd` claim (R3: the mappers are missing or removed) | Critical | MSG122, no session (AC-001.11); the realm import carries both mappers (AC-001.65) and a test proves it | Tasks 23, 25b |
| 104 | STORY-001 | Authorization | `kc_idp_hint` removed or edited in the browser (R4) | High | The login page lists no IdP and offers no form (AC-001.64) | Task 23 |
| 105 | STORY-001 | Authorization | First broker login offers "review profile" or "link existing account by email" (R5) | High | The custom flow creates the user and never links by email (AC-001.64) | Task 23 |
| 106 | STORY-001 | Authorization | The hint is edited to Workspace B's IdP on A's site (R6) | High | The token's Organization is B's: MSG122 (AC-001.11); two-Workspace test | Task 25b |
| 107 | STORY-001 | Timing | Two Workspace creations at once rewrite `tsolve-web`'s redirect list (R7) | High | One at a time; every earlier URI is still there after each write (AC-001.59) | Task 28 |
| 108 | STORY-001 | Error cascades | `provision` fails after the Organization exists, or the platform undoes the Workspace (R8) | High | The objects created are deleted in reverse order; a repeated call is harmless (AC-001.59, .62) | Task 28 |
| 109 | STORY-001 | Authorization | The Admin API credentials are read from a Workspace web replica (R9) | High | Not loaded there; least roles in the profiles that have them (AC-001.61) | Task 28 |
| 110 | STORY-001 | Error cascades | Keycloak is down (R10) | Medium | Nobody can start a sign-in; signed-in sessions in Redis continue; compose and Caddy health check; ADR-0003 upgrade note | Task 23 |
| 111 | STORY-001 | Environment | The backend reaches Keycloak at a name that differs from the token issuer (R11) | Medium | The start-up check refuses to start and names the issuer (NFR 4) | Task 23 |
| 112 | STORY-001 | Compliance | A company is removed but its Keycloak users, Organization or IdP remain (R12); PII in Keycloak logs | High | `remove` deletes them (AC-001.62); the runbook's "remove a company"; no email in Keycloak logs | Task 28 |
| 113 | STORY-001 | Data integrity | A Keycloak user is re-created and gets a new `sub` (R13) | High | MSG09 and a `WARN` with the Account id only (AC-001.56); the runbook rebinds after an identity check | Task 25a |
| 114 | STORY-001 | User types | A shared computer: the next person clicks "Sign in" while Keycloak's SSO session lives (R14) | Medium | SSO Idle ≤ 60 min and Max ≤ 8 h (AC-001.63); logout ends Keycloak's session (AC-002.1) | Tasks 23, 27 |
| 115 | STORY-001 | Timing | The callback reaches another API replica than the sign-in start (R15) | High | The request is in Redis, no HTTP session, `NullSecurityContextRepository` (AC-001.55); a two-instance test | Task 23c |
| 116 | STORY-002 | Integration | A `fetch` POST cannot follow the redirect to Keycloak's end-session URL (R16) | Medium | The answer is 200 `{ logoutUrl }`; the browser navigates (AC-002.1, AC-002.8) | Task 27 |
| 117 | STORY-001 | Integration | The platform, `jira-app` or fe-foundation still use the old contract (R17) | High | Change requests sent before the dependent code (this plan, "Platform change requests") | Tasks 28, 30, 27 |
| 118 | STORY-001 | Environment | Google has not applied the new redirect URI yet, or the client's URI limit is reached (R18) | Medium | Google refuses on its own `redirect_uri_mismatch` page and never redirects back, so neither Keycloak, the callback nor the Workspace site shows it (not a themed-page case, AC-001.67); the protection is the runbook's test sign-in before the company is told with the registered redirect URIs, which lists the URIs and says to check the limit | Task 28 (runbook) |
| 119 | STORY-001 | Data integrity | A person's Google email changes (R19) | Low | The Account is found by `sub`; `app_user.email` is unchanged (design decision above) | Task 25a |
| 120 | STORY-001 | State transitions | A developer database already ran the unreleased `008` and `009` (R20) | Low | `docker compose down -v`; the card notes it | Task 24 |
| 121 | STORY-001 | Authorization | Jira-link phishing: the victim opens the attacker's link URL and confirms without reading (replaces row 46) | Critical | Mitigated, not closed: single-use 10-minute request, the person's own session, the confirm page names both accounts, CSRF POST; option B (a typed code) stays open (AC-001.69–.72) | Task 30 |
| 122 | STORY-001 | Authorization | Login CSRF: a callback URL (state and code) of the attacker's own sign-in is opened in the victim's browser (replaces rows 45 and 100) | Critical | The victim's browser has no matching `__Host-TSOLVE_AUTHREQ`: MSG141, no session (AC-001.6, .53) | Task 23c |
| 123 | STORY-001 | Integration | PR #119's `UserListSessionFilter` next to `WebSessionFilter` | High | One session model; the second filter is dropped from identity (Q15) | Task 26a |
| 124 | STORY-001 | State transitions | Sign-in tried before the test sign-in of the runbook (redirect URI not yet applied) | Medium | Google refuses; the Workspace site shows the normal sign-in error; no data-model gate (AC-001.73 retired) | Task 28 (runbook) |
| 125 | STORY-001 | Integration | The dev Workspaces resolve: `acme.localhost` and `beta.localhost` each resolve to their dev Workspace | Medium | Each host gives its fixed id, slug and Organization id | Task 23a |
| 126 | STORY-001 | Input extremes | An unknown host or an unknown id is looked up in the dev Workspaces | Medium | Resolves to nothing (no Workspace, no error) | Task 23a |
| 127 | STORY-001 | Data integrity | A DevAccount and its dev Workspace disagree on the domain | High | They agree: each DevAccount's email domain equals the Google domain of its Workspace | Task 23a |
| 128 | STORY-001 | Environment | The app runs without the profile `dev` | High | `DevWorkspaceLookup` exists only with the profile `dev`; otherwise `NoWorkspaceLookup` answers nothing | Task 23a |
| 129 | STORY-001 | Integration | Redis wiring: the app reaches Redis through `REDIS_HOST`, `REDIS_PORT` and `REDIS_PASSWORD`; a key is stored with a time to live | High | The app connects with those three settings; a key with a time to live expires | Task 23a |
| 130 | STORY-001 | Error cascades | Redis health: the `redis` health indicator when Redis is up | Medium | The indicator is UP, so `/actuator/health` stays 200 UP | Task 23a |

Open questions for the spec owner: none. Every row is closed by the answers Q1–Q16.

## STORY-003: My Profile — KAN-160 plan, 2026-10-31

**Scope.** identity STORY-003 (KAN-160, "[UC-03] Xem profile của tôi") on the web app only, on MSW mocks (D1,
Hiếu, 2026-10-31), plus AC-002.7 (the "Log out" button, Q7): Tasks 15–18 of `iteration-1/tasks.md`. The backend
criteria AC-003.4–AC-003.10 are not built here; they are Task 19 (a stub, no Jira issue yet, no `progress.json`
feature, Q11), which waits for the `ticket-repository` written-ticket lookup (KAN-132, To Do), the `wiki` viewable
Space lookup and be-foundation. The OpenAPI contract and the mock handlers follow AC-003.4–.10 exactly, so the
backend can come later without changing the screen; mock tests cite them as "contract mirror of AC-003.x", never as
passed. This plan is stacked on `chore/KAN-231-identity-plan` (PR #94, Q0). Layout: the four Figma frames of 1.2.1
(Q1); where a frame and a decision disagree, the decision wins (listed under "Figma vs decisions").

### Flow (STORY-003)

```text
/profile (existing route, EMPLOYEE guard; the app shell shows the title "My Profile")
  MyProfilePage
   ├─ AccountSection        ← useSession()            no request (AC-003.1)  + "Log out" → useSignOut() (AC-002.7)
   ├─ ProjectsSection       ← useSession().projects    no request (AC-003.1); hidden when empty (Q4)
   ├─ SpacesSection
   │    ├─ "Spaces I manage"   ← useSession().managedSpaces, only for KNOWLEDGE_MANAGER (AC-003.2)
   │    └─ "Spaces I can view" ← useMyViewableSpaces() → GET /api/v1/me/spaces (AC-003.3, .14)
   └─ MySolutionsSection    ← useMySolutions({ page: urlPage - 1, size: 20 }) → GET /api/v1/me/solutions
        (AC-003.11–.14) · Ticket ID of a canOpen row = router link to paths.ticketDetail(ticketId) (AC-003.12)
```

The two requests start in parallel when the page opens. Each data section has its own spinner, empty and error
state, so one failure never hides the account information (AC-003.14). The account part needs no request: it is
the session (fe-foundation AC-001.2), already loaded by the guard.

### Components and files (STORY-003)

All in `frontend/src/features/my-profile/` unless stated.

| Where | What | Task |
|-------|------|------|
| `pages/MyProfilePage.tsx` | Replaces the placeholder; layout only: the sections in Figma order, stacked, gap from the theme | T15 (T16, T18 add sections) |
| `components/AccountSection.tsx` | `SectionCard` from the kit with no `title`; its own header row: "Account" (left) and the "Log out" button (right, `Button` default). Rows as label / value pairs (antd `Descriptions`, one column, or the kit's equivalent): Full name, Email, Roles, Department, Departments (Q4: a row with no value is not rendered) | T15 |
| `components/ProjectsSection.tsx` | `SectionCard` "Projects" with a `DataTable` (label "Projects", no paging): Jira project key, Department, Role in Project. Not rendered when `projects` is empty (Q4) | T15 |
| `components/SpacesSection.tsx` | `SectionCard` "Spaces" with two titled lists: "Spaces I manage" (T15) and "Spaces I can view" (T16) | T15, T16 |
| `components/SpaceList.tsx` | A list of `SpaceRef` names, each with the folder icon of the frame (`FolderOpenOutlined`, `aria-hidden`), one line, cut with "…" and the full name on hover | T15 |
| `components/MySolutionsSection.tsx` | `SectionCard` "My Solutions" with the kit's `DataTable` (label "My Solutions", server paging, `emptyCode` MSG04, `error`, no `onRetry`, Q9); the Ticket ID of a `canOpen` row is `cells.link`; no change to the shared `DataTable` | T18 |
| `hooks/useMyViewableSpaces.ts` | `useGetMyViewableSpaces()` (generated) | T16 |
| `hooks/useMySolutions.ts` | `useGetMySolutions(params, { query: { placeholderData: keepPreviousData } })`, as `features/users/hooks/useUsers.ts` | T18 |
| `lib/labels.ts` | `PROJECT_ROLE_LABELS: Record<ProjectRole, string>` = `PROJECT_MANAGER` → "Project Manager", `MEMBER` → "Ticket Resolver" (Q3) | T15 |
| `mocks/data.ts` | Fixtures keyed by `DevUserId`, see "Mock data" | T16, T17 |
| `mocks/handlers.ts` | `getMyViewableSpaces` (T16), `getMySolutions` (T17); `resetMockData()` restores the fixtures | T16, T17 |
| `frontend/openapi/tsolve-api.yaml` | The two endpoints and two schemas, see "Contract" | T16, T17 |
| `frontend/src/shared/api/generated/**` | `npm run generate:api` only, never edited by hand | T16, T17 |

Reused, not changed: `useSession`, `useSignOut`, `ROLES` / `ROLE_LABELS` (`@/shared/auth`); `SectionCard`,
`DataTable`, `cells.link`, `cells.text`, `cells.tag`, `LoadingState`, `ErrorState`, `EmptyState` (`@/shared/ui`);
`formatDate`, `PAGE_SIZE` (`@/shared/lib`); `paths.ticketDetail`, `useListSearchParams` (`@/shared/routing`);
`problem`, `MOCK_DELAY_MS` (`@/shared/api/mocks`); `getDevUser` (`@/shared/auth`). No edit to `app/`,
`src/mocks/session.ts` or other features: the feature's handlers read the current dev user with `getDevUser()`
(verified 2026-10-31; no adapter needed, unlike KAN-109/110).

Values shown in AccountSection (AC-003.1):
- Full name `user.fullName`, Email `user.email`.
- Roles: `ROLE_LABELS` of `roles`, in the order of `ROLES` (Employee first), joined with ", " (fe-foundation
  AC-001.1).
- Department: `department.name`, only when the user holds `PROJECT_MANAGER` and `department` is not null.
- Departments: the names of `departments`, joined with ", "; no row when empty.
- Projects table: `jiraProjectKey` (Q2: `/me` has no Project name; no `/me` contract change), `departmentName`,
  `PROJECT_ROLE_LABELS[roleInProject]`, in the order `/me` gives.

### Figma layout per frame (1.2.1)

| Frame | Sections, top to bottom | What each shows |
|-------|-------------------------|-----------------|
| 1.2.1 main (Ticket Resolver, two Departments) | Account · Projects · Spaces · My Solutions | Account: header "Account" with "Log out" at the top right; Full name, Email, Roles ("Employee, Ticket Resolver"), Departments ("IT, HR"). Projects: table. Spaces: "Spaces you can view" with three Spaces, folder icons. My Solutions: six status count cards, three filters, table Ticket ID (link) · Title · Project · Status (Status Tag) · Latest decision · Date (DD/MM/YYYY) |
| (b) Project Manager + Knowledge Manager | Account · Projects · Spaces | Account: Roles "Employee, Project Manager, Knowledge Manager", Department "IT" (no Departments row). Projects: two rows, role "Project Manager". Spaces: "Spaces you manage" (two) above "Spaces you can view" (two). No My Solutions section |
| (c) Employee only | Account · Projects · Spaces | Account: Full name, Email, Roles "Employee". Projects card with "Not a member of any Project.". Spaces: "Spaces you can view" with one Space. No My Solutions section |
| (d) My Solutions, no results | as main | The table header with the empty state: inbox icon and "No results found." (MSG04), here under the "Expired" status filter |

What this plan builds from the frames: the section order, cards and headings; the Account header with "Log out" at
the top right; label / value rows; the Projects table; one "Spaces" card with the managed list above the viewable
list and folder icons; the My Solutions table with the Ticket ID as a link, Status Tag, a date column and MSG04 with
the inbox icon (the kit's `EmptyState`). The red numbered markers are annotations, not UI.

#### Figma vs decisions (the decision wins; for Hiếu)

1. Projects table: the frame has a "Project ID" column (PRJ-0001). `/me` has only the UUID `projectId` and no
   display id, so the table has three columns: Jira project key, Department, Role in Project (Q2).
2. Role in Project: the frame shows "Member"; Q3 shows `MEMBER` as "Ticket Resolver".
3. Empty Projects: frame (c) shows the card with "Not a member of any Project."; since 1/11 (AC-003.16, MSG143) the
   card is shown with that text, as in the frame (Q4 reversed for this card).
4. Employee-only Spaces: frame (c) lists "Sao Mai Corporation"; the Employee-only dev user has no viewable Spaces
   (Q8), so frame (c)'s user sees the Spaces card with "No Spaces found" (AC-003.15, MSG142) in mock mode.
5. My Solutions for a user who writes no Solutions: frames (b) and (c) have no My Solutions section; AC-003.13 and
   Q4 keep the section for every user, with MSG04 when it has no rows.
6. My Solutions extras in the main frame — the six status count cards, the status / Project / date-range filters and
   the "Latest decision" column — are in neither STORY-003 nor `MySolution`; not built (R2 A). Later, and needs a
   spec change (Change Mode: new criteria, `MySolution.latestDecision`, filter parameters, a counts endpoint).
7. Date column header: the frame says "Date"; Q6 says "Updated".
8. Date: Q6 said browser local time unless a shared format exists; it exists — `formatDate` in `@/shared/lib`,
   (`frontend/src/shared/lib/date.ts`; `DD/MM/YYYY` in Vietnam time, `Asia/Ho_Chi_Minh`) — so the column uses it (same
   text as the frame). Confirmed by Hiếu 31/10.
9. Section wording: the frames say "Spaces you manage" / "Spaces you can view"; AC-003.2 and AC-003.3 name the lists
   "Spaces I manage" / "Spaces I can view", and the spec wins (rewording it is a spec change).
10. Project column of My Solutions: the frame shows the Jira key (ITSUP); the contract field is `projectName`, so the
    column shows the Project name the backend sends (the mocks use names such as "IT Support").
11. Row click: AC-003.12 (reworded 31/10, R1 A) makes only the Ticket ID of a `canOpen` row a link (`cells.link`, as
    Review Queue and Ticket List). The row itself is not clickable and the shared `DataTable` is not changed. A
    `canOpen: false` row shows the Ticket ID as plain text.

### State rules (STORY-003)

- Hide what is empty (Q4) for the Account rows and "Spaces I manage": no Department → no row; empty Departments →
  no row; no `KNOWLEDGE_MANAGER` or no managed Spaces → no "Spaces I manage" list.
- Two cards are always rendered (decided by Hiếu 1/11, reversing Q4 and the 31/10 rule for these cards):
  - Projects card (AC-003.16): the table, or when the user has no Projects the grey text MSG143 "Not a member of any
    Project." in place of the table.
  - Spaces card (AC-003.15): "Spaces I can view" shows its spinner at once (no 150 ms delay) while loading; loaded
    and empty → the grey text MSG142 "No Spaces found" under "Spaces I can view"; when it fails, MSG06 in place of
    that list (AC-003.14; "Spaces I manage" still shows).
- My Solutions is always rendered: spinner on the first load (`DataTable` `loading`), MSG04 when `content` is empty
  on page 1 (AC-003.13), MSG06 on failure (AC-003.14, `ErrorState` without "Try again", Q9).
- Paging (Q5): `useListSearchParams(defaultSort: TableSort)` (`shared/routing/useListSearchParams.ts`) requires a
  default sort and the contract has none. The section passes `{ field: 'updatedAt', order: 'desc' }` (the backend's
  order, newest first) only to satisfy the signature; the hook's `sort`, `setSort` and `apiPaging` are ignored and no
  column is sortable. The hook gives the 1-based `page` from `?page=` (invalid or below 1 → 1); the request sends
  `{ page: page - 1, size: PAGE_SIZE }` explicitly (no `sort`); `DataTable` pagination: 20 per page, no size changer, "Total N items". Changing page sets `?page=`; back
  from Ticket Detail returns to the same page.
- A page past the last (decided by Hiếu 31/10): when the answer has `totalPages > 0` and `page > totalPages`, the
  section replaces the URL with the last page (`?page=<totalPages>`, history replace); when `totalPages` is 0 and
  `page > 1`, it goes to page 1, which then shows MSG04.
- Rows (AC-003.12, reworded 31/10, R1 A): only the Ticket ID cell of a `canOpen` row is `cells.link` to
  `paths.ticketDetail(row.ticketId)` (keyboard and screen reader); the row itself is not clickable. A `canOpen: false`
  row shows the Ticket ID as plain text: no link, no pointer, no click handler, nothing focusable. The shared
  `DataTable` is not changed.
- Columns (AC-003.11): Ticket ID (`ticketKey`), Title (`cells.text`, ellipsis and full text on hover), Project
  (`projectName`, `cells.text`), Status (`cells.tag(status)`), Updated (`formatDate(updatedAt)`); fixed widths so
  the table fits 1366 px without sideways scroll (decided by Hiếu 31/10: Ticket ID 120, Project 180, Status 170,
  Updated 120, Title takes the rest).
- Log out (AC-002.7): the button calls `useSignOut()`; it is `loading` and ignores further clicks until the
  promise settles (one logout request); `useSignOut` clears the cache and opens Login (fe-foundation AC-008.1–.3).

### Contract (STORY-003)

Pattern as "Contract (Q6)" above. New tag `profile` ("My Profile: the signed-in user's Spaces and Solutions (SRS
III.1.2.1)"); `getMe` stays under `session`.
- `GET /api/v1/me/spaces`, `operationId: getMyViewableSpaces`, tag `profile`, no parameters: 200 `SpaceRef[]`
  (description: sorted by name; empty while the `wiki` lookup is missing, AC-003.4/.5); 401 `Problem` (`MSG07`,
  AC-003.10). Added in T16.
- `GET /api/v1/me/solutions`, `operationId: getMySolutions`, tag `profile`, parameters the shared
  `#/components/parameters/Page` and `#/components/parameters/Size` (min 0 / default 0; 1–100 / default 20 — exactly
  AC-003.6), no `Sort`: 200 `MySolutionPage` (description: newest `updatedAt` first; empty while the
  `ticket-repository` lookup is missing, AC-003.6/.8); 400 `Problem` with `fieldErrors` (`MSG06`, AC-003.9); 401
  `Problem` (`MSG07`, AC-003.10). Added in T17.
- Schemas (T17), exactly the spec's: `MySolutionPage` `{ content: MySolution[], page: PageMetadata }` (both
  required, as `ReviewQueuePage`); `MySolution` with all seven fields required: `ticketId` (string, uuid),
  `ticketKey`, `title`, `projectName` (strings), `status` (`$ref TicketStatus`), `updatedAt` (string, date-time),
  `canOpen` (boolean). Reused as they are: `SpaceRef`, `PageMetadata`, `TicketStatus`, `ProblemDetail`.
- No user id or Workspace in either request (AC-003.7): the contract has no such parameter, and a handler test
  asserts the mock ignores any extra query parameter.
- `npm run generate:api` regenerates; the PR's commit leaves no diff in `src/shared/api/generated`.

### Mock data and scenarios (STORY-003)

Fixtures in `mocks/data.ts`, keyed by `DevUserId`, fixed UUIDs (guideline: ids are UUID strings), Vietnamese titles:

| Dev user | My Solutions | Spaces I can view |
|----------|--------------|-------------------|
| `employee` | 0 (MSG04, Q8) | none (the Spaces card shows MSG142 "No Spaces found", AC-003.15, Q8) |
| `tr` | 45 (3 pages), all six statuses, three Projects, 3 rows `canOpen: false` | 3 |
| `tr-two-departments` | 21 (page 2 has one row) | 4, including both Departments' Spaces |
| `pm` | 20 (exactly one page, no second page) | 2 |
| `km` | 0 | 3, including its managed "IT Space" (a Space can be in both lists, footnote ⁸) |
| `admin` | 0 | 1 |
| `pm-km` | 3 | 2, including its managed "HR Space" |
| `admin-tr` | 5, one 200-character title, 2 rows `canOpen: false` | 6 (a long name of 120 characters) |

Handler rules (mirror of the contract; MSW, `delay(MOCK_DELAY_MS)`):
- No dev user → 401 `MSG07` (both endpoints; the global 401 handling then opens Login).
- `getMyViewableSpaces`: the user's Spaces sorted by name (`localeCompare`, `vi`), whatever the fixture order.
- `getMySolutions`: sorted by `updatedAt` descending; `page` default 0, `size` default 20; a missing parameter takes
  its default; `page` not an integer or below 0 → 400 with `fieldErrors: [{ field: 'page', code: 'MSG06' }]`;
  `size` not an integer, below 1 or above 100 → 400 with the `size` entry (both entries when both are wrong); the
  page is `slice(page * size, page * size + size)` with `PageMetadata` (`size`, `number`, `totalElements`,
  `totalPages`); a page past the end → 200 with empty `content` and the real totals.
- Any other query parameter (`userId`, `workspaceId`) is ignored.
- Empty state from data (`employee`, `km`, `admin`); the error state only in tests with `server.use` (500), no mock
  switch (Q8).
- Mock ticket ids are this feature's own UUIDs (no import from the `tickets` feature's mocks); Ticket Detail (5.5) is
  a placeholder today, so any id opens it.

### Test approach (STORY-003)

- Vitest + Testing Library + MSW, `renderApp('/profile', { user })` per dev user; during test-first work only
  `npm run test -- src/features/my-profile`; the full frontend verify once per
  commit. `it` names start with the AC id; mock-contract tests start with "contract mirror of AC-003.x".
- T15: AC-003.1 for `employee`, `tr-two-departments`, `pm`, `km`, `pm-km`, `admin-tr` (rows present and hidden per
  Q4, Role order, Department only for the PM, the Projects labels); AC-003.2 (list for `km` and `pm-km`, none for
  `pm`); AC-002.7 (click → `POST /auth/logout` once, Login opens, the query cache is empty).
- T16: AC-003.3 (names sorted); AC-003.15 (spinner at once, no delay; `employee` shows the Spaces card with MSG142; the card shows for every dev user); the Spaces half of AC-003.14 (`server.use` 500 on
  `/me/spaces` → MSG06 in the Spaces card, account and "Spaces I manage" still shown); handler tests: sorting, 401.
- T17 (handler tests only): order, defaults, page boundaries 0 / 1 / 20 / 21 / 45, page past the end, each 400 case
  with its `fieldErrors`, 401, extra parameters ignored.
- T18: AC-003.11 (columns, Status Tag text, `formatDate`, 20 rows, page 2 via the pager and via `?page=2`, size=20
  in the request); AC-003.12 (a click on the Ticket ID of a `canOpen` row opens `/tickets/:ticketId`; a `canOpen: false` row has no
  link and a click stays on `/profile`); AC-003.13 (MSG04 for `employee`); AC-003.14 (500 on `/me/solutions` →
  MSG06 in My Solutions, account and Spaces still shown); row 63: `?page=9` for `pm` (one page) → page 1,
  `?page=4` for `tr` (three pages) → page 3.
- Browser check at 1366 × 768 in mock mode after each PR, against the frames (dev users `tr`, `pm-km`, `employee`).

### Key decisions (STORY-003)

| Decision | Choice | Alternatives considered | Rationale |
|----------|--------|------------------------|-----------|
| Scope (D1) | Web app on MSW mocks; backend AC-003.4–.10 deferred to Task 19 (stub, Q11) | Backend now | No backend dependency is ready (KAN-132 To Do, `wiki` not built); the contract fixes the shape now |
| Stacking (Q0) | Branched from `chore/KAN-231-identity-plan` (PR #94) | Wait for PR #94 | The plan files are the same; rebase when #94 merges |
| Layout (Q1) | The four Figma frames of 1.2.1; decisions win where they differ | SRS wording only | Frames exist; differences listed above |
| Projects (Q2) | Shown by `jiraProjectKey`, with Department and role; no `/me` change | Add `projectName` to `/me` (fe- and be-foundation contract change) | All else is already in `/me` |
| Project role label (Q3) | `PROJECT_MANAGER` "Project Manager", `MEMBER` "Ticket Resolver" | "Member" | Hiếu's choice; the label of the Role that makes a member |
| Empty values (Q4; changed by Hiếu 1/11) | Hide empty Account rows and "Spaces I manage". The Spaces and Projects cards are always shown, with MSG142 "No Spaces found" (AC-003.15) and MSG143 "Not a member of any Project." (AC-003.16); My Solutions keeps MSG04 (AC-003.13, not changed; KAN-238 decides) | "—" rows; hiding the two cards (the original Q4) | A hidden card looked like a missing feature in QA; the Figma frame (c) already shows the Projects text |
| Paging (Q5) | Kit `DataTable` pagination, 20 fixed, `size=20` sent, page in the URL (1-based; 0-based to the API) | Page in state only; "Load more" | AC-003.11; back from Ticket Detail keeps the page; like the User List |
| Date (Q6) | Header "Updated", the shared `formatDate` (`DD/MM/YYYY`, Vietnam time, `shared/lib/date.ts`; confirmed by Hiếu 31/10) | `DD/MM/YYYY HH:mm`; relative dates | Q6 said "or the shared format if one exists" — it does |
| Log out (Q7) | Button at the top right of the Account card, `useSignOut()`, in Task 15 | Bottom of the page; leave to a KAN-106 plan | One-button FE criterion on this screen; no backend |
| Mock scenarios (Q8) | Empty from data; error only in tests | A `localStorage` switch; a `?mock=` parameter | No mock concern in product code |
| Error state (Q9) | MSG06 text only, no "Try again" | A retry button (needs a criterion) | No spec change |
| PRs (Q10) | PR 1 = T15, PR 2 = T16, PR 3 = T17 + T18 (~450 lines) | A contract-only PR; PR 3 split | Each endpoint's YAML in its screen's PR (spec rule); contract, mock and table are one behaviour |
| Deferred backend (Q11) | Task 19 stub; no feature until it has a Jira issue | A `pending` feature now; a design note only | Visible to the evaluator without an idle feature |
| OpenAPI tag (accepted minor) | `profile` | `session` | `/me` stays the session; the profile reads are their own group |
| Row click (R1 A, Hiếu 31/10; AC-003.12 reworded) | Only the Ticket ID of a `canOpen` row is a link (`cells.link`); the row is not clickable; a `canOpen: false` row shows plain text | Whole row clickable via a new prop pair on the shared `DataTable` (a shared edit); antd `Table` directly in the feature (bypasses the kit) | Same as Review Queue and Ticket List; no shared edit, no FE Lead gate |
| Page past the last (accepted minor) | Go to the last page (history replace) | MSG04 | The user asked for a page that moved, not for an empty list |
| Loading (accepted minor) | A spinner per section | One page spinner | One slow request never hides the rest |
| Log out header (decided in planning, builder check) | `SectionCard` without `title`, the card renders its own "Account" header row with the button | An `extra` prop on `SectionCard` (another shared edit) | No shared edit for one button |

### Edge cases and risks (STORY-003)

Dimensions analyzed: user types, input extremes, timing, scale, state transitions, environment, error cascades,
authorization, data integrity, integration, compliance. Skipped: business logic (no amounts, quotas or counts —
the Figma count cards are not built, Figma vs decisions item 6).

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 53 | STORY-003 | User types | Employee only: no Department, Departments, Projects, managed or viewable Spaces, no Solutions | High | Account card with Full name, Email, Roles "Employee"; Projects card with MSG143 "Not a member of any Project."; Spaces card with MSG142 "No Spaces found" and no "Spaces I manage"; My Solutions with MSG04 (AC-003.1/.13/.15/.16) | Tasks 15 (follow-up Quick change), 16, 18 |
| 54 | STORY-003 | User types | Project Manager with `department` set; a Ticket Resolver whose `department` is null; a user whose `department` is set but who is not a PM (bad data) | High | "Department" row only for the PM (AC-003.1); never for a non-PM | Task 15 |
| 55 | STORY-003 | User types | A user with four Roles (`admin-tr` plus PM / KM in a test fixture) | Medium | All labels, Employee first, in `ROLES` order (fe-foundation AC-001.1) | Task 15 |
| 56 | STORY-003 | User types | KM with no managed Spaces; PM + KM (`pm-km`); PM without KM | Medium | List only when KM and not empty; none for the PM alone (AC-003.2, Q4) | Task 15 |
| 57 | STORY-002 | Timing | "Log out" clicked twice, or while the Spaces and Solutions requests are in flight | Medium | One logout request (button loading); cache cleared; Login opens; late answers do not repaint the profile (AC-002.7, fe-foundation AC-008.1–.3) | Task 15 |
| 58 | STORY-003 | Input extremes | 200-character title, 120-character Space name, Vietnamese diacritics in titles, names and Project names | Low | One line, "…", full text on hover; no layout break at 1366 px | Tasks 16, 18 |
| 59 | STORY-003 | Input extremes | `?page=abc`, `?page=0`, `?page=-3`, `?page=1.5` in the URL | Medium | Read as page 1 (`useListSearchParams`); the request sends `page=0` | Task 18 |
| 60 | STORY-003 | Input extremes | API called with `page=-1`, `size=0`, `size=101`, `size=abc`, both wrong | High | 400 with a `fieldErrors` entry `MSG06` for each wrong parameter (contract mirror of AC-003.9) | Task 17 |
| 61 | STORY-003 | Scale | 0, 1, 20, 21, 45 Solutions | High | MSG04 at 0; one page and no second page at 1 and 20; page 2 with one row at 21; three pages at 45 (AC-003.11/.13) | Tasks 17, 18 |
| 62 | STORY-003 | Scale | Many viewable Spaces (a Workspace with dozens) | Low | Plain list, no paging (the contract has none); the page scrolls | Task 16 |
| 63 | STORY-003 | State transitions | `?page=3` when only 2 pages exist (Solutions removed meanwhile); `?page=2` when there are now none | Medium | Last page (history replace); none → page 1 with MSG04 (decided by Hiếu 31/10) | Task 18 |
| 64 | STORY-003 | State transitions | Back from Ticket Detail after opening a row on page 2 | Medium | `/profile?page=2` shows page 2 again (Q5) | Task 18 |
| 65 | STORY-003 | Timing | Slow network; page clicked twice while the previous page loads | Medium | Section spinner on first load; previous rows kept while the next page loads (`keepPreviousData`); the last click wins | Task 18 |
| 66 | STORY-003 | Error cascades | `/me/solutions` 500 while `/me/spaces` 200, and the reverse; both 500 | High | MSG06 only in the failing section; account (and "Spaces I manage") always shown (AC-003.14) | Tasks 16, 18 |
| 67 | STORY-003 | Error cascades | `/me/spaces` or `/me/solutions` answers 401 (session ended meanwhile) | High | The global 401 handling opens Login (fe-foundation); not MSG06 in the section | Tasks 16, 18 |
| 68 | STORY-003 | Error cascades | The Role is removed while the page is open, so a 403 comes from `GET /api/v1/me/solutions` or `/summary` (AC-003.17) | Low | The My Solutions section shows MSG08 in an `ErrorState` (the counters and the table each show it); the other cards stay (AC-003.14, AC-003.17) | Task 18 |
| 69 | STORY-003 | Authorization | Row with `canOpen: false` | High | Ticket ID is plain text; no link, no pointer, no click handler, nothing focusable; a click stays on `/profile` (AC-003.12) | Task 18 |
| 70 | STORY-003 | Authorization | Request carrying `userId` / `workspaceId` (someone edits the URL or the call) | Critical | Backend risk, Task 19. Mock ignores them and answers for the dev user (contract mirror of AC-003.7); the real guarantee is Task 19 | Tasks 17, 19 |
| 71 | STORY-003 | Authorization | `canOpen: true` row for a user whose Roles cannot open Ticket Detail (route guard TR / PM) | Medium | Not produced by the mocks (only TR / PM users get `canOpen: true`); in production the guard's own page answers; `canOpen` is the backend's UC-24 decision | Task 17 |
| 72 | STORY-003 | Environment | Keyboard and screen reader on the table and the lists | Medium | The Ticket ID link is reachable by Tab and opens with Enter; the table has the accessible name "My Solutions"; card titles are visible text; icons `aria-hidden` | Tasks 15, 18 |
| 73 | STORY-003 | Environment | Browser in another time zone (UTC−5) looking at an `updatedAt` near midnight Vietnam time | Low | Date shown in Vietnam time by `formatDate`, the same for every viewer | Task 18 |
| 74 | STORY-003 | Environment | Screen 1366 × 768 | Low | Table fits without sideways scroll; long values cut with "…" | Tasks 15, 18 |
| 75 | STORY-003 | Data integrity | Rows shift between page requests (a new Solution arrives): a row repeats on page 2 or is skipped | Low | Accepted with offset paging (the contract's); `rowKey` = `ticketId` keeps React keys unique per page | Task 18 |
| 76 | STORY-003 | Data integrity | A Space in both "Spaces I manage" and "Spaces I can view" | Low | Shown in both lists (viewable includes managed, Permission Matrix footnote ⁸) | Task 16 |
| 77 | STORY-003 | Integration | A future backend sends an unknown `status` | Low | The type checker rejects it at build (fe-foundation AC-007.3); at runtime the Status Tag's own fallback | Task 18 |
| 78 | STORY-003 | Integration | Contract drift: `generate:api` leaves a diff, or the YAML and the handlers disagree | High | The PR's verify runs `generate:api` with no diff; handlers typed with the generated types | Tasks 16, 17 |
| 79 | STORY-003 | Integration | Real backend while the lookups are missing: `/me/spaces` `[]`, `/me/solutions` empty page | Medium | The Spaces card shows MSG142 "No Spaces found" (AC-003.15); MSG04 in My Solutions — the same screens as the `employee` mock (AC-003.5/.8) | Tasks 16, 18 |
| 80 | STORY-002 | Compliance | The next person at the same computer after "Log out" | High | No profile data left: the cache is cleared by `useSignOut` and Login opens (AC-002.7, fe-foundation AC-008.1) | Task 15 |
| 81 | STORY-003 | Compliance | Personal data in the URL or in logs | Medium | The URL holds only `page`, `status`, `projectId`, `from` and `to` (none is personal data); nothing about the user is logged or stored by the feature | Tasks 15, 18 |
| 82 | STORY-003 | State transitions | "Spaces I can view" loaded empty (`employee`; any user with none), including a Knowledge Manager with managed Spaces but no viewable ones (bad data) | High | Spaces card stays; grey MSG142 "No Spaces found" under "Spaces I can view"; "Spaces I manage" unaffected (AC-003.15) | Task 16 |
| 83 | STORY-003 | Timing | `/me/spaces` is slow (first load) | Medium | The card and its spinner show at once, with no 150 ms delay and no flash of MSG142 before the answer (AC-003.15) | Task 16 |
| 84 | STORY-003 | User types | User with no Projects (`employee`, `km`, `admin`) | High | Projects card stays; grey MSG143 "Not a member of any Project." in place of the table, no table header (AC-003.16) | Task 15 (follow-up Quick change) |
| 85 | STORY-003 | Input extremes | `?status=FOO`, `?status=expired`, `?projectId=not-a-uuid`, `?from=2026-02-30`, `?to=31/10/2026`, `to` before `from`; a well-formed `projectId` that is not one of the summary's Projects | Medium | Dropped silently: no 400, no MSG06, filter at its default (AC-003.22); `to` before `from` drops both; dropped values are removed from the URL (history replace); an unknown `projectId` is dropped once the summary has loaded, kept while it loads or fails | Task 21 |
| 86 | STORY-003 | Environment | Date range at the UTC+7 day boundary (17:00:00Z vs 16:59:59Z) | Medium | Whole days in Vietnam time, inclusive (AC-003.19); `from`/`to` sent as `YYYY-MM-DD` | Task 21 |
| 87 | STORY-003 | Timing | Two filter changes while the table loads | Medium | Last change wins; previous rows kept meanwhile | Task 21 |
| 88 | STORY-003 | Error cascades | Summary fails, so there are no Projects for the filter | Medium | Project filter shows only "All Projects"; Status and Date range work (D3); Status options follow the counters' order; no match shows MSG04 only | Task 21 |
| 89 | STORY-003 | State transitions | Paging inside a filtered list; back from Ticket Detail with filters; a date range with only `from` or only `to` in a shared URL | Medium | Filters kept in the URL and the request; back restores filters and page; the picker writes the URL only when both days are picked, a one-sided shared URL is honoured | Task 21 |

Open questions for the spec owner: none blocking this plan. The Figma extras of item 6 (count cards, filters,
"Latest decision") are specified now (AC-003.18 to AC-003.23, Tasks 20 and 21). For Hiếu to decide later: the
"you" / "I" wording of item 9.

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
| S1 | User types, authorization | Lookup includes self, inactive, wrong-role or foreign-Workspace candidates; a different-Department KM exists | High | Exclude the first four; allow the other-Department KM; reject forged choices on both mutations (AC-009.3–.4) | Task 20 |
| S2 | Input extremes, scale, business logic | Zero, one or several affected Spaces; incomplete, repeated or unrelated Space ids; one recipient receives several Spaces | High | Require exactly the affected set; allow one eligible recipient for multiple Spaces; preserve unrelated assignments (AC-009.1–.4, .7) | Task 20 |
| S3 | Timing, error cascades | Recipient loses KM or becomes inactive after options load; one of several choices becomes invalid | High | Revalidate on submit; reject without partial account, Space or Project writes (AC-009.4, .6–.7) | Task 20 |
| S4 | State transitions, data integrity | Remove KM, remove only some Spaces, cancel, then retry after failure | High | Transfer only removed obligations; cancellation preserves confirmed data and failure preserves choices (AC-009.2, .6) | Task 20 |
| S5 | Timing, environment | Pending/refetching/failed lookup, offline response, rapid double submission | High | Disable choices/completion while lookup is unusable; retry state and a single pending mutation (AC-009.8) | Task 20 |
| S6 | Business logic, error cascades | No eligible recipient for one Space | High | MSG118; neither caller can complete; mock writes nothing (AC-009.5) | Task 20 |
| S7 | Data integrity, compliance | Success followed by reactivation or restoring KM with other valid Spaces | High | Preserve ids/history; transferred Spaces stay with recipients; session/detail show confirmed assignments (AC-009.7–.9) | Task 20 |
| S8 | Integration, environment | API generated code drifts; popup keyboard use or long Space labels | Medium | Deterministic generation; shared accessible form labels and browser inspection at 1366 × 768 (AC-009.1–.2, .8) | Task 20 |

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
| Stale or forged choices cause partial reassignment | High | Negative contract cases for each caller and mixed Project/Space state snapshots | Task 20 |
| Mock success is mistaken for deployed security/persistence | High | Mark OpenAPI behavior as a proposal; report backend integration as pending | Task 20 |

### Proposed Task 20 and iteration placement

Add one feature, `space-handover`, to the existing identity iteration 1 after Task 13. Story: STORY-009; criteria: AC-009.1–AC-009.9. Reuse merged dependencies and add focused contract/UI cases for S1–S8, OpenAPI descriptions plus generated output, and only demonstrated runtime fixes. Candidate files are under `frontend/src/features/users/{components,pages,mocks}/`, `frontend/openapi/tsolve-api.yaml`, generated API output, and `docs/bug-log.md` for reproduced bugs. No dependency, route or production backend change.

Verification: `sdd.ts trace identity STORY-009`; focused users tests; frontend format, lint, typecheck, full tests, build and deterministic API generation; browser-check UC-06 and UC-07 at 1366 × 768. Preserve all AC-006/007/008 behavior. Write the task session log and test/review reports with actual results. Propose one feature commit only after verification and obtain user agreement before committing; push only if requested.

Builder check: proposed Task 20 supplies the callers, models, eligibility rule, payload scope, errors, state invariants, test targets and exclusions. There is no unresolved build decision. The user approved the design and iteration placement on 2026-10-31; Task 20 is appended to iteration 1. Implementation and verification are recorded in its session and evaluation reports.

## User List backend — KAN-107, Task 32 (2026-11-07)

Covers AC-004.9 to AC-004.18. Decisions of the user (Dương, 07/11): Q1–Q4 = recommendation (spec Changelog).

- **Caller.** The endpoints depend only on what `main` already has: `@PreAuthorize("hasRole(ADMIN)")` with the
  `TSolveUser` in the security context, and `TenantScope.currentWorkspaceId()` for the bound Workspace, like the Review
  controllers. No session filter, no `identity/session` package, no `WorkspaceSessionBinding`, no `TenantContext`
  (PR #119's files stay dropped, KAN-275 Q15). Unauthenticated and non-Admin answers come from `SecurityConfig` and
  `GlobalExceptionHandler` as today (401 MSG07, 403 MSG08).
- **Interim mock (AC-004.17).** `vn.tsolve.identity.mock`, all `@Profile("identity-mock")`: `MockSignedInCallerFilter`
  (a servlet filter registered in the web chain before authorization; only when the context holds no authentication,
  so the owners' `WebSessionFilter` wins once it exists) reads `tsolve.identity.mock.account-email` and
  `tsolve.identity.mock.workspace-id`, binds the Workspace with `TenantScope.runIn` around the rest of the chain,
  re-reads the Account and its Roles, and sets a `TSolveUser`; a missing or Deactivated Account leaves the request
  signed out. `MockUserListSeeder` (AC-004.18) inserts the missing demo rows at start-up inside `runIn`. Javadoc on
  each: "replace when KAN-275 workspace-session (Tasks 26a/26b) lands". Never in tests' default context or `prod`.
- **Schema.** Changeset `019-identity-user-role-department.sql` (author `identity`): `user_role` (`id UUID PK
  DEFAULT gen_random_uuid()`, `user_id UUID NOT NULL → app_user(id) ON DELETE CASCADE`, `role VARCHAR(30)` CHECK in
  `TICKET_RESOLVER, PROJECT_MANAGER, KNOWLEDGE_MANAGER, ADMIN`, `assigned_at TIMESTAMPTZ NOT NULL`, UNIQUE
  `(user_id, role)`), and `app_user.department_id BIGINT NULL → department(department_id)` with an index. Baseline
  names; key types follow `main` (UUID `app_user.id`, BIGINT `department_id` with the UUID `public_id` exposed).
  Task 26b reuses this table instead of adding its own.
- **Read.** `UserListService` / `UserListServiceImpl` (read-only transaction) → `UserListRepository`, a native SQL
  query (count + page) on `app_user` left-joined to `department`, roles aggregated from `user_role`; filters bound as
  parameters, `q` escaped for `LIKE`; sort whitelist with `lower(...)` and `NULLS FIRST`/`LAST` and `id` tie-break.
  Departments: `department` where `status = 'ACTIVE'` ordered by `lower(name)`. The service asserts a bound Workspace
  first (`TenantScope.currentWorkspaceId()` throws → 500 MSG06). Lenient paging like the MSW mock: an unknown sort
  falls back to the default, `size` is capped at 100.
- **Tests.** Unit: query parsing (q length/escape, sort whitelist, paging). `@WebMvcTest`-style: 401/403/400.
  IT with `TwoTenantDatabases`: rows in `tenant_b` never appear for ACME; filters, sort, paging; departments list;
  mock filter with the profile on (signed in) and off (401); seeder idempotent.

## Add user backend — KAN-108, Task 33 (2026-11-07)

Covers AC-005.9 to AC-005.17; decisions Q1–Q3 of 07/11 (spec Changelog). Builds on Task 32 (same caller, same
`identity-mock` profile).

- **Schema.** Changeset `020-identity-space-manager.sql` (author `identity`): `space` (`space_id BIGSERIAL PK`,
  `public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid()`, `department_id BIGINT NOT NULL UNIQUE → department`,
  `name VARCHAR(100) NOT NULL`, `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` CHECK `ACTIVE, HIDDEN`) and
  `space_manager` (`space_id BIGINT PRIMARY KEY → space` — the key makes one Knowledge Manager per Space —
  `user_id UUID NOT NULL → app_user(id) ON DELETE CASCADE`, `assigned_at TIMESTAMPTZ NOT NULL`, index on `user_id`).
  Key style follows `department` on main; the wiki module reuses both.
- **Write.** `UserCreationService` / impl, `@Transactional`: validate in the mock's order (email required → length
  on the raw input → format + company domain → duplicate in this Workspace → roles → Department → Spaces → occupied
  Space), claim the email in `WorkspaceEmailRegistry`, insert `app_user`, `user_role`, `space_manager`, record the
  audit event, return the `UserAccount` read back through Task 32's repository mapping. A unique violation on
  `space_manager` (race) maps to 409 MSG117 and a unique violation on `app_user.email` to 409 MSG11. Field errors
  use the existing `fieldErrors` Problem shape the frontend reads.
- **Ports.** `WorkspaceEmailRegistry` (`boolean claim(UUID workspaceId, String email)`, `void release(...)`) in
  `identity.service`, default bean `AcceptAllWorkspaceEmailRegistry` with javadoc "interim until platform's
  workspace_email registry (Task 14)". Company domain: `WorkspaceLookup.findById(bound).googleDomain()`, then the
  `identity-mock` property `tsolve.identity.mock.company-domain` (default `acme.test`).
- **Seed.** `MockUserListSeeder` also ensures one Active Space per demo Department (and one Knowledge Manager
  assignment for the seeded Knowledge Manager), so creation-options and MSG117 are visible in the demo.
- **Tests.** Unit: validation order and codes. MVC: 401/403/400. IT (`TwoTenantDatabases`): creation lands only in
  `tenant_a`; a duplicate email in `tenant_b` does not block ACME; MSG11/MSG111; MSG117 including two concurrent
  creations for one Space; registry refusal → MSG155 and nothing saved; creation-options; audit row.
## Update roles backend — KAN-110, Task 34 (2026-11-07)

Covers AC-007.8 to AC-007.23; the user's ten decisions of 07/11 (spec Changelog). Builds on Task 32 (caller,
`identity-mock` profile, `user_role`, `app_user.department_id`) and Task 33 (`space`, `space_manager`, changeset 020,
arriving by rebase onto `feat/KAN-108-identity-add-user-api`).

- **Endpoints.** `GET /api/v1/users/{userId}` (`getUserDetail`), `GET /api/v1/users/{userId}/handover-options`
  (`getUserHandoverOptions`), `PUT /api/v1/users/{userId}` (`updateUserRoles`), all `@PreAuthorize` ADMIN. `{userId}`
  is matched as a UUID pattern so `/departments` (Task 32) and `/creation-options` (Task 33) keep their mappings; a
  non-UUID or unknown id is 404 MSG04. Contract and MSW behaviour: `frontend/openapi/tsolve-api.yaml` and
  `frontend/src/features/users/mocks/{roles-handler,handover,state}.ts` (research note
  `kan-110-research.md`, file:line references).
- **Schema.** Changeset `023-identity-one-project-manager.sql` (author `identity`): partial unique index
  `uq_membership_one_manager ON project_membership (project_id) WHERE role_in_project = 'PROJECT_MANAGER'`
  (baseline rule; enforces AC-007.19). No history tables: former managers and memberships are kept in the
  `ROLE_CHANGE` audit details only.
- **Read.** `UserDetail` = Task 32's `UserAccount` row + `isCurrentUser` + `ticketResolverProjectIds` + `projects`
  (`UserProject.name` = `project.jira_project_key`; Projects without a Project Manager row are left out) +
  `managedSpaces`. Handover options: eligible recipients computed by one query per responsibility kind.
- **Write.** `UserRoleUpdateService` / impl in one `@Transactional` method, checks in the AC-007.20 order. Locks:
  `SELECT … FOR UPDATE` on the target `app_user` row; WHEN ADMIN is removed, on every Active Admin's `app_user` row
  (ordered by id to avoid deadlocks) before counting; on the recipients' rows before re-checking eligibility; on the
  requested `space` rows before the MSG117 check. A unique violation on `space_manager` maps to 409 MSG117; a
  violation of the one-Project-Manager index (only reachable by two concurrent handovers of the same Project) rolls
  the whole update back and answers 500 MSG06, logged. Apply: roles, Department
  (stored as sent, also for a non-PM), Spaces, handovers, membership changes (AC-007.19), one audit event.
- **Seed.** `MockUserListSeeder` adds, idempotently, Projects of a demo Department (two `ACTIVE`, one non-Active)
  managed by the seeded Project Manager, a second Project Manager eligible for one Department only (so one Project
  shows MSG59), and `MEMBER` rows for the seeded Ticket Resolver (MSG78).
- **Tests.** Unit: each check, its order, codes, params. MVC: 401/403/404/400 MSG06. IT (`TwoTenantDatabases`):
  detail and handover options; a full update with Project and Space handovers; MSG117, MSG56, MSG113, MSG59,
  MSG118, Hand Over Responsibilities MSG01, MSG78 with and without confirmation; rollback leaves every row unchanged;
  a BETA recipient/Project/Space is never offered or accepted; two Admins removing each other's Admin role at once →
  exactly one succeeds; two updates racing for one Space → one 409 MSG117.
