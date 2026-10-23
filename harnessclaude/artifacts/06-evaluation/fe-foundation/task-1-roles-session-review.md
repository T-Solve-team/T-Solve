---
module: fe-foundation
feature: roles-session
task: 1
verdict: pass
attempt: 1
date: 2026-10-22
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: fe-foundation — Task 1: Five roles, multi-role session, logout, mock users and test helpers

## Summary
I reviewed the code in `default: 663f3e6..d9bfc7b` (`frontend/`, harness folder excluded) against STORY-001, STORY-008,
STORY-009 and the non-functional requirements. All 16 acceptance criteria are implemented, and each has a test that
names it and checks it. The full frontend verify passes: format, lint, typecheck, 87/87 tests, build. I found no
CRITICAL code issue and no Critical/High security issue. The `/me` contract in `openapi/tsolve-api.yaml` matches
the spec's Interface Contracts exactly. Logout is now fail-safe: the session is set to `null` locally with no
refetch, `useSignOut` never throws, and `tsolve.session.*` keys are removed from both storages. The open findings
are one MEDIUM test gap (the mutation half of AC-008.1 and the two-user check from the design's risk table), one
LOW rule breach in a test, and informational notes. None of them blocks the verdict.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Stage 1 below: every criterion PASS. `toSession` (`shared/auth/session.ts:10-13`) keeps only known roles and adds EMPLOYEE. `hasRole`/`hasAnyRole` read all roles (`useSession.ts:41-45`). 401 → `null`, other errors → `SessionErrorPage`. `resetSession(…, { signedOut: true })` drops other queries, mutations and `tsolve.session.*` keys, then sets the session to `null` (`useSession.ts:92-106`). `useSignOut` catches the logout error (`useSession.ts:112-121`), so `UserMenu` always reaches `navigate(paths.login)`. The 8 fixture users match the design table. Dev user ids are validated with `Object.hasOwn`, and blocked storage falls back to `pm`. |
| Completeness | pass | 16/16 criteria covered, none MISSING. EXTRA, and temporary per the plan (Tasks 2–3): `mainRole()` with `LANDING_PATHS` sends EMPLOYEE/KNOWLEDGE_MANAGER to My Profile; empty EMPLOYEE/KNOWLEDGE_MANAGER menus; a multi-role user sees only the menu of their main role. `ALL_SIGNED_IN_ROLES = ROLES` is a stand-in. NFR 3 (no new dependency) and NFR 4 (`{ role }` keeps its meaning; the default is still PROJECT_MANAGER) hold. |
| Correctness | pass | `format:check`, `lint`, `typecheck`, `build` exit 0. `vitest run`: 17 files, 87 passed, 0 failed, 0 skipped. The task's files re-run with the verbose reporter: 41/41, so no flakiness. Production bundle: no dev switcher, `tsolve.dev.user`, MSW or faker strings in `dist/`. `npm audit`: 0 vulnerabilities. |
| Integration | pass | `CurrentUser` / `Role` / `SpaceRef` in the yaml and the regenerated model match the spec contract (`department` is `anyOf [$ref, null]`, all 7 fields required). `@/shared/auth` exports `ROLES`, `ROLE_LABELS`, `useSession` (`session`, `roles`, `hasRole`, `hasAnyRole`, `isProjectManagerOf`, `isMemberOf`), `useSignOut` and `resetSession`. `landingPath(roles)` belongs to Task 2. `@/test/render` takes `{ role } \| { user }` as a union. Global 401 now signs out locally (`global-errors.ts:26`), and its MSG07 guard still checks the cached session first. Review feature: role values renamed; its test moved to the one-Department PM (IT). |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-001.1 five roles + labels | PASS | `roles.ts:7-25` (`satisfies readonly ApiRole[]`); `session.test.ts` AC-001.1 |
| 2 | AC-001.2 session exposes `/me` fields as given | PASS | `session.ts:12` spreads the response; `useSession.test.tsx` AC-001.2 compares with `pm-km` |
| 3 | AC-001.3 EMPLOYEE always present | PASS | `session.ts:12`; two AC-001.3 tests (added, not duplicated) |
| 4 | AC-001.4 role / any-role questions | PASS | `useSession.ts:41-45`; AC-001.4 with `admin-tr` (true and false cases) |
| 5 | AC-001.5 401 → signed out | PASS | `useSession.ts:19-27`; AC-001.5 |
| 6 | AC-001.6 other failure → session error page + Try again | PASS | `guards.tsx:13`; AC-001.6 (MSG06, Try again, no h1) |
| 7 | AC-001.7 unknown role left out | PASS | `session.ts:11`; AC-001.7 |
| 8 | AC-008.1 logout API, drop queries + mutations, remove `tsolve.session.*` | PASS (test partial, Issue 1) | `useSession.ts:72-104`; the test checks the API call, the query cache and both storages, but not the mutation cache |
| 9 | AC-008.2 go to Login | PASS | `UserMenu.tsx:15-18`; AC-008.2 |
| 10 | AC-008.3 logout fails → still clear and go to Login | PASS | `useSession.ts:116-120`; AC-008.3 with a 500 override, so the dev user stays signed in on the "server" |
| 11 | AC-009.1 fixture users, one Department per PM | PASS | `mocks/session.ts:29-92`; three AC-009.1 tests |
| 12 | AC-009.2 switcher → `/me` answers that user; Signed out → 401 | PASS | `DevUserSwitcher.tsx`; `mocks/session.test.ts` and `DevUserSwitcher.test.tsx` AC-009.2 |
| 13 | AC-009.3 `{ role }` → single-role user; `{ role: null }` → signed out | PASS | `render.tsx:16-24`; AC-009.3 ×2 |
| 14 | AC-009.4 `{ user }` → that fixture user | PASS | AC-009.4 |
| 15 | AC-009.5 unknown or blocked storage → PM | PASS | `dev-user.ts:37-46`; AC-009.5 ×2 |
| 16 | AC-009.6 no switcher, no mock server when mock mode is off | PASS | `main.tsx:15`, `RootLayout.tsx:8-11`; `mock-mode.test.tsx` covers `VITE_API_MOCKING=false`; I checked the production build by grepping `dist/` |

### Security review — fe-foundation (roles-session) — 2026-10-22

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 0 |
| Dependencies | 0 | 0 | 0 | 0 |
| Code | 0 | 0 | 0 | 0 |
| STRIDE | 0 | 0 | 0 | 0 |

- Secrets and dangerous patterns: grep of every changed `.ts/.tsx/.yaml`. The only hits are
  `document.body.innerHTML = '<div id="root"></div>'` with a static string in `mock-mode.test.tsx:12,21`, a false
  positive. Tracked env files: only `.env.example`; `.gitignore` covers `.env*`.
- Spoofing / EoP: roles in the client are UI hints only; the backend re-checks (architecture, guideline 08).
  `RequireRole` denies by default (`hasAnyRole` of an empty role list is false). Unknown server roles are
  dropped, never trusted.
- Information disclosure (edge cases 11, 18): logout clears the query and mutation caches and the
  `tsolve.session.*` keys in session and local storage. It does not refetch `/me`, so a still-valid server session
  cannot sign the user back in. The global 401 does the same.
- Dev-only paths: the switcher and the mocks sit behind the literal `import.meta.env.DEV && VITE_API_MOCKING ===
  'true'` and are absent from `dist/`.

## Traceability
```
  ✓ AC-001.1  frontend/src/shared/auth/session.test.ts
  ✓ AC-001.2  frontend/src/shared/auth/useSession.test.tsx
  ✓ AC-001.3  frontend/src/shared/auth/session.test.ts
  ✓ AC-001.4  frontend/src/shared/auth/useSession.test.tsx
  ✓ AC-001.5  frontend/src/shared/auth/useSession.test.tsx
  ✓ AC-001.6  frontend/src/shared/auth/useSession.test.tsx
  ✓ AC-001.7  frontend/src/shared/auth/session.test.ts
  ✓ AC-008.1  frontend/src/shared/auth/sign-out.test.tsx
  ✓ AC-008.2  frontend/src/shared/auth/sign-out.test.tsx
  ✓ AC-008.3  frontend/src/shared/auth/sign-out.test.tsx
  ✓ AC-009.1  frontend/src/mocks/session.test.ts
  ✓ AC-009.2  frontend/src/mocks/session.test.ts, frontend/src/app/dev/DevUserSwitcher.test.tsx
  ✓ AC-009.3  frontend/src/test/render.test.tsx
  ✓ AC-009.4  frontend/src/test/render.test.tsx
  ✓ AC-009.5  frontend/src/mocks/session.test.ts
  ✓ AC-009.6  frontend/src/app/dev/mock-mode.test.tsx
trace fe-foundation (STORY-001, STORY-008, STORY-009): 16/16 acceptance criteria have a test
```

## Issues

No CRITICAL issues. These do not block the verdict:

1. **MEDIUM** — `frontend/src/shared/auth/sign-out.test.tsx:29-49`: the AC-008.1 test never checks the
   "…and mutation" part. No mutation is cached before logout, and `getMutationCache()` is not asserted. The
   design's Critical risk "Logout leaves data of the previous user — … test with two users" (edge case 11) has no
   test either.
   Fix: run or build a mutation before logout and assert `queryClient.getMutationCache().getAll()` is empty
   afterwards. Add one test where user A logs out, user B is signed in (`setDevUser('km')` and reset), and B sees
   no data or `tsolve.session.*` entries of A.
2. **LOW** — `frontend/src/shared/auth/session.test.ts:46`: `withUnknownRole as unknown as CurrentUser` breaks
   guideline 04 R4 ("Never `as unknown as X`"). The lint rule does not catch it.
   Fix: make `toSession` accept what the server can really send, e.g.
   `(user: Omit<CurrentUser, 'roles'> & { roles: readonly string[] })`. The test then needs no cast, and the type
   matches the runtime case AC-001.7 handles.
3. **Informational** — `frontend/src/shared/auth/sign-out.test.tsx:69-71`: the "no refetch" check is weak. The
   Login page never shows the user button, so the assertion passes even if `/me` is refetched later.
   Fix: count `/me` requests with a `server.use` spy and assert none after logout, or assert the path is still
   `/login` after the next tick.
4. **Informational** — `frontend/src/app/dev/mock-mode.test.tsx:177`: the test covers only
   `VITE_API_MOCKING=false`. The "production build" half of AC-009.6 rests on the literal `import.meta.env.DEV`
   check. I verified it by grepping `dist/`, but no automated check exists.
   Fix (optional): a build-output check in CI, or stub `DEV=false` as well.
5. **Informational (EXTRA, temporary)** — `frontend/src/shared/auth/landing.ts:11,13,25`,
   `frontend/src/app/layout/SideMenu.tsx:11`, `frontend/src/app/layout/menu-config.ts:64,77`: Employee-only and
   KM users land on My Profile with an empty menu. A multi-role user (e.g. `admin-tr`) sees only the Admin menu, so
   Search is reachable only by URL. The plan says Tasks 2 and 3 replace this (AC-003.1, AC-004.x).
   Fix: none now. Task 2's review must check that `mainRole` and the empty menus are gone.
6. **Informational** — `frontend/guidelines/01-project-structure.md:23,34` and
   `frontend/guidelines/10-testing.md:174` still name `DevRoleSwitcher` / `dev-role.ts`.
   Fix: in Task 5 (STORY-010, guidelines).

## Assumptions made
- Coverage: the project has no coverage script and the spec sets no threshold, so I measured no coverage. Is that
  acceptable? (Open question for the user.)
- I graded Issue 1 as MEDIUM and non-blocking: AC-008.1 has a test that checks most of the clause, and the
  unasserted part is a one-line `getMutationCache().clear()` that I read in the code. A stricter reading ("a test
  that names an AC but does not check all of it is MISSING") would make this a fail. That choice is for the
  user.
- I judged the temporary landing and menu behaviour (Issue 5) against the task's scope ("no behaviour change for
  a single-role user beyond Report3's roles"), not against AC-003.1 / AC-004.x, which belong to Task 2.
- I did not run `npm run generate:api` because it writes into the working tree. I compared the generated files with
  the yaml change by hand. CI's no-diff check is the authority.
- I treated the review feature's mock data still holding HRHELP tickets for the IT-only PM as out of scope. Per the
  spec, scoping the queue to the user's Projects is `ticket-review`'s work.

## Test Report
See `harnessclaude/artifacts/06-evaluation/fe-foundation/task-1-roles-session-test-report.md`.
- `npm run format:check` → `All matched files use Prettier code style!` (exit 0)
- `npm run lint` → exit 0, no findings
- `npm run typecheck` → exit 0
- `npm run test` → `Test Files 17 passed (17) · Tests 87 passed (87)` in 50.6s
- `npm run build` → `✓ built in 767ms` (exit 0); `dist/` holds no dev switcher or mock code
- `bun harnessclaude/hooks/sdd.ts trace fe-foundation STORY-001 STORY-008 STORY-009` → 16/16
- Coverage: not available (no script)
