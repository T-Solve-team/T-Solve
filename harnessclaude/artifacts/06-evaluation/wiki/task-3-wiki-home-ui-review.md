---
module: wiki
feature: wiki-home-ui
task: 3
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: wiki — Task 3: Wiki Home and Wiki Document screens

Commit reviewed: `9c72f9bd`. Contract: spec STORY-002 (AC-002.14 to AC-002.20, and AC-002.21 for the three buttons only).

## Summary
11.1 Wiki Home and 11.2 Wiki Document are built as the spec says, and each AC has a test with real assertions.
- Wiki Home shows, in order: the Company-wide view grouped by Space name, the Spaces with their access level, the search box, the search results, then the selected Space's folder tree and documents table.
- `?space=` and `?folder=` are the source of truth.
- A search over 200 characters shows MSG02 and sends no request; blank text sends none either.
- Empty tables use the `DataTable` default, MSG04.
- 404 MSG110 shows the catalog text "This document is no longer available."
- Content is rendered as React text, so no HTML is injected.
- The Knowledge Manager buttons show only when the selected Space's `accessLevel` is `KNOWLEDGE_MANAGER`. "New folder" is disabled until Task 5.
- MSW handlers use the generated factories; no API code is hand-written.
- The `src/app/router/route-table.test.tsx` edit only removes the two placeholder rows, as that test's own comment asks.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-002.14 to AC-002.20 and the button part of AC-002.21 are met; a stale `?space=` the user cannot view triggers no browse request |
| Completeness | pass | Every AC of the task is named by a test in `WikiHomePage.test.tsx` or `WikiDocumentPage.test.tsx` |
| Correctness | pass (pending run) | Tests not run (dispatcher's instruction). Reading them, they would fail if the behaviour broke: column order, heading order, the request counters for AC-002.15 and AC-002.18, the URL params and the MSG110 text |
| Integration | pass | Uses the hooks generated from Task 2's contract; Download links to `/api/v1/wiki/documents/{id}/file` (Task 6) as a same-origin relative path, matching `http.ts` (AC-011.1) |

## Traceability
```
✓ AC-002.14 … ✓ AC-002.18, ✓ AC-002.21  frontend/src/features/wiki/pages/WikiHomePage.test.tsx
✓ AC-002.19, ✓ AC-002.20                frontend/src/features/wiki/pages/WikiDocumentPage.test.tsx
trace wiki (STORY-002): 21/21 acceptance criteria have a test
```

## Issues
1. minor `frontend/src/features/wiki/pages/WikiHomePage.tsx:116`: a `?folder=` that is not a folder of the selected Space gets 404 MSG04 from the API. The table shows it as an error with "Try again", which can never succeed. Fix (optional): on a 404 for documents, clear `folder` from the URL or show the empty state.
2. minor `frontend/src/features/wiki/hooks/useWikiHome.ts:19`: the submitted search is also kept in the URL as `?q=`, which the spec does not ask for (AC-002.17 names only `space` and `folder`). It is harmless and tested. Fix: none, unless the user wants it removed.

## Assumptions made
- A `?q=` URL parameter beyond AC-002.17 is accepted as a design choice, not scope creep.
- A disabled "New folder" button counts as "shown" for AC-002.21. Task 5 wires it.
- MSG02 below the box with `field_name: 'Search'` meets AC-002.18.

## Test Report
See `task-3-wiki-home-ui-test-report.md`. No test was run (dispatcher's instruction). The pass depends on the orchestrator's `npm run format:check`, `lint`, `typecheck`, `test`, `build` and a `generate:api` with no diff being green.
