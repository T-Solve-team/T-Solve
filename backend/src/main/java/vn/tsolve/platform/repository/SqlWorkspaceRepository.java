package vn.tsolve.platform.repository;

import java.util.Optional;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.tsolve.platform.dto.WorkspaceSummary;

public final class SqlWorkspaceRepository {
    private final JdbcTemplate catalog;

    public SqlWorkspaceRepository(JdbcTemplate catalog) {
        this.catalog = catalog;
    }

    public Optional<WorkspaceSummary> findById(long id) {
        return catalog
                .query(
                        "SELECT workspace_id, site_name, status, jira_cloud_id FROM tenant_workspace WHERE workspace_id=?",
                        (rs, row) ->
                                new WorkspaceSummary(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)),
                        id)
                .stream()
                .findFirst();
    }

    public Optional<WorkspaceSummary> findBySiteName(String name) {
        var matches = catalog.query(
                "SELECT workspace_id, site_name, status, jira_cloud_id FROM tenant_workspace WHERE lower(site_name)=?",
                (rs, row) -> new WorkspaceSummary(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)),
                name);
        // SQL's unique site_name is case-sensitive, but HTTP hosts are not; ambiguity must fail closed.
        return DataAccessUtils.optionalResult(matches);
    }

    public Optional<TenantConnection> findConnection(long id) {
        return catalog
                .query(
                        "SELECT workspace_id, db_host, db_port, db_name, db_username, db_password_ref"
                                + " FROM tenant_workspace WHERE workspace_id=?",
                        (rs, row) -> new TenantConnection(
                                rs.getLong(1),
                                rs.getString(2),
                                rs.getInt(3),
                                rs.getString(4),
                                rs.getString(5),
                                rs.getString(6)),
                        id)
                .stream()
                .findFirst();
    }
}
