---
module: dashboard-audit
feature: audit-log-api
task: 3
verdict: pass
attempt: 1
date: 2026-11-10
reviewer: harnessclaude-reviewer (subagent)
iteration: 1
---

# Review: dashboard-audit — Task 3: Serve the audit log (KAN-152, for KAN-153)

## Summary

Read side of PR #156, range `35c102a9..60078bbe`: `GET /api/v1/audit-events` (`AuditLogController.entries` →
`AuditLogServiceImpl.findEntries` → `AuditLogEntryRepository` Specification + `AuditEntryMapper`). It meets AC-003.1
to AC-003.8 and follows the contract `getAuditEvents`. The contract changed only in its description, which no
longer says "MSW only", and the generated client was regenerated.

- **Access:** Admin only, through `@PreAuthorize`. A refused request never reaches the service.
- **Validation:** runs before any query, in this order: page and size, sort, action, the date pair.
- **Sort:** the field is a fixed whitelist (`createdAt` only), so no user input reaches `ORDER BY`.
- **Actor filter:** a bound, escaped LIKE (`\`, `%`, `_`) on `lower(actor_email)`.
- **Dates:** the bounds are `[from 00:00 +07:00, to+1 00:00 +07:00)` as Instants. "Today" is computed in
  `Asia/Ho_Chi_Minh` from an injectable Clock.
- **Tests:** `AuditLogIT` seeds rows into real pgvector PostgreSQL and asserts the exact ids, field values and
  order. It covers the day edges with milliseconds, the tie-break across pages, literal `%`/`_`, and the combined
  filters.

Later commits on main (`60078bbe..HEAD`) do not touch the feature's files or the audit operation in the contract.
The shared `MessageCodeException` only gained an extra constructor, which leaves its behaviour unchanged. Only minor
test-strength remarks and small differences from the MSW mock remain; no blocking issue.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Each criterion checked against the code (below). The rules match the contract table and the MSW handler: trimmed, case-insensitive Actor substring, and a blank Actor means no filter; exact action; inclusive Vietnam-time days; AND; `createdAt,desc` then `id,asc`; default size 20. |
| Completeness | pass | All 8 criteria implemented. Design edge cases 34–38 each have a test: literal `%`/`_`, 23:59:59.999 against the next 00:00, equal timestamps across pages, Vietnam "today" while UTC is still yesterday, and 403/401 with no query. |
| Correctness | pass | The ITs assert exact id lists and order. Full `mvn verify` result: see the test report. |
| Integration | pass | The response is `PagedModel<AuditEntryResponse>`, which gives `{content, page:{size,number,totalElements,totalPages}}`, matching `AuditEntryPage`/`PageMetadata`. Every entry has `id, createdAt, actor, action, resourceType, resourceId, summary` (`summary` is always null). The values `AuditLogPage.tsx` sends are all accepted. A 400 is a ProblemDetail with MSG39 or MSG06. The read uses the request's database (wider isolation is out of scope). |

### Per-criterion verification

- **AC-003.1** — `AuditEntryMapper` maps `actorEmail → actor`, `targetType → resourceType` and `targetId → resourceId`,
  and sets `summary` to null. The entity is an `@Immutable` mapping of `audit_log_entry`.
  - `AuditLogIT.mapsContractFields_AC_003_1` asserts every field, including `createdAt` as an Instant and the null `summary`.
  - `AuditLogControllerTest.servesAdminWithDefaultPaging_AC_003_1` shows an Admin gets 200 and the service is
    called with `page=0, size=20, sort=[]`. Issues 1 and 2 cover what these tests do not check.
- **AC-003.2** — `PageRequest.of(page, size, Sort.by(direction, "createdAt").and(Sort.by("id")))`, with size 20 by default.
  - Unit tests check the exact sort orders and that `createdAt`, `createdAt,asc` and `createdAt,desc` are accepted.
  - `AuditLogIT.ordersEqualTimestampsAcrossPages_AC_003_2` seeds `…01` and `…02` with the same timestamp and pages
    through them one row at a time. They come back in UUID byte order, which matches the mock.
- **AC-003.3** — The value is trimmed, lower-cased (`toLowerCase(ROOT)`) and escaped, then matched with
  `cb.like(cb.lower(actorEmail), %…%, '\\')`. A blank value adds no predicate.
  - The IT seeds `PM%_\name@…` and `pmXXname@…`.
  - The search `"  pm%_\NAME  "` returns only the literal row, `%` and `_` each match exactly one row, and `"   "` returns both.
- **AC-003.4** — `AuditAction.valueOf` (exact, case-sensitive) followed by `cb.equal`. In the IT, a REJECT filter returns only the REJECT row.
- **AC-003.5** — The filter is `from.atStartOfDay(VN) ≤ createdAt < (to+1).atStartOfDay(VN)`.
  - The IT seeds both edges of the range. Included: 17:00Z on the previous UTC day, and 16:59:59.999Z on the last
    day. Excluded: 17:00Z on the last day, and 16:59:59.999Z on the day before the range.
  - A filter combining actor, `IMPORT` and a day returns exactly `[end, start]` in descending order, which proves the AND and the order.
- **AC-003.6** — MSG39 for any of these: only one date given, a date not in the pattern, a date that is not a real
  day (STRICT `uuuu-MM-dd`), `from > to`, or `to` after today in Vietnam.
  - A unit test covers 9 invalid pairs, each with no repository call.
  - `acceptsTodayInVietnamWhileUtcIsYesterday` uses a Clock fixed at 2026-11-06T18:00Z.
  - The controller test shows the error is returned as 400 with `code=MSG39`.
- **AC-003.7** — MSG06 for any of these: `page < 0`, `size` outside [1, 100], more than one `sort`, any other sort
  value, or an unknown or empty action.
  - Each case has a unit test with no repository call, and the controller test shows the 400 MSG06 response.
  - A `page` or `size` that is not a number gets 400 MSG06 from the shared Spring handler, but this feature has no test for it (Issue 3).
- **AC-003.8** — `@PreAuthorize("hasRole('ADMIN')")`. With the real `SecurityConfig` and `GlobalExceptionHandler`,
  `AuditLogControllerTest` shows a non-Admin gets 403 MSG08 and an anonymous caller gets 401 MSG07, both with
  `verifyNoInteractions(service)`.

### Security checks

- **SQL injection:** no SQL is built from strings. Every filter is a bound Criteria parameter, LIKE metacharacters
  are escaped, and the user cannot choose the sort field.
- **Paging:** `size ≤ 100`. `PageRequest` uses a long offset, so a large page number returns an empty page.
- **Time zones:** stored `TIMESTAMPTZ` values are compared as Instants, and the date bounds use `+07:00`. Results do
  not depend on the JVM time zone.
- **Data exposure:** the response DTO leaves out `actor_id` and `details`.

## Traceability

```
✓ AC-003.1  AuditLogControllerTest, AuditLogIT
✓ AC-003.2  AuditLogServiceImplTest, AuditLogIT
✓ AC-003.3  AuditLogIT
✓ AC-003.4  AuditLogIT
✓ AC-003.5  AuditLogIT
✓ AC-003.6  AuditLogControllerTest, AuditLogServiceImplTest
✓ AC-003.7  AuditLogControllerTest, AuditLogServiceImplTest
✓ AC-003.8  AuditLogControllerTest
trace dashboard-audit (STORY-003): 8/8 acceptance criteria have a test
```

## Issues

No blocking issue.

1. **Minor (test strength)** — `backend/src/test/java/vn/tsolve/analytics/AuditLogIT.java:118`: `insert(...)` uses
   `id` as both `audit_log_entry_id` and `target_id`. Because of that, `mapsContractFields_AC_003_1` (line 53) would
   still pass if the mapper swapped `id` and `resourceId`. Suggested fix: seed a separate `target_id` and assert
   `resourceId` against it.
2. **Minor (test strength)** — `backend/src/test/java/vn/tsolve/analytics/AuditLogControllerTest.java:38-45, 64-73`
   stubs the service with `any()` or an empty page. Two things go unchecked:
   - that `actor`, `action`, `from`, `to` and a repeated `sort`, read from the catch-all `MultiValueMap` at
     `AuditLogController.java:36-38`, reach the service unchanged;
   - the JSON layout of `AuditEntryPage` with real data.

   Other list controllers follow the same pattern, so the risk is low. Suggested fix:
   `verify(service).findEntries(...)` with explicit arguments, and a test that returns a one-entry `PageImpl` and
   asserts `$.content[0]` and `$.page.totalElements`.
3. **Minor (test gap)** — AC-003.7: a `page` or `size` that is not a number (`page=abc`, `size=1.5`) is covered only
   by the shared Spring handler, with no test in this feature. Suggested fix: add a check that `page=abc` returns
   400 `code=MSG06` to `AuditLogControllerTest`.
4. **Info** — `AuditLogServiceImpl.java:54-57`: the sort direction is case-sensitive, so `createdAt,ASC` gets 400
   MSG06. This follows the spec ("asc or desc"), and the frontend and the mock use lower case only.
5. **Info** — `AuditLogServiceImpl.java:59-64`: an empty `action=` gets 400 MSG06, while the MSW handler
   (`frontend/src/features/audit-log/mocks/handlers.ts:25-26`) treats it as no filter. The mock also clamps an
   out-of-range `page`/`size` (`handlers.ts:41-46`); the backend answers 400, as AC-003.7 requires. The real screen
   never sends these values, so the difference is in the mock only.
6. **Info** — `AuditLogController.java:36`: the catch-all `MultiValueMap` parameter may show up as an extra
   parameter in springdoc's `/v3/api-docs`. The hand-written contract is the source of truth, and the generated
   client did not change.
7. **Info** — `018-audit-log-entry.sql` indexes only `created_at`. The Actor filter and the page count scan the
   table. This is fine at the current volume; consider a `pg_trgm` index if the log grows large.

## Assumptions made

- An upper-case sort direction counts as malformed under AC-003.7 ("createdAt (asc or desc)") (Issue 4).
- An empty `action=` counts as an action that is not an AuditAction (400 MSG06), not as an absent one (Issue 5).
- For AC-003.7, the shared Spring handler's 400 MSG06 for a `page` or `size` that is not a number is accepted
  without a dedicated test (Issue 3).
- For AC-003.1, the `AuditEntryPage` JSON layout is accepted because it is the project's standard `PagedModel`
  serialization (Issue 2).
- Workspace isolation is judged only as "the read uses the request's database" (spec Out of Scope, ADR-0002).
- The reviewer did not run Maven. The dispatcher's `mvn verify` result is in the test report.

## Test Report

See `task-3-audit-log-api-test-report.md`: full `mvn verify` on `main` `29a42b7e` (BUILD SUCCESS) — 1086 unit + 395 IT, 0 failures/errors/skips; the feature's classes all green; formatter clean on the PR's Java files (BUG-130 workaround).
