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
│   ├── controller/      REST endpoints + request/response mapping only
│   ├── service/         business rules, @Transactional boundaries
│   ├── repository/      Spring Data JPA interfaces
│   ├── entity/          JPA entities + enums owned by the module
│   ├── dto/             request/response records
│   └── mapper/          MapStruct mappers entity ↔ dto
└── common/              config, error handling, shared utilities (no business logic)
```

- Calls go one way: controller → service → repository. A controller never touches a repository.
- A module uses another module only through that module's `service/` classes and `dto/` records —
  never its `repository/` or `entity/`.
- Layers a module does not need are not created.

## Communication Patterns

**Between modules:** in-process service calls (see Backend Code Structure)
**Between frontend and backend:** REST JSON under `/api/**`

## Shared Infrastructure

| Resource | Shared or Per-Module | Detail |
|----------|---------------------|--------|
| Database | shared | PostgreSQL 16 + pgvector, one schema |
| Schema migrations | shared | Liquibase: `db/changelog/db.changelog-master.xml` includes formatted-SQL changesets in `db/changelog/changes/NNN-<slug>.sql`; never edit an applied changeset, add a new one |
| Auth/Identity | TBD | Google OIDC (decompose) |
| Logging | shared | SLF4J to stdout |

## Repo Structure

**Chosen:** monorepo — `backend/` (Spring Boot), `frontend/` (React), `harnessclaude/` (this workspace).

## Deployment Topology

**Target:** containers · **Provider:** VPS (Vietnam) · **Orchestration:** Docker Compose + Caddy

## Cross-Cutting Concerns

| Concern | Decision | Detail |
|---------|----------|--------|
| Configuration | env vars only | Config files (`application.yml`, `docker-compose.yml`) hold only `${VAR}` placeholders — no real value, no default. Values live in `.env` (git-ignored; Spring reads it via `spring.config.import`) or the server's environment. `.env.example` lists the names with placeholders only. |
| Error handling | `common/error` | Domain exceptions → `GlobalExceptionHandler` → JSON `{timestamp, status, message}` |
| Testing strategy | JUnit 5 | Unit tests per service/entity rule; test names cite the AC id |
| Code style | Spotless | Palantir Java Format, checked on changed files (`mvn spotless:check`) |

## Key Constraints

- No secret or environment-specific value in any committed file.
- The schema is owned by Liquibase; Hibernate only validates (`ddl-auto: validate`).

## Backend Coding Conventions

Standard Java / Spring Boot practice. Code that breaks a rule here fails review.

### Naming

| Item | Rule | Example |
|------|------|---------|
| Package | lowercase, singular | `vn.tsolve.knowledge.service` |
| Class / record / enum | PascalCase noun | `Solution`, `SolutionStatus` |
| Layer classes | `<Entity>Controller`, `<Entity>Service`, `<Entity>Repository` | `SolutionService` |
| DTO | `<Action><Entity>Request`, `<Entity>Response` | `CreateSolutionRequest`, `SolutionResponse` |
| Mapper | `<Entity>Mapper` in `mapper/` | `SolutionMapper` |
| Exception | `<Reason>Exception` | `NotFoundException` |
| Method / field | camelCase; methods start with a verb | `createDraft`, `reviewDueAt` |
| Constant | `static final` UPPER_SNAKE_CASE | `MAX_IMPORT_SIZE` |
| Boolean | `is` / `has` / `can` prefix | `isPublished()` |
| Table / column | snake_case, singular table | `solution`, `review_due_at` |
| REST path | `/api/<plural-kebab-case>`; actions as sub-resources | `/api/solutions/{id}/transition` |
| JSON field | camelCase | `externalId` |
| Changeset | file `NNN-<kebab-slug>.sql`, id `NNN-<slug>`, author = module | `002-add-wiki-page.sql` |
| Test | `<Class>Test`; method names the behavior and the AC id | `rejectsEditWhenPublished_AC_003_2` |

### Layers

- **Controller** (`@RestController`): `@Valid` input, call one service method, return a response DTO. No business logic, no `@Transactional`, no repository.
- **Service** (`@Service`): business rules and transactions — `@Transactional` on writes, `@Transactional(readOnly = true)` on reads. Never returns `null`: return `Optional` or throw.
- **Repository**: Spring Data `JpaRepository`; derived queries first, JPQL `@Query` when the name gets unreadable, native SQL only for pgvector / full-text.
- **Entity** (`@Entity`): one table; `protected` no-arg constructor; state changes through intention-revealing methods (`publish()`, not `setStatus()`); enums as `@Enumerated(EnumType.STRING)`.
- **DTO**: Java `record`. Requests carry Bean Validation (`@NotBlank`, `@Size`, …).
- **Mapper**: MapStruct `@Mapper` interface, one per entity. The Maven compiler sets `mapstruct.defaultComponentModel=spring` (inject mappers like any bean) and `mapstruct.unmappedTargetPolicy=ERROR` (an unmapped field fails the build). Request → entity uses `@MappingTarget` updates or a constructor; fields guarded by a business rule (status, ids, audit timestamps) are `@Mapping(target = …, ignore = true)` and changed only by entity methods. No hand-written mapping code, no logic in mappers beyond field mapping.
- Services call mappers and return DTOs; entities never leave the service layer.

### Spring

- Constructor injection, `private final` fields; no field `@Autowired`.
- No Lombok.
- Config through `@ConfigurationProperties` records or `@Value("${…}")`; never hard-code a URL, credential, or environment-specific value (see Configuration above).
- `spring.jpa.open-in-view=false`; avoid N+1 with fetch joins or `@EntityGraph`.
- Time is `Instant` (UTC) in code, `TIMESTAMPTZ` in the database.

### REST and errors

- Status codes: 200 read/update, 201 create, 204 delete, 400 validation, 401/403 auth, 404 not found, 409 business-rule conflict.
- Throw, don't return errors: `NotFoundException` → 404, `BusinessRuleException` → 409, validation → 400, all rendered by `common/error/GlobalExceptionHandler`. No try/catch in controllers.
- User-facing message codes (MSGxx) come from the SRS; never invent message text.
- Lists that can grow take `Pageable` and return a page.

### Code style

- Palantir Java Format (4 spaces, 120 columns) through Spotless with `ratchetFrom origin/main` — only changed files are checked. `mvn spotless:apply` before committing; `mvn spotless:check` must pass.
- No wildcard imports, no unused code, no `System.out`; log with SLF4J and never log secrets or personal data.
- Javadoc on public classes; comments say *why*, not *what*.

### Database

- Every schema change is a new changeset; never edit an applied one. One logical change per changeset, with a rollback unless it is a plain create.
- Foreign keys and the indexes queries need are declared in the changeset.

### Tests

- JUnit 5 + AssertJ (`spring-boot-starter-test`). Services and entity rules unit-tested without Spring; `@WebMvcTest` for controller contracts; `@DataJpaTest` only for custom queries.
- Every acceptance criterion has at least one test whose name cites its id.
- `mvn verify` and `mvn spotless:check` pass before a task is done.
