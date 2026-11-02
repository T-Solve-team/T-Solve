# SQL Workspace bootstrap (KAN-107)

`02_tenant.sql` is a byte-preserving copy of the supplied schema. The catalog copy is in
`../catalog/01_catalog.sql`. The root SQL and database PDF remain the user's source documents.

The catalog and tenant XML files are independent Liquibase entry points. Apply
`catalog/catalog-master.xml` to an empty catalog database and `tenant/tenant-master.xml` to each
empty tenant database, using an owner credential. Runtime credentials must not own tables.
These entry points are not part of the existing legacy `db.changelog-master.xml`.

Database creation, first Admin, Workspace catalog rows and runtime grants are operations tasks.
There is no production seed or automatic schema/data conversion. Do not apply these bootstraps
over the legacy database or a manually initialized database, ignore table-exists errors, or mark
changesets applied to bypass an error. Adoption of an existing SQL database requires a separate
reviewed migration plan. The complete provisioning script remains KAN-240.

Before enabling `TSOLVE_SQL_WORKSPACE_ENABLED`, configure the catalog variables in `.env.example`
and a private server-side variable for each `db_password_ref`. Keep only the variable name in the
catalog, never the password. Supply the actual Host from the reverse proxy; forwarding headers
and request-supplied Workspace selectors are ignored.

Grant each runtime user CONNECT only to its own database and revoke the PostgreSQL default
PUBLIC CONNECT grants on the catalog and tenant databases. Grant schema/table privileges
explicitly. Separate non-owner users and cross-database connection denial are tested by
`SqlWorkspaceIsolationIT`; use equivalent database grants in deployment.

The feature is disabled by default. User paths return 503 MSG06 until enabled. Unknown Workspace
hosts return 404 MSG151 and Suspended Workspaces return 403 MSG150. Connection/secret/pool failures
return sanitized 500 MSG06 without choosing another database. Pools are lazy, default to five
connections per Workspace and at most 32 Workspace pools, and close at application shutdown.

The qualified `sqlTenantDatabase` resource holder contains its own routing DataSource, JDBC
template and transaction manager. They are intentionally not separate Spring beans of those
base types: publishing them would make Boot back off its legacy auto-configuration. The old
JPA repositories, sign-in state and Ticket/Import remain on the legacy primary database.

This feature supplies routing only. KAN-107's read API and real sign-in/session integration are
separate delivery steps. It does not establish tenant isolation for unported legacy modules.
