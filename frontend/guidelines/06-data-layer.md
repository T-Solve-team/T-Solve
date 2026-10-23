# 06 · Data layer & errors

> **Applies to:** anything that reads or writes server data, and anything that shows a system message.
> Files: `openapi/`, `orval.config.ts`, `src/shared/api/`, `src/shared/messages/`, `features/*/hooks/`.
> **Why it matters:** with one way to call the API and one way to show messages, every screen behaves
> the same, the types always match the backend, and MSG texts never drift from the SRS.

## 1. The pipeline

```
openapi/tsolve-api.yaml ──(npm run generate:api)──► src/shared/api/generated/   NEVER EDIT
                                                     ├── model/            types + enum constants
                                                     ├── <tag>/<tag>.ts    request functions, useX hooks, getXQueryKey
                                                     └── <tag>/<tag>.msw.ts  MSW handlers + faker data (mocks/tests only)
src/shared/api/index.ts      re-exports generated code and types + ApiError + createQueryClient
features/<f>/hooks/useX.ts   wraps a generated hook: options, invalidation, MSG toasts
features/<f>/pages/…         calls feature hooks only
```

- Server data lives **only** in the TanStack Query cache. No Redux/Zustand, no Context holding API data,
  no `useState` copies of query results.
- Pages and components import from `@/shared/api` (types) and their feature's `hooks/`. They never call
  `fetch` and never import from `@/shared/api/generated/...` directly. 🔒 (lint)

## 2. orval and regeneration

- `orval.config.ts` generates TanStack Query hooks (`client: 'react-query'`) using the native `fetch`
  transport through our wrapper `src/shared/api/http.ts` (`override.mutator`), split by OpenAPI tag
  (`mode: 'tags-split'`), plus MSW mock handlers with fake data for every endpoint. Hooks return the
  response body (`includeHttpResponseReturnType: false`), and their error type is `ApiError` (`http.ts`
  exports `ErrorType`, which orval picks up).
- `http.ts` is the only place that touches `fetch`. It adds the base URL (`VITE_API_BASE_URL`, empty = same
  origin: the Vite proxy in dev, Caddy in production), sends the session cookie, parses JSON (204 → `undefined`)
  and turns every non-2xx response — and network errors — into an `ApiError` (§6).
- Both the spec and the generated code are committed. *Why:* the app runs right after `npm ci` with no
  backend, and every API change is visible in the PR diff.

**When an endpoint changes** (one PR — DevOps design "one feature = one PR"):

1. Backend changes the controller/DTO.
2. With the backend running, `npm run api:pull` downloads its spec (springdoc,
   `http://localhost:8080/v3/api-docs.yaml`; other URL: `API_DOCS_URL=… npm run api:pull`) into
   `openapi/tsolve-api.yaml` — or edit the YAML by hand while the endpoint doesn't exist yet (the stub is how
   frontend work starts before the backend is ready).
3. `npm run generate:api`.
4. Fix the type errors that appear (`npm run typecheck`) — they show exactly which screens are affected.
5. Commit spec + generated code + fixes together.

Never resolve a merge conflict inside `generated/`; resolve the YAML and regenerate. (See [02](02-conflict-avoidance.md).)

## 3. Feature hooks

Wrap a generated hook in a feature hook when you add behaviour: polling, default params, invalidation,
messages. A feature hook **never reuses the generated name**: generated `useGetReviewQueue` →
feature `useReviewQueue`; generated `useApproveTickets` → feature `useApproveSelectedTickets`.
If a generated query needs nothing extra, a page may call it directly.

```ts
// features/review/hooks/useReviewQueue.ts
import { keepPreviousData } from '@tanstack/react-query';
import { useGetReviewQueue, type GetReviewQueueParams } from '@/shared/api';

export const REVIEW_QUEUE_POLL_MS = 5_000; // NFR: new ticket visible within 5 s

export function useReviewQueue(params: GetReviewQueueParams) {
  return useGetReviewQueue(params, {
    query: {
      refetchInterval: REVIEW_QUEUE_POLL_MS, // pauses automatically while the tab is hidden
      placeholderData: keepPreviousData,     // no flicker when page/sort changes
    },
  });
}
```

```ts
// features/review/hooks/useReviewDecisions.ts (with useRejectSelectedTickets, useRequestChangesForTickets)
import { useQueryClient } from '@tanstack/react-query';
import { getGetReviewQueueQueryKey, useApproveTickets } from '@/shared/api';
import { showMessage } from '@/shared/messages';

export function useApproveSelectedTickets() {
  const queryClient = useQueryClient();
  return useApproveTickets({
    mutation: {
      onSuccess: (result) => {
        showMessage('MSG14', { count: result.count });
        return queryClient.invalidateQueries({ queryKey: getGetReviewQueueQueryKey() });
      },
    },
  });
}
```

Names come from each `operationId` in the spec: `getReviewQueue` → `getReviewQueue()` (plain request
function), `useGetReviewQueue`, `getGetReviewQueueQueryKey`, `GetReviewQueueParams`; `approveTickets` →
`useApproveTickets`. The stub's operations today: `getMe`, `logout`, `getReviewQueue`,
`getReviewQueueTicketIds`, `approveTickets`, `rejectTickets`, `requestTicketChanges`.

## 4. Query keys, caching, invalidation

- **Keys:** always the generated `getGetXQueryKey(params?)`. Never hand-written arrays.
- **Invalidate after every successful mutation**, by the generated key of each list/detail it changes.
  Calling the key function **without params** matches every variation of that query (all pages, all
  filters) — that is what you usually want.

  | Mutation | Invalidate |
  |---|---|
  | Approve / Reject / Request changes | Review Queue, Ticket Detail of those tickets, Ticket List |
  | Unpublish / Re-publish / Send back | Ticket Detail, Ticket List, Expired Tickets, Review Queue |
  | Import finished | Import History, Ticket List |

- ❌ Never `queryClient.invalidateQueries()` with no key — it refetches the whole app.
- **Defaults** (set once in `shared/api/query-client.ts`; don't override per hook without a reason):
  `staleTime` 30 s · `gcTime` 5 min · queries retry once, except 4xx (never retried) · mutations never
  retry · refetch on window focus on.
- **No optimistic updates** in the MVP. Wait for the server, then invalidate. *Why:* simpler, and the
  NFR (confirmation within 2 s) does not need them.
- **Server-side pagination, 20 rows per page** (SRS III default). Page, size and sort go to the API as
  query params and live in the URL (see [05](05-react-components.md) C8). Spring conventions: `page` starts
  at **0** (antd's Pagination shows 1 — convert), `sort=field,asc|desc`, and a page comes back as
  `{ content: [...], page: { size, number, totalElements, totalPages } }`.

## 5. Loading, error, empty

| Flag (TanStack Query v5) | Meaning | Show |
|---|---|---|
| `isPending` | no data yet | `LoadingState` / table skeleton |
| `isError` | the request failed | `ErrorState` with retry (MSG06) — unless the global handler redirected (401/403) |
| `isFetching && !isPending` | background refetch (polling, focus) | nothing, or a subtle spinner — never blank the screen |
| data with 0 items | empty | `EmptyState` with MSG04, or the screen's code (MSG13 Review Queue) |

`ErrorState` gets its retry action from the page (`onRetry={() => void query.refetch()}`). Never call the
same query hook *inside* the error component: a new observer of a failed query refetches it on mount, the
page goes back to loading, the error component unmounts and remounts — an endless request loop.

For mutations: disable the confirm button and show its loading state while `isPending`; never allow a
double submit.

## 6. Errors and system messages (MSG codes)

### Error contract with the backend
Every error response is an RFC 9457 **Problem Details** object (Spring's `ProblemDetail`) extended with:

```json
{
  "status": 409,
  "title": "Conflict",
  "code": "MSG49",
  "params": { "department_name": "IT" },
  "fieldErrors": [{ "field": "jiraProjectKey", "code": "MSG49", "params": { "department_name": "IT" } }]
}
```

`http.ts` converts it into `ApiError { status, code: MsgCode, params, fieldErrors }` (`shared/api/errors.ts`).
`code` is always a code from our catalog: the backend's code when we know it, otherwise **MSG07** for 401,
**MSG08** for 403 and **MSG06** for everything else. A network failure is `status: 0`, `MSG06`.
*(This contract is our proposal; the backend must implement it — it is recorded in the decision log.)*

### The catalog
`src/shared/messages/catalog.ts` holds every web MSG code of Report3 System_Messages with its exact English
text and its display type (plus `MSG_NEW_2`, decision D3). Texts are templates
(`{count} ticket(s) approved and published.`). `{tracker_name}` is filled with `TRACKER_NAME` ("Jira",
`shared/config/tracker.ts`) unless you pass it. `catalog.test.ts` checks every text against
`report3-messages.json`, which `npm run messages:report3` builds from the docs folder.

```ts
msg('MSG02', { field_name: 'Reason', max_length: 1000 })  // → string, for field errors and inline text
showMessage('MSG14', { count: 3 })                         // → toast; level from the catalog (error if not a toast code)
await showAcknowledgement('MSG07')                         // → dialog with only OK; resolves when clicked
```

`showMessage` / `showAcknowledgement` work outside React too (the global error handler uses them):
`<MessageHost />`, rendered once by `AppProviders`, connects them to antd's App context.

✅ Do: `showMessage('MSG22')`  ❌ Don't: `message.success('Ticket unpublished.')` 🔒 (raw `message` banned)

### Where each message type is shown (Report3 message type)

| Report3 type | UI mechanism | Example |
|---|---|---|
| Red text below the field (or upload area) | `Form.Item` error (`rules` or server `fieldErrors`) — see [09](09-forms-and-validation.md) | MSG01, MSG02, MSG43, MSG49 |
| Inline | `EmptyState` / inline `Alert` in the page | MSG04, MSG13, MSG30, MSG54 |
| Toast | `showMessage(code)` | MSG05, MSG14, MSG16, MSG32 |
| Confirmation dialog | `ConfirmDialog` with the code as its text; when the confirm comes with a form, a warning `Alert` at the top of the `FormModal` (MSG15 in Reject Ticket) | MSG21, MSG19, MSG37 · MSG15 |
| Tooltip | `Tooltip title={msg(code)}` | MSG18 |

### Who handles which error

The global part lives in `app/providers/global-errors.ts` (wired into the QueryClient by `AppProviders`).

| Error | Handled by | Behaviour |
|---|---|---|
| 401 on any request while signed in | global | MSG07 dialog (once, however many requests failed) → OK → cache dropped → the route guard sends the user to Login with `returnTo` |
| 401 on `GET /me` | `useSession` | not an error: nobody is signed in |
| a **query** fails (403, 404, 5xx, network) | the screen | `ErrorState` in place with `msg(error.code)` — MSG08 for 403, MSG06 for 5xx. No toast. A role that may not open the screen at all gets the 403 page from the route guard first. |
| a **mutation** fails | global | toast with `msg(error.code, error.params)` — MSG08 for 403, MSG06 for 5xx / network |
| business error with a code (4xx) on a **form** | the feature | `meta: { handlesOwnErrors: true }` + map `fieldErrors` onto the fields ([09](09-forms-and-validation.md)) |
| business error on a page action (e.g. MSG40 on Finish) | the feature | `meta: { handlesOwnErrors: true }` + show it where the SRS says (in page or toast) |

`meta: { handlesOwnErrors: true }` on a mutation turns the global toast off for it. Never show two
messages for one error. Retries: queries retry once, except 4xx (never); mutations never retry.

## 7. Session

`useSession()` (in `shared/auth`) runs the generated `GET /api/v1/me` request (`staleTime: Infinity`; 401 →
`null`) and returns `{ session, roles, hasRole, hasAnyRole, isPending, isError, retry, isProjectManagerOf,
isMemberOf }`. `session` is the generated `CurrentUser` with only known roles (`user`, `roles`, `department`,
`departments`, `projects` with the role in each, `managedSpaces`, `workspaceReady`); `roles` always holds
`EMPLOYEE` (GBR-28). It is the only source of "who am I" — never store the user elsewhere.

`useSignOut()` calls `POST /api/v1/auth/logout` and then, even if that fails, `resetSession(client,
{ signedOut: true })`: every other cached query and mutation is dropped (they belong to the previous user)
and the session becomes "signed out" without asking the server, so mounted guards react at once (a dev user
switch reloads the session instead). It also removes browser storage keys that start with `tsolve.session.` (`SESSION_STORAGE_PREFIX`) —
put per-user data there only under that prefix. The expired-session flow and the dev user switcher use the
same `resetSession()`.

## 8. Checklist

- [ ] No `fetch`/axios outside `shared/api/http.ts`; no imports from `generated/` paths.
- [ ] Every mutation invalidates the right generated keys and shows its MSG on success.
- [ ] Loading, error, empty handled; no blank screen during background refetch.
- [ ] Every message comes from the catalog, shown by the mechanism its SRS type requires.
- [ ] Spec + generated code + fixes committed together when the API changed.

---
*Last verified against code: 2026-10-23, KAN-63 — screens, roles, paths and MSG codes checked against the Report3 foundation (`src/test/guidelines.test.ts`).*
