package vn.tsolve.common.audit.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.security.TSolveUser;

@Service
public class AuditServiceImpl implements AuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditServiceImpl(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(AuditAction action, String targetType, UUID targetId) {
        record(action, targetType, targetId, null);
    }

    @Override
    public void record(AuditAction action, String targetType, UUID targetId, Map<String, Object> details) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Audit recording requires a transaction");
        }
        TSolveUser user = TSolveUser.current()
                .orElseThrow(() -> new IllegalStateException("Audit recording requires a signed-in user"));
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(targetId, "targetId");
        String json;
        try {
            json = details == null ? null : objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException invalid) {
            throw new IllegalArgumentException("Audit details must be JSON serializable", invalid);
        }
        jdbc.update(
                """
                INSERT INTO audit_log_entry
                    (actor_id, actor_email, action, target_type, target_id, details, created_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                """,
                user.id(),
                user.email(),
                action.name(),
                targetType,
                targetId,
                json,
                Timestamp.from(Instant.now()));
    }
}
