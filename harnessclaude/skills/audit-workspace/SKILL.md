---
name: audit-workspace
description: Check the whole HarnessClaude workspace — structure, statuses, approvals, tasks, naming — with sdd.ts audit, then fix what it reports
type: utility
---

# Audit Workspace

## When

- The user says "audit", "check the workspace", "validate structure"
- Before a module changes phase, when you are unsure the workspace is consistent
- Before a demo or the end of an iteration

## Run

```bash
bun harnessclaude/hooks/sdd.ts audit
```

It checks, by code:

| Area | Fails when | Warns when |
|------|-----------|------------|
| Core files | manifest.yaml lacks project/mode/language or has an unknown mode; progress.json or approvals.json is not valid JSON; an `artifacts/` folder is missing | — |
| Modules | a status or feature status is not in the state machine; a module lacks the artifacts its status requires (module.md; from `planned`: spec.md, design.md, tasks.md at the active plan path); its spec fails `check`; it is planned/implementing/evaluating with an unapproved or changed spec; tasks name a story the spec lacks; an implementing+ module has no features | a story has no task; a passing feature has no review with `verdict: pass`; a completed module's spec changed |
| Iterations | enabled but no numeric `currentIteration` or no `07-iterations/iteration-{N}.md` | — |
| Naming | — | session logs not `task-{N}-{slug}.md`; evaluation files not `task-{N}-{slug}-review.md` / `-test-report.md`; a review without `verdict: pass \| fail` |
| Registry | — | an artifact missing from `_registry/artifact-index.md`, or an entry whose file is gone |

Exit 1 means at least one failure.

## Then

1. Fix every failure (✗) before any phase transition. Use the message — it names the file or command.
2. Fix warnings (!) when they point at real gaps; registry warnings are fixed by adding the rows.
3. Judgment checks the audit cannot make — content of the context brief, design, and tasks — are the validate-artifact skill's.
4. Re-run until it prints `audit: OK`, and show the user the final output.
