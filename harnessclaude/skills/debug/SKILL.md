---
name: debug
description: Root-cause-first debugging for failing tests, failed reviews, and unexpected behavior
type: on-demand
phases: [implement, evaluate]
agent: developer
delegates:
  - tdd
  - verify
---

# Debug

Use when a feature is in `fixing` and the review's cause is not obvious, when tests fail unexpectedly during implement, or when behavior differs from the spec.

## Iron Law

```
NO FIXES WITHOUT ROOT CAUSE INVESTIGATION FIRST
```

## Four Phases — finish each before the next

### 1. Investigate

1. Read the error, warnings, and stack trace completely.
2. Reproduce reliably. If you cannot, gather more data instead of guessing.
3. Check what changed: `git diff`, recent commits in the session log, new dependencies, config.
4. Multi-component flows: log what enters and leaves each boundary, run once, find *where* it breaks.
5. Trace the bad value backward to its origin — see `references/root-cause-tracing.md`.

### 2. Compare

1. Find similar code in the same repo that works.
2. Read it completely; list every difference, however small.
3. Note the config, environment, and dependencies it relies on.

### 3. Hypothesize

1. One hypothesis: "X is the root cause because Y."
2. Test it with the smallest possible change, one variable at a time.
3. Wrong → form a new hypothesis. Do not stack fixes.
4. If you do not understand something, say so and ask.

### 4. Fix

0. If the root cause is the spec — it is wrong, contradicts itself, or never said what should happen — stop. Fix the spec, not the code: ask the user, then change it through the plan skill's Change Mode (Changelog row, user approval) before any code change.
1. Write a failing test that reproduces the bug (`tdd` skill), named with the AC id the code violates.
2. Make one fix at the root cause. No "while I'm here" changes.
3. Run the full suite; apply `verify`.
4. Fix did not work → count attempts. Under 3: back to phase 1 with the new information.
5. **3 failed fixes → stop.** Each fix revealing a new problem elsewhere means the design is wrong, not the line. Set the feature to `blocked` via update-tracking, write what you learned in the session log, and discuss with the user before trying again.

## Record

Append to `artifacts/05-implementation/{module}/task-{N}-{slug}.md`:

```markdown
### Debug: {symptom}
- Root cause: {what and where}
- Evidence: {command / log line that proved it}
- Fix: {change} — commit {hash}
- Guard added: {validation or test that prevents recurrence}
```

## Red Flags — return to phase 1

"Quick fix for now", "just try changing X", "it's probably X", several changes before one test run, "one more attempt" after two failures, "seems fixed" without a run.
