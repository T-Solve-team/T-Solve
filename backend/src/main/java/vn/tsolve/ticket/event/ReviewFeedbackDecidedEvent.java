package vn.tsolve.ticket.event;

import java.util.List;
import java.util.UUID;

/**
 * Reject or Request Changes decisions were saved (review_decision.review_decision_id values), so the writer can be
 * emailed. It is published inside the decision transaction: listen with
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} so a rollback sends nothing.
 */
public record ReviewFeedbackDecidedEvent(List<UUID> decisionIds) {

    public ReviewFeedbackDecidedEvent {
        decisionIds = List.copyOf(decisionIds);
    }
}
