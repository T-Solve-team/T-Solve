package vn.tsolve.analytics.service;

import java.util.List;
import org.springframework.data.domain.Page;
import vn.tsolve.analytics.dto.AuditEntryResponse;

public interface AuditLogService {
    Page<AuditEntryResponse> findEntries(
            String actor, String action, String from, String to, int page, int size, List<String> sort);
}
