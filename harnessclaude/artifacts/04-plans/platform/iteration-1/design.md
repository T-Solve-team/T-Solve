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
