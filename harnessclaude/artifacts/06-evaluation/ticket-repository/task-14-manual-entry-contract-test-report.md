---
module: ticket-repository
feature: manual-entry-contract
task: 14
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Test Report: ticket-repository — Task 14: OpenAPI contract for manual entry and its Drafts

This file holds the **shared test run** for KAN-133 (range `default: 042cc6c3..22e623cb`, HEAD `22e623cb`, branch
`feat/KAN-133-ticket-repository-manual-entry`). The test reports of Tasks 15–19 and of `import-ticket-list` Task 31
refer to it. The reviewer ran every command on 2026-11-15 (Windows, Git Bash). The working tree had only the
untracked `docs/diagrams/`, which is not part of the build.

## Backend: `mvn -B -Dapi.version=1.44 clean verify` (JBR 21 of IntelliJ, Docker 29.1.3)

| Phase | Result |
|-------|--------|
| Surefire (unit) | **1725 run, 0 failures, 0 errors, 0 skipped** |
| Failsafe (integration, Testcontainers PostgreSQL) | **555 run, 0 failures, 0 errors, 0 skipped** |
| `spotless:check` | passed |
| Build | **BUILD SUCCESS**, exit 0 |

KAN-133 test classes in that run:

| Class | Tests | Result |
|-------|-------|--------|
| `ticket/controller/ManualEntryControllerTest` | 9 | pass |
| `ticket/entity/ManualEntryTicketTest` | 5 | pass |
| `ticket/service/impl/ManualEntryValidatorTest` | 22 | pass |
| `ticket/service/impl/ManualEntryServiceImplTest` | 11 | pass |
| `ticket/service/impl/ManualEntryEvidenceTest` | 28 | pass |
| `ticket/service/impl/ManualDraftServiceTest` | 14 | pass |
| `ticket/service/impl/ManualEntryIT` | 12 | pass (real PostgreSQL; the races of AC-006.8 and AC-007.9 are run with a second connection that holds a lock) |
| `ImportPreviewServiceImplTest`, `ImportConfirmServiceImplTest`, `ImportDraftServiceImplTest`, `JiraImportServiceImplTest` (changed) | all | pass |
| `review/ReviewStorageIT` (changed: drops changeset 031 from the "main before Review" changelog) | all | pass |

## Frontend (Node v24.22.2, from `frontend/`)

| Command | Result |
|---------|--------|
| `npm run format:check` | pass ("All matched files use Prettier code style!") |
| `npm run lint` | pass, exit 0. One warning (`react-refresh/only-export-components`) in `src/features/tickets/components/TicketListFilters.tsx`, a file outside this range |
| `npm run typecheck` (`tsc -b --noEmit`) | pass |
| `npm run generate:api` | ran; `git diff --stat src/shared/api/generated` is empty, so the committed client matches the contract |
| `npm run build` | pass, exit 0 |
| `npm run test` (full suite, run alone after the backend build ended) | **exit 1: 99 files, 1636 passed, 1 failed** |

The one failure in the full run:
`src/features/ticket-import/components/ManualEntryForm.test.tsx > AC-006.12 checks the Evidence type and size before
sending (MSG34)`: `Error: Test timed out in 15000ms` (15270 ms). In that run the whole file took 83,997 ms for 12 tests.

Solo reruns of that file (`npx vitest run src/features/ticket-import/components/ManualEntryForm.test.tsx`):

| Run | Result |
|-----|--------|
| solo 1 | 12/12 passed |
| solo 2 | 12/12 passed |
| verbose, with `DraftsList.test.tsx` and `ImportTicketsPage.test.tsx` (3 files) | 40/40 passed. The MSG34 test took **11,993 ms**; the other ManualEntryForm tests took 1.7–9.97 s |

So the full-run failure is a load timeout of the kind BUG-213 describes (`docs/bug-log.md`, Open): the test passes
when its file runs alone. Still, this new test uses 80% of the 15 s budget even under light load, so it is the most
likely test to fail the next full run. See the Task 19 review, Issue 2.

## Traceability

`bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-006`: 12/12 acceptance criteria have a test.
`bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-007`: 9/9 acceptance criteria have a test.
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-008`: 8/8 acceptance criteria have a test. It also
notes "ids in tests but not in the spec: AC-007.8". Those are the `ticket-repository` AC-007.8 tests in the shared
preview service tests, so this is expected.

## Not run

- No test checks the Caddy routing (edge 29). `infra/tests/deploy-docker-config.test.sh` does not check body limits,
  and no Caddy binary or image was available to run `caddy adapt` or `caddy validate`. The Caddyfile was read, not run.
- No coverage tool is configured for this module, so no coverage figure is given.

## Task 14 specifics

The contract has no test of its own. It is checked by: `generate:api` with no diff; `typecheck` and `build` of the
generated client; the MSW handlers and `ManualEntryForm.test.tsx`, which read the generated `saveManualEntry` and
`saveManualDraft` requests (multipart, `entry` = `JSON.stringify`, `files` parts); and `ManualEntryControllerTest`,
which sends the same multipart shape to the backend.
