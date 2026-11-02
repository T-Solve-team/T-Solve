package vn.tsolve.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class TSolveUserTest {
    @Test
    void definesFiveWorkspaceRoles_AC_002_1() {
        assertThat(Role.values())
                .extracting(Enum::name)
                .containsExactly(
                        Roles.EMPLOYEE,
                        Roles.TICKET_RESOLVER,
                        Roles.PROJECT_MANAGER,
                        Roles.KNOWLEDGE_MANAGER,
                        Roles.ADMIN);
    }

    @Test
    void addsEmployeeWithoutDuplicates_AC_002_2() {
        var user = new TSolveUser(UUID.randomUUID(), "user@example.test", "User", Set.of(Role.ADMIN, Role.EMPLOYEE));
        assertThat(user.authorities()).extracting(a -> a.getAuthority()).containsExactly("ROLE_EMPLOYEE", "ROLE_ADMIN");
        assertThat(new TSolveUser(UUID.randomUUID(), "user@example.test", "User", Set.of()).authorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_EMPLOYEE");
    }

    @Test
    void readsOnlyWorkspacePrincipal_AC_002_12() {
        try {
            assertThat(TSolveUser.current()).isEmpty();
            var user = new TSolveUser(UUID.randomUUID(), "user@example.test", "User", Set.of(Role.ADMIN));
            SecurityContextHolder.getContext()
                    .setAuthentication(
                            UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities()));
            assertThat(TSolveUser.current()).contains(user);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
