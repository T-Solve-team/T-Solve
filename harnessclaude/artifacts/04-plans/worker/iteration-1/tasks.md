---
module: worker
date: 2026-11-07
total_tasks: 6
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

## Task 4: Multipart HTML review email sender (KAN-333)

**Story:** STORY-001
**Feature:** review-email-html-sender
**Description:** Implement the reworded AC-001.1 and AC-001.2. `ReviewNotificationSender.send(recipient, subject, text, html)` checks for a single mailbox, sends one `MimeMessage` with the given plain-text part and HTML part from "T-Solve <TSOLVE_SMTP_FROM>", and wraps failures in `DeliveryException`. It holds no review content: `ReviewEmailRenderer` (ticket-review Task 19) renders the subject and both bodies, and the job handler calls the renderer and then the sender. The `Decision` enum lives with `ReviewEmail`, not in the sender, so the KAN-162 email outbox can rename and reuse the sender. AC-001.3 to AC-001.7 do not change.
**Files:** `backend/src/main/java/vn/tsolve/worker/client/{ReviewNotificationSender,ReviewEmailRenderer,ReviewEmail}.java`, `backend/src/main/resources/mail/review-notification.html`, `.env.example`; tests `backend/src/test/java/vn/tsolve/worker/client/{ReviewNotificationSenderTest,ReviewNotificationSenderIT,ReviewEmailRendererTest}.java`.
**Dependencies:** Task 1.
**Verification:** AC-001.1 and AC-001.2 tests check that both parts are present and that the From display name is set; `mvn verify`; `sdd.ts trace worker STORY-001`.

## Task 5: Shared email outbox and dispatcher (KAN-162)

**Story:** STORY-004, STORY-001
**Feature:** email-outbox
**Description:** Implement AC-004.1–004.12 and the reworded AC-001.1/001.2 (master design "STORY-004"). Thin TDD slices in one feature: (a) `OutboundEmail` + `EmailAddressRules` + `SmtpEmailSender` (rename of `ReviewNotificationSender`, MIME text or multipart); (b) Jobs changelog + `EmailOutboxRepository` + `EmailOutboxService.queue`; (c) `EmailOutboxDispatchHandler` (claim, Workspace check, send, backoff, FAILED, retention) and its recurring registration. Cover design edge cases 1–11.
**Files:**
- Modify: `backend/src/main/java/vn/tsolve/worker/config/{JobRunrRuntime,WorkerConfiguration}.java`, `backend/src/main/resources/application.yml`, `application-worker.yml`, `.env.example`, `backend/README-worker.md`.
- Rename: `worker/client/ReviewNotificationSender.java` → `SmtpEmailSender.java`, and its two tests.
- Create: `worker/config/{EmailOutboxConfiguration,EmailOutboxProperties}.java`, `worker/dto/{OutboundEmail,EmailOutboxDispatchRequest}.java`, `worker/repository/EmailOutboxRepository.java`, `worker/service/EmailOutboxService.java`, `worker/service/impl/{EmailOutboxServiceImpl,EmailOutboxDispatchHandler,EmailOutboxRegistration}.java`, `worker/util/EmailAddressRules.java`, `backend/src/main/resources/db/jobs-changelog/{db.changelog-master,001-email-outbox}.yaml`.
- Test: `worker/service/impl/{EmailOutboxServiceImplTest,EmailOutboxDispatchHandlerTest}.java`, `worker/EmailOutboxIT.java`, update `worker/config/WorkerConfigurationTest.java`.
**Dependencies:** STORY-002 runtime (completed). ticket-review Task 20 builds on it in the same branch.
**Verification:** tests named with each AC id; PostgreSQL Jobs DB + Mailpit IT; `mvn verify` (tests + spotless:check); `sdd.ts trace worker STORY-001` and `STORY-004`.

## Task 6: Email outbox review fixes (KAN-162)

**Story:** STORY-004
**Feature:** email-outbox-review-fixes
**Description:** Fix the Medium findings of the task 5 review (artifacts/06-evaluation/worker/task-5-email-outbox-review.md), each test-first and logged in docs/bug-log.md. No acceptance criterion changes. (a) AC-004.4: held rows of a non-Active Workspace must not use up the batch — the claim keeps reading due rows, oldest first, until `batch-size` rows are claimed for sending or no due row is left (bounded number of reads), and checks each Workspace once per run. (b) The recurring dispatch job runs with no JobRunr retries, since the next tick is the retry. (c) AC-004.6/004.7: a database error while writing one row outcome is logged by class name and does not stop the other rows. (d) Tests: the recurring job through real JobRunr and the AC-004.9 hold path against PostgreSQL in `EmailOutboxIT`.
**Files:** `backend/src/main/java/vn/tsolve/worker/service/impl/EmailOutboxDispatchHandler.java`, `worker/repository/EmailOutboxRepository.java`; tests `worker/service/impl/EmailOutboxDispatchHandlerTest.java`, `worker/EmailOutboxIT.java`; `docs/bug-log.md`.
**Dependencies:** Task 5.
**Verification:** tests named with the AC and BUG ids; `mvn verify` through the PR CI; `sdd.ts trace worker STORY-004`.
