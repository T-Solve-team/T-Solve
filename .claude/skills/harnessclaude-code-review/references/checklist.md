# Review Checklist

Two passes: **CRITICAL** findings fail the review, **Informational** findings are reported but do not block. Cite `file:line` and a one-line fix for each. Skip anything that is fine.

## Base — always apply

### CRITICAL

**Injection & data safety**
- String interpolation in SQL/NoSQL queries (use parameterized queries, even with type casts)
- Unsanitized user input stored or rendered as HTML
- Shell commands built by string concatenation (use argument arrays)
- User input in file paths (path traversal)

**Concurrency**
- Read-check-write without an atomic operation or lock
- Find-or-create without a unique constraint
- Status transitions without `WHERE old_status` in the update
- Shared mutable state without synchronization

**Security boundaries**
- New endpoint or route without authentication
- Authenticated but not authorized (user can read/modify another user's data — IDOR)
- Admin-only operation reachable by regular users
- Secrets in logs, error responses, or client code
- LLM/AI output written to a database or query without validation
- Token comparison with `==` instead of constant-time compare

### Informational

- A branch sets one side effect but forgets the paired one; logs claim an action that was skipped
- Magic numbers repeated across files; error strings reused as query filters
- Dead variables, unused imports, comments describing old behavior
- Missing negative-path tests; asserts on status but not on side effects
- Type changes across boundaries (string vs number) without normalization
- O(n·m) lookups in loops, unbounded queries, missing pagination, N+1 queries

## API overlay — when the code exposes REST / GraphQL / gRPC

**CRITICAL**
- No rate limiting on login, registration, password reset
- Tokens or keys in URL query parameters
- Request body accepted without schema validation; whole body spread into a model (mass assignment)
- Uploads without size/type limits; array inputs without length limits
- Password hashes, tokens, internal IDs, or stack traces in responses

**Informational**
- Inconsistent error shape or naming across endpoints
- Breaking response changes without a version bump or deprecation
- Swallowed exceptions; new endpoints without logging

## Web overlay — when the code renders UI

**CRITICAL**
- `innerHTML` / `dangerouslySetInnerHTML` from non-static data; `href={userInput}` without protocol check
- State-changing requests without CSRF protection; auth cookies without `SameSite`
- Queries inside list-rendering loops (server-rendered views)

**Informational**
- Missing `key` on list items; whole-library imports that could be narrowed
- Images without dimensions or lazy loading below the fold
- Click handlers without keyboard support, images without `alt`, inputs without labels, color-only indicators
- Fixed pixel widths that break on mobile

## Do NOT flag

- Redundancy that helps readability
- "Add a comment explaining this threshold"
- "This assertion could be tighter" when it already covers the behavior
- Style and formatting (a linter's job)
- "Consider X instead of Y" when Y works
- Anything already handled elsewhere in the same diff
