# Keycloak realms for the staging deploy

`realm-tsolve.json` and `realm-tsolve-ops.json` are the dev realms (`../`) without the fake Google, the test users
(alice, bob) and every localhost URL. Keycloak imports them at start (`start --import-realm`, files in
`/opt/keycloak/data/import/`) and substitutes `${NAME}` from the container's environment. An import runs once: if
the realm already exists in the database it is skipped, so a changed file needs a deleted realm (or a changed
value set in the Admin Console).

## Environment variables (all must be passed to the Keycloak container)

| Variable | Used for |
|---|---|
| `TSOLVE_DOMAIN` | Base domain. Console URIs `https://admin.<domain>/...`; demo site `https://<slug>.<domain>/...` |
| `TSOLVE_DEMO_SLUG` | The demo company: Organization name and alias, IdP alias `google-<slug>`, the `tsolve-web` URIs |
| `TSOLVE_DEMO_GOOGLE_DOMAIN` | Google Workspace domain of the demo company (Organization domain, `hostedDomain`, `kc.org.domain`) |
| `TSOLVE_DEMO_ORG_ID` | Fixed UUID of the demo Organization |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | The one Google OAuth client, used by the company IdP and the operators IdP |
| `KEYCLOAK_WEB_CLIENT_SECRET` | Secret of `tsolve-web` |
| `KEYCLOAK_CONSOLE_CLIENT_SECRET` | Secret of `tsolve-console` |
| `KEYCLOAK_ADMIN_CLIENT_SECRET` | Secret of `tsolve-provisioner` |

## Organization claim rule

The backend (`OidcCallbackHandler.namesOrganization`) reads the token's `organization` claim, a map
`{ "<alias>": { "id": "<organization id>" } }`, and requires some entry's **id** to equal the Workspace's
`organizationId`. So `TSOLVE_STUB_WORKSPACES` must carry `organizationId = TSOLVE_DEMO_ORG_ID` (the id, not the
alias). The realm file sets `"id": "${TSOLVE_DEMO_ORG_ID}"`; Keycloak keeps it on import. `hd` must equal
`TSOLVE_DEMO_GOOGLE_DOMAIN`.

## Google redirect URIs to register (Authorized redirect URIs of the one OAuth client)

Keycloak is the OAuth client of Google, so the URI points at Keycloak's broker endpoint:

- `https://login.<TSOLVE_DOMAIN>/realms/tsolve/broker/google-<TSOLVE_DEMO_SLUG>/endpoint`
- `https://login.<TSOLVE_DOMAIN>/realms/tsolve-ops/broker/google-operators/endpoint` (Platform Console)

This stack serves one company; there is no provisioning, so a different company means editing the env file, the realm
file and the Caddy hosts by hand, and a new URI of the first kind with its slug (Google does not accept wildcards).
The OAuth consent screen must be "Internal" or the user must be a test user.

## Sign-in design

- Companies: `tsolve-web` has one explicit callback and logout URI, for the demo site. Keycloak does not accept a
  wildcard in the host (`https://*.domain/...`); the URIs of another company are edited in the realm by hand.
- Operators: the same Google client through the `google-operators` IdP, then the TOTP second factor, as in dev. No
  local user is created. Who may enter is decided by the backend allow-list `PLATFORM_OPERATOR_EMAILS`. The first
  login of each operator sets up TOTP.
- `KC_HOSTNAME=https://login.<TSOLVE_DOMAIN>` and `sslRequired: all` need the proxy to send
  `X-Forwarded-*` (`KC_PROXY_HEADERS=xforwarded`, `KC_HTTP_ENABLED=true` behind Caddy).
