package vn.tsolve.analytics.dto;

import java.time.Instant;
import java.util.UUID;
import vn.tsolve.common.audit.AuditAction;

public record AuditEntryResponse(
        UUID id,
        Instant createdAt,
        String actor,
        AuditAction action,
        String resourceType,
        UUID resourceId,
        String summary) {}
