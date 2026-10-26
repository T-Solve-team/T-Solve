# 02 · Conflict avoidance & ownership

> **Applies to:** every PR that touches `frontend/`.
> **Goal:** two developers working on different features almost never edit the same file.
> Git rules (branch names, commits, squash-merge, syncing with `main`) are in the DevOps design and are
> not repeated here; see [12](12-workflow.md) for the frontend-specific steps.

## 1. Who owns what

| Path | Owner | Who may change it |
|---|---|---|
| `src/features/<feature>/` | the feature's developer | anyone; owner reviews |
| `src/app/` | FE Lead | FE Lead, or anyone with FE Lead review |
| `src/shared/` (incl. `shared/ui/`) | FE Lead | FE Lead, or anyone with FE Lead review |
| `src/shared/api/generated/` | nobody (tool output) | only `npm run generate:api` |
| `openapi/tsolve-api.yaml` | backend owner of the endpoint | changed in the same PR as the endpoint |
| `src/mocks/`, `src/test/` | FE Lead | FE Lead review |
| config files (`package.json`, `eslint.config.js`, `vite.config.ts`, `tsconfig*.json`, `orval.config.ts`) | FE Lead | FE Lead review |
| `guidelines/`, `AGENTS.md` | FE Lead | FE Lead review |

*Why:* shared code has many users; a careless change breaks screens the author never opened. Feature
code has one main user, so its owner can move fast. (Common practice: CODEOWNERS-based review, as in
the DevOps design §1.6.)

## 2. Registration without a central file

The usual hot spots in a React app — the route table, the menu, the mock list, the path constants — are
**pre-filled once for all 15 features and 34 screens**. You fill in your feature folder; you do not
edit these files.

| File | Pre-filled with | What you do instead |
|---|---|---|
| `app/router/feature-routes.ts` | one line per feature: `...reviewRoutes,` | export `<feature>Routes` from your `index.ts` |
| `app/layout/menu-config.ts` | the menu of each role and `menuFor(roles)` for a user with several roles (D4) | nothing — menus are fixed by design |
| `shared/routing/paths.ts` | paths and builders for the 26 routed screens (popups have none) | use `paths.reviewQueue`, `paths.ticketDetail(id)` |
| `mocks/handlers.ts` | one line per feature's `mocks/handlers.ts` | write handlers in your feature's `mocks/handlers.ts` |

Every feature folder already exists with its `index.ts`, its `routes.ts` (all its screens) and a placeholder
page per screen, so every menu item works from day one and you only replace your placeholder pages.

✅ Do — add your screen inside your own feature:

```tsx
// features/search/routes.ts
export const searchRoutes: FeatureRoute[] = [
  {
    path: paths.search,
    title: 'Search', // 6.1
    roles: ['TICKET_RESOLVER', 'PROJECT_MANAGER'],
    lazy: () => import('./pages/SearchPage'),
  },
];
```

❌ Don't — open `app/router/routes.tsx` and add a `/search` route there.

*Why:* if five people each add a line to one route file, every PR conflicts with every other PR.

## 3. Adding or changing a shared component

The Figma kit decides what is shared UI. Most kit components already exist in `shared/ui/`.

1. Check `shared/ui/index.ts` and the `/dev/kit` page (dev builds only) — it may already exist.
2. If your screen needs something that is **not** a "Web/…" kit component, build it inside your feature.
3. If a second feature needs the same thing, or the FE Lead adds it to the Figma kit, it moves to
   `shared/ui/` in a **separate, small PR** (`feat(ui): add <Component> (KAN-…)`) reviewed by the FE Lead.
   Your feature PR then uses it.
4. Changing an existing shared component's props is a breaking change: search for all users first and
   update them in the same PR.

*Why:* one small PR per shared change is quick to review, and it lands before features depend on it,
so features never block each other.

## 4. Files that always conflict — and how to handle them

| File | Rule |
|---|---|
| `package-lock.json` | Add dependencies only in a dedicated PR approved by the FE Lead. If it conflicts while syncing with `main`: take `main`'s version, then run `npm install` and commit the result. **Never hand-edit or hand-merge the lock file.** |
| `src/shared/api/generated/**` | Never hand-merge. Resolve the conflict in `openapi/tsolve-api.yaml` only, then run `npm run generate:api` and commit the output. |
| `openapi/tsolve-api.yaml` | Change only the paths/schemas of your endpoint. Keep the spec sorted as the backend emits it; don't reformat the file. |
| `shared/messages/catalog.ts` | Pre-filled from Report3 System_Messages (`npm run messages:report3` refreshes the reference copy). Changes only when Report3 changes (FE Lead). |

## 5. CODEOWNERS (snippet for DevOps)

DevOps owns `.github/CODEOWNERS`. These are the frontend lines; GitHub handles go in once known.
Later lines win, so feature lines come after the general ones.

```
# ---- frontend ----
/frontend/                                   @fe-lead
/frontend/src/features/auth/                 @owner-auth
/frontend/src/features/my-profile/           @owner-my-profile
/frontend/src/features/users/                @owner-users
/frontend/src/features/workspace/            @owner-workspace
/frontend/src/features/jira-integration/     @owner-jira-integration
/frontend/src/features/ticket-import/        @owner-ticket-import
/frontend/src/features/tickets/              @owner-tickets
/frontend/src/features/search/               @owner-search
/frontend/src/features/review/               @owner-review
/frontend/src/features/feedback/             @owner-feedback
/frontend/src/features/knowledge-dashboard/  @owner-knowledge-dashboard
/frontend/src/features/audit-log/            @owner-audit-log
/frontend/src/features/triage/               @owner-triage
/frontend/src/features/wiki/                 @owner-wiki
/frontend/src/features/chatbot/              @owner-chatbot
/frontend/openapi/                           @fe-lead @backend-lead
```

Everything under `/frontend/` not matched by a feature line (`src/app/`, `src/shared/`, configs,
guidelines) falls back to `@fe-lead`.

## 6. Day-to-day habits that prevent conflicts

- **One Jira issue = one feature folder** (usually one screen). If an issue needs two features, split it.
- **Keep PRs under ~400 changed lines** (excluding generated code and lock file).
- **Sync with `main` at least daily** (DevOps design §1.4) — small conflicts are easy, week-old ones are not.
- **Don't reformat files you didn't change.** Let your editor format on save (Prettier extension) and
  never run `npm run format` on the whole project inside a feature PR — it touches files owned by others.
- **Don't rename or move shared files in a feature PR.**

---
*Last verified against code: 2026-10-23, KAN-63 — screens, roles, paths and MSG codes checked against the Report3 foundation (`src/test/guidelines.test.ts`).*
