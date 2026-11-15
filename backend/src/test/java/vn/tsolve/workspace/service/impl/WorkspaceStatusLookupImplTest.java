package vn.tsolve.workspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.session.lookup.DepartmentLookup;
import vn.tsolve.common.session.lookup.ManagedSpaceLookup;
import vn.tsolve.common.session.lookup.ProjectMembershipLookup;
import vn.tsolve.common.session.lookup.WorkspaceStatusLookup;
import vn.tsolve.common.session.service.impl.SessionServiceImpl;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.workspace.repository.WorkspaceInfoRepository;

class WorkspaceStatusLookupImplTest {
    @Test
    @DisplayName("AC-006.1 readiness follows the row in the currently bound Workspace")
    void readsBoundWorkspace() {
        var scope = mock(TenantScope.class);
        var repository = mock(WorkspaceInfoRepository.class);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(scope.currentWorkspaceId()).thenReturn(first, second, first);
        when(repository.existsById(first)).thenReturn(true, false);
        when(repository.existsById(second)).thenReturn(false);
        var lookup = new WorkspaceStatusLookupImpl(scope, repository);
        assertThat(lookup.isCompanyInformationEntered()).isTrue();
        assertThat(lookup.isCompanyInformationEntered()).isFalse();
        assertThat(lookup.isCompanyInformationEntered()).isFalse();
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName(
            "AC-006.1 /me reports workspaceReady from the real lookup once the company information is saved (BUG-206)")
    void meReadsTheRealLookup_BUG_206() {
        var scope = mock(TenantScope.class);
        var repository = mock(WorkspaceInfoRepository.class);
        UUID workspaceId = UUID.randomUUID();
        when(scope.currentWorkspaceId()).thenReturn(workspaceId);
        when(repository.existsById(workspaceId)).thenReturn(false, true);
        var beans = new StaticListableBeanFactory();
        beans.addBean("workspaceStatusLookup", new WorkspaceStatusLookupImpl(scope, repository));
        var session = new SessionServiceImpl(
                beans.getBeanProvider(DepartmentLookup.class),
                beans.getBeanProvider(ProjectMembershipLookup.class),
                beans.getBeanProvider(ManagedSpaceLookup.class),
                beans.getBeanProvider(WorkspaceStatusLookup.class));
        var user = new TSolveUser(UUID.randomUUID(), "admin@example.test", "Admin", Set.of(Role.ADMIN));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities()));
        SecurityContextHolder.setContext(context);

        assertThat(session.getMe().workspaceReady()).isFalse();
        assertThat(session.getMe().workspaceReady()).isTrue();
    }
}
