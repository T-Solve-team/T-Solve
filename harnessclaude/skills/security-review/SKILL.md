---
name: security-review
description: STRIDE + OWASP review of a module — secrets, dependency audit, dangerous code patterns
type: on-demand
phases: [evaluate]
agent: reviewer
---

# Security Review

Threat-focused review of a module's code. Audit only: report findings with fixes; never change code during evaluation.

## When

- The evaluate skill runs it for features in modules that handle authentication, authorization, payments, personal data, file uploads, or user-supplied URLs/HTML.
- Before a module is marked `completed` if it exposes anything publicly.
- When the user asks for a security pass.

Skip for purely cosmetic changes or code with no user input or data handling.

## Process

1. **Scope** — the module's files in the target repo (from `tasks.md` and the commits in the session logs). Read them before analysis.
2. **Secrets** — grep with the patterns in `references/patterns.md`. Confirm each hit is real (not `YOUR_KEY`, not `process.env.X`).
3. **Tracked env files** — `git ls-files | grep -E "(^|/)\.env"` (anything not `.example`/`.sample` is a finding); check `.gitignore` covers `.env*`.
4. **Dependencies** — run the stack's audit if available: `npm audit --json`, `bun audit`, `pip-audit`, `govulncheck ./...`, `cargo audit`, `bundle audit`. If the tool is missing, say so; do not install tools without asking.
5. **Code patterns** — grep for the dangerous patterns in `references/patterns.md`, then read 5–10 lines around each hit and decide: real issue or false positive.
6. **STRIDE pass** — walk the checklist below against the module's design and code.
7. **Report** — append to the feature's review, or write `artifacts/06-evaluation/{module}/security-review.md` for a module-wide pass.

## STRIDE Checklist

| Threat | Check |
|--------|-------|
| **Spoofing** | Every non-public endpoint authenticated; passwords hashed with bcrypt/argon2; tokens expire and are validated server-side; cookies `Secure`, `HttpOnly`, `SameSite`; OAuth uses `state` |
| **Tampering** | Input validated (type, length, format); parameterized queries; CSRF tokens on state changes; uploads checked by content and size; no unsafe deserialization; no GET for mutations |
| **Repudiation** | Login, logout, failures, permission denials, and data changes logged with actor and time; no secrets or PII in logs |
| **Information disclosure** | No stack traces or internal paths in responses; minimal response fields; TLS for sensitive traffic; sensitive data encrypted at rest |
| **Denial of service** | Rate limits on auth and expensive endpoints; body size limits; pagination; timeouts on external calls; no catastrophic regexes |
| **Elevation of privilege** | Roles enforced server-side; user A cannot reach user B's resources (IDOR); admin routes separately guarded; least-privilege service accounts |

Also check OWASP A10 (SSRF): user-supplied URLs fetched server-side without an allowlist.

## Report Format

```markdown
## Security Review — {module} — {date}

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | | | | |
| Dependencies | | | | |
| Code | | | | |
| STRIDE | | | | |

### Findings
1. **[CRITICAL][CODE]** `src/api/user.ts:42` — SQL built with string concatenation
   - Fix: parameterized query
```

Critical or High findings make the feature's review verdict **fail**.

## Safety

- Never print a secret value — redact to the first 4 characters.
- Never use or test found credentials.
- A real committed credential must be rotated; tell the user.
