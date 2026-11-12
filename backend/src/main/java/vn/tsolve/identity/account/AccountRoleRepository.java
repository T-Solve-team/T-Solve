package vn.tsolve.identity.account;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.tsolve.common.security.Role;

/**
 * Reads {@code user_role} by {@code user_id} (AC-001.26). It takes the {@link JdbcTemplate} of one company database,
 * so the caller decides which Workspace's database is read (inside {@code TenantScope.runIn}). It is a bean, built in
 * {@code SessionConfig} on the JdbcTemplate, and {@code WebSessionFilter} reads the Roles through it at each request.
 */
public class AccountRoleRepository {

    private final JdbcTemplate jdbc;

    public AccountRoleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AccountRole> findByUserId(UUID userId) {
        return jdbc.query(
                "SELECT id, user_id, role, assigned_at FROM user_role WHERE user_id = ?",
                (rs, rowNum) -> new AccountRole(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        Role.valueOf(rs.getString("role")),
                        rs.getObject("assigned_at", Timestamp.class).toInstant()),
                userId);
    }
}
