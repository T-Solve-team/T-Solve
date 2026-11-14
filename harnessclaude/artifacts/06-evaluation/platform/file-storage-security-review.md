## Security Review — platform (STORY-007 file storage, KAN-96) — 2026-11-11

Scope: the uncommitted change on `feat/KAN-96-platform-file-storage`: `.env.example`, `backend/pom.xml`,
`application.yml` (`tsolve.storage`), `vn.tsolve.common.tenant.TenantContext`, `vn.tsolve.platform.{config,dto,
exception,service,service.impl}`, `ObjectFileStorageIT`, `GarageTestBase`. Against spec STORY-007 (AC-007.1 – .15),
design "Storage layout", "Signed URL host and routing", R11 – R13, edge rows 35 – 42, decisions `kan-96-decisions.md`.
The Caddy route for signed URLs (Task 17) is deferred (D2) and not in scope.

**Verdict: CAUTION.** No Critical or High finding, so security does not fail the feature. Finding 1 (gitleaks) blocks
the commit hook and the required CI check, so it must be fixed before committing.

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 1 | 0 |
| Dependencies | 0 | 0 | 0 | 0 |
| Code | 0 | 0 | 0 | 2 |
| STRIDE | 0 | 0 | 0 | 2 |

### Checks run
- **Tests:** `mvn -B -o -Dapi.version=1.44 -Dit.test=ObjectFileStorageIT verify` (spotless skipped): 12 run, 0 failures.
- **Secrets:** the patterns in `references/patterns.md` over the changed files: no real credential. I also ran
  `gitleaks dir` (8.x default rules, the CI tool) over a copy of the changed files: 2 `generic-api-key` hits, both test
  literals (finding 1). `.env.example` adds only empty `FILE_STORAGE_*=` lines. The Garage test key and secret are
  created at runtime by the Garage CLI (`GarageTestBase.java:52-55`), not hard-coded.
- **Tracked env files:** `git ls-files` shows only `.env.example` and `frontend/.env.example`. `.gitignore` has `.env`,
  `.env.*`, `!.env.example`.
- **Dependencies:** `mvn dependency:tree` shows `software.amazon.awssdk:*:2.57.12`, `httpclient 4.5.13` (contains
  the CVE-2020-13956 fix), `httpcore 4.4.16`, `commons-codec 1.16.1`, `commons-logging 1.2`. `apache5-client` and
  `netty-nio-client` are excluded, so the change adds no Netty and no httpclient5. I know of no CVE for these versions,
  but I did not check them against a live CVE database: no OWASP dependency-check is set up and I used no web
  search. The Netty 4.1.114 already on the classpath through Lettuce is older than the fixes for CVE-2025-24970 and
  CVE-2025-25193 (4.1.118). This change did not add it, so it is out of scope here; it belongs to the Spring Boot
  upgrade.
- **Key validation (AC-007.5, edge 35):** `KEY_PATTERN` is used with `matches()`, so the whole input must match, and
  `[0-9a-f]` matches ASCII only. I checked with a small Java program that `key\n`, `key\r\n`, a full-width digit `１`,
  a Cyrillic `а`, a trailing NUL and a leading U+202E are all refused. The IT also checks `../`, `C:\x`, `%2e%2e/`, NUL,
  `a/b`, an absolute key, upper case, an empty key and `null`, each against a dead client, so no object is touched.
- **Tenant isolation (GBR-37):** the Workspace id comes only from `TenantContext.requireWorkspaceId()`. No method takes
  a Workspace id, a path or a file name. The object name is always `<boundId>/<validated uuid>` (`ObjectFileStorage.java:77,
  90, 100, 144`). A missing `TenantContext` bean fails closed (`:147-152`), and `getIfAvailable` throws when there are
  two beans. The IT shows that B cannot read, sign or delete A's object, and A's object is still there afterwards.
- **Signed URLs (AC-007.12):** a presigned `GetObject` only. SigV4 signs the host, the path and every query parameter,
  so the method, the key and `response-*` overrides cannot be changed. TTL = `FILE_STORAGE_URL_TTL_SECONDS` (at least 1).
  The IT checks: a changed key gives 403, an unsigned request 403, an expired URL 400, and the host is per Workspace.
  `HeadObject` runs before signing (AC-007.13).
- **Information disclosure:** a refused key logs only its length (`:157`), so no log injection is possible. Every
  not-found answer is `MSG06` with no params. `IllegalStateException` messages never reach the response body:
  `GlobalExceptionHandler.java:106-114` logs only the class at ERROR and the full exception at DEBUG.
  `StorageProperties.toString()` hides the endpoint, keys and bucket (`StorageConfig.java:86-89`), and so does
  `AwsBasicCredentials.toString()`. Actuator exposes only `health,info`, so `configprops` cannot show the keys.
  `signedUrl` drops the `URISyntaxException` cause (`:135-136`), and the signed URL is never logged.
- **DoS (R13):** `store` reads at most `maxBytes + 1` bytes into memory, so one call holds about 10 MB (up to twice
  that while `readNBytes` builds the array). It refuses an oversized upload before any S3 call. Upload callers should
  also stay under the existing multipart limit (`application.yml:37-38`: 10 MB / 11 MB).

### STRIDE
| Threat | Result |
|--------|--------|
| Spoofing | No endpoint in this change. Requests to Garage are signed with server-side static credentials. The signed URL works as a bearer token for one object until the TTL ends, by design (R11). |
| Tampering | Keys are strictly validated. The content type is fixed to `application/octet-stream` (R12). A single `PutObject` of known length is atomic, so nothing partial is left (IT: dead endpoint and broken connection leave no object and no multipart upload). |
| Repudiation | A refused key is logged at WARN with no caller-supplied data. Caller and actor auditing belongs to the Evidence and Wiki callers. |
| Information disclosure | See Checks. One bucket-wide key with read/write access, so isolation rests only on application code (design R11; accepted). |
| Denial of service | Uploads are bounded in memory. See findings 3 and 4. |
| Elevation of privilege | The prefix comes only from the bound Workspace. See finding 5 (Suspended Workspaces). |

SSRF (A10): no URL from a user is fetched. The S3 endpoint and the URL pattern come from the operator's environment.

### Findings
1. **[MEDIUM][SECRETS]** `backend/src/test/java/vn/tsolve/support/GarageTestBase.java:28` (`rpc_secret = "0123…"`) and
   `backend/src/test/java/vn/tsolve/platform/ObjectFileStorageIT.java:127` (`String key = "abcdefab-…"`). gitleaks
   `generic-api-key` flags both. They are not secrets, but the pre-commit hook and the required `gitleaks` PR check
   (`.github/workflows/pr-checks.yml:54`) will refuse the commit.
   - Fix: build both at runtime: `HexFormat.of().formatHex(<32 random bytes>)` for `rpc_secret`, and
     `UUID.randomUUID().toString()` for the sample key. Or add a `// gitleaks:allow` comment on those lines. Never use
     `--no-verify`.
2. **[LOW][CODE]** `backend/src/main/java/vn/tsolve/platform/service/impl/ObjectFileStorage.java:117`. The site name is
   put into `FILE_STORAGE_PUBLIC_URL_PATTERN` unchecked. I checked that the site name `x@evil.example/y` gives the host
   `evil.example`. Today the site name comes only from the catalog, which accepts only RFC 1123 labels (AC-002.4,
   AC-013.2), so no outside input reaches this line. The risk is a future catalog bug or a seed row that skips
   validation.
   - Fix: check `siteName` against `^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$` before `replace`, and throw
     `IllegalStateException` if it does not match.
3. **[LOW][CODE]** `backend/src/main/java/vn/tsolve/platform/config/StorageConfig.java:78-83`. Neither setting has an
   upper limit. If `FILE_STORAGE_URL_TTL_SECONDS` is over 604800, every `signedUrl` fails at call time (the SigV4
   limit), and a long TTL undermines the short-lived URLs of H-Q4. If `FILE_STORAGE_MAX_BYTES` is `Integer.MAX_VALUE`
   or more, the read limit is capped (`ObjectFileStorage.java:66`) and `bytes.length > maxBytes` can never be true, so a
   larger file is stored cut short without an error; a large limit also invites heap exhaustion. Only an operator can
   set these values.
   - Fix: refuse at startup if the TTL is over 604800 (or a tighter cap such as 3600) or if `maxBytes` is over a sane
     cap (for example 100 MB), well below `Integer.MAX_VALUE - 8`.
4. **[LOW][STRIDE-DoS]** `backend/src/main/java/vn/tsolve/platform/service/FileStorage.java:10` /
   `ObjectFileStorage.java:90`. `read` returns the SDK's `ResponseInputStream`, which holds a pooled HTTP connection
   (Apache client default: 50). A caller that never closes it uses up the pool, and then every storage call hangs
   until the connection-acquire timeout. The interface does not say who must close the stream.
   - Fix: document on `FileStorage.read` that the caller must close the stream (try-with-resources), and check this in
     the Evidence and Wiki reviews.
5. **[LOW][STRIDE-EoP]** `ObjectFileStorage.java:115`. `signedUrl` does not check `WorkspaceSummary.status`.
   `runIn` may bind a Suspended Workspace (AC-002.15), so a job can still issue a URL for it. And because the Caddy
   route goes straight to Garage without the resolver, a URL issued before the suspension keeps working until its TTL
   ends (300 s by default). GBR-38 is weakened, but only within that window.
   - Fix (judgment call, not required by the spec): throw `NotFoundException` when `status == SUSPENDED`; otherwise
     accept the TTL window and say so in the design.

### Open items for Task 17 (Caddy signed-URL route, deferred under D2)
Several controls of this design live only in that route. Review them when it lands: `GET`/`HEAD` only with 405 for
other methods, `X-Content-Type-Options: nosniff` (R12, edge 41), `X-Amz-Signature` / `X-Amz-Credential` removed from
the access log (edge 40), and Garage with no published port. Until then, signed URLs for the public pattern reach
nothing (fail closed). Unsigned access is refused today (IT: unsigned 403), so AC-007.15 already holds.

### Assumptions made
- **Report path.** I was asked to write `artifacts/05-reviews/platform/file-storage-security.md`. The reviewer role
  and the security-review skill put reports under `artifacts/06-evaluation/`, so the report is at
  `artifacts/06-evaluation/platform/file-storage-security-review.md`.
- **Finding 5.** Whether a Suspended Workspace may still get a signed URL is a product decision for the user. It is
  recorded as Low and does not block.
- **Dependency CVEs.** The dependency versions were not checked against a live CVE database.
