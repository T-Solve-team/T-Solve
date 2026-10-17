---
name: create-artifact
description: Write new artifact files with correct templates and registry updates
type: utility
---

# Create Artifact

Write a new artifact file using the correct template and register it.

## Process

1. **Determine the path** from the artifact type:

   | Artifact | Path | Template |
   |----------|------|----------|
   | Research | `artifacts/01-research/RESEARCH-{NNN}-{slug}.md` | `research.md` |
   | Concept brief | `artifacts/00-baseline/concept-brief.md` | `concept-brief.md` |
   | Context brief | `artifacts/02-discovery/context-brief.md` | `context-brief.md` |
   | Architecture | `artifacts/03-modules/architecture.md` | `architecture.md` |
   | Module map | `artifacts/03-modules/module-map.md` | `module-map.md` |
   | Module definition | `artifacts/03-modules/{module}/module.md` | `module.md` |
   | Spec | `artifacts/03-modules/{module}/spec.md` | `spec.md` |
   | Design (master) | `artifacts/04-plans/{module}/design.md` | `design.md` |
   | Tasks | active plan path: `artifacts/04-plans/{module}/tasks.md`, or `…/iteration-{N}/tasks.md` with iterations | `tasks.md` |
   | Iteration-scoped design | `artifacts/04-plans/{module}/iteration-{N}/design.md` (iterations only) | `design.md` |
   | Iteration file | `artifacts/07-iterations/iteration-{N}.md` (iterations only) | `iteration.md` |
   | Session log | `artifacts/05-implementation/{module}/task-{N}-{slug}.md` | `session-log.md` |
   | Review | `artifacts/06-evaluation/{module}/task-{N}-{slug}-review.md` | `review.md` |
   | Test report | `artifacts/06-evaluation/{module}/task-{N}-{slug}-test-report.md` | run-tests skill |

   Specs live only in `03-modules/` — there are no per-iteration specs.

2. **Research id** — `NNN` is one more than the highest `RESEARCH-NNN` already in `artifacts/01-research/` (scan the files, not the registry), zero-padded to 3 digits. When several spikes run in parallel, number them all before starting.

3. **Load the template** from `templates/`, fill it, and write the file (create parent folders as needed).

4. **Register it** — append a row to `artifacts/_registry/artifact-index.md`:
   ```
   | {ID or —} | {type} | {module or —} | {status} | artifacts/{path} |
   ```

5. **Link it** — fill the template's references (e.g. `Spec: artifacts/03-modules/{module}/spec.md` in a design or review). Backlinks are not required.

## Rules

- Always use the template — don't write freeform artifacts
- Always register the file — `sdd.ts audit` warns about artifacts missing from the index
- Session logs and reviews follow the `task-{N}-{slug}` naming, where `{slug}` is the feature key in progress.json
