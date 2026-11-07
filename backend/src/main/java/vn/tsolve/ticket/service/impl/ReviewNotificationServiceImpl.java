package vn.tsolve.ticket.service.impl;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.ticket.dto.ReviewNotification;
import vn.tsolve.ticket.repository.ReviewDecisionRecordRepository;
import vn.tsolve.ticket.service.ReviewNotificationService;

@Service
public class ReviewNotificationServiceImpl implements ReviewNotificationService {

    private final ReviewDecisionRecordRepository decisions;

    public ReviewNotificationServiceImpl(ReviewDecisionRecordRepository decisions) {
        this.decisions = decisions;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReviewNotification> findNotification(UUID decisionId) {
        Objects.requireNonNull(decisionId, "decisionId");
        return decisions.findWriterNotification(decisionId).flatMap(row -> kind(row.getDecision())
                .map(kind -> new ReviewNotification(kind, row.getEmail(), row.getTicketKey(), row.getFeedback())));
    }

    private static Optional<ReviewNotification.Kind> kind(String decision) {
        return switch (decision) {
            case "REJECTED" -> Optional.of(ReviewNotification.Kind.REJECTED);
            case "CHANGES_REQUESTED" -> Optional.of(ReviewNotification.Kind.CHANGES_REQUESTED);
            default -> Optional.empty();
        };
    }
}
