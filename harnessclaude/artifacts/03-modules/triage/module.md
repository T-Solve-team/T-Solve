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
AI Ticket Triage (UC-36–37): every new issue in a linked tracker project is classified Simple or Complex; a Simple issue gets a suggested solution posted as a comment, written from Published tickets and Wiki documents (GBR-35); Project Managers see the triage history. The backend package `triage` is proposed. The web folder `frontend/src/features/triage/` moves from `fe-foundation` into these paths when the module is planned.

## Responsibilities
- Classification and the suggested solution (PII-masked, AI optional).
- The reporter's answer to a suggestion; triage records.
- Screens 10.1, 10.2.

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| Triage | Service interface + REST | Triage results and history |

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
