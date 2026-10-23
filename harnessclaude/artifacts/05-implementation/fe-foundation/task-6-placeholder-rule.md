# Task 6: Placeholders only while a screen is not built

**Module:** fe-foundation
**Assignee:** Trần Trung Hiếu
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-23 20:30

### Working on: placeholder-rule (change request inside KAN-71, branch `feature/KAN-71-chatbot-ask-answer`)
- Change request: AC-005.3 now reads "WHILE a placeholder screen is not built by its module, THE web app SHALL show a
  placeholder …"; Changelog row; the FE Lead approved it (`sdd.ts approve fe-foundation`, spec version d9126208).
  fe-foundation reopened `completed → planned → implementing` for this task.
- `frontend/src/app/router/route-table.test.tsx`: the 12.1 row leaves the placeholder test (chatbot builds 12.1 in
  the same PR), with a comment that a module building its screen removes its row. Later modules do that with a
  quick change, since it changes no criterion.
- Commit: default@507955c

```
trace fe-foundation (STORY-005): 5/5 acceptance criteria have a test
```

### Verify
The same run as chatbot task 1: format, lint, typecheck clean; 421 tests passed; build OK; `generate:api` no diff.

### Blockers
none

### Next up
Review by `harnessclaude-reviewer`; then fe-foundation back to `completed`.
