# Local E2E smoke (KAN-186 / AC-015.8–AC-015.12)

Use Node 24 LTS, Java 21, Maven 3.9+, and Docker Desktop. Run only against the disposable local dev database.
The suite checks the stack, inserts missing fixtures, signs in once through real Keycloak and fake Google as
`alice@acme.test` (organization `acme`), and reuses `e2e/.auth/alice.json`. It does not start services itself.

## Start the stack

From the repo root, the setup/service commands below are copied from the root `README.md`:

```bash
cp .env.example .env
docker compose up -d db redis
docker compose up -d keycloak fake-google
```

Before starting services, fill in `.env` as described in the root README. Local Compose DB values are
`DB_HOST=localhost`, `DB_PORT=5432`, `DB_NAME=tsolve`, `DB_USER=tsolve`, `DB_PASSWORD=tsolve`.
Set Redis and Keycloak secrets, `SERVER_PORT=8080`, `TSOLVE_LOGIN_ORIGIN=http://localhost:8180`,
`TSOLVE_SITE_ORIGIN_TEMPLATE=http://{slug}.localhost:8080`, and the remaining required backend settings.
The package loads the root `.env` with Node's native loader; exported environment variables take precedence.
Do not put credentials in this package.

In a separate PowerShell terminal at the repo root, select the required dev profile and the current local stubs,
then use the backend command from the root README:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:SEARCH_DEV_SCOPE = 'all'
$env:TSOLVE_STUB_WORKSPACE_READY = 'true'
cd backend
mvn spring-boot:run
```

`dev` seeds Alice (Admin) and Bob. `SEARCH_DEV_SCOPE=all` is the existing local demo adapter needed for the ticket
to be visible. The Workspace module's company-information lookup is not implemented yet: the existing readiness
stub must report `true` or Alice is redirected to Workspace Setup. These settings are for this local smoke only.
The fixture uses the current single `DB_*` dev database; it is not a two-database isolation fixture. It adds missing
Ticket Resolver / Project Manager roles, the `E2E ACME` Department and `E2EACME` Project, and a Published ticket if
none exists. It preserves existing accounts, roles and data, and never upgrades an existing draft ticket.

In another terminal, use the frontend commands from the root README:

```bash
cd frontend
npm ci
npm run dev
```

Set `VITE_API_MOCKING=false` in your existing `frontend/.env.local` before starting Vite (see
`frontend/README.md`, “Run against the real backend”). The suite requires real API calls.

The preflight checks, in order: PostgreSQL TCP `5432`, Redis TCP `6379`, Keycloak `8180`, fake Google `8181`,
backend `http://localhost:8080/actuator/health`, and web app `http://localhost:5173`.
The first failure stops setup with `local stack not running: <service>` before fixture writes or browser launch.

## Install and run

From the repo root:

```bash
cd e2e
npm ci
npx playwright install chromium
npm run e2e
npm run e2e:report
```

If the Chromium download is blocked or slow, set `E2E_BROWSER_CHANNEL` to use an installed browser for both
sign-in setup and tests. With Edge installed, run `$env:E2E_BROWSER_CHANNEL='msedge'; npm run e2e` in PowerShell
from `e2e/`; no Playwright browser download is needed. Leave it unset to use bundled Chromium.

The HTML report is `e2e/playwright-report/index.html`; it never opens automatically. `test-results/`,
`playwright-report/`, `.auth/`, and `node_modules/` are ignored. Setup rewrites Alice's saved state each run.

`E2E_BASE_URL` defaults to `http://acme.localhost:8080`, matching the committed Keycloak callback URI.
Browser page visits use the same hostname on port `5173`, matching Vite. Starting sign-in on port `5173`
would use an unregistered callback URI. The fake Google chooser has an `alice@acme.test` link; the imported realm
uses `google-acme`, its `hd=acme.test` mapping, and organization `acme`. No Keycloak password is needed.
After callback, the suite verifies `/api/v1/me` and opens the Vite home page. Alice's Admin home redirects to
`/users`. Chromium must accept the application's Secure `__Host-` cookie on the local HTTP `.localhost` hosts;
this browser-specific behavior still needs confirmation on a running stack.

The BETA test first confirms Alice's ACME session works, then explicitly forwards that session cookie to
`http://beta.localhost:8080/api/v1/me`. A normal browser cookie jar would omit the host-only ACME cookie there,
which would not test backend session binding. This smoke checks 401 and absence of BETA identity/session data;
the separate backend isolation IT covers data isolation across two Workspace databases.

## Stack-free checks

From `e2e/`:

```bash
npx tsc --noEmit -p .
npx playwright test --list
npx playwright test --config stack-check.config.ts
```

The separate stack-check config runs the decision, real probe-loop/setup-abort, and site-request helper tests without
global setup or database. Probe tests use throwaway local servers and empty storage state and need no browser.
The site-request helper tests use a browser cookie jar: first run `npx playwright install chromium`, or set
`$env:E2E_BROWSER_CHANNEL='msedge'` in PowerShell to use installed Edge before running the stack-check command.
The main listing includes these tests plus the six smoke/session tests and does not execute setup.
From the repo root, the equivalent typecheck is `e2e/node_modules/.bin/tsc --noEmit -p e2e`.

No Docker/browser smoke run was performed in the authoring sandbox. The live result/report and the dated harness
evaluation record must be produced on a machine running the stack; listing and typechecking do not establish an
AC-015.9/AC-015.10 pass.
