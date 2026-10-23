# 12 · Workflow & PR checklist

> **Applies to:** how you start, build and hand in frontend work.
> **Git rules** (branch names `<type>/<JIRA-KEY>-<module>-<desc>`, Conventional Commits with the Jira key,
> squash-merge, syncing with `main` daily, the PR template) are in the **DevOps design §1** — they are not
> repeated here. This file adds only what is specific to the frontend.

## 1. Starting a feature — walkthrough

The example is Jira issue `KAN-123` "6.2 Ticket List" in feature `tickets`; swap in your own screen. Every
command and snippet below was run against the template (step 6). The API names (`getTickets`,
`TicketSummary`) are examples — use what you agree with the backend owner.

### 1.0 First day only

```bash
cd frontend
node -v                       # v24.x (see .nvmrc; 22.22.2+ also works; with nvm: nvm use)
npm ci
cp .env.example .env.local    # Windows PowerShell: Copy-Item .env.example .env.local
npm run dev                   # http://localhost:5173
```

Mock mode is on by default (`VITE_API_MOCKING=true`): use the **user switcher** (bottom right) to sign in
as a mock user (one role or several) and open your screen — it shows a placeholder card with its number
and name. Then run `npm run format:check && npm run lint && npm run typecheck && npm run test` once: all
green means your machine is set up.
VS Code: install **ESLint** and **Prettier**, turn on *Format On Save*.

### 1.1 Branch and read

1. Branch from the latest `main` (git rules: README "Branching and conventions"): `feature/KAN-123-tickets-ticket-list`.
   `npm ci` again if `package-lock.json` changed.
2. Read the three sources for your screen: the SRS section (III.6.2: fields, columns, filters, buttons,
   MSG codes), the Figma frame (`6.2 Ticket List - Hiếu` on page `01 Web App`), and the guidelines you need
   (always 01–03, then 05–10 depending on the screen). Anything they don't say — a label, a message, a
   rule — **ask the FE Lead; don't invent it.**

### 1.2 API → generated hook

After agreeing the shape with the backend owner, add your endpoint to `openapi/tsolve-api.yaml` (it can
stay a stub until the backend lands). Add to the **existing** lists — don't paste a second `tags:` or
`paths:` key: a tag named after your feature under `tags:`, the path under `paths:` (next to the other
`/api/v1/...` paths) and the schemas under `components: schemas:`:

```yaml
# under tags:
  - name: tickets
    description: Ticket List and Ticket Detail (SRS III.5.5, III.6.2).

# under paths:
  /api/v1/tickets:
    get:
      tags: [tickets]
      operationId: getTickets # → useGetTickets, getGetTicketsQueryKey, GetTicketsParams
      summary: One page of tickets (SRS 6.2).
      parameters:
        - $ref: '#/components/parameters/Page'
        - $ref: '#/components/parameters/Size'
        - $ref: '#/components/parameters/Sort'
      responses:
        '200':
          description: One page of tickets.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/TicketSummaryPage'
        '401':
          $ref: '#/components/responses/Problem'
        '403':
          $ref: '#/components/responses/Problem'

# under components: schemas:
    TicketSummary:
      type: object
      required: [ticketId, sourceTicketId, title]
      properties:
        ticketId: { type: integer, format: int64 }
        sourceTicketId: { type: string, description: The Jira issue key, e.g. ITSUP-1031. }
        title: { type: string }
    TicketSummaryPage: # same paging shape as ReviewQueuePage
      type: object
      required: [content, page]
      properties:
        content: { type: array, items: { $ref: '#/components/schemas/TicketSummary' } }
        page: { $ref: '#/components/schemas/PageMetadata' }
```

Then `npm run generate:api`. You get `generated/tickets/tickets.ts` (the hook and query key) and
`tickets.msw.ts` (a fake-data handler). **Nothing to register:** `@/shared/api` exports the new hook and
types, and mock mode already answers the endpoint with fake data. Commit the YAML and `generated/`
together. ([06](06-data-layer.md) §2)

### 1.3 Hook and page

Your route, path and (for list screens) menu entry already exist — check the roles in your feature's
`routes.ts` against the Permission Matrix ([08](08-routing-and-access.md)). Wrap the generated hook in
`hooks/` ([06](06-data-layer.md) §3):

```ts
// features/tickets/hooks/useTicketList.ts
import { keepPreviousData } from '@tanstack/react-query';
import { useGetTickets, type GetTicketsParams } from '@/shared/api';

/** One page of the Ticket List (SRS 6.2). */
export function useTicketList(params: GetTicketsParams) {
  return useGetTickets(params, { query: { placeholderData: keepPreviousData } });
}
```

Replace the `ScreenPlaceholder` page. Keep the `export default` (the route loads the page lazily).
`DataTable` already shows loading, the error with **Try again**, and empty (MSG04 unless you pass
`emptyCode`); `useListSearchParams` keeps page and sort in the URL ([05](05-react-components.md) C8–C9):

```tsx
// features/tickets/pages/TicketListPage.tsx
/** 6.2 Ticket List — SRS III.6.2 */
import type { TicketSummary } from '@/shared/api';
import { paths, useListSearchParams } from '@/shared/routing';
import { cells, DataTable, type DataTableColumn } from '@/shared/ui';
import { useTicketList } from '../hooks/useTicketList';

/** Columns and widths from the Figma frame; sort keys are the API field names. */
const COLUMNS: DataTableColumn<TicketSummary>[] = [
  {
    key: 'sourceTicketId',
    title: 'Ticket ID',
    width: 120,
    sortable: true,
    render: (_, t) => cells.link(paths.ticketDetail(t.ticketId), t.sourceTicketId),
  },
  {
    key: 'title',
    title: 'Title',
    width: 242,
    sortable: true,
    render: (_, t) => cells.text(t.title),
  },
];

export function TicketListPage() {
  const list = useListSearchParams({ field: 'sourceTicketId', order: 'asc' });
  const tickets = useTicketList(list.apiPaging);

  return (
    <DataTable<TicketSummary>
      label="Tickets"
      columns={COLUMNS}
      rows={tickets.data?.content}
      rowKey={(t) => t.ticketId}
      loading={tickets.isPending}
      error={tickets.isError ? tickets.error : null}
      onRetry={() => void tickets.refetch()}
      pagination={{
        page: list.page,
        total: tickets.data?.page.totalElements ?? 0,
        onChange: list.setPage,
      }}
      sort={list.sort}
      onSortChange={list.setSort}
    />
  );
}

export default TicketListPage;
```

As the page grows, move parts into `components/` (toolbar, table, popups); forms and popups follow
[09](09-forms-and-validation.md). Copy from `features/review` when in doubt — but only what your screen
needs: polling, `emptyCode="MSG13"` and row selection belong to the Review Queue.

| You need | Copy from `features/review/` |
|---|---|
| A list page with filter, sort, paging, selection | `pages/ReviewQueuePage.tsx`, `components/ReviewQueueTable.tsx`, `components/ReviewQueueToolbar.tsx` |
| A popup with a form | `components/RejectTicketModal.tsx` |
| A mutation with an MSG toast and a refreshed list | `hooks/useReviewDecisions.ts` |
| Realistic mock data that changes when you click | `mocks/data.ts`, `mocks/handlers.ts` |
| A page test | `pages/ReviewQueuePage.test.tsx` |

### 1.4 Mocks and tests

Replace the fake data with realistic data in your feature's `mocks/handlers.ts` — real-looking ticket IDs,
titles, Projects, Vietnamese names, every status, and restore it in `resetMockData()`
([10](10-testing.md) §5). The file is already registered. Then the page test: happy, empty and error, plus
one test per mutation flow. Tests and mocks are the one place that imports a generated `*.msw` handler
directly (lint allows it there only):

```tsx
// features/tickets/pages/TicketListPage.test.tsx
import { screen } from '@testing-library/react';
import { http } from 'msw';
import { server } from '@/mocks/server';
import { getGetTicketsMockHandler } from '@/shared/api/generated/tickets/tickets.msw';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

describe('TicketListPage', () => {
  // + a happy-path test against your mock data (see ReviewQueuePage.test.tsx)

  it('shows MSG04 when there are no tickets', async () => {
    server.use(
      getGetTicketsMockHandler({
        content: [],
        page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
      }),
    );
    renderApp('/tickets', { role: 'TICKET_RESOLVER' });
    expect(await screen.findByText(msg('MSG04'), {}, { timeout: 5000 })).toBeInTheDocument();
  });

  it('shows the error in place, with Try again, when the tickets cannot be loaded', async () => {
    server.use(http.get('*/api/v1/tickets', () => problem(500)));
    renderApp('/tickets', { role: 'TICKET_RESOLVER' });
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent(msg('MSG06'));
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });
});
```

The first `findBy…` of a test gets `{ timeout: 5000 }` because it also loads the lazy page module. The
full suite takes about a minute; jsdom's "Not implemented: getComputedStyle" lines are harmless.

### 1.5 Check and hand in

1. `npm run dev`, click through the screen as **every role that can open it** (user switcher), and compare
   it with the Figma frame at 1366 × 768.
2. `npm run format:check && npm run lint && npm run typecheck && npm run test` (`npm run format` fixes
   formatting).
3. Open the PR with the template (DevOps §1.4) plus the checklist below. Keep it under ~400 lines
   (generated code and lock file excluded); split bigger work into stacked PRs.

**Stuck on something outside your feature** (a kit component missing, a shared helper, a guard)? Don't
patch `shared/` or `app/` in your PR — ask the FE Lead ([02](02-conflict-avoidance.md)).

## 2. Frontend PR checklist (paste under "Checklist" in the PR template)

```markdown
### Frontend
- [ ] Only my feature folder changed (or the FE Lead approved changes to shared/app/config)
- [ ] Screen matches the Figma frame and the SRS field table (titles, labels, columns, buttons)
- [ ] Glossary words only; no Cluster / Defer / Bulk sync / Project filter
- [ ] Loading, error and empty states (with the right MSG code)
- [ ] All messages from the MSG catalog; confirm dialogs where the SRS requires them
- [ ] Route has the correct roles; actions hidden for users who may not do them
- [ ] Works with mocks for every role that can open the screen
- [ ] Page test + mutation tests added; lint, typecheck, test pass
- [ ] With a HarnessClaude spec: every acceptance criterion has a test that names its id
- [ ] Spec changed → generate:api run and generated code committed
- [ ] Screenshot of the screen attached (1366 × 768)
```

**Reviewers** (CODEOWNERS): the feature owner, plus the FE Lead when `shared/`, `app/`, configs or the
spec changed. Review against this checklist and the guideline files, not personal taste.

## 3. Keeping your branch in sync

Follow DevOps §1.4 (`git fetch && git merge origin/main` at least daily). After syncing:

- `package-lock.json` changed → `npm ci`.
- `openapi/tsolve-api.yaml` changed → `npm run generate:api`, then `npm run typecheck` to see what broke.
- Conflict in `package-lock.json` or `generated/` → don't hand-merge; follow [02](02-conflict-avoidance.md) §4.

## 4. Working with an AI coding agent

- Give it `AGENTS.md` (Claude Code reads `CLAUDE.md`, which imports it), the guideline files for the task,
  the SRS section text and a screenshot of the Figma frame. Name the feature folder it may change.
- In this repo the agent works through **HarnessClaude** (the root `CLAUDE.md`; guide:
  `harnessclaude/README.md`), and its hooks enforce it. A new screen or a changed behaviour goes into the
  module's spec first; code is written only after you approve that spec. A small fix that changes no
  acceptance criterion (a label, a style, a bug against an existing criterion) is a quick change: the agent
  names the files and you confirm them. Each test names the criterion it proves ([10](10-testing.md) T6).
- Ask for one screen per session; ask it to run
  `npm run format:check && npm run lint && npm run typecheck && npm run test && npm run build`, and
  `npm run generate:api` with no diff in `src/shared/api/generated` (the harness `verify`).
- Review its output exactly like a teammate's PR, with the checklist above. Watch for: invented MSG texts
  or labels, edits to `shared/`/`app/`, hand-written API types, `useEffect` fetching, raw antd `Table`/`Modal`.
- You are the author of what you commit. Disclose AI help the way the team agreed (commit trailer /
  PR footer).

## 5. Definition of done (frontend)

The PR is merged, CI is green, the screen works on staging for every role that can open it, the Tester
has verified it (DevOps §1.4 step 8), and the Jira issue is moved to Done.

---
*Last verified against code: 2026-10-10, step 6 — the §1 walkthrough was run end to end (new API tag → generated hook → page → mocks → test). Role, Jira key and MSG codes updated to Report3 on 2026-10-23 (KAN-63); the walkthrough was not re-run.*
