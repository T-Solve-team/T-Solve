package vn.tsolve.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class TSolveUserTest {
    @Test
    void usesStableNonPersonalAuthenticationName_BUG_109() {
        var id = UUID.randomUUID();
        var user = new TSolveUser(id, "private@example.test", "Private Name", Set.of(Role.ADMIN));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities());
        assertThat(authentication.getName()).isEqualTo(id.toString()).hasSizeLessThanOrEqualTo(150);
        assertThat(user.toString()).doesNotContain(user.email(), user.fullName());
        var changed = new TSolveUser(id, "changed@example.test", "Changed Name", Set.of(Role.PROJECT_MANAGER));
        assertThat(UsernamePasswordAuthenticationToken.authenticated(changed, null, changed.authorities())
                        .getName())
                .isEqualTo(authentication.getName());
    }

    @Test
    void retainsImmutableOrderedRolesAndRejectsNullRoles_AC_002_2() {
        var user = new TSolveUser(
                UUID.randomUUID(), "user@example.test", "User", Set.of(Role.ADMIN, Role.PROJECT_MANAGER));
        assertThat(user.roles()).containsExactly(Role.EMPLOYEE, Role.PROJECT_MANAGER, Role.ADMIN);
        assertThatThrownBy(() -> user.roles().add(Role.KNOWLEDGE_MANAGER))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new TSolveUser(UUID.randomUUID(), "user@example.test", "User", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("roles");
    }

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
