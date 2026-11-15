# Task 10: Close keeps a Draft; no empty ticket

**Module:** jira-app
**Assignee:** BinhBinh13
**Status:** evaluating
**Iteration:** 2

> Written on 2026-11-14 with the work.

---

## Session 2026-11-14

### Working on: close-keeps-draft (spec change of 14/11)
- STORY-005: AC-005.1 reworded (Close and the dialog's close control keep the Draft, flag "Saved as draft"),
  AC-005.5 new; the Close story (AC-006.1 to AC-006.3, MSG70) retired. Reason: the team's Review Queue never holds
  an empty ticket.
- Jira App: buttons Rewrite with AI · Close · Finish; `doneFlag` shows MSG66 after Finish, "Saved as draft" after
  Close. Backend: `SolutionFormTicketServiceImpl.submit` always checks the required sections (400 MSG01 for an
  all-empty Solution); `SolutionSections.isEmpty` removed.
- Tests: `SolutionFormModal.test.jsx` (AC-005.1, AC-005.4, AC-005.5), `SolutionFormTicketServiceImplTest`
  (`refusesAnAllEmptySolutionSoItNeverReachesTheReviewQueue_AC_005_5`); 141 Jira App tests, backend tests green.
- Checked live on bdang798 (5.8.0, 5.9.0) by Claude with SAM1-16 and by the user: Close and Escape keep the Draft
  with no ticket, Continue draft reopens it, Finish submits it.
- Commit: `default@ffebe905`

### Blockers
none

### Next up
Independent review: `06-evaluation/jira-app/task-10-close-keeps-draft-review.md`.
