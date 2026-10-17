---
name: risk-review
description: Five-persona debate on a high-risk module design before it is approved — GO, CAUTION, or STOP
type: on-demand
phases: [plan]
agent: planner
---

# Risk Review

Stress-test a module's proposed design before the user approves it. Use only for high-risk modules:

- authentication, authorization, payments, or personal data
- data migrations or schema changes on existing data
- multi-tenancy, public APIs, or anything hard to reverse
- the user is unsure between competing approaches

Skip for routine modules — it costs time and tokens.

## Inputs

The draft `design.md`, `spec.md`, `architecture.md`, and the decisions gathered in the plan skill. Read the relevant existing code if the module changes it.

## Personas — each analyzes independently first

| Persona | Asks |
|---------|------|
| Architect | Does it fit `architecture.md`? What coupling does it add? Will it scale to the spec's targets? |
| Security | What can be abused? Where is data exposed? Are auth boundaries respected? |
| Performance | Latency, queries per request, memory, payload/bundle size |
| User | Is the flow clear? What do error and empty states look like? Accessible? |
| Devil's advocate | Why not do nothing? What is the simplest alternative? Which assumption, if wrong, breaks this? |

## Protocol

1. Write each persona's findings separately — do not let them converge early.
2. List points where four or more personas agree.
3. List conflicts; for each, decide which concern has higher impact and why.
4. Give a verdict:
   - **GO** — no critical risk; proceed.
   - **CAUTION** — manageable risks; mitigations become design changes or tasks.
   - **STOP** — any persona finds an auth bypass or data exposure without mitigation, a fundamental incompatibility with the architecture, an unacceptable performance cost with no workaround, or a false assumption that invalidates the approach. Return to decision gathering with the user.
5. Present the verdict to the user before writing tasks.

## Output

Append to `design.md` (active plan path):

```markdown
## Risk Review — {date} — Verdict: GO | CAUTION | STOP

Agreements: {bullets}

| Topic | Architect | Security | Performance | User | Devil's advocate | Resolution |
|-------|-----------|----------|-------------|------|------------------|------------|

| Risk | Severity | Mitigation | Task |
|------|----------|------------|------|
```

Every mitigation for a Critical/High risk must map to a design change or a task.
