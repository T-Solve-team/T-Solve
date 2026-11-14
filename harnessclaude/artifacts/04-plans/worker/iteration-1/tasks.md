---
module: worker
date: 2026-11-07
total_tasks: 4
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

## Task 3: List the Active Workspaces (KAN-157)

**Story:** STORY-003
**Feature:** active-workspaces
**Description:** Add `Set<UUID> activeWorkspaceIds()` to `WorkerTenantService` and implement it in `MockWorkerTenantService` (a copy of the Active allow-list; no binding). Update any other implementations or test doubles of the port. Test first.
**Files:** `backend/src/main/java/vn/tsolve/worker/service/WorkerTenantService.java`, `backend/src/main/java/vn/tsolve/worker/service/mock/MockWorkerTenantService.java`; test `backend/src/test/java/vn/tsolve/worker/service/mock/MockWorkerTenantServiceTest.java` (create or extend).
**Dependencies:** worker STORY-002 (completed).
**Verification:** AC-003.1 and AC-003.2 tests; `mvn verify`; `sdd.ts trace worker STORY-003`.

## Task 4: Shared email outbox and dispatcher (KAN-162)

**Story:** STORY-004, STORY-001
**Feature:** email-outbox
**Description:** Implement AC-004.1–004.12 and the reworded AC-001.1/001.2 (master design "STORY-004"). Thin TDD slices in one feature: (a) `OutboundEmail` + `EmailAddressRules` + `SmtpEmailSender` (rename of `ReviewNotificationSender`, MIME text or multipart); (b) Jobs changelog + `EmailOutboxRepository` + `EmailOutboxService.queue`; (c) `EmailOutboxDispatchHandler` (claim, Workspace check, send, backoff, FAILED, retention) and its recurring registration. Cover design edge cases 1–11.
**Files:**
- Modify: `backend/src/main/java/vn/tsolve/worker/config/{JobRunrRuntime,WorkerConfiguration}.java`, `backend/src/main/resources/application.yml`, `application-worker.yml`, `.env.example`, `backend/README-worker.md`.
- Rename: `worker/client/ReviewNotificationSender.java` → `SmtpEmailSender.java`, and its two tests.
- Create: `worker/config/{EmailOutboxConfiguration,EmailOutboxProperties}.java`, `worker/dto/{OutboundEmail,EmailOutboxDispatchRequest}.java`, `worker/repository/EmailOutboxRepository.java`, `worker/service/EmailOutboxService.java`, `worker/service/impl/{EmailOutboxServiceImpl,EmailOutboxDispatchHandler,EmailOutboxRegistration}.java`, `worker/util/EmailAddressRules.java`, `backend/src/main/resources/db/jobs-changelog/{db.changelog-master,001-email-outbox}.yaml`.
- Test: `worker/service/impl/{EmailOutboxServiceImplTest,EmailOutboxDispatchHandlerTest}.java`, `worker/EmailOutboxIT.java`, update `worker/config/WorkerConfigurationTest.java`.
**Dependencies:** STORY-002 runtime (completed). ticket-review Task 19 builds on it in the same branch.
**Verification:** tests named with each AC id; PostgreSQL Jobs DB + Mailpit IT; `mvn verify` (tests + spotless:check); `sdd.ts trace worker STORY-001` and `STORY-004`.
