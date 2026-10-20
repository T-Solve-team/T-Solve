# Module Map: ticket review request

This map is scoped to the user's current request. It does not decompose the entire T-Solve SRS.

| Module | Responsibility | Dependencies | Order |
|--------|----------------|--------------|-------|
| ticket-review | Queue, three decisions, history/audit, notification handoff and frontend | Auth/Project and email interfaces supplied by future integrations | 1 |

## Dependency graph
The local module graph has one vertex, ticket-review, and no edges; it is acyclic. Its external ports consume trusted auth/Project context and expose persisted notifications. No dependency modules are implemented by this request.

## Cross-module interface contracts
- Auth/Project integration supplies authenticated user identity and current management/access rights; missing integration denies access.
- Submission integration registers ticket, Solution, writer and review dates for review; no public test-data creation endpoint.
- Email integration consumes persisted notification records and handles actual SMTP delivery and retries under GBR-27.
- Search/triage/chatbot integrations consume only Published review tickets; their implementation is excluded.

## Related artifacts
- `artifacts/02-discovery/context-brief.md`
- `artifacts/03-modules/ticket-review/module.md`
- `artifacts/03-modules/ticket-review/spec.md`
