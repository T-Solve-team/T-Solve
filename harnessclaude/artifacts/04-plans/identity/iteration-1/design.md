---
module: identity
date: 2026-10-25
---

# Design: identity — current frontend delivery

Master: artifacts/04-plans/identity/design.md
Spec: artifacts/03-modules/identity/spec.md

## Architecture Overview

Deliver KAN-107–110 sequentially using the existing Admin routes, shared UI kit, generated API hooks and MSW data. This file groups the requested frontend work under the workspace's current iteration; Jira's S2 labels and dates are unchanged.

## Data Model

Use the master plan's user list, detail, mutation, lookup and handover contract areas. Keep actual endpoint/schema additions with the story that consumes them. No production backend changes.

## Key Decisions

Use the approved MSW approach and existing project stack. One issue branch and user-approved feature commit per task, followed by its harness commit. The hook approval command must be run by the user before code can start.

## File Structure

See the master's feature-local outline and the exact paths in tasks.md. Do not pre-create unused components.

## External Dependencies

No new dependencies.

## Edge Cases & Risks

The master plan's numbered risk table applies in full. Tasks 1–4 explicitly map its High-severity cases to verification. Risk verdict: CAUTION, with atomic mock mutations, rejected-operation tests, generated contracts and clearly scoped backend limitations as mitigations.

## Task 3 integration note
User approved the minimal central mock-session adapter for KAN-109/110, requiring FE Lead review. Follow the master's approved bridge note; retain unchanged session fixtures and keep the adapter implementation feature-local. No other shared/app edit is authorized by this note.

## KAN-111 preparation — draft

Master: artifacts/04-plans/identity/design.md, "KAN-111 preparation — 2026-10-29".
Spec: artifacts/03-modules/identity/spec.md, STORY-008 / AC-008.1–008.9.

The user selected preparing the scope/spec now and waiting for KAN-109/110 to merge into main before code. This is preparation in the current plan folder, not a change to Jira sprint dates. Reuse the included Project handover and its existing popup/mutation contracts; no standalone screen or endpoint. Reconcile the merged code against the master's risk table, implement only gaps, and retain existing Space behavior. Approval of the new spec and merged KAN-109 permit the first caller slice (user continuation request, 2026-10-30); the KAN-110 caller and final completion wait for KAN-110 on main. Full frontend verification, browser inspection and a user commit checkpoint apply; KAN-112 remains separate.

## KAN-112 — Task 22: Space handover

Master: artifacts/04-plans/identity/design.md, "KAN-112 — Space handover — 2026-10-31".
Spec: artifacts/03-modules/identity/spec.md, STORY-009 / AC-009.1–AC-009.9.

User approved the master design and placement in iteration 1 on 2026-10-31 after approving the spec. Reuse UC-06/07 caller flows, the shared handover popup, existing generated models and atomic mutable MSW helpers. Expand the existing OpenAPI proposal's descriptions; no new endpoint, model, dependency or backend implementation. Exercise master risk rows S1–S8: eligibility and stale choices, exactly the affected Space set, multi-Space/mixed-Project atomicity, cancellation/failure/retry, loading/refetch/pending controls and retained history. Task 22 owns implementation, browser evidence, trace and the required frontend checks. Runtime changes require a demonstrated gap; commit and push remain separate user checkpoints.

## STORY-003 My Profile — KAN-160 (2026-10-31)

Master: artifacts/04-plans/identity/design.md, "STORY-003: My Profile — KAN-160 plan, 2026-10-31".
Spec: artifacts/03-modules/identity/spec.md, STORY-003 / AC-003.1–003.14 and AC-002.7.

Tasks 15–18: the web app on MSW mocks (D1), stacked on `chore/KAN-231-identity-plan` (PR #94). Two new endpoints
under tag `profile` (`getMyViewableSpaces`, `getMySolutions`), each in its screen's PR; feature-local mocks read the
dev user with `getDevUser()`. No shared edit: in My Solutions only the Ticket ID of a `canOpen` row is a link
(`cells.link`, R1 A). Edge-case rows 53–81 of the master apply. Task 19 (backend
AC-003.4–.10) is a stub with no feature until it has a Jira issue.

## HLA change — Tasks 9b, 10, 11, 12, 14 (2026-11-03)

Master: artifacts/04-plans/identity/design.md, "HLA change, 2026-11-03" and "Data model (Q8, HLA Q2–Q4)".
Spec: artifacts/03-modules/identity/spec.md, Changelog row 2026-11-03.

Aligns identity's backend plan with the multi-tenant HLA (KAN-231 step 2, Hiếu: Q1–Q4 = A). The Platform Console on
`admin.` stays (Task 11). Sign-in states and hand-off codes move to the Platform / Tenant Registry database in Task 14;
Tasks 8–9 keep their tables until then. Sessions live in Redis (Task 10; local stack and tests use a Redis container).
One database per company: the account tables have no `workspace_id` and email uniqueness across the platform goes
through the registry (Tasks 9b and 14). Task 9 is unchanged. New edge-case rows 90–93 apply to Tasks 9b, 10 and 14.

## Keycloak re-plan — Tasks 23–31 (2026-11-05, KAN-275)

Master: artifacts/04-plans/identity/design.md, "Keycloak re-plan: STORY-001 sign-in and STORY-002 logout".
Spec: artifacts/03-modules/identity/spec.md, Changelog row 2026-11-05 (AC-001.52–.73, AC-002.8). ADR: `docs/adr/0003-keycloak-sign-in.md`.

Replaces the sign-in mechanism of Tasks 8, 9, 9b, 10, 11 and 12 with Keycloak 26.x (K1–K14): Spring Security's OAuth2
client against the realm `tsolve`, one Organization and one Google IdP per Workspace, the authorization request in
Redis, the callback on the Workspace site creating the Redis session. Tasks 8, 9 and 9b stay Done and their code is
removed by Task 24; Task 10's uncommitted tree is salvaged into Task 26; Tasks 11 and 12 become Tasks 29 and 30;
Task 14 keeps the platform adapters and the `FirstAdminCreator` but no longer moves sign-in tables. Feedback first:
Task 23 lets Hiếu sign in locally through Keycloak. Edge-case rows 103–124 apply. No open question.


## Task 23 split and the QA answers — KAN-276 (2026-11-05)

Master: artifacts/04-plans/identity/design.md, "Keycloak re-plan", rows 103–105, 110, 111, 114, 115, 122.
Spec: artifacts/03-modules/identity/spec.md. No acceptance criterion changes: every answer below fits AC-001.1, .52–.55 as written.

QA step 1 of Task 23 asked eight questions; the user answered on 5/11. Each is chosen / alternatives / rationale:

| QA | Chosen | Alternatives | Rationale and where it binds |
|----|--------|--------------|------------------------------|
| 1 | B: a prerequisite PR, Task 23a (Redis starter, compose service and config, `identity/dev` lookup, `slug` and `organizationId` on `WorkspaceRef`) | A: Task 23 adds them (11–12 files); C: wait for Task 26a | Keeps Task 23 at 8 production files. Tasks: 23a, 23. Task 25b no longer adds the two `WorkspaceRef` fields or creates `DevWorkspaceLookup`; it keeps the AC-001.48 refusal. |
| 2 | A: the success handler forwards in the same request to the dev-profile `DevClaimsController`; outside `dev` a success answers 302 `/login?error=MSG06` with one WARN until Task 26a | B: claims in Redis behind a single-use key; C: 501 outside `dev` | Nothing stored, nothing in a URL (row 122 stays closed). Task 23. |
| 3 | A: `admin.` sign-in start keeps the old path in Task 23, the callback on `admin.` answers 404 until Task 29 | B: 404 on `admin.` start now | The AC-001.28 test stays green; AC-001.28 is Task 29. |
| 4 | A: "both discovery documents" are the realms `tsolve` and `tsolve-ops`; Task 23 checks `tsolve`, Task 29 adds `tsolve-ops` | B: one realm read at two URLs | Row 111. |
| 5 | B: Keycloak unreachable at start gives a WARN naming the issuer; a mismatch on a reachable document fails start-up; no later re-check | A: fail start-up; C: B plus a background re-check | Row 110: signed-in sessions continue while Keycloak is down, so an API restart must not depend on it. |
| 6 | A: `TSOLVE_SIGNING_KEY` and `GOOGLE_*` stay required, `KEYCLOAK_WEB_CLIENT_SECRET` is added; Task 24 removes the old ones | B: old values optional now | The old classes live until Task 24. |
| 7 | A: WireMock `fake-google` with response templating and a JWT-signing extension (the nonce is encoded into the code, the token stub signs per request); the dev and CI IdPs are type `oidc` pointing at it | B: one pre-signed long-lived token; C: another mock server | Keycloak's `google` type has fixed Google URLs. The nonce check stays on in dev and CI (rows 103, 122). Real Google `hostedDomain` is not testable here (runbook, Task 28). |
| 8 | A: the redirect URI's origin comes from the request, with `server.forward-headers-strategy=framework` behind Caddy | B: `WorkspaceRef.siteHost` plus a configured scheme | The host has already resolved to a Workspace; Keycloak's exact match is the guard (AC-001.54); local ports keep working. |

Non-scope notes (kept out of Task 23): an optional real-Google IdP turned on by `.env` (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`) for a manual smoke test belongs to the Keycloak provisioning task (Task 28) and the runbook; a real Google Workspace or Cloud Identity domain is a prerequisite of the staging smoke test and of production readiness (`hd` exists only for Workspace domains), not of Task 23; Microsoft Entra ID is NOT in scope and needs a separate Change Mode decision.

Test size: the QA plan has 84 test methods for the old Task 23 against a rule of about 12 per task. Decided 6/11: Task 23 is split into three slices, each with a "Test scope" line, QA step 1 re-run on each: Task 23 (realm, compose, start-up check, 22 tests), Task 23b (sign-in start and the Redis authorization request, 28), Task 23c (callback and the round trip, 34). The boundary moved from the QA proposal: the Redis repository went to the start slice and the end-to-end test, with `AuthSecurityConfig`, to the callback slice; QA answers 2–8 bind the slice named in `tasks.md`.

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
