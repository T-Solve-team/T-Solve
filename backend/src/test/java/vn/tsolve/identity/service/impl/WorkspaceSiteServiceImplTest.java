package vn.tsolve.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import vn.tsolve.identity.signin.port.WorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

class WorkspaceSiteServiceImplTest {
    @SuppressWarnings("unchecked")
    private final ObjectProvider<WorkspaceLookup> lookups = mock(ObjectProvider.class);

    private final WorkspaceSiteServiceImpl sites = new WorkspaceSiteServiceImpl(lookups);

    @Test
    void returnsEmptyWhenLookupUnavailable_AC_010_8() {
        assertThat(sites.siteHost(UUID.randomUUID())).isEmpty();
    }

    @Test
    void resolvesKnownWorkspaceAndOmitsUnknownOrBlankHost_AC_010_8() {
        var lookup = mock(WorkspaceLookup.class);
        when(lookups.getIfAvailable()).thenReturn(lookup);
        UUID id = UUID.randomUUID();
        assertThat(sites.siteHost(id)).isEmpty();
        for (String host : new String[] {null, "", " "}) {
            when(lookup.findById(id))
                    .thenReturn(
                            Optional.of(new WorkspaceRef(id, host, null, WorkspaceRef.Status.ACTIVE, "acme", null)));
            assertThat(sites.siteHost(id)).isEmpty();
        }
        when(lookup.findById(id))
                .thenReturn(Optional.of(
                        new WorkspaceRef(id, "acme.example.test", null, WorkspaceRef.Status.ACTIVE, "acme", null)));
        assertThat(sites.siteHost(id)).contains("acme.example.test");
    }
}
