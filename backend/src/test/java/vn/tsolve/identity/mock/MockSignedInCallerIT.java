package vn.tsolve.identity.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.identity.TwoTenantDatabases;

@SpringBootTest(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=http://127.0.0.1:9",
            "TSOLVE_ADMIN_ORIGIN=http://admin.localhost:8081",
            "TSOLVE_SIGNING_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "GOOGLE_CLIENT_ID=test-client-id",
            "GOOGLE_CLIENT_SECRET=test-secret",
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "CORS_ALLOWED_ORIGINS=http://localhost:5173",
            "server.port=0",
            "DB_HOST=unused",
            "DB_PORT=0",
            "DB_NAME=unused",
            "DB_USER=unused",
            "DB_PASSWORD=unused",
            "tsolve.identity.mock.workspace-id=00000000-0000-4000-8000-00000000000a"
        })
@AutoConfigureMockMvc
@ActiveProfiles("identity-mock")
@Import(TwoTenantDatabases.class)
class MockSignedInCallerIT {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private MockUserListSeeder seeder;

    @Autowired
    private MockIdentityProperties properties;

    @Test
    void seedsSecondKnowledgeManagerWithoutDepartmentOrSpace_AC_006_19() throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        var row = db.queryForMap(
                "SELECT id, full_name, status, department_id FROM app_user WHERE email = 'knowledge-replacement@roles-demo.test'");
        assertThat(row.get("full_name")).isEqualTo("Demo Replacement Knowledge Manager");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("department_id")).isNull();
        assertThat(db.queryForList("SELECT role FROM user_role WHERE user_id = ?", String.class, row.get("id")))
                .containsExactly("KNOWLEDGE_MANAGER");
        assertThat(db.queryForObject(
                        "SELECT count(*) FROM space_manager WHERE user_id = ?", Integer.class, row.get("id")))
                .isZero();
        seeder.run(new DefaultApplicationArguments());
        assertThat(
                        db.queryForMap(
                                "SELECT id, full_name, status, department_id FROM app_user WHERE email = 'knowledge-replacement@roles-demo.test'"))
                .isEqualTo(row);
        assertThat(TwoTenantDatabases.tenantB()
                        .queryForObject(
                                "SELECT count(*) FROM app_user WHERE email = 'knowledge-replacement@roles-demo.test'",
                                Integer.class))
                .isZero();
    }

    @Test
    void seededSpacesAppearInCreationOptions_AC_005_14() throws Exception {
        mvc.perform(get("/api/v1/users/creation-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyDomain").value("acme.test"))
                .andExpect(jsonPath("$.spaces[?(@.name == 'Customer Support Wiki')].spaceId")
                        .isNotEmpty())
                .andExpect(jsonPath("$.spaces[?(@.name == 'IT Operations Wiki')].spaceId")
                        .isNotEmpty());
    }

    @Test
    void signsInFromSeededTenantThroughDefaultSecurityChain_AC_004_17_AC_004_18() throws Exception {
        assertThat(properties.workspaceId()).isEqualTo(TwoTenantDatabases.ACME);
        mvc.perform(get("/api/v1/users").param("q", "@acme.test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(7));
        assertThat(TwoTenantDatabases.tenantB()
                        .queryForObject("SELECT count(*) FROM app_user WHERE email = 'admin@acme.test'", Integer.class))
                .isZero();
    }

    @Test
    void seedingTwicePreservesRowsAndIds_AC_004_18() throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        List<Map<String, Object>> accounts = db.queryForList("SELECT * FROM app_user ORDER BY id");
        List<Map<String, Object>> roles = db.queryForList("SELECT * FROM user_role ORDER BY id");
        List<Map<String, Object>> departments = db.queryForList("SELECT * FROM department ORDER BY department_id");
        seeder.run(new DefaultApplicationArguments());
        seeder.run(new DefaultApplicationArguments());
        assertThat(db.queryForList("SELECT * FROM app_user ORDER BY id")).isEqualTo(accounts);
        assertThat(db.queryForList("SELECT * FROM user_role ORDER BY id")).isEqualTo(roles);
        assertThat(db.queryForList("SELECT * FROM department ORDER BY department_id"))
                .isEqualTo(departments);
    }

    @Test
    void deactivatedCallerIsNotAuthenticated_AC_004_17() throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        db.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE email = ?", properties.accountEmail());
        try {
            mvc.perform(get("/api/v1/users"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("MSG07"));
        } finally {
            db.update("UPDATE app_user SET status = 'ACTIVE' WHERE email = ?", properties.accountEmail());
        }
    }

    @Test
    void storedRoleChangesApplyOnNextRequest_AC_004_17() throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        db.update(
                "DELETE FROM user_role WHERE user_id = (SELECT id FROM app_user WHERE email = ?) AND role = 'ADMIN'",
                properties.accountEmail());
        try {
            mvc.perform(get("/api/v1/users"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("MSG08"));
        } finally {
            seeder.run(new DefaultApplicationArguments());
        }
    }

    @Test
    void seedsProjectHandoverAndMembershipScenariosOnlyOnce_AC_007_23() throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        List<Map<String, Object>> projects = db.queryForList(
                "SELECT * FROM project WHERE jira_project_key IN ('SUP', 'OPS', 'OLD') ORDER BY jira_project_key");
        List<Map<String, Object>> memberships = db.queryForList(
                "SELECT m.* FROM project_membership m JOIN project p ON p.public_id = m.project_id WHERE p.jira_project_key IN ('SUP', 'OPS', 'OLD') ORDER BY m.user_id, m.project_id");
        assertThat(projects).hasSize(3);
        assertThat(projects.stream().map(row -> row.get("status"))).containsExactly("REMOVED", "ACTIVE", "ACTIVE");
        assertThat(memberships).hasSize(6);
        assertThat(db.queryForObject(
                        "SELECT count(*) FROM app_user u JOIN user_role r ON r.user_id = u.id JOIN department d ON d.department_id = u.department_id WHERE u.email = 'manager-replacement@roles-demo.test' AND u.status = 'ACTIVE' AND r.role = 'PROJECT_MANAGER' AND d.name = 'Customer Support'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(db.queryForObject(
                        "SELECT count(*) FROM app_user u JOIN user_role r ON r.user_id = u.id JOIN department d ON d.department_id = u.department_id WHERE u.email <> 'manager@acme.test' AND u.status = 'ACTIVE' AND r.role = 'PROJECT_MANAGER' AND d.name = 'IT Operations'",
                        Integer.class))
                .isZero();
        seeder.run(new DefaultApplicationArguments());
        assertThat(
                        db.queryForList(
                                "SELECT * FROM project WHERE jira_project_key IN ('SUP', 'OPS', 'OLD') ORDER BY jira_project_key"))
                .isEqualTo(projects);
        assertThat(
                        db.queryForList(
                                "SELECT m.* FROM project_membership m JOIN project p ON p.public_id = m.project_id WHERE p.jira_project_key IN ('SUP', 'OPS', 'OLD') ORDER BY m.user_id, m.project_id"))
                .isEqualTo(memberships);
    }
}
