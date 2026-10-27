package vn.tsolve.tracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.TenantId;
import vn.tsolve.common.error.BusinessRuleException;

@Entity
@Table(name = "issue_event")
public class IssueEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Set by Hibernate from WorkspaceContext on insert (AC-003.7).
    @TenantId
    @Column(name = "workspace_id", nullable = false, updatable = false)
    private Long workspaceId;

    @Column(name = "cloud_id", nullable = false)
    private String cloudId;

    @Column(name = "issue_id", nullable = false)
    private String issueId;

    @Column(name = "issue_key", nullable = false)
    private String issueKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private IssueEventType eventType;

    @Column(name = "event_timestamp", nullable = false)
    private Long eventTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueEventStatus status;

    @Column(nullable = false)
    private short attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "token_ciphertext")
    private byte[] tokenCiphertext;

    @Column(name = "comment_posted", nullable = false)
    private boolean commentPosted;

    @Column(name = "error_type")
    private String errorType;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected IssueEvent() {}

    public IssueEvent(
            String cloudId,
            String issueId,
            String issueKey,
            IssueEventType eventType,
            Long eventTimestamp,
            byte[] tokenCiphertext,
            Instant now) {
        this.cloudId = cloudId;
        this.issueId = issueId;
        this.issueKey = issueKey;
        this.eventType = eventType;
        this.eventTimestamp = eventTimestamp;
        this.tokenCiphertext = tokenCiphertext;
        this.status = IssueEventStatus.RECEIVED;
        this.attempts = 0;
        this.nextAttemptAt = now;
        this.receivedAt = now;
    }

    private void requireStatus(IssueEventStatus expected) {
        if (status != expected) {
            throw new BusinessRuleException("Issue event " + id + " is " + status + ", expected " + expected);
        }
    }
    // The worker takes the event so that no other worker processes it
    public void claim(Instant now) {
        requireStatus(IssueEventStatus.RECEIVED);
        this.status = IssueEventStatus.PROCESSING;
        this.claimedAt = now;
    }

    // A retryable error: back to the queue until {@code nextAt}. The worker picks the delay.
    public void scheduleRetry(Instant nextAt) {
        requireStatus(IssueEventStatus.PROCESSING);
        this.status = IssueEventStatus.RECEIVED;
        this.attempts++;
        this.nextAttemptAt = nextAt;
        this.claimedAt = null;
    }

    public void markProcessed(Instant now) {
        finish(IssueEventStatus.PROCESSED, now);
    }

    /** Nothing to do for this event: project not in T-Solve, or not Done (AC-004.3, AC-004.4). */
    public void markIgnored(Instant now) {
        finish(IssueEventStatus.IGNORED, now);
    }

    /** {@code errorType} is a type such as {@code TOKEN_EXPIRED}, never error content (AC-004.5–AC-004.7). */
    public void markFailed(String errorType, Instant now) {
        finish(IssueEventStatus.FAILED, now);
        this.errorType = errorType;
    }

    /** MSG80 is on the Jira issue; a re-run of this event must not post it again (AC-005.4). */
    public void markCommentPosted() {
        this.commentPosted = true;
    }

    // Every way out of PROCESSING deletes the Jira token (AC-004.8).
    private void finish(IssueEventStatus finalStatus, Instant now) {
        requireStatus(IssueEventStatus.PROCESSING);
        this.status = finalStatus;
        this.finishedAt = now;
        this.tokenCiphertext = null;
    }

    public Long getId() {
        return id;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public String getCloudId() {
        return cloudId;
    }

    public String getIssueId() {
        return issueId;
    }

    public String getIssueKey() {
        return issueKey;
    }

    public IssueEventType getEventType() {
        return eventType;
    }

    public Long getEventTimestamp() {
        return eventTimestamp;
    }

    public IssueEventStatus getStatus() {
        return status;
    }

    public short getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public byte[] getTokenCiphertext() {
        return tokenCiphertext;
    }

    public boolean isCommentPosted() {
        return commentPosted;
    }

    public String getErrorType() {
        return errorType;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
