package vn.tsolve.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.identity.exception.UserListException;
import vn.tsolve.identity.repository.UserListRepository;
import vn.tsolve.identity.service.impl.UserListAccessServiceImpl;

class UserListAccessServiceTest {
    private static final UUID WORKSPACE = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID ACCOUNT = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000007");
    private final TenantContext context = mock(TenantContext.class);
    private final UserListRepository repository = mock(UserListRepository.class);
    private final UserListAccessServiceImpl access = new UserListAccessServiceImpl(context, repository);

    @Test
    void usesCurrentDatabaseRolesInsteadOfSnapshot_AC_004_16() {
        when(context.currentWorkspaceId()).thenReturn(Optional.of(WORKSPACE));
        when(repository.accountAccess(ACCOUNT))
                .thenReturn(
                        Optional.of(new UserListRepository.AccountAccess("ACTIVE", Set.of(Role.EMPLOYEE, Role.ADMIN))));
        Authentication session = authenticated(user(), WORKSPACE);
        assertThat(access.requireAdmin(session)).contains(Role.ADMIN);
        when(repository.accountAccess(ACCOUNT))
                .thenReturn(Optional.of(new UserListRepository.AccountAccess("ACTIVE", Set.of(Role.EMPLOYEE))));
        assertFailure(() -> access.requireAdmin(session), 403, "MSG08");
        when(repository.accountAccess(ACCOUNT))
                .thenReturn(
                        Optional.of(new UserListRepository.AccountAccess("ACTIVE", Set.of(Role.EMPLOYEE, Role.ADMIN))));
        assertThat(access.requireAdmin(session)).contains(Role.ADMIN);
    }

    @Test
    void rejectsUntrustedAndCrossWorkspaceIdentityBeforeLookup_AC_004_16_AC_004_18_BUG_115() {
        when(context.currentWorkspaceId()).thenReturn(Optional.of(WORKSPACE));
        var missingDetails = authenticated(user(), WORKSPACE);
        missingDetails.setDetails(null);
        var spoofedDetails = authenticated(user(), WORKSPACE);
        spoofedDetails.setDetails(WORKSPACE.toString());
        for (Authentication authentication : new Authentication[] {
            null,
            new UsernamePasswordAuthenticationToken(user(), null),
            authenticated("untrusted-name", WORKSPACE),
            authenticated(user(), UUID.randomUUID()),
            missingDetails,
            spoofedDetails,
            new AnonymousAuthenticationToken("test", user(), List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        }) {
            assertFailure(() -> access.requireAdmin(authentication), 401, "MSG07");
        }
        when(context.currentWorkspaceId()).thenReturn(Optional.empty());
        assertFailure(() -> access.requireAdmin(authenticated(user(), WORKSPACE)), 401, "MSG07");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsMissingDeactivatedAndNonAdminAccounts_AC_004_16() {
        when(context.currentWorkspaceId()).thenReturn(Optional.of(WORKSPACE));
        Authentication session = authenticated(user(), WORKSPACE);
        when(repository.accountAccess(ACCOUNT)).thenReturn(Optional.empty());
        assertFailure(() -> access.requireAdmin(session), 401, "MSG07");
        when(repository.accountAccess(ACCOUNT))
                .thenReturn(Optional.of(
                        new UserListRepository.AccountAccess("DEACTIVATED", Set.of(Role.EMPLOYEE, Role.ADMIN))));
        assertFailure(() -> access.requireAdmin(session), 401, "MSG07");
        when(repository.accountAccess(ACCOUNT))
                .thenReturn(Optional.of(new UserListRepository.AccountAccess("ACTIVE", Set.of(Role.EMPLOYEE))));
        assertFailure(() -> access.requireAdmin(session), 403, "MSG08");
    }

    static TSolveUser user() {
        return new TSolveUser(ACCOUNT, "test@example.test", "Test", Set.of(Role.EMPLOYEE));
    }

    static UsernamePasswordAuthenticationToken authenticated(Object principal, UUID workspace) {
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
        authentication.setDetails(new WorkspaceSessionBinding(workspace));
        return authentication;
    }

    private static void assertFailure(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action, int status, String code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(UserListException.class, failure -> {
            assertThat(failure.getStatus().value()).isEqualTo(status);
            assertThat(failure.getCode()).isEqualTo(code);
        });
    }
}
