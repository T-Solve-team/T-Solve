---
module: dashboard-audit
date: 2026-10-31
status: draft
---

# Design: dashboard-audit

Spec: artifacts/03-modules/dashboard-audit/spec.md

This design covers only KAN-153, the frontend audit log. The user approved the current spec on
2026-10-31; `sdd.ts diff dashboard-audit` confirms the recorded approval matches it.

## Architecture Overview

Replace AuditLogPage's placeholder with the existing DataTable and a filter toolbar. The page reads
Actor, action, from, to, page and sort from the URL. A feature hook calls the generated audit query.
The existing MSW registration already imports this feature's handlers, so no app registration changes
are required. The production backend endpoint remains a proposal.

Use the existing Admin route and session/site error handling. Keep server data in TanStack Query;
keep only unfinished Actor input and unfinished date selection local to the toolbar. Back/reload
restore applied filters from the URL. An invalid URL date range shows MSG39 and disables the list
query; it must not fall back to an unfiltered request or display stale rows as valid results.

## Data Model

No database changes. OpenAPI defines AuditEntry, AuditAction, AuditEntryPage and the getAuditEvents
query parameters exactly as the approved spec. Reuse the existing page metadata/ProblemDetail
schemas rather than introducing a second paging or error format. The generated client and model
barrels are the only source of API types.

Mock fixtures add a private Workspace key solely for filtering, removed from API responses. Include
more than 20 entries, all ten actions, multiple actor emails, null summaries, equal timestamps and
another Workspace's entries. Use synthetic data only. Actor search is trimmed, case-insensitive
substring matching; action is exact; all filters combine with AND before sorting/paging.

Date parameters are calendar days. Validate their exact YYYY-MM-DD shape and real calendar values,
then validate the pair, ordering and Vietnam-time today. Convert a valid range to start-of-first-day
and start-of-day-after-last-day with +07:00. Match `start <= createdAt < end` so fractional seconds at
the end of the selected day are included. Picker calendar values must not be converted through the
browser's timezone before storing them in the URL.

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| UI | Existing DataTable, cells, LoadingState, ErrorState, antd Input.Search/Select/DatePicker.RangePicker and Flex | Custom table or new UI dependency | Required existing UI kit; no new dependency |
| Actor | Email text search, submitted through Input.Search | User selector | User chose text search; historical actors do not require a user-options API |
| Date range | Unrestricted initially; inclusive Vietnam days; no future dates | Default last 30 days | User chose unrestricted default |
| API | OpenAPI proposal and generated TanStack Query hook, MSW implementation | Hand-written fetch or backend work | User chose frontend only and MSW; follows guideline 06 |
| List state | Existing useListSearchParams for paging, Actor and action; feature-local useSearchParams update for the date pair | Shared hook change or independent from/to updates | Existing hook sets one filter at a time; date pair must update atomically with page reset |
| Query | Feature hook forwards generated options and disables invalid date queries; keep previous data only for valid requests | New query/cache infrastructure | Existing query pipeline already handles background marking and errors |
| Workspace | Same-origin API, no tenant request fields; mock scoping before response | Workspace selector/header | Existing architecture and approved AC-001.10 |
| Ordering | Default createdAt descending, stable id tie-breaker | Client-side sorting of one returned page | Server-style pagination requires sorting before slicing |
| Scope | One feature key, audit-log, for the complete read/filter screen | Separate feature/commit for each filter | All 17 criteria belong to one user story and one Jira issue |

Decided in planning: use feature-local `hooks/audit-filters.ts` for pure date/action parsing so the
page, toolbar and mocks can share matching rules without modifying shared code (builder check).
Action display labels are a small feature-local map of the existing enum; they are ordinary labels,
not new catalog messages. Missing Summary uses cells.text's existing empty-value behavior.

## File Structure

Planned application files, not yet written:

```text
frontend/openapi/tsolve-api.yaml
frontend/src/shared/api/generated/                 regenerated only
frontend/src/features/audit-log/
  pages/AuditLogPage.tsx
  pages/AuditLogPage.test.tsx
  components/AuditLogToolbar.tsx
  components/AuditLogToolbar.module.css
  hooks/useAuditLog.ts
  hooks/audit-filters.ts
  hooks/audit-filters.test.ts
  mocks/data.ts
  mocks/handlers.ts
  mocks/handlers.test.ts
```

Keep routes.ts, index.ts, app registration and shared UI unchanged. Tests sit inside the module's
existing paths. Do not import another feature's private mock state or helpers.

## External Dependencies

Only the already-installed React, React Router, TanStack Query, antd, dayjs, orval, MSW and Vitest/RTL.
No dependency installation, backend service or external integration is needed for mock mode.

## Edge Cases & Risks

All 12 dimensions apply to this slice; persistence/migration and monetary subcases do not apply
because the feature is a read-only frontend. The proposed verification stages below cover each row.

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-001 | User types | Admin and Admin with another role | High | Existing role union admits both | V3 page guard |
| 2 | STORY-001 | User types | Employee or signed-out visitor | High | Guard prevents audit fetch; existing login/forbidden flow | V3 page guard |
| 3 | STORY-001 | Input extremes | Actor is whitespace or mixed-case email fragment | Medium | Trim; blank clears; case-insensitive match | V1 mocks, V2 page |
| 4 | STORY-001 | Input extremes | Impossible day, reversed/partial/future range in URL | High | MSG39, no invalid or unfiltered fallback query | V1 date parser, V2 page |
| 5 | STORY-001 | Timing | Slow first response, later refetch | Medium | Loading first; valid prior data may stay during refetch | V2 page |
| 6 | STORY-001 | Timing | Rapid filter changes and browser Back | High | Query key/URL match current filters; no stale-filter result | V2 page |
| 7 | STORY-001 | Scale | Zero or one matching row | Medium | MSG04 or one readable row | V1 mocks, V2 page |
| 8 | STORY-001 | Scale | 21+ rows and page boundary | High | Filter/sort then slice; correct totals and retained filters | V1 mocks, V2 page |
| 9 | STORY-001 | State transitions | Change a filter on page 2 | High | Reset to page 1 | V2 page |
| 10 | STORY-001 | State transitions | Clear date range; reload/back restores applied state | High | Atomic pair removal/restoration, no transient invalid fetch | V2 page |
| 11 | STORY-001 | Environment | Browser outside Vietnam; timestamp at 16:59:59.999Z | High | Include correct Vietnam calendar day | V1 date parser/mocks |
| 12 | STORY-001 | Environment | Keyboard interaction and narrow viewport | Medium | Visible labels; usable controls and horizontal table scrolling | V3 browser |
| 13 | STORY-001 | Error cascades | Network/500 error then retry succeeds | High | Existing ErrorState; retry retains query filters | V2 page |
| 14 | STORY-001 | Error cascades | 401 or unavailable-site response | High | Existing global session/site flow owns it | V3 integration |
| 15 | STORY-001 | Authorization | Non-Admin calls mock endpoint directly | High | 401/MSG07 or 403/MSG08 | V1 mocks |
| 16 | STORY-001 | Authorization | URL includes a forged Workspace filter | High | Application never forwards tenant selectors; fixtures scoped independently | V1 mocks, V2 request |
| 17 | STORY-001 | Data integrity | Equal timestamps or nullable Summary | Medium | Stable id tie-breaker; empty Summary rendered safely | V1 mocks, V2 page |
| 18 | STORY-001 | Data integrity | Target/actor contains markup-like text | High | Render as text through kit, no HTML interpretation | V2 page |
| 19 | STORY-001 | Integration | Contract regenerated twice | High | Second generation has no further output changes | V3 generation check |
| 20 | STORY-001 | Integration | Invalid date reaches mock endpoint directly | High | 400/MSG39, never broaden query | V1 mocks |
| 21 | STORY-001 | Compliance | Another Workspace has a matching actor/action | High | Other Workspace's fixture excluded before pagination/totals | V1 mocks |
| 22 | STORY-001 | Compliance | Audit data appears in fixtures or browser | Medium | Synthetic fixtures only; no extra console logging | V3 review |
| 23 | STORY-001 | Business logic | Combine all three filters with no matches | High | AND semantics; successful empty page | V1 mocks, V2 page |
| 24 | STORY-001 | Business logic | No date filters and clearing Actor/action | Medium | Unrestricted only within current Workspace | V1 mocks, V2 page |

## Risk Review — 2026-10-31 — Verdict: CAUTION

The review is performed inline; no backend isolation is being implemented in this slice.

| Topic | Architect | Security | Performance | User | Devil's advocate | Resolution |
|-------|-----------|----------|-------------|------|------------------|------------|
| Contract proposal | Fits existing generated pipeline | Production server must authorize | Page bounded to 20 rows | Screen works before backend | Static fixtures alone would not exercise integration | Keep generated client plus MSW; label proposal clearly |
| Workspace scope | Host owns scope | UI/mock is not a production boundary | Filter before totals and slicing | Only own Workspace's events | A mock passing is not server proof | Negative fixtures and request assertions; backend guarantee remains out of scope |
| Dates | Shared app timezone | Invalid URL must not broaden query | Constant-time validation | Inclusive days and visible MSG39 | Browser timezone conversion is easy to get wrong | Calendar strings; exclusive next-day bound; boundary tests |
| UI surface | Reuse kit | Read-only text rendering | No polling or new dependency | Loading/error/empty and labels | No popup required | Existing components and one feature |

Agreement across at least four perspectives: reuse the existing pipeline, stay read-only, and test dates
and Workspace scoping. The tension is shipping a frontend before a real backend exists; keep the mock
demo useful while explicitly retaining backend authorization/isolation as an integration requirement.

| Risk | Severity | Mitigation | Task |
|------|----------|------------|------|
| Mock behavior mistaken for production tenant enforcement | High | Explicit contract boundary; negative Workspace fixture; no backend completion claim | V1/V3 |
| Date errors silently expand visible range | High | Disable invalid query, show MSG39 and test boundaries/URL correction | V1/V2 |
| Contract drift | High | Regenerate client, use generated types and verify deterministic second generation | V1/V3 |

## Proposed Implementation and Verification

Propose one active iteration (iteration 1, as progress.json records), one feature `audit-log` and one
code commit for KAN-153. The following are stages of that feature, not separate feature commits:

1. V1: define the OpenAPI proposal and generate types; write failing date/filter/mock tests, then implement
   the pure filter helpers and realistic handler. Cover all dates, ordering, paging, authorization and
   cross-Workspace rows from the edge table.
2. V2: write failing page tests, then replace the placeholder with the toolbar, query hook and table.
   Cover AC-001.1 through AC-001.17 through user-visible behavior and intercepted requests.
3. V3: run all required frontend checks, regenerate again without further differences, verify the screen
   in a real browser and review the diff. Stop to report the feature and proposed commit to the user.

Only after design and this grouping are approved, write the iteration-scoped design/tasks and register
the pending feature through sdd.ts. Tasks will reference STORY-001 and the exact files listed above.

## Builder Check

Reviewed against the approved spec, module paths, architecture and the existing source. No new product
decision is required. Minor implementation choices resolved above: generated model reuse, feature-local
parser, atomic date URL update, action-label map and empty Summary rendering. The module definition's
legacy references to popup 9.3 do not override the approved spec or fe-foundation's D18/CR-15 removal.

Before coding, use a Jira branch `feat/KAN-153-dashboard-audit-view-filter` from current main following
the team's branch rule. Preserve existing unrelated changes. No push, merge or PR is authorized.
