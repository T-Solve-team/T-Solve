package vn.tsolve.ticket.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.tsolve.ticket.entity.ReviewDecisionRecord;

public interface ReviewDecisionRecordRepository extends JpaRepository<ReviewDecisionRecord, UUID> {

    String HISTORY_FROM =
            """
            from review_decision d
            join ticket t on t.ticket_id = d.ticket_id
            join app_user u on u.id = d.project_manager_id
            where t.project_id in (:projectIds)
              and d.decision in (:decisions)
              and d.decided_at >= :fromInclusive
              and d.decided_at < :toExclusive
            """;

    String HISTORY_SELECT =
            """
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
}
