package vn.tsolve.ticket.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.tsolve.ticket.entity.ReviewDecisionRecord;

public interface ReviewDecisionRecordRepository extends JpaRepository<ReviewDecisionRecord, UUID> {

    /** One decision with its ticket key and the writer's email; no row when the ticket has no writer account. */
    @Query(
            value =
                    """
                    select d.decision as decision, u.email as email, t.source_ticket_id as ticketKey,
                           d.reason_or_comment as feedback
                    from review_decision d
                    join ticket t on t.ticket_id = d.ticket_id
                    join app_user u on u.id = t.writer_id
                    where d.review_decision_id = :decisionId
                    """,
            nativeQuery = true)
    Optional<WriterNotificationRow> findWriterNotification(@Param("decisionId") UUID decisionId);

    interface WriterNotificationRow {
        String getDecision();

        String getEmail();

        String getTicketKey();

        String getFeedback();
    }
}
