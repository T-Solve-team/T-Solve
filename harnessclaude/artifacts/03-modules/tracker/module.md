---
name: tracker
repos: []
dependencies: []
complexity: medium
paths: [backend/src/main/java/vn/tsolve/tracker/**, backend/src/test/java/vn/tsolve/tracker/**, backend/src/main/java/vn/tsolve/common/security/**, backend/src/test/java/vn/tsolve/common/security/**, backend/src/main/java/vn/tsolve/common/config/TrackerProperties.java, backend/src/main/resources/db/changelog/**, backend/src/main/resources/application.yml, backend/pom.xml, frontend/openapi/tsolve-api.yaml, .env.example]
---

# Module: tracker

## Description
The server side of the Jira integration (diagram: Ticket Tracker Connector, package `tracker`). It exposes the
Jira App API that the T-Solve Jira App calls through Forge Remote, verifies each call's Forge Invocation Token,
reads issues and comments from Jira with the Forge app system token through `JiraClient`, ignores tickets of
projects not linked to T-Solve, and hands the ticket to the business modules (triage, search, ticket) whose
results it returns to the Jira App.

## Responsibilities
- Jira App API endpoints (`/api/v1/jira-app/**`, exact paths settled in the spec and added to
  `frontend/openapi/tsolve-api.yaml`): issue created event, issue moved-to-Done event, panel data,
  search again, Solution Form submit / "No Solution needed" pass-through
- FIT verification for those endpoints: signature (Atlassian JWKS, cached), issuer, audience = app id,
  expiry, cloudId → registered Workspace; 401/403 otherwise; tokens never logged
- `TicketTrackerClientFactory` + `TicketTrackerClient` interface + `JiraClient` (issue fields, paginated
  comments, assignee) using the app system token
- Project filter: drop events whose Jira project key is not linked to a T-Solve Project
- Idempotent handling of repeated events (retries, Done again)
- Logging rule: ids, keys, status codes, error types only

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| Jira App API | REST (Forge Remote, FIT) | Events (created, Done), panel data, search again, Solution Form actions |
| `TicketTrackerClientFactory.getClient(trackerType)` | Java service | Read a tracker issue (+comments) for other packages, e.g. import |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| `project` (other owner, not yet decomposed) | Project lookup by cloudId + jiraProjectKey | Is this ticket's project in T-Solve? |
| `triage` (other owner) | Triage a new ticket → decision | Decision returned to the Jira App for an internal comment |
| `search` (other owner) | Compute / store / read suggestions for an issue | Panel suggestions, search again |
| `ticket` (other owner) | Create/update AI draft from Done data; submit Solution Form; mark "No Solution needed" | Done flow → Review Queue |
| `identity` (other owner) | Jira accountId → T-Solve user | Author of a submitted Solution |
| Security layer (`identity` / common) | Security filter chain | Where the FIT filter for `/api/v1/jira-app/**` plugs in |
| Jira Cloud REST API (external) | `/rest/api/3/issue/{id}`, `/comment` | Ticket data |

Packages owned by others are called through their service interfaces; stubs are used until they exist.

## Target Repos
- monorepo `backend/`: package `vn.tsolve.tracker` and its tests; Liquibase changesets it needs
- `frontend/openapi/tsolve-api.yaml`: the Jira App API paths (shared contract file)

## Related Artifacts
- Spec: `artifacts/03-modules/tracker/spec.md`
- Brief: `artifacts/02-discovery/context-brief.md`
