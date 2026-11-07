---
module: identity
feature: remove-custom-sign-in
task: 24
verdict: pass
attempt: 2
date: 2026-11-08
reviewer: tsolve-code-reviewer (Claude Opus 5.5, independent, read-only)
iteration: 1
---

<!-- attempt 1 (below, "Review") was verdict: fail; attempt 2 is the "Re-review" section at the end. -->

# Review: identity — Task 24: remove-custom-sign-in (KAN-295)

## Summary
Scope: the uncommitted worktree `T-Solve-KAN-295` on `refactor/KAN-295-identity-remove-custom-sign-in` (base
`a7c7659b`). It has 50 changed files (+110 / −6228) and 3 new untracked files (changesets `021` and `022`,
`SignInTablesDroppedIT`). I read the full diff of every file that was not deleted. For the deleted files I read the
list only, plus the old `SignInStateService.filterReturnTo` and the old `SignInStateServiceTest` AC-001.3 cases, to
compare them with the moved code.

The removal itself is clean:
- The 14 production classes from the task list are gone. No production code, config, compose file, CI file or doc
  still references them, and the build compiles.
- `TSOLVE_SIGNING_KEY`, `GOOGLE_CLIENT_*` and `tsolve.oidc.google.*` are gone from `application.yml`, `.env.example`
  and the `SignInProperties` record. `PLATFORM_OPERATOR_EMAILS` stays, as spec Functional req. 4 lists it.
- `filterReturnTo` was moved byte for byte (compared with `HEAD:SignInStateService.java:105-120`).
- The Task 23 security rules are unchanged: both chains are `STATELESS`, the callback chain keeps the
  `NullSecurityContextRepository`, CSRF is off only on the two GET redirect paths (now 2 paths instead of 4), and the
  Workspace host guard, the state/Workspace check and the cookie expiry are untouched.
- The `admin.` 404 follows Q1. The OpenAPI file drops `authorize`, `complete` and the `login.` servers entry (Q2).
  `021` and `022` use `DROP TABLE IF EXISTS`, come last in the master changelog, and `004`–`007` are untouched.

Verdict: **FAIL (fix first)**, 3 blockers, all small:
1. `mvn spotless:check` fails on `SignInProperties.java`, so `mvn verify` is not green.
2. A frontend contract test now asserts that the callback never names MSG120 and calls MSG120 "retired". That
   contradicts the live AC-001.10.
3. The move of `filterReturnTo` dropped its only tests for control characters and the 2,048-character limit. Those
   are the open-redirect and header-injection guards of AC-001.3.

There are also 7 non-blockers.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Every item of the Task 24 description is done (see Stage 1). `/sign-in` on `admin.` → 404, no Location, nothing saved (`SignInStartControllerTest:186-194`). |
| Completeness | fail | The task passes no AC. AC-001.3 keeps a test, but the deleted cases are not replaced (blocker 3). The frontend contract test contradicts AC-001.10 (blocker 2). |
| Correctness | fail | Targeted tests pass (91 unit + 1 IT). `spotless:check` fails on 1 file (blocker 1). |
| Integration | pass | The OpenAPI change regenerates only the intended files. No frontend code used `authorize`/`completeSignIn`. `login.` and unknown hosts still answer 404 (`SignInStartControllerTest:250`, `AuthSecurityConfigTest:183`). |

### Stage 1 — task items (the task passes no AC; it retires AC-001.2, .4, .5, .17, .19, .35–.37, .39, .44–.47, .49–.51)

| # | Item | Status | Evidence |
|---|------|--------|----------|
| 1 | Delete the 14 classes and 12 tests from the list | PASS | `git status`: all `D`; plus `CallbackAccountIsolationIT` (Q3), `AccountStore`, `FakeGoogleIssuer`, `MutableClock` (orphans, QA Q6 A) |
| 2 | New changesets drop `sign_in_states`, `hand_off_code`; `004`–`007` unedited | PASS | `changes/021-…sql`, `changes/022-…sql`, master `:40-41`; `SignInTablesDroppedIT` green |
| 3 | `TSOLVE_SIGNING_KEY`, `tsolve.oidc.google.*`, `GOOGLE_*` gone from the web process | PASS | `application.yml:83-87`, `.env.example`, `SignInProperties.java:11-15`; no hit outside tests/ADR/harness |
| 4 | `authorize`, `complete` gone from OpenAPI; regenerated | PASS | `tsolve-api.yaml`; generated diff = 6 files, only removals plus the callback params |
| 5 | `AuthHosts` keeps only Workspace site vs `admin.` | PASS | `AuthHosts.java` (`isLoginHost`, `loginHost` removed) |
| 6 | `filterReturnTo` intact (AC-001.3) | PASS (code) / see blocker 3 (tests) | `SignInStartController.java:80-99` = `HEAD:SignInStateService.java:101-120` |
| 7 | Q1 `admin.` 404, Q2 callback text, Q3 IT deleted, Q4 no `AuthHostsTest` | PASS | as above |
| 8 | `sdd.ts trace` lists no retired id | PASS | trace below: none of the 16 ids appears |
| 9 | Build and suites stay green | FAIL | `spotless:check` (blocker 1) |

### Security pass (security-review skill; auth code)
- Secrets: the diff has none. Only test placeholders (`test-keycloak-secret`), and only `.env.example` files are
  tracked (`.gitignore` covers `.env*`). Dependencies: `pom.xml` and `package*.json` are unchanged, so I ran no
  audit.
- Spoofing / Tampering: the state, PKCE, host-bound Workspace check and cookie expiry are unchanged. The `returnTo`
  filter code is unchanged, but its tests are thinner (blocker 3). The removed `authorize` and `complete` now fall to
  the authenticated chain (anonymous → 401), so no surface is left open.
- Elevation of privilege: `admin.` sign-in is now 404, which is less surface. `login.` is no longer a special host;
  it goes through the Workspace lookup and gets 404, which is safe because site names are reserved (platform spec)
  and Keycloak serves `login.`.
- No Critical or High finding.

## Traceability
`bun harnessclaude/hooks/sdd.ts trace identity STORY-001` (exit 1 because of later tasks' criteria):
`trace identity (STORY-001): 21/56 acceptance criteria have a test`.
- ✓ AC-001.1, .3, .6, .8, .9, .10, .12, .15, .16, .40–.43, .48, .52–.55, .63–.65.
- ✗ AC-001.7, .11, .13, .14, .18, .20–.34, .38, .56–.62, .66–.72. These belong to Tasks 25a–29.
- **AC-001.28 is now ✗.** Its old test pinned the old `login.` authorize path. AC-001.28 is already rewritten for
  `tsolve-ops` and is owned by Task 29 (`tasks.md:658`), so this is expected per Q1.
- None of the 16 retired ids is listed.

## Issues

### Blockers
1. **`backend/src/main/java/vn/tsolve/identity/signin/SignInProperties.java:11-15`**: `spotless:check` fails.
   Palantir puts the four record components on one line. I ran the check on an LF-normalised copy, ratcheted
   against an empty base and limited to the 11 changed or related Java files; this was the only violation, and the
   other 10 files are clean. `mvn verify` (CLAUDE.md "verify") would fail.
   Fix: run `mvn spotless:apply` in a real clone, or reflow by hand, then run `spotless:check`.
2. **`frontend/src/shared/auth/sign-in.test.ts:229-234`**: the test is named "not MSG123 or the retired MSG120" and
   asserts `not.toContain('MSG120')` on the callback contract. MSG120 is not retired. AC-001.10 says the callback
   SHALL redirect to `/login?error=MSG120` when the Workspace is not Active, and AC-001.14 orders MSG09, MSG10 and
   MSG122 into the same callback. The test pins the opposite of a live criterion, and Task 25b would have to
   reverse it.
   Fix: drop the MSG120 negative assertion and the word "retired". Keep MSG06, MSG141 and "never MSG123". Optionally
   add one line to the callback description in `tsolve-api.yaml:355-360` saying that MSG09, MSG10, MSG120 and MSG122
   come with the Workspace and Account checks (Task 25b).
3. **`backend/src/test/java/vn/tsolve/identity/signin/SignInStartControllerTest.java:148-157`**: the deleted
   `SignInStateServiceTest` held the only cases for these filter rules:
   - tab, space, LF, CR, NUL, DEL
   - backslash inside the path, `///`
   - `javascript:`
   - the 2,048 / 2,049 / 3,000-character boundary.

   The remaining 8 cases cover none of them. If someone removed the control-character loop or the length check in
   `SignInStartController.java:84-99`, every test would stay green. The Javadoc presents those rules as AC-001.3,
   and the Keycloak design keeps "the same `returnTo` filter" (`design.md`, new section, `SignInStartController`
   row).
   Fix: move the two parametrized sources (`keptReturnTo` and `replacedReturnTo`, 4 + 19 cases) into a plain unit
   test of the package-private `SignInStartController.filterReturnTo`, named `…_AC_001_3`.

### Non-blockers
4. **`docs/bug-log.md:177` (BUG-165, Open)**: this PR fixes BUG-165 for `SignInStartControllerTest`. It adds
   `RedisAuthorizationRequestRepository`, `OidcCallbackHandler`, `@MockBean StringRedisTemplate`,
   `KEYCLOAK_WEB_CLIENT_SECRET` and `@Primary` on the recording repository, and deletes the other three failing
   classes. The developer's note says "no failure seen … to compare with main", but main is red for exactly this.
   Fix: in the same commit, set the BUG-165 row to Fixed (KAN-295) and point it at `SignInStartControllerTest` (and
   the deletions), as the project rule requires.
5. **Test ids**:
   - `SignInTablesDroppedIT:19` uses "edge 1" and `SignInStartControllerTest:187` uses "edge 2". Design rows 1 and 2
     are STORY-004 user-list rows (`design.md:83-84`), so the ids point at the wrong risks.
   - Design row 120 does exist (`design.md:821`, Task 24); QA step 1 missed it.

   Fix: name the IT `…_edge120` (or `K12`), and name the `admin.` test after Q1 / AC-001.28 interim, without a
   false edge number.
6. **Leftover test properties**: 21 test classes, not ~12, still pass `TSOLVE_SIGNING_KEY` / `GOOGLE_CLIENT_*`.
   These include `AuthSecurityConfigTest:69-71`, which this PR edits, and the identity classes `DiscoveryCheckTest`,
   `RedisWiringIT`, `RedisAuthorizationRequestRepositoryIT`, `KeycloakSignInEndToEndIT` and `TwoInstanceCallbackIT`.
   `SignInTestFixtures.KEY` exists only for them. This is harmless at runtime (Spring ignores unbound values), so it
   is acceptable as a follow-up, but it misleads readers into thinking the key is still needed.
   Fix: trim at least the identity classes now (and `AuthSecurityConfigTest`, which is touched anyway), and open a
   cleanup issue for the other modules' ITs.
7. **Small text and style fixes**:
   - `.env.example:27` still says "serves both the old login. host … until Task 23b"; the text is stale now.
   - `SignInProperties.java:8` ends with "value..".
   - `SignInStartControllerTest.java:69,76` uses fully qualified `@org.springframework…Primary` / `…MockBean`;
     import them as `AuthSecurityConfigTest` does.
8. **`frontend/openapi/tsolve-api.yaml:336`**: the `signIn` text names MSG122 as a callback failure, but the
   `authCallback` text lists only MSG06 and MSG141. Neither path documents its 404 (an `admin.` or unknown host),
   and `signIn` does not document its 302 `/login?error=MSG141` (AC-001.48). The `Set-Cookie` text (`:384`) could
   name `__Host-TSOLVE_AUTHREQ`. Make the two texts agree.
9. **Harness bookkeeping, for the orchestrator**:
   - `progress.json` has `remove-custom-sign-in: pending`.
   - There is no session log `05-implementation/identity/task-24-*.md`.
   - Task 24 in `tasks.md` does not record the Q1–Q8 answers.
   - The Task 23b note at `tasks.md:493` ("AC-001.28 test stays green") is stale.

   Fix: record the answers, and move the feature through `sdd.ts` to `evaluating` before re-review.

Known and not for this PR (card): Caddy header stripping (Task 28), the second-tab cookie overwrite, and no rate
limit on the anonymous start. Deployment note: remove `TSOLVE_SIGNING_KEY` and the web process's `GOOGLE_*` from the
server `.env` and the GitHub Actions secrets. The spec keeps `GOOGLE_*` only for the `ops` profile.

### Assumptions made
- "Developer's note that ~12 tests still pass unused properties": acceptable as a follow-up (issue 6), not a
  blocker. The count is 21.
- Blocker 3 is a blocker because the filter is a security guard and the fix only moves existing cases. Hiếu may
  downgrade it to a follow-up.
- The interim callback contract that lists only MSG06/MSG141 is acceptable (it matches the code after Task 23c).
  Only the negative MSG120 assertion is wrong.

## Test Report
State reviewed: the uncommitted worktree, unchanged since the developer's run (`git status` matches the hand-off
list). I did not re-run the full verify (per the card).

- Targeted backend run: `cd backend && mvn -o -B -Dtest='SignInStartControllerTest,AuthSecurityConfigTest,SignInPropertiesTest,KeycloakAuthorizationRequestResolverTest,OidcCallbackHandlerTest,DiscoveryCheckTest' -Dit.test='SignInTablesDroppedIT' -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -Dspotless.check.skip=true -DargLine=-Dapi.version=1.44 verify`
  - The log is in the scratchpad: `…\scratchpad\kan295-targeted.log`.
  - Result: `BUILD SUCCESS`. Unit: `Tests run: 91, Failures: 0, Errors: 0, Skipped: 0` (AuthSecurityConfigTest 14,
    DiscoveryCheckTest 11, KeycloakAuthorizationRequestResolverTest 8, OidcCallbackHandlerTest 25,
    SignInPropertiesTest 11, SignInStartControllerTest 22). IT: `SignInTablesDroppedIT` 1/1 (8.6 s).
  - The one ERROR log line, "Redis failed while reading…", comes from an intended negative test.
- Spotless: the plugin cannot run in a worktree (`ratchetFrom origin/main`). I ran it on a copy of `backend/` in a
  scratch git repo, LF-normalised, with `origin/main` set to an empty commit and `-DspotlessFiles` limited to the 11
  changed or related files.
  - The log is in the scratchpad: `…\scratchpad\kan295-spotless3.log`.
  - Result: `11 files … 1 needs changes … 10 were already clean`, and the violation is in `SignInProperties.java`
    (blocker 1).
- Frontend: not re-run. The developer reports format:check, lint (0 errors), typecheck, 1442 tests, build and
  generate:api all passing, with only the intended diff. Blocker 2 is a test-content issue, not a failing test.
- Trace: see Traceability (21/56; no retired id; AC-001.28 now uncovered, owned by Task 29).
- Proposed status: `fixing` (attempt 1). I changed no status.

## Re-review (attempt 2, 2026-11-08)

Scope: the same uncommitted worktree after fix round 1. Files changed after attempt 1 (by mtime against this file):
`SignInProperties.java`, `SignInStartControllerTest.java`, `SignInStartControllerFilterReturnToTest.java` (new),
`SignInTablesDroppedIT.java`, `frontend/src/shared/auth/sign-in.test.ts`, `docs/bug-log.md`, and harness files
(`tasks.md` Jira keys, `progress.json`, the new session log `05-implementation/identity/task-24-remove-custom-sign-in.md`).
I read the full diff of each against `a7c7659b`, and I compared the branch with `origin/main` (`e2c496cc`, 4 commits
ahead of the base).

**Verdict: PASS with findings.** B1–B3 are fixed. The fix round adds no defect in code. Findings R1 and R2 come
from `origin/main` moving on since the base; the commit card must handle them when it rebases. R3 and R4 are test-name
issues.

### B1–B3
| # | Status | Evidence |
|---|--------|----------|
| B1 spotless | FIXED | `SignInProperties.java:11-12`: the four components are on one line, and "value.." is fixed (`:8`). Spotless ran on an LF copy with an empty `origin/main`, limited to the 4 touched Java files: `Spotless.Java is keeping 4 files clean - 0 needs changes`, `BUILD SUCCESS`. |
| B2 MSG120 | FIXED | `sign-in.test.ts:229-234`: "callback names MSG06 and MSG141, and not MSG123". No `MSG120` negative assertion and no "retired MSG120" remain. The word "retired" now only names `authorize`/`complete` (`:214`), which are retired (correct). The `MSG120` at `:271` (AC-012.4 "no message" list) was there before this PR (`a7c7659b:…:315`), so it is out of scope. |
| B3 filter tests | FIXED | `SignInStartControllerFilterReturnToTest` has the same 4 kept and 19 replaced cases as `origin/main:…/SignInStateServiceTest.java:252-286`: same labels, same inputs, same order. It calls the package-private static `SignInStartController.filterReturnTo` directly. I checked each branch against the code at `SignInStartController.java:84-96`. The tab/NUL/DEL/space cases fail if the `c <= ' ' / 0x7F` loop is removed, `"/a\\b"` fails without the `'\\'` check, and the 2,049 and 3,000 cases fail without `MAX_RETURN_TO_LENGTH`. Test run: 23/23. |

Renames: `SignInTablesDroppedIT` → `…_edge120` (`design.md:821`, the Task 24 row). The `admin.` test →
`answersNotFoundToSignInStartOnAdmin_AC_001_28_interim` (see R3). The 404 test asserts the status, no Location and
nothing saved. The fix-round edits in `SignInStartControllerTest` only drop the old-state assertions and helpers. No
AC-001.1/.3/.10/.48 assertion was weakened.

### Findings (attempt 2)
1. **R1 — rebase onto `origin/main` (commit card).** The base `a7c7659b` is 4 commits behind `e2c496cc`. Expected
   conflicts:
   - Modify/delete on `AuthorizeControllerTest`, `CallbackControllerTest`, `CallbackFailureLoggingTest` (PR #163) and
     `CallbackAccountIsolationIT` (PR #141, BUG-171). Keep them deleted.
   - `SignInStartControllerTest`: PR #163 added the same Redis repository, `OidcCallbackHandler`, `@MockBean` and
     `@Primary` lines with real imports. Take main's import style, which also closes old non-blocker 7c.
   - `db.changelog-master.xml`: main appends `020-tracker-suggestion-comment` and `019-review-queue-notify`. Put the
     identity `020`/`021` after them. The changeset ids do not clash (`identity:020-…` vs `tracker:020-…`), but the
     file number `020` repeats. Renaming the identity files to `021`/`022` is optional.
   - `frontend/openapi/tsolve-api.yaml` (main adds `/review-queue/events`): run `generate:api` again after the rebase.
   - `.env.example` and `docs/bug-log.md`.

   The code changes under the developer's verify, so run the full verify once after the rebase.
2. **R2 — `docs/bug-log.md` BUG-165 row.** Main's PR #163 (`625a9a58`, KAN-6, quick change, 08/11) already applied
   this fix to all four classes and left the row `Open`. After the rebase, this PR no longer fixes BUG-165. It only
   keeps the fix in `SignInStartControllerTest` and deletes the other three classes.
   - Fix: set "Fixed in" to PR #163 (`625a9a58`). Keep the Test column (the class's context starts; every test in it
     errors without the beans). In Notes, add that KAN-295 deletes the other three classes.
   - Main also has two duplicate rows of this bug: BUG-169, and a second row with id BUG-172 that copies BUG-165's text
     and is `Open`. Mark them as duplicates of BUG-165 (see judgment call 2).
   - Judgment on "no test carries the bug id": acceptable. The bug is in test wiring (a missing `@Import`), so the
     regression check is the whole class failing to start. A `_BUG_165` test that only loads the context would add
     nothing that the 22 tests do not already check.
3. **R3 — `SignInStartControllerTest.java:187-188` "AC-001.28 interim".** The name makes `sdd.ts trace` count
   AC-001.28 as ✓ (trace is now 22/56, up from 21). But the test asserts 404, and AC-001.28 (`spec.md:148`) requires
   a 302 to `tsolve-ops`. This is a false ✓ for a live criterion. It does not block, for two reasons: the name says
   interim, and the test fails as soon as Task 29 builds the `admin.` start. (Attempt 1, item 5, suggested this name;
   I withdraw that suggestion.)
   Fix: drop the AC id, e.g. `answersNotFoundToSignInStartOnAdminUntilTask29_Q1`. Otherwise Task 29's review must not
   accept this test as AC-001.28 coverage.
4. **R4 — `SignInStartControllerFilterReturnToTest.java:50-51`.** The old method was
   `replacesAnyOtherReturnToWithRoot_AC_001_3_edge19` ("AC-001.3 / edge 19"). The move dropped `edge19`, but design
   row 19 (`04-plans/identity/design.md:520`) is exactly this risk.
   Fix: restore `_edge19` in the method name and "/ edge 19" in the display name.
5. **R5 — carried-over non-blockers, still open:**
   - 6: leftover `TSOLVE_SIGNING_KEY`/`GOOGLE_*` test properties (follow-up per Hiếu). Main's new
     `ReviewQueueEventsIT` adds one more.
   - 7a: `.env.example:27`, stale "until Task 23b" text.
   - 7c: fully qualified annotations (closed by R1 if main's version is taken).
   - 8: OpenAPI texts.
   - 9: `progress.json` is `in-progress`, not `evaluating`. Task 24 in `tasks.md` does not record Q1–Q8, and
     `tasks.md:493` is still stale. The session log now exists.

### Assumptions made (attempt 2)
- B3 equivalence is judged against `origin/main`'s `SignInStateServiceTest`. It is the same file as at `a7c7659b`: no
  diff between the two for that path.
- R1/R2 are not blockers for this worktree: the code under review is correct on its base, and the team rule already
  requires an up-to-date `main` before the PR.
- I updated the frontmatter to `verdict: pass`, `attempt: 2`, so that `sdd.ts audit` reads the current verdict. The
  attempt 1 text above is unchanged.

### Test Report (attempt 2)
Full verify not re-run (per the card). Logs are in the scratchpad
`C:\Users\ADMIN\AppData\Local\Temp\claude\5794e928a0a4597c\865c94aa-281d-4ade-bf59-28f60d7702d0\scratchpad\`.
- `cd backend && mvn -o -B -Dtest='SignInStartControllerTest,SignInStartControllerFilterReturnToTest,AuthSecurityConfigTest,SignInPropertiesTest' -Dit.test='SignInTablesDroppedIT' -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -Dspotless.check.skip=true -DargLine=-Dapi.version=1.44 verify`
  (log `kan295-r2-targeted.log`)
  - Unit: `Tests run: 70, Failures: 0, Errors: 0, Skipped: 0` (AuthSecurityConfigTest 14, SignInPropertiesTest 11,
    FilterReturnToTest 23, SignInStartControllerTest 22).
  - IT: `SignInTablesDroppedIT` 1/1. `BUILD SUCCESS`.
- Spotless on the 4 touched Java files (scratch LF copy; log `kan295-r2-spotless.log`): 4 clean, 0 need changes.
- `cd frontend && npx vitest run src/shared/auth/sign-in.test.ts` (log `kan295-r2-vitest.log`): `Tests 79 passed (79)`.
  `npx prettier --check` on the file passes.
- `sdd.ts trace identity STORY-001`: 22/56. AC-001.3 is now covered by both `SignInStartControllerFilterReturnToTest`
  and `SignInStartControllerTest`. AC-001.28 shows ✓ only through the interim name (R3). None of the 16 retired ids
  is listed.
- Proposed status: `evaluating` → `passing` once R2 is settled at commit time. I changed no status.
