---
name: explore
description: Discovery strategy for greenfield projects starting from scratch
delegates:
  - research
---

# Explore Mode

No existing documentation — discover what to build through conversation and research.

## Artifact Output Location

All design output goes to `artifacts/02-discovery/` (context-brief.md, {topic}-design.md). Register each file with the create-artifact skill.

## Process

1. **Understand the problem before the solution.** Ask one question per message, multiple-choice when possible. Cover: who it is for, the problem it solves, what success looks like, hard constraints (budget, deadline, platform, compliance), and what is explicitly out of scope.
2. **Research when the domain is unfamiliar** — use the research skill before proposing approaches.
3. **Propose 2–3 approaches** with one-line trade-offs each and your recommendation with the reason. Let the user choose; never pick silently.
4. **Present the design in short sections** (scope, main flows, data, integrations, risks) and confirm each section before moving on. Revise when the user pushes back.
5. **Apply YAGNI** — cut features the user did not ask for and that the success criteria do not need. List them under "Deferred" instead of designing them.
6. **Write** `{topic}-design.md` and synthesize `context-brief.md` from the agreed sections; register via create-artifact, then validate with validate-artifact.

## Tips

- Start broad, then narrow — understand the problem before discussing solutions
- Challenge assumptions gently — "Why does it need to be real-time?" is a valid question
- Document decisions and their reasoning — future you will thank present you
- Never write the design first and ask for changes after — agree section by section
