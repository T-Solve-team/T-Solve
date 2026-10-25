---
name: jira-app
owner: Đặng Tấn Bình
repos: [default]
dependencies: [project-integration, ticket-repository, search, triage, ai]
complexity: large
paths:
  - jira-app/**
  - frontend/openapi/**
  - frontend/src/shared/api/generated/**
  - docs/bug-log.md
---

# Module: jira-app

**Owner:** Đặng Tấn Bình

## Description
The T-Solve Jira App on Atlassian Forge: the Solution Form when an issue moves to a Done status (UC-19, with AI drafting and rewriting), the Issue Panel with suggested Published tickets, and the triage comment on new issues.

## Responsibilities
- Solution Form, drafts banner and Issue Panel inside Jira.
- Calls to the T-Solve API (Forge Remote).

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| Jira App | Forge modules | Screens inside Jira |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| ticket-repository | Submission | Sends the Solution |
| search | Retrieval | Suggested tickets |
| triage | Triage | The comment on new issues |
| project-integration | Projects | Which tracker projects are linked |

## Target Repos
- default: jira-app/.

## Related Artifacts
- Spec: `artifacts/03-modules/jira-app/spec.md` (written when the module is planned)
- Map: `artifacts/03-modules/module-map.md`
