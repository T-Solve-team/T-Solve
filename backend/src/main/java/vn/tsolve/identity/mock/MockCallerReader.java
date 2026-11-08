package vn.tsolve.identity.mock;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;

/** Interim mock — replace when KAN-275 workspace-session (identity Tasks 26a/26b) lands; never enable in prod. */
@Service
@Profile("identity-mock")
public class MockCallerReader {
    private final JdbcClient jdbc;

    public MockCallerReader(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<TSolveUser> read(String email) {
        return jdbc.sql("SELECT id, email, full_name FROM app_user WHERE email = :email AND status = 'ACTIVE'")
                .param("email", email.toLowerCase(Locale.ROOT))
                .query((rs, row) -> new TSolveUser(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("full_name") == null ? "" : rs.getString("full_name"),
                        EnumSet.of(Role.EMPLOYEE)))
                .optional()
                .map(user -> {
                    var roles = EnumSet.of(Role.EMPLOYEE);
                    roles.addAll(jdbc.sql("SELECT role FROM user_role WHERE user_id = :id")
                            .param("id", user.id())
                            .query((rs, row) -> Role.valueOf(rs.getString("role")))
                            .list());
                    return new TSolveUser(user.id(), user.email(), user.fullName(), roles);
                });
    }
}
