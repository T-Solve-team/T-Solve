---
iteration: 2
start: 2026-10-31
end: 2026-11-13
demo: 2026-11-13
status: planned
---

# Iteration 2

## Goal

The Jira App reaches its own Workspace and only it (Forge token, `cloudId`, install registration), Ollama runs on the
private AI VPS, and the demo seed fills both Workspaces with module data. Other modules add their sections when
they plan this iteration.

## Planned Deliverables

### platform

| Task | Assignee | Status |
|------|----------|--------|
| 6 · Verify the Forge Invocation Token (KAN-105) | Trần Trung Hiếu | pending |
| 7 · Tenant Resolver of the Jira App and Jira site registration (KAN-101) | Trần Trung Hiếu | pending |
| 14 · AI VPS with Ollama behind WireGuard (KAN-203) | Trần Trung Hiếu | pending |
| 16 · Demo seed: module data through contributors (KAN-97) | Trần Trung Hiếu | pending |

## Demo Script

### platform
1. From the T-Solve app on the team's Jira test site: a call reaches staging `api.` and runs in that site's
   Workspace; the same call with a tampered token → 401; from a Jira site that is not registered → MSG124 in the panel.
2. Suspend the Workspace in the Platform Console (or through the registry): the panel shows MSG121, and creating an
   issue records nothing; reactivate → the panel works again.
3. Install the app on a registered Jira site → its `cloudId` is stored; reinstall → no change.
4. From the application VPS, `infra/tests/ai-vps.test.sh` returns a 1024-dimension embedding and a completion; from a
   hosted runner, port 11434 of the AI VPS is closed; after rebooting the AI VPS the check passes again.
5. `run-seed.sh staging` fills both Workspaces with Accounts of all five Roles, Departments, a Project, Published
   tickets and Wiki documents (as far as the contributing modules are built).

## Actual Outcomes
<!-- filled at end of iteration, split by module -->

## Retro Notes
<!-- what went well, what to improve -->
