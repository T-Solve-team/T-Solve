---
module: tracker
feature: jira-project-read
task: 19
verdict: pass
attempt: 3
date: 2026-11-13
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: tracker — Task 19: Read a Jira project and its statuses (STORY-013, KAN-127)

## Summary

Reviewed commit `aa966ab8` (`aa966ab8^..aa966ab8`, 8 backend files) against tracker spec STORY-013 (AC-013.1 to
AC-013.13), the spec's interface rows for `TrackerProjectService.findProject`, `TicketTrackerClient.findProject` /
`projectStatuses`, design edge rows 58 to 65, and `backend/AGENTS.md`. Codex CLI (read-only, ephemeral) gave a second
opinion. I reproduced or confirmed each Codex finding myself: in-memory probes run against the commit's compiled
classes, or reading the test.

All 13 criteria are implemented for the cases the spec lists, and each has a test that asserts it. The targeted tests
(74) and `spotless:check` pass when I run them, and the full `mvn verify` log shows 1420 unit and 433 IT tests passing.
No CRITICAL finding.

One MEDIUM gap, found by both Claude and Codex: `TrackerProjectServiceImpl.findProject` catches only the two Jira
exceptions. A few malformed inputs throw a plain `IllegalArgumentException`, and that exception crosses the service
boundary:
- a site URL with no scheme
- `:` or a non-Latin-1 character in the email or token
- a broken `Content-Type` header on Jira's answer

AC-013.7/AC-013.8 and the interface's own Javadoc say no exception crosses. Codex rates this HIGH. I rate it MEDIUM and
non-blocking: Jira Cloud never sends these answers, the credential cases are configuration errors, and the global
handler already turns them into a 500 MSG06 with no leak. **This is a judgment call (see Assumptions): read strictly,
the verdict is fail.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | All 13 ACs are met on the paths the spec lists (table below). Edges: non-Jira runtime exceptions escape the service (MEDIUM-1); a valid JSON value followed by garbage is accepted (LOW-2). |
| Completeness | pass | No MISSING ACs by my reading (Codex reads AC-013.8/.10/.11 more strictly; see Assumptions). No EXTRA behavior: no endpoint, no persistence, no transaction, no Project dependency. Comment operations unchanged: `requiredText`, `call` and the comment 404 mapping are untouched, and the existing comment tests pass. |
| Correctness | pass | 74/74 targeted tests and spotless pass when I run them; the full verify log shows 1420 + 433 passing. No CRITICAL findings. |
| Integration | pass | Signatures match the spec's interface rows exactly (`TrackerProjectLookup(outcome, key, statuses)` with a nested `Outcome`; `Optional<String> findProject(JiraAccount, String)`; `List<TrackerStatus> projectStatuses(...)`). `JiraClient` is the only `TicketTrackerClient` bean and `ConfiguredJiraServiceAccount` the only `JiraServiceAccount`, so injection is unambiguous. The project-integration caller is not in this commit. |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-013.1 FOUND, canonical key, all statuses | PASS | `TrackerProjectServiceImpl.java:40-53`, `JiraClient.java:85-126`; `readsProjectAndStatusesWithBasicAuthentication_AC_013_1_AC_013_10` (exact URLs, Basic, Accept); `readsProjectThenStatusesWithCanonicalKey_AC_013_1_AC_013_10` (InOrder, the canonical `ITSUP` feeds the statuses read for the requested `itsup`) |
| 2 | AC-013.2 dedupe in first-seen order, first occurrence wins | PASS | `TrackerProjectServiceImpl.java:45-48` (`LinkedHashMap.putIfAbsent`); the client keeps duplicates (`flattensStatusesWithoutDeduplicating…_AC_013_2_AC_013_3`); `deduplicatesStatusesInFirstSeenOrder…_AC_013_2_AC_013_3` |
| 3 | AC-013.3 string id/name, `done` iff category key `done` | PASS | `JiraClient.java:118-121`; the test covers `done`, `Done`, `done `, a missing category and an empty category |
| 4 | AC-013.4 either read 404 → NOT_FOUND | PASS | `JiraClient.java:93-97` (project 404 → empty), `TrackerProjectServiceImpl.java:41-43,55`; client and service tests for both reads |
| 5 | AC-013.5 no account → DENIED, no Jira call | PASS | `TrackerProjectServiceImpl.java:36-38`; `deniesLookupWithoutCallingJira…_AC_013_5` (`verifyNoInteractions(jira)`) |
| 6 | AC-013.6 401/403/other 4xx → DENIED | PASS | `JiraClient.java:157-160`, `TrackerProjectServiceImpl.java:54-57`; parameterized 401/403/400 for each read at both layers |
| 7 | AC-013.7 5xx/429/timeout/unreachable → UNAVAILABLE, no partial result | PASS (edge gap MEDIUM-1) | `JiraClient.java:157-163` (429 checked before the generic 4xx), `TrackerProjectServiceImpl.java:58-60`; client tests inject `SocketTimeoutException` / `IOException` through MockRestServiceServer for both reads; service tests return an empty payload |
| 8 | AC-013.8 blank/missing key, id, name, or unreadable body → UNAVAILABLE | PASS (edge gaps MEDIUM-1, LOW-2) | `JiraClient.java:108-116,128-133` (`isTextual` plus nonblank, root and per-type arrays checked); 8 project and 15 statuses malformed bodies tested (empty, `null`, `{not json`, numbers, blanks, objects) |
| 9 | AC-013.9 valid `[]` → FOUND with empty list | PASS | `acceptsAnEmptyStatusesArray_AC_013_9`, `findsProjectWithNoStatuses_AC_013_9`; `null`, `""` and `{}` are UNAVAILABLE, not empty |
| 10 | AC-013.10 same Basic auth, 5s/10s timeouts | PASS (weak test LOW-3) | `JiraClient.java:36-43,144-150` unchanged and reused; the Basic header is asserted; the timeout test checks only the `@Value` defaults |
| 11 | AC-013.11 failed project read → no statuses call | PASS | `TrackerProjectServiceImpl.java:40-46`; `never().projectStatuses` asserted for empty, 404, 401/403/400, 500/429/0 (0 = malformed or I/O). The trailing-garbage project body counts as a successful read here (LOW-2). |
| 12 | AC-013.12 logs: key, status, outcome only | PASS | `TrackerProjectServiceImpl.java:37,42,49-52,56,59`; no exception or account is logged; `logsFoundKey…_AC_013_12` and `logsFailureKey…_AC_013_11_AC_013_12` check captured output against the email, token, body marker and site URL |
| 13 | AC-013.13 no persistence; no credential or body returned | PASS | Only two read dependencies (`returnsOnlyProjectDataUsingTwoReadDependencies_AC_013_13`); client exceptions carry the status only (`stripsCredentialsAndResponseBodiesFromBothReadFailures_AC_013_12_AC_013_13`) |

## Traceability

```
  ✓ AC-013.1  …/TrackerProjectServiceImplTest.java, …/JiraClientTest.java
  ✓ AC-013.2  …/TrackerProjectServiceImplTest.java, …/JiraClientTest.java
  ✓ AC-013.3  …/TrackerProjectServiceImplTest.java, …/JiraClientTest.java
  ✓ AC-013.4  …/TrackerProjectServiceImplTest.java, …/JiraClientTest.java
  ✓ AC-013.5  …/TrackerProjectServiceImplTest.java
  ✓ AC-013.6  …/TrackerProjectServiceImplTest.java, …/JiraClientTest.java
  ✓ AC-013.7  …/TrackerProjectServiceImplTest.java, …/JiraClientTest.java
  ✓ AC-013.8  …/TrackerProjectServiceImplTest.java, …/JiraClientTest.java
  ✓ AC-013.9  …/TrackerProjectServiceImplTest.java, …/JiraClientTest.java
  ✓ AC-013.10  …/TrackerProjectServiceImplTest.java, …/JiraClientTest.java
  ✓ AC-013.11  …/TrackerProjectServiceImplTest.java
  ✓ AC-013.12  …/TrackerProjectServiceImplTest.java, …/JiraClientTest.java
  ✓ AC-013.13  …/TrackerProjectServiceImplTest.java, …/JiraClientTest.java
  ! ids in tests but not in the spec: AC-001.72
trace tracker (STORY-013): 13/13 acceptance criteria have a test
```

`AC-001.72` comes from `common/security/SecurityConfigCsrfOrderTest` and `identity/jiralink/JiraLinkControllerTest`,
not from this commit.

## Issues

No CRITICAL findings. Non-blocking:

1. **MEDIUM-1 — exceptions that are not Jira exceptions cross the service boundary** (Claude and Codex
   independently; Codex rates it HIGH)
   - `backend/src/main/java/vn/tsolve/tracker/service/impl/TrackerProjectServiceImpl.java:54-61` catches only
     `JiraRejectedException` and `JiraUnavailableException`. `backend/src/main/java/vn/tsolve/tracker/client/JiraClient.java:152-165`
     (`call`) converts only `RestClientException`.
   - I ran the commit's classes against these inputs, and each one leaves `findProject` instead of becoming an outcome:
     - A site URL with no scheme (`acme.atlassian.net`) throws `IllegalArgumentException` ("URI is not absolute"). Found by Claude.
     - An email containing `:` throws `IllegalArgumentException` from `setBasicAuth` (`JiraClient.java:147`). Found by Codex.
     - A non-Latin-1 character in the email throws `IllegalArgumentException` from the same call. Found by Codex.
     - A 200 answer with an invalid `Content-Type`, on either read, throws `org.springframework.http.InvalidMediaTypeException`
       (an `IllegalArgumentException` subtype). Found by Codex.
   - The interface Javadoc (`TrackerProjectService.java:5`, "never thrown") and AC-013.7/AC-013.8 do not allow that.
   - Impact today: `GlobalExceptionHandler.handleUnexpected` gives a 500 MSG06 and logs only the class name, so nothing
     leaks. The service tests mock only the two mapped exception types, so they cannot catch this.
   - Fix: in `JiraClient.call`, also map `IllegalArgumentException` to `JiraUnavailableException(0)`. Do not log the
     message or cause. Add one test per read through the real client: an invalid `Content-Type` on a 200 answer, and
     an account email containing `:`. Alternative: catch `RuntimeException` in the service and return UNAVAILABLE,
     logging only the class name.
2. **LOW-2 — trailing data after a valid JSON value is accepted** (Codex, reproduced by Claude; Codex rates it MEDIUM)
   - `{"key":"ITSUP"} GARBAGE` on the project read (`JiraClient.java:87-92`) and `[] GARBAGE` on the statuses read
     (`JiraClient.java:103-107`) both end as FOUND. Jackson's default `FAIL_ON_TRAILING_TOKENS=false` stops reading
     after the first value, so the project case also goes on to make the statuses call.
   - Design row 62 lists "invalid JSON" as UNAVAILABLE. The spec says "unreadable", and Jira Cloud never sends such a
     body.
   - Fix (optional): for these two reads, read the body as a `String` and parse it with an `ObjectReader` that has
     `DeserializationFeature.FAIL_ON_TRAILING_TOKENS` enabled, mapping a failure to `JiraUnavailableException(0)`.
     Add both suffix cases to the AC-013.8 parameter lists.
3. **LOW-3 — the AC-013.10 timeout test does not check that the timeouts are applied** (Codex, by an in-memory
   mutation; Claude confirmed by reading the test)
   - `JiraClientTest.retainsExistingConnectAndReadTimeoutDefaults_AC_013_10` (`JiraClientTest.java:430-439`) asserts
     only the `@Value` default strings. Removing `factory.setConnectTimeout` / `setReadTimeout` (`JiraClient.java:40-41`)
     would still pass.
   - The constructor is unchanged by this commit, and `application.yml:92-93` sets 5s/10s, so the risk is regression only.
   - Fix (optional): build `JiraClient(Duration, Duration)` and assert the request factory's connect/read timeouts, for
     example through reflection on the builder's factory.
4. **INFO-4 — "Jira answered 0" in UNAVAILABLE logs** (Claude)
   - `TrackerProjectServiceImpl.java:59` prints status `0` when there was no HTTP answer (I/O, timeout, malformed
     body). AC-013.12 asks for the status "when available".
   - Fix (optional): log "no answer" when `getStatus() == 0`.
5. **INFO-5 — the `TrackerProjectLookup` invariants hold only through the factories** (Claude)
   - The public canonical constructor (`TrackerProjectLookup.java:9-13`) accepts FOUND with a null key, or a non-FOUND
     outcome with a key.
   - Callers in this commit use only `found` / `failed`.
   - Fix (optional): validate in the compact constructor.
6. **INFO-6 — scope noise** (Claude)
   - Spotless reflowed two existing text blocks in `JiraClientTest.java:57-65,84-91`. Formatting only.

## Assumptions made

- **MEDIUM-1 is not blocking.** Every error case the spec names is handled and tested: status codes, timeouts,
  unreachable Jira, and unreadable, blank or non-text bodies. The escapes need either a malformed credential or site
  URL (configuration) or a malformed HTTP header (not something Jira Cloud sends), and they surface as 500 MSG06 with
  no leak. I read AC-013.7/AC-013.8 as covering Jira's answers rather than local configuration errors. Codex rated it
  HIGH. If the user reads "no exception crosses the service boundary" strictly, the verdict becomes **fail** and
  MEDIUM-1 is the fix. **Open judgment call.**
- **LOW-2:** "unreadable body" (AC-013.8) does not cover a valid JSON value followed by garbage, even though design
  row 62 says "invalid JSON". Codex reads it as an AC-013.8/AC-013.11 defect. **Open judgment call.**
- **AC-013.10:** the existing constructor, unchanged here, plus a test of its 5s/10s `@Value` defaults is enough
  evidence. Codex marks the applied-timeout assertion MISSING. **Open judgment call.**
- **AC-013.13:** the constructor with only the two read dependencies, plus no repository, counts as evidence of "no
  persistence". No IT was required.
- A null `projectKey` is out of contract: the spec input is "Normalised Jira key from Project". Codex observed that it
  becomes an empty path segment.

## Test Report

Run fresh from `backend/` (JAVA_HOME = IntelliJ JBR) on the working tree. `git status` shows no changes under
`tracker/`, so the tree matches the commit for these files.

- `mvn -B -Dtest='JiraClientTest,TrackerProjectServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test spotless:check`
  - `JiraClientTest`: Tests run: 51, Failures: 0, Errors: 0, Skipped: 0
  - `TrackerProjectServiceImplTest`: Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
  - Total: Tests run: 74, Failures: 0, Errors: 0, Skipped: 0; spotless:check passed; BUILD SUCCESS
- Full `mvn verify` (implementer's run, log `kan127-t19-verify.log` in the session scratchpad, which I read):
  Surefire `Tests run: 1420, Failures: 0, Errors: 0, Skipped: 0`, Failsafe `Tests run: 433, Failures: 0, Errors: 0,
  Skipped: 0`, BUILD SUCCESS.
- `bun harnessclaude/hooks/sdd.ts trace tracker STORY-013`: 13/13 acceptance criteria have a test.
- Claude's probes (scratchpad, not committed), run against the commit's classes through `TrackerProjectServiceImpl`
  with the real `JiraClient` and MockRestServiceServer:
  - invalid `Content-Type`: escapes as `InvalidMediaTypeException`
  - email with `:`: escapes as `IllegalArgumentException`
  - non-Latin-1 email: escapes as `IllegalArgumentException`
  - scheme-less site URL: escapes as `IllegalArgumentException`
  - `{"key":"ITSUP"} trailing`: FOUND
- Codex: compiled the commit's sources in memory and ran 74/74 tests passing; reproduced the header, email and
  trailing-data cases; ran an in-memory mutation removing the timeout setters, and the AC-013.10 test still passed.

---

# Attempt 2 — 2026-11-12

**Verdict: pass** (attempt 2, with an open judgment call on MEDIUM-8). Fix commit `eeaee68d`
(`eeaee68d^..eeaee68d`), on top of `aa966ab8`. The changes are 4 backend files plus the BUG-203 row in
`docs/bug-log.md`. Decision D45 (`kan-127-decisions.md`) chose to fix MEDIUM-1, LOW-3 and INFO-4 and to leave LOW-2,
INFO-5 and INFO-6.

## Summary

MEDIUM-1 is fixed for every input attempt 1 listed, on both reads. `JiraClient.call` (`JiraClient.java:164-166`) now
maps `IllegalArgumentException` to `JiraUnavailableException(0)` and keeps neither the message nor the cause.
`findProject` and `projectStatuses` both build `http(account)` inside the `call` lambda (`JiraClient.java:87,103`), so
each of these becomes UNAVAILABLE (probe below):
- a scheme-less site URL
- an email with `:`
- an email with a non-Latin-1 character
- an invalid `Content-Type`

LOW-3 is fixed. The test reads the request factory's real timeouts. Without the setters the factory keeps its default
of -1, so the assertions on 3000 and 7000 would fail. INFO-4 is fixed: status 0 logs "no answer", and the test
asserts that "answered 0" is absent. LOW-2, INFO-5 and INFO-6 are unchanged, as D45 decided.

The comment operations show no behavioural regression. `SuggestionJobServiceImpl.java:51,66` catches every
`RuntimeException` the same way, so turning an `IllegalArgumentException` into `JiraUnavailableException` changes only
the log line in `SuggestionCommentServiceImpl.java:49-56`.

Two new findings, both confirmed by my own probe:
- **MEDIUM-8** (Codex): a site URL with a non-HTTP scheme still throws, now as `IllegalStateException`, which the fix
  does not catch. It is the same class of configuration error as MEDIUM-1. It is non-blocking by the same reasoning
  as attempt 1, but **read strictly (as D45 did), it is a fail** (see Assumptions).
- **LOW-7** (Claude and Codex): BUG-203's note overstates the effect on `findComment`.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-013.7/AC-013.8: no exception crosses `findProject` for any attempt-1 input, on either read. A non-HTTP site URL still escapes (MEDIUM-8, a configuration error). AC-013.12: status 0 logs "no answer"; the new tests assert `hasNoCause` and no email or token in the message. |
| Completeness | pass | 13/13 criteria still have a test. The new tests carry the `BUG_203` id the project rule asks for. Nothing EXTRA. |
| Correctness | pass | `JiraClientTest` 60/60 and `TrackerProjectServiceImplTest` 24/24 pass in an isolated worktree, with spotless passing too. The full verify log shows 1430 unit and 438 IT passing. With the fix reverted, each new `BUG_203` test fails: it expects `JiraUnavailableException` where the plain exception escaped in attempt 1. No CRITICAL finding. |
| Integration | pass | No interface or signature change. The comment path (STORY-012) passes all its existing tests; the behaviour change is limited to the log line (see LOW-7). |

### Attempt-1 findings

| Finding | D45 decision | Status in `eeaee68d` | Evidence |
|---------|--------------|----------------------|----------|
| MEDIUM-1 non-Jira exceptions escape | fix | **Fixed** for the listed inputs (residual: MEDIUM-8) | `JiraClient.java:164-166`; `JiraClientTest.java:458` (scheme-less, real client, both reads), `:484` (`:` and Cyrillic email, both reads), `:503` (invalid Content-Type, both reads), `:526` (through `TrackerProjectServiceImpl`: UNAVAILABLE, no key, no statuses, and no statuses call, because `jira.verify()` has one expectation) |
| LOW-2 trailing JSON accepted | leave | Unchanged, as decided | `JiraClient.java:87-92,103-107` still `body(JsonNode.class)` |
| LOW-3 timeout test cannot fail | fix | **Fixed** | `JiraClientTest.java:438-455` reads the `SimpleClientHttpRequestFactory` `connectTimeout`/`readTimeout` (3000/7000 ms from 3 s/7 s) |
| INFO-4 "Jira answered 0" | fix | **Fixed** | `TrackerProjectServiceImpl.java:59-67`; `logsFailureKeyStatusAndOutcomeWithoutCredentials_AC_013_11_AC_013_12` now runs with 0 and asserts "no answer" and not "answered 0" |
| INFO-5 record invariants | leave | Unchanged, as decided | `git diff aa966ab8 eeaee68d -- backend/src/main/java/vn/tsolve/tracker/dto` is empty |
| INFO-6 formatting noise | leave | Unchanged, as decided | The fix diff adds no formatting-only churn |

## Traceability

```
  ✓ AC-013.1 to AC-013.13: each named by TrackerProjectServiceImplTest.java and/or JiraClientTest.java (same files as attempt 1)
  ! ids in tests but not in the spec: AC-001.72
trace tracker (STORY-013): 13/13 acceptance criteria have a test
```

## Issues

No CRITICAL findings. Non-blocking:

7. **LOW-7 — BUG-203's note claims the comment operations are covered too, but `findComment` is not** (Claude and
   Codex independently; Claude's probe)
   - `JiraClient.java:65` builds `RestClient http = http(account)` outside `call`. Spring 6.1.14 runs the
     `defaultHeaders(...)` consumer, and with it `setBasicAuth`, when the client is built. So for an email with `:` or
     a non-Latin-1 character, `findComment` still throws a plain `IllegalArgumentException`.
   - `SuggestionCommentServiceImpl.post` calls `findComment` first, so for these emails the comment path behaves as it
     did before the fix and skips the "Jira answered nothing" log.
   - The BUG-203 note (`docs/bug-log.md:213`) says "the same inputs there now surface as `JiraUnavailableException(0)`".
     That holds for a scheme-less URL or a bad `Content-Type`, not for the email cases on `findComment`.
   - No user-visible effect: the job treats both exceptions alike. The gap is in STORY-012, not STORY-013.
   - Fix: correct the BUG-203 note. Optionally, as a STORY-012 quick change, build the client inside the two `call`
     lambdas of `findComment` and add a test.
8. **MEDIUM-8 — a site URL with a non-HTTP scheme still escapes `findProject`** (Codex; reproduced by Claude)
   - Probe, through `TrackerProjectServiceImpl` with the production `JiraClient`: `ftp://localhost`,
     `file:///C:/jira` and `mailto:x@y` each throw `java.lang.IllegalStateException` out of `findProject`.
     `SimpleClientHttpRequestFactory` throws it when the connection is not an `HttpURLConnection`.
     `JiraClient.call` (`JiraClient.java:152-167`) catches only `RestClientException` and `IllegalArgumentException`.
   - The message carries neither the email nor the token, and the global handler gives a 500 MSG06, so nothing
     leaks.
   - It is reachable only through a misconfigured `JIRA_SITE_URL`: `ConfiguredJiraServiceAccount` checks only for a
     blank value. It is the same kind of input as MEDIUM-1's scheme-less URL, and the new catch's own comment says "a
     malformed site URL … leaves no usable answer".
   - Fix: close the class of failures at the boundary the contract names. In `TrackerProjectServiceImpl.findProject`,
     catch `RuntimeException` last, log the key, `UNAVAILABLE` and the exception class name only, and return
     `failed(UNAVAILABLE)`. Add one test with an `ftp://` site through the real client. Alternative: also catch
     `IllegalStateException` in `JiraClient.call`.

## Assumptions made

- **MEDIUM-8 is not blocking.** I keep attempt 1's reading: AC-013.7/AC-013.8 cover Jira's answers, and a non-HTTP site
  URL is a configuration error that surfaces as 500 MSG06 with no leak. But D45 fixed MEDIUM-1 under the strict
  reading of AC-013.7 and the interface Javadoc ("never thrown"). Under that same reading, MEDIUM-8 breaks the
  contract and the verdict is **fail**. **Open judgment call.** The fix is a single catch in the service.
- LOW-2 (trailing JSON) is still open by D45's choice. I keep my attempt-1 reading that AC-013.8's "unreadable" does
  not cover it. **Open judgment call** (unchanged from attempt 1).
- LOW-7 is non-blocking, because it is outside STORY-013 and changes no behaviour.

## Test Report

Run fresh in an isolated worktree at `180caebc`, which contains `eeaee68d`. No tracker file differs between the two
(`git diff --stat eeaee68d 180caebc -- backend/src/main/java/vn/tsolve/tracker backend/src/test/java/vn/tsolve/tracker`
is empty). Run from `backend/` with JAVA_HOME set to the IntelliJ JBR:

- `mvn -B -Dapi.version=1.44 -Dtest='JiraClientTest,TrackerProjectServiceImplTest' -Dit.test='ProjectDoneStatusesMigrationIT,SendBackApiIT' -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false verify`
  - `JiraClientTest`: Tests run: 60, Failures: 0, Errors: 0, Skipped: 0
  - `TrackerProjectServiceImplTest`: Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
  - Surefire total 84/84; Failsafe 12/12; `spotless:check` passed; BUILD SUCCESS
- Full `mvn verify` at `180caebc` (implementer's log `kan127-t1-verify.log` in the session scratchpad, which I read):
  Surefire `Tests run: 1430, Failures: 0, Errors: 0, Skipped: 0`, Failsafe `Tests run: 438, Failures: 0, Errors: 0,
  Skipped: 0`, BUILD SUCCESS.
- `bun harnessclaude/hooks/sdd.ts trace tracker STORY-013`: 13/13 acceptance criteria have a test.
- Claude's probes (scratchpad, not committed): the compiled `JiraClient` at `180caebc`, built with the production
  constructor, with Jira pointed at a closed port.
  - Unchanged inputs, now mapped:
    - `findProject`, email with `:` → `JiraUnavailableException`
    - `findProject`, null site → `JiraUnavailableException`
    - `findProject`, null email → `JiraUnavailableException`
    - `addComment`, email with `:` → `JiraUnavailableException`
    - `findComment`, scheme-less site → `JiraUnavailableException`
  - LOW-7:
    - `findComment`, email with `:` → `IllegalArgumentException`
    - `findComment`, Cyrillic email → `IllegalArgumentException`
  - MEDIUM-8, through `TrackerProjectServiceImpl.findProject`:
    - `ftp://localhost`, `file:///C:/jira`, `mailto:x@y` → `IllegalStateException` escapes
    - `http://`, `https://exa mple.com` → UNAVAILABLE
- Codex (second opinion, `kan127-t19-rereview-codex.md` in the session scratchpad) reported MEDIUM-8 and LOW-7, and
  confirmed that LOW-2, INFO-5 and INFO-6 are unchanged. I reproduced both findings and kept them.

---

# Attempt 3 — 2026-11-13

**Verdict: pass** (attempt 3). Fix commit `ba276218` (`ba276218^..ba276218`), harness commit `7c71b75f`. Three files
changed: `TrackerProjectServiceImpl.java` (+7), `JiraClientTest.java` (+13), and the BUG-203 row in `docs/bug-log.md`.
Decision D46 chose to fix MEDIUM-8 and to correct the BUG-203 note for LOW-7.

## Summary

MEDIUM-8 is fixed. `findProject` now ends with a last `catch (RuntimeException e)`
(`TrackerProjectServiceImpl.java:69-76`). It logs the key, `UNAVAILABLE` and `e.getClass().getSimpleName()`, never
the message or the cause, and it returns `failed(UNAVAILABLE)`. The try block covers `serviceAccount.current()` and
both client calls, so no runtime exception can cross the service boundary anymore. That matches AC-013.7 and the
interface Javadoc ("never thrown", `TrackerProjectService.java:5`). The two specific catches still come first, so
their outcomes and log lines have not changed.

The new test `returnsUNAVAILABLEWithoutPartialProjectForNonHttpScheme_AC_013_7_BUG_203` (`JiraClientTest.java:525-536`)
runs the production `JiraClient` with an `ftp://` site. `SimpleClientHttpRequestFactory` throws its
`IllegalStateException` before it connects, so the test needs no network. Without the new catch the exception escapes
(my attempt-2 probe showed this for `ftp://`, `file:` and `mailto:`), so the test can fail.

LOW-7 is fixed. The BUG-203 note (`docs/bug-log.md:213`) now says that `findComment` builds its client outside `call`
and still throws a raw `IllegalArgumentException` for a bad email (STORY-012, out of scope). The note also describes
the new catch, and the Test column names the new test.

The fix causes no regression: all 61 `JiraClientTest` and 24 `TrackerProjectServiceImplTest` tests pass. One new
LOW finding is about test strength only.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-013.7/AC-013.8: every runtime failure inside `findProject` now becomes `UNAVAILABLE` with no key and no statuses. AC-013.12: the new log line has the key, the outcome and the exception class, with no message, cause, email, token or body. |
| Completeness | pass | 13/13 criteria have a test (trace below). The new test carries `BUG_203`, as the project rule asks. Nothing EXTRA. |
| Correctness | pass | `JiraClientTest` 61/61 and `TrackerProjectServiceImplTest` 24/24 pass in an isolated worktree at `0dd4f8cc`, and spotless passes. No CRITICAL finding. |
| Integration | pass | No signature change. The comment path (STORY-012) is untouched by this commit. The only caller, project-integration (`ProjectServiceImpl.readJiraProject`), already maps `UNAVAILABLE` to 503 MSG06. |

### Earlier findings

| Finding | Decision | Status in `ba276218` | Evidence |
|---------|----------|----------------------|----------|
| MEDIUM-1 non-Jira exceptions escape | D45 fix | Fixed (attempt 2); now backed by the catch-all | `JiraClient.java:164-166` plus `TrackerProjectServiceImpl.java:69-76` |
| LOW-2 trailing JSON accepted | D45 leave | Unchanged, as decided | `JiraClient.java` read paths unchanged |
| LOW-3 timeout test | D45 fix | Fixed (attempt 2) | unchanged |
| INFO-4 "answered 0" | D45 fix | Fixed (attempt 2) | `TrackerProjectServiceImpl.java:59-60` |
| INFO-5 record invariants | D45 leave | Unchanged, as decided | `tracker/dto` not touched |
| INFO-6 formatting noise | D45 leave | Unchanged, as decided | — |
| LOW-7 BUG-203 note overstated | D46 correct the note | **Fixed** | `docs/bug-log.md:213`: the note now names the `findComment` gap as STORY-012 and out of scope |
| MEDIUM-8 non-HTTP scheme escapes | D46 fix | **Fixed** | `TrackerProjectServiceImpl.java:69-76`; test `JiraClientTest.java:525-536` |

## Traceability

Run in the worktree at `0dd4f8cc` and in the main checkout. Both give the same result.

```
  ✓ AC-013.1 … AC-013.13  TrackerProjectServiceImplTest.java and/or JiraClientTest.java (AC-013.5 and AC-013.11 service test only)
  ! ids in tests but not in the spec: AC-001.72
trace tracker (STORY-013): 13/13 acceptance criteria have a test
```

`AC-001.72` comes from other modules' tests (see attempt 1).

## Issues

No CRITICAL, HIGH or MEDIUM findings. Non-blocking:

9. **LOW-9 — no test checks the new catch-all's log line**
   - `TrackerProjectServiceImpl.java:70-74` logs `e.getClass().getSimpleName()`. The new test (`JiraClientTest.java:525-536`)
     asserts only the outcome, key and statuses.
   - If someone later changed the line to log `e` or `e.getMessage()`, no test would fail. The message could carry
     the site URL, or the email: attempt 1 noted that `setBasicAuth` messages quote it.
   - AC-013.12 is covered for the other branches (`logsFailureKeyStatusAndOutcomeWithoutCredentials_AC_013_11_AC_013_12`).
   - Fix: add a `TrackerProjectServiceImplTest` case where the mocked client throws
     `new IllegalStateException("<planted email> <planted token> ftp://planted")`. Assert through `CapturedOutput` that
     the output contains `UNAVAILABLE (IllegalStateException)` and none of the planted strings.
10. **INFO-10 — the catch-all also hides programming errors**
    - A `NullPointerException` or similar bug inside `findProject` now becomes `UNAVAILABLE` (503 MSG06 for the
      Project caller), logged at WARN with the class name and no stack trace.
    - This is the intended trade-off of D46 (the "never thrown" contract wins), so it is not a defect. Note it when
      debugging a 503 that Jira did not cause.
    - Fix: none required.

## Assumptions made

- Logging the exception's simple class name goes beyond AC-013.12's list (key, HTTP status, outcome). I read the
  class name as harmless diagnostic text, the same kind as the existing "(no service account)" and "(no answer)"
  suffixes: it carries no credential or body. If AC-013.12 is meant as an exhaustive list, the class name must go.
  **Open judgment call (minor).**
- LOW-2 (trailing JSON) stays open by D45's choice. I keep my attempt-1 reading of "unreadable" (AC-013.8).
- The attempt-1 and attempt-2 judgment calls on MEDIUM-1 and MEDIUM-8 are closed: both are fixed.

## Test Report

Isolated worktree at `0dd4f8cc` (it contains `ba276218`, and `git diff ba276218 0dd4f8cc` touches no tracker file).
Run from `backend/` in Git Bash with JAVA_HOME set to the IntelliJ JBR.

- `mvn -B -Dapi.version=1.44 -Dtest='JiraClientTest,TrackerProjectServiceImplTest,ProjectServiceImplTest,ProjectControllerTest' -Dsurefire.failIfNoSpecifiedTests=false test`
  - `Tests run: 61, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.tracker.client.JiraClientTest`
  - `Tests run: 24, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.tracker.service.impl.TrackerProjectServiceImplTest`
  - total `Tests run: 256, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`
- `mvn -B spotless:check`: `BUILD SUCCESS`
- `bun harnessclaude/hooks/sdd.ts trace tracker STORY-013`: 13/13 acceptance criteria have a test
- Full `mvn verify`: not rerun by me. The session log reports Task 3's full verify on the same tree (unit 1600, IT 462, 0
  failures).
