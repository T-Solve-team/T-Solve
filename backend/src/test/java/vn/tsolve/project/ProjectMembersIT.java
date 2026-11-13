package vn.tsolve.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.TwoTenantDatabases;
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
@WithTSolveUser(id = "00000000-0000-0080-0000-000000000401", roles = Role.PROJECT_MANAGER)
class ProjectMembersIT {
    private static final String FIXTURE = "@kan128-fixture.test";
    private static final UUID DEPARTMENT = new UUID(128, 101);
    private static final UUID PROJECT = new UUID(128, 301);
    private static final UUID REMOVED = new UUID(128, 302);
    private static final UUID OTHER = new UUID(128, 303);
    private static final UUID PM = UUID.fromString("00000000-0000-0080-0000-000000000401");
    private static final String PM_ID = "00000000-0000-0080-0000-000000000401";
    private static final UUID OTHER_PM = UUID.fromString("00000000-0000-0080-0000-000000000402");
    private static final String OTHER_PM_ID = "00000000-0000-0080-0000-000000000402";
    private static final UUID ALICE = UUID.fromString("00000000-0000-0080-0000-000000000403");
    private static final String ALICE_ID = "00000000-0000-0080-0000-000000000403";
    private static final UUID NAMELESS_MEMBER = new UUID(128, 404);
    private static final UUID BOB = new UUID(128, 405);
    private static final UUID CAROL = new UUID(128, 406);
    private static final UUID DAVE = new UUID(128, 407);
    private static final UUID NAMELESS = new UUID(128, 408);
    private static final UUID INACTIVE = new UUID(128, 409);
    private static final UUID PM_ONLY = new UUID(128, 410);
    private static final UUID EMPLOYEE = new UUID(128, 411);
    private static final UUID BETA_RESOLVER = new UUID(128, 412);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantScope scope;

    @Autowired
    private ObjectMapper mapper;

    private final JdbcTemplate db = TwoTenantDatabases.tenantA();

    @BeforeEach
    void seed() {
        for (JdbcTemplate tenant : List.of(TwoTenantDatabases.tenantA(), TwoTenantDatabases.tenantB())) {
            new TransactionTemplate(new DataSourceTransactionManager(tenant.getDataSource()))
                    .executeWithoutResult(tx -> {
                        tenant.execute("ALTER TABLE audit_log_entry DISABLE TRIGGER audit_log_entry_no_update_delete");
                        tenant.update(
                                "DELETE FROM audit_log_entry WHERE target_id IN (?,?,?)", PROJECT, REMOVED, OTHER);
                        tenant.execute("ALTER TABLE audit_log_entry ENABLE TRIGGER audit_log_entry_no_update_delete");
                    });
            tenant.update("DELETE FROM app_user WHERE email LIKE ?", "%" + FIXTURE);
            tenant.update("DELETE FROM project_membership WHERE project_id IN (?,?,?)", PROJECT, REMOVED, OTHER);
            tenant.update("DELETE FROM project WHERE public_id IN (?,?,?)", PROJECT, REMOVED, OTHER);
            tenant.update("DELETE FROM department WHERE public_id = ?", DEPARTMENT);
        }
        db.update("INSERT INTO department(public_id,name,status) VALUES (?,?,'ACTIVE')", DEPARTMENT, "KAN128 Dept");
        project(PROJECT, "K128A", "ACTIVE");
        project(REMOVED, "K128B", "REMOVED");
        project(OTHER, "K128C", "ACTIVE");
        user(db, PM, "Zed Manager", "pm", "ACTIVE", "PROJECT_MANAGER");
        user(db, OTHER_PM, "Other Manager", "other-pm", "ACTIVE", "PROJECT_MANAGER");
        user(db, ALICE, "alice Member", "alice", "ACTIVE", "TICKET_RESOLVER");
        user(db, NAMELESS_MEMBER, null, "nameless-member", "ACTIVE", "TICKET_RESOLVER");
        user(db, BOB, "Bob Member", "bob", "ACTIVE", "TICKET_RESOLVER");
        user(db, CAROL, "Carol 100%_off", "carol", "ACTIVE", "TICKET_RESOLVER");
        user(db, DAVE, "dave Candidate", "dave", "ACTIVE", "TICKET_RESOLVER");
        user(db, NAMELESS, null, "zz-nameless", "ACTIVE", "TICKET_RESOLVER");
        user(db, INACTIVE, "Ivy Inactive", "ivy", "DEACTIVATED", "TICKET_RESOLVER");
        user(db, PM_ONLY, "Paul Manager", "paul", "ACTIVE", "PROJECT_MANAGER");
        user(db, EMPLOYEE, "Eve Employee", "eve", "ACTIVE");
        membership(PM, PROJECT, "PROJECT_MANAGER");
        membership(PM, REMOVED, "PROJECT_MANAGER");
        membership(OTHER_PM, OTHER, "PROJECT_MANAGER");
        membership(ALICE, PROJECT, "MEMBER");
        membership(NAMELESS_MEMBER, PROJECT, "MEMBER");
        membership(BOB, PROJECT, "MEMBER");
        // Another Workspace's database: never a candidate here.
        user(TwoTenantDatabases.tenantB(), BETA_RESOLVER, "Beta Resolver", "beta", "ACTIVE", "TICKET_RESOLVER");
    }

    @Test
    void returnsProjectHeader_AC_001_1() throws Exception {
        perform(get("/api/v1/projects/" + PROJECT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PROJECT.toString()))
                .andExpect(jsonPath("$.jiraProjectKey").value("K128A"))
                .andExpect(jsonPath("$.departmentName").value("KAN128 Dept"))
                .andExpect(jsonPath("$.projectManagerName").value("Zed Manager"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        perform(get("/api/v1/projects/" + REMOVED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REMOVED"));
    }

    @Test
    void listsManagerFirstThenMembersByNameIgnoringCaseNamelessLast_AC_001_2() throws Exception {
        JsonNode body =
                json(perform(get("/api/v1/projects/" + PROJECT + "/members")).andExpect(status().isOk()));
        assertThat(ids(body, "userId")).containsExactly(PM, ALICE, BOB, NAMELESS_MEMBER);
        JsonNode manager = body.get(0);
        assertThat(manager.get("fullName").asText()).isEqualTo("Zed Manager");
        assertThat(manager.get("email").asText()).isEqualTo("pm" + FIXTURE);
        assertThat(manager.get("role").asText()).isEqualTo("PROJECT_MANAGER");
        assertThat(Instant.parse(manager.get("dateAdded").asText())).isNotNull();
        assertThat(body.get(1).get("role").asText()).isEqualTo("MEMBER");
        assertThat(body.get(3).get("fullName").isNull()).isTrue();
    }

    @Test
    @WithTSolveUser(
            id = ALICE_ID,
            roles = {Role.PROJECT_MANAGER, Role.TICKET_RESOLVER})
    void refusesMemberOfTheProject_AC_001_3() throws Exception {
        // Design E1: a MEMBER of the Project, even one whose session carries the Project Manager role.
        expectForbiddenEverywhere(PROJECT);
    }

    @Test
    @WithTSolveUser(id = OTHER_PM_ID, roles = Role.PROJECT_MANAGER)
    void refusesManagerOfAnotherProjectInSameDepartment_AC_001_3() throws Exception {
        // Design E2.
        expectForbiddenEverywhere(PROJECT);
    }

    @Test
    void refusesDeactivatedManagerOrManagerWithoutRole_AC_001_3() throws Exception {
        // Design E3: the membership row is left behind.
        db.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", PM);
        expectForbiddenEverywhere(PROJECT);
        db.update("UPDATE app_user SET status = 'ACTIVE' WHERE id = ?", PM);
        db.update("DELETE FROM user_role WHERE user_id = ?", PM);
        expectForbiddenEverywhere(PROJECT);
    }

    @Test
    @WithTSolveUser(id = PM_ID, roles = Role.TICKET_RESOLVER)
    void refusesCallerWithoutProjectManagerRole_AC_001_3() throws Exception {
        expectForbiddenEverywhere(PROJECT);
    }

    @Test
    void answersNotFoundForUnknownProject_AC_001_4() throws Exception {
        // Design E11: unknown id → 404; malformed id → 400 from the framework.
        UUID unknown = UUID.randomUUID();
        perform(get("/api/v1/projects/" + unknown)).andExpect(status().isNotFound());
        perform(get("/api/v1/projects/" + unknown + "/members")).andExpect(status().isNotFound());
        perform(get("/api/v1/projects/" + unknown + "/member-candidates")).andExpect(status().isNotFound());
        perform(add(unknown, List.of(CAROL))).andExpect(status().isNotFound());
        perform(get("/api/v1/projects/not-a-uuid/members")).andExpect(status().isBadRequest());
        assertThat(memberCount(unknown)).isZero();
    }

    @Test
    void searchesActiveResolversNotYetMembersByNameOrEmail_AC_002_1() throws Exception {
        // Design E7: a Project Manager-only account is never a candidate.
        assertThat(candidates("  CAROL ")).containsExactly(CAROL);
        assertThat(candidates("dave" + FIXTURE.toUpperCase())).containsExactly(DAVE);
        assertThat(candidates("member")).isEmpty();
        assertThat(candidates("paul")).isEmpty();
        assertThat(candidates("ivy")).isEmpty();
        assertThat(candidates("eve")).isEmpty();
        assertThat(candidates("beta")).isEmpty();
        JsonNode body = json(perform(get("/api/v1/projects/" + PROJECT + "/member-candidates?q=carol"))
                .andExpect(status().isOk()));
        assertThat(body.get(0).get("fullName").asText()).isEqualTo("Carol 100%_off");
        assertThat(body.get(0).get("email").asText()).isEqualTo("carol" + FIXTURE);
    }

    @Test
    void matchesPercentAndUnderscoreLiterally_AC_002_1() throws Exception {
        // Design E8.
        assertThat(candidates("100%_")).containsExactly(CAROL);
        assertThat(candidates("%")).containsExactly(CAROL);
        assertThat(candidates("_")).containsExactly(CAROL);
        assertThat(candidates("0%o")).isEmpty();
    }

    @Test
    void returnsAtMost20CandidatesSortedByNameNamelessLast_AC_002_1() throws Exception {
        for (int i = 0; i < 20; i++) {
            user(db, new UUID(128, 600 + i), "Extra " + (char) ('a' + i), "extra" + i, "ACTIVE", "TICKET_RESOLVER");
        }
        // At least 22 named candidates exist now, so the nameless one falls outside the first 20.
        assertThat(candidateBody("")).hasSize(20);
        assertThat(candidates("")).doesNotContain(NAMELESS);
        db.update("DELETE FROM app_user WHERE email LIKE 'extra%'");
        assertThat(candidates("")).containsExactly(CAROL, DAVE, NAMELESS);
    }

    @Test
    void appliesNoFilterWhenQueryMissingOrBlank_AC_002_2() throws Exception {
        assertThat(candidates(null)).containsExactly(CAROL, DAVE, NAMELESS);
        assertThat(candidates("   ")).containsExactly(CAROL, DAVE, NAMELESS);
    }

    @Test
    void rejectsQueryLongerThan100AfterTrim_AC_002_3() throws Exception {
        perform(get("/api/v1/projects/" + PROJECT + "/member-candidates").param("q", "x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG02"))
                .andExpect(jsonPath("$[0]").doesNotExist());
        assertThat(candidates("  " + "x".repeat(100) + "  ")).isEmpty();
    }

    @Test
    void addsMembersWithDatabaseTimeAndReturnsFullList_AC_002_4() throws Exception {
        Instant before = db.queryForObject("SELECT now()", Timestamp.class).toInstant();
        JsonNode body =
                json(perform(add(PROJECT, List.of(NAMELESS, DAVE, CAROL))).andExpect(status().isCreated()));
        assertThat(ids(body, "userId")).containsExactly(PM, ALICE, BOB, CAROL, DAVE, NAMELESS_MEMBER, NAMELESS);
        for (UUID added : List.of(CAROL, DAVE, NAMELESS)) {
            Map<String, Object> row = db.queryForMap(
                    "SELECT role_in_project, date_added FROM project_membership WHERE user_id = ? AND project_id = ?",
                    added,
                    PROJECT);
            assertThat(row).containsEntry("role_in_project", "MEMBER");
            Instant dateAdded = ((Timestamp) row.get("date_added")).toInstant();
            assertThat(dateAdded).isAfterOrEqualTo(before).isBefore(before.plus(Duration.ofMinutes(1)));
        }
        assertThat(candidates("")).isEmpty();
    }

    @Test
    void recordsOneAuditEntryWithIdsInOrderSent_AC_002_5() throws Exception {
        perform(add(PROJECT, List.of(DAVE, CAROL))).andExpect(status().isCreated());
        List<Map<String, Object>> audits = db.queryForList("""
                SELECT action, target_type, actor_id, details ->> 'added' AS added
                FROM audit_log_entry WHERE target_id = ?
                """, PROJECT);
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0))
                .containsEntry("action", "PROJECT_MEMBER_CHANGE")
                .containsEntry("target_type", "PROJECT")
                .containsEntry("actor_id", PM);
        assertThat(mapper.readValue((String) audits.get(0).get("added"), String[].class))
                .containsExactly(DAVE.toString(), CAROL.toString());
    }

    @Test
    void rejectsMissingEmptyTooLongDuplicateOrNullIds_AC_002_6() throws Exception {
        // Design E4 is the duplicate case.
        List<String> bodies = new ArrayList<>(List.of("{}", "{\"userIds\":[]}", "{\"userIds\":null}"));
        bodies.add(mapper.writeValueAsString(
                Map.of("userIds", Stream.generate(UUID::randomUUID).limit(51).toList())));
        bodies.add(mapper.writeValueAsString(Map.of("userIds", List.of(CAROL, DAVE, CAROL))));
        Map<String, Object> withNull = new HashMap<>();
        withNull.put("userIds", Arrays.asList(CAROL, null));
        bodies.add(mapper.writeValueAsString(withNull));
        for (String body : bodies) {
            perform(post("/api/v1/projects/" + PROJECT + "/members")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(memberCount(PROJECT)).isEqualTo(4);
        assertThat(auditCount(PROJECT)).isZero();
    }

    @Test
    void refusesWholeRequestWhenAnyIdCannotBeAdded_AC_002_7() throws Exception {
        // Design E6 (deactivated after the search) and E7 (Project Manager-only account).
        db.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", DAVE);
        for (UUID refused : List.of(DAVE, INACTIVE, PM_ONLY, EMPLOYEE, ALICE, PM, BETA_RESOLVER, UUID.randomUUID())) {
            perform(add(PROJECT, List.of(CAROL, refused)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MSG06"));
        }
        assertThat(memberCount(PROJECT)).isEqualTo(4);
        assertThat(auditCount(PROJECT)).isZero();
    }

    @Test
    void refusesAddToRemovedProject_AC_002_8() throws Exception {
        // Design E9.
        perform(add(REMOVED, List.of(CAROL))).andExpect(status().isConflict());
        assertThat(memberCount(REMOVED)).isEqualTo(1);
        assertThat(auditCount(REMOVED)).isZero();
    }

    private void expectForbiddenEverywhere(UUID project) throws Exception {
        perform(get("/api/v1/projects/" + project))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"))
                .andExpect(jsonPath("$.jiraProjectKey").doesNotExist());
        perform(get("/api/v1/projects/" + project + "/members"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$[0]").doesNotExist());
        perform(get("/api/v1/projects/" + project + "/member-candidates?q=carol"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$[0]").doesNotExist());
        perform(add(project, List.of(CAROL))).andExpect(status().isForbidden());
        assertThat(memberCount(project)).isEqualTo(4);
        assertThat(auditCount(project)).isZero();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) {
        return scope.runIn(TwoTenantDatabases.ACME, () -> {
            try {
                return mvc.perform(request);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private MockHttpServletRequestBuilder add(UUID project, List<UUID> userIds) throws Exception {
        return post("/api/v1/projects/" + project + "/members")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("userIds", userIds)));
    }

    // Candidates are Workspace-wide and tenant A is shared with other ITs: only this class's accounts are compared.
    private List<UUID> candidates(String q) throws Exception {
        List<UUID> ids = new ArrayList<>();
        candidateBody(q).forEach(node -> {
            if (node.get("email").asText().endsWith(FIXTURE)) {
                ids.add(UUID.fromString(node.get("userId").asText()));
            }
        });
        return ids;
    }

    private JsonNode candidateBody(String q) throws Exception {
        var request = get("/api/v1/projects/" + PROJECT + "/member-candidates");
        if (q != null) {
            request.param("q", q);
        }
        return json(perform(request).andExpect(status().isOk()));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static List<UUID> ids(JsonNode body, String field) {
        List<UUID> ids = new ArrayList<>();
        body.forEach(node -> ids.add(UUID.fromString(node.get(field).asText())));
        return ids;
    }

    private int memberCount(UUID project) {
        return db.queryForObject(
                "SELECT count(*) FROM project_membership WHERE project_id = ?", Integer.class, project);
    }

    private int auditCount(UUID project) {
        return db.queryForObject("SELECT count(*) FROM audit_log_entry WHERE target_id = ?", Integer.class, project);
    }

    private void project(UUID id, String key, String status) {
        db.update(
                "INSERT INTO project(public_id,department_id,jira_project_key,status) SELECT ?,department_id,?,? FROM"
                        + " department WHERE public_id = ?",
                id,
                key,
                status,
                DEPARTMENT);
    }

    private static void user(JdbcTemplate db, UUID id, String fullName, String email, String status, String... roles) {
        db.update(
                "INSERT INTO app_user(id,email,full_name,status,created_at) VALUES (?,?,?,?,now())",
                id,
                email + FIXTURE,
                fullName,
                status);
        for (String role : roles) {
            db.update("INSERT INTO user_role(user_id,role,assigned_at) VALUES (?,?,now())", id, role);
        }
    }

    private void membership(UUID account, UUID project, String role) {
        db.update(
                "INSERT INTO project_membership(user_id,project_id,role_in_project) VALUES (?,?,?)",
                account,
                project,
                role);
    }
}
