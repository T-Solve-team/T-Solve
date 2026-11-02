package vn.tsolve.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import vn.tsolve.common.tenant.TenantAccessException;
import vn.tsolve.platform.repository.TenantConnection;

class TenantPoolProviderTest {
    @Test
    void opensOnePoolPerWorkspaceAndClosesIt_AC_002_22_AC_002_26() throws Exception {
        var opens = new AtomicInteger();
        var pool = mock(HikariDataSource.class);
        var provider = new TenantPoolProvider(
                id -> Optional.of(connection(id)),
                key -> "test-only",
                (c, p) -> {
                    opens.incrementAndGet();
                    return pool;
                },
                1);
        assertThatCode(() -> provider.apply(1L)).doesNotThrowAnyException();
        try (var threads = Executors.newFixedThreadPool(4)) {
            var futures = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(i -> threads.submit(() -> provider.apply(1L)))
                    .toList();
            for (var future : futures) {
                assertThat(future.get()).isSameAs(pool);
            }
        }
        assertThat(opens).hasValue(1);
        assertThatThrownBy(() -> provider.apply(2L)).isInstanceOf(TenantAccessException.class);
        provider.close();
        provider.close();
        verify(pool).close();
        assertThatThrownBy(() -> provider.apply(1L)).isInstanceOf(TenantAccessException.class);
    }

    @Test
    void missingSecretsNeverOpenAPool_AC_001_24_AC_001_25() {
        var pool = mock(HikariDataSource.class);
        try (var provider = new TenantPoolProvider(id -> Optional.of(connection(id)), key -> " ", (c, p) -> pool, 2)) {
            assertThatThrownBy(() -> provider.apply(1L))
                    .isInstanceOf(TenantAccessException.class)
                    .hasMessage("MSG06");
            verifyNoInteractions(pool);
        }
    }

    private static TenantConnection connection(long id) {
        return new TenantConnection(id, "unused", 5432, "unused", "unused", "TEST_PASSWORD");
    }

    @Test
    void rejectsIncompleteConnectionMetadataBeforeDriverFallback_BUG_106() {
        var pool = mock(HikariDataSource.class);
        for (var connection : java.util.List.of(
                new TenantConnection(1, "", 5432, "one", "one_app", "TEST_PASSWORD"),
                new TenantConnection(1, "db", 5432, "", "one_app", "TEST_PASSWORD"),
                new TenantConnection(1, "db", 0, "one", "one_app", "TEST_PASSWORD"),
                new TenantConnection(1, "db", 5432, "one", " ", "TEST_PASSWORD"))) {
            try (var provider =
                    new TenantPoolProvider(id -> Optional.of(connection), key -> "test-only", (c, p) -> pool, 2)) {
                assertThatThrownBy(() -> provider.apply(1L)).isInstanceOf(TenantAccessException.class);
            }
        }
        verifyNoInteractions(pool);
    }
}
