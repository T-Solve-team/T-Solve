package vn.tsolve.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.tsolve.identity.signin.SignInConfig;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.TenantScope;

/** AC-002.15 through the identity {@link TenantScope}: a Suspended Workspace still binds. */
class SuspendedWorkspaceScopeTest {

    private static final UUID SUSPENDED = InMemoryWorkspaceLookup.GAMMA.id();

    private final SignInConfig config = new SignInConfig();
    private final TenantContext holder =
            new ThreadLocalTenantContext(config.knownWorkspaces(new InMemoryWorkspaceLookup()));
    private final TenantScope scope = config.tenantScope(holder);

    @AfterEach
    void nothingIsLeftBound() {
        assertThat(holder.currentWorkspaceId()).as("binding left on the thread").isEmpty();
    }

    @Test
    @DisplayName("AC-002.15 lets a Suspended Workspace bind through TenantScope")
    void aSuspendedWorkspaceBindsThroughTheScope() {
        AtomicReference<Optional<UUID>> holderSees = new AtomicReference<>();

        String result = scope.runIn(SUSPENDED, () -> {
            holderSees.set(holder.currentWorkspaceId());
            return "ran";
        });

        assertThat(result).isEqualTo("ran");
        assertThat(holderSees.get()).contains(SUSPENDED);
    }
}
