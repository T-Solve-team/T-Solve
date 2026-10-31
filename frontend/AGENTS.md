# T-Solve web app — guide for developers and AI coding agents

> **Read this file first.** It is the entry point for every person and every AI agent working in
> `frontend/`. It lists the hard rules and links to the detailed guidelines in reading order.
> `CLAUDE.md` only imports this file, so Claude Code, Codex, Cursor and Copilot all read the same text.

## What you are building

T-Solve (Ticket Solution Repository Application) is a knowledge layer next to Jira. A **Ticket Resolver**
writes a **Solution** when they resolve a Jira issue; the **Project Manager** of that **Project** reviews it
in the **Review Queue**; **Published tickets** become searchable and are suggested back inside Jira. **AI
Ticket Triage** suggests solutions on new Jira issues, the **Wiki** holds each Department's documents, and the
**Knowledge Chatbot** answers from both.

This folder is the **T-Solve web app**: a React single-page app with **34 web screens** (26 pages with a path,
8 popups) for five roles — Employee, Ticket Resolver, Project Manager, Knowledge Manager, Admin. A user can
hold several roles, and every user is an Employee. The T-Solve Jira App (screens inside Jira) is a separate
app and is **not** in this folder.

Business truth lives outside the repo: Report3 (the SRS — UC list and Permission Matrix, Business Rules
GBR-01…36, System Messages), the SRS v3460 screen layouts (section III), the FE Lead's decisions D1–D6 and
the Figma file. Everything an agent needs from them is copied into these guidelines: screens and paths (01,
08), business words (03), Figma kit mapping (07), system messages (`src/shared/messages/catalog.ts`).

## Stack (exact versions in `package.json`)

React 19 · TypeScript 6 (strict) · Vite 8 · Ant Design 6 · React Router 8 (data mode) ·
TanStack Query 5 · orval 8 (API client generated from OpenAPI) · MSW 3 (mocks) · Vitest 5 +
React Testing Library · ESLint 10 + Prettier · Node 24 LTS.

## Hard rules

Each rule is enforced by a tool where possible (marked 🔒). Details and examples are in the linked file.

1. **Use the glossary words exactly** — Ticket, Solution, Evidence, Published ticket, Review Queue,
   Project Manager, Ticket Resolver, Space, Wiki document… Never write Cluster, Defer/Deferred, Bulk sync,
   Knowledge article, Attachment (UI), Unassigned Department, a Project filter, or the old role and triage
   words listed in 03 §5. → [03](guidelines/03-naming.md)
2. **Import a feature only through its `index.ts`.** Never reach into another feature's folders. 🔒
   → [01](guidelines/01-project-structure.md)
3. **Never edit `src/shared/api/generated/`.** Change `openapi/tsolve-api.yaml` and run
   `npm run generate:api` (CI fails if the two differ). Never hand-write API types or `fetch` calls;
   import API code only from `@/shared/api` (tests and mocks may also import the generated `*.msw`
   handlers). 🔒 → [06](guidelines/06-data-layer.md)
4. **Server data lives only in TanStack Query.** No `fetch` in `useEffect`, no global store, no copying
   query data into `useState`. → [06](guidelines/06-data-layer.md)
5. **System messages come only from the MSG catalog** (`showMessage('MSG14', { count })`, `msg('MSG01', …)`).
   Never type message text yourself; MSG codes and texts are fixed by the SRS. → [06](guidelines/06-data-layer.md)
6. **Build screens from the Figma kit components in `src/shared/ui/`** plus antd primitives. Raw antd
   `Tag`, `Table`, `Modal`, `Upload` and `message` are banned outside `shared/ui`. 🔒 No hard-coded
   colours, no `style={{…}}` for layout. → [07](guidelines/07-ui-and-styling.md)
7. **TypeScript strict, no `any`, no `@ts-ignore`, no non-null `!`.** 🔒 → [04](guidelines/04-typescript.md)
8. **Every data screen handles loading, error and empty states** (empty = MSG04 unless the SRS names
   another code, e.g. MSG13 for the Review Queue). → [05](guidelines/05-react-components.md)
9. **Access is declared, not scattered:** a route lists its allowed roles; the guard does the rest.
   The backend always re-checks permissions — the UI only hides what a user cannot do.
   → [08](guidelines/08-routing-and-access.md)
10. **Do not edit `src/app/` or `src/shared/` inside a feature PR** without the FE Lead's review. The
    four registration files are pre-filled for all features — you should not need to touch them.
    🔒 (CODEOWNERS, once DevOps adds the lines from 02 §5) → [02](guidelines/02-conflict-avoidance.md)
11. **No new npm dependency** without FE Lead approval and an entry in the decision log; add it in its own
    small PR. → [02](guidelines/02-conflict-avoidance.md), [13](guidelines/13-decision-log.md)
12. **Before you say "done":** `npm run format:check && npm run lint && npm run typecheck && npm run test`
    must pass (`npm run format` fixes formatting). An AI agent (the harness `verify`) also runs `npm run build`,
    and `npm run generate:api` must leave no diff in `src/shared/api/generated` — CI checks both.
    → [11](guidelines/11-code-quality-and-tooling.md)
13. **Every bug gets a row in [`docs/bug-log.md`](../docs/bug-log.md);** its fix comes with a test that names
    the bug id: `it('keeps the selection after Approve (BUG-004)')`. → [10](guidelines/10-testing.md) T6

## npm scripts

| Script | What it does |
|---|---|
| `npm run dev` | Dev server on http://localhost:5173. With `VITE_API_MOCKING=true` it runs fully on mocks. |
| `npm run build` | Type-checks and builds static files into `dist/`. |
| `npm run preview` | Serves `dist/` locally. |
| `npm run lint` / `lint:fix` | ESLint (includes import-boundary rules). |
| `npm run format` / `format:check` | Prettier write / check. |
| `npm run typecheck` | `tsc` with no output. |
| `npm run test` / `test:watch` | Vitest, once / in watch mode. |
| `npm run generate:api` | Regenerates the API client from `openapi/tsolve-api.yaml`. |
| `npm run api:pull` | Downloads the spec from a locally running backend into `openapi/tsolve-api.yaml`. |
| `npm run messages:report3` | Rebuilds `src/shared/messages/report3-messages.json` from Report3 System_Messages (`TSOLVE_DOCS`). |

## Guidelines — reading order

| # | File | Read it when… |
|---|---|---|
| 01 | [Project structure](guidelines/01-project-structure.md) | always, first |
| 02 | [Conflict avoidance & ownership](guidelines/02-conflict-avoidance.md) | always, before your first PR |
| 03 | [Naming & business vocabulary](guidelines/03-naming.md) | always |
| 04 | [TypeScript](guidelines/04-typescript.md) | writing any `.ts`/`.tsx` |
| 05 | [React components](guidelines/05-react-components.md) | writing components or pages |
| 06 | [Data layer & errors](guidelines/06-data-layer.md) | calling the API, showing messages |
| 07 | [UI & styling (Figma kit)](guidelines/07-ui-and-styling.md) | building any screen |
| 08 | [Routing & access](guidelines/08-routing-and-access.md) | adding pages, role checks |
| 09 | [Forms & validation](guidelines/09-forms-and-validation.md) | building forms or popups |
| 10 | [Testing](guidelines/10-testing.md) | writing tests |
| 11 | [Code quality & tooling](guidelines/11-code-quality-and-tooling.md) | lint/CI fails, configuring tools |
| 12 | [Workflow & PR checklist](guidelines/12-workflow.md) | starting a feature, opening a PR |
| 13 | [Decision log](guidelines/13-decision-log.md) | you want to know *why* |

**The reference implementation** is the `review` feature (Review Queue + Reject Ticket popup). When in
doubt, copy how it does things.

## Notes for AI agents

- Load `AGENTS.md` plus only the guideline files your task needs; each starts with an **Applies to** line.
- Work inside one feature folder unless the task says otherwise. If you believe `shared/` or `app/` must
  change, stop and say so instead of editing it.
- Screen names, field names, button labels, statuses and MSG texts are English and fixed by the SRS.
  If the task does not give them and they are not in these guidelines or the code, **ask** — do not invent.
- Do not add files outside the structure in [01](guidelines/01-project-structure.md).
- AI work goes through **HarnessClaude** (the root `CLAUDE.md`): a new screen or a changed behaviour goes into
  its module's spec first and is coded only after the spec is approved; a small fix that changes no acceptance
  criterion is a quick change. Tests name the criterion they prove ([10](guidelines/10-testing.md) T6).
