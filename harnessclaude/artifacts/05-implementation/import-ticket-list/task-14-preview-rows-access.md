# Task 14: Re-check Project access on preview rows

**Module:** import-ticket-list
**Assignee:** bluebearccc (with Claude)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-23 00:00

### Working on: preview-rows-access
- Spec change of 2026-10-22: AC-003.4, design E7 (user decision after evaluation attempt 1).
- `ImportPreviewServiceImpl` gets `ProjectAccess` injected. `owned(user, previewId)`, which `rows`, `row` and `removeRows` all go through, now checks `managesActive(user, preview.projectId)` after the owner check and throws `ProjectAccessDeniedException` (403 MSG08) before any row is read or changed.
- Order: an unknown preview, or another user's, is still 404 (AC-003.2) before the 403, so preview ids still cannot be probed.
- Test (TDD: red on compile, then green): `ImportPreviewServiceImplTest.callerWhoNoLongerManagesTheProjectCannotReadOrChangeRows_AC_003_4` covers all three methods → 403 MSG08 and no row repository calls. The existing 8 tests stub `managesActive` = true.
- Verify: `mvn spotless:apply`, then `mvn verify -DargLine="-Dnet.bytebuddy.experimental=true"` → exit 0; Surefire 93 tests, 0 failures; Failsafe 6 ITs, 0 failures.

- Commit: default@58e0a06 (feat/KAN-31-import-ticket-csv)

### Blockers
none (the trace gap stands, FEEDBACK.md 2026-10-22)

### Next up
Evaluation attempt 2 (csv-confirm, preview-rows-access).
