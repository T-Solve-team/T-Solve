package vn.tsolve.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A T-Solve Project linked to one Jira project. Only the columns read so far are mapped; the Project owner adds
 * the rest (department, field mapping, review range…) when the module is built.
 */
@Entity
@Table(name = "project")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "project_id")
    private UUID id;

    @Column(name = "jira_project_key", nullable = false)
    private String jiraProjectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    protected Project() {}

    public UUID getId() {
        return id;
    }

    public String getJiraProjectKey() {
        return jiraProjectKey;
    }

    public ProjectStatus getStatus() {
        return status;
    }
}
