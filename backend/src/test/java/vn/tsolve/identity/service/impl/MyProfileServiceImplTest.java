package vn.tsolve.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.session.dto.SpaceRef;
import vn.tsolve.common.session.lookup.ViewableSpaceLookup;

class MyProfileServiceImplTest {
    private static final UUID USER_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID USER_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID ID_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ID_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void signInAs(UUID id) {
        var user = new TSolveUser(id, "user@example.test", "Test User", Set.of());
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities()));
    }

    private static ObjectProvider<ViewableSpaceLookup> providerOf(ViewableSpaceLookup lookup) {
        return new StaticListableBeanFactory(Map.of("viewableSpaceLookup", lookup))
                .getBeanProvider(ViewableSpaceLookup.class);
    }

    private static SpaceRef space(UUID id, String name) {
        return new SpaceRef(id, name);
    }

    @Test
    void sortsByVietnameseNameThenSpaceId_AC_003_4() {
        signInAs(USER_A);
        var wiki1 = space(ID_1, "Wiki");
        var wiki2 = space(ID_2, "Wiki");
        var scrambled = List.of(
                space(UUID.randomUUID(), "Zalo"),
                wiki2,
                space(UUID.randomUUID(), "Đào tạo"),
                space(UUID.randomUUID(), "bảo mật"),
                space(UUID.randomUUID(), "Ống nước"),
                wiki1,
                space(UUID.randomUUID(), "Dữ liệu"),
                space(UUID.randomUUID(), "Ong vàng"),
                space(UUID.randomUUID(), "An toàn"));
        var reversed = new ArrayList<>(scrambled);
        Collections.reverse(reversed);
        var expectedNames =
                List.of("An toàn", "bảo mật", "Dữ liệu", "Đào tạo", "Ong vàng", "Ống nước", "Wiki", "Wiki", "Zalo");

        var first = new MyProfileServiceImpl(providerOf(accountId -> scrambled)).getMyViewableSpaces();
        var second = new MyProfileServiceImpl(providerOf(accountId -> List.copyOf(reversed))).getMyViewableSpaces();

        assertThat(first).extracting(SpaceRef::name).containsExactlyElementsOf(expectedNames);
        assertThat(first.subList(6, 8)).containsExactly(wiki1, wiki2);
        assertThat(first).containsExactlyInAnyOrderElementsOf(scrambled);
        assertThat(second).containsExactlyElementsOf(first);
    }

    @Test
    void answersEmptyWhenNoLookupBeanExists_AC_003_5() {
        signInAs(USER_A);
        var noBean = new StaticListableBeanFactory().getBeanProvider(ViewableSpaceLookup.class);

        var result = new MyProfileServiceImpl(noBean).getMyViewableSpaces();

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void asksTheLookupOnlyForTheSignedInAccount_AC_003_7() {
        // The Workspace half is the tenant from TenantContext inside the port; its binding is
        // WorkspaceBindingTest on main.
        var spaceA = space(UUID.randomUUID(), "Space of A");
        var spaceB = space(UUID.randomUUID(), "Space of B");
        var asked = new ArrayList<UUID>();
        var byAccount = Map.of(USER_A, List.of(spaceA), USER_B, List.of(spaceB));
        ViewableSpaceLookup lookup = accountId -> {
            asked.add(accountId);
            return byAccount.getOrDefault(accountId, List.of());
        };
        var service = new MyProfileServiceImpl(providerOf(lookup));

        signInAs(USER_A);
        var forA = service.getMyViewableSpaces();
        signInAs(USER_B);
        var forB = service.getMyViewableSpaces();

        assertThat(forA).containsExactly(spaceA);
        assertThat(forB).containsExactly(spaceB);
        assertThat(asked).containsExactly(USER_A, USER_B);
    }

    @Test
    void doesNotSwallowALookupFailure_AC_003_4() {
        signInAs(USER_A);
        var boom = new IllegalStateException("down");
        ViewableSpaceLookup lookup = accountId -> {
            throw boom;
        };
        var service = new MyProfileServiceImpl(providerOf(lookup));

        assertThatThrownBy(service::getMyViewableSpaces).isSameAs(boom);
    }
}
