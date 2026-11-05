# Runbook: onboard a company, and remove a company

For the T-Solve operations team and the Platform Operator. Background: ADR-0003 (`docs/adr/0003-keycloak-sign-in.md`).
`<slug>` is the Workspace's site name (the first label of `<slug>.tsolve.io.vn`); `<domain>` is the company's Google
domain. Never paste a secret into a ticket or a chat.

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

   If one is missing, run the provisioning `check` and then `provision` again; it is safe to repeat.
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
   before Google has applied the redirect URI, Google refuses and the Workspace site shows the normal sign-in error.

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
   that every other company's URIs in `tsolve-web` are intact.
4. Remove the redirect URI from the Google OAuth client by hand (Google Cloud Console, the list of step 3) and from the
   list kept with this runbook.
5. Delete the Workspace and its database through the platform's process (GBR-38), and keep the removal date and the
   ticket id. Keycloak's event log keeps no email and is cleared by its retention setting.
