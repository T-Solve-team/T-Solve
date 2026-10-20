package vn.tsolve.ticket.repository;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.tsolve.ticket.dto.ReviewSource;
import vn.tsolve.ticket.dto.ReviewStatus;
import vn.tsolve.ticket.entity.ReviewTicket;

public interface ReviewTicketRepository extends JpaRepository<ReviewTicket, Long> {
    Page<ReviewTicket> findByProjectIdInAndStatusAndSource(
            Collection<Long> projects, ReviewStatus status, ReviewSource source, Pageable pageable);

    @Query("select t.id from ReviewTicket t where t.projectId in :projects and t.status = :status "
            + "and t.source = :source order by t.id")
    List<Long> findQueueIds(
            @Param("projects") Collection<Long> projects,
            @Param("status") ReviewStatus status,
            @Param("source") ReviewSource source);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ReviewTicket t where t.id = :id")
    Optional<ReviewTicket> lockById(@Param("id") long id);
}
