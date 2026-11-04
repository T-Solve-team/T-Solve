---
module: platform
date: 2026-11-04
iteration: 1
---

# Design: platform — iteration 1 (local stack, Workspaces and the staging demo of 15/11; DevOps Phase A)

Master: `artifacts/04-plans/platform/design.md` (Data Model, Key Decisions, File Structure, Edge Cases, Traceability).
This file keeps only what Tasks 1 – 20 of `iteration-1/tasks.md` need. The 4/11 multi-tenant HLA (database per
Workspace, Registry, row guard, Garage, Redis, STORY-013, STORY-014) is in the master and in those tasks.

## Scope

All the tenant layer and the infrastructure for the demo of 15/11:
- the Registry, the row guard, routing and the Tenant Resolver of the web app (STORY-001, STORY-002, STORY-004;
  Tasks 3 – 6, 9);
- provisioning and the ops script (STORY-013; Tasks 7, 8);
- the local stack and CI (STORY-008; Tasks 1, 2, 10);
- the demo Workspaces through provisioning (STORY-012, AC-012.1, AC-012.3 – AC-012.5; Task 11);
- staging, Caddy and CD (STORY-009, STORY-010, and the edge limits AC-014.9 – AC-014.11 of STORY-014; Tasks 12 – 14);
- file storage and its signed-URL route (STORY-007; Tasks 15, 16);
- the Jira site link, the Forge Invocation Token and the Tenant Resolver of the Jira App (STORY-004, STORY-005,
  STORY-006; Tasks 17 – 19);
- the AI VPS (STORY-011; Task 20).

Iteration 2 holds only the seed's module data and the reset (Task 21) and the rest of STORY-014 (Tasks 22 – 24).

## Decisions used in this iteration

| Decision | Choice |
|----------|--------|
| P1, P2 | `be-foundation` owns the tenant master and the shared files; platform's changesets are added after changeset 001 |
| P3 | `spring-security-oauth2-jose` (Nimbus) for the Forge Invocation Token, version from the Spring Boot BOM |
| P5 | `runIn` runs the action in a new transaction (`REQUIRES_NEW`) |
| P7 | The seed runner creates Workspaces through provisioning and runs on staging after each deploy |
| P9 | `tsolve-caddy` image built in CI (Cloudflare DNS module, rate-limit module) |
| P11, Q12, Q13 | Ollama native on the AI VPS behind WireGuard; Cloudflare DNS-01 wildcards for `*.tsolve.io.vn` and `*.staging.tsolve.io.vn` |
| Q9 | The Jira site URL is normalised and checked through `TenantInfoClient` (public address, `https`, no redirects) |
| R1 – R3, R15 | Registry through `JdbcClient`; routing `DataSource` with lazy per-Workspace pools; `app.tenant_id` set from the bound id |
| R4 – R6 | One `runAlways`, `runOrder="last"` changeset installs policy `tenant_guard`; owner role and template database; per-Workspace runtime user |
| R7, R8 | Database and user named `tsolve_<env>_ws_<32 hex>`; AES-256-GCM password storage |
| R9, R10 | Provisioning order of AC-013.1; `InvitationJobQueue` is a port with a minimal JobRunr implementation in `platform` (user, 4/11) |
| R11 – R13 | Signed URLs on the Workspace's own site; objects stored as `application/octet-stream`; in-memory upload |
| R14 | The web process migrates the Registry and every Workspace database at startup; CD also runs a `migrate` step |
| R16 | One Redis container with persistence and `noeviction` |
| W1, W2 | AWS SDK v2 `s3`; `caddy-ratelimit` in the custom Caddy image |
| Forge | `ForgeSecurityConfig` `@Order(1)` on the `api.` host; stateless, no CSRF, no cookie; the claim paths of `cloudId`, principal and lifecycle `eventType` are confirmed against Atlassian's Forge Remote documentation first (Task 18) and recorded in the master |
| Planning | `ThreadLocal` binding; no resolver cache; `/actuator/health` passes every host; staging basic auth except `api.`; GHCR under the repository owner |

## Data model in this iteration

The Registry tables `workspaces` and `workspace_emails`, the tenant-table template, `tenant_identity` with the guard
function and policies, the two allow-lists, the roles and the template database, the storage layout and the seed file —
all as in the master's Data Model.

## Open before coding

- The user re-approves the spec (`sdd.ts approve platform`).
- Trần Hải Anh's answer on who edits the tenant master and changeset 001 (P1).
- The smoke test (Task 14) depends on `be-foundation` answering 401 `MSG07` on any `/api/**` path without a session.
- The real `FirstAdminCreator` (`identity`, KAN-247) and `InvitationJobQueue` (`platform`, JobRunr; its task is added by
  the planning card that follows this one) before Tasks 8, 11 and 14 can start the real backend.
- KAN-140 (the chat model) for the chat-model part of Task 20; its `bge-m3` part does not wait.

## Edge cases covered here

Master rows 1 – 55, 57 – 64 and 69 (STORY-001, STORY-002, STORY-004, STORY-005, STORY-006, STORY-007, STORY-008,
STORY-009, STORY-010, STORY-011, STORY-012 without the contributors, STORY-013 and the edge limits of STORY-014). Rows 45
(also in Task 23), 56 and 65 – 68 belong to iteration 2.
