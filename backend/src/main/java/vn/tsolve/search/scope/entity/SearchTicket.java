package vn.tsolve.search.scope.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

/** Search-owned read model of the legacy tables; it never changes another module's entities. */
@Entity
@Immutable
@Subselect(
        """
        select t.ticket_id as id, t.public_id, t.source_ticket_id as ticket_id, t.title, t.source, t.status, t.writer,
               t.resolved_date, t.updated_at, t.symptoms, t.root_cause, t.resolution_steps,
               t.workaround, t.scope, p.project_id, p.public_id as project_public_id,
               p.jira_project_key as project_name, d.department_id,
               d.public_id as department_public_id, d.name as department_name,
               d.status as department_status
          from ticket t join project p on (p.public_id = t.project_id
               or lpad(to_hex(p.project_id), 32, '0')::uuid = t.project_id)
          join department d on d.department_id = p.department_id
        """)
@Synchronize({"ticket", "project", "department"})
public class SearchTicket {
    @Id
    private UUID id;

    @Column(name = "public_id")
    private UUID publicId;

    @Column(name = "ticket_id")
    private String sourceTicketId;

    private String title;
    private String source;
    private String status;
    private String writer;

    @Column(name = "resolved_date")
    private LocalDate resolvedDate;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private String symptoms;

    @Column(name = "root_cause")
    private String rootCause;

    @Column(name = "resolution_steps")
    private String resolutionSteps;

    private String workaround;
    private String scope;

    // Internal Project key for visibility scopes; projectPublicId is the API UUID.
    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "project_public_id")
    private UUID projectPublicId;

    @Column(name = "project_name")
    private String projectName;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "department_public_id")
    private UUID departmentPublicId;

    @Column(name = "department_name")
    private String departmentName;

    @Column(name = "department_status")
    private String departmentStatus;

    protected SearchTicket() {}

    public UUID getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getSourceTicketId() {
        return sourceTicketId;
    }

    public String getTitle() {
        return title;
    }

    public String getSource() {
        return source;
    }

    public String getStatus() {
        return status;
    }

    public String getWriter() {
        return writer;
    }

    public LocalDate getResolvedDate() {
        return resolvedDate;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getSymptoms() {
        return symptoms;
    }

    public String getRootCause() {
        return rootCause;
    }

    public String getResolutionSteps() {
        return resolutionSteps;
    }

    public String getWorkaround() {
        return workaround;
    }

    public String getScope() {
        return scope;
    }

    public Long getProjectId() {
        return projectId;
    }

    public UUID getProjectPublicId() {
        return projectPublicId;
    }

    public String getProjectName() {
        return projectName;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public UUID getDepartmentPublicId() {
        return departmentPublicId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public String getDepartmentStatus() {
        return departmentStatus;
    }
}
