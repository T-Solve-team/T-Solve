package vn.tsolve.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import vn.tsolve.common.tenant.NoTenantException;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.identity.dto.UserListQuery;
import vn.tsolve.identity.repository.UserListRepository;
import vn.tsolve.identity.signin.port.PlatformTenantScope;

class UserListServiceImplTest {
    private final PlatformTenantScope scope = new PlatformTenantScope(new ThreadLocalTenantContext(id -> true));
    private final UserListRepository repository = mock(UserListRepository.class);
    private final UserListServiceImpl service = new UserListServiceImpl(scope, repository);
    private final UserListQuery query = UserListQuery.of(null, null, null, null, null, null, List.of());

    @Test
    void refusesUnboundWorkspaceBeforeReadingAccountsOrDepartments_AC_004_16() {
        assertThatThrownBy(() -> service.list(query)).isInstanceOf(NoTenantException.class);
        assertThatThrownBy(service::departments).isInstanceOf(NoTenantException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void readsInsideBoundWorkspace_AC_004_16() {
        UUID workspace = UUID.randomUUID();
        when(repository.list(query)).thenAnswer(invocation -> {
            assertThat(scope.currentWorkspaceId()).isEqualTo(workspace);
            return Page.empty();
        });
        assertThat(scope.runIn(workspace, () -> service.list(query))).isEmpty();
    }
}
