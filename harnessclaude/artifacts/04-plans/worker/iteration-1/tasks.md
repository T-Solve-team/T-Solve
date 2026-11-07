---
module: worker
date: 2026-11-07
total_tasks: 1
owner: Dev
---

# Tasks: worker — KAN-102

Spec: artifacts/03-modules/worker/spec.md
Master: artifacts/04-plans/worker/design.md

## Task 1: Tenant-scoped JobRunr queue and execution

**Story:** STORY-001
**Feature:** tenant-jobs
**Description:** Implement one complete enqueue-to-execution feature using JobRunr OSS with a dedicated PostgreSQL Jobs pool and explicit worker-mock platform adapter. Use thin TDD slices: (a) context boundary and mock, (b) typed queue/handler dispatch, (c) conditional runtime and native persistence/execution integration. These are verification steps within the same Jira feature and commit, not separate features. Acceptance: AC-001.1 through AC-001.16, with all Critical/High risk rows 1–11 tested.
**Files:**
- Modify: `backend/pom.xml`, `.env.example`.
- Create: `backend/src/main/java/vn/tsolve/worker/service/{WorkerTenantService,TenantExecutionService,TenantJobService,TenantJobHandler}.java`.
- Create: `backend/src/main/java/vn/tsolve/worker/service/impl/{TenantExecutionServiceImpl,TenantJobServiceImpl,TenantJobRequestHandler}.java`.
- Create: `backend/src/main/java/vn/tsolve/worker/dto/TenantJobRequest.java`.
- Create: `backend/src/main/java/vn/tsolve/worker/config/` and `backend/src/main/java/vn/tsolve/worker/service/mock/`.
- Create: `backend/src/main/resources/application-worker.yml`, `backend/src/main/resources/application-worker-mock.yml`, `backend/README-worker.md`.
- Test: `backend/src/test/java/vn/tsolve/worker/service/impl/TenantExecutionServiceImplTest.java`, `TenantJobServiceImplTest.java`.
- Test: `backend/src/test/java/vn/tsolve/worker/config/WorkerConfigurationTest.java`.
- Test: `backend/src/test/java/vn/tsolve/worker/JobRunrTenantIT.java`.
**Dependencies:** Platform context/catalog mocked as explicitly authorized; Jobs PostgreSQL required for integration checks.
**Estimated size:** large feature, executed in the three sequential slices above.
**Verification:** focused tests after each slice, real PostgreSQL JobRunr dispatch and retry tests, `mvn spotless:apply`, `mvn verify`, `mvn spotless:check`, `bun harnessclaude/hooks/sdd.ts check worker`, `trace worker STORY-001`. No frontend contract change.

## Checkpoint

- [x] Spec and design validated; user-approved scope assigned to current iteration 1.
- [x] All sixteen criteria traced and focused tests pass.
- [x] Full verification result and independent review recorded.
- [x] User approved code and harness commits; code commit 68bd287; no push or merge.
