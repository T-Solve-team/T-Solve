# Session log: Task 4 · review-email-html-sender (KAN-333)

- **Date:** 2026-11-14
- **Scope:** worker STORY-001 (AC-001.1, AC-001.2)
- **Commits:** default@dbeb3db7 on `feat/KAN-333-ticket-review-html-email` (shared by ticket-review Task 19 and worker Task 4: the handler calls the new sender signature, so the two can't be split)
- **Built by:** Codex, from Claude's brief. Claude reviewed the result against the spec and found two bugs: BUG-212 (the button text) and BUG-213 (a lost email for a legacy project id, or a missing project or reviewer). Codex fixed both test-first.
- **Checks run locally:** `mvn spotless:check` passed. Unit tests passed: ReviewEmailRendererTest, ReviewNotificationSenderTest, ReviewNotificationJobHandlerTest, WorkspaceSiteServiceImplTest (24 tests in total). `sdd.ts trace` is complete.
- **Not run locally:** the Docker ITs (ReviewNotificationIT, ReviewNotificationSenderIT), because Docker Desktop was off. They are left to PR CI.
