---
id: RESEARCH-007
title: Sync T-Solve Project membership from Jira project roles
question: Can T-Solve fill a Project's members from the Jira project's roles, so a Project Manager does not add the same people twice, and how?
status: complete
date: 2026-11-18
related: [project-integration, identity, jira-app, KAN-358]
---

# Sync T-Solve Project membership from Jira project roles

## Research Question
When a Project Manager adds people to a Jira project, can T-Solve make them members of the T-Solve Project
automatically? Which Jira events or APIs make it possible, with which permissions, and what limits apply?

## Context
- Today a Project Manager adds Ticket Resolvers on the web app, on Project Detail → Members → Add member
  (project-integration STORY-002).
- Membership decides who may write a Solution from Jira (jira-app AC-001.2). It also decides which Departments a
  Ticket Resolver sees on the web: Ticket List, Search, Spaces (identity `ScopeAccessService`, STORY-011; search
  STORY-012). A Ticket Resolver has no Department of their own, so membership is their only link to one.
- In the dev-site test of 18/11, the user (owner of project-integration) found it redundant to add people both in
  Jira and in T-Solve.
- Earlier the same day, `GET /rest/api/3/user/assignable/search?project=DP` was checked. It returned almost every
  user of the site, and `emailAddress` was `""` for all of them, even for a site admin. So Jira users cannot be
  matched to T-Solve Accounts by email.

## Findings

### 1. No Jira event for role or access changes
The Forge Jira events reference lists issue, comment, worklog, issue type, field, version, project lifecycle
(`avi:jira:created|updated|deleted|archived…:project`), component, user, filter and configuration events. It has
**no event** for project role actors, project permissions or team-managed access changes. `avi:jira:updated:project`
covers project settings, not who is in it.
→ T-Solve cannot react to "user added to the project". It has to **read the roles again on a schedule** (or when
someone asks).

### 2. The role API and what it returns
Probed on the dev site (bdang798) on 18/11, as a site admin user, read only:
- `GET /rest/api/3/project/DP/role` → the role links. Both **DP** and **SAM1** have the roles `Administrator`,
  `Member`, `Viewer`, `jira-guest-member` and `atlassian-addons-project-access` (the app role). These are
  **team-managed** projects.
- `GET /rest/api/3/project/10001/role/10015` (DP, Member) → `actors`, each with `type: atlassian-user-role-actor`,
  `displayName` and `actorUser.accountId`. **Only 5 people**, the ones added on purpose, unlike the assignable-user
  search, which returned nearly the whole site. A company-managed project can also list group actors
  (`atlassian-group-role-actor`). Expanding those needs the group API.
- Scopes: the official REST v2 page for the role endpoints names the granular scope `read:project-role:jira` (classic
  `read:jira-work`). The role actors page says **"Administer Projects" for the project or "Administer Jira"** is
  needed.

### 3. Can the app's own token read roles?
- The T-Solve Jira App calls Jira as itself (KAN-349). Its Jira user is **TSolve_JiraApp**
  (`712020:0defd642-0163-416f-808f-f89997cb3970`), an actor of each project's `atlassian-addons-project-access` role.
- **Checked on 18/11** with `POST /rest/api/3/permissions/check` (run as a site admin, for that accountId, read only).
  The app user **has `ADMINISTER_PROJECTS`, `BROWSE_PROJECTS` and `TRANSITION_ISSUES` on both DP (10001) and SAM1
  (10000)**. On these team-managed projects the add-ons role is granted project administration. So the role
  endpoints should **not** answer 403 for the app, unlike what this document first assumed.
- Scope: the role endpoints accept the classic scope `read:jira-work`, which the app already has (AC-013.2). No admin
  scope (`manage:*`) is needed.
- Still unconfirmed: one real call with the app's system token (it is held encrypted in Redis on staging), and
  **company-managed** projects. There, whether the add-ons role gets Administer Projects depends on the permission
  scheme. A 403 there must be handled ("Jira suggestions not available for this project").
- Fallback if the app is refused: a call **as the Project Manager** (Forge `asUser`) from a Jira App page. The user
  token is not stored, so it only works while the PM is in Jira.

### 4. Team-managed vs company-managed
| | Team-managed (DP, SAM1) | Company-managed |
|---|---|---|
| Roles | Administrator, Member, Viewer (fixed) | Defined by the Jira admin (e.g. Developers, Users) |
| Who can transition | Member and Administrator, not Viewer | Permission scheme ("Transition/Resolve issues"), often granted to everyone |
| Access level | Open / Limited / Private | Through the permission scheme |
| Which role means "the team" | `Member` (+ `Administrator`) | Must be chosen per project (setting) |

### 5. Mapping to T-Solve
For each actor of the chosen role(s):
1. `accountId` → T-Solve Account through `jira_account_links` (identity AC-001.77). This is the only reliable
   mapping, because emails are hidden and the Jira and T-Solve identities differ (e.g. `bdang798@gmail.com` in
   Jira, `ticket-resolver@bluefin-demo.io.vn` in T-Solve).
2. Keep only Active Accounts with the **Ticket Resolver** role (as STORY-002 requires). Others are reported, not
   added.
3. Add the missing ones as `MEMBER` with the existing add path and audit (`PROJECT_MEMBER_CHANGE`). Removing
   people who left the Jira role is a separate decision (it removes their data access).
People who have not linked their Jira account yet cannot be matched. They join on the next sync after they link.

### 6. Rate limits and cost
One sync per Project costs 1 call for the role list plus 1 per role read (2 for Administrator + Member), without
paging. That is far below Jira's per-app limits, even hourly for dozens of Projects. A group actor
(company-managed) adds 1+ calls per group.

## Recommendations
1. **No unattended sync.** No Jira event exists, and adding members blindly would also widen data access (search
   STORY-012). Membership stays a Project Manager decision.
2. **Do: "Suggest from Jira" in the web app's Add member popup (B'), reading Jira project roles with the app
   token.** When the Project Manager opens Add member, the backend reads the chosen role(s) with the app token (no new
   scope), keeps the actors who are linked, Active Ticket Resolvers and not yet members, and shows them first. The
   Project Manager ticks them and clicks Add (existing add path, audit and checks). If Jira refuses (403, e.g. a
   company-managed project), the popup says suggestions are not available and manual search still works.
3. Keep manual Add member for people who have not linked their Jira account.
4. Treat it as a project-integration change (owner Đặng Tấn Bình). It changes how members are found, not the SRS
   rule that membership is decided by the Project Manager. Tell the team before building.

## Decisions (user, 18/11)
- **Which role means "the team":** each Project chooses its role(s) when the Project Manager uses "Suggest from
  Jira". The default is `Member` (team-managed), or `Developers` / `Member` when the project has it (company-managed).
- **Removal:** T-Solve never removes a member because they left the Jira role. It only suggests additions; removing
  stays manual (project-integration STORY-003).
- **Groups:** a *group* here is an Atlassian user group managed in Atlassian Administration → Directory → Groups
  (e.g. `jira-software-users`, a custom `it-support-team`). In a role it appears as an actor of type
  `atlassian-group-role-actor` with `actorGroup.name` and `groupId`, without the people in it. The first version
  **does not expand groups**. It only suggests users added to the role directly, and tells the Project Manager when
  the role also contains a group ("Role Developers also contains group it-support-team (members not listed)").

## Open Questions
- Confirm with one real call using the app's system token on staging (expected 200 on DP and SAM1).
- Company-managed projects: is `ADMINISTER_PROJECTS` granted to the add-ons role by the default permission scheme?
  If not, the 403 message of Recommendation 2 applies.

## Related Artifacts
- `artifacts/03-modules/project-integration/spec.md`: STORY-002 Add Ticket Resolvers, Out of Scope "Emailing or
  notifying the added people"
- `artifacts/03-modules/jira-app/spec.md`: AC-001.2 (membership for the Solution Form), AC-013.2 (no admin scope),
  STORY-014 (account link)
- `artifacts/03-modules/identity/spec.md`: STORY-011 (scope checks), AC-001.77 (`JiraAccountLinkLookup`)
- RESEARCH-003 (Forge Solution Form): app identity and scopes
- Sources: Forge Jira events reference (developer.atlassian.com/platform/forge/events-reference/jira/); Jira Cloud
  REST project roles and project role actors (developer.atlassian.com/cloud/jira/platform/rest/v2/api-group-project-roles/,
  …/rest/v3/api-group-project-role-actors/); dev-site probes of 18/11
