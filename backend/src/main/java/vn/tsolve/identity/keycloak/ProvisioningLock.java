package vn.tsolve.identity.keycloak;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import vn.tsolve.identity.signin.port.ProvisioningWiring;

/**
 * Runs one provisioning operation at a time across all processes, with a PostgreSQL transaction advisory lock. The lock
 * ends with the transaction, so no failure of a release step can leave it held on a pooled connection; such a failure
 * aborts the connection and never replaces the result or the exception of the action.
 */
@Component
@Conditional(ProvisioningWiring.Configured.class)
public class ProvisioningLock {

    private static final Logger LOG = LoggerFactory.getLogger(ProvisioningLock.class);

    /** Any fixed number; only this class takes it. */
    private static final long LOCK_KEY = 0x54534F4C56454B43L;

    /** The longest a call waits for another one; longer than one provisioning run takes. */
    private static final String LOCK_TIMEOUT = "120s";

    private final DataSource dataSource;

    public ProvisioningLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public <T> T runLocked(Supplier<T> action) {
        Connection connection = take();
        T result;
        try {
            result = action.get();
        } catch (RuntimeException | Error failure) {
            release(connection, false, failure);
            throw failure;
        }
        release(connection, true, null);
        return result;
    }

    private Connection take() {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            try (Statement timeout = connection.createStatement()) {
                timeout.execute("SET LOCAL lock_timeout = '" + LOCK_TIMEOUT + "'");
            }
            try (PreparedStatement lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                lock.setLong(1, LOCK_KEY);
                lock.execute();
            }
            return connection;
        } catch (SQLException e) {
            if (connection != null) {
                abortQuietly(connection);
                closeQuietly(connection);
            }
            throw new IllegalStateException("Could not take the provisioning lock (SQLState " + e.getSQLState() + ")");
        }
    }

    /** Ends the transaction, which releases the lock. A failure here is attached to {@code failure}, or logged. */
    private static void release(Connection connection, boolean commit, Throwable failure) {
        try {
            if (commit) {
                connection.commit();
            } else {
                connection.rollback();
            }
        } catch (SQLException e) {
            abortQuietly(connection);
            if (failure != null) {
                failure.addSuppressed(e);
            } else {
                LOG.warn("Releasing the provisioning lock failed (SQLState {})", e.getSQLState());
            }
        } finally {
            closeQuietly(connection);
        }
    }

    private static void abortQuietly(Connection connection) {
        try {
            connection.abort(Runnable::run);
        } catch (SQLException | RuntimeException e) {
            LOG.warn("Aborting the provisioning lock connection failed ({})", e.getClass().getSimpleName());
        }
    }

    private static void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException | RuntimeException e) {
            LOG.warn("Closing the provisioning lock connection failed ({})", e.getClass().getSimpleName());
        }
    }
}
