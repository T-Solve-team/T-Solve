# KAN-370 decisions (BUG-342)

| # | Question | Options | Choice | Why |
|---|----------|---------|--------|-----|
| D1 | How to give the suggestion job a Workspace on staging | A) bind it in the in-process queue · B) web queues for the worker | B (user, 2026-11-18) | The webhook binds no Workspace, so A had nothing to pass on; B is the review email's model and adds JobRunr retries |
| D2 | Which flag | reuse `outbox-enabled` · new `web-jobs-enabled` · `enabled=true` in web | new `web-jobs-enabled` | `outbox-enabled` names email; `enabled=true` needs worker-mock or a platform adapter and switches Wiki (BUG-339/340) |
| D3 | Where the Workspace comes from | move `OutboxWorkspace` to a shared config · require `outbox-enabled` | require `outbox-enabled`, fail the start with a message naming both flags | Smallest change; staging sets both; a missing flag is loud, never silent |
| D4 | When JobRunr is created in the web process | at start · on the first enqueue | on the first enqueue | Web start never waits on the Jobs database (same as the outbox pool); tests need no database; a failed start is retried next job |
| D5 | Bug id | BUG-339 (quick change title) · BUG-342 | BUG-342 | BUG-339..341 are taken on main |
