# 11 · Code quality & tooling

> **Applies to:** `eslint.config.js`, `.prettierrc.json`, `.prettierignore`, `tsconfig*.json`, `vite.config.ts`,
> `orval.config.ts`, `package.json` scripts, `Dockerfile`, `Caddyfile`.
> **Why it matters:** rules that live in tools don't depend on anyone's memory. The same commands run on
> your machine, in the git hooks and in CI, so "it passed for me" means it passes everywhere.
> **Ownership:** these files belong to the FE Lead. Git hooks (Husky, lint-staged, commitlint), CI
> workflows, CODEOWNERS and Dependabot belong to **DevOps** (decision log) — §5 tells DevOps what to call.

## 1. The quality gates

| Check | Command | Blocks merge (in CI) |
|---|---|---|
| Formatting | `npm run format:check` | yes |
| Lint (incl. import boundaries) | `npm run lint` | yes (errors) |
| Types | `npm run typecheck` | yes |
| Tests | `npm run test` | yes |
| Build | `npm run build` | yes |
| Generated API up to date | `npm run generate:api` + no git diff | yes |
| SonarQube Cloud | (CI, DevOps) | no — advisory |

Run these before every push: `npm run format:check && npm run lint && npm run typecheck && npm run test`
(`npm run format` fixes formatting). Format On Save and, once DevOps adds it, the pre-commit hook (§5)
keep formatting right as you go.

## 2. ESLint (flat config, ESLint 10)

Plugins: `typescript-eslint` (type-aware), `eslint-plugin-react-hooks`, `eslint-plugin-react-refresh`,
`eslint-plugin-import-x` (+ `eslint-import-resolver-typescript` so it understands `@/` and `.ts`),
`@tanstack/eslint-plugin-query`, and `eslint-config-prettier` last (turns off rules Prettier handles).
No accessibility plugin (the accessibility NFR was removed — decision log FE-25).

| Rule | Level | Why |
|---|---|---|
| `import-x/no-restricted-paths` | error | The architecture ([01](01-project-structure.md)), as six zones in `eslint.config.js`: a feature imports another feature only through its `index.ts` and only for the allowed pairs (`ALLOWED_FEATURE_DEPENDENCIES`); features never import `app/`; `shared` never imports `features`, `app` or `mocks` (its tests may use the mock server); `app` uses a feature only through its `index.ts`; `src/mocks` imports only features' `mocks/`; only test files import `src/test` and `src/mocks`. Imports inside one feature are free. The feature list is read from `src/features/`, so a new feature folder is covered automatically. |
| `no-restricted-imports` (antd `Tag`, `Table`, `Modal`, `Upload`, `message`, `notification` outside `shared/ui`) | error | Use the kit component ([07](07-ui-and-styling.md) U1). The error message names it. |
| `no-restricted-imports` (`@/shared/api/generated/*`, `react-router-dom`, `axios`) | error | Import from `@/shared/api`; v8 has no `react-router-dom`; one HTTP client. |
| `no-restricted-globals` / `no-restricted-properties` (`fetch`, `window.fetch`, `globalThis.fetch` outside `shared/api/http.ts`, mocks and tests) | error | One HTTP layer: call the API through the generated hooks ([06](06-data-layer.md) §2). |
| `no-restricted-imports` (`msw`, `@faker-js/faker`, `@/shared/api/mocks` outside `mocks/` folders and tests) | error | Mock code must never reach the production bundle ([10](10-testing.md) §5). Mock files and tests may also import the generated `*.msw` handlers — nothing else from `generated/`. |
| `@typescript-eslint/no-explicit-any`, `ban-ts-comment`, `no-non-null-assertion` | error | [04](04-typescript.md) R1–R3. |
| `@typescript-eslint/consistent-type-imports` | error | `import type` for types (auto-fixable). |
| `@typescript-eslint/no-floating-promises`, `no-misused-promises` | error | A forgotten `await`/`.catch` hides errors. |
| `react-hooks/rules-of-hooks` · `react-hooks/exhaustive-deps` | error · warn | Hooks called correctly; effect dependencies complete. |
| `@tanstack/query/exhaustive-deps` | error | Query keys include every variable the query uses. |
| `react-refresh/only-export-components` | warn | Keeps hot reload working. |
| `no-console` (allows `warn`, `error`) | warn | No debug logs left behind; never log ticket content or personal data (DevOps §8). |
| `eqeqeq` | error | `===` only. |

**Warnings vs errors.** The DevOps design starts rules as warnings in sprint 1. We apply that to style-ish
rules only; **architecture and safety rules are errors from day one** because the template starts clean
and they are what keep five people from colliding. `npm run lint` fails on errors, not warnings.

Disabling a rule: only on one line, with a reason, and mention it in the PR:
`// eslint-disable-next-line react-hooks/exhaustive-deps -- runs once on mount by design`.
Never disable a rule for a whole file; never edit `eslint.config.js` in a feature PR.

## 3. Prettier

`.prettierrc.json`: `singleQuote: true`, `semi: true`, `trailingComma: "all"`, `printWidth: 100`.
`.prettierignore`: `dist/`, `src/shared/api/generated/` (orval formats it with Prettier itself),
`openapi/` (the spec stays as the backend emits it), `package-lock.json`, and **`*.md`** — guidelines are
hand-formatted with compact tables (Prettier would pad every table: bigger diffs and more tokens for AI agents).

Install the **ESLint** and **Prettier** extensions in VS Code and turn on *Format On Save*.
(Line endings are LF — the repo's `.gitattributes` enforces it, which matters on Windows.)

## 4. npm scripts

| Script | Runs |
|---|---|
| `dev` | `vite` |
| `build` | `tsc -b && vite build` |
| `preview` | `vite preview` |
| `lint` / `lint:fix` | `eslint .` / `eslint . --fix` |
| `format` / `format:check` | `prettier --write .` / `prettier --check .` |
| `typecheck` | `tsc -b --noEmit` |
| `test` / `test:watch` | `vitest run` / `vitest` |
| `generate:api` | `orval --config orval.config.ts` |
| `api:pull` | `node scripts/pull-api.mjs`: download `http://localhost:8080/v3/api-docs.yaml` (or `$API_DOCS_URL`) into `openapi/tsolve-api.yaml` |

`npm ci` (not `npm install`) everywhere except when you deliberately add a dependency. Node version comes
from `.nvmrc` (24); `engines` in `package.json` allows 22.22.2+ or 24.15+ (jsdom 30 minimum).

## 5. For DevOps: what hooks and CI should call

All commands run **inside `frontend/`**.

**pre-commit (on staged files under `frontend/`):**
`prettier --write` on `*.{ts,tsx,js,css,json}` and `eslint --fix` on `*.{ts,tsx}`. Exclude
`src/shared/api/generated/**`.

**CI job (path filter `frontend/**`), in order:**

```bash
npm ci
npm run format:check
npm run lint
npm run typecheck
npm run test
npm run generate:api && git diff --exit-code -- src/shared/api/generated   # spec and generated code in sync
npm run build
```

Use Node from `frontend/.nvmrc` and cache `~/.npm` keyed on `frontend/package-lock.json`.

**SonarQube Cloud** (`sonar-project.properties`, DevOps):
`sonar.exclusions=frontend/src/shared/api/generated/**,frontend/dist/**`
· `sonar.tests=frontend/src` with `sonar.test.inclusions=**/*.test.ts,**/*.test.tsx`.
*Why:* generated code is thousands of lines that would eat the free plan's 50k-LOC limit and report
"duplication" nobody can fix.

**Main Caddy (per environment, DevOps):** HTTPS and HSTS; `/api/*` → the backend; everything else →
`reverse_proxy <web app container>:8080`. The web app image has its own `Caddyfile` that only serves the
built files (§6) — the two never overlap. Health check: `GET /healthz` → 200.

**Dependabot:** weekly for `/frontend` (npm, and `docker` for the two base images in the `Dockerfile`);
group minor/patch updates into one PR; majors one by one —
the FE Lead checks them against [13](13-decision-log.md) (e.g. TypeScript 7 is held back on purpose; keep
`typescript` on `~6.0`).

**`npm audit`** should report 0 vulnerabilities. If it doesn't, tell the FE Lead before adding an
`overrides` entry or running `npm audit fix --force` (which can silently downgrade packages).

## 6. Build and Docker

```bash
docker build -t tsolve-frontend .                 # inside frontend/
docker run --rm -p 8081:8080 tsolve-frontend      # http://localhost:8081
```

**`Dockerfile`** (multi-stage, decision FE-39):

- Stage 1 `node:24.21.0-alpine3.24` runs `npm ci` (its own layer, cached until `package*.json` change)
  then `npm run build`. It runs on the build machine's CPU type (`--platform=$BUILDPLATFORM`), because the
  output is the same static files for every CPU.
- Stage 2 `caddy:2.11.7-alpine` copies `dist/` to `/srv` and our `Caddyfile`; runs as user `65534`
  (nobody), listens on **8080**, `HEALTHCHECK` on `/healthz`.
- Base images are pinned to exact versions; Dependabot (DevOps, `docker` ecosystem for `/frontend`) proposes
  the updates. Keep the Node major in line with `.nvmrc`.
- `VITE_*` variables are build-time: pass them as build args (`--build-arg VITE_…=…`); never put secrets in
  them (they end up in the browser). The Dockerfile declares none: the app calls its own origin, so one image
  serves every Workspace site — a new `VITE_*` variable needs its own `ARG` line there. `VITE_API_MOCKING` has
  no effect in a production build.
- **`.dockerignore`** keeps `node_modules`, `dist`, every `.env*` file and the docs out of the build
  context, so a local `.env.local` can never reach the image.

**`Caddyfile`** (this image only serves files; HTTPS, HSTS and the `/api` route are DevOps's main Caddy,
which sends every path except `/api/*` to this container on port 8080):

| Path | Response | Why |
|---|---|---|
| `/assets/*` that exists | the file, `Cache-Control: public, max-age=31536000, immutable` | names carry a content hash, so they never change |
| `/assets/*` that doesn't exist | 404, not cached | an old tab asking for a deleted chunk must not get `index.html` |
| `/api/*` | 404 | a routing mistake shows up as an API error, not as HTML read as JSON |
| `/healthz` | 200 `OK` | Docker health check |
| anything else | the file if it exists, otherwise `index.html`; `Cache-Control: no-cache` | SPA fallback (`/review-queue` works on reload); a new deploy is picked up on the next page load |

Every response also gets `zstd`/`gzip` compression and these headers: `Content-Security-Policy`
(only our own scripts, fonts and API; `style-src 'unsafe-inline'` because antd writes its CSS into
`<style>` tags at run time), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
`Referrer-Policy: strict-origin-when-cross-origin`. `connect-src` stays `'self'`: the app never calls another
origin (the API is on the page's own origin, decided by its Host).

Check a Caddyfile change with `caddy fmt --diff Caddyfile` and `caddy validate --config Caddyfile`, and
a Dockerfile change with `hadolint Dockerfile`.

## 7. Checklist

- [ ] `npm run format:check && npm run lint && npm run typecheck && npm run test` pass; no new warnings
  you could fix.
- [ ] No `eslint-disable` without a reason; no config files changed in a feature PR.
- [ ] Spec changed → `generate:api` run and committed.

---
*Last verified against code: 2026-10-10, step 5.5 — every path, name, rule and ✅ example checked against the scaffold.*
