# Task 27: draft-reminder-done-only

**Module:** jira-app · **Assignee:** Claude (for the user) · **Status:** evaluating · **Iteration:** 2

## Session 2026-11-18

### Working on: draft-reminder-done-only (KAN-371)
- Both `SolutionDraftRepository.findOpenDrafts` queries add `exists (… JiraIssueDoneState s … s.done = true)` for
  the Draft's Project and issue (AC-011.2). Javadoc of the service updated.
- `SolutionFormTicketIT.listsOpenDraftsNewestFirstAndLeavesOutIssuesWithATicketOrNotDone_AC_011_2_AC_011_3`: adds an
  issue moved out of Done and one never recorded; both are left out.
- Checks: `DraftReminder*Test`, `SolutionDraftServiceImplTest` pass (22); `mvn test-compile spotless:check` clean;
  trace STORY-011 7/7. **The IT was not run locally (Docker not running)** — left to CI.
- Commit: default@d0b2d1d6

### Next up
all KAN-371 features complete — evaluation (reviewer) and the user's dev-site check
