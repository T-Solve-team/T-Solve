package vn.tsolve.tracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.TenantId;

/** The Jira site an Admin registered for a Workspace; its cloudId ties Jira App calls to that Workspace. */
@Entity
@Table(name = "jira_site")
public class JiraSite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Set by Hibernate from WorkspaceContext on insert: the Admin's Workspace.
    @TenantId
    @Column(name = "workspace_id", nullable = false, unique = true, updatable = false)
    private Long workspaceId;

    @Column(name = "site_url", nullable = false)
    private String siteUrl;

    @Column(name = "cloud_id", nullable = false, unique = true)
    private String cloudId;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    protected JiraSite() {}

    public JiraSite(String siteUrl, String cloudId, Instant now) {
        this.siteUrl = siteUrl;
        this.cloudId = cloudId;
        this.registeredAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public String getSiteUrl() {
        return siteUrl;
    }

    public String getCloudId() {
        return cloudId;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }
}
