# Bug log

Every bug the team finds — in testing, code review, a demo or production, backend or frontend — gets one row
here. The test report takes its defect list from this table: copy the table into Excel or Word as it is, and
count by Severity and Status there.

## How to log a bug

1. Add a row at the bottom with the next id (`BUG-001`, `BUG-002`, …). Never reuse or renumber an id.
2. Fill in what you know now: where, how bad, how to reproduce, expected vs. actual. Add the Jira key if the
   bug has an issue. Log it even if nobody fixes it yet.
3. Say clearly whose it is. **Found by** is who found it; **Owner** is who fixes it — normally the developer
   who owns that feature or module (ask the lead if unsure). Write people by full name, as in Jira — never
   "me", "team" or "BE". If the owner changes, update the column.
4. The owner fixes it test-first: a test that fails before the fix, named with the bug id
   (`keepsFirstImport_BUG_004`, or `it('keeps the first import (BUG-004)')` in the frontend).
5. The fix PR sets Status to `Fixed` and fills in Fixed in and Test, in the same PR. The person who found it
   (or the tester) sets `Closed` after re-checking it.

**Severity:** `Critical` crash, data loss, security hole, or blocks a demo · `High` a main flow is wrong, no
workaround · `Medium` wrong, but there is a workaround · `Low` cosmetic, typo.

**Status:** `Open` → `In progress` → `Fixed` → `Closed`, or `Won't fix` (give the reason in Notes).

## Bugs

| ID | Found on | Found by | Owner | Area | Severity | Summary | Steps · expected · actual | Status | Jira | Fixed in | Test | Notes |
|----|----------|----------|-------|------|----------|---------|---------------------------|--------|------|----------|------|-------|
| BUG-001 | 2026-10-21 | Anh Tran Hai | Anh Tran Hai | backend · common/exception | Medium | Error responses are not RFC 9457 `ProblemDetail` and carry no MSG code | Call any endpoint with an unknown id · expected: `ProblemDetail` body with `code` (MSGxx), as in `backend/AGENTS.md` §5 · actual: `GlobalExceptionHandler` returns a `{timestamp, status, message}` map; base exceptions take no code | Open | | | | |
| BUG-002 | 2026-10-22 | Tran Trung Hieu | Tran Trung Hieu | frontend · app/router tests | Medium | The landing page route test fails in the full test suite | In `frontend/`, `npm run test` · expected: all tests pass · actual: `sends ADMIN from "/" to their landing page` cannot find the "User List" heading (fails at ~1.5 s); the file alone passes. The first `findByRole` used the 1 s default while it also loads the lazy page module (guideline 10 §6 asks for 5 s) | Fixed | KAN-56 | | `routes.test.tsx` `sends %s from "/" to their landing page (BUG-002)` | |
