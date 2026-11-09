package vn.tsolve.worker.service.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MockWorkerTenantServiceTest {
    @Test
    void listsActiveWorkspacesWithoutBinding_AC_003_1() {
        UUID active = UUID.randomUUID();
        var tenants = new MockWorkerTenantService(Set.of(active), Set.of());
        assertThat(tenants.currentWorkspaceId()).isEmpty();
        assertThat(tenants.activeWorkspaceIds()).containsExactly(active);
        assertThat(tenants.currentWorkspaceId()).isEmpty();
        tenants.runIn(active, () -> {
            assertThat(tenants.activeWorkspaceIds()).containsExactly(active);
            assertThat(tenants.currentWorkspaceId()).contains(active);
        });
        assertThat(tenants.currentWorkspaceId()).isEmpty();
    }

    @Test
    void returnsOnlyTheImmutableActiveAllowList_AC_003_2() {
        UUID active = UUID.randomUUID();
        UUID suspended = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        var configured = new HashSet<>(Set.of(active));
        var tenants = new MockWorkerTenantService(configured, Set.of(suspended));
        configured.add(unknown);
        assertThat(tenants.activeWorkspaceIds()).containsExactly(active).doesNotContain(suspended, unknown);
        assertThatThrownBy(() -> tenants.activeWorkspaceIds().add(unknown))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(new MockWorkerTenantService(Set.of(), Set.of(suspended)).activeWorkspaceIds())
                .isEmpty();
    }
}
