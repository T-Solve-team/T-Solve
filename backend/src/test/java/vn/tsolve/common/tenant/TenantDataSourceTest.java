package vn.tsolve.common.tenant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

class TenantDataSourceTest {
    @Test
    void hasNoDefaultConnection_AC_002_21_AC_002_24() {
        var context = new ScopedTenantContext(id -> true, TransactionOperations.withoutTransaction());
        DataSource target = mock(DataSource.class);
        var routing = new TenantDataSource(context, id -> target);
        assertThatThrownBy(routing::getConnection).isInstanceOf(TenantAccessException.class);
        assertThatThrownBy(() -> routing.getConnection("injected", "injected"))
                .isInstanceOf(TenantAccessException.class);
        verifyNoInteractions(target);
    }

    @Test
    void sanitizesDriverErrorsWithoutFallback_AC_002_26() throws Exception {
        var context = new ScopedTenantContext(id -> true, TransactionOperations.withoutTransaction());
        DataSource target = mock(DataSource.class);
        when(target.getConnection()).thenThrow(new SQLException("private connection detail"));
        var routing = new TenantDataSource(context, id -> target);
        assertThatThrownBy(() -> context.runIn(1L, () -> {
                    try {
                        return routing.getConnection();
                    } catch (SQLException exception) {
                        throw new AssertionError(exception);
                    }
                }))
                .isInstanceOf(TenantAccessException.class)
                .hasMessage("MSG06")
                .hasNoCause();
    }
}
