# Task 30a: Jira link request, confirm page and the confirm failures — KAN-234

## Session

### Hand-off
- done: PR #200 ready to merge. Task 30a (KAN-234): Jira link request service (Redis, single-use, 10 min), non-consuming GET `/api/v1/auth/jira-link/{requestId}`, confirm POST failure answers (400 MSG141, 401 MSG07, 403), `/jira-link` page with the confirm dialog, `SecurityConfig` runs `WebSessionFilter` before `CsrfFilter`. Review 1 CHANGES NEEDED (2 Important fixed in 42b67f57, PR title renamed); review 2 APPROVE, 0 new findings. Tests: backend 28 + 2 + 1 + 15 + 35 green; frontend `src/features/auth` 35/35.
- open: six Minors from review 1, listed in the PR text (GET errors all shown as MSG141; malformed-id GET tests missing; `JiraLinkRequestService.create` argument checks; 501 without a body; POST with a failed CSRF check refreshes the session idle time; JiraLinkPage 403 retry does not assert the refreshed cookie). Task 30b needs Task 26b merged (the Workspace database binding); the tenant binding after the filter belongs to 26b.
- next: Task 30b, record the Jira account link (KAN-310, Subtask of KAN-234).
- branch: `feat/KAN-234-identity-jira-account-link`.
- PR: #200.
