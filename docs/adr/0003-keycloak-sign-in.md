# Sign-in through Keycloak: one realm, one Organization and one Google identity provider per Workspace

Decided 4/11/2026 (decisions K1–K14, KAN-275); the open questions of the plan were answered 5/11/2026. Replaces the
`login.` sign-in that `identity` built itself (signed state values, hand-off codes, a hand-written Google OIDC client).
Does not change ADR-0002: one database per company, Redis for sessions.

T-Solve now delegates the sign-in to Keycloak 26.x, run at `login.tsolve.io.vn`. Keycloak brokers to Google; the
backend is an ordinary OIDC client of it. Sessions, the per-request Account re-read and tenant isolation stay in
`identity`. We chose this over keeping our own sign-in because the custom code (state signing, single-use codes, cookie
bindings, an OIDC client) was security code we had to write, test and keep correct, and Keycloak already does it.

## Decisions

- **K1** Keycloak 26.x at `login.tsolve.io.vn` (`TSOLVE_LOGIN_ORIGIN`), one realm `tsolve`; it replaces identity's
  authorize and callback on `login.`; the backend no longer serves `login.`.
- **K2** One Keycloak Organization per Workspace; its domain is the Workspace's Google domain; the Organization id is
  stored on the platform's Workspace record.
- **K3** One Google identity provider per Workspace, alias `google-<slug>`, all with the same Google OAuth client,
  `hostedDomain` = the Workspace's Google domain, linked to its Organization.
- **K4** Manual step per company (runbook, not code): add
  `https://login.tsolve.io.vn/realms/tsolve/broker/google-<slug>/endpoint` to the Google OAuth client's authorized
  redirect URIs. Google may take minutes to hours to apply it.
- **K5** Each Workspace site is an OIDC client: one confidential client `tsolve-web`, PKCE `S256`, the exact redirect
  URI `https://<slug>.tsolve.io.vn/api/v1/auth/callback` per site (no wildcard); sign-in start sends
  `kc_idp_hint=google-<slug>`.
- **K6** Workspace creation creates the Organization, IdP, domain and redirect URI through the Keycloak Admin REST
  API, behind a port, with a no-op bean until the platform exists.
- **K7** The backend uses Spring Security's OAuth2 client; no hand-written OIDC client. The in-flight authorization
  request is kept in Redis, never in a replica-local HTTP session.
- **K8** Callback on the Workspace site, in order: token Organization and `hd` match this site's Workspace, Workspace
  Active, Account, Account Active. Accounts are matched by Keycloak `sub` (stored on `app_user`, unique); on the first
  sign-in only, by verified email, then the `sub` is bound; a `sub` bound to another Account is refused.
- **K9** Sessions as designed: Redis, `__Host-TSOLVE_SESSION`, `WebSessionFilter` (8 h, 60 min idle, Account re-read
  each request). Keycloak SSO Session Max is at most 8 h and Idle at most 60 min.
- **K10** Logout ends the Redis session and does OIDC RP-initiated logout (`id_token_hint` kept in the session).
- **K11** Tenant isolation unchanged: one database per company, `TenantScope.runIn`, no `workspace_id`.
- **K12** The custom sign-in classes, tables (`sign_in_states`, `hand_off_code`), cookies, `TSOLVE_SIGNING_KEY` and the
  cleanup job are deleted; tables go by new changesets only.
- **K13** Keycloak's login and error pages get a minimal T-Solve theme.
- **K14** Local dev: Keycloak in Docker Compose with a realm import; tests: `testcontainers-keycloak` brokering to
  the WireMock fake issuer. CI never calls Google.

Answers of 5/11 that refine them: Platform Operators sign in through a separate realm `tsolve-ops` with a second factor
and the allow-list as a second check (Q1); a Jira link is a single-use request plus a confirm page (Q2); Keycloak runs
on the same VPS behind Caddy with its own database, a heap cap of about 1–1.5 GB, in the nightly `pg_dump`, backup owner
DevOps, a pinned image and a health check (Q3); a Workspace is Active at creation and the test sign-in is a runbook step done
before the company is told (Q4, revised); the Redis authorization request is keyed by the hash of the `__Host-TSOLVE_AUTHREQ` cookie (Q9);
`organization` and `hd` are both required (Q10); provisioning is serialised, verified and undone by alias, with
credentials only in the profiles that create Workspaces (Q12, Q14, Q16); the realm allows no registration, no password
form and no email linking (Q13); logout answers 200 `{ logoutUrl }` (Q7).

## Consequences

- Keycloak is a single point of failure for starting a sign-in; signed-in sessions in Redis continue. It needs a
  health check, a pinned image, backups (its own database) and an upgrade runbook (26.x Organizations).
- Personal data (names, emails, login events) is now also in Keycloak: removing a company deletes its Keycloak users,
  Organization and IdP (runbook `docs/runbooks/onboard-company.md`), and Keycloak logs carry no email.
- Each company needs one manual Google Cloud Console step (K4). Google limits the redirect URIs per client; check the
  limit before the n-th company.
- A re-created Keycloak user gets a new `sub`; the Account stays bound to the old one until the runbook's rebind.
- Cross-module changes: `platform` (the Google domain and Organization id on the Workspace
  record, the call of the provisioning port with a new `create` parameter), `jira-app` (a link request, not a sign-in state), `fe-foundation` (the
  logout navigates to `logoutUrl`). See the identity design, "Platform change requests".
- The identity plan is `harnessclaude/artifacts/04-plans/identity/` (Tasks 23–31); the spec is
  `harnessclaude/artifacts/03-modules/identity/spec.md` (AC-001.52–.73, AC-002.8).
