package vn.tsolve.wiki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.audit.impl.AuditServiceImpl;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.wiki.dto.GrantWikiPermissionsRequest;
import vn.tsolve.wiki.dto.WikiPermissionCandidates;
import vn.tsolve.wiki.dto.WikiSpaceSettings;
import vn.tsolve.wiki.repository.AccessRepository;
import vn.tsolve.wiki.repository.WikiPermissionRepository;
import vn.tsolve.wiki.service.WikiPermissionService;
import vn.tsolve.wiki.service.impl.WikiPermissionServiceImpl;

class WikiPermissionIT extends WikiReadTestSupport {
    private static AnnotationConfigApplicationContext context;
    private WikiPermissionService service;
    private long otherDepartment;
    private UUID otherDepartmentId;
    private UUID recipient;

    @BeforeAll
    static void startServices() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> jdbc.getDataSource());
        context.register(Config.class);
        context.refresh();
    }

    @AfterAll
    static void closeServices() {
        if (context != null) context.close();
    }

    @BeforeEach
    void preparePermissions() {
        manager();
        signIn();
        service = context.getBean(WikiPermissionService.class);
        otherDepartment = department("Finance");
        otherDepartmentId = departmentId(otherDepartment);
        recipient = account("Recipient", otherDepartment);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bothCoManagersCanOpenSettingsWithNamesInCaseInsensitiveOrder_AC_006_11() {
        UUID coManager = account("alice Manager", null);
        jdbc.update(
                "INSERT INTO user_role(user_id, role, assigned_at) VALUES (?, 'KNOWLEDGE_MANAGER', now())", coManager);
        jdbc.update("INSERT INTO space_manager(space_id, user_id, assigned_at) VALUES (?, ?, now())", space, coManager);

        assertThat(service.getSettings(user, spaceId).knowledgeManagers())
                .containsExactly("alice Manager", "Reader Name");
        var principal =
                new TSolveUser(coManager, "alice@example.test", "alice Manager", Set.of(Role.KNOWLEDGE_MANAGER));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
        assertThat(service.getSettings(coManager, spaceId).knowledgeManagers())
                .containsExactly("alice Manager", "Reader Name");
    }

    @Test
    void omitsDeactivatedCoManagerFromSettings_AC_006_11() {
        UUID coManager = account("alice Manager", null);
        jdbc.update(
                "INSERT INTO user_role(user_id, role, assigned_at) VALUES (?, 'KNOWLEDGE_MANAGER', now())", coManager);
        jdbc.update("INSERT INTO space_manager(space_id, user_id, assigned_at) VALUES (?, ?, now())", space, coManager);
        jdbc.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", coManager);

        assertThat(service.getSettings(user, spaceId).knowledgeManagers()).containsExactly("Reader Name");
    }

    @Test
    void omitsADemotedOrDeactivatedManagerFromTheHeader_AC_006_1_BUG_309() {
        var repository = context.getBean(WikiPermissionRepository.class);
        jdbc.update("DELETE FROM user_role WHERE user_id = ? AND role = 'KNOWLEDGE_MANAGER'", user);
        assertThat(repository.getSettings(space).knowledgeManagers()).isEmpty();
        jdbc.update("INSERT INTO user_role(user_id, role, assigned_at) VALUES (?, 'KNOWLEDGE_MANAGER', now())", user);
        jdbc.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", user);
        assertThat(repository.getSettings(space).knowledgeManagers()).isEmpty();
    }

    @Test
    void returnsSortedPermissionsWithLockedOwnDepartmentAndInactiveUsers_AC_006_1() {
        long alpha = department("Alpha");
        UUID zed = account("Zed", null);
        grant(List.of(otherDepartmentId, departmentId(alpha)), List.of(zed, recipient));
        jdbc.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", recipient);
        var settings = service.getSettings(user, spaceId);
        assertThat(settings.spaceId()).isEqualTo(spaceId);
        assertThat(settings.name()).isEqualTo("Support Wiki");
        assertThat(settings.departmentName()).isEqualTo("Support");
        assertThat(settings.knowledgeManagers()).containsExactly("Reader Name");
        assertThat(settings.ownDepartment())
                .isEqualTo(new WikiSpaceSettings.OwnDepartment(departmentId(department), "Support", true));
        assertThat(settings.departments())
                .extracting(WikiSpaceSettings.DepartmentPermission::name)
                .containsExactly("Alpha", "Finance");
        assertThat(settings.users())
                .extracting(WikiSpaceSettings.UserPermission::fullName)
                .containsExactly("Recipient", "Zed");
        assertThat(settings.users().getFirst().active()).isFalse();
        assertThat(settings.users().getFirst().homeDepartment()).isEqualTo("Finance");
        assertThat(settings.users().getFirst().email()).isEqualTo(recipient + "@example.test");
        assertThat(settings.users().getLast().homeDepartment()).isNull();
        assertThat(settings.users().getFirst().permissionId()).isNotNull();
        assertThat(settings.users().getFirst().grantedBy()).isEqualTo("Reader Name");
        assertThat(settings.users().getFirst().grantedAt()).isNotNull();
        assertThat(settings.departments().getFirst().grantedBy()).isEqualTo("Reader Name");
        assertThat(settings.departments().getFirst().grantedAt()).isNotNull();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM space_permission WHERE department_id = ?", Integer.class, department))
                .isZero();
    }

    @Test
    void candidatesExcludeOwnInactiveAndAlreadyGrantedGranteesAndSortByName_AC_006_2() {
        UUID alpha = account("Alpha", null);
        UUID zed = account("Zed", null);
        UUID inactive = account("Inactive", null);
        jdbc.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", inactive);
        long deleted = department("Deleted");
        jdbc.update("UPDATE department SET status = 'DELETED' WHERE department_id = ?", deleted);
        long alphaDepartment = department("Alpha");
        long zedDepartment = department("Zed");
        grant(List.of(otherDepartmentId), List.of(recipient));
        var candidates = service.listCandidates(user, spaceId);
        assertThat(candidates.departments())
                .extracting(WikiPermissionCandidates.Department::departmentId)
                .containsExactly(departmentId(alphaDepartment), departmentId(zedDepartment));
        assertThat(candidates.users())
                .extracting(WikiPermissionCandidates.User::fullName)
                .containsExactly("Alpha", "Reader Name", "Zed");
        assertThat(candidates.users())
                .extracting(WikiPermissionCandidates.User::userId)
                .contains(alpha, user, zed)
                .doesNotContain(recipient, inactive);
        assertThat(candidates.users().getFirst().homeDepartment()).isNull();
        // Own-Department View does not count as a stored individual permission.
        assertThat(candidates.users().get(1).homeDepartment()).isEqualTo("Support");
    }

    @Test
    void duplicateIdsAndRepeatGrantsStoreAndAuditEachPermissionOnce_AC_006_3_AC_006_9() throws Exception {
        Instant before = Instant.now();
        grant(List.of(otherDepartmentId, otherDepartmentId), List.of(recipient, recipient));
        grant(List.of(otherDepartmentId), List.of(recipient));
        assertThat(permissionCount()).isEqualTo(2);
        assertThat(auditCount()).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT granted_by FROM space_permission", UUID.class))
                .containsOnly(user);
        assertThat(jdbc.queryForList("SELECT granted_at FROM space_permission", java.sql.Timestamp.class))
                .allSatisfy(at -> assertThat(at.toInstant()).isBetween(before.minusMillis(1), Instant.now()));
        assertAudit("GRANT", "DEPARTMENT", otherDepartmentId);
        assertAudit("GRANT", "USER", recipient);
    }

    @Test
    void emptySelectionStoresAndAuditsNothing_AC_006_4() {
        assertThatThrownBy(() -> grant(List.of(), List.of()))
                .isInstanceOfSatisfying(FieldMessageCodeException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("MSG01");
                    assertThat(error.getField()).isEqualTo("grantees");
                });
        assertThat(permissionCount()).isZero();
        assertThat(auditCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"UNKNOWN_DEPARTMENT", "DELETED_DEPARTMENT", "OWN_DEPARTMENT", "UNKNOWN_USER", "INACTIVE_USER"})
    void invalidIdRejectsEntireMixedBatchIncludingExistingPermission_AC_006_5(String state) {
        grant(List.of(), List.of(recipient));
        UUID validUser = account("Valid", null);
        UUID badDepartment = otherDepartmentId;
        UUID badUser = recipient;
        switch (state) {
            case "UNKNOWN_DEPARTMENT" -> badDepartment = UUID.randomUUID();
            case "DELETED_DEPARTMENT" ->
                jdbc.update("UPDATE department SET status = 'DELETED' WHERE department_id = ?", otherDepartment);
            case "OWN_DEPARTMENT" -> badDepartment = departmentId(department);
            case "UNKNOWN_USER" -> badUser = UUID.randomUUID();
            case "INACTIVE_USER" -> jdbc.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", recipient);
            default -> throw new IllegalArgumentException(state);
        }
        UUID targetDepartment = badDepartment;
        UUID targetUser = badUser;
        assertThatThrownBy(() -> grant(List.of(targetDepartment), List.of(validUser, targetUser)))
                .isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                        .isEqualTo("MSG04"));
        assertThat(permissionCount()).isEqualTo(1);
        assertThat(auditCount()).isEqualTo(1);
    }

    @Test
    void viewRuleReflectsGrantAndRevokeOnNextRequestAndKeepsDepartmentView_AC_006_6_AC_006_10() {
        assertThat(browse.listSpaces(recipient)).isEmpty();
        grant(List.of(), List.of(recipient));
        assertThat(browse.listSpaces(recipient)).extracting(v -> v.spaceId()).containsExactly(spaceId);
        grant(List.of(otherDepartmentId), List.of());
        service.revoke(user, userPermission());
        assertThat(browse.listSpaces(recipient)).extracting(v -> v.spaceId()).containsExactly(spaceId);
        UUID departmentPermission =
                service.getSettings(user, spaceId).departments().getFirst().permissionId();
        service.revoke(user, departmentPermission);
        assertThat(browse.listSpaces(recipient)).isEmpty();
        assertThat(permissionCount()).isZero();
        assertThat(auditCount()).isEqualTo(4);
    }

    @Test
    void hidesUnknownOrOtherManagersPermission_AC_006_7() {
        grant(List.of(), List.of(recipient));
        UUID id = userPermission();
        long foreign = space(department("Other"), "Other Wiki");
        jdbc.update("DELETE FROM space_manager WHERE space_id = ?", space);
        jdbc.update("INSERT INTO space_manager(space_id, user_id, assigned_at) VALUES (?, ?, now())", foreign, user);
        for (UUID target : List.of(id, UUID.randomUUID())) {
            assertThatThrownBy(() -> service.revoke(user, target))
                    .isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                            .isEqualTo("MSG04"));
        }
        assertThat(permissionCount()).isEqualTo(1);
        assertThat(auditCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"OTHER_MANAGER", "ROLE_REMOVED", "DEACTIVATED", "HIDDEN", "DEPARTMENT_DELETED", "UNKNOWN_SPACE"})
    void rechecksCurrentManagerAndActiveScopeOnEveryRequest_AC_006_8(String state) {
        grant(List.of(), List.of(recipient));
        UUID id = userPermission();
        UUID target = spaceId;
        switch (state) {
            case "OTHER_MANAGER" -> jdbc.update("DELETE FROM space_manager WHERE space_id = ?", space);
            case "ROLE_REMOVED" -> jdbc.update("DELETE FROM user_role WHERE user_id = ?", user);
            case "DEACTIVATED" -> jdbc.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", user);
            case "HIDDEN" -> jdbc.update("UPDATE space SET status = 'HIDDEN' WHERE space_id = ?", space);
            case "DEPARTMENT_DELETED" ->
                jdbc.update("UPDATE department SET status = 'DELETED' WHERE department_id = ?", department);
            case "UNKNOWN_SPACE" -> target = UUID.randomUUID();
            default -> throw new IllegalArgumentException(state);
        }
        UUID requestedSpace = target;
        Class<? extends RuntimeException> expected =
                Set.of("HIDDEN", "DEPARTMENT_DELETED", "UNKNOWN_SPACE").contains(state)
                        ? NotFoundException.class
                        : AccessDeniedException.class;
        assertThatThrownBy(() -> service.getSettings(user, requestedSpace)).isInstanceOf(expected);
        assertThatThrownBy(() -> service.listCandidates(user, requestedSpace)).isInstanceOf(expected);
        assertThatThrownBy(() -> service.grant(
                        user, requestedSpace, new GrantWikiPermissionsRequest(List.of(otherDepartmentId), List.of())))
                .isInstanceOf(expected);
        if (!state.equals("UNKNOWN_SPACE")) {
            assertThatThrownBy(() -> service.revoke(user, id)).isInstanceOf(NotFoundException.class);
        }
        assertThat(permissionCount()).isEqualTo(1);
        assertThat(auditCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void grantAndRevokeAuditExactGranteeInSameTransaction_AC_006_9(boolean departmentGrant) throws Exception {
        grant(
                departmentGrant ? List.of(otherDepartmentId) : List.of(),
                departmentGrant ? List.of() : List.of(recipient));
        UUID id = departmentGrant
                ? service.getSettings(user, spaceId).departments().getFirst().permissionId()
                : userPermission();
        service.revoke(user, id);
        assertAudit("REVOKE", departmentGrant ? "DEPARTMENT" : "USER", departmentGrant ? otherDepartmentId : recipient);
        assertThat(auditCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GRANT", "REVOKE"})
    void auditFailureRollsBackWholePermissionBatch_AC_006_9(String operation) {
        if (operation.equals("REVOKE")) grant(List.of(), List.of(recipient));
        UUID id = operation.equals("REVOKE") ? userPermission() : null;
        jdbc.execute("""
                CREATE FUNCTION reject_permission_audit() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'audit unavailable'; END $$
                """);
        jdbc.execute(
                "CREATE TRIGGER reject_permission_audit BEFORE INSERT ON audit_log_entry FOR EACH ROW EXECUTE FUNCTION reject_permission_audit()");
        try {
            assertThatThrownBy(() -> {
                        if (operation.equals("GRANT")) grant(List.of(otherDepartmentId), List.of(recipient));
                        else service.revoke(user, id);
                    })
                    .isInstanceOf(RuntimeException.class);
            assertThat(permissionCount()).isEqualTo(operation.equals("GRANT") ? 0 : 1);
            assertThat(auditCount()).isEqualTo(operation.equals("GRANT") ? 0 : 1);
        } finally {
            jdbc.execute("DROP TRIGGER reject_permission_audit ON audit_log_entry");
            jdbc.execute("DROP FUNCTION reject_permission_audit()");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentGrantsSkipLoserAndWriteExactlyOneAudit_AC_006_3_AC_006_9(boolean departmentGrant) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentGrant(start, departmentGrant));
            var second = executor.submit(() -> concurrentGrant(start, departmentGrant));
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        }
        assertThat(permissionCount()).isEqualTo(1);
        assertThat(auditCount()).isEqualTo(1);
    }

    private Void concurrentGrant(CyclicBarrier start, boolean departmentGrant) throws Exception {
        signIn();
        try {
            start.await(10, TimeUnit.SECONDS);
            grant(
                    departmentGrant ? List.of(otherDepartmentId) : List.of(),
                    departmentGrant ? List.of() : List.of(recipient));
            return null;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void assertAudit(String operation, String type, UUID grantee) throws Exception {
        var entry = jdbc.queryForMap(
                "SELECT * FROM audit_log_entry WHERE details->>'operation' = ? AND details->>'granteeId' = ?",
                operation,
                grantee.toString());
        assertThat(entry)
                .containsEntry("actor_id", user)
                .containsEntry("action", "PERMISSION_CHANGE")
                .containsEntry("target_type", "WIKI_SPACE")
                .containsEntry("target_id", spaceId);
        var details = new ObjectMapper().readTree(entry.get("details").toString());
        assertThat(details.size()).isEqualTo(3);
        assertThat(details.get("operation").asText()).isEqualTo(operation);
        assertThat(details.get("granteeType").asText()).isEqualTo(type);
        assertThat(details.get("granteeId").asText()).isEqualTo(grantee.toString());
    }

    private void grant(List<UUID> departments, List<UUID> users) {
        service.grant(user, spaceId, new GrantWikiPermissionsRequest(departments, users));
    }

    private UUID userPermission() {
        return service.getSettings(user, spaceId).users().getFirst().permissionId();
    }

    private UUID departmentId(long id) {
        return jdbc.queryForObject("SELECT public_id FROM department WHERE department_id = ?", UUID.class, id);
    }

    private UUID account(String name, Long homeDepartment) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO app_user(id, email, full_name, department_id, status, created_at) VALUES (?, ?, ?, ?, 'ACTIVE', now())",
                id,
                id + "@example.test",
                name,
                homeDepartment);
        return id;
    }

    private int permissionCount() {
        return jdbc.queryForObject("SELECT count(*) FROM space_permission", Integer.class);
    }

    private int auditCount() {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log_entry WHERE actor_id = ?", Integer.class, user);
    }

    private void signIn() {
        var principal = new TSolveUser(user, "reader@example.test", "Reader Name", Set.of(Role.KNOWLEDGE_MANAGER));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = AccessRepository.class)
    static class Config {
        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("vn.tsolve.wiki.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            return factory;
        }

        @Bean
        JpaTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean
        AuditService audit() {
            return new AuditServiceImpl(jdbc, new ObjectMapper());
        }

        @Bean
        WikiPermissionRepository permissions() {
            return new WikiPermissionRepository(new NamedParameterJdbcTemplate(jdbc));
        }

        @Bean
        WikiPermissionService permissionService(
                WikiPermissionRepository permissions, AccessRepository access, AuditService audit) {
            return new WikiPermissionServiceImpl(permissions, access, audit);
        }
    }
}
