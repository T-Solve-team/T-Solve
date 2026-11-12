---
module: ticket-repository
feature: jira-drafts
task: 7
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 7: Drafts — save, list, reopen, delete

## Summary
Commit `d63c0150`. `ImportDraftServiceImpl` and `ImportDraftController` handle four things:
- Save as draft: no validation, MSG41, and the same Draft with a new time when saved again.
- The list: the caller's own Drafts, newest first, with source, Project, rows that are not Removed, and save time.
- Delete: a CSV preview, a non-Draft, a missing preview or another user's all give 404.
- Reopen: through the existing `GET /imports/previews/{id}`.

AC-004.1 to AC-004.5 and AC-004.7 are met; AC-004.6 comes from Task 2. The verdict is pass, with three open judgment
calls. The first matters: edits to a reopened Draft are saved at once, which may conflict with AC-002.11 and
AC-004.3.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-004.1: `saveAsDraft(now)` with no row validation; `DraftSavedResponse` carries MSG41 (`ImportDraftServiceImpl.java:43-52`). AC-004.2: owner-scoped list, newest first, row count without Removed rows (`:56-66`). AC-004.3: reopen = `ImportPreviewServiceImpl.preview`, which reads only the database. AC-004.4: delete with rows (cascade) (`:70-77`). AC-004.5: owner + JIRA_IMPORT filter → 404 MSG04 (`:80-88`). AC-004.7: the same row is updated with a new time. PM role on the class (`ImportDraftController.java:24`). |
| Completeness | pass | Every criterion of STORY-004 has code and a test. |
| Correctness | pass | `ImportDraftServiceImplTest` 8/8, `ImportDraftControllerTest` 5/5, `ImportDraftRepositoryIT` 6/6. |
| Integration | pass (note) | Responses match the contract's `DraftSavedResponse` and `ImportDraftSummary`. Reopen reuses the preview endpoint, which answers 403 for an inactive Project (judgment call 2). |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-004` → `7/7 acceptance criteria have a test`.
- ✓ AC-004.1, AC-004.2, AC-004.4, AC-004.5 `ImportDraftServiceImplTest`, `ImportDraftControllerTest`
- ✓ AC-004.3, AC-004.7 `ImportDraftServiceImplTest`
- ✓ AC-004.6 `ImportDraftRepositoryIT`

## Issues
No blocking issues found against an unambiguous criterion. Judgment calls for the user:

1. **Edits to a reopened Draft are saved at once.** A Draft is the preview itself (spec D3). Fill solution and
   remove-rows (`ImportPreviewServiceImpl.java:82-91, :95-129`) write straight into the Draft's rows, whether or not
   the user later clicks Save as draft. Example: a user reopens a Draft, fills a Solution over 20 rows, then cancels
   with MSG37. The Draft has lost its saved Solutions, and the next reopen shows the new ones.
   - AC-002.11 asks the web app to return "without changing a saved Draft", and AC-004.3 says "with its rows and
     Solutions **as saved**".
   - Read strictly, both criteria are violated, and no web-app work can undo writes the backend has already made.
   - Read loosely ("Cancel itself changes nothing"), the code is fine.
   - Recommendation: ask the user. Either reword AC-002.11 and AC-004.3 in Change Mode ("changes to a reopened Draft
     are kept at once; Save as draft only moves its save time"), or design a working copy on reopen. I did **not**
     fail the task for this, because the spec's D3 supports the loose reading.
2. **A Draft of a Project that became inactive cannot be reopened.** `ImportPreviewServiceImpl.owned()`
   (`:132-140`) calls `managesActive`, which is false for an inactive Project, so `GET /imports/previews/{id}` answers
   403. Design Edge 11 expects "Opens; Finish → MSG40, Draft kept". The spec (AC-002.9) gives 403 only for "no longer
   managed". This is existing `import-ticket-list` behaviour. The user can still delete the Draft. Decide whether
   reopen should allow an inactive Project.
3. **Delete and list do not re-check the Project.** `delete` (`ImportDraftServiceImpl.java:70-77`) and `list`
   (`:56-66`) skip `managesActive`, but `save` checks it (`:46-48`). NFR 3 says the backend re-checks "Projects the
   caller manages" on every call. Deleting your own Draft is harmless, and probably intended so that a PM can clean
   up after losing a Project. Confirm.

Informational:
4. `ImportDraftServiceImpl.java:43-52`. Save as draft takes no lock. If it runs while a Finish of the same preview
   commits, the dirty-check UPDATE matches 0 rows, and Hibernate throws `StaleObjectStateException`, which the user
   sees as a generic 500 instead of 404. Using `lockByIdAndOwner` (as fill-solution does) gives the 404.
5. Process: there is no `05-implementation/ticket-repository/task-7-jira-drafts.md` session log.

Assumptions made:
- Judgment calls 1 to 3 are left open. I passed the task under the loose reading of AC-002.11 and AC-004.3, the
  existing 403 rule for reopen, and owner-only checks for delete and list.

## Test Report
See `task-7-jira-drafts-test-report.md`. 632 unit + 48 IT, 0 failures.
