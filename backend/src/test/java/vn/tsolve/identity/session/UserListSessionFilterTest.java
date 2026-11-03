package vn.tsolve.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.tsolve.common.tenant.TenantAccessException;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.common.tenant.TenantDatabase;
import vn.tsolve.identity.repository.UserListRepository;
import vn.tsolve.identity.service.impl.UserListAccessServiceImpl;

class UserListSessionFilterTest {
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsInvalidSessionBeforeOpeningTenantConnection_AC_004_16_BUG_107() {
        var database = mock(TenantDatabase.class);
        when(database.read(any())).thenThrow(new TenantAccessException());
        var context = mock(TenantContext.class);
        when(context.currentWorkspaceId()).thenReturn(Optional.of(1L));
        var repository = mock(UserListRepository.class);
        var filter = new UserListSessionFilter(
                database,
                new UserListAccessServiceImpl(context, repository),
                Jackson2ObjectMapperBuilder.json().build());
        for (Object principal : new Object[] {null, "untrusted", new WorkspaceAccountPrincipal(2, 1)}) {
            SecurityContextHolder.clearContext();
            if (principal != null) {
                SecurityContextHolder.getContext()
                        .setAuthentication(new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            }
            var original = SecurityContextHolder.getContext();
            var response = new MockHttpServletResponse();
            assertThatCode(() -> filter.doFilter(
                            new MockHttpServletRequest("GET", "/api/v1/users"), response, new MockFilterChain()))
                    .doesNotThrowAnyException();
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(SecurityContextHolder.getContext()).isSameAs(original);
        }
        verifyNoInteractions(repository);
        org.mockito.Mockito.verify(database, org.mockito.Mockito.never()).read(any());
    }
}
