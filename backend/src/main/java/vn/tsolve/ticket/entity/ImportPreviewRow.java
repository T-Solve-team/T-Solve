package vn.tsolve.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.tsolve.ticket.csv.RowError;

/** One data row of an uploaded file, with its raw values as read; length limits apply only to Ticket. */
@Entity
@Table(name = "import_preview_row")
public class ImportPreviewRow {

    @Embeddable
    public record Id(@Column(name = "preview_id") Long previewId, @Column(name = "row_no") int rowNo) {}

    @EmbeddedId
    private Id id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PreviewRowStatus status;

    @Column(name = "ticket_id", updatable = false, columnDefinition = "text")
    private String ticketId;

    @Column(updatable = false, columnDefinition = "text")
    private String title;

    @Column(updatable = false, columnDefinition = "text")
    private String description;

    @Column(updatable = false, columnDefinition = "text")
    private String author;

    @Column(name = "resolved_date", updatable = false, columnDefinition = "text")
    private String resolvedDate;

    @Column(updatable = false, columnDefinition = "text")
    private String symptoms;

    @Column(name = "root_cause", updatable = false, columnDefinition = "text")
    private String rootCause;

    @Column(name = "resolution_steps", updatable = false, columnDefinition = "text")
    private String resolutionSteps;

    @Column(updatable = false, columnDefinition = "text")
    private String workaround;

    @Column(updatable = false, columnDefinition = "text")
    private String scope;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private List<RowError> errors;

    protected ImportPreviewRow() {}

    public ImportPreviewRow(Long previewId, int rowNo, Values values, List<RowError> errors) {
        this.id = new Id(previewId, rowNo);
        this.status = errors.isEmpty() ? PreviewRowStatus.VALID : PreviewRowStatus.ERROR;
        this.ticketId = values.ticketId();
        this.title = values.title();
        this.description = values.description();
        this.author = values.author();
        this.resolvedDate = values.resolvedDate();
        this.symptoms = values.symptoms();
        this.rootCause = values.rootCause();
        this.resolutionSteps = values.resolutionSteps();
        this.workaround = values.workaround();
        this.scope = values.scope();
        this.errors = List.copyOf(errors);
    }

    /** The template columns of one row, as written in the file (null when the cell is missing). */
    public record Values(
            String ticketId,
            String title,
            String description,
            String author,
            String resolvedDate,
            String symptoms,
            String rootCause,
            String resolutionSteps,
            String workaround,
            String scope) {}

    /** Removing applies to Valid and Error rows alike (AC-003.1). */
    public void remove() {
        status = PreviewRowStatus.REMOVED;
    }

    public boolean isValid() {
        return status == PreviewRowStatus.VALID;
    }

    public Id getId() {
        return id;
    }

    public int getRowNo() {
        return id.rowNo();
    }

    public PreviewRowStatus getStatus() {
        return status;
    }

    public Values getValues() {
        return new Values(
                ticketId,
                title,
                description,
                author,
                resolvedDate,
                symptoms,
                rootCause,
                resolutionSteps,
                workaround,
                scope);
    }

    public List<RowError> getErrors() {
        return errors;
    }
}
