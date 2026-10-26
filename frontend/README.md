# T-Solve web app (frontend)

React single-page app for T-Solve — the knowledge layer next to Jira. 25 screens for Admin, Department
Manager, Project Manager and Staff. The T-Solve Jira App is a separate app and is not in this folder.

**New here? Read [`AGENTS.md`](AGENTS.md) first** — hard rules, scripts and the guideline reading order
(the same file your AI coding agent reads). Then build your first screen with the walkthrough in
[guideline 12 §1](guidelines/12-workflow.md).

## Prerequisites

- **Node 24 LTS** (see `.nvmrc`; 24.15+ or 22.22.2+ works). With nvm: `nvm use`.
- npm (comes with Node).
- For the real backend: the backend running on `http://localhost:8080` (see the root `README.md`).
- VS Code with the **ESLint** and **Prettier** extensions, *Format On Save* on.

## Quick start

```bash
cd frontend
npm ci                    # exact versions from package-lock.json
cp .env.example .env.local
npm run dev               # http://localhost:5173
```

### Run without the backend (mock mode)

Set `VITE_API_MOCKING=true` in `.env.local` (the default in `.env.example`). Every API call is answered by
MSW mocks (`src/mocks/`, plus each feature's `mocks/handlers.ts`). A small **role switcher** (dev builds in
mock mode only) picks which fixture user `GET /api/v1/me` returns, so you can see the app as Admin, Department
Manager, Project Manager or Staff — or signed out.

### Run against the real backend

Set `VITE_API_MOCKING=false`. The dev server proxies `/api` to `http://localhost:8080`, so the session
cookie works without CORS settings. The Workspace is decided by the Host of the page, so open the app at
`http://<site name>.localhost:5173` (browsers resolve `*.localhost` to your machine; the proxy keeps the Host
header). `http://admin.localhost:5173` opens the Platform Console.

## Environment variables

| Variable | Default | Meaning |
|---|---|---|
| `VITE_API_MOCKING` | `true` | `true` = MSW mocks, `false` = real backend |

There is no API address setting: the app always calls `/api/v1/...` on the origin it was opened from.

`VITE_*` values are built into the JavaScript the browser downloads — **never put secrets in them**.

## Scripts

| Script | What it does |
|---|---|
| `npm run dev` | Dev server with hot reload |
| `npm run build` | Type-check and build static files into `dist/` |
| `npm run preview` | Serve `dist/` locally |
| `npm run lint` / `lint:fix` | ESLint (includes import-boundary rules) |
| `npm run format` / `format:check` | Prettier |
| `npm run typecheck` | TypeScript, no output |
| `npm run test` / `test:watch` | Vitest |
| `npm run generate:api` | Regenerate the API client from `openapi/tsolve-api.yaml` |
| `npm run api:pull` | Download the spec from the local backend |

Before pushing: `npm run format:check && npm run lint && npm run typecheck && npm run test`.

## Docker

```bash
docker build -t tsolve-frontend .
docker run --rm -p 8081:8080 tsolve-frontend   # http://localhost:8081 — static files served by Caddy
```

The image only serves the built files; `/api` answers 404 inside it. Details:
[guideline 11 §6](guidelines/11-code-quality-and-tooling.md).

## Where things are

| Path | What |
|---|---|
| `src/features/` | one folder per feature (screens, hooks, mocks, tests) |
| `src/shared/ui/` | Figma kit components |
| `src/app/` | providers, router, guards, app shell |
| `guidelines/` | the rules — start with `01-project-structure.md` |
| `openapi/tsolve-api.yaml` | API spec (input of the generated client) |

Project documents (SRS, glossary, Figma) live on Google Drive and Figma, not in the repo.
