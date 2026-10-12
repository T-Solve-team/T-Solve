<!--
PR title = the squash commit on main, so it must pass the commit check:
  type(scope): imperative summary (KAN-123)      e.g.  feat(review): add the reject popup (KAN-42)
Types: feat fix chore docs test refactor ci build perf. The Jira key links this PR to the issue.
Keep PRs under ~400 changed lines (not counting generated code and lock files).
-->

## Jira issue
KAN-___

## What & why

## How to test

## Screenshots (UI changes)

## Checklist
- [ ] CI green
- [ ] Tests added/updated
- [ ] DB migration added (if schema changed), forward-only, never edits a merged script
- [ ] No secrets committed; .env.example updated if config changed
- [ ] No ticket content / personal data written to logs
