package vn.tsolve.wiki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import vn.tsolve.chatbot.source.service.WikiSources;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.session.dto.SpaceRef;
import vn.tsolve.common.session.lookup.ManagedSpaceLookup;
import vn.tsolve.common.session.lookup.ViewableSpaceLookup;
import vn.tsolve.ticket.TicketTestDatabase;
import vn.tsolve.triage.dto.WikiSource;
import vn.tsolve.triage.service.WikiDocumentAccess;
import vn.tsolve.triage.service.WikiSourceRetrieval;
import vn.tsolve.wiki.repository.AccessRepository;

class WikiAccessIT {
    private static JdbcTemplate jdbc;
    private static LocalContainerEntityManagerFactoryBean factory;
    private EntityManager entityManager;
    private AnnotationConfigApplicationContext context;
    private AccessRepository access;
    private WikiSources chatbot;
    private WikiSourceRetrieval triage;
    private WikiDocumentAccess documents;
    private ManagedSpaceLookup managed;
    private ViewableSpaceLookup viewable;
    private UUID userId;
    private TSolveUser user;
    private long targetDepartment;
    private long targetSpace;

    @BeforeAll
    static void migrate() throws Exception {
        var server = TicketTestDatabase.DB;
        var admin = new JdbcTemplate(
                new DriverManagerDataSource(server.getJdbcUrl(), server.getUsername(), server.getPassword()));
        String databaseName = "wiki_access_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE " + databaseName);
        String url = server.getJdbcUrl();
        var dataSource = new DriverManagerDataSource(
                url.substring(0, url.lastIndexOf('/') + 1) + databaseName, server.getUsername(), server.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        try (Connection connection = dataSource.getConnection();
                ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor();
                Liquibase liquibase = new Liquibase(
                        "db/changelog/db.changelog-master.xml",
                        resources,
                        DatabaseFactory.getInstance()
                                .findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
            liquibase.update("");
        }
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("vn.tsolve.wiki.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
        factory.afterPropertiesSet();
    }

    @AfterAll
    static void closeFactory() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @BeforeEach
    void prepare() {
        jdbc.execute("TRUNCATE department, app_user CASCADE");
        entityManager = factory.getObject().createEntityManager();
        access = new JpaRepositoryFactory(entityManager).getRepository(AccessRepository.class);
        context = new AnnotationConfigApplicationContext();
        context.registerBean(AccessRepository.class, () -> access);
        context.register(
                vn.tsolve.wiki.service.impl.WikiSourcesImpl.class,
                vn.tsolve.wiki.service.impl.WikiSourceRetrievalImpl.class,
                vn.tsolve.wiki.service.impl.WikiDocumentAccessImpl.class,
                vn.tsolve.wiki.service.impl.WikiSpaceLookups.class);
        context.refresh();
        chatbot = context.getBean(WikiSources.class);
        triage = context.getBean(WikiSourceRetrieval.class);
        documents = context.getBean(WikiDocumentAccess.class);
        managed = context.getBean(ManagedSpaceLookup.class);
        viewable = context.getBean(ViewableSpaceLookup.class);
        userId = jdbc.queryForObject("""
                INSERT INTO app_user(email, full_name, status, created_at)
                VALUES ('viewer@example.test', 'Viewer', 'ACTIVE', now()) RETURNING id
                """, UUID.class);
        user = new TSolveUser(userId, "viewer@example.test", "Viewer", Set.of());
        targetDepartment = department("Target");
        targetSpace = space(targetDepartment, "Target Wiki");
    }

    @AfterEach
    void close() {
        if (context != null) {
            context.close();
        }
        if (entityManager != null) {
            entityManager.close();
        }
    }

    @Test
    void createsWikiTablesForTheViewRule_AC_001_1() {
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('folder', 'wiki_document', 'wiki_chunk', 'space_permission')
                """, String.class))
                .containsExactlyInAnyOrder("folder", "wiki_document", "wiki_chunk", "space_permission");
    }

    @Test
    void enforcesWikiStorageConstraints_AC_001_1() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO space_permission(space_id, granted_by, granted_at) VALUES (?, ?, now())
                """, targetSpace, userId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO space_permission(space_id, department_id, user_id, granted_by, granted_at)
                VALUES (?, ?, ?, ?, now())
                """, targetSpace, targetDepartment, userId, userId))
                .isInstanceOf(DataIntegrityViolationException.class);
        grant("USER_PERMISSION");
        assertThatThrownBy(() -> grant("USER_PERMISSION")).isInstanceOf(DataIntegrityViolationException.class);
        grantDepartment(targetSpace, targetDepartment);
        assertThatThrownBy(() -> grantDepartment(targetSpace, targetDepartment))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID folder = jdbc.queryForObject("""
                INSERT INTO folder(space_id, name, level, created_by, created_at, updated_at)
                VALUES (?, 'Guides', 1, ?, now(), now()) RETURNING folder_id
                """, UUID.class, targetSpace, userId);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO folder(space_id, name, level, created_by, created_at, updated_at)
                VALUES (?, 'GUIDES', 1, ?, now(), now())
                """, targetSpace, userId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE folder SET level = 4 WHERE folder_id = ?", folder))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID guide = document(targetSpace, "Guide", "COMPANY_WIDE", "INDEXED");
        assertThatThrownBy(() -> document(targetSpace, "GUIDE", "COMPANY_WIDE", "INDEXED"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(
                "UPDATE wiki_document SET status = 'DELETED', folder_id = ? WHERE wiki_document_id = ?", folder, guide);
        document(targetSpace, "GUIDE", "COMPANY_WIDE", "INDEXED");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM folder WHERE folder_id = ?", folder))
                .isInstanceOf(DataIntegrityViolationException.class);
        chunk(guide, 0, "Passage", 1, 0);
        assertThatThrownBy(() -> chunk(guide, 0, "Duplicate", 1, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "OWN_DEPARTMENT", "DEPARTMENT_PERMISSION", "USER_PERMISSION", "PROJECT_MEMBER"})
    void grantsEachViewPathFromCurrentDatabaseState_AC_001_1(String path) {
        assertThat(access.canViewSpace(userId, targetSpace)).isFalse();
        grant(path);
        assertThat(access.canViewSpace(userId, targetSpace)).isTrue();
        assertThat(viewable.findViewableSpaces(userId))
                .extracting(SpaceRef::name)
                .containsExactly("Target Wiki");
    }

    @ParameterizedTest
    @ValueSource(strings = {"HIDDEN_SPACE", "DELETED_DEPARTMENT"})
    void hidesInactiveScopesEvenFromManagers_AC_001_1(String scope) {
        grant("MANAGER");
        UUID document = document(targetSpace, "Guide", "COMPANY_WIDE", "INDEXED");
        chunk(document, 0, "Passage", 1, 0);
        if (scope.equals("HIDDEN_SPACE")) {
            jdbc.update("UPDATE space SET status = 'HIDDEN' WHERE space_id = ?", targetSpace);
        } else {
            jdbc.update("UPDATE department SET status = 'DELETED' WHERE department_id = ?", targetDepartment);
        }
        assertThat(access.canViewSpace(userId, targetSpace)).isFalse();
        assertThat(managed.findManagedSpaces(userId)).isEmpty();
        assertThat(viewable.findViewableSpaces(userId)).isEmpty();
        assertThat(chatbot.unavailable(user, Set.of(document))).containsExactly(document);
        assertThat(triage.nearest(vector(1, 0), 0.5, 10)).isEmpty();
    }

    @Test
    void grantsProjectDepartmentViewWithoutPermissionAndRechecksEligibility_AC_001_1_AC_001_2_BUG_249() {
        long home = department("Home");
        jdbc.update("UPDATE app_user SET department_id = ? WHERE id = ?", home, userId);
        UUID project = jdbc.queryForObject(
                "INSERT INTO project(department_id, jira_project_key, status) VALUES (?, 'PROJECT', 'INACTIVE') RETURNING public_id",
                UUID.class,
                targetDepartment);
        jdbc.update(
                "INSERT INTO project_membership(user_id, project_id, role_in_project) VALUES (?, ?, 'MEMBER')",
                userId,
                project);
        UUID guide = document(targetSpace, "Guide", "DEPARTMENT", "INDEXED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM space_permission", Integer.class))
                .isZero();
        assertThat(access.canViewSpace(userId, targetSpace)).isFalse();

        role("TICKET_RESOLVER");
        assertThat(access.canViewSpace(userId, targetSpace)).isTrue();
        assertThat(viewable.findViewableSpaces(userId))
                .containsExactly(new SpaceRef(publicId(targetSpace), "Target Wiki"));
        assertThat(documents.available(userId.toString(), List.of(guide.toString())))
                .containsExactly(guide.toString());

        jdbc.update("DELETE FROM user_role WHERE user_id = ? AND role = 'TICKET_RESOLVER'", userId);
        assertThat(access.canViewSpace(userId, targetSpace)).isFalse();
        assertThat(viewable.findViewableSpaces(userId)).isEmpty();
        assertThat(documents.available(userId.toString(), List.of(guide.toString())))
                .isEmpty();

        role("TICKET_RESOLVER");
        jdbc.update("UPDATE project_membership SET role_in_project = 'PROJECT_MANAGER' WHERE user_id = ?", userId);
        assertThat(access.canViewSpace(userId, targetSpace)).isFalse();
        jdbc.update("UPDATE project_membership SET role_in_project = 'MEMBER' WHERE user_id = ?", userId);
        assertThat(access.canViewSpace(userId, targetSpace)).isTrue();
        jdbc.update("UPDATE department SET status = 'DELETED' WHERE department_id = ?", targetDepartment);
        assertThat(access.canViewSpace(userId, targetSpace)).isFalse();
        assertThat(viewable.findViewableSpaces(userId)).isEmpty();
    }

    @Test
    void requiresResolverRoleForMemberDepartments_AC_001_2() {
        grant("PROJECT_MEMBER");
        assertThat(access.canViewSpace(userId, targetSpace)).isTrue();
        jdbc.update("DELETE FROM user_role WHERE user_id = ? AND role = 'TICKET_RESOLVER'", userId);
        assertThat(access.canViewSpace(userId, targetSpace)).isFalse();
        assertThat(viewable.findViewableSpaces(userId)).isEmpty();
        role("TICKET_RESOLVER");
        jdbc.update("UPDATE project_membership SET role_in_project = 'PROJECT_MANAGER' WHERE user_id = ?", userId);
        assertThat(access.canViewSpace(userId, targetSpace)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEPARTMENT_PERMISSION", "PROJECT_MEMBER"})
    void ignoresPermissionsGrantedToDeletedDepartments_AC_001_2(String path) {
        grant(path);
        jdbc.update(
                "UPDATE department SET status = 'DELETED' WHERE department_id IN (SELECT department_id FROM space_permission WHERE space_id = ?)",
                targetSpace);
        assertThat(access.canViewSpace(userId, targetSpace)).isFalse();
        assertThat(viewable.findViewableSpaces(userId)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "OWN_DEPARTMENT", "DEPARTMENT_PERMISSION", "USER_PERMISSION", "PROJECT_MEMBER"})
    void deactivationRemovesEveryView_AC_001_3(String path) {
        grant(path);
        UUID document = document(targetSpace, "Guide", "COMPANY_WIDE", "INDEXED");
        chunk(document, 0, "Passage", 1, 0);
        jdbc.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", userId);
        assertThat(access.canViewSpace(userId, targetSpace)).isFalse();
        assertThat(managed.findManagedSpaces(userId)).isEmpty();
        assertThat(viewable.findViewableSpaces(userId)).isEmpty();
        assertThat(chatbot.nearest(user, vector(1, 0), 0.5, 10)).isEmpty();
        assertThat(chatbot.unavailable(user, Set.of(document))).containsExactly(document);
        assertThat(documents.available(userId.toString(), List.of(document.toString())))
                .isEmpty();
    }

    @Test
    void permitsCompanyWideButNotDepartmentDocumentsOfANonViewableSpace_AC_001_4() {
        UUID company = document(targetSpace, "Company", "COMPANY_WIDE", "INDEXED");
        UUID restricted = document(targetSpace, "Department", "DEPARTMENT", "INDEXED");
        assertThat(chatbot.unavailable(user, Set.of(company, restricted))).containsExactly(restricted);
        grant("USER_PERMISSION");
        assertThat(chatbot.unavailable(user, Set.of(company, restricted))).isEmpty();
        jdbc.update("UPDATE department SET status = 'DELETED' WHERE department_id = ?", targetDepartment);
        assertThat(chatbot.unavailable(user, Set.of(company, restricted)))
                .containsExactlyInAnyOrder(company, restricted);
    }

    @Test
    void returnsTheBestPassagePerDocumentAboveThresholdInOrderWithLimit_AC_001_5_AC_007_10() {
        grant("USER_PERMISSION");
        UUID best = document(targetSpace, "Best", "DEPARTMENT", "INDEXED");
        chunk(best, 0, "Weak passage", 0, 1);
        chunk(best, 1, "Best passage", 1, 0);
        UUID next = document(targetSpace, "Next", "COMPANY_WIDE", "INDEXED");
        chunk(next, 0, "Next passage", 0.8f, 0.6f);
        UUID far = document(targetSpace, "Far", "COMPANY_WIDE", "INDEXED");
        chunk(far, 0, "Far passage", 0, 1);
        jdbc.update("UPDATE wiki_document SET document_type = 'POLICY' WHERE wiki_document_id = ?", best);
        jdbc.update("UPDATE wiki_document SET document_type = 'FAQ' WHERE wiki_document_id = ?", next);

        var passages = chatbot.nearest(user, vector(1, 0), 0.7, 10);
        assertThat(passages).extracting(WikiSources.WikiPassage::wikiDocumentId).containsExactly(best, next);
        assertThat(passages.getFirst().title()).isEqualTo("Best");
        assertThat(passages.getFirst().text()).isEqualTo("Best passage");
        assertThat(passages.getFirst().similarity()).isEqualTo(1);
        assertThat(passages.get(1).similarity()).isCloseTo(0.8, within(0.00001));
        assertThat(chatbot.nearest(user, vector(1, 0), 0.7, 1)).hasSize(1).containsExactly(passages.getFirst());
        assertThat(chatbot.nearest(user, vector(1, 0), 1, 10)).containsExactly(passages.getFirst());
        assertThat(chatbot.nearest(user, vector(1, 0), 0.7, 0)).isEmpty();
        assertThat(triage.nearest(vector(1, 0), 0.7, 10))
                .extracting(WikiSource::documentId)
                .containsExactly(next.toString());
    }

    @Test
    void breaksEqualSimilaritiesByDocumentThenPassagePosition_AC_001_5() {
        UUID first = document(targetSpace, "First", "COMPANY_WIDE", "INDEXED");
        UUID second = document(targetSpace, "Second", "COMPANY_WIDE", "INDEXED");
        chunk(first, 0, "First passage", 1, 0);
        chunk(first, 1, "Second passage", 1, 0);
        chunk(second, 0, "Other document", 1, 0);
        var rows = chatbot.nearest(user, vector(1, 0), 1, 10);
        assertThat(rows)
                .extracting(WikiSources.WikiPassage::wikiDocumentId)
                .containsExactlyElementsOf(jdbc.queryForList(
                        "SELECT wiki_document_id FROM wiki_document ORDER BY wiki_document_id", UUID.class));
        assertThat(rows.stream()
                        .filter(row -> row.wikiDocumentId().equals(first))
                        .findFirst()
                        .orElseThrow()
                        .text())
                .isEqualTo("First passage");
    }

    @Test
    void rechecksUnknownTrashedPermanentlyDeletedAndRevokedCitations_AC_001_6() {
        grant("USER_PERMISSION");
        UUID kept = document(targetSpace, "Kept", "COMPANY_WIDE", "INDEXED");
        UUID revoked = document(targetSpace, "Revoked", "DEPARTMENT", "INDEXED");
        UUID trashed = document(targetSpace, "Trashed", "COMPANY_WIDE", "INDEXED");
        UUID purged = document(targetSpace, "Purged", "COMPANY_WIDE", "INDEXED");
        UUID unknown = UUID.randomUUID();
        chunk(trashed, 0, "Still stored", 1, 0);
        chunk(purged, 0, "Removed with document", 1, 0);
        assertThat(chatbot.unavailable(user, Set.of(kept, revoked, trashed, purged)))
                .isEmpty();
        jdbc.update("DELETE FROM space_permission WHERE user_id = ?", userId);
        jdbc.update(
                "UPDATE wiki_document SET status = 'DELETED', deleted_by = ?, deleted_at = now() WHERE wiki_document_id = ?",
                userId,
                trashed);
        jdbc.update("DELETE FROM wiki_document WHERE wiki_document_id = ?", purged);
        assertThat(chatbot.unavailable(user, Set.of(kept, revoked, trashed, purged, unknown)))
                .containsExactlyInAnyOrder(revoked, trashed, purged, unknown);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM wiki_chunk WHERE wiki_document_id = ?", Integer.class, purged))
                .isZero();
        assertThat(chatbot.nearest(user, vector(1, 0), 0.5, 10)).isEmpty();
        assertThat(triage.nearest(vector(1, 0), 0.5, 10)).isEmpty();
    }

    @Test
    void suggestionsNeverRetrieveDepartmentDocumentsAndApplyThresholdOrderAndLimit_AC_001_7_AC_007_10() {
        grant("MANAGER");
        UUID restricted = document(targetSpace, "Private", "DEPARTMENT", "INDEXED");
        chunk(restricted, 0, "Private passage", 1, 0);
        UUID best = document(targetSpace, "Best", "COMPANY_WIDE", "INDEXED");
        chunk(best, 0, "Weak", 0, 1);
        chunk(best, 1, "Best passage", 1, 0);
        UUID next = document(targetSpace, "Next", "COMPANY_WIDE", "INDEXED");
        chunk(next, 0, "Next passage", 0.8f, 0.6f);
        UUID far = document(targetSpace, "Far", "COMPANY_WIDE", "INDEXED");
        chunk(far, 0, "Far passage", 0, 1);
        jdbc.update("UPDATE wiki_document SET document_type = 'FORM' WHERE wiki_document_id = ?", best);
        jdbc.update("UPDATE wiki_document SET document_type = 'RECORD' WHERE wiki_document_id = ?", next);

        var rows = triage.nearest(vector(1, 0), 0.7, 10);
        assertThat(rows).extracting(WikiSource::documentId).containsExactly(best.toString(), next.toString());
        assertThat(rows.getFirst().title()).isEqualTo("Best");
        assertThat(rows.getFirst().content()).isEqualTo("Best passage");
        assertThat(rows.getFirst().similarity()).isEqualTo(1);
        assertThat(triage.nearest(vector(1, 0), 0.7, 1)).containsExactly(rows.getFirst());
        assertThat(triage.nearest(vector(1, 0), 1, 10)).containsExactly(rows.getFirst());
        assertThat(triage.nearest(vector(1, 0), 0.7, 0)).isEmpty();
    }

    @Test
    void returnsOnlyGivenViewableUuidDocumentIds_AC_001_8() {
        UUID company = document(targetSpace, "Company", "COMPANY_WIDE", "PENDING");
        UUID restricted = document(targetSpace, "Private", "DEPARTMENT", "INDEXED");
        document(targetSpace, "Not requested", "COMPANY_WIDE", "INDEXED");
        assertThat(documents.available(
                        userId.toString(),
                        List.of(
                                company.toString(),
                                restricted.toString(),
                                "not-a-uuid",
                                UUID.randomUUID().toString())))
                .containsExactly(company.toString());
    }

    @Test
    void listsManagedSpacesByNameAndRechecksTheRole_AC_001_9() {
        grant("MANAGER");
        long another = space(department("Another"), "A Wiki");
        jdbc.update("INSERT INTO space_manager(space_id, user_id, assigned_at) VALUES (?, ?, now())", another, userId);
        assertThat(managed.findManagedSpaces(userId))
                .extracting(SpaceRef::name)
                .containsExactly("A Wiki", "Target Wiki");
        assertThat(managed.findManagedSpaces(userId).getFirst().spaceId()).isEqualTo(publicId(another));
        jdbc.update("DELETE FROM user_role WHERE user_id = ? AND role = 'KNOWLEDGE_MANAGER'", userId);
        assertThat(managed.findManagedSpaces(userId)).isEmpty();
        assertThat(viewable.findViewableSpaces(userId)).isEmpty();
        grant("USER_PERMISSION");
        assertThat(viewable.findViewableSpaces(userId))
                .extracting(SpaceRef::name)
                .containsExactly("Target Wiki");
        assertThat(managed.findManagedSpaces(userId)).isEmpty();
    }

    @Test
    void listsAllViewableSpacesOnceByNameUsingPublicIds_AC_001_10() {
        grant("USER_PERMISSION");
        long home = department("Home");
        jdbc.update("UPDATE app_user SET department_id = ? WHERE id = ?", home, userId);
        long alpha = space(home, "A Wiki");
        grantDepartment(targetSpace, home);
        space(department("Unrelated"), "Unrelated Wiki");
        assertThat(viewable.findViewableSpaces(userId))
                .containsExactly(
                        new SpaceRef(publicId(alpha), "A Wiki"), new SpaceRef(publicId(targetSpace), "Target Wiki"));
        assertThat(viewable.findViewableSpaces(UUID.randomUUID())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "FAILED"})
    void excludesUnindexedDocumentsFromRetrievalButKeepsThemAvailable_AC_001_11(String status) {
        UUID document = document(targetSpace, "Guide", "COMPANY_WIDE", status);
        // Even stale chunks must not make an unindexed document retrievable.
        chunk(document, 0, "Passage", 1, 0);
        assertThat(chatbot.nearest(user, vector(1, 0), 0.5, 10)).isEmpty();
        assertThat(triage.nearest(vector(1, 0), 0.5, 10)).isEmpty();
        assertThat(chatbot.unavailable(user, Set.of(document))).isEmpty();
        assertThat(documents.available(userId.toString(), List.of(document.toString())))
                .containsExactly(document.toString());
        assertThat(jdbc.queryForObject(
                        "SELECT search_vector @@ to_tsquery('simple', 'Guide') FROM wiki_document WHERE wiki_document_id = ?",
                        Boolean.class,
                        document))
                .isTrue();
    }

    private long department(String name) {
        return jdbc.queryForObject("INSERT INTO department(name) VALUES (?) RETURNING department_id", Long.class, name);
    }

    private long space(long departmentId, String name) {
        return jdbc.queryForObject(
                "INSERT INTO space(department_id, name) VALUES (?, ?) RETURNING space_id",
                Long.class,
                departmentId,
                name);
    }

    private UUID publicId(long spaceId) {
        return jdbc.queryForObject("SELECT public_id FROM space WHERE space_id = ?", UUID.class, spaceId);
    }

    private void role(String role) {
        jdbc.update("INSERT INTO user_role(user_id, role, assigned_at) VALUES (?, ?, now())", userId, role);
    }

    private void grantDepartment(long spaceId, long departmentId) {
        jdbc.update(
                "INSERT INTO space_permission(space_id, department_id, granted_by, granted_at) VALUES (?, ?, ?, now())",
                spaceId,
                departmentId,
                userId);
    }

    private void grant(String path) {
        switch (path) {
            case "MANAGER" -> {
                role("KNOWLEDGE_MANAGER");
                jdbc.update(
                        "INSERT INTO space_manager(space_id, user_id, assigned_at) VALUES (?, ?, now())",
                        targetSpace,
                        userId);
            }
            case "OWN_DEPARTMENT" ->
                jdbc.update("UPDATE app_user SET department_id = ? WHERE id = ?", targetDepartment, userId);
            case "DEPARTMENT_PERMISSION" -> {
                long home = department("Home");
                jdbc.update("UPDATE app_user SET department_id = ? WHERE id = ?", home, userId);
                grantDepartment(targetSpace, home);
            }
            case "USER_PERMISSION" ->
                jdbc.update(
                        "INSERT INTO space_permission(space_id, user_id, granted_by, granted_at) VALUES (?, ?, ?, now())",
                        targetSpace,
                        userId,
                        userId);
            case "PROJECT_MEMBER" -> {
                long projectDepartment = department("Project Department");
                UUID project = jdbc.queryForObject(
                        "INSERT INTO project(department_id, jira_project_key, status) VALUES (?, 'PROJECT', 'INACTIVE') RETURNING public_id",
                        UUID.class,
                        projectDepartment);
                jdbc.update(
                        "INSERT INTO project_membership(user_id, project_id, role_in_project) VALUES (?, ?, 'MEMBER')",
                        userId,
                        project);
                role("TICKET_RESOLVER");
                grantDepartment(targetSpace, projectDepartment);
            }
            default -> throw new IllegalArgumentException(path);
        }
    }

    private UUID document(long spaceId, String title, String visibility, String indexStatus) {
        return jdbc.queryForObject("""
                INSERT INTO wiki_document(space_id, title, visibility, status, content, file_key, file_name,
                    file_size, content_type, word_count, index_status, owner_id, created_at, updated_at)
                VALUES (?, ?, ?, 'PUBLISHED', 'Document body', 'wiki/file', 'guide.pdf', 10,
                    'application/pdf', 2, ?, ?, now(), now()) RETURNING wiki_document_id
                """, UUID.class, spaceId, title, visibility, indexStatus, userId);
    }

    private void chunk(UUID documentId, int position, String text, float x, float y) {
        jdbc.update("""
                INSERT INTO wiki_chunk(wiki_document_id, position, text, embedding, model, created_at)
                VALUES (?, ?, ?, CAST(? AS vector), 'bge-m3', now())
                """, documentId, position, text, Arrays.toString(vector(x, y)));
    }

    private static float[] vector(float x, float y) {
        float[] vector = new float[1024];
        vector[0] = x;
        vector[1] = y;
        return vector;
    }
}
