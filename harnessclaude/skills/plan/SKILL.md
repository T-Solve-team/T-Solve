---
name: plan
description: Create spec, design, and task breakdown for a module
type: sequential
agent: planner
delegates:
  - edge-cases
  - risk-review
---

# Plan

Create a complete, actionable plan for implementing a single module.

## Process

### Step 1: Read the context

1. Read module definition: `artifacts/03-modules/{module}/module.md`
2. Read module spec (BA-owned): `artifacts/03-modules/{module}/spec.md` — this has requirements, user stories (STORY-IDs), and acceptance criteria. Check the Changelog table for recent changes.
3. Read architecture overview: `artifacts/03-modules/architecture.md` — this has system-level decisions (deployment, DB, comms patterns) that constrain your design
4. Read context brief: `artifacts/02-discovery/context-brief.md`
5. Read any relevant research: `artifacts/01-research/`
6. Check dependencies: are dependent modules' interfaces defined?

### Step 2: Gather decisions BEFORE writing artifacts

<HARD-GATE>
Before writing any artifact, identify all decision points in the module and
resolve them with the user. Do NOT write spec.md or design.md with assumptions.
</HARD-GATE>

**Decision gathering process:**
1. List all technical decisions this module requires (database, framework, auth, APIs, etc.)
2. For each decision, check if it's already resolved:
   - Specified in context-brief.md → use it, no need to ask
   - Specified in module definition → use it, no need to ask
   - Decided in a research artifact → use it, no need to ask
   - Not specified anywhere → **must ask the user**
3. For each unresolved decision, present 2-3 options:
   - Option name
   - One-line trade-off
   - Your recommendation and why
4. **One question at a time** — don't dump all decisions in one message
5. Document each decision in the design artifact with rationale

**Example flow:**
```
You: "For the auth module, I need to decide on a few things.
     First: what database should we use?
     A) PostgreSQL — battle-tested, great for relational data
     B) SQLite — simple, no server needed, good for small-medium scale
     C) MongoDB — flexible schema, good for rapid iteration
     I'd recommend A for this use case because [reason]. What do you think?"

User: "go with A"

You: "Got it, PostgreSQL. Next: for authentication strategy..."
```

### Step 3: Write artifacts (after all decisions are resolved)

Write three artifacts (use create-artifact skill for each):

#### Spec (what to build) — BA-owned
Location: `artifacts/03-modules/{module}/spec.md`

The spec is owned by the BA and should already exist before planning begins. If it doesn't exist yet:
- Create it using `templates/spec.md` in `artifacts/03-modules/{module}/spec.md`
- Include requirements, user stories (with STORY-IDs), acceptance criteria with ids (`AC-001.1`, EARS form), Out of Scope, interface contracts

If it already exists, **do not overwrite** — read it and base your design and tasks on it. A spec written before acceptance-criteria ids existed gets ids added, with a Changelog row ("Add AC ids").

Size the spec to the risk:

| Module touches | Spec depth |
|----------------|------------|
| Auth, payments, personal data, schema or migrations, public or shared contracts | Deep — every story, error path, and non-functional target as criteria; exact interface contracts; `risk-review` |
| Core business logic | Normal — criteria for the happy path, errors, and edge cases of every story |
| UI, internal tooling, small fixes | Light — only the criteria the change must satisfy |

Then make it pass the contract check:
1. Run `bun harnessclaude/hooks/sdd.ts check {module}`.
2. Every gap or ambiguity becomes `[NEEDS CLARIFICATION: <question>]` in the spec. Ask the user — one question at a time — and write the answer into the spec. Never fill a gap with a guess.
3. Repeat until `check` passes. Errors must be fixed; warnings (non-EARS or vague wording, a WHEN story with no IF … THEN failure path) usually should be.

#### Design (how to build it)
Write to: `artifacts/04-plans/{module}/design.md` (master — always written here)

Using `templates/design.md`:
- Architecture overview
- Data model
- **Key technical decisions with rationale** (from Step 2 — include what was chosen and why)
- File structure plan
- External dependencies

#### Risk review (high-risk modules only)
If the module touches auth, payments, personal data, data migrations, multi-tenancy, or a public API — or the user is torn between approaches — run the `risk-review` skill on the draft design. Present the GO / CAUTION / STOP verdict to the user. STOP → return to Step 2 decision gathering. CAUTION → fold mitigations into the design or tasks.

#### Edge cases (before tasks)
Run the `edge-cases` skill to fill the **Edge Cases & Risks** section of design.md. Spec gaps it uncovers are questions for the user, not assumptions.

#### Tasks (implementation order)
Write to: the active plan path (see skill.md Plan Paths). When iterations enabled, this is only written as part of iteration slicing in Step 6, not at the root level.

Using `templates/tasks.md`:
- Ordered list of implementation tasks
- Each task: title, **Story ID (STORY-XXX)**, **Feature** key (a short kebab-case slug, e.g. `login-form` — it names the feature in progress.json, the session log `task-{N}-{slug}.md`, and the review), description, files to create/modify, dependencies on other tasks
- Every task MUST reference at least one STORY-ID from the spec
- Tasks should be small enough for one implementation session
- Include test tasks alongside implementation tasks — every Critical/High edge case gets one

#### Builder check (Normal and Deep specs)
Before the user reviews the plan, read it the way the builder will meet it. Skip this for a Light spec.

1. If subagents are available (Claude Code), dispatch one with fresh context — no conversation history, only the paths of `spec.md`, `design.md`, the tasks file, `module.md`, and `architecture.md` — and this brief:
   > You will build this module from these files alone. List what you would still have to ask before you could build it and write a test for every acceptance criterion: missing behavior, criteria you could not test, places where spec and design disagree, interfaces or data left undefined, decisions the design leaves open. For each: file and section, the question, and whether the answer changes what gets built (`blocking`) or is a call any builder could make (`minor`). Do not propose features. Do not edit files.

   Otherwise do it inline: re-read only those files, as someone who did not write them.
2. Settle each item:
   - Already answered in the artifacts → drop it.
   - `minor` (naming, internal structure — never a technology or a behavior) → decide it and write it into design.md as `Decided in planning: … (builder check)`, so the user sees it in the review.
   - `blocking` → `[NEEDS CLARIFICATION: <question>]` in the spec; ask the user one question at a time, write the answer in, and re-run `sdd.ts check`.
3. One round. Run a second only if the answers added stories.

### Step 4: Review with user

Present each artifact section by section. Ask "does this look right?" after each.
Only proceed to the next artifact after the current one is approved.

**Spec approval:** once the user agrees the spec is right, run `bun harnessclaude/hooks/sdd.ts approve {module}`. The user confirms it in the permission prompt — that confirmation is the approval. The module's code stays locked until it is done, and any later spec edit locks it again until the user approves the new version.

### Step 5: Iteration slicing (only if iterations enabled)

<HARD-GATE>
Only perform this step if manifest.yaml has `iterations.enabled: true`.
If iterations are not enabled, skip entirely.
</HARD-GATE>

After creating the master spec and design, and after all tasks are defined:

1. **Propose iteration grouping**: Review all tasks and propose which belong to iteration 1, iteration 2, etc. Consider:
   - Task dependencies (dependent tasks should be in the same or later iteration)
   - Task size (balance work across iterations)
   - Present the grouping to the user for approval

2. **User approves or adjusts**: Wait for confirmation before proceeding.

3. **Write iteration-scoped artifacts**: For each iteration N:
   - Create `artifacts/04-plans/{module}/iteration-{N}/design.md` — filtered subset of master design containing only decisions relevant to this iteration's tasks
   - Create `artifacts/04-plans/{module}/iteration-{N}/tasks.md` — only the tasks assigned to this iteration, each referencing STORY-IDs
   - Each scoped design must include: `Master: artifacts/04-plans/{module}/design.md` as a cross-reference
   - Each scoped tasks file must include: `Spec: artifacts/03-modules/{module}/spec.md` as a cross-reference
   - Note: iteration-scoped specs are NOT created — the spec in `03-modules/` is the single source of truth (BA-owned)

4. **Create iteration file**: Write `artifacts/07-iterations/iteration-{N}.md` using the `templates/iteration.md` template with:
   - Goal: derived from the iteration's scoped spec
   - Planned deliverables: the tasks for this iteration
   - Demo script: outline of what can be demonstrated

5. **Update progress.json**: Ensure `currentIteration` is set to 1 (should already be set by init)

### Step 6: Finalize

1. Validate the artifacts with the validate-artifact skill; `bun harnessclaude/hooks/sdd.ts check {module}` must pass (it also checks that tasks name only stories the spec has).
2. If the project has more than one module, make sure `module.md` has `paths` covering this module's code **and its tests** — acceptance-criterion ids restart in every module, and `sdd.ts trace` uses `paths` to know whose test a file is.
3. Move the module: `bun harnessclaude/hooks/sdd.ts status {module} planned` — it refuses until the user has approved the current spec.
4. Add every task of the active plan as a feature: `bun harnessclaude/hooks/sdd.ts feature {module} {slug} pending`, where `{slug}` is the task's `**Feature:**` key.

## Change Mode (existing module)

For a behavior change, a new requirement, or a spec gap found during implement or evaluate. The spec changes first; code follows.

1. Read the module's spec, design, tasks, and its entry in progress.json.
2. Update the spec: edit or add acceptance criteria (new ids continue the numbering — never reuse a removed id) and add a Changelog row (date, story, change, reason).
3. `bun harnessclaude/hooks/sdd.ts check {module}` — resolve every `[NEEDS CLARIFICATION]` with the user. A change that adds a story gets the builder check (Step 3) first.
4. Show the user what changed: `bun harnessclaude/hooks/sdd.ts diff {module}` lists the criteria added, reworded, and removed since their last approval. When they agree, run `sdd.ts approve {module}` — they confirm in the permission prompt, which repeats the changes.
5. Update design.md if the change affects it; append tasks to tasks.md (each with its STORY-ID and `**Feature:**` key) and add them: `sdd.ts feature {module} {slug} pending`.
6. If the module was `completed`, reopen it: `sdd.ts status {module} planned`. Then implement as usual.

A bug where the code breaks an existing criterion needs no spec change. If the fix fits in at most 5 files, make it a quick change (skill.md, Size the Work). Otherwise add a task "fix AC-xxx", reopen the module if it was `completed`, and fix it test-first.

## Rules

- **Never silently pick a technology** — present options, let the user choose
- **One question at a time** — don't overwhelm with multiple decisions
- The spec is the contract the evaluator checks against — be precise and testable
- Tasks should reference exact file paths in the target repo
- If a dependency module's interfaces aren't defined yet, flag as a blocker
- Include edge cases and error scenarios in the spec
- The design must document every decision with "chosen: X, alternatives: Y/Z, rationale: ..."
- If unsure about anything, ask — don't guess
