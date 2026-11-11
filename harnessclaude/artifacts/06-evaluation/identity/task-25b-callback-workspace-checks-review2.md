---
module: identity
feature: callback-workspace-checks
task: 25b
verdict: pass
attempt: 2
date: 2026-11-10
reviewer: tsolve-code-reviewer (Claude Sonnet 5.5, independent, read-only)
iteration: 1
---

# Review 2: identity — Task 25b: callback-workspace-checks (KAN-297)

Verdict: **APPROVE (pass), attempt 2.** Findings: 0 Critical, 0 Important, 2 Minor (both informational, new).
Scope: the uncommitted diff in `T-Solve-KAN-297` against `origin/main`; production files `OidcCallbackHandler.java`,
`SignInStartController.java`, `MockSignedInCallerFilter.java`. Numbering below follows the card (Imp1 = missing email,
Imp2 = formatting; in review 1 these were findings 2 and 1).

## Status of the first review's findings

| Finding | Status | Evidence |
|---|---|---|
| Imp1 missing/blank `email` | Fixed | `OidcCallbackHandler.java:108-114`: check after the Active step, before `runIn`; one ERROR with the Workspace id only; MSG06; `redirect` expires the cookie. Test `CallbackAccountChecksTest.java:403-434` (absent, null, empty, blank): the mock `signIn` succeeds for any input (`:75-77`), `verifyNoInteractions(accounts)`, nothing bound, one ERROR line, no name in the log. Order of AC-001.14 intact (`email_verified` -> Organization/`hd` -> Active -> email -> Account). |
| Imp2 formatting | Not verifiable by me | Developer reports a Palantir 2.39.0 dry run listing no file; the new `runIn` call is laid out as the formatter proposed (`:119-120`). `spotless:check` cannot run in a worktree; CI decides. |
| Min3 blank domain | Fixed (bare MSG122) | `:98` bare MSG122 for null or blank domain; `matchesHd` `:162-164` false for null or blank. Test `:436-` (blank domain, blank `hd`). URL-encoding of a non-blank domain not added (see Minor A). |
| Min4 ERROR line | Fixed | `:121-132`: `e.getClass().getName()` and root cause class name, no message. Test `:362-401` (3 failure kinds) asserts both class names, one ERROR line, no name in the log; the exception messages carry email, sub and name. |
| Min6 mock filter | Fixed | `MockSignedInCallerFilter.java:31-34`: `shouldNotFilter` true for `/api/v1/auth/`. Test `skipsSignInPaths_KAN_297_Minor_6`. Only controllers under that prefix: sign-in start, callback, dev claims page (all pick the Workspace from the host). `/api/v1/users` etc. still filtered. |
| Min7 Javadoc | Fixed | `OidcCallbackHandler.java:28-33` names AC-001.7, .10, .11, .14, .74 and MSG122/120/06/09/10. |
| Min5, Min8 | Decided by the user, not counted | |

## Security focus on the changed lines
- Personal data in logs: the two ERROR lines carry the Workspace id and class names only; no email, `sub`, name or
  message. Tests prove it with messages that contain all three.
- Cookie and session on the new MSG06 path: it goes through `redirect()`, which expires `__Host-TSOLVE_AUTHREQ`; no
  session or `SecurityContext` is kept; `signIn` and `runIn` are not reached (nothing bound).
- Mock filter skip: prefix `/api/v1/auth/` with the trailing slash, so `/api/v1/authz` is not matched; the
  filter is `identity-mock` (dev) only; a path with `..` or an encoded slash is refused by Spring Security's
  firewall before the filter. No non-auth path is exposed.
- SignInStartController: null or blank Organization id now gives MSG141, no state, no cookie (AC-001.48).

## Minor (informational, do not block)
- A. `OidcCallbackHandler.java:99` — a non-blank Google domain goes into `&google_domain=` unencoded. The value comes
  from the Workspace record (validated domain), not from the request; encode with `URLEncoder` when convenient.
- B. `MockSignedInCallerFilter.java:34` — the mock Workspace is also no longer bound on `/api/v1/auth/dev/claims`;
  that page reads the token claims in the same request and needs no Workspace, so this is correct. Say in the PR
  text that this is another owner's mock code (approved by the user).

## Traceability
`bun harnessclaude/hooks/sdd.ts trace identity` (last line): `trace identity: 155/191 acceptance criteria have a test`.
AC-001.7, .10, .11, .14, .48, .74 are covered; the 36 uncovered criteria belong to other tasks.

## Test Report
Targeted run (worktree, fresh), `mvn -q -Dspotless.check.skip=true -Dtest='CallbackAccountChecksTest,SignInStartControllerTest,OidcCallbackHandlerTest,MockSignedInCallerFilterTest' verify`:
`CallbackAccountChecksTest` 38, `SignInStartControllerTest` 27, `OidcCallbackHandlerTest` 25,
`MockSignedInCallerFilterTest` 5; Failures 0, Errors 0, Skipped 0 (95 tests). The `-Dtest` filter does not narrow the
failsafe phase, so the whole IT suite started behind it; I did not wait for it (about 10 min) and report no IT result
of my own. The developer's full verify (1236 unit, 407 IT, 0 failures) stands as the card says. `spotless:check` not
run (worktree).

Status: nothing changed in `progress.json`. Proposed: `passing` once the orchestrator records this attempt.
