---
name: scout
description: Map an existing codebase (inherit discovery) or trace the impact of a change (evaluate)
type: utility
phases: [discover, evaluate]
---

# Scout

Find the files and relationships that matter without reading the whole repo. Two modes.

## Mode 1 — Codebase Map (discover, `inherit` mode)

For each repo in `manifest.yaml`:

1. **Size it** — count files per top-level directory; note languages and the largest files. Skip `node_modules`, `dist`, `build`, `vendor`, `.git`, `coverage`, `__pycache__`, `.venv`.
2. **Stack** — read the manifest files (`package.json`, `go.mod`, `pyproject.toml`, `Cargo.toml`, `pom.xml`), framework config, CI config, `README`.
3. **Entry points** — servers, CLI mains, route tables, page/app directories, workers, cron jobs.
4. **Layers** — where data access, business logic, API/UI, and shared utilities live; how they import each other.
5. **Tests** — test directories, runner, rough count, which areas have none.
6. **Hot spots** — `git log --since="6 months ago" --name-only --pretty=format: | sort | uniq -c | sort -rn | head -20` for actively changing files; TODO/FIXME clusters.

**Large repos:** if subagents are available (Claude Code), split by top-level directory and scout in parallel with `harnessclaude-researcher` subagents, one directory group each, each returning a file list with one-line descriptions. Otherwise go directory by directory. Read big files in chunks (~500 lines), never whole.

Write the result into the **Current State** part of `artifacts/02-discovery/context-brief.md`:

```markdown
## Current State — {repo}
- Stack: {languages, frameworks, versions}
- Entry points: `path` — purpose
- Layout: {layer → directories}
- Tests: {runner, location, coverage gaps}
- Hot spots: {files changing often, debt clusters}
- Unknowns: {what needs the user's input}
```

## Mode 2 — Impact Scout (evaluate, before code review)

Mandatory for multi-file features or changes to shared code; optional for single-file changes.

1. Changed files: `git -C <repo> diff --name-only <base>..<head>` for the feature's commits, in each repo it changed.
2. For each changed export/function, find its callers (`grep -rn "functionName"`), including other modules.
3. Look for:
   - callers outside the task's scope that now behave differently
   - error paths that are not tested
   - boundaries: null, empty, max, concurrent calls
   - state mutated in place, caches not invalidated
   - interface contracts in `spec.md` that consumers rely on
4. Hand the findings to `code-review` Stage 2:

```markdown
Impact scout:
- Affected outside scope: `path:line` — why
- Untested paths: {list}
- Boundary risks: {list}
```

Do not fix anything in this mode — findings go into the review.
