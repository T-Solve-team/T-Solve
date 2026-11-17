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
import vn.tsolve.ticket.dto.SolutionSections;
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
            where p.public_id = t.project_id and t.ticket_id in (:ids) and t.status = 'EXPIRED'
            """, nativeQuery = true)
    int sendBack(@Param("ids") Collection<UUID> ids, @Param("today") LocalDate today, @Param("now") Instant now);

    @Query("select t from Ticket t where t.id = :id or t.publicId = :id")
    Optional<Ticket> findForReviewDetail(@Param("id") UUID id);

    interface Placement {
        UUID getProjectPublicId();

        String getProjectKey();

        UUID getDepartmentId();

        String getDepartmentName();

        boolean getDepartmentActive();
    }

    @Query(value = """
            select p.public_id as "projectPublicId", p.jira_project_key as "projectKey",
                   d.public_id as "departmentId", d.name as "departmentName",
                   d.status = 'ACTIVE' as "departmentActive"
            from project p join department d on d.department_id = p.department_id
            where p.public_id = :projectId or lpad(to_hex(p.project_id), 32, '0')::uuid = :projectId
            """, nativeQuery = true)
    Optional<Placement> findPlacement(@Param("projectId") UUID projectId);

    /** Whether the account is PROJECT_MANAGER of the Project in project_membership, whatever its status (AC-008.2). */
    @Query(value = """
            select exists (select 1 from project_membership m
                           where m.project_id = :projectId and m.user_id = :accountId
                             and m.role_in_project = 'PROJECT_MANAGER')
            """, nativeQuery = true)
    boolean isProjectManager(@Param("projectId") UUID projectId, @Param("accountId") UUID accountId);

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

    /**
     * The Solution of the writer's Solution Form ticket of this issue while it is in Changes requested — the ticket the
     * writer may resubmit (jira-app AC-012.2).
     */
    @Query("""
            select new vn.tsolve.ticket.dto.SolutionSections(
                t.symptoms, t.rootCause, t.resolutionSteps, t.workaround, t.scope)
            from Ticket t
            where t.ticketId = :issueKey
              and t.source = vn.tsolve.ticket.entity.TicketSource.SOLUTION_FORM
              and t.writerId = :writerId
              and t.status = vn.tsolve.ticket.entity.TicketStatus.CHANGES_REQUESTED
            """)
    Optional<SolutionSections> findResubmittableSections(
            @Param("issueKey") String issueKey, @Param("writerId") UUID writerId);

    /**
     * Puts the writer's Solution Form ticket in Changes requested back into Pending review with the new Solution and a
     * new due date counted like the insert above (jira-app AC-012.4, GBR-10); the trigger of changeset 009 sets the new
     * queue entry time because the status changes. Returns no id when the ticket is no longer resubmittable or its
     * Project is not ACTIVE, and then changes nothing (AC-012.5). It clears the change request comment, as
     * {@code Ticket.recordReviewFeedback} does for any outcome but Changes requested.
     *
     * <p>Deliberate exception: this does not go through {@code TicketTransitions}. The status guard, the Project check
     * and the write must be one atomic UPDATE, so that two resubmits (or a resubmit and an expiry) cannot both win;
     * the one move it makes, Changes requested to Pending review, is allowed by {@code TicketStatus}.
     */
    @Query(value = """
            update ticket t
               set symptoms = :symptoms, root_cause = :rootCause, resolution_steps = :resolutionSteps,
                   workaround = :workaround, scope = :scope, status = 'PENDING_REVIEW',
                   change_request_comment = null,
                   review_due_date = cast(:today as date)
                       + (case when p.review_range > 0 then p.review_range else 3 end),
                   updated_at = now()
              from project p
             where p.public_id = t.project_id and p.status = 'ACTIVE'
               and t.source_ticket_id = :issueKey and t.source = 'SOLUTION_FORM'
               and t.writer_id = :writerId and t.status = 'CHANGES_REQUESTED'
            returning t.ticket_id
            """, nativeQuery = true)
    Optional<UUID> resubmitSolutionFormTicket(
            @Param("issueKey") String issueKey,
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
            from Ticket t, TicketDecision d
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

    /** The Reject decisions of the writer's Rejected Solution Form ticket of this issue, latest first (AC-009.13). */
    @Query("""
            select new vn.tsolve.ticket.dto.TicketReviewView$Feedback(d.reviewerId, d.feedback)
            from Ticket t, TicketDecision d
            where t.id = d.ticketId and t.projectId = :projectId and t.writerId = :writerId
                and t.ticketId = :issueKey and t.source = SOLUTION_FORM and t.status = REJECTED
                and d.decision = 'REJECTED'
            order by d.decidedAt desc, d.id desc
            """)
    List<Feedback> findJiraRejection(
            @Param("projectId") UUID projectId,
            @Param("writerId") UUID writerId,
            @Param("issueKey") String issueKey,
            Pageable page);
}
