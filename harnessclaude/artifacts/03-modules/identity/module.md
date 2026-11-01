---
name: identity
owner: Trần Trung Hiếu (UC-01–03) · Phạm Tùng Dương (UC-04–09, RBAC rules)
repos: [default]
dependencies: [platform, be-foundation, fe-foundation]
complexity: large
paths:
  - backend/src/main/java/vn/tsolve/identity/**
  - backend/src/test/java/vn/tsolve/identity/**
  - frontend/src/features/auth/**
  - frontend/src/features/my-profile/**
  - frontend/src/features/users/**
  - frontend/src/mocks/session.ts
  - backend/src/main/resources/db/changelog/**
  - frontend/openapi/**
  - frontend/src/shared/api/generated/**
  - docs/bug-log.md
  - frontend/src/app/dev/**
  - frontend/src/shared/auth/sign-in.ts
  - frontend/src/app/router/routes.tsx
---

# Module: identity

**Owner:** Trần Trung Hiếu (UC-01–03) · Phạm Tùng Dương (UC-04–09, RBAC rules)

## Description
Role-Based Access Control (UC-01–09, UC-59): sign in with the company account through the OIDC Provider and log out, my profile, linking the user's Jira account, the user list, adding users, account status, roles and Department, and handing over Projects and Spaces.

## Responsibilities
- OIDC sign-in (Google in the MVP) and logout; the session cookie.
- User accounts: add by email, Active / Deactivated, roles, the Department of a Project Manager, the Spaces of a Knowledge Manager.
- Hand Over Responsibilities (Projects and Spaces) before a role change or deactivation (D3).
- UC-59 Link ticket tracker account: tie the Account to its Jira account, used as a sign-in target by the Jira App (KAN-234, GBR-19).
- Screens 1.1.1, 1.2.1, 1.3.1–1.3.3.

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| Users | Service interface + REST | User accounts, roles, status |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| be-foundation | Roles, session, errors | Security and `/me` |
| workspace | Departments | The Department of a Project Manager |

## Target Repos
- default: backend/ (`identity`), frontend/ (`auth`, `my-profile`, `users`).

## Related Artifacts
- Spec: `artifacts/03-modules/identity/spec.md` (written when the module is planned)
- Map: `artifacts/03-modules/module-map.md`
- Stories: KAN-103…112, KAN-117, KAN-118, KAN-160, KAN-197, KAN-229…232, KAN-234…239
