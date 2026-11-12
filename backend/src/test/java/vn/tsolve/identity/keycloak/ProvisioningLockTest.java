package vn.tsolve.identity.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The provisioning lock without a database (AC-001.59, review S3). The DataSource and Connection are mocks that record
 * every SQL text that is issued. A statement that releases a session lock ({@code pg_advisory_unlock}) fails like a
 * dead connection would, so the tests prove that no release step of the lock can replace the caller's result or
 * failure. No Docker.
 */
class ProvisioningLockTest {

    private final List<String> sql = new ArrayList<>();
    private Connection connection;
    private ProvisioningLock lock;
    /** Set by the tests of edge 1: a release step that fails, as on a dead connection. */
    private boolean sessionUnlockFails;

    @BeforeEach
    void setUp() throws SQLException {
        connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            sql.add(text);
            PreparedStatement statement = mock(PreparedStatement.class);
            when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
            if (sessionUnlockFails && text.contains("unlock")) {
                when(statement.execute()).thenThrow(new SQLException("connection is dead", "08006"));
                when(statement.executeQuery()).thenThrow(new SQLException("connection is dead", "08006"));
            }
            return statement;
        });
        Statement plain = mock(Statement.class);
        when(connection.createStatement()).thenReturn(plain);
        when(plain.execute(anyString())).thenAnswer(invocation -> {
            sql.add(invocation.getArgument(0));
            return false;
        });
        when(plain.executeUpdate(anyString())).thenAnswer(invocation -> {
            sql.add(invocation.getArgument(0));
            return 0;
        });
        when(plain.executeQuery(anyString())).thenAnswer(invocation -> {
            sql.add(invocation.getArgument(0));
            return mock(ResultSet.class);
        });
        lock = new ProvisioningLock(dataSource);
    }

    @Test
    @DisplayName("AC-001.59 S3 edge 1 a failing release step after a successful action does not replace the result")
    void releaseFailureKeepsResult_AC_001_59_edge1() throws SQLException {
        sessionUnlockFails = true;
        doThrow(new SQLException("commit failed", "08006")).when(connection).commit();
        doThrow(new SQLException("rollback failed", "08006")).when(connection).rollback();

        String result = lock.runLocked(() -> "organization-id");

        assertThat(result).isEqualTo("organization-id");
    }

    @Test
    @DisplayName("AC-001.59 S3 edge 1 a failing release step does not replace the action's own failure")
    void releaseFailureKeepsActionFailure_AC_001_59_edge1() throws SQLException {
        sessionUnlockFails = true;
        doThrow(new SQLException("commit failed", "08006")).when(connection).commit();
        doThrow(new SQLException("rollback failed", "08006")).when(connection).rollback();
        IllegalStateException failure = new IllegalStateException("the Keycloak write failed");

        assertThatThrownBy(() -> lock.runLocked(() -> {
                    throw failure;
                }))
                .isSameAs(failure);
    }

    @Test
    @DisplayName("AC-001.59 S3 edge 2 an exception of the action propagates unchanged and the connection is released")
    void actionFailurePropagatesAndReleasesConnection_AC_001_59_edge2() throws SQLException {
        IllegalStateException failure = new IllegalStateException("the Keycloak write failed");

        assertThatThrownBy(() -> lock.runLocked(() -> {
                    throw failure;
                }))
                .isSameAs(failure);

        verify(connection).close();
    }

    @Test
    @DisplayName("AC-001.59 S3 edge 3 the lock is transaction-scoped and a lock timeout is set before it is taken")
    void lockEndsWithTheTransactionAndHasATimeout_AC_001_59_edge3() throws SQLException {
        String result = lock.runLocked(() -> "done");

        assertThat(result).isEqualTo("done");
        assertThat(sql).anyMatch(text -> text.contains("pg_advisory_xact_lock"));
        assertThat(sql)
                .as("no session lock that outlives the transaction")
                .noneMatch(text -> text.contains("pg_advisory_lock") || text.contains("pg_advisory_unlock"));
        int timeout = indexOf("lock_timeout");
        int taken = indexOf("pg_advisory_xact_lock");
        assertThat(timeout).as("a lock_timeout statement was issued").isGreaterThanOrEqualTo(0);
        assertThat(timeout).as("the timeout is set before the lock is taken").isLessThan(taken);
        verify(connection).setAutoCommit(false);
        verify(connection).commit();
    }

    @Test
    @DisplayName("AC-001.59 S3 edge 3 a failing action rolls the transaction back, which releases the lock")
    void failingActionRollsBack_AC_001_59_edge3() throws SQLException {
        assertThatThrownBy(() -> lock.runLocked(() -> {
                    throw new IllegalStateException("the Keycloak write failed");
                }))
                .isInstanceOf(IllegalStateException.class);

        verify(connection).rollback();
    }

    private int indexOf(String fragment) {
        for (int i = 0; i < sql.size(); i++) {
            if (sql.get(i).toLowerCase().contains(fragment)) {
                return i;
            }
        }
        return -1;
    }
}
