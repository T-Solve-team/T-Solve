package vn.tsolve.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.signin.port.TenantScope;

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
            "DB_PASSWORD=unused"
        })
@AutoConfigureMockMvc
@Import(TwoTenantDatabases.class)
@WithTSolveUser(roles = Role.ADMIN)
class UserDetailIT {
    private static final UUID DEPARTMENT = new UUID(110, 201);
    private static final UUID OTHER_DEPARTMENT = new UUID(110, 202);
    private static final UUID TARGET = new UUID(110, 211);
    private static final UUID PM = new UUID(110, 212);
    private static final UUID KM = new UUID(110, 213);
    private static final UUID WRONG_PM = new UUID(110, 214);
    private static final UUID INACTIVE = new UUID(110, 215);
    private static final UUID MEMBER_PROJECT = new UUID(110, 221);
    private static final UUID ACTIVE_PROJECT = new UUID(110, 222);
    private static final UUID REMOVED_PROJECT = new UUID(110, 223);
    private static final UUID UNMANAGED_PROJECT = new UUID(110, 224);
    private static final UUID SPACE = new UUID(110, 231);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantScope scope;

    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void seed() {
        for (JdbcTemplate db : List.of(TwoTenantDatabases.tenantA(), TwoTenantDatabases.tenantB())) {
            db.update("DELETE FROM app_user WHERE email LIKE '%@detail-fixture.test'");
            db.update("DELETE FROM project WHERE jira_project_key LIKE 'DETAIL-%'");
            db.update("DELETE FROM space WHERE public_id = ?", SPACE);
            db.update("DELETE FROM department WHERE public_id IN (?, ?)", DEPARTMENT, OTHER_DEPARTMENT);
            db.update(
                    "INSERT INTO department(public_id,name,status) VALUES (?, 'Detail A', 'ACTIVE'), (?, 'Detail B', 'ACTIVE')",
                    DEPARTMENT,
                    OTHER_DEPARTMENT);
        }
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        user(db, TARGET, "Target", DEPARTMENT, "ACTIVE", "PROJECT_MANAGER", "KNOWLEDGE_MANAGER", "TICKET_RESOLVER");
        user(db, PM, "alpha", DEPARTMENT, "ACTIVE", "PROJECT_MANAGER");
        user(db, KM, "Beta", OTHER_DEPARTMENT, "ACTIVE", "KNOWLEDGE_MANAGER");
        user(db, WRONG_PM, "Other Department", OTHER_DEPARTMENT, "ACTIVE", "PROJECT_MANAGER");
        user(db, INACTIVE, "Inactive", DEPARTMENT, "DEACTIVATED", "PROJECT_MANAGER", "KNOWLEDGE_MANAGER");
        project(db, MEMBER_PROJECT, "DETAIL-member", "REMOVED", PM);
        project(db, ACTIVE_PROJECT, "DETAIL-active", "ACTIVE", TARGET);
        project(db, REMOVED_PROJECT, "DETAIL-old", "REMOVED", TARGET);
        project(db, UNMANAGED_PROJECT, "DETAIL-unmanaged", "ACTIVE", null);
        db.update(
                "INSERT INTO project_membership(user_id,project_id,role_in_project) VALUES (?,?,'MEMBER'), (?,?,'MEMBER')",
                TARGET,
                MEMBER_PROJECT,
                TARGET,
                UNMANAGED_PROJECT);
        db.update(
                "INSERT INTO space(public_id,department_id,name,status) SELECT ?,department_id,'Detail hidden space','HIDDEN' FROM department WHERE public_id = ?",
                SPACE,
                DEPARTMENT);
        db.update(
                "INSERT INTO space_manager(space_id,user_id,assigned_at) SELECT space_id,?,now() FROM space WHERE public_id = ?",
                TARGET,
                SPACE);
        user(
                TwoTenantDatabases.tenantB(),
                PM,
                "Beta secret",
                DEPARTMENT,
                "ACTIVE",
                "PROJECT_MANAGER",
                "KNOWLEDGE_MANAGER");
    }

    private void user(JdbcTemplate db, UUID id, String name, UUID department, String status, String... roles) {
        db.update(
                "INSERT INTO app_user(id,email,full_name,status,created_at,department_id) SELECT ?,?,?,?,now(),department_id FROM department WHERE public_id = ?",
                id,
                id + "@detail-fixture.test",
                name,
                status,
                department);
        for (String role : roles) {
            db.update("INSERT INTO user_role(user_id,role,assigned_at) VALUES (?,?,now())", id, role);
        }
    }

    private void project(JdbcTemplate db, UUID id, String key, String status, UUID manager) {
        db.update(
                "INSERT INTO project(public_id,department_id,jira_project_key,status) SELECT ?,department_id,?,? FROM department WHERE public_id = ?",
                id,
                key,
                status,
                DEPARTMENT);
        if (manager != null) {
            db.update(
                    "INSERT INTO project_membership(user_id,project_id,role_in_project) VALUES (?,?,'PROJECT_MANAGER')",
                    manager,
                    id);
        }
    }

    private ResultActions perform(String suffix) {
        return scope.runIn(TwoTenantDatabases.ACME, () -> {
            try {
                return mvc.perform(get("/api/v1/users/" + TARGET + suffix));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private JsonNode body(String suffix) throws Exception {
        return mapper.readTree(perform(suffix)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    @Test
    void returnsContractCurrentMembershipsHiddenSpacesAndUnmanagedResolverReferences_AC_007_8() throws Exception {
        JsonNode detail = body("");
        assertThat(detail.get("id").asText()).isEqualTo(TARGET.toString());
        assertThat(detail.get("isCurrentUser").asBoolean()).isFalse();
        assertThat(detail.get("lastSignInAt").isNull()).isTrue();
        assertThat(detail.get("roles").toString())
                .contains("EMPLOYEE", "PROJECT_MANAGER", "KNOWLEDGE_MANAGER", "TICKET_RESOLVER");
        assertThat(detail.get("projects").size()).isEqualTo(3);
        assertThat(detail.at("/projects/0/projectId").asText()).isEqualTo(ACTIVE_PROJECT.toString());
        assertThat(detail.at("/projects/0/name").asText()).isEqualTo("DETAIL-active");
        assertThat(detail.at("/projects/0/department/departmentId").asText()).isEqualTo(DEPARTMENT.toString());
        assertThat(detail.at("/projects/0/isActive").asBoolean()).isTrue();
        assertThat(detail.at("/projects/0/managerId").asText()).isEqualTo(TARGET.toString());
        assertThat(detail.get("ticketResolverProjectIds").toString())
                .contains(MEMBER_PROJECT.toString(), UNMANAGED_PROJECT.toString());
        assertThat(detail.at("/managedSpaces/0/spaceId").asText()).isEqualTo(SPACE.toString());
    }

    @Test
    void offersOnlyActiveSameDepartmentProjectManagersAndAnyDepartmentKnowledgeManagers_AC_007_9() throws Exception {
        JsonNode options = body("/handover-options");
        assertThat(options.get("projects").size()).isEqualTo(1);
        assertThat(options.at("/projects/0/project/projectId").asText()).isEqualTo(ACTIVE_PROJECT.toString());
        assertThat(options.at("/projects/0/recipients").size()).isEqualTo(1);
        assertThat(options.at("/projects/0/recipients/0/id").asText()).isEqualTo(PM.toString());
        JsonNode spaceRecipients = options.at("/spaces/0/recipients");
        assertThat(spaceRecipients.toString()).contains(KM.toString());
        for (JsonNode recipient : spaceRecipients) {
            assertThat(recipient.get("id").asText()).isNotEqualTo(TARGET.toString());
            assertThat(recipient.get("status").asText()).isEqualTo("ACTIVE");
            assertThat(recipient.get("roles").toString()).contains("KNOWLEDGE_MANAGER");
        }
        assertThat(options.toString())
                .doesNotContain("Beta secret", TARGET + "@detail-fixture.test", INACTIVE.toString());
    }

    @Test
    void neverReadsBetaOrAnUnboundDatabase_AC_007_22() throws Exception {
        assertThat(body("").toString()).doesNotContain("Beta secret");
        UUID betaOnly = new UUID(110, 299);
        user(TwoTenantDatabases.tenantB(), betaOnly, "Beta only", DEPARTMENT, "ACTIVE", "ADMIN");
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            try {
                mvc.perform(get("/api/v1/users/" + betaOnly))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("MSG04"));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            return null;
        });
        mvc.perform(get("/api/v1/users/" + TARGET))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }
}
