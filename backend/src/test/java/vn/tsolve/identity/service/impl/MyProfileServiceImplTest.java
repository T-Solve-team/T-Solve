package vn.tsolve.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.session.dto.MySolution;
import vn.tsolve.common.session.dto.MySolutionFilter;
import vn.tsolve.common.session.dto.SpaceRef;
import vn.tsolve.common.session.dto.TicketStatus;
import vn.tsolve.common.session.lookup.ViewableSpaceLookup;
import vn.tsolve.common.session.lookup.WrittenTicketLookup;
import vn.tsolve.identity.dto.MySolutionsQuery;

class MyProfileServiceImplTest {
    private static final UUID USER_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID USER_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID ID_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ID_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static final ObjectProvider<ViewableSpaceLookup> NO_SPACES =
            new StaticListableBeanFactory().getBeanProvider(ViewableSpaceLookup.class);
    private static final ObjectProvider<WrittenTicketLookup> NO_SOLUTIONS =
            new StaticListableBeanFactory().getBeanProvider(WrittenTicketLookup.class);

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

    private static ObjectProvider<WrittenTicketLookup> solutionsProviderOf(WrittenTicketLookup lookup) {
        return new StaticListableBeanFactory(Map.of("writtenTicketLookup", lookup))
                .getBeanProvider(WrittenTicketLookup.class);
    }

    private static SpaceRef space(UUID id, String name) {
        return new SpaceRef(id, name);
    }

    private static MySolution solution(String key) {
        return new MySolution(
                UUID.randomUUID(),
                key,
                "Title " + key,
                UUID.randomUUID(),
                "Project",
                TicketStatus.PUBLISHED,
                Instant.parse("2026-01-15T03:00:00Z"),
                true,
                null);
    }

    /** What the fake WrittenTicketLookup was asked, call by call. */
    private record Call(UUID accountId, MySolutionFilter filter, Pageable pageable) {}

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

        var first = new MyProfileServiceImpl(providerOf(accountId -> scrambled), NO_SOLUTIONS).getMyViewableSpaces();
        var second = new MyProfileServiceImpl(providerOf(accountId -> List.copyOf(reversed)), NO_SOLUTIONS)
                .getMyViewableSpaces();

        assertThat(first).extracting(SpaceRef::name).containsExactlyElementsOf(expectedNames);
        assertThat(first.subList(6, 8)).containsExactly(wiki1, wiki2);
        assertThat(first).containsExactlyInAnyOrderElementsOf(scrambled);
        assertThat(second).containsExactlyElementsOf(first);
    }

    @Test
    void answersEmptyWhenNoLookupBeanExists_AC_003_5() {
        signInAs(USER_A);

        var result = new MyProfileServiceImpl(NO_SPACES, NO_SOLUTIONS).getMyViewableSpaces();

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
        var service = new MyProfileServiceImpl(providerOf(lookup), NO_SOLUTIONS);

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
        var service = new MyProfileServiceImpl(providerOf(lookup), NO_SOLUTIONS);

        assertThatThrownBy(service::getMyViewableSpaces).isSameAs(boom);
    }

    @Test
    void convertsOneDayToTheWorkspaceDayBounds_AC_003_19() {
        signInAs(USER_A);
        var calls = new ArrayList<Call>();
        WrittenTicketLookup lookup = (accountId, filter, pageable) -> {
            calls.add(new Call(accountId, filter, pageable));
            return Page.empty(pageable);
        };
        var day = LocalDate.of(2026, 1, 15);

        new MyProfileServiceImpl(NO_SPACES, solutionsProviderOf(lookup))
                .getMySolutions(new MySolutionsQuery(null, null, null, null, day, day));

        assertThat(calls).hasSize(1);
        // Literal instants: the Workspace day is UTC+7, whatever the JVM's default zone is.
        assertThat(calls.get(0).filter().updatedFrom()).isEqualTo(Instant.parse("2026-01-14T17:00:00Z"));
        assertThat(calls.get(0).filter().updatedBefore()).isEqualTo(Instant.parse("2026-01-15T17:00:00Z"));
    }

    record DayCase(MySolutionsQuery query, Instant expectedFrom, Instant expectedBefore) {}

    static List<DayCase> openSides() {
        var day = LocalDate.of(2026, 1, 15);
        var start = Instant.parse("2026-01-14T17:00:00Z");
        var end = Instant.parse("2026-01-15T17:00:00Z");
        return List.of(
                new DayCase(new MySolutionsQuery(null, null, null, null, day, null), start, null),
                new DayCase(new MySolutionsQuery(null, null, null, null, null, day), null, end),
                new DayCase(new MySolutionsQuery(null, null, null, null, null, null), null, null));
    }

    @ParameterizedTest
    @MethodSource("openSides")
    void leavesAMissingSideOpenAndUsesTheDefaultPage_AC_003_19(DayCase testCase) {
        signInAs(USER_A);
        var calls = new ArrayList<Call>();
        WrittenTicketLookup lookup = (accountId, filter, pageable) -> {
            calls.add(new Call(accountId, filter, pageable));
            return Page.empty(pageable);
        };

        new MyProfileServiceImpl(NO_SPACES, solutionsProviderOf(lookup)).getMySolutions(testCase.query());

        var call = calls.get(0);
        assertThat(call.filter().updatedFrom()).isEqualTo(testCase.expectedFrom());
        assertThat(call.filter().updatedBefore()).isEqualTo(testCase.expectedBefore());
        assertThat(call.filter().status()).isNull();
        assertThat(call.filter().projectId()).isNull();
        assertThat(call.pageable()).isEqualTo(PageRequest.of(0, 20));
        assertThat(call.pageable().getSort().isUnsorted()).isTrue();
    }

    @Test
    void passesTheFiltersAndPageAsGiven_AC_003_19() {
        signInAs(USER_A);
        var foreignProject = UUID.randomUUID();
        var lookupPage = new PageImpl<>(List.of(solution("KAN-1")), PageRequest.of(3, 7), 99);
        var calls = new ArrayList<Call>();
        WrittenTicketLookup lookup = (accountId, filter, pageable) -> {
            calls.add(new Call(accountId, filter, pageable));
            return lookupPage;
        };

        var result = new MyProfileServiceImpl(NO_SPACES, solutionsProviderOf(lookup))
                .getMySolutions(new MySolutionsQuery(
                        3,
                        7,
                        TicketStatus.REJECTED,
                        foreignProject,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 31)));

        var call = calls.get(0);
        assertThat(call.filter().status()).isEqualTo(TicketStatus.REJECTED);
        assertThat(call.filter().projectId()).isEqualTo(foreignProject);
        assertThat(call.pageable()).isEqualTo(PageRequest.of(3, 7));
        assertThat(call.pageable().getSort().isUnsorted()).isTrue();
        assertThat(result).isSameAs(lookupPage);
    }

    @Test
    void answersAnEmptyPageWhenNoLookupBeanExists_AC_003_8() {
        signInAs(USER_A);

        var result = new MyProfileServiceImpl(NO_SPACES, NO_SOLUTIONS)
                .getMySolutions(new MySolutionsQuery(2, 5, null, null, null, null));

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
        assertThat(result.getPageable()).isEqualTo(PageRequest.of(2, 5));
    }

    @Test
    void asksTheLookupOnlyForTheSignedInAccountOnMySolutions_AC_003_7() {
        // The Workspace half is the tenant from TenantContext inside the port; its binding is
        // WorkspaceBindingTest on main.
        var rowA = solution("KAN-A");
        var rowB = solution("KAN-B");
        var byAccount = Map.of(USER_A, rowA, USER_B, rowB);
        var asked = new ArrayList<UUID>();
        WrittenTicketLookup lookup = (accountId, filter, pageable) -> {
            asked.add(accountId);
            return new PageImpl<>(List.of(byAccount.get(accountId)), pageable, 1);
        };
        var service = new MyProfileServiceImpl(NO_SPACES, solutionsProviderOf(lookup));
        var query = new MySolutionsQuery(null, null, null, null, null, null);

        signInAs(USER_A);
        var forA = service.getMySolutions(query);
        signInAs(USER_B);
        var forB = service.getMySolutions(query);

        assertThat(forA.getContent()).containsExactly(rowA);
        assertThat(forB.getContent()).containsExactly(rowB);
        assertThat(asked).containsExactly(USER_A, USER_B);
    }

    @Test
    void doesNotSwallowALookupFailure_AC_003_6() {
        signInAs(USER_A);
        var boom = new IllegalStateException("boom");
        WrittenTicketLookup lookup = (accountId, filter, pageable) -> {
            throw boom;
        };
        var service = new MyProfileServiceImpl(NO_SPACES, solutionsProviderOf(lookup));

        assertThatThrownBy(() -> service.getMySolutions(new MySolutionsQuery(null, null, null, null, null, null)))
                .isSameAs(boom);
    }
}
