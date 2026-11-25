package vn.tsolve.platform.registry;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import vn.tsolve.platform.dto.WorkspaceStatus;
import vn.tsolve.platform.dto.WorkspaceSummary;

/** Reads the Registry tables and claims Workspace emails over {@code registryJdbcClient}. */
public class RegistryRepository {

    private static final String SUMMARY_COLUMNS =
            "workspace_id, site_name, status, google_domain, keycloak_organization_id, jira_site_url, jira_cloud_id";

    private final JdbcClient jdbc;

    public RegistryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<WorkspaceSummary> findById(UUID workspaceId) {
        if (workspaceId == null) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT " + SUMMARY_COLUMNS + " FROM tenant_workspace WHERE workspace_id = ?")
                .param(workspaceId)
                .query(RegistryRepository::mapSummary)
                .optional();
    }

    public Optional<WorkspaceSummary> findBySiteName(String siteName) {
        if (siteName == null) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT " + SUMMARY_COLUMNS + " FROM tenant_workspace WHERE site_name = lower(?)")
                .param(siteName)
                .query(RegistryRepository::mapSummary)
                .optional();
    }

    public Optional<WorkspaceSummary> findByCloudId(String cloudId) {
        if (cloudId == null) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT " + SUMMARY_COLUMNS + " FROM tenant_workspace WHERE jira_cloud_id = ?")
                .param(cloudId)
                .query(RegistryRepository::mapSummary)
                .optional();
    }

    public List<WorkspaceSummary> findByStatus(WorkspaceStatus status) {
        return jdbc.sql("SELECT " + SUMMARY_COLUMNS + " FROM tenant_workspace WHERE status = ?")
                .param(status.name())
                .query(RegistryRepository::mapSummary)
                .list();
    }

    public void insertEmailIfAbsent(String normalizedEmail, UUID workspaceId) {
        jdbc.sql("INSERT INTO workspace_email (email, workspace_id) VALUES (?, ?) ON CONFLICT (email) DO NOTHING")
                .param(normalizedEmail)
                .param(workspaceId)
                .update();
    }

    public Optional<UUID> emailOwner(String normalizedEmail) {
        return jdbc.sql("SELECT workspace_id FROM workspace_email WHERE email = ?")
                .param(normalizedEmail)
                .query((rs, rowNum) -> rs.getObject("workspace_id", UUID.class))
                .optional();
    }

    private static WorkspaceSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new WorkspaceSummary(
                rs.getObject("workspace_id", UUID.class),
                rs.getString("site_name"),
                WorkspaceStatus.valueOf(rs.getString("status")),
                rs.getString("google_domain"),
                Optional.ofNullable(rs.getString("keycloak_organization_id")),
                Optional.ofNullable(rs.getString("jira_site_url")),
                Optional.ofNullable(rs.getString("jira_cloud_id")));
    }
}
