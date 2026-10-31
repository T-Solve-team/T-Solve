# 01 · Project structure

> **Applies to:** everything in `frontend/`.
> **Why it matters:** five people build features at the same time. A fixed place for every kind of file
> means people rarely edit the same file, reviewers know where to look, and AI agents don't invent folders.

## 1. The folder tree

```
frontend/
├── AGENTS.md · CLAUDE.md · README.md
├── guidelines/                      # these files
├── openapi/tsolve-api.yaml          # API spec, committed (input of orval)
├── scripts/                         # pull-api.mjs (npm run api:pull), report3-messages.mjs (npm run messages:report3)
├── public/                          # static files copied as-is (favicon). MSW's worker is served by Vite, not committed
├── src/
│   ├── main.tsx                     # starts mocks (if enabled) and renders <App />
│   ├── app/                         # WIRING ONLY — owner: FE Lead (dev/: user switcher, /dev/kit page)
│   │   ├── App.tsx
│   │   ├── providers/               # AppProviders (antd + query cache), global-errors.ts (401/403/MSG06)
│   │   ├── router/                  # routes.tsx (route tree), feature-routes.ts, guards.tsx, router.ts,
│   │   │                            #   RootLayout, FullPageLoading, usePageTitle
│   │   ├── dev/                     # DevUserSwitcher (mock users), KitPage (/dev/kit) — dev builds only
│   │   ├── layout/                  # AppShell, SideMenu, UserMenu, menu-config.ts
│   │   ├── theme/                   # theme.ts (antd tokens from Figma STYLES)
│   │   └── pages/                   # ForbiddenPage (403), NotFoundPage (404), RouteErrorPage, SessionErrorPage
│   ├── features/                    # BUSINESS SCREENS — owner: the feature's developer
│   │   └── <feature>/               # see §3
│   ├── shared/                      # REUSABLE, NO BUSINESS FLOW — owner: FE Lead
│   │   ├── ui/                      # Figma kit v4 components (StatusTag, DataTable, FormField…)
│   │   ├── api/                     # http.ts (fetch wrapper), errors.ts (ApiError), query-client.ts
│   │   │   ├── generated/           # orval output — NEVER EDIT
│   │   │   └── mocks/               # problem() helper for mock handlers (mocks and tests only)
│   │   ├── auth/                    # useSession.ts (GET /me, useSignOut), roles.ts, session.ts, landing.ts, dev-user.ts
│   │   ├── messages/                # MSG catalog (Report3) + report3-messages.json + msg / showMessage / showAcknowledgement
│   │   ├── forms/                   # rules (MSG01/02/03/44) + applyApiErrors
│   │   ├── routing/                 # paths.ts (every screen path), useListSearchParams
│   │   ├── lib/                     # tiny pure helpers: date.ts, labels.ts (source labels), paging.ts
│   │   └── config/                  # env.ts (typed import.meta.env), tracker.ts (TRACKER_NAME = 'Jira')
│   ├── mocks/                       # MSW: handlers.ts (all), session.ts (/me users), browser.ts, server.ts
│   └── test/                        # test setup, renderApp and renderWithProviders, guidelines.test.ts
├── .env.example · .nvmrc · .prettierrc.json · .prettierignore · .gitignore · eslint.config.js · orval.config.ts
├── index.html · tsconfig*.json · vite.config.ts · package.json · package-lock.json
└── Dockerfile · .dockerignore · Caddyfile
```

Dependency direction (enforced by ESLint, see [11](11-code-quality-and-tooling.md)):

```
app  ──►  features  ──►  shared
 └──────────────────────►  shared
features/x ──► features/y   only through features/y/index.ts, only for the pairs in §5
shared ──► (nothing in app or features)
```

## 2. What goes where

| I need to add… | Put it in |
|---|---|
| A new screen (a Figma frame) | `features/<feature>/pages/<ScreenName>Page.tsx` |
| A popup (a Figma "(popup)" frame) | `features/<feature>/components/<Name>Modal.tsx` |
| A part used by one feature only | `features/<feature>/components/` |
| A hook that calls the API | `features/<feature>/hooks/` (wraps a generated hook) |
| Realistic mock data for my screens | `features/<feature>/mocks/handlers.ts` |
| A component from the Figma kit ("Web/…") | `shared/ui/` — FE Lead PR, see [02](02-conflict-avoidance.md) |
| A pure helper used by 2+ features (no React, no API) | `shared/lib/` — FE Lead PR |
| A new MSG code or text | Nowhere — MSG texts come from the SRS. Ask the FE Lead. |
| An API type | Nowhere — it is generated. Change the spec, run `npm run generate:api`. |
| Global state | Nowhere — see [06](06-data-layer.md). |

**Rule:** start inside your feature. Something moves to `shared/` only when a **second** feature needs
it ("rule of two"). *Why:* early sharing creates coupling and review load; duplicated code in two
features is cheaper to merge later than a wrong shared abstraction is to undo.

## 3. Inside a feature folder

```
features/review/
├── index.ts                    # PUBLIC API — the only file others may import
├── routes.ts                   # FeatureRoute list for this feature's screens
├── pages/
│   ├── ReviewQueuePage.tsx     # 7.1
│   ├── ReviewHistoryPage.tsx   # 7.4
│   ├── ExpiredTicketsPage.tsx  # 7.5
│   ├── ReviewQueuePage.module.css
│   └── ReviewQueuePage.test.tsx    # tests sit next to what they test
├── components/
│   ├── ReviewQueueToolbar.tsx      # filter, selection count, decision buttons
│   ├── ReviewQueueToolbar.module.css
│   ├── ReviewQueueTable.tsx
│   ├── RejectTicketModal.tsx       # 7.3 (popup)
│   └── RequestChangesModal.tsx     # 7.2 (popup)
├── hooks/
│   ├── useReviewQueue.ts           # polling every 5 s
│   ├── useQueueTicketIds.ts        # "Select all" = every ticket in the queue
│   └── useReviewDecisions.ts       # approve / reject / request changes + MSG toasts
└── mocks/
    ├── data.ts                     # 58 realistic tickets (the Figma ones first)
    └── handlers.ts                 # handlers + resetMockData()
```

This is the **reference implementation** (decision FE-23): when in doubt, copy how `review` does it.

- Every feature folder and every routed screen's page file already exist; a page starts as a
  `ScreenPlaceholder` that the owner replaces with the real screen. Popups (no route) are created by their
  owner in `components/`.
- Create only the sub-folders you need. A tiny feature may have just `index.ts`, `routes.ts` and `pages/`.
- No `utils/` or `helpers/` dump folders. A helper used by one component lives in that component's file;
  a helper used across the feature gets a named file (`hooks/`, or a clearly named `.ts` next to its user).
- No feature-level `types.ts` for API shapes — they are generated. UI-only types sit next to the
  component that owns them.
- Every page file starts with a one-line comment naming its SRS screen, e.g.
  `/** 7.1 Review Queue — SRS III.7.1 */`. *Why:* traceability from code to SRS and Figma.

## 4. The public-API rule

Other code may import a feature **only through its `index.ts`**. `index.ts` exports the feature's
`routes` and, if another feature needs it, a few named components or hooks — nothing else.

```ts
// features/review/index.ts — once Ticket Detail needs the decision buttons (today it exports only reviewRoutes)
export { reviewRoutes } from './routes';
export { TicketDecisionActions } from './components/TicketDecisionActions'; // used by Ticket Detail
```

✅ Do

```ts
// features/tickets/pages/TicketDetailPage.tsx
import { TicketDecisionActions } from '@/features/review';
```

❌ Don't

```ts
import { TicketDecisionActions } from '@/features/review/components/TicketDecisionActions';
import { useApproveTickets } from '../../review/hooks/useApproveTickets';
```

*Why:* the owner of `review` can rename, split or rewrite anything inside the folder without breaking
other features. Only `index.ts` is a promise to the rest of the app. `npm run lint` (and CI) fails on deep
imports — `@/…` or relative `../../…` alike (`import-x/no-restricted-paths`). Two exceptions, both
enforced by the same lint config:
`app/router/feature-routes.ts` imports `routes` from each `index.ts` (normal), and `src/mocks/handlers.ts`
imports each feature's `mocks/handlers.ts` directly so mock code never ships in the production bundle.

## 5. The 15 features

34 web screens: 26 with a path, 8 popups. Roles are Report3's Permission Matrix; "Employee" means every
signed-in user (every user has the Employee role).

| Feature folder | Screens (SRS III) | Roles | May import (via index) |
|---|---|---|---|
| `auth` | 1.1.1 Login | signed out | — |
| `my-profile` | 1.2.1 My Profile | Employee | — |
| `users` | 1.3.1 User List · 1.3.2 User Detail · 1.3.3 Hand Over Responsibilities (popup) | Admin | — |
| `workspace` | 2.1 Workspace Setup · 2.2 Workspace Settings · 2.3 Department Detail · 2.4 Delete Department (popup) | Admin | — |
| `jira-integration` | 3.1 Jira Integration · 3.2 Project Detail | Admin, Project Manager (3.1) · Project Manager (3.2) | — |
| `ticket-import` | 5.1 Import Tickets · 5.2 Import Preview · 5.3 Import Ticket Preview (popup) · 5.4 Import History | Project Manager | — |
| `tickets` | 5.5 Ticket Detail · 6.2 Ticket List | Ticket Resolver, Project Manager | `review`, `feedback` |
| `search` | 6.1 Search | Ticket Resolver, Project Manager | — |
| `review` | 7.1 Review Queue · 7.2 Request Changes (popup) · 7.3 Reject Ticket (popup) · 7.4 Review History · 7.5 Expired Tickets | Project Manager | — |
| `feedback` | 8.1 Give Feedback (popup) [Web] | Ticket Resolver, Project Manager | — |
| `knowledge-dashboard` | 9.1 Knowledge Dashboard | Project Manager, Admin | — |
| `audit-log` | 9.2 Audit Log | Admin | — |
| `triage` | 10.1 Triage History · 10.2 Triage Result Detail (popup) | Project Manager | — |
| `wiki` | 11.1 Wiki Home · 11.2 Wiki Document | Employee | — |
| | 11.4 Upload Documents · 11.5 Folder (popup) · 11.7 Space Settings | Knowledge Manager | |
| `chatbot` | 12.1 Knowledge Chatbot | Employee | — |

Paths per screen are in [08](08-routing-and-access.md).

**A 16th folder, `platform-console`, is not a Workspace feature.** It holds the screens of the Platform Console host
(`admin.<domain>`, [08](08-routing-and-access.md) §3a), owned by the `platform-console` module. It has `routes.ts`
(exports `platformConsoleRoutes`, a plain react-router `RouteObject[]`), `index.ts`, `mocks/handlers.ts` and its pages.
Its routes are not in `featureRoutes` and it has no row in the table above. Today it only has a "Platform Console"
placeholder page at `/`.

Ownership notes that are easy to get wrong:

- **All review decisions live in `review`**: Approve, Reject, Request changes, Unpublish, Re-publish,
  Send back to queue. Each one records a Review Decision shown in Review History, so the mutations sit
  in one place. Ticket Detail will show them through `TicketDecisionActions` exported by `review` (built together with
  Ticket Detail).
- **Links between features use URLs, not imports.** `review` opens Ticket Detail with
  `paths.ticketDetail(id)` from `shared/routing/paths.ts`; it never imports `tickets`. *Why:* keeps the
  dependency graph one-way and free of cycles. The same goes for a Knowledge Chatbot citation that opens a
  Wiki Document (`paths.wikiDocument(id)`).
- Need a new cross-feature dependency? Ask the FE Lead; it is added to the table above and to the lint config.

## 6. Folders that must not appear

`src/components/`, `src/pages/`, `src/hooks/`, `src/utils/`, `src/services/`, `src/store/`,
`src/types/` at the top level. *Why:* they collect unrelated code from every feature — exactly the
files everyone ends up editing at once.

---
*Last verified against code: 2026-10-23, KAN-63 — screens, roles, paths and MSG codes checked against the Report3 foundation (`src/test/guidelines.test.ts`).*
