---
name: worker
owner: Phạm Tùng Dương
repos: [default]
dependencies: [platform]
complexity: medium
paths:
  - backend/src/main/java/vn/tsolve/worker/**
  - backend/src/test/java/vn/tsolve/worker/**
  - backend/src/main/resources/db/changelog/**
  - docs/bug-log.md
---

# Module: worker

**Owner:** Phạm Tùng Dương

## Description
Background jobs and email for every Workspace. The worker is the same Spring Boot jar started with the `worker`
profile (no web server) in its own container. Jobs run on **JobRunr, open-source edition, with PostgreSQL storage**
(decision of 25/10; `architecture.md` → Background Jobs), which gives retries, recurring jobs and a dashboard.
T-Solve adds the multi-tenant rules: every job carries its `workspaceId`, runs inside `runInTenant`, is postponed
while its Workspace is Suspended (D12), and a configurable cap limits the jobs of one Workspace that run at the same
time, so one company cannot block another (GBR-37; replaces the round-robin of TS-130, D43). Email goes out through
SMTP. The backend package `worker` is *proposed* (SMTP client in `worker/client/`).

**Alternatives considered (25/10):** our own job table with `SELECT … FOR UPDATE SKIP LOCKED` (true per-Workspace
turns, but retries, scheduling and monitoring to build ourselves); JobRunr Pro dynamic queues (per-tenant queues,
paid licence). JobRunr OSS was chosen because it needs no new infrastructure and covers everything but per-tenant
queues, which the cap replaces for the MVP.

## Responsibilities
- The worker process: `worker` profile, its own container (KAN-161, with `platform`'s Compose file).
- JobRunr OSS on the application database: enqueue, retries, recurring jobs, dashboard (KAN-161).
- `workspaceId` on every job, taken from `TenantContext` at enqueue; every job runs inside
  `runInTenant(workspaceId, …)` (KAN-102, GBR-37).
- A recurring job per Workspace (for JOB-02 at 00:00, used by `ticket-review`).
- A job of a Suspended Workspace is postponed, not run; it runs after reactivation (D12, GBR-24).
- Per-Workspace fairness: a configurable cap of concurrently running jobs per Workspace (D43).
- Email through SMTP, sent as a job, never to another Workspace's Accounts (KAN-162, GBR-27, GBR-37).

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| Job queue | Service interface | Enqueue a job for the current Workspace; schedule a recurring job per Workspace |
| `runInTenant` | Java | Run code with `TenantContext` set to a given Workspace (jobs only) |
| Email | Service interface | Send an email through SMTP as a job |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| platform | TenantContext, Workspace registry | Set the Workspace of a job; know whether a Workspace is Suspended |

## Target Repos
- default: backend/ (`worker`, proposed).

## Related Artifacts
- Spec: `artifacts/03-modules/worker/spec.md` (written when the module is planned)
- Architecture: `artifacts/03-modules/architecture.md` (Background Jobs)
- Map: `artifacts/03-modules/module-map.md`
- Stories: KAN-102, KAN-161, KAN-162
