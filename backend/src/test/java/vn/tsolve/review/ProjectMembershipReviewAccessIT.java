package vn.tsolve.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.review.service.impl.ProjectMembershipReviewAccess;

/** The production Review scope from project_membership (STORY-009, design "Project membership adapter"). */
@SpringBootTest(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=http://login.localhost:8081",
            "TSOLVE_ADMIN_ORIGIN=http://admin.localhost:8081",
            "TSOLVE_SIGNING_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "GOOGLE_CLIENT_ID=test-client-id",
            "GOOGLE_CLIENT_SECRET=test-secret",
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "CORS_ALLOWED_ORIGINS=http://localhost:5173",
            "server.port=0"
        })
@Import(TwoTenantDatabases.class)
class ProjectMembershipReviewAccessIT {

    private static final UUID MANAGER = UUID.fromString("5d1e7a1c-0b7e-4f4e-9a55-1f6f2c9b1a01");
    private static final UUID INACTIVE = UUID.fromString("5d1e7a1c-0b7e-4f4e-9a55-1f6f2c9b1a02");
    private static final UUID MANAGED = UUID.fromString("5d1e7a1c-0b7e-4f4e-9a55-1f6f2c9b1b01");
    private static final UUID OTHER = UUID.fromString("5d1e7a1c-0b7e-4f4e-9a55-1f6f2c9b1b02");
    private static final UUID CLOSED = UUID.fromString("5d1e7a1c-0b7e-4f4e-9a55-1f6f2c9b1b03");
    private static final UUID DEPARTMENT = UUID.fromString("5d1e7a1c-0b7e-4f4e-9a55-1f6f2c9b1c01");

    @Autowired
    private ProjectMembershipReviewAccess access;

    @Autowired
    private TwoTenantDatabases.RoutingTenantScope scope;

    private static long managedNumericId;

    @BeforeAll
    static void seed() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        db.update(
                "insert into app_user (id, email, status, created_at) values (?, 'pm-membership@example.test', 'ACTIVE', now())",
                MANAGER);
        db.update(
                "insert into app_user (id, email, status, created_at) values (?, 'pm-gone@example.test', 'DEACTIVATED', now())",
                INACTIVE);
        Long department = db.queryForObject(
                "insert into department (name, public_id) values ('Membership IT', ?) returning department_id",
                Long.class,
                DEPARTMENT);
        managedNumericId = project(db, department, "MEMB", MANAGED, "ACTIVE");
        project(db, department, "MEMO", OTHER, "ACTIVE");
        project(db, department, "MEMC", CLOSED, "INACTIVE");
        db.update(
                "insert into project_membership (user_id, project_id, role_in_project) values (?, ?, 'PROJECT_MANAGER')",
                MANAGER,
                MANAGED);
        db.update(
                "insert into project_membership (user_id, project_id, role_in_project) values (?, ?, 'MEMBER')",
                MANAGER,
                OTHER);
        db.update(
                "insert into project_membership (user_id, project_id, role_in_project) values (?, ?, 'PROJECT_MANAGER')",
                INACTIVE,
                OTHER);
    }

    private static long project(JdbcTemplate db, Long department, String key, UUID publicId, String status) {
        return db.queryForObject(
                "insert into project (department_id, jira_project_key, public_id, status) values (?, ?, ?, ?)"
                        + " returning project_id",
                Long.class,
                department,
                key,
                publicId,
                status);
    }

    @Test
    void managesOnlyProjectsWithAProjectManagerMembership_AC_009_1() {
        var managed = scope.runIn(TwoTenantDatabases.ACME, () -> access.managedProjectIds(MANAGER));

        assertThat(managed).hasValueSatisfying(ids -> assertThat(ids)
                .contains(MANAGED, legacy(managedNumericId))
                .doesNotContain(OTHER, CLOSED));
    }

    @Test
    void readsActiveWorkspaceProjectsWithTheirDepartmentAndManagedFlag_AC_009_2() {
        var readable = scope.runIn(TwoTenantDatabases.ACME, () -> access.readableProjects(MANAGER))
                .orElseThrow();

        assertThat(readable.get(MANAGED).managed()).isTrue();
        assertThat(readable.get(MANAGED).project().projectId()).isEqualTo(MANAGED);
        assertThat(readable.get(MANAGED).project().name()).isEqualTo("MEMB");
        assertThat(readable.get(MANAGED).departmentId()).isEqualTo(DEPARTMENT);
        assertThat(readable.get(legacy(managedNumericId))).isEqualTo(readable.get(MANAGED));
        assertThat(readable.get(OTHER).managed()).isFalse();
        assertThat(readable).doesNotContainKey(CLOSED);
    }

    @Test
    void deniesAnInactiveUnknownOrForeignAccount_AC_009_3() {
        assertThat(scope.runIn(TwoTenantDatabases.ACME, () -> access.managedProjectIds(INACTIVE)))
                .isEmpty();
        assertThat(scope.runIn(TwoTenantDatabases.ACME, () -> access.readableProjects(UUID.randomUUID())))
                .isEmpty();
        assertThat(scope.runIn(TwoTenantDatabases.BETA, () -> access.managedProjectIds(MANAGER)))
                .isEmpty();
    }

    private static UUID legacy(long projectId) {
        return new UUID(0, projectId);
    }
}
