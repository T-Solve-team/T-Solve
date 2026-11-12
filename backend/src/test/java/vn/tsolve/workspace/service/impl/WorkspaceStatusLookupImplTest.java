package vn.tsolve.workspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.workspace.repository.WorkspaceInfoRepository;

class WorkspaceStatusLookupImplTest {
    @Test
    @DisplayName("AC-002.1 readiness follows the row in the currently bound Workspace")
    void readsBoundWorkspace() {
        var scope = mock(TenantScope.class);
        var repository = mock(WorkspaceInfoRepository.class);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(scope.currentWorkspaceId()).thenReturn(first, second, first);
        when(repository.existsById(first)).thenReturn(true, false);
        when(repository.existsById(second)).thenReturn(false);
        var lookup = new WorkspaceStatusLookupImpl(scope, repository);
        assertThat(lookup.isReady()).isTrue();
        assertThat(lookup.isReady()).isFalse();
        assertThat(lookup.isReady()).isFalse();
    }
}
