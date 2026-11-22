# Task 11: Open Drafts for the reminder

**Module:** jira-app
**Assignee:** BinhBinh13
**Status:** evaluating
**Iteration:** 2

> Written on 2026-11-14 with the work.

---

## Session 2026-11-14

### Working on: draft-reminder-query (KAN-336, branch `feat/KAN-336-jira-app-draft-reminder`)
- STORY-011 (AC-011.2, AC-011.3).
- `ticket`: `OpenDraft` record; `SolutionDraftRepository.findOpenDrafts()` / `findOpenDrafts(creatorId)` (JPQL,
  `not exists` a `Ticket` with the issue key, newest saved first); `SolutionDraftService` exposes both.
- Decided: the active-creator check moved to the job (through `identity`'s `AccountLookupService`), so `ticket` reads
  no `identity` table (backend rule: modules meet through services only); tasks.md Task 11 updated.
- Tests: `SolutionFormTicketIT.listsOpenDraftsNewestFirstAndLeavesOutIssuesWithATicket_AC_011_2_AC_011_3` (green;
  run with `TSOLVE_WORKER_ENABLED=false`, the local `.env` enables the worker).
- Commit: `default@3d982f13` (rebased onto main 15/11; first recorded as `77be7235`)

### Blockers
none

### Next up
Independent review: `06-evaluation/jira-app/task-11-draft-reminder-query-review.md`.
