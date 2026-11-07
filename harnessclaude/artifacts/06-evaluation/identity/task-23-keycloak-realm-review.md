---
module: identity
feature: keycloak-realm
task: 23
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: tsolve-code-reviewer (Claude Opus 5.5, independent, read-only)
iteration: 1
---

# Review: identity — Task 23: keycloak-realm (KAN-276)

## Summary
**Scope:** the uncommitted tree in worktree `T-Solve-KAN-276` on `feat/KAN-276-identity-keycloak-sign-in` (base `7e2e4c6`).
- 10 modified files and 11 new files:
  - `infra/keycloak/realm-tsolve.json`
  - `infra/keycloak/fake-google/**` (Dockerfile, extension, 5 mappings)
  - `RealmImportIT`
  - `DiscoveryCheckTest`
- The diff stays inside the Task 23 file list.
- It does not touch sign-in start, Redis, callback, `AuthSecurityConfig` or session.

**Tests:** I re-ran the targeted tests: 45 run, 0 failed. This includes `RealmImportIT` 13/13 after the format-only edits.

**Findings:**
- Every AC of the task maps to a passing test that checks it. Every design row (103, 104, 105, 110, 111, 114) maps to a test or a listed manual check.
- The binding QA answers are implemented as written: Q1 A–Q6 A, and Q4/Q5 B/Q6/Q7 A of tasks.md.
- No CRITICAL code-review finding, and no Critical or High security finding.
- 12 Informational findings. Two should go into the same commit: the stale local-run docs (1) and the fake Google `kid` (3).

**Verdict:** **pass** (pass with fixes: apply findings 1 and 3 before the commit, or record them as accepted).

**Feature status:** the worktree's `progress.json` has no `keycloak-realm` key (it has `keycloak-sign-in`, `keycloak-prerequisites`, …). The card does not ask me to change status. I propose `passing` via `sdd.ts feature identity keycloak-realm …` once the key exists.

**Commit shape:** one commit is better than the proposed two. Commit (1) alone does not compile: `SignInTestFixtures` calls the 7-argument `SignInProperties` constructor that only commit (2) adds. That breaks `git bisect`. The project rule is also one feature per commit, plus its harness commit.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Stage 1 table below: AC-001.54, .63, .64 and .65 PASS. The design rows have tests or manual checks. |
| Completeness | pass | Nothing MISSING. EXTRA: `resetPasswordAllowed`/implicit/direct-grants off and `hd` admin-only. Both are justified by QA Q4 A and Q5 A. |
| Correctness | pass | 45/45 targeted tests pass in a fresh run. No CRITICAL findings. Spotless was not runnable in the worktree; the developer's scratch-clone result stands. |
| Integration | pass | `SignInProperties` gains a required `keycloakWebClientSecret` and keeps it out of `toString()`. The realm IdP/Organization ids equal `DevAccounts` (23a). The dev-config overlap with the old `login.` classes is a plan-level note (finding 2). |

### Stage 1 — spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-001.54 exact redirect URIs, no wildcard (Keycloak side) | PASS | `realm-tsolve.json:122-126`.<br>`RealmImportIT.acceptsOnlyTheExactCallbackUrisOfAcmeAndBeta_AC_001_54` checks the admin list and a positive control.<br>`refusesRedirectUriThatIsNotAnExactCallbackUri_AC_001_54` refuses 5 cases (`/x`, `?x=1`, https, gamma, evil). Keycloak logged `invalid_redirect_uri` for each.<br>The dev URIs are `http://<slug>.localhost:8080`, not the spec's production form (see Assumptions).<br>"send only" and the 404 on other hosts are Tasks 23b/23c. |
| 2 | AC-001.63 SSO Max ≤ 8 h, Idle ≤ 60 min | PASS | `realm-tsolve.json:7,9-10` (28800 / 3600, `rememberMe` false).<br>`limitsSsoSessionTo8HoursAnd60MinutesIdle_AC_001_63_edge114` |
| 3 | AC-001.64 no registration, no password form, IdPs hidden, brute force | PASS | `realm-tsolve.json:5-8,12,26-37,154,177`. The browser flow is `auth-cookie` + `identity-provider-redirector` only.<br>`allowsNoSelfRegistrationNoPasswordFormAndHidesEveryIdp_AC_001_64` (includes `resetPasswordAllowed`, Q4 A) |
| 4 | AC-001.64 first broker login creates the user, no review, never links by email, adds to the Organization | PASS (config) | `realm-tsolve.json:38-48`. `idp-create-user-if-unique` is REQUIRED with no existing-user handling, so a duplicate email fails the flow instead of linking.<br>`usesCreateOnlyFirstBrokerFlowAndLinksIdpsToOrganizations_AC_001_64_edge105`. The live walk is Task 23c. |
| 5 | AC-001.64 no hint → no IdP list, no form | PASS (weak) | `showsNoIdpAndNoFormWithoutAKnownHint_AC_001_64_edge104`. Keycloak logs a failed flow for both cases. The test cannot tell a redirect from an error page (finding 5). |
| 6 | AC-001.65 confidential, PKCE S256 | PASS | `realm-tsolve.json:115-121,127`. `makesTsolveWebConfidentialAndRequiresPkceS256_AC_001_65` checks:<br>• the admin settings<br>• implicit and direct grants off (Q4 A)<br>• the file holds only the placeholder, and the running secret equals the container env (Q6 A)<br>• live: no challenge and `plain` both get `error=invalid_request` and no code |
| 7 | AC-001.65 `organization` (id) and `hd` in the ID token | PASS | `realm-tsolve.json:86-107,129-145,196-209`. The user profile declares `hd` admin-only (`:20`).<br>`mapsOrganizationClaimAndHdAttributeIntoIdToken_AC_001_65_edge103` checks the exact claim `{"acme":{"id":ACME_ORGANIZATION_ID}}` (Q3 A) and `hd=acme.test`. |
| 8 | Q1 A dev users imported, linked, members | PASS | `realm-tsolve.json:210-255`. Asserted in the same AC-001.64 test against `DevAccounts.all()`. |
| 9 | Q2 A / edge 110, 111 discovery check | PASS | `SignInConfig.java:35-80`. `DiscoveryCheckTest` has 11 cases: match, trailing `/`, mismatch naming both issuers and `TSOLVE_LOGIN_ORIGIN`, refused, unknown host, 500, 503, slow, 404, non-JSON, no issuer. |
| 10 | Q6 A required secret | PASS | `SignInProperties.java:20,32,67-70`. `SignInPropertiesTest` covers bound, absent and blank values, and that `toString()` omits the secret. |
| 11 | Row 110 compose health, own DB/user, pinned image, heap cap | Manual (M1) | `docker-compose.yml:33-97`. I did not run it (see "Not checked"). |

## Traceability
```
  ✓ AC-001.54  backend/src/test/java/vn/tsolve/identity/signin/RealmImportIT.java
  ✓ AC-001.63  backend/src/test/java/vn/tsolve/identity/signin/RealmImportIT.java
  ✓ AC-001.64  backend/src/test/java/vn/tsolve/identity/signin/RealmImportIT.java
  ✓ AC-001.65  backend/src/test/java/vn/tsolve/identity/signin/RealmImportIT.java
trace identity (STORY-001): 23/56 acceptance criteria have a test
```
The ✗ lines of the full trace (for example AC-001.70–.72) belong to other tasks.

## Issues

CRITICAL (blocks pass): none.

Informational (does not block):
1. **`README.md:69-74` and `.env.example:24` vs `:36`: the local-run docs are stale.**
   - Problem:
     - Compose now has `:?` guards on `KEYCLOAK_DB_PASSWORD`, `KEYCLOAK_WEB_CLIENT_SECRET` and `TSOLVE_LOGIN_ORIGIN` (`docker-compose.yml:45,71,75`).
     - Compose interpolates the whole file, so README step 1 `docker compose up -d db redis` stops until all three are set. The 23a review saw the same with `REDIS_PASSWORD`.
     - The README names none of them and does not start `keycloak`/`fake-google`.
     - `.env.example:24` still gives `http://login.localhost:8080` as the local `TSOLVE_LOGIN_ORIGIN`, but `:36` says `http://localhost:8180`.
   - Fix: update README step 0/1 and make `.env.example:24` agree, in the same commit. README is outside the card's file list, so the orchestrator decides.
2. **`AuthHosts.java:19`, `SignInStartController.java:70`, `GoogleOidcClient.java:122`, `CallbackController.java:163`: one variable, two meanings until Tasks 23b/23c.**
   - Problem:
     - The old `login.` classes still use `TSOLVE_LOGIN_ORIGIN` as the backend's own login host.
     - If a developer sets it to Keycloak's origin as `.env.example:36` says, the old local sign-in redirects to Keycloak's host and breaks. `AuthHosts` also treats plain `localhost` as the login host.
     - If they keep the old value, the new check only WARNs and checks nothing.
   - Fix: plan decision. Either accept the dev break until 23b and say so in `.env.example`, or document which value to use until 23b lands.
3. **`infra/keycloak/fake-google/extension/.../IdTokenExtension.java:25`: constant `kid`.**
   - Problem:
     - The `kid` is the constant `fake-google-key`, while the RSA key is new at each start.
     - Keycloak caches IdP public keys by `kid` and re-fetches the JWKS only for an unknown `kid`.
     - So after `fake-google` restarts alone (for example after editing a mapping, which WireMock does not hot-reload), every brokered sign-in fails signature validation until Keycloak restarts or its key cache expires.
   - Fix: derive the `kid` from the key (base64url of SHA-256 of the modulus) or make it random per start.
4. **`RealmImportIT.java:70`: the IT and compose run different Keycloak versions.**
   - Problem: `new KeycloakContainer()` runs the library default `quay.io/keycloak/keycloak:26.4` (seen in the log). Compose pins `26.4.7` (`docker-compose.yml:58`), so the IT can test a different Keycloak than dev runs.
   - Fix: `new KeycloakContainer("quay.io/keycloak/keycloak:26.4.7")`, or a shared constant.
5. **`RealmImportIT.java:322-328` (edge 104): the test can pass on a redirect.**
   - Problem: it asserts only what the body lacks. A 302 to an IdP would pass (for example a `defaultProvider` on the redirector, which sends a request with no hint to one Workspace's Google).
   - Fix: also assert there is no `Location` header and the status is not 3xx. Optionally assert the body is Keycloak's error page.
6. **`realm-tsolve.json:50-108`: the import replaces Keycloak's built-in client scopes.**
   - Problem:
     - The listed scopes replace the built-ins, so the realm has no `basic`, `acr`, `roles` or `web-origins` scope.
     - Since Keycloak 25, `basic` carries the `sub` and `auth_time` mappers for the access token.
     - No test asserts that the ID token has `sub`, which Task 23c's Account match needs (AC-001.12).
   - Fix: add `sub` (and `nonce`, if used) to the `generateExampleIdToken` assertions in I7, or add the `basic` scope. Confirm in 23c at the latest.
7. **`realm-tsolve.json:20` (user profile): `email`, `firstName` and `lastName` are editable by `user`.**
   - Problem:
     - A signed-in user with a live SSO cookie may reach the account console (the browser flow's `auth-cookie` lets them in) and change their Keycloak email.
     - AC-001.12's email match then rests only on the backend checking `email_verified`.
     - Whether the console works without the `roles` scope was not checked.
   - Fix: make `email` edit `admin`-only like `hd`, or disable the `account` client / `manage-account`. The spec is silent, so this is a judgment call.
8. **`SignInConfig.java:46-52`: the discovery timeout is not 5 s in total.**
   - Problem:
     - The connect timeout and the response-header timeout are separate 5 s timers.
     - DNS lookup and the body read after the headers are not bounded.
     - Q2 A says "5 s in total"; the slow-reply test covers only a slow header.
     - Separately, 3xx and 4xx other than 404 also fail start-up. That is stricter than the Q2 A list, and reasonable, but undocumented.
   - Fix: a single deadline (for example `sendAsync(...).get(5, SECONDS)`), and name the 3xx/4xx rule in the Javadoc.
9. **`SignInTestFixtures.java:28` and the ITs with `TSOLVE_LOGIN_ORIGIN=http://login.localhost:8081`: every Spring test context makes a real HTTP call at start.**
   - Problem: anything listening on 8081 that is not a discovery document makes every such context fail to start.
   - Fix: point test origins at a closed reserved port (like `OLLAMA_BASE_URL: http://127.0.0.1:9`), or add a test-only switch.
10. **`docker-compose.yml:47-51` and `:60`: changed passwords and secrets are not applied after the first start.**
    - Problem:
      - The DB init skips `CREATE ROLE` when the role exists, so a changed `KEYCLOAK_DB_PASSWORD` is never applied and Keycloak cannot log in.
      - `--import-realm` skips an existing realm, so a changed `KEYCLOAK_WEB_CLIENT_SECRET` leaves Keycloak and the backend on different secrets.
    - Fix: one line in `.env.example`: "after changing these, `docker compose down` and drop the `keycloak` database".
11. **`realm-tsolve.json:117`: an unset variable could become the client secret.**
    - Problem: if a Keycloak imports this file without `KEYCLOAK_WEB_CLIENT_SECRET` in its environment (compose guards against this; the production import does not exist yet), the unresolved text `${KEYCLOAK_WEB_CLIENT_SECRET}` may become the public client secret.
    - Fix: Task 28 runbook step: after import, verify that the running secret is not the placeholder.
12. **`docker-compose.yml:56-86`: no documented admin account.**
    - Problem: compose sets no `KC_BOOTSTRAP_ADMIN_*`. The manual check M2 (admin console) needs `docker compose exec keycloak /opt/keycloak/bin/kc.sh bootstrap-admin user` or env-supplied bootstrap credentials. Leaving credentials out of the committed file is correct.
    - Fix: document the bootstrap step.

## Security Review — identity / keycloak-realm — 2026-11-07

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 1 (finding 11) |
| Dependencies | 0 | 0 | 0 | 0 (no Maven audit tool configured; `testcontainers-keycloak` 3.9.1 is test scope) |
| Code | 0 | 0 | 0 | 1 (finding 8) |
| STRIDE | 0 | 0 | 1 (finding 7) | 1 (fake Google, below) |

- **Secrets:**
  - The realm file holds only the `${KEYCLOAK_WEB_CLIENT_SECRET}` placeholder, and the IT proves it.
  - `fake-google-secret` / `fake-google-client` are fake values that the fake ignores.
  - `PGPASSWORD: tsolve` existed before this change.
  - `.env` is git-ignored. Only `.env.example` and `frontend/.env.example` are tracked.
- **Spoofing:**
  - PKCE S256 is enforced, live-tested for a missing challenge and for `plain`.
  - The client is confidential, with implicit, direct grants and service accounts off.
  - The fake Google tokens are RS256 with `iss`, `aud`, `exp` and `nonce`, and Keycloak validates the signature through JWKS (`validateSignature: true`).
- **Tampering / elevation:**
  - Redirect URIs are exact, live-tested against 5 mutations, with no wildcard in URIs or web origins.
  - First broker login cannot link by email: there is no detect-existing, confirm-link or auto-link step.
  - `hd` is admin-only (Q5 A).
- **Denial of service:**
  - Brute-force detection is on (Keycloak defaults; there is no password credential to guess).
  - The discovery check is bounded except DNS and the body read (finding 8).
- **Information disclosure:**
  - Keycloak (8180) and the fake Google (8181) bind to `127.0.0.1` only, and the management port 9000 is not published.
  - The discovery check never reads the secret.
- **Fake Google (dev only, Low):**
  - The authorize stub reflects any `redirect_uri` into a link (Handlebars HTML-escapes it).
  - The token stub ignores the client secret and code reuse.
  - Both are accepted for a loopback-only fake that never ships.

## Assumptions made
- AC-001.54 names `https://<slug>.tsolve.io.vn/...`. The committed realm holds the dev URIs `http://{acme,beta}.localhost:8080/...`, and production URIs come from provisioning (AC-001.58/59, Task 28). I judged the dev form acceptable for this task.
- The README says the dev site runs at `http://localhost:5173`, while the redirect URIs use port 8080. Which origin the site uses for the callback is Task 23b's call. Cookies are not port-scoped, so 8080 can work.

## Not checked
- `docker compose up` (M1–M6): compose health, the `keycloak` DB and user, the browser walk to the fake account chooser, admin console, and the start-up check against the compose Keycloak. I did not start containers on the user's machine.
- That the fake Google image builds, and that its signed token is accepted by Keycloak end to end. No Task 23 test brokers; that is Task 23c.
- Whether the ID token carries `sub` without the `basic` scope, and whether the account console is reachable (findings 6 and 7).
- `mvn spotless:check`: it fails in the worktree with "Cannot find git repository". I rely on the developer's scratch-clone run.
- The full `mvn verify`: not re-run, per the card.

## Test Report
`task-23-keycloak-realm-test-report.md` (same folder). Fresh targeted run: DiscoveryCheckTest 11, SignInPropertiesTest
19, IdentityClockTest 2, RealmImportIT 13, for a total of 45 with 0 failures and 0 errors (`mvn ... verify` exit 0, BUILD SUCCESS).
