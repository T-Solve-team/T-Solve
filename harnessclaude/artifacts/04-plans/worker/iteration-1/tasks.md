---
module: worker
date: 2026-11-07
total_tasks: 2
owner: Dev
---

# Tasks: worker — active iteration

Spec: artifacts/03-modules/worker/spec.md

## Task 1: SMTP review notification delivery (KAN-162)

**Story:** STORY-001
**Feature:** review-notification-smtp
**Description:** Implement and verify the SMTP delivery boundary for Reject and Request Changes. Cover AC-001.1 through AC-001.7 and master design edge cases 1–5. Check both messages against a local Mailpit server, reject invalid recipients before attempting delivery, confirm that transport failure is safe and limited to one attempt, and verify that the web context starts without an SMTP host.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/worker/client/ReviewNotificationSender.java`
- Test: `backend/src/test/java/vn/tsolve/worker/client/ReviewNotificationSenderTest.java`
- Test: `backend/src/test/java/vn/tsolve/worker/client/ReviewNotificationSenderIT.java`
- Modify: `backend/pom.xml`, `backend/src/main/resources/application.yml`, `.env.example`
**Dependencies:** none for local SMTP delivery. Job wiring, Workspace recipient lookup and retry follow KAN-161 and KAN-102. Review integration follows KAN-155.
**Estimated size:** small

## Task 2: Tenant-scoped JobRunr queue and execution

**Story:** STORY-002
**Feature:** tenant-jobs
**Description:** Implement one complete enqueue-to-execution feature using JobRunr OSS with a dedicated PostgreSQL Jobs pool and explicit worker-mock platform adapter. Use thin TDD slices: (a) context boundary and mock, (b) typed queue/handler dispatch, (c) conditional runtime and native persistence/execution integration. These are verification steps within the same Jira feature and commit, not separate features. Acceptance: AC-002.1 through AC-002.16, with all Critical/High risk rows 1–11 tested.
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
**Verification:** focused tests after each slice, real PostgreSQL JobRunr dispatch and retry tests, `mvn spotless:apply`, `mvn verify`, `mvn spotless:check`, `bun harnessclaude/hooks/sdd.ts check worker`, `trace worker STORY-002`. No frontend contract change.

## Checkpoint

- [x] Spec and design validated; user-approved scope assigned to current iteration 1.
- [x] All sixteen criteria traced and focused tests pass.
- [x] Full verification result and independent review recorded.
- [x] User approved code and harness commits; code commit 68bd287; no push or merge.
