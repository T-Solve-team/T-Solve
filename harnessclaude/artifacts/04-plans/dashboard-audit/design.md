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

---

## Change 2026-11-07 — KAN-152: write and serve the audit log (STORY-002, STORY-003)

Decided with the user on 07/11 (spec Changelog): audit review decisions and CSV import only; table
`audit_log_entry` from the team DDL, adapted; build the backend read API of the existing contract; leave
be-foundation STORY-004 untouched and retire it later.

### Architecture

```
ReviewDecisionController → TicketDecisionServiceImpl.decide ─┐  (same @Transactional)
ImportController → ImportConfirmServiceImpl.importRows ──────┤  (same TransactionTemplate)
                                                             ▼
                            common/audit AuditService.record ── JdbcTemplate INSERT ──► audit_log_entry
                                                                                           ▲
AuditLogController GET /api/v1/audit-events → AuditLogServiceImpl → AuditLogEntryRepository┘ (analytics, read-only)
```

- **Writer in `common/audit`** (`AuditAction`, `AuditService`, `impl/AuditServiceImpl`). `common` holds no entity
  (AGENTS.md §1), so the writer inserts with `JdbcTemplate`, which joins the caller's JPA transaction through the
  shared `DataSource`. It checks `TransactionSynchronizationManager.isActualTransactionActive()` and
  `TSolveUser.current()` first and throws `IllegalStateException` without storing anything. `details` is
  serialised with the Spring `ObjectMapper` and inserted as `?::jsonb`. Time is `Instant.now()` (UTC).
- **Reader in `analytics`**: `entity/AuditLogEntry` (`@Immutable`, read only, `details` not mapped),
  `repository/AuditLogEntryRepository` (`JpaRepository` + `JpaSpecificationExecutor`, so optional filters need no
  null-typed JPQL parameters), `service/AuditLogService` + `impl/AuditLogServiceImpl` (`@Transactional(readOnly)`,
  filter parsing), `dto/AuditEntryResponse`, `mapper/AuditEntryMapper` (`summary` → `null` constant),
  `exception/InvalidAuditFilterException` (extends `MessageCodeException`, 400 with MSG39 or MSG06),
  `controller/AuditLogController` (`@PreAuthorize` ADMIN, returns `PagedModel`).
- **Filter parsing** mirrors `TicketReviewReadServiceImpl.historyRange` (strict `uuuu-MM-dd`, Vietnam-time
  `[from, to+1)`), plus "no date after today" using `Clock` in `Asia/Ho_Chi_Minh`. `action` arrives as a `String`
  and is matched against `AuditAction.valueOf` (unknown → MSG06), so Spring's own enum conversion never answers
  first. `sort` arrives as the raw list: empty / `createdAt` / `createdAt,asc` / `createdAt,desc`; any other → MSG06.
  `id` ascending is always appended as tie-breaker.
- **Callers**: `TicketDecisionServiceImpl.decide` records after `decisions.saveAll(history)` — one entry per ticket
  (`APPROVED→APPROVE`, `REJECTED→REJECT`, `CHANGES_REQUESTED→REQUEST_CHANGES`, target `TICKET`).
  `ImportConfirmServiceImpl.importRows` records before returning — `IMPORT`, target `PROJECT` + Project id,
  details `{importBatchId, created, skipped}`. Both run before the transaction commits, so a later failure rolls
  the entry back with the action.

### Data model — changeset `018-audit-log-entry.sql` (author `dashboard-audit`)

```sql
--changeset dashboard-audit:018-audit-log-entry
DROP TABLE audit_event;                      -- never written; replaced
CREATE TABLE audit_log_entry (               -- append only
    audit_log_entry_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id           UUID,                 -- no FK: dev / Keycloak users are not always in app_user yet
    actor_email        VARCHAR(254) NOT NULL,
    action             VARCHAR(50)  NOT NULL,
    target_type        VARCHAR(50)  NOT NULL,
    target_id          UUID         NOT NULL,
    details            JSONB,
    created_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_audit_log_entry_action CHECK (action IN ('APPROVE', … the ten AuditAction values …))
);
CREATE INDEX idx_audit_log_entry_created ON audit_log_entry (created_at);

--changeset dashboard-audit:018-audit-log-entry-append-only splitStatements:false
-- function + BEFORE UPDATE OR DELETE (row) and BEFORE TRUNCATE (statement) triggers raising an exception
```

Number 018 because PR #152 takes 017; renumber at merge if needed. The platform convention guard test is checked
for `audit_event` / the new table and updated if it lists them.

### Edge cases

| # | Story | Case | Expected | Test |
|---|-------|------|----------|------|
| 28 | STORY-002 | Batch decision of 500 tickets | 500 entries, one per ticket, same transaction | AuditRecordingIT |
| 29 | STORY-002 | Decision refused (wrong status, foreign Project, bad feedback) | No entry (thrown before `record`) | AuditRecordingIT |
| 30 | STORY-002 | Import fails after rows are saved (Ticket ID taken) | Transaction rolls back, no IMPORT entry; Failed batch still recorded | AuditRecordingIT |
| 31 | STORY-002 | `record` with no transaction / no user | `IllegalStateException`, no row | AuditLogEntryIT |
| 32 | STORY-002 | UPDATE / DELETE / TRUNCATE on the table | Database error, rows unchanged | AuditLogEntryIT |
| 33 | STORY-002 | Actor email logged | Never: no log line carries it | review |
| 34 | STORY-003 | `actor` with `%` or `_` | Matched literally (escaped LIKE) | AuditLogIT |
| 35 | STORY-003 | Entries at 23:59:59.999 +07:00 on `to` and 00:00 +07:00 the day after | First included, second excluded | AuditLogIT |
| 36 | STORY-003 | Two entries with the same `created_at` | Ordered by id ascending, stable across pages | AuditLogIT |
| 37 | STORY-003 | `to` = today in Vietnam while UTC is still yesterday | Accepted (today is computed in Vietnam time) | AuditLogServiceImplTest |
| 38 | STORY-003 | Non-Admin, or no user | 403 MSG08 / 401 MSG07, no query | AuditLogControllerTest |

### Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| An audit failure blocks the demo's review decisions (no `TSolveUser`, e.g. a Jira App principal) | High | Only `ReviewDecisionController` and `ImportController` reach the two callers today, both with a `TSolveUser`; the IT tests run them as such |
| Two audit specs (be-foundation STORY-004, this one) | Medium | Spec note + follow-up to retire STORY-004 |
| Changeset number clash with PR #152 | Low | 018 now; renumber at merge |

## Change 2026-11-09 — KAN-150/151: Knowledge Dashboard (STORY-004, STORY-005)

Decided with the user on 09/11 (spec Changelog): the five groups of SRS 9.1 from stored data, no ticket views; one
endpoint; gaps by the asker's Department; antd + CSS charts, no dependency.

### Backend (`vn.tsolve.analytics`, read only)

```
DashboardController GET /api/v1/dashboard ─► DashboardServiceImpl
   (@PreAuthorize PM or ADMIN)                 ├─ DashboardScope.resolve(user, departmentId, projectId)  → Scope
                                               ├─ DashboardPeriod.parse(from, to, clock)                 → [from, to+1) VN time
                                               └─ DashboardRepository (NamedParameterJdbcTemplate, one query per group)
```

- **Scope** = `Scope(Set<departmentPk> departments, UUID projectId|null)`; `null` departments = whole Workspace (Admin
  without filter). PM-only users: `app_user.department_id`; a different `departmentId`, a `projectId` of another
  Department, or no Department → `AccessDeniedException` (403 MSG08, AC-004.5). Ids are `public_id` UUIDs taken as
  `String` and parsed in the service (bad → 400 MSG06; unknown → `NotFoundException` MSG04).
- **Period** mirrors the audit log's `[from, to+1)` Vietnam-time parsing plus the 366-day cap; absent pair →
  `today-29 .. today` from the `Clock` bean; errors → `MessageCodeException` 400 MSG39.
- **Queries** — plain SQL, each filtered by the scope through `ticket → project → department`:
  - outcomes: `review_decision.decided_at` in period, `GROUP BY decided_at::date (Asia/Ho_Chi_Minh), project`;
    imports: `ticket.source <> 'SOLUTION_FORM' AND ticket.created_at` in period; per-day rows filled in Java for every
    day; `pendingReview` / `expired` = current `ticket.status` counts (`PENDING_REVIEW`, `EXPIRED` — the enum names
    in `ticket-review`).
  - coverage: current `ticket.status = 'PUBLISHED'` vs all tickets, per Department.
  - feedback: `ticket_feedback.updated_at` in period (the latest rating of each user counts); rates computed in SQL;
    top / lowest 5 `ORDER BY rate DESC|ASC, total DESC, title`.
  - gaps: `search_log` `result_count = 0`, `GROUP BY lower(trim(query))` (shown as the first-seen spelling),
    `department_id` in scope; `chatbot_answer` `outcome = 'NO_SOURCE'` joined to `app_user.department_id`, grouped
    the same way; `LIMIT 10`.
  - suggestions: `suggestion_result.commented_at` in period, `project_id` in scope; solved = `reporter_answer = 'YES'`.
- **Options**: Admin → active Departments and their active Projects; PM → their Department and its Projects
  (Project `name` = Jira project key).
- No query text is logged (AC-004.15). `@Transactional(readOnly = true)`.

### Frontend (`features/knowledge-dashboard`)

- `api/useKnowledgeDashboard.ts` wraps the generated `useGetKnowledgeDashboard`; `hooks/useDashboardFilters.ts` keeps
  `period` (`7d` | `30d` | `90d` | `custom`), `from`, `to`, `departmentId`, `projectId` in the URL with the
  shared list-URL helpers; custom range validation (MSG39) before any request.
- `components/`: `DashboardFilters`, `OutcomesSection` (per-day and per-Project CSS bars: `div` widths computed at
  runtime, the one allowed `style`), `CoverageSection`, `FeedbackSection`, `GapsSection`, `SuggestionsSection`,
  `BarList` (shared within the feature). antd `Statistic`, `Progress`, `Table`; MSG04 per empty section.
- `mocks/handlers.ts`: one hand-written `getKnowledgeDashboard` handler with data for every section and 400 MSG39.
- Ticket links only when the user has `PROJECT_MANAGER` (AC-005.9), through `paths.ticketDetail`.

| Decision | Chosen | Alternatives | Rationale |
|----------|--------|--------------|-----------|
| Groups | Five SRS groups, no ticket views | Add a view counter; minimal | User 09/11: views need another module's change |
| API | One `GET /api/v1/dashboard` | One per group | User 09/11: one query per filter change, one service |
| Gaps scope | Asker's Department | Admin only | User 09/11 |
| Charts | antd + CSS | `@ant-design/plots`, `recharts` | User 09/11: no FE Lead dependency approval needed |
| Filter options | In the response | New Department / Project list APIs | Decided in planning (builder check): no such API exists |
| Data access | `NamedParameterJdbcTemplate` aggregates | JPA entities per table | Cross-module read model, aggregates only; no entity of another module is mapped |

| # | Case | Severity | Handling | Test |
|---|------|----------|----------|------|
| D1 | PM asks for another Department | High | 403 MSG08 | controller + service test |
| D2 | PM without Department | Medium | 403 MSG08 | service test |
| D3 | Period with no data | Low | zeros, null rates, every day listed | IT |
| D4 | 366-day range | Low | 366 per-day rows | service test |
| D5 | Same query with other case / spaces | Low | one gap row | IT |
| D6 | Department deactivated | Low | still counted in figures, not in options | IT |
| D7 | Invalid custom range in the page | Medium | MSG39, no request | component test |
