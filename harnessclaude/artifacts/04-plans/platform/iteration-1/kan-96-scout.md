# KAN-96 Task 16 — scout of the backend (11/11)

Read-only map for `FileStorage` / `ObjectFileStorage` on Garage. Sources: tasks.md §Task 16 (l. 567-596), spec STORY-007
(l. 218-238) and Interface Contracts (l. 423-495), design.md (Storage layout l. 273-283, R11 l. 285-299, Infrastructure
l. 342-348, Key Decisions R11-R13 l. 410-412, W1 l. 440, File Structure l. 446-514, Configuration l. 531-556, edge rows
l. 602-609), kan-96-decisions.md D1-D7, backend/AGENTS.md.

## 0. Blocking findings (read first)

1. **No `vn.tsolve.platform` and no `vn.tsolve.common.tenant` package exists** (on this branch or on any remote branch:
   `origin/feat/KAN-102-worker-tenant-context` and the `docs/KAN-*` branches have none). `TenantContext`,
   `WorkspaceCatalog`, `WorkspaceSummary`, `WorkspaceStatus` are only in the spec. D1/D5 (`ObjectProvider<TenantContext>`)
   need the **types** to compile, so Task 16 has to create at least the interfaces/records exactly as the spec's
   Interface Contracts (spec l. 427-447): `common/tenant/TenantContext.java`, `platform/service/WorkspaceCatalog.java`,
   `platform/dto/WorkspaceSummary.java`, `platform/dto/WorkspaceStatus.java`. That is more than the "six production
   files" of tasks.md l. 580, and Hiếu's Tasks 4 and 6 will create the same files → merge conflict unless the files
   are byte-identical to the spec. Decide and log as D8.
   - identity has its own interim ports `identity/signin/port/TenantScope.java:6` (`UUID currentWorkspaceId()`,
     `runIn`) and `WorkspaceLookup.java:6` (`findById` → `WorkspaceRef`), impl `LocalTenantScope.java`. Platform must
     not depend on identity (AGENTS.md §1 "No cycles", platform is the lower module), so these cannot be reused.
2. **Required env vars break every `@SpringBootTest`.** Test-wide values live in
   `backend/src/test/resources/config/application.yml` (l. 1-10: `SERVER_PORT`, `TSOLVE_JIRA_APP_ID`, `REDIS_*`, …).
   The other required ones are set per class with `@TestPropertySource` (`TSOLVE_LOGIN_ORIGIN` in 38 test files).
   29 classes use `@SpringBootTest`. If `FILE_STORAGE_*` have no default in `application.yml` (AGENTS.md §4: URLs and
   secrets have no default), each of them has to get the values. The test config file is **not in platform `paths`**
   (module.md lists only `backend/src/main/resources/application*.yml`), so writing it needs a quick change
   (`sdd.ts quick … backend/src/test/resources/config/application.yml`) or a `paths` entry. Recommended: add dummy
   `FILE_STORAGE_*` values there; building `S3Client`/`S3Presigner` makes no network call, so the contexts start.
3. **`FileTooLargeException` has no base or status in spec/design.** Precedent: `ticket/exception/UploadTooLargeException.java:8-12`
   extends `MessageCodeException` with `HttpStatus.PAYLOAD_TOO_LARGE, "MSG28"`. Spec AC-007.6 says "the caller shows its
   own message (MSG34 / MSG89)". Pick: (a) a plain `RuntimeException` the caller maps (recommended, it carries no
   code the caller would override), or (b) `MessageCodeException(413, "MSG28")`. Log the choice.
4. `InvalidFileKeyException` **must** use the coded constructor: `super(ProblemDetails.UNEXPECTED_ERROR, Map.of())`
   ("MSG06"). The message-only `NotFoundException(String)` gets **MSG04** from the handler (see §2).

## 1. Package layout

AGENTS.md §1 (l. ~49-75): `vn.tsolve.<module>.{controller,service,service/impl,repository,entity,dto,mapper,exception,client}`;
`common/{config,exception,security,util}`. Services are an interface + `service/impl/<X>Impl` (rule 3), but the
external-system-client naming (`AiClient`, `OllamaAiClient`) fits here too; design picks `FileStorage` +
`service/impl/ObjectFileStorage` (design l. 474-476), so keep it.
Example module `chatbot/rating/`: `controller/ChatbotRatingController.java`, `exception/ChatbotAnswerNotFoundException.java`,
`exception/InvalidChatbotRatingException.java`, service/… . `worker/config/WorkerConfiguration.java` +
`worker/config/WorkerProperties.java` is the closest config + properties pair in a module.
Target (design l. 446-482): `common/tenant/TenantContext.java`; `platform/config/StorageConfig.java` (`tsolve.storage.*`),
`platform/service/FileStorage.java`, `platform/service/impl/ObjectFileStorage.java`, `platform/dto/StoredFile.java`,
`platform/exception/{FileTooLargeException,InvalidFileKeyException}.java`. Tests: `test/java/vn/tsolve/platform/ObjectFileStorageIT.java`,
`test/java/vn/tsolve/support/GarageTestBase.java` (the `support` package does not exist yet; it is in platform `paths`).
Note design l. 467 also lists `PlatformProperties (… storage)` beside `StorageConfig`; tasks.md names only `StorageConfig`.
Simplest: a `StorageProperties` record (or a nested record in `StorageConfig`) + `StorageConfig` building the clients.

## 2. Exceptions

- `common/exception/CodedException.java:10` abstract `RuntimeException`; ctors `(String message)` l. 17 (code null) and
  `(String code, Map<String,?> params, String field)` l. 24.
- `NotFoundException.java:6` — `NotFoundException(String message)` l. 8; `NotFoundException(String code, Map<String,?> params)` l. 12.
- `MessageCodeException.java:10` — `protected (HttpStatus, String code, Map<String,Object>)` l. 14, `+ field` l. 18; `getStatus()`.
- `BusinessRuleException.java:6` — 409, ctors `(message)`, `(code, params)`, `(code, params, field)`.
- `ProblemDetails.java:12` `UNEXPECTED_ERROR = "MSG06"`.
- `GlobalExceptionHandler.java:71-75`: `handleNotFound` → 404; `e.getCode()==null` → code **MSG04**, else the
  exception's code. l. 77 `MessageCodeException` → its status; l. 106-115 catch-all → 500 MSG06 (class name logged only).
- Subclass example: `chatbot/rating/exception/ChatbotAnswerNotFoundException.java:6-9` → `super("MSG04", Map.of());`.
  So: `public InvalidFileKeyException() { super(ProblemDetails.UNEXPECTED_ERROR, Map.of()); }`
  (or the literal "MSG06", as `chatbot/exception/InvalidChatbotRequestException.java:10` does). Never put the key in
  the message (logged by the handler only as class name, but keep it out anyway).

## 3. Configuration properties and YAML

- Registration: `TSolveApplication.java:9` `@ConfigurationPropertiesScan` (whole `vn.tsolve` tree) — a record with
  `@ConfigurationProperties` anywhere is picked up; some modules also use `@EnableConfigurationProperties`
  (`worker/config/WorkerConfiguration.java:21`, `identity/signin/SignInConfig.java:23`).
- Style: `common/config/TrackerProperties.java:10-12`
  ```java
  @Validated
  @ConfigurationProperties(prefix = "tsolve.tracker")
  public record TrackerProperties(@NotBlank String jiraAppId, @NotNull URI forgeJwksUri) {}
  ```
  Defaults with `@DefaultValue("4") @Min(1) int workerCount` (`worker/config/WorkerProperties.java:17`). Validation in a
  compact constructor naming the env var, never the value: `identity/keycloak/KeycloakProvisioningProperties.java:33-55`;
  override `toString()` to keep secrets out (l. 57-60) — do that for the secret key.
- `application.yml` (`backend/src/main/resources/application.yml`): header l. 1-2 "only `${VAR}` placeholders";
  `.env` import l. 7; everything app-specific under `tsolve:` (l. 66-108), kebab-case keys mapped to env names, e.g.
  `tsolve.tracker.jira-app-id: ${TSOLVE_JIRA_APP_ID}` (l. 81); defaults only for non-secrets (`${TSOLVE_WORKER_COUNT:4}` l. 73).
  Multipart limit already 10MB / 11MB (l. 35-38). Proposed block:
  ```yaml
    storage:
      endpoint: ${FILE_STORAGE_ENDPOINT}
      access-key: ${FILE_STORAGE_ACCESS_KEY}
      secret-key: ${FILE_STORAGE_SECRET_KEY}
      bucket: ${FILE_STORAGE_BUCKET}
      public-url-pattern: ${FILE_STORAGE_PUBLIC_URL_PATTERN}
      url-ttl-seconds: ${FILE_STORAGE_URL_TTL_SECONDS:300}
      max-bytes: ${FILE_STORAGE_MAX_BYTES:10485760}
  ```
  Region: design l. 348 "a fixed region name" → a constant `garage` (must equal Garage's `s3_region`); not an env var.
- Test profile: there is **no** `application-test.yml`; tests use `backend/src/test/resources/config/application.yml`
  (env-style keys, Spring loads `config/` from the classpath) plus `@TestPropertySource` / `@DynamicPropertySource`.

## 4. backend/pom.xml (201 lines)

- Parent `spring-boot-starter-parent` **3.3.5** (l. 6-11); `java.version` **21** (l. 20).
- Properties l. 19-28: mapstruct 1.6.3, springdoc 2.6.0, spotless 2.43.0, wiremock 3.9.2, jobrunr 8.6.0,
  **`testcontainers.version` 1.21.4** (overrides Boot's managed version).
- **No `<dependencyManagement>` section.** The AWS SDK is not in Boot's BOM, so W1 needs one new block:
  `software.amazon.awssdk:bom` (import, latest on Maven Central today: **2.57.12**, 9/11/2026) + dependency
  `software.amazon.awssdk:s3` without a version. `s3` pulls apache-client and netty-nio-client; exclude
  `netty-nio-client` if you want (only the sync client is used) — optional.
- Test deps: `spring-boot-testcontainers` (l. 115-119), `org.testcontainers:postgresql` (l. 120-124; brings the core
  `testcontainers` jar with `GenericContainer` — nothing new needed for Garage), `testcontainers-keycloak` 3.9.1,
  wiremock. **No `org.testcontainers:junit-jupiter`**, so no `@Testcontainers`/`@Container`; containers are started
  in `static {}` blocks.
- Failsafe l. 147-159 (`integration-test` + `verify`, default includes `**/*IT.java`); Surefire default (`*Test`).
- Spotless l. 178-198: palantirJavaFormat + removeUnusedImports, `ratchetFrom origin/main`, `check` bound to `verify`.
- Docker 29: nothing committed. Tests document `-DargLine=-Dapi.version=1.44` ("never committed"), e.g.
  `identity/signin/RealmImportIT.java:50`. Local recipe (memory): JAVA_HOME = IntelliJ jbr, `mvn -B -Dapi.version=1.44 verify`.
- CI `.github/workflows/ci.yml:178-180`: a new `*IT` runs in `it-other` automatically; no list edit needed.

## 5. Testcontainers patterns

- No `PostgresTestBase` or `GarageTestBase` exists yet. Shared-per-JVM pattern: `identity/TwoTenantDatabases.java:34-58`
  — `@TestConfiguration`, `private static final PostgreSQLContainer<?> SERVER = …;` + `static { SERVER.start(); … }`,
  "stopped by Testcontainers' Ryuk". AGENTS.md §8 asks: one container per JVM, no new container per IT class.
- Spring-booted IT with a GenericContainer: `identity/RedisWiringIT.java:36-76` — `@SpringBootTest @AutoConfigureMockMvc
  @TestPropertySource(properties = {"TSOLVE_LOGIN_ORIGIN=…", …, "DB_HOST=unused", …})`, `@ServiceConnection static final
  PostgreSQLContainer<?> DB`, `static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
  .withCommand(…).withExposedPorts(6379)`, `static { DB.start(); REDIS.start(); }`, `@DynamicPropertySource` registering
  env-style names (`registry.add("REDIS_HOST", REDIS::getHost)`).
- 19 of the container ITs boot **no** Spring context (e.g. `review/ReviewStorageIT.java:23`: plain JUnit + container +
  hand-built objects). Recommended for `ObjectFileStorageIT`: no Spring context — build `ObjectFileStorage` by hand
  with a small fake `TenantContext` (ThreadLocal) and a fake `WorkspaceCatalog` (map id → summary). Nothing to boot,
  no Postgres needed, no dependency on Tasks 4/6, about 2-5 s. For D5, an `ObjectProvider` can be built in the test
  with `new StaticListableBeanFactory(Map.of(...)).getBeanProvider(TenantContext.class)` (an empty factory = "none bound").
  Add one `@SpringBootTest` case only if you want to prove the context starts without the beans (D5); it then needs
  the item-2 properties.

## 6. AC naming and `sdd.ts trace`

- Convention (AGENTS.md §2 Test row): method name ends with the id, `rejectsEditWhenPublished_AC_003_2`; also
  `@DisplayName("AC-003.1 …")` (e.g. `ai/client/AiStartupTest.java:17`). 1063 `AC_0…` matches in 156 files.
- Matcher `harnessclaude/hooks/lib/spec.ts:34`:
  `/(?<![A-Za-z0-9])AC(?:-(\d{3})\.(\d+)|_(\d{3})_(\d+))(?![A-Za-z0-9])/g` — anywhere in the file text (names, display
  names, comments). Note `refusesX_AC_007_5` matches (the `_` before AC is not alphanumeric); `AC_007_5and` would not.
- Files scanned (`hooks/sdd.ts:47`): `**/*.{test,spec}.*`, `**/test_*.py`, `**/*_test.*`, `**/*Test.java`, `**/*IT.java`,
  except `node_modules`, `.git`, `harnessclaude/` (l. 49). A file counts for platform only if it matches platform
  `paths` (`hooks/lib/workspace.ts:98-101`): `backend/src/test/java/vn/tsolve/platform/**`, `…/support/**`,
  `…/common/tenant/**` (module.md). So `ObjectFileStorageIT` under `vn/tsolve/platform/` is traced; per D6 it must
  name `AC_007_11`. Check: `bun harnessclaude/hooks/sdd.ts trace platform STORY-007`.

## 7. Code style

- SLF4J: `private static final Logger log = LoggerFactory.getLogger(X.class);` (25 uses; `LOG` in 9). No Lombok
  (AGENTS.md rule 9; none in pom or code). Records for DTOs/properties. Constructor injection, `private final`
  fields, no field `@Autowired`, no wildcard imports, no `System.out`. Comments say *why*. Never log secrets or
  personal data: log the refused key attempt as a fixed message + length/reason, not the raw value (it may hold
  `\0` or control characters — or log it escaped).

## 8. `.env.example` and compose

- `.env.example`: **no `FILE_STORAGE_*`** (0 matches); also no `FILE_STORAGE_ROOT` to remove. Add the 7 names with
  comments, in the style of l. 1-80 (a comment line per group, `NAME=` empty). Local value of the pattern:
  `http://{site}.localhost` (design l. 289-291).
- Root `docker-compose.yml`: services db (pgvector:pg16), redis:7.4-alpine, keycloak db, `quay.io/keycloak/keycloak:26.4.7`.
  **No Garage/MinIO/S3**; no `infra/` compose files (only `infra/keycloak/`). Garage in compose is Task 1 (D1, D7).

## 9. Garage

- **Pinned tag:** none anywhere in the repo or docs (design l. 344 only says "pinned `dxflrs/garage` image").
  Current release: **`dxflrs/garage:v2.5.0`** (Docker Hub tag list and the Garage quick start; image built
  24/10/2026). Use it and pin the same tag in Task 1's compose.
- **Image has no shell — verified** from the registry config of v2.5.0/amd64: one layer, history
  `COPY result/bin/garage /`, `Cmd ["/garage","server"]`, Env `RUST_LOG=garage=info`. So: config via
  `withCopyToContainer(Transferable.of(toml), "/etc/garage.toml")` (Docker API copy, no shell), CLI via
  `container.execInContainer("/garage", "…")`. Default config path `/etc/garage.toml` (confident).
- Minimal `garage.toml` for the test (from the quick start, without `root_domain` so every request is path-style;
  omit `[s3_web]`; `[admin]` only if you wait on `/health`):
  ```toml
  metadata_dir = "/tmp/meta"
  data_dir = "/tmp/data"
  db_engine = "sqlite"
  replication_factor = 1
  rpc_bind_addr = "[::]:3901"
  rpc_public_addr = "127.0.0.1:3901"
  rpc_secret = "<64 hex chars>"
  [s3_api]
  s3_region = "garage"
  api_bind_addr = "[::]:3900"
  [admin]
  api_bind_addr = "[::]:3903"
  ```
- **Shortcut (v2.3.0+, confident; in `cli/structs.rs` of 2.3.0):** `garage server --single-node --default-bucket`
  sets the single-node layout itself and creates a key from `GARAGE_DEFAULT_ACCESS_KEY` / `GARAGE_DEFAULT_SECRET_KEY`
  and a bucket `GARAGE_DEFAULT_BUCKET` (`--default-bucket` implies `--default-access-key`; both need `--single-node`).
  `withCommand("/garage","server","--single-node","--default-bucket")` + `withEnv(...)`. **Unsure:** which rights the
  default key gets on the bucket (the docs do not say; probably read+write+owner). If the bootstrap must match
  garage-init.sh exactly (D7: "the same `garage` CLI steps"), use the manual steps below.
- **Manual bootstrap** (same steps garage-init.sh should run; confident for the commands, less for output parsing):
  1. `/garage node id -q` → `<hex id>@<addr>`; take the part before `@` (or parse `/garage status`; a unique prefix of
     the id is accepted by `layout assign`).
  2. `/garage layout assign -z dc1 -c 1G <id>`, then `/garage layout apply --version 1`.
  3. `/garage bucket create <bucket>`.
  4. Key: `/garage key create <name>` (prints `Key ID: GK…` and `Secret key: <64 hex>`; parse) **or**
     `/garage key import --yes -n <name> <keyId> <secret>` (`--yes` is required, `structs.rs` KeyImportOpt).
     Key id format: docs' example output is `GK` + 24 hex (`GK3515373e4c851ebaad366558`) but the quick start generates
     `GK$(openssl rand -hex 16)` (32 hex). **Unsure** whether import validates the length — `key create` + parse
     avoids the question.
  5. `/garage bucket allow --read --write <bucket> --key <name>` — design l. 347 says read/write on that bucket only,
     so leave out `--owner` (not needed for Put/Get/Head/Delete).
  Check each `ExecResult.getExitCode() == 0`, else fail with stderr.
- **Readiness:** `HostPortWaitStrategy`'s internal check runs `/bin/sh` in the container; without a shell Testcontainers
  should skip it with a warning (fairly sure, not verified for 1.21.4). Safer: `Wait.forHttp("/health").forPort(3903)`
  (admin API, no token; confident it exists, not verified for v2.5.0) or `Wait.forLogMessage(".*S3 API.*", 1)`
  (exact text unverified). With `--single-node --default-bucket`, also retry `HeadBucket` until 200.
- **Anonymous access (AC-007.15, Edge #39):** Garage has no anonymous S3 access and no bucket policies; public reads exist
  only through the `[s3_web]` website endpoint when website access is enabled on a bucket (confident). An unsigned
  `GET http://host:3900/<bucket>/<obj>` should get 403 — assert it.
- **Signed URL in the test (R11):** sign for `http://{site}.localhost:<mappedPort>`, send the request to
  `127.0.0.1:<mappedPort>` with `Host: acme.localhost:<mappedPort>` (SigV4 signs `host`). `*.localhost` does not
  resolve reliably in Java on Windows. `java.net.http.HttpClient` and `HttpURLConnection` refuse a custom `Host` unless
  `-Djdk.httpclient.allowRestrictedHeaders=host` / `sun.net.http.allowRestrictedHeaders=true`; Apache HttpClient
  (comes with the SDK's `apache-client`) or a raw socket allows it. TTL: set `url-ttl-seconds` to 1-2 in the test and
  wait past it (an IT may wait on the clock; AGENTS.md §8 forbids that only in `*Test`).
- **Edge #38 (Garage down / connection broken):** stopping the shared container would break the other tests; point a
  second `ObjectFileStorage` at a closed port and at a local `ServerSocket` that closes mid-body, then check with the
  real client that `ListObjectsV2` under the prefix and `ListMultipartUploads` are empty.
- Client settings: `S3Client.builder().endpointOverride(endpoint).region(Region.of("garage"))
  .forcePathStyle(true)` (`serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true))` on the presigner),
  static credentials; `S3Presigner.builder().endpointOverride(<public URL with the site>)` — one presigner per
  site name, or one presigner per call (cheap, no network). Recent SDK 2.x versions add CRC32 checksums to `PutObject`
  by default; if Garage rejects them, set `requestChecksumCalculation(WHEN_REQUIRED)` (unsure for v2.5.0).

## Unresolved questions

1. Who creates the `TenantContext` / `WorkspaceCatalog` / `WorkspaceSummary` / `WorkspaceStatus` types now (§0.1)?
2. Edit `backend/src/test/resources/config/application.yml` (outside platform `paths`) by a quick change, or give the
   storage properties some other way (§0.2)?
3. Base class and status of `FileTooLargeException` (§0.3).
4. Garage bootstrap in the test: `--single-node --default-bucket` (short) or the manual CLI steps (matches D7) (§9).
