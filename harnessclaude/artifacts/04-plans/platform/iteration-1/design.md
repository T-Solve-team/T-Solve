---
module: platform
date: 2026-11-04
iteration: 1
---

# Design: platform — iteration 1 (local stack, Workspaces and the staging demo of 15/11; DevOps Phase A)

Master: `artifacts/04-plans/platform/design.md` (Data Model, Key Decisions, File Structure, Edge Cases, Traceability).
This file keeps only what Tasks 1 – 22 of `iteration-1/tasks.md` need. The 4/11 multi-tenant HLA (database per
Workspace, Registry, row guard, Garage, Redis, STORY-013, STORY-014) is in the master and in those tasks.

## Scope

All the tenant layer and the infrastructure for the demo of 15/11:
- the one Workspace binding, the Registry, the row guard, routing and the Tenant Resolver of the web app (STORY-001,
  STORY-002, STORY-004; Tasks 3 – 6, 6a, 10), in the wave order of 22/11 (below and in `tasks.md`);
- provisioning, the invitation queue and the ops script (STORY-013; Tasks 7 – 9);
- the local stack and CI (STORY-008; Tasks 1, 2, 11);
- the demo Workspaces through provisioning (STORY-012, AC-012.1, AC-012.3 – AC-012.5; Task 12);
- staging, Caddy and CD (STORY-009, STORY-010, and the edge limits AC-014.9 – AC-014.11 of STORY-014; Tasks 13 – 15);
- file storage and its signed-URL route (STORY-007; Tasks 16, 17);
- the Jira site link, the Forge Invocation Token and the Tenant Resolver of the Jira App (STORY-004, STORY-005,
  STORY-006; Tasks 18 – 21);
- the AI VPS (STORY-011; Task 22).

Iteration 2 holds only the seed's module data and the reset (Task 23) and the rest of STORY-014 (Tasks 24 – 26).

## Decisions used in this iteration

| Decision | Choice |
|----------|--------|
| P1, P2 | `be-foundation` owns the tenant master and the shared files; platform's changesets are added after changeset 001 |
| P3 | `spring-security-oauth2-jose` (Nimbus) for the Forge Invocation Token, version from the Spring Boot BOM |
| P5 | `runIn` binds only and opens no transaction; transactions stay with the services' `@Transactional` (changed for Task 6, Hiếu, Q1 = A) |
| P7 | The seed runner creates Workspaces through provisioning and runs on staging after each deploy |
| P9 | `tsolve-caddy` image built in CI (Cloudflare DNS module, rate-limit module) |
| P11, Q12, Q13 | Ollama native on the AI VPS behind WireGuard; Cloudflare DNS-01 wildcards for `*.tsolve.io.vn` and `*.staging.tsolve.io.vn` |
| Q9 | The Jira site URL is normalised and checked through `TenantInfoClient` (public address, `https`, no redirects) |
| R1 – R3, R15 | Registry through `JdbcClient`; routing `DataSource` with lazy per-Workspace pools; `app.tenant_id` set from the bound id |
| R4 – R6 | One `runAlways`, `runOrder="last"` XML changeSet (it runs a SQL file) installs policy `tenant_guard`; owner role and template database; per-Workspace runtime user |
| R7, R8 | Database and user named `tsolve_<env>_ws_<32 hex>`; AES-256-GCM password storage |
| R9, R10 | Provisioning order of AC-013.1; `InvitationJobQueue` is a port with a minimal JobRunr implementation in `platform` (user, 4/11) |
| R11 – R13 | Signed URLs on the Workspace's own site; objects stored as `application/octet-stream`; in-memory upload |
| R14 | The web process migrates the Registry and every Workspace database at startup; CD also runs a `migrate` step |
| R16 | One Redis container with persistence and `noeviction` |
| R17 – R20 | Task 4: bluefin adopted by an idempotent script (no creation, AC-001.19 untouched); its Jira site copied from the tenant `workspace` row or left empty; identity, jira-app and worker lookups read `WorkspaceCatalog` outside `dev` (no cache, host to site name through `TSOLVE_SITE_ORIGIN_TEMPLATE`); `claimEmail` has no caller until identity Task 14 |
| W1, W2 | AWS SDK v2 `s3`; `caddy-ratelimit` in the custom Caddy image |
| Forge | `ForgeSecurityConfig` `@Order(1)` on the `api.` host; stateless, no CSRF, no cookie; the claim paths of `cloudId`, principal and lifecycle `eventType` are confirmed against Atlassian's Forge Remote documentation first (Task 19) and recorded in the master |
| Planning | `ThreadLocal` binding; no resolver cache; `/actuator/health` passes every host; staging basic auth except `api.`; GHCR under the repository owner |

## Wave order and Task 6a (Hiếu, 22/11; KAN-384; split into 6a-1 and 6a-2)

Multi-tenant support comes first and is tried on staging (site `bluefin`, domain `bluefin.tsolve.io.vn`) instead of the
local stack; Tasks 11, 14 and the GHCR part of Task 2 wait. The waves, with what each item lets you see, are in the
order rule of `tasks.md`: wave 1 = Task 6a-1 then 6a-2 (KAN-384, KAN-385) ∥ Task 3; wave 2 = Task 5 (built, not switched on) ∥ Task 4 (catalog with
bluefin adopted); wave 3 = Task 6-1 (background work bound) then Task 6-2 (routing) ∥ Task 10 (resolver rest, `TenantIsolationIT` after Task 6-2); wave 4 = the
row guard switched on for bluefin on staging; wave 5 = Task 7 ∥ `identity` KAN-247, Task 8, Task 9, then Task 12. The
Jira App Tasks 18 – 21 are a separate lane after Tasks 4 and 6-2.

Task 10 (planned 25/11, KAN-258) is two PRs: 10a the web resolver (master, "The web resolver (Task 10)": base domain
from `TSOLVE_SITE_ORIGIN_TEMPLATE`, active only with a catalog bean, the raw `Host` header, the public `bindRequest`
that only the resolver calls, no existence check in `runIn` for the bound id, no cache) and 10b `TenantIsolationIT`
after Task 6-2.

Task 6a (6a-1 the holder, 6a-2 the removal of the fallbacks) builds the master's "The binding" in two steps, so that routing (Task 6-2) lands on a binding that every entry
already uses:
- **Now (Tasks 6a-1 and 6a-2):** Task 6a-1: `ThreadLocalTenantContext` in `common/tenant` is the only per-thread binding. identity
  `TenantScope` (`PlatformTenantScope`) and the worker's `WorkerTenantService.runIn` delegate to it; requests bind it
  through `WebSessionFilter`, Jira App calls through the cloudId path of the Jira lookup (`ConfigJiraWorkspaceLookup`; replaced by `CatalogJiraWorkspaceLookup` in Task 4b), jobs through
  `TenantExecutionServiceImpl`; the holder is `@Primary`, so the stub is not injected from then on. Task 6a-2 then removes `StubTenantContext`, the `OutboxWorkspace` fallback, `TSOLVE_STUB_WORKSPACE_ID`,
  `TSOLVE_TRACKER_DEV_WORKSPACE_ID` and the request-thread check of `MockWorkerTenantService`. `runIn` opens no
  transaction. The existence check (AC-002.14) goes through the port `KnownWorkspaces`, answered until Task 4 by
  identity's `WorkspaceLookup.findById` over the env stubs (Decided in planning, builder check). No database change.
- **Later:** Task 4 answers `KnownWorkspaces` from `WorkspaceCatalog.findById`; Task 6-2 adds `TenantBinding` and the
  routing; Task 10 adds the resolver's own bind (`bindRequest`) for site requests.

## Task 6: background work bound, then routing (Hiếu, Q1 – Q10 = A; split into 6-1 and 6-2)

Two tasks, two PRs, each one QA card and one developer card, merged and deployed in order; 6-1 is on staging before 6-2
deploys. Full text in `tasks.md`; the routing design is the master's "Database routing".

| # | Decided in planning |
|---|---------------------|
| Q1 | P5 is "bind only": `runIn` opens no transaction, so no connection is held through an AI call. AC-002.23 holds because the pool is chosen when `doBegin` takes the connection, a transaction begun unbound throws, and a switch is refused (AC-002.13). `WebSessionFilter` is unchanged; Task 10 binds with `bindRequest` (Task 10, Q2) |
| Q2 | The routing `DataSource` is primary exactly when the Registry is configured and the profile is not `dev`. The gate stays; moving the ITs to production routing is a later task |
| Q3 | Boot's tenant Liquibase gets `spring.liquibase.url/user/password` from `DB_HOST/PORT/NAME/USER/PASSWORD`, under the routing gate only (builder check: the ITs set `DB_HOST=unused`). Staging migrates as today, until `TenantStartupMigrator` or wave 4's owner role |
| Q4 | Routing on: `ddl-auto: none` and the R15 properties. Routing off: `validate` stays, so CI still catches schema drift (AGENTS §7 unchanged) |
| Q5 | bluefin is routed with its row as it is; wave 4 creates its runtime user and updates the row before the guard (AC-001.32 unmet on staging until then) |
| Q6 | 6-1 changes four files in search, chatbot and ticket-repository (one bug row; their owners review). `InProcessSuggestionJobQueue` (never runs on staging) and the rewrite pool (no tenant database work) are unchanged |
| Q7 | `TENANT_DB_POOL_MAX` default 5, staging 10 (today's Boot default for bluefin). Both routing processes count (Edge #15). The Review Queue's LISTEN connection holds one slot of a Workspace pool per process. At 10 per process, 25 Workspaces would need 500: lower staging's value before it has that many |
| Q8 | `common/tenant`: `TenantBinding`, `TenantRoutingDataSource`, `TenantAwareJpaTransactionManager`, port `TenantDataSources`. `platform/registry`: `TenantPoolRegistry`, `TenantDataSourceConfig` (`common/` depends on no module, AGENTS §1) |
| Q9 | `management.health.db.ignore-routing-data-sources: true`; `TENANT_DB_PORT` (default 5432); `RegistryRepository.findConnection` is platform-internal (AC-004.18); no package-private `bind`/`unbind`, `TenantBinding` stays package-private; spec unchanged; Task 6-2 owns the two-Workspace fixture `TwoWorkspaceDatabases`, Task 10 reuses it |
| Q10 | Split as above: 6-1 on `fix/KAN-461-platform-bind-background-work`, 6-2 on `feat/KAN-254-platform-tenant-routing` |

Two more answers (Hiếu, 25/11, both the recommendation), full text in Task 6-2:

| # | Decided in planning |
|---|---------------------|
| N1 | bluefin's row password is checked before the routing deploy with a `verify` mode of `RegistryPasswordCli` (a ninth production file in 6-2, accepted over the size guide) that prints only OK or FAIL and never the password. CD deploys on merge, so the check runs before the merge on an image built from the PR head (`infra/README.md`) |
| N2 | 6-2 raises staging's `max_connections` from 100 to 300 (Edge #15). The database restarts for it: CD does not recreate `db`, so it is done by hand right after that deploy |

## Data model in this iteration

The Registry tables `workspaces` and `workspace_emails`, the tenant-table template, `tenant_identity` with the guard
function and policies, the two allow-lists, the roles and the template database, the storage layout and the seed file —
all as in the master's Data Model.

## Open before coding

- The user re-approves the spec (`sdd.ts approve platform`).
- Trần Hải Anh's answer on who edits the tenant master and changeset 001 (P1).
- The smoke test (Task 15) depends on `be-foundation` answering 401 `MSG07` on any `/api/**` path without a session.
- The real `FirstAdminCreator` (`identity`, KAN-247) and `InvitationJobQueue` (`platform`, JobRunr; Task 8, minimal, user decision of 4/11) before Tasks 9, 12 and 15 can start the
  real backend.
- KAN-140 (the chat model) for the chat-model part of Task 22; its `bge-m3` part does not wait.
## Edge cases covered here

Master rows 1 – 55, 57 – 64, 69 – 71, 72 – 89, 90, 91 and 92 (STORY-001, STORY-002, STORY-004, STORY-005, STORY-006, STORY-007, STORY-008,
STORY-009, STORY-010, STORY-011, STORY-012 without the contributors, STORY-013 and the edge limits of STORY-014). Rows 45
(also in Task 25), 56 and 65 – 68 belong to iteration 2.

## KAN-186 (STORY-015)
See the master design, section "KAN-186: cross-module isolation tests and the E2E smoke" — Tasks 23 and 24.
