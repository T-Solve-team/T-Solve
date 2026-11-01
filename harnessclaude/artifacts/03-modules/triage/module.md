---
name: triage
owner: Trần Hải Anh
repos: [default]
dependencies: [search, wiki, ai, worker]
complexity: medium
paths:
  - backend/src/main/java/vn/tsolve/triage/**
  - backend/src/test/java/vn/tsolve/triage/**
  - backend/src/main/resources/db/changelog/**
  - frontend/openapi/**
  - frontend/src/shared/api/generated/**
  - docs/bug-log.md
---

# Module: triage

**Owner:** Trần Hải Anh

## Description
AI Solution Suggestion (UC-36–37, GBR-35; FE-10 renamed on 29/10). When an issue is created in a linked Jira project,
T-Solve retrieves similar Published tickets and Wiki documents; if a source passes the relevance threshold, the LLM
writes a suggested solution that the Jira App posts as a comment, and the reporter answers Yes / No; otherwise
nothing is posted. There is no Simple / Complex (or Sensitive) class, no routing, no label and no class correction;
every issue follows the team's normal Jira process. The models run on the team's own Ollama, so there is no PII
masking. Project Managers see the suggestion history. The module name stays `triage` so paths and history do not
change; the backend package `triage` is proposed. The web folder `frontend/src/features/triage/` moves from
`fe-foundation` into these paths when the module is planned.

## Responsibilities
- The shared search call and the relevance threshold that decides whether to suggest (KAN-47).
- The suggested solution, run as a `worker` job on the issue-created event of `project-integration` (KAN-126).
- The reporter's Yes / No answer and the suggestion records (the comment is posted by `jira-app`, KAN-131).
- UC-37 View suggestion history (KAN-149).
- Screens 10.1, 10.2.

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| Suggestion | Service interface + REST | Suggestions, answers and history |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| search | Retrieval | Sources for the suggestion |
| wiki | Wiki documents | Sources for the suggestion |

## Target Repos
- default: backend/ (`triage`, proposed), frontend/ (`triage`).

## Related Artifacts
- Spec: `artifacts/03-modules/triage/spec.md` (written when the module is planned)
- Map: `artifacts/03-modules/module-map.md`
- Stories: KAN-47, KAN-149 (KAN-126 in `project-integration`, KAN-131 in `jira-app`, KAN-244 in `fe-foundation`)
