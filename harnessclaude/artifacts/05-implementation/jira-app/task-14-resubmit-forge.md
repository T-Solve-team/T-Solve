# Task 14: Resubmit — Issue Panel and Solution Form

**Module:** jira-app
**Assignee:** BinhBinh13
**Status:** evaluating
**Iteration:** 2

> Written on 2026-11-15 with the work (PR #237, KAN-130).

---

## Session 2026-11-14

### Working on: resubmit-forge (STORY-012, AC-008.7)
- The Issue Panel shows "Edit and resubmit" with MSG64 when `mayResubmit` and opens the form with `resubmit: true`.
- `SolutionFormModal` shows the change request (MSG64) above the sections, sends `resubmit` on Finish, keeps a Draft on
  Close, and checks, rewrites and retries as before (AC-012.7).
- `tsolveClient.submitSolution`: a retried resubmission that meets 409 MSG06 re-reads the panel and counts a
  `PENDING_REVIEW` status as submitted.
- MSG64 shown as `Changes requested by {manager_name}: "{comment}".` (the last sentence dropped, user decision in the
  dev-site test; the web app keeps the SRS text).
- Commits: `default@3621449b`, `default@29b61222`, `default@2561c1d9` (before the squash of #203; re-applied on main
  by the merge `ef1fc5e0`)

## Session 2026-11-15: PR #237 review and merge with main

- The merge kept main's renumbered Forge bug reference (BUG-233) in the `submitSolution` javadoc of `tsolveClient.js`,
  next to the new resubmit sentence. No other stale BUG-214..223 or `031-jira-app-solution-draft.sql` reference was
  left in KAN-130's files.
- Verify (Forge app): `npx jest` 12 suites, 153 tests passed; `npm run lint` exit 0. Frontend: `npm run generate:api`
  no diff, `npm run typecheck` exit 0.

### Blockers
none

### Next up
Independent review: `06-evaluation/jira-app/task-14-resubmit-forge-review.md`.
