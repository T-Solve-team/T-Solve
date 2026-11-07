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
import vn.tsolve.identity.dto.UserListQuery;
import vn.tsolve.identity.repository.UserListRepository;
import vn.tsolve.identity.signin.port.LocalTenantScope;

class UserListServiceImplTest {
    private final LocalTenantScope scope = new LocalTenantScope();
    private final UserListRepository repository = mock(UserListRepository.class);
    private final UserListServiceImpl service = new UserListServiceImpl(scope, repository);
    private final UserListQuery query = UserListQuery.of(null, null, null, null, null, null, List.of());

    @Test
    void refusesUnboundWorkspaceBeforeReadingAccountsOrDepartments_AC_004_16() {
        assertThatThrownBy(() -> service.list(query)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(service::departments).isInstanceOf(IllegalStateException.class);
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
