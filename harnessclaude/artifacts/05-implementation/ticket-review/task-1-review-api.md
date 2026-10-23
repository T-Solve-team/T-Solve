# Task 1: Backend review API

**Module:** ticket-review
**Assignee:** OpenAI Codex
**Status:** in-progress — backend implementation verified; user commit agreement pending; official trace remains blocked by BUG-013
**Iteration:** 1

---

## Session 2026-10-20

### Working on: review-api
- User approved the spec and ran the approval command; recorded hash remains `9dd13a5fbaab750dc0017b3816b9c04035c5acf1337e3dc86c83484a6cb0a2f6`.
- Branch: `feat/ticket-review`. No commit, push, merge or PR has been made. All edits outside the harness at the start of implementation were the bug log and Graphify output created in this same authorized request.
- Implemented queue/detail reads and Approve / Reject / Request changes with server Project rights, strict ticket IDs, single-ticket changes, per-ticket history/audit and durable email handoffs.
- All batch tickets are validated before mutation; ascending-ID row locks, optimistic versions and one transaction protect decision/history/audit/notification consistency.
- Review Solution uses a mandatory secondary row. Review enums are exposed from `dto/` so other modules consume only service interfaces and DTO types.
- Scoped controller advice emits Problem Details without changing the legacy global error handler. Method authorization is enabled; authentication/session/CSRF setup stays with the identity integration. No permissive auth adapter or pretend sender was added.
- Discovered BUG-009–BUG-012 during verification and retained their reproductions and fixes in `docs/bug-log.md`. BUG-013 is open; frontend BUG-006–BUG-008 remain open for Task 2.

### Files created/modified
Paths below are repository-relative; none of the existing legacy Java scaffold classes was changed.

| Area | Files |
|------|-------|
| `backend/src/main/java/vn/tsolve/review/config/` | ReviewSecurityConfig.java |
| `backend/src/main/java/vn/tsolve/review/controller/` | ReviewController.java, ReviewExceptionHandler.java |
| `backend/src/main/java/vn/tsolve/review/dto/` | ApproveTicketsRequest.java, IntegralTicketIdDeserializer.java, PendingReviewNotification.java, RejectTicketsRequest.java, RequestChangesRequest.java, ReviewAction.java, ReviewActor.java, ReviewDecisionResult.java, ReviewQueueItem.java, ReviewRegistration.java, ReviewSource.java, ReviewStatus.java, ReviewTicketResponse.java, SolutionSections.java |
| `backend/src/main/java/vn/tsolve/review/entity/` | ReviewAuditEvent.java, ReviewDecision.java, ReviewEvidence.java, ReviewNotification.java, ReviewTicket.java |
| `backend/src/main/java/vn/tsolve/review/mapper/` | ReviewMapper.java |
| `backend/src/main/java/vn/tsolve/review/repository/` | ReviewAuditRepository.java, ReviewDecisionRepository.java, ReviewNotificationRepository.java, ReviewTicketRepository.java |
| `backend/src/main/java/vn/tsolve/review/service/` | AuditService.java, ReviewAccessService.java, ReviewException.java, ReviewNotificationService.java, ReviewService.java |
| `backend/src/main/java/vn/tsolve/review/service/impl/` | AuditServiceImpl.java, ReviewNotificationServiceImpl.java, ReviewServiceImpl.java |
| `backend/src/test/java/vn/tsolve/review/` | ReviewPostgresTest.java, controller/ReviewControllerTest.java, entity/ReviewTicketTest.java, service/ReviewServiceTest.java |
| Schema | New `backend/src/main/resources/db/changelog/changes/002-add-ticket-review.sql`; added its include to `db.changelog-master.xml` |
| Contract and tracking | `frontend/openapi/tsolve-api.yaml`, `docs/bug-log.md`, harness session/test report, registry, progress/events and feedback |

### Verification
- Expected initial red: ReviewTicketTest, four assertion failures before the decision implementation; ReviewServiceTest, two assertion failures before the queue/identity implementation.
- BUG-011 reproduced as expected HTTP 400 versus actual 200 before adding strict integer deserialization.
- BUG-012 reproduced on PostgreSQL as expected one Solution row versus actual zero before mandatory secondary mapping.
- Final command: `mvn spotless:apply`, `mvn verify`, then `mvn spotless:check`, using JDK 21.0.12.1 and Maven 3.9.9.
- Final result: **33 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS; standalone spotless:check exit 0**.
- Breakdown: entity 4, service 13, controller 8, PostgreSQL/HTTP integration 8.
- PostgreSQL checks run on a disposable PostgreSQL 16 container, bound only to loopback port 55432 without a mounted data volume. Liquibase applied the schema and Hibernate validated it. No project database was used.
- Concurrent test waits until `pg_stat_activity` shows a real database lock wait before releasing the first transaction; only one decision/history/audit commits. Failure injection on the second batch ticket leaves both statuses and all decision/audit/notification writes unchanged.
- HTTP integration exercises actual controller → service → PostgreSQL, including Project denial, detail serialization, paging metadata and Request changes. Auth/session inputs are supplied through Spring test facilities and the mocked integration port; this is not a live OIDC/browser/email test.
- Full local logs: ignored `backend/target/review-verify.log` and `backend/target/surefire-reports/`. Their availability is local and temporary; the durable summary is here and in the test report.
- `git diff --check`: exit 0. `sdd.ts check ticket-review` and workspace audit pass.
- Official `sdd.ts trace ticket-review`: **0/30, failed**, due to BUG-013's Java discovery/ID-format limitations. No hook or tracking gate was bypassed. Supporting manual Java-name inventory finds mentions of 24/30 AC IDs; mentions alone do not establish complete criteria coverage. Frontend confirmations, URL/message/loading behavior and integration-documentation delivery assertions remain for Task 2/module evaluation.
- Graphify refreshed after the final test addition to 1,500 nodes / 3,527 edges; the review write path was also queried during verification. SQL extraction remains unavailable because tree_sitter_sql is missing; schema was verified directly through Liquibase/PostgreSQL.
- The disposable PostgreSQL test container was stopped and removed after successful verification. Re-running the database tests requires starting a fresh isolated PostgreSQL instance with the documented environment.

### Integration instructions and limits

1. **Identity and Project rights:** provide one Spring `ReviewAccessService` implementation. `currentActor()` must revalidate active identity and current managed/readable Project IDs from trusted server-side data on every call. Give reviewers Spring authority `ROLE_PROJECT_MANAGER`; do not obtain roles or Project lists from review payloads. Missing integration returns denied access. The existing `/me`, OIDC session, logout, frontend credential/CSRF transport and security-filter error rendering must be supplied by the identity module.
2. **Submission:** call `ReviewService.registerSubmission(ReviewRegistration)` from a trusted in-process submission/import integration after membership, source, Solution/Evidence and review-date checks. IDs are review-owned; legacy `/api/tickets` snapshots do not appear automatically. No public seed or arbitrary-ticket-creation endpoint exists. Source Form tickets start Pending review; imported sources bypass review as Published. Initial deadline calculation and resubmission are external responsibilities under GBR-10 / UC-19.
3. **HTTP API:** `GET /api/v1/review-queue`, `/review-queue/ticket-ids`, `/tickets/{ticketId}`; `POST /api/v1/reviews/approve`, `/reject`, `/request-changes`. Queue query uses `projectId`, Spring paging and allowed sort fields. Changes payload is `{ticketId, comment}`; Approve/Reject use `{ticketIds}` with an optional required-for-Reject `reason`. Request bodies never select the actor.
4. **Evidence:** saved references expose ID/name/media type/size only. File upload/download, permission-checked delivery and Jira Issue Panel rendering of reviewComment require their actual integrations; no working URL or Jira notification is fabricated.
5. **Email:** consume `ReviewNotificationService.pending(Pageable)` in an external worker and resolve recipient identity to its authorized email address. Use the handoff ID as an idempotency key; send outside a database transaction and acknowledge only successful delivery with `acknowledgeDelivery`. The sender owns scheduling, delivery idempotency, error logging and at most three retries within one hour under GBR-27. Pending records remain when no sender exists. Approval creates no ticket email.
6. **Downstream knowledge:** integrations call `ReviewService.getPublished` under the trusted readable Project scope. Search indexing, triage and chatbot retrieval implementations are excluded; this feature does not claim automatic ingestion.
7. **Frontend:** Task 2 must regenerate the client, update review MSG codes and Project filtering, remove the bulk changes action and implement Ticket Detail actions. Generated sources/UI have intentionally not been changed in this backend-only feature. Auth/email ports do not make a full live flow available by themselves.

### Re-running verification
Use JDK 21, Maven 3.9.9 and an isolated PostgreSQL 16 database. Supply `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `SERVER_PORT` and `CORS_ALLOWED_ORIGINS` in the environment; tests require an isolated database and do not skip when it is absent. Run from `backend/`: `mvn verify` and `mvn spotless:check`. The downloaded JDK under ignored `backend/target/tooling` is temporary tooling, not a committed dependency or a substitute for installing the team's normal Java 21 toolchain. Maven clean removes this temporary directory.

### Blockers
- User agreement is required before committing this feature and starting review-ui, under the repository's per-feature commit rule.
- BUG-013 prevents official Java trace evidence; a separate authorized framework fix is required. This does not invalidate the successful Maven/PostgreSQL execution, and it is not represented as an official trace pass.

### Next up
Present the backend files, checks and proposed commit message to the user. After agreement, commit this feature and its harness records separately; do not start review-ui before that checkpoint. Proposed code message: `feat(ticket-review): implement review API and transactional decisions (STORY-001..006)`.

## Implementation references
- Spring Data JPA transaction boundaries: https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html
- Spring Data JPA repository locking: https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/locking.html
- Hibernate SecondaryRow semantics: https://docs.hibernate.org/orm/7.0/javadocs/org/hibernate/annotations/SecondaryRow.html; availability in the installed 6.5.3.Final runtime was confirmed with javap.

## Session 2026-10-20 — User requested FE/BE startup and manual testing

- Added `src/test/java/vn/tsolve/review/manual/ManualReviewApplication`, `ManualReviewConfiguration`, `ManualReviewToolsController` and `manual-smoke.ps1`. The explicit manual-review test profile supplies local HTTP Basic test users, a trusted test Project adapter, fresh fixtures and DB inspection. It is absent from the packaged production jar and binds to loopback only. No production auth implementation/fallback was added.
- Frontend is running at loopback port 5173 with the existing mocks and compatible Node 24.19.0. Browser confirmed the Project Manager queue with 58 mock rows. No frontend feature implementation or generated-client regeneration was started.
- Backend is running at loopback port 8080 with its own `review_manual` database in `tsolve-review-manual-20261020` at loopback port 55432. This container is intentionally left running for the user's manual tests. Verify ran against a separate `review_verification` database.
- Found and fixed BUG-014–BUG-017, retaining each in the ledger. A manual PowerShell check initially misparsed Problem Details bytes as JSON numbers even though the API correctly returned 409/MSG20; the reusable smoke runner now decodes both body forms.
- BUG-016 regression reproduced the incorrect Swagger pageable object before the ParameterObject fix. New final Maven verify: **34 tests, 0 failures/errors/skipped, BUILD SUCCESS**, including Spotless.
- Live server smoke: **18 assertions passed**. Direct Swagger browser execution also verified Authorize, GET CSRF and POST fixtures with the automatically attached CSRF header and HTTP 200. Fresh user batch 467d29c5 has IDs 43/44/45/46/47/48; GET fixtures remains authoritative.
- Manual checklist, test accounts, JSON bodies, expected statuses, DB-side verification, fresh-batch creation and restart commands are recorded in `task-1-review-api-manual-test.md`.
- No commit, push, merge or PR was requested or performed. review-api remains in-progress at its commit checkpoint; BUG-013 and frontend work remain pending.

## Commit checkpoint — KAN-24

- User explicitly authorized commit and reported the staged secret scan passed.
- Code commit: default@4d36a48 (`feat(ticket-review): implement transactional review API (KAN-24)`). Includes review backend, migration, OpenAPI contract, regression tests, local manual harness and permanent bug ledger.
- Verification evidence remains 34 passing backend tests (including Spotless) and 18 passing live HTTP checks; staged whitespace check passed before commit.
- No push, merge or PR. Frontend integration is pending. The commit checkpoint for starting Task 2 is satisfied; the official trace limitation BUG-013 remains open, so feature/module status has not been advanced past its evidence gate.

## Debug — CI generated API drift (BUG-018, KAN-24)

- Root cause: backend commit changed frontend/openapi/tsolve-api.yaml but deferred generation. CI correctly detected drift. Regenerating also exposed five consumer type errors; the new contract uses projectId and single-ticket changes.
- Fix: ran npm run generate:api, updated queue Project filtering and select-all params/mocks, removed Request Changes from the queue, and changed the reusable modal to a single ticketId. Generated files were never hand edited; the CI check is unchanged.
- Regressions: two BUG-018 page tests first failed on the old bulk button and missing Project filter; both pass after the fix. Added a single-ticket modal network payload test instead of retaining the obsolete bulk-mutation expectation.
- Verification: format:check, lint, typecheck, build all exit 0. Full suite: 11 files, 65 tests passed. A second generation has identical SHA-256 hashes for every generated file, including newly created files. git diff --check passes.
- BUG-019: initial full suite had nine pre-test ENOENT failures under sandbox AC/Temp. Retried with TEMP/TMP pointing at ignored frontend/node_modules/.cache/vitest-temp; all suites passed. Exact underlying OS cause remains unconfirmed; no repository test settings changed.
- Graphify refreshed to 1,591 nodes / 3,698 edges; missing SQL parser remains the known limitation.
- Commit pending user agreement. Proposed: fix(review): sync generated client with API contract (KAN-24).
- Scope: this repairs CI and its directly broken consumers. Report 3 MSG migration, actual Ticket Detail actions and live session/CSRF integration remain Task 2; no complete FE/BE manual flow is claimed.

### BUG-018 commit
- User approved the proposed fix commit.
- Code commit: default@b6098c5 (`fix(review): sync generated client with API contract (KAN-24)`).
- Staged secret scan and whitespace check passed. No push requested for this fix.

## Debug — Form timer teardown and PR title (2026-10-21)

- BUG-020: user supplied CI evidence of 65 passing tests plus an unhandled useDelayState timeout after jsdom disposal. Ant Design useDebounce schedules empty error/warning lists after 10 ms; installed useDelayState has no unmount cleanup.
- User approved a quick change scoped to frontend/src/shared/forms/forms.test.tsx and docs/bug-log.md. No production or shared infrastructure changes.
- Red regression: rendering and unmounting Form leaves three pending timers. Test leaves no delayed Form updates after teardown (BUG-020) failed expected 0, actual 3.
- Fix: fake only setTimeout/clearTimeout for this suite, cleanup React then execute pending callbacks inside act while jsdom still exists, assert zero pending timers and restore real timers in finally. Existing field assertions use vi.waitFor so fake time progresses. Initial direct findBy waits stalled under the fake clock; switching to Vitest clock-aware waits resolved that test-harness issue without changing validation assertions.
- Focused suite passes all five tests. No catch-all exception handler, disabled tests, ignored errors, changed dependency or production behavior.
- BUG-021: PR #26 initially had invalid title Feat/ticket review. Connector metadata write was denied 403; user changed it. Readback confirmed feat(review): add transactional ticket review API (KAN-24), matching the repository type/scope/Jira rules.
- Format, lint, typecheck and build pass. First full-suite run: 65 passed, one unrelated ADMIN lazy route heading timeout, no unhandled errors. Logged BUG-002, kept route source unchanged because it is outside the approved quick change. Full-suite rerun in progress without competing verification commands.
- Graphify refreshed: 1,595 nodes / 3,699 edges; known missing SQL parser remains.
- Final standalone full-suite rerun: **11 files, 66 tests passed, exit 0, no unhandled errors**. Non-fatal jsdom pseudo-element getComputedStyle warnings remain visible. BUG-002 remains open as an intermittent route-test timing issue; the successful rerun is not represented as a route fix.
- Proposed quick-change commit: fix(test): drain form debounce timers before teardown (KAN-24). Awaiting the per-feature user commit checkpoint; no commit or push performed.
- User approved commit. Quick-change code commit: default@4d9fce7 (`fix(test): drain form debounce timers before teardown (KAN-24)`). Staged secret scan and whitespace checks passed. Closed the approved quick change with sdd.ts quick done. No push performed.

## FE Lead D1/D2 correction (2026-10-22)

The single-ticket restriction and empty-Solution scenarios described in this historical report are superseded. Current approved spec uses batch Request changes with one shared comment and no empty-Solution review case. Report3 sources and D1/D2 replace the layout-template interpretation. Bug references above use canonical reconciled IDs. See task-5-fe-lead-amendments.md for current verification.
