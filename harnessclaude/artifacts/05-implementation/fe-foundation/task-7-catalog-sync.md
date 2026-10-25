# Task 7: Catalog synced to the System_Messages of 25/10; 9.3 Audit Entry Detail removed

**Module:** fe-foundation
**Assignee:** Trần Trung Hiếu
**Status:** in-progress (code uncommitted)
**Iteration:** 1

---

## Session 2026-10-25

### Working on: catalog-sync (KAN-204, branch `feature/KAN-204-fe-foundation-catalog-sync`)
- Tests first (catalog, route-table, guidelines): 10 failed on the old json/docs, as planned.
- `npm run messages:report3` -> 141 codes; `catalog.ts`: MSG61, MSG73 removed; MSG119/120/122/123/141 (inline), MSG134 (field) added;
  new texts of MSG15, MSG17, MSG89.
- 9.3 removed from audit-log/routes.ts, paths.ts, AGENTS.md (34 screens, 8 popups), guidelines 01, 02, 08.
- Deviations from the Task 7 file list (decisions S1, S2): `msg.test.ts` line 7 (old MSG17 text), guideline 09 line 7 (35 -> 34 screens).
- tasks.md: Jira line now KAN-204 and the branch name.

```
trace fe-foundation (STORY-005, STORY-006, STORY-010): 17/17 acceptance criteria have a test
```

### Next up
Review; commit after the user agrees.
