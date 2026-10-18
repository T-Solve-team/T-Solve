---
project: T-Solve
date: 2026-10-19
---

# Architecture Overview

System-level decisions that apply across all modules. Rows marked TBD are settled in the decompose phase.
Every backend design and task follows "Backend Coding Conventions" at the end of this file.

## Deployment Model

**Chosen:** modular monolith (one Spring Boot 3 app, Java 21, Maven)
**Rationale:** one team, one deployable on one VPS; module boundaries live in packages, not services.

## Backend Code Structure

**Chosen:** layered architecture, folders split by module (package-by-module, then by layer).

```
backend/src/main/java/vn/tsolve/
├── <module>/              one folder per module from module-map.md (e.g. ticket, knowledge, search)
│   ├── controller/        <Entity>Api (interface: mappings + springdoc) and <Entity>Controller (implements it)
│   ├── service/           <Entity>Service interfaces — what other layers and modules call
│   │   └── impl/          <Entity>ServiceImpl: business rules, @Transactional boundaries
│   ├── repository/        Spring Data JPA interfaces
│   ├── entity/            JPA entities + enums owned by the module
│   ├── dto/               request/response records
│   └── mapper/            MapStruct mappers entity ↔ dto
└── common/                config, error handling, shared utilities (no business logic)
```

- Calls go one way: controller → service → repository. A controller never touches a repository.
- Controllers and other modules depend on `service/` interfaces only — never on `service/impl/`,
  `repository/` or `entity/` of another module. They exchange `dto/` records.
- Layers a module does not need are not created.

## Communication Patterns

**Between modules:** in-process calls to service interfaces (see Backend Code Structure)
**Between frontend and backend:** REST JSON under `/api/v1/**`; the agreed contract is `frontend/openapi/tsolve-api.yaml`

## Shared Infrastructure

| Resource | Shared or Per-Module | Detail |
|----------|---------------------|--------|
| Database | shared | PostgreSQL 16 + pgvector, one schema |
| Schema migrations | shared | Liquibase: `db/changelog/db.changelog-master.xml` includes formatted-SQL changesets in `db/changelog/changes/NNN-<slug>.sql`; never edit an applied changeset, add a new one |
| Auth/Identity | TBD | Google OIDC (decompose) |
| Logging | shared | SLF4J to stdout |
| API docs | shared | springdoc-openapi: `/v3/api-docs`, Swagger UI `/swagger-ui.html` |

## Repo Structure

**Chosen:** monorepo — `backend/` (Spring Boot), `frontend/` (React), `harnessclaude/` (this workspace).

## Deployment Topology

**Target:** containers · **Provider:** VPS (Vietnam) · **Orchestration:** Docker Compose + Caddy

## Cross-Cutting Concerns

| Concern | Decision | Detail |
|---------|----------|--------|
| Configuration | env vars only | Config files (`application.yml`, `docker-compose.yml`) hold only `${VAR}` placeholders — no real value, no default. Values live in `.env` (git-ignored; Spring reads it via `spring.config.import`) or the server's environment. `.env.example` lists the names with placeholders only. |
| Error handling | `common/error` | Domain exceptions → `GlobalExceptionHandler` → RFC 9457 `ProblemDetail` with an SRS `code` (MSGxx) |
| Security | deny by default | Every endpoint declares its roles with `@PreAuthorize`; the backend always re-checks permissions |
| Audit | append-only | Every write action records an audit event (`audit_event` is never updated or deleted) |
| API docs | springdoc | Every endpoint and DTO fully documented (see API documentation) |
| Testing strategy | JUnit 5 | Unit tests per service/entity rule; test names cite the AC id |
| Code style | Spotless | Palantir Java Format, checked on changed files (`mvn spotless:check`) |

## Key Constraints

- No secret or environment-specific value in any committed file.
- The schema is owned by Liquibase; Hibernate only validates (`ddl-auto: validate`).
- Personal data is masked before any text leaves the server for an external AI service.

## Backend Coding Conventions

Standard Java / Spring Boot practice. Code that breaks a rule here fails review.

### Naming

| Item | Rule | Example |
|------|------|---------|
| Package | lowercase, singular | `vn.tsolve.knowledge.service` |
| Class / record / enum | PascalCase noun | `Solution`, `SolutionStatus` |
| API interface / controller | `<Entity>Api`, `<Entity>Controller implements <Entity>Api` | `SolutionApi`, `SolutionController` |
| Service | interface `<Entity>Service`, implementation `<Entity>ServiceImpl` in `service/impl/` | `SolutionService`, `SolutionServiceImpl` |
| Repository | `<Entity>Repository` | `SolutionRepository` |
| DTO | `<Action><Entity>Request`, `<Entity>Response` | `CreateSolutionRequest`, `SolutionResponse` |
| Mapper | `<Entity>Mapper` in `mapper/` | `SolutionMapper` |
| Exception | `<Reason>Exception` | `NotFoundException` |
| Method / field | camelCase; methods start with a verb | `createDraft`, `reviewDueAt` |
| Constant | `static final` UPPER_SNAKE_CASE | `MAX_IMPORT_SIZE` |
| Boolean | `is` / `has` / `can` prefix | `isPublished()` |
| Table / column | snake_case, singular table | `solution`, `review_due_at` |
| REST path | `/api/v1/<plural-kebab-case>`; actions as sub-resources | `/api/v1/solutions/{id}/transition` |
| operationId | camelCase verb + noun, unique, same as in the contract | `listReviewQueue` |
| JSON field | camelCase | `externalId` |
| Changeset | file `NNN-<kebab-slug>.sql`, id `NNN-<slug>`, author = module | `002-add-wiki-page.sql` |
| Test | `<Class>Test`; method names the behavior and the AC id | `rejectsEditWhenPublished_AC_003_2` |

### Layers

- **Api interface** (`<Entity>Api`): request mappings and every springdoc annotation — nothing else.
- **Controller** (`@RestController`, implements `<Entity>Api`): takes the validated input, calls **one** service method, returns its DTO. No business logic, no branching on business state, no `@Transactional`, no repository, no mapper. Injects service **interfaces** only.
- **Service** (interface + `impl/`): the impl holds the business rules and transactions — `@Transactional` on writes, `@Transactional(readOnly = true)` on reads. Never returns `null`: return `Optional` or throw. Returns DTOs (through the mapper); entities never leave the service layer.
- **Repository**: Spring Data `JpaRepository`; derived queries first, JPQL `@Query` when the name gets unreadable, native SQL only for pgvector / full-text.
- **Entity** (`@Entity`): one table; `protected` no-arg constructor; state changes through intention-revealing methods (`publish()`, not `setStatus()`); enums as `@Enumerated(EnumType.STRING)`.
- **DTO**: Java `record`. Requests carry Bean Validation (`@NotBlank`, `@Size`, …).
- **Mapper**: MapStruct `@Mapper` interface, one per entity. The Maven compiler sets `mapstruct.defaultComponentModel=spring` (inject mappers like any bean) and `mapstruct.unmappedTargetPolicy=ERROR` (an unmapped field fails the build). Request → entity uses `@MappingTarget` updates or a constructor; fields guarded by a business rule (status, ids, audit timestamps) are `@Mapping(target = …, ignore = true)` and changed only by entity methods. No hand-written mapping code, no logic in mappers beyond field mapping.

### SOLID

Applied where it removes real coupling — not as extra layers.

- **S — single responsibility:** one Api/controller per resource, one service per aggregate, mappers only map. A class that grows past one clear job is split by use case.
- **O — open/closed:** variation goes behind an interface with one implementation per variant (e.g. an `AiClient` with Ollama and OpenRouter implementations), not `if/else` or `switch` on a type flag.
- **L — Liskov:** an implementation keeps its interface's contract — same inputs accepted, same exceptions, no `UnsupportedOperationException`.
- **I — interface segregation:** a service interface exposes only what its callers use; no catch-all "manager" interfaces.
- **D — dependency inversion:** controllers and other modules depend on service interfaces; implementations depend on repository and client interfaces; everything is injected through the constructor.
- Not used (over-engineering here): abstract base services, generic CRUD superclasses, separate domain models beside the JPA entities, one class per use case.

### API documentation (springdoc)

All annotations live on the `<Entity>Api` interface and the DTO records. The more precise, the better — a reader must be able to call the endpoint from Swagger UI alone.

- `@Tag(name, description)` on every Api interface.
- `@Operation(operationId, summary, description)` on every method; `summary` is one line, `description` states the business rule, the roles allowed, and the MSG codes the endpoint can return.
- `@ApiResponses` lists **every** status the endpoint returns — success and each error (400, 401, 403, 404, 409) — with its schema (`ProblemDetail` for errors).
- `@Parameter(description, example, required)` on every path, query, and paging parameter; `@io.swagger.v3.oas.annotations.parameters.RequestBody(description, required)` on every body.
- `@Schema(description, example, requiredMode, …)` on every DTO record and **every** field, with constraints (`maxLength`, `minimum`, `format`) matching its Bean Validation; enums list and explain each value.
- `@SecurityRequirement` on endpoints that need a session.
- Swagger UI is on by default for development; production turns it off with the env var `SPRINGDOC_SWAGGER_UI_ENABLED=false`.

### Spring

- Constructor injection, `private final` fields; no field `@Autowired`.
- No Lombok.
- Config through `@ConfigurationProperties` records or `@Value("${…}")`; never hard-code a URL, credential, or environment-specific value (see Configuration above).
- `spring.jpa.open-in-view=false`; avoid N+1 with fetch joins or `@EntityGraph`.
- No outbound HTTP call (Jira, AI, SMTP) inside a `@Transactional` method — call it before or after the transaction.
- Text sent to an external AI service goes through PII masking first.
- Time is `Instant` (UTC) in code, `TIMESTAMPTZ` in the database.

### REST, errors, security

- The contract in `frontend/openapi/tsolve-api.yaml` comes first: an API change updates it before the code, and the springdoc output (`/v3/api-docs`) must match it.
- Status codes: 200 read/update, 201 create, 204 delete, 400 validation, 401/403 auth, 404 not found, 409 business-rule conflict.
- Errors are thrown, not returned: `NotFoundException` → 404, `BusinessRuleException` → 409, validation → 400, all rendered by `common/error/GlobalExceptionHandler` as RFC 9457 `ProblemDetail` with a `code` property holding the SRS message code (MSGxx). No try/catch in controllers; never invent message text.
- Paging: lists that can grow take `page` (from 0), `size`, `sort` as a `Pageable` and return Spring Data's `PagedModel` — `{content, page: {size, number, totalElements, totalPages}}`.
- Every endpoint declares its allowed roles with `@PreAuthorize`; an endpoint without one is a review failure. The backend re-checks permissions even when the UI hides an action.
- Every write action records an audit event (actor, action, resource, result).

### Code style

- Palantir Java Format (4 spaces, 120 columns) through Spotless with `ratchetFrom origin/main` — only changed files are checked. `mvn spotless:apply` before committing; `mvn spotless:check` must pass.
- No wildcard imports, no unused code, no `System.out`; log with SLF4J and never log secrets or personal data.
- Javadoc on public classes; comments say *why*, not *what*.

### Database

- Every schema change is a new changeset; never edit an applied one. One logical change per changeset, with a rollback unless it is a plain create.
- Foreign keys and the indexes queries need are declared in the changeset.

### Tests

- JUnit 5 + AssertJ (`spring-boot-starter-test`). Service implementations and entity rules unit-tested without Spring (service dependencies mocked through their interfaces); `@WebMvcTest` for controller contracts; `@DataJpaTest` only for custom queries.
- Every acceptance criterion has at least one test whose name cites its id.
- `mvn verify` and `mvn spotless:check` pass before a task is done.
