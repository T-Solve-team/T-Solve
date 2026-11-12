# Session Log: workspace — Task 3 jira-site-linker

**Module:** workspace
**Assignee:** Codex (developer), orchestrated by Claude
**Status:** evaluating
**Iteration:** 1

## Session 2026-11-12

### Working on: jira-site-linker
- Codex wrote `JiraSiteRule` (https only, host = one label + `.atlassian.net`, no user info/port/query/fragment/path,
  bare authority), port `JiraSiteLinker`, `JiraSiteLinkRefusedException` (LINKED_ELSEWHERE, UNREADABLE) and
  `InterimJiraSiteLinker` (`/_edge/tenant_info`, no redirects, tracker timeouts, logs host + status only).
- Decisions (Codex): `Locale.ROOT` lower-casing; an explicit empty port is rejected; status 0 for transport/decoding
  failures (Jira client convention).
- Verify: the first run in a worktree was stopped by low memory (unit 1476 passed, IT cut off); re-run on the working
  tree together with Task 4: `mvn -B verify` — 1534 unit + 454 IT, 0 failures, BUILD SUCCESS
  (JiraSiteRuleTest 35, InterimJiraSiteLinkerTest 32).
- Commit: default@7da71b8

### Next up
Task 4 workspace-setup-api
