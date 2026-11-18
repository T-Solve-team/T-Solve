# Task 24: form-from-issue

**Module:** jira-app · **Assignee:** Claude (for the user) · **Status:** evaluating · **Iteration:** 2

## Session 2026-11-18

### Working on: form-from-issue (KAN-371)
- `openForm` without a Draft answers `Origin.ISSUE` with `IssueText.sections(summary, description)` and no message;
  no AI call (AC-002.1). `IssueText` holds the old `fromIssue` join/cut so Task 25 reuses it.
- Removed `SolutionFormAi.draft`, `SolutionFormDraft`, `Origin.AI_DRAFT`, MSG67 (service + Forge `messages.js`);
  `rewrite` and the `TSOLVE_STUB_AI` modes stay for STORY-007.
- OpenAPI: origin enum `[DRAFT, ISSUE, TICKET]`, descriptions updated; `message` kept (null) so the Forge client and
  resubmission responses keep their shape. `npm run generate:api` regenerated three files.
- Forge tests: AC-002.1 form test rewritten, AC-002.2 test removed, fixtures `AI_DRAFT` → `ISSUE`; a watchDone test
  id corrected AC-002.2 → AC-002.1.
- Checks: backend `JiraAppSolutionServiceImplTest`, `SolutionFormAiImplTest`, `IssueTextTest`, jira-app controller
  tests — 64 run, 0 failed; `mvn test-compile spotless:check` clean; Forge `npm test` 195 passed, `npm run lint`
  clean; frontend `format:check`, `typecheck` clean. Full `mvn verify` left to CI (team practice).
- Trace (STORY-002, STORY-005): AC-002.1 to AC-002.5 and AC-005.1/2/4/5 covered; AC-005.6 to AC-005.10 are Task 25;
  AC-005.3 "ambiguous" with ticket-repository is pre-existing.
- Commit: default@0897441d

### Next up
Task 25 draft-at-done-backend
