package vn.tsolve.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.identity.exception.UserListException;
import vn.tsolve.identity.service.impl.UserListTransaction;

class UserListTransactionTest {
    private final UUID workspace = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private final TenantContext context = mock(TenantContext.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final UserListTransaction transactions = new UserListTransaction(jdbc, context, manager);

    @Test
    void absentContextFailsBeforeConnection_AC_004_19_BUG_114() {
        when(context.currentWorkspaceId()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> transactions.read(() -> "not executed")).isInstanceOf(UserListException.class);
        verifyNoInteractions(jdbc, manager);
    }

    @Test
    void missingOrRejectedGuardNeverRunsBusinessQuery_AC_004_18_AC_004_19_BUG_114() {
        when(context.currentWorkspaceId()).thenReturn(Optional.of(workspace));
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var calls = new AtomicInteger();
        java.util.function.Supplier<Object> query = () -> calls.incrementAndGet();
        for (Boolean valid : new Boolean[] {false, null}) {
            when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(workspace), eq(workspace)))
                    .thenReturn(valid);
            assertThatThrownBy(() -> transactions.read(query))
                    .isInstanceOfSatisfying(UserListException.class, failure -> assertThat(failure.getStatus())
                            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        }
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(workspace), eq(workspace)))
                .thenThrow(new DataAccessResourceFailureException("Missing platform guard"));
        assertThatThrownBy(() -> transactions.read(query)).isInstanceOfSatisfying(UserListException.class, failure -> {
            assertThat(failure.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(failure.getCode()).isEqualTo("MSG06");
        });
        assertThat(calls.get()).isZero();
    }

    @Test
    void unexpectedQueryFailureReturns500AfterReadinessPasses_AC_004_19_BUG_121() {
        when(context.currentWorkspaceId()).thenReturn(Optional.of(workspace));
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(workspace), eq(workspace)))
                .thenReturn(true);
        assertThatThrownBy(() -> transactions.read(() -> {
                    throw new DataAccessResourceFailureException("Read failure after readiness check");
                }))
                .isInstanceOfSatisfying(UserListException.class, failure -> {
                    assertThat(failure.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(failure.getCode()).isEqualTo("MSG06");
                });
    }
}
