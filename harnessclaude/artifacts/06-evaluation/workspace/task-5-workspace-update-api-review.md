---
module: workspace
feature: workspace-update-api
task: 5
verdict: pass
attempt: 2
date: 2026-11-13
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: workspace — Task 5: Update API (UC-11)

## Summary
Commits `default@0f0a1ff` (original) and `default@92f0118` (fix), covering STORY-005. AC-005.5 now reads "IF that
check fails, or the stored token it needs cannot be decrypted, THEN … 400 `MSG156` … on `serviceAccountToken` and
change nothing" (spec Changelog 2026-11-12, D29, re-approved by the user in `approvals.json`).

The fix adds `storedToken(...)` (`WorkspaceServiceImpl.java:185-193`). It catches the cipher's
`IllegalStateException`, logs an ERROR with the Workspace id only (no value, no cause), and throws the `MSG156` field
error. This happens before the Jira call and before the write transaction, so nothing changes. `TokenCipher.decrypt`
maps every failure to that one exception, including a null stored token (NPE is caught at `TokenCipher.java:69`), so
no decrypt path reaches the 500 handler any more.

The new test "AC-005.5 undecryptable stored token on an email change returns MSG156 and changes nothing D29"
(`WorkspaceServiceImplTest.java:587`) checks these things:
- the field, the code and the params;
- no cause;
- exactly one ERROR log that contains the id and no token, ciphertext or email, with no throwable;
- no save, no audit, no transaction, no Jira call and no encrypt.

I re-checked the whole feature against the current spec; AC-005.1..4 and AC-005.6..10 are unchanged and still met.
Verdict: **pass**.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `WorkspaceServiceImpl.update` `:139-183` + `storedToken` `:185-193` implement every AC, including the reworded AC-005.5. |
| Completeness | pass | 10/10 ACs PASS. EXTRA (unchanged, tested, harmless): a non-string token → `MSG03` on `serviceAccountToken`; a malformed body → `MSG03`. |
| Correctness | pass | Orchestrator's verify after the four fixes: `mvn -B verify` — 1594 unit + 481 IT, BUILD SUCCESS. Not re-run by the reviewer (brief: low-RAM machine). No CRITICAL finding. |
| Integration | pass | The `PUT` contract is unchanged. The new error path uses the existing `FieldMessageCodeException` → ProblemDetails mapping, as the other `MSG156` paths do. |

### Stage 1 — spec compliance

| # | AC | Status | Evidence |
|---|----|--------|----------|
| 1 | AC-005.1 | PASS | `:166-182`; IT "AC-005.1 PUT persists encrypted changes only in the bound tenant" |
| 2 | AC-005.2 | PASS | `:153-154,166`; IT: an omitted, null or blank token keeps the identical ciphertext |
| 3 | AC-005.3 | PASS | `:143-148`; unit + IT for MSG01/MSG02/MSG03, row and audit unchanged |
| 4 | AC-005.4 | PASS | `:158-165` (any non-blank token counts as changed, D21); IT checks the stored site and the decrypted stored token on an email change |
| 5 | AC-005.5 | PASS (extended) | Jira failure: `:162-164`, unit + IT. Undecryptable stored token: `:185-193`, unit `WorkspaceServiceImplTest.java:587` |
| 6 | AC-005.6 | PASS | `:158` skips Jira and the cipher; IT renames while Jira throws |
| 7 | AC-005.7 | PASS | The request site is never read; `stored.getJiraSiteAddress()` at `:161,169`; unit + IT |
| 8 | AC-005.8 | PASS | `:173-180`; unit + IT, no token or ciphertext |
| 9 | AC-005.9 | PASS | `:157`; unit, slice, IT |
| 10 | AC-005.10 | PASS | `@PreAuthorize` on the controller; slice + IT |

## Traceability
```
$ bun harnessclaude/hooks/sdd.ts trace workspace STORY-005
  ✓ AC-005.1 … ✓ AC-005.10
  (AC-005.5: WorkspaceServiceImplTest, WorkspaceControllerTest, WorkspaceControllerIT)
trace workspace (STORY-005): 10/10 acceptance criteria have a test
```

## Issues
CRITICAL: none. IMPORTANT: none.

MINOR (do not block):
1. (carried) `WorkspaceServiceImpl.java:156-172`: the row is read outside the write transaction and replaced with a new
   entity (`save` → merge of every column). With two concurrent PUTs the last writer wins, and the second audit's `old`
   values are stale. A future non-null `token_expiry_date` would be wiped. Fix when it matters: re-read inside the
   `TransactionTemplate` (or add `@Version`) and change the row through an entity method (`backend/AGENTS.md` §3).
2. (carried) `WorkspaceServiceImpl.java:178-179`: `Map.of` throws an NPE if the stored `company_name` or
   `service_account_email` is null. This cannot happen through Setup; noted only.
3. (new) The undecryptable-token path is proven at unit level only (a mocked cipher). The real cipher's exception type
   is covered by `TokenCipherTest`, so a controller IT is optional.

## Attempt 1
Verdict pass with an IMPORTANT finding (`0f0a1ff`).
- IMPORTANT: on an email-only update, an undecryptable stored token threw from `cipher.decrypt` outside any handler →
  500 `UNEXPECTED_ERROR`. **Resolved**: the spec now covers it (AC-005.5 extended, D29), and the code was fixed in
  `92f0118` (MSG156 on `serviceAccountToken`, id-only log, nothing changed) with its test.
- MINOR 2–3 (stale read / merge, `Map.of` with null). **Not changed**; they are carried as MINOR 1–2.

## Assumptions made
- The attempt-1 open judgment call (does AC-005.5 cover the decrypt failure?) is settled by the spec change of
  2026-11-12, which the user re-approved. No open call remains.
- Tests were not re-run by the reviewer. The dispatcher's brief forbids `mvn` on this low-RAM machine. The
  orchestrator's recorded verify (session log, "Session 2026-11-13") is used.

## Test Report
See `task-5-workspace-update-api-test-report.md`. Recorded: `mvn -B verify` BUILD SUCCESS (1594 unit + 481 IT).
Trace 10/10 (run by the reviewer).
