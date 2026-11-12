---
module: workspace
feature: workspace-update-api
task: 5
verdict: pass
attempt: 1
date: 2026-11-12
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: workspace — Task 5: Update API (UC-11)

## Summary
Commit `default@0f0a1ff` (STORY-003). `PUT /api/v1/workspace` meets AC-003.1..10 and every AC has a test that checks
it (unit, MockMvc slice and Testcontainers IT). The Jira check runs before the write transaction (good, unlike
setup), the site is never read from the request and is `updatable = false` on the entity, and the audit carries
old/new public values plus `tokenChanged` only. One IMPORTANT finding: when the email changes, no token is typed and
the stored token cannot be decrypted, the call fails with a generic 500 instead of a field error. The spec does not
say this case explicitly (see Assumptions); verdict pass with that finding open.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `WorkspaceServiceImpl.update` `:137-182` implements every AC; edge path at `:158` returns 500 (Issue 1). |
| Completeness | pass | 10/10 ACs PASS. EXTRA: non-string token → `MSG03` on `serviceAccountToken`; malformed body → `MSG03` (tested, harmless). |
| Correctness | pass | Recorded verify: `mvn -B verify` BUILD SUCCESS (1581 unit + 481 IT). Not re-run by the reviewer (dispatcher's low-RAM limit). No CRITICAL finding. |
| Integration | pass | `PUT` contract and `WorkspaceUpdateRequest { companyName, serviceAccountEmail, serviceAccountToken? }` match the spec; OpenAPI declared in Task 4, `WorkspaceOpenApiTest` extended. |

### Stage 1 — spec compliance

| # | AC | Status | Evidence |
|---|----|--------|----------|
| 1 | AC-003.1 | PASS | `:165-181`; IT "AC-003.1 PUT persists encrypted changes only in the bound tenant" decrypts the new value |
| 2 | AC-003.2 | PASS | `:151-152,165`; IT: omitted/null/blank keep the identical ciphertext |
| 3 | AC-003.3 | PASS | `:141-146`; unit + IT for MSG01/MSG02/MSG03, row and audit unchanged |
| 4 | AC-003.4 | PASS | `:156-160` (any non-blank token counts as changed, D21); IT checks stored site + decrypted stored token on email change |
| 5 | AC-003.5 | PASS | `:161-163`; unit + IT: MSG156 on `serviceAccountToken`, nothing changed, no audit |
| 6 | AC-003.6 | PASS | `:156` skips Jira and cipher; IT renames while Jira throws |
| 7 | AC-003.7 | PASS | request site never read; `stored.getJiraSiteAddress()` `:160,168`; unit (even non-string) + IT |
| 8 | AC-003.8 | PASS | `:172-179`; unit + IT, no token/ciphertext in details |
| 9 | AC-003.9 | PASS | `:155`; unit (no external calls) + slice + IT |
| 10 | AC-003.10 | PASS | `@PreAuthorize` `WorkspaceController.java:71`; slice + IT (no Jira call, no change) |

## Traceability
```
$ bun harnessclaude/hooks/sdd.ts trace workspace STORY-003
  ✓ AC-003.1 … ✓ AC-003.10
trace workspace (STORY-003): 10/10 acceptance criteria have a test
```

## Issues

CRITICAL: none.

IMPORTANT (does not block):
1. `backend/src/main/java/vn/tsolve/workspace/service/impl/WorkspaceServiceImpl.java:157-158` — undecryptable stored
   token on an email-only change. `cipher.decrypt(...)` throws `IllegalStateException("Workspace token decryption
   failed")` outside the `try` at `:159`, so it reaches `GlobalExceptionHandler.handleUnexpected` → **500**
   `UNEXPECTED_ERROR`. Nothing leaks (the handler logs the class name only, the cipher drops causes) and nothing is
   stored, but the Admin gets a generic error with no hint. This is the exact recovery scenario the design names
   ("Key loss = all tokens undecryptable … the Admin re-enters the token in 2.2"): an Admin who fixes only the email
   hits a 500. Renaming only, or typing a new token, both still work.
   **Spec coverage:** not explicit. AC-004.3 handles a decrypt failure only for `JiraServiceAccount.current()`.
   AC-003.4/003.5 come closest: the "resulting service account" (new email + stored token) cannot authenticate, so
   the natural reading is "that check fails" → `MSG156` on `serviceAccountToken` (which also points the Admin at the
   right field). Under that reading the code breaks AC-003.5 on this path.
   Fix: wrap the decrypt — `catch (IllegalStateException e)` → `log.error("Workspace {} stored Jira token could not
   be decrypted", workspaceId)` (no value) and throw `error("serviceAccountToken", "MSG156", Map.of("tracker_name",
   "Jira"))`; add a test `AC-003.5 undecryptable stored token on an email change returns MSG156 and changes nothing`.
   Spec: add one sentence to AC-003.5 (or a new AC) so the case is in the contract.

MINOR:
2. `WorkspaceServiceImpl.java:154-171` — the row is read outside the write transaction and replaced with a fresh
   entity (`save` → merge of every column). Two concurrent PUTs: last writer wins and the second audit's `old` values
   are stale; a future non-null `token_expiry_date` would be wiped. Fix when it matters: re-read inside the
   `TransactionTemplate` (or `@Version`) and change through an entity method (`backend/AGENTS.md` §3 "changed
   through intention-revealing methods — no setters", here a new instance stands in for an `edit(...)` method).
3. `WorkspaceServiceImpl.java:177-178` — `Map.of` throws NPE if a stored `company_name`/`service_account_email` is
   null (`service_account_email` is nullable in the DDL). Unreachable through Setup; noted only.

## Assumptions made
- **Open judgment call (for the user):** whether AC-003.5 covers "stored token cannot be decrypted during an update".
  I read it as yes (→ MSG156) but rated Issue 1 IMPORTANT, not CRITICAL, because the spec does not say it outright
  and a working recovery exists (type the token again). If the user agrees it is covered, Issue 1 is an AC-003.5
  defect and this task should go back to fixing; if not, the spec should still say what to answer.
- Tests were not re-run by the reviewer (dispatcher's low-RAM limit); the orchestrator's recorded verify is used.

## Test Report
See `task-5-workspace-update-api-test-report.md`. Recorded: `mvn -B verify` BUILD SUCCESS — 1581 unit + 481 IT
(WorkspaceServiceImplTest 76, WorkspaceControllerIT 42). Trace 10/10 (run by the reviewer).
