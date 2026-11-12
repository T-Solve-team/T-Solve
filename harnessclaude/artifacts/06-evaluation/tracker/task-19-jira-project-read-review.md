---
module: tracker
feature: jira-project-read
task: 19
verdict: pass
attempt: 1
date: 2026-11-12
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
