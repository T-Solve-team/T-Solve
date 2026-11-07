# Task 11: Ticket List backend and frontend completion — KAN-77

**Module:** search
**Assignee:** Trần Hải Anh
**Status:** in-progress
**Iteration:** 1

## Session 2026-11-06

The user requested implementation in ascending Jira-number order, completing BE/FE per task and skipping drawing/report-only tasks. Cancelled items remain in scope when incomplete. KAN-25 already has implementation: fresh checks passed 30 BE tests and 27 FE transport/form/global-error tests. KAN-68 is planning-only. The first pending feature is KAN-77 Ticket List; its backend is mapped to KAN-147 in Jira, and the same UI was delivered under KAN-227. Reuse one implementation for these three keys.

### Branch and prerequisites

- Worktree: `C:/Users/Tranh/OneDrive/Desktop/Capstone/T-Solve-kan77`.
- Branch: `feat/KAN-77-search-ticket-list-complete`, from cached `origin/main` `62bd608`; upstream removed.
- Applied existing commits `7c6920e` (BUG-122 approvals JSON) and `b037d06` (approved Search plan) with `git cherry-pick --no-commit`. No new commit, push, merge or Jira mutation. Approval records were carried by those existing changes, not manually recreated. `sdd.ts diff search` confirms the spec matches its approval.
- Reused the uncommitted migration-008 code and schema/rollback tests from the existing focused-ellis worktree by copying them; the original WIP was not changed.
- Took only the dependencies needed by Ticket List from Search Tasks 4, 5 and 9, then Tasks 11–12. Tasks 6–8 (embedding/log/feedback tables) are not needed to execute the list query and remain pending. This implements no embedding or ranking work of KAN-141 beyond the shared list prerequisites.

### Code written

- `GET /api/v1/tickets`: role checks, signed-in TSolveUser, validation, MSG errors and Spring page response.
- Immutable SearchTicket read model with UUID API identities, Project Jira key, Department scope and null review due date.
- Visibility: ACTIVE Department and Published in own Departments, or non-Published own/managed-Project tickets. A Department filter only narrows the scope. Keywords match all Solution sections with literal percent/underscore/backslash. Sort uses a whitelist and internal-id descending tie-breaker.
- Migration 008 for public UUIDs and migration 009 for trigger-maintained ticket.updated_at; existing changesets untouched. Added application/rollback tests.
- Default NoVisibleScope and exact-case local-only DevAllVisibleScope, including unset/empty/ALL/true/trailing-space tests. No production login or membership is fabricated.
- OpenAPI declares GET tickets' 400 response. Existing generated client already uses ApiError for these responses; generated files were restored to HEAD after the blocked generator cleaned its output. No generated file was hand-edited.
- FE reuses the generated hook. BUG-123 validates a Department UUID and sort field from shared URLs before calling BE; the displayed sort follows the validated request. Both regressions failed before the change and pass afterwards.
- BUG-124 handles a positive page whose offset exceeds JPA's integer limit: count the matching rows and return an empty page with that total. The regression first failed against the offset-limited query and passes after the fix.

### Fresh verification

- Controller red phase: 4 tests, 3 failed with 404 before the endpoint existed; no import/context errors.
- Service red phase: 19 tests, 15 failed before service behavior was implemented; no errors.
- FE red phase: the two BUG-123 cases failed with the malformed UUID/unsupported sort sent to the API.
- Final `mvn -B -o test`: **553 tests, 0 failures/errors/skipped**, BUILD SUCCESS. New Search unit/controller/mapper/provider cases: **25 passing**.
- Ticket List FE: **37 tests passing** via `npm run test -- src/features/tickets --maxWorkers=2 --pool=threads --configLoader=native --hookTimeout=60000`.
- `npm run typecheck`: pass. `npm run lint`: pass with one existing TicketListFilters Fast Refresh warning. `npm run format:check`: pass. `npm run build -- --configLoader native`: pass.
- Same Spotless 2.43.0/Palantir formatter through a temporary unratcheted formatter POM: **24 Search Java files clean**. The standard worktree command cannot discover the Git repository for ratcheting; application pom.xml was not changed to skip checks.
- `sdd.ts trace search STORY-001 STORY-006`: **26/26 AC IDs have tests**. This includes integration-test source, not evidence the database assertions ran.
- `git diff --check`: pass. Initial full FE regression: **1,354 passed, 2 failed, 75 files**, duration 1,117.15 s. Failures: Chatbot AC-003.1 hit the existing 15-second test limit (logged BUG-125), and Review Queue's BUG-100 case reached `settleDecision` before any decision mutation existed. Both selected cases passed separately on the original unchanged T-Solve branch (Chatbot: 1 passed/60 filtered; Review: 1 passed/9 filtered). This is evidence of nondeterminism, not a green full suite. The subsequent verification-only test changes are recorded below.

### Debug: FE regression synchronization, 06/11 12:29 (+07)

- Review Queue: a temporary diagnostic that queued a real TanStack mutation on the next microtask reliably failed at the helper's immediate empty-cache check. Awaiting the first real mutation before subscribing to its terminal state made that diagnostic pass (91 ms). The temporary helper-only test was removed; the existing BUG-100 user-flow case retains its cancellation, selection, confirmation, toast and total assertions. This improves synchronization; it does not yet establish the cause of every full-suite bulk-confirmation failure.
- Chatbot BUG-125: temporary timing instrumentation measured 4,931 ms for the first question and 9,003 ms for both; the selected rating case took 10,219 ms. This rating case now uses real user-event paste plus Enter for its two questions. Requests and rating assertions remain; the dedicated typing/keyboard cases are unchanged. Diagnostic console output was removed. No production Chatbot/Review code, timeout, shared code, dependency or acceptance criterion changed.
- `npm run test -- src/features/review/pages/ReviewQueuePage.test.tsx src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx -t 'AC-002.2 / AC-002.5|AC-003.1 shows Helpful and Not helpful' --maxWorkers=2 --pool=threads --configLoader=native --hookTimeout=60000 --reporter=verbose`: **2 passed, 69 excluded by name**, 25.83 s. Rating case 3,769 ms; bulk-confirmation case 9,601 ms.
- After these test edits: typecheck, format:check and lint passed (same existing Fast Refresh warning). Full FE regression rerun completed: **1,354 passed, 2 failed; 73 passed files, 2 failed; 932.97 s**. Only the unchanged My Solutions initial-table and filter-default cases failed, both unable to find the table named My Solutions. Chatbot and Review Queue, including the two adjusted cases, passed. Output: `frontend/.kan77-fe-regression-after-sync.log`. The full suite is still not green.
- Keep these verification-only changes separately reviewable from the KAN-77 product implementation; do not include unrelated feature behavior in its eventual commit.
- At 12:31, the ongoing rerun reported two additional failures in unchanged My Solutions tests (initial table AC-003.11 and filter defaults AC-003.22). Logged as BUG-126/127, owner taken from the existing My Profile bug records. Their full assertions and isolated reproduction are pending; do not describe them as confirmed product defects. Format/lint/typecheck overlapped the beginning of this run, so record that load when comparing results.
- The user explicitly requested retaining bug history for their report. The bug-log instructions now require keeping Fixed/Closed rows and reproduction evidence. No bug row was deleted.

### Remaining verification and integration

- Initially `mvn -B -o verify` failed with 24 Docker-related integration initialization errors after 552 then-current units passed. After full access, the new Search database suite passed all 15 tests with `-Dapi.version=1.44`; migration, query and rollback assertions now have real PostgreSQL evidence. A fresh whole-backend test/integration run is underway.
- Docker named-pipe access is now available. The remaining client/API discovery issue (BUG-129) is resolved per run by the documented docker-java API property, without changing project dependencies or global Docker settings.
- `npm run generate:api` now succeeds twice and leaves no generated diff; the earlier Orval subprocess blocker is cleared.
- Real populated browser flow requires identity's sign-in and the real VisibleScope membership provider. The approved default remains fail-closed, so a signed-in user currently receives an empty list until the adapter lands. DevAllVisibleScope is local-only, not a production replacement.
- The Ticket ID link still points to the existing 5.5 route, which mounts ReviewTicketDetail and calls getReviewTicketDetail. The corresponding GET `/api/v1/tickets/{ticketId}` backend is not in this snapshot; full list-to-detail acceptance also depends on that ticket-repository/review integration (KAN-135), beyond the list endpoint.
- Features remain in-progress. Do not mark KAN-77/KAN-147/KAN-227 passing before the remaining acceptance checks are resolved. The user's subsequent instruction explicitly authorizes publishing this work for review and continuing the next KAN while the integration checks remain pending.

### Next up

Collect the full FE result and check its failure independently, run database/contract checks where Docker and subprocesses are allowed, then verify real FE–BE access with the membership adapter. Commit only after the user's feature review, following the root project rule. Proposed eventual code message: `feat(search): serve and connect the ticket list (KAN-77)`; schema and approved-plan prerequisites must be split into their own scoped commits. No commits have been created in this session.

### User handoff for blocked checks

The user acknowledged the request to run the following outside the sandbox; no result has been supplied yet. An acknowledgement is not a passing test result.

In `T-Solve-kan77/backend`:

```powershell
mvn -B -o test-compile failsafe:integration-test failsafe:verify -DOLLAMA_BASE_URL=http://127.0.0.1:11434 -DOLLAMA_CHAT_MODEL=test
```

In `T-Solve-kan77/frontend`, after the full FE run ends (it has now ended):

```powershell
npm run generate:api
```

Run generation a second time to check determinism; compare generated changes without overwriting other planned module paths. Await these results and the real-data dependencies before marking the feature passing. Planning/report-only KAN-68/188/189 are excluded; cancelled KAN-100/138/227 remain included.

### Publication requested, 06/11 12:48 (+07)

The user explicitly requested a branch, push and PR, then continuing the remaining KAN issues. The current branch is already `feat/KAN-77-search-ticket-list-complete`; no extra duplicate branch is needed. Keep the feature in progress and describe the pending checks in the PR.

- `git ls-remote --heads origin feat/KAN-77-search-ticket-list-complete` failed to connect to GitHub through proxy 127.0.0.1. No remote branch or PR is confirmed.
- Repo hook dependencies were absent. Normal `npm ci --offline` installed the existing locked tools successfully, but Husky reported `git command not found` and did not install its hook launchers. No dependency declaration or lockfile changed.
- The scoped harness commit attempt, explicitly enabling the tracked `.husky` hooks, stopped with `cannot spawn .husky/pre-commit: No such file or directory`. No commit was created. Do not disable the hooks to publish.
- Gitleaks' staged prerequisite scan passed. The harness prerequisite files remain staged; product and verification changes remain in this worktree. The proposed PR description is saved beside the evaluation report.
- A GitHub integration was discovered but is not installed/connected. Its connection has not been assumed. Continue independent KAN-100 work while publication is blocked by the local hooks/network environment.

### Full-access recheck, 06/11 14:00 (+07)

- Docker CLI and GitHub are reachable; `npm run prepare` installed the real Husky launchers. `gh` is authenticated as the user's account. A live fetch confirms main is still 62bd608.
- Search SQL suite: 15 tests passed, no failures/errors/skips. KAN-100's separate row-guard suite also passed 30 tests; it remains in its own branch.
- API client generation passed twice, with identical, empty generated diffs.
- BUG-129 records Docker CLI success versus the old Java client's API discovery failure; `-Dapi.version=1.44` makes both suites pass. BUG-130 retains the still-reproducible Spotless/JGit linked-worktree discovery failure, including an unsuccessful GIT_DIR/GIT_WORK_TREE experiment. No check is disabled.
- Pending: the whole-backend fresh run, the previously reported unrelated My Solutions test failures, and real browser identity/membership/detail integration. The user has explicitly authorized publication for review with remaining checks recorded.

Fresh final results: `mvn -B -o test` passed **553/553**. A separate `mvn -B -o test-compile failsafe:integration-test failsafe:verify -Dapi.version=1.44 -DOLLAMA_BASE_URL=http://127.0.0.1:11434 -DOLLAMA_CHAT_MODEL=test` passed **53/53 ITs**. An initial combined unit/IT invocation incorrectly supplied the Ollama system properties to the two intentional missing-setting startup tests; splitting the invocations preserved those negative cases without any test/product change. The two My Solutions cases passed when independently rechecked (2 passed/46 name-filtered), but this is not a new green full-FE run.
