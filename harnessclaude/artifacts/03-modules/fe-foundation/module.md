---
name: fe-foundation
owner: Trần Trung Hiếu
repos: [default]
dependencies: []
complexity: large
paths:
  - frontend/src/app/**
  - frontend/src/shared/**
  - frontend/src/mocks/**
  - frontend/src/test/**
  - frontend/src/features/*/routes.ts
  - frontend/src/features/*/index.ts
  - frontend/src/features/*/mocks/handlers.ts
  - frontend/src/features/triage/**
  - frontend/src/features/wiki/**
  - frontend/src/features/chatbot/**
  - frontend/src/features/workspace/pages/WorkspaceSetupPage.tsx
  - frontend/src/features/workspace/pages/WorkspaceSettingsPage.tsx
  - frontend/src/features/workspace/pages/DepartmentDetailPage.tsx
  - frontend/src/features/review/**
  - frontend/scripts/**
  - frontend/*
  - frontend/openapi/**
  - frontend/guidelines/**
  - docs/bug-log.md
---

# Module: fe-foundation

**Owner:** Trần Trung Hiếu

## Description
The shared base of the web app that every screen builds on, brought in line with Report3 and decisions D1–D6 (KAN-57): roles and the multi-role session, guards, landing page, menus, the 35-screen route map with placeholders for the new features, the Report3 MSG catalog, kit status tags, mock users and test helpers, and the frontend guidelines. The Review Queue's behaviour and Ticket Detail are not part of it (`ticket-review`, `ticket-repository`); until the `ticket-review` module is on `main`, this module also owns `features/review/` so it can keep the review feature on `main` green (renumbered codes, roles, one-Department mock data).

## Responsibilities
- Five roles, several per user; `useSession` with all roles, Department, Departments, Projects, managed Spaces.
- `RequireRole` (any of the route's roles), landing page and Workspace Setup redirect, union menus (D4).
- Every route, path and menu entry; placeholders for Triage, Wiki and Chatbot until those modules are planned (each one's planning removes its folder from these paths).
- The MSG catalog from Report3 System_Messages; `{tracker_name}`; Simple / Complex status tags.
- Fixture users with role combinations, the dev user switcher, `renderApp` / `renderWithProviders` (`{ role }` kept).
- `frontend/AGENTS.md`, the guidelines and the root config files (guideline 11).

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| Web app base | Routes, guards, menus, kit, `msg()`, `showMessage()` | What every screen uses |
| Test helpers | `renderApp`, `renderWithProviders`, fixture users | Page tests of every feature |
| API contract | `openapi/tsolve-api.yaml` + generated client | Owner of the contract file and its regeneration |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| be-foundation | `/me` | The session shape |

## Target Repos
- default: frontend/ (`src/app`, `src/shared`, `src/mocks`, `src/test`, the features' registration files, guidelines).

## Related Artifacts
- Spec: `artifacts/03-modules/fe-foundation/spec.md` (written when the module is planned)
- Map: `artifacts/03-modules/module-map.md`
