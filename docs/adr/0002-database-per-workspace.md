# Database per Workspace: one deployment, one database per company, a catalog to find it

Supersedes ADR-0001 (multi-tenant pool model). Decided 29/10/2026; Workspace provisioning settled 31/10/2026.

T-Solve still serves many companies from one deployment, but each Workspace now has its own PostgreSQL database
and its own file storage. A catalog database holds only what is needed to find a Workspace: site name, status,
database connection, the Jira site and its `cloudId`, and the emails of its Accounts (one email in one Workspace).
The Workspace of a request still comes only from the Workspace site's host name, the verified Jira `cloudId`, or the
job's Workspace, never from request data; binding it routes every query to that Workspace's database. Business
tables have no `workspace_id`, and PostgreSQL row-level security is not used. Workspaces are created, suspended and
reactivated by the T-Solve operations team with an ops script outside the application; there is no Platform
Console and no Platform Operator actor in the MVP (GBR-24, GBR-37, GBR-38).

We chose this over the pool model because a database boundary is a stronger and simpler guarantee to defend than
"every query and every table carries the right `workspace_id`", each company can be backed up and restored on its
own, and business tables stay the same as in the single-tenant design of Report 3.

## Consequences

- The application holds one connection pool per Workspace in use; pools are opened lazily and bounded, so the total
  number of database connections grows with the number of active Workspaces.
- Every schema change runs once per Workspace database. A deploy migrates the catalog, then each Workspace database,
  and stops naming the Workspace whose migration failed.
- Background jobs run once per Active Workspace; a job carries only its Workspace id and record ids. JobRunr's own
  tables live in the catalog database.
- Each Workspace database has its own runtime database user that can connect only to it; migrations and the ops
  script use a separate owner user. Database passwords are stored encrypted in the catalog.
- The tenant-isolation test (an Account of Workspace A cannot read Workspace B) stays mandatory before the demo.
- `login.` and `api.` have no Workspace of their own and must get it from a signed state value or a verified token;
  `admin.` is no longer used.
