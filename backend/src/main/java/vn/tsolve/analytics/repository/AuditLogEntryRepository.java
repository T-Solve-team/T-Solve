package vn.tsolve.analytics.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import vn.tsolve.analytics.entity.AuditLogEntry;

public interface AuditLogEntryRepository
        extends JpaRepository<AuditLogEntry, UUID>, JpaSpecificationExecutor<AuditLogEntry> {}
