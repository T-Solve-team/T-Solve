---
module: ticket-repository
feature: manual-entry-tab
task: 17
verdict: pass
attempt: 2
date: 2026-11-14
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 17: Manual entry tab

## Summary
Commit default@1f040bed turns on the Manual entry tab (`ManualEntryForm`). The tab is read from `?tab=` and a Draft
from `?draftId=`. Save shows MSG32, clears the form and removes `draftId` from the URL. Save as draft shows MSG41 and
keeps the id. Server `fieldErrors` appear under their fields (`solution` under Symptoms), and MSG31 appears under
Ticket ID. Any other rejection appears as an Alert. Both saves refresh the Drafts list.

The scoped tests, typecheck and lint pass, and the trace is 14/14. One CRITICAL defect remains: a reopened Draft can
show stale fields. The form loads a Draft through a cached query that Save as draft never invalidates. Reopening the
same Draft later in the session shows the version from before the last Save as draft. A further Save as draft then
overwrites the newer content on the server. This breaks AC-006.11 ("every field as saved") and can lose data.
**FAIL.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | fail | AC-006.11 is broken when the same Draft is reopened after a Save as draft (CRITICAL 1). Everything else behaves as the spec says. |
| Completeness | pass | AC-006.9, AC-006.11 and AC-006.14 each have tests that name them and check them. No EXTRA behaviour. |
| Correctness | fail | Tests are green, but CRITICAL 1 stands. |
| Integration | pass | Calls only the generated `useCreateManualTicket`, `useSaveManualTicketDraft` and `useGetManualTicketDraft`. The request and response shapes match the Interface Contracts. The backend 409 carries `fieldErrors[{field: ticketId}]` (`ManualTicketServiceImpl.java:224` → `GlobalExceptionHandler.java:183-185`), so MSG31 under Ticket ID also works against the real API. |

### Stage 1 — spec compliance

| # | AC | Status | Evidence |
|---|----|--------|----------|
| 9 | AC-006.9 Save as draft: only the Project is checked; MSG41 and the Draft's id | PASS | `ManualEntryForm.tsx:140-162`: validates only `projectId`, then MSG41, then `setDraftId(saved.previewId)`, then the Drafts list is invalidated. Tests `AC-006.9 Save as draft shows MSG41, lists the Draft and keeps its id…` (the second request carries `draftId`) and `AC-006.9 … without a Project shows MSG01 under Project and sends nothing`. |
| 11 | AC-006.11 the web app shows the Draft's Project and every field on the Manual entry tab | PARTIAL, see CRITICAL 1 | `ManualEntryForm.tsx:35-55`, `DraftsList.tsx:69-75`, `ImportTicketsPage.tsx:24`. Tests `AC-006.11 a reopened Draft fills every field` (all 10 fields plus the tab is selected) and `AC-006.11 Open on a Manual Draft goes to the Manual entry tab with its id`. The first load is correct. A reopen after Save as draft uses stale cache. |
| 14 | AC-006.14 each message next to its field, input kept; on success MSG32 and the form cleared | PASS | `ManualEntryForm.tsx:58-68,99-103,120-133`. Four tests cover it: field errors for Title, resolvedDate and `solution` with the values kept; the taken Ticket ID → MSG31 under Ticket ID; 403 → Alert with MSG08, input kept; success → MSG32, fields cleared, `draftId: null` sent. |

Other checks requested by the dispatcher:
- **Messages:** every message comes from `msg` or `showMessage` with catalog codes. No text is typed by hand.
- **Data layer:** generated hooks only, with no hand-written fetch. Server data is not copied into state; only the
  `previewId` returned by the mutation is held. Mutations use `meta.handlesOwnErrors`, and a 401 is left to the
  global MSG07 dialog (`:101`).
- **Drafts list refresh:** `getListImportDraftsQueryKey()` is invalidated after both Save and Save as draft
  (`:89-90,132,156`).
- **Loading and error states for the Draft load:** `LoadingState` and `ErrorState` with Retry (`:40-41`).
- **resolvedDate:** future days are disabled against today in `Asia/Ho_Chi_Minh` (`:28-29`; the picker's local
  calendar date is compared with the Vietnam date, which is correct). The date is sent as `YYYY-MM-DD` (`:93`). An
  invalid stored string becomes an empty field (`:47,52`).
- **URL handling:** `?tab=` drives `activeKey`, with CSV as the default. `?draftId=N` loads the Draft, and a
  non-numeric value gives an empty form. After a successful Save, `draftId` is deleted with `replace` (`:125-131`),
  and the new key remounts an empty form.
- **SolutionFormModal:** the diff only adds `export` and a doc comment to `SolutionFields`. Its behaviour is
  unchanged, and the existing SolutionFormModal tests are green.
- **MSW:** the three endpoints, the MANUAL rows in the Drafts list and Delete are consistent with the contract.
  There is one status-code mismatch (Informational 5).
- **AGENTS.md rule 10:** `git diff 1f040bed~1 1f040bed -- src/app src/shared` is empty. Rules 2 and 3 hold, since
  imports go only through `@/shared/*` and the feature's own folders.
- **import-ticket-list AC-008.1:** the reworded criterion (Changelog 2026-11-14) matches the updated test in
  `ImportTicketsPage.test.tsx:63-71`.

## Traceability
```
✓ AC-006.1 … AC-006.8, AC-006.10, AC-006.12, AC-006.13 — backend tests (Tasks 14–16)
✓ AC-006.9  frontend ManualEntryForm.test.tsx + backend
✓ AC-006.11 frontend ManualEntryForm.test.tsx + backend
✓ AC-006.14 frontend ManualEntryForm.test.tsx
trace ticket-repository (STORY-006): 14/14 acceptance criteria have a test
```

## Issues

CRITICAL:
1. `frontend/src/features/ticket-import/components/ManualEntryForm.tsx:38-43` together with `:153-157`: a reopened
   Draft can show the fields as they were before the last Save as draft.
   - **Cause:** the Draft comes from `useGetManualTicketDraft(draftId)` (staleTime 30 s, gcTime 5 min in
     `shared/api/query-client.ts:66-67`). The form takes the data only once, as `initialValues` at mount. Save as
     draft invalidates the Drafts list but never `getGetManualTicketDraftQueryKey(id)`.
   - **How to reproduce:** open Draft A from the Drafts list and change the Title. Click Save as draft. Open Draft B,
     then open Draft A again, or leave the page and come back within 5 minutes. The cached query is not pending, so
     the form mounts at once with A's old fields. A refetch after 30 s does not update a mounted form either.
   - **Impact:** a further Save as draft writes the old content over the newer saved version. That is silent data
     loss, and it contradicts AC-006.11 ("every field as saved").
   - **Fix:** in Save as draft's `onSuccess`, call
     `queryClient.removeQueries({ queryKey: getGetManualTicketDraftQueryKey(saved.previewId) })` or
     `setQueryData(...)` with the saved fields. Remove the query after a successful Save too. Optionally add
     `refetchOnMount: 'always'` and gate on `isFetching` for the first render. Add a test named AC-006.11 for the
     sequence "save as draft → open another Draft → reopen the first → the new value is shown".

Informational:
1. `ManualEntryForm.tsx:40-41`: once a reopened Draft is loaded, any later refetch that fails swaps the form for
   `ErrorState`, and everything typed is lost. Such a refetch can come from window focus after 30 s, for example
   when the Draft was deleted or saved in another tab (404), or when a 5xx occurs. This goes against "keep
   everything entered" in AC-006.14. Fix: render the form whenever `draft.data` exists (check `isPending` and
   `!data` first), or turn off `refetchOnWindowFocus` for this query.
2. `ManualEntryForm.tsx:58-68`: a `fieldErrors` entry whose `field` the form does not have is set on an unregistered
   name, so it is invisible. Because `byField.size > 0`, no Alert is shown either. Fix: fall back to the Alert for
   any field that is not registered.
3. `ImportTicketsPage.tsx:25-33` and `ManualEntryForm.tsx:41`: a missing or deleted Draft (`?draftId=N` → 404
   MSG04) leaves the user on `ErrorState`, with no way back to an empty Manual form except editing the URL.
   Switching tabs keeps `draftId`. Fix: drop `draftId` when the tab changes, or offer a "new entry" action. Spec
   behaviour is not affected.
4. `ImportTicketsPage.tsx:24`: an unknown `?tab=` value, such as `?tab=x`, shows no tab panel at all. Fix: fall back
   to `csv` when the key is not in `TABS`.
5. `mocks/handlers.ts` (`POST */api/v1/imports/manual`): the mock answers 200, while the contract
   (`openapi/tsolve-api.yaml:1232`) and the backend answer 201. Fix: `HttpResponse.json(..., { status: 201 })`.
6. Test gaps, none of which blocks the verdict:
   - future days disabled in the date picker;
   - an invalid stored `resolvedDate`;
   - the Draft load's loading and error states;
   - a Save as draft rejection (403/404) that keeps the input;
   - `draftId` removed from the URL after a successful Save of a reopened Draft;
   - AC-006.12 on the web side (the Draft disappears from the Drafts list after Save).

   The code for each was read and looks right. Each would be one short test.

## Assumptions made
- MSG02 for `solution` (the total length of the Solution) is shown under Symptoms, the first Solution section. The
  form has no single "Solution" field, so I read this as satisfying "next to its field" in AC-006.14. This is a
  judgment call for the user.
- CRITICAL 1 is classed as blocking because it can overwrite saved Draft content and contradicts AC-006.11. Strictly
  read, AC-006.11 only covers "reopen", and the first reopen works. Judgment call: the user may downgrade it to
  Informational.
- A 401 on Save or Save as draft shows only the global MSG07 dialog and no form message (guideline 06 §6). AC-006.14
  is read as not covering session expiry.

## Test Report
`task-17-manual-entry-tab-test-report.md`: in `frontend/`, the command
`npx vitest run src/features/ticket-import --maxWorkers=2` ran 6 files and 116 tests, all passing.
`npm run typecheck` passed. `npm run lint` gave 0 errors and 1 warning, which predates this commit and is in
`features/tickets`.

---

# Attempt 2 — 2026-11-14 — verdict: PASS

Reviewed the fix commit default@d6975188 (range `1f040bed..d6975188`). Attempt 1's text above is kept as it was;
the frontmatter now holds attempt 2's verdict.

## Summary
The CRITICAL is fixed. After a Save as draft, the cached Manual Draft query is removed
(`ManualEntryForm.tsx:169-171`). After a Save of a reopened Draft, it is removed as well (`:133-135`). A later reopen
therefore builds a new query, waits in `LoadingState` and mounts the form from the server's current Draft. A new test
named AC-006.11 runs the exact sequence from attempt 1. It would fail without the fix, because the stale query's data
is still fresh within its 30 s `staleTime`. Four of the five INFORMATIONAL items are fixed correctly. Item 3 (a 404
Draft leaves the error screen) was deliberately not fixed, as the session log records. The fix itself brings one new
minor regression (INFORMATIONAL 1 below), which does not block the verdict. **PASS.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-006.9, AC-006.11, AC-006.12 and AC-006.14 behave as the spec says, including a reopen after Save as draft. |
| Completeness | pass | Trace 14/14. Every new behaviour has a test. |
| Correctness | pass | 123/123 scoped tests pass, typecheck is clean and eslint finds nothing. No CRITICAL. |
| Integration | pass | No change to the contract or `src/shared`. The mock now answers 201, as the contract does. |

## Checks

| Check | Result | Evidence |
|---|---|---|
| CRITICAL 1 (stale Draft after Save as draft, AC-006.11) | fixed | `:169-171` `removeQueries(getGetManualTicketDraftQueryKey(saved.previewId))`. Test `AC-006.11 reopening a Draft after Save as draft shows the fields as saved`: saves "Original title", reopens it, saves "Saved edit", leaves, reopens, and expects "Saved edit". |
| Save of a reopened Draft drops its cache (AC-006.12) | fixed | `:133-135`. Test `AC-006.12 reopening a Draft after Save shows MSG04 instead of cached fields`. |
| Info 1: a failed refetch kept the form | fixed | `:43` gives `ErrorState` only when `!draft.data`. Tests `AC-006.14 a failed background Draft refetch keeps the form and entered fields` (a 500 refetch keeps "Unsaved edit") and `AC-006.11 a Draft load failure without data shows the error screen`. |
| Info 2: field-error routing in `showFieldErrors` | fixed, no regression | `:60-77`, `:108-114`. See the routing table below. |
| Info 3: a 404 Draft has no way back | not fixed (decided) | The session log says the fix needs UI text the SRS does not give. Accepted; no spec criterion is affected. |
| Info 4: unknown `?tab=` | fixed | `ImportTicketsPage.tsx:25` falls back to `csv` for a key not in `TABS`. Test `AC-008.1 an unknown tab value falls back to CSV`. |
| Info 5: the mock answers 201 | fixed | `mocks/handlers.ts:213-221`. The MSG32 test now asserts `statuses == [201]`. |
| New UI text outside the MSG catalog | none | The Alert's text is now a string, but it is built only from `msg(code, params)` (`:66`, `:112`). New literals appear only in tests. |
| `src/app`, `src/shared` untouched (AGENTS.md rule 10) | yes | `git diff 1f040bed d6975188 -- frontend/src/app frontend/src/shared` is empty. |

### Field-error routing (`showFieldErrors`, `onError`)

| Case | Behaviour | Why |
|---|---|---|
| An error for a known field | under its field | `Object.hasOwn(form.getFieldsValue(), name)`. `getFieldsValue()` gives a key for every registered field, empty ones included (`@rc-component/form` `cloneByNamePathList` → `set` without `removeIfUndefined`). Every field the backend names (`ManualTicketServiceImpl.java:228-253`: projectId, ticketId, title, description, resolvedDate, symptoms, rootCause, resolutionSteps, scope, and `solution` → `symptoms`), plus `ticketId` for MSG31 (`:223-225`), is a registered top-level field. |
| A 400 with only known-field errors | no Alert | `unhandled` is empty and `fieldErrors.length > 0`, so `''` → `null`. Covered by the existing AC-006.14 field-error tests, which still pass. |
| An error for an unknown field | in the Alert | Test `it.each([false, true])` `AC-006.14 shows an unknown field error in the Alert`: `draftId` → MSG04 in the Alert; with `title` added, MSG02 is under Title and Title's value is kept. |
| A non-field error (403 and others) | its message in the Alert | `fieldErrors.length === 0` → `msg(error.code, error.params)`. The existing 403 → MSG08 test passes. |
| A previous Alert | cleared | `send()` calls `setRejection(null)` before each request (`:119`). |
| 401 | no form message | unchanged (`:110`) |

## Traceability
```
✓ AC-006.1 … AC-006.8, AC-006.10, AC-006.13 — backend tests
✓ AC-006.9  frontend ManualEntryForm.test.tsx + backend
✓ AC-006.11 frontend ManualEntryForm.test.tsx + backend
✓ AC-006.12 frontend ManualEntryForm.test.tsx + backend
✓ AC-006.14 frontend ManualEntryForm.test.tsx
! ids in tests but not in the spec: AC-008.1
trace ticket-repository (STORY-006): 14/14 acceptance criteria have a test
```
`AC-008.1` belongs to import-ticket-list. The trace flags it because the unknown-tab test sits in this module's
`ManualEntryForm.test.tsx` (INFORMATIONAL 2).

## Issues

CRITICAL: none.

Informational:
1. **New with this fix:** `ManualEntryForm.tsx:169-171` together with `:39-45`. After a Save as draft on a **reopened**
   Draft (the URL has `draftId=A`), the query being removed is the one the mounted `ManualEntryForm` observes. On the
   next render of `ManualEntryForm`, which follows any URL change such as a tab switch (`setSearchParams` keeps
   `draftId`), `useQuery` builds a new query with no data. In TanStack query-core 5.104.1,
   `QueryObserver#updateQuery` and `getOptimisticResult` (`queryObserver.js:414-424`) return `isPending`, so the page
   shows `LoadingState`, unmounts `ManualEntryFields` and then remounts it from the server. Whatever was typed after
   the last Save as draft is lost, for example after Save as draft, editing the Title, then CSV and back to Manual
   entry. Before the fix, the form kept that input. Nothing is overwritten on the server, since the form shows the
   last saved version. This was found by reading the code and the library source; there is no test for it.
   **Fix:** in Save as draft's `onSuccess`, call
   `queryClient.setQueryData(getGetManualTicketDraftQueryKey(saved.previewId), …)` instead of `removeQueries`. Pass
   the saved `ManualTicketDraft` if the response is one, or else the request values with `previewId`. The cache then
   holds the saved fields, a reopen shows them, and the mounted form is not remounted because its `key` is the same.
   Alternatively, call `removeQueries` only when `saved.previewId` differs from the URL's `draftId`.
2. `ManualEntryForm.test.tsx` (`AC-008.1 an unknown tab value falls back to CSV`): the test names a criterion of
   another module (import-ticket-list), and the fallback is not what AC-008.1 states. Move it to
   `ImportTicketsPage.test.tsx`, or name it without an AC id.
3. Still open from attempt 1, as decided:
   - Info 3: a 404 Draft gives `ErrorState` with no way back to an empty form.
   - Most of Info 6's test gaps: future days disabled, an invalid stored `resolvedDate`, a Save as draft rejection
     that keeps the input, and `draftId` removed after Save.

   The Draft load's error state and AC-006.12 on the web side are now tested.

## Assumptions made
- INFORMATIONAL 1 is classed as non-blocking. It loses only unsaved input, on a tab switch after a Save as draft, and
  no spec criterion covers keeping input across tab switches; AC-006.14 covers rejections. **Judgment call for the
  user:** it can be raised to CRITICAL if keeping unsaved Manual input across tab switches is expected.
- Info 3 was left unfixed because the SRS gives no text for a "new entry" action. I accept that decision and have not
  failed the task on it.
- The attempt 1 assumptions still hold: MSG02 for `solution` is shown under Symptoms, and a 401 shows only the global
  MSG07 dialog.

## Test Report (attempt 2)
See the attempt 2 section of `task-17-manual-entry-tab-test-report.md`. In `frontend/`,
`npx vitest run src/features/ticket-import --maxWorkers=2` ran 6 files and **123 tests, all passing** (116 before,
7 added). `npm run typecheck` is clean and `npx eslint src/features/ticket-import` gives 0 problems. The trace is
14/14.
