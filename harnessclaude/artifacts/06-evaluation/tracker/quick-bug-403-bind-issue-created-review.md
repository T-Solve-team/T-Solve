---
module: tracker
feature: quick-bug-403-bind-issue-created
task: 0
verdict: pass
attempt: 2
date: 2026-11-27
reviewer: tsolve-code-reviewer
---

# Review: tracker — Quick change BUG-403 (KAN-467): bind the issue-created webhook to its site's Workspace

## Summary
Attempt 2. Reviewed the whole uncommitted change on 3098620b in worktree `T-Solve-KAN-467` (11 files, +208 −51:
controller, service interface and impl, two test classes, `docs/bug-log.md`, tracker spec, design, iteration-1 tasks,
`approvals.json`, `events.jsonl`) against the tracker spec as approved now (hash 9bc2419a, `sdd.ts diff tracker`:
"unchanged since it was approved (2026-11-27T03:11:47.263Z)") and the attempt-1 review.

The blocking spec gap of attempt 1 is closed: Non-Functional 8 and Out of Scope now say the "issue created" webhook
binds the Workspace of the FIT's `cloudId` through the platform catalog, AC-003.9 covers "no Workspace / no catalog →
202, nothing stored, no job", and the note under AC-011.10 says its configured-list branch does not arise for this
webhook. The code matches the approved text. `receive(caller, payload)` now takes the principal from the controller
(`@AuthenticationPrincipal`, as `JiraAppIssueEventController`); no `SecurityContextHolder` is left in tracker. The
missing tests are added. design.md and tasks.md describe the new binding and the removal of the dev-Workspace fallback
(KAN-385; no `DEV_WORKSPACE` left in `backend/src/main`).

Verdict **pass**. One minor docs defect introduced by this change (`docs/bug-log.md:31`, issue 1) should be fixed in
the same commit; it needs no re-review. The end verify of the card has no result (see Test Report) — the review
passes the code, not that gate.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Every AC of STORY-003 and the issue-created part of STORY-011 holds with the site's Workspace bound; AC-003.9 met by `IssueCreatedServiceImpl.java:55-72`. |
| Completeness | pass | No EXTRA behaviour left: the 202-store-nothing path is now AC-003.9. A null caller also returns early (defence in depth; the FIT chain / `@PreAuthorize` answer first). Suspended Workspace still recorded — Hiếu's decision, not a finding. |
| Correctness | pass | Reviewer: 22 tests, 0 failures, BUILD SUCCESS (`IssueCreatedServiceImplTest` 14, `JiraAppEventControllerTest` 8); `spotless:check` exit 0. No CRITICAL finding. |
| Integration | pass | `IssueCreatedService.receive` signature changed; its only callers are the controller and the two test classes (grep). Dependency `tracker` → `platform` (`WorkspaceCatalog`) is downward; `TenantContext` is always a bean (`ThreadLocalTenantContext`, `@Component @Primary`). HTTP contract unchanged (body, 202/400). |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-003.1 store a row for an Active Project's issue, 202 | PASS | `IssueCreatedServiceImpl.java:74-90`; `storesSuggestionResultForIssueOfActiveProject_AC_003_1`; `reachesTheDatabaseAndTheQueueBoundToTheWorkspaceOfTheCallersSite_BUG_403` (lookup, insert and enqueue all see the bound id) |
| 2 | AC-003.4 duplicate → 202, no second row | PASS | `:91-97`; duplicate tests |
| 3 | AC-003.6 invalid body → 400 | PASS | unchanged; `JiraAppEventControllerTest` |
| 4 | AC-003.8 not an Active Project → 202, nothing | PASS | `:76-79`; `storesNothingForIssueOfNoActiveProject_AC_003_8` |
| 5 | AC-003.9 site of no Workspace / no catalog → 202, nothing, no job | PASS | `:56-60, 66-72`; `touchesNothingForASiteWithNoWorkspace_AC_003_9_BUG_403`, `touchesNothingInAProcessWithNoWorkspaceCatalog_AC_003_9`; controller answers 202 whenever `receive` returns |
| 6 | AC-011.1 text in row, job after commit, 202 | PASS | `:80-99`; `inOrder` test |
| 7 | AC-011.2 no job for duplicate / foreign project | PASS | as rows 2, 4 |
| 8 | AC-011.8 Chatbot label recorded like others | PASS | `:98-99` |
| 9 | AC-011.10 / 011.11 (with the new note) | PASS | the job is enqueued only inside `runIn` (`:61`), so the configured-list branch is not reached from this endpoint, as the note says |
| 10 | NFR 8 (reworded) — only the FIT's cloudId chooses the Workspace | PASS | `:66-72`; `IssueCreatedPayload` has no site field |

## Traceability
```
trace tracker (STORY-003, STORY-011): 16/16 acceptance criteria have a test
! ids in tests but not in the spec: AC-001.72, AC-002.13, AC-002.16, AC-010.13
```
`sdd.ts check tracker`: "8 stories, 60 acceptance criteria — OK". Full output in the test report.

## Attempt-1 findings

| # | Finding (attempt 1) | Status | Evidence |
|---|---------------------|--------|----------|
| 1 | Blocking — spec: NFR 8 / Out of Scope contradicted, 202-store-nothing path had no AC | Closed | spec.md NFR 8, AC-003.9, note on AC-011.10, Out of Scope, Changelog 2026-11-27; approved 9bc2419a |
| 2 | Duplicated Workspace lookup vs `CatalogJiraWorkspaceLookup` | Deferred (agreed follow-up, card) | `IssueCreatedServiceImpl.java:64-72` |
| 3 | Service read `SecurityContextHolder` | Closed | `JiraAppEventController.java:34-36` passes `@AuthenticationPrincipal`; no `SecurityContextHolder` under tracker |
| 4 | Missing tests: no catalog, no / non-Jira-App auth, unbind on throw | Closed | `touchesNothingInAProcessWithNoWorkspaceCatalog_AC_003_9`, `touchesNothingWithoutAJiraAppCaller_AC_003_9`, `unbindsTheWorkspaceWhenRecordingFails_BUG_403`, `rejectsCallOfAnAuthenticationThatIsNotTheJiraApp_BUG_403` (403, service untouched) |
| 5 | Info: stale bug-log note; Suspended Workspace recorded; Registry outage → 500 | Note closed (with a new formatting slip, issue 1); Suspended — on purpose (card); outage — unchanged, as issue-updated | `docs/bug-log.md:31` |

## Issues

CRITICAL (blocks pass): none.

Informational (does not block):
1. **[Minor — docs] `docs/bug-log.md:31`** — removing the stale sentence also removed the line break and the blank
   quote line: the line now ends `the id stays unused.>` and runs straight into the "Duplicate ids" note, so the
   rendered note shows a stray `>` and the two notes merge into one paragraph.
   Fix: end line 31 at `the id stays unused.` and restore the `>` line before `> **Duplicate ids …`.
2. **[Info] `IssueCreatedServiceImplTest.java:138`** — `touchesNothingWithoutAJiraAppCaller_AC_003_9` is tagged
   AC-003.9, but AC-003.9 covers only "cloudId in no Workspace / no catalog"; a null caller is the spec-change note's
   "FIT filter answers 401 first (AC-001.5)" case. Harmless (AC-003.9 has two real tests).
   Fix (optional): tag it `_BUG_403` instead.
3. **[Info] `docs/bug-log.md:426`** — BUG-403 row Test column names only `IssueCreatedServiceImplTest (methods ending
   _BUG_403)`; `JiraAppEventControllerTest.rejectsCallOfAnAuthenticationThatIsNotTheJiraApp_BUG_403` also carries
   the id. Fix (optional): add it to the column. PR column "—" is filled after the PR (card).

### Security review (STRIDE, scope = the diff)

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 0 |
| Dependencies | 0 | 0 | 0 | 0 |
| Code | 0 | 0 | 0 | 0 |
| STRIDE | 0 | 0 | 0 | 0 |

- Spoofing / Elevation: the Workspace comes only from the `JiraAppPrincipal` set by the FIT chain; the endpoint
  keeps `@PreAuthorize hasRole JIRA_APP`, and a non-Jira-App authentication gets 403 before the service (new
  controller test). `@AuthenticationPrincipal` resolves null for another principal type, and the service then stores
  nothing. The body cannot pick a tenant.
- Tampering: validation unchanged (AC-003.6); `runIn` refuses an id `KnownWorkspaces` does not know and refuses to
  switch an already-bound Workspace (`ThreadLocalTenantContext.java:45-63`).
- Information disclosure / Repudiation: the new log line carries only the issue key (NFR 4); no FIT, cloudId or
  Workspace id.
- DoS: one Registry read per event; endpoint is FIT-only. No new dependency.
- Binding is cleared on success and on failure (`finally`; both tested).

## Test Report
- `mvn -B test -Dtest=IssueCreatedServiceImplTest,JiraAppEventControllerTest -Dspotless.check.skip=true` (backend,
  worktree, uncommitted on 3098620b): "Tests run: 22, Failures: 0, Errors: 0, Skipped: 0 … BUILD SUCCESS".
- `mvn -B -q spotless:check`: exit 0, no output.
- `sdd.ts trace tracker STORY-003 STORY-011`: 16/16. `sdd.ts check tracker`: OK. `sdd.ts diff tracker`: unchanged
  since approval.
- Not run (card): the end `mvn spotless:check test` (developer's run killed for low memory, no result) and
  `SuggestionResultRepositoryIT` on this state. Full suites: GitHub CI on the PR (no PR yet).

Details: `quick-bug-403-bind-issue-created-test-report.md`.

Proposed status: none for a feature (quick change). Before the commit: fix issue 1, then the developer's end checks
(`mvn spotless:check test` and `-Dit.test=SuggestionResultRepositoryIT`) or CI on the PR.
