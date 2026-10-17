---
name: harnessclaude-search-artifact
description: Query artifacts by module, status, type, or keyword
type: utility
---

# Search Artifact

Find and query artifacts in the workspace.

## Query Types

1. **By type**: "find all specs" → `artifacts/03-modules/*/spec.md`; designs → `artifacts/04-plans/*/design.md`; tasks → `artifacts/04-plans/*/**/tasks.md`
2. **By module**: "what artifacts exist for auth?" → `artifacts/03-modules/auth/`, `04-plans/auth/`, `05-implementation/auth/`, `06-evaluation/auth/`
3. **By status**: "what's blocked?" → read progress.json, filter by status
4. **By keyword**: "find mentions of authentication" → grep across `artifacts/`
5. **By registry**: "list all artifacts" → read `artifacts/_registry/artifact-index.md`

## Output

Present results as a table:

| ID | Type | Module | Status | Path |
|----|------|--------|--------|------|

## Rules

- Check artifact-index.md first for fast lookups
- Fall back to file scanning if the index seems stale (`sdd.ts audit` lists unregistered files)
- Return paths relative to the harnessclaude root
