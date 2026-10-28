# Task 9: Route tree chosen by host (Platform Console host)

**Module:** fe-foundation
**Assignee:** Trần Trung Hiếu
**Status:** in progress (PR not opened yet)
**Iteration:** 1

---

## Session 2026-10-28

### Working on: host-routes (KAN-207, branch `feature/KAN-207-fe-foundation-host-routes`)
- Stories STORY-013, STORY-010; AC-013.1–AC-013.4, AC-010.3; design edge cases 29–30. Change request KAN-198.
- `isPlatformConsoleHost(hostname)` in `app/router/host.ts`: first label `admin`, lower-cased, one trailing dot removed.
- `buildRoutes(host = 'localhost', routes = featureRoutes)`: the Console host gets `RootLayout` >
  [`platformConsoleRoutes`, `*` → `NotFoundPage`] (no Workspace guard, no AppShell); every other host gets only the
  Workspace tree. `router.ts` passes `window.location.hostname`; `renderApp` passes its `host` option.
- `features/platform-console/`: `routes.ts` (`platformConsoleRoutes`, a plain `RouteObject[]`), `index.ts`,
  `mocks/handlers.ts` (empty, registered in `src/mocks/handlers.ts`), `pages/PlatformConsolePage.tsx` (placeholder h1
  "Platform Console"). `featureRoutes` stays Workspace-only (comment only).
- Guidelines: 01 (the `platform-console` folder), 08 (§3a route tree by host), 13 (FE-44). Only 13 is covered by a
  test (AC-010.3); 01 and 08 are not.
- The pure host tests are in `src/app/router/host.test.ts`, a file not listed in tasks.md.
- `host` of `buildRoutes` has the default `'localhost'` so the unchanged `DevUserSwitcher.test.tsx` (calls
  `buildRoutes()`) still compiles; production and `renderApp` always pass a host.
- No bug found.

### Verify (frontend, one after another)
- format:check: "All matched files use Prettier code style!"
- lint: exit 0
- typecheck: exit 0
- test: "1 failed | 732 passed (733)" (the failure is BUG-022, not this task's)
- build: exit 0
- generate:api: exit 0, no diff in `src/shared/api/generated`
- trace: "8/8 acceptance criteria have a test"

### Hand-off (2026-10-28)
- done: route tree chosen by host, `platform-console` registration and placeholder, guidelines 01 / 08 / 13
- open: Bình (`platform-console`) to confirm the name and type of `platformConsoleRoutes` on the PR
- next: review, then the commits and the PR for KAN-207
- branch: `feature/KAN-207-fe-foundation-host-routes`
- PR: not opened yet
