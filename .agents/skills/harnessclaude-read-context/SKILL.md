---
name: harnessclaude-read-context
description: Session orientation — read manifest, progress, artifacts, and git state
type: utility
---

# Read Context

Orient yourself at the start of every session or before taking any action.

## Process

1. **Read manifest.yaml**
   - Project name, mode, repos, sources
   - Validate repo paths exist (warn if not) — each `repos` entry is a path relative to `harnessclaude/` or an object with `path:`; with no repos, the project itself (`..`) is the repo
   - No repos, or a new team member → suggest the onboard skill

2. **Read progress.json**
   - List all modules and their current status
   - List all features and their status within active modules

3. **Read artifact-index.md** (`artifacts/_registry/`) — what artifacts exist.

4. **Scan `artifacts/00-baseline/`**
   - List any files found (PDFs, docs, markdown, etc.)
   - If files exist and mode is `undecided`:
     → Suggest switching to `blueprint` mode ("You have baseline docs — blueprint mode is recommended")
   - If files exist and mode is `explore`:
     → Suggest switching to `blueprint` mode ("Found docs in baseline — did you mean to use blueprint mode?")
   - If files exist and discovery hasn't started yet:
     → Suggest starting the discover phase to process them
   - If files are not listed in artifact-index.md or in manifest.yaml `sources`:
     → Flag them as new and ask if the user wants to re-run or update discovery

5. **Audit** — run `bun harnessclaude/hooks/sdd.ts audit` and keep its failures for the summary.

6. **Git check** — `bun harnessclaude/hooks/sdd.ts repos`: each repo's folder, branch, and uncommitted files, plus the harness repo itself in the workspace layout (skill.md "Repos and Git"). A missing repo → suggest `harnessclaude init`, which clones it. For a module that is `implementing` or `evaluating`, a repo not on its `feat/{module}` branch is worth mentioning; commit drift (a recorded commit no longer on the branch, or the branch gone) comes from the audit in step 5 — after the user agrees, fix it with `sdd.ts commit {module} {repo}`.

7. **Produce orientation summary**

## Output Format

Present to the user:

```markdown
## Session Context
Project: {name} ({mode} mode)
Iteration: {N} ({status}) ← only if iterations enabled
Repos: {key: branch, clean | N changed files | missing} for each repo
Harness repo: {branch, clean | N changed files} ← workspace layout only

## Baseline Documents
{list of files in 00-baseline/, or "None"}
{if new docs found: "⚠ New documents detected since last session"}

## Module Status
- {module}: {status} · spec {approved | not approved | changed since approval} · {passing}/{total} features passing
  - from `planned`: spec `03-modules/{module}/spec.md` ✓/✗ · design `04-plans/{module}/design.md` ✓/✗ · tasks (active plan path) ✓/✗
- ...

## Audit
{"OK", or the failures from `sdd.ts audit`}

## Suggested Next Action
{what to do next based on current state}

## Active Blockers
{any blocked features or modules}

## Recent Events
{last 5 entries from events/events.jsonl}
```

The active plan path is `04-plans/{module}/` — or `04-plans/{module}/iteration-{N}/` when iterations are enabled (see skill.md Plan Paths). Specs are never per-iteration.

## Rules

- Always run this before any other action
- If progress.json doesn't exist, suggest running discover phase
- If manifest.yaml is missing repos, warn but don't block
