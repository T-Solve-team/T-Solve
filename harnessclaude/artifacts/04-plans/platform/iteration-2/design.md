---
module: platform
date: 2026-11-04
iteration: 2
---

# Design: platform — iteration 2 (module seed data, and operations readiness after 15/11; DevOps Phase B)

Master: `artifacts/04-plans/platform/design.md` (Data Model, Key Decisions, File Structure, Edge Cases, Traceability).
This file keeps only what Tasks 21 – 24 of `iteration-2/tasks.md` need. Iteration 1 holds Tasks 1 – 20.

## Scope

- The seed's module data through contributors, and the reset (STORY-012; AC-012.2, AC-012.5 – AC-012.9; Task 21).
- Operations readiness (STORY-014, after 15/11): the nightly backup with the off-server copy and the restore drill
  (AC-014.1 – AC-014.5; Task 22), monitoring and alerts (AC-014.6 – AC-014.8; Task 23), the load-test script and one
  measured run (AC-014.12, AC-014.13; Task 24). The edge limits (AC-014.9 – AC-014.11) are in iteration 1, Task 13.

## Decisions used in this iteration

| Decision | Choice |
|----------|--------|
| P7 | The seed runner creates Workspaces through provisioning; CD runs it on staging after each deploy; contributors are called in `order()`, each in `runIn` of the new Workspace, with `modules.<module>` of the seed file |
| Seed reset | `run-seed.sh --reset` checks `TSOLVE_ENVIRONMENT` is `dev` or `staging` first; it empties the bucket with the AWS CLI v2 (`aws s3 rm --recursive --endpoint-url`), the same S3 API as W1 |
| W3 | Grafana Alloy (the one collector), Prometheus, Loki and Grafana; Grafana-managed alerts, no separate alert-routing service |
| W4 | k6 for the load test |
| W5 | `restic` (encrypted, deduplicated) to a Cloudflare R2 bucket with its own token; the `restic` password is kept offline by two people |
| R16 | One Redis per environment, persistence, `noeviction`; the "Redis unreachable" and memory alerts are Task 23 |

## Open before coding

- Iteration 1 Tasks 11 – 15 come first; the contributors are each module's own work (`identity`,
  `workspace`, `project-integration`, `ticket-repository`, `wiki`).
- Manual: the R2 bucket and token, and the `restic` password (Task 22); the metrics endpoints of the backend and worker
  (DevOps F4, owner Trần Hải Anh) for Task 23; Hiếu's confirmation of the load-test numbers (Task 24).

## Edge cases covered here

Master rows 56 (a seed contributor fails), 65 – 67 (backup and restore), 68 (many users behind one address, Task 24) and
45 (a full Redis, Task 23). The other rows are in iteration 1.
