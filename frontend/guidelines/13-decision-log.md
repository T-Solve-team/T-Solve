# 13 · Decision log (frontend)

> **Applies to:** every choice behind the other guidelines.
> **Format:** one entry per decision — the decision, the options considered, why. Decided by the FE Lead
> (Trần Trung Hiếu) on **2026-10-09** unless a date says otherwise. To change one, propose it, get the FE
> Lead's approval, then add a new entry that supersedes the old one (never rewrite history).
> Where an entry overrides the DevOps design, it says so; DevOps should update their file to match.
> MSG codes in older entries were renumbered to Report3 on 2026-10-23 (FE-41); no entry's meaning changed.

| ID | Topic | Status |
|---|---|---|
| FE-01 | Library versions | Approved |
| FE-02 | UI kit: antd 6, no ProComponents, component layer mirrors Figma | Approved — overrides DevOps "ProComponents adopted" |
| FE-03 | Routing: React Router 8, data mode | Approved |
| FE-04 | No client-state library | Approved |
| FE-05 | Forms: Ant Form + MSG rule builders | Approved |
| FE-06 | Styling: tokens + CSS Modules | Approved |
| FE-07 | API client: orval, spec and output committed | Approved |
| FE-08 | Error contract with MSG codes | Approved — needs backend agreement |
| FE-09 | Session: backend OIDC + cookie + `/me` | Approved — needs backend agreement |
| FE-10 | "Real-time" Review Queue = 5 s polling | Approved |
| FE-11 | Mocking: MSW | Approved |
| FE-12 | Testing scope and tools | Approved — closes DevOps open item #3 |
| FE-13 | One path alias `@/` | Approved |
| FE-14 | Import boundaries in ESLint | Approved |
| FE-15 | Node 24 LTS | Approved |
| FE-16 | Dates: dayjs, UTC+7 | Approved |
| FE-17 | Git hooks and CI stay with DevOps | Approved (option rejected: hooks in `frontend/`) |
| FE-18 | Guidelines location and format | Approved |
| FE-19 | Jira App is a separate app | Approved |
| FE-20 | Replace the old skeleton | Approved |
| FE-21 | Strict TypeScript | Approved — overrides DevOps "TypeScript (non-strict)" |
| FE-22 | Feature list and folder structure | Approved |
| FE-23 | Reference feature: Review Queue | Approved |
| FE-24 | Not adopted | Approved |
| FE-25 | ESLint 10, no accessibility lint plugin | Approved — supersedes the ESLint part of FE-01 |
| FE-26 | Import boundaries with import-x instead of eslint-plugin-boundaries | Approved — supersedes the tool in FE-14 |
| FE-27 | Prettier does not format Markdown | Approved |
| FE-28 | Screen title in the AppShell header, from the route | Approved (step 5.2) |
| FE-29 | Library chunks and antd reset.css | Approved (step 5.2) |
| FE-30 | MSW worker served by Vite, mock mode in dev builds only | Approved (step 5.3) |
| FE-31 | npm override for Vitest's optional msw peer | Approved (step 5.3) |
| FE-32 | API shapes proposed by the OpenAPI stub | Approved (step 5.3) — needs backend agreement |
| FE-33 | Where errors are shown: queries in place, mutations as toasts | Approved (step 5.3) — refines FE-08 |
| FE-34 | Review Queue keeps Request changes, for all selected tickets | Approved (2026-10-10) — kept by D1 (2026-10-21); Report3 GBR-09 and UC-30 to be updated |
| FE-35 | Screen sizes win over kit sizes | Approved (2026-10-10) |
| FE-36 | Kit built in step 5.4; uploads and date-range rule deferred | Approved (step 5.4) |
| FE-37 | List state in the URL with `useListSearchParams` | Approved (step 5.4) |
| FE-38 | Mock data is reset after every test | Approved (step 5.4) |
| FE-39 | Docker image: pinned Node build + non-root Caddy on 8080, CSP | Approved (step 5.5) |
| FE-40 | Five roles, several roles per user: landing page, menus, Workspace Setup | Approved (2026-10-22) — supersedes the one-role session of FE-09 |
| FE-41 | Report3 message catalog | Approved (2026-10-22) — supersedes the SRS V.2 catalog of FE-08 |
| FE-42 | Route map for the 35 Report3 screens, 15 features | Approved (2026-10-22) — supersedes the feature list of FE-22 |

---

**FE-01 — Library versions.** React 19.3 · Vite 8 · antd 6 · TanStack Query 5 · orval 8 · React Router 8 ·
Vitest 5 · MSW 3 · **TypeScript 6.0** · ESLint 9.39 *(ESLint superseded by FE-25)*.
*Options:* latest majors everywhere (TypeScript 7, ESLint 10). *Why:* on 2026-10-09 `typescript-eslint`
supports TypeScript < 6.1 and TypeScript 7.0 has no stable compiler API until 7.1; `eslint-plugin-jsx-a11y`
does not declare ESLint 10 support, so `npm ci` would fail on the peer dependency. Revisit when both are fixed.

**FE-02 — UI kit.** antd 6 without ProComponents. `src/shared/ui/` mirrors Figma kit v4 ("WEB COMPONENTS ·
Web/…") one-to-one; props mirror Figma variant properties; tokens from Figma STYLES in `theme.ts`; raw antd
`Tag/Table/Modal/Upload/message/notification` banned outside `shared/ui`. Figma placeholder colours
(`text/disabled`, `bg/disabled`, `bg/hover`, `bg/layout`, `bg/tooltip`) use antd defaults; font Inter
self-hosted (`@fontsource/inter`).
*Options:* antd 5 + ProComponents 2.x; antd 6 + ProComponents 3 beta. *Why:* ProComponents 2.x supports only
antd 5; antd 5's last release was Dec 2025 and ProComponents 2.x's Jul 2025; ProComponents 3 has been in beta
for 15 months — not acceptable in a graded project. ProTable/ProLayout also bring their own look, which
fights the Figma App Shell. *Overrides* DevOps design §0/§10 "ProComponents adopted".

**FE-03 — Routing.** React Router 8, data mode (`createBrowserRouter`); each feature exports `FeatureRoute[]`
with required `roles` and lazy pages; no route loaders for data.
*Options:* TanStack Router; React Router declarative mode. *Why:* widely known, data mode gives lazy routes
and `useBlocker` (MSG37); TanStack Router's type safety costs code generation and learning time.

**FE-04 — No client-state library.** Session via TanStack Query (`/me`), filters in URL search params,
local state otherwise. *Options:* Zustand, Redux Toolkit. *Why:* almost all state is server data or URL
state; a store would duplicate it. Revisit only for a concrete need.

**FE-05 — Forms.** Ant Form + shared rule builders that output SRS MSG texts; validate on blur; server
`fieldErrors` mapped by field name. *Options:* react-hook-form + zod. *Why:* antd inputs are built for Ant
Form; a second library adds adapters and a second set of rules.

**FE-06 — Styling.** antd theme tokens + CSS Modules using antd CSS variables; no global CSS, no `.ant-*`
overrides. *Options:* Tailwind, CSS-in-JS (antd-style), Sass. *Why:* zero extra dependency (built into Vite),
class names scoped automatically (no cross-feature clashes), one source of values (the tokens).

**FE-07 — API client.** orval → TanStack Query hooks + a fetch wrapper (`http.ts`), split by tag, with MSW
mocks. Spec committed at `openapi/tsolve-api.yaml`; generated code committed in `src/shared/api/generated/`
and excluded from SonarQube. *Options:* generate in CI only; hand-written client. *Why:* app runs right
after `npm ci` without a backend; API changes visible in PR diffs; no hand-written duplicate types.

**FE-08 — Error contract.** RFC 9457 Problem Details + `code` (MSGxx) + `params` + `fieldErrors[]`; the
frontend holds the MSG catalog (SRS V.2; *source superseded by FE-41*); unknown → MSG06. *Options:* backend sends final text; HTTP status
only. *Why:* the SRS fixes MSG texts by code; one catalog keeps them exact. **Needs backend agreement** (the
current backend returns `{status, message}`). Also assumed: server-side pagination `page`/`size`/`sort`, 20 rows.

**FE-09 — Session.** Backend runs OIDC (Google) and sets an HttpOnly session cookie; `GET /api/v1/me`
returns user, role (*several roles: FE-40*), Projects (with role in each) and Departments; 401 → MSG07 → Login; sign-in errors
come back as `/login?error=MSGxx`. *Options:* tokens in the browser (localStorage). *Why:* no token handling
in JavaScript (XSS-safe); matches SRS API-05. **Needs backend agreement.**

**FE-10 — Review Queue "real time".** TanStack Query polling every 5 s while the tab is visible.
*Options:* WebSocket, Server-Sent Events. *Why:* meets the NFR (visible within 5 s) with no server work;
upgrade later only if needed.

**FE-11 — Mocking.** MSW 3: orval-generated fake-data handlers as a base, hand-written per-feature handlers
for realistic data; `VITE_API_MOCKING=true`; same handlers in tests; dev-only role switcher (*a user switcher since FE-40*).
*Options:* json-server, hand-mocked hooks. *Why:* intercepts real `fetch`, so mock mode and tests exercise the
real data layer.

**FE-12 — Testing.** Vitest + React Testing Library + user-event + jsdom + MSW; kit components, shared
logic, one page test per feature, every mutation flow; no coverage gate, no E2E in the template.
*Options:* Jest; Playwright E2E. *Why:* Vitest reuses the Vite config; E2E upkeep is too high for a 14-week
part-time MVP. Closes DevOps design open item #3 (Vitest vs Jest).

**FE-13 — Path alias.** Only `@/` → `src/`. *Why:* one rule to remember; relative imports inside a feature.

**FE-14 — Import boundaries.** ESLint rule *(tool superseded by FE-26)*: `app → features → shared`; another feature only via
its `index.ts`; allowed cross-feature pairs: `tickets → review`, `tickets → feedback`. *Why:* keeps features
independent so five people can work in parallel; enforced by CI, not memory.

**FE-15 — Node.** Node 24 LTS in `.nvmrc`, CI and Docker; `engines` `^22.22.2 || >=24.15.0` (jsdom 30
minimum, which is stricter than React Router 8's 22.22; corrected during the scaffold).
*Why:* supported until 2028; covers the whole project.

**FE-16 — Dates.** dayjs (already an antd dependency) with one format, `DD/MM/YYYY HH:mm` (`formatDateTime`;
`formatDate` = `DD/MM/YYYY`), Asia/Ho_Chi_Minh (SRS III, NFR 1.1). *Options:* date-fns, Intl only. *Why:* no extra dependency; antd pickers use it.

**FE-17 — Git hooks and CI.** No Husky, lint-staged, commitlint or `.github/` files in `frontend/`; DevOps
owns them. The frontend provides configs and npm scripts ([11](11-code-quality-and-tooling.md) §5).
*Rejected option:* Husky 9 in `frontend/.husky`. *Why:* the FE Lead assigned hooks and CI to DevOps.

**FE-18 — Guidelines.** `frontend/AGENTS.md` as entry, `frontend/CLAUDE.md` = `@AGENTS.md`, topic files in
`frontend/guidelines/NN-*.md`. *Why:* no `docs/` folder at the repo root (DevOps); `AGENTS.md` is read by most
AI coding tools, and the import avoids duplicate text.

**FE-19 — Jira App.** Not in `frontend/`; a separate Forge app (Atlassian Design System per NFR) that shares
only the OpenAPI spec. No npm workspaces in the MVP. *Why:* different design system, auth and build.

**FE-20 — Old skeleton.** The pre-SRS `frontend/` code (Solutions page, `DRAFT…ARCHIVED` statuses) is deleted
and replaced; `*.tsbuildinfo` is ignored. *Why:* it contradicts the glossary and SRS v2.

**FE-21 — Strict TypeScript.** `strict` plus `noUncheckedIndexedAccess`, `erasableSyntaxOnly`,
`verbatimModuleSyntax`; no `any`, no `!`, no `enum`. *Overrides* DevOps design open item #4 "TypeScript
(non-strict)". *Why:* the compiler catches the bugs juniors most often ship; TS 6 is strict by default anyway.

**FE-22 — Structure and features.** `src/app` (FE Lead), `src/features/<feature>` (owner), `src/shared`
(FE Lead); 12 features covering the 25 web screens (*superseded by FE-42*); four registration files pre-filled once. All review
decisions live in `review`. *Why:* see [01](01-project-structure.md) and [02](02-conflict-avoidance.md).

**FE-23 — Reference feature.** Review Queue (7.1) + Reject Ticket popup (7.3), built end to end in the
template. *Options:* Ticket List (6.2). *Why:* covers polling, row selection, bulk mutation with confirm,
a form popup, MSG toasts and empty state — every pattern the team needs.

**FE-24 — Not adopted.** React Compiler, Storybook (a dev-only `/dev/kit` page instead), an i18n library
(UI is English-only), Nx/Turbo, Redux, optimistic updates. **Deferred:** chart library for the Knowledge
Dashboard (choose when that feature starts, checking antd 6 compatibility).

**FE-25 — ESLint 10, no accessibility lint (2026-10-09, during the scaffold).** ESLint 10.12 without any
a11y plugin. *Options:* stay on ESLint 9 (end-of-life: npm marks it unsupported); ESLint 10 +
`eslint-plugin-jsx-a11y-x` (maintained fork). *Why:* the only reason for ESLint 9 was
`eslint-plugin-jsx-a11y` (no release since Oct 2024, no ESLint 10 support); the FE Lead removed the
accessibility NFR from the SRS, so the plugin is not needed. Semantic HTML basics stay as good practice
([05](05-react-components.md) §3). Supersedes the ESLint part of FE-01.

**FE-26 — Import boundaries with import-x (2026-10-09).** `eslint-plugin-import-x` rule
`no-restricted-paths` with six zones (guideline 11 §2), plus `eslint-import-resolver-typescript`.
*Options:* `eslint-plugin-boundaries` 7.2 (pins `handlebars` 4.7.9 — critical advisory — and
`micromatch` → `braces` — high, no fixed version; an `overrides` entry could fix only the first);
`dependency-cruiser` (0 advisories, but a separate CLI: no editor feedback, own config language, extra CI step);
`eslint-plugin-project-structure` (same `braces` advisory); built-in `no-restricted-imports` (cannot see
relative paths that cross features). *Why:* import-x resolves real file paths, so `@/…` and `../../…` deep
imports are both caught; it supports ESLint 10, is actively maintained, and `npm audit` reports 0
vulnerabilities. Tested on the scaffold: every forbidden import fails, every allowed one passes — and it adds
two checks the old setup lacked (features → `app/`, non-test code → test helpers). Supersedes the tool named in FE-14.

**FE-27 — Prettier does not format Markdown (2026-10-09).** `*.md` is in `.prettierignore`. *Why:* Prettier
pads every Markdown table to equal column widths — each small doc edit becomes a whole-table diff, and the
padding adds tokens every time an AI agent loads the guidelines.

**FE-28 — Screen title in the AppShell header (2026-10-09, step 5.2).** Each `FeatureRoute` has a `title`
(the SRS screen name); the AppShell header shows it and the browser tab reads "<title> · T-Solve". There is
no `PageHeader` component. *Why:* the Figma App Shell puts the page title in the header, so it belongs to the
shell, not to each page; one source (the route) keeps header, tab and menu consistent.

**FE-29 — Library chunks and antd reset (2026-10-09, step 5.2).** `vite.config.ts` splits React, antd and other
libraries into their own chunks; `antd/dist/reset.css` (with the Inter font files) is the only global CSS. *Why:* our code is ~20 kB and
changes every deploy, while the libraries (about 270 kB gzipped) rarely change, so browsers keep
them cached; without a reset the browser's default `body` margin offsets the shell.
*Update (step 5.4):* with Table, Form, Modal and Select in use the libraries are ~370 kB gzipped; the antd
chunk alone is ~800 kB (~260 kB gzipped), so `chunkSizeWarningLimit` is 1000 kB. The warning would otherwise show on every build and
teach people to ignore build warnings; splitting antd further gains nothing because it is cached as one file.

**FE-30 — MSW worker served by Vite; mock mode in dev builds only (2026-10-10, step 5.3).** `msw/vite` in
`worker-only` mode serves `/mockServiceWorker.js` from `node_modules` on the dev server; nothing is committed
in `public/`. Mocks start only when `import.meta.env.DEV` and `VITE_API_MOCKING=true` (written literally in
`main.tsx`, so production builds contain no MSW, faker or mock data — checked on the built bundle).
*Options:* `npx msw init public` (a committed copy that must be regenerated on every MSW upgrade, or the
browser warns about a version mismatch). *Why:* the worker can never drift from the installed version.
Refines FE-11.

**FE-31 — npm override for Vitest's optional msw peer (2026-10-10, step 5.3).** `package.json` →
`"overrides": { "@vitest/mocker": { "msw": "$msw" } }`. *Why:* Vitest 5.0.3's `@vitest/mocker` declares an
*optional* peer `msw ^2.4.9`, used only by Vitest browser mode (not used here). Without the override `npm ci`
still works but `npm ls` reports the tree as invalid. Remove the override when Vitest declares MSW 3.

**FE-32 — API shapes proposed by the OpenAPI stub (2026-10-10, step 5.3).** `openapi/tsolve-api.yaml`
(tags `session`, `review`) proposes, for the backend to confirm or replace:
`GET /api/v1/me` (401 when nobody is signed in) and `POST /api/v1/auth/logout` (204);
Spring paging (`page` from 0, `size`, `sort=field,dir`; response `{ content, page: { size, number,
totalElements, totalPages } }` = Spring Data `PagedModel`); `GET /api/v1/review-queue/ticket-ids` so
"Select all" can take every ticket in the queue, not only the page shown (SRS 7.1);
`POST /api/v1/reviews/{approve|reject|request-changes}` with `{ ticketIds, reason | comment }` returning
`{ count }` (the `{count}` of MSG14 / MSG16 / MSG17); enum spellings as in guideline 03 §5. *Why:* each shape
is what Spring produces with the least custom code, and the frontend can be built and tested before the
backend exists. When the backend's spec differs, `npm run api:pull` + `generate:api` wins.

**FE-33 — Where errors are shown (2026-10-10, step 5.3).** A failed **query** is shown in place by the
screen (`ErrorState` with `msg(error.code)`), never as a toast; a failed **mutation** gets a global toast
unless it handles its own error (`meta.handlesOwnErrors`); a 401 while signed in shows MSG07 once and then
resets the session. `ApiError.code` falls back to MSG07 (401), MSG08 (403) or MSG06. *Options:* a global
MSG06 toast for every failure (the guideline draft). *Why:* a failed list already shows its error in the
page, and a toast as well would be two messages for one error. Refines FE-08 and guideline 06 §6.

**FE-34 — Review Queue keeps Request changes (Hiếu, 2026-10-10).** The Review Queue (7.1) has a Request
changes button next to Reject and Approve (Figma callout 6). It opens the Request Changes popup (7.2) for
**all selected tickets with one comment**, like Reject; each author sees the same comment in Jira. Every
queue ticket comes from the Solution Form, so MSG18 cannot apply on the queue (it still applies on Ticket
Detail). SRS v2 7.1 / 7.2 still describe the older behaviour — Hiếu updates them, including a count form of
MSG17 for several tickets. **Kept by D1 (Hiếu, 2026-10-21):** Report3 GBR-09 and UC-30 still say one ticket
at a time; Report3 is to be updated, the frontend keeps this behaviour.

**FE-35 — Screen sizes win over kit sizes (Hiếu, 2026-10-10).** When a Figma screen and the kit disagree on a
size or spacing, the screen wins (screens were designed after the kit); colours and fonts always come from
the styles. Examples: Reject Ticket popup 600 px wide (kit pattern 520); Review Queue table card radius 8
(kit Card 12).

**FE-36 — Kit scope in step 5.4 (2026-10-10).** Built: `StatusTag`, `DataTable` + `cells`, `EmptyState`,
`LoadingState`, `ErrorState`, `ConfirmDialog`, `FormModal`, `AppForm`, `FormField`, `SectionCard`,
`StatCard`, `shared/forms` (`rules`, `applyApiErrors`) and the `/dev/kit` page. Deferred to the first screen
that needs them: `EvidenceUpload`, `CsvUpload`, `rules.dateRangeNotFuture` (MSG39). Kit boolean props follow
guideline 05 C4 (`isOpen`, `isDanger`, not `open`, `danger`). Table look from Figma as `Table` tokens in
`theme.ts` (padding 12 × 8, Body Strong headers). *Why:* the example feature uses everything built; uploads
need their own design check against the Figma Upload variants and the Evidence rules.

**FE-37 — List state in the URL (2026-10-10).** `useListSearchParams(defaultSort)` (`shared/routing`) keeps
page, sort and filters of a list in the URL. The URL `page` counts from 1 like the UI; `apiPaging` converts to
the API's page from 0 (FE-32). A filter or sort change goes back to page 1. *Why:* the back button, reload and
shared links show the same list (guideline 05 C8), and every list screen handles it the same way.

**FE-38 — Mock data is reset after every test (2026-10-10).** Each feature's `mocks/handlers.ts` exports
`handlers` and `resetMockData()`; `src/test/setup.ts` calls every `resetMockData()` after each test. Mutable mock
data (e.g. the Review Queue removing decided tickets) lives in a module variable of the feature's mocks.
*Why:* mocks that behave like the backend make mock mode convincing, and tests stay independent (guideline 10 T4).

**FE-39 — Docker image (Hiếu, 2026-10-10, step 5.5).** Stage 1 `node:24.21.0-alpine3.24` builds; stage 2
`caddy:2.11.7-alpine` serves `dist/` on port **8080** as user 65534 (nobody), with `/healthz` for the Docker
health check. Base images pinned to exact versions (Dependabot updates them). The Caddyfile serves files
only: one-year immutable cache for `/assets/*`, a plain 404 for missing assets and for `/api/*`, SPA fallback
with `no-cache` for everything else, and a Content-Security-Policy that allows only our own origin
(`style-src 'unsafe-inline'` for antd's run-time CSS). HTTPS, HSTS and the `/api` route stay in DevOps's main
Caddy. *Options:* the image only builds `dist/` and DevOps's main Caddy serves the files (one Caddy, but
frontend cache and CSP rules would live in a DevOps file, and deploy/rollback would copy files instead of
swapping image tags) — rejected by Hiếu: keep the image self-contained. *Why:* a non-root process on an unprivileged port is the default every scanner (Sonar, hadolint) asks
for; pinned images make two builds of one commit identical; the CSP belongs here because it depends on what
the frontend loads, and it was checked in Chromium (no violations on the Review Queue and its popups).

**FE-40 — Five roles, several per user (Hiếu, 2026-10-22, KAN-58…61).** The web app knows the five Report3
roles (Employee, Ticket Resolver, Project Manager, Knowledge Manager, Admin). `/me` returns all of a user's
roles; the session always adds Employee (GBR-28) and drops unknown values. A route opens for any of its roles
(`hasAnyRole`). Landing page: the first match of Admin → User List, Project Manager → Review Queue, Knowledge
Manager → Wiki Home, Ticket Resolver → Search, otherwise Knowledge Chatbot. One menu for all roles,
`menuFor(roles)`: the role menus in the order Admin, Project Manager, Knowledge Manager, Ticket Resolver,
Employee, each item once. While `workspaceReady` is false an Admin is sent to 2.1 Workspace Setup. Mock mode
has fixture users with one role and with several (dev user switcher). *Options:* one role per user, as in
SRS v2. *Why:* Report3 GBR-28 lets a user hold several roles; decision D4 settled the landing order, the
menus and the Workspace Setup trigger.

**FE-41 — Report3 message catalog (Hiếu, 2026-10-22, KAN-62).** `catalog.ts` holds the web codes of Report3
System_Messages with their exact texts and types, plus `MSG_NEW_2` (D3). Left out: the Jira App messages and
the codes dropped by D2 and D3. `{tracker_name}` is filled with `TRACKER_NAME` ("Jira", D4). A committed copy
(`report3-messages.json`, built by `npm run messages:report3`) lets `catalog.test.ts` check every text.
Every code used in the app and in these guidelines was renumbered by meaning. *Options:* keep the SRS V.2
codes and map them at the API edge. *Why:* Report3 renumbered every code; one catalog with Report3's numbers
means the code, the docs and the backend use the same code for the same message.

**FE-42 — Route map for the 35 Report3 screens (Hiếu, 2026-10-22, KAN-61).** 26 screens with a path and 9
popups, in 15 features: the 12 of FE-22 plus `triage` (10.x), `wiki` (11.x) and `chatbot` (12.1); 2.1 is now
Workspace Setup and the Workspace screens are renumbered 2.2–2.4. New screens start as placeholders.
*Why:* Report3 adds AI Ticket Triage, the Wiki and the Knowledge Chatbot; registering every screen once
keeps feature PRs out of the shared route, menu and mock files (guideline 02).

## Working conventions (not code decisions)

- Each change goes on its own short-lived branch; PR title and description follow the DevOps template.
- AI attribution: commits keep `Co-Authored-By`; PR descriptions keep "Generated with Claude Code";
  no session links.
