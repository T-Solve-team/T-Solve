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
├── <module>/            one folder per module from module-map.md (e.g. ticket, knowledge, search)
│   ├── controller/      REST endpoints + springdoc annotations
│   ├── service/         <Entity>Service interfaces — what controllers and other modules call
│   │   └── impl/        <Entity>ServiceImpl: business rules, @Transactional boundaries
│   ├── repository/      Spring Data JPA interfaces
│   ├── entity/          JPA entities + enums owned by the module
│   ├── dto/             request/response records
│   └── mapper/          MapStruct mappers entity → response dto
└── common/              config, error handling, shared utilities (no business logic)
```

- Calls go one way: controller → service → repository. A controller never touches a repository.
- Controllers and other modules depend on `service/` interfaces only, never on `service/impl/`.
- A module uses another module only through that module's `service/` interfaces and `dto/` records —
  never its `repository/`, `entity/` or `mapper/`. Both rules are checked in code review.
- An entity refers to another module's entity by its id, never through a JPA association (`@ManyToOne`, …).
- Layers a module does not need are not created.

## Communication Patterns

**Between modules:** in-process calls to the other module's service interfaces (see Backend Code Structure)
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
| Configuration | env vars | Config files hold `${VAR}` placeholders. Secrets, credentials, hosts and URLs have **no** default — they come only from `.env` (git-ignored; Spring reads it via `spring.config.import`) or the server's environment. Non-sensitive values (a port, a feature flag) may carry a default: `${SERVER_PORT:8080}`. `.env.example` lists every name with no real value. |
| Error handling | `common/error` | Domain exceptions → `GlobalExceptionHandler` → RFC 9457 `ProblemDetail` with an SRS `code` (MSGxx) |
| Security | deny by default | Every endpoint declares its roles with `@PreAuthorize`; the backend always re-checks permissions |
| Audit | listed actions | The actions in "REST, errors, security" record an audit event; `audit_event` is never updated or deleted |
| API docs | springdoc | Minimal annotations on controllers; the YAML contract is the source of truth |
| Testing strategy | JUnit 5 | Unit tests per service/entity rule; test names cite the AC id |
| Code style | Spotless | Palantir Java Format, checked on changed files (`mvn spotless:check`) |

## Key Constraints

- No secret or environment-specific value in any committed file.
- The schema is owned by Liquibase; Hibernate only validates (`ddl-auto: validate`).
- Personal data is masked before any text leaves the server for an external AI service.

## Backend Coding Conventions

Standard Java / Spring Boot practice, kept small on purpose. Code that breaks a rule here fails review.

### Naming

| Item | Rule | Example |
|------|------|---------|
| Package | lowercase, singular | `vn.tsolve.knowledge.service` |
| Class / record / enum | PascalCase noun | `Solution`, `SolutionStatus` |
| Layer classes | `<Entity>Controller`, `<Entity>Repository` | `SolutionController` |
| Service | interface `<Entity>Service` + implementation `<Entity>ServiceImpl` in `service/impl/` — always, even with one implementation | `SolutionService`, `SolutionServiceImpl` |
| External system client | interface `<System>Client`, one class per implementation | `AiClient`, `OllamaAiClient` |
| DTO | `<Action><Entity>Request`, `<Entity>Response` | `CreateSolutionRequest`, `SolutionResponse` |
| Mapper | `<Entity>Mapper` in `mapper/` | `SolutionMapper` |
| Exception | `<Reason>Exception` | `NotFoundException` |
| Method / field | camelCase; methods start with a verb | `createDraft`, `reviewDueAt` |
| Constant | `static final` UPPER_SNAKE_CASE | `MAX_IMPORT_SIZE` |
| Boolean | `is` / `has` / `can` prefix | `isPublished()` |
| Table / column | snake_case, singular table | `solution`, `review_due_at` |
| REST path | `/api/v1/<plural-kebab-case>`; actions as sub-resources | `/api/v1/solutions/{id}/transition` |
| operationId | camelCase verb + noun, the same as in the contract | `listReviewQueue` |
| JSON field | camelCase | `externalId` |
| Changeset | file `NNN-<kebab-slug>.sql`, id `NNN-<slug>`, author = module | `002-add-wiki-page.sql` |
| Test | `<Class>Test`; method names the behavior and the AC id | `rejectsEditWhenPublished_AC_003_2` |

### Layers

- **Controller** (`@RestController`): takes the validated input (`@Valid`), calls **one** service method, returns its DTO in a `ResponseEntity` (`ok(dto)`, `status(CREATED).body(dto)`, `noContent().build()`). No business logic, no `@Transactional`, no repository, no mapper.
- **Service** (interface `<Entity>Service` + `@Service` class `service/impl/<Entity>ServiceImpl`; required even with one implementation): the interface declares what callers may use; the impl holds the business rules and transactions — `@Transactional` on writes, `@Transactional(readOnly = true)` on reads. Never returns `null`: return `Optional` or throw. Returns DTOs; entities never leave the service layer.
- **Repository**: Spring Data `JpaRepository`; derived queries first, JPQL `@Query` when the name gets unreadable, native SQL only for pgvector / full-text.
- **Entity** (`@Entity`): one table; `protected` no-arg constructor; created through a constructor and changed through intention-revealing methods (`edit(…)`, `publish()`) — no setters; enums as `@Enumerated(EnumType.STRING)`.
- **DTO**: Java `record`. Requests carry Bean Validation (`@NotBlank`, `@Size`, …).
- **Mapper**: MapStruct `@Mapper`, one per entity, **entity → response only**. The compiler sets `mapstruct.defaultComponentModel=spring` and `mapstruct.unmappedTargetPolicy=ERROR`, so a response field nobody maps fails the build. Request → entity goes through the entity's constructor or methods, never a mapper.

### Design principles (SOLID, kept practical)

- One controller per resource; services are split by feature, each named after its main entity. Split a class when it does two jobs.
- Dependency inversion: every service is an interface with its implementation in `service/impl/`, even when there is only one; callers inject the interface. External systems sit behind an interface too (`AiClient`, `JiraClient`, mail).
- Not used (over-engineering here): abstract base services, generic CRUD superclasses, separate domain models beside the JPA entities, one class per use case.

### API documentation (springdoc)

The YAML contract (`frontend/openapi/tsolve-api.yaml`) is the source of truth; controllers implement it. On the controller, keep annotations minimal:

- `@Tag(name)` on the controller class.
- `@Operation(operationId, summary)` on each endpoint — `operationId` the same as in the contract.
- `@ApiResponse` only for the error codes the endpoint can return (400/403/404/409) — not for 200/201.
- `@Schema(description, example)` only on a DTO field whose meaning is not obvious. Required, length and range come from Bean Validation; enum values are listed automatically.
- The security scheme is declared once in an `OpenAPI` bean in `common/config`.
- Swagger UI is on by default; production turns it off with `SPRINGDOC_SWAGGER_UI_ENABLED=false`.

### Spring

- Constructor injection, `private final` fields; no field `@Autowired`.
- No Lombok.
- Config through `@ConfigurationProperties` records or `@Value("${…}")`; never hard-code a secret, URL, or environment-specific value (see Configuration above).
- `spring.jpa.open-in-view=false`; avoid N+1 with fetch joins or `@EntityGraph`.
- No outbound HTTP call (Jira, AI, SMTP) inside a `@Transactional` method — call it before or after the transaction.
- Text sent to an external AI service goes through PII masking first.
- Time is `Instant` (UTC) in code, `TIMESTAMPTZ` in the database.

### REST, errors, security

- An API change updates the YAML contract first, then the code.
- Status codes: 200 read/update, 201 create, 204 delete, 400 validation, 401/403 auth, 404 not found, 409 business-rule conflict.
- Errors are thrown, not returned: `NotFoundException` → 404, `BusinessRuleException` → 409, validation → 400, all rendered by `common/error/GlobalExceptionHandler` as RFC 9457 `ProblemDetail` with a `code` property holding the SRS message code (MSGxx). No try/catch in controllers; never invent message text.
- Paging: lists that can grow take `page` (from 0), `size`, `sort` as a `Pageable`; the service returns `Page<XxxResponse>`, the controller returns `ResponseEntity<PagedModel<XxxResponse>>` (`new PagedModel<>(page)`) — `{content, page: {size, number, totalElements, totalPages}}`.
- Every endpoint declares its allowed roles with `@PreAuthorize` (role names as constants); the security config also requires authentication for any other request, so a missing annotation never opens an endpoint.
- Audit: approve, reject, request changes, publish/unpublish, import, delete, role or permission change, and Jira configuration change each record an audit event (actor, action, resource) through one `AuditService.record(…)` call, in the same transaction, on success.

### Code style

- Palantir Java Format (4 spaces, 120 columns) through Spotless with `ratchetFrom origin/main` — only files changed on your branch are checked. `mvn spotless:apply` before committing; `mvn spotless:check` must pass.
- No wildcard imports, no unused code, no `System.out`; log with SLF4J and never log secrets or personal data.
- Comments and Javadoc only where something is not obvious, and they say *why*, not *what*.

### Database

- Every schema change is a new changeset; never edit an applied one. One logical change per changeset. A mistake is fixed forward with a new changeset; a dev database can be reset.
- Foreign keys and the indexes queries need are declared in the changeset.

### Tests

- JUnit 5 + AssertJ (`spring-boot-starter-test`). Services and entity rules unit-tested without Spring, collaborators mocked with Mockito.
- `@WebMvcTest` only where it adds something: access denied (403), validation (400), error mapping — one or two cases per resource.
- No `@DataJpaTest` for now (there is no in-memory database; pgvector needs a real PostgreSQL).
- Every acceptance criterion has at least one test whose name cites its id.
- `mvn verify` passes before a task is done.
