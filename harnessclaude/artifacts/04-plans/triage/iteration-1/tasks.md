---
module: triage
date: 2026-11-02
total_tasks: 1
owner: Dev
---

# Tasks: triage iteration 1

Spec: artifacts/03-modules/triage/spec.md
Master: artifacts/04-plans/triage/design.md

## Task 1: Suggestion History list and detail (KAN-149)

**Story:** STORY-001, STORY-002
**Feature:** suggestion-history
**Description:** Deliver the complete frontend-only 10.1 list and 10.2 read-only popup on MSW.
Stage V1: add the proposed list/detail OpenAPI contract and regenerate; write failing tests
for date parsing, managed-Project/Workspace filtering, answer filter, newest-first order,
20-row pagination and direct-request authorization; then implement the mock handlers,
history query and list/filters. Stage V2: write failing tests for popup loading, no-suggestion
state, source visibility, 404/500/retry, changing selected issue, unsafe Jira URL and the
absence of class/routing controls; then implement the detail hook and popup. Cover
AC-001.1–AC-001.12 and AC-002.1–AC-002.8, including all High rows 1–18 of the master
design's Edge Cases & Risks table. Run trace, full frontend checks, deterministic
regeneration and browser verification at 1366 × 768. Report changed files, results and
proposed commit before committing.

**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`
- Generate only: `frontend/src/shared/api/generated/**`
- Modify: `frontend/src/features/triage/pages/TriageHistoryPage.tsx`
- Modify: `frontend/src/features/triage/mocks/handlers.ts`
- Create: `frontend/src/features/triage/pages/TriageHistoryPage.test.tsx`
- Create: `frontend/src/features/triage/components/HistoryFilters.tsx`
- Create: `frontend/src/features/triage/components/TriageResultDetailModal.tsx`
- Create: `frontend/src/features/triage/components/TriageHistory.module.css`
- Create: `frontend/src/features/triage/hooks/useSuggestionHistory.ts`
- Create: `frontend/src/features/triage/hooks/history-filters.ts`
- Test: `frontend/src/features/triage/hooks/history-filters.test.ts`
- Create: `frontend/src/features/triage/mocks/data.ts`
- Test: `frontend/src/features/triage/mocks/handlers.test.ts`

**Dependencies:** Current triage spec approved through `sdd.ts approve triage`; existing
frontend foundation. The real KAN-131 read API is not required for mock mode.
**Estimated size:** medium.
**Verification:** `bun harnessclaude/hooks/sdd.ts trace triage`; in `frontend/`,
`npm run format:check`, `npm run lint`, `npm run typecheck`, `npm run test`,
`npm run build`, and `npm run generate:api` twice with no second-run diff; browser
check as Project Manager and forbidden user. One KAN-149 code commit and one harness
commit only after user agreement.
