# Database per Workspace: one deployment, one database per company, a Tenant Registry to find it

Supersedes ADR-0001 (multi-tenant pool model). Decided 29/10/2026; Workspace provisioning settled 31/10/2026.

T-Solve still serves many companies from one deployment, but each Workspace now has its own PostgreSQL database
and its own file storage. A Tenant Registry database (the "catalog") holds only what is needed to find a Workspace: site name, status,
database connection, the Jira site and its `cloudId`, and the emails of its Accounts (one email in one Workspace).
The Workspace of a request still comes only from the Workspace site's host name, the verified Jira `cloudId`, or the
job's Workspace, never from request data; binding it routes every query to that Workspace's database. Business
tables have no `workspace_id`; a row policy against a one-row tenant-id table backs up the database boundary (addendum).
Workspaces are created, suspended and reactivated by one provisioning service, which the T-Solve operations team runs
with an ops script and a Platform Operator runs from the Platform Console (addendum; GBR-24, GBR-37, GBR-38).

We chose this over the pool model because a database boundary is a stronger and simpler guarantee to defend than
"every query and every table carries the right `workspace_id`", each company can be backed up and restored on its
own, and business tables stay the same as in the single-tenant design of Report 3.

## Consequences

- The application holds one connection pool per Workspace in use; pools are opened lazily and bounded, so the total
  number of database connections grows with the number of active Workspaces.
- Every schema change runs once per Workspace database. A deploy migrates the Registry, then each Workspace database,
  and stops naming the Workspace whose migration failed.
- Background jobs run once per Active Workspace; a job carries only its Workspace id and record ids. JobRunr's own
  tables live in a separate Jobs database (see the addendum).
- Each Workspace database has its own runtime database user that can connect only to it; migrations and the ops
  script use a separate owner user. Database passwords are stored encrypted in the Registry.
- The tenant-isolation test (an Account of Workspace A cannot read Workspace B) stays mandatory before the demo.
- `login.` and `api.` have no Workspace of their own and must get it from a verified token (`login.` is Keycloak, ADR-0003);
  `admin.` is used again by the Platform Console (addendum).

## Addendum 04/11/2026 (multi-tenant HLA, KAN-98)

- Name: the former catalog DB is now the **Tenant Registry** (Registry DB). It holds no sign-in state: the sign-in is Keycloak's (ADR-0003).
- **Jobs DB:** JobRunr uses its own database `tsolve_<env>_jobs` on the same PostgreSQL server, not the Registry DB. `platform` owns it and stores the first-Admin invitation job in it through `jobrunr-spring-boot-3-starter`; `worker` runs the jobs.
- **Row guard (reverses "RLS is not used"):** each Workspace database has a one-row tenant-id table and a row policy on
  every tenant table, so a query bound to the wrong Workspace returns nothing. It backs up the database boundary; the
  runtime user has no BYPASSRLS. Accepted: SQL injection in a module's code could set the tenant setting to the id of
  its own Workspace; it still reaches only that Workspace's database. The guard defends against misrouting, not
  injection; the database boundary and the runtime user are the defence against injection.
- **Garage** (S3-compatible) holds files: object keys prefixed by Workspace id, signed URLs, no pod-local files.
- **Redis** (one container, persistence on) keeps the sessions.
- `admin.` is used again for the Platform Console. Docker Compose is the MVP and demo deployment; Kubernetes with high
  availability is the target.
