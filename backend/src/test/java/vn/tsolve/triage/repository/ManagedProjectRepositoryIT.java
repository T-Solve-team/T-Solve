package vn.tsolve.triage.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.identity.signin.SignInTestFixtures;
import vn.tsolve.triage.dto.ManagedProject;
import vn.tsolve.triage.service.ManagedProjects;

/** Which Projects count as managed for the suggestion history: the import access rule, read on PostgreSQL. */
@SpringBootTest
@TestPropertySource(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=" + SignInTestFixtures.LOGIN_ORIGIN,
            "TSOLVE_ADMIN_ORIGIN=" + SignInTestFixtures.ADMIN_ORIGIN,
            "TSOLVE_SIGNING_KEY=" + SignInTestFixtures.KEY,
            "GOOGLE_CLIENT_ID=test-client-id",
            "GOOGLE_CLIENT_SECRET=test-secret",
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "CORS_ALLOWED_ORIGINS=http://localhost:5173",
            "server.port=0",
            "DB_HOST=unused",
            "DB_PORT=0",
            "DB_NAME=unused",
            "DB_USER=unused",
            "DB_PASSWORD=unused"
        })
class ManagedProjectRepositoryIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        DB.start();
    }

    private static final UUID PM = new UUID(347, 1);
    private static final UUID MEMBER = new UUID(347, 2);
    private static final UUID DEACTIVATED = new UUID(347, 3);
    private static final UUID NO_ROLE = new UUID(347, 4);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ManagedProjectRepository repository;

    @Autowired
    private ManagedProjects managedProjects;

    private UUID itsup;
    private UUID itnet;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM app_user WHERE email LIKE '%@mp-fixture.test'");
        jdbc.update("DELETE FROM project WHERE jira_project_key LIKE 'MP%'");
        jdbc.update("DELETE FROM department WHERE name LIKE 'MP %'");
        long it = department("MP IT", "ACTIVE");
        long gone = department("MP Gone", "DELETED");
        itsup = project(it, "MPSUP", "ACTIVE");
        itnet = project(it, "MPNET", "ACTIVE");
        UUID removed = project(it, "MPOLD", "REMOVED");
        UUID inDeletedDepartment = project(gone, "MPGONE", "ACTIVE");
        UUID ofDeactivated = project(it, "MPDEA", "ACTIVE");
        UUID ofNoRole = project(it, "MPNOR", "ACTIVE");

        user(PM, "ACTIVE", true);
        user(MEMBER, "ACTIVE", true);
        user(DEACTIVATED, "DEACTIVATED", true);
        user(NO_ROLE, "ACTIVE", false);
        membership(PM, itsup, "PROJECT_MANAGER");
        membership(PM, itnet, "PROJECT_MANAGER");
        membership(PM, removed, "PROJECT_MANAGER");
        membership(PM, inDeletedDepartment, "PROJECT_MANAGER");
        membership(MEMBER, itsup, "MEMBER");
        membership(DEACTIVATED, ofDeactivated, "PROJECT_MANAGER");
        membership(NO_ROLE, ofNoRole, "PROJECT_MANAGER");
    }

    private long department(String name, String status) {
        return jdbc.queryForObject(
                "INSERT INTO department (name, status) VALUES (?, ?) RETURNING department_id",
                Long.class,
                name,
                status);
    }

    private UUID project(long department, String key, String status) {
        return jdbc.queryForObject(
                "INSERT INTO project (department_id, jira_project_key, status) VALUES (?, ?, ?) RETURNING public_id",
                UUID.class,
                department,
                key,
                status);
    }

    private void user(UUID id, String status, boolean projectManager) {
        jdbc.update(
                "INSERT INTO app_user (id, email, status, created_at) VALUES (?, ?, ?, now())",
                id,
                id + "@mp-fixture.test",
                status);
        if (projectManager) {
            jdbc.update("INSERT INTO user_role (user_id, role, assigned_at) VALUES (?, 'PROJECT_MANAGER', now())", id);
        }
    }

    private void membership(UUID user, UUID project, String role) {
        jdbc.update(
                "INSERT INTO project_membership (user_id, project_id, role_in_project) VALUES (?, ?, ?)",
                user,
                project,
                role);
    }

    private long id(UUID project) {
        return jdbc.queryForObject("SELECT project_id FROM project WHERE public_id = ?", Long.class, project);
    }

    @Test
    void returnsOnlyTheActiveProjectsInActiveDepartmentsByJiraKey_AC_003_1() {
        assertThat(repository.managedBy(PM))
                .containsExactly(
                        new ManagedProject(id(itnet), itnet.toString(), "MPNET"),
                        new ManagedProject(id(itsup), itsup.toString(), "MPSUP"));
    }

    @Test
    void returnsNoneForAMemberADeactivatedAccountOrAnAccountWithoutTheRole_AC_003_3() {
        assertThat(repository.managedBy(MEMBER)).isEmpty();
        assertThat(repository.managedBy(DEACTIVATED)).isEmpty();
        assertThat(repository.managedBy(NO_ROLE)).isEmpty();
    }

    @Test
    void readsTheSignedInAccountAndReturnsNoneForAUserThatIsNoAccountId_AC_004_1() {
        assertThat(managedProjects.of(PM.toString()))
                .extracting(ManagedProject::key)
                .containsExactly("MPNET", "MPSUP");
        assertThat(managedProjects.of("not-a-uuid")).isEmpty();
        assertThat(managedProjects.of(null)).isEmpty();
    }
}
