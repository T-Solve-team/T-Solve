---
module: platform
date: 2026-10-25
iteration: 1
---

# Design: platform — iteration 1 (to 30/10)

Master: `artifacts/04-plans/platform/design.md` (full Data Model, Key Decisions, File Structure, Edge Cases, Risk
Review, Builder Check). This file keeps only what Tasks 1–5, 8–13 and 15 need.

## Scope

The tenant layer for Workspace-site requests (STORY-001…004, STORY-002's resolver), file storage (STORY-007), the
local stack (STORY-008), Caddy and certificates (STORY-009), staging with CI/CD (STORY-010) and the Workspaces-only
seed that the smoke test needs (STORY-012, AC-012.1, AC-012.3–AC-012.5; decision P7). The Forge token, the Jira App
resolver, the AI VPS and the seed's module data are iteration 2.

## Decisions used in this iteration

| Decision | Choice |
|----------|--------|
| P1 | Tasks 2–5 wait on Trần Hải Anh's answer about who writes the `workspaces` reshape changeset; platform offers a draft |
| P2 | `backend/pom.xml`, `application*.yml`, `backend/.dockerignore` are in platform's `paths` (be-foundation owns them) |
| P4 | `TenantAwareJpaTransactionManager.doBegin` → `set_config('app.workspace_id', ?, true)` |
| P5 | `runIn` runs the action in a new transaction (`REQUIRES_NEW`) |
| P6 | Roles and default privileges in `infra/postgres/init/10-tsolve-roles.sh`, also used by Testcontainers; no GRANT in changesets |
| P7 | Seed runner (Workspaces only) runs on staging after each deploy |
| P9 | `tsolve-caddy` image built in CI |
| Q12 | Cloudflare DNS; DNS-01 wildcards `*.tsolve.io.vn` (+ apex) and `*.staging.tsolve.io.vn`; token in the edge `.env` |
| X2, U3 | Join tables: composite key with `workspace_id`; append-only: no `updated_at`; ids by `@GeneratedValue(UUID)` + `DEFAULT gen_random_uuid()` |
| Planning | `ThreadLocal` binding; no resolver cache; placeholder tenant id + `@PrePersist` guard; `/actuator/health` passes every host; migrations at web startup and as a CD step; staging basic auth except `api.`; GHCR under the repository owner |

## Data model in this iteration

`workspaces`, `jira_sites`, the tenant-table template, the two allow-lists, the database roles, the storage layout
and the seed file — all as in the master's Data Model.

## Open before coding

- Spec questions at AC-010.6 (a Workspace named `staging` on production) and AC-012.5 (seed re-runs after S3); the
  user re-approves the spec after answering them.
- Trần Hải Anh's answer on the reshape changeset (P1) for Tasks 2–5.
- Settled by the builder check (master, Builder Check): AC-002.19 for health and API docs, guard rules for join and
  append-only tables, temp files outside Workspace folders, the smoke test's dependency on be-foundation Task 2.

## Edge cases covered here

Master rows 1–14, 24–34, 37, 38, 40–42 (rows 15–23, 35, 36 and 39 are iteration 2).


## KAN-107 SQL Workspace foundation — draft, 03/11/2026

Spec: `artifacts/03-modules/platform/spec.md`, STORY-001 and STORY-002.
This amendment takes precedence over the old pool-model design for this delivery only.
The old tasks remain historical/planned work; they must not introduce UUID tables, RLS or a
default database fallback into this slice. This amendment does not complete the platform module.

### Architecture Overview

The existing primary DataSource, JPA, transaction manager and Liquibase chain continue to serve
legacy modules. A separately qualified catalog DataSource reads `tenant_workspace`. A separately
qualified SQL tenant DataSource obtains the Workspace only from `TenantContext` and obtains
connections only from that Workspace's pool. User List is the first consumer. It never falls back
to the catalog or legacy database. Keep the catalog and pool-provider implementation internal to
`platform`; publish only Workspace lookup DTOs/services and the shared tenant interfaces.

The resolver is a servlet filter before Spring Security, initially scoped to `/api/v1/users` and
subpaths. It validates the actual Host against `TSOLVE_BASE_DOMAIN`, normalizes case/port, ignores
forwarding and Workspace-selector headers, refuses unknown/reserved hosts with MSG151 and
Suspended sites with MSG150, binds in a lexical scope, and clears in finally. Health/docs and
other modules retain current behavior. Do not register the filter twice through both servlet and
security registration. Synchronous User List reads require no thread/context propagation.

`TenantContext` exposes Optional<Long>, requireWorkspaceId and runIn. Binding another Workspace
inside an existing binding or transaction fails. runIn verifies catalog existence, allows Suspended
for internal ops as the platform spec requires, restores the binding on exceptions, and starts a
new SQL-tenant transaction only after binding. The web path refuses Suspended before binding.

### Data Model

Version byte-preserving copies of the original SQL under
`backend/src/main/resources/db/changelog/catalog/01_catalog.sql` and
`backend/src/main/resources/db/changelog/tenant/02_tenant.sql`. XML changelogs use sqlFile rather
than modifying or including root user-owned files at runtime. No destructive migration, automatic
legacy conversion or production demo seed. Schema tests verify PostgreSQL metadata, constraints,
implicit Employee, all 23 tenant tables and absence of legacy tables. Existing databases that were
manually initialized must be explicitly adopted by operations in a future migration decision;
do not hide table-exists errors with IF NOT EXISTS or mark a changeset applied automatically.

Catalog migrations use an owner credential. Tenant bootstrap is an explicit owner-run Liquibase
operation, separate from runtime read credentials. Runtime startup uses already provisioned tenant
databases. No HTTP endpoint creates a Workspace. The full provisioning ops script remains KAN-240.

### Key Decisions

| Decision | Choice | Alternatives considered | Rationale |
|----------|--------|-------------------------|-----------|
| Schema authority | Supplied SQL; singular names and Long keys | Previous UUID convention | Explicit user decision; preserve originals |
| Rollout | Qualified SQL path beside legacy path | Migrate all modules now | Explicit user decision; avoid breaking Ticket/Import |
| Passwords | Resolve db_password_ref from server configuration | Store encrypted password in catalog | SQL has a secret reference, no password column |
| Connections | Existing HikariCP, lazy per Workspace, max 5 connections and max 32 pools by configurable defaults | Unbounded pools; fallback DB | Bound resources and fail closed; no new library |
| Cache | Cache pools, not Workspace status; catalog read each request | Cached ACTIVE status | Suspension applies on next request; cap exhaustion returns 500 MSG06 without fallback |
| Transactions | Qualified JdbcTransactionManager on SQL tenant DS | Reuse legacy JPA transaction manager | Avoid accidentally reading/writing the legacy database |
| Host scope | Only user paths for this slice | Global resolver before migration | Necessary foundation now; existing auth/Jira modules migrate separately |

Use Spring JDBC/DataSourceUtils for transaction-bound connections. The custom routing DataSource
requires a binding before opening a connection and has no default target. Spring's routing API can
otherwise fall back to a default, including on a null key; tests must prove the absence of fallback.
Reference: https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/jdbc/datasource/lookup/AbstractRoutingDataSource.html
Pool-cap/connection errors produce sanitized 500 MSG06; disabled SQL feature produces 503 MSG06.
Do not log JDBC URLs, usernames, passwords, secret-reference values or user data.

### File Structure

- `backend/src/main/java/vn/tsolve/common/tenant/`: TenantContext, scoped binding and SQL routing DataSource.
- `backend/src/main/java/vn/tsolve/platform/service/`: read-only catalog lookup interface.
- `backend/src/main/java/vn/tsolve/platform/dto/`: public Workspace summary without connection secrets.
- `backend/src/main/java/vn/tsolve/platform/`: internal JDBC catalog repository, pool provider, feature config and host filter.
- `backend/src/main/resources/db/changelog/{catalog,tenant}/`: bootstrap XML and SQL copies.
- `backend/src/main/resources/application.yml`, `.env.example`: feature switch, catalog config, pool limits; placeholders for secrets.
- Tests mirror `common/tenant` and `platform`, plus SQL schema and cross-database privilege ITs.

### External Dependencies

Reuse Spring JDBC, HikariCP, Liquibase, PostgreSQL and Testcontainers already present. No new library.
No calls to external services. A later identity adapter must consume Long-based Workspace ids;
do not implement the obsolete UUID TenantScope adapter or silently translate ids in this slice.

### Edge Cases & Risks

Dimensions analyzed: input extremes, timing, scale, state transitions, environment, error cascades,
authorization, integrity, integration and privacy. User-type/business-role cases live in identity.
Payment/deletion/retention changes and mobile UI changes are inapplicable to this infrastructure slice.

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| P1 | STORY-002 | Authorization | Forged forwarding/Workspace header, foreign or reserved Host | Critical | Actual Host only; no fallback/tenant connection | SQL-P3 |
| P2 | STORY-002 | Timing | Request throws; next request reuses thread; nested cross-Workspace runIn | Critical | Clear/restore binding; reject switch before connection | SQL-P2/P3 |
| P3 | STORY-002 | Integrity | A and B have identical user ids; runtime A tries catalog/B | Critical | Separate database results and PostgreSQL connection denial | SQL-P4 |
| P4 | STORY-001 | Integration | Legacy schema accidentally applied to SQL tenant | High | Empty bootstrap/schema check fails; no destructive fix | SQL-P1/P4 |
| P5 | STORY-002 | Scale | Concurrent first-use of same pool, cap reached, shutdown | High | Single pool per id, bounded connections, fail closed, close pools | SQL-P2/P4 |
| P6 | STORY-001 | Error cascades/privacy | Missing secret or DB offline | High | Sanitized coded error, no credential logging or fallback | SQL-P2/P4 |
| P7 | STORY-002 | State transitions | ACTIVE becomes SUSPENDED while pool is cached | High | Next request denied before tenant query | SQL-P3/P4 |
| P8 | STORY-001 | Environment | Existing manually initialized database or incomplete bootstrap | High | Explicit owner bootstrap fails visibly; no automatic adoption | SQL-P1/P4 |

### Risk Review — 03/11/2026 — Verdict: CAUTION

| Persona | Independent finding | Resolution |
|---------|---------------------|------------|
| Architect | Full primary-DS replacement breaks existing mappings and sign-in state | Qualified SQL path; other modules migrate separately |
| Security | A default DS, host spoofing or leaked ThreadLocal exposes another company | No default, Host validation, lexical binding and two-tenant tests |
| Performance | An unbounded pool registry can exhaust PostgreSQL | Configurable hard pool/connection caps and concurrent initialization tests |
| User | API availability is not a working Google sign-in flow | Explicit login dependency and disabled-feature response |
| Devil's advocate | One configured tenant DB would be smaller but cannot prove isolation | Real catalog + two tenant DBs is necessary for authorized scope |

Agreement: all five require explicit rollout boundaries and meaningful isolation evidence.
Conflict: full architecture migration versus small scope; staged migration has the lower immediate
data-loss risk, with no claim that unported modules are isolated. High/Critical mitigations map to
SQL-P1 through SQL-P4. The user resolved the two pre-existing Jira messages separately: malformed URL uses MSG06; a Suspended Workspace in Jira App uses 403 MSG150. No Jira functionality is added to this slice.

### Builder Check

The user resolved unrelated Jira-link/Jira-App error messages as MSG06 and 403 MSG150 respectively; implementation remains in their original tasks. Long-based catalog/context,
separate runtime/owner credentials, SQL bootstrap location, filter scope, transactions and pool
caps are specified above. Internal class names may change without changing contracts. The first
feature checkpoint is after SQL-P4 and a full backend verify, before User List implementation.
