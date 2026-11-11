---
module: identity
feature: callback-workspace-checks
task: 25b
verdict: fail
attempt: 1
date: 2026-11-10
reviewer: tsolve-code-reviewer (Claude Fable 5.2, independent, read-only)
iteration: 1
---

# Review: identity — Task 25b: callback-workspace-checks (KAN-297)

## Summary
Scope: the uncommitted diff in the worktree `T-Solve-KAN-297` on `feat/KAN-297-identity-callback-workspace-checks`
(HEAD = `origin/main` = `a28db06d`, confirmed). Production change: `signin/OidcCallbackHandler.java` (+71) and
`signin/SignInStartController.java` (+5/-2); `backend/pom.xml` one line (Testcontainers 1.21.4, approved, not
reviewed further). Tests: new `CallbackAccountChecksTest`, changed `CallbackAccountIsolationIT`,
`SignInStartControllerTest`, `OidcCallbackHandlerTest`, fixtures and wiring. The spec change (AC-001.74) is in the
spec, the change log and `approvals.json` before the code.

Verdict: **CHANGES NEEDED (fail), attempt 1.** Findings: 0 Critical, 2 Important, 6 Minor.
Severity used here: Critical = a security or data defect; Important = fix before the commit; Minor = can follow.

The tenant gate itself is sound. I found no cross-tenant path: the Workspace comes from the host only, the saved
request was matched against the same host before the handler runs, a `beta` token on `acme` is refused before the
Account step, `signIn` runs only inside `TenantScope.runIn` and outside any transaction, every answer expires the
cookie, no session or `SecurityContext` is kept, and no email, `sub` or name reaches the log. The verdict is fail
for two smaller reasons: the formatting gate of `mvn verify` would fail on 4 files (finding 1), and the third
trigger of AC-001.74 (no `email` claim) holds only on a first sign-in and its test hides that (finding 2).

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass, one exception | 5 of 6 criteria fully met; AC-001.74 partly (finding 2) |
| Completeness | pass | Every criterion has a test that asserts it; no EXTRA behaviour |
| Correctness | fail | Targeted tests pass (96 unit, 4 IT, re-run fresh); a Palantir dry-run shows `spotless:check` would fail (finding 1) |
| Integration | pass, with notes | Constructor change wired in all tests; `identity-mock` + `dev` conflict (finding 6); three host lookups per callback (finding 5) |

### Stage 1: spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-001.7 Workspace only from the host and the saved request | PASS | `OidcCallbackHandler.java:88-89` (host only), `:108` (scope bound with that id); the saved request's Workspace was matched to the same host in `AuthSecurityConfig.java:113-120`. Tests: `takesTheWorkspaceOnlyFromTheHost_AC_001_7` (parameter, header, cookie naming BETA are ignored; only `acme.localhost` is looked up), IT `refusesBetasGoogleUserOnAcmeAndChangesNeitherDatabase_AC_001_7_edge106` |
| 2 | AC-001.10 Workspace not Active → MSG120 | PASS | `:101-104`. Tests: `refusesAWorkspaceThatIsNotActiveWithMsg120_AC_001_10`, first-failure row (d) |
| 3 | AC-001.11 Organization and `hd`, both required → MSG122 + `google_domain` | PASS | `:95-100`, `:138-148`. Tests: 9 Organization rows (absent, null, empty, BETA's, list, string, no `id`, null and blank Workspace id), 6 `hd` rows (absent, null, empty, BETA's, suffix, subdomain), case-only difference accepted. Hardening gap: finding 3 |
| 4 | AC-001.14 order, first failure only | PASS | `:83-122`: `email_verified` (MSG06) → Organization and `hd` → Active → Account → Account Active. Tests: `reportsOnlyTheFirstFailure_AC_001_14` (5 pairs, each also asserts the Account step never ran), `mapsTheAccountOutcomeToItsMessage_AC_001_14` |
| 5 | AC-001.48 no Google domain or no Organization id at sign-in start | PASS | `SignInStartController.java:63-73`. Tests: 302 to MSG141 (null and blank id), nothing saved and no cookie (4 hosts), one WARN with the id only (no host, slug, domain or `returnTo`) |
| 6 | AC-001.74 Account step fails → MSG06, cookie expired, one ERROR with the Workspace id | PARTIAL | `:105-118`. `signIn` throws and database down: met and tested (the exception messages in the test carry the email, `sub` and name, so a logged message would fail it). No `email` claim: met only when no Account holds the `sub` yet (finding 2) |

Tests against their names: each test asserts what it says; none passes vacuously. The QA hand-off records the red
run (29 of 30 failing against a logic-less stub); I did not repeat the red run.

## Traceability
`bun harnessclaude/hooks/sdd.ts trace identity` (worktree, 2026-11-10):

```
✓ AC-001.7   signin/CallbackAccountChecksTest.java, signin/CallbackAccountIsolationIT.java
✓ AC-001.10  signin/CallbackAccountChecksTest.java, signin/SignInStartControllerTest.java
✓ AC-001.11  signin/CallbackAccountChecksTest.java
✓ AC-001.14  signin/CallbackAccountChecksTest.java
✓ AC-001.48  signin/SignInStartControllerTest.java
✓ AC-001.74  signin/CallbackAccountChecksTest.java
trace identity: 155/191 acceptance criteria have a test
```

The command exits 1 because 36 criteria of other tasks (sessions, Operator sign-in, logout, STORY-010) have no test
yet; none belongs to Task 25b.

## Issues

### Important

1. **`mvn spotless:check` would fail on 4 files** — `OidcCallbackHandler.java:108-109`,
   `CallbackAccountIsolationIT.java:144-145` and `:201`, `SignInStartControllerTest.java:244-245` and `:265-266`,
   `CallbackAccountChecksTest.java:226` and `:368-369`.
   The developer could not run Spotless in the worktree and formatted by hand. I ran the Palantir formatter 2.39.0
   (the version Spotless 2.43.0 put in the local Maven cache) as a dry run on the 11 changed Java files; it would
   rewrite these 4. Example, the only production hunk:
   ```java
   // now
   result = scope.runIn(workspace.id(), () -> accounts.signIn(
           token.getSubject(), token.getEmail(), token.getClaimAsString("name")));
   // formatter
   result = scope.runIn(
           workspace.id(),
           () -> accounts.signIn(token.getSubject(), token.getEmail(), token.getClaimAsString("name")));
   ```
   Limit of this evidence: it is the formatter's command line, not the Maven plugin. The command line also re-wraps
   long strings, which Spotless does not, so I left out the two string-only hunks it reported
   (`CallbackAccountIsolationIT.java:99`, `SignInStartControllerTest.java:246-247`). The seven hunks above are plain
   layout and I expect the plugin to report them.
   Fix: run `mvn spotless:apply` on these files from a normal clone (not a worktree), then `mvn spotless:check`.

2. **AC-001.74, no `email` claim: refused only on a first sign-in; the test's mock hides it** —
   `OidcCallbackHandler.java:106-118`, `AccountSignInService.java:59-64`, `CallbackAccountChecksTest.java:75-77`.
   The handler has no check of the email. It relies on the `NullPointerException` that `signIn` throws at
   `email.toLowerCase` (`AccountSignInService.java:67`). That line is reached only when no Account holds the `sub`.
   For an Account already bound to the `sub`, `signIn(sub, null, name)` returns `SIGNED_IN`, so a token with
   `email_verified` true and no `email` signs in instead of answering MSG06. A blank email gives MSG09 (unbound)
   or `SIGNED_IN` (bound). The unit test cannot show this: its mock throws for every null email with the comment
   "as the real service does", which is true only for the unbound case.
   Risk: low. The Account is still found by its bound `sub` and the Organization and `hd` were checked. It is a
   spec gap, not a tenant leak.
   Fix: check the claim in the handler after the Active check and before `runIn` (the AC-001.14 order is kept):
   ```java
   String email = token.getEmail();
   if (email == null || email.isBlank()) {
       log.error("Sign-in failed in the Account step of Workspace {} (no email claim)", workspace.id());
       redirect(response, MSG06);
       return;
   }
   ```
   Add a test row where the mock returns `SIGNED_IN` for a null email (it fails today), and correct the mock's
   comment. Judgment call for the spec owner: see "Assumptions made".

### Minor

3. **A blank Google domain passes the `hd` check and goes raw into the redirect** —
   `OidcCallbackHandler.java:146-148` and `:97-98`. `namesOrganization` refuses a null or blank Organization id,
   but `matchesHd` only refuses null: a Workspace whose domain is `"  "` matches an `hd` of `"  "`. The redirect
   then carries `&google_domain=  ` unencoded. Not reachable today (sign-in start refuses that Workspace and Google
   sends no blank `hd`), but the tenant gate should fail closed.
   Fix: `return domain != null && !domain.isBlank() && hd instanceof String value && value.equalsIgnoreCase(domain);`
   send the bare `MSG122` for a null or blank domain, and URL-encode the value
   (`URLEncoder.encode(domain, StandardCharsets.UTF_8)`). Add the `blankdomain.localhost` row to the `hd` test.

4. **The ERROR line gives too little to diagnose a failure** — `OidcCallbackHandler.java:112-115`. Only the simple
   class name is logged, so a database outage, a scope conflict and a programming error look alike and there is
   no stack. Dropping the message is right (it can hold personal data).
   Fix: log `e.getClass().getName()` and the root cause's class name; optionally the top stack frame
   (`e.getStackTrace()[0]`), which holds no personal data.

5. **The handler looks the Workspace up again instead of using the one the request check matched** —
   `OidcCallbackHandler.java:88-89` against `AuthSecurityConfig.java:61-63` and `:113-120`. One callback asks the
   lookup three times (chain matcher, request check, handler). All three use the same host, so there is no leak
   today. With the real platform lookup the answers can differ between calls and each is a query.
   Fix: have `CheckedRequests` put the matched `WorkspaceRef` in the request attribute (in place of
   `Boolean.TRUE`) and let the handler read it, with MSG141 when it is absent. Can wait for the platform adapter.

6. **`dev` + `identity-mock`: a callback on any Workspace but the mock one ends in MSG06** —
   `identity/mock/MockSignedInCallerFilter.java:42` binds the mock Workspace (default `…00a1`, Acme) around every
   `/api/*` request, the callback included; `LocalTenantScope.java:28-29` refuses a second, different Workspace,
   so `scope.runIn` at `OidcCallbackHandler.java:108` throws on `beta.localhost` and the user sees MSG06 with one
   ERROR `IllegalStateException`. Acme works.
   Fix: not in this PR's files. Tell the owner of `identity-mock` to skip `/api/v1/auth/` in `shouldNotFilter`, or
   note it in the PR text until the mock is removed (Task 26a/26b).

7. **Stale class comment** — `OidcCallbackHandler.java:28-32` still says "A failed request check answers MSG141,
   any other failure MSG06" and lists only AC-001.6, .8, .9, .53, .55.
   Fix: name the new checks and messages (AC-001.7, .10, .11, .14, .74; MSG122, MSG120, MSG09, MSG10).

8. **Test gaps, none blocking** — `CallbackAccountChecksTest.java`: no accepted case for an `organization` claim
   with several entries where one matches (the `anyMatch` at `OidcCallbackHandler.java:142-143`); no row for an
   `id` that is not a string; the `domain == null` branch at `:98` has no test (QA dropped it because the spec is
   silent, accepted in the card).

### Notes (no action in this task)

- Outside `dev` a sign-in that passes every check binds the `sub` and records the sign-in time, then answers MSG06
  ("session not built yet", `OidcCallbackHandler.java:131-133`). Interim until Task 26a; not reachable today
  because `NoWorkspaceLookup` knows no Workspace. The IT asserts exactly this.
- A lookup failure (`findBySiteHost` throwing) is outside AC-001.74 and would be a 500. The dev and in-memory
  lookups cannot throw; decide the behaviour when the platform adapter is written.
- Known and accepted in the card, not counted: bare MSG122 for a Workspace with no Google domain; the platform
  change requests not sent; the two spec wording conflicts.
- `tasks.md` lists `port/{WorkspaceLookup,NoWorkspaceLookup}.java` as modified; they are unchanged (nothing in
  them needed to change).

## Security Review — identity (Task 25b diff) — 2026-11-10

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 0 |
| Dependencies | not audited | | | |
| Code | 0 | 0 | 0 | 1 (finding 3) |
| STRIDE | 0 | 0 | 0 | 2 (findings 2, 4) |

- Secrets: no hit in the diff; only `.env.example` files are tracked.
- Dependencies: the project has no Maven audit plugin; the one change is test-scope Testcontainers 1.21.4.
- Spoofing / elevation: a `beta` token on `acme` is refused with MSG122 before any Account is read (unit rows and
  the two-database IT: neither database changes, `signIn` never called). Claim handling fails closed for an
  absent, null, list, string or id-less `organization` and for an absent, null, empty, suffix or subdomain `hd`.
  The realm import sets `addOrganizationId`, and the end-to-end IT of the developer's run passes the check with a
  real Keycloak token for both Workspaces.
- Tampering: no request parameter, header or cookie reaches the Workspace choice. `X-Forwarded-Host` is trusted by
  design (`forward-headers-strategy: framework`, documented in `application.yml:42-46`); unchanged by this task.
- Information disclosure: the redirects carry only a message code and the Workspace's own Google domain (by
  spec). No email, `sub`, name or exception text is logged; never a 500 from the Account step.
- Repudiation: a refusal with MSG122, MSG120, MSG09 (no Account) or MSG10 leaves no log line. The spec does not
  ask for one; see "Assumptions made".
- Session and cookie: the chain is stateless; every path of the handler expires `__Host-TSOLVE_AUTHREQ`; no
  T-Solve session exists yet. The Keycloak SSO session stays after a refusal (Keycloak's side, logout is another
  task).
- Transaction boundary: the handler has no `@Transactional`; the IT observes no active transaction inside
  `signIn` and the Acme Workspace bound.

## Assumptions made

1. AC-001.74 reads "IF the Account step fails itself (…, or the verified ID token has `email_verified` true but
   no `email`)". I read the missing email as a trigger of its own, as design row 133 does ("MSG06"). If the owner
   means "only when the step really throws", the code is right and only the mock's comment and the test name
   need to say "first sign-in". Finding 2 depends on this.
2. "Log one ERROR entry" is read as: the callback writes one. A real database outage can add ERROR lines from
   Hibernate or the pool; no IT covers that and I did not test it.
3. Whether a refused cross-tenant sign-in (MSG122) should leave a WARN with the Workspace id is not in the spec. I
   did not count its absence as a finding.

## Test Report
Details in `task-25b-callback-workspace-checks-test-report.md`.

- Re-run by me (worktree, fresh): `mvn -Dspotless.check.skip=true -Dtest='CallbackAccountChecksTest,SignInStartControllerTest,OidcCallbackHandlerTest,AuthSecurityConfigTest' -Dit.test='CallbackAccountIsolationIT' -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false verify`
  → unit `Tests run: 96, Failures: 0, Errors: 0, Skipped: 0`; IT `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`;
  `BUILD SUCCESS`, 01:05 min.
- Not re-run (the card's instruction): the full suite. The developer reports 1227 unit and 407 IT, 0 failures,
  with Spotless skipped. `KeycloakSignInEndToEndIT` and `TwoInstanceCallbackIT` were not re-run by me.
- Not verified: `mvn spotless:check` through the Maven plugin (finding 1 is from the formatter's dry run);
  coverage; whether the tree changed after the developer's verify (the work is uncommitted, so there is no commit
  to compare).

Status: nothing changed in `progress.json` (the feature is still `pending`). Proposed after the orchestrator
records this attempt: `fixing`.
