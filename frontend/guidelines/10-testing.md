# 10 · Testing

> **Applies to:** every `*.test.ts(x)` file, `src/test/`, `src/mocks/` and `features/*/mocks/`.
> **Why it matters:** a few good tests catch the bugs that hurt in a demo (wrong state after Approve, a
> missing empty state, a broken guard) without slowing a part-time team down. There is **no coverage
> gate** (DevOps decision) — write tests where they pay off, not to hit a number.

## 1. Tools

| Tool | Job |
|---|---|
| **Vitest 5** | test runner; shares `vite.config.ts` (aliases, plugins), so tests resolve imports exactly like the app |
| **React Testing Library** + **user-event** | render components and act like a user (click, type, Tab) |
| **jsdom** | browser-like DOM in Node |
| **MSW 3** | intercepts `fetch` and answers with the same handlers used in mock mode — tests go through the real `http.ts`, generated hooks and TanStack Query |
| `src/test/render.tsx` | `renderApp(path, { role })` — the whole app (real routes, guards, AppShell) at a URL · `renderWithProviders(ui, { role })` — one component with theme, antd and a fresh QueryClient. Both use the app's real QueryClient (global error handling included) without retries, return it as `queryClient`, and set who the `/me` mock returns (`role: null` = signed out). |
| `src/test/setup.ts` | starts the MSW server for every test file; a request with **no** handler fails the test (`onUnhandledFrame: 'error'`); handlers are reset after each test |

Not adopted (on purpose): E2E (Playwright/Cypress), snapshot tests, coverage thresholds, component
screenshots. *Why:* high upkeep for a 14-week MVP; the Tester's system and acceptance tests (Report 5)
cover end-to-end flows on staging.

## 2. What to test

| Write tests for | Typically |
|---|---|
| **Kit components** (`shared/ui`) | each variant renders the right text/role; StatusTag label per status; DataTable empty/loading/error; upload limits → MSG40/MSG62 (once the upload components exist) |
| **Logic** in `shared/` (`date.ts`, `rules`, `msg()` templating, `applyApiErrors`, landing page per role) | plain unit tests, many cases, fast |
| **One page test per feature** (minimum) | happy path + empty state + error state, through MSW |
| **Every mutation flow you build** | confirm → request sent with the right body → MSG toast → list refreshed |
| **Guards** | wrong role → 403 with MSG08; no session → `/login` |

Don't test: antd itself, generated code, TanStack Query caching, CSS, or implementation details (state
variable names, how many times a hook rendered).

## 3. Rules

### T1 — Test behaviour through what the user sees.
Query by role and accessible name, then by label text, then by text. `data-testid` only as a last resort.

```tsx
// ✅ Do
await user.click(screen.getByRole('button', { name: 'Reject' }));
expect(await screen.findByText('2 ticket(s) rejected.')).toBeInTheDocument();

// ❌ Don't
fireEvent.click(container.querySelector('.ant-btn-dangerous')!);
expect(component.state.isRejectOpen).toBe(true);
```

*Why:* tests that read like the SRS steps survive refactors and also check accessibility (a button with
no accessible name cannot be found by role).

### T2 — Mock the network, not your modules.
Use MSW handlers (`server.use(...)` to override per test). Don't `vi.mock` the API hooks or `fetch`.
*Why:* mocking hooks skips the code most likely to be wrong — params, keys, invalidation, error mapping.

```ts
// ✅ Do — override one endpoint for this test, with orval's typed handler (the body is type-checked)
server.use(
  getGetReviewQueueMockHandler({
    content: [],
    page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
  }),
);
// ✅ Do — an error in the agreed format (Problem Details + MSG code)
server.use(http.post('*/api/v1/reviews/reject', () => problem(409, 'MSG47')));

// ❌ Don't
vi.mock('@/shared/api', () => ({ useGetReviewQueue: () => ({ data: [] }) }));
```

### T3 — Use the SRS texts in assertions, through the catalog.
`expect(await screen.findByText(msg('MSG16'))).toBeInTheDocument()` — the test breaks if someone types
the text by hand differently.

### T4 — Every test is independent.
A fresh QueryClient per render (done by `renderApp` / `renderWithProviders`), MSW handlers reset after each test
(done in `src/test/setup.ts`), no shared mutable fixtures. Vitest 5 clears mocks between tests by default.

### T5 — Wait for the UI, never for time.
Use `findBy…` / `waitFor`; never `setTimeout` or arbitrary sleeps. For the 5-second polling, test the
hook option (`refetchInterval`), not real waiting.

### T6 — File and name conventions.
Test file sits next to its subject: `ReviewQueuePage.test.tsx`. `describe` = the unit
(`'ReviewQueuePage'`); `it` = the behaviour in plain English (`'shows MSG16 when no tickets are waiting'`).
When the work has a HarnessClaude spec, the `it` starts with the id of the acceptance criterion it proves:
`it('AC-002.1 lists the pending tickets of my Projects')` — `bun harnessclaude/hooks/sdd.ts trace <module>`
looks for that id. A bug fix ends with the bug id from `docs/bug-log.md`:
`it('keeps the selection after Approve (BUG-004)')`.
`vi.mock`/`vi.hoisted`, if ever needed, go at the top level of the file (Vitest 5 requirement).

## 4. Example — a page test

```tsx
// features/review/pages/ReviewQueuePage.test.tsx (shortened)
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { server } from '@/mocks/server';
import { getGetReviewQueueMockHandler } from '@/shared/api/generated/review/review.msw';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

describe('ReviewQueuePage', () => {
  it('lists pending tickets of my Projects', async () => {
    renderApp('/review-queue', { role: 'PROJECT_MANAGER' });
    // The first findBy also loads the lazy page module: give it more time (§6).
    expect(
      await screen.findByRole('row', { name: /ITSUP-1031/ }, { timeout: 5000 }),
    ).toBeInTheDocument();
  });

  it('shows MSG16 when no tickets are waiting', async () => {
    server.use(
      getGetReviewQueueMockHandler({
        content: [],
        page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
      }),
    );
    renderApp('/review-queue', { role: 'PROJECT_MANAGER' });
    expect(await screen.findByText(msg('MSG16'))).toBeInTheDocument();
  });

  it('asks MSG90 before approving two tickets, then shows MSG18', async () => {
    const user = userEvent.setup();
    renderApp('/review-queue', { role: 'PROJECT_MANAGER' });
    await user.click(await screen.findByRole('checkbox', { name: /ITSUP-1031/ }));
    await user.click(screen.getByRole('checkbox', { name: /ITSUP-1102/ }));
    await user.click(screen.getByRole('button', { name: 'Approve' }));
    await user.click(await screen.findByRole('button', { name: 'OK' }));  // MSG90 dialog
    expect(await screen.findByText(msg('MSG18', { count: 2 }))).toBeInTheDocument();
  });
});
```

*(Shortened. The full test — popups, Select all, Department filter, error state — is
`features/review/pages/ReviewQueuePage.test.tsx`.)*

## 5. Mock handlers (mock mode and tests use the same ones)

```
src/mocks/handlers.ts — every handler, first match wins:
  1. src/mocks/session.ts           GET /me (fixture user per role) · POST /auth/logout     FE Lead
  2. features/<f>/mocks/handlers.ts  realistic data and business errors                    feature owner
  3. orval's generated *.msw.ts      faker data for every other endpoint in the spec        generated
```

- Write your feature's handlers in `features/<f>/mocks/handlers.ts` (it is already registered). Keep mutable
  mock data in a module variable and restore it in the file's `resetMockData()` — it runs after every test,
  so a ticket approved in one test is back in the next (example: `features/review/mocks/`). Start from the
  generated typed handler and give it realistic data — real-looking ticket IDs, titles, Projects, Vietnamese
  names, every status your screen shows:
  ```ts
  import { getGetReviewQueueMockHandler } from '@/shared/api/generated/review/review.msw';
  export const handlers: HttpHandler[] = [
    // The resolver must return a ReviewQueuePage: { content, page: { size, number, totalElements, totalPages } }
    getGetReviewQueueMockHandler(async ({ request }) => {
      await delay(MOCK_DELAY_MS);
      const url = new URL(request.url);
      const size = Number(url.searchParams.get('size')) || 20;
      const page = Number(url.searchParams.get('page')) || 0;
      return {
        content: tickets.slice(page * size, page * size + size),
        page: { size, number: page, totalElements: tickets.length, totalPages: Math.ceil(tickets.length / size) },
      };
    }),
  ];
  ```
  The full version (filter and sort too) is `features/review/mocks/handlers.ts`.
- Errors: `problem(status, code?, { params, fieldErrors })` from `@/shared/api/mocks` builds the agreed error
  body. Latency: `await delay(MOCK_DELAY_MS)` (`delay` from `msw`) — 400 ms in the browser, 0 in tests.
- Who is signed in: the dev role switcher (browser) or the `role` option of `renderApp` (tests) choose which
  fixture user `/me` returns (`shared/auth/dev-role.ts`). Fixture users live in `src/mocks/session.ts`.
- 🔒 `msw`, `@faker-js/faker` and `@/shared/api/mocks` may be imported only in `mocks/` folders and tests, and
  the generated `*.msw` files only there — app code never ships mock code. `src/mocks` is imported only by
  tests and `main.tsx` (mock mode, dev builds only).

## 6. Running tests

| Command | When |
|---|---|
| `npm run test` | once, all tests (CI runs this) |
| `npm run test:watch` | while developing |
| `npx vitest run src/features/review` | only one feature |

Page tests are slow in jsdom (an antd table with 20 rows takes 2–5 s per test), so the test timeout is
15 s (`vite.config.ts`). The first `findBy…` in a file also loads the lazy page module: give it
`{ timeout: 5000 }` as `ReviewQueuePage.test.tsx` does. Don't raise timeouts further — a test that needs
more is doing too much; split it.

## 7. Checklist

- [ ] At least one page test for the feature (happy + empty + error).
- [ ] Each mutation flow you added has a test (confirm → toast → refreshed list).
- [ ] Queries by role/label/text; MSG texts via `msg()`; no `vi.mock` of API hooks.
- [ ] With a HarnessClaude spec: every acceptance criterion has a test that names its id (`sdd.ts trace` passes).
- [ ] `npm run test` passes locally.

---
*Last verified against code: 2026-10-10, step 5.5 — every path, name, rule and ✅ example checked against the scaffold.*
