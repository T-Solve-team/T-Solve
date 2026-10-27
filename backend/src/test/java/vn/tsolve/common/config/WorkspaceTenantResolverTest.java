package vn.tsolve.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;
import vn.tsolve.common.security.WorkspaceContext;

class WorkspaceTenantResolverTest {

    private final WorkspaceTenantResolver resolver = new WorkspaceTenantResolver();

    @Test
    void resolvesTheCurrentWorkspace_AC_003_7() {
        Long resolved = WorkspaceContext.callAs(7L, resolver::resolveCurrentTenantIdentifier);

        assertThat(resolved).isEqualTo(7L);
    }

    @Test
    void resolvesNoneOutsideAnyWorkspace() {
        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(WorkspaceContext.NONE);
    }

    @Test
    void onlyRootSeesEveryWorkspace_AC_004_9() {
        assertThat(resolver.isRoot(WorkspaceContext.ROOT)).isTrue();
        assertThat(resolver.isRoot(7L)).isFalse();
        assertThat(resolver.isRoot(WorkspaceContext.NONE)).isFalse();
    }

    @Test
    void registersItselfWithHibernate() {
        Map<String, Object> properties = new HashMap<>();

        resolver.customize(properties);

        assertThat(properties).containsEntry(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}
