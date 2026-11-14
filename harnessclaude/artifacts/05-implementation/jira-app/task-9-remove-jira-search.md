# Task 9: Remove the Published-ticket search from the Jira App

**Module:** jira-app
**Assignee:** BinhBinh13
**Status:** evaluating
**Iteration:** 2

> Written on 2026-11-14 with the work.

---

## Session 2026-11-14

### Working on: remove-jira-search (spec change of 14/11)
- STORY-008: AC-008.4, AC-008.5, AC-008.6, AC-008.8 and STORY-009 removed (user decision in the dev-site test).
- Jira App: no search box, results, MSG04/MSG76 or preview. Backend: `/jira-app/tickets/**`,
  `PublishedTicketService`, `JiraProjectDepartmentRepository` and the panel's `canSearch` removed;
  `TSOLVE_WEB_ORIGIN` no longer needed. OpenAPI and the generated client follow.
- CI #648 on `aa8ae95e`: every job green.
- Commit: `default@94dd213a`

### Blockers
none

### Next up
Independent review: `06-evaluation/jira-app/task-9-remove-jira-search-review.md`.
