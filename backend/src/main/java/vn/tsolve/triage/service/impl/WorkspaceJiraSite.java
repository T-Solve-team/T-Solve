package vn.tsolve.triage.service.impl;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.tsolve.triage.service.JiraSite;

/** Reads {@code workspace.jira_site_address} from the bound Workspace database (AC-003.5, AC-003.6). */
@Service
public class WorkspaceJiraSite implements JiraSite {

    private final JdbcTemplate jdbc;

    public WorkspaceJiraSite(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> url() {
        return jdbc.queryForList("SELECT jira_site_address FROM workspace", String.class).stream()
                .filter(site -> site != null && !site.isBlank())
                .findFirst();
    }
}
