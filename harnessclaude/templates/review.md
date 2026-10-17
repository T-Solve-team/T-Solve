---
module: {module name}
feature: {feature key — the slug in progress.json}
task: {task number}
verdict: pass | fail
attempt: {1|2|3}
date: {date}
reviewer: {assignee}
iteration: {N}  # only if iterations are enabled; delete this line otherwise
---

# Review: {module} — Task {N}: {feature name}

## Summary
{Brief assessment}

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass/fail | {detail} |
| Completeness | pass/fail | {detail} |
| Correctness | pass/fail | {detail} |
| Integration | pass/fail | {detail} |

## Traceability
{Output of `bun harnessclaude/hooks/sdd.ts trace {module} {task's STORY-IDs}` — a criterion without a test means verdict fail}

## Issues
{If fail — numbered list with file:line, description, suggested fix}

## Test Report
{Test execution summary and coverage}
