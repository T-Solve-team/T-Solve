# Test Report: identity — Task 25b: callback-workspace-checks (KAN-297)

**Date:** 2026-11-10
**State tested:** worktree `T-Solve-KAN-297`, branch `feat/KAN-297-identity-callback-workspace-checks`, uncommitted
on top of `origin/main` `a28db06d`.
**Commands:**
- Targeted tests (compiles main and test code first):
  `cd backend && mvn -Dspotless.check.skip=true -Dtest='CallbackAccountChecksTest,SignInStartControllerTest,OidcCallbackHandlerTest,AuthSecurityConfigTest' -Dit.test='CallbackAccountIsolationIT' -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false verify`
- Trace: `bun harnessclaude/hooks/sdd.ts trace identity`
- Format (dry run, nothing written): Palantir Java Format 2.39.0 command line, `--palantir -n`, on the 11 changed
  Java files
- Full `mvn verify`: not re-run (the card gives the developer's result). Coverage: the project has no coverage
  command.

The card's own command ends in `verify` with only `-Dtest`; that would run all 407 ITs. I added `-Dit.test` so the
run stays targeted, and added the two changed classes the card did not list (`AuthSecurityConfigTest`,
`CallbackAccountIsolationIT`).

## Results
- Unit: Total 96 · Passed 96 · Failed 0 · Skipped 0
  - `CallbackAccountChecksTest` 30 · `SignInStartControllerTest` 27 · `OidcCallbackHandlerTest` 25 ·
    `AuthSecurityConfigTest` 14
- IT (Docker 29.2.1): `CallbackAccountIsolationIT` Total 4 · Passed 4 · Failed 0 · Skipped 0 (34.7 s)
- Build: `BUILD SUCCESS`, total 01:05 min
- Compile: pass · Format: **would fail** (see Failures) · Lint: the backend has none besides Spotless
- Developer's full verify (not re-run): unit 1227 / 0 failures; IT 407 / 0 failures / 0 skipped; `BUILD SUCCESS`
  12:14 min with `-Dspotless.check.skip=true`

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not measured (no coverage command) | none in the spec |

## Acceptance Criteria → Tests
```
✓ AC-001.7   backend/src/test/java/vn/tsolve/identity/signin/CallbackAccountChecksTest.java, backend/src/test/java/vn/tsolve/identity/signin/CallbackAccountIsolationIT.java
✓ AC-001.10  backend/src/test/java/vn/tsolve/identity/signin/CallbackAccountChecksTest.java, backend/src/test/java/vn/tsolve/identity/signin/SignInStartControllerTest.java
✓ AC-001.11  backend/src/test/java/vn/tsolve/identity/signin/CallbackAccountChecksTest.java
✓ AC-001.14  backend/src/test/java/vn/tsolve/identity/signin/CallbackAccountChecksTest.java
✓ AC-001.48  backend/src/test/java/vn/tsolve/identity/signin/SignInStartControllerTest.java
✓ AC-001.74  backend/src/test/java/vn/tsolve/identity/signin/CallbackAccountChecksTest.java
trace identity: 155/191 acceptance criteria have a test
```
Exit code 1: 36 criteria of the module have no test yet (AC-001.18, .20 to .27, .29 to .34, .38, .66, .69 to .72,
AC-002.1 to .6, .8, AC-010.1 to .8). All belong to later tasks; none to Task 25b.

## Failures

### Format — 4 files would be rewritten by the formatter
- Files and lines: `OidcCallbackHandler.java:108-109`; `CallbackAccountIsolationIT.java:144-145`, `:201`;
  `SignInStartControllerTest.java:244-245`, `:265-266`; `CallbackAccountChecksTest.java:226`, `:368-369`.
- Cause: formatted by hand, because Spotless cannot run in a git worktree ("Cannot find git repository").
- Evidence and its limit: the formatter's command line, not the Maven plugin. The command line also re-wraps long
  strings, which Spotless does not; the two string-only hunks it reported
  (`CallbackAccountIsolationIT.java:99`, `SignInStartControllerTest.java:246-247`) are left out for that reason.
  The other 7 files are unchanged by the formatter, and it reported no unused import.
- Suggested fix: `mvn spotless:apply`, then `mvn spotless:check`, from a normal clone.

No test failed.

## Notes
- Flaky: none seen (one run). Skipped: none. Slow (> 5 s): `AuthSecurityConfigTest` 9.9 s (context start),
  `CallbackAccountIsolationIT` 34.7 s (containers).
- Not re-run by me: `KeycloakSignInEndToEndIT` (7), `TwoInstanceCallbackIT` (23) and the rest of the suite.
- A test that would fail today and is missing: `signIn` returning `SIGNED_IN` for a token with no `email`
  (AC-001.74, review finding 2). The existing mock throws for every null email, which the real service does only
  when no Account holds the `sub`.
- Logs (outside the repo): `C:\Users\ADMIN\AppData\Local\Temp\claude\5794e928a0a4597c\cad3773a-aed8-4f2f-b0d6-3997b41a77fb\scratchpad\kan297-targeted.log`
  and `C:\Users\ADMIN\AppData\Local\Temp\claude\5794e928a0a4597c\cad3773a-aed8-4f2f-b0d6-3997b41a77fb\scratchpad\kan297-trace.log`.
