package vn.tsolve.review.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.hibernate.annotations.SecondaryRow;
import org.springframework.http.HttpStatus;
import vn.tsolve.review.dto.ReviewAction;
import vn.tsolve.review.dto.ReviewRegistration;
import vn.tsolve.review.dto.ReviewRegistration.EvidenceReference;
import vn.tsolve.review.dto.ReviewSource;
import vn.tsolve.review.dto.ReviewStatus;
import vn.tsolve.review.dto.SolutionSections;
import vn.tsolve.review.exception.ReviewException;

@Entity
@Table(name = "ticket_review")
@SecondaryTable(name = "ticket_review_solution", pkJoinColumns = @PrimaryKeyJoinColumn(name = "ticket_id"))
@SecondaryRow(table = "ticket_review_solution", optional = false)
public class ReviewTicket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(name = "source_ticket_id", nullable = false)
    private String sourceTicketId;

    @Column(name = "project_id", nullable = false)
    private long projectId;

    @Column(name = "project_name", nullable = false)
    private String projectName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewSource source;

    @Column(name = "writer_id", nullable = false)
    private String writerId;

    @Column(name = "writer_name", nullable = false)
    private String authorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus status = ReviewStatus.PENDING_REVIEW;

    @Column(name = "entered_queue_at", nullable = false)
    private Instant enteredQueueAt;

    @Column(name = "review_due_at", nullable = false)
    private Instant reviewDueDate;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "review_comment")
    private String reviewComment;

    @Version
    private long version;

    @Column(table = "ticket_review_solution", columnDefinition = "text")
    private String symptoms;

    @Column(table = "ticket_review_solution", name = "root_cause", columnDefinition = "text")
    private String rootCause;

    @Column(table = "ticket_review_solution", name = "resolution_steps", columnDefinition = "text")
    private String resolutionSteps;

    @Column(table = "ticket_review_solution", columnDefinition = "text")
    private String workaround;

    @Column(table = "ticket_review_solution", name = "scope_of_applicability", columnDefinition = "text")
    private String scopeOfApplicability;

    @ElementCollection
    @CollectionTable(name = "ticket_review_evidence", joinColumns = @JoinColumn(name = "ticket_id"))
    private List<ReviewEvidence> evidence = new ArrayList<>();

    protected ReviewTicket() {}

    public ReviewTicket(ReviewRegistration request) {
        sourceTicketId = required(request.sourceTicketId());
        title = required(request.title());
        if (request.projectId() <= 0) throw new IllegalArgumentException("A Project id must be positive");
        projectId = request.projectId();
        projectName = required(request.projectName());
        source = Objects.requireNonNull(request.source());
        writerId = required(request.writerId());
        authorName = required(request.writerName());
        enteredQueueAt = Objects.requireNonNull(request.enteredQueueAt());
        reviewDueDate = Objects.requireNonNull(request.reviewDueDate());
        if (reviewDueDate.isBefore(enteredQueueAt)) throw new IllegalArgumentException("Invalid review dates");
        updatedAt = enteredQueueAt;
        SolutionSections solution = Objects.requireNonNull(request.solution());
        symptoms = solution.symptoms();
        rootCause = solution.rootCause();
        resolutionSteps = solution.resolutionSteps();
        workaround = solution.workaround();
        scopeOfApplicability = solution.scopeOfApplicability();
        if (request.evidence().size() > 5) throw new IllegalArgumentException("At most five Evidence references");
        request.evidence().forEach(reference -> evidence.add(new ReviewEvidence(reference)));
        // Imported tickets bypass review under GBR-23. Only the submission integration calls this constructor.
        if (source != ReviewSource.SOLUTION_FORM) status = ReviewStatus.PUBLISHED;
    }

    public void decide(ReviewAction action, String comment, Instant now) {
        validateDecision(action, comment);
        status = switch (action) {
            case APPROVE -> ReviewStatus.PUBLISHED;
            case REJECT -> ReviewStatus.REJECTED;
            case REQUEST_CHANGES -> ReviewStatus.CHANGES_REQUESTED;};
        if (action == ReviewAction.REQUEST_CHANGES) reviewComment = comment;
        updatedAt = Objects.requireNonNull(now);
    }

    public void validateDecision(ReviewAction action, String comment) {
        Objects.requireNonNull(action);
        if (status != ReviewStatus.PENDING_REVIEW) throw new ReviewException(HttpStatus.CONFLICT, "MSG06");
        if (action == ReviewAction.REQUEST_CHANGES && source != ReviewSource.SOLUTION_FORM) {
            throw new ReviewException(HttpStatus.CONFLICT, "MSG18");
        }
        if (action != ReviewAction.APPROVE) {
            String field = action == ReviewAction.REJECT ? "reason" : "comment";
            String label = action == ReviewAction.REJECT ? "Reason" : "Comment";
            if (comment == null || comment.isBlank()) {
                throw new ReviewException(HttpStatus.BAD_REQUEST, "MSG01", field, Map.of("field_name", label));
            }
            if (comment.length() > 1000) {
                throw new ReviewException(
                        HttpStatus.BAD_REQUEST, "MSG02", field, Map.of("field_name", label, "max_length", 1000));
            }
        }
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Submission metadata is required");
        return value;
    }

    public Long getId() {
        return id;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public String getSourceTicketId() {
        return sourceTicketId;
    }

    public String getTitle() {
        return title;
    }

    public long getProjectId() {
        return projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public ReviewSource getSource() {
        return source;
    }

    public String getWriterId() {
        return writerId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public Instant getEnteredQueueAt() {
        return enteredQueueAt;
    }

    public Instant getReviewDueDate() {
        return reviewDueDate;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public SolutionSections getSolution() {
        return new SolutionSections(symptoms, rootCause, resolutionSteps, workaround, scopeOfApplicability);
    }

    public List<EvidenceReference> getEvidence() {
        return evidence.stream().map(ReviewEvidence::toReference).toList();
    }
}
