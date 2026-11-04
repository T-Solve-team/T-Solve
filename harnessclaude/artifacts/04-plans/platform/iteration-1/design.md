---
module: platform
date: 2026-11-04
iteration: 1
---

# Design: platform — iteration 1 (to 30/10; slicing of 25/10, to be redone with the tasks)

Master: `artifacts/04-plans/platform/design.md` (Data Model, Key Decisions, File Structure, Edge Cases, Traceability).
This file keeps only what the first iteration's tasks need. The 4/11 sync (database per Workspace, Registry, row
guard, MinIO, Redis, STORY-013, STORY-014) is in the master; the tasks card re-assigns tasks to iterations, and
STORY-013 and STORY-014 have none yet.

## Scope

The tenant layer for Workspace-site requests (STORY-001, STORY-002, STORY-004), file storage (STORY-007), the local
stack (STORY-008), Caddy and certificates (STORY-009), staging with CI/CD (STORY-010) and the Workspaces-only seed that
the smoke test needs (STORY-012, AC-012.1, AC-012.3 – AC-012.5; decision P7). The Forge token, the Jira App resolver,
the AI VPS and the seed's module data stay in iteration 2.

## Decisions used in this iteration

| Decision | Choice |
|----------|--------|
| P1 | `be-foundation` owns the tenant master; platform's changesets (`tenant_identity`, guard function, guard policies) are added after changeset 001 |
| P2 | `backend/pom.xml`, `application*.yml`, `backend/.dockerignore` are in platform's `paths` (be-foundation owns them) |
| P5 | `runIn` runs the action in a new transaction (`REQUIRES_NEW`) |
| R1 – R3 | Registry through `JdbcClient`; routing `DataSource` with lazy per-Workspace pools; `app.tenant_id` set from the bound id per transaction |
| R4 – R6 | One `runAlways`, `runOrder="last"` changeset installs policy `tenant_guard`; owner role with a template database; per-Workspace runtime user created by provisioning |
| R9, R14 | Provisioning order of AC-013.1; the web process migrates the Registry and every Workspace database at startup, CD also runs a `migrate` step |
| R11 – R13 | Signed URLs on the Workspace's own site, signed for `FILE_STORAGE_PUBLIC_URL_PATTERN`; objects stored as `application/octet-stream`; in-memory upload |
| R16 | One Redis container with persistence and `noeviction` |
| P7 | The seed runner creates Workspaces through provisioning and runs on staging after each deploy |
| P9 | `tsolve-caddy` image built in CI (Cloudflare DNS module, rate-limit module) |
| Q12 | Cloudflare DNS; DNS-01 wildcards `*.tsolve.io.vn` (+ apex) and `*.staging.tsolve.io.vn`; token in the edge `.env` |
| Planning | `ThreadLocal` binding; no resolver cache; `/actuator/health` passes every host; staging basic auth except `api.`; GHCR under the repository owner |

## Data model in this iteration

The Registry tables `workspaces` and `workspace_emails`, the tenant-table template, `tenant_identity` with the guard
function and policies, the two allow-lists, the roles and the template database, the storage layout and the seed file —
all as in the master's Data Model.

## Open before coding

- The user re-approves the spec (`sdd.ts approve platform`) and answers the "Decisions waiting for the user" of the
  master (W1 – W5; W1 and W2 block the storage and Caddy tasks).
- Trần Hải Anh's answer on who edits the tenant master and changeset 001 (P1).
- The smoke test depends on `be-foundation` answering 401 `MSG07` on any `/api/**` path without a session.

## Edge cases covered here

Master rows 1 – 27, 35 – 51 and 54 – 55 (STORY-001, STORY-002, STORY-004, STORY-007, STORY-008, STORY-009,
STORY-010 and the Workspaces-only seed). Rows of STORY-005, STORY-006, STORY-011, the contributors of STORY-012 and
STORY-013 / STORY-014 are assigned by the tasks card.
