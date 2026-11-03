package vn.tsolve.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.identity.exception.UserListException;
import vn.tsolve.identity.repository.UserListRepository;
import vn.tsolve.identity.service.impl.UserListAccessServiceImpl;

class UserListAccessServiceTest {
    private final TenantContext context = mock(TenantContext.class);
    private final UserListRepository repository = mock(UserListRepository.class);
    private final UserListAccessServiceImpl access = new UserListAccessServiceImpl(context, repository);

    @Test
    void usesCurrentDatabaseRolesInsteadOfSnapshot_AC_004_16() {
        when(context.currentWorkspaceId()).thenReturn(Optional.of(1L));
        when(repository.accountAccess(7))
                .thenReturn(
                        Optional.of(new UserListRepository.AccountAccess("ACTIVE", Set.of(Role.EMPLOYEE, Role.ADMIN))));
        Authentication session = authenticated(new WorkspaceAccountPrincipal(1, 7), "EMPLOYEE");
        assertThat(access.requireAdmin(session)).contains(Role.ADMIN);
        when(repository.accountAccess(7))
                .thenReturn(Optional.of(new UserListRepository.AccountAccess("ACTIVE", Set.of(Role.EMPLOYEE))));
        assertFailure(() -> access.requireAdmin(session), 403, "MSG08");
        when(repository.accountAccess(7))
                .thenReturn(
                        Optional.of(new UserListRepository.AccountAccess("ACTIVE", Set.of(Role.EMPLOYEE, Role.ADMIN))));
        assertThat(access.requireAdmin(session)).contains(Role.ADMIN);
    }

    @Test
    void rejectsUntrustedAndCrossWorkspaceIdentityBeforeLookup_AC_004_16_AC_004_18() {
        when(context.currentWorkspaceId()).thenReturn(Optional.of(1L));
        for (Authentication authentication : new Authentication[] {
            null,
            new UsernamePasswordAuthenticationToken(new WorkspaceAccountPrincipal(1, 7), null),
            authenticated("untrusted-name", "ADMIN"),
            authenticated(new WorkspaceAccountPrincipal(2, 7), "ADMIN"),
            new AnonymousAuthenticationToken(
                    "test", new WorkspaceAccountPrincipal(1, 7), List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        }) {
            assertFailure(() -> access.requireAdmin(authentication), 401, "MSG07");
        }
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsMissingDeactivatedAndNonAdminAccounts_AC_004_16() {
        when(context.currentWorkspaceId()).thenReturn(Optional.of(1L));
        Authentication session = authenticated(new WorkspaceAccountPrincipal(1, 7), "ADMIN");
        when(repository.accountAccess(7)).thenReturn(Optional.empty());
        assertFailure(() -> access.requireAdmin(session), 401, "MSG07");
        when(repository.accountAccess(7))
                .thenReturn(Optional.of(
                        new UserListRepository.AccountAccess("DEACTIVATED", Set.of(Role.EMPLOYEE, Role.ADMIN))));
        assertFailure(() -> access.requireAdmin(session), 401, "MSG07");
        when(repository.accountAccess(7))
                .thenReturn(Optional.of(new UserListRepository.AccountAccess("ACTIVE", Set.of(Role.EMPLOYEE))));
        assertFailure(() -> access.requireAdmin(session), 403, "MSG08");
    }

    private static Authentication authenticated(Object principal, String role) {
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static void assertFailure(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action, int status, String code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(UserListException.class, failure -> {
            assertThat(failure.getStatus().value()).isEqualTo(status);
            assertThat(failure.getCode()).isEqualTo(code);
        });
    }
}
