package vn.tsolve.ticket.service.impl;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.ticket.dto.ReviewNotification;
import vn.tsolve.ticket.repository.TicketDecisionRepository;
import vn.tsolve.ticket.service.ReviewNotificationService;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.service.JiraServiceAccount;

@Service
public class ReviewNotificationServiceImpl implements ReviewNotificationService {

    private final TicketDecisionRepository decisions;
    private final JiraServiceAccount jira;

    public ReviewNotificationServiceImpl(TicketDecisionRepository decisions, JiraServiceAccount jira) {
        this.decisions = decisions;
        this.jira = jira;
    }

    @Override
    public Optional<String> jiraSiteUrl() {
        // AC-010.8: only an https site gives the email a link.
        return jira.current()
                .map(JiraAccount::siteUrl)
                .map(site -> site.strip().replaceAll("/+$", ""))
                .filter(site -> site.startsWith("https://") && site.length() > "https://".length());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReviewNotification> findNotification(UUID decisionId) {
        Objects.requireNonNull(decisionId, "decisionId");
        return decisions.findWriterNotification(decisionId).flatMap(row -> kind(row.getDecision())
                .map(kind -> new ReviewNotification(
                        kind,
                        row.getEmail(),
                        row.getTicketKey(),
                        row.getFeedback(),
                        row.getTicketId(),
                        row.getTicketTitle(),
                        row.getProjectName(),
                        row.getReviewerName(),
                        row.getWriterName(),
                        row.getDecidedAt())));
    }

    private static Optional<ReviewNotification.Kind> kind(String decision) {
        return switch (decision) {
            case "REJECTED" -> Optional.of(ReviewNotification.Kind.REJECTED);
            case "CHANGES_REQUESTED" -> Optional.of(ReviewNotification.Kind.CHANGES_REQUESTED);
            default -> Optional.empty();
        };
    }
}
