package vn.tsolve.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.tenant.TenantDatabase;
import vn.tsolve.identity.dto.UserListQuery;
import vn.tsolve.platform.SqlBootstrapIT;

class UserListRepositoryIT {
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        DB.start();
        try {
            SqlBootstrapIT.migrate(DB.getJdbcUrl(), DB, "tenant");
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private final TenantDatabase database =
            new TenantDatabase(SqlBootstrapIT.jdbc(DB.getJdbcUrl(), DB).getDataSource());
    private final UserListRepository repository = new UserListRepository(database);

    @BeforeEach
    void seed() {
        var jdbc = database.jdbc();
        jdbc.execute("TRUNCATE app_user, department RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO department(department_id,name,status) VALUES(1,'Sales','ACTIVE'),(2,'IT','INACTIVE')");
        jdbc.update("INSERT INTO app_user(user_id,department_id,full_name,company_email,account_status) VALUES"
                + " (1,1,'Admin','admin@example.test','ACTIVE'),"
                + " (2,NULL,'Alice','alice@example.test','ACTIVE'),"
                + " (3,1,'Bob','bob@example.test','ACTIVE'),"
                + " (4,2,'Alpha','alpha@example.test','DEACTIVATED'),"
                + " (5,NULL,'Literal %_','literal@example.test','ACTIVE'),"
                + " (9223372036854775807,2,'Zed','zed@example.test','ACTIVE')");
        jdbc.update("INSERT INTO user_role(user_id,role) VALUES(1,'ADMIN'),(3,'PROJECT_MANAGER'),"
                + " (3,'TICKET_RESOLVER'),(4,'KNOWLEDGE_MANAGER')");
    }

    @Test
    void readsSqlUsersWithoutRoleJoinDuplicates_AC_004_9_AC_004_10_AC_004_12_AC_004_13() {
        var page = database.read(() -> repository.list(query()));
        assertThat(page.getTotalElements()).isEqualTo(6);
        assertThat(page.getContent())
                .extracting(user -> user.id())
                .containsExactly("1", "2", "4", "3", "5", "9223372036854775807");
        assertThat(page.getContent().get(3).roles())
                .containsExactly(Role.EMPLOYEE, Role.TICKET_RESOLVER, Role.PROJECT_MANAGER);
        assertThat(page.getContent().get(1).department()).isNull();
        assertThat(page.getContent().get(2).status()).isEqualTo("DEACTIVATED");
    }

    @Test
    void combinesLiteralSearchRoleStatusAndDepartment_AC_004_11() {
        assertThat(repository
                        .list(query("q", "  BOB ", "role", "PROJECT_MANAGER", "status", "ACTIVE", "departmentId", "1"))
                        .getContent())
                .extracting(user -> user.id())
                .containsExactly("3");
        assertThat(repository.list(query("q", "%_")).getContent())
                .extracting(user -> user.id())
                .containsExactly("5");
        assertThat(repository.list(query("q", "' OR true --")).getTotalElements())
                .isZero();
        assertThat(repository.list(query("role", "EMPLOYEE")).getContent())
                .extracting(user -> user.id())
                .containsExactly("2", "5", "9223372036854775807");
    }

    @Test
    void pagesDistinctUsersAndKeepsMatchingTotal_AC_004_13_AC_004_14() {
        database.jdbc().update("UPDATE app_user SET full_name='Same' WHERE user_id IN (2,3)");
        var first = repository.list(query("q", "Same", "size", "1", "sort", "fullName,desc"));
        var second = repository.list(query("q", "Same", "size", "1", "page", "1", "sort", "fullName,desc"));
        assertThat(first.getContent().getFirst().id()).isEqualTo("2");
        assertThat(second.getContent().getFirst().id()).isEqualTo("3");
        assertThat(first.getTotalElements()).isEqualTo(2);
        var beyond = repository.list(query("page", "100"));
        assertThat(beyond.getContent()).isEmpty();
        assertThat(beyond.getTotalElements()).isEqualTo(6);
        assertThat(repository.list(query("departmentId", "999")).getContent()).isEmpty();
    }

    @Test
    void readsRetainedDepartmentsAndCurrentAccess_AC_004_15_AC_004_16() {
        assertThat(repository.departments())
                .extracting(department -> department.name())
                .containsExactly("IT", "Sales");
        assertThat(repository.departments())
                .extracting(department -> department.departmentId())
                .containsExactly("2", "1");
        assertThat(repository.accountAccess(1).orElseThrow().roles()).contains(Role.ADMIN, Role.EMPLOYEE);
        assertThat(repository.accountAccess(4).orElseThrow().status()).isEqualTo("DEACTIVATED");
        assertThat(repository.accountAccess(999)).isEmpty();
    }

    private static UserListQuery query(String... pairs) {
        var parameters = new LinkedMultiValueMap<String, String>();
        for (int i = 0; i < pairs.length; i += 2) parameters.add(pairs[i], pairs[i + 1]);
        return UserListQuery.parse(parameters);
    }
}
