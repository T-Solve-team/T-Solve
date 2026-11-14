---
module: triage
date: 2026-11-02
---

# Design: triage iteration 1

Spec: artifacts/03-modules/triage/spec.md
Master: artifacts/04-plans/triage/design.md

## Architecture Overview

Iteration 1 delivers the KAN-149 frontend slice only: 10.1 Suggestion History and the read-only
10.2 Triage Result Detail popup. The existing route and mock registration remain. A generated
list query and detail query use the proposed OpenAPI contract. Feature-local MSW data supports
filtering, paging, role/Project checks and unavailable sources; production authorization and
the backend read API remain separate work.

## Key Decisions

- One feature `suggestion-history` under Jira KAN-149, with list and popup built in two
  test-first stages and verified together before its one code commit.
- Use managed Project, answer and paired inclusive Vietnam dates as URL filters.
- Show only a Ticket source count and available Wiki links in detail. No class or routing controls.
- Reuse the existing UI kit and generated API pipeline. The read-only popup uses the available
  FormModal shell; visual verification must check the two close actions.

## Edge Cases & Risks

Master design rows 1–18 apply. The one task includes tests for every High row: role and
Workspace scope, forged Project/detail access, malformed dates, pagination, retry, stale detail,
unsafe Jira URLs, unavailable Wiki sources, Ticket source exposure and generated-client drift.
The master risk verdict is CAUTION until the proposed backend contract and production
authorization are implemented.

## Change 2026-11-04 — backend

KAN-149 now needs FE and BE. Task 2 aligns the FE contract and mocks with the Project key, no
issue summary and a nullable Jira URL; ids keep the session's id type. Task 3 adds the backend list/detail in `vn.tsolve.triage` after
KAN-117 is on `main`. Master design "Backend history API" and rows 19–24 apply.

## Change 2026-11-07 — AI Solution Suggestion job

Task 4 (KAN-47) adds the `triage-suggestion` worker job of STORY-005. It needs search Task 20 and tracker
Task 14 on the same branch. Master design "AI Solution Suggestion job — 2026-11-07" and rows 25–38 apply.

## Change 2026-11-08 — triage implements `SuggestionSearch`

Task 4 is re-planned: `main` already runs the suggestion job (KAN-22), so triage implements the tracker's port
`SuggestionSearch` and deletes its stand-in. Master design "AI Solution Suggestion — 2026-11-08" applies.
