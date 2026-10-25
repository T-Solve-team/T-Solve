---
name: platform-console
owner: Đặng Tấn Bình
repos: [default]
dependencies: [platform, identity, worker, fe-foundation]
complexity: medium
paths:
  - backend/src/main/java/vn/tsolve/console/**
  - backend/src/test/java/vn/tsolve/console/**
  - frontend/src/features/platform-console/**
  - backend/src/main/resources/db/changelog/**
  - frontend/openapi/**
  - frontend/src/shared/api/generated/**
  - docs/bug-log.md
---

# Module: platform-console

**Owner:** Đặng Tấn Bình

## Description
The Platform Console on `admin.tsolve.io.vn`, where Platform Operators manage Workspaces (UC-54…56, called P-01…03
in the Stories; D2, D21). A Platform Operator signs in through `login.` against an allow-list of Operator emails in
the deployment configuration; that session is valid only on `admin.` (D13). The Console holds no Workspace data: it
works on the Workspace registry of `platform` and never runs in a Workspace's context, except the user count of
UC-54 and the first Admin account of UC-56 (GBR-37). The backend package `console` and the web folder
`features/platform-console/` are *proposed*; the web app chooses the Console's route tree by host (`fe-foundation`).

## Responsibilities
- UC-54 View Workspaces: company name, Workspace site, status, number of users (KAN-113).
- UC-55 Create Workspace: company name, site name (RFC 1123 label, reserved names, unique; MSG125, MSG126, MSG139),
  Google domain (valid, unique on the platform; MSG127, MSG138), one or more Jira site URLs (MSG136, MSG137);
  MSG128 (KAN-114, GBR-24, D6, D7, D11, D20).
- UC-55 Suspend / Reactivate a Workspace (MSG129…132; D12, D29) (KAN-115).
- UC-56 Invite the first Admin: an email of the Workspace's Google domain (MSG133, MSG134), an Active Admin
  Account created inside the Workspace and an invitation email with the Workspace site link (GBR-27); refused once an
  Admin has signed in (MSG140, D14) (KAN-116).
- Platform Console access: only a Platform Operator (MSG135); no delete, no editing of Admins (D2).
- Platform Console screens.

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| Platform Console | REST on `admin.` | View, create, suspend, reactivate Workspaces; invite the first Admin |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| platform | Workspace registry | Create, list, suspend and reactivate Workspaces and their Jira sites |
| identity | Accounts | Create the first Admin Account inside the target Workspace; user count per Workspace |
| worker | Job queue, email | Send the invitation email |
| fe-foundation | Web app base | Routes by host, guards, kit, MSG catalog |

## Target Repos
- default: backend/ (`console`, proposed), frontend/ (`platform-console`, proposed).

## Related Artifacts
- Spec: `artifacts/03-modules/platform-console/spec.md` (written when the module is planned)
- Map: `artifacts/03-modules/module-map.md`
- Stories: KAN-113, KAN-114, KAN-115, KAN-116
