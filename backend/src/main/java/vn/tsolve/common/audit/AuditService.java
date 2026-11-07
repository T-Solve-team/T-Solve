package vn.tsolve.common.audit;

import java.util.Map;
import java.util.UUID;

public interface AuditService {
    void record(AuditAction action, String targetType, UUID targetId);

    void record(AuditAction action, String targetType, UUID targetId, Map<String, Object> details);
}
