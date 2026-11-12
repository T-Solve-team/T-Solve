package vn.tsolve.identity.dev;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.tsolve.common.security.Role;
import vn.tsolve.identity.account.Account;
import vn.tsolve.identity.account.AccountRepository;
import vn.tsolve.identity.account.AccountStatus;
import vn.tsolve.identity.signin.port.TenantScope;

/**
 * Adds the {@link DevAccounts} and their {@code user_role} rows to their Workspace's database, only what is missing
 * (dev profile only): an Account already there keeps its id, sub and Roles (AC-001.26).
 */
@Component
@Profile("dev")
public class DevAccountSeeder implements ApplicationRunner {

    private final TenantScope scope;
    private final AccountRepository accounts;
    private final JdbcTemplate jdbc;

    public DevAccountSeeder(TenantScope scope, AccountRepository accounts, JdbcTemplate jdbc) {
        this.scope = scope;
        this.accounts = accounts;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (DevAccounts.DevAccount dev : DevAccounts.all()) {
            scope.runIn(dev.workspaceId(), () -> {
                Account account = accounts.findByEmail(dev.email())
                        .orElseGet(() -> accounts.save(
                                new Account(dev.email(), dev.displayName(), AccountStatus.ACTIVE, Instant.now())));
                for (Role role : dev.roles()) {
                    jdbc.update(
                            "INSERT INTO user_role (user_id, role, assigned_at) VALUES (?, ?, ?)"
                                    + " ON CONFLICT (user_id, role) DO NOTHING",
                            account.getId(),
                            role.name(),
                            Timestamp.from(Instant.now()));
                }
                return null;
            });
        }
    }
}
