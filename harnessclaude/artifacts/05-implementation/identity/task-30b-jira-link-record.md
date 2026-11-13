# Task 30b: record the Jira account link on confirm — KAN-310

## Session

### Hand-off
- done: PR #216 ready to merge. Task 30b (KAN-310, Subtask of KAN-234): confirm records the Jira account link (upsert, IdentityClock time), answers 200 MSG75; a database failure answers 500 MSG06 with no SQL, and the request stays used up; the `/jira-link` page shows MSG75 for Jira in place of the question and buttons. Review 1 (Fable) approve with 1 Important + 5 Minor, all fixed in the fix1 commit except finding 6 (no audit or log line for a link, decided: no); review 2 (Sonnet) approve, 0 Critical / 0 Important / 2 Minor. CI: all 14 checks pass at f66b4908. Feature `jira-link-record` set to passing.
- open: review 2 Minors — `LinkNotRecordedException` is nested in a concrete service (consistent with `JiraLinkRequestService`); `JiraLinkRequestService.create` throws `IllegalArgumentException` for a bad tracker account id (500 unless the future `jira-app` caller catches it; check when the create endpoint is built). The 403 double-click test in `JiraLinkPage.test.tsx` has a pre-existing race (flaky); a mock waiting on a promise resolved after the `dblClick` would fix it. Backend ITs were not run locally (Docker off); CI is the evidence.
- next: next item per Hiếu's accepted order.
- branch: `feat/KAN-234-identity-jira-account-link-record`.
- PR: #216.
