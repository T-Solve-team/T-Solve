# T-Solve backend — guide for developers and AI coding agents

> **Read this file first.** It is the one place for the backend's conventions, for every person and every
> AI tool working in `backend/`. `CLAUDE.md` only imports this file, so Claude Code, Codex, Cursor and
> Copilot all read the same text. Code that breaks a rule here fails review. A rule changes only here, in a
> pull request the team reviews — never in one tool's private settings.

## Stack (exact versions in `pom.xml`)

Java 21 · Spring Boot 3.3 (Web, Data JPA, Validation, Security, Actuator) · PostgreSQL 16 + pgvector ·
Liquibase · MapStruct · springdoc-openapi · JUnit 5 + AssertJ + Mockito · Spotless (Palantir Java Format) ·
Maven. One deployable: a modular monolith.

## Hard rules

1. **Package by module, then by layer** — `vn.tsolve.<module>.<layer>`. → [§1](#1-project-structure)
2. **Calls go one way:** controller → service → repository. Another module is used only through its
   `service/` interfaces and `dto/` records. → [§1](#1-project-structure)
3. **Every service is an interface** `<Entity>Service` with its implementation in `service/impl/`, even with
   one implementation. Callers inject the interface. → [§3](#3-layers)
4. **Entities never leave the service layer.** Services return DTO records; entity → response goes through a
   MapStruct mapper. → [§3](#3-layers)
5. **The schema changes only through a new Liquibase changeset;** never edit an applied one. → [§7](#7-database)
6. **No secret, URL or environment-specific value in a committed file.** Config holds `${VAR}`
   placeholders. → [§4](#4-spring-and-configuration)
7. **Every endpoint declares its roles with `@PreAuthorize`.** Errors are thrown and rendered as
   `ProblemDetail` with the SRS message code (MSGxx); never invent message text. → [§5](#5-rest-errors-security)
8. **An API change updates `frontend/openapi/tsolve-api.yaml` first,** then the code. → [§6](#6-api-documentation-springdoc)
9. **No Lombok, no field injection, no `System.out`, no wildcard imports.** → [§4](#4-spring-and-configuration), [§9](#9-code-style)
10. **Every bug found gets a row in [`docs/bug-log.md`](../docs/bug-log.md);** its fix comes with a test that
    names the bug id. → [§10](#10-bugs)
11. **Before you say "done":** `mvn spotless:apply`, then `mvn verify` passes. → [§8](#8-tests)

## Commands (run in `backend/`)

| Command | What it does |
|---|---|
| `mvn spring-boot:run` | Runs the app. Needs `../.env` and the database (`docker compose up -d db` in the repo root). Health: `/actuator/health`. |
| `mvn verify` | Tests + Spotless format check. Must pass before a PR. |
| `mvn spotless:apply` | Formats the files changed since `origin/main`. |
| `mvn test -Dtest=SolutionServiceImplTest` | Runs one test class. |

Swagger UI: `/swagger-ui.html` · OpenAPI JSON: `/v3/api-docs`.

---

## 1. Project structure

Layered, one folder per module (package-by-module, then by layer):

```
backend/src/main/java/vn/tsolve/
├── <module>/            one folder per module (ticket, knowledge, identity, workspace, search, analytics)
│   ├── controller/      REST endpoints + springdoc annotations
│   ├── service/         <Entity>Service interfaces — what controllers and other modules call
│   │   └── impl/        <Entity>ServiceImpl: business rules, @Transactional boundaries
│   ├── repository/      Spring Data JPA interfaces
│   ├── entity/          JPA entities + enums owned by the module
│   ├── dto/             request/response records
│   ├── mapper/          MapStruct mappers entity → response dto
│   ├── exception/       the module's own exceptions, each extending a base in common/exception (§5)
│   └── client/          <System>Client interfaces + implementations for external systems (AI, Jira, mail)
└── common/              shared infrastructure only — no business logic, no entities
    ├── config/          @Configuration classes, the OpenAPI bean, @ConfigurationProperties records
    ├── exception/       base exceptions (NotFoundException, BusinessRuleException, …) + GlobalExceptionHandler
    ├── security/        security config, role constants
    └── util/            only when two modules need the same helper

backend/src/main/resources/
├── application.yml      ${VAR} placeholders only (§4)
└── db/changelog/        db.changelog-master.xml + changes/NNN-<slug>.sql (§7)

backend/src/test/java/vn/tsolve/   mirrors main: a test sits in the same package as the class it tests
```

- Calls go one way: controller → service → repository. A controller never touches a repository.
- Controllers and other modules depend on `service/` interfaces only, never on `service/impl/`.
- A module uses another module only through that module's `service/` interfaces and `dto/` records —
  never its `repository/`, `entity/`, `mapper/` or `client/`. Another module's exceptions may be caught.
- No cycles between modules: if A calls B, B never calls A. Move the shared piece into the lower module.
- `common/` depends on no module; every module may depend on `common/`.
- An entity refers to another module's entity by its id, never through a JPA association (`@ManyToOne`, …).
- Layers a module does not need are not created.
- The classes now in `knowledge/` and `ticket/` predate this layout; they move into it when their module is built.

## 2. Naming

| Item | Rule | Example |
|------|------|---------|
| Package | lowercase, singular | `vn.tsolve.knowledge.service` |
| Class / record / enum | PascalCase noun | `Solution`, `SolutionStatus` |
| Layer classes | `<Entity>Controller`, `<Entity>Repository` | `SolutionController` |
| Service | interface `<Entity>Service` + `<Entity>ServiceImpl` in `service/impl/` | `SolutionService`, `SolutionServiceImpl` |
| External system client | interface `<System>Client`, one class per implementation | `AiClient`, `OllamaAiClient` |
| DTO | `<Action><Entity>Request`, `<Entity>Response` | `CreateSolutionRequest`, `SolutionResponse` |
| Mapper | `<Entity>Mapper` in `mapper/` | `SolutionMapper` |
| Exception | base: `<Reason>Exception` in `common/exception/`; module: `<Entity><Reason>Exception` in `<module>/exception/` | `NotFoundException`, `SolutionNotFoundException` |
| Method / field | camelCase; methods start with a verb | `createDraft`, `reviewDueAt` |
| Constant | `static final` UPPER_SNAKE_CASE | `MAX_IMPORT_SIZE` |
| Boolean | `is` / `has` / `can` prefix | `isPublished()` |
| Table / column | snake_case, plural table | `solutions`, `review_due_at` |
| Key column | primary key `id` (UUID); a reference is `<entity>_id` (UUID); Java type `UUID`. A join table keeps the composite key of its two references and has no `id` | `id`, `solution_id`, `workspace_id` |
| REST path | `/api/v1/<plural-kebab-case>`; actions as sub-resources | `/api/v1/solutions/{id}/transition` |
| operationId | camelCase verb + noun, the same as in the contract | `listReviewQueue` |
| JSON field | camelCase; an id is a UUID string (`type: string`, `format: uuid`) | `externalId`, `ticketId` |
| Changeset | file `NNN-<kebab-slug>.sql`, id `NNN-<slug>`, author = module | `002-add-wiki-page.sql` |
| Test | `<Class>Test`; the method names the behavior and the AC or bug id | `rejectsEditWhenPublished_AC_003_2`, `keepsFirstImport_BUG_004` |

## 3. Layers

- **Controller** (`@RestController`): takes the validated input (`@Valid`), calls **one** service method, returns
  its DTO in a `ResponseEntity` (`ok(dto)`, `status(CREATED).body(dto)`, `noContent().build()`). No business
  logic, no `@Transactional`, no repository, no mapper.
- **Service** (interface `<Entity>Service` + `@Service` class `service/impl/<Entity>ServiceImpl`): the interface
  declares what callers may use; the impl holds the business rules and transactions — `@Transactional` on
  writes, `@Transactional(readOnly = true)` on reads. Never returns `null`: return `Optional` or throw. Returns
  DTOs; entities never leave the service layer.
- **Repository**: Spring Data `JpaRepository`; derived queries first, JPQL `@Query` when the name gets
  unreadable, native SQL only for pgvector / full-text.
- **Entity** (`@Entity`): one table; `protected` no-arg constructor; created through a constructor and changed
  through intention-revealing methods (`edit(…)`, `publish()`) — no setters; enums as `@Enumerated(EnumType.STRING)`.
- **DTO**: Java `record`. Requests carry Bean Validation (`@NotBlank`, `@Size`, …).
- **Mapper**: MapStruct `@Mapper`, one per entity, **entity → response only**. The compiler sets
  `mapstruct.defaultComponentModel=spring` and `mapstruct.unmappedTargetPolicy=ERROR`, so a response field nobody
  maps fails the build. Request → entity goes through the entity's constructor or methods, never a mapper.

Design, kept practical:

- One controller per resource; services are split by feature, each named after its main entity. Split a class
  when it does two jobs.
- External systems sit behind an interface too (`AiClient`, `JiraClient`, mail).
- Not used here: abstract base services, generic CRUD superclasses, separate domain models beside the JPA
  entities, one class per use case.

## 4. Spring and configuration

- Constructor injection, `private final` fields; no field `@Autowired`. No Lombok.
- Config through `@ConfigurationProperties` records or `@Value("${…}")`.
- Config files hold `${VAR}` placeholders. Secrets, credentials, hosts and URLs have **no** default — they come
  only from `.env` (git-ignored; Spring reads it via `spring.config.import`) or the server's environment.
  Non-sensitive values (a port, a feature flag) may carry a default: `${SERVER_PORT:8080}`. `.env.example`
  lists every name with no real value.
- `spring.jpa.open-in-view=false`; avoid N+1 with fetch joins or `@EntityGraph`.
- No outbound HTTP call (Jira, AI, SMTP) inside a `@Transactional` method — call it before or after the transaction.
- Text sent to an external AI service goes through PII masking first.
- Time is `Instant` (UTC) in code, `TIMESTAMPTZ` in the database.

## 5. REST, errors, security

- Status codes: 200 read/update, 201 create, 204 delete, 400 validation, 401/403 auth, 404 not found,
  409 business-rule conflict.
- Errors are thrown, not returned: `NotFoundException` → 404, `BusinessRuleException` → 409, validation → 400,
  all rendered by `common/exception/GlobalExceptionHandler` as RFC 9457 `ProblemDetail` with a `code` property
  holding the SRS message code (MSGxx). No try/catch in controllers; never invent message text.
- Base exceptions live in `common/exception/` and carry the MSG code (`new NotFoundException("MSG12", …)`).
  A module adds its own exception in `<module>/exception/` only when the name tells the reader something
  (`SolutionNotFoundException extends NotFoundException`); it extends a base, so the one global handler maps it
  to its status — no `@ExceptionHandler` in a module. Exceptions are unchecked (`RuntimeException`).
- Paging: lists that can grow take `page` (from 0), `size`, `sort` as a `Pageable`; the service returns
  `Page<XxxResponse>`, the controller returns `ResponseEntity<PagedModel<XxxResponse>>` (`new PagedModel<>(page)`)
  — `{content, page: {size, number, totalElements, totalPages}}`.
- Every endpoint declares its allowed roles with `@PreAuthorize` (role names as constants); the security config
  also requires authentication for any other request, so a missing annotation never opens an endpoint.
- Audit: approve, reject, request changes, publish/unpublish, import, delete, role or permission change, and Jira
  configuration change each record an audit event (actor, action, resource) through one `AuditService.record(…)`
  call, in the same transaction, on success. `audit_event` rows are never updated or deleted.

## 6. API documentation (springdoc)

The YAML contract `frontend/openapi/tsolve-api.yaml` is the source of truth; controllers implement it. An API
change updates the contract first, then the code. On the controller, keep annotations minimal:

- `@Tag(name)` on the controller class.
- `@Operation(operationId, summary)` on each endpoint — `operationId` the same as in the contract.
- `@ApiResponse` only for the error codes the endpoint can return (400/403/404/409) — not for 200/201.
- `@Schema(description, example)` only on a DTO field whose meaning is not obvious. Required, length and range
  come from Bean Validation; enum values are listed automatically.
- The security scheme is declared once in an `OpenAPI` bean in `common/config`.
- Swagger UI is on by default; production turns it off with `SPRINGDOC_SWAGGER_UI_ENABLED=false`.

## 7. Database

- PostgreSQL 16 + pgvector, one schema, owned by Liquibase; Hibernate only validates (`ddl-auto: validate`).
- `db/changelog/db.changelog-master.xml` includes formatted-SQL changesets in `db/changelog/changes/NNN-<slug>.sql`.
- Every schema change is a new changeset; never edit an applied one. One logical change per changeset. A mistake
  is fixed forward with a new changeset; a dev database can be reset.
- Foreign keys and the indexes queries need are declared in the changeset.
- Every table follows the tenant-table convention of the `platform` spec (AC-001.8): a plural name;
  `id UUID PRIMARY KEY DEFAULT gen_random_uuid()` (a join table: the composite key of its two references, no `id`);
  `workspace_id UUID NOT NULL REFERENCES workspaces (id)` with an index whose first column is `workspace_id`;
  every uniqueness rule that holds within a Workspace as a unique constraint that includes `workspace_id`;
  `created_at` and `updated_at` as `TIMESTAMPTZ NOT NULL` (an append-only table has no `updated_at`); row-level
  security enabled and forced, with the platform's tenant policy. Only the tables on the platform's allow-lists
  are exempt, and the legacy list only shrinks: the convention guard test fails `mvn verify` otherwise.
- Entities of tenant tables extend `TenantEntity` (`vn.tsolve.common.tenant`) and map the key as
  `@Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;`.

## 8. Tests

- JUnit 5 + AssertJ (`spring-boot-starter-test`). Services and entity rules are unit-tested without Spring,
  collaborators mocked with Mockito.
- `@WebMvcTest` only where it adds something: access denied (403), validation (400), error mapping — one or two
  cases per resource.
- No `@DataJpaTest` for now (there is no in-memory database; pgvector needs a real PostgreSQL).
- Every acceptance criterion has at least one test whose name cites its id; every fixed bug has one that cites
  the bug id (§10).
- `mvn verify` passes before a task is done.

## 9. Code style

- Palantir Java Format (4 spaces, 120 columns) through Spotless with `ratchetFrom origin/main` — only files
  changed on your branch are checked. `mvn spotless:apply` before committing; `mvn spotless:check` must pass.
- No wildcard imports, no unused code, no `System.out`; log with SLF4J and never log secrets, ticket content or
  personal data.
- Comments and Javadoc only where something is not obvious, and they say *why*, not *what*.

## 10. Bugs

Every bug found — in testing, review, a demo or production — is logged in [`docs/bug-log.md`](../docs/bug-log.md)
(the steps are at the top of that file). The test report takes its defect list from that table.

- Log it when you find it, even if you do not fix it now. Name who found it and its **Owner** (who fixes it,
  normally the developer of that module) by full name.
- The owner fixes it test-first: a test that fails before the fix, named with the bug id (`keepsFirstImport_BUG_004`).
- The fix PR updates the bug's row (status, PR, test) in the same PR.

## Notes for AI agents

- Load this file before writing backend code; it overrides general Spring habits.
- If a task needs something these rules forbid (a new dependency, a change in `common/`, a schema edit), stop
  and say so instead of working around it.
- Business words, MSG codes and texts come from the SRS. If the task does not give them and they are not in the
  code or the contract, **ask** — do not invent.
