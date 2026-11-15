---
module: wiki
feature: wiki-folder-api
task: 4
attempt: 1
date: 2026-11-15
iteration: 1
---

# Test report: wiki — Task 4: wiki-folder-api

The dispatcher told the reviewer not to run `mvn`, `npm` or any test, because memory is tight and Docker is off. Nothing was run.

| Suite | Result | Source |
|-------|--------|--------|
| `WikiFolderServiceImplTest` | not run by the reviewer | The BUG-222 row says all 79 wiki unit and controller tests pass |
| `WikiFolderControllerTest` | not run by the reviewer | same |
| `WikiFolderIT` (Testcontainers) | pending: the orchestrator's Docker run | — |
| `audit-filters.test.ts` | not run by the reviewer | The commit message says the frontend checks pass |
| `mvn spotless:check`, and no diff from `npm run generate:api` | pending | — |

Traceability (`bun harnessclaude/hooks/sdd.ts trace wiki STORY-003`): 17/17 acceptance criteria have a test.

The pass verdict depends on `mvn verify` (including `WikiFolderIT`) and the frontend checks being green.
