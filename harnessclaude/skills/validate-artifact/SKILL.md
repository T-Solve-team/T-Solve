---
name: validate-artifact
description: Check artifact completeness before phase transitions
type: utility
---

# Validate Artifact

Verify that an artifact has all required content before a phase transition. Structure that code can check — spec ids, open questions, tasks against stories, statuses, approvals, naming — is checked by `sdd.ts`; this skill adds the checks that need reading.

## Validation Rules by Type

### Context Brief (`02-discovery/context-brief.md`)
- [ ] Project overview and goals
- [ ] Key requirements (at least 3)
- [ ] Constraints and assumptions
- [ ] Identified risks

### Module Definition (`03-modules/{module}/module.md`)
- [ ] Name and description
- [ ] Responsibilities (at least 1)
- [ ] Interfaces section (can be "none" for leaf modules)
- [ ] Dependencies section
- [ ] Target repo(s)
- [ ] `paths` covering the module's code **and its tests** — required in practice once there is more than one module: acceptance-criterion ids restart in every module, so `sdd.ts trace` needs `paths` to know whose test a test file is

### Module Map (`03-modules/module-map.md`)
- [ ] All modules listed
- [ ] Dependency graph (no cycles — validate by attempting topological sort)
- [ ] Implementation order

### Spec (`03-modules/{module}/spec.md`)
Run `bun harnessclaude/hooks/sdd.ts check {module}` — its errors are FAIL items (stories without criteria, bad or duplicate AC ids, unresolved `[NEEDS CLARIFICATION]` / TBD / TODO, template placeholders, empty Changelog, tasks naming a story the spec lacks) and its warnings are Warnings. Then check by reading:
- [ ] Requirements (functional)
- [ ] Interface contracts
- [ ] Definition of done checklist

### Design (`04-plans/{module}/design.md`)
- [ ] Architecture overview
- [ ] Data model (or "N/A" with justification)
- [ ] Key technical decisions, each with chosen / alternatives / rationale
- [ ] Edge Cases & Risks table (edge-cases skill), unless the module is trivial

### Tasks (active plan path `tasks.md`)
`sdd.ts check {module}` checks that every story the tasks name exists and warns about stories without a task. Then:
- [ ] At least 1 task
- [ ] Each task has: title, `**Story:** STORY-NNN`, description, files
- [ ] Tasks are ordered; every Critical/High edge case has a test task

### Iteration-Scoped Design (`04-plans/{module}/iteration-{N}/design.md`, iterations only)
Filtered subsets of the master design. Relaxed checks:
- [ ] Architecture overview (can reference master for full detail)
- [ ] Key technical decisions relevant to this iteration's tasks
- [ ] Cross-reference to spec: `Spec: artifacts/03-modules/{module}/spec.md`
- [ ] Cross-reference to master design: `Master: artifacts/04-plans/{module}/design.md`

## Output

```
PASS — all required fields present
```
or
```
FAIL — missing:
  - {field 1}
  - {field 2}
Warnings:
  - {non-blocking issue}
```

## Rules

- FAIL blocks the phase transition — fix before proceeding
- Warnings are informational — note but don't block
- If a section says "TBD" or "TODO", that's a FAIL
- For the whole workspace at once, use the audit-workspace skill (`sdd.ts audit`)
