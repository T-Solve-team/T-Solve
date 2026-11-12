# Task 29a: Operator sign-in on the `tsolve-ops` realm — KAN-233

## Session

### Hand-off
- done: Task 29a (KAN-233): realm `tsolve-ops` (`infra/keycloak/realm-tsolve-ops.json`: Google IdP without `hd`, OTP required in the post-broker flow, client `tsolve-console`, exact redirect URI, no password form, brute-force on, SSO <= 8 h / idle <= 60 min, email admin-only, no `auth-cookie`); the Console start on `admin.` answers a redirect to Keycloak, which answers 303 to the Google broker login (no login page; `defaultProvider=google-operators`, because Keycloak 26.4.7 cannot show a hardened page with one Google button); discovery check of `tsolve-ops` (fatal on a wrong issuer or 404, one WARN when unreachable); README and `.env.example` document `KEYCLOAK_CONSOLE_CLIENT_SECRET`. Tests: `OpsRealmImportIT` 14, `OperatorSignInTest`, `DiscoveryCheckTest`; older tests updated. Spec: AC-001.66 hardened; Task 29 split into 29a and 29b. Design rows 136-141. Review: Pass with fixes (Fable): Important 1 and 2 fixed; Minors 3, 4, 6 open. CI green on 854b34eb.
- open: Task 29b = KAN-313 (callback allow-list, `OperatorAllowList`, `OperatorPrincipal`, Operator session on `admin.`; after Task 27 merges); review Minors 3 (TOTP on a repeat login), 4 (compare the imported client secret with the env value), 6 (stale wording in `OpsRealmImportIT` and `SignInConfig` Javadoc); the Console process profile (AC-001.61) follow-up; deploy runbook step for the `tsolve-ops` realm.
- next: merge PR #209; then Task 27 (KAN-106) and Task 29b (KAN-313).
- branch: feat/KAN-233-identity-operator-sign-in
- PR: #209 ready to merge
