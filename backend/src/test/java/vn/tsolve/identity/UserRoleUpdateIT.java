package vn.tsolve.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
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
class UserRoleUpdateIT {
    private static final UUID TARGET = new UUID(110, 1);
    private static final UUID RECIPIENT = new UUID(110, 2);
    private static final UUID SECOND = new UUID(110, 3);
    private static final UUID BETA_USER = new UUID(110, 4);
    private static final UUID DEPARTMENT = new UUID(110, 10);
    private static final UUID OTHER_DEPARTMENT = new UUID(110, 11);
    private static final UUID BETA_DEPARTMENT = new UUID(110, 12);
    private static final UUID PROJECT = new UUID(110, 20);
    private static final UUID OLD_PROJECT = new UUID(110, 21);
    private static final UUID MEMBER_PROJECT = new UUID(110, 22);
    private static final UUID BETA_PROJECT = new UUID(110, 23);
    private static final UUID SPACE = new UUID(110, 30);
    private static final UUID FREE_SPACE = new UUID(110, 31);
    private static final UUID BETA_SPACE = new UUID(110, 32);
    private static final String DOMAIN = "@roles-fixture.test";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantScope scope;

    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void seed() {
        for (JdbcTemplate db : List.of(TwoTenantDatabases.tenantA(), TwoTenantDatabases.tenantB())) {
            db.update(
                    "DELETE FROM project_membership WHERE project_id IN (?, ?, ?, ?)",
                    PROJECT,
                    OLD_PROJECT,
                    MEMBER_PROJECT,
                    BETA_PROJECT);
            db.update(
                    "DELETE FROM space_manager WHERE space_id IN (SELECT space_id FROM space WHERE public_id IN (?, ?, ?))",
                    SPACE,
                    FREE_SPACE,
                    BETA_SPACE);
            db.update("DELETE FROM space WHERE public_id IN (?, ?, ?)", SPACE, FREE_SPACE, BETA_SPACE);
            db.update(
                    "DELETE FROM project WHERE public_id IN (?, ?, ?, ?)",
                    PROJECT,
                    OLD_PROJECT,
                    MEMBER_PROJECT,
                    BETA_PROJECT);
            db.update("DELETE FROM app_user WHERE email LIKE ?", "%" + DOMAIN);
            db.update(
                    "DELETE FROM department WHERE public_id IN (?, ?, ?)",
                    DEPARTMENT,
                    OTHER_DEPARTMENT,
                    BETA_DEPARTMENT);
        }
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        department(db, DEPARTMENT, "Roles fixture Support");
        department(db, OTHER_DEPARTMENT, "Roles fixture Operations");
        account(db, TARGET, "Outgoing", DEPARTMENT, "PROJECT_MANAGER", "TICKET_RESOLVER", "KNOWLEDGE_MANAGER");
        account(db, RECIPIENT, "Replacement", DEPARTMENT, "PROJECT_MANAGER", "KNOWLEDGE_MANAGER");
        account(db, SECOND, "Second", OTHER_DEPARTMENT);
        project(db, PROJECT, DEPARTMENT, "K110-SUP", "ACTIVE", TARGET);
        project(db, OLD_PROJECT, DEPARTMENT, "K110-OLD", "REMOVED", TARGET);
        project(db, MEMBER_PROJECT, OTHER_DEPARTMENT, "K110-MEMBER", "ACTIVE", SECOND);
        membership(db, TARGET, MEMBER_PROJECT, "MEMBER");
        space(db, SPACE, DEPARTMENT, "Roles fixture Space", TARGET);
        space(db, FREE_SPACE, OTHER_DEPARTMENT, "Roles fixture Free", null);
        JdbcTemplate beta = TwoTenantDatabases.tenantB();
        department(beta, BETA_DEPARTMENT, "Roles fixture Beta");
        account(beta, BETA_USER, "Beta secret", BETA_DEPARTMENT, "PROJECT_MANAGER", "KNOWLEDGE_MANAGER");
        project(beta, BETA_PROJECT, BETA_DEPARTMENT, "K110-BETA", "ACTIVE", BETA_USER);
        space(beta, BETA_SPACE, BETA_DEPARTMENT, "Beta secret Space", BETA_USER);
    }

    @Test
    void atomicallyHandsOverResponsibilitiesRemovesMembershipsAndAudits_AC_007_10_AC_007_19_AC_007_21()
            throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        int auditsBefore = auditCount();
        ObjectNode body = update(List.of(), null, List.of());
        handovers(body, RECIPIENT, RECIPIENT);
        body.put("confirmTicketResolverRemoval", true);
        perform(TARGET, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("EMPLOYEE"))
                .andExpect(jsonPath("$.department").isEmpty())
                .andExpect(jsonPath("$.managedSpaces").isEmpty())
                .andExpect(jsonPath("$.ticketResolverProjectIds").isEmpty());
        assertThat(db.queryForObject(
                        "SELECT user_id FROM project_membership WHERE project_id = ? AND role_in_project = 'PROJECT_MANAGER'",
                        UUID.class,
                        PROJECT))
                .isEqualTo(RECIPIENT);
        assertThat(db.queryForObject(
                        "SELECT user_id FROM space_manager WHERE space_id = (SELECT space_id FROM space WHERE public_id = ?)",
                        UUID.class,
                        SPACE))
                .isEqualTo(RECIPIENT);
        assertThat(db.queryForList(
                        "SELECT project_id FROM project_membership WHERE user_id = ? ORDER BY project_id",
                        UUID.class,
                        TARGET))
                .containsExactly(OLD_PROJECT);
        assertThat(db.queryForObject("SELECT count(*) FROM user_role WHERE user_id = ?", Integer.class, TARGET))
                .isZero();
        assertThat(auditCount()).isEqualTo(auditsBefore + 1);
        String details = db.queryForObject(
                "SELECT details::text FROM audit_log_entry WHERE target_id = ? ORDER BY created_at DESC LIMIT 1",
                String.class,
                TARGET);
        assertThat(details)
                .contains(
                        PROJECT.toString(),
                        SPACE.toString(),
                        RECIPIENT.toString(),
                        MEMBER_PROJECT.toString(),
                        "PROJECT_MANAGER",
                        "KNOWLEDGE_MANAGER");
    }

    @Test
    void keepsResolverMembershipAfterProjectHandoverAndAllowsNonPmDepartment_AC_007_10_AC_007_19() throws Exception {
        ObjectNode body = update(List.of("TICKET_RESOLVER", "KNOWLEDGE_MANAGER"), OTHER_DEPARTMENT, List.of(SPACE));
        body.putArray("projectHandovers")
                .addObject()
                .put("projectId", PROJECT.toString())
                .put("recipientId", RECIPIENT.toString());
        perform(TARGET, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department.departmentId").value(OTHER_DEPARTMENT.toString()));
        assertThat(TwoTenantDatabases.tenantA()
                        .queryForObject(
                                "SELECT role_in_project FROM project_membership WHERE user_id = ? AND project_id = ?",
                                String.class,
                                TARGET,
                                PROJECT))
                .isEqualTo("MEMBER");
    }

    @Test
    void droppingResolverKeepsManagedProjectsAndRequiresConfirmation_AC_007_18_AC_007_19() throws Exception {
        ObjectNode body = update(List.of("PROJECT_MANAGER", "KNOWLEDGE_MANAGER"), DEPARTMENT, List.of(SPACE));
        List<List<Map<String, Object>>> before = snapshot();
        perform(TARGET, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG78"))
                .andExpect(jsonPath("$.params.count").value(1))
                .andExpect(jsonPath("$.params.user_name").value("Outgoing"));
        assertThat(snapshot()).isEqualTo(before);
        body.put("confirmTicketResolverRemoval", true);
        perform(TARGET, body).andExpect(status().isOk());
        assertThat(TwoTenantDatabases.tenantA()
                        .queryForList(
                                "SELECT project_id FROM project_membership WHERE user_id = ? ORDER BY project_id",
                                UUID.class,
                                TARGET))
                .containsExactly(PROJECT, OLD_PROJECT);
    }

    @Test
    void rejectsOccupiedSpaceWithoutWriting_AC_007_14_AC_007_20() throws Exception {
        List<List<Map<String, Object>>> before = snapshot();
        perform(SECOND, update(List.of("KNOWLEDGE_MANAGER"), null, List.of(SPACE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG117"))
                .andExpect(jsonPath("$.params.space_name").value("Roles fixture Space"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("spaceIds"));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void rejectsNoProjectRecipientBeforeSpaceAndChoiceErrors_AC_007_17_AC_007_20() throws Exception {
        TwoTenantDatabases.tenantA().update("DELETE FROM user_role WHERE user_id = ?", RECIPIENT);
        List<List<Map<String, Object>>> before = snapshot();
        perform(TARGET, update(List.of(), null, List.of()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG59"))
                .andExpect(jsonPath("$.params.department_name").value("Roles fixture Support"));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void rejectsNoSpaceRecipientBeforeChoiceErrors_AC_007_17_AC_007_20() throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        List<UUID> others = db.queryForList(
                "SELECT u.id FROM app_user u JOIN user_role r ON r.user_id = u.id WHERE u.status = 'ACTIVE' AND r.role = 'KNOWLEDGE_MANAGER' AND u.id <> ?",
                UUID.class,
                TARGET);
        others.forEach(id -> db.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", id));
        try {
            List<List<Map<String, Object>>> before = snapshot();
            perform(TARGET, update(List.of("PROJECT_MANAGER", "TICKET_RESOLVER"), DEPARTMENT, List.of()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MSG118"));
            assertThat(snapshot()).isEqualTo(before);
        } finally {
            others.forEach(id -> db.update("UPDATE app_user SET status = 'ACTIVE' WHERE id = ?", id));
        }
    }

    @Test
    void rejectsMissingDuplicateUnrelatedAndIneligibleHandovers_AC_007_16_AC_007_17() throws Exception {
        List<List<Map<String, Object>>> before = snapshot();
        List<ObjectNode> bodies = new ArrayList<>();
        ObjectNode missing = update(List.of(), null, List.of());
        bodies.add(missing);
        ObjectNode duplicate = update(List.of(), null, List.of());
        handovers(duplicate, RECIPIENT, RECIPIENT);
        duplicate
                .withArray("projectHandovers")
                .addObject()
                .put("projectId", PROJECT.toString())
                .put("recipientId", RECIPIENT.toString());
        bodies.add(duplicate);
        ObjectNode unrelated = update(List.of(), null, List.of());
        handovers(unrelated, RECIPIENT, RECIPIENT);
        ((ObjectNode) unrelated.withArray("projectHandovers").get(0)).put("projectId", OLD_PROJECT.toString());
        bodies.add(unrelated);
        ObjectNode ineligible = update(List.of(), null, List.of());
        handovers(ineligible, SECOND, SECOND);
        bodies.add(ineligible);
        for (ObjectNode body : bodies) {
            perform(TARGET, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MSG01"))
                    .andExpect(jsonPath("$.params.field_name").value("Hand Over Responsibilities"))
                    .andExpect(jsonPath("$.fieldErrors").doesNotExist());
            assertThat(snapshot()).isEqualTo(before);
        }
    }

    @Test
    void neverAcceptsBetaRecipientProjectSpaceOrDepartment_AC_007_22() throws Exception {
        List<List<Map<String, Object>>> before = snapshot();
        ObjectNode recipient = update(List.of(), null, List.of());
        handovers(recipient, BETA_USER, BETA_USER);
        perform(TARGET, recipient)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"));
        ObjectNode project = update(List.of(), null, List.of());
        handovers(project, RECIPIENT, RECIPIENT);
        ((ObjectNode) project.withArray("projectHandovers").get(0)).put("projectId", BETA_PROJECT.toString());
        perform(TARGET, project)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"));
        perform(TARGET, update(List.of("KNOWLEDGE_MANAGER"), null, List.of(BETA_SPACE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("spaceIds"));
        perform(TARGET, update(List.of("PROJECT_MANAGER"), BETA_DEPARTMENT, List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("departmentId"));
        perform(BETA_USER, update(List.of(), null, List.of()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
        assertThat(snapshot()).isEqualTo(before);
        assertThat(TwoTenantDatabases.tenantB()
                        .queryForObject(
                                "SELECT user_id FROM space_manager WHERE space_id = (SELECT space_id FROM space WHERE public_id = ?)",
                                UUID.class,
                                BETA_SPACE))
                .isEqualTo(BETA_USER);
    }

    @Test
    void rollsBackEveryWriteWhenAuditInsertFails_AC_007_20_AC_007_21() throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        db.execute(
                "CREATE FUNCTION reject_k110_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.target_id = '"
                        + TARGET + "'::uuid THEN RAISE EXCEPTION 'test audit failure'; END IF; RETURN NEW; END $$");
        db.execute(
                "CREATE TRIGGER reject_k110_audit BEFORE INSERT ON audit_log_entry FOR EACH ROW EXECUTE FUNCTION reject_k110_audit()");
        try {
            List<List<Map<String, Object>>> before = snapshot();
            int auditsBefore = auditCount();
            ObjectNode body = update(List.of(), null, List.of());
            handovers(body, RECIPIENT, RECIPIENT);
            body.put("confirmTicketResolverRemoval", true);
            perform(TARGET, body)
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("MSG06"));
            assertThat(snapshot()).isEqualTo(before);
            assertThat(auditCount()).isEqualTo(auditsBefore);
        } finally {
            db.execute("DROP TRIGGER reject_k110_audit ON audit_log_entry");
            db.execute("DROP FUNCTION reject_k110_audit()");
        }
    }

    @Test
    void rejectsLastActiveAdminBeforeSelfRemovalAndRejectsSelfOtherwise_AC_007_15() throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        makeAdmin(SECOND);
        List<UUID> others = deactivateOtherAdmins(SECOND, null);
        try {
            performAs(SECOND, update(List.of(), null, List.of()), SECOND)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MSG56"));
            makeAdmin(RECIPIENT);
            performAs(SECOND, update(List.of(), null, List.of()), SECOND)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MSG113"));
            assertThat(db.queryForObject(
                            "SELECT count(*) FROM user_role WHERE user_id = ? AND role = 'ADMIN'",
                            Integer.class,
                            SECOND))
                    .isEqualTo(1);
        } finally {
            restoreAdmins(others);
        }
    }

    @Test
    void concurrentMutualAdminRemovalsLeaveExactlyOneActiveAdmin_AC_007_15() throws Exception {
        makeAdmin(SECOND);
        makeAdmin(RECIPIENT);
        List<UUID> others = deactivateOtherAdmins(SECOND, RECIPIENT);
        try {
            // Retain SECOND's managed Project so either request can win without a handover.
            ObjectNode second = update(List.of("PROJECT_MANAGER"), OTHER_DEPARTMENT, List.of());
            ObjectNode recipient =
                    update(List.of("PROJECT_MANAGER", "KNOWLEDGE_MANAGER"), DEPARTMENT, List.of(FREE_SPACE));
            List<MvcResult> results = race(
                    () -> performAs(SECOND, second, RECIPIENT).andReturn(),
                    () -> performAs(RECIPIENT, recipient, SECOND).andReturn());
            assertThat(results.stream()
                            .map(result -> result.getResponse().getStatus())
                            .sorted()
                            .toList())
                    .containsExactly(200, 409);
            MvcResult rejected = results.stream()
                    .filter(result -> result.getResponse().getStatus() == 409)
                    .findFirst()
                    .orElseThrow();
            assertThat(mapper.readTree(rejected.getResponse().getContentAsString())
                            .path("code")
                            .asText())
                    .isEqualTo("MSG56");
            assertThat(TwoTenantDatabases.tenantA()
                            .queryForObject(
                                    "SELECT count(*) FROM user_role r JOIN app_user u ON u.id = r.user_id WHERE role = 'ADMIN' AND u.status = 'ACTIVE'",
                                    Integer.class))
                    .isEqualTo(1);
        } finally {
            restoreAdmins(others);
        }
    }

    @Test
    void concurrentClaimsForOneSpaceYieldOneConflict_AC_007_14() throws Exception {
        ObjectNode first = update(List.of("PROJECT_MANAGER", "KNOWLEDGE_MANAGER"), DEPARTMENT, List.of(FREE_SPACE));
        ObjectNode second =
                update(List.of("PROJECT_MANAGER", "KNOWLEDGE_MANAGER"), OTHER_DEPARTMENT, List.of(FREE_SPACE));
        List<MvcResult> results = race(
                () -> performAs(RECIPIENT, first, new UUID(0, 1)).andReturn(),
                () -> performAs(SECOND, second, new UUID(0, 1)).andReturn());
        assertThat(results.stream()
                        .map(result -> result.getResponse().getStatus())
                        .sorted()
                        .toList())
                .containsExactly(200, 409);
        MvcResult rejected = results.stream()
                .filter(result -> result.getResponse().getStatus() == 409)
                .findFirst()
                .orElseThrow();
        JsonNode error = mapper.readTree(rejected.getResponse().getContentAsString());
        assertThat(error.path("code").asText()).isEqualTo("MSG117");
        assertThat(error.at("/params/space_name").asText()).isEqualTo("Roles fixture Free");
        assertThat(TwoTenantDatabases.tenantA()
                        .queryForObject(
                                "SELECT count(*) FROM space_manager WHERE space_id = (SELECT space_id FROM space WHERE public_id = ?)",
                                Integer.class,
                                FREE_SPACE))
                .isEqualTo(1);
    }

    @Test
    void partialIndexRejectsASecondProjectManager_AC_007_19() {
        assertThatThrownBy(() -> membership(TwoTenantDatabases.tenantA(), RECIPIENT, PROJECT, "PROJECT_MANAGER"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_membership_one_manager");
    }

    private ObjectNode update(List<String> roles, UUID department, List<UUID> spaces) {
        ObjectNode body = mapper.createObjectNode();
        roles.forEach(body.putArray("roles")::add);
        if (department == null) {
            body.putNull("departmentId");
        } else {
            body.put("departmentId", department.toString());
        }
        var ids = body.putArray("spaceIds");
        spaces.forEach(id -> ids.add(id.toString()));
        return body;
    }

    private void handovers(ObjectNode body, UUID projectRecipient, UUID spaceRecipient) {
        body.putArray("projectHandovers")
                .addObject()
                .put("projectId", PROJECT.toString())
                .put("recipientId", projectRecipient.toString());
        body.putArray("spaceHandovers")
                .addObject()
                .put("spaceId", SPACE.toString())
                .put("recipientId", spaceRecipient.toString());
    }

    private ResultActions perform(UUID target, ObjectNode body) {
        return scope.runIn(TwoTenantDatabases.ACME, () -> {
            try {
                return mvc.perform(put("/api/v1/users/{id}", target)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private ResultActions performAs(UUID target, ObjectNode body, UUID actorId) {
        TSolveUser actor = new TSolveUser(actorId, "admin" + DOMAIN, "Admin", Set.of(Role.ADMIN));
        return scope.runIn(TwoTenantDatabases.ACME, () -> {
            try {
                var authentication =
                        UsernamePasswordAuthenticationToken.authenticated(actor, null, actor.authorities());
                return mvc.perform(put("/api/v1/users/{id}", target)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .authentication(authentication))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private List<MvcResult> race(Callable<MvcResult> first, Callable<MvcResult> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return first.call();
            });
            var two = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return second.call();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));
        }
    }

    private List<List<Map<String, Object>>> snapshot() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        return List.of(
                db.queryForList("SELECT * FROM app_user ORDER BY id"),
                db.queryForList("SELECT * FROM user_role ORDER BY id"),
                db.queryForList("SELECT * FROM project_membership ORDER BY user_id, project_id"),
                db.queryForList("SELECT * FROM space_manager ORDER BY space_id"));
    }

    private int auditCount() {
        return TwoTenantDatabases.tenantA()
                .queryForObject(
                        "SELECT count(*) FROM audit_log_entry WHERE target_id = ? AND action = 'ROLE_CHANGE'",
                        Integer.class,
                        TARGET);
    }

    private void makeAdmin(UUID userId) {
        TwoTenantDatabases.tenantA()
                .update(
                        "INSERT INTO user_role(user_id, role, assigned_at) VALUES (?, 'ADMIN', now()) ON CONFLICT (user_id, role) DO NOTHING",
                        userId);
    }

    private List<UUID> deactivateOtherAdmins(UUID one, UUID two) {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        List<UUID> ids = db
                .queryForList(
                        "SELECT u.id FROM app_user u JOIN user_role r ON r.user_id = u.id WHERE u.status = 'ACTIVE' AND r.role = 'ADMIN'",
                        UUID.class)
                .stream()
                .filter(id -> !id.equals(one) && !id.equals(two))
                .toList();
        ids.forEach(id -> db.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", id));
        return ids;
    }

    private void restoreAdmins(List<UUID> ids) {
        ids.forEach(
                id -> TwoTenantDatabases.tenantA().update("UPDATE app_user SET status = 'ACTIVE' WHERE id = ?", id));
    }

    private void department(JdbcTemplate db, UUID id, String name) {
        db.update("INSERT INTO department(public_id, name, status) VALUES (?, ?, 'ACTIVE')", id, name);
    }

    private void account(JdbcTemplate db, UUID id, String name, UUID department, String... roles) {
        db.update(
                "INSERT INTO app_user(id, email, full_name, status, created_at, department_id) SELECT ?, ?, ?, 'ACTIVE', now(), department_id FROM department WHERE public_id = ?",
                id,
                id + DOMAIN,
                name,
                department);
        for (String role : roles) {
            db.update("INSERT INTO user_role(user_id, role, assigned_at) VALUES (?, ?, now())", id, role);
        }
    }

    private void project(JdbcTemplate db, UUID id, UUID department, String key, String status, UUID manager) {
        db.update(
                "INSERT INTO project(public_id, department_id, jira_project_key, status) SELECT ?, department_id, ?, ? FROM department WHERE public_id = ?",
                id,
                key,
                status,
                department);
        membership(db, manager, id, "PROJECT_MANAGER");
    }

    private void membership(JdbcTemplate db, UUID userId, UUID projectId, String role) {
        db.update(
                "INSERT INTO project_membership(user_id, project_id, role_in_project) VALUES (?, ?, ?)",
                userId,
                projectId,
                role);
    }

    private void space(JdbcTemplate db, UUID id, UUID department, String name, UUID manager) {
        db.update(
                "INSERT INTO space(public_id, department_id, name) SELECT ?, department_id, ? FROM department WHERE public_id = ?",
                id,
                name,
                department);
        if (manager != null) {
            db.update(
                    "INSERT INTO space_manager(space_id, user_id, assigned_at) SELECT space_id, ?, now() FROM space WHERE public_id = ?",
                    manager,
                    id);
        }
    }
}
