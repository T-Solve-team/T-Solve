package vn.tsolve.ticket.repository;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.tsolve.ticket.dto.TicketReviewView.QueueRow;
import vn.tsolve.ticket.entity.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    /** The given Ticket IDs that already exist — one query for a whole file (MSG31). */
    @Query("select t.ticketId from Ticket t where t.ticketId in :ticketIds")
    Set<String> findExistingTicketIds(@Param("ticketIds") Collection<String> ticketIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.id in :ids order by t.id")
    List<Ticket> lockForReview(@Param("ids") Collection<UUID> ids);

    @Query(
            """
            select new vn.tsolve.ticket.dto.TicketReviewView$QueueRow(
                t.id, t.ticketId, t.projectId, t.title, t.writer, t.reviewQueueEnteredAt, t.reviewDueDate)
            from Ticket t
            where t.projectId in :projects and t.status = PENDING_REVIEW and t.source = SOLUTION_FORM
            """)
    List<QueueRow> findReviewQueue(@Param("projects") Collection<Long> projectIds);
}
