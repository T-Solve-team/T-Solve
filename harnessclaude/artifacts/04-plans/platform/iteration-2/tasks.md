---
module: platform
date: 2026-11-04
total_tasks: 4
owner: Dev
iteration: 2
---

# Tasks: platform — iteration 2 (module seed data, and operations readiness after 15/11; DevOps Phase B)

Spec: `artifacts/03-modules/platform/spec.md` · Design: `artifacts/04-plans/platform/iteration-2/design.md`
(master: `artifacts/04-plans/platform/design.md`).

Task numbers continue those of iteration 1. "Edge #n" refers to the Edge Cases table of the design. The size rule
and the order rule of iteration 1 apply. STORY-014 is built after 15/11 (DevOps Phase B: B3 – B5, B10), except its edge
limits (AC-014.9 – AC-014.11), which are in iteration 1, Task 14 (DevOps step A5).

---

## Task 23: Demo seed: module data through contributors, and the reset

**Story:** STORY-012
**Feature:** demo-seed-contributors
**Assignee:** Trần Trung Hiếu (KAN-97, S3); Jira sub-task KAN-271
**After this task you can:** run `infra/seed/run-seed.sh --reset` on the local stack and see both Workspaces created anew with
the data of every module that has a contributor; run it with `TSOLVE_ENVIRONMENT=production` and see it refused.
**Covers:** AC-012.2, AC-012.5 (the reset before the S3 seed), AC-012.6 (contributor failures), AC-012.7, AC-012.8, AC-012.9.
**Design:** Seed data; Key Decisions P7.
**Description:** `SeedRunner` calls every `DemoSeedContributor` in `order()`, each inside `TenantContext.runIn` of the new Workspace,
with the `modules.<module>` part of the seed file (module = package segment after `vn.tsolve.`); stops with a non-zero
exit naming the Workspace and the contributor on a failure. Extend `infra/seed/demo-workspaces.json` with the TS-005
sections for each module that has a contributor (the contributors themselves are each module's work: identity,
workspace, project-integration, ticket-repository, wiki). Add `--reset` to `run-seed.sh`: environment check first
(`dev` or `staging` only, else non-zero with nothing touched), then stop backend and worker, drop every Workspace
database named in the Registry, empty the Registry tables and the bucket (`aws s3 rm --recursive --endpoint-url $FILE_STORAGE_ENDPOINT`, the AWS CLI v2; not the MinIO client), re-apply the Registry changelog, start,
seed. Last step, once, by hand: run `infra/seed/run-seed.sh --reset` on the staging server before the S3 seed.
**Edge rows:** #54 (`--reset` and the seed with `TSOLVE_ENVIRONMENT` unset, `production` or `prod` → non-zero before anything is
touched), #56 (a contributor fails for Workspace 2 → stops, names Workspace 2 and the contributor).
**Tests (about 8):** `SeedRunnerTest`: order and binding with two test contributors, a failing contributor (Edge #56);
`infra/tests/seed-reset.test.sh` against the local stack: refused for `production`, `prod` and unset (Edge #54, AC-012.9);
a reset local stack re-seeded with the contributors run on freshly created Workspaces and no "already exists, skipped"
output (AC-012.5, AC-012.8); the databases and bucket prefixes of the old Workspaces are gone.
**Files:**
- Modify: `backend/src/main/java/vn/tsolve/platform/seed/SeedRunner.java`, `SeedData.java`
- Modify: `infra/seed/demo-workspaces.json`, `infra/seed/run-seed.sh`
- Test: `backend/src/test/java/vn/tsolve/platform/SeedRunnerTest.java`, `infra/tests/seed-reset.test.sh`
**Dependencies:** iteration 1 Tasks 12, 16 (the bucket); the AWS CLI v2 on the host that runs the script; contributors of the modules above
**Estimated size:** medium
**Done when:** the reset walkthrough above works and both test files pass.

---

## Task 24: Nightly backup, off-server copy and the restore drill

**Story:** STORY-014
**Feature:** backup-restore
**Assignee:** Trần Trung Hiếu (KAN-17, KAN-18); Jira sub-task KAN-272
**After this task you can:** see last night's backup in the Cloudflare R2 bucket (dumps of the Registry, Jobs and each Workspace
database and each Workspace's object prefix, encrypted by `restic`), and follow `infra/RESTORE.md` to bring one Workspace up on staging.
**Covers:** AC-014.1, AC-014.2, AC-014.3, AC-014.4, AC-014.5.
**Design:** Infrastructure — Operations; Tooling decisions W5.
**Description:** `infra/scripts/backup-db.sh` (the Workspace list from the Registry; one compressed custom-format dump per database;
a copy of each Workspace prefix; `restic` to R2 with a token limited to that bucket; `restic forget` keeps seven days;
exit non-zero naming the failed database or prefix and raise the alert; `CATALOG_SECRET_KEY` kept apart from the dumps,
Redis not backed up; variables `RESTIC_PASSWORD`, `BACKUP_R2_ENDPOINT`, `BACKUP_R2_BUCKET`, `BACKUP_R2_ACCESS_KEY`, `BACKUP_R2_SECRET_KEY` in `.env.example`); a nightly systemd timer; `infra/RESTORE.md` (restore one Workspace to staging under a new database
name, user and password, rewrite its Registry row for staging, the time taken recorded; dump and prefix are taken at
different moments, Edge #67). Manual: create the R2 bucket and token; two people keep the `restic` password offline.
**Edge rows (Critical/High):** #65 (one dump or prefix copy fails → non-zero, named, alert, other files still written),
#66 (a staging restore never carries a production database name, user or password). Medium: #67.
**Tests (about 8 checks):** `infra/tests/backup.test.sh` (one file per database and one copy per prefix; a failing dump is named
and the rest are written, Edge #65; no key in the dump directory, AC-014.3; Redis absent); the drill of `RESTORE.md` once
with its time recorded, and a check that the restored Registry row holds no production name, user or password (Edge #66,
AC-014.5).
**Files:**
- Create: `infra/scripts/backup-db.sh`, `infra/systemd/tsolve-backup.service`, `tsolve-backup.timer`, `infra/RESTORE.md`
- Modify: `.env.example`, `infra/README.md`
- Test: `infra/tests/backup.test.sh`
**Dependencies:** iteration 1 Tasks 13, 16; the R2 bucket and the `restic` password (manual)
**Estimated size:** medium
**Done when:** one backup is in R2 and the restore drill has been run once with its time written in `RESTORE.md`.

---

## Task 25: Monitoring and alerts: Alloy, Prometheus, Loki, Grafana

**Story:** STORY-014
**Feature:** monitoring
**Assignee:** Trần Trung Hiếu (KAN-19, KAN-20, KAN-202); Jira sub-task KAN-273
**After this task you can:** open `grafana.<base>` and see one dashboard for the backend, worker, PostgreSQL, Redis (memory, clients,
commands per second) and Garage; stop Redis and see the "Redis unreachable" alert reach the alert channel.
**Covers:** AC-014.6, AC-014.7, AC-014.8.
**Design:** Infrastructure — Operations; Redis; Tooling decisions W3.
**Description:** `infra/compose.monitoring.yml` (Grafana Alloy as the one collector for metrics and container logs, Prometheus, Loki,
Grafana), `infra/monitoring/alloy.river`, `prometheus.yml`, the dashboard and the alert rules as files (Grafana-managed
alerts, no separate alert-routing service): Redis unreachable, Jobs database unreachable, Garage unreachable, a failed
nightly backup, Redis memory near its limit. The backend and worker expose metrics (DevOps F4, owner Trần Hải Anh);
`grafana.<base>` route and basic authentication in the main `Caddyfile`; variables `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD`, `GRAFANA_BASIC_AUTH_HASH`, `GRAFANA_ALERT_WEBHOOK_URL` in `.env.example`.
**Edge rows (Critical/High):** #45 (a full Redis under `noeviction` fails writes loudly and the memory alert fires).
**Tests (about 6 checks):** `infra/tests/monitoring.test.sh`: every target is up; stopping Redis fires the alert within its
for-duration; stopping Garage and the Jobs database fire theirs; a simulated backup failure fires the backup alert; the
Redis memory alert fires near the limit (Edge #45).
**Files:**
- Create: `infra/compose.monitoring.yml`, `infra/monitoring/alloy.river`, `prometheus.yml`, `alerts.yml`, `dashboard.json`
- Modify: `infra/caddy/Caddyfile`, `.env.example`, `infra/README.md`
- Test: `infra/tests/monitoring.test.sh`
**Dependencies:** iteration 1 Tasks 13, 14; F4 (metrics endpoints); Task 24 for the backup-failure signal
**Estimated size:** medium
**Done when:** the Redis-unreachable alert is seen on the alert channel.

---

## Task 26: Load-test script and one measured run on staging

**Story:** STORY-014
**Feature:** load-test
**Assignee:** Trần Trung Hiếu (KAN-202; Hiếu confirms the numbers); Jira sub-task KAN-274
**After this task you can:** run `k6 run infra/loadtest/tsolve.js` against staging and see a pass or a non-zero exit, and read the
report of one run.
**Covers:** AC-014.12, AC-014.13.
**Design:** Infrastructure — Operations; Tooling decisions W4.
**Description:** `infra/loadtest/tsolve.js` (sign-in, ticket list and a call of the Jira App path; 500 concurrent users; thresholds: 95th
percentile under 1 second, errors under 1 %; the numbers wait for Hiếu's confirmation), `infra/loadtest/README.md` (how to
run it, the staging basic-auth and seed Workspaces it uses, the stated edge-limit values it must stay under), and the report
of one run in `infra/loadtest/report-<date>.md`. The run goes through the edge limits of Task 14: the rates in `.env` are set so
that the test traffic is not refused, or the test says which limit it hit.
**Edge rows:** #68 (many users behind one address hit `EDGE_RATE_PER_IP`; the report names the values used).
**Tests (about 4 checks):** a run against staging exits 0 when the thresholds hold and non-zero when a threshold is missed
(run once with a threshold set impossibly low to prove it, AC-014.13); the report exists and names the numbers.
**Files:**
- Create: `infra/loadtest/tsolve.js`, `infra/loadtest/README.md`, `infra/loadtest/report-<date>.md`
- Test: `infra/tests/loadtest.test.sh`
**Dependencies:** iteration 1 Tasks 14, 15; Task 23 (seed data for the ticket list); Task 25 (to watch the run)
**Estimated size:** small
**Done when:** one report is committed and the script's exit code follows the thresholds.
