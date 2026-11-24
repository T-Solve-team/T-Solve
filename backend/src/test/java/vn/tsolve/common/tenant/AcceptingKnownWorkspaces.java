package vn.tsolve.common.tenant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Shared IT fixture: the one {@link ThreadLocalTenantContext} over a {@link KnownWorkspaces} that accepts every id.
 * The ITs that bind through the production chain use ids the interim {@code WorkspaceLookup} cannot know (it is empty
 * in tests); the refusal of an unknown id is covered by the unit tests of the holder.
 */
@TestConfiguration(proxyBeanMethods = false)
public class AcceptingKnownWorkspaces {
    @Bean
    KnownWorkspaces knownWorkspaces() {
        return id -> true;
    }

    @Bean
    ThreadLocalTenantContext tenantContext(KnownWorkspaces known) {
        return new ThreadLocalTenantContext(known);
    }
}
