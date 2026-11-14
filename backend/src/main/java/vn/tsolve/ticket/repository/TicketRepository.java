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
import vn.tsolve.ticket.entity.TicketStatus;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    /** The given Ticket IDs that already exist — one query for a whole file (MSG31). */
    @Query("select t.ticketId from Ticket t where t.ticketId in :ticketIds")
    Set<String> findExistingTicketIds(@Param("ticketIds") Collection<String> ticketIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.id in :ids or t.publicId in :ids order by t.id")
    List<Ticket> lockForReview(@Param("ids") Collection<UUID> ids);

    // Ticket Project ids use project.public_id (UUID); project.project_id remains a numeric storage key.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update ticket t
            set status = 'PENDING_REVIEW',
                review_due_date = cast(:today as date) + (case when p.review_range > 0 then p.review_range else 3 end),
                updated_at = :now
            from project p
            where p.public_id = t.project_id and t.ticket_id in (:ids)
            """, nativeQuery = true)
    int sendBack(@Param("ids") Collection<UUID> ids, @Param("today") LocalDate today, @Param("now") Instant now);

    @Query("select t from Ticket t where t.id = :id or t.publicId = :id")
    Optional<Ticket> findForReviewDetail(@Param("id") UUID id);

    /**
     * Creates the Solution Form ticket of a Jira issue in Pending review (jira-app AC-004.1). writer_id and
     * review_due_date are not writable through the entity, so the row is inserted here; the due date follows the
     * send-back rule above (GBR-10), and the triggers of changesets 009 and 019 set the queue entry time and notify the
     * Review Queue. Returns no id when the Ticket ID already exists ({@code uq_ticket_ticket_id}, AC-004.3) or the
     * Project is not ACTIVE.
     */
    @Query(value = """
            insert into ticket (source_ticket_id, project_id, source, status, title, writer, writer_id,
                                symptoms, root_cause, resolution_steps, workaround, scope, review_due_date)
            select :issueKey, p.public_id, 'SOLUTION_FORM', 'PENDING_REVIEW', :title, :writer, :writerId,
                   :symptoms, :rootCause, :resolutionSteps, :workaround, :scope,
                   cast(:today as date) + (case when p.review_range > 0 then p.review_range else 3 end)
            from project p
            where p.public_id = :projectId and p.status = 'ACTIVE'
            on conflict (source_ticket_id) do nothing
            returning ticket_id
            """, nativeQuery = true)
    Optional<UUID> insertSolutionFormTicket(
            @Param("projectId") UUID projectId,
            @Param("issueKey") String issueKey,
            @Param("title") String title,
            @Param("writer") String writer,
            @Param("writerId") UUID writerId,
            @Param("symptoms") String symptoms,
            @Param("rootCause") String rootCause,
            @Param("resolutionSteps") String resolutionSteps,
            @Param("workaround") String workaround,
            @Param("scope") String scope,
            @Param("today") LocalDate today);

    /** The status of the ticket whose Ticket ID is this issue key, whatever its source (jira-app AC-001.2, AC-008.2). */
    @Query("select t.status from Ticket t where t.ticketId = :issueKey")
    Optional<TicketStatus> findStatusByTicketId(@Param("issueKey") String issueKey);

    @Query("""
            select new vn.tsolve.ticket.dto.TicketReviewView$QueueRow(
                t.id, t.ticketId, t.projectId, t.title, t.writer, t.reviewQueueEnteredAt, t.reviewDueDate)
            from Ticket t
            where t.projectId in :projects and t.status = PENDING_REVIEW and t.source = SOLUTION_FORM
            """)
    List<QueueRow> findReviewQueue(@Param("projects") Collection<UUID> projectIds);

    @Query("""
            select new vn.tsolve.ticket.dto.TicketReviewView$ExpiredRow(
                t.id, t.ticketId, t.projectId, t.title, t.writer, t.reviewQueueEnteredAt, t.reviewDueDate, t.updatedAt,
                t.expiredAt, t.statusBeforeExpiry)
            from Ticket t
            where t.projectId in :projects and t.status = EXPIRED and t.source = SOLUTION_FORM
            """)
    List<ExpiredRow> findExpiredTickets(@Param("projects") Collection<UUID> projectIds);

    @Query("""
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
