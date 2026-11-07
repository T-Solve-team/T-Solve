package vn.tsolve.ticket.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.tsolve.ticket.entity.ReviewDecisionRecord;

public interface ReviewDecisionRecordRepository extends JpaRepository<ReviewDecisionRecord, UUID> {}
