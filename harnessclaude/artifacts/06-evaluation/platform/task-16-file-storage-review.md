---
module: platform
feature: file-storage
task: 16
verdict: pass
attempt: 3
date: 2026-11-11
reviewer: harnessclaude-reviewer (Claude Opus 5.5, independent read-only; Codex second opinion)
iteration: 1
---

# Review: platform — Task 16: file-storage

## Summary
Range: the uncommitted working tree on `feat/KAN-96-platform-file-storage` (diff of `backend/pom.xml`,
`application.yml`, `.env.example`, plus the untracked `common/tenant/`, `platform/` main and test files). Harness files
and `docs/diagrams/` are out of scope.

The production code meets STORY-007. All 15 criteria are implemented, every criterion is named by a test, and
`ObjectFileStorageIT` passes 12/12 against a real Garage v2.5.0 with Spotless clean. Most tests prove what they claim:
key refusal and the no-Workspace refusal run against a client pointed at a dead port, so any S3 call would fail with
`SdkClientException` rather than the expected exception. The cross-Workspace read, sign and delete are checked against
the real bucket. The signed URL is downloaded, then shown to fail with a changed key, with no signature and after the
TTL.

Verdict **fail** for one blocker: `GarageTestBase.java:28` holds a 64-hex `rpc_secret` literal, and gitleaks 8.30
(rule `generic-api-key`, entropy 4.0) flags it. Run locally, `gitleaks dir` reports 1 leak. The repo's pre-commit hook
(`.husky/pre-commit`) and the required `gitleaks` PR check (`pr-checks.yml`) both fail on it. The team rules forbid
`--no-verify`, so the feature cannot be committed as it stands. The fix is one line. The other items are minor and do
not block on their own.

Feature status in `progress.json`: `file-storage: evaluating`. I did not change it, because this reviewer only writes
files under `06-evaluation`. Proposed: `bun harnessclaude/hooks/sdd.ts feature platform file-storage fixing`.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-007.1–.15 behave as specified (see the Stage 1 table) |
| Completeness | pass | `sdd.ts trace platform STORY-007`: 15/15 criteria have a test; the 12 IT cases cover edge rows 35–39 and 42 |
| Correctness | fail | Tests pass, but the change cannot pass the repo's gitleaks gate (issue 1); there are also minor test-strength and flakiness gaps |
| Integration | pass | `TenantContext`, `WorkspaceCatalog`, `WorkspaceSummary`, `WorkspaceStatus`, `FileStorage` and `StoredFile` match the spec's Interface Contracts word for word. `InvalidFileKeyException` extends `NotFoundException` with `MSG06`, which `GlobalExceptionHandler.handleNotFound` maps to 404. `FileTooLargeException` is a `MessageCodeException` (413, `MSG28`), mapped by `handleMessageCode` |

### Stage 1: spec compliance per criterion

| AC | Code | Test proves it? |
|----|------|-----------------|
| 007.1 | `store`: one `PutObject`, `<workspaceId>/<uuid>`, returns the UUID and size | yes: v4 UUID, size, HEAD length and type, and a list under the prefix |
| 007.2 | `store(InputStream)` takes no name | yes, by its signature |
| 007.3 | the object name is always built from `requireWorkspaceId()` | yes: the cross-Workspace case |
| 007.4 | B's `getObject` on `B/<A's key>` gets `NoSuchKey` and throws `NotFoundException` | yes: `MSG06` is asserted and A's object is still intact |
| 007.5 | canonical lower-case UUID regex, checked before any S3 call; logs only the length (no log injection) | refusal before S3: yes (dead client). The log line is **not asserted** (issue 6) |
| 007.6 | `readNBytes(max+1)`, then the check, before `PutObject` | exactly 10 MB, +1 and 0 bytes: yes. "Before any S3 call" is only shown indirectly, by an unchanged object count (issue 5) |
| 007.7 | `ObjectProvider.getIfAvailable()` returns null, which throws; this runs before the input is read | yes: both an unbound and an absent bean, with a stream that fails if read and a dead client |
| 007.8 | a single `PutObject` of known length (R13) | partly: both failure tests fail before reaching Garage (issue 7) |
| 007.9 | `NoSuchKey` throws `NotFoundException` | yes |
| 007.10 | `deleteObject` is idempotent | yes: a missing key, a double delete and a removal |
| 007.11 | — | yes: B cannot read, sign or delete A's object, and A can still read it |
| 007.12 | HEAD, then an offline presign for the Workspace's site, path-style, TTL from the config | yes: host, path, `X-Amz-Expires`, download, changed key 403, expiry. The 2 s TTL makes it timing-sensitive (issue 8) |
| 007.13 | `NoSuchKey`/404 on HEAD throws `NotFoundException` | yes |
| 007.14 | `RequestBody.fromBytes` and a streamed `ResponseInputStream`; no file I/O | by inspection yes. The test checks a snapshot of the global tmpdir (issue 9) |
| 007.15 | the bucket has no anonymous access | yes: an unsigned GET gets 403 (the bucket setup is the test base's until Task 1) |

Decisions D1–D17 do not conflict with any AC. D16 (Garage answers an expired URL with 400) still means the URL "stops
working". D9 (no storage beans when the endpoint is blank) is allowed by STORY-007, and AC-008.5 tightens it later.

## Traceability
```
✓ AC-007.1 … ✓ AC-007.15  backend/src/test/java/vn/tsolve/platform/ObjectFileStorageIT.java
trace platform (STORY-007): 15/15 acceptance criteria have a test
```

## Issues

1. **major**: `backend/src/test/java/vn/tsolve/support/GarageTestBase.java:28`. The literal `rpc_secret = "0123…cdef"`
   (64 hex characters) is flagged by gitleaks `generic-api-key` (entropy 4.0). Verified with
   `gitleaks dir GarageTestBase.java`, which reported "leaks found: 1". The pre-commit hook and the required CI check
   both block on it. **Fix:** generate the value at class init, e.g.
   `HexFormat.of().formatHex(SecureRandom.getInstanceStrong()/new SecureRandom() → 32 bytes)` formatted into `CONFIG`.
   Alternatively, append ` # gitleaks:allow` to that TOML line, since TOML accepts `#` comments. Source: you.
2. **minor**: `backend/pom.xml:60` (D14). `apache-client` pulls `httpclient 4.5.13`, which brings
   `commons-logging:commons-logging:1.2` onto the compile classpath next to Spring's `spring-jcl`. These are duplicate
   `org.apache.commons.logging` classes, and Spring warns about them at startup. **Fix:** add an `<exclusion>` of
   `commons-logging:commons-logging` on `apache-client`. Source: you (`mvn dependency:tree`).
3. **minor**: `backend/src/main/java/vn/tsolve/platform/config/StorageConfig.java:78-83`. The bounds checks are only
   `>= 1`. A TTL above 604800 s (7 days) passes startup, but then every `signedUrl` fails, because the SigV4 presigner
   rejects it. A `maxBytes` of `Integer.MAX_VALUE` or more makes `ObjectFileStorage.java:66` cap the read at about
   2 GB. The check at line 71 can then never fire, so a longer stream would be stored **truncated** without an error.
   **Fix:** validate `urlTtlSeconds <= 604800` and `maxBytes < Integer.MAX_VALUE` (or a sane cap such as 100 MB) in
   the record constructor. Source: codex-verified (TTL) and you (truncation).
4. **minor**: `backend/src/main/java/vn/tsolve/platform/service/impl/ObjectFileStorage.java:91`. `read` maps only
   `NoSuchKeyException`, while `signedUrl` (lines 105-108) also maps a plain `S3Exception` with status 404. If a store
   or proxy answers 404 without the `NoSuchKey` code, `read` returns a 500 instead of AC-007.9's `NotFoundException`.
   Every other failure correctly propagates (fail closed). **Fix:** use the same `S3Exception` 404 branch in `read`.
   Source: codex-verified.
5. **minor (test strength)**: `ObjectFileStorageIT.java:153`. The +1-byte case uses the live client and checks only
   that the object count did not change. It would not catch a regression that uploads and then deletes, or that starts
   and aborts a multipart upload. **Fix:** also run `storage(dead).store(new byte[MAX_BYTES + 1])` and assert
   `FileTooLargeException` (not `SdkClientException`), the pattern already used for AC-007.5 and AC-007.7.
   Source: codex-verified.
6. **minor (test gap)**: `ObjectFileStorageIT.java:126` and `ObjectFileStorage.java:157`. AC-007.5 requires "log the
   attempt", but no test asserts the warning, or that the raw key is not echoed in it. **Fix:** use Boot's
   `OutputCaptureExtension` and assert that one `Rejected invalid file key` line appears per refusal and that it
   contains neither `..` nor the NUL. Source: you.
7. **minor (test strength)**: `ObjectFileStorageIT.java:191-223`. Both AC-007.8 cases fail before any byte reaches
   Garage: one hits a dead port, the other a local socket that resets. The later "no objects or uploads" check therefore
   holds trivially. AC-007.8 is in fact met by design, since a single `PutObject` with no multipart upload is atomic
   (R13). **Fix (optional):** cut a real upload through a small TCP relay to Garage midway, then assert no object and
   no multipart upload. At minimum, add a comment that the guarantee rests on R13. Source: codex-verified.
8. **minor (flakiness)**: `ObjectFileStorageIT.java:266-281` and `:333`. With a TTL of 2 s, the valid download must
   complete within 2 s of signing, and the expiry check has only 1 s of margin. Garage judges expiry by the container
   or Docker VM clock, so a skew of about 1 s (common on Docker Desktop and Windows) or a slow CI runner will make it
   flaky. **Fix:** use a TTL of about 5 s with `Thread.sleep(ttl + 3 s)`, or a separate long-TTL storage for the
   positive checks. Source: you and codex.
9. **minor (flakiness/strength)**: `ObjectFileStorageIT.java:299-306` and `:411-415`. The test compares the
   top-level names in the shared `java.io.tmpdir`. On Windows `%TEMP%`, any other process that writes there during the
   test makes it fail. It also misses files that are created and then deleted, and files in nested folders. **Fix:**
   ignore names that existed before and match only SDK and Java temp patterns, or run Failsafe with a private
   `java.io.tmpdir` and assert that it stays empty. The code itself writes no file (by inspection). Source: you and
   codex.
10. **info**: `StorageConfig.java:23-60`. The D9 condition, the `@ConfigurationPropertiesScan` binding of
    `tsolve.storage.*` and the `ObjectProvider` wiring are not exercised in any Spring context. Every test builds
    `StorageProperties` by hand. **Fix:** add a cheap `ApplicationContextRunner` unit test with a blank endpoint (no
    beans) and a set endpoint (a bean exists; an invalid pattern fails startup with the variable named). Source:
    codex-verified.
11. **info**: `ObjectFileStorage.java:105-109`. A HEAD 403, 5xx or transport error propagates (fail closed, correct),
    but no test covers it. Source: codex-verified.

## Assumptions made
- The gitleaks hit is treated as a fail, not a pass with conditions. My reasoning: the root CLAUDE.md requires
  commits through the hooks (never `--no-verify`), and gitleaks is a required PR check. If the user prefers, this can
  be a pass that requires the one-line fix before the commit.
- AC-007.8 is judged met by design (R13, a single `PutObject`), even though the tests do not interrupt a real upload
  to Garage.
- I wrote this review to `harnessclaude/artifacts/06-evaluation/platform/task-16-file-storage-review.md`, the
  location the reviewer role and evaluate skill require, not to the dispatcher's
  `artifacts/05-reviews/platform/file-storage-review.md`.

## Test Report
See `task-16-file-storage-test-report.md`. `ObjectFileStorageIT`: 12/12 passed in 8.3 s, Spotless check clean, and
BUILD SUCCESS. The full `mvn -B -Dapi.version=1.44 verify` passed: Surefire 1242/1242 and Failsafe 433/433. gitleaks reports 1 leak (issue 1).

---

# Attempt 2 (2026-11-11)

**Verdict: FAIL** (attempt 2 of 3). Reviewer: harnessclaude-reviewer (Claude Opus 5.5, read-only), with a Codex
second opinion on the fix diff. I checked its claims myself.

## Summary
The change is still uncommitted on `feat/KAN-96-platform-file-storage`. It touches only the files listed in attempt 1
(`.env.example`, `backend/pom.xml`, `application.yml`, `common/tenant/`, `platform/` main and test, `support/GarageTestBase`).
`docs/diagrams/` and the harness files are not part of it. Decision D18 required fixes for review findings 1–6 and 8–10
and for security findings 1–4.

All of those are fixed in the production code, and none of the fixes breaks AC-007.1..15 or the interface contract.
gitleaks is now clean. One fix brings in a new problem: the JFR check for AC-007.14 (the fix for finding 9) **fails on
correct code** whenever anything writes to the console during the recording. This always happens when the IT runs
alone, so the test depends on the order the ITs run in. That is a failing test under a valid run, so the verdict is fail.
The fix is one line.

## Required findings: status
| Finding | Status | Evidence |
|---------|--------|----------|
| R1 / S1 gitleaks | fixed | `GarageTestBase.java:30,102-106` builds a random 32-byte hex `rpc_secret`. `ObjectFileStorageIT.java:139` uses `UUID.randomUUID()`. `gitleaks dir backend/src`: no leaks found |
| R2 commons-logging | fixed | `pom.xml` adds an `<exclusion>` of `commons-logging` on `apache-client`. In the poms, `s3` brings `apache-client` only in test scope, and the `services` parent brings `apache5-client` and `netty-nio-client` (excluded). So httpclient 4 arrives only through the direct dependency. I did not run `dependency:tree` (Maven was barred) |
| R3 / S3 bounds | fixed | `StorageConfig.java:83-89` sets TTL to 1..604800 and size to 1..104857600 (D20), so `(int) maxBytes + 1` at `ObjectFileStorage.java:67` cannot overflow. `StorageConfigTest.java:53-59` refuses 604801, 104857601 and 0 (TTL) |
| R4 read 404 | fixed | `ObjectFileStorage.java:92-93,155-160` share `mapNotFound`. `ObjectFileStorageIT.java:267-282` mocks a bare 404 (mapped to MSG06) and checks that 403 and 500 propagate (`isSameAs`). Undoing the fix makes the test fail |
| R5 +1 byte on a dead client | fixed | `ObjectFileStorageIT.java:182-187`. Any S3 call would raise `SdkClientException`, so the test proves the size is refused before upload |
| R6 log assertion | fixed | `ObjectFileStorageIT.java:153-159` expects exactly 27 `Rejected invalid file key` lines (9 keys × 3 calls) and no raw key fragments. The current Failsafe XML shows all 27 lines, each with `length=` only |
| R8 TTL flake | fixed | `TTL_SECONDS = 5` (`:70`) and a sleep of TTL + 3 s (`:323`). This leaves a margin of about 3–5 s against clock skew between the container and the host, which is better than before. Some risk remains (see A2-4) |
| R9 tmpdir check | **replaced by a check that is itself flaky** | see A2-1 |
| R10 context test | fixed | `StorageConfigTest` checks that a blank or absent endpoint creates no beans, that a set endpoint binds all 7 settings and creates exactly one `S3Client`, `FileStorage` and `StorageProperties`, and that invalid settings stop startup with the variable named and the secret not shown |
| S2 site name | fixed | `ObjectFileStorage.java:44,113-116` is the AC-002.4 RFC 1123 label regex, checked before `replace`. `ObjectFileStorageIT.java:391-408` covers `x@evil.example/y`, `Acme`, `-acme`, `acme-`, `a.b` and `""`, and asserts the exact message, so undoing the check makes it fail |
| S4 close the stream | fixed | Javadoc on `FileStorage.java:10-14`. A comment only, so the spec's interface signature is unchanged |

## Issues (attempt 2)

A2-1. **major (failing test, depends on run order)**: `backend/src/test/java/vn/tsolve/platform/ObjectFileStorageIT.java:358-369`
   (`path == null ||` at `:363`). JFR emits `jdk.FileWrite` with `path = null` for every write to stdout or stderr,
   from **any thread**. The filter counts each such event as a local file. I checked this with a JFR probe on the JBR 21.0.6
   and on JDK 24: `System.out` and `System.err` writes, and a write from a second thread, all record `path=null`.
   Then I ran a second probe with logback 1.5.18 and no configuration (its default is root DEBUG on the console). One
   `log.debug` line on `software.amazon.awssdk.request` is counted as a violation by this exact filter.
   When `ObjectFileStorageIT` runs in a JVM where no Spring context has set up logging, the AWS SDK and Apache client
   DEBUG lines during `store`/`read` fail the test even though the storage code is correct. That JVM can be
   `-Dit.test=ObjectFileStorageIT` (the attempt-1 targeted command), an IDE run of the class, or the CI `it-other` part
   if Linux file order puts it first. It passes today only because Spring ITs run earlier in the same JVM: the Failsafe
   XML shows Spring's log format, and the current run passes 14/14. A background thread of a cached context that logs
   during the 0.5 s window can also fail it. Codex found the same problem on its own (it rated it medium).
   **Fix:** ignore events with no path, because console and pipe writes are not files on disk. For example, use
   `filteredOn(e -> e.getString("path") != null)` and then keep only paths outside `tempDir`. Optionally also keep only
   events on the test thread (`e.getThread().getJavaThreadId() == Thread.currentThread().threadId()`), since the storage
   is synchronous. The check still catches the regressions that matter: `Files.createTempFile` with a write,
   `ResponseTransformer.toFile`, and an SDK spool to `%TEMP%`.
A2-2. **info**: `StorageConfigTest.java:18-19` uses `withUserConfiguration(StorageConfig.class)` only, so the
   `@ConditionalOnExpression` on `StorageProperties` (`StorageConfig.java:61`) under `@ConfigurationPropertiesScan`
   (`TSolveApplication.java:9`) is not exercised. If that condition were removed, startup with a blank endpoint would
   break, but this test would still pass. The many `@SpringBootTest` ITs with a blank endpoint would catch it in `mvn verify`.
   Source: codex-verified.
A2-3. **info**: `StorageConfigTest.java:53-59` has no `max-bytes=0` case for the lower bound. TTL 604800 and size
   104857600 are not checked as accepted values.
A2-4. **info**: `ObjectFileStorageIT.java:314-326`. The positive download must finish within 5 s of signing, and the
   expiry check has a 3 s margin. A clock skew of more than about 3 s on Docker Desktop or WSL2 can still cause a flake.
   This is accepted as the fix that D18 asked for.
A2-5. **info**: `ObjectFileStorageIT.java:153-159` checks the total number of log lines, not one line per call. A
   raw key in the log would still be caught by the `../`+key and `/`+key lines. Source: codex-verified (I rated it lower).

Accepted, not re-raised: R7 (D18, the atomicity of a single `PutObject`), R11 (info), S5 (D19).

## Traceability
`bun harnessclaude/hooks/sdd.ts trace platform STORY-007`: 15/15 criteria have a test. AC-007.1 and AC-007.12 are now
also named in `StorageConfigTest`.

## Assumptions made
- **Not run by me:** the dispatcher barred Maven and Docker because an orchestrator `mvn verify` was running. The test
  results are read from that run's `target/` reports: Surefire `StorageConfigTest` 7/7 (11:20) and Failsafe
  `ObjectFileStorageIT` 14/14 (11:26). I did not run the IT alone. A2-1 is proven from its parts (the two JFR probes
  above, plus the logback default), not by a failing isolated run.
- **A2-1 counts as a fail**, because a test that fails on correct code when run alone is a failing test under the rules
  (`evaluate` SKILL: failing test means fail). Judgment call for the user: if an order-dependent test is acceptable
  until the next attempt, this becomes a pass with the one-line fix as a condition.
- The 100 MB cap on `FILE_STORAGE_MAX_BYTES` (D20) is not in the spec, which gives only the default. I treat it as an
  allowed operator guard, not a spec change.

Proposed status: `bun harnessclaude/hooks/sdd.ts feature platform file-storage fixing` (the next attempt is the last).

# Attempt 3 (2026-11-11)
Verdict: **PASS**

## Summary
Since attempt 2, one change: the A2-1 hunk in `backend/src/test/java/vn/tsolve/platform/ObjectFileStorageIT.java:361-368`.
The `path == null ||` clause is now `path != null && !…startsWith(tempDir)`, with a comment. Only this file is newer than
the attempt-2 review file (by mtime, across `backend/src`, `pom.xml` and `.env.example`). Production files are
unchanged since 11:00–11:03. The anchors I checked in attempt 2 are where I expect them: `TTL_SECONDS` is still at `:70`,
the log assertion at `:153-159`, and the site test is shifted by 1 line because of the added comment. The files are
untracked, so there is no `git diff` to compare against.

## A2-1: status
Fixed. I checked the parts the fix depends on with a JFR probe on JBR 21.0.6 (scratchpad `probe/Probe.java`):
- `Files.write`, `FileOutputStream(File)`, `RandomAccessFile` and `FileChannel.open` writes to `Files.createTempFile`
  all record `jdk.FileWrite` with the real `%TEMP%\pN.tmp` path. That path is outside the JUnit `@TempDir`, so the
  filter still catches a temp-file spool in `%TEMP%`, as well as `ResponseTransformer.toFile` and similar writes.
- `System.out` records `path=null`, so the filter now ignores console writes. This was the cause of the failure.
- The only disk write that records `path=null` is `new FileOutputStream(fileDescriptor)` wrapping an already opened
  file. That disk file is still caught if it was opened through `RandomAccessFile` or `FileOutputStream(File)` and
  written that way. Writes through mmap and native code were never visible to JFR. So the weakening is negligible
  (info A3-1).

## Issues (attempt 3)
A3-1. **info**: as described above, a disk write made only through a `FileOutputStream(FileDescriptor)` would not be
   seen. Neither the AWS SDK nor Apache httpclient 4 writes files this way. No action.
A2-2 to A2-5 remain info, as in attempt 2. A2-4 (TTL clock-skew margin) is still the main residual flake risk.

## Traceability
`bun harnessclaude/hooks/sdd.ts trace platform STORY-007`: 15/15.

## Test Report
I ran the IT myself in the scenario that failed before:
`mvn -B -Dapi.version=1.44 -Dit.test=ObjectFileStorageIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false verify`.
The JVM had no Spring context: the Failsafe XML shows the logback default format, `[main] INFO … --`. Result:
ObjectFileStorageIT 14/14 in 16.8 s, `spotless:check` clean, BUILD SUCCESS. `gitleaks dir backend/src`: no leaks.
I did not run the full suite again. For that I rely on the orchestrator's full `mvn verify` before the fix (1249 unit,
435 IT, 0 failures). The fix touches only one test-local lambda.

## Assumptions made
- "Only that hunk changed" is checked by mtime and line anchors, not by a byte diff, because the files are untracked
  and attempt 2 kept no snapshot.
- I did not run the full suite again after the fix, as described in the Test Report. Judgment call for the user: should a
  full `mvn verify` run before the commit? The project's commit rule requires it anyway.

Proposed status: `bun harnessclaude/hooks/sdd.ts feature platform file-storage passing`.
