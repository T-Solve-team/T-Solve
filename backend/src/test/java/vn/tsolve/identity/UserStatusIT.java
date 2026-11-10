package vn.tsolve.identity;

import static org.assertj.core.api.Assertions.assertThat;
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
class UserStatusIT {
    private static final UUID TARGET = new UUID(109, 1);
    private static final UUID RECIPIENT = new UUID(109, 2);
    private static final UUID SECOND = new UUID(109, 3);
    private static final UUID BETA_USER = new UUID(109, 4);
    private static final UUID DEPARTMENT = new UUID(109, 10);
    private static final UUID OTHER_DEPARTMENT = new UUID(109, 11);
    private static final UUID BETA_DEPARTMENT = new UUID(109, 12);
    private static final UUID PROJECT = new UUID(109, 20);
    private static final UUID OLD_PROJECT = new UUID(109, 21);
    private static final UUID MEMBER_PROJECT = new UUID(109, 22);
    private static final UUID BETA_PROJECT = new UUID(109, 23);
    private static final UUID SPACE = new UUID(109, 30);
    private static final UUID FREE_SPACE = new UUID(109, 31);
    private static final UUID BETA_SPACE = new UUID(109, 32);
    private static final String DOMAIN = "@status-fixture.test";

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
        department(db, DEPARTMENT, "Status fixture Support");
        department(db, OTHER_DEPARTMENT, "Status fixture Operations");
        account(db, TARGET, "Outgoing", DEPARTMENT, "PROJECT_MANAGER", "TICKET_RESOLVER", "KNOWLEDGE_MANAGER");
        account(db, RECIPIENT, "Replacement", DEPARTMENT, "PROJECT_MANAGER", "KNOWLEDGE_MANAGER");
        account(db, SECOND, "Second", OTHER_DEPARTMENT);
        project(db, PROJECT, DEPARTMENT, "K109-SUP", "ACTIVE", TARGET);
        project(db, OLD_PROJECT, DEPARTMENT, "K109-OLD", "REMOVED", TARGET);
        project(db, MEMBER_PROJECT, OTHER_DEPARTMENT, "K109-MEMBER", "ACTIVE", SECOND);
        membership(db, TARGET, MEMBER_PROJECT, "MEMBER");
        space(db, SPACE, DEPARTMENT, "Status fixture Space", TARGET);
        space(db, FREE_SPACE, OTHER_DEPARTMENT, "Status fixture Free", null);
        JdbcTemplate beta = TwoTenantDatabases.tenantB();
        department(beta, BETA_DEPARTMENT, "Status fixture Beta");
        account(beta, BETA_USER, "Beta secret", BETA_DEPARTMENT, "PROJECT_MANAGER", "KNOWLEDGE_MANAGER");
        project(beta, BETA_PROJECT, BETA_DEPARTMENT, "K109-BETA", "ACTIVE", BETA_USER);
        space(beta, BETA_SPACE, BETA_DEPARTMENT, "Beta secret Space", BETA_USER);
    }

    @Test
    void deactivatesWithHandoversKeepsAssignmentsAndAudits_AC_006_9_AC_006_15_AC_006_17() throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        var roles = db.queryForList("SELECT * FROM user_role WHERE user_id = ? ORDER BY id", TARGET);
        var accountBefore = db.queryForMap("SELECT * FROM app_user WHERE id = ?", TARGET);
        var memberBefore = db.queryForMap(
                "SELECT * FROM project_membership WHERE user_id = ? AND project_id = ?", TARGET, MEMBER_PROJECT);
        var oldBefore = db.queryForMap(
                "SELECT * FROM project_membership WHERE user_id = ? AND project_id = ?", TARGET, OLD_PROJECT);
        int auditsBefore = auditCount();
        ObjectNode body = update("DEACTIVATED");
        handovers(body, RECIPIENT, RECIPIENT);
        perform(TARGET, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEACTIVATED"))
                .andExpect(jsonPath("$.managedSpaces").isEmpty());
        assertThat(db.queryForObject(
                        "SELECT user_id FROM project_membership WHERE project_id = ? AND role_in_project = 'PROJECT_MANAGER'",
                        UUID.class,
                        PROJECT))
                .isEqualTo(RECIPIENT);
        assertThat(db.queryForObject(
                        "SELECT role_in_project FROM project_membership WHERE user_id = ? AND project_id = ?",
                        String.class,
                        TARGET,
                        PROJECT))
                .isEqualTo("MEMBER");
        assertThat(db.queryForObject(
                        "SELECT user_id FROM space_manager WHERE space_id = (SELECT space_id FROM space WHERE public_id = ?)",
                        UUID.class,
                        SPACE))
                .isEqualTo(RECIPIENT);
        assertThat(db.queryForList("SELECT * FROM user_role WHERE user_id = ? ORDER BY id", TARGET))
                .isEqualTo(roles);
        accountBefore.put("status", "DEACTIVATED");
        assertThat(db.queryForMap("SELECT * FROM app_user WHERE id = ?", TARGET))
                .isEqualTo(accountBefore);
        assertThat(db.queryForMap(
                        "SELECT * FROM project_membership WHERE user_id = ? AND project_id = ?",
                        TARGET,
                        MEMBER_PROJECT))
                .isEqualTo(memberBefore);
        assertThat(db.queryForMap(
                        "SELECT * FROM project_membership WHERE user_id = ? AND project_id = ?", TARGET, OLD_PROJECT))
                .isEqualTo(oldBefore);
        assertThat(auditCount()).isEqualTo(auditsBefore + 1);
        JsonNode event = mapper.readTree(db.queryForObject(
                "SELECT details::text FROM audit_log_entry WHERE target_id = ? ORDER BY created_at DESC LIMIT 1",
                String.class,
                TARGET));
        assertThat(event.path("statusBefore").asText()).isEqualTo("ACTIVE");
        assertThat(event.path("statusAfter").asText()).isEqualTo("DEACTIVATED");
        assertThat(event.at("/projectHandovers/0/projectId").asText()).isEqualTo(PROJECT.toString());
        assertThat(event.at("/projectHandovers/0/recipientId").asText()).isEqualTo(RECIPIENT.toString());
        assertThat(event.at("/spaceHandovers/0/spaceId").asText()).isEqualTo(SPACE.toString());
        assertThat(event.at("/spaceHandovers/0/recipientId").asText()).isEqualTo(RECIPIENT.toString());
    }

    @Test
    void removesHandedOverMembershipWithoutResolverButKeepsOthers_AC_006_15() throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        db.update("DELETE FROM user_role WHERE user_id = ? AND role = 'TICKET_RESOLVER'", TARGET);
        membership(db, RECIPIENT, PROJECT, "MEMBER");
        ObjectNode body = update("DEACTIVATED");
        handovers(body, RECIPIENT, RECIPIENT);
        perform(TARGET, body).andExpect(status().isOk());
        assertThat(db.queryForObject(
                        "SELECT role_in_project FROM project_membership WHERE user_id = ? AND project_id = ?",
                        String.class,
                        RECIPIENT,
                        PROJECT))
                .isEqualTo("PROJECT_MANAGER");
        assertThat(db.queryForList(
                        "SELECT project_id FROM project_membership WHERE user_id = ? ORDER BY project_id",
                        UUID.class,
                        TARGET))
                .containsExactly(OLD_PROJECT, MEMBER_PROJECT);
    }

    @Test
    void activationKeepsEverythingAndReturnsNoResponsibilities_AC_006_10() throws Exception {
        ObjectNode body = update("DEACTIVATED");
        handovers(body, RECIPIENT, RECIPIENT);
        perform(TARGET, body).andExpect(status().isOk());
        List<List<Map<String, Object>>> before = snapshot();
        perform(TARGET, update("ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.managedSpaces").isEmpty());
        before.get(0).stream()
                .filter(row -> TARGET.equals(row.get("id")))
                .findFirst()
                .orElseThrow()
                .put("status", "ACTIVE");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void rejectsNoProjectRecipientBeforeSpaceAndMatching_AC_006_14_AC_006_16() throws Exception {
        TwoTenantDatabases.tenantA().update("DELETE FROM user_role WHERE user_id = ?", RECIPIENT);
        var before = snapshot();
        perform(TARGET, update("DEACTIVATED"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG59"))
                .andExpect(jsonPath("$.params.department_name").value("Status fixture Support"));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void rejectsNoSpaceRecipientBeforeMatching_AC_006_14_AC_006_16() throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        List<UUID> others = db.queryForList(
                "SELECT u.id FROM app_user u JOIN user_role r ON r.user_id = u.id WHERE u.status = 'ACTIVE' AND r.role = 'KNOWLEDGE_MANAGER' AND u.id <> ?",
                UUID.class,
                TARGET);
        others.forEach(id -> db.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", id));
        // Keep a Project recipient eligible while leaving no Knowledge Manager recipient.
        db.update("DELETE FROM user_role WHERE user_id = ? AND role = 'KNOWLEDGE_MANAGER'", RECIPIENT);
        db.update("UPDATE app_user SET status = 'ACTIVE' WHERE id = ?", RECIPIENT);
        try {
            var before = snapshot();
            perform(TARGET, update("DEACTIVATED"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MSG118"));
            assertThat(snapshot()).isEqualTo(before);
        } finally {
            others.forEach(id -> db.update("UPDATE app_user SET status = 'ACTIVE' WHERE id = ?", id));
        }
    }

    @Test
    void rejectsBadChoicesWithoutWriting_AC_006_14_AC_006_16() throws Exception {
        var before = snapshot();
        int audits = auditCount();
        List<ObjectNode> bodies = new ArrayList<>();
        bodies.add(update("DEACTIVATED"));
        ObjectNode duplicate = update("DEACTIVATED");
        handovers(duplicate, RECIPIENT, RECIPIENT);
        duplicate
                .withArray("projectHandovers")
                .add(duplicate.withArray("projectHandovers").get(0).deepCopy());
        bodies.add(duplicate);
        ObjectNode ineligible = update("DEACTIVATED");
        handovers(ineligible, SECOND, SECOND);
        bodies.add(ineligible);
        ObjectNode unrelated = update("DEACTIVATED");
        handovers(unrelated, RECIPIENT, RECIPIENT);
        ((ObjectNode) unrelated.withArray("projectHandovers").get(0)).put("projectId", OLD_PROJECT.toString());
        bodies.add(unrelated);
        for (ObjectNode body : bodies) {
            perform(TARGET, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MSG01"))
                    .andExpect(jsonPath("$.params.field_name").value("Hand Over Responsibilities"))
                    .andExpect(jsonPath("$.fieldErrors").doesNotExist());
            assertThat(snapshot()).isEqualTo(before);
            assertThat(auditCount()).isEqualTo(audits);
        }
    }

    @Test
    void malformedStatusChangesNothing_AC_006_12_AC_006_16() throws Exception {
        var before = snapshot();
        ObjectNode body = update("invalid");
        perform(TARGET, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"));
        assertThat(snapshot()).isEqualTo(before);
        body = update("ACTIVE");
        handovers(body, RECIPIENT, RECIPIENT);
        perform(TARGET, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void lastAdminBeforeSelfAndSelfBeforeHandover_AC_006_13_AC_006_16() throws Exception {
        makeAdmin(SECOND);
        List<UUID> others = deactivateOtherAdmins(SECOND, null);
        try {
            var before = snapshot();
            performAs(SECOND, update("DEACTIVATED"), SECOND)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MSG56"));
            assertThat(snapshot()).isEqualTo(before);
            makeAdmin(RECIPIENT);
            before = snapshot();
            performAs(SECOND, update("DEACTIVATED"), SECOND)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MSG112"));
            assertThat(snapshot()).isEqualTo(before);
        } finally {
            restoreAdmins(others);
        }
    }

    @Test
    void betaRecipientsProjectsSpacesAndAccountRefused_AC_006_18() throws Exception {
        var before = snapshot();
        var betaBefore = TwoTenantDatabases.tenantB().queryForList("SELECT * FROM app_user ORDER BY id");
        ObjectNode body = update("DEACTIVATED");
        handovers(body, BETA_USER, BETA_USER);
        perform(TARGET, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"));
        handovers(body, RECIPIENT, RECIPIENT);
        ((ObjectNode) body.withArray("projectHandovers").get(0)).put("projectId", BETA_PROJECT.toString());
        perform(TARGET, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"));
        handovers(body, RECIPIENT, RECIPIENT);
        ((ObjectNode) body.withArray("spaceHandovers").get(0)).put("spaceId", BETA_SPACE.toString());
        perform(TARGET, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"));
        perform(BETA_USER, update("ACTIVE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
        assertThat(snapshot()).isEqualTo(before);
        assertThat(TwoTenantDatabases.tenantB().queryForList("SELECT * FROM app_user ORDER BY id"))
                .isEqualTo(betaBefore);
    }

    @Test
    void auditFailureRollsBackEveryWrite_AC_006_16_AC_006_17() throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        db.execute(
                "CREATE FUNCTION reject_k109_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.target_id = '"
                        + TARGET + "'::uuid THEN RAISE EXCEPTION 'test audit failure'; END IF; RETURN NEW; END $$");
        db.execute(
                "CREATE TRIGGER reject_k109_audit BEFORE INSERT ON audit_log_entry FOR EACH ROW EXECUTE FUNCTION reject_k109_audit()");
        try {
            var before = snapshot();
            int audits = auditCount();
            ObjectNode body = update("DEACTIVATED");
            handovers(body, RECIPIENT, RECIPIENT);
            perform(TARGET, body)
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("MSG06"));
            assertThat(snapshot()).isEqualTo(before);
            assertThat(auditCount()).isEqualTo(audits);
        } finally {
            db.execute("DROP TRIGGER reject_k109_audit ON audit_log_entry");
            db.execute("DROP FUNCTION reject_k109_audit()");
        }
    }

    @Test
    void concurrentMutualAdminDeactivationsLeaveOneActiveAdmin_AC_006_13() throws Exception {
        makeAdmin(SECOND);
        makeAdmin(RECIPIENT);
        // Neither Admin has a responsibility, so only the Admin guard determines the loser.
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        db.update("DELETE FROM project_membership WHERE user_id = ?", SECOND);
        List<UUID> others = deactivateOtherAdmins(SECOND, RECIPIENT);
        try {
            List<MvcResult> results = race(
                    () -> performAs(SECOND, update("DEACTIVATED"), RECIPIENT).andReturn(),
                    () -> performAs(RECIPIENT, update("DEACTIVATED"), SECOND).andReturn());
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
            assertThat(db.queryForObject(
                            "SELECT count(*) FROM app_user u JOIN user_role r ON r.user_id = u.id WHERE u.status = 'ACTIVE' AND r.role = 'ADMIN'",
                            Integer.class))
                    .isEqualTo(1);
        } finally {
            restoreAdmins(others);
        }
    }

    @Test
    void noOpHasNoWritesOrAudit_AC_006_16_AC_006_17() throws Exception {
        var before = snapshot();
        int audits = auditCount();
        perform(TARGET, update("ACTIVE")).andExpect(status().isOk());
        assertThat(snapshot()).isEqualTo(before);
        assertThat(auditCount()).isEqualTo(audits);
        // A deactivated account with no managed responsibility is also a no-op.
        TwoTenantDatabases.tenantA().update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", RECIPIENT);
        int recipientAudits = TwoTenantDatabases.tenantA()
                .queryForObject("SELECT count(*) FROM audit_log_entry WHERE target_id = ?", Integer.class, RECIPIENT);
        before = snapshot();
        perform(RECIPIENT, update("DEACTIVATED")).andExpect(status().isOk());
        assertThat(snapshot()).isEqualTo(before);
        assertThat(TwoTenantDatabases.tenantA()
                        .queryForObject(
                                "SELECT count(*) FROM audit_log_entry WHERE target_id = ?", Integer.class, RECIPIENT))
                .isEqualTo(recipientAudits);
    }

    @Test
    void unboundWorkspaceReturnsInternalError_AC_006_18() throws Exception {
        var before = snapshot();
        mvc.perform(put("/api/v1/users/{id}/status", TARGET)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("MSG06"));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void statusAndRoleUpdatesShareAdminSerialization_AC_006_13() throws Exception {
        makeAdmin(SECOND);
        makeAdmin(RECIPIENT);
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        db.update("DELETE FROM project_membership WHERE user_id = ?", SECOND);
        List<UUID> others = deactivateOtherAdmins(SECOND, RECIPIENT);
        try {
            ObjectNode roles = mapper.createObjectNode();
            roles.putArray("roles").add("PROJECT_MANAGER");
            roles.put("departmentId", DEPARTMENT.toString());
            roles.putArray("spaceIds");
            TSolveUser actor = new TSolveUser(SECOND, "admin" + DOMAIN, "Admin", Set.of(Role.ADMIN));
            List<MvcResult> results = race(
                    () -> performAs(SECOND, update("DEACTIVATED"), RECIPIENT).andReturn(),
                    () -> scope.runIn(TwoTenantDatabases.ACME, () -> {
                        try {
                            var authentication =
                                    UsernamePasswordAuthenticationToken.authenticated(actor, null, actor.authorities());
                            return mvc.perform(put("/api/v1/users/{id}", RECIPIENT)
                                            .with(org.springframework.security.test.web.servlet.request
                                                    .SecurityMockMvcRequestPostProcessors.authentication(
                                                    authentication))
                                            .with(csrf())
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(roles.toString()))
                                    .andReturn();
                        } catch (Exception error) {
                            throw new AssertionError(error);
                        }
                    }));
            assertThat(results.stream()
                            .map(result -> result.getResponse().getStatus())
                            .sorted()
                            .toList())
                    .containsExactly(200, 409);
            var rejected = results.stream()
                    .filter(result -> result.getResponse().getStatus() == 409)
                    .findFirst()
                    .orElseThrow();
            assertThat(mapper.readTree(rejected.getResponse().getContentAsString())
                            .path("code")
                            .asText())
                    .isEqualTo("MSG56");
            assertThat(db.queryForObject(
                            "SELECT count(*) FROM app_user u JOIN user_role r ON r.user_id = u.id WHERE u.status = 'ACTIVE' AND r.role = 'ADMIN'",
                            Integer.class))
                    .isEqualTo(1);
        } finally {
            restoreAdmins(others);
        }
    }

    private ObjectNode update(String status) {
        return mapper.createObjectNode().put("status", status);
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
                return mvc.perform(put("/api/v1/users/{id}/status", target)
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
                return mvc.perform(put("/api/v1/users/{id}/status", target)
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
