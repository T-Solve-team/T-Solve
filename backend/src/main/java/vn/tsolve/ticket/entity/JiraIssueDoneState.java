package vn.tsolve.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Whether a Project's Jira issue is in the Done status category, as the issue-updated trigger last saw it (jira-app
 * AC-005.9). Written only by an upsert in {@code JiraIssueDoneStateRepository}; read by the open-Draft query.
 */
@Entity
@Table(name = "jira_issue_done_state")
public class JiraIssueDoneState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "jira_issue_done_state_id")
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "jira_issue_key", nullable = false, updatable = false)
    private String issueKey;

    @Column(nullable = false)
    private boolean done;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JiraIssueDoneState() {}

    public boolean isDone() {
        return done;
    }
}
