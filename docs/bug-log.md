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
