---
module: ticket-review
feature: quick-bug-339-review-email-outbox
task: 0
verdict: pass
attempt: 1
date: 2026-11-18
reviewer: harnessclaude-reviewer
---

# Review: ticket-review — Quick change BUG-339 (KAN-366, PR #290)

## Summary
Read-only review, no tests run (CI green per the orchestrator). Approve with notes. The fix is correct and tenant-safe. Two notes: the web process now depends on the Jobs DB at start, and no test covers the Liquibase wiring or the pool close.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | The web process now gets an EmailOutboxService. Its Workspace comes from TenantContext. |
| Completeness | pass | Config, compose, env examples, README-worker and bug-log row are all updated. |
| Correctness | pass | The conditions are mutually exclusive with EmailOutboxConfiguration. See the notes. |
| Integration | pass | No WorkerTenantService bean is registered. The tracker queue and the review expiry are unaffected. |

## Traceability
Quick change with no STORY id. Bug-id tests: OutboxOnlyConfigurationTest (5 methods) and ReviewNotificationEnqueueBugTest (2 new methods), all named _BUG_339.

## Issues
1. (Note, medium) OutboxOnlyConfiguration.java:58-64 and 69-79. The comment says the web start does not block when the Jobs DB is slow, but `jobsLiquibase` migrates at start, and Boot makes the entityManagerFactory depend on every SpringLiquibase bean. A Jobs DB outage therefore fails the web start. This is the same behaviour as the worker. Staging's `backend` also now hard-requires the JOBS_DB_* variables, which it gets from the shared env_file. Fix: reword the comment to say that startup needs the Jobs DB, and add a line in README-worker.
2. (Note, low) The tests do not cover the Liquibase target or the pool close. OutboxOnlyConfigurationTest replaces SpringLiquibase.afterPropertiesSet with a no-op. Nothing proves that the migration runs on the Jobs DataSource rather than the business one, and nothing proves JobsPool.close() is called on shutdown. The test asserts no DataSource bean, which guards the business pool. Fix: add an assertion that the migration's DataSource is the JobsPool's, or run an IT against Testcontainers. Not blocking.
3. (Note, low) ReviewNotificationEnqueuer.java:81-82. The new WARN fires on every Reject / Request Changes in dev and test profiles, where the outbox is intentionally absent. That is a noise risk only. It logs counts only, with no mail data. Acceptable.
4. (Info) ReviewNotificationEnqueuer.java:50-67. There are two public constructors and one @Autowired, so Spring picks the 6-arg one. The 4- and 5-arg ones pass a null ObjectProvider, which currentWorkspace() null-checks. Safe.
5. (Info) OutboxOnlyConfiguration.java:105-120. RequestTenant throws UnsupportedOperationException from isWorkspaceActive and activeWorkspaceIds. EmailOutboxServiceImpl calls only currentWorkspaceId, so those are unreachable. If the impl later calls another method, the failure surfaces at runtime. Acceptable.

Verified:
- Conditional: `outbox-enabled=true and enabled!=true` against `enabled=true`. They cannot both hold. The bean names `jobsLiquibase` and `jobsLiquibaseDefinition` therefore do not clash.
- Ordering: `emailOutboxRepository` is `@DependsOn("jobsLiquibase")`. The migration supplier resolves JobsPool lazily.
- Pool lifecycle: `destroyMethod="close"` closes Hikari. The repository is destroyed before the pool, because it depends on it.
- Blank `JOBS_DB_URL` throws IllegalStateException at bean creation, with no fallback to the business DataSource. The message does not include the password or the URL. The test covers it.
- Logs carry counts and class names only.
- Tenant safety: the event fires AFTER_COMMIT on the request thread, where TenantContext is still bound. An unbound context yields InvalidOutboundEmailException, which is caught per decision and logged as a WARN, with no cross-workspace write. The Workspace is never taken from the event payload.
- WorkspaceJiraServiceAccount, ReviewExpiryServiceImpl and WorkerSuggestionJobQueue see no WorkerTenantService bean, so they behave as before. The test asserts this.
- The worker container does not set the new flag and sets enabled=true, so it is unchanged.
- Compose and env: the flag sits on `backend` only. JOBS_DB_* come from the shared env_file. `.env.example` has the commented flag.

## Test Report
Not run, on instruction. CI reported green.
