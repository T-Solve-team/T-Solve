---
module: wiki
feature: wiki-browse-api
task: 2
attempt: 1
date: 2026-11-15
iteration: 1
---

# Test report: wiki — Task 2: wiki-browse-api

The dispatcher told the reviewer not to run `mvn`, `npm` or any test: memory is tight and Docker is off. Nothing was executed.

| Suite | Result | Source |
|-------|--------|--------|
| `WikiBrowseControllerTest`, `WikiDocumentControllerTest`, `WikiBrowseServiceImplTest`, `WikiSearchServiceImplTest` | not run; the commit message says they pass | commit `24df32dd` |
| `WikiBrowseIT` | not run; expected to **fail** in `listsOnlyViewableSpacesByNameAndComputesManagerAccess_AC_002_1` (duplicate key on `space.department_id`) | static reading |
| `WikiSearchIT` | not run; expected to **fail** in `filtersAccessStatusAndDeletedDepartmentsForBothSearchPaths_AC_002_7_AC_002_8` (same cause) | static reading |
| `mvn spotless:check` | not run | — |

Traceability (`bun harnessclaude/hooks/sdd.ts trace wiki STORY-002`): 21/21 acceptance criteria have a test.
