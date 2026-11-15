---
module: jira-app
feature: jira-issue-access
task: 3
verdict: pass
attempt: 1
date: 2026-11-13
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: jira-app — Task 3: Jira issue access (shared with ticket-review)

## Summary
Commit `2dba1a73` adds `JiraIssueAccess`, which turns a FIT caller and an issue key into "linked user, ACTIVE Project,
member?". It also adds the production `ReviewJiraAccess` adapter used by `ticket-review`'s MSG64 feedback endpoint,
the `TSOLVE_STUB_JIRA_SITES` Workspace stand-in, and `AccountLookupService.findName`. I reviewed the code at HEAD
(`feat/KAN-126-jira-app-solution-form-v2`). Since the commit, `asUser` has been added (JiraIssueAccessImpl.java:46-52)
and the shared refusal has moved into `workspace()` (JiraIssueAccessImpl.java:55-60). The adapter, the tests and
`findName` have not changed.

The access rule does what the spec and design say:
- The Workspace comes only from the verified FIT `context.cloudId` (FitAuthenticationConverter.java:19,
  FitValidators.java:33-43). A site that is not listed is refused with 403 MSG08 before any lookup
  (JiraIssueAccessImpl.java:55-60).
- The user is looked up inside the bound Workspace (JiraIssueAccessImpl.java:43, 63).
- The Project is matched on the exact key with `status = ACTIVE` (ProjectServiceImpl.java:23-27). `jira_project_key` is
  unique (002-tenant-project-suggestion.sql:24).
- Membership goes through identity's `ScopeAccessService.canAccessProject` (JiraIssueAccessImpl.java:67-69).

I found no critical problems. The open points are about test depth and stand-in limits (see Issues). I did not run
the tests myself because the dispatcher asked me not to run Maven; the main session runs the backend suite.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | **AC-001.2:** the form is allowed only for a member of the ACTIVE Project whose key is the issue key's prefix (JiraIssueAccessImpl.java:62-77, JiraIssueContext.java:21-27). Whether a Solution Form ticket already exists is checked by Task 5's caller, not here. **AC-001.3:** an unlinked user gets 403 MSG62 (JiraIssueContext.java:22, JiraAskerNotLinkedException.java:10-12); a non-member, an unknown or inactive Project, an unregistered site, or a call without a Jira user gets 403 MSG08 (JiraIssueContext.java:23-25, JiraIssueAccessImpl.java:56-59). **AC-008.1:** `linked()` is false for an unlinked account, and `requireMember` checks the link first, so an unlinked non-member still gets MSG62. **AC-008.7:** the adapter passes the caller's own Account id and the Project to `ticket-review` (JiraIssueAccessReviewAdapter.java:30-37). `jiraFeedback(projectId, writerId, issueKey)` filters by writer, so another writer gets no feedback. A non-member or unlinked caller gets 403 MSG08 before any feedback is read. `accountName` reads `app_user.full_name`, including deactivated Accounts (AccountLookupServiceImpl.java:37-39). |
| Completeness | pass | All four task criteria have tests whose names carry the AC id (see Traceability). One extra rule beyond the spec's wording: membership also requires the matching Workspace role and an ACTIVE Department (ScopeAccessRepository.java:10-16, 38-48). The design's Builder Check (Task 3) records this decision, so it is accepted (see Assumptions). |
| Correctness | pass | The tests check behaviour, not just names. They assert the bound Workspace (JiraIssueAccessImplTest:55); no reads for an unregistered site (:106-107); the MSG08 code (:82-84); MSG62 for an unlinked user (:68); no scope call without a Project (:97); and that the adapter callback is not run for a non-member (JiraIssueAccessReviewAdapterTest:195-197). Project-key splitting is tested (:121-125). There is no test that key `AB-1` does not match Project `A`, but matching is an exact equality query, so it is correct by construction. Test results: see the test report. |
| Integration | pass | The adapter is a Spring bean that implements `review.service.ReviewJiraAccess`. `JiraReviewFeedbackController` takes it through `Optional<ReviewJiraAccess>`, and the one test bean in `ReviewDecisionApiIT` is marked `@Primary`, so the context stays unambiguous. Every `@SpringBootTest` context now loads the adapter, which checks the wiring. `accountName` is called inside the `inWriterIssue` callback, so it runs in the bound Workspace as the interface asks. Task 5's callers validate the key with `ISSUE_KEY` before `inIssue` (JiraAppSolutionController.java:47,60,71; JiraAppPanelController.java:35). |

## Traceability

```
  ✓ AC-001.1  jira-app/TSolve_JiraApp/src/frontend/background/BackgroundApp.test.jsx, jira-app/TSolve_JiraApp/src/frontend/background/watchDone.test.js
  ✓ AC-001.2  backend/src/test/java/vn/tsolve/jiraapp/service/impl/ConfigJiraWorkspaceLookupTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraIssueAccessImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-001.3  jira-app/TSolve_JiraApp/src/frontend/background/BackgroundApp.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/ConfigJiraWorkspaceLookupTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraIssueAccessImplTest.java
  ✓ AC-001.4  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java
  ✓ AC-001.5  jira-app/TSolve_JiraApp/src/frontend/background/BackgroundApp.test.jsx, jira-app/TSolve_JiraApp/src/frontend/background/watchDone.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java
  ✓ AC-008.1  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraIssueAccessImplTest.java
  ✓ AC-008.2  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-008.3  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java
  ✓ AC-008.4  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppTicketServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-008.5  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppTicketServiceImplTest.java
  ✓ AC-008.6  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java
  ✓ AC-008.7  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraIssueAccessReviewAdapterTest.java
  ✓ AC-008.8  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
trace jira-app (STORY-001, STORY-008): 13/13 acceptance criteria have a test
```

Stage 1 for this task's criteria:

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-001.2 | PASS | JiraIssueAccessImplTest `allowsAMemberOfTheIssuesProjectInsideTheSitesWorkspace_AC_001_2`, `hasNoProjectForAKeyOfNoActiveProject_AC_001_2`; ConfigJiraWorkspaceLookupTest `mapsEachRegisteredSiteToItsWorkspace_AC_001_2`. The "no Solution Form ticket yet" part belongs to Task 5 (JiraAppSolutionServiceImplTest). |
| 2 | AC-001.3 | PASS | JiraIssueAccessImplTest `refusesAUserWhoIsNotAMemberWithMsg08_AC_001_3`, `refusesASiteWithNoRegisteredWorkspaceBeforeReadingAnything_AC_001_3`, `refusesACallWithoutAJiraUser_AC_001_3`, `reportsAnUnlinkedAccountAsNotLinked_AC_001_3_AC_008_1` |
| 3 | AC-008.1 | PASS | `reportsAnUnlinkedAccountAsNotLinked_AC_001_3_AC_008_1` (linked = false, MSG62 from `requireMember`) |
| 4 | AC-008.7 | PASS (unit level) | JiraIssueAccessReviewAdapterTest, four tests. The endpoint's writer filter and MSG64 body are covered by ticket-review's `ReviewDecisionApiIT` (lines 303-314), but with a test `ReviewJiraAccess` (see Issue 1). |

## Security Review — jira-app (jira-issue-access) — 2026-11-13

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 0 |
| Dependencies | n/a (no new dependencies) | | | |
| Code | 0 | 0 | 0 | 1 |
| STRIDE | 0 | 0 | 1 | 1 |

- **Spoofing:** the caller and the site come only from the verified FIT. `cloudId` must be present for the token to
  validate (FitValidators.java:38-43). No Workspace id is ever taken from the request.
- **Tampering:** the issue key is checked against a regex before use. The Project lookup is a Spring Data derived
  query, and membership is a named-parameter SQL query, so there is no injection path.
- **Elevation of privilege / IDOR:** a non-member cannot reach feedback; the adapter refuses before the callback
  runs. A member cannot read another writer's feedback, because `writerId` is the caller's own Account id, never a
  request value.
- **Repudiation:** refusals are not logged (Issue 4).
- **Information disclosure:** refusals return only MSG08 or MSG62, with no internal detail.
- **Medium (STRIDE):** the stand-ins limit isolation today (Issue 3).

## Issues

No CRITICAL findings. Informational, not blocking:

1. **[Medium] The production adapter is never tested through the feedback endpoint.**
   `backend/src/test/java/vn/tsolve/review/ReviewDecisionApiIT.java:83-85` marks a test `ReviewJiraAccess` as
   `@Primary`. So the MSG64 endpoint's API tests ("MSG64 for the writer, nothing for another writer, 403 for a
   stranger") run against the test double, never `JiraIssueAccessReviewAdapter` + `JiraIssueAccessImpl`. The task text
   asked for exactly that endpoint-level check for AC-008.7. Today only the adapter's unit tests cover it, and they
   mock `JiraIssueAccess`.
   - Fix: add one IT, or a `@WebMvcTest` with the real adapter and `JiraIssueAccessImpl` over mocked lookups. Call
     `GET /api/v1/jira-app/issues/{key}/review-feedback` and assert: MSG64 for the writer; `feedback: null` for a
     member who is not the writer; 403 MSG08 for a non-member and for an unregistered site.

2. **[Low] Unreachable branch in the adapter.** `JiraIssueAccessReviewAdapter.java:32-34` throws Spring's
   `AccessDeniedException` whenever `!context.member()`. So `requireMember()` on line 35 can never throw, and its
   MSG62 path for an unlinked user can never be reached. The feedback endpoint answers 403 MSG08 for an unlinked user
   (through GlobalExceptionHandler.java:90-96), not MSG62. That is acceptable, because the panel shows MSG62 from its
   own endpoint (AC-008.1). But the two checks repeat each other, and the outcome depends on which one comes first.
   - Fix: keep one check. Either call `context.requireMember()` alone (an unlinked user then gets MSG62 here too), or
     drop line 35 and build `WriterIssue` from `context.user()` and `context.projectId()` directly.

3. **[Medium, STRIDE] The Workspace binding does not isolate data in production yet.** In production,
   `TenantScope` is `LocalTenantScope` (SignInConfig.java, `tenantScope()` bean; LocalTenantScope.java:26-39). It only
   stores the id in a thread-local and does not route the datasource. So today the cloudId gate is the only thing
   separating sites; the Account, Project and membership reads all hit the one database. Separately,
   `ConfigJiraAskerLookup.find` ignores `cloudId` (ConfigJiraAskerLookup.java:31-34). Atlassian account ids are global
   per person, so if two sites are registered, the same person resolves to the same Account on both. This is a
   documented stand-in (design.md decision "Workspace of a FIT call"). It is not a defect of this task, but it must
   not ship as multi-tenant.
   - Fix: none in this task. Make sure platform's site registry and the routing `TenantScope` replace both stand-ins
     before more than one Workspace is served. Until then, `TSOLVE_STUB_JIRA_SITES` should list only one site per
     database.

4. **[Low] Refusals are not logged.** `JiraIssueAccessImpl.java:55-60` and `JiraIssueAccessReviewAdapter.java:32-34`
   refuse without a log line. That makes it hard to tell an unregistered site from a membership problem when Forge
   reports a 403.
   - Fix: log at INFO or DEBUG with `cloudId`, issue key and reason only. Never log the accountId together with
     personal data, so non-functional rule 3 still holds.

5. **[Low] Project keys are matched case-sensitively.** `ProjectServiceImpl.java:23-27` uses an exact match.
   Controllers only accept upper-case keys (`^[A-Z][A-Z0-9_]*-…`). Nothing I found forces `project.jira_project_key`
   to be stored in upper case, so a Project saved as `itsup` would never match. The same exact match correctly keeps
   key `AB-1` away from Project `A`.
   - Fix: none needed if the Project create and edit path upper-cases the key. Otherwise normalize it there, not here.

## Assumptions made
- **AC-008.7 test depth.** I accepted unit-level coverage of the adapter plus ticket-review's endpoint IT (which uses
  a test double) as meeting AC-008.7 for this task. The task text asked for an endpoint-level test. This is a judgment
  call; if the user wants that test, Issue 1 becomes blocking.
- **"Member" is stricter than the spec's Terms.** The spec defines a member as having a `project_membership` row
  (`MEMBER` or `PROJECT_MANAGER`). The code also requires the matching Workspace role (Ticket Resolver or Project
  Manager), an ACTIVE Account and an ACTIVE Department. I accepted this because the design's Builder Check
  (Task 3, user decision) records it. The spec's Terms line was not updated to match.
- **Error codes.** The spec does not give an HTTP code or message for an unregistered site or a FIT without a
  `principal`. Following design Edge Cases #12 and #13, I treated 403 MSG08 as correct for both.
- **Feedback endpoint for an unlinked user.** I treated 403 MSG08 (not MSG62) as acceptable, because AC-008.1's MSG62
  comes from the panel endpoint.
- **Status.** `progress.json` already lists `jira-issue-access` as `passing`, not `evaluating`. As instructed, I did
  not change it.

## Test Report
See task-3-jira-issue-access-test-report.md
