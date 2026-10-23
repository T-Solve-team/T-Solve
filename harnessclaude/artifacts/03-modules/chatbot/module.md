---
name: chatbot
owner: Trần Trung Hiếu
repos: [default]
dependencies: [search, wiki]
complexity: medium
paths:
  - backend/src/main/java/vn/tsolve/chatbot/**
  - backend/src/test/java/vn/tsolve/chatbot/**
  - backend/src/main/resources/db/changelog/**
  - frontend/openapi/**
  - frontend/src/shared/api/generated/**
  - docs/bug-log.md
---

# Module: chatbot

**Owner:** Trần Trung Hiếu

## Description
Knowledge Chatbot (UC-51–53, KAN-48): answers with citations from the Solutions of all Published tickets (without ticket details) and the Wiki documents the user may see; history for the session only; ratings (GBR-36). The backend package `chatbot` is proposed. The web folder `frontend/src/features/chatbot/` moves from `fe-foundation` into these paths when the module is planned.

## Responsibilities
- Questions and answers with citations; MSG109 when no source is found.
- Session conversations, cleared at logout (UC-02).
- Helpful / Not helpful on an answer; knowledge gaps.
- Screen 12.1.

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| Chatbot | REST | Questions, answers, ratings |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| search | Retrieval | Sources for answers |
| wiki | Wiki documents | Sources for answers |

## Target Repos
- default: backend/ (`chatbot`, proposed), frontend/ (`chatbot`).

## Related Artifacts
- Spec: `artifacts/03-modules/chatbot/spec.md` (written when the module is planned)
- Map: `artifacts/03-modules/module-map.md`
