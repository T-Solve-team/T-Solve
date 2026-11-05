package vn.tsolve.identity.service.impl;

import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.identity.exception.UserListException;

/** Uses platform's primary transaction resources; it never selects a database or sets a tenant. */
@Component
@ConditionalOnProperty(name = "tsolve.identity.user-list.enabled", havingValue = "true")
public class UserListTransaction {
    private final JdbcTemplate jdbc;
    private final TenantContext context;
    private final TransactionTemplate transaction;

    public UserListTransaction(JdbcTemplate jdbc, TenantContext context, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.context = context;
        transaction = new TransactionTemplate(manager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T read(Supplier<T> query) {
        var workspace = context.currentWorkspaceId().orElseThrow(UserListTransaction::unavailable);
        try {
            return transaction.execute(status -> {
                try {
                    Boolean valid = jdbc.queryForObject(
                            "SELECT tenant_guard_ok() AND NULLIF(current_setting('app.tenant_id', true), '')::uuid = ?"
                                    + " AND (SELECT count(*) = 1 AND bool_and(tenant_id = ?) FROM tenant_identity)",
                            Boolean.class,
                            workspace,
                            workspace);
                    if (!Boolean.TRUE.equals(valid)) throw unavailable();
                } catch (DataAccessException failure) {
                    // Missing platform schema or guard never permits reads from the legacy database.
                    throw unavailable();
                }
                try {
                    return query.get();
                } catch (DataAccessException failure) {
                    throw new UserListException(HttpStatus.INTERNAL_SERVER_ERROR, "MSG06");
                }
            });
        } catch (TransactionException | DataAccessException failure) {
            throw unavailable();
        }
    }

    private static UserListException unavailable() {
        return new UserListException(HttpStatus.SERVICE_UNAVAILABLE, "MSG06");
    }
}
