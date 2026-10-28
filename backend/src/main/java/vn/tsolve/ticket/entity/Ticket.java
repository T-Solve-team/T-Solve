package vn.tsolve.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** A resolved issue with its Solution (GBR-33). projectId points at the workspace module's Project. */
@Entity
@Table(name = "ticket")
public class Ticket {

    // allocationSize matches INCREMENT BY 50 in 002 so inserts can be batched
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ticket_seq")
    @SequenceGenerator(name = "ticket_seq", sequenceName = "ticket_id_seq", allocationSize = 50)
    private Long id;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private String ticketId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TicketSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    private String writer;

    @Column(name = "resolved_date")
    private LocalDate resolvedDate;

    @Column(nullable = false, columnDefinition = "text")
    private String symptoms;

    @Column(name = "root_cause", nullable = false, columnDefinition = "text")
    private String rootCause;

    @Column(name = "resolution_steps", nullable = false, columnDefinition = "text")
    private String resolutionSteps;

    @Column(columnDefinition = "text")
    private String workaround;

    @Column(nullable = false, columnDefinition = "text")
    private String scope;

    @Column(name = "import_batch_id", updatable = false)
    private Long importBatchId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Ticket() {}

    /** An imported ticket: brought in by its Project Manager, so it is Published at once (GBR-23). */
    public static Ticket imported(
            String ticketId,
            Long projectId,
            TicketSource source,
            Long importBatchId,
            String title,
            String description,
            String writer,
            LocalDate resolvedDate,
            Solution solution) {
        Ticket t = new Ticket();
        t.ticketId = ticketId;
        t.projectId = projectId;
        t.source = source;
        t.status = TicketStatus.PUBLISHED;
        t.importBatchId = importBatchId;
        t.title = title;
        t.description = description;
        t.writer = writer;
        t.resolvedDate = resolvedDate;
        t.symptoms = solution.symptoms();
        t.rootCause = solution.rootCause();
        t.resolutionSteps = solution.resolutionSteps();
        t.workaround = solution.workaround();
        t.scope = solution.scope();
        return t;
    }

    /** The five Solution sections (GBR-33); Workaround is the only optional one. */
    public record Solution(
            String symptoms, String rootCause, String resolutionSteps, String workaround, String scope) {}

    public Long getId() {
        return id;
    }

    public String getTicketId() {
        return ticketId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public TicketSource getSource() {
        return source;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getWriter() {
        return writer;
    }

    public LocalDate getResolvedDate() {
        return resolvedDate;
    }

    public Solution getSolution() {
        return new Solution(symptoms, rootCause, resolutionSteps, workaround, scope);
    }

    public Long getImportBatchId() {
        return importBatchId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
