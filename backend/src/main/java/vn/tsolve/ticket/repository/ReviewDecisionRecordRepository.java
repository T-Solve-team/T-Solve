package vn.tsolve.ticket.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.tsolve.ticket.entity.ReviewDecisionRecord;

public interface ReviewDecisionRecordRepository extends JpaRepository<ReviewDecisionRecord, UUID> {

    String HISTORY_FROM = """
            from review_decision d
            join ticket t on t.ticket_id = d.ticket_id
            join app_user u on u.id = d.project_manager_id
            where t.project_id in (:projectIds)
              and d.decision in (:decisions)
              and d.decided_at >= :fromInclusive
              and d.decided_at < :toExclusive
            """;

    String HISTORY_SELECT = """
            select d.review_decision_id as decisionId, t.ticket_id as ticketId, t.source_ticket_id as sourceTicketId,
                   t.title as title, t.project_id as projectId, d.decision as decision,
                   d.project_manager_id as actorId, coalesce(u.full_name, u.email) as actorName,
                   t.writer as authorName, d.decided_at as decidedAt, d.reason_or_comment as feedback
            """;

    /** Review History, newest first; the decision id breaks ties so pages never overlap (STORY-011). */
    @Query(
            value = HISTORY_SELECT + HISTORY_FROM + " order by d.decided_at desc, d.review_decision_id desc",
            countQuery = "select count(*) " + HISTORY_FROM,
            nativeQuery = true)
    Page<HistoryRow> findHistoryNewestFirst(
            @Param("projectIds") Collection<UUID> projectIds,
            @Param("decisions") Collection<String> decisions,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive,
            Pageable page);

    @Query(
            value = HISTORY_SELECT + HISTORY_FROM + " order by d.decided_at asc, d.review_decision_id asc",
            countQuery = "select count(*) " + HISTORY_FROM,
            nativeQuery = true)
    Page<HistoryRow> findHistoryOldestFirst(
            @Param("projectIds") Collection<UUID> projectIds,
            @Param("decisions") Collection<String> decisions,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive,
            Pageable page);

    interface HistoryRow {
        UUID getDecisionId();

        UUID getTicketId();

        String getSourceTicketId();

        String getTitle();

        UUID getProjectId();

        String getDecision();

        UUID getActorId();

        String getActorName();

        String getAuthorName();

        Instant getDecidedAt();

        String getFeedback();
    }

    /** Review email facts; no row when the ticket has no writer account. */
    @Query(value = """
                    select d.decision as decision, u.email as email, t.source_ticket_id as ticketKey,
                           d.reason_or_comment as feedback, t.ticket_id as ticketId, t.title as ticketTitle,
                           p.jira_project_key as projectName, coalesce(r.full_name, r.email) as reviewerName,
                           u.full_name as writerName, d.decided_at as decidedAt
                    from review_decision d
                    join ticket t on t.ticket_id = d.ticket_id
                    join app_user u on u.id = t.writer_id
                    left join project p on (p.public_id = t.project_id
                        or lpad(to_hex(p.project_id), 32, '0')::uuid = t.project_id)
                    left join app_user r on r.id = d.project_manager_id
                    where d.review_decision_id = :decisionId
                    """, nativeQuery = true)
    Optional<WriterNotificationRow> findWriterNotification(@Param("decisionId") UUID decisionId);

    interface WriterNotificationRow {
        UUID getTicketId();

        String getTicketTitle();

        String getProjectName();

        String getReviewerName();

        String getWriterName();

        Instant getDecidedAt();

        String getDecision();

        String getEmail();

        String getTicketKey();

        String getFeedback();
    }
}
