---
module: platform
feature: task-3a-registry-db
task: 3a
verdict: pass
attempt: 2
date: 2026-11-22
reviewer: tsolve-code-reviewer
---

# Review 2: Task 3a, fixes I1, M1, M4 (uncommitted tree)

Verdict: approve with notes. 0 critical / 0 important / 2 minor. Scope: C001, C002, RegistryDataSourceConfig.java, 10-tsolve-dbs.sh. Developer's verify not re-run (PasswordCipherTest, RegistryChangelogIT 9/9, spotless:check all BUILD SUCCESS); no finding made me doubt it.

## Fix checks
- I1 fixed. C001 now has site_name VARCHAR(63), the `ck_tenant_workspace_site_name` regex CHECK, `gen_random_uuid()` and `now()` defaults, and the four `uq_tenant_workspace_*` names as in the design.
- M1 fixed. `registryJdbcClient` is registered in the same post-processor as the other Registry beans: `setAutowireCandidate(false)`, so it does not displace the primary beans (edge 72), and the class carries the same `tsolve.registry.url` gate. Minor: no test fetches the bean (RegistryChangelogIT checks only `registryDataSource` and `registryTransactionManager`); add `.hasBean("registryJdbcClient")` at line 268 and `doesNotHaveBean` at line 330 when Task 3b first uses it.
- M4 fixed. REGISTRY_DB_USER and REGISTRY_DB_PASSWORD use `:?` guards that name the variable (lines 22-23). REGISTRY_DB_NAME keeps its `:=` default. The app, Keycloak and Jobs lines are unchanged. The guards run after three databases are created; harmless, because Postgres init aborts on a non-zero exit and the volume must be wiped anyway.

## C001/C002 against the design DDL, column by column
| Column / item | Design | Built | Rating |
|---|---|---|---|
| workspace_id, site_name, status, db_password, created_at, updated_at | | same | none |
| db_name | VARCHAR(63) | VARCHAR(255) | harmless; Postgres truncates identifiers at 63, so the app must validate the name |
| db_user | VARCHAR(63) | VARCHAR(255) | harmless, same reason |
| schema_version | VARCHAR(255) | VARCHAR(50) | narrower, small risk: the longest current tenant changeset id is about 35 chars, so it fits today; a long future id would fail the Registry insert |
| google_domain | VARCHAR(253) | VARCHAR(255) | harmless (wider) |
| keycloak_organization_id | VARCHAR(100) | VARCHAR(255) | harmless (wider) |
| jira_site_url | VARCHAR(300) | VARCHAR(500) | harmless (wider) |
| jira_cloud_id | VARCHAR(100) | VARCHAR(255) | harmless (wider) |
| column order | design order | differs | harmless |
| uq_* and ck site_name constraints | named | named, same | none |
| C002 email check | named `ck_workspace_email_lower` (table-level) | unnamed inline CHECK, auto name `workspace_email_email_check` | harmless unless code or tests match the name; none do |
| C002 index | `idx_workspace_email_workspace` | `idx_workspace_email_workspace_id` | harmless unless something references it; nothing does |

Wider columns only widen what is accepted, so they cannot reject valid data. The only column that can reject valid data later is schema_version. Hieu decides: widen it to 255 now, or amend the design to 50 (and the other widths to what was built). A merged changeset cannot be edited.

## Findings
- Minor: C001:12 schema_version VARCHAR(50) is narrower than the design's 255. Fix: VARCHAR(255) or amend the design.
- Minor: RegistryChangelogIT:268,330 has no assert for `registryJdbcClient`. Fix: add `hasBean` / `doesNotHaveBean`.

## Test report
No new run. Developer's results are as above, on the same uncommitted tree. The earlier test report is `task-3a-registry-db-test-report.md`.
