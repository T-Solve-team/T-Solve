---
module: jira-app
feature: solution-contract
task: 1
attempt: 1
iteration: 2
date: 2026-11-13
reviewer: main session (harnessclaude evaluate, run-tests)
---

# Test Report: jira-app — Task 1 "Contract" (KAN-126)

Branch `feat/KAN-126-jira-app-solution-form-v2`, head `221d53ca` (repo `default`, monorepo). The feature's code is at `f16c8a02`. Run on 2026-11-13 by the main session; the review is `task-1-solution-contract-review.md`.

## Results

| Check | Command (directory) | Result |
|-------|---------------------|--------|
| OpenAPI client | `npm run generate:api` (`frontend/`) | PASS. No diff in `src/shared/api/generated` |
| Web typecheck | `npm run typecheck` (`frontend/`) | PASS |
| Web format | `npm run format:check` (`frontend/`) | PASS |
| Backend format | `mvn spotless:check` (`backend/`) | PASS |

## Not run

- **Full `mvn verify`**: it ran on the branch head `221d53ca` and stopped in the unit-test phase with 1411 tests, 3 failures and 129 errors, all in `ai.client`, `identity.*`, `common.security.SecurityConfigCsrfOrderTest` and `ticket.client.JiraRestIssueSourceTest`. Their cause is the sandbox: WireMock and the Keycloak/Redis clients fail with `Unable to establish loopback connection` / `SocketException: Invalid argument: connect`. None of them is a test of this feature, and none of those files is changed by KAN-126. The CI backend job is the check for them.
- **Integration tests (Testcontainers)**: not run. Docker Desktop is not running on this machine (`Could not find a valid Docker environment`), so `failsafe` could not start PostgreSQL.
- **Coverage**: JaCoCo did not run, because `verify` stopped before the report. Traceability is checked below instead.
- **Web `lint`, `test`, `build`**: not run for this task. Its only changes under `frontend/` are the OpenAPI file and the generated client; the regenerated client has no diff and the typecheck passes. The frontend CI shard failure on this PR is `src/app/dev/mock-mode.test.tsx` from `main`, not this task.

## Traceability

`bun harnessclaude/hooks/sdd.ts trace jira-app STORY-001 STORY-002 STORY-004 STORY-005 STORY-006 STORY-007 STORY-008 STORY-009`: **34/35** criteria have a test. The one without is AC-005.3 (see Task 2): its tests are in `backend/src/test/java/vn/tsolve/ticket/`, outside the module's trace paths. The contract has no criterion of its own; its check is the generated client.
