# Authentication guide

How a person signs in to T-Solve, for developers. The decision and its reasons are in
[ADR-0003](adr/0003-keycloak-sign-in.md); the steps to onboard a company (including the test sign-in) are in the
[runbook](runbooks/onboard-company.md). The contract is `harnessclaude/artifacts/03-modules/identity/spec.md`
(AC-001.1 to .72; the Keycloak ones are AC-001.52 to .72).

## The flow

1. A person opens `https://<slug>.tsolve.io.vn/login` (a Workspace site) and chooses to sign in.
2. The backend of that site starts an OIDC authorization request (PKCE `S256`, client `tsolve-web`) with
   `kc_idp_hint=google-<slug>` and sends the browser to Keycloak (realm `tsolve`, `TSOLVE_LOGIN_ORIGIN`). The saved
   request lives in Redis, not in an HTTP session.
3. Keycloak sends the browser to Google, using the Google identity provider of that Workspace.
4. Google returns to Keycloak, and Keycloak returns to `https://<slug>.tsolve.io.vn/api/v1/auth/callback`.
5. The callback runs on the Workspace site itself and keeps no state of its own. It checks, in order: the saved
   request, the code exchange and ID token, the Organization and `hd`, Workspace Active, the Account, Account Active
   (AC-001.14). Only then does it create the Redis session (`__Host-TSOLVE_SESSION`).

Anything that goes wrong in the callback redirects to `/login?error=MSGnn` on the Workspace site.

## The T-Solve theme

Keycloak's login and error pages use a minimal theme, `infra/keycloak/themes/tsolve/login/`. The realm import sets
`loginTheme` to `tsolve` for realm `tsolve`. The theme is five templates plus overrides:

- Templates: `template.ftl`, `login.ftl`, `error.ftl`, `login-page-expired.ftl`, `logout-confirm.ftl`.
- Overrides: `theme.properties` (only `css/tsolve.css`; no common styles, no scripts), `messages/messages_en.properties`
  (replaces Keycloak's wordings with MSG06, and the link text "Back to sign-in"), `resources/css/tsolve.css`.

The pages load no script and no resource from another host, and show no link except "Back to sign-in" (AC-001.68).

## Where each failure lands

| Failure | Where the person lands |
|---|---|
| Cancelled at Google (`access_denied`) | Keycloak's themed error page |
| Unknown `kc_idp_hint` | Keycloak's themed error page |
| Expired Keycloak login session | Keycloak's themed error page |
| Keycloak internal error (for example the token call to Google fails) | Keycloak's themed error page |
| Google `redirect_uri_mismatch` | Google's own page; it never reaches Keycloak or the callback |
| An error Keycloak does hand back (for example `prompt=none` with no Keycloak session) | The callback, then `/login?error=MSG06` |

The themed error page shows MSG06, "Something went wrong. Please try again later.", and a "Back to sign-in" link
(AC-001.67). The link goes to the `baseUrl` of the client that started the request, or, when Keycloak does not know
the client, to the theme fallback below. `redirect_uri_mismatch` means the Google redirect URI of the runbook is
missing or not applied yet.

## `TSOLVE_WEB_BASE_URL`

The T-Solve web address used for "Back to sign-in". It is read in two places: the theme fallback `backToSignInUrl` in
`theme.properties`, and the realm import (`tsolve-web` client `baseUrl` in `infra/keycloak/realm-tsolve.json`).
Docker Compose passes it to the `keycloak` service.

- Development default: `http://localhost:5173/`.
- Production: `https://tsolve.io.vn/`.

## Running the Keycloak integration tests

They start Keycloak, the fake Google and Redis in Docker, so Docker must be running:

```
cd backend
mvn -Dit.test='KeycloakThemeIT,KeycloakFailurePlacementIT' verify
```

`KeycloakThemeIT` checks the realm theme and the page rules; `KeycloakFailurePlacementIT` checks the table above. On
Docker 29 add `-DargLine=-Dapi.version=1.44`. Use it on your command line only; never commit it.

## Known limits

- The logout confirmation page (`logout-confirm.ftl`) keeps Keycloak's own wording.
- Use the theme only on the realm `tsolve`. It must stay off the Platform Operator realm `tsolve-ops`.
- The theme is the five templates and the overrides above. Any other Keycloak page (for example account
  management) is not themed.
