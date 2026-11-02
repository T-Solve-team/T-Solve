---
module: triage
date: 2026-11-02
---

# Design: triage frontend history

Spec: artifacts/03-modules/triage/spec.md

## Architecture Overview

This plan covers only the KAN-149 web slice. The registered `/triage-history` route already checks
Project Manager. Replace its placeholder with a filter toolbar, DataTable and 10.2 detail popup.
Read the Project, answer, date and page state from the URL. Feature-local hooks call two generated
TanStack Query endpoints. The detail popup is keyed by the selected Jira issue key; closing or
switching selection must not show the previous issue's data. The existing mock registration includes
`triage/mocks/handlers.ts`, so no central file changes are required.

Mock mode contains results for the IT Project Manager's two managed Projects, the HR Project
Manager's Project and another Workspace. The handler checks the session role and Project access
before returning any content. It combines valid filters, sorts by `createdAt` descending with
`issueKey` as a stable tie-breaker, then slices for the requested page. The detail handler finds the
issue only inside the caller's accessible Projects and Workspace. Neither mock behavior nor the
route guard is a production security guarantee; the backend KAN-149 endpoint must enforce both.

## Data Model

The OpenAPI proposal exposes `SuggestionResultSummary`, `SuggestionResultDetail`,
`SuggestionResultPage` and `SuggestionWikiSource`. It reuses `PageMetadata`, `Problem` and
UUID project/document ids. `issueKey` is a stable opaque Jira key, matching the tracker's
one-result-per-issue constraint, so the FE does not depend on its internal BIGSERIAL
`suggestion_id`. `reporterAnswer` is nullable `YES`/`NO`, shown as No answer when null;
the answer filter's `NONE` means null. The summary returns a bounded plain-text excerpt.
The detail returns the full nullable suggestion and only Wiki source references that the viewer
may be told about; Ticket sources are represented by a count, not titles or links.

The proposed contract must be agreed with the tracker/triage backend owner before replacing MSW.
KAN-131 currently records a result but has no history read API; this feature does not claim one
exists. The tracker's current table has `sources_used`, `suggested_solution` and
`reporter_answer`; the backend will need to derive the proposed source counts and visibility.

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|-------------------------|-----------|
| Scope | One list plus read-only detail popup | List only | User chose both on 2026-11-02; the existing 10.2 popup completes the view flow |
| Filters | Managed Project, answer, paired inclusive Vietnam dates | Obsolete class/routing filters | SRS III.10.1 has Project/answer/date; KAN-149 removed classes and routing |
| Source display | Ticket source count, available Wiki document links | Ticket source links or raw source JSON | KAN-47 forbids exposing Ticket source links/details in the suggestion; no data leak in history |
| Contract | Two OpenAPI proposals, generated client, MSW | Hand-written fetch or wait for backend | FE task must remain runnable while KAN-131 is pending |
| Detail key | Jira issue key | Internal BIGSERIAL id | One result per issue; no numeric-id coupling in FE |
| Popup | Use a feature-local read-only presentation inside the existing FormModal shell, with both footer actions closing | Raw antd Modal or shared UI change | Raw Modal is banned and shared UI changes need FE Lead review; the two footer buttons are a kit limitation to verify visually |
| Jira link | HTTPS only from the API, no URL constructed from untrusted issue key | Blindly render API URL | Prevent executable schemes while preserving Jira navigation |
| Dates | Inclusive Vietnam calendar dates; invalid values stop the query and show MSG39 | Silently broaden or coerce | Matches the audit and search patterns; malformed shared links cannot broaden data |

## File Structure

```text
frontend/openapi/tsolve-api.yaml                 proposal for list/detail schemas
frontend/src/shared/api/generated/**            generated only
frontend/src/features/triage/
  pages/TriageHistoryPage.tsx                    list and popup selection
  pages/TriageHistoryPage.test.tsx
  components/HistoryFilters.tsx
  components/TriageResultDetailModal.tsx
  components/TriageHistory.module.css
  hooks/useSuggestionHistory.ts
  hooks/history-filters.ts
  hooks/history-filters.test.ts
  mocks/data.ts
  mocks/handlers.ts
  mocks/handlers.test.ts
```

No app, shared, test harness, package or route registration edits. `index.ts` exports the popup
only if another feature needs it; KAN-149 uses it privately.

## External Dependencies

- Existing React, antd, TanStack Query, React Router, dayjs, orval, MSW and Vitest/RTL only.
- KAN-131's real suggestion result persistence and Wiki source visibility are required for later
  backend integration, not for the mock FE slice.

## Edge Cases & Risks

Dimensions analyzed: user types, input extremes, timing, scale, state transitions, environment,
error cascades, authorization, data integrity, integration, compliance and business logic.

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-001 | User types | Employee or Resolver opens route or direct API | High | Guard/handler deny with MSG08, no rows | Task 1 |
| 2 | STORY-001 | Authorization | PM filters a Project they do not manage | High | 403/MSG08, no fallback to all Projects | Task 1 |
| 3 | STORY-001 | Authorization | Other Workspace has matching issue key | High | Excluded before counts, pagination and detail lookup | Task 1/2 |
| 4 | STORY-001 | Input extremes | Missing, impossible, partial or reversed date | High | MSG39; no unfiltered request | Task 1 |
| 5 | STORY-001 | Environment | Result at 16:59:59.999Z/17:00:00Z | High | Correct inclusive Vietnam day | Task 1 |
| 6 | STORY-001 | Scale | 0, 1 and 21+ matching results | High | MSG04 or correct pages/total | Task 1 |
| 7 | STORY-001 | State transitions | Filter on page 2; Back then reload | High | Reset page on change; restore URL filters | Task 1 |
| 8 | STORY-001 | Error cascades | 500 then retry | High | ErrorState; same filters on retry | Task 1 |
| 9 | STORY-001 | Data integrity | Equal timestamps, null suggestion/answer | Medium | Stable issue-key order; dash/No answer | Task 1 |
| 10 | STORY-001 | Input extremes | HTML-like issue summary or solution | High | React text rendering only | Task 1/2 |
| 11 | STORY-001 | Integration | HTTP or javascript Jira URL | High | No executable link | Task 1 |
| 12 | STORY-002 | Timing | Open A, close, open B while A request finishes | High | Only B shown, no stale detail | Task 2 |
| 13 | STORY-002 | Error cascades | 404/500 detail, retry | High | MSG04 or ErrorState; list remains | Task 2 |
| 14 | STORY-002 | Compliance | Unavailable Wiki document | High | MSG110, no link | Task 2 |
| 15 | STORY-002 | Business logic | Result with no suggestion and no answer | Medium | Read-only dash and No answer | Task 2 |
| 16 | STORY-002 | Authorization | Forged issue key from unmanaged Project | High | 404/MSG04, no existence leak | Task 2 |
| 17 | STORY-002 | Integration | Source data contains Ticket ids | High | Show count only, no Ticket source details | Task 2 |
| 18 | STORY-001 | Integration | Regenerate client twice | High | Second run produces no diff | Task 2 |

Open questions for the spec owner: none. The source display and filter choices are explicit in the
spec review; changes to them require a spec change before code.

## Risk Review — 2026-11-02 — Verdict: CAUTION

Architect: the two query hooks fit the existing feature boundary, but the backend read contract
must be agreed before real integration. Security: result access and Wiki availability are data-level
checks that the production backend must enforce. Performance: page 20 summaries and one detail
request avoid returning every full suggestion in the list. User: the read-only detail remains useful
for a no-suggestion result; keyboard and error states need tests. Devil's advocate: the old SRS
class/routing controls would be dangerous to revive, so they are explicitly excluded.

Agreement: ship a bounded mock-backed FE slice with explicit proposed endpoints and negative
authorization tests. Conflict: the UI kit has no read-only detail modal. The existing FormModal shell
is the minimal feature-local route; verify its two close actions in the browser and request FE Lead
review if the final Figma frame requires a dedicated kit component.

| Risk | Severity | Mitigation | Task |
|------|----------|------------|------|
| Mock access test mistaken for production authorization | High | Label proposal, negative fixture and direct-handler tests | Task 1/2 |
| Source details leak through history | High | DTO includes only Ticket count and available Wiki references | Task 2 |
| Backend contract drift | High | Generated client; agreement with KAN-131 owner before backend integration | Task 1/2 |
| Obsolete class/routing behavior reappears | High | Explicit out-of-scope and absence tests | Task 2 |

## Proposed Iteration

Current iteration 1 gets one Jira feature `suggestion-history` (KAN-149) with two implementation
stages: Task 1 list/filter/API mocks and Task 2 detail popup/verification. Both stages belong to
one code commit after the full frontend verify and user agreement. Branch:
`feature/KAN-149-triage-suggestion-history` from current `main`. No push, merge or PR is authorized.
