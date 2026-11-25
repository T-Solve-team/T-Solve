package vn.tsolve.identity.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.identity.signin.SignInConfig;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.TenantScope;

/**
 * Q1 (a): {@code KnownWorkspaces} answers from {@code WorkspaceLookup.findById} only, so the identity-mock Workspace
 * must be known to the Workspace catalog; the seeder refuses an id that is not.
 */
class MockUserListSeederTest {

    private static final UUID UNKNOWN = UUID.fromString("00000000-0000-4000-8000-0000000000a1");

    private final SignInConfig config = new SignInConfig();
    private final TenantContext holder =
            new ThreadLocalTenantContext(config.knownWorkspaces(new InMemoryWorkspaceLookup()));
    private final TenantScope scope = config.tenantScope(holder);
    private final JdbcClient jdbc = mock(JdbcClient.class);

    @AfterEach
    void nothingIsLeftBound() {
        assertThat(holder.currentWorkspaceId()).isEmpty();
    }

    @Test
    @DisplayName(
            "AC-002.14 the mock user seeder refuses a Workspace that KnownWorkspaces does not know and writes nothing")
    void refusesAnUnknownMockWorkspace() {
        var properties = new MockIdentityProperties("admin@acme.test", UNKNOWN, "acme.test");
        var seeder = new MockUserListSeeder(scope, properties, jdbc);

        assertThatThrownBy(() -> seeder.run(new DefaultApplicationArguments())).isInstanceOf(RuntimeException.class);

        verifyNoInteractions(jdbc);
    }
}
