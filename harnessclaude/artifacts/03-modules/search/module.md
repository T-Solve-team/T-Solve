---
name: search
owner: Trần Hải Anh
repos: [default]
dependencies: [ticket-repository, ai, worker]
complexity: large
paths:
  - backend/src/main/java/vn/tsolve/search/**
  - backend/src/test/java/vn/tsolve/search/**
  - frontend/src/features/search/**
  - frontend/src/features/feedback/**
  - frontend/src/features/tickets/pages/TicketListPage*
  - frontend/src/features/tickets/components/TicketList*
  - frontend/src/features/tickets/hooks/useTicketList*
  - frontend/src/features/tickets/mocks/**
  - backend/src/main/resources/db/changelog/**
  - frontend/openapi/**
  - frontend/src/shared/api/generated/**
  - docs/bug-log.md
---

# Module: search

**Owner:** Trần Hải Anh

## Description
Intelligent Retrieval and Reuse & Feedback (UC-26–28): semantic search of Published tickets, the ticket list, and Helpful / Not helpful feedback; the retrieval interface shared with triage, the chatbot and the Jira App.

## Responsibilities
- Embeddings and semantic search over the Published tickets of the user's Departments (footnote ²).
- The ticket list (footnote ⁵).
- Feedback on Published tickets.
- The shared retrieval interface (KAN-38).
- Screens 6.1, 6.2, 8.1.

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| Retrieval | Service interface | Published tickets and Wiki documents a user may see |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| ticket-repository | Tickets | What is searched |
| wiki | Wiki documents | Documents for retrieval |

## Target Repos
- default: backend/ (`search`), frontend/ (`search`, `feedback`).

## Related Artifacts
- Spec: `artifacts/03-modules/search/spec.md` (written when the module is planned)
- Map: `artifacts/03-modules/module-map.md`
