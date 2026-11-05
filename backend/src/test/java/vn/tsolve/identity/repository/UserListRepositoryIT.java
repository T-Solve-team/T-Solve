package vn.tsolve.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.tsolve.identity.UserListTestDatabase.A;
import static vn.tsolve.identity.UserListTestDatabase.ADMIN;
import static vn.tsolve.identity.UserListTestDatabase.DEPARTMENT;
import static vn.tsolve.identity.UserListTestDatabase.RETAINED;
import static vn.tsolve.identity.UserListTestDatabase.id;
import static vn.tsolve.identity.UserListTestDatabase.owner;

import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import vn.tsolve.common.security.Role;
import vn.tsolve.identity.UserListTestDatabase;
import vn.tsolve.identity.dto.UserListQuery;
import vn.tsolve.identity.service.impl.UserListTransaction;

class UserListRepositoryIT {
    private final UserListTestDatabase.TestTenantContext context = new UserListTestDatabase.TestTenantContext();
    private final javax.sql.DataSource source = UserListTestDatabase.routing(context);
    private final JdbcTemplate jdbc = new JdbcTemplate(source);
    private final UserListTransaction transaction =
            new UserListTransaction(jdbc, context, new UserListTestDatabase.TestTransactionManager(source, context));
    private final UserListRepository repository = new UserListRepository(jdbc);

    @BeforeEach
    void seed() {
        UserListTestDatabase.seed();
    }

    @Test
    void readsMigratedUuidAccountsWithoutRoleJoinDuplicates_AC_004_9_AC_004_10_AC_004_12_AC_004_13_BUG_114_BUG_118() {
        var page = read(() -> repository.list(query("sort", "email,asc")));
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getContent()).extracting(user -> user.id()).containsExactly(ADMIN, id(2), id(4), id(5), id(3));
        assertThat(page.getContent().getFirst().email()).isEqualTo("a-admin@example.test");
        assertThat(page.getContent().get(4).roles())
                .containsExactly(Role.EMPLOYEE, Role.TICKET_RESOLVER, Role.PROJECT_MANAGER);
        assertThat(page.getContent().get(1).department()).isNull();
        assertThat(page.getContent().get(2).status()).isEqualTo("DEACTIVATED");
        assertThat(read(() -> jdbc.queryForObject("SELECT current_setting('transaction_read_only')", String.class)))
                .isEqualTo("on");
        assertThat(read(() -> jdbc.queryForObject("SELECT current_setting('transaction_isolation')", String.class)))
                .isEqualTo("repeatable read");
    }

    @Test
    void combinesLiteralSearchRoleStatusAndDepartment_AC_004_11() {
        assertThat(read(() -> repository.list(query(
                                "q",
                                "  RESOLVER ",
                                "role",
                                "PROJECT_MANAGER",
                                "status",
                                "ACTIVE",
                                "departmentId",
                                DEPARTMENT.toString())))
                        .getContent())
                .extracting(user -> user.id())
                .containsExactly(id(3));
        assertThat(read(() -> repository.list(query("q", "%_"))).getContent())
                .extracting(user -> user.id())
                .containsExactly(id(5));
        assertThat(read(() -> repository.list(query("q", "' OR true --"))).getTotalElements())
                .isZero();
        assertThat(read(() -> repository.list(query("role", "EMPLOYEE"))).getContent())
                .extracting(user -> user.id())
                .containsExactlyInAnyOrder(id(2), id(4), id(5));
    }

    @Test
    void pagesDistinctUsersWithUuidTieOrderAndMatchingTotal_AC_004_13_AC_004_14() {
        owner(A).update("UPDATE app_user SET full_name='Same' WHERE id IN (?,?)", id(2), id(3));
        var first = read(() -> repository.list(query("q", "Same", "size", "1", "sort", "fullName,desc")));
        var second = read(() -> repository.list(query("q", "Same", "size", "1", "page", "1", "sort", "fullName,desc")));
        assertThat(first.getContent().getFirst().id()).isEqualTo(id(2));
        assertThat(second.getContent().getFirst().id()).isEqualTo(id(3));
        assertThat(first.getTotalElements()).isEqualTo(2);
        var beyond = read(() -> repository.list(query("page", "100")));
        assertThat(beyond.getContent()).isEmpty();
        assertThat(beyond.getTotalElements()).isEqualTo(5);
        assertThat(read(() -> repository.list(
                                query("departmentId", UUID.randomUUID().toString())))
                        .getContent())
                .isEmpty();
    }

    @Test
    void readsRetainedDepartmentsAndCurrentAccess_AC_004_15_AC_004_16() {
        assertThat(read(repository::departments))
                .extracting(department -> department.name())
                .containsExactly("IT", "Retained");
        assertThat(read(repository::departments))
                .extracting(department -> department.departmentId())
                .containsExactly(DEPARTMENT, RETAINED);
        assertThat(read(() -> repository.accountAccess(ADMIN)).orElseThrow().roles())
                .contains(Role.ADMIN, Role.EMPLOYEE);
        assertThat(read(() -> repository.accountAccess(id(4))).orElseThrow().status())
                .isEqualTo("DEACTIVATED");
        assertThat(read(() -> repository.accountAccess(UUID.randomUUID()))).isEmpty();
    }

    private <T> T read(Supplier<T> query) {
        return context.runIn(A, () -> transaction.read(query));
    }

    private static UserListQuery query(String... pairs) {
        var parameters = new LinkedMultiValueMap<String, String>();
        for (int i = 0; i < pairs.length; i += 2) parameters.add(pairs[i], pairs[i + 1]);
        return UserListQuery.parse(parameters);
    }
}
