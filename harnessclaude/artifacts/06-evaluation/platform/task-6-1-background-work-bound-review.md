---
module: platform
feature: background-work-bound
task: 6-1
verdict: pass
attempt: 1
date: 2026-11-26
reviewer: tsolve-code-reviewer
iteration: 1
currentIteration: 1
---

# Review: platform — Task 6-1: background-work-bound (PR #351, KAN-461)

## Summary

Verdict **pass**. Reviewed range `0bbf98ed...5bd6e902` (11 files, +486 / -24): `4b0dc067` (tests), `8cb6f829` (4
production files + bug log), `5bd6e902` (bug-log PR number only). The publish listener reads the Workspace on the
publishing thread and runs `embed` inside `runIn` of that id on the executor thread; the three recurring jobs run once
per `findActive()` Workspace inside `runIn`, one unbound run with no catalog bean, a per-Workspace failure logs a fixed
text with only the exception's simple class name and the loop goes on, and a `findActive()` failure propagates. The
`ponytail:` comment of `ConversationRetentionJob` is gone. `InProcessSuggestionJobQueue` and `SolutionFormAiImpl` are
untouched, as decided. BUG-402 (Fixed, PR #351) and BUG-403 (Open, logged only) are in `docs/bug-log.md`. Targeted
run: 25 tests, 0 failures; `spotless:check` passes; GitHub CI on `5bd6e902` green. No Critical or Important finding;
3 Minor.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Every item of the Task 6-1 description and design edge #88 holds (table below). |
| Completeness | pass | All 4 production files changed; 8 planned `_BUG_402` tests present in the 4 named classes; constructor-only updates in `TicketEmbeddingServiceImplTest` and `ConversationRetentionJobIT`; bug-log rows written. No EXTRA behaviour. |
| Correctness | pass | Reviewer run: 5 classes, 25 tests, 0 failures/errors/skips; `spotless:check` pass. Developer verify (`spotless:check verify -Dit.test=ConversationRetentionJobIT`, unit 3342/0, IT 3/0) plus CI green on the PR head. |
| Integration | pass | Uses the platform port `WorkspaceCatalog.findActive()` (AC-004.29) through `ObjectProvider` and `TenantContext.runIn` (AC-002.12) unchanged; `WorkspaceCatalogImpl` is registered only with the Registry, so ITs and local runs keep one unbound run. `runIn`'s finally restores the binding, so no Workspace leaks into the next loop iteration or the scheduler thread. |

## Stage 1 — Spec compliance (Task 6-1 description, design "The binding", edge #84, #88)

The task covers no acceptance criterion of its own (tests carry `BUG_402`), so Stage 1 checks the planned behaviour.

| # | Requirement | Status | Evidence |
|---|-------------|--------|----------|
| 1 | Listener reads `currentWorkspaceId()` in `on()`, runs `embed` in `runIn` of it on the executor thread | PASS | `TicketsPublishedListener.java:36,41`; `embedsInThePublishersWorkspaceOnTheExecutorThread_BUG_402` runs the captured task after `runIn` returns, so it fails if the id were read on the executor thread |
| 2 | Listener with nothing bound behaves as today; a failure stays the existing warn line | PASS | `TicketsPublishedListener.java:41-47`; `embedsUnboundWhenThePublisherHasNoWorkspace_BUG_402`, `neverThrowsIntoTheImportCommit_AC_005_5` |
| 3 | Each job: one bound run per Active Workspace via `findActive()` (Suspended not listed) | PASS | `EmbeddingCatchUpJob.java:50-66`, `ConversationRetentionJob.java:41-58`, `PreviewCleanupJob.java:73-89`; each `*_AFailingOneDoesNotStopTheNext_BUG_402` / `...SkipsOneThatCannotBeBound_BUG_402` records the bound id at call time and `verifyNoMoreInteractions(catalog)` pins `findActive()` as the only catalog call |
| 4 | A failure in one Workspace is logged without ids; the loop goes on | PASS | same tests: B (and C) still run after A throws; output contains the class name and neither the Workspace ids nor the exception message |
| 5 | No catalog bean → one unbound run, as today | PASS | `runsOnceUnboundWithoutACatalogAndACatalogFailurePropagates_BUG_402` (×3): repository called once, `Optional.empty()` recorded |
| 6 | `findActive()` failure propagates, never read as an empty list (edge #84) | PASS | same tests: `isSameAs(failure)`, `verifyNoInteractions(untouched)` |
| 7 | dev: `runIn` refusing an unknown id → that Workspace logged and skipped, no extra code | PASS | `deletesInEachActiveWorkspaceBoundAndSkipsOneThatCannotBeBound_BUG_402` (`IllegalArgumentException` logged, B still cleaned) |
| 8 | `ponytail:` comment of `ConversationRetentionJob` removed | PASS | diff of `ConversationRetentionJob.java` |
| 9 | `InProcessSuggestionJobQueue`, `SolutionFormAiImpl` unchanged | PASS | not in the diff |
| 10 | Bug-log row for this task; tests named with its id | PASS | `docs/bug-log.md` BUG-402 (Fixed, Jira KAN-461, Fixed in PR #351, test classes named); BUG-403 Open |
| 11 | `runIn` opens no transaction; transactions start inside the binding | PASS | none of the job methods is `@Transactional`; `deleteExpired`/`embed` open their own transactions inside `runIn` (`TicketEmbeddingServiceImpl` uses `REQUIRES_NEW` templates) |

## Traceability

`bun harnessclaude/hooks/sdd.ts trace platform` → `61/188 acceptance criteria have a test`, exit 1 (expected: this
task covers no criterion). `trace platform STORY-002` → `6/25`, exit 1; AC-002.12 – AC-002.14 (the `runIn` contract
this task relies on) are ✓ in `ThreadLocalTenantContextTest`; the ✗ rows (AC-002.17 onward, routing) belong to Task 6-2
and later. The `_BUG_402` tests sit in other modules' paths by design (tasks.md "Covers").

## Issues

Critical: none. Important: none.

Minor (do not block):
1. `backend/src/test/java/vn/tsolve/search/embedding/service/impl/TicketsPublishedListenerTest.java:61` — no case where
   `runIn` refuses the publisher's Workspace on the executor thread (for example the id is no longer known). The inner
   `catch` at `TicketsPublishedListener.java:42` covers it, but nothing pins that it gives the warn line and does not
   escape the executor. Fix: one assertion in a later touch of this class (refusing `ThreadLocalTenantContext`, warn
   line with `cause=IllegalArgumentException`).
2. `ConversationRetentionJob.java:41-58`, `EmbeddingCatchUpJob.java:50-66`, `PreviewCleanupJob.java:73-89` — the same
   catalog → `findActive()` → `runIn` → catch-and-log loop three times. Fix: none now (a shared helper would be a
   `common/` change); extract it if a fourth job needs it.
3. `EmbeddingCatchUpJob.java:69-70` — `service.skippedIds()` is one process-wide set (`TicketEmbeddingServiceImpl`
   `failures`, keyed by ticket id across all Workspaces) and is passed into every Workspace's query. Harmless with
   UUID ids; note for the multi-Workspace work after Task 6-2. Fix: none now.

## Security Review — platform Task 6-1 — 2026-11-26

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 0 |
| Dependencies | 0 | 0 | 0 | 0 |
| Code | 0 | 0 | 0 | 0 |
| STRIDE | 0 | 0 | 0 | 0 |

- Secrets: added lines scanned; only test strings (`"secret"` as a must-not-log marker). No env file in the diff.
- Dependencies: `pom.xml` unchanged; no audit needed.
- Information disclosure: the per-Workspace failure lines carry only the exception's simple class name, so
  conversation content, ticket text and ids from exception messages are not logged (AGENTS.md §9). The listener keeps
  its existing line with ticket ids (not personal data). A `findActive()` failure goes to the scheduler's error
  handler with its message; that comes from the Registry query and holds no tenant data.
- Elevation / cross-Workspace: each run binds exactly one Workspace through `runIn` (existence check, refuses a
  different outer binding, restores in `finally`); tests assert nothing is bound afterwards. A Suspended Workspace is
  never bound by these jobs (only `findActive()` is read).
- Denial of service: Workspaces run one after another on the scheduler thread; a slow Workspace delays the next. With
  one Workspace on staging this is fine.

## Assumptions made

- Retention while Suspended: `ConversationRetentionJob` and `PreviewCleanupJob` skip Suspended Workspaces, so their
  expired conversations and previews stay until reactivation. This follows GBR-38 / AC-002.15 ("not running its jobs")
  and the planning decision; chatbot AC-013.9 does not say otherwise. Recorded as a judgment call, not a finding.
- The failure line names no Workspace (planning Q1 A), so an operator cannot tell which Workspace failed from the log
  alone; JOB-02 does log the id. Decided in planning; not reopened.

## Test Report

See `task-6-1-background-work-bound-test-report.md`. Reviewer: `mvn -B spotless:check test -Dtest=TicketsPublishedListenerTest,EmbeddingCatchUpJobTest,ConversationRetentionJobTest,PreviewCleanupJobTest,TicketEmbeddingServiceImplTest`
→ `Tests run: 25, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`. CI on PR #351 at `5bd6e902`
(`gh pr checks 351`): backend, backend-tests unit / it-other / it-keycloak-a / it-keycloak-b, frontend, gitleaks,
pr-title pass; frontend-checks and frontend-tests skipped (no frontend change).

Proposed status: `background-work-bound` → `passing` (it is still `pending` in `progress.json`; the orchestrator moves
it through `sdd.ts feature`).
