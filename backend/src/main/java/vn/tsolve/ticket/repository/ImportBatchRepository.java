package vn.tsolve.ticket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.tsolve.ticket.entity.ImportBatch;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {}
