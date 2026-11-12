# Task 2: Solution Form ticket storage and Drafts

**Module:** jira-app
**Assignee:** BinhBinh13
**Status:** evaluating
**Iteration:** 2

> Written on 2026-11-13 from the commit and its message, after the PR review of KAN-126 found the log missing. It records what the commit holds; reasoning not in the commit message is not reconstructed.

---

## Session 2026-11-12

### Working on: solution-ticket-storage (KAN-126, branch `feat/KAN-126-jira-app-solution-form-v2`)
- STORY-002, STORY-004, STORY-005, STORY-006.
- Changeset `027-jira-app-solution-draft.sql` (table `solution_draft`); `SolutionDraft`, `SolutionDraftRepository`, `SolutionDraftService(Impl)`.
- `SolutionFormTicketService(Impl)`: inserts the ticket in Pending review with writer and review due date (GBR-10); one ticket per issue through `uq_ticket_ticket_id`, mapped to 409 MSG31; MSG01/MSG02 checks; Close stores an empty Solution; the Draft is deleted on submit.
- Deviation from the task text: no new partial unique index on `(project_id, source_ticket_id)`; the existing global `uq_ticket_ticket_id` gives one ticket per issue key.
- Tests: `SolutionFormTicketServiceImplTest`, `SolutionDraftServiceImplTest`, `SolutionFormTicketIT`.
- Follow-up quick change `0077864a` (BUG-198): `@JsonIgnore` on `SolutionSections.isEmpty()`/`length()`, test `SolutionSectionsTest`.
- Commit: `default@285c597e, fix 0077864a`

### Blockers
none

### Next up
Independent review: `06-evaluation/jira-app/task-2-solution-ticket-storage-review.md`.
