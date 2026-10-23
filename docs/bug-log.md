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
| BUG-002 | 2026-10-22 | Tran Trung Hieu | Tran Trung Hieu | frontend · app/router tests | Medium | The landing page route test fails in the full test suite | In `frontend/`, `npm run test` · expected: all tests pass · actual: `sends ADMIN from "/" to their landing page` cannot find the "User List" heading (fails at ~1.5 s); the file alone passes. The first `findByRole` used the 1 s default while it also loads the lazy page module (guideline 10 §6 asks for 5 s) | Fixed | KAN-56 | #34 | `routes.test.tsx` `sends %s from "/" to their landing page (BUG-002)` | |
| BUG-003 | 2026-10-23 | Tran Trung Hieu | Tran Trung Hieu | frontend · app/providers tests | Medium | The global error test fails now and then in the full test suite | In `frontend/`, `npm run test` · expected: all tests pass on every run · actual: `shows a failed mutation as a toast with its MSG code` cannot find the "Review Queue" heading (fails at ~1.9 s; 1 of 87 on `main`, again on the KAN-61 branch); the file alone passes. The first `findByRole` of the file used the 1 s default while it also loads the lazy page module (guideline 10 §6) | Fixed | KAN-65 | #40 | `global-errors.test.tsx` `shows a failed mutation as a toast with its MSG code (BUG-003)` | The Review Queue's first test times out the same way: BUG-004 |
| BUG-004 | 2026-10-23 | Tran Trung Hieu | Tran Trung Hieu | frontend · review tests | Medium | The Review Queue's first test fails now and then in the full test suite | In `frontend/`, `npm run test` · expected: all tests pass on every run · actual: `lists the pending tickets of my Projects, earliest review due date first, 20 per page` cannot find the `ITSUP-1031` link (2 of 6 full runs on 23/10, ~6.2 s); the file alone passes. Its first `findBy` already waits 5 s (guideline 10 §6): the first render loads the lazy page and a 20-row table while other test files run in parallel | Fixed | KAN-60 | #42 | `ReviewQueuePage.test.tsx` `lists the pending tickets of my Projects, earliest review due date first, 20 per page (BUG-004)` | Fixed without a longer timeout: Vitest runs test files on half the CPU cores (`maxWorkers: '50%'` in `vite.config.ts`), the file loads the lazy page module once before its tests, and every test's first `findBy` after a render waits up to 5 s. The preload alone was not enough (review of KAN-60, attempt 1) |
| BUG-005 | 2026-10-23 | Tran Trung Hieu | Tran Trung Hieu | frontend · app/router guards | Low | Before Workspace Setup, an Admin who opens My Profile with capital letters is sent to Workspace Setup | Admin signed in, Workspace not set up, open `/Profile` · expected: My Profile (the router matches paths without regard to case) · actual: sent to `/workspace-setup` — `RequireWorkspace` compares the path case-sensitively | Fixed | KAN-62 | #43 | `guards.test.tsx` `AC-003.2 treats a path in other letter case as the same screen (BUG-005)` | Found by the review of KAN-60 (attempt 2). Fixed by comparing the path lower-cased |
| BUG-006 | 2026-10-23 | Tran Trung Hieu | Tran Trung Hieu | frontend · chatbot | Low | A field error on another field than the question shows no message on 12.1 | Ask the Knowledge Chatbot while the server answers 400 with a field error on `history` · expected: MSG06, the question kept in the box (AC-001.13) · actual: nothing is shown, because `applyApiErrors` puts the error on a field the form does not have. Found in the review of KAN-71 before merge | Fixed | KAN-71 | #51 | `KnowledgeChatbotPage.test.tsx` `AC-001.13 shows MSG06 for a field error on another field than the question (BUG-006)` | |
