package vn.tsolve.ticket.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.tsolve.ticket.dto.TicketReviewView.ExpiredRow;
import vn.tsolve.ticket.dto.TicketReviewView.Feedback;
import vn.tsolve.ticket.dto.TicketReviewView.QueueRow;
import vn.tsolve.ticket.entity.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    /** The given Ticket IDs that already exist — one query for a whole file (MSG31). */
    @Query("select t.ticketId from Ticket t where t.ticketId in :ticketIds")
    Set<String> findExistingTicketIds(@Param("ticketIds") Collection<String> ticketIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.id in :ids or t.publicId in :ids order by t.id")
    List<Ticket> lockForReview(@Param("ids") Collection<UUID> ids);

    // Ticket Project ids use project.public_id (UUID); project.project_id remains a numeric storage key.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    """
            update ticket t
            set status = 'PENDING_REVIEW',
                review_due_date = cast(:today as date) + (case when p.review_range > 0 then p.review_range else 3 end),
                updated_at = :now
            from project p
            where p.public_id = t.project_id and t.ticket_id in (:ids)
            """,
            nativeQuery = true)
    int sendBack(@Param("ids") Collection<UUID> ids, @Param("today") LocalDate today, @Param("now") Instant now);

    @Query("select t from Ticket t where t.id = :id or t.publicId = :id")
    Optional<Ticket> findForReviewDetail(@Param("id") UUID id);

    @Query(
            """
            select new vn.tsolve.ticket.dto.TicketReviewView$QueueRow(
                t.id, t.ticketId, t.projectId, t.title, t.writer, t.reviewQueueEnteredAt, t.reviewDueDate)
            from Ticket t
            where t.projectId in :projects and t.status = PENDING_REVIEW and t.source = SOLUTION_FORM
            """)
    List<QueueRow> findReviewQueue(@Param("projects") Collection<UUID> projectIds);

    @Query(
            """
            select new vn.tsolve.ticket.dto.TicketReviewView$ExpiredRow(
                t.id, t.ticketId, t.projectId, t.title, t.writer, t.reviewQueueEnteredAt, t.reviewDueDate, t.updatedAt)
            from Ticket t
            where t.projectId in :projects and t.status = EXPIRED and t.source = SOLUTION_FORM
            """)
    List<ExpiredRow> findExpiredTickets(@Param("projects") Collection<UUID> projectIds);

    @Query(
            """
            select new vn.tsolve.ticket.dto.TicketReviewView$Feedback(d.reviewerId, d.feedback)
            from Ticket t, ReviewDecisionRecord d
            where t.id = d.ticketId and t.projectId = :projectId and t.writerId = :writerId
                and t.ticketId = :issueKey and t.source = SOLUTION_FORM and t.status = CHANGES_REQUESTED
                and d.decision = 'CHANGES_REQUESTED'
            order by d.decidedAt desc, d.id desc
            """)
    List<Feedback> findJiraFeedback(
            @Param("projectId") UUID projectId,
            @Param("writerId") UUID writerId,
            @Param("issueKey") String issueKey,
            Pageable page);
}
