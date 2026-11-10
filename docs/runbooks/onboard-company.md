# Runbook: onboard a company, and remove a company

For the T-Solve operations team and the Platform Operator. Background: ADR-0003 (`docs/adr/0003-keycloak-sign-in.md`) and the authentication guide (`docs/authentication.md`).
`<slug>` is the Workspace's site name (the first label of `<slug>.tsolve.io.vn`); `<domain>` is the company's Google
domain. Never paste a secret into a ticket or a chat.

## One-time setup: the provisioner secret and the `ops` values

The provisioning runs only in the `ops` process (profile `ops`); a Workspace web process never reads these values.

1. **Set the secret in two places only.** Generate one long random value for `KEYCLOAK_ADMIN_CLIENT_SECRET` and set it
   in the environment of the Keycloak container (the realm import reads it into the client `tsolve-provisioner`) and in
   the environment of the `ops` process. Nowhere else: not in a Workspace web process, not in a ticket or a chat.
   Developers: `docker compose up` now needs `KEYCLOAK_ADMIN_CLIENT_SECRET` in `.env`; copy the line from `.env.example`.
2. **Check the secret after the realm import.** The realm file holds the placeholder `${KEYCLOAK_ADMIN_CLIENT_SECRET}`.
   If Keycloak imported the realm without the variable, the client's secret may be that literal text, which is public in
   git, on a client that holds `manage-realm`. Check both:
   - Admin console, realm `tsolve`, "Clients" > `tsolve-provisioner` > "Credentials": the client secret must not read
     `${KEYCLOAK_ADMIN_CLIENT_SECRET}`.
   - A token request with the intended secret answers 200 with a token, and the same request with the literal placeholder
     answers 401. Both commands pass the secret to `curl` through standard input, so it never appears in the process
     list. Never type the secret as a command argument and never paste it into the shell history; read it from the
     environment variable `KEYCLOAK_ADMIN_CLIENT_SECRET` as shown. (`printf` is a shell built-in, so its arguments are
     not listed as a process either.)

     Real secret, expected HTTP 200:

     ```
     printf 'data-urlencode = "client_secret=%s"\n' "$KEYCLOAK_ADMIN_CLIENT_SECRET" | curl -s -o /dev/null -w '%{http_code}\n' -d grant_type=client_credentials -d client_id=tsolve-provisioner --config - https://login.tsolve.io.vn/realms/tsolve/protocol/openid-connect/token
     ```

     Placeholder text, expected HTTP 401 (the body says `unauthorized_client` or `invalid_client`; drop `-o /dev/null` to see it):

     ```
     printf '%s\n' 'data-urlencode = "client_secret=${KEYCLOAK_ADMIN_CLIENT_SECRET}"' | curl -s -o /dev/null -w '%{http_code}\n' -d grant_type=client_credentials -d client_id=tsolve-provisioner --config - https://login.tsolve.io.vn/realms/tsolve/protocol/openid-connect/token
     ```

     If the first command does not answer 200, or the second does not answer 401, the secret is wrong or the placeholder
     is still the secret: go on to the next paragraph. Run the commands in a shell where the variable is already set, and
     do not run `echo` or `set -x` on it.

   On any doubt, regenerate the secret in the admin console ("Credentials" > "Regenerate"), set the new value in the `ops`
   environment, and rotate it again whenever it may have been seen.

   **If the realm `tsolve` already exists** (Keycloak was started before `tsolve-provisioner` was added to
   `infra/keycloak/realm-tsolve.json`): a restart skips `--import-realm` for an existing realm, so the client is not
   created. Add it by hand, then repeat both checks above:
   - Preferred: admin console, realm `tsolve`, "Realm settings" > "Action" > "Partial import", choose the file
     `infra/keycloak/realm-tsolve.json`, tick only "Clients" (and no other resource), and use the policy "Skip" so that
     existing clients such as `tsolve-web` are untouched. Then open "Clients" > `tsolve-provisioner` > "Credentials" and
     set the client secret to the value of `KEYCLOAK_ADMIN_CLIENT_SECRET` (a partial import reads the file as it is, so
     the placeholder text may be stored instead of the environment value).
   - Or create the client by hand in "Clients" > "Create client": client ID `tsolve-provisioner`, "Client
     authentication" on (confidential), "Service accounts roles" on. Turn "Standard flow", "Implicit flow" and "Direct
     access grants" off, and leave "Valid redirect URIs" empty. Under "Credentials", set the client secret to the value
     of `KEYCLOAK_ADMIN_CLIENT_SECRET`. Under "Service accounts roles" > "Assign role" > "Filter by clients" (realm-management),
     assign exactly these 8 roles: the 7 named in `infra/keycloak/realm-tsolve.json` plus `manage-realm`.
   - Then repeat the two token checks (200 for the real secret, 401 for the placeholder) before using provisioning.
3. **Set the other `ops` values.** `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` (the T-Solve Google OAuth client),
   `TSOLVE_LOGIN_ORIGIN` (`https://login.tsolve.io.vn`; plain `http` is accepted only for `localhost` and `127.0.0.1`),
   and `TSOLVE_SITE_ORIGIN_TEMPLATE` (`https://{slug}.tsolve.io.vn`; it must contain `{slug}`). With one of the three
   secrets missing, the `ops` process starts without provisioning and logs one WARN naming the missing variable. With
   all three set, an unusable `TSOLVE_LOGIN_ORIGIN` or `TSOLVE_SITE_ORIGIN_TEMPLATE` stops the start-up and the message
   names the variable.

## Onboard a company

1. **Create the Workspace.** From the Platform Console (or `infra/ops/workspace.sh create --site <slug> --admin-email
   <email> --google-domain <domain>` on the application VPS). The platform creates the database and the first Admin,
   and calls the identity provisioning, which creates the Keycloak Organization, the Google identity provider
   `google-<slug>` and the redirect URIs of the site. The Workspace is Active at creation (platform AC-013.1). Nobody is
   told about the site until step 5 passes; the test sign-in is a step of this runbook, not a gate in the data model.
2. **Check the Keycloak objects.** In the Keycloak admin console, realm `tsolve`, confirm all of these exist:
   - Organization `<slug>` with the domain `<domain>`;
   - Identity provider `google-<slug>` (hosted domain `<domain>`, hidden on the login page, the T-Solve first-login
     flow, linked to the Organization);
   - Client `tsolve-web`: valid redirect URI `https://<slug>.tsolve.io.vn/api/v1/auth/callback` and valid post-logout
     redirect URI `https://<slug>.tsolve.io.vn/login`; every earlier company's URIs are still in the lists.

   If one is missing, run the provisioning `check` and then `provision` again; it is safe to repeat. If the Organization
   was deleted by hand while `google-<slug>` still exists, `provision` refuses and changes nothing: run `remove` for the
   Workspace first, then `provision`. If `tsolve-web` has lost an earlier company's URI, the provisioning logs one ERROR
   naming it and does not put it back: restore the URI by hand in "Clients" > `tsolve-web`.
3. **Add the Google redirect URI by hand.** In Google Cloud Console, in the project of the T-Solve OAuth client, open
   "APIs & Services" > "Credentials" > the OAuth 2.0 client > "Authorized redirect URIs", and add exactly:

   `https://login.tsolve.io.vn/realms/tsolve/broker/google-<slug>/endpoint`

   Save. Add it to the list of redirect URIs kept with this runbook, so you can count them against Google's limit for
   one client before the next company.
4. **Wait.** Google may need minutes, sometimes hours, to apply it. Do not test before about 5 minutes.
5. **Test the sign-in.** Open `https://<slug>.tsolve.io.vn/login` and sign in as the first Admin with a Google account of
   `<domain>`. A Google page that says `redirect_uri_mismatch` means step 3 is not applied yet (or has a typo): wait,
   or compare the URI character by character. Expected: the Admin lands in the Workspace.
6. **Tell the company.** Only after the test passes, send the company the site address. If anyone tries to sign in
   before Google has applied the redirect URI, Google refuses on its own `redirect_uri_mismatch` page; no T-Solve page shows it, which is why step 5 comes first.

If the Admin gets MSG122 (wrong domain), check the `hostedDomain` of the identity provider and the Organization's
domain. If MSG09, the Account's email is not the Google account's email.

## Rebind a person's sign-in (a re-created Keycloak user)

A person whose Keycloak user was deleted and re-created gets a new `sub`; sign-in answers MSG09. After an identity
check (the company's Admin confirms who it is), clear `app_user.keycloak_sub` for that Account in the Workspace's
database; the next sign-in matches by email once and binds the new `sub`. Record who did it and why. An Admin button
replaces this later.

## Remove a company

1. Agree the removal with the company's Admin and the Platform Operator; export what the company asks for first.
2. Suspend the Workspace in the Platform Console so nobody can sign in.
3. Run the provisioning `remove` for the Workspace. It deletes in Keycloak: the identity provider `google-<slug>`, the
   two redirect URIs of the site in `tsolve-web`, every Keycloak user of the Organization (their emails and names are
   personal data), and the Organization. It is safe to run twice. Confirm in the admin console that none is left and
   that every other company's URIs in `tsolve-web` are intact. If `remove` fails with an ERROR that names a lost URI,
   restore that URI by hand in "Clients" > `tsolve-web` and run `remove` again.
4. Remove the redirect URI from the Google OAuth client by hand (Google Cloud Console, the list of step 3) and from the
   list kept with this runbook.
5. Delete the Workspace and its database through the platform's process (GBR-38), and keep the removal date and the
   ticket id. Keycloak's event log keeps no email and is cleared by its retention setting.
